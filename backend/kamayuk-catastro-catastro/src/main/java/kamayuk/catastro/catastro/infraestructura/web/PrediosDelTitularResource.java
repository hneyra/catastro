package kamayuk.catastro.catastro.infraestructura.web;

import java.time.LocalDate;
import java.util.List;

/**
 * Los predios de un contribuyente a una fecha (C-5).
 *
 * <p>{@code contribuyenteId} y {@code aLaFecha} vuelven en la respuesta a proposito. Esta es la
 * lectura de la que sale la base del impuesto predial, y una lista vacia aqui se lee como «este
 * contribuyente no tiene ningun predio»: si por un error de nombre de parametro la respuesta fuera
 * de otra persona, o de otra fecha, ninguna cifra pareceria mal. Quien lee comprueba las dos —el
 * guardia de #298— y se niega si no coinciden.
 */
public record PrediosDelTitularResource(
        long contribuyenteId, LocalDate aLaFecha, List<PredioDelTitularResource> predios) {}
