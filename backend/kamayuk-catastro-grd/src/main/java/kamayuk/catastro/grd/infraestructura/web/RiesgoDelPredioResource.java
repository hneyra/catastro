package kamayuk.catastro.grd.infraestructura.web;

import java.time.LocalDate;
import java.util.List;
import kamayuk.catastro.grd.dominio.RiesgoDelPredio;

/**
 * El riesgo de un lote, como sale por HTTP (#5, AC-3).
 *
 * <p><b>{@code hayRiesgoNoMitigable} va arriba y derivado</b>, no escondido dentro de la lista. Es
 * el dato que decide, y obligarle a quien lee a recorrer las zonas para calcularlo es pedirle que
 * repita en su codigo la unica linea que importa — y esa repeticion es la que se escribe al reves.
 *
 * <p><b>{@code aLaFecha} sale siempre</b> (regla 9). Las zonas caducan: una carta de peligro se
 * sustituye por otra, y una respuesta sin fecha es una respuesta que dentro de un mes es distinta
 * sin que nadie pueda decir cual se dio.
 *
 * <p><b>Ninguna de las dos listas es una decision.</b> Este endpoint dice lo que hay; quien emite
 * una licencia decide (ADR-0024).
 */
public record RiesgoDelPredioResource(
        long predioId,
        LocalDate aLaFecha,
        boolean hayRiesgoNoMitigable,
        List<ZonaDeRiesgoResource> zonas,
        List<FajaMarginalResource> fajasMarginales) {

    static RiesgoDelPredioResource de(RiesgoDelPredio riesgo) {
        return new RiesgoDelPredioResource(
                riesgo.predioId(),
                riesgo.aLaFecha(),
                riesgo.hayRiesgoNoMitigable(),
                riesgo.zonas().stream().map(ZonaDeRiesgoResource::de).toList(),
                riesgo.fajas().stream().map(FajaMarginalResource::de).toList());
    }
}
