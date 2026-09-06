package kamayuk.catastro.grd;

import java.time.LocalDate;

/**
 * Lo que otro contexto necesita saber de un lote para decidir, y nada mas (#5).
 *
 * <p><b>{@code enRiesgoNoMitigable} es el campo que decide, y por eso va aparte del nivel.</b> Una
 * zona de riesgo MUY ALTO <i>mitigable</i> no impide nada —se construye la obra de mitigacion—; una
 * no mitigable si. Publicar solo el nivel obligaria a quien lee a volver a mirar cada zona para
 * saber cual de las dos cosas tiene delante, y esa segunda mirada es la que se olvida.
 *
 * <p><b>Lleva su fecha</b> (regla 9): las dos afirmaciones caducan —una carta de peligro se
 * sustituye y un certificado vence—, asi que una respuesta sin fecha es una respuesta que manana es
 * otra.
 *
 * @param zonasDeRiesgo cuantas zonas vigentes intersectan el lote; cero es una respuesta, no una
 *     ausencia
 * @param enRiesgoNoMitigable si alguna de ellas no es mitigable
 * @param enFajaMarginal si el lote toca una faja marginal vigente de la ANA
 * @param conItseVigente si hay al menos un certificado vigente y sin anular a esa fecha
 */
public record SituacionDelPredio(
        long predioId,
        LocalDate aLaFecha,
        int zonasDeRiesgo,
        boolean enRiesgoNoMitigable,
        boolean enFajaMarginal,
        boolean conItseVigente) {}
