package kamayuk.catastro.nucleo.aplicacion;

import java.util.List;
import java.util.Objects;
import kamayuk.catastro.compartido.MarcoGeografico;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.nucleo.dominio.FrentesDelPredio;
import kamayuk.catastro.nucleo.dominio.MarcoDeLoLevantado;
import kamayuk.catastro.nucleo.dominio.MargenDelMarco;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Recorre el padron proponiendo los frentes de cada predio (#7, AC 1; #26, AC-1 y AC-2).
 *
 * <h2>Este objeto NO abre transaccion, y ahi esta la decision</h2>
 *
 * <p>La abre {@link ProponerLosFrentesDeUnPredio}, una por predio. Envolver el recorrido en una
 * sola es el defecto que #328, #54, #430 y #247 §2 midieron cuatro veces: el poligono invalido de
 * un lote marca la transaccion como <i>rollback-only</i> y se lleva por delante el informe y todo
 * lo que ya iba bien. Es el mismo reparto que {@code PublicacionDelPadron} y {@code
 * PublicarUnHecho}.
 *
 * <h2>El recorrido va POR LOTES, y el cursor avanza (#26, AC-1)</h2>
 *
 * <p>Hasta #26 esto pedia <b>un solo lote empezando siempre de cero</b> —{@code
 * prediosPorDerivar(0L, tope)}, sin bucle— y el {@code desde} que el puerto declara estaba muerto.
 * Con un padron de 40 000 predios y un tope de 500, los 39 500 restantes no se derivaban NUNCA, y
 * cada corrida volvia a recorrer los mismos 500 sin efecto —{@code proponer} es idempotente por
 * {@code ON CONFLICT DO NOTHING}—. El informe decia «500 predio(s) recorrido(s)» y no decia de
 * cuantos, asi que la corrida parecia completa: <b>el sintoma era la ausencia de sintoma</b>.
 *
 * <p>Por eso {@code tamanoDelLote} se llama asi y no {@code tope}: es cuantos predios se piden por
 * consulta, no cuantos recorre la corrida. La corrida recorre <b>el padron entero</b>, y no hay
 * techo — si algun dia hiciera falta uno, es otro parametro y se llama de otra manera.
 *
 * <h2>Lo que produce son PROPUESTAS</h2>
 *
 * <p>Ni una de las longitudes que escribe se puede cobrar: nacen {@code PROPUESTA} y confirmarlas
 * es un acto de una persona con su observacion (ADR-0021, {@code ConfirmarElFrente}). Este proceso
 * no determina nada y no nombra ningun servicio de arbitrio: el importe es de {@code rentas}
 * (ADR-0024).
 */
@Service
public class DerivacionDeLosFrentes {

    private final FrentesDelPredio frentes;
    private final ProponerLosFrentesDeUnPredio proponer;

    public DerivacionDeLosFrentes(FrentesDelPredio frentes, ProponerLosFrentesDeUnPredio proponer) {
        this.frentes = frentes;
        this.proponer = proponer;
    }

    /**
     * Deriva los frentes de <b>todo</b> el padron, pidiendolo por lotes.
     *
     * @param tolerancia a cuantos metros del eje se considera que el lote da a esa via
     * @param tamanoDelLote cuantos predios se piden por consulta. No es un techo de la corrida
     * @param observacion por que se corre (regla 10)
     */
    public Informe derivar(Medida tolerancia, int tamanoDelLote, Observacion observacion) {
        Objects.requireNonNull(tolerancia, "La derivacion necesita su tolerancia");
        Objects.requireNonNull(observacion, "Toda escritura lleva su observacion (regla 10)");
        if (tamanoDelLote < 1) {
            throw new IllegalArgumentException(
                    "Una corrida que no recorre ningun predio no es una corrida: tamano del lote "
                            + tamanoDelLote);
        }

        String avisoDeLatitud = avisoDeLatitud();

        // El censo se toma ANTES y de la misma consulta que pagina —`FrentesDelPredio` las declara
        // juntas y el adaptador las escribe con el mismo fragmento de SQL—, para que el
        // denominador no pueda decir una cosa y el recorrido otra. Es una foto del arranque: un
        // predio dado de alta a mitad de corrida con identificador mayor que el cursor SI se
        // recorre, y por eso `agotoElPadron()` compara con `>=` y no con `==`.
        int enElPadron = frentes.cuantosPrediosPorDerivar();

        long desde = 0L;
        int recorridos = 0;
        int lotes = 0;
        int conFrentes = 0;
        int propuestos = 0;
        while (true) {
            List<Long> lote = frentes.prediosPorDerivar(desde, tamanoDelLote);
            if (lote.isEmpty()) {
                break;
            }
            lotes++;
            long ultimo = desde;
            for (long predioId : lote) {
                int escritos = proponer.proponer(predioId, tolerancia, observacion);
                propuestos += escritos;
                if (escritos > 0) {
                    conFrentes++;
                }
                ultimo = Math.max(ultimo, predioId);
            }
            recorridos += lote.size();
            if (ultimo <= desde) {
                // El cursor no avanzo: el adaptador esta devolviendo el mismo lote. Sin esto la
                // corrida se queda en un bucle infinito proponiendo lo mismo, que es peor que
                // pararse — un proceso que no termina no deja informe y nadie sabe por que.
                throw new IllegalStateException(
                        "El recorrido pidio los predios posteriores al "
                                + desde
                                + " y el mayor que volvio es el "
                                + ultimo
                                + ": el cursor no avanza y la corrida no puede terminar. Lo que"
                                + " esta mal es `prediosPorDerivar`, que tiene que respetar su"
                                + " `desde`");
            }
            desde = ultimo;
            if (lote.size() < tamanoDelLote) {
                // Un lote corto solo lo devuelve el `LIMIT` cuando ya no queda nada detras.
                break;
            }
        }
        return new Informe(enElPadron, recorridos, lotes, conFrentes, propuestos, avisoDeLatitud);
    }

    /**
     * Si el margen del corte vale en la latitud de este padron, y si no, por que no (#29).
     *
     * <p>Se pregunta <b>antes</b> del bucle y una sola vez: es una propiedad del padron entero, y
     * preguntarla por predio serian catorce mil agregados para contestar lo mismo.
     *
     * <p><b>Avisa, no se niega.</b> Fuera de la banda lo que el corte encuentra sigue siendo
     * correcto —el {@code ST_DWithin} metrico de detras no cambia—; lo que falta son las vias que
     * el marco descarto antes de que aquel las viera. Negarse dejaria a esa municipalidad sin ni un
     * frente en vez de con los que si salen; callarse dejaria un predio de esquina con un frente en
     * vez de dos, y eso no se distingue de un predio que no da a la calle.
     *
     * <p>Sin cartografia no hay latitud que mirar, y eso <b>no</b> es un aviso: es el estado de hoy
     * en toda instalacion, y ya lo dice el informe con sus ceros y cada predio con su motivo.
     */
    private @Nullable String avisoDeLatitud() {
        MarcoDeLoLevantado levantado = frentes.marcoDelPadron();
        MarcoGeografico marco = levantado.marco();
        return marco == null ? null : MargenDelMarco.avisoSiNoCubre(marco);
    }

    /**
     * Lo que la corrida hizo.
     *
     * <p><b>Con denominador (#26, AC-2).</b> Un recuento a secas —«500 predio(s) recorrido(s)»— es
     * lo que hacia que una corrida que solo miraba el primer lote pareciera completa.
     *
     * @param prediosEnElPadron cuantos predios iba a recorrer la corrida al arrancar
     * @param prediosRecorridos cuantos se miraron
     * @param lotes cuantas consultas de pagina hicieron falta
     * @param prediosConFrenteNuevo en cuantos se escribio al menos una propuesta
     * @param frentesPropuestos cuantas propuestas se escribieron
     * @param avisoDeLatitud por que el corte propone de menos en este padron, o {@code null} si el
     *     margen del marco vale aqui (#29). Viaja en el informe y no en un registro suelto para que
     *     quien invoque la corrida —hoy un {@code ApplicationRunner}, manana lo que sea— tenga que
     *     decidir que hace con el
     */
    public record Informe(
            int prediosEnElPadron,
            int prediosRecorridos,
            int lotes,
            int prediosConFrenteNuevo,
            int frentesPropuestos,
            @Nullable String avisoDeLatitud) {
        /**
         * Si la corrida llego al final del padron.
         *
         * <p>Puede ser falso, y por eso se afirma: un recorrido que se parara en el primer lote lo
         * dejaria en falso, que es exactamente el defecto de #26 (a).
         */
        public boolean agotoElPadron() {
            return prediosRecorridos >= prediosEnElPadron;
        }
    }
}
