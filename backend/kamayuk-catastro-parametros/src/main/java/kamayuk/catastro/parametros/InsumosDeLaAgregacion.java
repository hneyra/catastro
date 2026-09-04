package kamayuk.catastro.parametros;

import kamayuk.catastro.dominio.Ejercicio;
import kamayuk.catastro.dominio.PoliticaDeRedondeo;
import kamayuk.catastro.dominio.PoliticasDeRedondeo;
import kamayuk.catastro.dominio.PuntoDeRedondeo;
import kamayuk.catastro.dominio.ValorNormativo;

/**
 * Lo que una {@link ReglaDeAgregacion} puede leer ademas de los aportes: ejercicio, parametros
 * sellados y politicas de redondeo. Ni reloj ni base de datos, igual que una regla corriente.
 */
public record InsumosDeLaAgregacion(
        Ejercicio ejercicio, ParametrosSellados parametros, PoliticasDeRedondeo redondeo) {

    /** El parametro numerico que la regla necesita; si falta, no se produce importe. */
    public ValorNormativo numero(String tipo, String clave) {
        return parametros.exigirNumero(tipo, clave);
    }

    /**
     * La politica del punto que la agregacion redondea. Nombrar el punto es obligatorio: ver {@link
     * PuntoDeRedondeo} y D-03c.
     */
    public PoliticaDeRedondeo redondeoEn(PuntoDeRedondeo punto) {
        return redondeo.en(punto);
    }
}
