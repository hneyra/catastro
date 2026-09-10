package kamayuk.catastro.json;

import java.math.BigDecimal;
import java.util.function.Function;
import kamayuk.catastro.dominio.Alicuota;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Dinero;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Porcentaje;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Como se escriben en JSON los objetos de valor del dominio. <b>Una sola definicion</b>.
 *
 * <h2>Por que vive en su propio paquete y no dentro de {@code web}</h2>
 *
 * <p>Porque tiene dos consumidores y no uno. {@code ConfiguracionDeJson} lo registra en el {@code
 * ObjectMapper} de Spring —lo que sale por HTTP y lo que va al buzon de salida— y {@link
 * SerializadorDeJson} lo usa para componer lo que se asienta en la bitacora (#20). Dejarlo en
 * {@code kamayuk.catastro.web} obligaria a que la bitacora dependiera de la capa de PRESENTACION
 * para poder escribir una columna de la base, que es exactamente la atadura que los javadoc de los
 * casos de uso citaban para justificar componer el JSON a mano.
 *
 * <p>Y no vive en {@code kamayuk.catastro.dominio}: ahi no entra Jackson (regla 7). Este paquete
 * depende del dominio y nadie del dominio depende de el, que es la direccion correcta.
 *
 * <p><b>Todo decimal sale como cadena, nunca como numero JSON.</b> El {@code number} de JavaScript
 * es un binario de doble precision: {@code 0.1 + 0.2} no es {@code 0.3}, y un importe con muchos
 * digitos se redondea al leerlo en el navegador. Es exactamente el defecto que la regla 1 prohibe
 * en Java, y no tendria sentido protegerlo en el servidor y perderlo en el transporte (RNF-055).
 *
 * <p>API de <b>Jackson 3</b> ({@code tools.jackson}), que es la que trae Spring Boot 4: {@code
 * ValueSerializer} donde Jackson 2 tenia {@code JsonSerializer}.
 */
public final class ObjetosDeValorEnJson {

    private ObjetosDeValorEnJson() {}

    /**
     * Los objetos de valor, cada uno con la representacion que este sistema ya publica.
     *
     * <p>Devuelve una instancia nueva en cada llamada: un {@code SimpleModule} se registra en un
     * mapeador concreto y compartir el mismo entre dos seria acoplarlos por el objeto.
     */
    public static SimpleModule modulo() {
        SimpleModule modulo = new SimpleModule("kamayuk-objetos-de-valor");

        registrarDecimal(modulo, Dinero.class, d -> d.valor().toPlainString(), Dinero::de);
        registrarDecimal(modulo, Alicuota.class, a -> a.valor().toPlainString(), Alicuota::de);
        registrarDecimal(modulo, Porcentaje.class, p -> p.valor().toPlainString(), Porcentaje::de);
        registrarDecimal(modulo, AreaM2.class, a -> a.valor().toPlainString(), AreaM2::de);

        // Una `Medida` lleva la unidad DENTRO y un `AreaM2` no, y no es una incoherencia: es lo que
        // #607 decide y lo que `FrenteResource` y el `frontis` de `FichaResource` ya publican
        // —«18.50 ML»—. El barrido se determina sobre metros LINEALES y el recojo sobre metros
        // CUADRADOS, y leer unos por otros no falla: cobra otra cosa.
        //
        // Ningun DTO tiene hoy un componente de tipo `Medida` —los tres que la nombran la
        // convierten a mano—, asi que registrarla no cambia ni un byte del contrato HTTP; lo que
        // hace es que la bitacora pueda anotar una longitud sin componerla a mano.
        registrarTexto(modulo, Medida.class, Medida::toString, ObjetosDeValorEnJson::medidaDe);

        return modulo;
    }

    /**
     * La regla que la BITACORA anade y el transporte no necesita: ningun {@code BigDecimal} suelto
     * sale como numero JSON.
     *
     * <h2>Por que no se instala en el mapeador de Spring</h2>
     *
     * <p>Porque ahi no hace falta y no seria gratis. Por HTTP todo decimal viaja ya dentro de un
     * objeto de valor —{@code Dinero}, {@code AreaM2}, {@code Alicuota}, {@code Porcentaje}—, asi
     * que esta regla no cambiaria nada de lo que se publica; lo que si tocaria es el <b>cuerpo de
     * los eventos del buzon</b>, que otro sistema consume y cuya forma no se cambia sin que alguien
     * lo decida.
     *
     * <p>Y en la bitacora si hace falta: {@code auditoria.datos_nuevos} se republica
     * <b>verbatim</b> por {@code GET /seguridad/auditoria}, donde ningun esquema declara el tipo de
     * nada. Un {@code score} de {@code 0.9100} escrito como numero lo lee el navegador como {@code
     * 0.91}, y lo que la bitacora existe para poder decir es lo que se guardo, no lo que se parece
     * a lo que se guardo.
     */
    public static SimpleModule decimalesComoTexto() {
        SimpleModule modulo = new SimpleModule("kamayuk-decimales-como-texto");
        modulo.addSerializer(
                BigDecimal.class,
                new ValueSerializer<BigDecimal>() {
                    @Override
                    public void serialize(
                            BigDecimal valor,
                            JsonGenerator generador,
                            SerializationContext contexto) {
                        generador.writeString(valor.toPlainString());
                    }
                });
        return modulo;
    }

    /** «12.50 ML» de vuelta a su magnitud y su unidad. */
    private static Medida medidaDe(String texto) {
        String limpio = texto.strip();
        int separador = limpio.lastIndexOf(' ');
        if (separador < 0) {
            throw new IllegalArgumentException(
                    "Una medida es magnitud y unidad —«12.50 ML»—, y llego '" + texto + "'");
        }
        return Medida.de(limpio.substring(0, separador).strip(), limpio.substring(separador + 1));
    }

    private static <T> void registrarDecimal(
            SimpleModule modulo,
            Class<T> tipo,
            Function<T, String> aTexto,
            Function<String, T> desdeTexto) {

        registrarTexto(
                modulo,
                tipo,
                aTexto,
                // Se acepta tambien el numero, para no romper a un cliente que mande 100 en vez
                // de "100.00"; lo que no se hace nunca es *emitir* un numero. BigDecimal lee el
                // texto exacto.
                texto -> desdeTexto.apply(new BigDecimal(texto.trim()).toPlainString()));
    }

    private static <T> void registrarTexto(
            SimpleModule modulo,
            Class<T> tipo,
            Function<T, String> aTexto,
            Function<String, T> desdeTexto) {

        modulo.addSerializer(
                tipo,
                new ValueSerializer<T>() {
                    @Override
                    public void serialize(
                            T valor, JsonGenerator generador, SerializationContext contexto) {
                        generador.writeString(aTexto.apply(valor));
                    }
                });

        modulo.addDeserializer(
                tipo,
                new ValueDeserializer<T>() {
                    @Override
                    public T deserialize(JsonParser lector, DeserializationContext contexto) {
                        return desdeTexto.apply(lector.getValueAsString());
                    }
                });
    }
}
