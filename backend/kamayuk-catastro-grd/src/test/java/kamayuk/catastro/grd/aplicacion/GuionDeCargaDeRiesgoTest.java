package kamayuk.catastro.grd.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El guion de carga y el proceso que lo atiende son el mismo (#5, AC-7).
 *
 * <p><b>Es la guarda de C-6 aplicada al par que este issue anade</b>, y desde este lado. Lo que C-6
 * midio contra {@code cargar-transferencias-demo.sh} es el modo de fallo que se vigila aqui: un
 * guion que lanza un Job con una propiedad que <b>ningun cargador de su repositorio atiende</b>
 * arranca la aplicacion, <b>no carga ni una fila</b> y sale con codigo 0. Ni un aviso, ni una fila
 * rechazada, ni una linea de log. Su sintoma es la ausencia de sintoma.
 *
 * <p>La guarda de {@code infrastructure} —{@code siembra-de-la-demostracion.test.ts}— cruza los
 * guiones de los tres repositorios con sus {@code @ConditionalOnProperty}, y es la que manda. Esta
 * es su gemela local y no sobra: corre en el build de <b>este</b> repositorio, donde vive el par, y
 * muerde antes de empujar.
 *
 * <p>Se lee el texto del guion y el de la anotacion en vez de comparar contra una lista escrita en
 * la prueba: una lista seria un tercer sitio con la misma verdad, y el tercer sitio es el que se
 * desincroniza.
 */
@DisplayName("#5 — El guion de carga nombra el proceso que este repositorio implementa")
class GuionDeCargaDeRiesgoTest {

    /** La variable de entorno que enciende un cargador, tal como Spring la enlaza. */
    private static final Pattern VARIABLE_DE_ARCHIVO =
            Pattern.compile("\\b[A-Z][A-Z0-9]*_[A-Z0-9]+_ARCHIVO\\b");

    private static final Pattern PROPIEDAD_DEL_CARGADOR =
            Pattern.compile("@ConditionalOnProperty\\(\"([^\"]+)\\.archivo\"\\)");

    @Test
    @DisplayName(
            "cargar-riesgo.sh manda exactamente una variable ..._ARCHIVO, y es la de CargarRiesgo")
    void elGuionMandaLaVariableDeSuProceso() throws IOException {
        String guion = Files.readString(guion("cargar-riesgo.sh"), StandardCharsets.UTF_8);

        Set<String> mandadas = new LinkedHashSet<>();
        Matcher encontradas = VARIABLE_DE_ARCHIVO.matcher(guion);
        while (encontradas.find()) {
            mandadas.add(encontradas.group());
        }

        assertThat(mandadas)
                .as(
                        "un guion que manda dos variables enciende dos cargadores, y uno que no"
                                + " manda ninguna arranca la aplicacion y no carga nada, con exit 0")
                .containsExactly(variableDe(propiedadDeCargarRiesgo()));
    }

    @Test
    @DisplayName("y el archivo que monta es el que existe en ejemplos/")
    void elArchivoQueMontaExiste() throws IOException {
        String guion = Files.readString(guion("cargar-riesgo.sh"), StandardCharsets.UTF_8);

        assertThat(guion)
                .as("el nombre dentro del ConfigMap y la ruta del contenedor tienen que casar")
                .contains("--from-file=riesgo.csv=")
                .contains("value: /datos/riesgo.csv");
        assertThat(raiz().resolve("infra/carga-de-datos/ejemplos/riesgo.csv"))
                .as("el archivo de ejemplo que ensena la forma que el SIG tiene que producir")
                .isRegularFile();
    }

    @Test
    @DisplayName("el guion NO exige es_demostracion, igual que el del plano catastral")
    void elGuionNoExigeDemostracion() throws IOException {
        // SIN los comentarios: el propio guion explica la decision escribiendo la palabra, y
        // contarla pondria esta prueba roja por una frase. Es la misma cautela con que
        // `CatalogoDelSistemaTest` ancla su patron a la arroba de verdad.
        String guion =
                Files.readAllLines(guion("cargar-riesgo.sh"), StandardCharsets.UTF_8).stream()
                        .filter(linea -> !linea.stripLeading().startsWith("#"))
                        .collect(java.util.stream.Collectors.joining("\n"));

        assertThat(guion.toLowerCase(java.util.Locale.ROOT))
                .as(
                        "una carta de peligro es un acto de CENEPRED sobre el territorio de esa"
                                + " municipalidad, no un dato inventado: exigirlo dejaria a una"
                                + " instalacion de verdad sin forma de cargarla (#430)")
                .doesNotContain("--es-demostracion");
    }

    /** La propiedad que {@code CargarRiesgo} declara, leida de su propio fuente. */
    private static String propiedadDeCargarRiesgo() throws IOException {
        Path fuente =
                raiz().resolve(
                                "backend/kamayuk-catastro-grd/src/main/java/kamayuk/catastro/grd/"
                                        + "aplicacion/CargarRiesgo.java");
        Matcher declarada =
                PROPIEDAD_DEL_CARGADOR.matcher(Files.readString(fuente, StandardCharsets.UTF_8));
        assertThat(declarada.find())
                .as(
                        "CargarRiesgo dejo de declarar @ConditionalOnProperty(\"....archivo\"). O"
                                + " se quito —y entonces el runner corre siempre— o cambio de forma"
                                + " y esta guarda dejo de mirar")
                .isTrue();
        return declarada.group(1);
    }

    /**
     * {@code kamayuk.carga-riesgo} -&gt; {@code KAMAYUK_CARGARIESGO_ARCHIVO}, como Spring lo
     * enlaza.
     */
    private static String variableDe(String propiedad) {
        return propiedad.replace("-", "").replace(".", "_").toUpperCase(java.util.Locale.ROOT)
                + "_ARCHIVO";
    }

    private static Path guion(String nombre) {
        return raiz().resolve("infra/carga-de-datos").resolve(nombre);
    }

    private static Path raiz() {
        Path candidato = Path.of("").toAbsolutePath();
        while (candidato != null && !Files.isDirectory(candidato.resolve("infra/carga-de-datos"))) {
            candidato = candidato.getParent();
        }
        if (candidato == null) {
            throw new IllegalStateException(
                    "No se encontro infra/carga-de-datos subiendo desde "
                            + Path.of("").toAbsolutePath()
                            + ". Sin el, esta guarda no lee ningun guion, y «no se pudo comprobar»"
                            + " no es «esta bien»");
        }
        return candidato;
    }
}
