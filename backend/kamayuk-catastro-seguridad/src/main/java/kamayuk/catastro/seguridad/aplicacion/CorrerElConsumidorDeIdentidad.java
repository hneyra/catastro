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
 * <h2>Sale con error si quedo algo pospuesto</h2>
 *
 * <p>Un evento pospuesto es uno que no se aplico y no se acuso: el emisor lo sigue teniendo
 * pendiente y la corrida siguiente lo vuelve a intentar, asi que no se pierde. Lo que no puede
 * pasar es que la corrida salga en verde con el: un {@code Job} que se declara {@code Complete} con
 * un permiso sin aplicar es un permiso que nadie va a echar de menos hasta que alguien reciba un
 * 403. Se lanza al final y no al primero, para que lo que si se podia aplicar quede aplicado.
 */
@Component
@Profile("batch")
@ConditionalOnProperty({"kamayuk.identidad.url", "kamayuk.identidad.consumidor.municipalidad"})
public class CorrerElConsumidorDeIdentidad implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CorrerElConsumidorDeIdentidad.class);

    /** El tope de vueltas por corrida: 50 × 200 eventos, mucho mas de lo que llega en 5 minutos. */
    public static final int VUELTAS_MAXIMAS = 50;

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
            List<IngestarEventosDeIdentidad.Vuelta> vueltas = hastaAgotar(ingestor);
            exigirQueNadaQuedaraPospuesto(vueltas);
        } finally {
            TenantContext.limpiar();
        }
    }

    /**
     * Da vueltas hasta que una no acuse nada o se llegue al tope, con el contexto ya fijado.
     *
     * <p>Es estatico y publico porque la implantacion termina con esta misma pasada (identidad#4
     * AC-4), y ahi el contexto ya esta fijado por ella.
     */
    public static List<IngestarEventosDeIdentidad.Vuelta> hastaAgotar(
            IngestarEventosDeIdentidad ingestor) {
        List<IngestarEventosDeIdentidad.Vuelta> vueltas = new ArrayList<>();
        for (int i = 0; i < VUELTAS_MAXIMAS; i++) {
            IngestarEventosDeIdentidad.Vuelta vuelta = ingestor.unaVuelta();
            vueltas.add(vuelta);
            if (vuelta.sinProgreso()) {
                return vueltas;
            }
        }
        log.warn(
                "El consumidor de identidad dio {} vueltas y el buzon sigue con eventos: se sigue"
                        + " en la corrida siguiente",
                VUELTAS_MAXIMAS);
        return vueltas;
    }

    /** Lo que quedo pospuesto sale como error, para que la corrida no se lea como completa. */
    public static void exigirQueNadaQuedaraPospuesto(
            List<IngestarEventosDeIdentidad.Vuelta> vueltas) {
        IngestarEventosDeIdentidad.Vuelta ultima = vueltas.getLast();
        if (ultima.pospuestos() > 0) {
            throw new IllegalStateException(
                    "El consumidor de identidad termino con "
                            + ultima.pospuestos()
                            + " evento(s) POSPUESTOS: no se aplicaron y no se acusaron, asi que"
                            + " el buzon los volvera a servir en la corrida siguiente. No se"
                            + " pierden, pero una corrida en verde con ellos dentro se leeria como"
                            + " que la copia local esta al dia, y no lo esta. El motivo de cada uno"
                            + " esta en las lineas WARN de arriba");
        }
    }
}
