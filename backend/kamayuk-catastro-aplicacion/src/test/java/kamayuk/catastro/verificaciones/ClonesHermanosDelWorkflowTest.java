package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que las pruebas leen del clon hermano es lo que el workflow trae.
 *
 * <h2>Que defecto cierra</h2>
 *
 * <p>El {@code Backend} de este repositorio llevaba rojo con «{@code
 * .../rentas/docs/50-api/anti-entropia/huella-del-lote.json} no existe». El clon de {@code rentas}
 * SI estaba —hermano, con su {@code path:}, como debe— y lo que fallaba era mas fino: su {@code
 * sparse-checkout} nombraba <b>un solo directorio</b>, {@code docs/50-api/contratos-que-consume},
 * escrito cuando era el unico que hacia falta. {@code VectoresDeHuellaTest} (P6) anadio un segundo,
 * y <b>nada ataba esa lista a lo que las pruebas leen de verdad</b>.
 *
 * <h2>Por que esta guarda y no una linea mas en el YAML</h2>
 *
 * <p>Porque la linea mas arregla el rojo de hoy y deja el mecanismo intacto: la proxima prueba de
 * contrato que lea otro directorio del hermano volvera a fallar en CI y solo en CI, con un mensaje
 * sobre un archivo que en local existe. Lo que hay que sujetar no es el nombre de un directorio
 * sino que <b>la lista del workflow cubra lo que las clases de prueba resuelven</b>.
 *
 * <p>Por eso las rutas no se escriben aqui: se le preguntan a las propias clases. La guarda busca
 * en el arbol de pruebas las que extienden una de las dos bases de contrato y les pide su archivo
 * —{@code archivo()} y {@code archivoDelConsumidor()}—, que es exactamente el metodo que la prueba
 * usara al correr. Una lista escrita a mano seria el segundo sitio donde olvidarse de un
 * directorio, que es el defecto que esto cierra.
 */
@DisplayName("C-22 — El workflow trae lo que las pruebas leen del clon hermano")
class ClonesHermanosDelWorkflowTest {

    /** Las dos bases que leen del clon de otro repositorio, con el metodo que da su archivo. */
    private static final Map<String, String> BASES_DE_CONTRATO =
            Map.of(
                    "VectoresDeHuellaTestBase", "archivo",
                    "ContratoConElConsumidorTestBase", "archivoDelConsumidor");

