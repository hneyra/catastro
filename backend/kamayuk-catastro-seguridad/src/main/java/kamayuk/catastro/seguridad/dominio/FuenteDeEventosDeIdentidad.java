package kamayuk.catastro.seguridad.dominio;

import java.util.List;
import java.util.UUID;

/**
 * El buzon de {@code identidad}, visto desde este consumidor: se traen los pendientes y se acusan
 * los que ya no hay que volver a servir (ADR-0039 etapa 3, ADR-0028 §3).
 *
 * <p>Es un puerto y no el cliente HTTP a proposito: el consumidor se prueba contra un buzon de
 * mentira que recuerda que se le acuso, y eso es lo que permite afirmar la propiedad que este
 * camino existe para tener —que <b>nada se acusa antes de que su transaccion confirme</b>— sin
 * levantar a {@code identidad}.
 *
 * <p>Todo lo que este puerto lanza es {@link IdentidadNoContesta}, y todo es <b>transitorio</b>: un
 * emisor caido, una credencial que no vale, un proxy que contesta HTML. Ninguno mata un evento; se
 * arreglan mirando el despliegue y la vuelta siguiente lo vuelve a intentar.
 */
public interface FuenteDeEventosDeIdentidad {

    /**
     * Los eventos pendientes para este consumidor, en el orden del emisor.
     *
     * @param limite cuantos como maximo; el emisor admite hasta 500
     */
    Lote pendientes(int limite);

    /**
     * Le dice al emisor que estos eventos ya no hay que servirlos: estan aplicados, o apartados con
     * su motivo. <b>Solo despues de que su transaccion haya confirmado</b>: un acuse anterior al
     * commit es un evento que se pierde si el commit falla, y el emisor ya no lo vuelve a servir.
     */
    void acusar(List<UUID> eventoIds);

    /**
     * Un lote del buzon.
     *
     * @param eventos los que vinieron, en orden de secuencia
     * @param quedan cuantos le faltan a este consumidor en total <b>contando los de esta
     *     pagina</b>, que es como lo publica {@code identidad}. No es «ademas de estos»: decirlo
     *     asi —que es lo que este javadoc decia— deja la linea «174 acusados; quedan 174 en el
     *     buzon», que se lee como que la vuelta no sirvio de nada (H6 de la medicion de AC-5/AC-6).
     *     Lo que se imprime es la resta, y quien la hace es {@code IngestarEventosDeIdentidad}
     */
    record Lote(List<EventoRecibido> eventos, long quedan) {
        public Lote {
            eventos = List.copyOf(eventos);
        }
    }

    /** {@code identidad} no contesta, o no contesta lo que dice contestar. Se reintenta. */
    final class IdentidadNoContesta extends RuntimeException {
        public IdentidadNoContesta(String mensaje) {
            super(mensaje);
        }

        public IdentidadNoContesta(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
