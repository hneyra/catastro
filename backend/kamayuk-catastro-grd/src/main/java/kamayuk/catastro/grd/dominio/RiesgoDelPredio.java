package kamayuk.catastro.grd.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Lo que cruza el lote de un predio a una fecha: sus zonas de riesgo y sus fajas marginales (#5).
 *
 * <p><b>Lleva su fecha</b> (regla 9). Las dos listas caducan —una carta de peligro se sustituye y
 * una resolucion de la ANA se deroga—, asi que una respuesta sin fecha es una respuesta que dentro
 * de un mes es otra y no hay forma de saber cual se dio.
 *
 * <p><b>Ninguna de las dos listas es una decision.</b> Ni siquiera {@link #hayRiesgoNoMitigable},
 * que es lo mas cerca que este contexto llega: dice que existe una zona que no se puede mitigar, no
 * que no se pueda dar una licencia (ADR-0024).
 */
public record RiesgoDelPredio(
        long predioId, LocalDate aLaFecha, List<ZonaDeRiesgo> zonas, List<FajaMarginal> fajas) {

    public RiesgoDelPredio {
        Objects.requireNonNull(aLaFecha, "El riesgo de un predio se lee a una fecha (regla 9)");
        zonas = List.copyOf(zonas);
        fajas = List.copyOf(fajas);
    }

    /** El dato que decide: hay al menos una zona que no se puede mitigar. */
    public boolean hayRiesgoNoMitigable() {
        return zonas.stream().anyMatch(ZonaDeRiesgo::impide);
    }
}
