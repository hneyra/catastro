package kamayuk.catastro.seguridad.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kamayuk.catastro.seguridad.dominio.AplicadorDeEventosDeIdentidad;
import kamayuk.catastro.seguridad.dominio.AplicadorDeEventosDeIdentidad.NoSePuedeAplicar;
import kamayuk.catastro.seguridad.dominio.AplicadorDeEventosDeIdentidad.NoSePuedeAplicarAhora;
import kamayuk.catastro.seguridad.dominio.EventoRecibido;
import kamayuk.catastro.seguridad.dominio.FuenteDeEventosDeIdentidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

/**
 * Una vuelta del consumidor: trae un lote del buzon de {@code identidad}, aplica cada evento en su
 * transaccion y acusa lo que ya no hay que volver a servir (ADR-0039 etapa 4, identidad#4 AC-1).
 *
 * <h2>El acuse va DESPUES del commit, y esta clase es donde se ve</h2>
 *
 * <p>Un evento entra en {@code resueltos} solo cuando {@link AplicadorDeEventosDeIdentidad#aplicar}
 * ha vuelto —o sea, cuando su transaccion {@code REQUIRES_NEW} confirmo— o cuando se aparto con su
 * motivo. Un commit que no confirma sale de ahi como excepcion y el evento NO se acusa: el emisor
 * lo sigue teniendo pendiente y la vuelta siguiente lo vuelve a intentar. Acusar antes seria
 * perderlo, porque el emisor ya no lo sirve.
 *
 * <h2>Cuatro desenlaces que no se confunden</h2>
 *
 * <ul>
 *   <li><b>Nunca</b> ({@link NoSePuedeAplicar}): se aparta con su motivo, se acusa y se avisa a una
 *       persona con nombre. Un tipo desconocido tambien va aqui: acusarlo sin apartarlo lo
 *       perderia, y no acusarlo bloquearia la cola detras de el para siempre.
 *   <li><b>Ahora no</b> ({@link NoSePuedeAplicarAhora}): el evento esta bien y le falta algo que
 *       {@code identidad} publico antes —el grupo de una afiliacion—. No se acusa y no se aparta:
 *       se cuenta como pospuesto y el emisor lo vuelve a servir. <b>La corrida NO falla por el</b>;
 *       lo que hay es un aviso cuando se estanca, y el motivo esta abajo.
 *   <li><b>La base no contesta</b> ({@link DataAccessException}, {@link TransactionException}): eso
 *       no es un hecho del dominio y <b>corta la vuelta</b>. Se acusa lo que ya confirmo y se
 *       relanza, de modo que la corrida sale con error. Clasificarlo evento a evento como
 *       «pospuesto» es lo que la medicion de AC-5/AC-6 encontro (H4): con la clave del pool pisada,
 *       una implantacion produjo <b>174 lineas a un segundo cada una</b> —tres minutos— diciendo
 *       «pospuesto» de una base que no estaba, y el diagnostico apuntaba a los eventos.
 *   <li><b>Ajeno</b>: un {@code PERMISO_FIJADO} de una opcion de otro sistema. Se ignora y se
 *       acusa, porque el emisor lo sirve a los cuatro y no va a dejar de servirlo. Se cuentan y se
 *       resumen en <b>una linea por vuelta</b>: una implantacion entera trae 146 de estos en {@code
 *       catastro} (H6), y 146 lineas de aviso por algo que es normal es como se acaba ignorando el
 *       registro entero.
 * </ul>
 *
 * <h2>Un pospuesto no hace fallar la corrida, y cuando se estanca se avisa</h2>
 *
 * <p>Un pospuesto no se pierde: no se acuso, asi que el emisor lo vuelve a servir y la corrida
 * siguiente lo aplica en cuanto llegue su dependencia (medido: una corrida). Hacer fallar la
 * corrida por el convierte el {@code CronJob} en un {@code Job} {@code Failed} cada cinco minutos
 * —reintentado ademas una vez por {@code backoffLimit: 1}— <b>mientras la dependencia no
 * llegue</b>, y un trabajo que siempre falla deja de decir nada el dia que falle de verdad. Lo que
 * no puede pasar es que nadie se entere si la dependencia no llega nunca: por eso, al final de la
 * corrida, los pospuestos con mas de {@link #ANTIGUEDAD_QUE_SE_AVISA} se avisan <b>una vez, con la
 * lista</b>.
 *
 * <h2>Lo que hace que el bucle termine</h2>
 *
 * <p>Un evento pospuesto no se acusa, asi que el buzon lo vuelve a servir en la vuelta siguiente:
 * con la parada escrita como «el lote vino vacio», un buzon en el que solo queden pospuestos daria
 * vueltas hasta agotar el tope. Por eso la parada es {@link Vuelta#sinProgreso()}: una vuelta en la
 * que nada se acuso no va a producir nada en la siguiente.
 */
