package kamayuk.catastro.fiscalizacion.infraestructura.web;

import java.time.LocalDate;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import org.jspecify.annotations.Nullable;

/**
 * La campania, como sale por HTTP.
 *
 * <p><b>El umbral sale</b>, y es lo que hace legible cualquier tasa de descarte que se pinte al
 * lado: sin el, dos campanias con 40 % de descarte parecen iguales y pueden haber detectado con
 * criterios opuestos.
 */
public record CampaniaResource(
        long id,
        String codigo,
        String nombre,
        String estado,
        LocalDate inicio,
        @Nullable LocalDate fin,
        String umbral) {

    public static CampaniaResource de(Campania campania) {
        return new CampaniaResource(
                campania.id() == null ? 0 : campania.id(),
                campania.codigo(),
                campania.nombre(),
                campania.estado().name(),
                campania.inicio(),
                campania.fin(),
                campania.umbral().toString());
    }
}