    @Test
    @DisplayName("cada archivo que una prueba resuelve en un hermano cae bajo su sparse-checkout")
    void loQueSeLeeEsLoQueSeTrae() throws Exception {
        Path raiz = raizDelClon();
        Map<String, Set<String>> traido = sparseCheckoutPorRepositorio(raiz);
        List<String[]> leido = loQueLasPruebasResuelven(raiz);

        assertThat(leido)
                .as(
                        "no se encontro ninguna clase de prueba que lea del clon hermano: esta"
                                + " guarda no estaria midiendo nada. O se movieron las pruebas de"
                                + " contrato, o cambio el nombre de sus clases base")
                .isNotEmpty();

        for (String[] par : leido) {
            String hermano = par[0];
            String ruta = par[1];
            Set<String> patrones = traido.get(hermano);

            assertThat(patrones)
                    .as(
                            "una prueba de este repositorio lee «%s/%s» y el workflow no hace"
                                    + " checkout de «%s»: en CI ese archivo no existe y la prueba cae"
                                    + " con «no existe», que se lee como si el otro sistema no lo"
                                    + " hubiera publicado",
                            hermano, ruta, hermano)
                    .isNotNull();

            assertThat(patrones.stream().anyMatch(ruta::startsWith))
                    .as(
                            "el checkout de «%s» trae %s, y esta prueba lee «%s», que no cae bajo"
                                    + " ninguno. El sparse-checkout hay que ampliarlo en"
                                    + " .github/workflows/backend.yml: sin eso el rojo solo aparece en"
                                    + " CI, sobre un archivo que en local esta",
                            hermano, patrones, ruta)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("EL CONTRASTE: y el sparse-checkout que se lee es el del workflow, no una copia")
    void yLoQueSeLeeEsElWorkflow() throws Exception {
        // Sin esto, «todo cubierto» seria compatible con no haber encontrado ningun checkout: un
        // mapa vacio no puede contradecir a nadie si nadie lee nada, y un cambio de formato del
        // YAML dejaria la guarda muda en verde.
        Map<String, Set<String>> traido = sparseCheckoutPorRepositorio(raizDelClon());

        assertThat(traido)
                .as(
                        "no se leyo ningun `sparse-checkout` de .github/workflows/backend.yml. O el"
                                + " workflow dejo de usarlos —y entonces esta guarda sobra— o cambio su"
                                + " forma y hay que enseñarsela")
                .isNotEmpty();
    }

    /** El directorio del clon de este repositorio: el primer ancestro con un `.git`. */
    private static Path raizDelClon() {
        Path actual = Path.of("").toAbsolutePath();
        while (actual != null && !Files.isDirectory(actual.resolve(".git"))) {
            actual = actual.getParent();
        }
        if (actual == null) {
            throw new IllegalStateException(
                    "No se encontro la raiz del clon desde " + Path.of("").toAbsolutePath());
        }
        return actual;
    }

    /**
     * Los `sparse-checkout` del workflow, por repositorio hermano.
     *
     * <p>Se lee el YAML como texto en vez de con un analizador: la forma que interesa son tres
     * claves seguidas dentro de un paso, y meter una dependencia de YAML en el modulo de pruebas
     * por eso seria pagar mas de lo que se compra.
     */
    private static Map<String, Set<String>> sparseCheckoutPorRepositorio(Path raiz)
            throws IOException {
        Path workflow = raiz.resolve(".github/workflows/backend.yml");
        assertThat(workflow).as("no esta el workflow del backend").exists();

        Map<String, Set<String>> porRepositorio = new LinkedHashMap<>();
        String repositorio = null;
        boolean enLista = false;
        for (String cruda : Files.readString(workflow, StandardCharsets.UTF_8).split("\n")) {
            String linea = cruda.strip();
            if (linea.startsWith("- name:")) {
                repositorio = null;
                enLista = false;
            } else if (linea.startsWith("repository:")) {
                String valor = linea.substring("repository:".length()).strip();
                repositorio = valor.contains("/") ? valor.substring(valor.indexOf('/') + 1) : valor;
                enLista = false;
            } else if (linea.startsWith("sparse-checkout:") && repositorio != null) {
                String valor = linea.substring("sparse-checkout:".length()).strip();
                enLista = valor.equals("|") || valor.equals(">");
                if (!enLista && !valor.isEmpty()) {
                    porRepositorio
                            .computeIfAbsent(repositorio, r -> new LinkedHashSet<>())
                            .add(valor);
                } else if (enLista) {
                    porRepositorio.computeIfAbsent(repositorio, r -> new LinkedHashSet<>());
                }
            } else if (enLista) {
                if (linea.isEmpty() || linea.startsWith("#") || linea.contains(":")) {
                    enLista = false;
                } else {
                    porRepositorio.get(repositorio).add(linea);
                }
            }
        }
        return porRepositorio;
    }

    /**
     * Lo que cada prueba de contrato resuelve, como (hermano, ruta dentro de ese hermano).
     *
     * <p>La ruta se le pide al mismo metodo que la prueba usara al correr, y se relativiza contra
     * el directorio que contiene a los clones. Preguntarselo a la clase es lo que impide que esta
     * guarda y las pruebas digan cosas distintas.
     */
    private static List<String[]> loQueLasPruebasResuelven(Path raiz) throws Exception {
        List<String[]> resueltas = new ArrayList<>();
        Path fuentes = raiz.resolve("backend");
        try (Stream<Path> archivos = Files.walk(fuentes)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith("Test.java")).toList()) {
                String texto = Files.readString(archivo, StandardCharsets.UTF_8);
                for (Map.Entry<String, String> base : BASES_DE_CONTRATO.entrySet()) {
                    if (!texto.contains("extends " + base.getKey())) {
                        continue;
                    }
                    Path camino = caminoDe(claseDe(texto, archivo), base.getValue());
                    Path relativa = raiz.getParent().relativize(camino);
                    resueltas.add(
                            new String[] {
                                relativa.getName(0).toString(),
                                relativa.subpath(1, relativa.getNameCount()).toString()
                            });
                }
            }
        }
        return resueltas;
    }

    private static Class<?> claseDe(String texto, Path archivo) throws ClassNotFoundException {
        String paquete = texto.substring(texto.indexOf("package ") + 8, texto.indexOf(";")).strip();
        String nombre = archivo.getFileName().toString().replace(".java", "");
        return Class.forName(paquete + "." + nombre);
    }

    private static Path caminoDe(Class<?> clase, String metodo) throws Exception {
        Object instancia = clase.getDeclaredConstructor().newInstance();
        Method resolutor = clase.getSuperclass().getDeclaredMethod(metodo);
        resolutor.setAccessible(true);
        return (Path) resolutor.invoke(instancia);
    }
}
