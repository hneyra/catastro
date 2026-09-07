package kamayuk.catastro.fiscalizacion.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.DatosDeAuditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deja sin efecto un hallazgo firme (#23 AC-1 y AC-2).
 *
 * <h2>La salida que faltaba</h2>
 *
 * <p>{@code fiscalizacion} entrego el camino de ida entero —campania, candidato, las dos
 * compuertas, hallazgo, evidencia y acta— y ninguna de sus salidas. {@code
 * Hallazgo.dejadoSinEfecto()} existia y <b>no lo llamaba nadie</b>: ni un caso de uso, ni un
 * endpoint, y {@code EstadoDelHallazgo.DEJADO_SIN_EFECTO} era inalcanzable. Lo caro no es el hueco
 * sino lo que dejaba en pie: un acta equivocada llega al administrado en papel, y la unica salida
 * operativa era no tocar nada.
 *
 * <h2>Es un ACTO, no un borrado ni un `UPDATE` a secas</h2>
 *
 * <p>Regla 4: aqui no se borra. La fila del hallazgo se queda, su acta se queda donde esta —{@code
 * V9} le revoca el {@code UPDATE} a {@code acta}, asi que es inmutable de verdad y no por
 * costumbre— y lo unico que cambia es que ese hallazgo deja de habilitar ningun acto nuevo. Con
 * <b>su motivo, su nombre y su fecha</b>, como {@code candidato.descarte} e {@code itse}: sin las
 * tres, lo unico que quedaria es la {@code observacion} de la fila, que este mismo acto
 * sobrescribe.
 *
 * <h2>Lo que NO hace</h2>
 *
 * <p>No corrige la ficha, no reescribe las dos areas y no toca el acta. Sigue siendo ADR-0035: un
 * hallazgo se <b>informa</b>. Y el candidato del que salio <b>no vuelve a la cola</b>: {@code
 * EstadoDelCandidato.VERIFICADO_EN_CAMPO} es terminal a proposito, y por que se lee en {@code
 * FiscalizacionRepository.guardar(Acta)}.
 */
@Service
public class DejarSinEfectoElHallazgo {

    private final FiscalizacionRepository repositorio;
    private final Auditoria auditoria;
    private final Clock reloj;

    public DejarSinEfectoElHallazgo(
            FiscalizacionRepository repositorio, Auditoria auditoria, Clock reloj) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Lo deja sin efecto.
     *
     * <p>El <b>motivo</b> y la <b>observacion</b> son dos cosas y se piden las dos, por lo mismo
     * que en un descarte de candidato: el motivo dice por que ese hallazgo ya no vale —y viaja con
     * la fila, y se publica al consumidor— y la observacion dice por que se hizo esta escritura
     * (regla 10). Un solo texto para las dos cosas obligaria a elegir cual de las dos preguntas se
     * contesta.
     *
     * @throws RegistrarEvidencia.HallazgoInexistente si no hay tal hallazgo en esta municipalidad
     * @throws RegistrarEvidencia.HallazgoSinEfecto si ya estaba dejado sin efecto
     */
    @Transactional
    public Hallazgo dejarSinEfecto(long hallazgoId, String motivo, Observacion observacion) {
        Hallazgo anterior =
                repositorio
                        .hallazgoPorId(hallazgoId)
                        .orElseThrow(() -> new RegistrarEvidencia.HallazgoInexistente(hallazgoId));
        if (!anterior.estaFirme()) {
            throw new RegistrarEvidencia.HallazgoSinEfecto(hallazgoId);
        }

        Hallazgo anulado =
                repositorio.guardar(
                        anterior.dejadoSinEfecto(
                                motivo, OrigenContext.actual().usuario(), reloj.instant()),
                        observacion);

        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj),
                                "hallazgo",
                                String.valueOf(hallazgoId),
                                Operacion.MODIFICACION,
                                observacion)
                        .con(descripcion(anterior), descripcion(anulado)));
        return anulado;
    }

    /**
     * El antes y el despues DE VERDAD, y no dos veces lo mismo.
     *
     * <p>Lo compone el serializador (#20). Aqui vivia el tercero de los {@code escapar()} a mano
     * que #20 borro: cubria {@code \\} y {@code "} y no los caracteres de control, asi que un
     * motivo de anulacion con un salto de linea reventaba el {@code cast(... AS jsonb)} igual que
     * los otros dos.
     */
    private static DatosDeAuditoria descripcion(Hallazgo hallazgo) {
        Hallazgo.Anulacion anulacion = hallazgo.anulacion();
        return DatosDeAuditoria.campos()
                .mas("estado", hallazgo.estado())
                .mas("motivoAnulacion", anulacion == null ? null : anulacion.motivo())
                .mas("anuladoPor", anulacion == null ? null : anulacion.quien())
                .mas("anuladoEn", anulacion == null ? null : anulacion.cuando())
                .datos();
    }
}
