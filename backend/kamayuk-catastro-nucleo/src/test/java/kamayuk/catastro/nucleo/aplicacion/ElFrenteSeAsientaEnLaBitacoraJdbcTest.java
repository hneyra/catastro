package kamayuk.catastro.nucleo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import kamayuk.catastro.auditoria.AuditoriaJdbc;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import kamayuk.catastro.nucleo.infraestructura.FrentesDelPredioJdbc;
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
 * AC 5 de #20: los dos casos de uso del frente, <b>hasta el {@code cast(… AS jsonb)}</b>.
 *
 * <h2>Por que hacia falta esta prueba y no bastaban las que ya habia</h2>
 *
 * <p>Porque ninguna pasaba por ese {@code cast}. {@code DerivacionDeFrentesJdbcTest} ejerce el
 * <b>repositorio</b> —{@code cortarContraLasVias}, {@code proponer}, {@code confirmar}— y nunca el
 * caso de uso; {@code DerivacionDeLosFrentesTest} ejerce el caso de uso con una {@code Auditoria}
 * de mentira, que solo recuerda lo que le pasan. Entre las dos, la bitacora de los frentes se
 * escribia <b>solo en produccion</b>: {@code POST /frentes/{id}/confirmacion} devolvia 500 y el
 * derivador del perfil {@code batch} moria en el primer frente que conseguia proponer, con el
 * defecto en verde desde #7.
 *
 * <p>Aqui la {@code Auditoria} es la de verdad —{@code AuditoriaJdbc}— contra PostgreSQL real, y
 * los casos de uso van envueltos en un proxy transaccional de verdad, para que lo que se verifique
 * sea la anotacion y no un {@code TransactionTemplate} escrito por la propia prueba.
 *
 * <h2>El usuario del origen lleva una comilla, y no es una gracia</h2>
 *
 * <p>{@code confirmado_por} sale de {@code OrigenContext}, o sea del token, y de ahi pasa al JSON
 * del asiento. Un usuario llamado {@code Juan "El Tuerto" Perez} es lo que rompe el {@code cast}
 * cuando el JSON se compone concatenando —y lo rompe con la transaccion entera revertida y un
 * mensaje que habla de JSON y no de el—. Con el serializador, entra tal cual y se puede volver a
 * leer campo a campo.
 */
