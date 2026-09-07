package kamayuk.catastro.urbano.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #22 — el {@code LIMIT} de {@code ZONA_QUE_CONTIENE} es tambien una BARRERA de optimizacion, y
 * quitarlo devuelve el marco al {@code Join Filter} (ADR-0034 regla 2).
 *
 * <h2>Por que esta clase existe, y no se la escribio #4</h2>
 *
 * <p>#4 dejo escrito que {@code CROSS JOIN LATERAL} es lo que mete el marco en el {@code Index
 * Cond}. <b>Es la mitad de la verdad, y la otra mitad la midio #21</b>: PostgreSQL <b>aplana</b>
 * (<i>subquery pull-up</i>) un {@code LATERAL} que no lleve barrera, y entonces las cuatro
 * comparaciones vuelven a ser condiciones de UNION exactamente igual que con un {@code JOIN} llano.
 * Lo que salva a esta consulta no es el {@code LATERAL} solo: es el {@code LATERAL} <b>con su
 * {@code LIMIT}</b>.
 *
 * <p>Eso convierte al {@code LIMIT} en una pieza de rendimiento ademas de una de semantica, y por
 * eso #22 —que cambia {@code LIMIT 1} por {@code LIMIT 2}— tiene que dejar una guarda: quitarlo del
 * todo, que es lo que «pedir todas las zonas» sugiere hacer, no cambia ninguna respuesta y deshace
 * lo que #4 midio.
 *
 * <p><b>Y hacia falta una clase aparte, medido.</b> Con el {@code LIMIT} quitado, las 41 pruebas de
 * {@code urbano} —incluida la de plan de {@code ZonificacionFronteraTest}— pasaban en <b>VERDE</b>:
 * al tamano que aquella siembra, el planificador elige el buen plan por su cuenta. Que el marco
 * llegue al indice es una decision de COSTE, asi que un solo tamano mide la preferencia del
 * planificador ese dia. Los dos de aqui son los que el barrido de #22 midio como discriminantes
 * sobre el esquema de verdad, con su politica RLS puesta.
 *
 * <p>Se le pide el SQL a la constante del repositorio, se puebla, y <b>sin {@code enable_seqscan =
 * off}</b>: lo que hay que medir es que el planificador lo ELIJA, no que el indice sea alcanzable.
 * Y la conexion es la de {@code kamayuk_app}, que es el unico rol para el que este defecto existe.
 */
@DisplayName("#22 — El LIMIT de la consulta de zona es tambien la barrera que sujeta el marco")
class ElMarcoDeLaZonificacionLlegaAlIndiceTest {

    /** Los dos tamanos discriminantes del barrido de #22: (zonas, predios). */
    private static final int[][] TAMANOS = {{3000, 3000}, {3000, 200}};

    private static final String LOTE =
            "MULTIPOLYGON(((-80.6900 -5.2700,-80.6850 -5.2700,-80.6850 -5.2660,"
                    + "-80.6900 -5.2660,-80.6900 -5.2700)))";

    private static BaseDeDatosDePrueba base;
    private static final List<Escenario> ESCENARIOS = new ArrayList<>();

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
        for (int i = 0; i < TAMANOS.length; i++) {
            String sufijo = Integer.toString(i + 1);
            long muni =
                    DatosDePrueba.crearMunicipalidad(
                            base, "2204" + sufijo + "1", "Municipalidad de #22 — " + sufijo);
            long predio = sembrar(muni, sufijo, TAMANOS[i][0], TAMANOS[i][1]);
            ESCENARIOS.add(new Escenario(muni, predio, TAMANOS[i][0], TAMANOS[i][1]));
        }
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    @DisplayName("las cuatro columnas del marco y la politica, juntas en el Index Cond")
    void elMarcoLlegaAlIndiceEnLosDosTamanos() throws SQLException {
        for (Escenario escenario : ESCENARIOS) {
            List<String> plan = planDe(escenario);
            String entero = String.join("\n", plan);

            assertThat(entero)
                    .as(
                            "%s: lo que hay que exigir NUNCA es la palabra «Index» —la del quinto"
                                    + " hallazgo de RLS tambien la decia—: es que el indice NOMBRADO"
                                    + " sea el del marco. Plan medido:%n%s",
                            escenario.rotulo(), entero)
                    .contains("zonificacion_marco_ix");

            String condicionDelIndice =
                    plan.stream()
                            .filter(linea -> linea.contains("Index Cond"))
                            .reduce("", (a, b) -> a + "\n" + b);
            assertThat(condicionDelIndice)
                    .as(
                            "%s: si las cuatro caen al «Join Filter» la respuesta sigue siendo"
                                    + " CORRECTA y se lee la zonificacion entera del inquilino — el"
                                    + " quinto hallazgo de RLS por la puerta de atras. Plan"
                                    + " medido:%n%s",
                            escenario.rotulo(), entero)
                    .contains("marco_oeste")
                    .contains("marco_sur")
                    .contains("marco_este")
                    .contains("marco_norte")
                    .contains("municipalidad_id");
        }
    }

