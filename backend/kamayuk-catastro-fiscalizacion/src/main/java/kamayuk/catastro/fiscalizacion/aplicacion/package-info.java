/**
 * Los casos de uso del hallazgo catastral: abrir la campania, detectar, las dos compuertas, la
 * evidencia y el acta.
 *
 * <p>Cada transicion de estado de un candidato es una ESCRITURA, asi que cada una exige su {@link
 * kamayuk.catastro.dominio.Observacion} (regla 10). Las consultas van
 * {@code @Transactional(readOnly = true)}: sin transaccion no hay {@code SET LOCAL} y la politica
 * RLS rechaza la consulta (#486).
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.fiscalizacion.aplicacion;
