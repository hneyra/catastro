package kamayuk.catastro.fiscalizacion.dominio;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Lo que sustenta un hallazgo, con su huella y sus <b>dos relojes</b> (ADR-0035 punto 3).
 *
 * <h2>Los dos relojes son dos hechos distintos</h2>
 *
 * <p>{@link #capturadoEn} es el reloj del <b>aparato</b> —cuando se tomo la foto— y {@link
 * #recibidoEn} el del <b>servidor</b> —cuando entro—. Confundirlos hace inauditable la captura en
 * campo, y el caso no es hipotetico: una brigada sin cobertura sube por la tarde lo que fotografio
 * por la manana, y con un solo reloj esa foto pasa a estar tomada por la tarde sin que nadie lo
 * haya decidido. El dia que un administrado discuta si el inspector estuvo alli, la diferencia
 * entre los dos relojes es justamente lo que hay que poder mirar.
 *
 * <p>Y no se comparan ni se corrige uno con el otro: el reloj de una tableta puede ir mal, y eso
 * <b>tambien</b> es un dato. Lo que no se puede es perderlo.
 *
 * <h2>Una foto no sustenta dos actas</h2>
 *
 * <p>{@code UNIQUE (municipalidad_id, sha256)}. Lo sostiene el motor y no un {@code if}: dos cargas
 * simultaneas del mismo archivo leerian las dos «no esta» y las dos entrarian.
 *
 * @param id nulo mientras no se haya guardado
 * @param dispositivo con que se tomo; nulo cuando la evidencia no viene de campo —una ortofoto no
 *     tiene tableta—
 */
public record Evidencia(
        @Nullable Long id,
        long hallazgoId,
        TipoDeEvidencia tipo,
        HuellaDeEvidencia huella,
        String ruta,
        Instant capturadoEn,
        Instant recibidoEn,
        @Nullable String dispositivo) {

    private static final int RUTA_MAXIMA = 500;

    public Evidencia {
        Objects.requireNonNull(tipo, "La evidencia necesita su tipo");
        Objects.requireNonNull(huella, "La evidencia necesita su huella");
        Objects.requireNonNull(ruta, "La evidencia necesita donde esta guardada");
        Objects.requireNonNull(capturadoEn, "La evidencia necesita el reloj del aparato");
        Objects.requireNonNull(recibidoEn, "La evidencia necesita el reloj del servidor");
        ruta = ruta.strip();
        if (ruta.isEmpty() || ruta.length() > RUTA_MAXIMA) {
            throw new IllegalArgumentException(
                    "La ruta del archivo va de 1 a " + RUTA_MAXIMA + " caracteres: '" + ruta + "'");
        }
    }

    public boolean esNueva() {
        return id == null;
    }

    /**
     * Cuanto tardo en llegar.
     *
     * <p>Es lo que hace util tener los dos relojes en vez de uno: un desfase de horas es una
     * brigada sin cobertura, y uno de dias es una carga hecha en gabinete que conviene mirar. La
     * clase no juzga cual es cual — lo devuelve para que alguien lo mire.
     *
     * <p>Puede ser <b>negativo</b>, y no se corrige: significa que el reloj del aparato va
     * adelantado, y eso tambien es un dato sobre la captura.
     */
    public java.time.Duration desfaseDeLosRelojes() {
        return java.time.Duration.between(capturadoEn, recibidoEn);
    }
}
