package kamayuk.catastro.fiscalizacion.dominio;

/**
 * De donde salio la sospecha (ADR-0035 punto 1).
 *
 * <p>Se guarda porque la tasa de descarte <b>por origen</b> es lo unico que dice si una fuente de
 * insumos vale lo que cuesta: un vuelo de dron cuyo 90 % se descarta en gabinete no es un detector,
 * es un gasto. Sin esta columna, todos los descartes se leen juntos y esa pregunta no se puede
 * hacer.
 */
public enum OrigenDelCandidato {

    /** Vuelo fotogrametrico o imagen satelital ortorrectificada. */
    ORTOFOTO,

    /** Levantamiento con vehiculo aereo no tripulado. */
    DRON,

    /** El cruce del area de la ficha contra la del poligono inscrito (ADR-0021). */
    CRUCE_DE_AREAS,

    /** Aviso de un tercero. */
    DENUNCIA,

    /** Recorrido de una brigada, manzana por manzana. */
    BARRIDO_DE_CAMPO
}
