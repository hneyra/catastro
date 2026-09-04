package kamayuk.catastro.catastro.infraestructura.web;

import kamayuk.catastro.catastro.TitularDelPredio;
import kamayuk.catastro.dominio.Porcentaje;

/**
 * Una cuota de titularidad vigente, tal como la publica {@code GET /catastro/titularidad} (C-5).
 *
 * <p>No lleva {@code titularidadId}: quien pregunta «de quien es este predio» no necesita el
 * identificador de la fila, y publicarlo aqui pondria al alcance de esta lectura el dato con el que
 * se <b>transfiere</b> una cuota. Quien si lo necesita —el registro de una transferencia— lo pide
 * por su ruta, {@code /catastro/titularidad/cuota}, que es de un titular y un predio cada vez.
 *
 * <p>El {@code contribuyenteId} es el identificador interno del padron, no el codigo ni el
 * documento: la correlacion predio→persona con el codigo la sirve {@code rentas} bajo el permiso
 * del padron, y eso no cambia (#366, ADR-0015 §2.4).
 */
public record CuotaDeUnTitularResource(
        long contribuyenteId, String condicion, Porcentaje porcentaje) {

    public static CuotaDeUnTitularResource de(TitularDelPredio cuota) {
        return new CuotaDeUnTitularResource(
                cuota.contribuyenteId(), cuota.condicion(), cuota.porcentaje());
    }
}
