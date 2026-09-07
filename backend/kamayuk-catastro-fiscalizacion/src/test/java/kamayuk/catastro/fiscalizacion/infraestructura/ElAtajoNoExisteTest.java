package kamayuk.catastro.fiscalizacion.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import kamayuk.catastro.auditoria.AuditoriaJdbc;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.fiscalizacion.aplicacion.LevantarActa;
import kamayuk.catastro.fiscalizacion.aplicacion.VerificarEnCampo;
import kamayuk.catastro.fiscalizacion.aplicacion.VerificarEnGabinete;
import kamayuk.catastro.fiscalizacion.dominio.Acta;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.ClaseDeHallazgo;
import kamayuk.catastro.fiscalizacion.dominio.EstadoDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.EtapaDeVerificacion;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import kamayuk.catastro.fiscalizacion.dominio.OrigenDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import kamayuk.catastro.fiscalizacion.dominio.TasaDeDescarte;
import kamayuk.catastro.nucleo.LectorDeFichas;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AC 3 de #6: <b>el atajo se intenta, y se comprueba que no se puede</b>.
 *
 * <h2>Por que va hasta PostgreSQL de verdad</h2>
 *
 * <p>Porque las dos compuertas no las sostiene solo un {@code if} de Java: las sostienen tambien
 * las claves foraneas —el hallazgo cuelga del candidato y el acta del hallazgo— y la politica RLS,
 * que es lo unico que impide que un candidato de otra municipalidad sirva de puerta. Un doble no
 * tiene ninguna de las dos cosas.
 *
 * <p>La conexion es la de {@code kamayuk_app}. Un superusuario omite RLS incluso con {@code FORCE
 * ROW LEVEL SECURITY}, asi que una prueba escrita sobre el no verificaria ningun aislamiento.
 */
