package kamayuk.catastro.auditoria;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import kamayuk.catastro.json.SerializadorDeJson;
import org.jspecify.annotations.Nullable;

/**
 * El «antes» o el «despues» de un asiento: un objeto JSON, y no puede ser otra cosa (#20).
 *
 * <h2>Por que existe este tipo y no basta con un {@code String}</h2>
 *
 * <p>Porque la columna es {@code jsonb} y {@link RegistroDeAuditoria} admitia {@code String}. Con
 * esa firma, pasar prosa compila, se despliega y revienta <b>en produccion</b>, en el {@code cast}
 * de {@link AuditoriaJdbc}: {@code POST /catastro/api/v1/frentes/{id}/confirmacion} devolvia 500 y
 * el derivador del perfil {@code batch} moria en el primer frente que conseguia proponer. Ninguna
 * prueba pasaba por ese {@code cast}, asi que el defecto vivio en verde desde #7.
 *
 * <p>Es la regla 2 de la casa aplicada a la bitacora —«si el desarrollador no lo maneja, no puede
 * olvidarlo»—: la unica forma de obtener un {@code DatosDeAuditoria} es {@link #campos()}, que pasa
 * cada valor por el serializador. Componer una cadena a mano ya no compila, y por eso los dos
 * {@code escapar()} que habia —duplicados y los dos incompletos, sin los caracteres de control— se
 * borraron en vez de arreglarse.
 *
 * <h2>Donde vive, y por que aqui</h2>
 *
 * <p>En {@code kamayuk.catastro.auditoria}, junto al registro del que es parametro. <b>No nombra
 * Jackson</b>: serializar es de {@link SerializadorDeJson}, en {@code kamayuk.catastro.json}. Asi
 * los casos de uso —que estan en {@code ..aplicacion..} y se prueban sin contexto de Spring— solo
 * ven este tipo, y la regla 7 sigue en pie: en el dominio no entra ni Jackson ni Spring.
 *
 * <h2>Lo que se comprueba al construirlo</h2>
 *
 * <p>Que el texto producido vuelva a leerse como un <b>objeto</b> JSON. No es paranoia barata: es
 * lo que convierte «lo que llega al {@code cast} es JSON valido» en algo <b>ejecutado</b> —en cada
 * asiento, tambien en produccion— y no en algo afirmado por un javadoc.
 */
public final class DatosDeAuditoria {

    private final String json;

    private DatosDeAuditoria(String json) {
        if (!SerializadorDeJson.esObjetoJson(json)) {
            throw new IllegalArgumentException(
                    "Lo que se asienta en `auditoria.datos_nuevos` es un OBJETO JSON, y salio: "
                            + json);
        }
        this.json = json;
    }

    /** Empieza a componer el antes o el despues, campo a campo. */
    public static Campos campos() {
        return new Campos();
    }

    /** El objeto JSON, tal como se le pasa al {@code cast(… AS jsonb)}. */
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
     * Los campos del asiento, en el orden en que se escriben.
     *
     * <p>Ordenado a proposito: la bitacora se lee comparando el antes con el despues, y dos objetos
     * con los mismos campos en distinto orden obligan a leer los dos enteros para ver que cambio.
     */
    public static final class Campos {

        private final Map<String, @Nullable Object> valores = new LinkedHashMap<>();

        private Campos() {}

        /**
         * Un campo mas.
         *
         * <p>El valor entra <b>tal cual</b> —un objeto de valor, un enumerado, una fecha, un
         * numero, otro objeto anidado o {@code null}— y lo escribe el serializador. Aqui no se
         * convierte nada a texto a mano, que es lo que #20 vino a quitar.
         *
         * @throws IllegalArgumentException si el nombre esta en blanco o ya estaba
         */
        public Campos mas(String nombre, @Nullable Object valor) {
            Objects.requireNonNull(nombre, "Un campo del asiento necesita su nombre");
            if (nombre.isBlank()) {
                throw new IllegalArgumentException("Un campo del asiento necesita su nombre");
            }
            if (valores.containsKey(nombre)) {
                // Un objeto JSON con la clave repetida es legal para el motor y ambiguo para quien
                // lo lea: la biblioteca del lector decide cual gana. Aqui no se decide: se falla.
                throw new IllegalArgumentException(
                        "El campo '" + nombre + "' ya estaba en este asiento");
            }
            valores.put(nombre, valor);
            return this;
        }

        /** Lo compuesto, ya serializado y comprobado. */
        public DatosDeAuditoria datos() {
            if (valores.isEmpty()) {
                throw new IllegalStateException(
                        "Un asiento sin ningun campo no explica nada: o tiene datos, o se pasa"
                                + " `null` a con(…)");
            }
            return new DatosDeAuditoria(SerializadorDeJson.texto(valores));
        }
    }
}
