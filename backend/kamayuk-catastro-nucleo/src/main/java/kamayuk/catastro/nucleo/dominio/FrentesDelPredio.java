package kamayuk.catastro.nucleo.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;

/**
 * Los frentes de un predio: leerlos, proponerlos y confirmarlos (#7).
 *
 * <p>Como el resto de repositorios de este contexto: ningun metodo recibe {@code municipalidadId}
 * —lo pone la politica RLS con lo que {@code SET LOCAL} fijo (regla 2)— y no hay un solo {@code
 * DELETE}. Una propuesta equivocada no se borra: se corrige confirmando la medida buena, y la
 * constancia de que hubo una propuesta se queda.
 */
public interface FrentesDelPredio {

    /** Los frentes inscritos de un predio, con su via, en orden estable. */
    List<FrenteDelPredio> deUnPredio(long predioId);

    /**
     * Cuando se derivaron por ultima vez los frentes de ese predio.
     *
     * <p>Vacio significa <b>que no se ha derivado nunca</b>, que no es lo mismo que haberlo hecho y
     * no encontrar nada. Ver {@link DerivacionDeFrentes}.
     */
    Optional<DerivacionDeFrentes> ultimaDerivacion(long predioId);

    /** Si el predio existe en el padron de esta municipalidad. */
    boolean existeElPredio(long predioId);

    /**
     * Los predios que el derivador tiene que recorrer, en orden de identificador.
     *
     * @param desde el ultimo identificador ya recorrido, para poder seguir donde se dejo
     * @param tope cuantos como mucho
     */
    List<Long> prediosPorDerivar(long desde, int tope);

    /**
     * Corta el lote contra el eje de calzada de las vias que lo bordean, y propone un tramo por
     * via.
     *
     * <p><b>Aqui no se decide nada:</b> lo que devuelve son propuestas (ADR-0021). La lista vacia
     * es una respuesta legitima y frecuente —hoy no hay ni un poligono cargado—, y por eso quien
     * llama tiene que decir <b>por que</b> salio vacia al anotar la derivacion.
     *
     * @param tolerancia a que distancia del eje se considera que el borde del lote da a esa via
     */
    List<FrentePropuesto> cortarContraLasVias(long predioId, Medida tolerancia);

    /**
     * Escribe una propuesta, si ese predio no tenia ya un frente a esa via.
     *
     * <p><b>Devuelve la longitud TAL COMO QUEDO GUARDADA</b>, y no un {@code boolean}, por un
     * motivo que se descubrio al escribirlo: el corte mide sobre el elipsoide y produce todos los
     * decimales que produce, mientras la columna es {@code numeric(12,2)}. Quien decide con cuantos
     * decimales se guarda una longitud es <b>el esquema</b> —dato versionado (ADR-0032)—, no este
     * codigo: escribir aqui un {@code setScale(2, HALF_UP)} seria tomar por descuido la decision
     * que D-03a y D-03b tienen abierta, y el escaner de fuentes lo pone rojo con razon.
     *
     * <p>Lo que se gana ademas: la auditoria anota lo que <b>quedo escrito</b> y no lo que se
     * calculo, que son dos cifras distintas y solo una esta en la tabla.
     *
     * @return la longitud guardada, o vacio cuando ya habia un frente a esa via: volver a derivar
     *     no pisa lo que hay, y desde luego no pisa una longitud confirmada
     */
    Optional<Medida> proponer(FrentePropuesto propuesto, Observacion observacion);

    /** Deja constancia de que el derivador paso por ese predio, y de con que resultado. */
    void anotarDerivacion(DerivacionDeFrentes derivacion);

    /**
     * Confirma la longitud de un frente: el acto que la vuelve oficial (regla 10, ADR-0021).
     *
     * @param longitud la que se afirma, que <b>no</b> tiene por que ser la propuesta: lo normal es
     *     que alguien haya ido con la cinta
     * @throws FrenteInexistente si ese frente no esta en el padron de esta municipalidad
     */
    FrenteDelPredio confirmar(
            long frenteId, Medida longitud, Observacion observacion, Instant cuando);

    /** No hay tal frente en esta municipalidad. */
    final class FrenteInexistente extends RuntimeException {

        @java.io.Serial private static final long serialVersionUID = 1L;

        public FrenteInexistente(long frenteId) {
            super(
                    "El frente "
                            + frenteId
                            + " no esta en el padron de esta municipalidad: no se puede confirmar"
                            + " una longitud que no existe");
        }
    }
}
