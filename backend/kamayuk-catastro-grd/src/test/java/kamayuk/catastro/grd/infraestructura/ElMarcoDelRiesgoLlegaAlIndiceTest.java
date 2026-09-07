package kamayuk.catastro.grd.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #21 — las dos lecturas espaciales de {@code grd} resuelven el lote UNA vez y entran por el indice
 * del marco, en vez de recorrer la carta de peligro entera del inquilino (ADR-0034 regla 2).
 *
 * <h2>Lo que se exige NO es la palabra «Index»</h2>
 *
 * <p>Esa es justamente la del quinto hallazgo de RLS: el plan la dice mientras lee el padron entero
 * por el {@code Index Cond} de la politica. Aqui se exigen <b>dos cosas distintas</b>, que se
 * rompen por separado y hacen falta las dos:
 *
 * <ol>
 *   <li><b>El lote se resuelve una sola vez</b> ({@code loops=1} sobre {@code predio}). Es lo que
 *       garantiza {@code CROSS JOIN LATERAL}, y es <b>estructural</b>: el predio va fuera del bucle
 *       por construccion. Con el {@code JOIN} llano el planificador puede poner la capa fuera y
 *       volver a buscar el mismo lote por su PK <b>una vez por zona</b>.
 *   <li><b>Las cuatro columnas del marco salen en el {@code Index Cond}</b>, junto a la condicion
 *       de la politica. Es lo que compra la barrera {@code OFFSET 0}: sin ella PostgreSQL aplana el
 *       {@code LATERAL} y las cuatro vuelven a ser condiciones de UNION.
 * </ol>
 *
 * <h2>Por que se miden DOS tamanos, y no uno</h2>
 *
 * <p><b>Porque con uno la prueba no muerde, y esto se midio.</b> La primera version de esta clase
 * fijaba un solo tamano —3 000 filas en cada tabla, dos municipalidades— y con el {@code JOIN}
 * llano puesto encima <b>pasaba en VERDE</b>: a ese tamano el planificador elegia el buen plan por
 * su cuenta. Que el marco llegue al indice es una decision de COSTE, asi que un tamano solo mide la
 * preferencia del planificador ese dia; lo que separa las dos formas es el barrido. Los dos que
 * quedan son los que el barrido de #21 midio como discriminantes: con el {@code JOIN} llano, {@code
 * predio} se relee 20 y 3 000 veces respectivamente, y el marco no llega al indice en ninguno de
 * los dos.
 *
 * <p>Y <b>cada tamano vive en su municipalidad</b>: la politica RLS es lo unico que acota, asi que
 * mezclarlos en una sola dejaria a las dos formas mirando la misma tabla.
 *
 * <h2>Tres decisiones mas de metodo</h2>
 *
 * <ul>
 *   <li><b>El SQL se le pide al repositorio</b>, no se copia aqui: una copia seguiria en verde el
 *       dia que alguien devolviera la de produccion al {@code JOIN} llano, que es exactamente el
 *       cambio que no se ve en el resultado.
 *   <li><b>Se pueblan LAS DOS tablas.</b> Un plan sobre dos filas no mide el plan: mide el tamano.
 *   <li><b>Sin {@code enable_seqscan = off}</b>, a proposito: forzarlo mediria si el indice es
 *       ALCANZABLE, y lo que hay que medir es que el planificador lo ELIJA.
 * </ul>
 *
 * <p>La conexion es la de {@code kamayuk_app}: un superusuario omite RLS aun con {@code FORCE ROW
 * LEVEL SECURITY} (primer hallazgo de RLS), y ademas es <b>el unico rol para el que este defecto
 * existe</b> — el operador espacial deja de promoverse solo cuando hay politica que atravesar.
 */
@DisplayName("#21 — El lote se resuelve una vez y el marco llega al indice, en las dos capas")
class ElMarcoDelRiesgoLlegaAlIndiceTest {

    /** Los dos tamanos discriminantes del barrido de #21: (filas de capa, predios). */
    private static final int[][] TAMANOS = {{20, 3000}, {3000, 3000}};

    private static final String LOTE =
            "MULTIPOLYGON(((-80.6900 -5.2700,-80.6850 -5.2700,-80.6850 -5.2660,"
                    + "-80.6900 -5.2660,-80.6900 -5.2700)))";

    /** {@code Index Scan using predio_pk on predio p (actual time=… rows=1 loops=N)}. */
    private static final Pattern VUELTAS_SOBRE_EL_PREDIO =
            Pattern.compile("on predio p \\(actual[^)]*loops=(\\d+)\\)");

