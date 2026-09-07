package kamayuk.catastro.nucleo.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.DatosDeAuditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.nucleo.dominio.EstadoDeLaLongitud;
import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import kamayuk.catastro.nucleo.dominio.FrentesDelPredio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirma la longitud de un frente: el acto que la vuelve oficial (#7, AC 2, ADR-0021).
 *
 * <h2>Por que hay un acto y no un simple {@code UPDATE}</h2>
 *
 * <p>Porque de esta cifra cuelga un cobro. El derivador propone cortando el lote contra el eje de
 * la calzada, y esa propuesta sirve para saber a que calle da el predio y por donde ir a medir —no
 * para determinar un arbitrio—. Lo que la vuelve oficial es que <b>alguien la afirme</b>, y por eso
 * queda su nombre, su hora y su observacion (regla 10).
 *
 * <p>Es exactamente lo que ADR-0021 decide sobre el area del terreno: derivarla y darla por buena
 * cambiaria la base de todo el padron sin que nadie lo decidiera, y un metro es indistinguible de
 * otro al leerlo.
 *
 * <h2>Confirmar admite OTRA longitud, y no es un descuido</h2>
 *
 * <p>Lo normal es que quien confirma haya ido con la cinta. Un acto que solo pudiera decir «sí a lo
 * que salio» seria un boton de aceptar, y entonces la distincion entre propuesta y medida no
 * significaria nada. La anterior no se pierde: queda en la auditoria, con el antes y el despues.
 */
@Service
public class ConfirmarElFrente {

    private final FrentesDelPredio frentes;
    private final Auditoria auditoria;
    private final Clock reloj;

    public ConfirmarElFrente(FrentesDelPredio frentes, Auditoria auditoria, Clock reloj) {
        this.frentes = frentes;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Confirma la longitud de un frente.
     *
     * @throws FrentesDelPredio.FrenteInexistente si el frente no esta en esta municipalidad
     */
    @Transactional
    public FrenteDelPredio confirmar(long frenteId, Medida longitud, Observacion observacion) {
        FrenteDelPredio confirmado =
                frentes.confirmar(frenteId, longitud, observacion, reloj.instant());

        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj),
                                "frente_predio",
                                String.valueOf(frenteId),
                                Operacion.MODIFICACION,
                                observacion)
                        .con(
                                DatosDeAuditoria.campos()
                                        .mas("estado", EstadoDeLaLongitud.PROPUESTA)
                                        .mas("origen", "CORTE_CONTRA_EL_EJE_DE_CALZADA")
                                        .datos(),
                                DatosDeAuditoria.campos()
                                        .mas("estado", confirmado.estado())
                                        .mas("confirmadoPor", confirmado.confirmadoPor())
                                        .mas("longitud", confirmado.longitud())
                                        .datos()));

        return confirmado;
    }
}
