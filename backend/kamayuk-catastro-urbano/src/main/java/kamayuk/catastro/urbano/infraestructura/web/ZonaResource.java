package kamayuk.catastro.urbano.infraestructura.web;

import java.time.LocalDate;
import java.util.List;
import kamayuk.catastro.urbano.ParametroDeLaZona;
import kamayuk.catastro.urbano.ZonaVigente;
import org.jspecify.annotations.Nullable;

/**
 * La zona de un predio, como sale por HTTP (#4).
 *
 * <p><b>Lleva {@code aLaFecha} dentro</b>, y no es redundante con el parametro de la peticion: la
 * regla 9 dice que toda cifra mostrada indica su fecha, y aqui lo mostrado <b>depende</b> de ella.
 * Quien pide sin {@code aLaFecha} recibe la de hoy y no la escribio en ningun sitio; sin este
 * campo, una respuesta guardada o pegada en un expediente no diria a que dia se refiere.
 *
 * <p>Campos en espanol {@code camelCase}, como el resto del contrato.
 */
public record ZonaResource(
        LocalDate aLaFecha,
        String codigo,
        String nombre,
        String plan,
        String ordenanza,
        LocalDate vigenciaDesde,
        @Nullable LocalDate vigenciaHasta,
        List<ParametroResource> parametros) {

    public static ZonaResource de(ZonaVigente zona, LocalDate aLaFecha) {
        return new ZonaResource(
                aLaFecha,
                zona.codigo(),
                zona.nombre(),
                zona.plan(),
                zona.ordenanza(),
                zona.vigenciaDesde(),
                zona.vigenciaHasta(),
                zona.parametros().stream().map(ParametroResource::de).toList());
    }

    /** Un parametro urbanistico. El valor es texto: ver {@link ParametroDeLaZona}. */
    public record ParametroResource(String clave, String valor, @Nullable String unidad) {

        static ParametroResource de(ParametroDeLaZona parametro) {
            return new ParametroResource(parametro.clave(), parametro.valor(), parametro.unidad());
        }
    }
}
