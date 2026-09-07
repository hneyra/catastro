package kamayuk.catastro.urbano;

import java.time.LocalDate;
import java.util.List;

/**
 * A que zona cae un predio, a una fecha (#4).
 *
 * <p>Es la puerta que este modulo publica. La contesta {@code ConsultaDeZonificacion}, y quien la
 * llama por HTTP es {@code ZonificacionController}; el dia que {@code rentas} necesite la zona para
 * evaluar una licencia, lo que consumira por HTTP es lo que esta interfaz devuelve.
 *
 * <p><b>Ningun metodo recibe el identificador de municipalidad</b> (regla 2): sale del token y se
 * fija una vez con {@code SET LOCAL}.
 */
public interface ZonificacionDelPredio {

    /**
     * La zona vigente a esa fecha para ese predio, con los parametros urbanisticos que rigen.
     *
     * <p><b>Nunca devuelve una zona nula, y esa es la mitad que importa.</b> Un predio sin poligono
     * no esta en ninguna zona <i>que se sepa</i>, y contestar «zona: null» seria indistinguible de
     * «este predio esta en zona nula» — que acaba en una licencia mal negada. Hoy no hay ni un
     * poligono cargado en ninguna instalacion, asi que este es el camino que se recorre siempre al
     * principio: por eso se separa en su propia excepcion y no en un valor.
     *
     * @throws PredioInexistente si ese predio no esta en el padron de esta municipalidad
     * @throws PredioSinGeometria si el predio existe y no tiene poligono
     * @throws SinZonaVigente si el predio tiene poligono y ningun plan vigente a esa fecha lo cubre
     * @throws ZonaAmbigua si mas de una zona vigente lo cubre, que es un hallazgo y no una zona
     */
    ZonaVigente zonaDe(long predioId, LocalDate aLaFecha);

    /** Ese predio no esta en el padron de esta municipalidad. */
    final class PredioInexistente extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        public PredioInexistente(long predioId) {
            super("No hay ningun predio con identificador " + predioId + " en esta municipalidad");
        }
    }

    /**
     * El predio existe y no tiene poligono, asi que no se puede decir en que zona cae.
     *
     * <p>No es un error del sistema ni una zona vacia: es que falta el dato cartografico. El
     * mensaje lo dice para que quien atiende sepa que hay que cargar el plano y no que hay que
     * corregir la ordenanza.
     */
    final class PredioSinGeometria extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        public PredioSinGeometria(long predioId) {
            super(
                    "El predio "
                            + predioId
                            + " no tiene poligono cargado, asi que no se puede decir a que zona"
                            + " cae. Se carga con el plano catastral (ADR-0021)");
        }
    }

    /**
     * Mas de una zona vigente cubre el punto interior del lote, y eso <b>se informa</b> (#22).
     *
     * <p>No se elige una. La consulta cerraba con {@code LIMIT 1} y sin {@code ORDER BY}, asi que
     * la zona que salia la decidia el plan de ejecucion y podia cambiar entre corridas sobre los
     * mismos datos — y de esa respuesta cuelga si una licencia se concede o se niega. El javadoc de
     * la propia consulta ya prometia lo contrario: «cuando el lote cruza dos zonas lo que hay es un
     * hallazgo que se informa, no una respuesta que el sistema se inventa».
     *
     * <p>Los dos codigos van <b>dentro del mensaje</b> porque son lo unico con lo que se corrige:
     * quien lo reciba tiene que saber que dos filas del plan se pisan. Desde {@code V11} el motor
     * lo impide al cargar, asi que esto solo puede venir de datos anteriores a esa migracion o de
     * dos zonas de PLANES distintos que el otro camino admite.
     */
    final class ZonaAmbigua extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        public ZonaAmbigua(long predioId, LocalDate aLaFecha, List<String> codigos) {
            super(
                    "El predio "
                            + predioId
                            + " cae en mas de una zona vigente al "
                            + aLaFecha
                            + " ("
                            + String.join(", ", codigos)
                            + "), asi que «la zona de este predio» no tiene una respuesta. Es un"
                            + " hallazgo del plan de zonificacion y se corrige en el, no aqui");
        }
    }

    /** El predio tiene poligono y ningun plan vigente a esa fecha lo cubre. */
    final class SinZonaVigente extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        public SinZonaVigente(long predioId, LocalDate aLaFecha) {
            super(
                    "Ningun plan de zonificacion vigente al "
                            + aLaFecha
                            + " cubre el predio "
                            + predioId);
        }
    }
}
