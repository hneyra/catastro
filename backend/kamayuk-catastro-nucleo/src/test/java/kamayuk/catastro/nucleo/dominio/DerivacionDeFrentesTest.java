package kamayuk.catastro.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La constancia de la derivacion, y por que un cero sin motivo no vale (#7, AC 3).
 *
 * <p>«Este predio no da a ninguna calle» y «a este predio no le ha pasado el derivador» son la
 * misma lista vacia y dos problemas distintos. Lo que este tipo impide es que se guarde el primero
 * sin decir cual de los dos es.
 */
@DisplayName("#7 — La constancia de la derivacion de frentes")
class DerivacionDeFrentesTest {

    private static final Instant CUANDO = Instant.parse("2026-09-06T10:00:00Z");

    @Test
    @DisplayName("una derivacion que propuso frentes no tiene motivo que dar")
    void conFrentesNoHayMotivo() {
        DerivacionDeFrentes derivacion = new DerivacionDeFrentes(100L, CUANDO, 2, null);

        assertThat(derivacion.propuestos()).isEqualTo(2);
        assertThat(derivacion.motivo()).isNull();
    }

    @Test
    @DisplayName("una que no propuso ninguno dice por que")
    void sinFrentesHayQueDecirPorQue() {
        DerivacionDeFrentes derivacion =
                new DerivacionDeFrentes(100L, CUANDO, 0, "El lote no tiene poligono");

        assertThat(derivacion.propuestos()).isZero();
        assertThat(derivacion.motivo()).isNotNull();
    }

    @Test
    @DisplayName("cero sin motivo no se puede construir: nadie revisa un cero")
    void ceroSinMotivoNoSePuedeConstruir() {
        assertThatThrownBy(() -> new DerivacionDeFrentes(100L, CUANDO, 0, null))
                .as(
                        "es el criterio de #6 AC 8 aplicado aqui: un cero sin motivo se lee como"
                                + " «no da a ninguna calle», que de un predio urbano es falso")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dice por que");
    }

    @Test
    @DisplayName("y con frentes Y motivo tampoco: seria decir dos cosas a la vez")
    void conFrentesYMotivoTampoco() {
        assertThatThrownBy(
                        () -> new DerivacionDeFrentes(100L, CUANDO, 2, "El lote no tiene poligono"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene motivo que dar");
    }
}
