package kamayuk.catastro.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.json.SerializadorDeJson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #20: lo que se asienta en la bitacora es un objeto JSON, y no puede ser otra cosa.
 *
 * <p>Las dos columnas son {@code jsonb}. Lo que estas pruebas fijan es el comportamiento del que
 * cuelga que el {@code cast(… AS jsonb)} de {@code AuditoriaJdbc} no pueda fallar: que escapar lo
 * haga el serializador y no un {@code escapar()} que hay que acordarse de llamar, y que lo
 * producido se vuelva a leer como objeto antes de aceptarlo.
 */
@DisplayName("#20 — Los datos de un asiento")
class DatosDeAuditoriaTest {

    @Test
    @DisplayName("escapar lo hace el serializador: comilla, barra y caracteres de control")
    void escaparLoHaceElSerializador() {
        // Los dos escapar() que #20 borro cubrian \\ y " y NINGUN caracter de control, asi que un
        // motivo con un salto de linea rompia el cast igual en el que «si escapaba».
        DatosDeAuditoria datos =
                DatosDeAuditoria.campos()
                        .mas("inspector", "Juan \"El Tuerto\" Perez")
                        .mas("motivo", "es un toldo,\n no una edificacion\t")
                        .mas("ruta", "C:\\evidencias\\foto.jpg")
                        .datos();

        assertThat(SerializadorDeJson.esObjetoJson(datos.json())).isTrue();
        assertThat(datos.json())
                .contains("\\\"El Tuerto\\\"")
                .contains("\\n")
                .contains("\\t")
                .contains("C:\\\\evidencias\\\\foto.jpg");
    }

    @Test
    @DisplayName("los campos salen en el orden en que se escriben, y el nulo es null JSON")
    void losCamposSalenEnOrden() {
        // Ordenado a proposito: la bitacora se lee comparando el antes con el despues, y dos
        // objetos con los mismos campos en distinto orden obligan a leer los dos enteros.
        DatosDeAuditoria datos =
                DatosDeAuditoria.campos()
                        .mas("codigo", "V-1")
                        .mas("zona", null)
                        .mas("activo", true)
                        .datos();

        assertThat(datos.json()).isEqualTo("{\"codigo\":\"V-1\",\"zona\":null,\"activo\":true}");
    }

    @Test
    @DisplayName("un area sale sin unidad y una medida con ella, que es lo que #607 decide")
    void elAreaSaleSinUnidadYLaMedidaConElla() {
        DatosDeAuditoria datos =
                DatosDeAuditoria.campos()
                        .mas("areaTerreno", AreaM2.de("120.00"))
                        .mas("longitud", Medida.enMetrosLineales("18.50"))
                        .datos();

        assertThat(datos.json())
                .as(
                        "un AreaM2 lleva la unidad en la cabecera de su columna y una Medida la"
                                + " lleva dentro, porque ahi la unidad ES parte del dato")
                .isEqualTo("{\"areaTerreno\":\"120.00\",\"longitud\":\"18.50 ML\"}");
    }

    @Test
    @DisplayName("un campo repetido no se decide en silencio: falla")
    void unCampoRepetidoFalla() {
        // Un objeto JSON con la clave repetida es legal para el motor y ambiguo para quien lo lea:
        // la biblioteca del lector decide cual gana.
        assertThatThrownBy(
                        () ->
                                DatosDeAuditoria.campos()
                                        .mas("codigo", "V-1")
                                        .mas("codigo", "V-2")
                                        .datos())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("codigo");
    }

    @Test
    @DisplayName("y un asiento sin ningun campo no explica nada")
    void unAsientoVacioFalla() {
        assertThatThrownBy(() -> DatosDeAuditoria.campos().datos())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("EL CONTRASTE: la prosa que reventaba el cast no es un objeto JSON")
    void laProsaNoEsUnObjetoJson() {
        // Las dos cadenas exactas que `src/main` pasaba antes de #20, y la tercera —un escalar—
        // que el motor SI aceptaria y que tampoco sirve: una cadena suelta dentro de la columna no
        // se puede consultar por campo ni comparar con la del dia anterior.
        assertThat(
                        SerializadorDeJson.esObjetoJson(
                                "Longitud PROPUESTA, derivada del corte contra el eje de calzada"))
                .isFalse();
        assertThat(
                        SerializadorDeJson.esObjetoJson(
                                "{\"numero\":\"ACTA-1\",\"inspector\":\"Juan"
                                        + " \"El Tuerto\" Perez\"}"))
                .as("el JSON compuesto a mano con una comilla dentro tampoco lo es")
                .isFalse();
        assertThat(SerializadorDeJson.esObjetoJson("\"Longitud PROPUESTA\""))
                .as("un escalar es JSON valido y no es lo que la bitacora asienta")
                .isFalse();
        assertThat(SerializadorDeJson.esObjetoJson("{\"codigo\":\"V-1\"}")).isTrue();
    }
}
