package kamayuk.catastro.catastro.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un hecho del buzon, ya con su secuencia, listo para entregarse (C-8).
 *
 * @param secuencia el {@code id} de la fila. Monotona, y es con lo que el receptor descarta un
 *     hecho viejo que llega tarde (`V4` de `rentas`)
 */
public record EventoDeCatastro(
        long secuencia,
        UUID eventoId,
        TipoDeEventoDeCatastro tipo,
        @Nullable Long predioId,
        @Nullable Integer ejercicio,
        String cuerpo,
        String huella,
        int intentos,
        Instant creadoEn) {

    public EventoDeCatastro {
        Objects.requireNonNull(eventoId, "Un evento del buzon tiene identidad");
        Objects.requireNonNull(tipo, "Un evento del buzon dice de que tipo es");
        Objects.requireNonNull(cuerpo, "Un evento del buzon lleva su cuerpo");
        Objects.requireNonNull(huella, "Un evento del buzon lleva su huella");
        Objects.requireNonNull(creadoEn, "Un evento del buzon sabe cuando se emitio");
        if (secuencia < 1) {
            throw new IllegalArgumentException(
                    "La secuencia sale de una columna IDENTITY, que empieza en 1: " + secuencia);
        }
    }
}
