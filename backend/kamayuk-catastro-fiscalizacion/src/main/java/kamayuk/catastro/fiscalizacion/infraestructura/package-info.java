/**
 * Persistencia y borde HTTP del hallazgo catastral.
 *
 * <p>Ninguna consulta filtra por {@code municipalidad_id} y ningun {@code INSERT} lo recibe de
 * Java: lo pone el motor con {@code current_setting}, del mismo parametro que la politica RLS
 * consulta (regla 2).
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.fiscalizacion.infraestructura;
