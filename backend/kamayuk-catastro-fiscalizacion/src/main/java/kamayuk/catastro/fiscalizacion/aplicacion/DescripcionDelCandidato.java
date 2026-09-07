package kamayuk.catastro.fiscalizacion.aplicacion;

import kamayuk.catastro.auditoria.DatosDeAuditoria;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;

/**
 * El «antes» y el «despues» de un candidato, para la columna JSON de la auditoria.
 *
 * <p>Vive en su propia clase y no repetido en las dos compuertas porque una {@code MODIFICACION}
 * cuyo antes y despues se compongan de dos maneras distintas no se puede leer: la mitad de las
 * filas diria que cambio un campo que no cambio.
 *
 * <p><b>Los insumos no entran.</b> Son un JSON entero y pueden traer la huella de una ortofoto: en
 * la bitacora ocuparian mas que todo lo demas junto y no cambian nunca, asi que su sitio es la fila
 * del candidato, donde ya estan.
 *
 * <h2>Aqui vivia el segundo {@code escapar()}, y por eso #20 lo borro</h2>
 *
 * <p>Era byte a byte el mismo que el de {@code AbrirCampania} —dos copias de la misma funcion, que
 * es como se garantiza que una se arregle y la otra no— y los dos estaban <b>incompletos</b>:
 * cubrian {@code \} y {@code "} y ningun caracter de control. El {@code motivo} de un descarte lo
 * escribe una persona en un formulario y un salto de linea ahi rompia el {@code cast(… AS jsonb)},
 * con la transaccion entera revertida y un mensaje que hablaba de JSON y no del motivo.
 */
final class DescripcionDelCandidato {

    private DescripcionDelCandidato() {}

    static DatosDeAuditoria de(Candidato candidato) {
        Candidato.Descarte descarte = candidato.descarte();
        return DatosDeAuditoria.campos()
                .mas("clase", candidato.clase())
                .mas("origen", candidato.origen())
                .mas("score", candidato.score().valor())
                .mas("estado", candidato.estado())
                .mas("descarte", descarte == null ? null : descarteDe(descarte))
                .datos();
    }

    /**
     * El descarte anidado.
     *
     * <p>Un {@code record} y no otro {@code DatosDeAuditoria}: lo que se anida es un <b>valor</b>
     * dentro del objeto de arriba, no un objeto ya serializado. Con el {@code record}, quien decide
     * como se escribe cada campo sigue siendo el serializador, en un solo paso.
     */
    private static DescarteEnLaBitacora descarteDe(Candidato.Descarte descarte) {
        return new DescarteEnLaBitacora(
                descarte.etapa().name(), descarte.motivo(), descarte.quien());
    }

    /** Lo que la bitacora dice de un descarte: en que etapa fue, por que, y quien lo decidio. */
    private record DescarteEnLaBitacora(String etapa, String motivo, String quien) {}
}
