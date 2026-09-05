package kamayuk.catastro.nucleo.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import kamayuk.catastro.dominio.Dinero;
import org.jspecify.annotations.Nullable;

/**
 * La valuacion de un predio en un ejercicio: el HECHO SELLADO de ADR-0027 §1.
 *
 * <h2>No es «el valor del predio»</h2>
 *
 * <p>Es la valuacion de un predio EN UN EJERCICIO y A UNA FECHA DE CORTE, con la identidad de todos
 * sus insumos dentro: que ficha regia, que conjunto de parametros se uso, que version del catalogo
 * de reglas corrio, cuales corrieron y con que titulares. Es la regla 9 aplicada al autovaluo:
 * recalcular 2026 en 2036 tiene que dar el mismo centimo, y para eso hay que saber con que se
 * calculo, no volver a resolverlo.
 *
 * <h2>O las cuatro cifras, o el motivo. Nunca las dos cosas y nunca ninguna</h2>
 *
 * <p>El invariante esta aqui y tambien en {@code valuacion_predio_cifra_o_motivo_ck} de `V5` de
 * `rentas`, y las dos veces por lo mismo: <b>un cero es indistinguible de un predio que de verdad
 * no vale nada</b>. Es el defecto que #48 midio con la licencia de obra que salia con «valor de
 * obra 0,00» — plausible, impreso, y base de lo que se liquido.
 *
 * <p><b>Hoy todas las filas llevan motivo</b>, y ese es el estado real del sistema, no una
 * limitacion de esta clase: ver {@link ValorizacionDelPredio}.
 */
public record ValuacionDelPredio(
        long predioId,
        int ejercicio,
        LocalDate fechaDeCorte,
        @Nullable Dinero valorTerreno,
        @Nullable Dinero valorConstruccion,
        @Nullable Dinero valorObras,
        @Nullable Dinero valorDelPredio,
        @Nullable String motivo,
        @Nullable String llaveQueFalta,
        @Nullable Long fichaCatastralId,
        long conjuntoId,
        String reglasVersion,
        String reglasAplicadas,
        List<CuotaDeTitular> titulares) {

    public ValuacionDelPredio {
        Objects.requireNonNull(fechaDeCorte, "Toda valuacion dice a que fecha esta (regla 9)");
        Objects.requireNonNull(reglasVersion, "Toda valuacion dice que catalogo de reglas corrio");
        Objects.requireNonNull(reglasAplicadas, "Toda valuacion dice que reglas corrieron");
        titulares =
                List.copyOf(Objects.requireNonNull(titulares, "La lista de titulares, o vacia"));
        boolean hayCifra = valorDelPredio != null;
        boolean hayMotivo = motivo != null;
        if (hayCifra == hayMotivo) {
            throw new IllegalArgumentException(
                    "Una valuacion trae las cifras O el motivo por el que no se pudo valorizar,"
                            + " nunca las dos y nunca ninguna (ADR-0027 §1, #48). Predio "
                            + predioId
                            + ", ejercicio "
                            + ejercicio
                            + ": valor="
                            + valorDelPredio
                            + ", motivo="
                            + motivo);
        }
        if (hayCifra && (valorTerreno == null || valorConstruccion == null || valorObras == null)) {
            throw new IllegalArgumentException(
                    "Una valuacion con cifra trae el desglose entero: sin el, el total no se puede"
                            + " explicar ni recalcular. Predio "
                            + predioId);
        }
        if (motivo != null && motivo.isBlank()) {
            throw new IllegalArgumentException(
                    "«No se pudo valorizar» sin decir por que es un cero con otra forma. Predio "
                            + predioId);
        }
        if (hayCifra && llaveQueFalta != null) {
            throw new IllegalArgumentException(
                    "Una valuacion con cifras no puede nombrar una llave que falta: eso diria a la"
                            + " vez que se valorizo y que no se pudo. Predio "
                            + predioId);
        }
    }

    /** Si trae cifras. Hoy, ninguna: ver {@link ValorizacionDelPredio}. */
    public boolean seValorizo() {
        return valorDelPredio != null;
    }
}
