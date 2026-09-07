package kamayuk.catastro.fiscalizacion.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.fiscalizacion.aplicacion.AbrirCampania;
import kamayuk.catastro.fiscalizacion.aplicacion.DejarSinEfectoElHallazgo;
import kamayuk.catastro.fiscalizacion.aplicacion.DetectarSubvaluadores;
import kamayuk.catastro.fiscalizacion.aplicacion.LevantarActa;
import kamayuk.catastro.fiscalizacion.aplicacion.RegistrarEvidencia;
import kamayuk.catastro.fiscalizacion.aplicacion.VerificarEnCampo;
import kamayuk.catastro.fiscalizacion.aplicacion.VerificarEnGabinete;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.ClaseDeHallazgo;
import kamayuk.catastro.fiscalizacion.dominio.EstadoDeCampania;
import kamayuk.catastro.fiscalizacion.dominio.EstadoDelHallazgo;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import kamayuk.catastro.fiscalizacion.dominio.OrigenDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import kamayuk.catastro.nucleo.LectorDeFichas;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #23: <b>las salidas del ciclo</b> — dejar sin efecto un hallazgo y cerrar una campania.
 *
 * <p>{@code fiscalizacion} entrego el camino de ida entero y ninguna de sus salidas: {@code
 * Hallazgo.dejadoSinEfecto()} no lo llamaba nadie, {@code AbrirCampania.cerrar} no tenia endpoint,
 * y con ellos {@code EstadoDelHallazgo.DEJADO_SIN_EFECTO}, {@code EstadoDeCampania.CERRADA} y
 * {@code DetectarSubvaluadores.CampaniaCerradaParaDetectar} eran inalcanzables.
 *
 * <p>Va contra PostgreSQL de verdad y como {@code kamayuk_app} porque la mitad de lo que se mide la
 * sostiene el motor: los dos {@code CHECK} cruzados de {@code V12} —que atan el estado al acto en
 * los dos sentidos— no los puede comprobar ningun doble.
 */
