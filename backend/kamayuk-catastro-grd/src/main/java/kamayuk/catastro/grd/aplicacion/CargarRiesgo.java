package kamayuk.catastro.grd.aplicacion;

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
 * Carga la carta de peligro y la faja marginal de una municipalidad ya implantada (#5, AC-7).
 *
 * <p>Mismo patron que {@code CargarPredios}: perfil {@code batch}, un proceso de vida corta sin
 * servidor web, que fija a mano los dos contextos que en una peticion salen del token.
 *
 * <p><b>El perfil no es decoracion</b>: sin el, este runner correria tambien en el proceso web y
 * ese contenedor tendria que conocer una ruta de archivo que no le toca. Lo vigila {@code
 * TODA_SIEMBRA_CORRE_SOLO_EN_EL_PERFIL_BATCH}.
 *
 * <p>El informe se registra completo, con cada fila rechazada y su motivo. Una fila rechazada no
 * aborta la corrida, pero tiene que quedar visible en el log de quien la corrio: una carga que mete
 * la mitad de los poligonos y sale con codigo 0 sin decirlo es el modo de fallo que C-6 midio.
 */
@Component
@Profile("batch")
@ConditionalOnProperty("kamayuk.carga-riesgo.archivo")
@EnableConfigurationProperties(DatosDeCargaRiesgo.class)
public class CargarRiesgo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CargarRiesgo.class);

    private final ImportarCartaDeRiesgo importar;
    private final DatosDeCargaRiesgo datos;

    public CargarRiesgo(ImportarCartaDeRiesgo importar, DatosDeCargaRiesgo datos) {
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
                log.warn(
                        "Poligono de la fila {} rechazado: {}",
                        rechazada.fila(),
                        rechazada.motivo());
            }
            log.info(
                    "Carta de riesgo de la municipalidad {} cargada desde {}: {} fila(s) leidas,"
                            + " {} poligono(s) nuevo(s), {} rechazada(s)",
                    datos.municipalidadId(),
                    datos.archivo(),
                    informe.totalFilas(),
                    informe.nuevas(),
                    informe.rechazadas().size());
        } finally {
            OrigenContext.limpiar();
            TenantContext.limpiar();
        }
    }
}
