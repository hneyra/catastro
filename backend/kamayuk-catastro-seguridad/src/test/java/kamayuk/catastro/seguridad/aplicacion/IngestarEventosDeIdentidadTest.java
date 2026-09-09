package kamayuk.catastro.seguridad.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import kamayuk.catastro.seguridad.dominio.AplicadorDeEventosDeIdentidad;
import kamayuk.catastro.seguridad.dominio.EventoRecibido;
import kamayuk.catastro.seguridad.dominio.FuenteDeEventosDeIdentidad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * La vuelta del consumidor y su parada, sin base y sin red: lo que decide CADA desenlace
 * (identidad#4 AC-1) se mide contra un aplicador de mentira que contesta lo que se le diga.
 *
 * <p>Lo que SI necesita la base —que el acuse vaya despues del commit de verdad, y que cada evento
 * abra su transaccion— esta en {@code AplicarUnEventoDeIdentidadJdbcTest}.
 */
@DisplayName("ADR-0039 etapa 4 — la vuelta del consumidor, y cuando para")
class IngestarEventosDeIdentidadTest {

    private static final Instant AHORA = Instant.parse("2026-09-09T12:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    @Test
    @DisplayName("lo aplicado, lo repetido y lo ajeno se acusan; lo apartado tambien, y con aviso")
    void queSeAcusa() {
        EventoRecibido aplicado = evento("USUARIO_DADO_DE_ALTA");
        EventoRecibido repetido = evento("USUARIO_DADO_DE_ALTA");
        EventoRecibido ajeno = evento("PERMISO_FIJADO");
        EventoRecibido roto = evento("PERMISO_FIJADO");
        BuzonDeMentira buzon = new BuzonDeMentira(List.of(aplicado, repetido, ajeno, roto));
        AplicadorDeMentira aplicador =
                new AplicadorDeMentira(
                        e ->
                                e == aplicado
                                        ? AplicadorDeEventosDeIdentidad.Resultado.APLICADO
                                        : e == repetido
                                                ? AplicadorDeEventosDeIdentidad.Resultado
                                                        .YA_APLICADO
                                                : e == ajeno
                                                        ? AplicadorDeEventosDeIdentidad.Resultado
                                                                .IGNORADO_AJENO
                                                        : lanzaNunca("el cuerpo no es JSON"));
        AlertaQueRecuerda alerta = new AlertaQueRecuerda();

        IngestarEventosDeIdentidad.Vuelta vuelta =
                new IngestarEventosDeIdentidad(buzon, aplicador, alerta, RELOJ).unaVuelta();

        assertThat(buzon.acusados)
                .containsExactly(
                        aplicado.eventoId(),
                        repetido.eventoId(),
                        ajeno.eventoId(),
                        roto.eventoId());
        assertThat(aplicador.muertos).containsExactly(roto.eventoId());
        assertThat(alerta.avisos).containsExactly(roto.eventoId() + ": el cuerpo no es JSON");
        assertThat(vuelta.acusados()).isEqualTo(4);
        assertThat(vuelta.sinProgreso()).isFalse();
        assertThat(vuelta.toString())
                .isEqualTo(
                        "leidos 4 · aplicados 1 · ya estaban 1 · ignorados (de otro sistema) 1"
                                + " · apartados 1 · pospuestos 0 · quedan en el buzon 0");
    }

    @Test
    @DisplayName("AC-7 (3): lo que no se puede aplicar AHORA no se acusa, no se aparta y no avisa")
    void loTransitorioNoSeAcusa() {
        EventoRecibido tardio = evento("MIEMBRO_AFILIADO");
        EventoRecibido laBaseCaida = evento("GRUPO_DADO_DE_ALTA");
        EventoRecibido bueno = evento("USUARIO_DADO_DE_ALTA");
        BuzonDeMentira buzon = new BuzonDeMentira(List.of(tardio, laBaseCaida, bueno));
        AplicadorDeMentira aplicador =
                new AplicadorDeMentira(
                        e -> {
                            if (e == tardio) {
                                throw new AplicadorDeEventosDeIdentidad.NoSePuedeAplicarAhora(
                                        "el grupo no ha llegado");
                            }
                            if (e == laBaseCaida) {
                                throw new DataAccessResourceFailureException("connection refused");
                            }
                            return AplicadorDeEventosDeIdentidad.Resultado.APLICADO;
                        });
        AlertaQueRecuerda alerta = new AlertaQueRecuerda();

        IngestarEventosDeIdentidad.Vuelta vuelta =
                new IngestarEventosDeIdentidad(buzon, aplicador, alerta, RELOJ).unaVuelta();

        assertThat(buzon.acusados)
                .as("solo lo que confirmo; lo pospuesto lo vuelve a servir el emisor")
                .containsExactly(bueno.eventoId());
        assertThat(aplicador.muertos)
                .as("apartar un fallo transitorio lo mataria por un motivo que iba a arreglarse")
                .isEmpty();
        assertThat(alerta.avisos).isEmpty();
        assertThat(vuelta.pospuestos()).isEqualTo(2);
        assertThat(vuelta.aplicados()).isEqualTo(1);
    }

    @Test
    @DisplayName("una vuelta en la que nada se acusa es «sin progreso», y el runner para ahi")
    void sinProgreso() {
        EventoRecibido tardio = evento("MIEMBRO_AFILIADO");
        BuzonDeMentira buzon = new BuzonDeMentira(List.of(tardio));
        AplicadorDeMentira aplicador =
                new AplicadorDeMentira(
                        e -> {
                            throw new AplicadorDeEventosDeIdentidad.NoSePuedeAplicarAhora(
                                    "todavia");
                        });
        IngestarEventosDeIdentidad ingestor =
                new IngestarEventosDeIdentidad(buzon, aplicador, new AlertaQueRecuerda(), RELOJ);

        List<IngestarEventosDeIdentidad.Vuelta> vueltas =
                CorrerElConsumidorDeIdentidad.hastaAgotar(ingestor);

        assertThat(vueltas)
                .as(
                        "el pospuesto no se acusa, asi que el buzon lo vuelve a servir: sin la"
                                + " parada por progreso esto daria 50 vueltas y 50 avisos de lo mismo")
                .hasSize(1);
        assertThat(vueltas.getFirst().sinProgreso()).isTrue();
        assertThat(
                        catchThrowable(
                                () ->
                                        CorrerElConsumidorDeIdentidad.exigirQueNadaQuedaraPospuesto(
                                                vueltas)))
                .as("y la corrida NO sale en verde con un evento sin aplicar dentro")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 evento(s) POSPUESTOS");
    }

    @Test
    @DisplayName(
            "mientras haya algo que acusar se sigue dando vueltas, hasta que el buzon se vacia")
    void mientrasHayaProgreso() {
        EventoRecibido uno = evento("USUARIO_DADO_DE_ALTA");
        EventoRecibido dos = evento("GRUPO_DADO_DE_ALTA");
        BuzonDeMentira buzon = new BuzonDeMentira(List.of(uno, dos), 1);
        AplicadorDeMentira aplicador =
                new AplicadorDeMentira(e -> AplicadorDeEventosDeIdentidad.Resultado.APLICADO);
        IngestarEventosDeIdentidad ingestor =
                new IngestarEventosDeIdentidad(buzon, aplicador, new AlertaQueRecuerda(), RELOJ);

        List<IngestarEventosDeIdentidad.Vuelta> vueltas =
                CorrerElConsumidorDeIdentidad.hastaAgotar(ingestor);

        // Dos con lotes de uno, y una tercera vacia que es la que dice que no queda nada.
        assertThat(vueltas).hasSize(3);
        assertThat(buzon.acusados).containsExactly(uno.eventoId(), dos.eventoId());
        assertThat(vueltas.getLast().leidos()).isZero();
        CorrerElConsumidorDeIdentidad.exigirQueNadaQuedaraPospuesto(vueltas);
    }

    @Test
    @DisplayName("un buzon que no contesta tumba la vuelta, sin acusar nada")
    void elBuzonNoContesta() {
        FuenteDeEventosDeIdentidad caido =
                new FuenteDeEventosDeIdentidad() {
                    @Override
                    public Lote pendientes(int limite) {
                        throw new IdentidadNoContesta("identidad no contesta");
                    }

                    @Override
                    public void acusar(List<UUID> eventoIds) {
                        throw new AssertionError("no habia nada que acusar");
                    }
                };
        IngestarEventosDeIdentidad ingestor =
                new IngestarEventosDeIdentidad(
                        caido,
                        new AplicadorDeMentira(
                                e -> AplicadorDeEventosDeIdentidad.Resultado.APLICADO),
                        new AlertaQueRecuerda(),
                        RELOJ);

        assertThat(catchThrowable(ingestor::unaVuelta))
                .isInstanceOf(FuenteDeEventosDeIdentidad.IdentidadNoContesta.class);
    }

    @Test
    @DisplayName(
            "un evento sin tipo, con la huella corta o con secuencia negativa no tiene la forma de un evento")
    void laFormaDeUnEvento() {
        assertThat(
                        catchThrowable(
                                () ->
                                        new EventoRecibido(
                                                UUID.randomUUID(),
                                                1,
                                                " ",
                                                1,
                                                "{}",
                                                "a".repeat(64),
                                                AHORA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sin tipo");
        assertThat(
                        catchThrowable(
                                () ->
                                        new EventoRecibido(
                                                UUID.randomUUID(), 1, "X", 1, "{}", "abc", AHORA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("huella");
        assertThat(
                        catchThrowable(
                                () ->
                                        new EventoRecibido(
                                                UUID.randomUUID(),
                                                -1,
                                                "X",
                                                1,
                                                "{}",
                                                "a".repeat(64),
                                                AHORA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secuencia");
        assertThat(evento("cuenta_bloqueada").tipo())
                .as("un octavo tipo se lee y se decide despues")
                .isNull();
        assertThat(evento("usuario_dado_de_alta").tipo())
                .as("y el nombre se lee sin distinguir mayusculas, como lo escribe el emisor")
                .isNotNull();
    }

    // ------------------------------------------------------------------

    private static AplicadorDeEventosDeIdentidad.Resultado lanzaNunca(String motivo) {
        throw new AplicadorDeEventosDeIdentidad.NoSePuedeAplicar(motivo);
    }

    private static EventoRecibido evento(String tipo) {
        return new EventoRecibido(UUID.randomUUID(), 1, tipo, 1, "{}", "0".repeat(64), AHORA);
    }

    private static final class BuzonDeMentira implements FuenteDeEventosDeIdentidad {
        private final List<EventoRecibido> eventos;
        private final int porLote;
        private final List<UUID> acusados = new ArrayList<>();

        BuzonDeMentira(List<EventoRecibido> eventos) {
            this(eventos, Integer.MAX_VALUE);
        }

        BuzonDeMentira(List<EventoRecibido> eventos, int porLote) {
            this.eventos = eventos;
            this.porLote = porLote;
        }

        @Override
        public Lote pendientes(int limite) {
            List<EventoRecibido> pendientes =
                    eventos.stream().filter(e -> !acusados.contains(e.eventoId())).toList();
            List<EventoRecibido> lote = pendientes.subList(0, Math.min(porLote, pendientes.size()));
            return new Lote(lote, pendientes.size() - lote.size());
        }

        @Override
        public void acusar(List<UUID> eventoIds) {
            acusados.addAll(eventoIds);
        }
    }

    private static final class AplicadorDeMentira implements AplicadorDeEventosDeIdentidad {
        private final Function<EventoRecibido, Resultado> decide;
        private final List<UUID> muertos = new ArrayList<>();

        AplicadorDeMentira(Function<EventoRecibido, Resultado> decide) {
            this.decide = decide;
        }

        @Override
        public Resultado aplicar(EventoRecibido evento, Instant cuando) {
            return decide.apply(evento);
        }

        @Override
        public void matar(EventoRecibido evento, String motivo, Instant cuando) {
            muertos.add(evento.eventoId());
        }

        @Override
        public long muertosSinExplicar() {
            return muertos.size();
        }
    }

    private static final class AlertaQueRecuerda implements AlertaDeEventosSinAplicar {
        private final List<String> avisos = new ArrayList<>();

        @Override
        public void hayUnEventoSinAplicar(EventoRecibido evento, String motivo, long muertos) {
            avisos.add(evento.eventoId() + ": " + motivo);
        }
    }
}
