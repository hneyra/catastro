package kamayuk.catastro.fiscalizacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0035 punto 5 y AC 7 de #6: la tasa de descarte, por etapa. */
@DisplayName("ADR-0035 — La tasa de descarte se cuenta por compuerta")
class TasaDeDescarteTest {

    @Test
    @DisplayName("las dos compuertas se cuentan por separado, y con su denominador")
    void cadaCompuertaSuCifra() {
        TasaDeDescarte tasa = new TasaDeDescarte(1000, 700, 200, 50);

        assertThat(tasa.descartadosEn(EtapaDeVerificacion.GABINETE)).isEqualTo(700);
        assertThat(tasa.descartadosEn(EtapaDeVerificacion.CAMPO)).isEqualTo(200);
        assertThat(tasa.loQuePasoGabinete())
                .as(
                        "es el denominador de los descartes de campo: sin el, 700 y 200 no se"
                                + " pueden comparar entre si porque campo solo ve lo que gabinete"
                                + " admitio")
                .isEqualTo(300);
        assertThat(tasa.enCurso()).isEqualTo(50);
    }

    @Test
    @DisplayName("una campania recien detectada tiene todo en curso y nada descartado")
    void recienDetectada() {
        TasaDeDescarte tasa = new TasaDeDescarte(42, 0, 0, 0);

        assertThat(tasa.enCurso()).isEqualTo(42);
        assertThat(tasa.loQuePasoGabinete()).isEqualTo(42);
    }
}
