package kamayuk.catastro.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import kamayuk.catastro.dominio.Medida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Los invariantes del frente: su unidad, su longitud y el acto que la confirma (#7, ADR-0021).
 *
 * <p>Sin Spring y sin base de datos (regla 7).
 */
@DisplayName("#7 — El frente de un predio")
class FrenteDelPredioTest {

    private static FrenteDelPredio frente(EstadoDeLaLongitud estado, String quien, Instant cuando) {
        return new FrenteDelPredio(
                7L,
                100L,
                200L,
                "V-01",
                "Avenida Grau",
                "LINESTRING(-80.7 -4.9, -80.7002 -4.9)",
                Medida.enMetrosLineales("18.50"),
                estado,
                true,
                "101",
                null,
                quien,
                cuando);
    }

    @Nested
    @DisplayName("la longitud")
    class LaLongitud {

        @Test
        @DisplayName("va en metros lineales, y unos metros cuadrados se rechazan")
        void vaEnMetrosLineales() {
            assertThatThrownBy(
                            () ->
                                    new FrenteDelPredio(
                                            null,
                                            100L,
                                            200L,
                                            "V-01",
                                            "Avenida Grau",
                                            null,
                                            Medida.enMetrosCuadrados("18.50"),
                                            EstadoDeLaLongitud.PROPUESTA,
                                            false,
                                            null,
                                            null,
                                            null,
                                            null))
                    .as(
                            "el barrido se determina sobre metros LINEALES y el recojo sobre"
                                    + " CUADRADOS: leer unos por otros no falla, cobra otra cosa")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("va en ML")
                    .hasMessageContaining("M2");
        }

        @Test
        @DisplayName("no puede ser cero: un frente de cero metros no es un frente")
        void noPuedeSerCero() {
            assertThatThrownBy(
                            () ->
                                    new FrenteDelPredio(
                                            null,
                                            100L,
                                            200L,
                                            "V-01",
                                            "Avenida Grau",
                                            null,
                                            Medida.enMetrosLineales("0.00"),
                                            EstadoDeLaLongitud.PROPUESTA,
                                            false,
                                            null,
                                            null,
                                            null,
                                            null))
                    .as("un cero es indistinguible de un frente que nadie midio")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no es un frente");
        }
    }

    @Nested
    @DisplayName("confirmar es un acto (regla 10, ADR-0021)")
    class ConfirmarEsUnActo {

        @Test
        @DisplayName("una propuesta no lleva quien ni cuando")
        void unaPropuestaNoLlevaAutor() {
            FrenteDelPredio propuesto = frente(EstadoDeLaLongitud.PROPUESTA, null, null);

            assertThat(propuesto.estaConfirmada()).isFalse();
            assertThat(propuesto.confirmadoPor()).isNull();
        }

        @Test
        @DisplayName("una confirmada lleva las dos cosas")
        void unaConfirmadaLlevaAutorYHora() {
            FrenteDelPredio confirmado =
                    frente(
                            EstadoDeLaLongitud.CONFIRMADA,
                            "tecnico.catastro",
                            Instant.parse("2026-09-06T10:00:00Z"));

            assertThat(confirmado.estaConfirmada()).isTrue();
            assertThat(confirmado.confirmadoPor()).isEqualTo("tecnico.catastro");
        }

        @Test
        @DisplayName("CONFIRMADA sin autor no se puede construir: seria una cifra que nadie firmo")
        void confirmadaSinAutorNoSePuedeConstruir() {
            assertThatThrownBy(() -> frente(EstadoDeLaLongitud.CONFIRMADA, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Confirmar es un ACTO");
        }

        @Test
        @DisplayName("y PROPUESTA con autor tampoco: el acto o esta entero o no esta")
        void propuestaConAutorTampoco() {
            assertThatThrownBy(
                            () ->
                                    frente(
                                            EstadoDeLaLongitud.PROPUESTA,
                                            "tecnico.catastro",
                                            Instant.parse("2026-09-06T10:00:00Z")))
                    .as(
                            "es la misma igualdad que `frente_confirmacion_ck` de V10, y se"
                                    + " comprueba en las dos direcciones porque una sola dejaria"
                                    + " pasar la mitad")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Confirmar es un ACTO");
        }
    }
}
