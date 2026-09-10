package kamayuk.catastro.seguridad.aplicacion;

import java.util.List;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.seguridad.infraestructura.RegistroDeMunicipalidadesJdbc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Pone la municipalidad dentro de la base de {@code catastro}: sin esto no hay nada que
 * administrar.
 *
 * <h2>El hueco que cierra (C-6, hueco 3)</h2>
 *
 * <p>Cada sistema tiene <b>su propia base</b> (ADR-0032) y en cada una hay una tabla {@code
 * municipalidad} con su {@code es_demostracion}. {@code SoloEnDemostracion} la consulta <b>en la
 * base de su propio sistema</b>, y las politicas RLS resuelven {@code app.municipalidad_id} contra
 * ella. Hasta C-7, el unico {@code INSERT INTO municipalidad} del arbol de este repositorio estaba
 * en fixtures de prueba: una instalacion real no tenia como escribir esa fila, y sin ella los pasos
 * de siembra se negaban a correr —correctamente— sin que nada dijera que era lo que faltaba.
 *
 * <p>Es el mismo hueco que #430 cerro para {@code area} y {@code caja}, y se cierra igual: <b>por
 * donde entra la configuracion de la municipalidad, no con una pantalla</b>.
 *
 * <h2>Por que un proceso y no un endpoint</h2>
 *
 * <p>Porque {@code municipalidad} solo la escribe {@code kamayuk_owner}. Un endpoint que lo hiciera
 * le exigiria a {@code kamayuk_app} un privilegio que se le quito a proposito, y seria el camino
 * mas corto de una pantalla de alta a una escalada entre municipalidades.
 *
 * <p>Corre en el perfil {@code batch}: sin servidor web, sin puerto expuesto y con vida corta. Las
 * credenciales de {@code kamayuk_owner} entran <b>solo</b> en el paso 1, para <b>un</b> {@code
 * INSERT}, en una conexion que se abre y se cierra. Todo lo demas va por el camino normal de la
 * aplicacion, como {@code kamayuk_app} y con su auditoria.
 *
 * <h2>Un grupo, no dos</h2>
 *
 * <p>{@code rentas} crea dos —administracion y {@code Seguridad}—; aqui solo el primero. El segundo
 * es la plantilla de quien administra <b>el acceso de los usuarios</b>, y esas pantallas viven en
 * {@code rentas} (ADR-0030 §3): crear aqui un grupo que no puede administrar nada seria decir que
 * existe una delegacion que no existe.
 *
 * <h2>Idempotente, entera</h2>
 *
 * <p>Se ejecuta en cada despliegue. Lo que ya existe se queda como esta —con los permisos que
 * alguien haya configurado despues—, y lo que falta se crea. Nunca borra.
 *
 * <h2>Y termina con una pasada del consumidor de {@code identidad} (etapa 4)</h2>
 *
 * <p>Desde ADR-0039 la copia local la mantiene el buzon de {@code identidad}, y el {@code CronJob}
 * que lo consume corre cada cinco minutos. Una municipalidad recien implantada tendria hasta cinco
 * minutos con la copia como la dejo la siembra —sin lo que {@code identidad} ya publico—, asi que
 * la implantacion la pone al dia antes de salir. Si el despliegue no dice donde esta {@code
 * identidad}, se dice y no se falla: en esta etapa la implantacion sin consumidor sigue siendo una
 * implantacion (es lo que hay en un compose sin identidad de servicio), y lo que falta lo recoge el
 * {@code CronJob}.
 */
