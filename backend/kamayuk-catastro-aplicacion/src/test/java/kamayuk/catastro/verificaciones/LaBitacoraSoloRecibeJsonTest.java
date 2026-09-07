package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC 4 de #20: la bitacora no recibe texto libre, y se comprueba sobre <b>todos</b> sus llamadores.
 *
 * <h2>Que defecto existe para impedir</h2>
 *
 * <p>{@code auditoria.datos_anteriores} y {@code datos_nuevos} son {@code jsonb}, y {@code
 * AuditoriaJdbc} las escribe con {@code cast(:datosAnteriores AS jsonb)}. Mientras {@code
 * RegistroDeAuditoria.con(...)} admitio un {@code String}, dos casos de uso le pasaban <b>prosa</b>
 * —{@code ConfirmarElFrente} y {@code ProponerLosFrentesDeUnPredio}, que reventaban SIEMPRE— y
 * otros diez componian el JSON a mano interpolando texto sin escapar, de modo que un inspector
 * llamado {@code Juan "El Tuerto" Perez} hacia imposible levantar su acta. El build estaba en VERDE
 * porque ninguna prueba llegaba a ese {@code cast}.
 *
 * <h2>La lista se DERIVA, no se escribe</h2>
 *
 * <p>Es la leccion de C-7 con las variables del descriptor: una lista escrita a mano se
 * desincroniza el primer mes, y su modo de fallo es que el llamador nuevo no aparece en ella. El
 * issue nombraba <b>cinco</b> compositores; recorriendo {@code src/main} salen <b>dieciocho</b>
 * archivos, y todos menos uno tenian la misma forma. Por eso esto recorre el arbol.
 *
 * <h2>Lo que esta prueba NO puede ver, dicho antes de que alguien lo descubra</h2>
 *
 * <p>Que el JSON diga lo que hace falta. Que sea JSON <b>valido</b> lo garantiza el tipo —{@code
 * DatosDeAuditoria} solo se construye serializando, y no tiene ninguna fabrica que acepte texto—, y
 * que llegue entero hasta el {@code cast} lo miden las pruebas de {@code ConfirmarElFrente}, {@code
 * ProponerLosFrentesDeUnPredio} y {@code LevantarActa} contra PostgreSQL real. Esto vigila la
 * tercera cosa: que nadie vuelva a componerlo a mano.
 */
@DisplayName("#20 AC 4 — la bitacora no recibe texto libre, en ningun llamador")
class LaBitacoraSoloRecibeJsonTest {

    /**
     * Como se ve un JSON escrito a mano en el fuente: una comilla escapada dentro de un literal.
     */
    private static final String COMILLA_ESCAPADA = "\\\"";

    @Test
    @DisplayName("ningun llamador de la bitacora compone su JSON a mano")
    void ningunLlamadorComponeElJsonAMano() throws IOException {
        List<String> hallazgos = new ArrayList<>();

        for (Path archivo : llamadoresDeLaBitacora()) {
            hallazgos.addAll(hallazgosDe(relativo(archivo), leer(archivo)));
        }

        assertThat(hallazgos)
                .as(
                        "las dos columnas son jsonb: el JSON lo escribe el serializador de"
                                + " ConfiguracionDeJson a traves de DatosDeAuditoria, y no una"
                                + " concatenacion. Un escape a mano que hay que acordarse de llamar"
                                + " es el defecto, no el arreglo — los dos que habia cubrian la"
                                + " barra y la comilla y NO los caracteres de control, asi que un"
                                + " nombre con un salto de linea rompia igual el que «si escapaba»")
                .isEmpty();
    }

    @Test
    @DisplayName("ningun `.con(...)` de la bitacora lleva un literal de texto")
    void ningunConLlevaUnLiteral() throws IOException {
        // La otra mitad, y NO es redundante con la de arriba: la prosa de `ConfirmarElFrente`
        // —«Longitud PROPUESTA, derivada del corte contra el eje de calzada»— no lleva ni una
        // comilla escapada dentro, asi que el escaner del JSON a mano NO la veia. Se midio
        // escribiendola: el primer escaner pasaba en VERDE sobre el defecto exacto que este
        // issue existe para cerrar. Lo que la separa es que un `.con(` no puede llevar texto.
        //
        // Hoy tampoco compila —la firma pide DatosDeAuditoria—, y por eso esto vigila la
        // REGRESION: el dia que alguien ensanche la firma «para un caso», esto sale rojo antes.
        List<String> hallazgos = new ArrayList<>();

        for (Path archivo : llamadoresDeLaBitacora()) {
            hallazgos.addAll(literalesEnLosCon(relativo(archivo), leer(archivo)));
        }

        assertThat(hallazgos)
                .as(
                        "lo que va a una columna jsonb no se escribe en el sitio donde se asienta:"
                                + " se compone con DatosDeAuditoria, que es lo unico que puede"
                                + " producirlo")
                .isEmpty();
    }

