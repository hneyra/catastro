package kamayuk.catastro.catastro.infraestructura.web;

import java.time.LocalDate;
import kamayuk.catastro.catastro.aplicacion.ConsultaDeCaracteristicas;
import kamayuk.catastro.dominio.AreaM2;
import org.jspecify.annotations.Nullable;

/**
 * Lo inscrito de un predio a una fecha, tal como sale por HTTP (C-5).
 *
 * <p>{@code areaTerreno} viaja como {@link AreaM2} y no como cadena compuesta a mano: un area tiene
 * <b>un</b> sitio donde se convierte en texto, el serializador de {@code ConfiguracionDeJson}, y
 * tener dos convenciones fue lo que hizo que el mismo predio publicara su area de dos formas segun
 * el modulo (#607).
 *
 * <p>{@code enElPadron} es un campo y no un {@code 404}, y esa es una decision de esta frontera: un
 * {@code 404} tiene que seguir queriendo decir <b>«esa ruta no existe»</b>. Si «este predio no
 * esta» tambien fuera 404, un cliente que pidiera una ruta mal escrita leeria «el predio no esta en
 * el padron» — plausible, falso, y sin nada que lo delate.
 *
 * <p>{@code aLaFecha} es la fecha con la que se resolvio. No es adorno: es lo unico con lo que
 * quien lee puede comprobar que su criterio llego (C-1, desajuste 3).
 */
public record CaracteristicasDelPredioResource(
        long predioId,
        boolean enElPadron,
        @Nullable Long fichaId,
        @Nullable Long fichaEconomicaId,
        @Nullable String uso,
        @Nullable String sectorCodigo,
        @Nullable AreaM2 areaTerreno,
        LocalDate aLaFecha) {

    public static CaracteristicasDelPredioResource de(
            ConsultaDeCaracteristicas.CaracteristicasEnUnaFecha lo) {
        return new CaracteristicasDelPredioResource(
                lo.predioId(),
                lo.enElPadron(),
                lo.fichaId(),
                lo.fichaEconomicaId(),
                lo.uso(),
                lo.sectorCodigo(),
                lo.areaTerreno(),
                lo.aLaFecha());
    }
}
