package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import kamayuk.comun.verificaciones.AsercionesQueNoPuedenFallarTestBase;
import kamayuk.comun.verificaciones.RevisorDeAserciones.Censo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #724: ninguna asercion de AssertJ compara un {@code Optional} con algo que no lo es.
 *
 * <p>Recorre {@code src/test} de todos los modulos de <b>este</b> repositorio; el escaner y su
 * muestra viven en {@code comun-verificaciones}.
 *
 * <p>Y añade la que es de {@code sgtm} y no de la libreria: la premisa que sostiene la decision de
 * #724 —que {@code llave} es ambiguo POR NOMBRE— afirmada contra el arbol de este repositorio.
 */
@DisplayName("#724 — Aserciones que no pueden fallar")
class AsercionesQueNoPuedenFallarTest extends AsercionesQueNoPuedenFallarTestBase {

    @Test
    @DisplayName("el censo sigue diciendo que `llave` es ambiguo por nombre, tambien aqui")
    void elCensoSeparaLosDosLlave() throws IOException {
        // Lo que sostiene la decision de #724, medido contra el arbol real y no razonado: el
        // mismo nombre con dos tipos. Si alguien unificara los dos, esta prueba lo diria.
        //
        // Y ya dijo dos veces que el EJEMPLO envejece y la conclusion no. La primera fue #723,
        // que le hizo declarar `ParametroSinPublicar` a `ValorUnitarioSinParametrizar`. La segunda
        // es P5C: `DerechoSinParametrizar` es de `licencias`, que se quedo en `rentas`, asi que en
        // ESTE arbol el ejemplo del lado Optional es otro. Lo que no cambia —y es lo unico que la
        // prueba afirma— es que `llave` sigue siendo ambiguo por nombre y por eso hizo falta
        // censar por clase.
        Censo censo = censarDelDisco(fuentesJava(raizDelBackend()));

        assertThat(censo.nombresInequivocos())
                .as("`llave` es ambiguo por nombre; por eso hizo falta el censo por clase")
                .doesNotContain("llave");
        assertThat(censo.clasesConOptional("llave"))
                .as("las de la familia `ParametroSinPublicar` lo declaran Optional")
                .contains("ParametroAusente")
                .as(
                        "y `ParametroQueFalta` —la proyeccion HTTP del mismo discriminador— lo"
                                + " lleva como componente String anulable, que es el otro lado")
                .doesNotContain("ParametroQueFalta");
    }
}