@DisplayName("#23 — Las salidas del ciclo: dejar sin efecto un hallazgo y cerrar la campania")
class ElCicloDelHallazgoJdbcTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC);
    private static final Observacion OBSERVACION =
            Observacion.de("acta rectificada por la jefatura");
    private static final String INSUMOS = "{\"origen\":\"ORTOFOTO\",\"tesela\":\"z16/1/1\"}";

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static FiscalizacionRepositoryJdbc repositorio;
    private static AbrirCampania campanias;
    private static VerificarEnGabinete gabinete;
    private static VerificarEnCampo campo;
    private static LevantarActa actas;
    private static DejarSinEfectoElHallazgo anulaciones;
    private static DetectarSubvaluadores detector;
    private static TransactionTemplate enUnaTransaccion;
    private static long predio;
    private static long ficha;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "240313", "La que rectifica");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidad, parametroId, "CI");

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        JdbcClient jdbc = JdbcClient.create(pool);
        TenantTransactionManager gestor = new TenantTransactionManager(pool);
        repositorio = new FiscalizacionRepositoryJdbc(jdbc);
        AuditoriaJdbc auditoria = new AuditoriaJdbc(jdbc, RELOJ);

        campanias = envolver(new AbrirCampania(repositorio, auditoria, RELOJ), gestor);
        gabinete = envolver(new VerificarEnGabinete(repositorio, auditoria, RELOJ), gestor);
        campo =
                envolver(
                        new VerificarEnCampo(repositorio, LECTOR_DE_FICHAS, auditoria, RELOJ),
                        gestor);
        actas = envolver(new LevantarActa(repositorio, auditoria, RELOJ), gestor);
        anulaciones = envolver(new DejarSinEfectoElHallazgo(repositorio, auditoria, RELOJ), gestor);
        detector =
                envolver(
                        new DetectarSubvaluadores(repositorio, PADRON_VACIO, auditoria, RELOJ),
                        gestor);
        enUnaTransaccion = new TransactionTemplate(gestor);

        predio = unaClave("SELECT id FROM predio ORDER BY id LIMIT 1");
        ficha = unaClave("SELECT id FROM ficha_catastral WHERE tipo = 'UNICA' ORDER BY id LIMIT 1");
    }

    private static final LectorDeFichas LECTOR_DE_FICHAS =
            new LectorDeFichas() {
                @Override
                public Optional<Long> fichaVigenteEn(long predioId, LocalDate fecha) {
                    return predioId == predio ? Optional.of(ficha) : Optional.empty();
                }

                @Override
                public Optional<AreaM2> areaDeLaVersion(long fichaId) {
                    return fichaId == ficha ? Optional.of(AreaM2.de("120.00")) : Optional.empty();
                }
            };

    /**
     * Un padron que no encuentra nada, pero que SI puede mirar.
     *
     * <p>No lanza {@link AreasDelPadron.SinCartografia}: si lanzara, la prueba de la campania
     * cerrada pasaria por el motivo equivocado —la deteccion se pararia antes de mirar el estado— y
     * diria que la rama muerta esta viva cuando no lo estaria.
     */
    private static final AreasDelPadron PADRON_VACIO =
            new AreasDelPadron() {
                @Override
                public CruceDelPadron contrastar(Score umbral, int tope) {
                    return new CruceDelPadron(java.util.List.of(), new Cobertura(0, 0, 0, 0, 0, 0));
                }

                @Override
                public boolean estaEnElPadron(long predioId) {
                    return true;
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
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        OrigenContext.fijar(new Origen("ana.jefa", "PC-01", "10.0.0.1"));
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
        OrigenContext.limpiar();
    }

    @Test
    @DisplayName("dejar sin efecto un hallazgo escribe el ACTO: motivo, quien y cuando (AC-1)")
    void dejarSinEfectoEsUnActo() {
        long hallazgoId = unHallazgoFirme("CAM-A1");

        Hallazgo anulado =
                anulaciones.dejarSinEfecto(
                        hallazgoId, "el area medida era la del vecino", OBSERVACION);

        assertThat(anulado.estado()).isEqualTo(EstadoDelHallazgo.DEJADO_SIN_EFECTO);
        assertThat(anulado.anulacion()).isNotNull();
        assertThat(anulado.anulacion().quien())
                .as("quien lo decidio sale del contexto de origen, no de la peticion (ARQ-03)")
                .isEqualTo("ana.jefa");
        assertThat(unTexto("SELECT motivo_anulacion FROM hallazgo WHERE id = " + hallazgoId))
                .as("y llega A LA FILA, que es donde alguien la lee dos anos despues")
                .isEqualTo("el area medida era la del vecino");
        assertThat(unTexto("SELECT anulado_por FROM hallazgo WHERE id = " + hallazgoId))
                .isEqualTo("ana.jefa");
    }

    @Test
    @DisplayName("y no borra nada: el hallazgo y su acta se quedan donde estaban (regla 4)")
    void noBorraNada() {
        long hallazgoId = unHallazgoFirme("CAM-A2");
        long actaId =
                enUnaTransaccion
                        .execute(
                                estado ->
                                        actas.levantar(
                                                hallazgoId,
                                                "ACT-A2",
                                                "luis.campo",
                                                "Mayor area construida",
                                                OBSERVACION))
                        .id();

        anulaciones.dejarSinEfecto(hallazgoId, "el acta salio con el area del vecino", OBSERVACION);

        assertThat(unaCifra("SELECT count(*) FROM hallazgo WHERE id = " + hallazgoId))
                .as("la fila se queda: aqui no se borra (regla 4)")
                .isEqualTo(1);
        assertThat(unaCifra("SELECT count(*) FROM acta WHERE id = " + actaId))
                .as(
                        "y su acta tambien, porque es INMUTABLE: `V9` le revoca el UPDATE, asi que"
                                + " un acta equivocada no se edita ni se retira, se deja sin efecto"
                                + " el hallazgo que la sostiene")
                .isEqualTo(1);
        assertThat(unTexto("SELECT area_verificada::text FROM hallazgo WHERE id = " + hallazgoId))
                .as("y lo que el inspector verifico no se reescribe")
                .isEqualTo("180.00");
    }

    @Test
    @DisplayName("dos veces no: dejarlo sin efecto dos veces serian dos actos donde hubo uno")
    void noSeDejaSinEfectoDosVeces() {
        long hallazgoId = unHallazgoFirme("CAM-A3");
        anulaciones.dejarSinEfecto(hallazgoId, "primera y unica", OBSERVACION);

        assertThatThrownBy(() -> anulaciones.dejarSinEfecto(hallazgoId, "otra vez", OBSERVACION))
                .isInstanceOf(RegistrarEvidencia.HallazgoSinEfecto.class);
    }

    @Test
    @DisplayName("y el MOTOR lo sostiene: un DEJADO_SIN_EFECTO sin motivo no se puede escribir")
    void elCheckCruzadoMuerde() {
        long hallazgoId = unHallazgoFirme("CAM-A4");

        assertThatThrownBy(
                        () ->
                                ejecutar(
                                        "UPDATE hallazgo SET estado = 'DEJADO_SIN_EFECTO'"
                                                + " WHERE id = "
                                                + hallazgoId))
                .as(
                        "saltandose el dominio por SQL, que es la unica forma de fabricar el"
                                + " estado que `hallazgo_estado_anulacion_check` existe para"
                                + " impedir")
                .hasMessageContaining("hallazgo_estado_anulacion_check");

        assertThatThrownBy(
                        () ->
                                ejecutar(
                                        "UPDATE hallazgo SET motivo_anulacion = 'un motivo'"
                                                + " WHERE id = "
                                                + hallazgoId))
                .as("y por el otro lado: un FIRME con motivo de anulacion tampoco entra")
                .hasMessageContaining("hallazgo_");
    }

    @Test
    @DisplayName("cerrar la campania deja de admitir candidatos, y la deteccion lo dice (AC-6)")
    void laCampaniaSeCierraYLaDeteccionSePara() {
        Campania abierta =
                campanias.abrir("CAM-CIERRE", "Barrido", Score.de("0.20"), 500, OBSERVACION);

        Campania cerrada = campanias.cerrar(abierta.id(), OBSERVACION);

        assertThat(cerrada.estado()).isEqualTo(EstadoDeCampania.CERRADA);
        assertThat(cerrada.fin()).as("y con su fecha de cierre").isEqualTo(LocalDate.now(RELOJ));
        assertThatThrownBy(() -> detector.detectar(abierta.id(), OBSERVACION))
                .as(
                        "hasta #23 esta rama era MUERTA: sin endpoint de cierre, CERRADA era"
                                + " inalcanzable y una campania seguia admitiendo candidatos"
                                + " despues de que alguien hubiera citado su tasa de descarte")
                .isInstanceOf(DetectarSubvaluadores.CampaniaCerradaParaDetectar.class);
    }

    @Test
    @DisplayName("y cerrarla dos veces tampoco: serian dos actos donde hubo uno")
    void noSeCierraDosVeces() {
        Campania abierta =
                campanias.abrir("CAM-CIERRE-2", "Barrido", Score.de("0.20"), 500, OBSERVACION);
        campanias.cerrar(abierta.id(), OBSERVACION);

        assertThatThrownBy(() -> campanias.cerrar(abierta.id(), OBSERVACION))
                .isInstanceOf(AbrirCampania.CampaniaYaCerrada.class);
    }

    // ── Fixtures ───────────────────────────────────────────────────────

    /** El recorrido de ida entero, hasta un hallazgo firme. */
    private long unHallazgoFirme(String codigoDeCampania) {
        return enUnaTransaccion.execute(
                estado -> {
                    Campania campania =
                            repositorio.guardar(
                                    Campania.nueva(
                                            codigoDeCampania,
                                            "Barrido " + codigoDeCampania,
                                            LocalDate.now(RELOJ),
                                            Score.de("0.20"),
                                            500),
                                    OBSERVACION);
                    Candidato candidato =
                            repositorio.guardar(
                                    Candidato.detectado(
                                            campania.id(),
                                            predio,
                                            ClaseDeHallazgo.SUBVALUADOR,
                                            OrigenDelCandidato.ORTOFOTO,
                                            Score.de("0.9100"),
                                            INSUMOS,
                                            null),
                                    OBSERVACION);
                    gabinete.admitir(candidato.id(), OBSERVACION);
                    return campo.confirmar(
                                    candidato.id(), AreaM2.de("180.00"), "luis.campo", OBSERVACION)
                            .id();
                });
    }

    private void ejecutar(String sql) {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement()) {
                sentencia.executeUpdate(sql);
            }
            app.commit();
        } catch (SQLException fallo) {
            throw new IllegalStateException(fallo.getMessage(), fallo);
        }
    }

    private String unTexto(String consulta) {
        return String.valueOf(unaFila(consulta));
    }

    private long unaCifra(String consulta) {
        return ((Number) unaFila(consulta)).longValue();
    }

    private Object unaFila(String consulta) {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement();
                    ResultSet fila = sentencia.executeQuery(consulta)) {
                if (!fila.next()) {
                    throw new IllegalStateException("Sin fila: " + consulta);
                }
                return fila.getObject(1);
            }
        } catch (SQLException fallo) {
            throw new IllegalStateException(fallo.getMessage(), fallo);
        }
    }

    private static long unaClave(String consulta) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement();
                    ResultSet fila = sentencia.executeQuery(consulta)) {
                if (!fila.next()) {
                    throw new IllegalStateException("La siembra no dejo lo que hace falta");
                }
                return fila.getLong(1);
            }
        }
    }
}
