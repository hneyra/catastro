package kamayuk.catastro.grd;

import java.time.LocalDate;

/**
 * Lo que este contexto le cuenta al resto sobre un lote: su riesgo y su ITSE (#5).
 *
 * <p>Es la <b>unica</b> puerta del contexto hacia fuera, y devuelve un {@link SituacionDelPredio}
 * —dos booleanos y sus cuentas— y no las entidades: quien pregunta esto es quien tiene que decidir
 * si emite una licencia, y para eso no necesita el poligono de la zona ni el numero del
 * certificado.
 *
 * <p><b>Ningun metodo recibe el identificador de municipalidad</b> (regla 2): sale del token y lo
 * fija {@code SET LOCAL} al abrir la transaccion.
 *
 * <p><b>Y la fecha entra como argumento</b>, no se lee del reloj (regla 6 y regla 9): «tiene ITSE»
 * no es una afirmacion, es «tiene ITSE al 12 de marzo de 2026». Preguntar hoy por una licencia que
 * se emitio en marzo tiene que dar la misma respuesta que se dio entonces.
 */
public interface LectorDeGestionDeRiesgo {

    /**
     * La situacion del predio a una fecha.
     *
     * @throws kamayuk.catastro.grd.dominio.PredioSinGeometria si el lote no tiene poligono: sobre
     *     un predio sin geometria «cero zonas de riesgo» es una respuesta falsa
     */
    SituacionDelPredio situacionDe(long predioId, LocalDate aLaFecha);
}
