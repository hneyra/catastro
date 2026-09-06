/**
 * Casos de uso de la gestion del riesgo: la frontera transaccional (ARQ-04 §1).
 *
 * <p>Aqui empieza y termina la transaccion, y por tanto aqui es donde el contexto de municipalidad
 * llega a la base con {@code SET LOCAL}. Las lecturas tambien la abren, y no es ceremonia: sin ella
 * la politica RLS no encuentra {@code app.municipalidad_id} y la consulta <b>falla</b>, que es
 * exactamente lo que debe pasar.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.grd.aplicacion;
