package kamayuk.catastro.fiscalizacion.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * #30 AC-2 y AC-6: ninguna de las tres listas trae el poligono que nadie publica.
 *
 * <h2>Por que se mide contra PostgreSQL y no leyendo la constante</h2>
 *
 * <p>Lo que hay que impedir no es que la palabra {@code ST_AsText} aparezca en un archivo: es que
 * el motor <b>serialice un poligono a texto y lo mande por el cable</b>. Una guarda de texto se
 * cumple sola el dia que la conversion se escriba de otra forma —{@code geometria::text}, {@code
 * ST_AsEWKT}, una vista—, y ademas no dice cuanto pesa. Aqui se ejecutan <b>las listas de columnas
 * de produccion</b>, se leen sus valores uno a uno y se pesa lo que llega.
 *
 * <p>Se les pide a las constantes del repositorio y no a copias escritas aqui: una copia seguiria
 * verde el dia que alguien cambiara la de produccion, que es el cambio que esta prueba existe para
 * atrapar. Es la misma decision que {@code HallazgosDelPredioJdbcTest} tomo para su prueba de plan.
 *
 * <h2>Los poligonos de la fixture son de PDU, y sin eso esto no mide nada</h2>
 *
 * <p>Con un cuadrado de cuatro vertices «la pagina es pequena» seria cierto con el defecto puesto.
 * Los que se siembran en {@code candidato} y en {@code hallazgo} tienen {@link #VERTICES} vertices
 * y su WKT ronda los 188 KB, que es lo que pesa una zona de un plan de verdad; y la prueba
 * <b>comprueba ese tamano antes de concluir nada</b>.
 *
 * <p>Medido antes de este issue, con esta misma fixture: la pagina de {@link #FILAS} candidatos
 * pesaba <b>3 772 822 bytes</b>, la de hallazgos <b>3 772 964</b> y la lista de UN predio —una sola
 * fila— <b>188 651</b>.
 */
@DisplayName("#30 — ninguna lista de fiscalizacion trae el poligono")
class NingunaListaTraeElPoligonoTest {

    /** Vertices del poligono. Una zona de un plan real tiene miles. */
    private static final int VERTICES = 5000;

    /** Filas por pagina, como la cola de gabinete. */
    private static final int FILAS = 20;

    /**
     * Cuanto puede pesar como mucho una pagina de {@link #FILAS} filas.
     *
     * <p>Son trece columnas de identificadores, cadenas cortas y cifras: medido, 922 bytes la de
     * candidatos y 1 064 la de hallazgos. El tope va holgado porque lo que separa el caso bueno del
     * malo no son decenas de bytes sino tres ordenes de magnitud.
     */
    private static final int TOPE_DE_LA_PAGINA = 16_384;

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static long campania;
    private static long predio;
    private static int bytesDelPoligono;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "200631", "Municipalidad de #30");
        sembrar();
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    @DisplayName("EL SUJETO: los poligonos sembrados son de PDU, no cuadrados de cuatro vertices")
    void losPoligonosSonDePdu() {
        assertThat(bytesDelPoligono)
                .as(
                        "sin poligonos grandes esta prueba se cumpliria sola: cualquier pagina"
                                + " seria «pequena», con el ST_AsText puesto o quitado")
                .isGreaterThan(100_000);
    }

    @Test
    @DisplayName("la cola de gabinete: 20 candidatos y ni un vertice")
    void laColaDeGabinete() throws SQLException {
        comprobar(
                "la cola de candidatos",
                "SELECT "
                        + FiscalizacionRepositoryJdbc.COLUMNAS_DE_LA_COLA
                        + " FROM candidato WHERE campania_id = "
                        + campania
                        + " ORDER BY id LIMIT "
                        + FILAS);
    }

    /**
     * Los hallazgos de UN predio: la sentencia entera, y ni un vertice.
     *
     * <p><b>#23 llego antes y retiro {@code hallazgo.geometria} del esquema y del registro</b>
     * ({@code V12}), que era el punto (c) de #30 —el {@code ST_AsText(NULL)} de cada fila de cada
     * respuesta— y que el propio issue anticipo: «si #23 decide retirar `hallazgo.geometria`, el
     * punto (c) se resuelve solo». Se resolvio solo.
     *
     * <p><b>Y hay que decir lo que eso le hace a esta comprobacion</b>, en vez de dejarla
     * pareciendo que muerde como las otras dos: su defecto <b>ya no se puede reintroducir sin una
     * migracion</b>. Medido devolviendo {@code ST_AsText(h.geometria)} a la sentencia de
     * produccion: no falla la asercion, falla el MOTOR —«ERROR: column h.geometria does not
     * exist»—, que es una garantia mas fuerte que una prueba. Se queda como guarda de regresion
     * sobre la decision de #23: el dia que una columna de geometria vuelva a {@code hallazgo}, esto
     * mide si sale por el cable.
     */
    @Test
    @DisplayName("los hallazgos de UN predio: la sentencia entera, y ni un vertice (#23 y #30)")
    void losHallazgosDelPredio() throws SQLException {
        comprobar(
                "los hallazgos del predio",
                FiscalizacionRepositoryJdbc.HALLAZGOS_DEL_PREDIO.replace(
                        ":predioId", Long.toString(predio)));
    }

    /** Ejecuta la sentencia de produccion como {@code kamayuk_app} y pesa lo que vuelve. */
    private static void comprobar(String queEs, String sql) throws SQLException {
        List<String> valores = new ArrayList<>();
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement consulta = app.createStatement();
                    ResultSet filas = consulta.executeQuery(sql)) {
                int columnas = filas.getMetaData().getColumnCount();
                while (filas.next()) {
                    for (int c = 1; c <= columnas; c++) {
                        String valor = filas.getString(c);
                        valores.add(valor == null ? "" : valor);
                    }
                }
            }
        }

        assertThat(valores)
                .as("%s no devolvio ni una fila: esto no midio nada", queEs)
                .isNotEmpty();
        assertThat(String.join("|", valores))
                .as(
                        "%s traeria %d bytes de poligono por fila, y ningun recurso de este"
                                + " contrato lo publica (#30)",
                        queEs, bytesDelPoligono)
                .doesNotContain("POLYGON")
                .doesNotContain("-80.");
        int bytes = valores.stream().mapToInt(v -> v.getBytes(StandardCharsets.UTF_8).length).sum();
        assertThat(bytes)
                .as("%s pesa lo que sus columnas de texto y numeros, no lo que un plano", queEs)
                .isLessThan(TOPE_DE_LA_PAGINA);
    }

    private static void sembrar() throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            ejecutar(
                    app,
                    "CREATE TEMP TABLE poligono_de_pdu AS"
                            + " SELECT ST_Multi(ST_MakePolygon(ST_MakeLine(p.pts)))::geography AS g"
                            + " FROM (SELECT array_agg(ST_SetSRID(ST_MakePoint("
                            + "   -80.65 + 0.30 * cos(2*pi()*g/"
                            + VERTICES
                            + "),"
                            + "   -5.25 + 0.30 * sin(2*pi()*g/"
                            + VERTICES
                            + ")), 4326) ORDER BY g) AS pts"
                            + "   FROM generate_series(0, "
                            + VERTICES
                            + ") g) p");
            ejecutar(
                    app,
                    "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo, direccion,"
                            + " geometria)"
                            + " SELECT "
                            + municipalidad
                            + ", '2006310101001' || to_char(g,'FM0000000'),"
                            + " 'URBANO', 'Predio ' || g,"
                            + " ST_Multi(ST_MakeEnvelope(-80.70 + (g % 100) * 0.001,"
                            + "   -5.30 + (g / 100) * 0.001, -80.70 + (g % 100) * 0.001 + 0.0005,"
                            + "   -5.30 + (g / 100) * 0.001 + 0.0005, 4326))::geography"
                            + " FROM generate_series(0, 199) g");
            ejecutar(
                    app,
                    "INSERT INTO ficha_catastral (municipalidad_id, predio_id, version, tipo,"
                            + " area_terreno, uso, vigencia_desde, observacion, usuario_registro,"
                            + " origen, documento_origen)"
                            + " SELECT "
                            + municipalidad
                            + ", p.id, 1, 'UNICA', 100.00, 'Casa habitacion',"
                            + " DATE '2026-01-01', 'prueba de #30', 'prueba', 'MIGRACION',"
                            + " 'DOC-30' FROM predio p");
            ejecutar(
                    app,
                    "INSERT INTO campania (municipalidad_id, codigo, nombre, estado, inicio,"
                            + " umbral, tope, observacion, usuario_registro)"
                            + " VALUES ("
                            + municipalidad
                            + ", 'C-30', 'Campania de la prueba de #30', 'ABIERTA',"
                            + " DATE '2026-01-01', 0.05, 500, 'prueba de #30', 'prueba')");
            campania = escalar(app, "SELECT id FROM campania WHERE codigo = 'C-30'");
            ejecutar(
                    app,
                    "INSERT INTO candidato (municipalidad_id, campania_id, predio_id, clase,"
                            + " origen, score, insumos, geometria, estado, observacion,"
                            + " usuario_registro)"
                            + " SELECT "
                            + municipalidad
                            + ", "
                            + campania
                            + ", p.id, 'SUBVALUADOR', 'CRUCE_DE_AREAS', 0.50, '{}'::jsonb,"
                            + " (SELECT g FROM poligono_de_pdu), 'DETECTADO', 'prueba de #30',"
                            + " 'prueba' FROM predio p");
            ejecutar(
                    app,
                    "INSERT INTO hallazgo (municipalidad_id, candidato_id, clase, predio_id,"
                            + " ficha_id, area_de_la_ficha, area_verificada, inspector,"
                            + " verificado_en, estado, observacion, usuario_registro)"
                            + " SELECT "
                            + municipalidad
                            + ", c.id, 'SUBVALUADOR', c.predio_id,"
                            + " (SELECT f.id FROM ficha_catastral f WHERE f.predio_id = c.predio_id"
                            + "   LIMIT 1), 100.00, 180.00, 'Inspector', DATE '2026-02-01',"
                            + " 'FIRME', 'prueba de #30', 'prueba' FROM candidato c");
            ejecutar(app, "ANALYZE");
            predio = escalar(app, "SELECT min(predio_id) FROM hallazgo");
            bytesDelPoligono =
                    (int) escalar(app, "SELECT octet_length(ST_AsText(g)) FROM poligono_de_pdu");
            app.commit();
        }
    }

    private static void ejecutar(Connection app, String sql) throws SQLException {
        try (Statement s = app.createStatement()) {
            s.execute(sql);
        }
    }

    private static long escalar(Connection app, String sql) throws SQLException {
        try (Statement s = app.createStatement();
                ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getLong(1);
        }
    }
}
