package kamayuk.catastro.nucleo.aplicacion;

import java.util.List;
import java.util.Optional;
import kamayuk.catastro.nucleo.dominio.DerivacionDeFrentes;
import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import kamayuk.catastro.nucleo.dominio.FrentesDelPredio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los frentes de un predio, y desde cuando no se derivan (#7, AC 3).
 *
 * <p><b>El {@code @Transactional(readOnly = true)} no es decorativo</b>: sin transaccion no se
 * emite el {@code SET LOCAL app.municipalidad_id}, la politica RLS de {@code frente_predio} no
 * encuentra el parametro y la consulta <b>no devuelve vacio: revienta</b> con «invalid input syntax
 * for type bigint». Es el defecto que #486 midio y que {@code ConsultaDeVias} documenta.
 */
@Service
public class ConsultaDeFrentes {

    private final FrentesDelPredio frentes;

    public ConsultaDeFrentes(FrentesDelPredio frentes) {
        this.frentes = frentes;
    }

    /**
     * Los frentes de un predio, con la constancia de la ultima derivacion.
     *
     * <p>La lista vacia es una respuesta legitima —y hoy la mas frecuente— y por eso <b>viaja
     * acompanada</b>: ver {@link DerivacionDeFrentes}. Sin ella, «no da a ninguna calle» y «nadie
     * lo ha calculado» serian la misma respuesta y se arreglarian de maneras distintas.
     *
     * @throws PredioInexistente si el predio no esta en el padron de esta municipalidad. Es 404 y
     *     no una lista vacia: decir «no tiene frentes» de un predio que no existe manda a quien
     *     atiende a buscar un dato que falta en vez del predio que escribio mal
     */
    @Transactional(readOnly = true)
    public FrentesConsultados delPredio(long predioId) {
        if (!frentes.existeElPredio(predioId)) {
            throw new PredioInexistente(predioId);
        }
        return new FrentesConsultados(
                predioId, frentes.deUnPredio(predioId), frentes.ultimaDerivacion(predioId));
    }

    /**
     * Lo que la consulta devuelve.
     *
     * @param ultimaDerivacion vacio significa <b>nunca se ha derivado</b>, que no es lo mismo que
     *     haberlo hecho y no encontrar nada
     */
    public record FrentesConsultados(
            long predioId,
            List<FrenteDelPredio> frentes,
            Optional<DerivacionDeFrentes> ultimaDerivacion) {}

    /** No hay tal predio en esta municipalidad. */
    public static final class PredioInexistente extends RuntimeException {

        @java.io.Serial private static final long serialVersionUID = 1L;

        public PredioInexistente(long predioId) {
            super(
                    "El predio "
                            + predioId
                            + " no esta en el padron de esta municipalidad: no se pueden consultar"
                            + " los frentes de un predio que no existe");
        }
    }
}
