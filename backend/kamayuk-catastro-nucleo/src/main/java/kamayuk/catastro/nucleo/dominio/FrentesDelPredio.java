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
     * El marco de lo que este padron tiene levantado, para saber si el corte vale aqui (#29).
     *
     * <p>Se pregunta <b>una vez por corrida</b> y no una por predio: lo que decide es la banda de
     * latitud en la que {@link MargenDelMarco#METROS_POR_GRADO} vale, y esa es una propiedad del
     * padron entero. Fuera de ella el marco ensanchado se queda corto y el corte descarta vias que
     * si bordean el lote, <b>sin ningun sintoma</b>.
     *
     * <p>Devuelve {@link MarcoDeLoLevantado} y no cuatro numeros porque las dos ausencias se
     * contestan igual y se arreglan distinto: «no hay ni un poligono cargado» —el estado de hoy en
     * toda instalacion— y «lo levantado no encuadra».
     */
    MarcoDeLoLevantado marcoDelPadron();

    /**
     * Los predios que el derivador tiene que recorrer, en orden de identificador.
     *
     * <p><b>Solo los ACTIVOS</b> (#26, AC-3): un predio dado de baja ya no esta en el padron y no
     * hay nada que explicar sobre el. Los que <b>no tienen poligono si se recorren</b>, y no es un
     * descuido: ese predio tiene algo que explicar y lo explica su fila de {@link
     * DerivacionDeFrentes} —filtrandolo, «no hay cartografia» se convertiria en «nunca se ha
     * derivado»—. El motivo entero, con su medida, en el adaptador JDBC.
     *
     * @param desde el ultimo identificador ya recorrido, para poder seguir donde se dejo. Se
     *     <b>respeta</b>: un adaptador que lo ignore deja el recorrido sin poder avanzar, y {@code
     *     DerivacionDeLosFrentes} lo dice en vez de quedarse en un bucle
     * @param tamanoDelLote cuantos como mucho. Es el tamano de la pagina, no un techo de la corrida
     */
    List<Long> prediosPorDerivar(long desde, int tamanoDelLote);

    /**
     * Cuantos predios va a recorrer el derivador: el denominador del informe (#26, AC-2).
     *
     * <p>Cuenta <b>exactamente el mismo conjunto</b> que pagina {@link #prediosPorDerivar}, y el
     * adaptador lo escribe con el mismo fragmento de SQL a proposito: con dos predicados escritos
     * por separado, el denominador podria decir una cosa y el recorrido otra, y entonces «se agoto
     * el padron» dejaria de significar nada.
     */
    int cuantosPrediosPorDerivar();

    /**
     * Un frente por su identificador, o vacio si no esta en el padron de esta municipalidad.
     *
     * <p>Existe porque confirmar necesita saber <b>que habia antes</b> (#26, AC-5): {@link
     * #confirmar} devuelve el frente YA confirmado, asi que despues del acto la longitud anterior
     * no esta en ninguna parte —la tabla la pisa y la bitacora no la nombraba—.
     */
    Optional<FrenteDelPredio> unFrente(long frenteId);

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
     * <p><b>Solo alcanza a un frente PROPUESTA</b> (#26, AC-4), y lo decide el {@code WHERE} del
     * {@code UPDATE} y no un {@code if} del llamador: dos confirmaciones simultaneas leerian las
     * dos «esta propuesta» y las dos escribirian. Rectificar una longitud ya confirmada es OTRO
     * acto —con su motivo y con la anterior recuperable— y hoy no existe.
     *
     * @param longitud la que se afirma, que <b>no</b> tiene por que ser la propuesta: lo normal es
     *     que alguien haya ido con la cinta
     * @throws FrenteInexistente si ese frente no esta en el padron de esta municipalidad
     * @throws LongitudYaConfirmada si su longitud ya la firmo alguien
     */
    FrenteDelPredio confirmar(
            long frenteId, Medida longitud, Observacion observacion, Instant cuando);

    /**
     * Ese frente ya tiene su longitud confirmada, y una segunda confirmacion la pisaria (#26).
     *
     * <p>De esta cifra cuelga un cobro, y un metro es indistinguible de otro al leerlo: sustituir
     * en silencio la que alguien firmo cambia la base de los arbitrios de ese predio sin que nadie
     * lo decida y sin dejar rastro del valor anterior. Lleva dentro <b>lo que hay</b> —cuanto y
     * quien— para que quien reciba el rechazo no tenga que ir a buscarlo.
     */
    final class LongitudYaConfirmada extends RuntimeException {

        @java.io.Serial private static final long serialVersionUID = 1L;

        public LongitudYaConfirmada(long frenteId, Medida longitud, String confirmadoPor) {
            super(
                    "El frente "
                            + frenteId
                            + " ya tiene su longitud CONFIRMADA en "
                            + longitud
                            + ", y la firmo "
                            + confirmadoPor
                            + ": confirmarla otra vez la pisaria sin dejar rastro de la anterior."
                            + " Rectificar una longitud confirmada es otro acto —con su motivo—, y"
                            + " hoy no existe");
        }
    }

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