    @Test
    @DisplayName("y el escaner puede fallar: sobre un fuente que si compone a mano, lo encuentra")
    void elEscanerPuedeFallar() {
        // El contraste, y hace falta las dos mitades. Sin el, un escaner que no encontrara nunca
        // nada —un patron mal escrito, un `soloCodigo` que se comiera el archivo entero— pasaria en
        // verde diciendo que no hay hallazgos, que es como esta clase dejaria de proteger nada.
        String comoEraElDefecto =
                """
                final class LevantarActa {
                    void asentar() {
                        registro.con(null, "{\\"numero\\":\\"" + numero + "\\"}");
                    }
                    private static String escapar(String texto) {
                        return texto.replace("\\"", "\\\\\\"");
                    }
                }
                """;
        String comoEsAhora =
                """
                final class LevantarActa {
                    void asentar() {
                        registro.con(
                                null,
                                DatosDeAuditoria.objeto().campo("numero", numero).componer());
                    }
                }
                """;

        assertThat(hallazgosDe("LevantarActa.java", comoEraElDefecto))
                .as("el JSON a mano y el escape a mano, los dos")
                .hasSize(2);
        assertThat(literalesEnLosCon("LevantarActa.java", comoEraElDefecto))
                .as("y el literal dentro del `.con(`, que es lo que lo hace texto libre")
                .hasSize(1);
        assertThat(hallazgosDe("LevantarActa.java", comoEsAhora))
                .as("y sobre el arreglo, ninguno")
                .isEmpty();
        assertThat(literalesEnLosCon("LevantarActa.java", comoEsAhora))
                .as(
                        "el compositor declara sus campos con `.campo(`, que se llama distinto A"
                                + " PROPOSITO: con los dos metodos llamandose `con`, este escaner no"
                                + " podia separar el literal que es el defecto del que es el nombre"
                                + " de un campo, y marcaba los dieciocho compositores")
                .isEmpty();
    }

    @Test
    @DisplayName("y la prosa se caza AUNQUE no lleve ninguna comilla escapada dentro")
    void laProsaSeCazaAunqueNoTengaComillas() {
        // El caso que puso en verde al primer escaner, escrito tal como estaba en `main`.
        String comoEstabaConfirmarElFrente =
                """
                final class ConfirmarElFrente {
                    void asentar() {
                        registro.con(
                                "Longitud PROPUESTA, derivada del corte contra el eje de calzada",
                                "Longitud CONFIRMADA por " + quien + ": " + longitud);
                    }
                }
                """;

        assertThat(hallazgosDe("ConfirmarElFrente.java", comoEstabaConfirmarElFrente))
                .as(
                        "no lleva ni una comilla escapada: el escaner del JSON a mano NO la ve, y"
                                + " por eso las dos comprobaciones no se sustituyen")
                .isEmpty();
        assertThat(literalesEnLosCon("ConfirmarElFrente.java", comoEstabaConfirmarElFrente))
                .as("y la que si la ve es la del literal en el `.con(`")
                .hasSize(1);
    }

    @Test
    @DisplayName("y no se cuenta lo que dice un comentario: la prosa que EXPLICA el defecto pasa")
    void unComentarioQueExplicaElDefectoNoEsElDefecto() {
        // Los javadoc de `AbrirCampania` y `DescripcionDelCandidato` cuentan por que se quito el
        // `escapar()`. Una regla que gritara ahi obligaria a borrar la explicacion para pasar, que
        // es como una barrera se convierte en algo que la gente rodea (#437).
        String soloEnUnComentario =
                """
                /** Antes habia aqui un escapar( ) y un "{\\"codigo\\":\\"" concatenado a mano. */
                final class AbrirCampania {
                    void asentar() {
                        registro.con(null, DatosDeAuditoria.objeto().componer());
                    }
                }
                """;

        assertThat(hallazgosDe("AbrirCampania.java", soloEnUnComentario)).isEmpty();
    }

    @Test
    @DisplayName("y se leyo un arbol de verdad: los llamadores son dieciocho, no cinco")
    void seLeyoUnArbolDeVerdad() throws IOException {
        // La mitad que importa. Si `llamadoresDeLaBitacora()` devolviera una lista vacia —porque
        // cambio la disposicion de los modulos, o porque el directorio de trabajo del corredor no
        // es el que se supone—, las dos primeras pruebas pasarian en verde sin haber mirado un
        // byte.
        List<String> rutas =
                llamadoresDeLaBitacora().stream()
                        .map(LaBitacoraSoloRecibeJsonTest::relativo)
                        .toList();

        assertThat(rutas)
                .as(
                        "el issue nombraba cinco compositores; recorriendo src/main son dieciocho"
                                + " archivos. Esa diferencia es la razon de que la lista se derive")
                .hasSizeGreaterThanOrEqualTo(15);
        assertThat(rutas)
                .as("y entre ellos, los dos que pasaban prosa y el del inspector con comilla")
                .anyMatch(ruta -> ruta.endsWith("ConfirmarElFrente.java"))
                .anyMatch(ruta -> ruta.endsWith("ProponerLosFrentesDeUnPredio.java"))
                .anyMatch(ruta -> ruta.endsWith("LevantarActa.java"));
    }