public class IngestarEventosDeIdentidad {

    private static final Logger log = LoggerFactory.getLogger(IngestarEventosDeIdentidad.class);

    /** Cuantos eventos se piden por vuelta. El emisor admite hasta 500. */
    public static final int POR_VUELTA = 200;

    /**
     * A partir de cuanto un evento pospuesto deja de ser una carrera y pasa a ser un aviso.
     *
     * <p>Son <b>tres ticks</b> del {@code CronJob} de cinco minutos, y esa es toda la razon del
     * numero: una vez puede ser el orden de dos hechos que se cruzan, dos ya es raro, y a la
     * tercera la dependencia no va a llegar sola —alguien perdio una fila de la copia, o el emisor
     * publico algo que este sistema no sabe enlazar—. Mas corto avisaria de carreras normales y el
     * aviso se ignoraria; mas largo deja a la copia diciendo de los permisos algo que {@code
     * identidad} ya no dice, sin que nadie lo sepa.
     */
    public static final Duration ANTIGUEDAD_QUE_SE_AVISA = Duration.ofMinutes(15);

    private final FuenteDeEventosDeIdentidad fuente;
    private final AplicadorDeEventosDeIdentidad aplicador;
    private final AlertaDeEventosSinAplicar alerta;
    private final Clock reloj;

    public IngestarEventosDeIdentidad(
            FuenteDeEventosDeIdentidad fuente,
            AplicadorDeEventosDeIdentidad aplicador,
            AlertaDeEventosSinAplicar alerta,
            Clock reloj) {
        this.fuente = fuente;
        this.aplicador = aplicador;
        this.alerta = alerta;
        this.reloj = reloj;
    }

