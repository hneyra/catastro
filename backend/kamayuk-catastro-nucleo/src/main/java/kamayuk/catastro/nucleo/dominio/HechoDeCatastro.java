package kamayuk.catastro.nucleo.dominio;

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
 * @param predioId el predio del que habla, o nulo en un cierre de corrida, en una manzana y en el
 *     hallazgo de un omiso catastral
 * @param ejercicio el ejercicio del que habla; solo la valuacion y el cierre de corrida tienen uno
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
        // Los mismos dos invariantes que `catastro_evento_ejercicio_ck` y `..._predio_ck`, tal
        // como `V10` los reescribio. Van tambien aqui —y no solo en el motor— porque el mensaje
        // del CHECK nombra la restriccion y no dice cual de los seis tipos se compuso mal.
        //
        // ESTABAN ESCRITOS EN NEGATIVO —«el que no es PREDIO_PROYECTADO lleva ejercicio»— y con
        // tres tipos eso era correcto. Con seis es falso, y de la peor manera: obligaba a una
        // manzana a nombrar un ejercicio que no tiene. Se reescriben en positivo, como en `V10`.
        if (esDeUnEjercicio(tipo) != (ejercicio != null)) {
            throw new IllegalArgumentException(
                    "Solo la valuacion y el cierre de corrida son de un ejercicio; un predio, una"
                            + " manzana, un frente y un hallazgo no lo son: "
                            + tipo
                            + " con ejercicio "
                            + ejercicio);
        }
        if (!admiteEsePredio(tipo, predioId)) {
            throw new IllegalArgumentException(
                    "Ese tipo de hecho no admite ese predio: "
                            + tipo
                            + " con predio "
                            + predioId
                            + ". Un cierre de corrida y una manzana no hablan de ningun predio; la"
                            + " proyeccion, la valuacion y el frente si; y un hallazgo firme admite"
                            + " las dos cosas, porque un OMISO_CATASTRAL es —por definicion— lo que"
                            + " no tiene predio (ADR-0035)");
        }
    }

    /** Los dos tipos que son de un ejercicio. Los otros cuatro no lo son. */
    private static boolean esDeUnEjercicio(TipoDeEventoDeCatastro tipo) {
        return tipo == TipoDeEventoDeCatastro.VALUACION_PUBLICADA
                || tipo == TipoDeEventoDeCatastro.CORRIDA_CERRADA;
    }

    /**
     * Si ese tipo de hecho puede hablar de ese predio.
     *
     * <p>Escrito con un {@code switch} exhaustivo y no con una negacion: un septimo tipo obliga a
     * decidir su forma en vez de heredar la del vecino, y el compilador lo exige.
     */
    private static boolean admiteEsePredio(TipoDeEventoDeCatastro tipo, @Nullable Long predioId) {
        return switch (tipo) {
            case PREDIO_PROYECTADO, VALUACION_PUBLICADA, FRENTE_PUBLICADO -> predioId != null;
            case CORRIDA_CERRADA, MANZANA_PUBLICADA -> predioId == null;
            case HALLAZGO_FIRME -> true;
        };
    }
}
