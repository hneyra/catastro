package kamayuk.catastro.catastro.infraestructura.web;

import java.time.LocalDate;
import kamayuk.catastro.catastro.CuotaDeTitularidad;
import kamayuk.catastro.dominio.Porcentaje;
import org.jspecify.annotations.Nullable;

/**
 * La cuota vigente de UN titular sobre UN predio, con el identificador con el que se transfiere
 * (C-5).
 *
 * <h2>Por que la ausencia es un campo nulo y no un 404</h2>
 *
 * <p>«Esta persona no tiene cuota vigente en este predio» es una respuesta legitima —es lo que hace
 * que un registro de transferencia falle diciendo {@code TransferenteSinTitularidad}—, y si se
 * contestara con {@code 404} seria indistinguible de haber pedido una ruta que no existe. Al otro
 * lado de la frontera esas dos cosas se arreglan de maneras opuestas: una es un dato del padron y
 * la otra es un despliegue.
 *
 * <p>{@code predioId} y {@code contribuyenteId} vuelven en la respuesta para que quien la lee pueda
 * comprobar que le contestaron de lo que pregunto. Es el guardia de #298: sin comprobar la fila, el
 * portal le ensenaba a quien tecleaba su DNI la deuda de la primera persona del padron.
 */
public record CuotaDelTitularResource(
        long predioId,
        long contribuyenteId,
        LocalDate aLaFecha,
        boolean tieneCuota,
        long titularidadId,
        @Nullable Porcentaje porcentaje) {

    public static CuotaDelTitularResource sinCuota(
            long predioId, long contribuyenteId, LocalDate aLaFecha) {
        return new CuotaDelTitularResource(predioId, contribuyenteId, aLaFecha, false, 0L, null);
    }

    public static CuotaDelTitularResource de(CuotaDeTitularidad cuota, LocalDate aLaFecha) {
        return new CuotaDelTitularResource(
                cuota.predioId(),
                cuota.contribuyenteId(),
                aLaFecha,
                true,
                cuota.titularidadId(),
                cuota.porcentaje());
    }
}
