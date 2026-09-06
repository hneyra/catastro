package kamayuk.catastro.nucleo.aplicacion;

import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.nucleo.dominio.BuzonDeSalida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Publica el territorio al buzon, como trabajo por lotes (#7).
 *
 * <p>Un {@code ApplicationRunner} del perfil {@code batch} y no un {@code @Scheduled}, por lo que
 * {@code PublicarElPadron} midio: no hay ni un {@code @EnableScheduling} en los cuatro backends y
 * el perfil {@code batch} termina el proceso. Lo invoca un {@code CronJob}, y <b>ese CronJob no
 * esta desplegado</b>.
 *
 * <p>Va aparte de {@code PublicarElPadron} y no dentro: el padron y el territorio se publican con
 * cadencias distintas —la proyeccion del predio cambia cada vez que alguien ficha, los frentes
 * cambian cuando pasa una brigada— y meterlos en la misma corrida obligaria a leer el padron entero
 * para publicar una manzana.
 *
 * <p><b>Y no entrega nada</b>: la entrega la hace el consumidor viniendo a buscarlo ({@code
 * EventosController}). Un proceso que publicara y entregara en la misma corrida volveria a meter la
 * red dentro del acto que escribe, que es lo que un outbox existe para evitar.
 */
@Component
@Profile("batch")
@ConditionalOnProperty("kamayuk.catastro.territorio.municipalidad")
public class PublicarElTerritorio implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicarElTerritorio.class);

    private final PublicacionDelTerritorio publicacion;
    private final long municipalidadId;

    public PublicarElTerritorio(
            PublicacionDelTerritorio publicacion,
            @Value("${kamayuk.catastro.territorio.municipalidad}") long municipalidadId) {
        this.publicacion = publicacion;
        this.municipalidadId = municipalidadId;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        TenantContext.fijar(new MunicipalidadId(municipalidadId));
        try {
            PublicacionDelTerritorio.Informe informe = publicacion.publicar();
            log.info(
                    "Territorio publicado en la municipalidad {}: {} manzana(s) leida(s) y {}"
                            + " nueva(s); {} predio(s) con frentes y {} hecho(s) nuevo(s); {}"
                            + " hallazgo(s) firme(s) y {} nuevo(s)",
                    municipalidadId,
                    informe.manzanasLeidas(),
                    informe.manzanasNuevas(),
                    informe.prediosConFrentes(),
                    informe.frentesNuevos(),
                    informe.hallazgosFirmes(),
                    informe.hallazgosNuevos());
        } catch (BuzonDeSalida.HechoSelladoReescrito reescrito) {
            // No se traga: un hallazgo firme que vuelve con otro contenido es alguien reescribiendo
            // lo que otra persona firmo, y el receptor no puede distinguirlo de un reenvio.
            log.error("La publicacion del territorio se paro: {}", reescrito.getMessage());
            throw reescrito;
        } finally {
            TenantContext.limpiar();
        }
    }
}