    private static BaseDeDatosDePrueba base;
    private static final List<Escenario> ESCENARIOS = new ArrayList<>();

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
        for (int i = 0; i < TAMANOS.length; i++) {
            int[] tamano = TAMANOS[i];
            String sufijo = Integer.toString(i + 1);
            long muni =
                    DatosDePrueba.crearMunicipalidad(
                            base, "2104" + sufijo + "1", "Municipalidad de #21 — " + sufijo);
            long predio = sembrar(muni, sufijo, tamano[0], tamano[1]);
            ESCENARIOS.add(new Escenario(muni, predio, tamano[0], tamano[1]));
        }
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    @DisplayName("las zonas de riesgo: el lote una vez, y `zona_riesgo_marco_ix` con la politica")
    void lasZonasEntranPorElMarco() throws SQLException {
        for (Escenario escenario : ESCENARIOS) {
            exigir(
                    escenario,
                    GestionDeRiesgoRepositoryJdbc.zonasQueCruzanElLoteSql(),
                    "zona_riesgo_marco_ix");
        }
    }

    @Test
    @DisplayName("y las fajas marginales: son dos capas, dos indices y el mismo predicado")
    void lasFajasEntranPorElMarco() throws SQLException {
        // Las dos y no una: comparten el predicado —por eso comparten el defecto— pero son tablas
        // distintas con indices distintos, y un plan bueno en una no dice nada de la otra.
        for (Escenario escenario : ESCENARIOS) {
            exigir(
                    escenario,
                    GestionDeRiesgoRepositoryJdbc.fajasQueCruzanElLoteSql(),
                    "faja_marginal_marco_ix");
        }
    }

    // ------------------------------------------------------------------

    private static void exigir(Escenario escenario, String sql, String indice) throws SQLException {
        List<String> plan = planDe(escenario, sql);
        String entero = String.join("\n", plan);
        String donde = escenario.rotulo();

        Matcher vueltas = VUELTAS_SOBRE_EL_PREDIO.matcher(entero);
        assertThat(vueltas.find())
                .as("%s: el plan tiene que recorrer «predio». Plan medido:%n%s", donde, entero)
                .isTrue();
        assertThat(Integer.parseInt(vueltas.group(1)))
                .as(
                        "%s: el lote se resuelve UNA vez. Si el planificador pone la capa fuera del"
                                + " bucle, vuelve a buscar el mismo predio por su PK una vez por fila"
                                + " de la capa —y la respuesta sigue siendo la misma—. Plan medido:%n%s",
                        donde, entero)
                .isEqualTo(1);

        assertThat(entero)
                .as(
                        "%s: lo que hay que exigir NUNCA es la palabra «Index» —la del quinto"
                                + " hallazgo de RLS tambien la decia—: es que el indice NOMBRADO sea el"
                                + " del marco. Plan medido:%n%s",
                        donde, entero)
                .contains(indice);

        String condicionDelIndice =
                plan.stream()
                        .filter(linea -> linea.contains("Index Cond"))
                        .reduce("", (a, b) -> a + "\n" + b);
        assertThat(condicionDelIndice)
                .as(
                        "%s: las cuatro columnas del marco y la condicion de la politica, juntas en"
                                + " el Index Cond. Si caen al «Join Filter» la respuesta sigue siendo"
                                + " CORRECTA y se lee la capa entera del inquilino. Plan medido:%n%s",
                        donde, entero)
                .contains("marco_oeste")
                .contains("marco_sur")
                .contains("marco_este")
                .contains("marco_norte")
                .contains("municipalidad_id");
    }

