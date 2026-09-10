package kamayuk.catastro.seguridad.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.autorizacion.ComprobadorDeAcceso;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.seguridad.dominio.CatalogoDelSistema;
import kamayuk.catastro.seguridad.dominio.FuenteDeEventosDeIdentidad;
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
 * Pone la municipalidad dentro de la base de {@code catastro}: la fila de {@code municipalidad}, el
 * catalogo de este sistema y —traida del buzon de {@code identidad}— la copia local de la
 * autorizacion.
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
 * <h2>Lo que este sistema siembra, y lo que le llega (ADR-0039 etapa 5, identidad#5 AC-1)</h2>
 *
 * <p>Siembra <b>su catalogo</b> —{@code modulo_sistema} y {@code acceso}— y nada mas: quien es el
 * administrador, a que grupo pertenece y que puede hacer lo decide {@code identidad}, y llega por
 * el buzon. Hasta la etapa 4 esta implantacion escribia ademas esas cuatro filas, y con ellas dos
 * origenes para la misma tabla: el sembrador solo agregaba, asi que <b>un permiso revocado en
 * {@code identidad} volvia a otorgarse en el despliegue siguiente</b> sin que nada lo dijera.
 *
 * <h2>Y por eso el ORDEN de implantacion importa, y aqui se comprueba (AC-3)</h2>
 *
 * <p>{@code identidad} se implanta <b>antes</b> que los cuatro satelites: su implantacion es la que
 * da de alta al administrador y la que lo publica. Implantar este sistema primero dejaria una copia
 * local <b>vacia</b> —catalogo sembrado y ni un usuario— con la que nadie puede entrar, y el
 * sintoma no se parece a su causa: el funcionario recibe un 403 «no estas dado de alta» semanas
 * despues. Asi que esta implantacion no termina bien de tres maneras distintas, y cada una nombra
 * lo suyo:
 *
 * <ul>
 *   <li><b>no hay a quien preguntarle</b>: el despliegue no dice donde esta {@code identidad};
 *   <li><b>hay a quien preguntarle y no contesta</b>: {@link
 *       FuenteDeEventosDeIdentidad.IdentidadNoContesta} —emisor caido, sin cuenta de servicio, sin
 *       credencial—, que se relanza con el remedio dentro;
 *   <li><b>contesto y no trajo nada</b>: la copia local se quedo sin nadie que pueda entrar. Es el
 *       caso que las otras dos NO cubren y el unico que se ve mirando el <b>resultado</b> y no la
 *       llamada: un buzon vacio para esta municipalidad contesta {@code 200} y una vuelta sin
 *       progreso, exactamente igual que un buzon que ya se aplico entero.
 * </ul>
 *
 * <p><b>Un {@code Job} que sale con codigo 0 sin haber implantado nada es el defecto de C-18 §5 con
 * otra cara</b>: en Kubernetes se ve {@code Complete} y nadie mira dentro.
 *
 * <h2>Lo que NO se hace aqui, y corre por otro camino: el pospuesto</h2>
 *
 * <p>El mismo consumidor corre cada cinco minutos desde un {@code CronJob}, y ahi un evento
 * <b>pospuesto</b> —uno que espera a su dependencia— no hace fallar la corrida: sale con {@code
 * rc=0} y avisa cuando se estanca ({@link IngestarEventosDeIdentidad#ANTIGUEDAD_QUE_SE_AVISA}). Eso
 * no cambia, y esta implantacion no lo endurece: <b>no se falla por haber pospuesto algo</b>, se
 * falla porque la copia quedo inservible. Son dos preguntas distintas —«¿quedo algo por aplicar?» y
 * «¿puede entrar alguien?»— y responder la primera aqui convertiria el {@code CronJob} en un {@code
 * Job} {@code Failed} cada cinco minutos, que es de lo que la etapa 4 salio.
 *
 * <h2>Un grupo, ninguno</h2>
 *
 * <p>Hasta la etapa 4 esta implantacion creaba el grupo de administracion. Ya no crea ninguno: los
 * grupos son de {@code identidad} y llegan por el buzon con sus miembros y sus permisos.
 *
 * <h2>Idempotente, entera</h2>
 *
 * <p>Se ejecuta en cada despliegue. Lo que ya existe se queda como esta —con los permisos que
 * {@code identidad} haya publicado despues—, y lo que falta se crea. Nunca borra. Y la comprobacion
 * final sigue valiendo en el segundo despliegue, porque mira <b>la copia</b> y no los eventos de
 * esta vuelta: la segunda vez el buzon viene vacio —ya se acuso todo— y el administrador sigue
 * estando.
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
    private final SembradorDelCatalogo sembrador;
    private final DatosDeImplantacion datos;
    private final ObjectProvider<IngestarEventosDeIdentidad> consumidor;
    private final ComprobadorDeAcceso comprobador;
    private final Clock reloj;

    public ImplantarMunicipalidad(
            RegistroDeMunicipalidadesJdbc registro,
            SembradorDelCatalogo sembrador,
            DatosDeImplantacion datos,
            ObjectProvider<IngestarEventosDeIdentidad> consumidor,
            ComprobadorDeAcceso comprobador,
            Clock reloj) {
        this.registro = registro;
        this.sembrador = sembrador;
        this.datos = datos;
        this.consumidor = consumidor;
        this.comprobador = comprobador;
        this.reloj = reloj;
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
                            Observacion.de(
                                    "Implantacion de la municipalidad "
                                            + datos.ubigeo()
                                            + " en catastro (despliegue)"));

            // El regimen se registra aunque sea una sola palabra: es lo unico del resultado que no
            // se puede comprobar mirando pantallas. Una instalacion que se creia de demostracion y
            // salio real emite papeles sin marca, y quien lo descubre es quien recibe uno (#122).
            log.info(
                    "Municipalidad {} lista en catastro ({}): id {}, {} accesos nuevos de los {}"
                            + " del catalogo de este sistema",
                    datos.ubigeo(),
                    datos.esDemostracion() ? "DEMOSTRACION" : "instalacion real",
                    municipalidadId,
                    nuevos,
                    CatalogoDelSistema.opciones().size());
            ponerLaCopiaAlDia();
            exigirQueLaCopiaSirva();
        } finally {
            OrigenContext.limpiar();
            TenantContext.limpiar();
        }
    }

    /**
     * Trae del buzon de {@code identidad} todo lo que ya publico para esta municipalidad. Con el
     * contexto de tenant ya fijado por {@link #run}.
     *
     * <p>No es opcional y no lo puede ser: es el <b>unico</b> camino por el que esta copia local se
     * entera de quien puede hacer que. Hasta la etapa 4 la ausencia del consumidor se decia con un
     * {@code WARN} y la implantacion seguia, porque el sembrador dejaba un administrador escrito a
     * mano; retirado el sembrador (AC-1), esa misma ausencia deja la copia <b>vacia</b>.
     */
    private void ponerLaCopiaAlDia() {
        IngestarEventosDeIdentidad ingestor = consumidor.getIfAvailable();
        if (ingestor == null) {
            throw new LaCopiaDeLaAutorizacionNoLlego(
                    "Municipalidad "
                            + datos.ubigeo()
                            + ": este despliegue no dice donde esta `identidad`"
                            + " (kamayuk.identidad.url vacia), asi que la implantacion no puede"
                            + " traerse la copia local de la autorizacion y esta base se quedaria"
                            + " con su catalogo sembrado y CERO usuarios: nadie podria entrar, y"
                            + " el sintoma seria un 403 «no estas dado de alta» semanas despues."
                            + " Remedio: dar al Job de implantacion las seis variables"
                            + " KAMAYUK_IDENTIDAD_* que el descriptor y el compose ya declaran,"
                            + " e implantar `identidad` PRIMERO");
        }
        List<IngestarEventosDeIdentidad.Vuelta> vueltas;
        try {
            vueltas = CorrerElConsumidorDeIdentidad.hastaAgotar(ingestor);
        } catch (FuenteDeEventosDeIdentidad.IdentidadNoContesta noContesta) {
            // Se relanza con el remedio dentro en vez de dejarla subir: el mensaje del cliente dice
            // QUE contesto `identidad` —un 401, un 403, un puerto cerrado— y no dice que eso, en
            // una implantacion, significa que este sistema se queda sin autorizacion ninguna.
            throw new LaCopiaDeLaAutorizacionNoLlego(
                    "Municipalidad "
                            + datos.ubigeo()
                            + ": la implantacion no pudo leer el buzon de `identidad`, asi que la"
                            + " copia local de la autorizacion no llego y esta base se quedaria"
                            + " con su catalogo sembrado y CERO usuarios. Remedio: implantar"
                            + " `identidad` PRIMERO —es su implantacion la que da de alta al"
                            + " administrador, crea la cuenta de servicio de este sistema y la"
                            + " afilia al grupo «Consumidores del buzon»— y volver a correr esta."
                            + " Lo que dijo `identidad`: "
                            + noContesta.getMessage(),
                    noContesta);
        }
        log.info(
                "Municipalidad {}: la copia local de la autorizacion se puso al dia con el buzon de"
                        + " identidad en {} vuelta(s); la ultima: {}",
                datos.ubigeo(),
                vueltas.size(),
                vueltas.getLast());
    }

    /**
     * Y despues: que el administrador que este despliegue declara pueda entrar de verdad.
     *
     * <h2>Por que no basta con que el consumidor haya corrido</h2>
     *
     * <p>Porque un buzon que no tiene nada para esta municipalidad contesta <b>200 con la cola
     * vacia</b>, que es indistinguible de uno que ya se aplico entero: la vuelta sale «sin
     * progreso» y el consumidor termina bien. Pasa cuando {@code identidad} todavia no se ha
     * implantado, cuando se implanto para <b>otro</b> ubigeo, o cuando la cuenta de servicio de
     * este sistema apunta a una municipalidad que no es la suya. En los tres casos, sin esta
     * comprobacion, el {@code Job} sale con codigo 0 y deja una copia con cero usuarios.
     *
     * <h2>Lo pregunta el guardia de PRODUCCION, y no una consulta escrita aqui</h2>
     *
     * <p>Se usa {@link ComprobadorDeAcceso}, que es el mismo que decide si una peticion pasa: la
     * pregunta que hay que contestar no es «¿hay filas en {@code usuario}?» sino «¿alguien puede
     * abrir una pantalla de este sistema?», y esas dos se separan por la precedencia
     * usuario-sobre-grupo, por la vigencia y por {@code acceso.activo}, que son suyas. Escribir
     * aqui otra consulta mediria una copia de esa logica.
     *
     * <p>Se exige {@code LECTURA} sobre <b>al menos una</b> opcion y no sobre las {@code N}: lo que
     * hace falta afirmar es que la copia sirve, no que {@code identidad} haya concedido exactamente
     * lo que este sistema espera —eso lo decide {@code identidad}, y un administrador con permiso
     * sobre parte del catalogo es una decision suya, no un despliegue roto—.
     */
    private void exigirQueLaCopiaSirva() {
        LocalDate hoy = LocalDate.now(reloj);
        String cuenta = datos.administrador();
        List<String> puede =
                CatalogoDelSistema.opciones().stream()
                        .map(CatalogoDelSistema.Opcion::codigo)
                        .filter(
                                codigo ->
                                        comprobador.autoriza(
                                                cuenta, codigo, Privilegio.LECTURA, hoy))
                        .toList();
        if (puede.isEmpty()) {
            throw new LaCopiaDeLaAutorizacionNoLlego(
                    "Municipalidad "
                            + datos.ubigeo()
                            + ": el buzon de `identidad` contesto y la copia local quedo sin nadie"
                            + " que pueda entrar — la cuenta «"
                            + cuenta
                            + "» no puede leer ninguna de las "
                            + CatalogoDelSistema.opciones().size()
                            + " opciones de este sistema"
                            + (comprobador.conoceAlUsuario(cuenta)
                                    ? ", y eso que esta dada de alta aqui: le falta el permiso, o"
                                            + " su grupo no llego"
                                    : ", y ni siquiera esta dada de alta aqui: el buzon no trajo su"
                                            + " alta")
                            + ". Un buzon vacio para esta municipalidad contesta 200 igual que uno"
                            + " ya aplicado, asi que sin esta comprobacion este Job saldria"
                            + " «Complete» con CERO usuarios. Remedio: implantar `identidad`"
                            + " PRIMERO y con el MISMO ubigeo ("
                            + datos.ubigeo()
                            + ") y el mismo administrador, y volver a correr esta implantacion");
        }
        log.info(
                "Municipalidad {}: la copia local sirve — «{}» puede leer {} de las {} opciones de"
                        + " este sistema",
                datos.ubigeo(),
                cuenta,
                puede.size(),
                CatalogoDelSistema.opciones().size());
    }

    /**
     * La implantacion no dejo esta base en condiciones de autorizar a nadie.
     *
     * <p>Es una sola excepcion para los tres desenlaces —sin consumidor, el buzon no contesta, y el
     * buzon contesto y no trajo nada— porque lo que hay que impedir es uno solo: que el {@code Job}
     * salga con codigo 0 sobre una copia vacia. Lo que los distingue es el mensaje, que es lo que
     * lee quien tiene que arreglarlo.
     */
    public static final class LaCopiaDeLaAutorizacionNoLlego extends IllegalStateException {

        public LaCopiaDeLaAutorizacionNoLlego(String mensaje) {
            super(mensaje);
        }

        public LaCopiaDeLaAutorizacionNoLlego(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
