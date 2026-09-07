package kamayuk.catastro.esquema;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #27 (AC-4) — los dos comentarios que {@code V10} volvio falsos, y lo que un DBA lee hoy.
 *
 * <h2>Por que se lee el CATALOGO y no el archivo de la migracion</h2>
 *
 * <p>Porque no son la misma cosa, y esa diferencia es media razon de que este issue exista. {@code
 * V6} sigue diciendo en su texto «{@code longitud_m}: la que midio el tecnico. NO se deriva de la
 * geometria», y <b>no se edita</b>: Flyway guarda el checksum de una migracion aplicada y {@code
 * validateOnMigrate} esta activo, asi que cambiarle una letra deja toda base ya migrada sin poder
 * migrar —{@code V10} lo midio: «Migration checksum mismatch for migration version 5»—. Lo que un
 * DBA consulta es {@code col_description}, y eso si lo puede reemplazar una migracion nueva. Esta
 * prueba mide ese valor, que es el que se lee.
 *
 * <h2>Las dos afirmaciones, y por que cada una necesita sus dos mitades</h2>
 *
 * <ul>
 *   <li><b>{@code longitud_m}</b>: desde {@code V10} el derivador SI la escribe, como {@code
 *       PROPUESTA}. Un comentario que diga «no se deriva» a secas es medio falso; uno que diga «se
 *       deriva» a secas borra ADR-0021. Se exigen las dos mitades.
 *   <li><b>{@code longitud_estado}</b>: el {@code DEFAULT 'PROPUESTA'} de {@code V10} marco como
 *       propuesta del derivador toda fila anterior a {@code V10} — y esas solo las pudo escribir
 *       una persona, porque el derivador no existia. Es la etiqueta al reves, no se puede
 *       reetiquetar sin inventar el dato que falta, y por eso lo unico que se puede hacer es
 *       DECIRLO donde se lee.
 * </ul>
 */
@DisplayName("#27 — Los comentarios que V10 volvio falsos dicen la verdad (V13)")
class ComentariosQueDicenLaVerdadTest {

    private static BaseDeDatosDePrueba base;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    @DisplayName("longitud_m distingue la propuesta de la medida, y no dice «no se deriva» a secas")
    void elComentarioDeLongitudDistingueLasDosCosas() throws SQLException {
        String comentario = comentarioDe("frente_predio", "longitud_m");

        assertThat(comentario)
                .as("las dos formas de esta cifra tienen que estar nombradas")
                .contains("PROPUESTA")
                .contains("CONFIRMADA");
        assertThat(comentario)
                .as(
                        "y lo que NO se deriva es la confirmada, no la columna: de ella cuelga un"
                                + " cobro (ADR-0021). Un comentario que dijera «se deriva» a secas"
                                + " borraria la decision, y uno que dijera «no se deriva» a secas es"
                                + " lo que V10 volvio medio falso")
                .contains("ADR-0021")
                .contains("longitud_estado");
    }

    @Test
    @DisplayName("longitud_estado avisa de que el DEFAULT de V10 reetiqueto lo anterior")
    void elComentarioDelEstadoAvisaDelReetiquetado() throws SQLException {
        String comentario = comentarioDe("frente_predio", "longitud_estado");

        assertThat(comentario)
                .as(
                        "toda fila anterior a V10 quedo marcada PROPUESTA, y esas solo las pudo"
                                + " escribir una persona: hoy no hay ni una fila en ninguna"
                                + " instalacion, y el dia que las haya ya no se podran distinguir")
                .contains("V10")
                .contains("anterior");
        assertThat(comentario)
                .as("y sigue diciendo lo que V10 decia, que era cierto y no se pierde")
                .contains("derivador")
                .contains("persona");
    }

    @Test
    @DisplayName("el indice de prefijo dice a QUE operadores sirve, que es lo que estaba flojo")
    void elIndiceDePrefijoDiceASuOperador() throws SQLException {
        String comentario = comentarioDelIndice("habilitacion_urbana_codigo_prefijo_ix");

        assertThat(comentario)
                .as(
                        "el comentario de V7 decia «la clase de operadores que el rango usa» sin"
                                + " decir cuales, y de ahi salio la lectura de #27 de que el indice"
                                + " no servia. Los operadores son estos y estan escritos")
                .contains("~>=~")
                .contains("~<~");
    }

    @Test
    @DisplayName("y candidato.predio_id nombra las DOS restricciones que hoy lo atan")
    void elComentarioDelPredioDelCandidatoNombraLasDos() throws SQLException {
        String comentario = comentarioDe("candidato", "predio_id");

        assertThat(comentario)
                .contains("candidato_omiso_sin_predio_ck")
                .contains("candidato_predio_de_la_clase_check");
    }

    // ------------------------------------------------------------------

    private static String comentarioDe(String tabla, String columna) throws SQLException {
        return unaCadena(
                "SELECT col_description(?::regclass, a.attnum)"
                        + "  FROM pg_attribute a"
                        + " WHERE a.attrelid = ?::regclass AND a.attname = ?",
                tabla,
                tabla,
                columna);
    }

    private static String comentarioDelIndice(String indice) throws SQLException {
        return unaCadena("SELECT obj_description(?::regclass, 'pg_class')", indice);
    }

    private static String unaCadena(String sql, String... parametros) throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER);
                PreparedStatement consulta = owner.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                consulta.setString(i + 1, parametros[i]);
            }
            try (ResultSet fila = consulta.executeQuery()) {
                assertThat(fila.next()).as("el objeto de «%s» tiene que existir", sql).isTrue();
                String valor = fila.getString(1);
                assertThat(valor)
                        .as(
                                "sin comentario no hay nada que medir, y esta prueba pasaria en"
                                        + " verde sobre el defecto exacto que existe para atrapar")
                        .isNotNull();
                return valor;
            }
        }
    }
}
