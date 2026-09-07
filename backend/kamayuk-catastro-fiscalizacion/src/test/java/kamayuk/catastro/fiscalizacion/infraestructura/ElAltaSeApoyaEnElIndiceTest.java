package kamayuk.catastro.fiscalizacion.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import kamayuk.catastro.auditoria.AuditoriaJdbc;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.compartido.Pagina;
import kamayuk.catastro.compartido.Paginacion;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.fiscalizacion.aplicacion.AbrirCampania;
import kamayuk.catastro.fiscalizacion.dominio.Acta;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.CandidatoEnLaCola;
import kamayuk.catastro.fiscalizacion.dominio.CriterioDeCandidatos;
import kamayuk.catastro.fiscalizacion.dominio.Evidencia;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import kamayuk.catastro.fiscalizacion.dominio.HallazgoDelPredio;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import kamayuk.catastro.fiscalizacion.dominio.TasaDeDescarte;
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

/**
 * AC-3 de #24: <b>el alta de campania se apoya en {@code campania_codigo_uq}, no en un {@code
 * if}</b>.
 *
 * <h2>Como se provoca la carrera sin dos hilos, y por que vale</h2>
 *
 * <p>El defecto que AC-3 describe necesita que dos peticiones lean las dos «no esta» y las dos
 * inserten. Con dos hilos eso depende del planificador del sistema operativo y la prueba seria
 * intermitente —y una prueba intermitente en el camino que decide si algo es un 409 o un 500 no
 * sirve—.
 *
 * <p>Aqui la ventana se abre <b>a proposito</b>: un decorador del repositorio planta la fila rival,
 * desde OTRA conexion y con su propio {@code COMMIT}, justo cuando {@code AbrirCampania} pregunta
 * si el codigo existe — y contesta que no. Es exactamente lo que la segunda peticion ve en una
 * carrera de verdad, y el {@code INSERT} que viene despues choca contra el indice unico de
 * PostgreSQL, que es lo unico que se esta midiendo. El decorador no simula el indice: solo decide
 * CUANDO aparece la fila.
 *
 * <p>Sin la captura de {@code DuplicateKeyException}, esa peticion contesta un 500. Con ella,
 * {@code CampaniaYaAbierta}, que es lo mismo que contesta el atajo del caso corriente — y por eso
 * las dos direcciones se miden: el atajo y el indice tienen que dar la MISMA respuesta.
 */
