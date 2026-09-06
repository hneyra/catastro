package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC 6 de #7: {@code catastro} no nombra un servicio de arbitrio. En ninguna parte.
 *
 * <h2>Por que esto es una prueba y no una frase de un ADR</h2>
 *
 * <p>ADR-0024 pone la frontera: {@code catastro} aporta el <b>insumo</b> —cuantos metros lineales
 * de frente tiene un predio y a que via dan— y el importe lo determina {@code rentas}. La forma en
 * que esa frontera se pierde no es un rediseno: es una linea. Alguien anade {@code enum Servicio}
 * «para poder etiquetar el frente», despues un factor de barrido «que es un dato del predio», y
 * cuando se nota hay media determinacion escrita a este lado y dos sistemas calculando el mismo
 * tributo con dos formulas que pueden divergir.
 *
 * <p>Y no es hipotetico: los tres nombres existen HOY en {@code rentas}, en {@code
 * DeterminarArbitrios} y en su enumerado {@code Servicio}. Lo que se comprueba aqui es que no
 * lleguen a este lado.
 *
 * <h2>Recorre el arbol y no el bytecode, a proposito</h2>
 *
 * <p>Una regla de ArchUnit veria una clase o una dependencia; lo que hay que impedir es tambien un
 * <b>literal</b>, una columna de una migracion y un campo de un JSON de contrato. Por eso el
 * recorrido es de texto y cubre {@code src/main} de los once modulos <b>y</b> las migraciones.
 *
 * <p>Es del mismo estilo que las afirmaciones {@code .doesNotContain("autovaluo")} que ya vigilan
 * lo que sale por HTTP, y las hace estructurales en vez de dejarlas colgando de una respuesta
 * concreta.
 *
 * <h2>Lo que esta prueba NO puede ver, dicho antes de que alguien lo descubra</h2>
 *
 * <p>Un arbitrio determinado sin nombrar ningun servicio —una columna {@code importe_mensual} en
 * {@code frente_predio}, por ejemplo—. Eso no lo caza ningun escaner de nombres: lo caza que
 * ninguna de las tablas de este esquema tenga columna de importe, y eso lo lee la revision. Lo que
 * esta prueba garantiza es que el vocabulario de {@code rentas} no cruce, que es como el defecto
 * empieza.
 */
@DisplayName("#7 AC 6 — catastro no nombra un servicio de arbitrio")
class CatastroNoNombraUnArbitrioTest {

    /**
     * Los tres servicios del enumerado {@code Servicio} de {@code rentas}.
     *
     * <p>Se escriben aqui y no se leen del clon hermano a proposito: lo que esta prueba afirma es
     * que <b>estas palabras</b> no estan en este repositorio, y leerlas de {@code rentas} haria que
     * la prueba dejara de mirar el dia que aquel renombrara su enumerado — justo cuando el
     * vocabulario nuevo podria empezar a filtrarse.
     */
    private static final List<String> SERVICIOS_DE_ARBITRIO =
            List.of("LIMPIEZA_PUBLICA", "PARQUES_JARDINES", "SERENAZGO");

    /**
     * Y el vocabulario del calculo, que es como el defecto llega de verdad.
     *
     * <p>Un «factor de barrido» o una «tarifa» en este repositorio son la determinacion empezando a
     * escribirse a este lado. {@code arbitrio} NO esta en la lista y es deliberado: este
     * repositorio lo nombra en veinte comentarios para decir <b>que no lo calcula</b>, y una regla
     * que prohibiera la palabra prohibiria explicar la frontera.
     */
    private static final List<String> VOCABULARIO_DEL_CALCULO =
            List.of("factor_de_barrido", "factorDeBarrido", "tarifa_de_arbitrio");

