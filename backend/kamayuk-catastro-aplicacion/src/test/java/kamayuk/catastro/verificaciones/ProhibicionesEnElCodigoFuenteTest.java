package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import kamayuk.comun.verificaciones.ProhibicionesEnElCodigoFuenteTestBase;
import kamayuk.comun.verificaciones.RevisorDeCodigoFuente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las prohibiciones de texto de ARQ-04 §2, sobre el codigo de {@code sgtm}.
 *
 * <p>Hereda de {@code comun-verificaciones} el escaner y las pruebas que lo demuestran, y añade las
 * dos que son <b>de este repositorio</b>: el censo de las clases que componen el area a mano —esa
 * lista es suya, no de la libreria— y la celda del historial, que se afirma leyendo una clase de
 * produccion de {@code kamayuk-catastro-fiscalizacion}.
 */
@DisplayName("ARQ-04 §2 — Prohibiciones en el codigo fuente")
class ProhibicionesEnElCodigoFuenteTest extends ProhibicionesEnElCodigoFuenteTestBase {

    @Test
    @DisplayName("las dos clases de catastro que componen el area a mano, una a una")
    void elCensoDeLasClasesQueComponenElArea() {
        // La misma linea, byte a byte, en dos archivos: en uno es un hallazgo y en el otro no.
        // Lo que decide es el NOMBRE DE LA CLASE, y por eso la lista se escribe por clase y no
        // por paquete: anadir una tercera es una linea visible en el diff.
        //
        // En el monolito eran seis. Aqui son DOS porque las otras cuatro son de `rentas` y de
        // `licencias`: la lista es la de este sistema, y dejar en ella una clase que no existe
        // seria una entrada muerta, que es justo el defecto que esta prueba mide dos metodos mas
        // abajo para la celda del historial.
        String fuente =
                """
                final class Modelo {
                    static Tabla de(Fue fue) {
                        return Campo.de("Area del terreno (m2)",
                                fue.areaTerreno().valor().toPlainString());
                    }
                }
                """;

        assertThat(RevisorDeCodigoFuente.revisarAreas("UnRecursoCualquiera.java", fuente))
                .as("fuera de la lista, la misma linea es un hallazgo")
                .hasSize(1);
        assertThat(
                        RevisorDeCodigoFuente.revisarAreas(
                                "ModeloDeLaFichaDelContribuyente.java", fuente))
                .as("el papel no tiene serializador y la unidad va en el rotulo de la fila")
                .isEmpty();
        assertThat(new ConfiguracionDeCatastro().componenElAreaAManoConMotivo())
                .as(
                        "las dos de hoy: el modelo del papel de la ficha y la descripcion de"
                                + " auditoria del versionado. La columna JSON de la bitacora SI"
                                + " sale por HTTP —la publica verbatim—, asi que el motivo de la"
                                + " segunda no es «no llega al cliente» sino que ahi el area no es"
                                + " un campo tipado sino texto libre, y se escribe sin la unidad"
                                + " para que diga lo mismo que el resto")
                .containsExactlyInAnyOrder(
                        "ModeloDeLaFichaDelContribuyente", "ActualizarFichaCatastral");
    }

    @Test
    @DisplayName("la descripcion del versionado esta en la lista, y la celda del historial no")
    void laDescripcionDelVersionadoEstaYLaCeldaDelHistorialNoPuedeEstar() throws IOException {
        // La otra mitad de #607, adaptada a lo que ESTE sistema tiene despues de P5C.
        //
        // `ActualizarFichaCatastral` escribe «120.00 m2» dentro de la descripcion que va a la
        // columna JSON de la auditoria, y esa columna SI sale por HTTP. Por eso esta en la lista
        // con su motivo: ahi el area no es un campo tipado sino una instantanea de texto libre.
        //
        // Y la celda del historial de liquidaciones —la otra excepcion legitima de #607— NO puede
        // estar aqui, y no por criterio sino porque su clase es de `fiscalizacion`, que se quedo
        // en `rentas`. Una entrada muerta en una lista de excepciones es exactamente el defecto
        // que esa lista existe para no tener, asi que esta prueba lo afirma en las dos
        // direcciones.
        Path descripcion =
                raizDelBackend()
                        .resolve(
                                "kamayuk-catastro-catastro/src/main/java/kamayuk/catastro/catastro")
                        .resolve("aplicacion/ActualizarFichaCatastral.java");

        assertThat(descripcion)
                .as("la clase tiene que existir para poder afirmar esto de ella")
                .exists();

        String fuente = Files.readString(descripcion, StandardCharsets.UTF_8);

        assertThat(RevisorDeCodigoFuente.revisarAreas("UnRecursoCualquiera.java", fuente))
                .as("fuera de la lista, lo que esa clase escribe SI seria un hallazgo")
                .isNotEmpty();
        assertThat(RevisorDeCodigoFuente.revisarAreas(descripcion.getFileName().toString(), fuente))
                .as("y dentro de la lista, no")
                .isEmpty();

        assertThat(new ConfiguracionDeCatastro().componenElAreaAManoConMotivo())
                .as("la celda del historial es de `fiscalizacion`, que no vive en este sistema")
                .doesNotContain("DiferenciaEntreLiquidaciones");
    }
}
