package kamayuk.catastro.urbano.aplicacion;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.carga.InformeDeImportacion;
import kamayuk.catastro.carga.InformeDeImportacion.FilaRechazada;
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
 * Carga el plan de zonificacion de una municipalidad ya implantada (#4).
 *
 * <p>Mismo patron que {@code CargarPredios}: perfil {@code batch}, un proceso de vida corta sin
 * servidor web, que fija a mano los dos contextos que en una peticion salen del token.
 *
 * <p>El informe se registra completo: cuantas zonas nacieron, cuantas ya estaban y cuales se
 * rechazaron con su motivo. Una fila rechazada no aborta la corrida, pero tiene que quedar visible
 * en el registro de quien la corrio — y aqui mas que en ningun otro cargador, porque el motivo mas
 * probable de un rechazo es que <b>otro plan siga vigente sobre ese suelo</b>, o sea que falta
 * cerrar el plan anterior antes de cargar el nuevo. Un cargador que se lo tragara dejaria media
 * zonificacion nueva conviviendo con la vieja.
 */
@Component
@Profile("batch")
@ConditionalOnProperty("kamayuk.carga-zonificacion.archivo")
@EnableConfigurationProperties(DatosDeCargaZonificacion.class)
public class CargarZonificacion implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CargarZonificacion.class);

    private final ImportarZonificacion importar;
    private final DatosDeCargaZonificacion datos;

    public CargarZonificacion(ImportarZonificacion importar, DatosDeCargaZonificacion datos) {
        this.importar = importar;
        this.datos = datos;
    }

    @Override
    public void run(ApplicationArguments argumentos) throws IOException {
        TenantContext.fijar(new MunicipalidadId(datos.municipalidadId()));
        OrigenContext.fijar(Origen.deProceso(datos.usuarioDelProceso()));
        try (Reader archivo =
                Files.newBufferedReader(Path.of(datos.archivo()), StandardCharsets.UTF_8)) {
            InformeDeImportacion informe =
                    importar.importar(archivo, Observacion.de(datos.observacion()));

            for (FilaRechazada rechazada : informe.rechazadas()) {
                log.warn("Zona de la fila {} rechazada: {}", rechazada.fila(), rechazada.motivo());
            }
            log.info(
                    "Zonificacion de la municipalidad {} cargada desde {}: {} fila(s) leidas, {}"
                            + " zona(s) nueva(s), {} ya existente(s), {} rechazada(s)",
                    datos.municipalidadId(),
                    datos.archivo(),
                    informe.totalFilas(),
                    informe.nuevas(),
                    informe.totalFilas() - informe.nuevas() - informe.rechazadas().size(),
                    informe.rechazadas().size());
        } finally {
            OrigenContext.limpiar();
            TenantContext.limpiar();
        }
    }
}