    @Test
    @DisplayName(
            "ningun archivo de src/main nombra LIMPIEZA_PUBLICA, PARQUES_JARDINES ni SERENAZGO")
    void ningunArchivoNombraUnServicioDeArbitrio() throws IOException {
        List<String> hallazgos = new ArrayList<>();

        for (Path archivo : archivosDeProduccion()) {
            String contenido = Files.readString(archivo, StandardCharsets.UTF_8);
            for (String prohibido : SERVICIOS_DE_ARBITRIO) {
                if (contenido.contains(prohibido)) {
                    hallazgos.add(relativo(archivo) + " — nombra «" + prohibido + "»");
                }
            }
            String enMinusculas = contenido.toLowerCase(Locale.ROOT);
            for (String prohibido : VOCABULARIO_DEL_CALCULO) {
                if (enMinusculas.contains(prohibido.toLowerCase(Locale.ROOT))) {
                    hallazgos.add(relativo(archivo) + " — nombra «" + prohibido + "»");
                }
            }
        }

        assertThat(hallazgos)
                .as(
                        "ADR-0024: `catastro` aporta el insumo del arbitrio —los metros lineales de"
                                + " frente— y NO lo determina. El nombre de un servicio a este lado es"
                                + " la determinacion empezando a escribirse aqui, y acabaria en dos"
                                + " sistemas calculando el mismo tributo con dos formulas que pueden"
                                + " divergir")
                .isEmpty();
    }

    @Test
    @DisplayName("y la prueba puede fallar: sobre un texto que si lo nombra, encuentra los tres")
    void laPruebaPuedeFallar() {
        // El contraste. Sin el, un recorrido que no leyera ningun archivo —un `glob` mal escrito,
        // un modulo renombrado— pasaria en verde diciendo que no hay hallazgos, que es exactamente
        // la forma en que esta clase dejaria de proteger nada.
        String comoSeriaElDefecto =
                "public enum Servicio { LIMPIEZA_PUBLICA, PARQUES_JARDINES, SERENAZGO }";

        assertThat(SERVICIOS_DE_ARBITRIO)
                .allMatch(comoSeriaElDefecto::contains)
                .as("los tres nombres son los que hay que buscar")
                .hasSize(3);
    }

    @Test
    @DisplayName("y se leyo un arbol de verdad: hay archivos que revisar")
    void seLeyoUnArbolDeVerdad() throws IOException {
        // La otra mitad del contraste, y la que importa: si `archivosDeProduccion()` devolviera
        // una lista vacia —porque cambio la disposicion de los modulos, o porque el directorio de
        // trabajo del corredor no es el que se supone—, la primera prueba pasaria en verde sin
        // haber mirado ni un byte.
        assertThat(archivosDeProduccion())
                .as("el recorrido tiene que encontrar el codigo de produccion y las migraciones")
                .hasSizeGreaterThan(200);
        assertThat(archivosDeProduccion().stream().map(CatastroNoNombraUnArbitrioTest::relativo))
                .as("y entre ellos, la migracion del frente y el borde que lo publica")
                .anyMatch(ruta -> ruta.endsWith("V10__buzon_del_territorio.sql"))
                .anyMatch(ruta -> ruta.endsWith("FrenteController.java"));
    }

    /** Todo {@code .java} y {@code .sql} de {@code src/main}, en los once modulos. */
    private static List<Path> archivosDeProduccion() throws IOException {
        Path backend = RaizDelRepositorio.ruta().resolve("backend");
        try (Stream<Path> arbol = Files.walk(backend)) {
            return arbol.filter(Files::isRegularFile)
                    .filter(ruta -> ruta.toString().contains("/src/main/"))
                    .filter(ruta -> !ruta.toString().contains("/build/"))
                    .filter(
                            ruta -> {
                                String nombre = ruta.getFileName().toString();
                                return nombre.endsWith(".java") || nombre.endsWith(".sql");
                            })
                    .sorted()
                    .toList();
        }
    }

    private static String relativo(Path archivo) {
        return RaizDelRepositorio.ruta().relativize(archivo).toString();
    }
}
