package kamayuk.catastro.fiscalizacion.dominio;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Cuanto cree el detector, entre 0 y 1.
 *
 * <p><b>{@code BigDecimal} y no {@code double}</b>, y no es por precision monetaria —esto no es
 * dinero—: la regla 1 prohibe {@code double} y {@code float} en todo el sistema, y aqui el motivo
 * propio es otro. El umbral de una campania se compara contra este numero, y con coma flotante
 * {@code 0.7} no es {@code 0.7}: dos candidatos identicos podrian caer a lados distintos del mismo
 * umbral segun como se calculo cada uno. Un detector que no es reproducible no se puede calibrar, y
 * calibrarlo es para lo que sirve la tasa de descarte.
 *
 * <p><b>Y no es una cifra tributaria</b> (regla 5): no entra en ninguna cantidad que se cobre. Lo
 * unico que decide es a quien mira una persona primero.
 */
public record Score(BigDecimal valor) implements Comparable<Score> {

    private static final BigDecimal MAXIMO = BigDecimal.ONE;

    public Score {
        Objects.requireNonNull(valor, "Un candidato necesita su score");
        if (valor.signum() < 0 || valor.compareTo(MAXIMO) > 0) {
            throw new IllegalArgumentException("El score va de 0 a 1: " + valor.toPlainString());
        }
    }

    public static Score de(String texto) {
        return new Score(new BigDecimal(texto));
    }

    /** Si llega al umbral de la campania. El umbral es inclusivo: «al menos tanto». */
    public boolean alcanza(Score umbral) {
        return valor.compareTo(umbral.valor) >= 0;
    }

    @Override
    public int compareTo(Score otro) {
        return valor.compareTo(otro.valor);
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Score score && valor.compareTo(score.valor) == 0;
    }

    @Override
    public int hashCode() {
        return valor.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return valor.toPlainString();
    }
}
