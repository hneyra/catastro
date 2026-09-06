package kamayuk.catastro.grd.infraestructura.web;

import java.time.LocalDate;
import kamayuk.catastro.grd.dominio.FajaMarginal;
import org.jspecify.annotations.Nullable;

/**
 * Una faja marginal que cruza el lote, como sale por HTTP (#5).
 *
 * <p>{@code anchoM} sale como cadena y con su unidad en el nombre: es la magnitud que fija la
 * resolucion de la ANA, y publicarla como numero de coma flotante la redondearia por el camino —lo
 * mismo que la regla 1 evita para un importe, y aqui un decimal de mas o de menos mueve un lindero
 * (ADR-0021)—.
 */
public record FajaMarginalResource(
        long id,
        String codigo,
        String cuerpoDeAgua,
        String anchoM,
        String fuente,
        String documentoOrigen,
        LocalDate vigenciaDesde,
        @Nullable LocalDate vigenciaHasta) {

    static FajaMarginalResource de(FajaMarginal faja) {
        return new FajaMarginalResource(
                faja.id() == null ? 0L : faja.id(),
                faja.codigo(),
                faja.cuerpoDeAgua(),
                faja.ancho().magnitud().toPlainString(),
                faja.fuente(),
                faja.documentoOrigen(),
                faja.vigenciaDesde(),
                faja.vigenciaHasta());
    }
}
