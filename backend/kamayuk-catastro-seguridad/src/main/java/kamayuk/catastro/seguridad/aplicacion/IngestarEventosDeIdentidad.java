package kamayuk.catastro.seguridad.aplicacion;

import java.time.Clock;
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
 * <h2>Tres desenlaces que no se confunden</h2>
 *
 * <ul>
 *   <li><b>Nunca</b> ({@link NoSePuedeAplicar}): se aparta con su motivo, se acusa y se avisa a una
 *       persona con nombre. Un tipo desconocido tambien va aqui: acusarlo sin apartarlo lo
 *       perderia, y no acusarlo bloquearia la cola detras de el para siempre.
 *   <li><b>Ahora no</b> ({@link NoSePuedeAplicarAhora}, o la base que no contesta): no se acusa y
 *       no se aparta. Se cuenta como pospuesto, y si al final quedan pospuestos la corrida sale con
 *       error para que se vea — un {@code Job} en verde con eventos sin aplicar es el modo de fallo
 *       que C-6 midio con el guion que salia con codigo 0.
 *   <li><b>Ajeno</b>: un {@code PERMISO_FIJADO} de una opcion de otro sistema. Se ignora con un
 *       aviso y se acusa, porque el emisor lo sirve a los cuatro y no va a dejar de servirlo.
 * </ul>
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
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public Vuelta unaVuelta() {
        FuenteDeEventosDeIdentidad.Lote lote = fuente.pendientes(POR_VUELTA);
        Instant ahora = reloj.instant();
        List<UUID> resueltos = new ArrayList<>();
        int aplicados = 0;
        int yaEstaban = 0;
        int ignorados = 0;
        int muertos = 0;
        int pospuestos = 0;

        for (EventoRecibido evento : lote.eventos()) {
            try {
                switch (aplicador.aplicar(evento, ahora)) {
                    case APLICADO -> aplicados++;
                    case YA_APLICADO -> yaEstaban++;
                    case IGNORADO_AJENO -> {
                        ignorados++;
                        log.warn(
                                "Evento {} ({}, secuencia {}) IGNORADO: fija un permiso sobre una"
                                        + " opcion de otro sistema, y este consumidor solo aplica"
                                        + " las de `catastro`. Se acusa igual: el emisor lo sirve a"
                                        + " los cuatro",
                                evento.eventoId(),
                                evento.tipoPublicado(),
                                evento.secuencia());
                    }
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
                pospuestos++;
                log.warn(
                        "Evento {} ({}, secuencia {}) POSPUESTO, no se acusa: {}",
                        evento.eventoId(),
                        evento.tipoPublicado(),
                        evento.secuencia(),
                        todavia.getMessage());
            } catch (DataAccessException | TransactionException laBase) {
                // La base no contesto, o el commit no confirmo. Es «ahora no» por otro camino:
                // el evento esta bien, no se acusa, y la vuelta siguiente lo vuelve a intentar.
                // Se sigue con el resto del lote: lo que falle detras fallara por lo mismo y se
                // vera igual; lo que no, queda aplicado.
                pospuestos++;
                log.warn(
                        "Evento {} ({}, secuencia {}) POSPUESTO: la base no confirmo su"
                                + " transaccion y NO se acusa — {}",
                        evento.eventoId(),
                        evento.tipoPublicado(),
                        evento.secuencia(),
                        laBase.toString());
            }
        }

        fuente.acusar(resueltos);
        Vuelta vuelta =
                new Vuelta(
                        lote.eventos().size(),
                        aplicados,
                        yaEstaban,
                        ignorados,
                        muertos,
                        pospuestos,
                        lote.quedan());
        log.info("Consumidor de identidad: {}", vuelta);
        return vuelta;
    }

    /**
     * Lo que paso en una vuelta.
     *
     * @param leidos cuantos vinieron en el lote
     * @param aplicados cuantos se escribieron en la copia
     * @param yaEstaban cuantos eran reintentos del emisor
     * @param ignorados cuantos eran permisos de otro sistema
     * @param muertos cuantos se apartaron con su motivo
     * @param pospuestos cuantos no se pudieron aplicar ahora y no se acusaron
     * @param quedan cuantos quedaban en el buzon ademas de estos, segun el emisor
     */
    public record Vuelta(
            int leidos,
            int aplicados,
            int yaEstaban,
            int ignorados,
            int muertos,
            int pospuestos,
            long quedan) {

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
                    + pospuestos
                    + " · quedan en el buzon "
                    + quedan;
        }
    }
}
