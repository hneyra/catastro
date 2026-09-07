package kamayuk.catastro.fiscalizacion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kamayuk.catastro.auditoria.AuditoriaJdbc;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.fiscalizacion.dominio.Acta;
import kamayuk.catastro.fiscalizacion.infraestructura.FiscalizacionRepositoryJdbc;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #20, AC-5 — levantar un acta llega hasta el {@code cast(... AS jsonb)} de la bitacora.
 *
 * <h2>Por que esto no se puede probar con un doble</h2>
 *
 * <p>Lo que fallaba no era el caso de uso: era la <b>columna</b>. {@code LevantarActa} componia su
 * asiento a mano —{@code "{\"numero\":\"" + numero + "\",\"inspector\":\"" + inspector + "\"}"}— y
 * lo que interpolaba no venia escapado, asi que un inspector llamado {@code Juan "El Tuerto" Perez}
 * producia texto que {@code auditoria.datos_nuevos} rechaza. Con la {@code Auditoria} de mentira
 * —la que solo recuerda— eso pasa en verde: nadie llega al {@code cast}.
 *
 * <p>Y el precio del defecto no era un mensaje feo: la auditoria se escribe <b>dentro de la
 * transaccion de la operacion auditada</b>, asi que el rechazo del motor deshacia el acta entera.
 * Un inspector con una comilla en el nombre no podia levantar ninguna, y el mensaje hablaba de JSON
 * y no de el.
 *
 * <p>La conexion es la de {@code kamayuk_app} y no la del superusuario, como en todas las pruebas
 * de persistencia: es lo unico que hace que la RLS y los privilegios signifiquen algo.
 */
