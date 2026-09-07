package kamayuk.catastro.auditoria;

import java.time.LocalDate;
import java.util.Objects;
import kamayuk.catastro.dominio.Ejercicio;
import kamayuk.catastro.dominio.Observacion;
import org.jspecify.annotations.Nullable;

/**
 * Lo que se va a asentar en la auditoria.
 *
 * <p>La {@link Observacion} es un campo del registro y no un {@code String} opcional: es la
 * diferencia entre una regla que se cumple y una que se recuerda. Ver ADR-0008 y la regla 10.
 *
 * <p>El {@link Ejercicio} es la <b>clave de particion</b> de la tabla, asi que no es un dato
 * decorativo: si no coincide con ninguna particion, la insercion falla. Entra como argumento y no
 * se deduce del reloj, por la misma razon que las reglas tributarias no leen la hora: una
 * reejecucion tiene que producir el mismo resultado. Para el caso corriente esta {@link
 * #enLaFechaDe}.
 *
 * @param ejercicio ejercicio al que se imputa el acto; clave de particion
 * @param tabla tabla afectada
 * @param clave clave de la fila afectada, en texto
 * @param operacion que clase de acto es
 * @param observacion por que se hizo, escrito por quien lo hizo
 * @param datosAnteriores estado previo en JSON, si la operacion lo tenia; se compone con {@link
 *     DatosDeAuditoria}, que es lo unico que puede producirlo (#20)
 * @param datosNuevos estado resultante en JSON, si lo hay
 */
public record RegistroDeAuditoria(
        Ejercicio ejercicio,
        String tabla,
        String clave,
        Operacion operacion,
        Observacion observacion,
        @Nullable String datosAnteriores,
        @Nullable String datosNuevos) {

    private static final int TABLA_MAXIMO = 60;
    private static final int CLAVE_MAXIMO = 120;

    public RegistroDeAuditoria {
        Objects.requireNonNull(ejercicio, "La auditoria se particiona por ejercicio");
        Objects.requireNonNull(tabla, "Hay que decir sobre que tabla fue");
        Objects.requireNonNull(clave, "Hay que decir sobre que fila fue");
        Objects.requireNonNull(operacion, "Hay que decir que clase de acto fue");
        Objects.requireNonNull(
                observacion, "Sin observacion no se guarda (regla 10, ADR-0008, RNF-052)");
        tabla = tabla.strip();
        clave = clave.strip();
        if (tabla.isEmpty() || tabla.length() > TABLA_MAXIMO) {
            throw new IllegalArgumentException(
                    "El nombre de tabla va de 1 a "
                            + TABLA_MAXIMO
                            + " caracteres: '"
                            + tabla
                            + "'");
        }
        if (clave.isEmpty() || clave.length() > CLAVE_MAXIMO) {
            throw new IllegalArgumentException(
                    "La clave va de 1 a " + CLAVE_MAXIMO + " caracteres: '" + clave + "'");
        }
    }

    /** El caso corriente: el ejercicio sale de la fecha de la operacion, que entra como dato. */
    public static RegistroDeAuditoria enLaFechaDe(
            LocalDate fecha,
            String tabla,
            String clave,
            Operacion operacion,
            Observacion observacion) {
        return new RegistroDeAuditoria(
                Ejercicio.de(fecha), tabla, clave, operacion, observacion, null, null);
    }

    /**
     * El mismo registro con el antes y el despues.
     *
     * <p><b>Recibe {@link DatosDeAuditoria} y no un {@code String}, y ahi esta el arreglo de
     * #20.</b> Las dos columnas son {@code jsonb} y {@link AuditoriaJdbc} las escribe con {@code
     * cast(... AS jsonb)}: mientras esta firma admitio texto, el unico que comprobaba era el motor,
     * y comprobaba en produccion. Dos casos de uso le pasaban prosa y diez mas componian el JSON a
     * mano interpolando sin escapar, con el build en VERDE porque ninguna prueba llegaba al {@code
     * cast}. Con un tipo, el que se escriba manana no puede equivocarse — y no hay lista de
     * llamadores que se quede corta, que es como este defecto sobrevivio a una revision.
     */
    public RegistroDeAuditoria con(
            @Nullable DatosDeAuditoria datosAnteriores, @Nullable DatosDeAuditoria datosNuevos) {
        return new RegistroDeAuditoria(
                ejercicio,
                tabla,
                clave,
                operacion,
                observacion,
                datosAnteriores == null ? null : datosAnteriores.json(),
                datosNuevos == null ? null : datosNuevos.json());
    }
}
