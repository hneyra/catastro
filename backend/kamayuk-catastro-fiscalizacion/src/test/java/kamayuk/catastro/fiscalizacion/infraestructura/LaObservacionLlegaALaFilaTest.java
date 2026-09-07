package kamayuk.catastro.fiscalizacion.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.fiscalizacion.dominio.Acta;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.ClaseDeHallazgo;
import kamayuk.catastro.fiscalizacion.dominio.EstadoDeCampania;
import kamayuk.catastro.fiscalizacion.dominio.Evidencia;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import kamayuk.catastro.fiscalizacion.dominio.HuellaDeEvidencia;
import kamayuk.catastro.fiscalizacion.dominio.OrigenDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import kamayuk.catastro.fiscalizacion.dominio.TipoDeEvidencia;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AC-2 de #24: <b>la observacion del usuario llega a la fila</b>, en las cinco tablas.
 *
 * <h2>Por que el tipo en la firma no basta, y esta prueba si</h2>
 *
 * <p>{@code FiscalizacionRepository} recibe una {@link Observacion} desde #24, y eso impide que un
 * llamador pase {@code ""} —el tipo no se puede construir vacio—. Lo que <b>no</b> impide es que el
 * repositorio la reciba y escriba otra cosa: hasta #24 las cinco columnas guardaban una constante
 * del codigo («alta de campania», «deteccion», «verificacion en campo», «evidencia del hallazgo»,
 * «acta del hallazgo») y ninguna prueba lo veia, porque ninguna leia la columna.
 *
 * <p>Asi que esto escribe por el puerto y <b>lee de vuelta la columna</b> con una consulta aparte.
 * Una por tabla y no una sola: con una sola, cuatro literales seguirian pasando en verde.
 *
 * <h2>Contra PostgreSQL de verdad y como {@code kamayuk_app}</h2>
 *
 * <p>La columna es {@code observacion character varying(500) NOT NULL} en las cinco tablas de
 * {@code V9}, y quien decide que fila se ve es la politica RLS. Un doble no tiene ninguna de las
 * dos cosas, y lo que aqui se mide es exactamente lo que la fila acaba guardando.
 */