@DisplayName("#20 AC-5 — Levantar un acta, contra PostgreSQL")
class LevantarActaJdbcTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC);

    /**
     * El nombre que rompia el asiento, y las cinco formas de un golpe.
     *
     * <p>La comilla y la barra las cubria el {@code escapar()} que otras clases tenian; el salto de
     * linea, el tabulador y el no-ASCII no los cubria <b>ninguno</b> de los dos, asi que el que «si
     * escapaba» rompia igual.
     */
    private static final String INSPECTOR_DIFICIL =
            "Juan \"El Tuerto\" Perez \\ Ñancahuazú\n\tturno 2º";

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static long hallazgoSinActa;
    private static LevantarActa levantarActa;
    private static TransactionTemplate enUnaTransaccion;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "240503", "La del acta");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidad, parametroId, "AC");

        // La fixture siembra un hallazgo firme Y su acta. El acta es unica por hallazgo, asi que
        // para levantar una hay que plantar un hallazgo que no la tenga: se clona el sembrado
        // sobre el mismo candidato ya VERIFICADO_EN_CAMPO, que es la compuerta que el caso de uso
        // comprueba.
        hallazgoSinActa = plantarUnHallazgoFirmeSinActa();

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        levantarActa =
                new LevantarActa(
                        new FiscalizacionRepositoryJdbc(JdbcClient.create(pool)),
                        // LA BITACORA DE VERDAD: es lo unico que hace que esta prueba mida algo.
                        new AuditoriaJdbc(JdbcClient.create(pool), RELOJ),
                        RELOJ);
        enUnaTransaccion = new TransactionTemplate(new TenantTransactionManager(pool));
    }

    @AfterAll
    static void cerrar() {
        if (base != null) {
            base.close();
        }
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
        OrigenContext.limpiar();
    }

    @Test
    @DisplayName(
            "un inspector con comilla, barra y salto de linea levanta su acta, y el asiento entra")
    void unInspectorConComillaLevantaSuActa() throws SQLException {
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        OrigenContext.fijar(new Origen("jperez", "PC-FISCA-01", "10.20.30.42"));

        Acta acta =
                enUnaTransaccion.execute(
                        estado ->
                                levantarActa.levantar(
                                        hallazgoSinActa,
                                        "ACT \"2026\"/001",
                                        INSPECTOR_DIFICIL,
                                        "Se constato exceso de area en el segundo piso",
                                        Observacion.de(
                                                "Acta levantada tras la verificacion en campo")));

        assertThat(acta.id()).as("el acta se guardo: la transaccion no se deshizo").isNotNull();
        assertThat(unTexto("SELECT datos_nuevos ->> 'inspector' FROM auditoria WHERE tabla='acta'"))
                .as(
                        "no basta con que la fila entre: el nombre tiene que salir IGUAL que"
                                + " entro, o el escape lo habria mutilado en silencio")
                .isEqualTo(INSPECTOR_DIFICIL);
        assertThat(unTexto("SELECT datos_nuevos ->> 'numero' FROM auditoria WHERE tabla='acta'"))
                .as("y el numero del papel tambien puede llevar comillas")
                .isEqualTo("ACT \"2026\"/001");
    }

    @Test
    @DisplayName("y el asiento es JSON de verdad: el motor lo indexa por sus campos")
    void elAsientoEsJsonDeVerdad() throws SQLException {
        // El contraste que impide que lo de arriba se cumpla solo. Si la columna guardara texto,
        // `->>` devolveria NULL sobre cualquier clave y las dos aserciones de arriba fallarian por
        // otro motivo; que `jsonb_typeof` diga «object» es lo que afirma que el cast se hizo.
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        OrigenContext.fijar(new Origen("jperez", "PC-FISCA-01", "10.20.30.42"));

        assertThat(unTexto("SELECT jsonb_typeof(datos_nuevos) FROM auditoria WHERE tabla='acta'"))
                .isEqualTo("object");
        assertThat(unTexto("SELECT datos_anteriores::text FROM auditoria WHERE tabla='acta'"))
                .as("un alta no tiene antes, y eso es NULL de columna y no la cadena «null»")
                .isNull();
    }

    // ── Fixtures ───────────────────────────────────────────────────────

    /**
     * Un candidato mas, verificado en campo, y su hallazgo firme SIN acta.
     *
     * <p>Hay que clonar el candidato y no solo el hallazgo: {@code hallazgo_candidato_uq} admite un
     * hallazgo por candidato —la primera version de esta fixture murio con «duplicate key value
     * violates unique constraint "hallazgo_candidato_uq"»—, y el acta es unica por hallazgo, asi
     * que para poder levantar una hace falta un hallazgo que no la tenga.
     *
     * <p>Se clona con {@code VERIFICADO_EN_CAMPO} porque esa es la compuerta que {@code
     * LevantarActa} comprueba de mas: la clave foranea dice que el candidato existe, no en que
     * estado esta.
     */
    private static long plantarUnHallazgoFirmeSinActa() throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            long candidatoId =
                    unaCifra(
                            app,
                            "INSERT INTO candidato (municipalidad_id, campania_id, predio_id,"
                                    + " clase, origen, score, insumos, geometria, estado, observacion,"
                                    + " usuario_registro)"
                                    + " SELECT municipalidad_id, campania_id, predio_id, clase, origen,"
                                    + " score, insumos, geometria, 'VERIFICADO_EN_CAMPO',"
                                    + " 'candidato del acta', usuario_registro"
                                    + " FROM candidato ORDER BY id LIMIT 1"
                                    + " RETURNING id");
            long hallazgoId =
                    unaCifra(
                            app,
                            "INSERT INTO hallazgo (municipalidad_id, candidato_id, clase,"
                                    + " predio_id, ficha_id, area_de_la_ficha, area_verificada,"
                                    + " inspector, verificado_en, observacion, usuario_registro)"
                                    + " SELECT municipalidad_id, "
                                    + candidatoId
                                    + ", clase, predio_id, ficha_id, area_de_la_ficha,"
                                    + " area_verificada, inspector, verificado_en,"
                                    + " 'hallazgo sin acta', usuario_registro"
                                    + " FROM hallazgo ORDER BY id LIMIT 1"
                                    + " RETURNING id");
            app.commit();
            return hallazgoId;
        }
    }

    private static long unaCifra(Connection conexion, String sql) throws SQLException {
        try (Statement sentencia = conexion.createStatement();
                ResultSet fila = sentencia.executeQuery(sql)) {
            assertThat(fila.next()).as("la fixture tiene que dejar una fila: %s", sql).isTrue();
            return fila.getLong(1);
        }
    }

    private static String unTexto(String consulta) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                Statement sentencia = admin.createStatement();
                ResultSet fila = sentencia.executeQuery(consulta)) {
            assertThat(fila.next()).as("tiene que haber un asiento que leer").isTrue();
            return fila.getString(1);
        }
    }
}
