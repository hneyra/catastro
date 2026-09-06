package kamayuk.catastro.nucleo.aplicacion;

import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.dominio.Observacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * El derivador de frentes: corta el lote contra el eje de calzada y PROPONE (#7, AC 1).
 *
 * <p>Mismo patron que los ocho cargadores que ya existen —{@code CargarPredios}, {@code
 * CargarZonificacion}, {@code CargarRiesgo}—: perfil {@code batch}, un proceso de vida corta sin
 * servidor web, que fija a mano los dos contextos que en una peticion salen del token.
 *
 * <p><b>Y un {@code ApplicationRunner} y no un {@code @Scheduled}</b>, por lo que {@code
 * PublicarElPadron} midio: en los cuatro backends no hay ni un {@code @EnableScheduling}, y el
 * perfil {@code batch} termina el proceso —un proceso que sale no puede sostener un temporizador—.
 * Lo invoca un {@code CronJob}, y <b>ese CronJob no esta desplegado</b>: hay que decirlo.
 *
 * <p><b>El informe se registra entero, tambien cuando no propone nada</b>, que es lo que va a pasar
 * hoy en toda instalacion: no hay ni un poligono cargado en ninguna (P5C, hueco de carga
 * cartografica). Un proceso que saliera con codigo 0 sin una linea seria el defecto que C-6 midio
 * con el guion de transferencias — arranca, no hace nada, y nadie lo sabe.
 */
@Component
@Profile("batch")
@ConditionalOnProperty("kamayuk.derivacion-de-frentes.municipalidad-id")
@EnableConfigurationProperties(DatosDeDerivacionDeFrentes.class)
public class DerivarFrentes implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DerivarFrentes.class);

    private final DerivacionDeLosFrentes derivacion;
    private final DatosDeDerivacionDeFrentes datos;

    public DerivarFrentes(DerivacionDeLosFrentes derivacion, DatosDeDerivacionDeFrentes datos) {
        this.derivacion = derivacion;
        this.datos = datos;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        TenantContext.fijar(new MunicipalidadId(datos.municipalidadId()));
        OrigenContext.fijar(Origen.deProceso(datos.usuarioDelProceso()));
        try {
            DerivacionDeLosFrentes.Informe informe =
                    derivacion.derivar(
                            datos.tolerancia(), datos.tope(), Observacion.de(datos.observacion()));
            log.info(
                    "Frentes derivados en la municipalidad {} con tolerancia {}: {} predio(s)"
                            + " recorrido(s), {} con frente nuevo, {} frente(s) PROPUESTO(s). Una"
                            + " longitud propuesta no se cobra: confirmarla es un acto (ADR-0021)",
                    datos.municipalidadId(),
                    datos.tolerancia(),
                    informe.prediosRecorridos(),
                    informe.prediosConFrenteNuevo(),
                    informe.frentesPropuestos());
            if (informe.frentesPropuestos() == 0) {
                log.warn(
                        "Ningun frente propuesto sobre {} predio(s). Lo mas probable hoy es que no"
                                + " haya cartografia cargada: sin poligono de lote o sin eje de"
                                + " via no hay contra que cortar. Cada predio recorrido dejo su"
                                + " motivo en `frente_derivacion`",
                        informe.prediosRecorridos());
            }
        } finally {
            OrigenContext.limpiar();
            TenantContext.limpiar();
        }
    }
}
