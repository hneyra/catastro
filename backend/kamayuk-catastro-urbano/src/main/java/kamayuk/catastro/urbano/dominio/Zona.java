package kamayuk.catastro.urbano.dominio;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Una zona de un plan de zonificacion: su codigo, su nombre, su poligono y su vigencia (#4).
 *
 * <p>El poligono viaja como <b>WKT</b> y no como un objeto de geometria, igual que en {@code
 * CatastroRepository.asignarGeometria}: quien sabe de geometria es el motor, y meter una libreria
 * de geometria en el dominio traeria un segundo modelo del mismo poligono que habria que mantener
 * de acuerdo con el primero.
 *
 * @param id nulo mientras la zona no se haya guardado
 * @param vigenciaHasta el ultimo dia que rige —inclusivo—; nulo mientras el plan siga vigente
 */
public record Zona(
        @Nullable Long id,
        String plan,
        String ordenanza,
        String codigo,
        String nombre,
        String geometriaWkt,
        LocalDate vigenciaDesde,
        @Nullable LocalDate vigenciaHasta) {

    /** El largo de {@code zonificacion.codigo}. */
    private static final int LARGO_DEL_CODIGO = 20;

    /** El largo de {@code zonificacion.plan}. */
    private static final int LARGO_DEL_PLAN = 30;

    /** El largo de {@code zonificacion.ordenanza}. */
    private static final int LARGO_DE_LA_ORDENANZA = 60;

    /** El largo de {@code zonificacion.nombre}. */
    private static final int LARGO_DEL_NOMBRE = 120;

    public Zona {
        plan = exigir(plan, "el plan", LARGO_DEL_PLAN);
        ordenanza = exigir(ordenanza, "la ordenanza", LARGO_DE_LA_ORDENANZA);
        codigo = exigir(codigo, "el codigo de zona", LARGO_DEL_CODIGO);
        nombre = exigir(nombre, "el nombre de la zona", LARGO_DEL_NOMBRE);
        geometriaWkt = Objects.requireNonNull(geometriaWkt, "La zona es un poligono").strip();
        if (geometriaWkt.isEmpty()) {
            throw new IllegalArgumentException(
                    "Una zona sin poligono no cubre ningun suelo y no puede decidir nada");
        }
        Objects.requireNonNull(vigenciaDesde, "Toda zona rige desde una fecha (regla 9)");
        if (vigenciaHasta != null && vigenciaHasta.isBefore(vigenciaDesde)) {
            throw new IllegalArgumentException(
                    "La zona no puede dejar de regir ("
                            + vigenciaHasta
                            + ") antes de empezar ("
                            + vigenciaDesde
                            + ")");
        }
    }

    /**
     * ¿Rige esta zona en esa fecha?
     *
     * <p>{@code vigenciaHasta} es <b>inclusiva</b>, como en todo este esquema: el ultimo dia que
     * rige es un dia en que rige. Escribirlo con {@code isBefore} dejaria ese dia fuera y la
     * consulta contestaria «ninguna zona» justo el dia del relevo del plan.
     */
    public boolean rigeEn(LocalDate fecha) {
        return !fecha.isBefore(vigenciaDesde)
                && (vigenciaHasta == null || !fecha.isAfter(vigenciaHasta));
    }

    private static String exigir(String valor, String que, int largo) {
        String limpio = Objects.requireNonNull(valor, "Falta " + que).strip();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("Falta " + que);
        }
        if (limpio.length() > largo) {
            throw new IllegalArgumentException(
                    "Excede "
                            + largo
                            + " caracteres, que es lo que admite la columna: '"
                            + limpio
                            + "'");
        }
        return limpio;
    }
}
