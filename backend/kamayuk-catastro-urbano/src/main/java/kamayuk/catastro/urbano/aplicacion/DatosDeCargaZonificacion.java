package kamayuk.catastro.urbano.aplicacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lo que hay que saber para cargar el plan de zonificacion de una municipalidad (#4).
 *
 * <p>Mismo trato que {@code DatosDeCargaPredios}: propiedades y no argumentos de linea de comandos,
 * para que no queden en el historial del proceso ni en los registros del orquestador.
 *
 * <p><b>No hay una propiedad «es de demostracion».</b> Este cargador escribe en municipalidades de
 * verdad —es su unico motivo de existir— y por eso no lleva la guarda {@code SoloEnDemostracion}
 * que si llevan los pasos de la siembra de ejemplo. Lo que carga no son datos inventados: es el
 * plan de desarrollo urbano de la propia municipalidad, aprobado por ordenanza.
 *
 * @param municipalidadId identificador ya existente de la municipalidad cuyo plan se carga
 * @param archivo ruta al CSV con las zonas del plan y sus parametros urbanisticos
 * @param usuarioDelProceso con que nombre firma la auditoria lo que hace este proceso
 * @param observacion el «por que» de la carga (regla 10, ADR-0008)
 */
@ConfigurationProperties("kamayuk.carga-zonificacion")
public record DatosDeCargaZonificacion(
        long municipalidadId, String archivo, String usuarioDelProceso, String observacion) {

    public DatosDeCargaZonificacion {
        if (municipalidadId < 1) {
            throw new IllegalArgumentException(
                    "Falta kamayuk.carga-zonificacion.municipalidad-id, o no es un identificador"
                            + " valido");
        }
        archivo = exigir(archivo, "kamayuk.carga-zonificacion.archivo");
        usuarioDelProceso =
                usuarioDelProceso == null || usuarioDelProceso.isBlank()
                        ? "carga-zonificacion"
                        : usuarioDelProceso;
        observacion =
                observacion == null || observacion.isBlank()
                        ? "Carga del plan de zonificacion aprobado por ordenanza (#4)"
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
