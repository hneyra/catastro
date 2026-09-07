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
 * <h2>Las dos mitades del contraste, y por que hacen falta las dos (#29)</h2>
 *
 * <p>Un escaner puede fallar de dos maneras opuestas y ninguna prueba caza las dos. Puede <b>no
 * encontrar el arbol</b> —un {@code glob} mal escrito, un modulo renombrado, un directorio de
 * trabajo que no es el que se supone— y entonces «no hay hallazgos» es cierto sobre el conjunto
 * vacio; eso lo caza {@link #seLeyoUnArbolDeVerdad}. Y puede <b>no reconocer el defecto</b> —una
 * palabra mal escrita en la lista, un {@code contains} que se volvio {@code equals}— y entonces el
 * recorrido lee los cuatrocientos archivos y no ve nada; eso lo caza {@link #laPruebaPuedeFallar},
 * corriendo <b>el mismo recorrido y el mismo escaner</b> sobre una muestra que si lo nombra.
 *
 * <p><b>Hasta #29 la segunda no existia.</b> Lo que habia comparaba tres literales contra otro
 * literal escrito dos lineas mas arriba: no llamaba al recorrido, no leia ningun archivo y no podia
 * ponerse roja por ningun cambio en el escaner. Medido: con {@code /src/main/} cambiado por un
 * segmento que no existe, aquella prueba pasaba en VERDE.
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

    /**
     * Donde vive la muestra que este escaner tiene que encontrar.
     *
     * <p><b>Fuera de {@code backend/}</b>, y no por gusto: el filtro que hay que ejercer es {@code
     * /src/main/}, asi que la muestra esta bajo un {@code src/main/} suyo — y cualquier sitio bajo
     * {@code backend/} la meteria en el recorrido de PRODUCCION de aqui abajo y en el de los cinco
     * escaneres de {@code comun-verificaciones}, que recorren {@code backend/} buscando exactamente
     * eso. La muestra pondria roja la guarda que existe para demostrar.
     */
    private static final String MUESTRA = "muestras/nombra-un-arbitrio";

    @Test
    @DisplayName(
            "ningun archivo de src/main nombra LIMPIEZA_PUBLICA, PARQUES_JARDINES ni SERENAZGO")
    void ningunArchivoNombraUnServicioDeArbitrio() throws IOException {
        assertThat(hallazgosEn(archivosDeProduccion()))
                .as(
                        "ADR-0024: `catastro` aporta el insumo del arbitrio —los metros lineales de"
                                + " frente— y NO lo determina. El nombre de un servicio a este lado es"
                                + " la determinacion empezando a escribirse aqui, y acabaria en dos"
                                + " sistemas calculando el mismo tributo con dos formulas que pueden"
                                + " divergir")
                .isEmpty();
    }

    @Test
    @DisplayName("y la prueba puede fallar: sobre la muestra, el MISMO recorrido la encuentra")
    void laPruebaPuedeFallar() throws IOException {
        // EL CONTRASTE, y corre el escaner de verdad (#29). Lo que se ejerce es la cadena entera
        // —el recorrido con su filtro de rutas, la lectura del archivo y las seis palabras—, sobre
        // un archivo de disco y no sobre una cadena escrita aqui. Romper `fuentesBajoMain` pone
        // esto rojo; antes de #29 no lo ponia.
        List<Path> muestra = fuentesBajoMain(RaizDelRepositorio.ruta().resolve(MUESTRA));

        assertThat(muestra)
                .as(
                        "sin la muestra en «%s» este contraste se cumpliria solo, que es el defecto"
                                + " que existe para atrapar",
                        MUESTRA)
                .hasSize(1);

        List<String> hallazgos = hallazgosEn(muestra);

        assertThat(hallazgos)
                .as("las tres del enumerado de `rentas` mas las tres del vocabulario del calculo")
                .hasSize(SERVICIOS_DE_ARBITRIO.size() + VOCABULARIO_DEL_CALCULO.size());
        assertThat(hallazgos)
                .as("y cada hallazgo nombra la ruta, que es lo que hace util al rojo de arriba")
                .allMatch(hallazgo -> hallazgo.startsWith(MUESTRA + "/"));
        for (String prohibido : SERVICIOS_DE_ARBITRIO) {
            assertThat(hallazgos)
                    .as("el escaner tiene que reconocer «%s»", prohibido)
                    .anyMatch(hallazgo -> hallazgo.contains("«" + prohibido + "»"));
        }
        for (String prohibido : VOCABULARIO_DEL_CALCULO) {
            assertThat(hallazgos)
                    .as("y tambien «%s», que es como el defecto llega de verdad", prohibido)
                    .anyMatch(hallazgo -> hallazgo.contains("«" + prohibido + "»"));
        }
    }

    @Test
    @DisplayName("y se leyo un arbol de verdad: hay archivos que revisar")
    void seLeyoUnArbolDeVerdad() throws IOException {
        // La otra mitad del contraste: si `archivosDeProduccion()` devolviera una lista vacia
        // —porque cambio la disposicion de los modulos, o porque el directorio de trabajo del
        // corredor no es el que se supone—, la primera prueba pasaria en verde sin haber mirado
        // ni un byte.
        assertThat(archivosDeProduccion())
                .as("el recorrido tiene que encontrar el codigo de produccion y las migraciones")
                .hasSizeGreaterThan(200);
        assertThat(archivosDeProduccion().stream().map(CatastroNoNombraUnArbitrioTest::relativo))
                .as("y entre ellos, la migracion del frente y el borde que lo publica")
                .anyMatch(ruta -> ruta.endsWith("V10__buzon_del_territorio.sql"))
                .anyMatch(ruta -> ruta.endsWith("FrenteController.java"));
    }

    /**
     * EL ESCANER: las rutas que nombran algo prohibido, con que nombran.
     *
     * <p>Esta en un metodo y no dentro de la prueba porque el contraste lo ejerce con la muestra:
     * dos copias del recorrido acabarian discrepando, y la que discrepara seria justo la que dice
     * que no hay hallazgos.
     */
    private static List<String> hallazgosEn(List<Path> archivos) throws IOException {
        List<String> hallazgos = new ArrayList<>();
        for (Path archivo : archivos) {
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
        return hallazgos;
    }

    /** Todo {@code .java} y {@code .sql} de {@code src/main}, en los once modulos. */
    private static List<Path> archivosDeProduccion() throws IOException {
        return fuentesBajoMain(RaizDelRepositorio.ruta().resolve("backend"));
    }

    /**
     * EL RECORRIDO, con su filtro de rutas, sobre la raiz que se le de.
     *
     * <p>Recibe la raiz para que el contraste pueda ejercerlo <b>tal cual</b> sobre la muestra: si
     * el filtro se rompe, las dos pruebas se ponen rojas y no solo la del arbol.
     */
    private static List<Path> fuentesBajoMain(Path raiz) throws IOException {
        try (Stream<Path> arbol = Files.walk(raiz)) {
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
