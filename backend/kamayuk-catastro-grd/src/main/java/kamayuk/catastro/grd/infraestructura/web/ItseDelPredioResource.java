package kamayuk.catastro.grd.infraestructura.web;

import java.time.LocalDate;
import java.util.List;
import kamayuk.catastro.grd.dominio.CertificadoItse;
import org.jspecify.annotations.Nullable;

/**
 * El ITSE de un predio a una fecha, como sale por HTTP (#5, AC-4).
 *
 * <p><b>{@code aLaFecha} sale siempre, y es la mitad de la respuesta.</b> Es la regla 9 aplicada a
 * un certificado: lo que se contesta no es «tiene ITSE» sino «tiene ITSE el 12 de marzo de 2026».
 * Sin la fecha dentro, quien guarde esta respuesta —o la imprima— no puede decir despues a que dia
 * correspondia, y un certificado vence.
 *
 * <p><b>{@code vigentes} nunca trae un vencido</b>, y hay una prueba que inserta uno vencido y
 * comprueba que no sale. La lista vacia es una respuesta y no una ausencia: dice que ese dia no
 * habia ninguno.
 */
public record ItseDelPredioResource(
        long predioId, LocalDate aLaFecha, List<CertificadoItseResource> vigentes) {

    static ItseDelPredioResource de(
            long predioId, LocalDate aLaFecha, List<CertificadoItse> certificados) {
        return new ItseDelPredioResource(
                predioId,
                aLaFecha,
                certificados.stream().map(CertificadoItseResource::de).toList());
    }

    /**
     * Un certificado.
     *
     * <p>{@code nivelRiesgo} es el que el certificado <b>acredita</b>. Lo que un giro <b>exige</b>
     * es de {@code rentas} ({@code ciiu.riesgo_itse}) y se escribe con este mismo vocabulario, para
     * que compararlos no necesite traducir.
     */
    public record CertificadoItseResource(
            long id,
            String numero,
            String nivelRiesgo,
            String modalidad,
            LocalDate vigenciaDesde,
            LocalDate vigenciaHasta,
            @Nullable LocalDate fechaAnulacion) {

        static CertificadoItseResource de(CertificadoItse certificado) {
            return new CertificadoItseResource(
                    certificado.id() == null ? 0L : certificado.id(),
                    certificado.numero(),
                    certificado.nivelRiesgo().name(),
                    certificado.modalidad().name(),
                    certificado.vigenciaDesde(),
                    certificado.vigenciaHasta(),
                    certificado.fechaAnulacion());
        }
    }
}
