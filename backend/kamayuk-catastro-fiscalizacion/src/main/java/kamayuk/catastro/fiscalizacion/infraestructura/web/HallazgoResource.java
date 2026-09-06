package kamayuk.catastro.fiscalizacion.infraestructura.web;

import java.time.LocalDate;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import org.jspecify.annotations.Nullable;

/**
 * Un hallazgo, como sale por HTTP.
 *
 * <p><b>Lleva {@code fichaId} y {@code verificadoEn}</b>, y las dos por el mismo motivo: una cifra
 * de este recurso —el exceso— solo significa algo si se sabe contra que version se comparo y
 * cuando. Es la regla 9 aplicada a una superficie: la ficha se versiona, asi que una diferencia sin
 * su version es una diferencia que manana es otra.
 *
 * <p><b>Ni un importe.</b> Dos superficies y su resta. Lo que se cobre lo decide {@code rentas}
 * (ADR-0024).
 *
 * <p><b>Las tres areas van tipadas como {@link AreaM2}</b> y no compuestas a mano: quien las
 * escribe es el serializador de {@code ConfiguracionDeJson}, y escribe la cifra sola. Componerlas
 * aqui seria una segunda convencion para lo mismo, y con dos el sistema acaba publicando el area
 * del mismo predio de dos formas segun a quien se le pregunte (#607).
 *
 * @param excesoVerificado lo verificado menos lo inscrito; <b>nulo</b> cuando no hay con que
 *     comparar —un omiso catastral no tiene ficha— o cuando lo verificado no supera lo inscrito.
 *     Nulo y no cero: cero significaria que coinciden
 */
public record HallazgoResource(
        long id,
        long candidatoId,
        String clase,
        @Nullable Long predioId,
        @Nullable Long fichaId,
        @Nullable AreaM2 areaDeLaFicha,
        AreaM2 areaVerificada,
        @Nullable AreaM2 excesoVerificado,
        String inspector,
        LocalDate verificadoEn,
        String estado) {

    public static HallazgoResource de(Hallazgo hallazgo) {
        return new HallazgoResource(
                hallazgo.id() == null ? 0 : hallazgo.id(),
                hallazgo.candidatoId(),
                hallazgo.clase().name(),
                hallazgo.predioId(),
                hallazgo.fichaId(),
                hallazgo.areaDeLaFicha(),
                hallazgo.areaVerificada(),
                hallazgo.excesoVerificado().orElse(null),
                hallazgo.inspector(),
                hallazgo.verificadoEn(),
                hallazgo.estado().name());
    }
}
