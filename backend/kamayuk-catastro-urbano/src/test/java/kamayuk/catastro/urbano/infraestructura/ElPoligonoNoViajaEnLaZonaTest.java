package kamayuk.catastro.urbano.infraestructura;

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
 * #30 AC-1 y AC-6: la consulta de zonificacion no trae el poligono que nadie lee.
 *
 * <h2>Por que esto se mide contra PostgreSQL y no leyendo la constante</h2>
 *
 * <p>Porque lo que hay que impedir no es que la palabra {@code ST_AsText} aparezca en un archivo:
 * es que el motor <b>serialice un poligono de PDU a texto y lo mande por el cable</b>. Una guarda
 * de texto sobre la constante se cumple sola el dia que alguien escriba la conversion de otra forma
 * —{@code geometria::text}, un {@code ST_AsEWKT}, una vista—, y no dice nada de lo que cuesta. Aqui
 * se ejecuta <b>la sentencia de produccion</b>, se leen sus columnas una a una y se pesa lo que
 * llega.
 *
 * <p>Se le pide a {@link UrbanoRepositoryJdbc#ZONA_QUE_CONTIENE} y no a una copia escrita aqui: una
 * copia seguiria verde el dia que alguien cambiara la de produccion, que es exactamente el cambio
 * que esta prueba existe para atrapar.
 *
 * <h2>El poligono de la fixture es de PDU, y sin eso esto no mide nada</h2>
 *
 * <p>Un cuadrado de cuatro vertices pesa 120 bytes: con el, «la respuesta es pequena» seria cierto
 * con el defecto puesto. La zona que se siembra tiene {@link #VERTICES} vertices —lo que tiene una
 * zona de un plan de desarrollo urbano de verdad— y su WKT ronda los 188 KB. La prueba <b>lo
 * comprueba antes de concluir nada</b>: si el poligono no fuera grande, el sujeto habria
 * desaparecido y esto se cumpliria solo.
 */
@DisplayName("#30 — el poligono de la zona no viaja en la lectura")
class ElPoligonoNoViajaEnLaZonaTest {

    /** Vertices del poligono de PDU. Una zona de un plan real tiene miles. */
    private static final int VERTICES = 5000;

    /** Predios de relleno, para que el plan medido sea el de produccion. */
    private static final int PREDIOS = 500;

    /**
     * Cuanto puede pesar como mucho la respuesta de UNA zona.
     *
     * <p>Sus siete columnas son dos identificadores, cuatro cadenas cortas y dos fechas: no llegan
     * a doscientos bytes. El tope se pone holgado porque lo que separa el caso bueno del malo no
     * son decenas de bytes sino tres ordenes de magnitud.
     */
    private static final int TOPE_DE_LA_RESPUESTA = 4_096;

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static long predio;
    private static int bytesDelPoligono;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "200630", "Municipalidad de #30");
        sembrar();
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    @DisplayName("EL SUJETO: la zona sembrada es un poligono de PDU, no un cuadrado")
    void elPoligonoEsDePdu() {
        assertThat(bytesDelPoligono)
                .as(
                        "sin un poligono grande esta prueba se cumpliria sola: cualquier respuesta"
                                + " seria «pequena», con el ST_AsText puesto o quitado")
                .isGreaterThan(100_000);
    }

    @Test
    @DisplayName("la respuesta de la zona no lleva un solo vertice, y pesa lo que dice llevar")
    void laZonaLlegaSinSuPoligono() throws SQLException {
        List<String> valores = leer();

        assertThat(valores)
                .as("si la consulta no devolvio ni una fila, esto no midio nada")
                .isNotEmpty();
        assertThat(String.join("|", valores))
                .as(
                        "el poligono de la zona son %d bytes de texto que ninguna componente de"
                                + " ZonaVigente lee y que ZonaResource no publica (#30)",
                        bytesDelPoligono)
                .doesNotContain("POLYGON")
                .doesNotContain("-80.");

        int bytes = valores.stream().mapToInt(v -> v.getBytes(StandardCharsets.UTF_8).length).sum();
        assertThat(bytes)
                .as(
                        "medido: con el ST_AsText puesto la respuesta pesaba %d bytes; el poligono"
                                + " solo son %d",
                        bytesDelPoligono + 41, bytesDelPoligono)
                .isLessThan(TOPE_DE_LA_RESPUESTA);
    }

    /** La sentencia de produccion, corrida como {@code kamayuk_app} y bajo la politica. */
    private static List<String> leer() throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            String sql =
                    UrbanoRepositoryJdbc.ZONA_QUE_CONTIENE
                            .replace(":predio", Long.toString(predio))
                            .replace(":fecha", "DATE '2026-06-15'");
            List<String> valores = new ArrayList<>();
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
            return valores;
        }
    }

    private static void sembrar() throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            ejecutar(
                    app,
                    "INSERT INTO zonificacion (municipalidad_id, plan, ordenanza, codigo, nombre,"
                            + " geometria, vigencia_desde, observacion, usuario_registro)"
                            + " SELECT "
                            + municipalidad
                            + ", 'PDU-2026', 'ORD-030-2026', 'RDM',"
                            + " 'Residencial de densidad media',"
                            + " ST_Multi(ST_MakePolygon(ST_MakeLine(p.pts)))::geography,"
                            + " DATE '2026-01-01', 'zona de la prueba de #30', 'prueba'"
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
                            + ", '2006300101001' || to_char(g,'FM0000000'),"
                            + " 'URBANO', 'Predio ' || g,"
                            + " ST_Multi(ST_MakeEnvelope(-80.70 + (g % 100) * 0.001,"
                            + "   -5.30 + (g / 100) * 0.001, -80.70 + (g % 100) * 0.001 + 0.0005,"
                            + "   -5.30 + (g / 100) * 0.001 + 0.0005, 4326))::geography"
                            + " FROM generate_series(0, "
                            + (PREDIOS - 1)
                            + ") g");
            ejecutar(app, "ANALYZE zonificacion");
            ejecutar(app, "ANALYZE predio");
            predio = escalar(app, "SELECT min(id) FROM predio");
            bytesDelPoligono =
                    (int)
                            escalar(
                                    app,
                                    "SELECT octet_length(ST_AsText(geometria)) FROM zonificacion"
                                            + " WHERE codigo = 'RDM'");
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