@DisplayName("AC 3 — Las dos compuertas, y el atajo que no existe")
class ElAtajoNoExisteTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);
    private static final Observacion OBSERVACION =
            Observacion.de("verificacion de la campania de prueba");
    private static final String INSUMOS = "{\"origen\":\"ORTOFOTO\",\"tesela\":\"z16/1/1\"}";

    private static BaseDeDatosDePrueba base;
    private static long municipalidadA;
    private static long municipalidadB;
    private static FiscalizacionRepositoryJdbc repositorio;
    private static VerificarEnGabinete gabinete;
    private static VerificarEnCampo campo;
    private static LevantarActa actas;
    private static TransactionTemplate enUnaTransaccion;
    private static long predioDeA;
    private static long fichaDeA;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidadA = DatosDePrueba.crearMunicipalidad(base, "240301", "La que fiscaliza");
        municipalidadB = DatosDePrueba.crearMunicipalidad(base, "240302", "La vecina");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidadA, parametroId, "FA");
        DatosDePrueba.sembrarTenant(base, municipalidadB, parametroId, "FB");

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        JdbcClient jdbc = JdbcClient.create(pool);
        TenantTransactionManager gestor = new TenantTransactionManager(pool);
        repositorio = new FiscalizacionRepositoryJdbc(jdbc);
        AuditoriaJdbc auditoria = new AuditoriaJdbc(jdbc, RELOJ);

        gabinete = envolver(new VerificarEnGabinete(repositorio, auditoria, RELOJ), gestor);
        campo =
                envolver(
                        new VerificarEnCampo(repositorio, LECTOR_DE_FICHAS, auditoria, RELOJ),
                        gestor);
        actas = envolver(new LevantarActa(repositorio, auditoria, RELOJ), gestor);

        // La SIEMBRA de esta prueba tambien va dentro de una transaccion, y no por comodidad:
        // el `INSERT` toma la municipalidad de `current_setting('app.municipalidad_id')`, que solo
        // existe si alguien emitio el `SET LOCAL`. Fuera de transaccion la primera insercion muere
        // con «unrecognized configuration parameter», que es exactamente lo que debe pasar (#486)
        // — se midio al escribir esta prueba, y por eso queda dicho aqui.
        enUnaTransaccion = new TransactionTemplate(gestor);

        predioDeA = unPredioDe(municipalidadA);
        fichaDeA = unaFichaDe(municipalidadA, predioDeA);
    }

    /**
     * El lector de fichas del vecino, con lo unico que este contexto le pide.
     *
     * <p>Devuelve identificador y area: ni un metodo que escriba. Es la unica arista de este modulo
     * hacia otro contexto, y esta declarada en {@code tiposAjenosQueFiscalizacionSoloLee()}.
     */
    private static final LectorDeFichas LECTOR_DE_FICHAS =
            new LectorDeFichas() {
                @Override
                public Optional<Long> fichaVigenteEn(long predioId, java.time.LocalDate fecha) {
                    return predioId == predioDeA ? Optional.of(fichaDeA) : Optional.empty();
                }

                @Override
                public Optional<AreaM2> areaDeLaVersion(long fichaId) {
                    return fichaId == fichaDeA
                            ? Optional.of(AreaM2.de("120.00"))
                            : Optional.empty();
                }
            };

    @SuppressWarnings("unchecked")
    private static <T> T envolver(T objetivo, TenantTransactionManager gestor) {
        ProxyFactory fabrica = new ProxyFactory(objetivo);
        fabrica.setProxyTargetClass(true);
        fabrica.addAdvice(
                new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        return (T) fabrica.getProxy();
    }

    @AfterAll
    static void cerrar() {
        if (base != null) {
            base.close();
        }
    }

    @BeforeEach
    void contexto() {
        TenantContext.fijar(new MunicipalidadId(municipalidadA));
        OrigenContext.fijar(new Origen("ana.gabinete", "PC-07", "10.0.0.7"));
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
        OrigenContext.limpiar();
    }

    @Nested
    @DisplayName("Un candidato no produce nada sin las DOS compuertas")
    class SinLasDosCompuertas {

        @Test
        @DisplayName("EL ATAJO: verificar en campo lo que gabinete no admitio no se puede")
        void elAtajoDeLaSegundaCompuerta() {
            long candidatoId = unCandidatoDetectado("ATAJO-1");

            assertThatThrownBy(
                            () ->
                                    campo.confirmar(
                                            candidatoId,
                                            AreaM2.de("180.00"),
                                            "luis.campo",
                                            OBSERVACION))
                    .isInstanceOf(Candidato.TransicionQueNoExiste.class)
                    .hasMessageContaining("DETECTADO");

            enUnaTransaccion.executeWithoutResult(
                    estado -> {
                        assertThat(repositorio.hallazgoDelCandidato(candidatoId))
                                .as(
                                        "y no queda ningun hallazgo: la transaccion deshace lo que"
                                                + " hubiera empezado")
                                .isEmpty();
                        assertThat(repositorio.candidatoPorId(candidatoId).orElseThrow().estado())
                                .isEqualTo(EstadoDelCandidato.DETECTADO);
                    });
        }

        @Test
        @DisplayName("y un acta sobre un candidato que no llego a campo tampoco")
        void elAtajoDelActa() {
            long candidatoId = unCandidatoDetectado("ATAJO-2");
            gabinete.admitir(candidatoId, OBSERVACION);
            Hallazgo hallazgo =
                    campo.confirmar(candidatoId, AreaM2.de("180.00"), "luis.campo", OBSERVACION);

            // Y ahora la unica forma de que el acta se quede sin sus compuertas: mover el
            // candidato por debajo. La foranea dice que la fila existe, no en que estado esta.
            devolverElCandidatoADetectado(candidatoId);

            assertThatThrownBy(
                            () ->
                                    actas.levantar(
                                            hallazgo.id(),
                                            "ACT-ATAJO-2",
                                            "luis.campo",
                                            "se hallo mas area de la inscrita",
                                            OBSERVACION))
                    .isInstanceOf(LevantarActa.SinLasDosCompuertas.class)
                    .hasMessageContaining("Una ortofoto detecta techos, no predios");

            enUnaTransaccion.executeWithoutResult(
                    estado -> assertThat(repositorio.actaDelHallazgo(hallazgo.id())).isEmpty());
        }

        @Test
        @DisplayName("el recorrido bueno SI llega al acta, y ese es el contraste")
        void elRecorridoBuenoLlegaAlActa() {
            long candidatoId = unCandidatoDetectado("BUENO-1");

            gabinete.admitir(candidatoId, OBSERVACION);
            Hallazgo hallazgo =
                    campo.confirmar(candidatoId, AreaM2.de("180.00"), "luis.campo", OBSERVACION);
            Acta acta =
                    actas.levantar(
                            hallazgo.id(),
                            "ACT-BUENO-1",
                            "luis.campo",
                            "se hallo mas area de la inscrita",
                            OBSERVACION);

            assertThat(acta.id()).isNotNull();
            assertThat(hallazgo.fichaId())
                    .as("y el hallazgo dice QUE VERSION de ficha se contrasto (AC 4)")
                    .isEqualTo(fichaDeA);
            assertThat(hallazgo.areaDeLaFicha())
                    .as("con su area COPIADA al verificar, no releida despues")
                    .isEqualTo(AreaM2.de("120.00"));
            assertThat(hallazgo.excesoVerificado()).contains(AreaM2.de("60.00"));
        }
    }

    @Nested
    @DisplayName("#20 AC 5 — Lo que el acta asienta llega a la columna jsonb")
    class LaBitacoraDelActa {

        /**
         * Lo que rompia el {@code cast}: una comilla dentro de un nombre que teclea una persona.
         */
        private static final String INSPECTOR_CON_COMILLA = "Juan \"El Tuerto\" Perez";

        @Test
        @DisplayName("un inspector con una comilla en el nombre ya no impide levantar el acta")
        void unInspectorConComillaNoImpideLevantarElActa() throws SQLException {
            // Hasta #20, `LevantarActa` componia su JSON concatenando y no escapaba ni el numero ni
            // el inspector: con este nombre, el `cast(… AS jsonb)` moria con «invalid input syntax
            // for type json», la transaccion entera se revertia y el acta era IMPOSIBLE de levantar
            // —con un mensaje que hablaba de JSON y no del inspector—.
            long candidatoId = unCandidatoDetectado("COMILLA-1");
            gabinete.admitir(candidatoId, OBSERVACION);
            Hallazgo hallazgo =
                    campo.confirmar(
                            candidatoId, AreaM2.de("180.00"), INSPECTOR_CON_COMILLA, OBSERVACION);

            Acta acta =
                    actas.levantar(
                            hallazgo.id(),
                            "ACT-COMILLA-1",
                            INSPECTOR_CON_COMILLA,
                            "se hallo mas area de la inscrita",
                            OBSERVACION);

            assertThat(acta.id()).isNotNull();
            assertThat(
                            unTexto(
                                    "SELECT datos_nuevos ->> 'inspector' FROM auditoria"
                                            + " WHERE tabla = 'acta' AND clave = '"
                                            + acta.id()
                                            + "'"))
                    .as(
                            "el nombre entra TAL CUAL y se puede volver a leer campo a campo:"
                                    + " escapar lo hace el serializador")
                    .isEqualTo(INSPECTOR_CON_COMILLA);
            assertThat(
                            unTexto(
                                    "SELECT jsonb_typeof(datos_nuevos) FROM auditoria"
                                            + " WHERE tabla = 'acta' AND clave = '"
                                            + acta.id()
                                            + "'"))
                    .as("y lo que se asienta es un objeto, no un escalar")
                    .isEqualTo("object");
        }

        @Test
        @DisplayName("y el «antes/despues» del candidato tambien, con el motivo de un descarte")
        void elAntesYElDespuesDelCandidatoTambien() throws SQLException {
            // El otro compositor de este modulo, y el que tenia el segundo `escapar()`: el
            // `motivo` de un descarte lo escribe una persona en un formulario, y ese escapar()
            // cubria la comilla y NO los caracteres de control.
            long candidatoId = unCandidatoDetectado("COMILLA-2");
            gabinete.admitir(candidatoId, OBSERVACION);
            campo.descartar(
                    candidatoId,
                    "es un toldo,\n no una \"edificacion\"\tsegun el plano",
                    OBSERVACION);

            assertThat(
                            unTexto(
                                    "SELECT datos_nuevos -> 'descarte' ->> 'motivo' FROM auditoria"
                                            + " WHERE tabla = 'candidato' AND clave = '"
                                            + candidatoId
                                            + "'"
                                            + " ORDER BY id DESC LIMIT 1"))
                    .as("con su salto de linea y su tabulador dentro, que el escapar() no cubria")
                    .isEqualTo("es un toldo,\n no una \"edificacion\"\tsegun el plano");
        }
    }

    @Nested
    @DisplayName("El descarte se conserva, y su tasa se puede consultar por etapa")
    class ElDescarteSeCuenta {

        @Test
        @DisplayName("los descartes de las dos compuertas se cuentan por separado (AC 7)")
        void laTasaPorEtapa() {
            long campaniaId = unaCampania("TASA-1");
            long enGabinete = unCandidatoDetectado(campaniaId);
            long enCampo = unCandidatoDetectado(campaniaId);
            long verificado = unCandidatoDetectado(campaniaId);
            long enCurso = unCandidatoDetectado(campaniaId);

            gabinete.descartar(enGabinete, "ampliacion ya declarada en la DJ 2025", OBSERVACION);
            gabinete.admitir(enCampo, OBSERVACION);
            campo.descartar(enCampo, "es un toldo, no una edificacion", OBSERVACION);
            gabinete.admitir(verificado, OBSERVACION);
            campo.confirmar(verificado, AreaM2.de("180.00"), "luis.campo", OBSERVACION);

            TasaDeDescarte tasa =
                    enUnaTransaccion.execute(estado -> repositorio.tasaDeDescarte(campaniaId));

            assertThat(tasa.detectados()).isEqualTo(4);
            assertThat(tasa.descartadosEn(EtapaDeVerificacion.GABINETE)).isEqualTo(1);
            assertThat(tasa.descartadosEn(EtapaDeVerificacion.CAMPO)).isEqualTo(1);
            assertThat(tasa.verificados()).isEqualTo(1);
            assertThat(tasa.enCurso()).isEqualTo(1);
            assertThat(enCurso).isPositive();

            Candidato descartado =
                    enUnaTransaccion.execute(
                            estado -> repositorio.candidatoPorId(enGabinete).orElseThrow());
            assertThat(descartado.descarte()).isNotNull();
            assertThat(descartado.descarte().motivo())
                    .as(
                            "y la fila SE QUEDA, con su motivo: un descarte borrado es un modelo que"
                                    + " nadie puede medir")
                    .contains("ampliacion ya declarada");
        }
    }

    @Nested
    @DisplayName("El aislamiento lo sostiene RLS y no un WHERE")
    class ElAislamiento {

        @Test
        @DisplayName("la municipalidad vecina no ve el candidato de la otra, ni puede admitirlo")
        void elCandidatoDeLaOtraNoSeVe() {
            long candidatoId = unCandidatoDetectado("AISLADO-1");

            TenantContext.limpiar();
            TenantContext.fijar(new MunicipalidadId(municipalidadB));

            enUnaTransaccion.executeWithoutResult(
                    estado ->
                            assertThat(repositorio.candidatoPorId(candidatoId))
                                    .as(
                                            "no lo ve, y no porque ninguna consulta lo filtre: lo"
                                                    + " filtra la politica")
                                    .isEmpty());
            assertThatThrownBy(() -> gabinete.admitir(candidatoId, OBSERVACION))
                    .isInstanceOf(VerificarEnGabinete.CandidatoInexistente.class);
        }
    }

    // ── Siembra ────────────────────────────────────────────────────────

    private static long unaCampania(String codigo) {
        return enUnaTransaccion.execute(
                estado ->
                        repositorio
                                .guardar(
                                        Campania.nueva(
                                                codigo,
                                                "Campania " + codigo,
                                                java.time.LocalDate.now(RELOJ),
                                                Score.de("0.20"),
                                                500),
                                        OBSERVACION)
                                .id());
    }

    private long unCandidatoDetectado(String codigoDeCampania) {
        return unCandidatoDetectado(unaCampania(codigoDeCampania));
    }

    private long unCandidatoDetectado(long campaniaId) {
        return enUnaTransaccion.execute(
                estado ->
                        repositorio
                                .guardar(
                                        Candidato.detectado(
                                                campaniaId,
                                                predioDeA,
                                                ClaseDeHallazgo.SUBVALUADOR,
                                                OrigenDelCandidato.ORTOFOTO,
                                                Score.de("0.9100"),
                                                INSUMOS,
                                                null),
                                        OBSERVACION)
                                .id());
    }

    /**
     * Devuelve el candidato a DETECTADO por SQL, saltandose el dominio.
     *
     * <p>Es la unica forma de fabricar el estado que {@code LevantarActa} tiene que rechazar: por
     * el camino de la aplicacion no se puede llegar a el, que es justamente lo que esta prueba
     * mide.
     */
    private void devolverElCandidatoADetectado(long candidatoId) {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            kamayuk.catastro.esquema.ContextoDeTenant.fijar(app, municipalidadA);
            try (PreparedStatement sentencia =
                    app.prepareStatement(
                            "UPDATE candidato SET estado = 'DETECTADO' WHERE id = ?")) {
                sentencia.setLong(1, candidatoId);
                sentencia.executeUpdate();
            }
            app.commit();
        } catch (SQLException fallo) {
            throw new IllegalStateException("No se pudo preparar el estado de la prueba", fallo);
        }
    }

    /** Lee una celda por la conexion de administracion, que es la que ve la fila ya escrita. */
    private static String unTexto(String consulta) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                PreparedStatement sentencia = admin.prepareStatement(consulta);
                ResultSet fila = sentencia.executeQuery()) {
            if (!fila.next()) {
                throw new IllegalStateException(
                        "La consulta no devolvio ninguna fila: " + consulta);
            }
            return fila.getString(1);
        }
    }

    private static long unPredioDe(long municipalidadId) throws SQLException {
        return unaClaveDe(municipalidadId, "SELECT id FROM predio ORDER BY id LIMIT 1");
    }

    private static long unaFichaDe(long municipalidadId, long predioId) throws SQLException {
        return unaClaveDe(
                municipalidadId,
                "SELECT id FROM ficha_catastral WHERE predio_id = "
                        + predioId
                        + " AND tipo = 'UNICA' ORDER BY id LIMIT 1");
    }

    private static long unaClaveDe(long municipalidadId, String consulta) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            kamayuk.catastro.esquema.ContextoDeTenant.fijar(app, municipalidadId);
            try (Statement sentencia = app.createStatement();
                    ResultSet filas = sentencia.executeQuery(consulta)) {
                if (!filas.next()) {
                    throw new IllegalStateException(
                            "La siembra no dejo lo que esta prueba necesita");
                }
                return filas.getLong(1);
            }
        }
    }
}
