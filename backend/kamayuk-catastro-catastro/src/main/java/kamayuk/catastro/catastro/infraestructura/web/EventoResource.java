package kamayuk.catastro.catastro.infraestructura.web;

import kamayuk.catastro.catastro.dominio.EventoDeCatastro;
import org.jspecify.annotations.Nullable;

/**
 * Un hecho del buzon, tal como viaja (C-8).
 *
 * <p>{@code cuerpo} sale <b>como cadena y no como objeto anidado</b>, y es deliberado: el cuerpo se
 * congelo al publicarse y lo que el receptor copia en su columna {@code huella} es la huella de ese
 * cuerpo, no la del que este servidor recomponga hoy. Reserializarlo aqui —aunque produjera los
 * mismos bytes— seria abrir la puerta a que un dia no los produzca, y entonces el receptor
 * guardaria una huella que no describe lo que aplico.
 *
 * @param secuencia con lo que el receptor descarta un hecho viejo que llega tarde (`V4` de
 *     `rentas`)
 * @param huella la del cuerpo canonico, calculada en el emisor. El receptor la copia y NO la
 *     recalcula (`V9`)
 */
public record EventoResource(
        String eventoId,
        long secuencia,
        String tipo,
        @Nullable Long predioId,
        @Nullable Integer ejercicio,
        String cuerpo,
        String huella,
        String emitidoEn) {

    /** El evento del dominio, tal como sale por HTTP. */
    public static EventoResource de(EventoDeCatastro evento) {
        return new EventoResource(
                evento.eventoId().toString(),
                evento.secuencia(),
                evento.tipo().name(),
                evento.predioId(),
                evento.ejercicio(),
                evento.cuerpo(),
                evento.huella(),
                evento.creadoEn().toString());
    }
}
