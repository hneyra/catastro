package kamayuk.catastro.seguridad.aplicacion;

import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * El proceso que consume el buzon de {@code identidad} para una municipalidad: el {@code CronJob}
 * de cada cinco minutos (identidad#4 AC-2), y tambien la ultima pasada de la implantacion.
 *
 * <p>Es un {@code ApplicationRunner} del perfil {@code batch} y no un {@code @Scheduled}, por lo
 * mismo que {@code PublicarElPadron} y que {@code CorrerElIngestor} de {@code rentas}: en los cinco
 * backends no hay un solo {@code @EnableScheduling}, y el perfil {@code batch} termina el proceso.
 * Lo despierta el {@code CronJob}; cuando termina, sale.
 *
 * <h2>Vueltas acotadas, y parada por «sin progreso»</h2>
 *
 * <p>Se dan vueltas hasta que una no acuse nada o se llegue al tope. El tope existe porque un buzon
 * que recibe mas deprisa de lo que este consumidor aplica no puede convertir una corrida en un
 * proceso sin fin —de eso se encarga la corrida siguiente—; la parada por progreso existe porque un
 * evento pospuesto no se acusa y el buzon lo vuelve a servir: sin ella, un buzon en el que solo
 * queden pospuestos daria las cincuenta vueltas y cincuenta avisos de lo mismo.
 *
 * <h2>Un pospuesto NO hace fallar la corrida</h2>
 *
 * <p>Un evento pospuesto es uno que no se aplico y no se acuso: el emisor lo sigue teniendo
 * pendiente y la corrida siguiente lo vuelve a intentar, asi que no se pierde. Y no es un fallo de
 * la corrida: es una dependencia que todavia no ha llegado. Salir con codigo 1 por el —que es lo
 * que esta clase hacia hasta que se midio— deja en el cluster un {@code Job} {@code Failed} cada
 * cinco minutos, reintentado ademas una vez por {@code backoffLimit: 1}, durante todo el tiempo que
 * la dependencia tarde; y un trabajo que sale rojo por algo normal deja de significar nada el dia
 * que salga rojo de verdad. Lo que si tiene que pasar es que alguien se entere cuando eso se
 * estanca, y de eso se encarga {@link IngestarEventosDeIdentidad#avisarSiLosPospuestosSeEstancan}:
 * un aviso por corrida con la lista, a partir de {@link
 * IngestarEventosDeIdentidad#ANTIGUEDAD_QUE_SE_AVISA}.
 *
 * <p>Lo que si hace salir con error es que <b>la base o el buzon no contesten</b>: eso no es un
 * hecho del dominio, corta la vuelta y se propaga.
 *
 * <h2>Y corre DETRAS de la implantacion</h2>
 *
 * <p>Los dos son {@code ApplicationRunner} del perfil {@code batch}, asi que en un proceso que
 * lleve las variables de los dos —lo que hace el compose— corren los dos. Sin orden declarado los
 * dos valen {@code LOWEST_PRECEDENCE} y el que va primero lo decide el registro de beans: medido en
 * la instalacion de AC-5/AC-6, el consumidor corrio ANTES y la implantacion no llego a correr (H4).
 * Consumir el buzon antes de que exista la municipalidad no tiene sentido —no hay copia que poner
 * al dia—, asi que el orden se declara y no se hereda.
 */
@Component
@Profile("batch")
@Order(CorrerElConsumidorDeIdentidad.ORDEN)
@ConditionalOnProperty({"kamayuk.identidad.url", "kamayuk.identidad.consumidor.municipalidad"})
public class CorrerElConsumidorDeIdentidad implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CorrerElConsumidorDeIdentidad.class);

    /** El tope de vueltas por corrida: 50 × 200 eventos, mucho mas de lo que llega en 5 minutos. */
    public static final int VUELTAS_MAXIMAS = 50;

    /**
     * Detras de la implantacion, y por eso se escribe como su orden mas uno.
     *
     * <p>No es un numero elegido: es la relacion la que importa, y escribirla asi hace que mover el
     * de la implantacion mueva este con ella.
     */
    public static final int ORDEN = ImplantarMunicipalidad.ORDEN + 1;

    private final IngestarEventosDeIdentidad ingestor;
    private final long municipalidadId;

    public CorrerElConsumidorDeIdentidad(
            IngestarEventosDeIdentidad ingestor,
            @Value("${kamayuk.identidad.consumidor.municipalidad}") long municipalidadId) {
        this.ingestor = ingestor;
        this.municipalidadId = municipalidadId;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        // El perfil batch no tiene filtros HTTP: el contexto que en una peticion sale del token se
        // fija aqui, y se limpia al salir. Es lo mismo que hacen los demas runners de este perfil.
        TenantContext.fijar(new MunicipalidadId(municipalidadId));
        try {
            hastaAgotar(ingestor);
        } finally {
            TenantContext.limpiar();
        }
    }

    /**
     * Da vueltas hasta que una no acuse nada o se llegue al tope, con el contexto ya fijado, y
     * avisa al final si los pospuestos se estancaron.
     *
     * <p>Es estatico y publico porque la implantacion termina con esta misma pasada (identidad#4
     * AC-4), y ahi el contexto ya esta fijado por ella. El aviso vive aqui dentro y no en el
     * llamador para que los dos caminos —el {@code CronJob} y la implantacion— lo den por igual:
     * uno por corrida, sea quien sea el que corre.
     */
    public static List<IngestarEventosDeIdentidad.Vuelta> hastaAgotar(
            IngestarEventosDeIdentidad ingestor) {
        List<IngestarEventosDeIdentidad.Vuelta> vueltas = new ArrayList<>();
        for (int i = 0; i < VUELTAS_MAXIMAS; i++) {
            IngestarEventosDeIdentidad.Vuelta vuelta = ingestor.unaVuelta();
            vueltas.add(vuelta);
            if (vuelta.sinProgreso()) {
                ingestor.avisarSiLosPospuestosSeEstancan(vueltas);
                return vueltas;
            }
        }
        log.warn(
                "El consumidor de identidad dio {} vueltas y el buzon sigue con eventos: se sigue"
                        + " en la corrida siguiente",
                VUELTAS_MAXIMAS);
        ingestor.avisarSiLosPospuestosSeEstancan(vueltas);
        return vueltas;
    }
}
