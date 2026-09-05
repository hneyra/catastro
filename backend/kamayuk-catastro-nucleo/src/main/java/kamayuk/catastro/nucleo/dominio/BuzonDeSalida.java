package kamayuk.catastro.nucleo.dominio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El buzon de salida de {@code catastro} (C-8, ADR-0026 §3).
 *
 * <p><b>Se escribe en la misma transaccion que el hecho que se publica.</b> Nadie llama por red
 * desde dentro de esa transaccion: si la fila esta, el hecho esta. Lo que la entrega es otro
 * proceso, y lo hace despues del {@code COMMIT}.
 */
public interface BuzonDeSalida {

    /** Que paso al publicar. */
    enum Publicacion {
        /** Se escribio: es un hecho que no estaba. */
        NUEVO,
        /**
         * Ya estaba, con el mismo contenido.
         *
         * <p>Es el caso corriente de una reproyeccion: la identidad de una proyeccion y la de un
         * cierre se derivan del contenido, asi que volver a publicar lo que no cambio no escribe
         * nada. No es un error y no se cuenta como tal.
         */
        YA_ESTABA
    }

    /**
     * Escribe el hecho, si no estaba.
     *
     * @throws HechoSelladoReescrito si ya estaba con OTRO contenido
     */
    Publicacion publicar(HechoDeCatastro hecho);

    /** Lo pendiente de entregar, en el orden en que se emitio. */
    List<EventoDeCatastro> pendientes(int limite);

    /** Marca entregados los que el receptor acuso. */
    int marcarEntregados(List<UUID> eventoIds, Instant cuando);

    /** Anota el intento fallido, para que un buzon que no se vacia se pueda diagnosticar. */
    void anotarIntentoFallido(List<UUID> eventoIds, String motivo);

    /** Cuantos quedan sin entregar. */
    long pendientesQueQuedan();

    /**
     * La misma identidad con otro contenido.
     *
     * <p>Solo puede ocurrirle a una valuacion: la identidad de los otros dos hechos se deriva de su
     * contenido, asi que un contenido distinto es otra identidad (ver {@link IdentidadDelEvento}).
     *
     * <p>Y significa exactamente una cosa: <b>alguien esta reescribiendo un hecho sellado</b>
     * (ADR-0027 §1). No se publica encima. Se dice, y se para — porque el receptor no puede
     * distinguirlo de un reenvio y lo descartaria por deduplicacion, dejando la valuacion vieja
     * puesta y a este lado creyendo que publico la nueva.
     */
    final class HechoSelladoReescrito extends RuntimeException {

        @java.io.Serial private static final long serialVersionUID = 1L;

        public HechoSelladoReescrito(HechoDeCatastro hecho, String huellaQueYaEstaba) {
            super(
                    "El hecho "
                            + hecho.eventoId()
                            + " ("
                            + hecho.tipo()
                            + ", predio "
                            + hecho.predioId()
                            + ", ejercicio "
                            + hecho.ejercicio()
                            + ") ya se publico con la huella "
                            + huellaQueYaEstaba
                            + " y ahora se quiere publicar con "
                            + hecho.huella()
                            + ". Una valuacion es un HECHO SELLADO (ADR-0027 §1): corregirla es"
                            + " publicar otra, y hoy no hay donde —`valuacion_predio` de `rentas`"
                            + " tiene la clave (municipalidad, ejercicio, predio) y su ingestor no"
                            + " tiene UPDATE—. Publicar encima dejaria a `rentas` descartando esto"
                            + " por deduplicacion y a este lado creyendo que lo mando");
        }
    }
}
