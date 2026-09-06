package kamayuk.catastro.grd.dominio;

import java.time.LocalDate;
import java.util.Objects;
import kamayuk.catastro.dominio.Observacion;
import org.jspecify.annotations.Nullable;

/**
 * Un poligono de la carta de peligro que la municipalidad recibio (#5).
 *
 * <p><b>{@code mitigable} es el dato que decide, no {@code nivel}.</b> Una zona MUY ALTO mitigable
 * no impide nada: se ejecuta la obra de mitigacion y se construye. Una no mitigable si. Por eso es
 * un {@code boolean} obligatorio y no un campo opcional que se pueda dejar sin llenar.
 *
 * <p>El poligono no viaja en este registro: se queda en la base. Lo que la lectura devuelve es
 * <b>que zonas cruzan el lote</b>, y para eso el poligono ya se uso —dentro de la misma sentencia,
 * detras del marco (ADR-0034 regla 2)—.
 *
 * @param id nulo mientras no se haya guardado
 * @param fenomeno inundacion, sismo, deslizamiento, huaico... El catalogo es de CENEPRED
 * @param vigenciaHasta nula mientras la carta siga vigente
 * @param observacion el «por que» de quien la cargo. Sin ella no se guarda (regla 10, RNF-052)
 */
public record ZonaDeRiesgo(
        @Nullable Long id,
        String codigo,
        String fenomeno,
        NivelDeRiesgo nivel,
        boolean mitigable,
        String fuente,
        String documentoOrigen,
        LocalDate vigenciaDesde,
        @Nullable LocalDate vigenciaHasta,
        Observacion observacion) {

    public ZonaDeRiesgo {
        codigo = exigir(codigo, "La zona de riesgo necesita su codigo");
        fenomeno = exigir(fenomeno, "La zona de riesgo necesita su fenomeno");
        fuente = exigir(fuente, "La zona de riesgo necesita su fuente");
        documentoOrigen = exigir(documentoOrigen, "La zona de riesgo necesita su documento origen");
        Objects.requireNonNull(nivel, "La zona de riesgo necesita su nivel");
        Objects.requireNonNull(vigenciaDesde, "La zona de riesgo necesita desde cuando rige");
        Objects.requireNonNull(
                observacion, "Sin observacion no se guarda una zona de riesgo (regla 10)");
        if (vigenciaHasta != null && vigenciaHasta.isBefore(vigenciaDesde)) {
            throw new IllegalArgumentException(
                    "La zona de riesgo "
                            + codigo
                            + " termina ("
                            + vigenciaHasta
                            + ") antes de empezar ("
                            + vigenciaDesde
                            + ")");
        }
    }

    /** Lo que de verdad impide: riesgo que no se puede mitigar. */
    public boolean impide() {
        return !mitigable;
    }

    private static String exigir(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.strip();
    }
}
