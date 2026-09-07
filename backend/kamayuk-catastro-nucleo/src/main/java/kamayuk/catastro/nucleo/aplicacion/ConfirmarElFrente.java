package kamayuk.catastro.nucleo.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.DatosDeAuditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
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
 *
 * <h2>Confirmar UNA vez, y solo sobre una PROPUESTA (#26, AC-4)</h2>
 *
 * <p>Lo que decide es el {@code WHERE longitud_estado = 'PROPUESTA'} del {@code UPDATE} —el motor,
 * no un {@code if} de aqui—: dos confirmaciones simultaneas leerian las dos «esta propuesta» y las
 * dos escribirian. La segunda recibe {@link FrentesDelPredio.LongitudYaConfirmada}, que es un
 * problema de negocio con su nombre y no un exito silencioso.
 *
 * <p><b>Rectificar una longitud ya confirmada es OTRO acto, y hoy no existe.</b> Se decide y se
 * escribe: una rectificacion tiene que llevar su propio motivo —por que la cifra que alguien firmo
 * estaba mal— y dejar la anterior recuperable, y eso no es «confirmar otra vez». Mientras ese acto
 * no exista, la unica salida es la que hay: la cifra confirmada se queda, y quien crea que esta mal
 * abre el trabajo que la corrige. Admitir la segunda confirmacion en su lugar cambiaria la base de
 * los arbitrios de ese predio sin dejar rastro del valor anterior, que es el defecto que #26 mide.
 *
 * <h2>La bitacora dice el «antes» DE VERDAD (#26, AC-5)</h2>
 *
 * <p>Hasta #26 el «antes» era una constante —«la longitud era una propuesta»— sin ninguna cifra
 * dentro, asi que la longitud anterior no quedaba en ninguna parte: ni en la tabla, que la pisa, ni
 * en la bitacora, que no la nombraba. Ahora se lee el frente antes de tocarlo y se asienta lo que
 * habia. Es una consulta mas por confirmacion, y es lo que cuesta poder contestar «cuanto decia
 * antes» dentro de dos anios.
 *
 * <p>La longitud entra <b>entera</b>, como {@link Medida}, en el antes y en el despues: la unidad
 * va dentro porque ahi la unidad ES parte del dato, y quien la escribe es el serializador de #20
 * ({@code ObjetosDeValorEnJson}). Partirla en magnitud y unidad dejaria el antes y el despues con
 * formas distintas, y la bitacora se lee comparando uno con otro.
 */
@Service
public class ConfirmarElFrente {

    /**
     * De donde salio la cifra que se esta sustituyendo.
     *
     * <p>Es una constante y no un texto libre porque lo unico que puede haber antes de una
     * confirmacion es lo que dejo el derivador —desde #26 lo garantiza el {@code WHERE
     * longitud_estado = 'PROPUESTA'}, y no una costumbre—. La cifra, en cambio, <b>se lee</b>: era
     * lo que faltaba.
     */
    private static final String ORIGEN_DE_LA_PROPUESTA = "CORTE_CONTRA_EL_EJE_DE_CALZADA";

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
     * @throws FrentesDelPredio.LongitudYaConfirmada si su longitud ya la firmo alguien
     */
    @Transactional
    public FrenteDelPredio confirmar(long frenteId, Medida longitud, Observacion observacion) {
        // Se lee ANTES de tocar la fila, y no despues: `confirmar` devuelve el frente YA
        // confirmado, asi que despues del UPDATE la cifra anterior no esta en ninguna parte.
        FrenteDelPredio antes =
                frentes.unFrente(frenteId)
                        .orElseThrow(() -> new FrentesDelPredio.FrenteInexistente(frenteId));

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
                                        .mas("estado", antes.estado())
                                        .mas("longitud", antes.longitud())
                                        .mas("origen", ORIGEN_DE_LA_PROPUESTA)
                                        .datos(),
                                DatosDeAuditoria.campos()
                                        .mas("estado", confirmado.estado())
                                        .mas("confirmadoPor", confirmado.confirmadoPor())
                                        .mas("longitud", confirmado.longitud())
                                        .datos()));

        return confirmado;
    }
}
