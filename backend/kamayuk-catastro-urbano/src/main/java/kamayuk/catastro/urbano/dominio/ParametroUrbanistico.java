package kamayuk.catastro.urbano.dominio;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Lo que una zona permite, tal y como lo dice su ordenanza (#4).
 *
 * <p>Ver {@link kamayuk.catastro.urbano.ParametroDeLaZona} para por que el valor es texto.
 */
public record ParametroUrbanistico(String clave, String valor, @Nullable String unidad) {

    /** El largo de {@code parametro_urbanistico.clave}. */
    private static final int LARGO_DE_LA_CLAVE = 40;

    /** El largo de {@code parametro_urbanistico.valor}. */
    private static final int LARGO_DEL_VALOR = 120;

    /** El largo de {@code parametro_urbanistico.unidad}. */
    private static final int LARGO_DE_LA_UNIDAD = 20;

    public ParametroUrbanistico {
        clave = exigir(clave, "la clave del parametro", LARGO_DE_LA_CLAVE);
        valor = exigir(valor, "el valor del parametro", LARGO_DEL_VALOR);
        if (unidad != null) {
            unidad = unidad.strip();
            if (unidad.isEmpty()) {
                unidad = null;
            } else if (unidad.length() > LARGO_DE_LA_UNIDAD) {
                throw new IllegalArgumentException(
                        "La unidad excede " + LARGO_DE_LA_UNIDAD + " caracteres: '" + unidad + "'");
            }
        }
    }

    private static String exigir(String valor, String que, int largo) {
        String limpio = Objects.requireNonNull(valor, "Falta " + que).strip();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("Falta " + que);
        }
        if (limpio.length() > largo) {
            throw new IllegalArgumentException(
                    "Excede "
                            + largo
                            + " caracteres, que es lo que admite la columna: '"
                            + limpio
                            + "'");
        }
        return limpio;
    }
}
