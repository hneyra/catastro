package kamayuk.catastro.catastro.dominio;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Lo que se escribe en el buzon de salida, en la misma transaccion que el hecho (C-8).
 *
 * <p>No lleva secuencia: se la pone el motor al insertar ({@code catastro_evento.id}, que es {@code
 * IDENTITY}). Pedirsela a quien publica seria pedirle que la invente, y dos hechos con la misma
 * secuencia no se pueden ordenar.
 *
 * @param eventoId la identidad del hecho, derivada por {@link IdentidadDelEvento}
 * @param tipo cual de los tres es
 * @param predioId el predio del que habla, o nulo en un cierre de corrida
 * @param ejercicio el ejercicio del que habla, o nulo en una proyeccion de predio
 * @param cuerpo el evento entero en JSON, tal como viajara. Se congela: no se recompone al entregar
 * @param huella sha256 del cuerpo canonico, calculada aqui y copiada por el receptor sin recalcular
 */
public record HechoDeCatastro(
        UUID eventoId,
        TipoDeEventoDeCatastro tipo,
        @Nullable Long predioId,
        @Nullable Integer ejercicio,
        String cuerpo,
        String huella) {

    public HechoDeCatastro {
        Objects.requireNonNull(eventoId, "Un hecho publicado tiene identidad");
        Objects.requireNonNull(tipo, "Un hecho publicado dice de que tipo es");
        Objects.requireNonNull(cuerpo, "Un hecho publicado lleva su cuerpo");
        Objects.requireNonNull(huella, "Un hecho publicado lleva su huella");
        if (huella.length() != 64) {
            throw new IllegalArgumentException(
                    "La huella es un sha256 en hexadecimal: 64 caracteres, y esta tiene "
                            + huella.length());
        }
        // Los mismos dos invariantes que `catastro_evento_ejercicio_ck` y `..._predio_ck` de `V5`.
        // Van tambien aqui —y no solo en el motor— porque el mensaje del CHECK nombra la
        // restriccion y no dice cual de los tres tipos se compuso mal.
        boolean esProyeccion = tipo == TipoDeEventoDeCatastro.PREDIO_PROYECTADO;
        if (esProyeccion == (ejercicio != null)) {
            throw new IllegalArgumentException(
                    "Una proyeccion de predio no tiene ejercicio y los otros dos hechos si: "
                            + tipo
                            + " con ejercicio "
                            + ejercicio);
        }
        boolean esCierre = tipo == TipoDeEventoDeCatastro.CORRIDA_CERRADA;
        if (esCierre == (predioId != null)) {
            throw new IllegalArgumentException(
                    "Un cierre de corrida no habla de un predio y los otros dos si: "
                            + tipo
                            + " con predio "
                            + predioId);
        }
    }
}
