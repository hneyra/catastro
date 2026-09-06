package kamayuk.catastro.nucleo.dominio;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Cuando se derivaron por ultima vez los frentes de un predio, y cuantos salieron (#7).
 *
 * <h2>Por que esto existe, y no basta con la lista de frentes</h2>
 *
 * <p>Porque «este predio no da a ninguna calle» y «a este predio no le ha pasado el derivador» son
 * la <b>misma lista vacia</b> y dos problemas distintos: el primero se arregla midiendo en campo,
 * el segundo cargando la cartografia. Hoy no hay ni un poligono en ninguna instalacion, asi que la
 * respuesta que se va a dar siempre al principio es la segunda — y un {@code 200 []} a secas la
 * haria indistinguible de la primera.
 *
 * <p>Es el mismo criterio con el que {@code DetectarSubvaluadores} se niega a devolver cero sin
 * cartografia (#6, AC 8): nadie revisa un cero.
 *
 * @param derivadoEn cuando corrio el derivador sobre ESTE predio
 * @param propuestos cuantos tramos dio el corte. <b>No es cuantas filas se escribieron</b>: volver
 *     a derivar un predio cuyos frentes ya estaban da los mismos tramos y no escribe ninguno, y lo
 *     que esta fila contesta es «cuantos frentes tiene este predio segun el corte», que es estable
 *     entre corridas. Cuantas se escribieron lo dice el informe de la corrida
 * @param motivo por que no salio ninguno; nulo cuando salio alguno. La base lo exige ({@code
 *     frente_derivacion_motivo_ck})
 */
public record DerivacionDeFrentes(
        long predioId, Instant derivadoEn, int propuestos, @Nullable String motivo) {

    public DerivacionDeFrentes {
        Objects.requireNonNull(derivadoEn, "Una derivacion tiene su hora (regla 9)");
        if (propuestos < 0) {
            throw new IllegalArgumentException(
                    "Una derivacion no puede proponer un numero negativo de frentes: "
                            + propuestos);
        }
        if ((propuestos == 0) != (motivo != null)) {
            throw new IllegalArgumentException(
                    "Una derivacion que no propuso ninguno dice por que, y una que propuso alguno"
                            + " no tiene motivo que dar: "
                            + propuestos
                            + " con motivo "
                            + motivo);
        }
    }
}
