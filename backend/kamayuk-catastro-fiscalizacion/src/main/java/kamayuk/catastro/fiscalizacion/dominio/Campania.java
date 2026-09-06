package kamayuk.catastro.fiscalizacion.dominio;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * El lote de deteccion: que se busco, cuando y con que umbral (ADR-0035).
 *
 * <p><b>El umbral viaja con la campania y no con la configuracion</b>, y es lo unico que hace
 * comparable la tasa de descarte de dos corridas: la de una campania que detecto con 0,5 y la de
 * otra que detecto con 0,9 no dicen lo mismo, y sin el umbral escrito en la fila las dos cifras se
 * leen juntas y no significan nada.
 *
 * @param id nulo mientras no se haya guardado
 * @param fin nulo mientras la campania siga abierta
 */
public record Campania(
        @Nullable Long id,
        String codigo,
        String nombre,
        EstadoDeCampania estado,
        LocalDate inicio,
        @Nullable LocalDate fin,
        Score umbral) {

    private static final int CODIGO_MAXIMO = 20;
    private static final int NOMBRE_MAXIMO = 160;

    public Campania {
        Objects.requireNonNull(codigo, "La campania necesita su codigo");
        Objects.requireNonNull(nombre, "La campania necesita su nombre");
        Objects.requireNonNull(estado, "La campania necesita su estado");
        Objects.requireNonNull(inicio, "La campania necesita su fecha de inicio");
        Objects.requireNonNull(umbral, "La campania necesita el umbral con que detecto");
        codigo = codigo.strip();
        nombre = nombre.strip();
        if (codigo.isEmpty() || codigo.length() > CODIGO_MAXIMO) {
            throw new IllegalArgumentException(
                    "El codigo de campania va de 1 a " + CODIGO_MAXIMO + ": '" + codigo + "'");
        }
        if (nombre.isEmpty() || nombre.length() > NOMBRE_MAXIMO) {
            throw new IllegalArgumentException(
                    "El nombre de campania va de 1 a " + NOMBRE_MAXIMO + ": '" + nombre + "'");
        }
        if (fin != null && fin.isBefore(inicio)) {
            throw new IllegalArgumentException(
                    "Una campania no puede cerrar antes de abrir: " + inicio + " → " + fin);
        }
    }

    /** Una campania nueva, abierta hoy. */
    public static Campania nueva(String codigo, String nombre, LocalDate inicio, Score umbral) {
        return new Campania(null, codigo, nombre, EstadoDeCampania.ABIERTA, inicio, null, umbral);
    }

    public boolean esNueva() {
        return id == null;
    }

    /**
     * Si admite candidatos nuevos.
     *
     * <p>Una campania cerrada no los admite, y no por orden: sus cifras ya se leyeron. Anadirle un
     * candidato despues cambia una tasa de descarte que alguien pudo haber usado para decidir el
     * umbral de la siguiente.
     */
    public boolean admiteCandidatos() {
        return estado == EstadoDeCampania.ABIERTA;
    }
}
