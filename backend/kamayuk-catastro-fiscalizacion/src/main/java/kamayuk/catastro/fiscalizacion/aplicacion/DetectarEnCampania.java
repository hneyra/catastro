package kamayuk.catastro.fiscalizacion.aplicacion;

import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * La corrida de deteccion de subvaluadores, en el perfil {@code batch} (#30, AC-3).
 *
 * <h2>Por que sale del {@code POST} y no se vuelve asincrona</h2>
 *
 * <p>Hasta #30 la lanzaba {@code POST /fiscalizacion/campanias/{id}/deteccion} y la peticion
 * esperaba a que terminara. Lo que espera es esto: {@code AreasDelPadronJdbc.CRUCE} recorre
 * <b>todos</b> los predios con geometria del inquilino calculando {@code ST_Area} geodesico, y su
 * {@code ORDER BY diferencia DESC} obliga a calcularlo todo antes de aplicar el {@code LIMIT}. No
 * hay indice que lo evite —un area geodesica no esta en ninguna columna, y ADR-0021 dice por que no
 * debe estarlo—, asi que el trabajo crece con el padron y el tope solo acota lo que sale, no lo que
 * cuesta. Hoy no se nota porque no hay ni un poligono cargado en ninguna instalacion; el dia que se
 * cargue el plano de una municipalidad, esa peticion agota el tiempo de espera del ingreso.
 *
 * <p><b>Y la salida no es un 202 con un identificador de trabajo</b>, que era la otra opcion que el
 * issue admitia. Una respuesta asincrona mueve las tres respuestas que hoy son visibles —«esta
 * municipalidad no tiene cartografia», «esa campania no existe», «esa campania esta cerrada»— a un
 * sitio donde nadie mira, y este sistema no tiene tabla de trabajos donde dejarlas ni ruta para
 * preguntarlas: habria que inventar las dos. En {@code batch} esas tres respuestas siguen siendo
 * visibles y en el sitio donde ya se miran los otros nueve procesos de este perfil — el registro
 * del proceso y su <b>codigo de salida</b>.
 *
 * <p>Es ademas lo que {@link DetectarSubvaluadores} decia de si mismo desde #6 y no era cierto:
 * «corre en el perfil {@code batch} —lo lanza quien abre la campania— y no en la web: recorre el
 * padron».
 *
 * <h2>Que se observa cuando falla</h2>
 *
 * <p><b>Falla ruidosa y con codigo distinto de cero.</b> Las tres excepciones de negocio se dejan
 * salir de {@code run}: Spring Boot convierte eso en un arranque fallido, o sea un {@code Job} de
 * Kubernetes en estado {@code Failed} y no {@code Complete}. Antes de dejarlas salir se registra la
 * frase, porque una traza sola no dice que hacer.
 *
 * <p><b>Sin cartografia falla, y no sale con cero.</b> Es la decision de AC 8 de #6 llevada al
 * proceso: un detector que sobre un padron sin poligonos terminara bien y sin candidatos estaria
 * afirmando «no hay subvaluadores», la campania se cerraria con cero hallazgos y nadie va a revisar
 * un cero. Que hoy eso deje el {@code Job} en rojo en toda instalacion es correcto: es lo que pasa,
 * y es el defecto de C-6 —arrancar, no hacer nada y salir con 0— por su otra cara.
 *
 * <h2>El censo de #25 no se pierde: cambia de superficie</h2>
 *
 * <p>{@code DeteccionResource} publicaba los candidatos <b>y de cuantos predios salian</b>, porque
 * «0 candidatos» sin su denominador no es una respuesta. Ese recurso se retira con la ruta —una
 * respuesta sin endpoint que la devuelva es una promesa que nadie cumple— y las cuatro cifras de
 * {@code AreasDelPadron.Cobertura} pasan al registro de esta corrida, que es donde se miran las de
 * los otros nueve procesos de este perfil. Se registran <b>siempre</b>, y con mas motivo cuando la
 * corrida no deja ningun candidato: es justo el caso que #25 existe para que no se lea como «el
 * padron esta bien».
 *
 * <p>Un {@code ApplicationRunner} y no un {@code @Scheduled}, por lo mismo que {@code
 * DerivarFrentes}: en los cuatro backends no hay ni un {@code @EnableScheduling}, y el perfil
 * {@code batch} termina el proceso. Lo invoca un {@code Job}, y <b>ese Job no esta desplegado</b>:
 * hay que decirlo, igual que con {@code DerivarFrentes} y {@code PublicarElPadron}.
 */
@Component
@Profile("batch")
@ConditionalOnProperty("kamayuk.deteccion-de-subvaluadores.campania-id")
@EnableConfigurationProperties(DatosDeDeteccionDeSubvaluadores.class)
public class DetectarEnCampania implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DetectarEnCampania.class);

    private final DetectarSubvaluadores detector;
    private final DatosDeDeteccionDeSubvaluadores datos;

    public DetectarEnCampania(
            DetectarSubvaluadores detector, DatosDeDeteccionDeSubvaluadores datos) {
        this.detector = detector;
        this.datos = datos;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        TenantContext.fijar(new MunicipalidadId(datos.municipalidadId()));
        OrigenContext.fijar(Origen.deProceso(datos.usuarioDelProceso()));
        try {
            DetectarSubvaluadores.Deteccion deteccion =
                    detector.detectar(datos.campaniaId(), Observacion.de(datos.observacion()));
            AreasDelPadron.Cobertura cobertura = deteccion.cobertura();
            log.info(
                    "Deteccion de subvaluadores de la campania {} en la municipalidad {}: {}"
                            + " candidato(s) sobre {} predio(s) contrastado(s) —{} sin geometria,"
                            + " {} sin ficha vigente, {} truncado(s) por el tope—. Un candidato es"
                            + " una SOSPECHA: no corrige ninguna ficha y lo miran dos personas"
                            + " (ADR-0035)",
                    datos.campaniaId(),
                    datos.municipalidadId(),
                    deteccion.candidatos().size(),
                    cobertura.contrastados(),
                    cobertura.sinGeometria(),
                    cobertura.sinFichaVigente(),
                    cobertura.truncadosPorElTope());
            if (deteccion.candidatos().isEmpty()) {
                log.warn(
                        "Ningun candidato en la campania {} sobre {} predio(s) contrastado(s). Hay"
                                + " poligonos —si no los hubiera esto habria fallado con"
                                + " SinCartografia— asi que o ninguna ficha difiere de su lote mas"
                                + " que el umbral de la campania, o el umbral deja fuera a los que"
                                + " difieren. Un cero sin su denominador no es una respuesta (#25)",
                        datos.campaniaId(),
                        cobertura.contrastados());
            }
        } catch (AreasDelPadron.SinCartografia sinPlanos) {
            log.error(
                    "La deteccion de la campania {} NO SE PUDO CORRER: {}. Esto no es «no hay"
                            + " subvaluadores»: es que no se puede mirar, y por eso este proceso"
                            + " sale con error en vez de con cero candidatos",
                    datos.campaniaId(),
                    sinPlanos.getMessage());
            throw sinPlanos;
        } catch (AbrirCampania.CampaniaInexistente
                | DetectarSubvaluadores.CampaniaCerradaParaDetectar noSePuede) {
            log.error(
                    "La deteccion no corrio sobre la campania {}: {}",
                    datos.campaniaId(),
                    noSePuede.getMessage());
            throw noSePuede;
        } finally {
            OrigenContext.limpiar();
            TenantContext.limpiar();
        }
    }
}
