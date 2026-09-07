package kamayuk.catastro.auditoria;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import kamayuk.catastro.dominio.Alicuota;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Dinero;
import kamayuk.catastro.dominio.Porcentaje;
import kamayuk.catastro.web.ConfiguracionDeJson;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * El «antes» y el «despues» que van a las dos columnas {@code jsonb} de la bitacora (#20).
 *
 * <h2>Por que esto es un tipo y no un {@code String}</h2>
 *
 * <p>{@code auditoria.datos_anteriores} y {@code datos_nuevos} son {@code jsonb}, y {@link
 * AuditoriaJdbc} las escribe con {@code cast(:datosAnteriores AS jsonb)}. Mientras la firma admitio
 * un {@code String}, el motor era el unico que comprobaba, y comprobaba <b>en produccion</b>: dos
 * casos de uso le pasaban prosa —{@code ConfirmarElFrente} y {@code ProponerLosFrentesDeUnPredio}—
 * y diez mas componian el JSON a mano interpolando texto sin escapar, de modo que un inspector
 * llamado {@code Juan "El Tuerto" Perez} hacia imposible levantar su acta. El build estaba en
 * VERDE: ninguna prueba pasaba por ese {@code cast}.
 *
 * <p>Se corta <b>en la firma</b> de {@link RegistroDeAuditoria#con} y no validando el {@code
 * String} en el constructor, y el motivo es la diferencia entre los dos: validar en el constructor
 * convierte el defecto en un fallo de <b>ejecucion</b>, que solo aparece cuando alguien ejerce ese
 * camino — que es exactamente el estado del que sale este issue. Un tipo lo convierte en un fallo
 * de <b>compilacion</b>, y entonces el llamador que se escriba manana no puede equivocarse: no hay
 * lista de llamadores que se pueda quedar corta.
 *
 * <p><b>No hay ninguna fabrica que acepte texto.</b> Un {@code deJson(String)} devolveria el
 * defecto entero por la puerta de atras, y ademas seria la unica linea que alguien copiaria.
 *
 * <h2>Lo escribe el serializador que ya existe</h2>
 *
 * <p>{@link ConfiguracionDeJson#moduloDeObjetosDeValor()}, el mismo que la capa web: asi un {@code
 * AreaM2} se asienta igual en la bitacora que como sale por HTTP —la cifra sola, como cadena y
 * nunca como numero JSON (#607, RNF-055)—, y no hay dos definiciones que puedan divergir.
 *
 * <h2>Admite solo lo que la columna admite</h2>
 *
 * <p>{@link Composicion#campo} rechaza cualquier tipo que no este en la lista, <b>nombrandolo</b>.
 * Sin eso, pasar una entidad entera meteria media tabla en la bitacora y Jackson la serializaria
 * sin protestar: el JSON seria valido y el asiento, ilegible.
 *
 * <p>Las fechas se convierten aqui con {@code toString()} y no se dejan a Jackson: es lo que las
 * diez descripciones escritas a mano hacian —{@code "\"vigenciaDesde\":\"" + certificado
 * .vigenciaDesde()}—, asi que el byte no cambia, y no queda colgando de un valor por omision del
 * serializador que una version futura podria mover.
 */
public final class DatosDeAuditoria {

    /**
     * El mapeador es <b>uno</b> y es inmutable: Jackson 3 lo garantiza, asi que compartirlo entre
     * hilos es correcto y construir uno por asiento seria caro para nada.
     */
    private static final JsonMapper MAPEADOR =
            JsonMapper.builder().addModule(ConfiguracionDeJson.moduloDeObjetosDeValor()).build();

    private final String json;

    private DatosDeAuditoria(String json) {
        this.json = json;
    }

    /** Empieza a componer un objeto JSON. Es el unico camino que hay. */
    public static Composicion objeto() {
        return new Composicion();
    }

    /** El JSON, tal como va al {@code cast(... AS jsonb)}. */
    public String json() {
        return json;
    }

    @Override
    public String toString() {
        return json;
    }

    @Override
    public boolean equals(@Nullable Object otro) {
        return otro instanceof DatosDeAuditoria datos && json.equals(datos.json);
    }

    @Override
    public int hashCode() {
        return json.hashCode();
    }

    /**
     * Los campos del asiento, en el orden en que se declaran.
     *
     * <p>Es un {@link LinkedHashMap} y no un {@code Map.of} por dos motivos que se notan al leer la
     * bitacora: el orden de los campos es el que escribio quien compuso —una {@code MODIFICACION}
     * cuyo antes y despues salgan con los campos en distinto orden no se puede comparar de un
     * vistazo— y un campo puede valer {@code null}, que es lo que distingue «no tiene fin» de «no
     * se anoto el fin».
     */
    public static final class Composicion {

        private final Map<String, @Nullable Object> campos = new LinkedHashMap<>();

        private Composicion() {}

        /**
         * Anade un campo.
         *
         * <p>Se llama {@code campo} y no {@code con} <b>a proposito, y lo decidio una medida</b>:
         * con los dos metodos llamandose igual, el escaner de {@code LaBitacoraSoloRecibeJsonTest}
         * no podia distinguir el {@code .con(...)} que asienta —donde un literal de texto es el
         * defecto— del que declara un campo —donde el literal es el nombre del campo y es
         * correcto—, y marcaba los dieciocho compositores. Dos nombres distintos para dos cosas
         * distintas es lo que hace que la guarda pueda mirar.
         *
         * @throws IllegalArgumentException si el valor no es de un tipo que la bitacora sepa
         *     asentar, o si el campo se repite
         */
        public Composicion campo(String campo, @Nullable Object valor) {
            Objects.requireNonNull(campo, "Un campo de la bitacora necesita su nombre");
            if (campos.containsKey(campo)) {
                throw new IllegalArgumentException(
                        "El campo '"
                                + campo
                                + "' ya esta en este asiento: el segundo taparia al primero y la"
                                + " bitacora diria que solo hubo uno");
            }
            campos.put(campo, aValorAsentable(campo, valor));
            return this;
        }

        /** Cierra la composicion. */
        public DatosDeAuditoria componer() {
            return new DatosDeAuditoria(MAPEADOR.writeValueAsString(campos));
        }

        private static @Nullable Object aValorAsentable(String campo, @Nullable Object valor) {
            return switch (valor) {
                case null -> null;
                case String texto -> texto;
                case Boolean booleano -> booleano;
                case Number numero -> numero;
                case Enum<?> constante -> constante.name();
                case AreaM2 area -> area;
                case Dinero dinero -> dinero;
                case Alicuota alicuota -> alicuota;
                case Porcentaje porcentaje -> porcentaje;
                case LocalDate fecha -> fecha.toString();
                case Instant instante -> instante.toString();
                case OffsetDateTime momento -> momento.toString();
                case Composicion anidada -> anidada.campos;
                case DatosDeAuditoria ya ->
                        throw new IllegalArgumentException(
                                "El campo '"
                                        + campo
                                        + "' recibe unos datos ya compuestos: para anidar un objeto"
                                        + " se pasa la Composicion, sin cerrarla con componer()");
                default ->
                        throw new IllegalArgumentException(
                                "La bitacora no sabe asentar un "
                                        + valor.getClass().getName()
                                        + " (campo '"
                                        + campo
                                        + "'). Se asienta un dato, no una entidad: pasa sus campos"
                                        + " uno a uno, o el objeto de valor que ConfiguracionDeJson"
                                        + " ya sabe escribir");
            };
        }
    }
}
