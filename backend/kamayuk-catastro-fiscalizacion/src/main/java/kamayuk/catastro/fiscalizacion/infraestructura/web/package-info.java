/**
 * El borde HTTP de la fiscalizacion catastral, bajo {@code /catastro/api/v1/fiscalizacion}.
 *
 * <p>Los cuerpos son <b>listas blancas</b>: lo que no esta declarado no entra, aunque llegue en el
 * JSON. Ninguno acepta geometria (ADR-0021, {@code TODA_GEOMETRIA_ENTRA_POR_BATCH}): un poligono
 * entra por la carga cartografica, con su plano y su acta.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.fiscalizacion.infraestructura.web;
