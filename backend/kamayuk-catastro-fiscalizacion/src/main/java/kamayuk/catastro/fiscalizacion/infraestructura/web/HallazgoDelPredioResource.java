package kamayuk.catastro.fiscalizacion.infraestructura.web;

import java.time.LocalDate;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.fiscalizacion.dominio.HallazgoDelPredio;
import org.jspecify.annotations.Nullable;

/**
 * Un hallazgo de un predio, como sale por HTTP (#17, AC-1).
 *
 * <p>Es {@link HallazgoResource} <b>mas su campania y su acta</b>. Se publica aparte y no ampliando
 * aquel, por lo mismo que {@link HallazgoDelPredio} es un registro propio: en la pagina de una
 * campania el {@code campaniaId} seria el mismo en las cuatro mil filas —o sea ruido— y el acta
 * obligaria a una consulta por fila. Aqui las dos son la respuesta: quien pregunta por un predio
 * quiere saber en que campania se le hallo algo y si eso llego a acto.
 *
 * <p><b>{@code predioId} no es anulable aqui, y esa es la afirmacion</b>: esta ruta, por
 * construccion, no puede devolver un {@code OMISO_CATASTRAL} —{@code hallazgo_contraste_check} de
 * {@code V9} le exige {@code predio_id} nulo—. Quien lea «los hallazgos del predio» va a suponer
 * que estan todos los de la campania que le tocan, y los omisos no estan en ninguno.
 *
 * <p><b>Ni un importe.</b> Dos superficies y su resta. Lo que se cobre lo decide {@code rentas}
 * (ADR-0024).
 *
 * @param excesoVerificado lo verificado menos lo inscrito; <b>nulo</b> cuando lo verificado no
 *     supera lo inscrito. Nulo y no cero: cero significaria que coinciden
 * @param acta el acta levantada, o nulo si todavia no hay ninguna
 */
public record HallazgoDelPredioResource(
        long id,
        long candidatoId,
        long campaniaId,
        String campaniaCodigo,
        String clase,
        long predioId,
        @Nullable Long fichaId,
        @Nullable AreaM2 areaDeLaFicha,
        AreaM2 areaVerificada,
        @Nullable AreaM2 excesoVerificado,
        String inspector,
        LocalDate verificadoEn,
        String estado,
        @Nullable ActaResource acta) {

    public static HallazgoDelPredioResource de(HallazgoDelPredio delPredio) {
        var hallazgo = delPredio.hallazgo();
        Long predioId = hallazgo.predioId();
        return new HallazgoDelPredioResource(
                hallazgo.id() == null ? 0 : hallazgo.id(),
                hallazgo.candidatoId(),
                delPredio.campaniaId(),
                delPredio.campaniaCodigo(),
                hallazgo.clase().name(),
                // El constructor compacto de HallazgoDelPredio ya rechazo el nulo: aqui no puede
                // serlo, y publicarlo anulable invitaria a leer esta ruta como si trajera omisos.
                predioId == null ? 0 : predioId,
                hallazgo.fichaId(),
                hallazgo.areaDeLaFicha(),
                hallazgo.areaVerificada(),
                hallazgo.excesoVerificado().orElse(null),
                hallazgo.inspector(),
                hallazgo.verificadoEn(),
                hallazgo.estado().name(),
                delPredio.actaLevantada().map(ActaResource::de).orElse(null));
    }
}
