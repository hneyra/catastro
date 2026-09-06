/**
 * Gestion del riesgo de desastres del predio (#5): zona de riesgo, faja marginal e ITSE.
 *
 * <p>Este paquete raiz es la <b>API publica</b> del contexto (ARQ-01 §4.1): lo unico que otro
 * modulo puede importar. Lo que hay es un lector —{@link
 * kamayuk.catastro.grd.LectorDeGestionDeRiesgo}— y el resumen que devuelve.
 *
 * <p><b>Publica un hecho y ninguna consecuencia</b> (ADR-0024). Dice si el lote intersecta una zona
 * de riesgo no mitigable y si el predio tiene ITSE vigente a una fecha; no dice si se puede dar una
 * licencia. Quien decide es quien emite, y vive en {@code rentas}.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.grd;
