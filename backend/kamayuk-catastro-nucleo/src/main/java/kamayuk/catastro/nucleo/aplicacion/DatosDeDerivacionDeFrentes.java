package kamayuk.catastro.nucleo.aplicacion;

import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lo que hay que saber para derivar los frentes de una municipalidad (#7).
 *
 * <p>Mismo trato que {@code DatosDeCargaZonificacion}: propiedades y no argumentos de linea de
 * comandos, para que no queden en el historial del proceso ni en los registros del orquestador.
 *
 * <p><b>Sin propiedad «es de demostracion»</b>, por lo mismo: esto corre sobre municipalidades de
 * verdad y lo que produce no son datos inventados, sino propuestas que una persona confirma.
 *
 * <h2>La tolerancia es una propiedad y no una constante, y hay que decir por que</h2>
 *
 * <p>Porque no sale de ninguna norma: es cuanto se admite que el borde del lote se separe del eje
 * de la calzada para seguir considerando que da a esa via, y eso depende del ancho de las calles
 * del sitio y de la calidad del levantamiento. Un valor en el codigo obligaria a desplegar para
 * ajustarlo en un distrito con calles de veinte metros.
 *
 * <p><b>No es un valor tributario y la regla 5 no le aplica</b>: no es una alicuota, ni un tramo,
 * ni un arancel, ni un valor unitario. Es un parametro de un algoritmo geometrico, y lo que sale de
 * el es una PROPUESTA que nadie puede cobrar sin confirmarla (ADR-0021).
 *
 * @param municipalidadId identificador ya existente de la municipalidad cuyos frentes se derivan
 * @param toleranciaM a cuantos metros del eje se considera que el borde del lote da a esa via
 * @param tope cuantos predios como mucho recorre esta corrida
 * @param usuarioDelProceso con que nombre firma la auditoria lo que hace este proceso
 * @param observacion el «por que» de la derivacion (regla 10, ADR-0008)
 */
@ConfigurationProperties("kamayuk.derivacion-de-frentes")
public record DatosDeDerivacionDeFrentes(
        long municipalidadId,
        String toleranciaM,
        int tope,
        String usuarioDelProceso,
        String observacion) {

    /**
     * Metros por omision.
     *
     * <p>Media calzada de una via local peruana ronda los cinco metros, y el borde de un lote
     * levantado con GPS de mano se separa del eje algo mas. Es un valor de partida y no una verdad:
     * la propiedad existe justamente para cambiarlo sin desplegar.
     */
    private static final String TOLERANCIA_POR_OMISION = "8.00";

    /** Cuantos predios recorre una corrida si nadie dice otra cosa. */
    private static final int TOPE_POR_OMISION = 5000;

    public DatosDeDerivacionDeFrentes {
        if (municipalidadId < 1) {
            throw new IllegalArgumentException(
                    "Falta kamayuk.derivacion-de-frentes.municipalidad-id, o no es un identificador"
                            + " valido");
        }
        toleranciaM =
                toleranciaM == null || toleranciaM.isBlank()
                        ? TOLERANCIA_POR_OMISION
                        : toleranciaM.strip();
        tope = tope < 1 ? TOPE_POR_OMISION : tope;
        usuarioDelProceso =
                usuarioDelProceso == null || usuarioDelProceso.isBlank()
                        ? "derivacion-de-frentes"
                        : usuarioDelProceso;
        observacion =
                observacion == null || observacion.isBlank()
                        ? "Derivacion automatica de frentes: propuesta, no medida (#7, ADR-0021)"
                        : observacion;
    }

    /** La tolerancia como medida, con su unidad. Se valida al construirla, no al usarla. */
    public Medida tolerancia() {
        return Medida.de(toleranciaM, FrenteDelPredio.UNIDAD);
    }
}