@Component
@Profile("batch")
@Order(ImplantarMunicipalidad.ORDEN)
@ConditionalOnProperty("kamayuk.implantacion.ubigeo")
@EnableConfigurationProperties(DatosDeImplantacion.class)
public class ImplantarMunicipalidad implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImplantarMunicipalidad.class);

    /**
     * La implantacion va la primera de los {@code ApplicationRunner} de este perfil.
     *
     * <p>Es la que da de alta la municipalidad, y sin ella cualquier otro runner del proceso
     * trabaja sobre un inquilino que todavia no existe. Sin un orden declarado los runners valen
     * {@code LOWEST_PRECEDENCE} y el que corre primero lo decide el registro de beans: medido en la
     * instalacion de AC-5/AC-6, el consumidor de identidad corrio ANTES y esta implantacion nunca
     * llego a ejecutarse (H4). El numero no importa; lo que importa es que sea menor que el de
     * {@link CorrerElConsumidorDeIdentidad#ORDEN}, que se escribe como este mas uno.
     */
    public static final int ORDEN = 0;

    private final RegistroDeMunicipalidadesJdbc registro;
    private final SembradorDeLaCopiaLocal sembrador;
    private final DatosDeImplantacion datos;
    private final ObjectProvider<IngestarEventosDeIdentidad> consumidor;

    public ImplantarMunicipalidad(
            RegistroDeMunicipalidadesJdbc registro,
            SembradorDeLaCopiaLocal sembrador,
            DatosDeImplantacion datos,
            ObjectProvider<IngestarEventosDeIdentidad> consumidor) {
        this.registro = registro;
        this.sembrador = sembrador;
        this.datos = datos;
        this.consumidor = consumidor;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        long municipalidadId =
                registro.darDeAltaSiFalta(
                        datos.ubigeo(), datos.nombre(), datos.tipo(), datos.esDemostracion());

        // El perfil batch no tiene filtros HTTP, asi que los dos contextos que en una peticion
        // salen del token se fijan aqui a mano. `Origen.deProceso` existe para esto: una escritura
        // sin peticion detras, que aun asi tiene que decir quien.
        TenantContext.fijar(new MunicipalidadId(municipalidadId));
        OrigenContext.fijar(Origen.deProceso(datos.usuarioDelProceso()));
        try {
            int nuevos =
                    sembrador.sembrar(
                            datos.administrador(),
                            datos.nombreDelAdministrador(),
                            Observacion.de(
                                    "Implantacion de la municipalidad "
                                            + datos.ubigeo()
                                            + " en catastro (despliegue)"));

            // El regimen se registra aunque sea una sola palabra: es lo unico del resultado que no
            // se puede comprobar mirando pantallas. Una instalacion que se creia de demostracion y
            // salio real emite papeles sin marca, y quien lo descubre es quien recibe uno (#122).
            log.info(
                    "Municipalidad {} lista en catastro ({}): id {}, {} accesos nuevos,"
                            + " administrador '{}'",
                    datos.ubigeo(),
                    datos.esDemostracion() ? "DEMOSTRACION" : "instalacion real",
                    municipalidadId,
                    nuevos,
                    datos.administrador());
            ponerLaCopiaAlDia();
        } finally {
            OrigenContext.limpiar();
            TenantContext.limpiar();
        }
    }

    /**
     * La ultima pasada: lo que {@code identidad} ya publico para esta municipalidad, aplicado antes
     * de salir. Con el contexto de tenant ya fijado por {@link #run}.
     */
    private void ponerLaCopiaAlDia() {
        IngestarEventosDeIdentidad ingestor = consumidor.getIfAvailable();
        if (ingestor == null) {
            log.warn(
                    "Municipalidad {}: la implantacion NO consumio el buzon de identidad, porque"
                            + " este despliegue no dice donde esta (kamayuk.identidad.url vacia)."
                            + " La copia local queda como la sembro la implantacion, y lo que"
                            + " identidad ya publico lo recogera el consumidor cuando corra",
                    datos.ubigeo());
            return;
        }
        List<IngestarEventosDeIdentidad.Vuelta> vueltas =
                CorrerElConsumidorDeIdentidad.hastaAgotar(ingestor);
        log.info(
                "Municipalidad {}: la copia local de la autorizacion se puso al dia con el buzon de"
                        + " identidad en {} vuelta(s); la ultima: {}",
                datos.ubigeo(),
                vueltas.size(),
                vueltas.getLast());
    }
}
