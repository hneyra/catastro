package kamayuk.catastro.catastro.dominio;

/**
 * Un predio no se borra: se da de baja (RNF-051). Su codigo aparece en determinaciones emitidas.
 */
public enum EstadoPredio {
    ACTIVO,
    DADO_DE_BAJA
}
