/**
 * Los casos de uso de {@code urbano}: consultar la zona de un predio y cargar un plan (#4).
 *
 * <p>Aqui viven el {@code @Transactional} y, con el, el {@code SET LOCAL app.municipalidad_id} que
 * la politica RLS exige: una consulta fuera de transaccion corre sin contexto y la base la rechaza,
 * que es exactamente lo que debe pasar.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.urbano.aplicacion;
