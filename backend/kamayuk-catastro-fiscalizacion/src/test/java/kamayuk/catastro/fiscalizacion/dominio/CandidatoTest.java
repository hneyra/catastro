package kamayuk.catastro.fiscalizacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** ADR-0035 puntos 1 y 5: lo que la maquina sospecha, y el descarte que se conserva. */
@DisplayName("ADR-0035 — El candidato y las dos compuertas")
class CandidatoTest {

    private static final Instant AHORA = Instant.parse("2026-09-05T15:00:00Z");
    private static final String INSUMOS = "{\"origen\":\"ORTOFOTO\"}";

    private static Candidato detectado(ClaseDeHallazgo clase, Long predioId) {
        return Candidato.detectado(
                1L, predioId, clase, OrigenDelCandidato.ORTOFOTO, Score.de("0.9"), INSUMOS, null);
    }

    @Nested
    @DisplayName("Lo que la maquina puede sospechar")
    class LoQueSeSospecha {

        @Test
        @DisplayName("un omiso catastral NO tiene predio, y por eso predio_id es nulable")
        void elOmisoNoTienePredio() {
            Candidato omiso = detectado(ClaseDeHallazgo.OMISO_CATASTRAL, null);

            assertThat(omiso.predioId())
                    .as(
                            "hay techo en la ortofoto y no hay fila de predio: exigirlo obligaria a"
                                    + " inventar un predio para poder sospechar que falta")
                    .isNull();
            assertThat(omiso.estado()).isEqualTo(EstadoDelCandidato.DETECTADO);
        }

