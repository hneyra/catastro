package kamayuk.catastro.nucleo.infraestructura.web;

import java.util.List;
import kamayuk.catastro.nucleo.aplicacion.ConsultaDeFrentes;
import kamayuk.catastro.nucleo.dominio.DerivacionDeFrentes;
import org.jspecify.annotations.Nullable;

/**
 * Los frentes de un predio, con la constancia de cuando se derivaron (#7, AC 3).
 *
 * <h2>La lista vacia NO viaja sola, y eso es el punto de este tipo</h2>
 *
 * <p>«Este predio no da a ninguna calle» y «a este predio no le ha pasado el derivador» son la
 * misma lista vacia y dos problemas distintos: el primero se arregla midiendo en campo y el segundo
 * cargando la cartografia. Hoy no hay ni un poligono en ninguna instalacion, asi que la respuesta
 * que se va a dar siempre al principio es la segunda.
 *
 * <p>Por eso la respuesta lleva {@code derivadoEn} —nulo cuando <b>nunca</b> se derivo— y {@code
 * motivoDeLaDerivacion}, que dice por que la ultima corrida no propuso ninguno. Es el mismo
 * criterio con el que la deteccion de subvaluadores se niega a devolver cero sin cartografia (#6,
 * AC 8): nadie revisa un cero.
 *
 * @param derivadoEn cuando corrio el derivador sobre este predio; nulo si no ha corrido nunca
 * @param frentesDerivados cuantos propuso esa corrida; nulo si no ha corrido nunca
 * @param motivoDeLaDerivacion por que no propuso ninguno; nulo si propuso alguno o si no corrio
 */
public record FrentesDelPredioResource(
        long predioId,
        List<FrenteResource> frentes,
        @Nullable String derivadoEn,
        @Nullable Integer frentesDerivados,
        @Nullable String motivoDeLaDerivacion) {

    /** Lo consultado, tal como sale por HTTP. */
    public static FrentesDelPredioResource de(ConsultaDeFrentes.FrentesConsultados consultado) {
        DerivacionDeFrentes derivacion = consultado.ultimaDerivacion().orElse(null);
        return new FrentesDelPredioResource(
                consultado.predioId(),
                consultado.frentes().stream().map(FrenteResource::de).toList(),
                derivacion == null ? null : derivacion.derivadoEn().toString(),
                derivacion == null ? null : derivacion.propuestos(),
                derivacion == null ? null : derivacion.motivo());
    }
}
