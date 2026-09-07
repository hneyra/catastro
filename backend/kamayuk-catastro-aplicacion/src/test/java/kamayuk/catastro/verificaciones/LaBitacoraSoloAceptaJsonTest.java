package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import kamayuk.catastro.auditoria.DatosDeAuditoria;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.json.SerializadorDeJson;
import kamayuk.comun.verificaciones.ReglasDeArquitectura;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC 4 de #20: lo que llega a {@code auditoria.datos_anteriores} y {@code datos_nuevos} es JSON.
 *
 * <h2>El defecto que existe para impedir</h2>
 *
 * <p>Las dos columnas son {@code jsonb} y {@code AuditoriaJdbc} las escribe con {@code cast(:datos
 * AS jsonb)}. Hasta #20 la firma admitia {@code String}: dos casos de uso pasaban <b>prosa</b> —y
 * reventaban siempre— y otros catorce componian el JSON concatenando cadenas —y reventaban con una
 * comilla—. Ninguna prueba pasaba por ese {@code cast}, asi que el defecto vivio en verde desde #7.
 *
 * <h2>Los llamadores se derivan del BYTECODE, y por que no del fuente</h2>
 *
 * <p>Una lista escrita a mano se desincroniza el primer mes, y su modo de fallo es que el llamador
 * nuevo no aparece en ella (la leccion de C-7 con las variables del descriptor). Y una expresion
 * regular sobre el fuente tampoco vale, que es la leccion de los dos ultimos defectos de esta
 * serie: {@code \.con\(} casa <b>hoy</b> con {@code CriterioDeCandidatos.con(estado)}, con {@code
 * FichaCatastral.con(construcciones)} y con {@code FichaResource.con(ficha, historial)} —tres
 * metodos que no tienen nada que ver con la bitacora— y no casaria con una llamada partida en dos
 * lineas por el formateador. El bytecode no admite varias formas: la llamada esta resuelta, con su
 * dueno y su descriptor.
 *
 * <h2>Que mide cada prueba, y que NO mide ninguna</h2>
 *
 * <p>Despues de #20, «lo que pasan es JSON valido» es una propiedad <b>de tipos</b> y no de
 * ejecucion: el unico modo de obtener un {@link DatosDeAuditoria} es {@code campos()…datos()}, que
 * serializa y vuelve a leer lo producido. Asi que aqui se comprueban las tres cosas de las que
 * cuelga esa propiedad: que hay llamadores de verdad, que ninguno puede pasar una cadena, y que no
 * existe una puerta trasera que fabrique un {@code DatosDeAuditoria} sin pasar por el serializador.
 *
 * <p><b>Lo que ninguna prueba de aqui puede ver</b>, dicho antes de que alguien lo descubra: que la
 * fila entre <b>de verdad</b> en la tabla. Eso lo miden las pruebas del AC 5 —{@code
 * ConfirmarElFrenteJdbcTest} y el caso del inspector con comilla de {@code ElAtajoNoExisteTest}—,
 * contra PostgreSQL real y con la {@code Auditoria} de verdad.
 */
@DisplayName("#20 AC 4 — La bitacora solo acepta JSON")
class LaBitacoraSoloAceptaJsonTest {

    /**
     * La unica clase de {@code src/main} que compone un objeto JSON concatenando cadenas, con su
     * motivo.
     *
     * <p>{@code RespuestaDeError} escribe el cuerpo {@code application/problem+json} de los errores
     * que ocurren <b>antes</b> de que haya controlador —la cadena de seguridad y el filtro de
     * tenant—, donde todavia no hay convertidores de mensaje que usar. No escribe ninguna columna
     * {@code jsonb}, y sus cuatro campos son un enumerado y un mensaje del catalogo: ni uno lo
     * teclea un usuario.
     */
    private static final String COMPONE_JSON_A_MANO_CON_MOTIVO = "RespuestaDeError";

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = ReglasDeArquitectura.clasesDeProduccion();
    }

    @Test
    @DisplayName("EL CONTRASTE: se encontraron llamadores de RegistroDeAuditoria.con(…)")
    void hayLlamadoresQueRevisar() {
        // Sin esto, un importador que no viera nada —un paquete renombrado, un modulo fuera del
        // classpath— dejaria las dos pruebas de abajo en verde sin haber revisado una sola
        // llamada. Es exactamente la forma en que esta clase dejaria de proteger nada.
        List<JavaMethodCall> llamadas = llamadasA("con");

        assertThat(llamadas)
                .as(
                        "no se encontro ni un llamador de RegistroDeAuditoria.con(…): esta guarda NO"
                                + " MIDIO NADA. O el importador de ArchUnit no ve el codigo de"
                                + " produccion, o el metodo se renombro")
                .isNotEmpty();
        assertThat(llamadas)
                .as("y son los de todo el sistema, no los de un modulo suelto")
                .hasSizeGreaterThanOrEqualTo(15);
        assertThat(llamadas.stream().map(JavaMethodCall::getOriginOwner).map(JavaClass::getName))
                .as("entre ellos, los dos que #20 destapo: el frente y el acta")
                .anyMatch(nombre -> nombre.endsWith(".ConfirmarElFrente"))
                .anyMatch(nombre -> nombre.endsWith(".LevantarActa"));
    }

    @Test
    @DisplayName("ningun llamador puede pasar una cadena: la firma solo admite DatosDeAuditoria")
    void ningunLlamadorPasaUnaCadena() {
        // Se lee del DESCRIPTOR DE LA LLAMADA y no de la declaracion del metodo, y no es lo mismo:
        // si alguien anadiera una sobrecarga `con(String, String)` «para un caso rapido», mirar la
        // declaracion seguiria encontrando la buena y esto encontraria la que se llama de verdad.
        List<String> hallazgos =
                llamadasA("con").stream()
                        .filter(
                                llamada ->
                                        llamada.getTarget().getRawParameterTypes().stream()
                                                .anyMatch(
                                                        tipo ->
                                                                !DatosDeAuditoria.class
                                                                        .getName()
                                                                        .equals(tipo.getName())))
                        .map(
                                llamada ->
                                        llamada.getOriginOwner().getSimpleName()
                                                + "."
                                                + llamada.getOrigin().getName()
                                                + " → "
                                                + llamada.getTarget().getFullName())
                        .sorted()
                        .toList();

        assertThat(hallazgos)
                .as(
                        "la columna es `jsonb`: un `String` que no es JSON compila, se despliega y"
                                + " revienta en el cast, en produccion. Lo que se pasa es un"
                                + " DatosDeAuditoria, que solo se puede construir serializando")
                .isEmpty();
    }

    @Test
    @DisplayName("y no hay puerta trasera: DatosDeAuditoria no se construye desde una cadena")
    void laUnicaPuertaEsElSerializador() {
        // Las dos pruebas de arriba valen mientras el tipo signifique algo. Un
        // `DatosDeAuditoria.crudo(String)` «para un caso rapido» las dejaria pasando en verde con
        // el defecto de vuelta: la firma seguiria siendo tipada y el contenido volveria a ser lo
        // que alguien concateno.
        assertThat(
                        Stream.of(DatosDeAuditoria.class.getDeclaredConstructors())
                                .filter(LaBitacoraSoloAceptaJsonTest::esVisible)
                                .map(Constructor::toString)
                                .toList())
                .as("no se construye directamente: se compone con campos()")
                .isEmpty();

        List<String> puertas =
                Stream.of(DatosDeAuditoria.class.getDeclaredMethods())
                        .filter(metodo -> Modifier.isStatic(metodo.getModifiers()))
                        .filter(LaBitacoraSoloAceptaJsonTest::esVisible)
                        .filter(LaBitacoraSoloAceptaJsonTest::devuelveDatos)
                        .map(Method::toString)
                        .sorted()
                        .toList();

        assertThat(puertas)
                .as(
                        "la unica fabrica publica es campos(), que devuelve el compositor; una que"
                                + " devolviera DatosDeAuditoria directamente podria saltarse el"
                                + " serializador")
                .isEmpty();

        assertThatThrownBy(() -> DatosDeAuditoria.campos().datos())
                .as("y un asiento sin ningun campo no explica nada")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("lo que el serializador produce es un objeto JSON, con comillas y saltos dentro")
    void loQueElSerializadorProduceEsUnObjetoJson() {
        // Los valores son los que los llamadores pasan de verdad: enumerados, objetos de valor,
        // fechas, decimales, nulos y —lo que reventaba— texto tecleado por una persona.
        DatosDeAuditoria datos =
                DatosDeAuditoria.campos()
                        .mas("inspector", "Juan \"El Tuerto\" Perez")
                        .mas("motivo", "es un toldo,\n no una edificacion\t\u0007")
                        .mas("ruta", "C:\\evidencias\\foto.jpg")
                        .mas("estado", java.time.DayOfWeek.MONDAY)
                        .mas("areaVerificada", AreaM2.de("180.00"))
                        .mas("longitud", Medida.enMetrosLineales("18.50"))
                        .mas("score", new BigDecimal("0.9100"))
                        .mas("vigenciaHasta", LocalDate.of(2026, 9, 7))
                        .mas("fichaId", null)
                        .datos();

        assertThat(SerializadorDeJson.esObjetoJson(datos.json()))
                .as("lo que llega al cast(… AS jsonb) tiene que ser un objeto JSON")
                .isTrue();
        assertThat(datos.json())
                .as("todo decimal como texto, tambien la medida con su unidad y el area sin ella")
                .contains("\"areaVerificada\":\"180.00\"")
                .contains("\"longitud\":\"18.50 ML\"")
                .contains("\"score\":\"0.9100\"")
                .contains("\"vigenciaHasta\":\"2026-09-07\"")
                .contains("\"fichaId\":null");
    }

    @Test
    @DisplayName("y ninguna clase de src/main compone un objeto JSON concatenando cadenas")
    void ningunaClaseComponeJsonAMano() throws IOException {
        // La otra mitad del arreglo: el tipo impide pasar una cadena a con(…), pero no impide
        // componer `{\"a\":\"` a mano y pasarlo como VALOR de un campo. Eso no revienta —queda una
        // cadena JSON dentro del objeto— y devuelve el escape a mano por la puerta de atras.
        //
        // Se busca la SECUENCIA de dos caracteres, no una forma sintactica: en Java `{\"` solo
        // aparece dentro de un literal de cadena, y no depende de como este partida la expresion.
        List<String> hallazgos = new java.util.ArrayList<>();
        List<Path> revisados = fuentesDeProduccion();

        for (Path archivo : revisados) {
            String clase = archivo.getFileName().toString().replace(".java", "");
            if (clase.equals(COMPONE_JSON_A_MANO_CON_MOTIVO)) {
                continue;
            }
            if (Files.readString(archivo, StandardCharsets.UTF_8).contains("{\\\"")) {
                hallazgos.add(clase);
            }
        }

        assertThat(revisados)
                .as("el recorrido tiene que encontrar el codigo de produccion de los once modulos")
                .hasSizeGreaterThan(200);
        assertThat(hallazgos)
                .as(
                        "un JSON compuesto a mano necesita que alguien se acuerde de escapar lo que"
                                + " interpola. Los dos escapar() que este sistema tenia estaban"
                                + " duplicados y los dos incompletos —sin los caracteres de"
                                + " control—, que es la demostracion de que eso no se sostiene."
                                + " Se compone con SerializadorDeJson")
                .isEmpty();
    }

    @Test
    @DisplayName("y la excepcion declarada no esta muerta: RespuestaDeError si compone a mano")
    void laExcepcionDeclaradaNoEstaMuerta() throws IOException {
        // La leccion que este mismo issue midio en la OTRA lista de excepciones: de las cinco
        // clases de componenElAreaAManoConMotivo(), dos llevaban desde #6 sin producir un solo
        // hallazgo. Una excepcion que no exime nada es invisible, y cuando el codigo que decia
        // eximir desaparece, la entrada se queda ahi eximiendo a la clase que venga con ese nombre.
        Path archivo =
                fuentesDeProduccion().stream()
                        .filter(
                                ruta ->
                                        ruta.getFileName()
                                                .toString()
                                                .equals(COMPONE_JSON_A_MANO_CON_MOTIVO + ".java"))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "la clase eximida ya no existe: quita la excepcion"));

        assertThat(Files.readString(archivo, StandardCharsets.UTF_8))
                .as("si dejara de componer JSON a mano, la excepcion sobraria")
                .contains("{\\\"");
    }

    // ── Ayudantes ──────────────────────────────────────────────────────

    private static List<JavaMethodCall> llamadasA(String metodo) {
        return clases.stream()
                .flatMap(clase -> clase.getMethodCallsFromSelf().stream())
                .filter(
                        llamada ->
                                RegistroDeAuditoria.class
                                        .getName()
                                        .equals(llamada.getTargetOwner().getName()))
                .filter(llamada -> metodo.equals(llamada.getTarget().getName()))
                .toList();
    }

    private static boolean esVisible(java.lang.reflect.Member miembro) {
        return !Modifier.isPrivate(miembro.getModifiers());
    }

    private static boolean devuelveDatos(Method metodo) {
        return DatosDeAuditoria.class.equals(metodo.getReturnType());
    }

    /** Todo {@code .java} de {@code src/main}, en los once modulos. */
    private static List<Path> fuentesDeProduccion() throws IOException {
        Path backend = RaizDelRepositorio.ruta().resolve("backend");
        try (Stream<Path> arbol = Files.walk(backend)) {
            return arbol.filter(Files::isRegularFile)
                    .filter(ruta -> ruta.toString().contains("/src/main/"))
                    .filter(ruta -> !ruta.toString().contains("/build/"))
                    .filter(ruta -> ruta.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }
}
