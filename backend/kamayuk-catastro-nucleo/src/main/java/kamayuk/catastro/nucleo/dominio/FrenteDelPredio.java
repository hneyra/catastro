package kamayuk.catastro.nucleo.dominio;

import java.time.Instant;
import java.util.Objects;
import kamayuk.catastro.dominio.Medida;
import org.jspecify.annotations.Nullable;

/**
 * El tramo de un predio que da a una via (#7, `V6`, `V10`).
 *
 * <p>Una esquina tiene dos. Es el insumo de los arbitrios —los metros lineales de frontis con que
 * se determina el barrido—, y <b>este sistema no determina ninguno</b>: el importe lo pone {@code
 * rentas}, que es donde ADR-0024 deja la frontera. Aqui no hay ni un importe, ni un factor, ni el
 * nombre de un servicio.
 *
 * <h2>La longitud es una {@link Medida} y no un {@code BigDecimal}</h2>
 *
 * <p>Por lo mismo que {@code AreaM2} y {@code Dinero}: un {@code BigDecimal} suelto en una firma no
 * dice de que es la cifra. Y la unidad importa doble aqui, porque el arbitrio de barrido se
 * determina sobre metros LINEALES y el de recojo sobre metros CUADRADOS: leer unos por otros no
 * falla, cobra otra cosa.
 *
 * @param id nulo mientras no se haya guardado
 * @param viaCodigo y {@code viaNombre}, copiados de la via para que el frente se pueda leer sin una
 *     segunda consulta. No son la verdad de la via: la verdad esta en {@code via}
 * @param geometriaWkt el tramo, en WKT. Nulo cuando la lectura no lo pidio
 * @param longitud los metros lineales. Ver {@link EstadoDeLaLongitud} antes de usarla para nada
 * @param retiro el retiro sobre el frente, cuando la via lo exige
 */
public record FrenteDelPredio(
        @Nullable Long id,
        long predioId,
        long viaId,
        String viaCodigo,
        String viaNombre,
        @Nullable String geometriaWkt,
        Medida longitud,
        EstadoDeLaLongitud estado,
        boolean esPrincipal,
        @Nullable String numeracion,
        @Nullable Medida retiro,
        @Nullable String confirmadoPor,
        @Nullable Instant confirmadoEn) {

    /** La unidad de un frente. Sumar metros lineales con metros cuadrados no significa nada. */
    public static final String UNIDAD = "ML";

    public FrenteDelPredio {
        Objects.requireNonNull(viaCodigo, "Un frente da a una via, y la via tiene codigo");
        Objects.requireNonNull(viaNombre, "Un frente da a una via, y la via tiene nombre");
        Objects.requireNonNull(longitud, "Un frente tiene su longitud");
        Objects.requireNonNull(estado, "Un frente dice si su longitud es propuesta o confirmada");
        if (!UNIDAD.equals(longitud.unidad())) {
            throw new IllegalArgumentException(
                    "La longitud de un frente va en " + UNIDAD + " y esta en " + longitud.unidad());
        }
        if (longitud.esCero()) {
            throw new IllegalArgumentException(
                    "Un frente de cero metros no es un frente: el predio no da a esa via. Un cero"
                            + " aqui se cobraria como un frontis de longitud nula, que es"
                            + " indistinguible de un frente que nadie midio");
        }
        if (retiro != null && !UNIDAD.equals(retiro.unidad())) {
            throw new IllegalArgumentException(
                    "El retiro de un frente va en " + UNIDAD + " y esta en " + retiro.unidad());
        }
        // El mismo invariante que `frente_confirmacion_ck` de `V10`, y va tambien aqui porque el
        // mensaje del CHECK nombra la restriccion y no dice que le falta a la fila.
        boolean confirmada = estado == EstadoDeLaLongitud.CONFIRMADA;
        if (confirmada != (confirmadoPor != null && confirmadoEn != null)) {
            throw new IllegalArgumentException(
                    "Confirmar es un ACTO: lleva quien y cuando, o no es confirmacion. Estado "
                            + estado
                            + " con confirmadoPor "
                            + confirmadoPor
                            + " y confirmadoEn "
                            + confirmadoEn);
        }
    }

    /** Si su longitud sirve para determinar algo, o solo para ir a medirla. */
    public boolean estaConfirmada() {
        return estado == EstadoDeLaLongitud.CONFIRMADA;
    }
}
