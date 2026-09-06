package kamayuk.catastro.grd.infraestructura.web;

import java.time.LocalDate;
import kamayuk.catastro.grd.dominio.ZonaDeRiesgo;
import org.jspecify.annotations.Nullable;

/**
 * Una zona de riesgo que cruza el lote, como sale por HTTP (#5, AC-3).
 *
 * <p><b>{@code mitigable} sale al lado de {@code nivel} y no en su lugar.</b> El nivel dice cuanto
 * peligro hay; {@code mitigable} dice si se puede hacer algo, y <b>es el que decide</b>. Publicar
 * solo el nivel dejaria a quien lo lee creyendo que MUY_ALTO impide siempre, que es falso: una zona
 * MUY ALTO mitigable se construye con su obra de mitigacion.
 *
 * <p><b>Lista blanca, y el poligono NO esta en ella.</b> No es un olvido: la pregunta es «¿que
 * cruza este lote?» y la respuesta es la zona, no su geometria —que puede tener miles de vertices y
 * ya se uso dentro de la consulta—. Quien quiera dibujarla tiene el visor del plano (ADR-0022).
 */
public record ZonaDeRiesgoResource(
        long id,
        String codigo,
        String fenomeno,
        String nivel,
        boolean mitigable,
        String fuente,
        String documentoOrigen,
        LocalDate vigenciaDesde,
        @Nullable LocalDate vigenciaHasta) {

    static ZonaDeRiesgoResource de(ZonaDeRiesgo zona) {
        return new ZonaDeRiesgoResource(
                zona.id() == null ? 0L : zona.id(),
                zona.codigo(),
                zona.fenomeno(),
                zona.nivel().name(),
                zona.mitigable(),
                zona.fuente(),
                zona.documentoOrigen(),
                zona.vigenciaDesde(),
                zona.vigenciaHasta());
    }
}
