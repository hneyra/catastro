package kamayuk.catastro.nucleo.infraestructura.web;

import kamayuk.catastro.dominio.Porcentaje;
import kamayuk.catastro.nucleo.PredioDelContribuyente;

/**
 * Un predio de un contribuyente, con su cuota y con cuanto del predio esta registrado (C-5).
 *
 * <h2>Los dos porcentajes son dos cosas distintas y las dos viajan</h2>
 *
 * <p>{@code porcentajeTitularidad} es la cuota de quien pregunta: es lo que <b>pondera su base
 * imponible</b>, y calcular sin el produce un error sistematico en todo el padron (#395 lo midio:
 * dos predios de 100 000 al 50 % dando una base de 200 000 donde debe decir 100 000).
 *
 * <p>{@code porcentajeRegistradoDelPredio} es lo que suma la titularidad <b>entera</b> de ese
 * predio, y sirve para otra cosa: avisar de que el saneamiento esta incompleto (#690). No se deriva
 * del anterior y no se puede: en una copropiedad bien saneada el primero es 50 y el segundo 100.
 */
public record PredioDelTitularResource(
        long predioId,
        String codRefCatastral,
        String tipo,
        String direccion,
        Porcentaje porcentajeTitularidad,
        Porcentaje porcentajeRegistradoDelPredio) {

    public static PredioDelTitularResource de(PredioDelContribuyente predio) {
        return new PredioDelTitularResource(
                predio.predioId(),
                predio.codigoReferenciaCatastral(),
                predio.tipo(),
                predio.direccion(),
                predio.porcentajeTitularidad(),
                predio.porcentajeRegistradoDelPredio());
    }
}
