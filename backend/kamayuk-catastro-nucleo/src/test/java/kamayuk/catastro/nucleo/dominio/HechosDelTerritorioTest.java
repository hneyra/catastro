package kamayuk.catastro.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Los tres hechos nuevos del buzon: su forma y su identidad (#7, AC 4 y AC 5).
 *
 * <h2>Lo que estas pruebas existen para impedir</h2>
 *
 * <p>Los dos invariantes cruzados de {@code catastro_evento} estaban escritos <b>en negativo</b>
 * —«el que no es {@code PREDIO_PROYECTADO} lleva ejercicio», «el que no es {@code CORRIDA_CERRADA}
 * lleva predio»—. Con tres tipos eran ciertos; con seis son falsos, y de la peor manera: obligan a
 * una manzana a nombrar un ejercicio que no tiene y un predio que no es suyo. Aqui se fija la forma
 * de los seis, uno a uno.
 *
 * <p>Y la identidad: tres tipos se derivan del CONTENIDO y dos de la IDENTIDAD, y esa diferencia es
 * lo que separa «este hecho ya lo mande» de «alguien esta reescribiendo lo que otro firmo».
 */
@DisplayName("#7 — Los hechos del territorio en el buzon")
class HechosDelTerritorioTest {

    private static final String HUELLA = "a".repeat(64);
    private static final long MUNI = 1L;

    private static HechoDeCatastro hecho(
            TipoDeEventoDeCatastro tipo, Long predioId, Integer ejercicio) {
        return new HechoDeCatastro(UUID.randomUUID(), tipo, predioId, ejercicio, "{}", HUELLA);
    }

    @Nested
    @DisplayName("la forma de cada tipo (AC 4)")
    class LaFormaDeCadaTipo {

        @Test
        @DisplayName("una manzana no lleva ejercicio ni predio")
        void laManzanaNoLlevaNinguno() {
            assertThatCode(() -> hecho(TipoDeEventoDeCatastro.MANZANA_PUBLICADA, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("y con ejercicio se rechaza: un frente y una manzana no son de ningun ano")
        void laManzanaConEjercicioSeRechaza() {
            assertThatThrownBy(() -> hecho(TipoDeEventoDeCatastro.MANZANA_PUBLICADA, null, 2026))
                    .as(
                            "es el CHECK que V5 tenia escrito en negativo: «lo que no es"
                                    + " PREDIO_PROYECTADO lleva ejercicio» obligaba a la manzana a"
                                    + " nombrar uno")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Solo la valuacion y el cierre de corrida");
        }

        @Test
        @DisplayName("y con predio tambien: una manzana no habla de un predio")
        void laManzanaConPredioSeRechaza() {
            assertThatThrownBy(() -> hecho(TipoDeEventoDeCatastro.MANZANA_PUBLICADA, 100L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no admite ese predio");
        }

        @Test
        @DisplayName("un frente lleva predio y no lleva ejercicio")
        void elFrenteLlevaPredioYNoEjercicio() {
            assertThatCode(() -> hecho(TipoDeEventoDeCatastro.FRENTE_PUBLICADO, 100L, null))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> hecho(TipoDeEventoDeCatastro.FRENTE_PUBLICADO, null, null))
                    .as("los frentes son DE un predio: sin el, el receptor no sabe de quien son")
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> hecho(TipoDeEventoDeCatastro.FRENTE_PUBLICADO, 100L, 2026))
                    .as(
                            "el frente medido en 2026 sigue siendo el mismo en 2027; versionarlo"
                                    + " por ano seria una decision tributaria (ADR-0024)")
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("un hallazgo firme admite predio Y no-predio, y esa es la unica excepcion")
        void elHallazgoAdmiteLasDosCosas() {
            assertThatCode(() -> hecho(TipoDeEventoDeCatastro.HALLAZGO_FIRME, 100L, null))
                    .as("un SUBVALUADOR contrasta un predio concreto")
                    .doesNotThrowAnyException();
            assertThatCode(() -> hecho(TipoDeEventoDeCatastro.HALLAZGO_FIRME, null, null))
                    .as(
                            "y un OMISO_CATASTRAL es —por definicion— lo que NO tiene predio:"
                                    + " exigirlo dejaria fuera del buzon justo la mitad que a `rentas`"
                                    + " mas le interesa")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("y los tres tipos de C-8 siguen valiendo exactamente lo que valian")
        void losTresDeCochoNoCambian() {
            assertThatCode(() -> hecho(TipoDeEventoDeCatastro.PREDIO_PROYECTADO, 100L, null))
                    .doesNotThrowAnyException();
            assertThatCode(() -> hecho(TipoDeEventoDeCatastro.VALUACION_PUBLICADA, 100L, 2026))
                    .doesNotThrowAnyException();
            assertThatCode(() -> hecho(TipoDeEventoDeCatastro.CORRIDA_CERRADA, null, 2026))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> hecho(TipoDeEventoDeCatastro.CORRIDA_CERRADA, 100L, 2026))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("de donde sale la identidad (AC 5)")
    class DeDondeSaleLaIdentidad {

        @Test
        @DisplayName("la manzana y el frente, del CONTENIDO: mismo contenido, misma identidad")
        void laManzanaYElFrenteSalenDelContenido() {
            assertThat(IdentidadDelEvento.deUnaManzanaPublicada(MUNI, 5L, HUELLA))
                    .isEqualTo(IdentidadDelEvento.deUnaManzanaPublicada(MUNI, 5L, HUELLA));
            assertThat(IdentidadDelEvento.deLosFrentesDeUnPredio(MUNI, 100L, HUELLA))
                    .isEqualTo(IdentidadDelEvento.deLosFrentesDeUnPredio(MUNI, 100L, HUELLA));
        }

        @Test
        @DisplayName("y otro contenido es OTRA identidad: republicar lo que cambio si es un hecho")
        void otroContenidoEsOtraIdentidad() {
            assertThat(IdentidadDelEvento.deLosFrentesDeUnPredio(MUNI, 100L, HUELLA))
                    .isNotEqualTo(
                            IdentidadDelEvento.deLosFrentesDeUnPredio(MUNI, 100L, "b".repeat(64)));
        }

        @Test
        @DisplayName("el hallazgo firme, de la IDENTIDAD: no depende de su contenido")
        void elHallazgoSaleDeSuIdentidad() {
            // Es lo contrario de los otros dos, y es deliberado: un hallazgo lo firmo una persona.
            // Que la misma identidad vuelva con otra area verificada no es un hecho nuevo, y por
            // eso el buzon lo PARA con HechoSelladoReescrito en vez de aplicarlo encima.
            assertThat(IdentidadDelEvento.deUnHallazgoFirme(MUNI, 42L))
                    .isEqualTo(IdentidadDelEvento.deUnHallazgoFirme(MUNI, 42L));
            assertThat(IdentidadDelEvento.deUnHallazgoFirme(MUNI, 42L))
                    .isNotEqualTo(IdentidadDelEvento.deUnHallazgoFirme(MUNI, 43L));
        }

        @Test
        @DisplayName("ningun tipo choca con otro con los mismos numeros")
        void ningunTipoChocaConOtro() {
            // El prefijo del nombre derivado lleva el tipo, y por eso «la manzana 100» y «los
            // frentes del predio 100» con la misma huella no son el mismo evento. Sin el, dos
            // hechos distintos se deduplicarian entre si y uno de los dos no llegaria nunca.
            assertThat(IdentidadDelEvento.deUnaManzanaPublicada(MUNI, 100L, HUELLA))
                    .isNotEqualTo(IdentidadDelEvento.deLosFrentesDeUnPredio(MUNI, 100L, HUELLA));
            assertThat(IdentidadDelEvento.deUnaManzanaPublicada(MUNI, 100L, HUELLA))
                    .isNotEqualTo(IdentidadDelEvento.deUnPredioProyectado(MUNI, 100L, HUELLA));
        }

        @Test
        @DisplayName("y dos municipalidades con el mismo numero no comparten identidad")
        void dosMunicipalidadesNoCompartenIdentidad() {
            assertThat(IdentidadDelEvento.deUnHallazgoFirme(1L, 42L))
                    .isNotEqualTo(IdentidadDelEvento.deUnHallazgoFirme(2L, 42L));
        }

        @Test
        @DisplayName("los uuid derivados son de la version 8 de RFC 9562, y no v5")
        void losUuidSonDeLaVersionOcho() {
            // El comentario de V5 decia «UUID v5 (RFC 4122 §4.3)» y el codigo dice lo contrario.
            // Esto lo fija sobre el valor y no sobre el comentario: un uuid v5 y este son bytes
            // distintos para el mismo nombre, asi que quien reprodujera la derivacion leyendo
            // aquel comentario obtendria OTRA identidad para el mismo hecho.
            UUID identidad = IdentidadDelEvento.deUnHallazgoFirme(MUNI, 42L);

            assertThat(identidad.version()).isEqualTo(8);
            assertThat(identidad.variant()).isEqualTo(2);
        }
    }
}
