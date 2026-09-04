package kamayuk.catastro.esquema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P5C — La guarda del arancel, reconstruida contra la copia local de conjuntos sellados (`V3`).
 *
 * <h2>Que se esta comprobando, y por que aqui y no en Java</h2>
 *
 * <p>El monolito tenia en `V18` un disparador que rechazaba escribir un arancel cuyo conjunto de
 * parametros ya estuviera SELLADO. Ese disparador consultaba `conjunto_parametros`, que se fue a
 * {@code normativa} en P5B; `V2` de {@code rentas} lo retiro y dejo escrito el hueco: «hoy nada
 * impide cargar un arancel contra un conjunto ya sellado».
 *
 * <p>Se escribe <b>por SQL directo</b> y no por el caso de uso, y eso no es comodidad: es lo que
 * #188 midio y #435 y #542 confirmaron —quitar una guarda de Java deja las pruebas en verde cuando
 * quien rechaza de verdad es la base—. {@code ValuacionRepository#guardarArancel} documenta a
 * proposito que «inserta sin comprobar el estado del conjunto: esa comprobacion no vive en Java»,
 * asi que una prueba que pasara por el caso de uso no distinguiria las dos cosas.
 *
 * <h2>Como se demostro que muerde</h2>
 *
 * <p>Antes de escribir esta clase, la guarda ya se habia cobrado una pieza: al reconstruirla,
 * {@code DatosDePrueba} —que sembraba la cache del conjunto ANTES del arancel— empezo a fallar con
 * «El conjunto de parametros 1 esta sellado». Tenia razon, y el arreglo fue poner la siembra en el
 * orden real: primero el arancel contra un conjunto abierto, y despues la descarga del conjunto ya
 * sellado. La mutacion, ademas, esta medida en `P5C-extraccion.md`: quitando `V3` entero, la
 * primera prueba de aqui pasa a insertar sin ruido.
 */
@DisplayName("P5C — El arancel de un conjunto sellado no se escribe")
class GuardaDelArancelTest {

    /** El sellado es un hecho del conjunto, y `restrict_violation` es como lo dice PostgreSQL. */
    private static final String RESTRICCION_VIOLADA = "23001";

    private static final AtomicInteger CORRELATIVO = new AtomicInteger();

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static long viaId;
    private static long conjuntoSellado;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad =
                DatosDePrueba.crearMunicipalidad(base, "200701", "Municipalidad del arancel");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidad, parametroId, "AR");

        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            viaId = primerId(app, "via");
            conjuntoSellado = conjuntoEnLaCache(app);
            app.rollback();
        }
    }

    @AfterAll
    static void cerrar() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    @DisplayName("contra un conjunto que ESTA en la cache local, el INSERT se rechaza")
    void contraUnConjuntoSelladoNoSeInserta() {
        assertThatThrownBy(
                        () ->
                                insertarArancel(
                                        conjuntoSellado, "TRAMO-" + CORRELATIVO.incrementAndGet()))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("esta sellado")
                .hasMessageContaining("publicar otra version del conjunto")
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo(RESTRICCION_VIOLADA);
    }

    @Test
    @DisplayName("y el UPDATE tambien: corregir un arancel usado es publicar otra version")
    void contraUnConjuntoSelladoTampocoSeActualiza() throws SQLException {
        // Se escribe primero contra un conjunto abierto y DESPUES se intenta moverlo al sellado,
        // que es la forma en que un `UPDATE` puede acabar tocando una version ya usada.
        long abierto = conjuntoSellado + 1_000;
        String tramo = "TRAMO-" + CORRELATIVO.incrementAndGet();
        insertarArancel(abierto, tramo);

        assertThatThrownBy(
                        () -> {
                            try (Connection app = base.conexion(BaseDeDatosDePrueba.APP);
                                    PreparedStatement sentencia =
                                            app.prepareStatement(
                                                    "UPDATE arancel SET conjunto_id = ?"
                                                            + " WHERE municipalidad_id = ?"
                                                            + " AND tramo = ?")) {
                                ContextoDeTenant.fijar(app, municipalidad);
                                sentencia.setLong(1, conjuntoSellado);
                                sentencia.setLong(2, municipalidad);
                                sentencia.setString(3, tramo);
                                sentencia.executeUpdate();
                            }
                        })
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("esta sellado");
    }

    @Test
    @DisplayName("contra un conjunto que NO esta en la cache, entra: es el contraste")
    void contraUnConjuntoAbiertoSiSeInserta() {
        // La mitad que impide pasarse de listo. Una guarda que rechazara TODO arancel dejaria el
        // catalogo vial sin poder cargarse nunca, y las dos pruebas de arriba seguirian verdes.
        //
        // Y dice ademas lo que esta guarda ve MENOS que la de `V18`: un conjunto que este sellado
        // en `normativa` y que esta base todavia no haya descargado NO se detecta. La guarda solo
        // puede hablar de lo que ve, y eso esta escrito en la cabecera de `V3`.
        assertThatCode(
                        () ->
                                insertarArancel(
                                        conjuntoSellado + 2_000,
                                        "TRAMO-" + CORRELATIVO.incrementAndGet()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la cache no distingue ambitos: basta una mitad para saber que esta sellado")
    void bastaUnaMitadDelSnapshot() throws SQLException {
        // `normativa_conjunto` tiene clave (municipalidad, conjunto, ambito) y el conjunto se
        // descarga en dos mitades. La guarda cuenta sobre las DOS primeras columnas a proposito:
        // si mirara el ambito, un arancel entraria mientras solo estuviera descargada la mitad de
        // OBLIGACION, que es exactamente cuando el conjunto ya esta sellado.
        try (Connection admin = base.conexionAdmin();
                PreparedStatement sentencia =
                        admin.prepareStatement(
                                "SELECT count(DISTINCT ambito) FROM normativa_conjunto"
                                        + " WHERE municipalidad_id = ? AND conjunto_id = ?")) {
            sentencia.setLong(1, municipalidad);
            sentencia.setLong(2, conjuntoSellado);
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                assertThat(fila.getInt(1))
                        .as("la fixture descarga una sola mitad, y aun asi la guarda muerde")
                        .isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("el conjunto sellado de una municipalidad no congela el arancel de la vecina")
    void elConjuntoSeMiraPorMunicipalidad() throws Exception {
        // `conjunto_parametros` es de tenant en `normativa`: cada municipalidad abre y sella el
        // suyo, y un `conjunto_id` solo es unico dentro de su municipalidad. Si la guarda mirara
        // solo el numero, sellar el conjunto 1 en una municipalidad congelaria el arancel de
        // todas las demas.
        //
        // SE ESCRIBE CON EL CONJUNTO DE LA PRIMERA, que es lo que hace la prueba interesante.
        //
        // Y aqui hay un hallazgo que conviene tener escrito: quitar `AND c.municipalidad_id =
        // v_muni` de la guarda deja esta prueba —y las otras cuatro— en VERDE. No es que la
        // prueba sea floja: es que la clausula es REDUNDANTE por el camino normal.
        // `normativa_conjunto` lleva RLS con FORCE y el disparador no es SECURITY DEFINER, asi
        // que corre con el contexto de quien escribe y la fila de la vecina no la ve. Lo que esta
        // prueba demuestra, entonces, no es la clausula sino la PROPIEDAD: que sellar en una
        // municipalidad no congela el arancel de la otra. La propiedad se sostiene, y quien la
        // sostiene es RLS. Esta escrito tambien en la cabecera de `V3`.
        long vecina = DatosDePrueba.crearMunicipalidad(base, "200702", "Municipalidad vecina");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, vecina, parametroId, "AV");

        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, vecina);
            long viaDeLaVecina = primerId(app, "via", vecina);
            app.rollback();

            assertThatCode(
                            () ->
                                    insertarArancel(
                                            vecina,
                                            viaDeLaVecina,
                                            conjuntoSellado,
                                            "TRAMO-" + CORRELATIVO.incrementAndGet()))
                    .doesNotThrowAnyException();
        }
    }

    // ------------------------------------------------------------------

    private static void insertarArancel(long conjunto, String tramo) throws SQLException {
        insertarArancel(municipalidad, viaId, conjunto, tramo);
    }

    private static void insertarArancel(long muni, long via, long conjunto, String tramo)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP);
                PreparedStatement sentencia =
                        app.prepareStatement(
                                "INSERT INTO arancel (municipalidad_id, via_id, tramo, valor_m2,"
                                        + " documento_fuente, conjunto_id)"
                                        + " VALUES (?, ?, ?, 100.000000, 'prueba de la guarda', ?)")) {
            ContextoDeTenant.fijar(app, muni);
            sentencia.setLong(1, muni);
            sentencia.setLong(2, via);
            sentencia.setString(3, tramo);
            sentencia.setLong(4, conjunto);
            sentencia.executeUpdate();
            app.commit();
        }
    }

    private static long conjuntoEnLaCache(Connection app) throws SQLException {
        try (PreparedStatement consulta =
                app.prepareStatement(
                        "SELECT conjunto_id FROM normativa_conjunto WHERE municipalidad_id = ?"
                                + " ORDER BY conjunto_id LIMIT 1")) {
            consulta.setLong(1, municipalidad);
            try (ResultSet fila = consulta.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }

    private static long primerId(Connection app, String tabla) throws SQLException {
        return primerId(app, tabla, municipalidad);
    }

    private static long primerId(Connection app, String tabla, long muni) throws SQLException {
        try (PreparedStatement consulta =
                app.prepareStatement(
                        "SELECT id FROM "
                                + tabla
                                + " WHERE municipalidad_id = ? ORDER BY id LIMIT 1")) {
            consulta.setLong(1, muni);
            try (ResultSet fila = consulta.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }
}