    /**
     * Una vuelta, con el contexto de tenant ya fijado por quien llama.
     *
     * @throws FuenteDeEventosDeIdentidad.IdentidadNoContesta si el buzon no contesta; se propaga
     *     para que la corrida salga con error
     * @throws DataAccessException si la base no contesta; corta la vuelta y se propaga por lo mismo
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public Vuelta unaVuelta() {
        FuenteDeEventosDeIdentidad.Lote lote = fuente.pendientes(POR_VUELTA);
        Instant ahora = reloj.instant();
        List<UUID> resueltos = new ArrayList<>();
        List<Pospuesto> pospuestos = new ArrayList<>();
        int aplicados = 0;
        int yaEstaban = 0;
        int ignorados = 0;
        int muertos = 0;

        for (EventoRecibido evento : lote.eventos()) {
            try {
                switch (aplicador.aplicar(evento, ahora)) {
                    case APLICADO -> aplicados++;
                    case YA_APLICADO -> yaEstaban++;
                    case IGNORADO_AJENO -> ignorados++;
                    // El enumerado es de tres y esto es exhaustivo; Checkstyle exige el `default`
                    // y un cuarto resultado que nadie decidio como acusar no se acusa a ciegas.
                    default ->
                            throw new IllegalStateException(
                                    "Resultado de aplicar sin decidir: " + evento.eventoId());
                }
                resueltos.add(evento.eventoId());
            } catch (NoSePuedeAplicar nunca) {
                String motivo = nunca.getMessage() == null ? "sin motivo" : nunca.getMessage();
                aplicador.matar(evento, motivo, ahora);
                muertos++;
                resueltos.add(evento.eventoId());
                alerta.hayUnEventoSinAplicar(evento, motivo, aplicador.muertosSinExplicar());
            } catch (NoSePuedeAplicarAhora todavia) {
                String motivo = todavia.getMessage() == null ? "sin motivo" : todavia.getMessage();
                pospuestos.add(Pospuesto.de(evento, motivo));
                log.warn(
                        "Evento {} ({}, secuencia {}) POSPUESTO, no se acusa: {}",
                        evento.eventoId(),
                        evento.tipoPublicado(),
                        evento.secuencia(),
                        motivo);
            } catch (DataAccessException | TransactionException laBase) {
                // La base no contesto, o el commit no confirmo. NO es un hecho del dominio: no es
                // que a este evento le falte algo, es que no hay base. Se corta la vuelta y se
                // relanza para que la corrida salga con error; seguir con el resto del lote
                // clasificaria como «pospuesto» un evento por cada fila que quedaba (H4: 174
                // lineas, tres minutos), y mandaria a mirar los eventos en vez del motor.
                log.error(
                        "La base no contesto al aplicar el evento {} ({}, secuencia {}): la vuelta"
                                + " se corta aqui y la corrida sale con error. Lo que ya confirmo"
                                + " se acusa; lo demas sigue pendiente en el buzon — {}",
                        evento.eventoId(),
                        evento.tipoPublicado(),
                        evento.secuencia(),
                        laBase.toString());
                acusarLoQueYaConfirmo(resueltos);
                throw laBase;
            }
        }

        if (ignorados > 0) {
            log.info(
                    "Vuelta del consumidor de identidad: {} evento(s) IGNORADOS por fijar permisos"
                            + " sobre opciones de otros sistemas. Se acusan igual: el emisor los"
                            + " sirve a los cuatro consumidores",
                    ignorados);
        }
        fuente.acusar(resueltos);
        Vuelta vuelta =
                new Vuelta(
                        lote.eventos().size(),
                        aplicados,
                        yaEstaban,
                        ignorados,
                        muertos,
                        List.copyOf(pospuestos),
                        quedanDespuesDelAcuse(lote, resueltos.size()));
        log.info("Consumidor de identidad: {}", vuelta);
        return vuelta;
    }

    /**
     * Avisa UNA vez por corrida si quedan pospuestos que llevan mas de {@link
     * #ANTIGUEDAD_QUE_SE_AVISA} en el buzon, con la lista entera.
     *
     * <p>Se miran los de la <b>ultima</b> vuelta y no los de todas: un pospuesto que no se acusa lo
     * vuelve a servir el emisor, asi que la ultima vuelta trae los que siguen sin poder aplicarse y
     * las anteriores traen copias de los mismos. La edad sale del {@code creadoEn} que publica el
     * emisor, o sea del reloj de {@code identidad}: es cuanto lleva ese hecho sin llegar a esta
     * copia, que es justo lo que hay que decidir.
     */
    public void avisarSiLosPospuestosSeEstancan(List<Vuelta> vueltas) {
        if (vueltas.isEmpty()) {
            return;
        }
        Instant ahora = reloj.instant();
        List<Pospuesto> viejos =
                vueltas.getLast().pospuestos().stream()
                        .filter(p -> !p.edad(ahora).minus(ANTIGUEDAD_QUE_SE_AVISA).isNegative())
                        .toList();
        if (viejos.isEmpty()) {
            return;
        }
        alerta.hayPospuestosEstancados(viejos, ANTIGUEDAD_QUE_SE_AVISA, ahora);
    }

    /**
     * Cuantos quedan pendientes DESPUES de este acuse.
     *
     * <p>El {@code quedan} del emisor cuenta <b>los de esta pagina</b> (asi lo publica {@code
     * identidad}: «cuantos le faltan en total, contando los de esta pagina»), de modo que
     * imprimirlo tal cual deja la linea «174 acusados; quedan 174 en el buzon», que es la que la
     * medicion encontro (H6) y que se lee como que la vuelta no sirvio de nada.
     */
    private static long quedanDespuesDelAcuse(FuenteDeEventosDeIdentidad.Lote lote, int acusados) {
        return Math.max(0, lote.quedan() - acusados);
    }

