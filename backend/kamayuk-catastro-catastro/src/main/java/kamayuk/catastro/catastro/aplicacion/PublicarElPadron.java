package kamayuk.catastro.catastro.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.Ejercicio;
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
 * Publica el padron al buzon de salida, como trabajo por lotes (C-8).
 *
 * <h2>Un {@code ApplicationRunner} del perfil {@code batch}, y NO un {@code @Scheduled}</h2>
 *
 * <p>Se midio antes de elegir, y {@code @Scheduled} <b>no correria</b>: en los cuatro backends no
 * hay <b>ni un</b> {@code @EnableScheduling} —Spring Boot no lo activa por autoconfiguracion, asi
 * que el unico {@code @Scheduled} del sistema, el publicador del buzon de `caja`, tampoco se
 * registra (P6 §4.4)— y el perfil {@code batch} <b>termina el proceso</b> con {@code
 * web-application-type: none}: un proceso que sale no puede sostener un temporizador.
 *
 * <p>Asi que se hace como todo lo demas que corre por lotes aqui —la implantacion, las seis cargas
 * del padron, la anti-entropia de `rentas`—: un runner que un {@code CronJob} invoca. <b>Ese {@code
 * CronJob} no esta desplegado</b>, y hay que decirlo: {@code infra/} despliega hoy un solo sistema
 * y ninguno de los cuatro del corte tiene manifiesto.
 *
 * <h2>Que hace, en este orden</h2>
 *
 * <ol>
 *   <li><b>Proyecta el padron.</b> Siempre. No necesita conjunto sellado y por eso se puede correr
 *       hoy, con D-02a abierta.
 *   <li><b>Corre la valuacion del ejercicio</b>, si se le dio uno. Si el ejercicio no tiene
 *       conjunto sellado, se niega <b>nombrandolo</b> y sale distinto de cero: fijar un conjunto
 *       inventado produciria un padron calculado con parametros que nadie sello.
 * </ol>
 *
 * <p>Y <b>no entrega nada</b>: la entrega la hace el consumidor, viniendo a buscarlo (ver {@code
 * EventosController}). Un proceso que publicara y entregara en la misma corrida volveria a meter la
 * red dentro del acto que escribe el padron, que es exactamente lo que un outbox existe para
 * evitar.
 */
@Component
@Profile("batch")
@ConditionalOnProperty("kamayuk.catastro.publicacion.municipalidad")
public class PublicarElPadron implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicarElPadron.class);

    private final PublicacionDelPadron publicacion;
    private final Clock reloj;
    private final long municipalidadId;
    private final int ejercicio;

    public PublicarElPadron(
            PublicacionDelPadron publicacion,
            Clock reloj,
            @Value("${kamayuk.catastro.publicacion.municipalidad}") long municipalidadId,
            // Cero significa «solo proyectar»: la valuacion es un acto de un ejercicio y no se
            // corre por descuido. Un valor por omision que fuera «el ejercicio en curso» dejaria
            // que una corrida de emision arrancara desde una tarea programada que nadie pidio.
            @Value("${kamayuk.catastro.publicacion.ejercicio:0}") int ejercicio) {
        this.publicacion = publicacion;
        this.reloj = reloj;
        this.municipalidadId = municipalidadId;
        this.ejercicio = ejercicio;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        // La fecha sale del reloj INYECTADO y entra como argumento (regla 6, regla 9): lo que se
        // publica dice a que fecha esta, y dentro de un mes ese informe tiene que poder leerse.
        LocalDate aLaFecha = LocalDate.now(reloj);
        TenantContext.fijar(new MunicipalidadId(municipalidadId));
        try {
            log.info(
                    "Proyectando el padron de la municipalidad {} al {}",
                    municipalidadId,
                    aLaFecha);
            log.info("{}", publicacion.proyectarElPadron(aLaFecha));
            if (ejercicio > 0) {
                log.info(
                        "Corriendo la valuacion del ejercicio {} con corte al {}",
                        ejercicio,
                        aLaFecha);
                log.info("{}", publicacion.correrLaValuacion(new Ejercicio(ejercicio), aLaFecha));
            }
            log.info(
                    "Quedan {} hecho(s) por entregar en el buzon",
                    publicacion.pendientesDeEntregar());
        } finally {
            // SIEMPRE, y aunque haya lanzado: sin esto, cualquier cosa que corriera despues leeria
            // con el contexto de esta municipalidad — datos reales bajo otra etiqueta.
            TenantContext.limpiar();
        }
    }
}
