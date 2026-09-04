package kamayuk.catastro.catastro.infraestructura.web;

import java.time.LocalDate;
import java.util.List;

/**
 * De quienes son estos predios, a una fecha (C-5).
 *
 * <h2>Una peticion para una pagina entera, y por que la forma es esta</h2>
 *
 * <p>El puerto que esto sirve declara {@code deVarios(Collection<Long>, LocalDate)} y esa forma la
 * conservo P5C a proposito: una pagina de veinte omisos tiene que costar <b>una</b> peticion y no
 * veinte. Por eso {@code predio} es un parametro repetido y la respuesta agrupa.
 *
 * <p><b>Solo salen los predios que tienen alguna cuota vigente</b>, igual que hacia el puerto
 * dentro del proceso: un predio pedido y sin titular no aparece. Devolver una entrada con la lista
 * vacia seria mas informativo y cambiaria lo que el mapa contesta —{@code null} pasaria a ser lista
 * vacia— para los cuatro sitios que ya lo consumen, y esta frontera no esta para cambiar
 * comportamiento sino para trasladarlo.
 *
 * <p>{@code aLaFecha} es la fecha con la que se resolvio, no la que se pidio: es lo unico con lo
 * que quien lee comprueba que su criterio llego (C-1, desajuste 3).
 */
public record TitularesDeLosPrediosResource(
        LocalDate aLaFecha, List<TitularesDeUnPredioResource> predios) {}
