package kamayuk.catastro.urbano.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #4 — La zona: lo que rechaza al construirse y cuando rige. Sin Spring y sin base (regla 7). */
@DisplayName("#4 — La zona del plan de zonificacion")
class ZonaTest {

    private static final String POLIGONO =
            "MULTIPOLYGON(((-80.69 -5.27,-80.67 -5.27,-80.67 -5.25,-80.69 -5.25,-80.69 -5.27)))";

    private static final LocalDate ENERO = LocalDate.of(2026, 1, 1);
    private static final LocalDate JUNIO = LocalDate.of(2026, 6, 15);

    @Test
    @DisplayName("una zona sin poligono no cubre ningun suelo y no puede decidir nada")
    void sinPoligono() {
        assertThatThrownBy(() -> zona("  ", ENERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no cubre ningun suelo");
    }

    @Test
    @DisplayName("una zona que deja de regir antes de empezar se rechaza al construirse")
    void vigenciaAlReves() {
        assertThatThrownBy(() -> zona(POLIGONO, JUNIO, ENERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("antes de empezar");
    }

    @Test
    @DisplayName("un codigo mas largo que su columna se rechaza aqui, no en el INSERT")
    void codigoDemasiadoLargo() {
        assertThatThrownBy(
                        () ->
                                new Zona(
                                        null,
                                        "PDU-2026",
                                        "ORD-004",
                                        "R".repeat(21),
                                        "Residencial",
                                        POLIGONO,
                                        ENERO,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Excede 20 caracteres");
    }

    @Test
    @DisplayName("sin ordenanza no hay zona: negar una licencia sin norma es negarla sin motivo")
    void sinOrdenanza() {
        assertThatThrownBy(
                        () ->
                                new Zona(
                                        null,
                                        "PDU-2026",
                                        " ",
                                        "RDM",
                                        "Residencial",
                                        POLIGONO,
                                        ENERO,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("la ordenanza");
    }

    @Test
    @DisplayName("el ultimo dia de vigencia es un dia en que la zona rige, y el siguiente no")
    void vigenciaHastaEsInclusiva() {
        Zona cerrada = zona(POLIGONO, ENERO, LocalDate.of(2026, 6, 15));

        assertThat(cerrada.rigeEn(LocalDate.of(2026, 6, 15)))
                .as(
                        "escrito con isBefore, el dia del relevo del plan diria «ninguna zona» — y"
                                + " ese dia es justo cuando alguien lo mira")
                .isTrue();
        assertThat(cerrada.rigeEn(LocalDate.of(2026, 6, 16))).isFalse();
        assertThat(cerrada.rigeEn(LocalDate.of(2025, 12, 31))).isFalse();
    }

    @Test
    @DisplayName("una zona abierta rige desde su fecha y en adelante")
    void zonaAbierta() {
        Zona abierta = zona(POLIGONO, ENERO, null);

        assertThat(abierta.rigeEn(ENERO)).isTrue();
        assertThat(abierta.rigeEn(LocalDate.of(2099, 1, 1))).isTrue();
        assertThat(abierta.rigeEn(LocalDate.of(2025, 12, 31))).isFalse();
    }

    private static Zona zona(String wkt, LocalDate desde, LocalDate hasta) {
        return new Zona(null, "PDU-2026", "ORD-004", "RDM", "Residencial", wkt, desde, hasta);
    }
}
