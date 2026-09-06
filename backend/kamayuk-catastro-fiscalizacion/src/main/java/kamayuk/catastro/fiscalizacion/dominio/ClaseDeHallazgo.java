package kamayuk.catastro.fiscalizacion.dominio;

/**
 * Que clase de hecho se sospecha, y despues se verifica (ADR-0035 §Contexto).
 *
 * <p>Son <b>dos y no tres</b>. El tercero de la tabla del ADR —el <b>omiso declarativo</b>: hay
 * predio, falta la declaracion jurada del ejercicio— ya esta resuelto y no es un hallazgo: lo
 * contesta {@code conciliadoA(ejercicio)} desde {@code rentas} (ADR-0015), y es una CONSULTA
 * derivable de lo que hay. Meterlo aqui produciria dos verdades sobre el mismo hecho y la que se
 * leyera seria la que nadie recalculo.
 *
 * <p>Los dos que si estan tienen en comun lo que los hace hallazgos: su insumo <b>entra de
 * fuera</b> —una ortofoto, una brigada— y necesita que una persona lo confirme antes de tener
 * efecto.
 */
public enum ClaseDeHallazgo {

    /**
     * Hay techo en la ortofoto y <b>no hay fila de {@code predio}</b>.
     *
     * <p>Por definicion no tiene predio al que apuntar, y de ahi sale que {@code
     * candidato.predio_id} sea nulable. Confirmado, habilita {@code InscribirFicha}, que es el acto
     * que ya existe y que crea predio y ficha en el mismo acto — no lo ejecuta este modulo.
     */
    OMISO_CATASTRAL,

    /**
     * {@code ficha_catastral.area_terreno} no coincide con el area medida.
     *
     * <p>Es la frase de ADR-0021 hecha entidad. Confirmado, habilita versionar la ficha con su
     * observacion; y NO la versiona: eso deja el padron corregido sin acto administrativo detras,
     * que es exactamente lo que ADR-0035 punto 4 prohibe.
     */
    SUBVALUADOR
}