@DisplayName("#20 AC 5 — El frente llega a la bitacora, contra PostgreSQL")
class ElFrenteSeAsientaEnLaBitacoraJdbcTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC);

    private static final Medida OCHO_METROS = Medida.enMetrosLineales("8.00");

    private static final Observacion PORQUE =
            Observacion.de("Derivacion de frentes de la prueba de #20");

    /** Lo que rompia el {@code cast}: una comilla dentro de un nombre que teclea una persona. */
    private static final String INSPECTOR_CON_COMILLA = "Juan \"El Tuerto\" Perez";

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static FrentesDelPredioJdbc frentes;
    private static ProponerLosFrentesDeUnPredio derivador;
    private static ConfirmarElFrente confirmador;
    private static TransactionTemplate enUnaTransaccion;
    private static long predioId;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "240701", "La de la bitacora");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidad, parametroId, "BI");

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        JdbcClient jdbc = JdbcClient.create(pool);
        TenantTransactionManager gestor = new TenantTransactionManager(pool);
        frentes = new FrentesDelPredioJdbc(jdbc);
        AuditoriaJdbc auditoria = new AuditoriaJdbc(jdbc, RELOJ);

        derivador = envolver(new ProponerLosFrentesDeUnPredio(frentes, auditoria, RELOJ), gestor);
        confirmador = envolver(new ConfirmarElFrente(frentes, auditoria, RELOJ), gestor);
        enUnaTransaccion = new TransactionTemplate(gestor);

        predioId = unaCifraDe("SELECT id FROM predio ORDER BY id LIMIT 1");
    }

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

    /** El lote con su poligono y dos vias con su eje; el escenario de {@code #7}, en corto. */
    @BeforeEach
    void elEscenario() throws SQLException {
        limpiarComoAdmin("DELETE FROM auditoria");
        limpiarComoAdmin("DELETE FROM frente_predio");
        limpiarComoAdmin("DELETE FROM frente_derivacion");
        ejecutar("UPDATE via SET eje = NULL");
        ejecutar(
                "UPDATE predio SET geometria = ST_Multi(ST_Transform("
                        + "  ST_SetSRID(ST_MakeBox2D(ST_Point(534000, 9458000),"
                        + "                          ST_Point(534200, 9458200)), 32717),"
                        + "  4326))::geography WHERE id = "
                        + predioId);
        ejeDeLaVia("V-GRAU", "LINESTRING(533900 9458203, 534300 9458203)");
        ejeDeLaVia("V-SUR", "LINESTRING(533900 9458000, 534300 9458000)");

        TenantContext.fijar(new MunicipalidadId(municipalidad));
        OrigenContext.fijar(Origen.deProceso("derivacion-de-frentes"));
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
        OrigenContext.limpiar();
    }

    @Test
    @DisplayName("el derivador propone y la bitacora lo guarda: no muere en el primer frente")
    void elDerivadorEscribeSuAsiento() throws SQLException {
        int escritos = derivador.proponer(predioId, OCHO_METROS, PORQUE);

        assertThat(escritos).as("las dos vias bordean el lote").isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM auditoria WHERE tabla = 'frente_predio'"))
                .as(
                        "un asiento por frente. Antes de #20 el primero moria con «invalid input"
                                + " syntax for type json» y se llevaba la transaccion, asi que la"
                                + " corrida del perfil batch no pasaba del primer predio con frente")
                .isEqualTo(2);
        assertThat(
                        textos(
                                "SELECT datos_nuevos ->> 'estado' FROM auditoria"
                                        + " WHERE tabla = 'frente_predio'"))
                .as(
                        "y se puede consultar POR CAMPO, que es lo que una columna jsonb compra y"
                                + " una prosa dentro de un varchar no")
                .containsOnly("PROPUESTA");
        assertThat(
                        textos(
                                "SELECT jsonb_typeof(datos_nuevos) FROM auditoria"
                                        + " WHERE tabla = 'frente_predio'"))
                .as("lo que se asienta es un OBJETO, no un escalar")
                .containsOnly("object");
    }

    @Test
    @DisplayName("confirmar asienta el antes y el despues, con el nombre entrecomillado dentro")
    void confirmarAsientaElAntesYElDespues() throws SQLException {
        derivador.proponer(predioId, OCHO_METROS, PORQUE);
        long frenteId = unFrente().id();

        OrigenContext.limpiar();
        OrigenContext.fijar(new Origen(INSPECTOR_CON_COMILLA, "PC-CAMPO-02", "10.0.0.9"));

        FrenteDelPredio confirmado =
                confirmador.confirmar(
                        frenteId,
                        Medida.enMetrosLineales("111.00"),
                        Observacion.de("Medido en campo con cinta, 2026-09-06"));

        assertThat(confirmado.confirmadoPor()).isEqualTo(INSPECTOR_CON_COMILLA);
        assertThat(
                        textos(
                                "SELECT datos_anteriores ->> 'estado' FROM auditoria"
                                        + " WHERE operacion = 'MODIFICACION'"))
                .as("el antes: la longitud que corto una maquina")
                .containsExactly("PROPUESTA");
        assertThat(
                        textos(
                                "SELECT datos_nuevos ->> 'confirmadoPor' FROM auditoria"
                                        + " WHERE operacion = 'MODIFICACION'"))
                .as(
                        "el nombre entra TAL CUAL, con sus comillas: escapar lo hace el"
                                + " serializador, no un escapar() que hay que acordarse de llamar")
                .containsExactly(INSPECTOR_CON_COMILLA);
        assertThat(
                        textos(
                                "SELECT datos_nuevos ->> 'longitud' FROM auditoria"
                                        + " WHERE operacion = 'MODIFICACION'"))
                .as("una Medida lleva la unidad dentro, porque ahi la unidad ES parte del dato")
                .containsExactly("111.00 ML");
    }

    // ── Fixtures ───────────────────────────────────────────────────────

    private FrenteDelPredio unFrente() {
        List<FrenteDelPredio> guardados =
                enUnaTransaccion.execute(estado -> frentes.deUnPredio(predioId));
        assertThat(guardados).as("la fixture tiene que dejar frentes que confirmar").isNotEmpty();
        return guardados.get(0);
    }

    private static void ejeDeLaVia(String codigo, String wktEnUtm) throws SQLException {
        ejecutar(
                "UPDATE via SET eje = ST_Transform(ST_GeomFromText('"
                        + wktEnUtm
                        + "', 32717), 4326)::geography WHERE codigo = '"
                        + codigo
                        + "'");
        ejecutar(
                "INSERT INTO via (municipalidad_id, codigo, tipo_via, nombre, eje)"
                        + " SELECT current_setting('app.municipalidad_id')::bigint, '"
                        + codigo
                        + "', 'AVENIDA', 'Via "
                        + codigo
                        + "', ST_Transform(ST_GeomFromText('"
                        + wktEnUtm
                        + "', 32717), 4326)::geography"
                        + " WHERE NOT EXISTS (SELECT 1 FROM via WHERE codigo = '"
                        + codigo
                        + "')");
    }

    /** Lo que la aplicacion no puede hacer y la fixture si: borrar para dejar el escenario. */
    private static void limpiarComoAdmin(String sql) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                Statement sentencia = admin.createStatement()) {
            sentencia.executeUpdate(sql);
        }
    }

    private static void ejecutar(String sql) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement()) {
                sentencia.executeUpdate(sql);
            }
            app.commit();
        }
    }

    private static long contar(String consulta) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                PreparedStatement sentencia = admin.prepareStatement(consulta);
                ResultSet fila = sentencia.executeQuery()) {
            fila.next();
            return fila.getLong(1);
        }
    }

    private static List<String> textos(String consulta) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                PreparedStatement sentencia = admin.prepareStatement(consulta);
                ResultSet filas = sentencia.executeQuery()) {
            List<String> valores = new java.util.ArrayList<>();
            while (filas.next()) {
                valores.add(filas.getString(1));
            }
            return valores;
        }
    }

    private static long unaCifraDe(String consulta) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement();
                    ResultSet fila = sentencia.executeQuery(consulta)) {
                if (!fila.next()) {
                    throw new IllegalStateException("La fixture no dejo lo que la prueba necesita");
                }
                return fila.getLong(1);
            }
        }
    }
}