    /**
     * Acusa lo que ya confirmo, cuando la vuelta se corta por un fallo de la base.
     *
     * <p>Se atrapa {@code RuntimeException} a proposito: lo que se esta haciendo aqui es salir con
     * el fallo de la base <b>en la mano</b>, y un acuse que tampoco se pueda entregar no debe
     * sustituirlo — el que hay que leer es el primero. Lo que no se acuse se vuelve a servir y se
     * descarta por deduplicacion.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void acusarLoQueYaConfirmo(List<UUID> resueltos) {
        if (resueltos.isEmpty()) {
            return;
        }
        try {
            fuente.acusar(resueltos);
        } catch (RuntimeException tampoco) {
            log.error(
                    "Y tampoco se pudieron acusar los {} evento(s) que SI confirmaron antes del"
                            + " fallo: se volveran a servir y se descartaran por deduplicacion — {}",
                    resueltos.size(),
                    tampoco.toString());
        }
    }

    /**
     * Un evento que no se pudo aplicar todavia, con lo que hace falta para nombrarlo en un aviso.
     *
     * <p>No se guarda el {@link EventoRecibido} entero a proposito: lo unico que quien reciba el
     * aviso puede usar es de que hecho habla y desde cuando espera; el cuerpo es un JSON que no le
     * dice nada y que en un permiso trae la matriz entera.
     *
     * @param eventoId el identificador con el que el emisor lo sirve
     * @param tipoPublicado el tipo tal como el emisor lo escribio
     * @param sujetoId el usuario o grupo del que habla, en la base del emisor
     * @param secuencia su orden en el buzon
     * @param creadoEn cuando lo escribio el emisor; de aqui sale la edad
     * @param motivo por que no se pudo aplicar, en las palabras del aplicador
     */
    public record Pospuesto(
            UUID eventoId,
            String tipoPublicado,
            long sujetoId,
            long secuencia,
            Instant creadoEn,
            String motivo) {

        static Pospuesto de(EventoRecibido evento, String motivo) {
            return new Pospuesto(
                    evento.eventoId(),
                    evento.tipoPublicado(),
                    evento.sujetoId(),
                    evento.secuencia(),
                    evento.creadoEn(),
                    motivo);
        }

        /** Cuanto lleva este hecho publicado sin poder aplicarse aqui. */
        public Duration edad(Instant ahora) {
            return Duration.between(creadoEn, ahora);
        }
    }

    /**
     * Lo que paso en una vuelta.
     *
     * @param leidos cuantos vinieron en el lote
     * @param aplicados cuantos se escribieron en la copia
     * @param yaEstaban cuantos eran reintentos del emisor
     * @param ignorados cuantos eran permisos de otro sistema
     * @param muertos cuantos se apartaron con su motivo
     * @param pospuestos los que no se pudieron aplicar ahora y no se acusaron
     * @param quedan cuantos quedan en el buzon DESPUES de este acuse
     */
    public record Vuelta(
            int leidos,
            int aplicados,
            int yaEstaban,
            int ignorados,
            int muertos,
            List<Pospuesto> pospuestos,
            long quedan) {

        public Vuelta {
            pospuestos = List.copyOf(pospuestos);
        }

        /** Cuantos se acusaron. */
        public int acusados() {
            return aplicados + yaEstaban + ignorados + muertos;
        }

        /**
         * Si dar otra vuelta no va a cambiar nada: nada se acuso, asi que el buzon volveria a
         * servir exactamente lo mismo.
         */
        public boolean sinProgreso() {
            return acusados() == 0;
        }

        @Override
        public String toString() {
            return "leidos "
                    + leidos
                    + " · aplicados "
                    + aplicados
                    + " · ya estaban "
                    + yaEstaban
                    + " · ignorados (de otro sistema) "
                    + ignorados
                    + " · apartados "
                    + muertos
                    + " · pospuestos "
                    + pospuestos.size()
                    + " · quedan en el buzon tras el acuse "
                    + quedan;
        }
    }
}
