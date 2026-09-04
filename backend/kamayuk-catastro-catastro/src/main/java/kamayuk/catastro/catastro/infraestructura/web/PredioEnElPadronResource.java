package kamayuk.catastro.catastro.infraestructura.web;

/**
 * Si el predio esta inscrito en el padron de esta municipalidad (C-5).
 *
 * <h2>Publica dos campos, y lo que NO publica es la decision</h2>
 *
 * <p>No lleva el {@code estado}. Un predio <b>dado de baja sigue estando en el padron</b>, y de eso
 * depende que su deuda se pueda seguir moviendo: #660 lo midio al reves —cerrarle la puerta a la
 * correccion de una deuda cuya unidad ya no existe dejaba esa deuda viva y sin forma de
 * extinguirla— y #680 lo dejo escrito, «un predio dado de baja sigue estando en el padron y su
 * deuda se tiene que poder mover». Publicar aqui el estado invitaria a que quien lee lo filtrara, y
 * eso es reintroducir ese defecto desde el otro lado de la frontera.
 *
 * <p>Tampoco es un {@code 404}: el 404 de esta frontera significa «esa ruta no existe». Ver {@link
 * CaracteristicasDelPredioResource}.
 */
public record PredioEnElPadronResource(long predioId, boolean enElPadron) {}
