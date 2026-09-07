package kamayuk.catastro.fiscalizacion.infraestructura.web;

import java.time.LocalDate;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import org.jspecify.annotations.Nullable;

/**
 * La campania, como sale por HTTP.
 *
 * <p><b>El umbral y el tope salen</b>, y es lo que hace legible cualquier tasa de descarte que se
 * pinte al lado: sin ellos, dos campanias con 40 % de descarte parecen iguales y pueden haber
 * detectado con criterios opuestos —una con 0,5 sobre quinientos predios y otra con 0,9 sobre cinco
 * mil— (#25).
 */
public record CampaniaResource(
        long id,
        String codigo,
        String nombre,
        String estado,
        LocalDate inicio,
        @Nullable LocalDate fin,
        String umbral,
        int tope) {

    public static CampaniaResource de(Campania campania) {
        return new CampaniaResource(
                campania.id() == null ? 0 : campania.id(),
                campania.codigo(),
                campania.nombre(),
                campania.estado().name(),
                campania.inicio(),
                campania.fin(),
                campania.umbral().toString(),
                campania.tope());
    }
}
