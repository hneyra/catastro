package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import kamayuk.comun.verificaciones.ProhibicionesEnElCodigoFuenteTestBase;
import kamayuk.comun.verificaciones.RevisorDeCodigoFuente;
import kamayuk.comun.verificaciones.RevisorDeEsquema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las prohibiciones de texto de ARQ-04 §2, sobre el codigo de {@code sgtm}.
 *
 * <p>Hereda de {@code comun-verificaciones} el escaner y las pruebas que lo demuestran, y añade las
 * dos que son <b>de este repositorio</b>: el censo de las clases que componen el area a mano —esa
 * lista es suya, no de la libreria— y la celda del historial, que se afirma leyendo una clase de
 * produccion de {@code kamayuk-catastro-fiscalizacion}.
 */
@DisplayName("ARQ-04 §2 — Prohibiciones en el codigo fuente")
class ProhibicionesEnElCodigoFuenteTest extends ProhibicionesEnElCodigoFuenteTestBase {

    @Test
    @DisplayName("ninguna tabla del esquema se queda fuera del reparto de la regla 11")
    void ningunaTablaDelEsquemaSeQuedaFueraDelReparto() throws IOException {
        // LA LECCION DE R-N, APLICADA A LAS TABLAS. El reparto se consulta con
        // `getOrDefault(tabla, SISTEMA_REPLICADO)`, y «replicado» significa «no esta a ningun lado
        // de la frontera»: una tabla que FALTA en el mapa no pone nada rojo, DEJA DE REVISARSE, en
        // verde. Es exactamente lo que R-N midio con `SISTEMA_DEL_MODULO` y lo que cerro con
        // `modulosDelReparto()`; aqui es lo mismo por el otro eje.
        //
        // Se midio al escribirla, y no estaba vacia: faltaban CINCO tablas que nadie habia
        // decidido —`catastro_evento` desde C-8 y las cuatro `normativa_*` desde P5B—, y con ellas
        // la regla 11 llevaba dos migraciones sin mirar el buzon de salida de este sistema.
        //
        // Comprueba UNA SOLA DIRECCION a proposito, igual que `modulosDelReparto()`: el mapa
        // arrastra del monolito nombres de tablas que este sistema no tiene, y nombrar de mas no
        // cuesta nada —es lo que hace que un cruce, si llega, se vea—. Podarlo es otro trabajo.
        Set<String> delEsquema = RevisorDeEsquema.tablasDe(migracionesDeEsteEsquema());
        Set<String> repartidas = new ConfiguracionDeCatastro().sistemaDeCadaTabla().keySet();

        assertThat(delEsquema)
                .as("el recorrido tiene que encontrar el esquema, o esto no comprueba nada")
                .hasSizeGreaterThan(40);
        assertThat(delEsquema)
                .as(
                        "toda tabla que este esquema crea tiene que estar repartida: la que falta"
                                + " no da un cruce, deja de revisarse — y eso pasa en VERDE (la leccion"
                                + " de R-N)")
                .allSatisfy(
                        tabla ->
                                assertThat(repartidas)
                                        .as("la tabla «%s» no esta en el reparto", tabla)
                                        .contains(tabla));
    }

    /** Las migraciones de este esquema, en orden de version. */
    private static List<RevisorDeEsquema.Migracion> migracionesDeEsteEsquema() throws IOException {
        Path directorio =
                RaizDelRepositorio.ruta()
                        .resolve(
                                "backend/kamayuk-catastro-esquema/src/main/resources/db/migration");
        try (java.util.stream.Stream<Path> archivos = Files.list(directorio)) {
            return archivos.filter(ruta -> ruta.getFileName().toString().endsWith(".sql"))
                    .sorted(
                            java.util.Comparator.comparingInt(
                                    ProhibicionesEnElCodigoFuenteTest::versionDe))
                    .map(
                            ruta -> {
                                try {
                                    return new RevisorDeEsquema.Migracion(
                                            ruta.getFileName().toString(),
                                            Files.readString(ruta, StandardCharsets.UTF_8));
                                } catch (IOException noSePudoLeer) {
                                    throw new java.io.UncheckedIOException(noSePudoLeer);
                                }
                            })
                    .toList();
        }
    }