@DisplayName("#24 AC-2 — la observacion del usuario llega a la fila, y no un literal del codigo")
class LaObservacionLlegaALaFilaTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-07T10:00:00Z"), ZoneOffset.UTC);

    /**
     * Cinco textos distintos, uno por tabla, y ninguno parecido a la constante que habia.
     *
     * <p>Distintos a proposito: si las cinco escrituras compartieran el texto, un repositorio que
     * guardara el de la primera en todas pasaria en verde.
     */
    private static final Observacion DE_LA_CAMPANIA =
            Observacion.de("apertura por acuerdo de concejo 014-2026");

    private static final Observacion DEL_CANDIDATO =
            Observacion.de("corrida del 7 de setiembre sobre el sector 04");
    private static final Observacion DEL_HALLAZGO =
            Observacion.de("brigada 3 midio el frente y el fondo con cinta");
    private static final Observacion DE_LA_EVIDENCIA =
            Observacion.de("fotografia del lindero norte, tomada al ingresar");
    private static final Observacion DEL_ACTA =
            Observacion.de("acta entregada en el domicilio fiscal, con cargo");
    private static final Observacion DEL_CIERRE =
            Observacion.de("cierre para poder citar la tasa de descarte");
    private static final Observacion DE_LA_ANULACION =
            Observacion.de("se dejo sin efecto: el area medida era la del vecino");

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static FiscalizacionRepositoryJdbc repositorio;
    private static TransactionTemplate enUnaTransaccion;
    private static long predio;
    private static long ficha;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "240311", "La que anota por que");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidad, parametroId, "OB");

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        repositorio = new FiscalizacionRepositoryJdbc(JdbcClient.create(pool));
        enUnaTransaccion = new TransactionTemplate(new TenantTransactionManager(pool));

        predio = unaClave("SELECT id FROM predio ORDER BY id LIMIT 1");
        ficha = unaClave("SELECT id FROM ficha_catastral WHERE tipo = 'UNICA' ORDER BY id LIMIT 1");
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
        kamayuk.catastro.auditoria.OrigenContext.fijar(
                new kamayuk.catastro.auditoria.Origen("ana.gabinete", "PC-07", "10.0.0.7"));
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
        kamayuk.catastro.auditoria.OrigenContext.limpiar();
    }

    @Test
    @DisplayName("las cinco tablas guardan la observacion que se paso, y no un literal")
    void lasCincoTablasGuardanLaObservacionDelUsuario() {
        Recorrido recorrido = enUnaTransaccion.execute(estado -> sembrarElRecorrido());

        assertThat(observacionDe("campania", recorrido.campaniaId()))
                .isEqualTo(DE_LA_CAMPANIA.texto());
        assertThat(observacionDe("candidato", recorrido.candidatoId()))
                .isEqualTo(DEL_CANDIDATO.texto());
        assertThat(observacionDe("hallazgo", recorrido.hallazgoId()))
                .isEqualTo(DEL_HALLAZGO.texto());
        assertThat(observacionDe("evidencia", recorrido.evidenciaId()))
                .isEqualTo(DE_LA_EVIDENCIA.texto());
        assertThat(observacionDe("acta", recorrido.actaId())).isEqualTo(DEL_ACTA.texto());
    }

    @Test
    @DisplayName("y los DOS `UPDATE` tambien: cerrar y anular son modificaciones (regla 10)")
    void losDosUpdateTambienLaEscriben() {
        Recorrido recorrido = enUnaTransaccion.execute(estado -> sembrarElRecorrido());

        enUnaTransaccion.executeWithoutResult(
                estado -> {
                    Campania abierta =
                            repositorio.campaniaPorId(recorrido.campaniaId()).orElseThrow();
                    repositorio.guardar(
                            new Campania(
                                    abierta.id(),
                                    abierta.codigo(),
                                    abierta.nombre(),
                                    EstadoDeCampania.CERRADA,
                                    abierta.inicio(),
                                    abierta.inicio(),
                                    abierta.umbral(),
                                    abierta.tope()),
                            DEL_CIERRE);
                    Hallazgo firme =
                            repositorio.hallazgoPorId(recorrido.hallazgoId()).orElseThrow();
                    repositorio.guardar(
                            firme.dejadoSinEfecto(
                                    "el area medida era la del vecino",
                                    "ana.gabinete",
                                    RELOJ.instant()),
                            DE_LA_ANULACION);
                });

        assertThat(observacionDe("campania", recorrido.campaniaId())).isEqualTo(DEL_CIERRE.texto());
        assertThat(observacionDe("hallazgo", recorrido.hallazgoId()))
                .isEqualTo(DE_LA_ANULACION.texto());
    }

    /** El recorrido entero por el puerto: campania, candidato, hallazgo, evidencia y acta. */
    private Recorrido sembrarElRecorrido() {
        // Un sufijo por recorrido: `evidencia_sha256_uq` y `acta_numero_uq` son POR
        // municipalidad, asi que dos recorridos con la misma huella chocarian entre pruebas —y
        // chocaron: «duplicate key value violates unique constraint "evidencia_sha256_uq"».
        String sufijo = String.valueOf(System.nanoTime() % 100000);
        String huella = String.format("%064x", System.nanoTime());
        Campania campania =
                repositorio.guardar(
                        Campania.nueva(
                                "CAM-" + sufijo,
                                "Barrido de prueba",
                                LocalDate.now(RELOJ),
                                Score.de("0.20"),
                                500),
                        DE_LA_CAMPANIA);
        Candidato candidato =
                repositorio.guardar(
                        Candidato.detectado(
                                campania.id(),
                                predio,
                                ClaseDeHallazgo.SUBVALUADOR,
                                OrigenDelCandidato.ORTOFOTO,
                                Score.de("0.9100"),
                                "{\"origen\":\"ORTOFOTO\"}",
                                null),
                        DEL_CANDIDATO);
        Hallazgo hallazgo =
                repositorio.guardar(
                        Hallazgo.deSubvaluador(
                                candidato.id(),
                                predio,
                                ficha,
                                AreaM2.de("120.00"),
                                AreaM2.de("180.00"),
                                "brigada.tres",
                                LocalDate.now(RELOJ)),
                        DEL_HALLAZGO);
        Evidencia evidencia =
                repositorio.guardar(
                        new Evidencia(
                                null,
                                hallazgo.id(),
                                TipoDeEvidencia.FOTO,
                                HuellaDeEvidencia.de(huella),
                                "s3://evidencias/" + sufijo + ".jpg",
                                RELOJ.instant(),
                                RELOJ.instant(),
                                "tableta-01"),
                        DE_LA_EVIDENCIA);
        Acta acta =
                repositorio.guardar(
                        Acta.nueva(
                                "ACT-" + sufijo,
                                hallazgo.id(),
                                LocalDate.now(RELOJ),
                                "brigada.tres",
                                "Se hallo mayor area construida"),
                        DEL_ACTA);
        return new Recorrido(
                campania.id(), candidato.id(), hallazgo.id(), evidencia.id(), acta.id());
    }

    private record Recorrido(
            long campaniaId, long candidatoId, long hallazgoId, long evidenciaId, long actaId) {}

    /**
     * Lee la columna con una conexion aparte, fuera del puerto que la escribio.
     *
     * <p>Preguntarsela al propio repositorio no serviria: ninguno de sus mapeadores lee {@code
     * observacion}, asi que el valor volveria de un objeto de Java y no de la fila.
     */
    private String observacionDe(String tabla, long id) {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            kamayuk.catastro.esquema.ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement();
                    ResultSet filas =
                            sentencia.executeQuery(
                                    "SELECT observacion FROM " + tabla + " WHERE id = " + id)) {
                if (!filas.next()) {
                    throw new IllegalStateException(
                            "No hay fila " + id + " en «" + tabla + "»: la escritura no ocurrio");
                }
                return filas.getString(1);
            }
        } catch (SQLException fallo) {
            throw new IllegalStateException("No se pudo leer «" + tabla + "»", fallo);
        }
    }

    private static long unaClave(String consulta) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            kamayuk.catastro.esquema.ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement();
                    ResultSet filas = sentencia.executeQuery(consulta)) {
                if (!filas.next()) {
                    throw new IllegalStateException("La siembra no dejo lo que hace falta");
                }
                return filas.getLong(1);
            }
        }
    }
}
