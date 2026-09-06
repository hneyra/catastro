package kamayuk.catastro.nucleo.infraestructura.web;

import org.jspecify.annotations.Nullable;

/**
 * La peticion que confirma la longitud de un frente (#7, AC 2).
 *
 * <h2>Trae la longitud, y no es un boton de aceptar</h2>
 *
 * <p>Lo normal es que quien confirma haya ido con la cinta: un acto que solo pudiera decir «si a lo
 * que salio» dejaria la distincion entre propuesta y medida sin significado. La propuesta anterior
 * no se pierde — queda en la auditoria, con el antes y el despues.
 *
 * <p><b>Sin geometria dentro</b> (ADR-0021, {@code TODA_GEOMETRIA_ENTRA_POR_BATCH}): confirmar es
 * afirmar unos metros, no redibujar el tramo. Un poligono que entrara por aqui cambiaria el padron
 * sin brigada, sin plano y sin acta.
 *
 * @param longitud los metros lineales que se afirman, como cadena decimal ({@code "18.50"}). Sin
 *     unidad dentro: la unidad de un frente es {@code ML} y la fija el dominio
 * @param observacion por que se confirma (regla 10, RNF-052). Sin ella no se guarda
 */
public record ConfirmacionDeFrente(@Nullable String longitud, @Nullable String observacion) {}
