package kamayuk.catastro.grd.dominio;

import java.time.LocalDate;
import java.util.Objects;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
import org.jspecify.annotations.Nullable;

/**
 * La faja marginal de un cuerpo de agua, delimitada por la Autoridad Nacional del Agua (#5).
 *
 * <p>Es una entidad aparte de {@link ZonaDeRiesgo} y no un fenomeno mas de aquella: la ANA no
 * declara un <b>nivel</b> sino una <b>restriccion</b> de dominio publico hidraulico, con su ancho y
 * su resolucion. Meterla en la otra obligaria a inventarle un nivel y un {@code mitigable} que
 * ningun acto le dio, y ese {@code mitigable} inventado es exactamente el campo del que cuelga la
 * decision.
 *
 * <p>El ancho es una {@link Medida} y no un {@code BigDecimal} desnudo: el dominio no expone
 * decimales sin unidad, y aqui la unidad importa —una faja de 25 se lee igual en metros que en
 * pies—.
 *
 * @param id nulo mientras no se haya guardado
 * @param ancho el que fija la resolucion de la ANA, en metros lineales. <b>NO se deriva del
 *     poligono</b>, por lo mismo que el area del terreno tampoco (ADR-0021): lo fija un acto
 */
public record FajaMarginal(
        @Nullable Long id,
        String codigo,
        String cuerpoDeAgua,
        Medida ancho,
        String fuente,
        String documentoOrigen,
        LocalDate vigenciaDesde,
        @Nullable LocalDate vigenciaHasta,
        Observacion observacion) {

    public FajaMarginal {
        codigo = exigir(codigo, "La faja marginal necesita su codigo");
        cuerpoDeAgua = exigir(cuerpoDeAgua, "La faja marginal necesita su cuerpo de agua");
        fuente = exigir(fuente, "La faja marginal necesita su fuente");
        documentoOrigen = exigir(documentoOrigen, "La faja marginal necesita su documento origen");
        Objects.requireNonNull(ancho, "La faja marginal necesita su ancho");
        Objects.requireNonNull(vigenciaDesde, "La faja marginal necesita desde cuando rige");
        Objects.requireNonNull(
                observacion, "Sin observacion no se guarda una faja marginal (regla 10)");
        if (ancho.magnitud().signum() <= 0) {
            throw new IllegalArgumentException(
                    "El ancho de la faja marginal "
                            + codigo
                            + " tiene que ser positivo: "
                            + ancho.magnitud());
        }
        if (vigenciaHasta != null && vigenciaHasta.isBefore(vigenciaDesde)) {
            throw new IllegalArgumentException(
                    "La faja marginal "
                            + codigo
                            + " termina ("
                            + vigenciaHasta
                            + ") antes de empezar ("
                            + vigenciaDesde
                            + ")");
        }
    }

    private static String exigir(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.strip();
    }
}
