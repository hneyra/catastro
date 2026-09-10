package kamayuk.catastro.seguridad.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
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
import org.springframework.core.annotation.OrderUtils;
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
                                + " · apartados 1 · pospuestos 0 · quedan en el buzon tras el"
                                + " acuse 0");
    }

    @Test
    @DisplayName("AC-7 (3): lo que no se puede aplicar AHORA no se acusa, no se aparta y no avisa")
    void loTransitorioNoSeAcusa() {
        EventoRecibido tardio = evento("MIEMBRO_AFILIADO");
        EventoRecibido bueno = evento("USUARIO_DADO_DE_ALTA");
        BuzonDeMentira buzon = new BuzonDeMentira(List.of(tardio, bueno));
        AplicadorDeMentira aplicador =
                new AplicadorDeMentira(
                        e -> {
                            if (e == tardio) {
                                throw new AplicadorDeEventosDeIdentidad.NoSePuedeAplicarAhora(
                                        "el grupo no ha llegado");
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
        assertThat(vuelta.pospuestos()).hasSize(1);
        assertThat(vuelta.pospuestos().getFirst().motivo()).isEqualTo("el grupo no ha llegado");
        assertThat(vuelta.aplicados()).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "H4: la base que no contesta CORTA la vuelta y se relanza, en vez de posponer evento a"
                    + " evento")
    void laBaseQueNoContestaCortaLaVuelta() {
        EventoRecibido primero = evento("USUARIO_DADO_DE_ALTA");
        EventoRecibido cuandoSeCae = evento("GRUPO_DADO_DE_ALTA");
        EventoRecibido detras = evento("USUARIO_DADO_DE_ALTA");
        BuzonDeMentira buzon = new BuzonDeMentira(List.of(primero, cuandoSeCae, detras));
        List<UUID> intentados = new ArrayList<>();
        AplicadorDeMentira aplicador =
                new AplicadorDeMentira(
                        e -> {
                            intentados.add(e.eventoId());
                            if (e == cuandoSeCae) {
                                throw new DataAccessResourceFailureException(
                                        "Could not open JDBC Connection");
                            }
                            return AplicadorDeEventosDeIdentidad.Resultado.APLICADO;
                        });
        AlertaQueRecuerda alerta = new AlertaQueRecuerda();
        IngestarEventosDeIdentidad ingestor =
                new IngestarEventosDeIdentidad(buzon, aplicador, alerta, RELOJ);

        Throwable laBase = catchThrowable(ingestor::unaVuelta);

        assertThat(laBase)
                .as(
                        "[una base que no contesta NO es un hecho del dominio: si se clasifica como"
                                + " «pospuesto» se repite por cada fila que quedaba —174 lineas a un"
                                + " segundo cada una en la medicion de AC-5/AC-6 (H4)— y el diagnostico"
                                + " apunta a los eventos en vez de al motor]")
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(intentados)
                .as("y lo que iba detras no se intenta: iba a fallar por lo mismo")
                .containsExactly(primero.eventoId(), cuandoSeCae.eventoId());
        assertThat(buzon.acusados)
                .as("lo que YA confirmo se acusa igual, que es la regla de siempre")
                .containsExactly(primero.eventoId());
        assertThat(alerta.avisos).isEmpty();
        assertThat(alerta.estancados).isEmpty();
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

        List<IngestarEventosDeIdentidad.Vuelta> vueltas = new ArrayList<>();
        Throwable laCorrida =
                catchThrowable(
                        () -> vueltas.addAll(CorrerElConsumidorDeIdentidad.hastaAgotar(ingestor)));

        assertThat(laCorrida)
                .as(
                        "[un pospuesto NO hace fallar la corrida: es una dependencia que todavia no"
                                + " ha llegado, no se pierde —no se acuso— y la corrida siguiente lo"
                                + " aplica. Salir con codigo 1 por el deja un Job Failed cada cinco"
                                + " minutos, reintentado por backoffLimit, mientras la dependencia"
                                + " tarde; y un trabajo que siempre falla deja de decir nada el dia que"
                                + " falle de verdad]")
                .isNull();
        assertThat(vueltas)
                .as(
                        "el pospuesto no se acusa, asi que el buzon lo vuelve a servir: sin la"
                                + " parada por progreso esto daria 50 vueltas y 50 avisos de lo mismo")
                .hasSize(1);
        assertThat(vueltas.getFirst().sinProgreso()).isTrue();
        assertThat(vueltas.getFirst().pospuestos()).hasSize(1);
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

    @Test
    @DisplayName(
            "un pospuesto que lleva mas de 15 minutos produce UN aviso por corrida, con la lista")
    void avisaCuandoElPospuestoSeEstanca() {
        EventoRecibido tardio =
                evento("MIEMBRO_AFILIADO", 77, 4321, AHORA.minus(Duration.ofMinutes(20)));
        AlertaQueRecuerda alerta = new AlertaQueRecuerda();
        IngestarEventosDeIdentidad ingestor = elQuePospone(List.of(tardio), alerta);

        List<IngestarEventosDeIdentidad.Vuelta> vueltas =
                CorrerElConsumidorDeIdentidad.hastaAgotar(ingestor);

        assertThat(vueltas).hasSize(1);
        assertThat(alerta.estancados)
                .as(
                        "[la corrida no falla por un pospuesto —seria un Job Failed cada cinco"
                                + " minutos mientras la dependencia no llegue—, asi que lo unico que"
                                + " impide que la copia se quede atras EN SILENCIO es este aviso, y"
                                + " tiene que decir de que hecho habla]")
                .containsExactly(
                        "aviso de 1 tras 15 min:"
                                + " [MIEMBRO_AFILIADO/sujeto 77/secuencia 4321/edad 20]");
    }

    @Test
    @DisplayName("y uno de dos minutos NO avisa: eso es una carrera, no un atasco")
    void unPospuestoRecienNacidoNoAvisa() {
        EventoRecibido tardio =
                evento("MIEMBRO_AFILIADO", 77, 4321, AHORA.minus(Duration.ofMinutes(2)));
        AlertaQueRecuerda alerta = new AlertaQueRecuerda();

        CorrerElConsumidorDeIdentidad.hastaAgotar(elQuePospone(List.of(tardio), alerta));

        assertThat(alerta.estancados)
                .as(
                        "avisar de una carrera normal —el grupo llega en el lote siguiente— es como"
                                + " se consigue que nadie lea los avisos")
                .isEmpty();
    }

    @Test
    @DisplayName("y una corrida sin pospuestos no avisa de nada")
    void sinPospuestosNoAvisa() {
        BuzonDeMentira buzon = new BuzonDeMentira(List.of(evento("USUARIO_DADO_DE_ALTA")));
        AlertaQueRecuerda alerta = new AlertaQueRecuerda();
        IngestarEventosDeIdentidad ingestor =
                new IngestarEventosDeIdentidad(
                        buzon,
                        new AplicadorDeMentira(
                                e -> AplicadorDeEventosDeIdentidad.Resultado.APLICADO),
                        alerta,
                        RELOJ);

        CorrerElConsumidorDeIdentidad.hastaAgotar(ingestor);

        assertThat(alerta.estancados).isEmpty();
        assertThat(alerta.avisos).isEmpty();
    }

    @Test
    @DisplayName("H6: los permisos de otros sistemas se resumen en UNA linea por vuelta")
    void unaLineaPorVueltaParaLosAjenos() {
        List<EventoRecibido> ajenos =
                List.of(
                        evento("PERMISO_FIJADO"),
                        evento("PERMISO_FIJADO"),
                        evento("PERMISO_FIJADO"));
        IngestarEventosDeIdentidad ingestor =
                new IngestarEventosDeIdentidad(
                        new BuzonDeMentira(ajenos),
                        new AplicadorDeMentira(
                                e -> AplicadorDeEventosDeIdentidad.Resultado.IGNORADO_AJENO),
                        new AlertaQueRecuerda(),
                        RELOJ);

        List<ILoggingEvent> lineas = loQueRegistra(ingestor::unaVuelta);

        assertThat(lineas.stream().map(ILoggingEvent::getFormattedMessage).toList())
                .as(
                        "[una implantacion entera trae 146 permisos ajenos en catastro (H6): una"
                                + " linea por evento son 146 avisos de algo que es normal, y asi es"
                                + " como se acaba sin leer el registro]")
                .hasSize(2)
                .anySatisfy(l -> assertThat(l).contains("3 evento(s) IGNORADOS"))
                .anySatisfy(l -> assertThat(l).contains("ignorados (de otro sistema) 3"));
    }

    @Test
    @DisplayName("H6: «quedan» se cuenta DESPUES del acuse, no antes")
    void quedanSeCuentaDespuesDelAcuse() {
        // El emisor cuenta los de esta pagina dentro de su `quedan`: cinco pendientes, dos
        // servidos.
        BuzonDeMentira buzon =
                new BuzonDeMentira(
                        List.of(
                                evento("USUARIO_DADO_DE_ALTA"),
                                evento("USUARIO_DADO_DE_ALTA"),
                                evento("USUARIO_DADO_DE_ALTA"),
                                evento("USUARIO_DADO_DE_ALTA"),
                                evento("USUARIO_DADO_DE_ALTA")),
                        2);
        IngestarEventosDeIdentidad.Vuelta vuelta =
                new IngestarEventosDeIdentidad(
                                buzon,
                                new AplicadorDeMentira(
                                        e -> AplicadorDeEventosDeIdentidad.Resultado.APLICADO),
                                new AlertaQueRecuerda(),
                                RELOJ)
                        .unaVuelta();

        assertThat(vuelta.quedan())
                .as(
                        "[el `quedan` del emisor CUENTA los de esta pagina, asi que imprimirlo tal"
                                + " cual deja «174 acusados; quedan 174 en el buzon», que se lee como"
                                + " que la vuelta no sirvio de nada (H6)]")
                .isEqualTo(3);
        assertThat(vuelta.toString()).endsWith("quedan en el buzon tras el acuse 3");
    }

    @Test
    @DisplayName("H4: el consumidor autonomo corre DETRAS de la implantacion")
    void elConsumidorCorreDetrasDeLaImplantacion() {
        // Es como Spring ordena los ApplicationRunner: AnnotationAwareOrderComparator pregunta por
        // @Order de la clase cuando el bean no implementa Ordered, que es el caso de los dos. La
        // version de UN argumento devuelve null cuando no hay anotacion, y esa es la mitad que hay
        // que afirmar: «sin declarar» no es un numero grande, es un EMPATE con todos los demas
        // runners sin declarar, que rompe el desempate a favor del registro de beans.
        Integer implantacion = OrderUtils.getOrder(ImplantarMunicipalidad.class);
        Integer consumidor = OrderUtils.getOrder(CorrerElConsumidorDeIdentidad.class);

        assertThat(implantacion)
                .as(
                        "[esta es la mitad que de verdad sujeta el orden: sin @Order la implantacion"
                                + " vale LOWEST_PRECEDENCE y empata con el consumidor, y quien corre"
                                + " primero lo decide el registro de beans. Medido en la instalacion de"
                                + " AC-5/AC-6, el consumidor corrio ANTES y la implantacion no llego a"
                                + " correr (H4)]")
                .isNotNull();
        assertThat(consumidor)
                .as(
                        "[y esta es la que lo deja escrito en vez de heredado: sin @Order el"
                                + " consumidor vale LOWEST_PRECEDENCE y hoy corre detras por descarte,"
                                + " o sea por como esten anotados los demas y no porque nadie lo haya"
                                + " decidido]")
                .isNotNull();
        assertThat(consumidor)
                .as(
                        "consumir el buzon antes de que la municipalidad exista no tiene copia que"
                                + " poner al dia")
                .isGreaterThan(implantacion);
    }

    // ------------------------------------------------------------------

    /** Un consumidor cuyo unico evento no se puede aplicar todavia. */
    private static IngestarEventosDeIdentidad elQuePospone(
            List<EventoRecibido> eventos, AlertaQueRecuerda alerta) {
        return new IngestarEventosDeIdentidad(
                new BuzonDeMentira(eventos),
                new AplicadorDeMentira(
                        e -> {
                            throw new AplicadorDeEventosDeIdentidad.NoSePuedeAplicarAhora(
                                    "el grupo no ha llegado");
                        }),
                alerta,
                RELOJ);
    }

    /** Lo que el consumidor escribe en el registro mientras corre lo que se le pase. */
    private static List<ILoggingEvent> loQueRegistra(Runnable queHace) {
        ch.qos.logback.classic.Logger registro =
                (ch.qos.logback.classic.Logger)
                        org.slf4j.LoggerFactory.getLogger(IngestarEventosDeIdentidad.class);
        ListAppender<ILoggingEvent> anotadas = new ListAppender<>();
        anotadas.start();
        registro.addAppender(anotadas);
        try {
            queHace.run();
        } finally {
            registro.detachAppender(anotadas);
        }
        return anotadas.list.stream().filter(e -> e.getLevel() != Level.DEBUG).toList();
    }

    private static AplicadorDeEventosDeIdentidad.Resultado lanzaNunca(String motivo) {
        throw new AplicadorDeEventosDeIdentidad.NoSePuedeAplicar(motivo);
    }

    private static EventoRecibido evento(String tipo) {
        return evento(tipo, 1, 1, AHORA);
    }

    private static EventoRecibido evento(
            String tipo, long sujetoId, long secuencia, Instant creadoEn) {
        return new EventoRecibido(
                UUID.randomUUID(), secuencia, tipo, sujetoId, "{}", "0".repeat(64), creadoEn);
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
            // `quedan` cuenta LOS DE ESTA PAGINA, que es como lo publica identidad
            // (EventosController: «cuantos le faltan en total, contando los de esta pagina»).
            // Este doble decia lo contrario, y por eso ninguna prueba podia ver H6.
            return new Lote(lote, pendientes.size());
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
        private final List<String> estancados = new ArrayList<>();

        @Override
        public void hayUnEventoSinAplicar(EventoRecibido evento, String motivo, long muertos) {
            avisos.add(evento.eventoId() + ": " + motivo);
        }

        @Override
        public void hayPospuestosEstancados(
                List<IngestarEventosDeIdentidad.Pospuesto> viejos,
                Duration desdeHace,
                Instant ahora) {
            estancados.add(
                    "aviso de "
                            + viejos.size()
                            + " tras "
                            + desdeHace.toMinutes()
                            + " min: "
                            + viejos.stream()
                                    .map(
                                            v ->
                                                    v.tipoPublicado()
                                                            + "/sujeto "
                                                            + v.sujetoId()
                                                            + "/secuencia "
                                                            + v.secuencia()
                                                            + "/edad "
                                                            + v.edad(ahora).toMinutes())
                                    .toList());
        }
    }
}
