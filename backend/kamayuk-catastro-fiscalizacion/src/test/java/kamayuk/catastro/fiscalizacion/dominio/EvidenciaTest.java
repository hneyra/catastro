package kamayuk.catastro.fiscalizacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0035 punto 3 y AC 5 de #6: la huella y los DOS relojes. */
@DisplayName("ADR-0035 — La evidencia, con su huella y sus dos relojes")
class EvidenciaTest {

    private static final Instant EN_LA_MANANA = Instant.parse("2026-09-05T09:00:00Z");
    private static final Instant POR_LA_TARDE = Instant.parse("2026-09-05T18:00:00Z");
    private static final String HUELLA = "a".repeat(64);

    private static Evidencia evidencia(Instant capturado, Instant recibido) {
        return new Evidencia(
                null,
                1L,
                TipoDeEvidencia.FOTO,
                HuellaDeEvidencia.de(HUELLA),
                "s3://evidencias/foto-01.jpg",
                capturado,
                recibido,
                "tableta-01");
    }

    @Test
    @DisplayName("los dos relojes se guardan por separado, y su desfase se puede medir")
    void losDosRelojes() {
        Evidencia sinCobertura = evidencia(EN_LA_MANANA, POR_LA_TARDE);

        assertThat(sinCobertura.capturadoEn()).isEqualTo(EN_LA_MANANA);
        assertThat(sinCobertura.recibidoEn()).isEqualTo(POR_LA_TARDE);
        assertThat(sinCobertura.desfaseDeLosRelojes().toHours())
                .as(
                        "una brigada sin cobertura sube por la tarde lo que fotografio por la"
                                + " manana; con un solo reloj esa foto pasaria a estar tomada por la"
                                + " tarde sin que nadie lo decidiera")
                .isEqualTo(9);
    }

    @Test
    @DisplayName("un reloj de aparato ADELANTADO da desfase negativo, y no se corrige")
    void elRelojAdelantadoTambienEsUnDato() {
        Evidencia adelantada = evidencia(POR_LA_TARDE, EN_LA_MANANA);

        assertThat(adelantada.desfaseDeLosRelojes().isNegative())
                .as(
                        "rechazarla perderia la foto y ademas el dato de que ese aparato tiene el"
                                + " reloj mal, que es lo que hay que poder mirar")
                .isTrue();
    }

    @Test
    @DisplayName("la huella es un sha256 de verdad, y no una cadena cualquiera")
    void laHuellaEsUnSha256() {
        assertThatThrownBy(() -> HuellaDeEvidencia.de("no-es-una-huella"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64 hexadigitos");
        assertThat(HuellaDeEvidencia.de(HUELLA.toUpperCase(java.util.Locale.ROOT)).valor())
                .as("se normaliza a minuscula: dos grafias de la misma huella son la misma huella")
                .isEqualTo(HUELLA);
    }
}
