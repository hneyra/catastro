package kamayuk.catastro.fiscalizacion.infraestructura.web;

import java.time.LocalDate;
import kamayuk.catastro.fiscalizacion.dominio.Acta;

/**
 * El acta, como sale por HTTP.
 *
 * <p>Sin importe y sin tributo: dice que se hallo, quien y cuando. Lo que se cobre lo decide {@code
 * rentas} (ADR-0024).
 */
public record ActaResource(
        long id,
        String numero,
        long hallazgoId,
        LocalDate fecha,
        String inspector,
        String detalle) {

    public static ActaResource de(Acta acta) {
        return new ActaResource(
                acta.id() == null ? 0 : acta.id(),
                acta.numero(),
                acta.hallazgoId(),
                acta.fecha(),
                acta.inspector(),
                acta.detalle());
    }
}