@DisplayName("#24 AC-3 — la garantia del alta es el indice unico, y no el `if`")
class ElAltaSeApoyaEnElIndiceTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-07T10:00:00Z"), ZoneOffset.UTC);
    private static final Observacion OBSERVACION =
            Observacion.de("apertura de la campania de prueba");

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static FiscalizacionRepositoryJdbc repositorio;
    private static TenantTransactionManager gestor;
    private static AuditoriaJdbc auditoria;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "240312", "La del indice unico");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidad, parametroId, "IU");

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        JdbcClient jdbc = JdbcClient.create(pool);
        gestor = new TenantTransactionManager(pool);
        repositorio = new FiscalizacionRepositoryJdbc(jdbc);
        auditoria = new AuditoriaJdbc(jdbc, RELOJ);
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
        OrigenContext.fijar(new Origen("ana.gabinete", "PC-07", "10.0.0.7"));
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
        OrigenContext.limpiar();
    }

    @Test
    @DisplayName(
            "el codigo que aparece DESPUES del `if` lo para el indice, y sale CampaniaYaAbierta")
    void elIndiceParaLoQueElIfNoVio() {
        String codigo = "CAM-CARRERA";
        AbrirCampania campanias =
                envolver(
                        new AbrirCampania(
                                new PlantaLaRivalAlPreguntar(repositorio, codigo),
                                auditoria,
                                RELOJ));

        assertThatThrownBy(
                        () ->
                                campanias.abrir(
                                        codigo,
                                        "Barrido en carrera",
                                        Score.de("0.20"),
                                        500,
                                        OBSERVACION))
                .isInstanceOf(AbrirCampania.CampaniaYaAbierta.class)
                .hasMessageContaining(codigo);
    }

    @Test
    @DisplayName(
            "y el atajo del caso corriente contesta LO MISMO: la respuesta no depende de quien la para")
    void elAtajoContestaLoMismo() {
        String codigo = "CAM-ATAJO";
        AbrirCampania campanias = envolver(new AbrirCampania(repositorio, auditoria, RELOJ));

        Campania primera = campanias.abrir(codigo, "Barrido", Score.de("0.20"), 500, OBSERVACION);
        assertThat(primera.id()).isNotNull();

        assertThatThrownBy(
                        () ->
                                campanias.abrir(
                                        codigo, "Otro barrido", Score.de("0.20"), 500, OBSERVACION))
                .isInstanceOf(AbrirCampania.CampaniaYaAbierta.class)
                .hasMessageContaining(codigo);
    }

    @SuppressWarnings("unchecked")
    private static <T> T envolver(T objetivo) {
        ProxyFactory fabrica = new ProxyFactory(objetivo);
        fabrica.setProxyTargetClass(true);
        fabrica.addAdvice(
                new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        return (T) fabrica.getProxy();
    }

    /**
     * El repositorio de verdad, con la ventana de la carrera abierta a proposito.
     *
     * <p>Al preguntarle por el codigo planta la fila rival desde OTRA conexion, con su propio
     * {@code COMMIT}, y contesta que no existe. Todo lo demas —el {@code INSERT} y su choque contra
     * {@code campania_codigo_uq}— lo hace el repositorio real contra PostgreSQL real.
     */
    private static final class PlantaLaRivalAlPreguntar implements FiscalizacionRepository {

        private final FiscalizacionRepository real;
        private final String codigoRival;

        PlantaLaRivalAlPreguntar(FiscalizacionRepository real, String codigoRival) {
            this.real = real;
            this.codigoRival = codigoRival;
        }

        @Override
        public Optional<Campania> campaniaPorCodigo(String codigo) {
            if (codigo.equals(codigoRival)) {
                plantar(codigo);
            }
            return Optional.empty();
        }

        private void plantar(String codigo) {
            try (Connection otra = base.conexion(BaseDeDatosDePrueba.APP)) {
                kamayuk.catastro.esquema.ContextoDeTenant.fijar(otra, municipalidad);
                try (PreparedStatement sentencia =
                        otra.prepareStatement(
                                "INSERT INTO campania (municipalidad_id, codigo, nombre, estado,"
                                        + " inicio, umbral, tope, observacion, usuario_registro)"
                                        + " VALUES (?, ?, 'La que llego primero', 'ABIERTA',"
                                        + " DATE '2026-09-07', 0.2000, 500, 'la otra peticion',"
                                        + " 'otra.sesion')")) {
                    sentencia.setLong(1, municipalidad);
                    sentencia.setString(2, codigo);
                    sentencia.executeUpdate();
                }
                otra.commit();
            } catch (SQLException fallo) {
                throw new IllegalStateException("No se pudo plantar la campania rival", fallo);
            }
        }

        @Override
        public Campania guardar(Campania campania, Observacion observacion) {
            return real.guardar(campania, observacion);
        }

        @Override
        public Optional<Campania> campaniaPorId(long id) {
            return real.campaniaPorId(id);
        }

        @Override
        public Candidato guardar(Candidato candidato, Observacion observacion) {
            return real.guardar(candidato, observacion);
        }

        @Override
        public Optional<Candidato> candidatoPorId(long id) {
            return real.candidatoPorId(id);
        }

        @Override
        public Pagina<CandidatoEnLaCola> candidatos(
                CriterioDeCandidatos criterio, Paginacion paginacion) {
            return real.candidatos(criterio, paginacion);
        }

        @Override
        public TasaDeDescarte tasaDeDescarte(long campaniaId) {
            return real.tasaDeDescarte(campaniaId);
        }

        @Override
        public Hallazgo guardar(Hallazgo hallazgo, Observacion observacion) {
            return real.guardar(hallazgo, observacion);
        }

        @Override
        public Optional<Hallazgo> hallazgoPorId(long id) {
            return real.hallazgoPorId(id);
        }

        @Override
        public Optional<Hallazgo> hallazgoDelCandidato(long candidatoId) {
            return real.hallazgoDelCandidato(candidatoId);
        }

        @Override
        public Pagina<Hallazgo> hallazgos(long campaniaId, Paginacion paginacion) {
            return real.hallazgos(campaniaId, paginacion);
        }

        @Override
        public List<HallazgoDelPredio> hallazgosDelPredio(long predioId) {
            return real.hallazgosDelPredio(predioId);
        }

        @Override
        public Evidencia guardar(Evidencia evidencia, Observacion observacion) {
            return real.guardar(evidencia, observacion);
        }

        @Override
        public List<Evidencia> evidenciasDe(long hallazgoId) {
            return real.evidenciasDe(hallazgoId);
        }

        @Override
        public Acta guardar(Acta acta, Observacion observacion) {
            return real.guardar(acta, observacion);
        }

        @Override
        public Optional<Acta> actaDelHallazgo(long hallazgoId) {
            return real.actaDelHallazgo(hallazgoId);
        }
    }
}
