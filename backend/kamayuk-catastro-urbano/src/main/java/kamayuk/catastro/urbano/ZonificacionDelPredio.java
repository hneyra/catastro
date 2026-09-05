package kamayuk.catastro.urbano;

import java.time.LocalDate;

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
