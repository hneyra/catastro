package kamayuk.catastro.nucleo.dominio;

import java.util.Objects;
import kamayuk.catastro.dominio.Medida;

/**
 * Lo que el derivador propone para un predio: un tramo, su via y sus metros (#7).
 *
 * <p>Es lo que sale de cortar el lote contra el eje de calzada, y <b>no</b> es un frente todavia:
 * no tiene numeracion —la numeracion municipal la asigna una persona—, no dice si es el principal y
 * su longitud nace {@link EstadoDeLaLongitud#PROPUESTA}. Un tipo distinto de {@link
 * FrenteDelPredio} y no un {@code boolean} dentro de aquel, porque lo que se puede componer sin
 * intervencion humana y lo que no son dos cosas, y tenerlas en un solo tipo invita a rellenar los
 * huecos con valores por omision.
 *
 * @param geometriaWkt el tramo cortado, en WKT
 * @param longitud los metros que ese tramo mide sobre el elipsoide. Una PROPUESTA (ADR-0021)
 */
public record FrentePropuesto(long predioId, long viaId, String geometriaWkt, Medida longitud) {

    public FrentePropuesto {
        Objects.requireNonNull(geometriaWkt, "Un frente propuesto trae el tramo que se corto");
        Objects.requireNonNull(longitud, "Un frente propuesto trae los metros que midio el corte");
        if (!FrenteDelPredio.UNIDAD.equals(longitud.unidad())) {
            throw new IllegalArgumentException(
                    "La longitud de un frente va en "
                            + FrenteDelPredio.UNIDAD
                            + " y esta en "
                            + longitud.unidad());
        }
        if (longitud.esCero()) {
            throw new IllegalArgumentException(
                    "Un corte de cero metros no se propone: el lote no da a esa via");
        }
    }
}
