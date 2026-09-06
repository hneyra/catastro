package kamayuk.catastro.grd.aplicacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lo que hay que saber para cargar la carta de restricciones de una municipalidad (#5, AC-7).
 *
 * <p>Mismo trato que {@code DatosDeCargaPredios}: propiedades y no argumentos de linea de comandos,
 * para que no queden en el historial del proceso ni en los registros del orquestador.
 *
 * <p><b>No hay una propiedad «es de demostracion»</b>, y es la misma decision que tomo el cargador
 * del plano: la carta de peligro de CENEPRED y la faja marginal de la ANA <b>no son datos
 * inventados</b>, son actos de dos organismos del Estado sobre el territorio de esa municipalidad.
 * Exigir {@code es_demostracion} dejaria a una instalacion de verdad sin forma de cargarlos, que es
 * el hueco que #430 encontro para {@code area} y {@code caja}.
 *
 * @param municipalidadId identificador ya existente de la municipalidad cuya carta se carga
 * @param archivo ruta al CSV de dos capas: PELIGRO y FAJA_MARGINAL
 * @param usuarioDelProceso con que nombre firma la auditoria lo que hace este proceso
 * @param observacion el «por que» de la carga (regla 10, ADR-0008)
 */
@ConfigurationProperties("kamayuk.carga-riesgo")
public record DatosDeCargaRiesgo(
        long municipalidadId, String archivo, String usuarioDelProceso, String observacion) {

    public DatosDeCargaRiesgo {
        if (municipalidadId < 1) {
            throw new IllegalArgumentException(
                    "Falta kamayuk.carga-riesgo.municipalidad-id, o no es un identificador valido");
        }
        archivo = exigir(archivo, "kamayuk.carga-riesgo.archivo");
        usuarioDelProceso =
                usuarioDelProceso == null || usuarioDelProceso.isBlank()
                        ? "carga-riesgo"
                        : usuarioDelProceso;
        observacion =
                observacion == null || observacion.isBlank()
                        ? "Carga de la carta de riesgo y faja marginal (#5)"
                        : observacion;
    }

    private static String exigir(String valor, String propiedad) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Falta " + propiedad + ", que no tiene valor por omision");
        }
        return valor.strip();
    }
}