    private static int versionDe(Path migracion) {
        String nombre = migracion.getFileName().toString();
        return Integer.parseInt(nombre.substring(1, nombre.indexOf("__")));
    }

    @Test
    @DisplayName("las dos clases de catastro que componen el area a mano, una a una")
    void elCensoDeLasClasesQueComponenElArea() {
        // La misma linea, byte a byte, en dos archivos: en uno es un hallazgo y en el otro no.
        // Lo que decide es el NOMBRE DE LA CLASE, y por eso la lista se escribe por clase y no
        // por paquete: anadir una tercera es una linea visible en el diff.
        //
        // En el monolito eran seis. Al llegar aqui fueron DOS, C-8 anadio el componedor de hechos
        // del buzon y #6 las dos de la fiscalizacion catastral, y hoy vuelven a ser DOS.
        //
        // #20 QUITO TRES, Y LAS TRES POR MOTIVOS DISTINTOS, MEDIDOS CORRIENDO EL REVISOR SOBRE
        // LOS ARCHIVOS REALES SIN LA EXENCION:
        //
        //   · `ActualizarFichaCatastral` escribia `areaTerreno().valor().toPlainString()` y era
        //     una exencion VIVA —el revisor la encontraba—; hoy pasa el `AreaM2` tipado al
        //     serializador y no queda nada que eximir. El byte que sale no cambia.
        //   · `DetectarSubvaluadores` y `VerificarEnCampo` YA ESTABAN MUERTAS ANTES DE #20, y eso
        //     es el hallazgo: escriben `…areaDeLaFicha().valor()` SIN `toPlainString()`, y
        //     AREA_COMPUESTA_A_MANO exige `valor().toPlainString()` o `toString()`. Medido con el
        //     revisor sobre los dos archivos tal como estaban en `main`: cero hallazgos con la
        //     exencion y cero sin ella. #6 las anadio contra un patron que no las miraba, o sea
        //     que la lista llevaba dos entradas que no eximian de nada — y una exencion que no
        //     exime nada no es inofensiva: es una puerta abierta que nadie vigila, porque el dia
        //     que esa clase SI compusiera un area a mano no se pondria roja.
        String fuente =
                """
                final class Modelo {
                    static Tabla de(Fue fue) {
                        return Campo.de("Area del terreno (m2)",
                                fue.areaTerreno().valor().toPlainString());
                    }
                }
                """;

        assertThat(RevisorDeCodigoFuente.revisarAreas("UnRecursoCualquiera.java", fuente))
                .as("fuera de la lista, la misma linea es un hallazgo")
                .hasSize(1);
        assertThat(
                        RevisorDeCodigoFuente.revisarAreas(
                                "ModeloDeLaFichaDelContribuyente.java", fuente))
                .as("el papel no tiene serializador y la unidad va en el rotulo de la fila")
                .isEmpty();
        assertThat(new ConfiguracionDeCatastro().componenElAreaAManoConMotivo())
                .as(
                        "las dos de hoy: el modelo del papel de la ficha —que no tiene"
                                + " serializador— y el componedor de hechos del buzon (C-8), donde"
                                + " el area se compone SOLO para la huella del hecho, que es un"
                                + " resumen criptografico y cambiarla moveria la huella de eventos"
                                + " ya publicados. Las otras tres se fueron en #20: la de la ficha"
                                + " porque ahora pasa el AreaM2 tipado al serializador, y las dos"
                                + " de la fiscalizacion porque estaban MUERTAS —no eximian de nada,"
                                + " medido con el revisor sobre los archivos reales—")
                .containsExactlyInAnyOrder("ModeloDeLaFichaDelContribuyente", "ComponedorDeHechos");
    }

