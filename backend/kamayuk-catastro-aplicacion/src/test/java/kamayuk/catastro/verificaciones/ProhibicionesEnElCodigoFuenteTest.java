package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import kamayuk.comun.verificaciones.ProhibicionesEnElCodigoFuenteTestBase;
import kamayuk.comun.verificaciones.RevisorDeCodigoFuente;
import kamayuk.comun.verificaciones.RevisorDeEsquema;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las prohibiciones de texto de ARQ-04 §2, sobre el codigo de {@code sgtm}.
 *
 * <p>Hereda de {@code comun-verificaciones} el escaner y las pruebas que lo demuestran, y añade las
 * que son <b>de este repositorio</b>: el censo de las clases que componen el area a mano —esa lista
 * es suya, no de la libreria—, la comprobacion de que ninguna de sus entradas esta muerta, y el
 * reparto de tablas de la regla 11.
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
    @DisplayName("las DOS clases de catastro que componen el area a mano, una a una")
    void elCensoDeLasClasesQueComponenElArea() {
        // La misma linea, byte a byte, en dos archivos: en uno es un hallazgo y en el otro no.
        // Lo que decide es el NOMBRE DE LA CLASE, y por eso la lista se escribe por clase y no
        // por paquete: anadir una tercera es una linea visible en el diff.
        //
        // En el monolito eran seis. Al llegar aqui fueron DOS, con C-8 y #6 subieron a CINCO, y
        // con #20 vuelven a ser DOS. El motivo de cada baja esta en el javadoc de
        // `componenElAreaAManoConMotivo()`, y no es el mismo para las tres:
        // `ActualizarFichaCatastral`
        // dejo de componer el area a mano —ahora la pasa tipada a `DatosDeAuditoria`—, y las dos
        // de fiscalizacion NUNCA produjeron un hallazgo, medido.
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
                        "las dos de hoy: el modelo del papel de la ficha —donde la unidad va en el"
                                + " rotulo de la fila— y el componedor de hechos del buzon (C-8),"
                                + " donde el area se compone SOLO para la huella del hecho, que es"
                                + " un resumen criptografico y no pasa por ningun serializador."
                                + " Cambiarlo moveria la huella de eventos ya publicados. Las dos"
                                + " escriben la cifra sola")
                .containsExactlyInAnyOrder("ModeloDeLaFichaDelContribuyente", "ComponedorDeHechos");
    }

    @Test
    @DisplayName("y ninguna entrada de la lista esta muerta: las dos eximen un hallazgo de verdad")
    void ningunaEntradaDeLaListaEstaMuerta() throws IOException {
        // ESTA es la prueba que faltaba, y #20 la escribio porque midio lo contrario: de las cinco
        // entradas que la lista tenia, DOS —`DetectarSubvaluadores` y `VerificarEnCampo`, anadidas
        // por #6— no producian un solo hallazgo. El escaner busca `area….toString()` o
        // `area….valor().toPlainString()` y las dos escribian `.valor()` a secas; el area se
        // componia a mano, si, pero no en la forma que el escaner reconoce.
        //
        // Una exencion que no exime nada es invisible: nadie la nota, y el dia que la clase con ese
        // nombre SI componga un area a mano, el escaner callara. Es exactamente la advertencia que
        // la prueba de abajo escribe sobre `DiferenciaEntreLiquidaciones`, aplicada a la lista
        // entera y no a un nombre elegido a mano.
        List<String> muertas = new ArrayList<>();

        for (String clase : new ConfiguracionDeCatastro().componenElAreaAManoConMotivo()) {
            Path archivo = archivoDe(clase);
            String fuente = Files.readString(archivo, StandardCharsets.UTF_8);
            if (RevisorDeCodigoFuente.revisarAreas("UnRecursoCualquiera.java", fuente).isEmpty()) {
                muertas.add(clase);
            }
        }

        assertThat(muertas)
                .as(
                        "una entrada que no exime ningun hallazgo sobra en la lista: o la clase dejo"
                                + " de componer el area a mano —y entonces se quita— o la compone de"
                                + " una forma que el escaner no ve, y entonces lo que falta es el"
                                + " escaner")
                .isEmpty();
    }

    @Test
    @DisplayName("la celda del historial no puede estar en la lista: su clase es de `rentas`")
    void laCeldaDelHistorialNoPuedeEstar() {
        // La otra mitad de #607, adaptada a lo que ESTE sistema tiene despues de P5C y de #20.
        //
        // La celda del historial de liquidaciones NO puede estar aqui, y no por criterio sino
        // porque su clase es de `fiscalizacion`, que se quedo en `rentas`. Una entrada muerta en
        // una lista de excepciones es exactamente el defecto que esa lista existe para no tener
        // —y la prueba de arriba lo comprueba ahora para TODAS las entradas, y no solo para esta—.
        assertThat(new ConfiguracionDeCatastro().componenElAreaAManoConMotivo())
                .as("la celda del historial es de `fiscalizacion`, que no vive en este sistema")
                .doesNotContain("DiferenciaEntreLiquidaciones");
    }

    @Test
    @DisplayName("la descripcion del versionado ya NO compone el area a mano: la pasa tipada (#20)")
    void laDescripcionDelVersionadoYaNoComponeElAreaAMano() throws IOException {
        // Hasta #20 esta prueba afirmaba lo contrario: que `ActualizarFichaCatastral` escribia
        // «120.00» dentro de la descripcion que va a la columna JSON de la auditoria, con
        // `.valor().toPlainString()` para que saliera sin la unidad. Era el unico sitio de este
        // sistema donde el area se componia a mano PARA UNA COLUMNA, y por eso estaba en la lista.
        //
        // Ahora esa descripcion la compone `DatosDeAuditoria` con el `AreaM2` tipado, y quien
        // escribe la cifra es el serializador de `ObjetosDeValorEnJson` —la cifra sola, sin la
        // unidad—, que es donde #607 dice que tiene que escribirse. La afirmacion se invierte: lo
        // que se comprueba es que ya no hay nada que eximir.
        Path descripcion = archivoDe("ActualizarFichaCatastral", "kamayuk-catastro-nucleo");

        String fuente = Files.readString(descripcion, StandardCharsets.UTF_8);

        assertThat(RevisorDeCodigoFuente.revisarAreas("UnRecursoCualquiera.java", fuente))
                .as(
                        "ni siquiera fuera de la lista es ya un hallazgo: el area va tipada y la"
                                + " escribe el serializador")
                .isEmpty();
        assertThat(fuente)
                .as("y se ve en el fuente: el AreaM2 entra entero en el campo del asiento")
                .contains(".mas(\"areaTerreno\", ficha.areaTerreno())");
        assertThat(new ConfiguracionDeCatastro().componenElAreaAManoConMotivo())
                .as("por eso salio de la lista")
                .doesNotContain("ActualizarFichaCatastral");
    }

    /** El {@code .java} de esa clase dentro de {@code src/main}, buscado en los once modulos. */
    private static Path archivoDe(String clase) throws IOException {
        return archivoDe(clase, null);
    }

    private static Path archivoDe(String clase, @Nullable String modulo) throws IOException {
        Path backend = raizDelBackend();
        try (Stream<Path> arbol = Files.walk(backend)) {
            return arbol.filter(Files::isRegularFile)
                    .filter(ruta -> ruta.toString().contains("/src/main/"))
                    .filter(ruta -> !ruta.toString().contains("/build/"))
                    .filter(ruta -> modulo == null || ruta.toString().contains("/" + modulo + "/"))
                    .filter(ruta -> ruta.getFileName().toString().equals(clase + ".java"))
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new AssertionError(
                                            "la clase '"
                                                    + clase
                                                    + "' esta en la lista de excepciones y no existe"
                                                    + " en src/main: es una entrada muerta"));
        }
    }
}
