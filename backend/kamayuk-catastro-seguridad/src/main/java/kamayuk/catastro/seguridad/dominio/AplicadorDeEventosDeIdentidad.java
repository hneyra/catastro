package kamayuk.catastro.seguridad.dominio;

import java.time.Instant;

/**
 * Quien lleva un evento de {@code identidad} a la copia local de la autorizacion, o dice por que no
 * puede — y con que clase de «no puede», que es lo que decide todo lo demas.
 *
 * <h2>Tres desenlaces, y no se confunden</h2>
 *
 * <ul>
 *   <li>Devuelve un {@link Resultado}: el evento esta aplicado, ya lo estaba, o es de otro sistema
 *       y se ignora. Los tres SE ACUSAN.
 *   <li>Lanza {@link NoSePuedeAplicar}: no se podra aplicar nunca —el cuerpo no es JSON, el tipo no
 *       se conoce, el emisor reescribio un evento ya aplicado—. Se aparta con {@link #matar}, SE
 *       ACUSA y se avisa a una persona con nombre.
 *   <li>Lanza {@link NoSePuedeAplicarAhora}: el evento esta bien y hoy no se puede —nombra un grupo
 *       o una cuenta que todavia no ha llegado—. NO se acusa: el emisor lo sigue teniendo pendiente
 *       y la vuelta siguiente lo vuelve a intentar.
 * </ul>
 *
 * <p>Lo que la base no contesta —una conexion caida, un commit que no confirma— no es ninguna de
 * las tres: sale como lo que es, y el consumidor lo trata como «ahora no».
 */
public interface AplicadorDeEventosDeIdentidad {

    /**
     * Aplica el evento en SU PROPIA transaccion, con el contexto de tenant que este fijado.
     *
     * @throws NoSePuedeAplicar si no se podra aplicar nunca
     * @throws NoSePuedeAplicarAhora si hoy no, y manana quiza
     */
    Resultado aplicar(EventoRecibido evento, Instant cuando);

    /** Aparta un evento que no se puede aplicar nunca, en su propia transaccion. */
    void matar(EventoRecibido evento, String motivo, Instant cuando);

    /** Cuantos eventos hay apartados y sin explicar en la municipalidad del contexto. */
    long muertosSinExplicar();

    /** Como acabo un evento que SI se acusa. */
    enum Resultado {
        /** Se escribio en la copia local. */
        APLICADO,
        /** Ya estaba, con la misma huella: un reintento del emisor, sin efecto. */
        YA_APLICADO,
        /**
         * Es un {@code PERMISO_FIJADO} sobre una opcion de OTRO sistema. Este consumidor no lo
         * aplica —seria dar permiso sobre una pantalla que aqui no existe, o peor, sobre una de
         * aqui que se llame igual— y lo acusa, porque el emisor lo sirve a los cuatro y no va a
         * dejar de servirlo.
         */
        IGNORADO_AJENO
    }

    /** No se podra aplicar nunca. Con el motivo en las palabras del consumidor. */
    final class NoSePuedeAplicar extends RuntimeException {
        public NoSePuedeAplicar(String motivo) {
            super(motivo);
        }

        public NoSePuedeAplicar(String motivo, Throwable causa) {
            super(motivo, causa);
        }
    }

    /** Hoy no se puede; lo que falta puede llegar en la vuelta siguiente. */
    final class NoSePuedeAplicarAhora extends RuntimeException {
        public NoSePuedeAplicarAhora(String motivo) {
            super(motivo);
        }
    }
}
