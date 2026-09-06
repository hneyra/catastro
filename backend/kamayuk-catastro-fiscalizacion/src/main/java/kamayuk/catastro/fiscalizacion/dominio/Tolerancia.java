package kamayuk.catastro.fiscalizacion.dominio;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Cuanto se admite que dos areas difieran sin sospechar, como fraccion de 1.
 *
 * <p><b>Entra por la campania y no es una constante</b>, y ese es el punto: un padron levantado con
 * GPS de mano y otro con restitucion fotogrametrica no toleran lo mismo, y quien lo sabe es quien
 * lanza la campania. Una constante en el codigo obligaria a desplegar para cambiarla, y entonces no
 * se cambia.
 *
 * <p>Es un tipo y no un {@code BigDecimal} suelto por lo mismo que {@link Score}: una fraccion y un
 * porcentaje se confunden a la vista —0,10 y 10 son la misma tolerancia escritas de dos maneras—, y
 * confundirlos hace que el detector dispare cien veces de mas o cien de menos sin que nada falle.
 * Aqui es siempre <b>fraccion de 1</b>, y el constructor lo sostiene.
 *
 * <p>{@code BigDecimal} y no {@code double}: regla 1, y ademas porque esta cifra se compara contra
 * la diferencia relativa de cada predio — con coma flotante, dos predios identicos podrian caer a
 * lados distintos del mismo umbral.
 */
public record Tolerancia(BigDecimal valor) {

    public Tolerancia {
        Objects.requireNonNull(valor, "La deteccion necesita su tolerancia");
        if (valor.signum() < 0 || valor.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "La tolerancia va de 0 a 1, como fraccion y no como porcentaje: "
                            + valor.toPlainString());
        }
    }

    public static Tolerancia de(String texto) {
        return new Tolerancia(new BigDecimal(texto));
    }

    @Override
    public String toString() {
        return valor.toPlainString();
    }
}
