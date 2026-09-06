package kamayuk.catastro.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ningun motivo de {@link ValorizacionDelPredio} desborda la columna en la que su consumidor lo
 * guarda.
 *
 * <h2>El defecto que esta guarda existe para impedir, medido</h2>
 *
 * <p>Al cerrar #8 uno de los diez motivos crecio hasta <b>388 caracteres</b> —el del {@code %
 * actualizacion}, que se convirtio en un parrafo explicando D-11—. La columna en la que {@code
 * rentas} lo guarda es {@code valuacion_predio.motivo varchar(300)} (su {@code
 * V5__valuacion_recibida.sql}), y su ingestor <b>no lo recorta</b>. Medido contra PostgreSQL: el
 * lote de eventos de este repositorio deja de poder ingerirse con «ERROR: value too long for type
 * character varying(300)», y el contraste lo fija —con el lote anterior a #8, la misma prueba pasa
 * en verde—.
 *
 * <p><b>Lo caro no es el rojo: es lo que habria pasado sin el.</b> El buzon de salida entrega y
 * reintenta; un motivo que no cabe no se pierde con un aviso, se queda reintentando para siempre, y
 * lo unico que se ve del otro lado es que las valuaciones de ese predio no llegan.
 *
 * <h2>Por que se lee el fuente y no se ejercitan las diez ramas</h2>
 *
 * <p>Porque lo que hay que vigilar es que <b>ninguna</b> crezca, y una prueba por rama deja fuera
 * la undecima que alguien anada manana. Se leen los literales de cada {@code new SinValorizar(...)}
 * y se mide lo que componen, que es la misma tecnica de {@code RevisorDeCodigoFuente}.
 *
 * <h2>Y lo que esta guarda NO cierra, dicho para que no se lea por mas de lo que es</h2>
 *
 * <p>El 300 esta escrito <b>aqui</b> y la columna vive en otro repositorio: son dos sitios con la
 * misma verdad, que es justo la forma de defecto que C-17 encontro cinco veces. Lo que cierra esto
 * es que el motivo no crezca sin que nadie lo note; lo que NO cierra es que alguien estreche la
 * columna en {@code rentas} y aqui no se entere. Derivarlo del hermano —como hace {@code
 * ContratoConRentasTest} con el contrato— es el arreglo bueno y es otro trabajo.
 */
@DisplayName("#8 — Ningun motivo desborda la columna del consumidor")
class NingunMotivoDesbordaAlConsumidorTest {

    /**
     * {@code valuacion_predio.motivo varchar(300)}, de {@code V5__valuacion_recibida.sql} de {@code
     * rentas}.
     */
    private static final int LARGO_EN_RENTAS = 300;

    /**
     * Lo que las partes NO literales del motivo pueden anadir.
     *
     * <p>Los motivos se componen concatenando literales con expresiones —{@code +
     * insumos.ejercicio()}, {@code + construccion.categoria()}—, que esta medida no ve. Se reserva
     * espacio para ellas en vez de fingir que el literal es el motivo entero: sin el margen, un
     * motivo de 299 caracteres literales mas un ejercicio de cuatro pasaria en verde y reventaria
     * en produccion.
     */
    private static final int MARGEN_DE_LO_INTERPOLADO = 40;

    private static final Pattern SIN_VALORIZAR = Pattern.compile("new SinValorizar\\(");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test
    @DisplayName("los diez motivos caben en varchar(300), con margen para lo interpolado")
    void ningunoDesborda() throws IOException {
        List<String> motivos = motivosDelFuente();

        assertThat(motivos)
                .as(
                        "si esto sale vacio, el recorrido dejo de encontrar los motivos y no vigila nada")
                .hasSizeGreaterThanOrEqualTo(10);

        assertThat(motivos)
                .allSatisfy(
                        motivo ->
                                assertThat(motivo.length())
                                        .as(
                                                "«%s…» mide %d y `rentas` lo guarda en varchar(%d):"
                                                        + " su ingestor no recorta, asi que el hecho no"
                                                        + " se pierde con un aviso — se queda"
                                                        + " reintentando para siempre",
                                                motivo.substring(0, Math.min(70, motivo.length())),
                                                motivo.length(),
                                                LARGO_EN_RENTAS)
                                        .isLessThanOrEqualTo(
                                                LARGO_EN_RENTAS - MARGEN_DE_LO_INTERPOLADO));
    }

    /**
     * El primer argumento de cada {@code new SinValorizar(...)}, con sus literales unidos.
     *
     * <p>Se corta en la coma que separa el motivo de la llave, contando parentesis para no
     * confundirse con los de {@code insumos.ejercicio()}.
     */
    private static List<String> motivosDelFuente() throws IOException {
        Path fuente =
                Path.of("src/main/java/kamayuk/catastro/nucleo/dominio/ValorizacionDelPredio.java");
        String codigo = Files.readString(fuente, StandardCharsets.UTF_8);
        List<String> motivos = new ArrayList<>();
        Matcher inicio = SIN_VALORIZAR.matcher(codigo);
        while (inicio.find()) {
            motivos.add(unirLiterales(primerArgumento(codigo, inicio.end())));
        }
        return motivos;
    }

    /** Desde el parentesis abierto hasta la coma de primer nivel. */
    private static String primerArgumento(String codigo, int desde) {
        int profundidad = 0;
        boolean dentroDeCadena = false;
        for (int i = desde; i < codigo.length(); i++) {
            char c = codigo.charAt(i);
            if (dentroDeCadena) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    dentroDeCadena = false;
                }
            } else if (c == '"') {
                dentroDeCadena = true;
            } else if (c == '(') {
                profundidad++;
            } else if (c == ')') {
                if (profundidad == 0) {
                    return codigo.substring(desde, i);
                }
                profundidad--;
            } else if (c == ',' && profundidad == 0) {
                return codigo.substring(desde, i);
            }
        }
        throw new IllegalStateException(
                "No se cerro el «new SinValorizar(» que empieza en " + desde);
    }

    private static String unirLiterales(String argumento) {
        StringBuilder motivo = new StringBuilder();
        Matcher literal = LITERAL.matcher(argumento);
        while (literal.find()) {
            motivo.append(literal.group(1).replace("\\\"", "\"").replace("\\n", "\n"));
        }
        return motivo.toString();
    }
}
