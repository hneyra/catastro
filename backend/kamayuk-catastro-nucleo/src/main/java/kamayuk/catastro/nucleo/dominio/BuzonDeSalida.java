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
     * <p>Solo puede ocurrirle a los hechos cuya identidad se deriva de la IDENTIDAD y no del
     * contenido: la valuacion (C-8) y, desde #7, el hallazgo firme. Los demas derivan su identidad
     * del contenido, asi que un contenido distinto es otra identidad (ver {@link
     * IdentidadDelEvento}).
     *
     * <p>Y significa exactamente una cosa: <b>alguien esta reescribiendo un hecho firmado</b>
     * (ADR-0027 §1 para la valuacion, ADR-0035 punto 2 para el hallazgo). No se publica encima. Se
     * dice, y se para — porque el receptor no puede distinguirlo de un reenvio y lo descartaria por
     * deduplicacion, dejando el hecho viejo puesto y a este lado creyendo que publico el nuevo.
     */
    final class HechoSelladoReescrito extends RuntimeException {

        @java.io.Serial private static final long serialVersionUID = 1L;

        /**
         * Que hecho firmado se esta reescribiendo, y por que no se puede publicar encima.
         *
         * <p>Un {@code switch} y no una frase fija, y no es cosmetica: el mensaje decia «Una
         * valuacion es un HECHO SELLADO» para cualquier tipo, y con #7 hay un segundo tipo que
         * puede llegar aqui —el hallazgo firme—. Un diagnostico que nombra el hecho equivocado
         * manda a quien atiende a mirar la corrida de valuacion por un acta de fiscalizacion.
         */
        private static String porQueEsUnHechoFirmado(TipoDeEventoDeCatastro tipo) {
            return switch (tipo) {
                case VALUACION_PUBLICADA ->
                        "Una valuacion es un HECHO SELLADO (ADR-0027 §1): corregirla es publicar"
                                + " otra, y hoy no hay donde —`valuacion_predio` de `rentas` tiene la"
                                + " clave (municipalidad, ejercicio, predio) y su ingestor no tiene"
                                + " UPDATE—.";
                case HALLAZGO_FIRME ->
                        "Un hallazgo firme es lo que una PERSONA verifico, con su nombre y su fecha"
                                + " (ADR-0035 punto 2): que vuelva con otro contenido es alguien"
                                + " reescribiendo lo que otro firmo, y corregirlo es dejarlo sin efecto"
                                + " y levantar otro, no editarlo.";
                default ->
                        "Este tipo de hecho deriva su identidad del CONTENIDO, asi que dos"
                                + " contenidos distintos tendrian que ser dos identidades: que no lo"
                                + " sean es un defecto de la derivacion, no del dato.";
            };
        }

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
                            + ". "
                            + porQueEsUnHechoFirmado(hecho.tipo())
                            + " Publicar encima dejaria a `rentas` descartando esto por"
                            + " deduplicacion y a este lado creyendo que lo mando");
        }
    }
}
