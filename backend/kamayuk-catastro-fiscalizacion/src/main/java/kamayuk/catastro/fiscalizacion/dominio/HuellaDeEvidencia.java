package kamayuk.catastro.fiscalizacion.dominio;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * El sha256 de un archivo de evidencia, <b>calculado en el dispositivo</b> (ADR-0035 punto 3).
 *
 * <p>Aqui no se recalcula, y eso es una decision y no una omision: recalcularla sobre lo que llego
 * comprobaria que lo que se tiene es igual a lo que se tiene. Lo que la huella sostiene es lo otro
 * —que el archivo que la brigada tomo es el que esta guardado— y para eso tiene que venir de donde
 * se tomo.
 *
 * <p>Es un tipo y no un {@code String} por lo mismo que {@code Observacion} lo es: un {@code String
 * sha256} se cumple pasando {@code ""} el dia que corre prisa, y una evidencia sin huella no
 * sustenta nada.
 */
public record HuellaDeEvidencia(String valor) {

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    public HuellaDeEvidencia {
        Objects.requireNonNull(valor, "Una evidencia necesita su huella");
        valor = valor.strip().toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(valor).matches()) {
            throw new IllegalArgumentException(
                    "La huella de una evidencia es un sha256: 64 hexadigitos en minuscula, y llego"
                            + " «"
                            + valor
                            + "»");
        }
    }

    public static HuellaDeEvidencia de(String texto) {
        return new HuellaDeEvidencia(texto);
    }

    @Override
    public String toString() {
        return valor;
    }
}
