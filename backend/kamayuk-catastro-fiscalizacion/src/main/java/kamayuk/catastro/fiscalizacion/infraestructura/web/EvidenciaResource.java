package kamayuk.catastro.fiscalizacion.infraestructura.web;

import java.time.Instant;
import kamayuk.catastro.fiscalizacion.dominio.Evidencia;
import org.jspecify.annotations.Nullable;

/**
 * Una evidencia, como sale por HTTP.
 *
 * <p><b>Los dos relojes salen los dos</b>, y ahi esta el punto: quien mira la evidencia de un acta
 * tiene que poder ver que la foto se tomo a las nueve y entro a las seis de la tarde. Publicar uno
 * solo —o su diferencia ya calculada— dejaria la captura sin auditar, que es exactamente lo que
 * ADR-0035 punto 3 evita al separarlos.
 *
 * @param desfaseEnSegundos {@code recibidoEn - capturadoEn}. Va ademas de los dos, no en su lugar:
 *     es lo que se ordena y se filtra en una grilla. Puede ser <b>negativo</b>, y eso significa que
 *     el reloj del aparato va adelantado — tambien es un dato
 */
public record EvidenciaResource(
        long id,
        long hallazgoId,
        String tipo,
        String sha256,
        String ruta,
        Instant capturadoEn,
        Instant recibidoEn,
        long desfaseEnSegundos,
        @Nullable String dispositivo) {

    public static EvidenciaResource de(Evidencia evidencia) {
        return new EvidenciaResource(
                evidencia.id() == null ? 0 : evidencia.id(),
                evidencia.hallazgoId(),
                evidencia.tipo().name(),
                evidencia.huella().valor(),
                evidencia.ruta(),
                evidencia.capturadoEn(),
                evidencia.recibidoEn(),
                evidencia.desfaseDeLosRelojes().toSeconds(),
                evidencia.dispositivo());
    }
}
