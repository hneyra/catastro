package kamayuk.catastro.grd.dominio;

import java.time.LocalDate;
import java.util.Objects;
import kamayuk.catastro.dominio.Observacion;
import org.jspecify.annotations.Nullable;

/**
 * Un certificado de Inspeccion Tecnica de Seguridad en Edificaciones (#5).
 *
 * <p><b>Siempre vence</b>, y por eso {@code vigenciaHasta} no admite nulo: un certificado eterno no
 * existe, y dejar la fecha vacia lo convertiria en uno. De ahi sale {@link #vigenteA}, que es la
 * unica pregunta que este contexto contesta sobre el.
 *
 * <p><b>Se anula, no se borra</b> (RNF-051, regla 4): el administrado tiene el papel y lo exhibe en
 * el local. Borrarlo en la base deja al papel y al sistema diciendo cosas distintas, y quien tiene
 * el papel gana la discusion. La anulacion es un acto con su fecha y su motivo, y los dos van
 * juntos o no va ninguno.
 *
 * <p><b>La anulacion vale desde su fecha y no hacia atras.</b> Una licencia que se emitio en marzo
 * con un certificado que se anulo en julio se emitio con un certificado que en marzo estaba
 * vigente: preguntar hoy por marzo tiene que dar la respuesta de marzo, o el sistema no puede
 * explicar sus propios actos (regla 9).
 *
 * @param id nulo mientras no se haya guardado
 * @param nivelRiesgo el que el certificado <b>acredita</b>; lo que un giro <b>exige</b> vive en
 *     {@code ciiu.riesgo_itse}, en {@code rentas}, con este mismo vocabulario
 */
public record CertificadoItse(
        @Nullable Long id,
        long predioId,
        String numero,
        NivelDeRiesgo nivelRiesgo,
        ModalidadItse modalidad,
        LocalDate vigenciaDesde,
        LocalDate vigenciaHasta,
        @Nullable LocalDate fechaAnulacion,
        @Nullable String motivoAnulacion,
        Observacion observacion) {

    public CertificadoItse {
        numero = exigir(numero, "El certificado ITSE necesita su numero");
        Objects.requireNonNull(nivelRiesgo, "El certificado ITSE necesita su nivel de riesgo");
        Objects.requireNonNull(modalidad, "El certificado ITSE necesita su modalidad");
        Objects.requireNonNull(
                observacion, "Sin observacion no se guarda un certificado ITSE (regla 10)");
        Objects.requireNonNull(vigenciaDesde, "El certificado ITSE necesita desde cuando rige");
        Objects.requireNonNull(
                vigenciaHasta,
                "El certificado ITSE necesita hasta cuando rige: un certificado siempre vence");
        if (!vigenciaHasta.isAfter(vigenciaDesde)) {
            throw new IllegalArgumentException(
                    "El certificado ITSE "
                            + numero
                            + " vence ("
                            + vigenciaHasta
                            + ") antes de empezar a regir ("
                            + vigenciaDesde
                            + ")");
        }
        if ((fechaAnulacion == null) != (motivoAnulacion == null || motivoAnulacion.isBlank())) {
            throw new IllegalArgumentException(
                    "La anulacion del certificado ITSE "
                            + numero
                            + " va con su fecha y su motivo, o no va: una anulacion sin por que no"
                            + " es un acto (regla 10)");
        }
        motivoAnulacion = motivoAnulacion == null ? null : motivoAnulacion.strip();
    }

    /**
     * Si el certificado estaba vigente ese dia.
     *
     * <p>Los dos extremos entran: un certificado que rige «del 1 de enero al 31 de diciembre» esta
     * vigente el 31 de diciembre. La fecha llega como argumento y no del reloj (regla 6).
     */
    public boolean vigenteA(LocalDate fecha) {
        Objects.requireNonNull(fecha, "«Vigente» no es una afirmacion: es «vigente a una fecha»");
        if (fecha.isBefore(vigenciaDesde) || fecha.isAfter(vigenciaHasta)) {
            return false;
        }
        return fechaAnulacion == null || fecha.isBefore(fechaAnulacion);
    }

    private static String exigir(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.strip();
    }
}
