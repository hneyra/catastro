package kamayuk.catastro.seguridad.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un evento del buzon de {@code identidad}, tal como llego y antes de decidir nada sobre el.
 *
 * <p>El tipo se guarda <b>tal como el emisor lo escribio</b> ({@link #tipoPublicado()}) y se
 * traduce al leerlo ({@link #tipo()}): un nombre que este sistema no conoce no impide construir el
 * evento, porque lo que hay que hacer con el —apartarlo con su motivo y acusarlo— exige tenerlo
 * entero, cuerpo incluido. Es la leccion de {@code rentas}#54: rechazar al armar el lote se lleva
 * por delante la vuelta entera con los eventos buenos dentro.
 *
 * <p>La huella viaja copiada y no se recalcula aqui: la calcula el emisor sobre su forma canonica
 * (ADR-0039 etapa 2), y lo que este lado hace con ella es compararla con la que guardo la primera
 * vez que aplico este {@code eventoId} — dos eventos con el mismo identificador y distinta huella
 * no son un reintento, son un emisor reescribiendo un hecho ya aplicado.
 *
 * @param eventoId el identificador que el emisor le dio; es lo que se acusa y lo que hace
 *     idempotente al consumidor
 * @param secuencia el orden en el buzon del emisor, creciente por municipalidad
 * @param tipoPublicado el nombre del tipo, tal como llego
 * @param sujetoId el identificador del usuario, grupo o —en un permiso— del sujeto en la base del
 *     emisor. NO es un identificador de esta base: los enlaces se resuelven por clave natural
 * @param cuerpo el evento entero como texto; es JSON si el emisor hizo lo suyo, y si no lo es se
 *     guarda igual en la cola de muertos, que por eso es {@code text}
 * @param huella el sha256 con el que el emisor firmo el cuerpo
 * @param creadoEn cuando lo escribio el emisor
 */
public record EventoRecibido(
        UUID eventoId,
        long secuencia,
        String tipoPublicado,
        long sujetoId,
        String cuerpo,
        String huella,
        Instant creadoEn) {

    public EventoRecibido {
        Objects.requireNonNull(eventoId, "eventoId");
        Objects.requireNonNull(creadoEn, "creadoEn");
        tipoPublicado = Objects.requireNonNull(tipoPublicado, "tipoPublicado").strip();
        cuerpo = Objects.requireNonNull(cuerpo, "cuerpo");
        huella = Objects.requireNonNull(huella, "huella").strip();
        if (tipoPublicado.isEmpty()) {
            throw new IllegalArgumentException(
                    "El evento " + eventoId + " llego sin tipo: no tiene la forma de un evento");
        }
        if (huella.length() != 64) {
            throw new IllegalArgumentException(
                    "El evento "
                            + eventoId
                            + " trae una huella de "
                            + huella.length()
                            + " caracteres, y un sha256 en hexadecimal tiene 64");
        }
        if (secuencia < 0) {
            throw new IllegalArgumentException(
                    "El evento " + eventoId + " trae una secuencia negativa: " + secuencia);
        }
    }

    /** El tipo, o {@code null} si este sistema no sabe aplicar el que el emisor publico. */
    public @Nullable TipoDeEventoDeIdentidad tipo() {
        return TipoDeEventoDeIdentidad.declarado(tipoPublicado);
    }
}
