package kamayuk.catastro.json;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Compone JSON con un serializador, para lo que no viaja por HTTP: la bitacora (#20).
 *
 * <h2>Que problema resuelve, medido</h2>
 *
 * <p>{@code auditoria.datos_anteriores} y {@code datos_nuevos} son {@code jsonb}, y {@code
 * AuditoriaJdbc} los escribe con {@code cast(:datosAnteriores AS jsonb)}. Hasta #20, catorce casos
 * de uso componian ese JSON <b>concatenando cadenas</b> y dos pasaban directamente prosa. Lo
 * primero revienta con una comilla —«Juan "El Tuerto" Perez» y el {@code INSERT} muere con «invalid
 * input syntax for type json», con la transaccion entera revertida y un mensaje que habla de JSON y
 * no del inspector—; lo segundo revienta siempre.
 *
 * <p>Aqui no se escapa nada a mano y no hay ningun metodo {@code escapar()} que haya que acordarse
 * de llamar: lo hace el generador de Jackson, que es quien conoce los caracteres de control que los
 * dos {@code escapar()} borrados <b>no</b> cubrian.
 *
 * <h2>Por que un mapeador propio y no el de Spring</h2>
 *
 * <p>Porque quien compone la bitacora es un caso de uso, y un caso de uso no tiene —ni debe tener—
 * un {@code ObjectMapper} inyectado: se prueba sin levantar contexto (regla 7). Y porque la
 * bitacora necesita una regla que el transporte no necesita: {@link
 * ObjetosDeValorEnJson#decimalesComoTexto()}, cuyo javadoc dice por que no se instala en el otro.
 *
 * <p>Lo que si comparten es la <b>definicion</b> de como se escribe cada objeto de valor: {@link
 * ObjetosDeValorEnJson#modulo()}, una sola, en los dos mapeadores. Dos definiciones podrian
 * divergir y entonces el mismo area diria dos cosas segun por donde saliera.
 */
public final class SerializadorDeJson {

    /**
     * Sin estado observable y seguro entre hilos: un {@code ObjectMapper} de Jackson lo es una vez
     * construido, y aqui no se reconfigura nunca despues.
     */
    private static final ObjectMapper MAPEADOR =
            JsonMapper.builder()
                    .addModule(ObjetosDeValorEnJson.modulo())
                    .addModule(ObjetosDeValorEnJson.decimalesComoTexto())
                    .build();

    private SerializadorDeJson() {}

    /** El valor, escrito en JSON. */
    public static String texto(Object valor) {
        return MAPEADOR.writeValueAsString(valor);
    }

    /**
     * Si el texto es un <b>objeto</b> JSON.
     *
     * <p>Objeto y no cualquier JSON: {@code cast('"Longitud PROPUESTA"' AS jsonb)} lo acepta el
     * motor sin rechistar —un escalar tambien es JSON valido—, y una columna de bitacora con una
     * cadena suelta dentro no se puede consultar por campo ni comparar con la del dia anterior. Lo
     * que se asienta es el ANTES y el DESPUES de una fila, y eso es un objeto.
     */
    public static boolean esObjetoJson(String texto) {
        try {
            return MAPEADOR.readTree(texto).isObject();
        } catch (JacksonException noEsJson) {
            return false;
        }
    }
}
