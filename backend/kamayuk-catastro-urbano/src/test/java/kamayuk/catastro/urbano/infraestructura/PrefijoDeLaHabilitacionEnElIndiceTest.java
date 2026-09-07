package kamayuk.catastro.urbano.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.persistencia.RangoDePrefijo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #27 (AC-3) — el indice de prefijo de {@code habilitacion_urbana} SE CONSERVA, y aqui esta por
 * que.
 *
 * <h2>La premisa del encargo salio falsa al medirla</h2>
 *
 * <p>#27 daba por muerto {@code habilitacion_urbana_codigo_prefijo_ix} porque «una comparacion de
 * rango ({@code >=} / {@code <}) usa la clase por omision y el orden de la colacion». <b>En este
 * repositorio la busqueda por prefijo no se escribe asi</b>: {@link RangoDePrefijo#condicion} emite
 * {@code ~>=~} y {@code ~<~}, que son precisamente los operadores de {@code varchar_pattern_ops} —y
 * los unicos <i>leakproof</i> de los dos juegos, que es el tercer hallazgo de RLS y la razon de que
 * un {@code LIKE} no llegue al indice bajo la politica—. Por eso {@code V1} ya creo {@code
 * via_codigo_prefijo_ix} con {@code text_pattern_ops} para la unica busqueda por prefijo viva del
 * sistema, y por eso el de {@code V7} sirve exactamente a la forma que este codigo usa.
 *
 * <p>No hace falta creerselo: {@code BusquedaDelCatalogoVialTest} ya mide desde #565 que esa forma
 * llega al indice sobre {@code via}, y DAT-01 §0 hallazgo 3 lo escribe como la mitigacion del
 * proyecto —«un rango con los operadores de {@code text_pattern_ops} …, <b>sobre un indice
 * declarado con esa clase de operadores</b>»—. Lo que #27 leyo como una contradiccion era el
 * comentario de {@code V7}, que decia «la clase de operadores que el rango usa» sin decir cuales.
 *
 * <h2>Las tres direcciones que separan las hipotesis</h2>
 *
 * <ul>
 *   <li>la forma que {@code RangoDePrefijo} escribe llega a ESTE indice;
 *   <li><b>el contraste</b>: la forma que el encargo suponia llega al indice UNICO y no a este, o
 *       sea que los dos sirven a cosas distintas y ninguno sobra —sin este caso, «llega a un
 *       indice» se cumpliria con cualquiera de los dos y no habria medido nada—;
 *   <li><b>el contraste caro</b>: retirado dentro de una transaccion que se deshace, el mismo rango
 *       cae al {@code Filter} y el plan <b>sigue diciendo «Index»</b> apoyado solo en la politica.
 *       Es el quinto hallazgo de RLS literal, y es lo que retirar el indice dejaria puesto para el
 *       dia que alguien escriba la primera consulta.
 * </ul>
 *
 * <h2>Lo que sigue siendo cierto, y se dice</h2>
 *
 * <p>Hoy <b>nada</b> consulta {@code habilitacion_urbana}: ningun {@code .java} de {@code src/main}
 * la nombra, medido. Si esa tabla debe tener codigo es #31 y no esto. Lo que esta prueba impide es
 * que la respuesta a «no la consulta nadie» sea retirar el indice y dejar el defecto puesto.
 *
 * <p><b>Sin {@code enable_seqscan = off}</b>: forzarlo mediria si el indice es ALCANZABLE, y lo que
 * hay que medir es que el planificador lo ELIGE. La conexion es la de {@code kamayuk_app}: como
 * superusuario —que omite RLS— el plan seria otro.
 */
@DisplayName("#27 — El prefijo de la habilitacion llega a su indice, y retirarlo cuesta el padron")
class PrefijoDeLaHabilitacionEnElIndiceTest {

    /**
     * Suficientes para que el planificador prefiera un indice.
     *
     * <p>El mismo motivo que {@code PlanoEnElIndiceTest}: con unas pocas filas PostgreSQL recorre
     * la tabla <b>y hace bien</b>, asi que una prueba de plan sobre dos filas no mide el plan, mide
     * el tamano.
     */
    private static final int HABILITACIONES = 6_000;

    /** El prefijo del ensayo: reparte sesenta, asi que selecciona una centesima parte. */
    private static final String PREFIJO = "HU-0007";

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "200612", "Municipalidad de #27");
        long vecina = DatosDePrueba.crearMunicipalidad(base, "200613", "Municipalidad vecina, #27");
        // Las DOS, y no es un adorno: con una sola dueña de toda la tabla la condicion de la
        // politica selecciona el 100 % de las filas y no acota nada.
        for (long cual : new long[] {municipalidad, vecina}) {
            sembrar(cual);
        }
        analizar();
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    @DisplayName("el prefijo, TAL COMO lo escribe RangoDePrefijo, llega al indice de patron")
    void elPrefijoDeLaConvencionLlegaAlIndiceDePatron() throws SQLException {
        String plan = explicar(consultaDePrefijo());

        assertThat(plan)
                .as(
                        "`RangoDePrefijo.condicion` emite ~>=~ y ~<~, que son los operadores de"
                                + " varchar_pattern_ops: el indice de V7 sirve exactamente a la"
                                + " forma que este codigo usa, y por eso V13 lo CONSERVA. Lo que se"
                                + " exige NUNCA es la palabra «Index» sino que el indice NOMBRADO"
                                + " sea el del filtro. Plan medido:%n%s",
                        plan)
                .contains("habilitacion_urbana_codigo_prefijo_ix");

        assertThat(String.join(" ", condicionesDeIndice(plan)))
                .as(
                        "el prefijo y la politica JUNTOS en el Index Cond: si la municipalidad"
                                + " cayera al Filter, la consulta leeria las habilitaciones de todas"
                                + " las municipalidades. Plan medido:%n%s",
                        plan)
                .contains("codigo")
                .contains("municipalidad_id");
    }

    @Test
    @DisplayName("EL CONTRASTE: el rango con >= y < llega al indice UNICO, no a este")
    void elRangoConLaClasePorOmisionLlegaAlOtroIndice() throws SQLException {
        String plan =
                explicar(
                        "SELECT id, codigo FROM habilitacion_urbana"
                                + " WHERE codigo >= '"
                                + PREFIJO
                                + "' AND codigo < 'HU-0008'");

        assertThat(plan)
                .as(
                        "las dos clases de operadores NO se sustituyen: >= y < usan el orden de la"
                                + " colacion y los sirve habilitacion_urbana_codigo_uq. Sin este"
                                + " caso, «llega a un indice» se cumpliria con cualquiera de los dos"
                                + " y no habria medido nada. Plan medido:%n%s",
                        plan)
                .contains("habilitacion_urbana_codigo_uq")
                .doesNotContain("habilitacion_urbana_codigo_prefijo_ix");
    }

    @Test
    @DisplayName(
            "EL CONTRASTE CARO: sin el, el prefijo cae al Filter y el plan sigue diciendo «Index»")
    void sinElIndiceSeReproduceElQuintoHallazgoDeRls() throws SQLException {
        // Se retira DENTRO de una transaccion que se deshace: es la unica forma de medir lo que
        // costaria la decision contraria sin dejarla tomada. Sin este caso, «se conserva» seria una
        // opinion escrita en un comentario de migracion.
        String plan;
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER)) {
            ContextoDeTenant.fijar(owner, municipalidad);
            ejecutar(owner, "DROP INDEX habilitacion_urbana_codigo_prefijo_ix");
            plan = explicarEn(owner, consultaDePrefijo());
            owner.rollback();
        }

        assertThat(plan)
                .as("el indice no esta, luego no puede aparecer. Plan medido:%n%s", plan)
                .doesNotContain("habilitacion_urbana_codigo_prefijo_ix");
        assertThat(plan)
                .as(
                        "y el plan SIGUE diciendo «Index»: se apoya en otro indice solo por la"
                                + " condicion de la politica y recorre la habilitacion entera del"
                                + " inquilino, descartando despues. Es el quinto hallazgo de RLS"
                                + " literal. Plan medido:%n%s",
                        plan)
                .contains("Index");
        assertThat(plan)
                .as(
                        "y el prefijo baja al Filter, que es donde deja de acotar. Plan medido:%n%s",
                        plan)
                .contains("Filter:");
        assertThat(String.join(" ", condicionesDeIndice(plan)))
                .as("solo la politica en el Index Cond. Plan medido:%n%s", plan)
                .contains("municipalidad_id")
                .doesNotContain("~>=~");

        assertThat(existeElIndice("habilitacion_urbana_codigo_prefijo_ix"))
                .as("y la medida no deja la decision tomada: el rollback lo devuelve")
                .isTrue();
    }

    // ------------------------------------------------------------------

    /**
     * El prefijo escrito por la pieza de PRODUCCION, no por una copia a mano.
     *
     * <p>Es lo unico que ata esta prueba a la convencion: escribiendo el {@code ~>=~} aqui, el dia
     * que alguien devolviera {@code RangoDePrefijo} a un {@code LIKE} la prueba seguiria verde y el
     * indice quedaria sirviendo a una forma que nadie usa.
     */
    private static String consultaDePrefijo() {
        StringBuilder donde = new StringBuilder();
        Map<String, Object> parametros = new LinkedHashMap<>();
        RangoDePrefijo.condicion(donde, parametros, "codigo", PREFIJO, "codigo");
        String sql = "SELECT id, codigo FROM habilitacion_urbana WHERE true" + donde;
        for (Map.Entry<String, Object> parametro : parametros.entrySet()) {
            sql = sql.replace(":" + parametro.getKey(), "'" + parametro.getValue() + "'");
        }
        return sql;
    }

    private static String explicar(String sql) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            return explicarEn(app, sql);
        }
    }

    private static String explicarEn(Connection conexion, String sql) throws SQLException {
        List<String> lineas = new ArrayList<>();
        try (PreparedStatement explicar = conexion.prepareStatement("EXPLAIN " + sql);
                ResultSet filas = explicar.executeQuery()) {
            while (filas.next()) {
                lineas.add(filas.getString(1));
            }
        }
        return String.join("\n", lineas);
    }

    private static List<String> condicionesDeIndice(String plan) {
        return plan.lines()
                .map(String::strip)
                .filter(linea -> linea.startsWith("Index Cond:"))
                .toList();
    }

    private static boolean existeElIndice(String nombre) throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER);
                PreparedStatement consulta =
                        owner.prepareStatement(
                                "SELECT count(*) FROM pg_class"
                                        + " WHERE relkind = 'i' AND relname = ?")) {
            consulta.setString(1, nombre);
            try (ResultSet fila = consulta.executeQuery()) {
                fila.next();
                return fila.getInt(1) > 0;
            }
        }
    }

    // ── Fixtures ───────────────────────────────────────────────────────

    private static void sembrar(long municipalidadId) throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER)) {
            ContextoDeTenant.fijar(owner, municipalidadId);
            // Los codigos reparten sesenta prefijos: con todos iguales el indice no acotaria nada
            // y el plan medido seria otro.
            ejecutar(
                    owner,
                    "INSERT INTO habilitacion_urbana (municipalidad_id, codigo, denominacion,"
                            + " resolucion, fecha_resolucion, estado, observacion,"
                            + " usuario_registro)"
                            + " SELECT "
                            + municipalidadId
                            + ", 'HU-' || lpad((i % 60)::text, 4, '0') || '-'"
                            + "         || lpad(("
                            + municipalidadId
                            + " * 100000 + i)::text, 8, '0'),"
                            + " 'Habilitacion ' || i, 'RES-' || i, DATE '2026-01-01', 'APROBADA',"
                            + " 'siembra de la prueba de #27', 'pruebas'"
                            + " FROM generate_series(1, "
                            + HABILITACIONES
                            + ") i");
            owner.commit();
        }
    }

    /** Sin estadisticas el planificador adivina, y la prueba mediria su adivinanza. */
    private static void analizar() throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER)) {
            ejecutar(owner, "ANALYZE habilitacion_urbana");
            owner.commit();
        }
    }

    private static void ejecutar(Connection conexion, String sql) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.execute();
        }
    }
}