    /** Los hallazgos de un fuente: el JSON a mano y el escape a mano. */
    private static List<String> hallazgosDe(String archivo, String contenido) {
        List<String> hallazgos = new ArrayList<>();
        String codigo = soloCodigo(contenido);
        if (codigo.contains(COMILLA_ESCAPADA)) {
            hallazgos.add(
                    archivo
                            + " — compone JSON a mano: una comilla escapada dentro de un literal."
                            + " La columna es jsonb y lo que se interpola no viene escapado");
        }
        if (codigo.contains("String escapar(")) {
            hallazgos.add(
                    archivo
                            + " — trae su propio escapar(): el que habia cubria la barra y la"
                            + " comilla y no los caracteres de control");
        }
        return hallazgos;
    }

    /**
     * Los literales de texto que aparecen dentro de un {@code .con(...)}.
     *
     * <p>Recorre los parentesis contando, y no con una expresion regular: el argumento puede llevar
     * parentesis dentro —{@code descripcion(guardada)}— y un patron que parara en el primer {@code
     * )} veria medio argumento y no encontraria nada, o sea que pasaria en verde.
     */
    private static List<String> literalesEnLosCon(String archivo, String contenido) {
        List<String> hallazgos = new ArrayList<>();
        String codigo = soloCodigo(contenido);
        int desde = 0;
        while ((desde = codigo.indexOf(".con(", desde)) >= 0) {
            int abre = desde + ".con(".length();
            int cierra = finDeLaLlamada(codigo, abre);
            String argumentos = sinLosNombresDeCampo(codigo.substring(abre, cierra));
            if (argumentos.indexOf('"') >= 0) {
                hallazgos.add(
                        archivo
                                + " — pasa texto a la bitacora: «"
                                + argumentos.replaceAll("\\s+", " ").strip()
                                + "»");
            }
            desde = cierra;
        }
        return hallazgos;
    }

    /**
     * Quita los <b>nombres de campo</b> del compositor, que son literales legitimos.
     *
     * <p>Un asiento se compone dentro del propio {@code .con(...)} —{@code
     * DatosDeAuditoria.objeto().campo("codigo", …).componer()}—, asi que el texto del argumento
     * lleva dentro los nombres de los campos. El nombre de un campo va <b>siempre</b> pegado al
     * {@code .campo(}, y por eso se puede quitar sin quitar nada mas: lo que quede es texto que
     * alguien esta metiendo en el asiento, que es el defecto.
     */
    private static String sinLosNombresDeCampo(String argumentos) {
        return argumentos.replaceAll("\\.campo\\(\\s*\"[^\"]*\"", ".campo(");
    }

    /** El indice del parentesis que cierra el que se abrio en {@code abre}. */
    private static int finDeLaLlamada(String codigo, int abre) {
        int nivel = 1;
        for (int i = abre; i < codigo.length(); i++) {
            char letra = codigo.charAt(i);
            if (letra == '(') {
                nivel++;
            } else if (letra == ')' && --nivel == 0) {
                return i;
            }
        }
        return codigo.length();
    }

    /**
     * El fuente sin comentarios.
     *
     * <p>Escrito aqui y no tomado de {@code comun-verificaciones} porque aquel es de la libreria y
     * esta prueba es de este repositorio; y porque lo que hace falta es exacto: quitar {@code //},
     * {@code /* *​/} y las cadenas de texto no, que son justamente lo que se mira.
     */
    private static String soloCodigo(String contenido) {
        return contenido.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
    }

    /** Todo {@code .java} de {@code src/main} que nombra {@code RegistroDeAuditoria}. */
    private static List<Path> llamadoresDeLaBitacora() throws IOException {
        Path backend = RaizDelRepositorio.ruta().resolve("backend");
        try (Stream<Path> arbol = Files.walk(backend)) {
            return arbol.filter(Files::isRegularFile)
                    .filter(ruta -> ruta.toString().contains("/src/main/"))
                    .filter(ruta -> !ruta.toString().contains("/build/"))
                    .filter(ruta -> ruta.getFileName().toString().endsWith(".java"))
                    // El propio tipo y quien lo escribe no son llamadores suyos.
                    .filter(ruta -> !ruta.toString().contains("/kamayuk/catastro/auditoria/"))
                    .filter(LaBitacoraSoloRecibeJsonTest::nombraLaBitacora)
                    .sorted()
                    .toList();
        }
    }

    private static boolean nombraLaBitacora(Path archivo) {
        return leer(archivo).contains("RegistroDeAuditoria")
                || leer(archivo).contains("DatosDeAuditoria");
    }

    private static String leer(Path archivo) {
        try {
            return Files.readString(archivo, StandardCharsets.UTF_8);
        } catch (IOException noSePudoLeer) {
            throw new java.io.UncheckedIOException(noSePudoLeer);
        }
    }

    private static String relativo(Path archivo) {
        return RaizDelRepositorio.ruta().relativize(archivo).toString();
    }
}