    // ------------------------------------------------------------------

    private static List<String> planDe(Escenario escenario) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, escenario.municipalidad());
            String sql =
                    UrbanoRepositoryJdbc.ZONA_QUE_CONTIENE
                            .replace(":predio", Long.toString(escenario.predio()))
                            .replace(":fecha", "DATE '2026-06-15'");
            List<String> lineas = new ArrayList<>();
            try (PreparedStatement explicar = app.prepareStatement("EXPLAIN " + sql);
                    ResultSet filas = explicar.executeQuery()) {
                while (filas.next()) {
                    lineas.add(filas.getString(1));
                }
            }
            return lineas;
        }
    }

    private static long sembrar(long muni, String sufijo, int zonas, int predios)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, muni);
            long predio;
            try (PreparedStatement alta =
                    app.prepareStatement(
                            "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo,"
                                    + " direccion, geometria) VALUES (?, ?, 'URBANO', 'Jr. de la"
                                    + " Zonificacion', ST_GeogFromText(CAST(? AS text))) RETURNING"
                                    + " id")) {
                alta.setLong(1, muni);
                alta.setString(2, String.format("2204%s0100100100099999", sufijo));
                alta.setString(3, LOTE);
                try (ResultSet fila = alta.executeQuery()) {
                    fila.next();
                    predio = fila.getLong(1);
                }
            }
            // El relleno va en otra banda de latitud: lo unico que aporta es TAMANO, y las zonas
            // van todas del MISMO plan porque `zonificacion_planes_no_se_pisan` lleva
            // «plan WITH <>» — con otro plan cada fila chocaria con la anterior.
            ejecutar(
                    app,
                    "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo, direccion,"
                            + " geometria) SELECT ?, ? || to_char(g, 'FM0000000'), 'URBANO', 'Relleno'"
                            + " || g, ST_Multi(ST_MakeEnvelope(-81 + (g % 100) * 0.01 + 0.004, -6 + (g"
                            + " / 100) * 0.01 + 0.004, -81 + (g % 100) * 0.01 + 0.006, -6 + (g / 100) *"
                            + " 0.01 + 0.006, 4326))::geography FROM generate_series(0, ?) g",
                    muni,
                    String.format("2204%s0100100", sufijo),
                    predios - 1);
            ejecutar(
                    app,
                    "INSERT INTO zonificacion (municipalidad_id, plan, ordenanza, codigo, nombre,"
                            + " geometria, vigencia_desde, observacion, usuario_registro) SELECT ?,"
                            + " 'PDU-2026-' || ?, 'ORD-022-2026', 'Z' || g, 'Zona de relleno' || g,"
                            + " ST_Multi(ST_MakeEnvelope(-81 + (g % 100) * 0.01, -6 + (g / 100) * 0.01,"
                            + " -81 + (g % 100) * 0.01 + 0.01, -6 + (g / 100) * 0.01 + 0.01,"
                            + " 4326))::geography, DATE '2026-01-01', 'relleno de la prueba de plan de"
                            + " #22', 'prueba' FROM generate_series(0, ?) g",
                    muni,
                    sufijo,
                    zonas - 1);
            ejecutar(app, "ANALYZE zonificacion");
            ejecutar(app, "ANALYZE predio");
            app.commit();
            return predio;
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
    private record Escenario(long municipalidad, long predio, int zonas, int predios) {
        String rotulo() {
            return "zonas=" + zonas + " predios=" + predios;
        }
    }
}
