package kamayuk.catastro.fiscalizacion.aplicacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lo que hay que saber para correr la deteccion de subvaluadores de una campania (#30).
 *
 * <p>Mismo trato que {@code DatosDeDerivacionDeFrentes} y {@code DatosDeCargaZonificacion}:
 * propiedades y no argumentos de linea de comandos, para que no queden en el historial del proceso
 * ni en los registros del orquestador.
 *
 * <h2>Ni umbral ni tope, y eso es de #25</h2>
 *
 * <p>Los dos los <b>congela la campania</b> desde #25, que es lo que permite comparar dos tasas de
 * descarte: sin saber con que umbral detecto cada una, las dos cifras no se pueden poner una al
 * lado de la otra. Volver a ofrecerlos aqui seria dejar que una corrida contradijera la fila que la
 * campania guarda, que es exactamente el defecto que aquel issue midio.
 *
 * <h2>La campania se nombra por su identificador, y hay que decir por que</h2>
 *
 * <p>Porque es lo que devuelve el {@code POST /fiscalizacion/campanias} que la abrio, y es la clave
 * con la que {@link DetectarSubvaluadores} la busca. Aceptar el <b>codigo</b> —que es lo que una
 * persona teclea— obligaria a este runner a resolverlo, o sea a tener una lectura mas y un segundo
 * modo de fallo («ese codigo no existe») que hoy no tiene.
 *
 * @param municipalidadId identificador ya existente de la municipalidad cuyo padron se contrasta
 * @param campaniaId la campania ABIERTA a la que se le cuelgan los candidatos
 * @param usuarioDelProceso con que nombre firma la auditoria lo que hace este proceso
 * @param observacion el «por que» de la corrida (regla 10, ADR-0008)
 */
@ConfigurationProperties("kamayuk.deteccion-de-subvaluadores")
public record DatosDeDeteccionDeSubvaluadores(
        long municipalidadId, long campaniaId, String usuarioDelProceso, String observacion) {

    public DatosDeDeteccionDeSubvaluadores {
        if (municipalidadId < 1) {
            throw new IllegalArgumentException(
                    "Falta kamayuk.deteccion-de-subvaluadores.municipalidad-id, o no es un"
                            + " identificador valido");
        }
        if (campaniaId < 1) {
            throw new IllegalArgumentException(
                    "Falta kamayuk.deteccion-de-subvaluadores.campania-id, o no es un"
                            + " identificador valido. Es el que devolvio el alta de la campania");
        }
        usuarioDelProceso =
                usuarioDelProceso == null || usuarioDelProceso.isBlank()
                        ? "deteccion-de-subvaluadores"
                        : usuarioDelProceso;
        observacion =
                observacion == null || observacion.isBlank()
                        ? "Corrida de deteccion de subvaluadores: sospecha, no correccion"
                                + " (ADR-0021, ADR-0035)"
                        : observacion;
    }
}