    private static List<String> planDe(Escenario escenario, String sql) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, escenario.municipalidad());
            String resuelto =
                    sql.replace(":predioId", Long.toString(escenario.predio()))
                            .replace(":aLaFecha", "DATE '2026-06-15'");
            List<String> lineas = new ArrayList<>();
            // ANALYZE porque «loops» es una cifra de EJECUCION: sin el, el plan no dice cuantas
            // veces se recorre el predio, que es la mitad estructural de lo que esto vigila.
            try (PreparedStatement explicar =
                            app.prepareStatement("EXPLAIN (ANALYZE, COSTS OFF) " + resuelto);
                    ResultSet filas = explicar.executeQuery()) {
                while (filas.next()) {
                    lineas.add(filas.getString(1));
                }
            }
            return lineas;
        }
    }

    /**
     * El lote del escenario y el relleno de las tres tablas. Devuelve el predio que se consulta.
     */
    private static long sembrar(long muni, String sufijo, int filasDeCapa, int predios)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, muni);
            long via =
                    unaFila(
                            app,
                            "INSERT INTO via (municipalidad_id, codigo, tipo_via, nombre)"
                                    + " VALUES (?, ?, 'AVENIDA', ?) RETURNING id",
                            muni,
                            "V-" + sufijo,
                            "Avenida del riesgo " + sufijo);
            long predio =
                    unaFila(
                            app,
                            "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo,"
                                    + " via_id, direccion, lote, geometria) VALUES (?, ?, 'URBANO', ?,"
                                    + " 'Jr. del Riesgo', '01', ST_GeogFromText(CAST(? AS text)))"
                                    + " RETURNING id",
                            muni,
                            String.format("2104%s0100100100099999", sufijo),
                            via,
                            LOTE);
            // El relleno va en otra banda de latitud: lo unico que aporta es TAMANO, y el tamano
            // es lo que hace que el plan medido sea el de produccion y no el de una tabla vacia.
            ejecutar(
                    app,
                    "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo, direccion,"
                            + " geometria) SELECT ?, ? || to_char(g, 'FM0000000'), 'URBANO', 'Relleno'"
                            + " || g, ST_Multi(ST_MakeEnvelope(-81 + (g % 100) * 0.01 + 0.004, -6 + (g"
                            + " / 100) * 0.01 + 0.004, -81 + (g % 100) * 0.01 + 0.006, -6 + (g / 100) *"
                            + " 0.01 + 0.006, 4326))::geography FROM generate_series(0, ?) g",
                    muni,
                    String.format("2104%s0100100", sufijo),
                    predios - 1);
            ejecutar(
                    app,
                    "INSERT INTO zona_riesgo (municipalidad_id, codigo, fenomeno, nivel, mitigable,"
                            + " fuente, documento_origen, vigencia_desde, geometria, observacion,"
                            + " usuario_registro) SELECT ?, 'ZR-' || ? || '-' || g, 'INUNDACION',"
                            + " 'ALTO', true, 'CENEPRED', 'DOC-21', DATE '2026-01-01',"
                            + " ST_Multi(ST_MakeEnvelope(-81 + (g % 100) * 0.01, -6 + (g / 100) * 0.01,"
                            + " -81 + (g % 100) * 0.01 + 0.01, -6 + (g / 100) * 0.01 + 0.01,"
                            + " 4326))::geography, 'relleno de la prueba de plan de #21', 'prueba' FROM"
                            + " generate_series(0, ?) g",
                    muni,
                    sufijo,
                    filasDeCapa - 1);
            ejecutar(
                    app,
                    "INSERT INTO faja_marginal (municipalidad_id, codigo, cuerpo_agua, ancho_m,"
                            + " fuente, documento_origen, vigencia_desde, geometria, observacion,"
                            + " usuario_registro) SELECT ?, 'FM-' || ? || '-' || g, 'Rio Piura', 25.00,"
                            + " 'ANA', 'RD-21', DATE '2026-01-01', ST_Multi(ST_MakeEnvelope(-81 + (g %"
                            + " 100) * 0.01, -6 + (g / 100) * 0.01, -81 + (g % 100) * 0.01 + 0.01, -6 +"
                            + " (g / 100) * 0.01 + 0.01, 4326))::geography, 'relleno de la prueba de"
                            + " plan de #21', 'prueba' FROM generate_series(0, ?) g",
                    muni,
                    sufijo,
                    filasDeCapa - 1);
            ejecutar(app, "ANALYZE zona_riesgo");
            ejecutar(app, "ANALYZE faja_marginal");
            ejecutar(app, "ANALYZE predio");
            app.commit();
            return predio;
        }
    }

    private static long unaFila(Connection app, String sql, Object... argumentos)
            throws SQLException {
        try (PreparedStatement sentencia = app.prepareStatement(sql)) {
            for (int i = 0; i < argumentos.length; i++) {
                sentencia.setObject(i + 1, argumentos[i]);
            }
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }

    private static void ejecutar(Connection app, String sql, Object... argumentos)
            throws SQLException {
        try (PreparedStatement sentencia = app.prepareStatement(sql)) {
            for (int i = 0; i < argumentos.length; i++) {
                sentencia.setObject(i + 1, argumentos[i]);
            }
            sentencia.executeUpdate();
        }
    }

    /** Un tamano de tabla con su municipalidad y el predio que se consulta en ella. */
    private record Escenario(long municipalidad, long predio, int filasDeCapa, int predios) {
        String rotulo() {
            return "capa=" + filasDeCapa + " predios=" + predios;
        }
    }
}
