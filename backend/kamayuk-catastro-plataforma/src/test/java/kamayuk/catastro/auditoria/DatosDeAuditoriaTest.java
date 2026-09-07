package kamayuk.catastro.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Dinero;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * #20: lo que va a las dos columnas {@code jsonb} es JSON <b>por construccion</b>.
 *
 * <p>Sin base de datos a proposito: lo que se mide aqui es que el tipo no pueda producir otra cosa.
 * Que el resultado llegue entero hasta el {@code cast(... AS jsonb)} lo miden {@code
 * AuditoriaJdbcTest} y las dos pruebas de caso de uso contra PostgreSQL real.
 */
@DisplayName("#20 — DatosDeAuditoria")
class DatosDeAuditoriaTest {

    @Nested
    @DisplayName("escapa lo que un escape a mano se dejaba")
    class LoQueElEscapeAManoNoCubria {

        @Test
        @DisplayName("la comilla y la barra, que los dos escapar() si cubrian")
        void laComillaYLaBarra() {
            assertThat(
                            DatosDeAuditoria.objeto()
                                    .campo("inspector", "Juan \"El Tuerto\" Perez \\ C:\\temp")
                                    .componer()
                                    .json())
                    .isEqualTo("{\"inspector\":\"Juan \\\"El Tuerto\\\" Perez \\\\ C:\\\\temp\"}");
        }

        @Test
        @DisplayName("y los caracteres de control, que NINGUNO de los dos cubria")
        void losCaracteresDeControl() {
            // Los dos `escapar()` borrados hacian `.replace("\\","\\\\").replace("\"","\\\"")` y
            // nada mas, asi que un nombre de campania con un salto de linea producia texto que la
            // columna rechaza — y rompia igual el que «si escapaba». Es la mitad del defecto que
            // no se ve leyendo el codigo, porque el escape esta ahi.
            assertThat(
                            DatosDeAuditoria.objeto()
                                    .campo("nombre", "Cercado\nSector 2\ttarde")
                                    .componer()
                                    .json())
                    .isEqualTo("{\"nombre\":\"Cercado\\nSector 2\\ttarde\"}");
        }
    }

    @Nested
    @DisplayName("admite solo lo que la bitacora sabe asentar")
    class SoloLoQueSabeAsentar {

        @Test
        @DisplayName("un area sale como cadena y con la cifra sola, igual que por HTTP (#607)")
        void unAreaSaleComoElSerializadorLaEscribe() {
            assertThat(
                            DatosDeAuditoria.objeto()
                                    .campo("areaTerreno", AreaM2.de("120.00"))
                                    .campo("valor", Dinero.de("1500.50"))
                                    .componer()
                                    .json())
                    .as("es el mismo modulo que usa la capa web: una definicion, no dos")
                    .isEqualTo("{\"areaTerreno\":\"120.00\",\"valor\":\"1500.50\"}");
        }

        @Test
        @DisplayName("los campos salen en el orden en que se declararon, y el nulo es null JSON")
        void elOrdenYElNulo() {
            assertThat(
                            DatosDeAuditoria.objeto()
                                    .campo("codigo", "S-01")
                                    .campo("zona", null)
                                    .campo("activo", true)
                                    .campo("umbral", new BigDecimal("0.70"))
                                    .campo("fin", (LocalDate) null)
                                    .campo("inicio", LocalDate.of(2026, 1, 31))
                                    .campo("estado", Operacion.ALTA)
                                    .componer()
                                    .json())
                    .as(
                            "una zona ausente se asienta como null JSON y no como la cadena «null»,"
                                    + " que es lo que hacia el textoOpcional escrito a mano")
                    .isEqualTo(
                            "{\"codigo\":\"S-01\",\"zona\":null,\"activo\":true,\"umbral\":0.70,"
                                    + "\"fin\":null,\"inicio\":\"2026-01-31\",\"estado\":\"ALTA\"}");
        }

        @Test
        @DisplayName("un objeto anidado se compone con otra Composicion, sin cerrarla")
        void unObjetoAnidado() {
            assertThat(
                            DatosDeAuditoria.objeto()
                                    .campo("estado", "DESCARTADO")
                                    .campo(
                                            "descarte",
                                            DatosDeAuditoria.objeto()
                                                    .campo("etapa", "GABINETE")
                                                    .campo("motivo", "Area \"revisada\""))
                                    .componer()
                                    .json())
                    .isEqualTo(
                            "{\"estado\":\"DESCARTADO\",\"descarte\":{\"etapa\":\"GABINETE\","
                                    + "\"motivo\":\"Area \\\"revisada\\\"\"}}");
        }

        @Test
        @DisplayName("y una entidad entera se RECHAZA nombrando su tipo")
        void unaEntidadEnteraSeRechaza() {
            // Jackson la serializaria sin protestar: el JSON seria valido y el asiento, ilegible
            // —media tabla dentro de una columna que alguien tiene que poder leer—. Que el tipo
            // diga que no es la mitad de «no admite lo que la columna no admite».
            record Predio(long id, String codigo) {}

            assertThatThrownBy(
                            () ->
                                    DatosDeAuditoria.objeto()
                                            .campo("predio", new Predio(1L, "P-1"))
                                            .componer())
                    .as(
                            "sin la lista, Jackson lo escribiria como"
                                    + " {\"predio\":{\"id\":1,\"codigo\":\"P-1\"}}: JSON"
                                    + " valido y asiento ilegible, con media tabla dentro de una"
                                    + " columna que alguien tiene que poder leer")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no sabe asentar")
                    .hasMessageContaining("Predio")
                    .hasMessageContaining("predio");
        }

        @Test
        @DisplayName("y un campo repetido tambien: el segundo taparia al primero en silencio")
        void unCampoRepetidoSeRechaza() {
            assertThatThrownBy(
                            () ->
                                    DatosDeAuditoria.objeto()
                                            .campo("codigo", "S-01")
                                            .campo("codigo", "S-02"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ya esta en este asiento");
        }

        @Test
        @DisplayName("y unos datos ya compuestos, con el remedio dentro del mensaje")
        void unosDatosYaCompuestosSeRechazan() {
            DatosDeAuditoria cerrados =
                    DatosDeAuditoria.objeto().campo("etapa", "CAMPO").componer();

            assertThatThrownBy(() -> DatosDeAuditoria.objeto().campo("descarte", cerrados))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sin cerrarla con componer()");
        }
    }

    @Test
    @DisplayName("no hay ninguna fabrica que acepte texto: el defecto no tiene puerta de atras")
    void noHayFabricaQueAcepteTexto() {
        // La afirmacion central de AC-2 hecha prueba. Un `deJson(String)` —o un constructor
        // publico— devolveria el defecto entero: seria la unica linea que alguien copiaria el dia
        // que tuviera prisa, y volveriamos a tener texto libre en una columna jsonb.
        assertThat(java.util.Arrays.stream(DatosDeAuditoria.class.getConstructors()))
                .as("el constructor es privado")
                .isEmpty();
        assertThat(
                        java.util.Arrays.stream(DatosDeAuditoria.class.getMethods())
                                .filter(m -> java.lang.reflect.Modifier.isStatic(m.getModifiers()))
                                .filter(m -> m.getReturnType() != void.class)
                                .map(java.lang.reflect.Method::getName))
                .as("la unica fabrica publica es la que empieza una composicion")
                .containsExactly("objeto");
    }
}
