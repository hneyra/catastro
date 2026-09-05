package kamayuk.catastro.catastro.infraestructura.web;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.autorizacion.RequiereAcceso;
import kamayuk.catastro.catastro.aplicacion.EntregaDeEventos;
import kamayuk.catastro.catastro.dominio.EventoDeCatastro;
import kamayuk.catastro.web.Api;
import kamayuk.catastro.web.CodigoDeError;
import kamayuk.catastro.web.ProblemaDeNegocio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El buzon de salida, servido para que el ingestor de {@code rentas} lo consuma (C-8).
 *
 * <h2>Por que aqui se LEE y no se EMPUJA, al reves que en `caja`</h2>
 *
 * <p>`caja` publica sus pagos <b>empujandolos</b> a un endpoint de `rentas`, y esto los sirve para
 * que `rentas` <b>venga a buscarlos</b>. La diferencia no es de gusto: la decide un privilegio.
 *
 * <ul>
 *   <li>El receptor de un pago escribe {@code pago_recibido}, y `V8` de `rentas` le da a {@code
 *       sgtm_app} {@code INSERT, SELECT, UPDATE} sobre ella. El proceso que atiende HTTP
 *       <b>puede</b> recibir, asi que empujar funciona.
 *   <li>El receptor de estos eventos escribe {@code predio_ref}, {@code ficha_ref}, {@code
 *       valuacion_predio} y {@code valuacion_corrida}, y `V4` y `V5` le dan a {@code sgtm_app}
 *       <b>solo {@code SELECT}</b>. Quien las escribe es {@code rol_ingestor_catastro}, que no
 *       atiende peticiones. Un endpoint que recibiera empujones tendria que llevar esa credencial
 *       dentro del proceso web — y entonces «la proyeccion es de solo lectura para la aplicacion»
 *       dejaria de ser un privilegio y volveria a ser disciplina, que es exactamente lo que `V4`
 *       dice que no quiere ser.
 * </ul>
 *
 * <h2>Y por que hay acuse, en vez de que el consumidor lleve un cursor</h2>
 *
 * <p>Porque un cursor <b>pierde eventos en silencio</b>: {@code catastro_evento.id} se asigna al
 * {@code INSERT} y no al {@code COMMIT}, asi que una transaccion que tomo el 100 y confirma despues
 * de otra que tomo el 101 queda por detras de un cursor que ya paso por 101. La fila esta, el
 * consumidor no la vera nunca, y nada lo dice. Con un estado no hay posicion que adelantar.
 *
 * <p>El acuse llega <b>despues</b> de que el consumidor haya confirmado su transaccion, asi que un
 * acuse perdido reentrega: la entrega es <b>al menos una vez</b> y quien deduplica es el receptor,
 * por {@code evento_id}. Es lo mismo que `caja` dice de su {@code pagoId}.
 */
@RestController
@RequestMapping(Api.RAIZ + "/catastro/eventos")
public class EventosController {

    /**
     * El acceso con el que se lee y se acusa el buzon.
     *
     * <p>Es {@code consulta_fichas}, la opcion con la que se lee el padron, y no uno propio: lo que
     * estos eventos llevan <b>es</b> el padron, y darle un permiso propio crearia una opcion de
     * menu que nadie abre y que nadie administra — el mismo razonamiento con que {@code
     * PagoController} de `rentas` usa {@code caja_tributaria}.
     *
     * <p><b>Y arrastra un hueco que hay que decir</b>: quien pueda acusar puede marcar entregado lo
     * que no consumio, y esos hechos no se vuelven a servir. Lo que falta para cerrarlo no es otro
     * permiso sino una <b>identidad de servicio</b> —ADR-0028 §2, RFC 8693—, que ninguno de los
     * cuatro repositorios tiene todavia.
     */
    private static final String ACCESO = "consulta_fichas";

    /** Cuantos se sirven por peticion como maximo. Una vuelta tiene que acabar. */
    private static final int TOPE = 500;

    private final EntregaDeEventos entrega;
    private final Clock reloj;

    public EventosController(EntregaDeEventos entrega, Clock reloj) {
        this.entrega = entrega;
        this.reloj = reloj;
    }

    /** Lo pendiente de entregar, en el orden en que se emitio. */
    @GetMapping
    @RequiereAcceso(acceso = ACCESO, privilegio = Privilegio.LECTURA)
    public LoteDeEventosResource pendientes(@RequestParam(defaultValue = "200") int limite) {
        if (limite < 1 || limite > TOPE) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "El limite va de 1 a " + TOPE + ", y llego " + limite);
        }
        List<EventoDeCatastro> pendientes = entrega.pendientes(limite);
        List<EventoResource> eventos = new ArrayList<>();
        for (EventoDeCatastro evento : pendientes) {
            eventos.add(EventoResource.de(evento));
        }
        return new LoteDeEventosResource(
                List.copyOf(eventos), entrega.pendientesQueQuedan(), reloj.instant().toString());
    }

    /**
     * Acusa los eventos que el consumidor ya aplico y confirmo.
     *
     * <p>Devuelve <b>cuantos se marcaron</b>, que puede ser menos de los que llegaron: acusar dos
     * veces el mismo evento es lo que pasa cada vez que un acuse se pierde despues de que el
     * receptor confirmara, y no es un error.
     */
    @PostMapping("/acuse")
    @RequiereAcceso(acceso = ACCESO, privilegio = Privilegio.REGISTRO)
    public AcuseResource acusar(@RequestBody PeticionDeAcuse peticion) {
        List<String> ids = peticion.eventoIds();
        if (ids == null || ids.isEmpty()) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "Un acuse sin ningun evento no dice nada: o se acusa algo, o no se llama");
        }
        if (ids.size() > TOPE) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "Un acuse de mas de " + TOPE + " eventos: llegaron " + ids.size());
        }
        List<UUID> eventos = new ArrayList<>();
        for (String id : ids) {
            try {
                eventos.add(UUID.fromString(id.strip()));
            } catch (IllegalArgumentException noEsUuid) {
                throw new ProblemaDeNegocio(
                        CodigoDeError.VALIDACION, "«" + id + "» no es un identificador de evento");
            }
        }
        int marcados = entrega.marcarEntregados(List.copyOf(eventos), reloj.instant());
        return new AcuseResource(ids.size(), marcados, entrega.pendientesQueQuedan());
    }

    /** Lo que el consumidor manda para acusar. */
    public record PeticionDeAcuse(List<String> eventoIds) {}

    /** Un lote de eventos pendientes. */
    public record LoteDeEventosResource(
            List<EventoResource> eventos, long pendientesQueQuedan, String aLaFecha) {}

    /** El resultado de un acuse. */
    public record AcuseResource(int recibidos, int marcados, long pendientesQueQuedan) {}
}