        @Test
        @DisplayName("un subvaluador SIN predio no se puede escribir")
        void elSubvaluadorExigePredio() {
            assertThatThrownBy(() -> detectado(ClaseDeHallazgo.SUBVALUADOR, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sin predio no hay ficha que contrastar");
        }

        @Test
        @DisplayName("sin insumos no se puede escribir: un descarte no se podria explicar")
        void sinInsumosNoHaySospecha() {
            assertThatThrownBy(
                            () ->
                                    Candidato.detectado(
                                            1L,
                                            7L,
                                            ClaseDeHallazgo.SUBVALUADOR,
                                            OrigenDelCandidato.CRUCE_DE_AREAS,
                                            Score.de("0.5"),
                                            "  ",
                                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("volver a la fuente de la sospecha");
        }
    }

    @Nested
    @DisplayName("Las dos compuertas se pasan EN ORDEN")
    class LasDosCompuertas {

        @Test
        @DisplayName("el recorrido completo llega a VERIFICADO_EN_CAMPO")
        void elRecorridoCompleto() {
            Candidato verificado =
                    detectado(ClaseDeHallazgo.SUBVALUADOR, 7L)
                            .admitidoEnGabinete()
                            .verificadoEnCampo();

            assertThat(verificado.estado()).isEqualTo(EstadoDelCandidato.VERIFICADO_EN_CAMPO);
            assertThat(verificado.descarte()).isNull();
        }

        @Test
        @DisplayName("EL ATAJO: verificar en campo sin pasar por gabinete no se puede")
        void elAtajoNoExiste() {
            Candidato reciendDetectado = detectado(ClaseDeHallazgo.SUBVALUADOR, 7L);

            assertThatThrownBy(reciendDetectado::verificadoEnCampo)
                    .as(
                            "es la alternativa que ADR-0035 descarta con su motivo: sin las dos"
                                    + " compuertas la municipalidad emite miles de valores que se"
                                    + " caen en reclamacion")
                    .isInstanceOf(Candidato.TransicionQueNoExiste.class)
                    .hasMessageContaining("DETECTADO")
                    .hasMessageContaining("verificarlo en campo");
        }

        @Test
        @DisplayName("y admitir dos veces en gabinete tampoco")
        void gabineteNoSeRepite() {
            Candidato admitido = detectado(ClaseDeHallazgo.SUBVALUADOR, 7L).admitidoEnGabinete();

            assertThatThrownBy(admitido::admitidoEnGabinete)
                    .isInstanceOf(Candidato.TransicionQueNoExiste.class);
        }

        @Test
        @DisplayName("un candidato ya verificado no admite ninguna transicion mas")
        void loVerificadoEsTerminal() {
            Candidato verificado =
                    detectado(ClaseDeHallazgo.SUBVALUADOR, 7L)
                            .admitidoEnGabinete()
                            .verificadoEnCampo();

            assertThat(verificado.estado().esTerminal()).isTrue();
            assertThatThrownBy(
                            () ->
                                    verificado.descartadoEn(
                                            EtapaDeVerificacion.CAMPO, "tarde", "ana", AHORA))
                    .isInstanceOf(Candidato.TransicionQueNoExiste.class);
        }
    }

    @Nested
    @DisplayName("El descarte se conserva, con su etapa y su motivo")
    class ElDescarte {

        @Test
        @DisplayName("descartar en gabinete deja la etapa, el motivo, quien y cuando")
        void enGabinete() {
            Candidato descartado =
                    detectado(ClaseDeHallazgo.SUBVALUADOR, 7L)
                            .descartadoEn(
                                    EtapaDeVerificacion.GABINETE,
                                    "ampliacion ya declarada en la DJ 2025",
                                    "ana.gabinete",
                                    AHORA);

            assertThat(descartado.estado()).isEqualTo(EstadoDelCandidato.DESCARTADO);
            Candidato.Descarte descarte = descartado.descarte();
            assertThat(descarte).isNotNull();
            assertThat(descarte.etapa())
                    .as(
                            "la etapa es lo que permite contar por compuerta, y esa es la unica"
                                    + " cifra que dice si el umbral de deteccion sirve")
                    .isEqualTo(EtapaDeVerificacion.GABINETE);
            assertThat(descarte.motivo()).contains("ampliacion ya declarada");
            assertThat(descarte.quien()).isEqualTo("ana.gabinete");
            assertThat(descarte.cuando()).isEqualTo(AHORA);
        }

        @Test
        @DisplayName("un descarte SIN motivo no se puede escribir (regla 4)")
        void sinMotivoNoSeDescarta() {
            Candidato uno = detectado(ClaseDeHallazgo.SUBVALUADOR, 7L);

            assertThatThrownBy(
                            () ->
                                    uno.descartadoEn(
                                            EtapaDeVerificacion.GABINETE, "   ", "ana", AHORA))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("motivo del descarte");
        }

        @Test
        @DisplayName("no se puede descartar EN CAMPO lo que gabinete no admitio")
        void campoSoloDescartaLoQueGabineteAdmitio() {
            Candidato reciendDetectado = detectado(ClaseDeHallazgo.SUBVALUADOR, 7L);

            assertThatThrownBy(
                            () ->
                                    reciendDetectado.descartadoEn(
                                            EtapaDeVerificacion.CAMPO,
                                            "no se hallo",
                                            "luis",
                                            AHORA))
                    .as(
                            "contarlo como descarte de campo falsearia la cifra: campo solo ve lo"
                                    + " que gabinete admitio")
                    .isInstanceOf(Candidato.TransicionQueNoExiste.class)
                    .hasMessageContaining("descartarlo en campo");
        }

        @Test
        @DisplayName("un estado DESCARTADO sin su descarte no se puede construir")
        void elEstadoYElDescarteVanJuntos() {
            assertThatThrownBy(
                            () ->
                                    new Candidato(
                                            1L,
                                            1L,
                                            7L,
                                            ClaseDeHallazgo.SUBVALUADOR,
                                            OrigenDelCandidato.ORTOFOTO,
                                            Score.de("0.9"),
                                            INSUMOS,
                                            null,
                                            EstadoDelCandidato.DESCARTADO,
                                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("si y solo si");
        }
    }
}