    @Test
    @DisplayName("las tres que #20 saco de la lista ya no tienen nada que eximir")
    void lasTresQueSalieronDeLaListaNoTienenNadaQueEximir() throws IOException {
        // La otra direccion, y la que impide que la poda de #20 se deshaga sin que nadie lo note:
        // si alguna de las tres volviera a componer un area a mano, esto sale rojo NOMBRANDOLA, y
        // entonces hay que decidir —tipar el area o volver a eximir la clase— en vez de que la
        // exencion aparezca sola en un diff.
        for (String clase :
                List.of("ActualizarFichaCatastral", "DetectarSubvaluadores", "VerificarEnCampo")) {
            Path archivo = archivoDeProduccion(clase);
            assertThat(archivo).as("la clase tiene que existir para afirmar esto de ella").exists();
            assertThat(
                            RevisorDeCodigoFuente.revisarAreas(
                                    "SinExencion.java",
                                    Files.readString(archivo, StandardCharsets.UTF_8)))
                    .as(
                            "«%s» salio de componenElAreaAManoConMotivo() en #20 porque no tenia"
                                    + " nada que eximir. Si vuelve a componer un area a mano, o se"
                                    + " tipa como AreaM2 o vuelve a la lista con su motivo",
                            clase)
                    .isEmpty();
        }
    }

    /** El unico {@code .java} de {@code src/main} que se llama asi. */
    private static Path archivoDeProduccion(String clase) throws IOException {
        Path backend = RaizDelRepositorio.ruta().resolve("backend");
        try (java.util.stream.Stream<Path> arbol = Files.walk(backend)) {
            return arbol.filter(Files::isRegularFile)
                    .filter(ruta -> ruta.toString().contains("/src/main/"))
                    .filter(ruta -> !ruta.toString().contains("/build/"))
                    .filter(ruta -> ruta.getFileName().toString().equals(clase + ".java"))
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new AssertionError(
                                            "No existe ningun src/main/**/" + clase + ".java"));
        }
    }

    @Test
    @DisplayName("el componedor de hechos esta en la lista, y la celda del historial no puede")
    void elComponedorDeHechosEstaYLaCeldaDelHistorialNoPuedeEstar() throws IOException {
        // La otra mitad de #607, adaptada a lo que ESTE sistema tiene despues de P5C y de #20.
        //
        // Hasta #20 esto se afirmaba sobre `ActualizarFichaCatastral`, que escribia el area a mano
        // dentro de la descripcion que va a la columna JSON de la auditoria. Hoy esa clase pasa el
        // `AreaM2` tipado al serializador y no compone nada, asi que la exencion viva que queda es
        // `ComponedorDeHechos`: ahi el area se compone SOLO para la huella del hecho sellado, que
        // es un resumen criptografico y no pasa por ningun serializador (C-8).
        //
        // Y la celda del historial de liquidaciones —la otra excepcion legitima de #607— NO puede
        // estar aqui, y no por criterio sino porque su clase es de `fiscalizacion`, que se quedo
        // en `rentas`. Una entrada muerta en una lista de excepciones es exactamente el defecto
        // que esa lista existe para no tener —#20 encontro DOS—, asi que esta prueba lo afirma en
        // las dos direcciones.
        Path descripcion = archivoDeProduccion("ComponedorDeHechos");

        assertThat(descripcion)
                .as("la clase tiene que existir para poder afirmar esto de ella")
                .exists();

        String fuente = Files.readString(descripcion, StandardCharsets.UTF_8);

        assertThat(RevisorDeCodigoFuente.revisarAreas("UnRecursoCualquiera.java", fuente))
                .as("fuera de la lista, lo que esa clase escribe SI seria un hallazgo")
                .isNotEmpty();
        assertThat(RevisorDeCodigoFuente.revisarAreas(descripcion.getFileName().toString(), fuente))
                .as("y dentro de la lista, no")
                .isEmpty();

        assertThat(new ConfiguracionDeCatastro().componenElAreaAManoConMotivo())
                .as("la celda del historial es de `fiscalizacion`, que no vive en este sistema")
                .doesNotContain("DiferenciaEntreLiquidaciones");
    }
}
