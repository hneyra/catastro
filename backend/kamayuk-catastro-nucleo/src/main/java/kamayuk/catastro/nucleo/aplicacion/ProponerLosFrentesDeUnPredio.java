package kamayuk.catastro.nucleo.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.nucleo.dominio.DerivacionDeFrentes;
import kamayuk.catastro.nucleo.dominio.FrentePropuesto;
import kamayuk.catastro.nucleo.dominio.FrentesDelPredio;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corta UN lote contra los ejes de las vias que lo bordean y propone sus frentes (#7).
 *
 * <h2>Por que es una clase aparte y no un metodo del que recorre el padron</h2>
 *
 * <p>Por los dos motivos que {@code PublicarUnHecho} ya documenta y que este proyecto ha medido
 * cuatro veces:
 *
 * <ol>
 *   <li><b>Una transaccion por predio, no una por corrida.</b> El lote cuyo poligono es invalido
 *       —autointersecado, que es lo que sale de un levantamiento apresurado— hace fallar el corte,
 *       y con el bucle envuelto en una sola transaccion esa fila marca todo como
 *       <i>rollback-only</i> y se lleva por delante las propuestas de los catorce mil predios que
 *       ya iban bien.
 *   <li><b>La anotacion no se aplica por auto-invocacion.</b> Con este metodo en la misma clase que
 *       el bucle, {@code @Transactional} no lo interceptaria y la separacion seria una promesa del
 *       javadoc: es lo que #430 midio con {@code ImportarCajas}.
 * </ol>
 *
 * <h2>Lo que hace es PROPONER, y el nombre lo dice a proposito</h2>
 *
 * <p>La longitud que sale del corte nace {@code PROPUESTA} y nadie puede determinar un arbitrio
 * sobre ella sin saber que lo hace (ADR-0021, {@code EstadoDeLaLongitud}). Este metodo no confirma
 * nada, no pisa un frente que ya estuviera y desde luego no toca uno confirmado.
 */
@Service
public class ProponerLosFrentesDeUnPredio {

    private final FrentesDelPredio frentes;
    private final Auditoria auditoria;
    private final Clock reloj;

    public ProponerLosFrentesDeUnPredio(
            FrentesDelPredio frentes, Auditoria auditoria, Clock reloj) {
        this.frentes = frentes;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Propone los frentes de un predio y deja constancia de la corrida.
     *
     * <p><b>Anota la derivacion pase lo que pase</b>, tambien cuando no sale ninguna: es la unica
     * forma de que «este predio no da a ninguna calle» y «a este predio no le ha pasado el
     * derivador» dejen de ser la misma lista vacia. Ver {@link DerivacionDeFrentes}.
     *
     * @return cuantas propuestas se escribieron; cero si ya estaban o si no salio ninguna
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int proponer(long predioId, Medida tolerancia, Observacion observacion) {
        Instant ahora = reloj.instant();
        List<FrentePropuesto> propuestos = frentes.cortarContraLasVias(predioId, tolerancia);

        int escritos = 0;
        for (FrentePropuesto propuesto : propuestos) {
            Optional<Medida> guardada = frentes.proponer(propuesto, observacion);
            if (guardada.isPresent()) {
                escritos++;
                auditoria.registrar(
                        RegistroDeAuditoria.enLaFechaDe(
                                        LocalDate.now(reloj),
                                        "frente_predio",
                                        predioId + ":" + propuesto.viaId(),
                                        Operacion.ALTA,
                                        observacion)
                                .con(null, descripcion(propuesto.viaId(), guardada.get())));
            }
        }

        frentes.anotarDerivacion(
                new DerivacionDeFrentes(
                        predioId, ahora, propuestos.size(), motivoDeQueNoSalgaNinguno(propuestos)));
        return escritos;
    }

    /**
     * Por que la corrida no propuso nada, o {@code null} si propuso algo.
     *
     * <p>Es una sola frase y dice las tres causas, porque distinguirlas exigiria tres consultas mas
     * por predio —¿tiene poligono?, ¿hay via con eje cerca?, ¿el corte dio longitud?— sobre catorce
     * mil predios. Lo que no se hace es callarlo: un cero sin motivo se lee como «no da a ninguna
     * calle», que de un predio urbano es falso.
     */
    private static @Nullable String motivoDeQueNoSalgaNinguno(List<FrentePropuesto> propuestos) {
        if (!propuestos.isEmpty()) {
            return null;
        }
        return "El corte no dio ningun tramo: o el lote no tiene poligono, o ninguna via cercana"
                + " tiene eje levantado, o el borde no llega a la franja de la calzada";
    }

    /**
     * El antes y el despues de la auditoria: la propuesta, con su unidad y su estado.
     *
     * <p>Anota la longitud <b>tal como quedo guardada</b> y no la que salio del corte: con cuantos
     * decimales se guarda lo decide la columna, no este codigo, y una auditoria que dijera otra
     * cifra que la de la tabla no serviria para explicar la tabla.
     */
    private static String descripcion(long viaId, Medida guardada) {
        return "Frente PROPUESTO a la via "
                + viaId
                + ": "
                + guardada
                + ". Derivado del corte contra el eje de calzada; no confirmado (ADR-0021)";
    }
}
