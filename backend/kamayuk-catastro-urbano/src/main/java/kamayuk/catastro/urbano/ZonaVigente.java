package kamayuk.catastro.urbano;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * La zona que rige sobre un predio a una fecha, con lo que esa zona permite (#4).
 *
 * <p><b>Lleva su plan y su ordenanza dentro, y no es adorno.</b> Negar una licencia es un acto
 * administrativo que se motiva: quien la niega tiene que poder citar la norma, y quien la recibe
 * tiene que poder leerla. Una respuesta que dijera solo «RDM» obligaria a buscar en otro sitio de
 * que plan sale, y ese otro sitio puede decir otra cosa.
 *
 * <p><b>Y lleva su vigencia</b>, por la regla 9 aplicada al territorio: no existe «la zona», existe
 * la zona vigente a una fecha. Un plan aprobado el ano pasado no decide una licencia que se pidio
 * antes.
 *
 * @param vigenciaHasta el ultimo dia que la zona rige —inclusivo, como toda vigencia de este
 *     esquema—; nulo mientras el plan siga vigente
 */
public record ZonaVigente(
        String codigo,
        String nombre,
        String plan,
        String ordenanza,
        LocalDate vigenciaDesde,
        @Nullable LocalDate vigenciaHasta,
        List<ParametroDeLaZona> parametros) {

    public ZonaVigente {
        parametros = List.copyOf(parametros);
    }
}
