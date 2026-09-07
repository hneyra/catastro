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
 * Recorre el padron proponiendo los frentes de cada predio (#7, AC 1).
 *
 * <h2>Este objeto NO abre transaccion, y ahi esta la decision</h2>
 *
 * <p>La abre {@link ProponerLosFrentesDeUnPredio}, una por predio. Envolver el recorrido en una
 * sola es el defecto que #328, #54, #430 y #247 §2 midieron cuatro veces: el poligono invalido de
 * un lote marca la transaccion como <i>rollback-only</i> y se lleva por delante el informe y todo
 * lo que ya iba bien. Es el mismo reparto que {@code PublicacionDelPadron} y {@code
 * PublicarUnHecho}.
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
     * Deriva los frentes de hasta {@code tope} predios, empezando por el primero.
     *
     * @param tolerancia a cuantos metros del eje se considera que el lote da a esa via
     * @param tope cuantos predios como mucho
     * @param observacion por que se corre (regla 10)
     */
    public Informe derivar(Medida tolerancia, int tope, Observacion observacion) {
        Objects.requireNonNull(tolerancia, "La derivacion necesita su tolerancia");
        Objects.requireNonNull(observacion, "Toda escritura lleva su observacion (regla 10)");
        if (tope < 1) {
            throw new IllegalArgumentException(
                    "Una corrida que no recorre ningun predio no es una corrida: tope " + tope);
        }

        String avisoDeLatitud = avisoDeLatitud();

        List<Long> predios = frentes.prediosPorDerivar(0L, tope);
        int propuestos = 0;
        int conFrentes = 0;
        for (long predioId : predios) {
            int escritos = proponer.proponer(predioId, tolerancia, observacion);
            propuestos += escritos;
            if (escritos > 0) {
                conFrentes++;
            }
        }
        return new Informe(predios.size(), conFrentes, propuestos, avisoDeLatitud);
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
     * @param prediosRecorridos cuantos predios se miraron
     * @param prediosConFrenteNuevo en cuantos se escribio al menos una propuesta
     * @param frentesPropuestos cuantas propuestas se escribieron
     * @param avisoDeLatitud por que el corte propone de menos en este padron, o {@code null} si el
     *     margen del marco vale aqui (#29). Viaja en el informe y no en un registro suelto para que
     *     quien invoque la corrida —hoy un {@code ApplicationRunner}, manana lo que sea— tenga que
     *     decidir que hace con el
     */
    public record Informe(
            int prediosRecorridos,
            int prediosConFrenteNuevo,
            int frentesPropuestos,
            @Nullable String avisoDeLatitud) {}
}
