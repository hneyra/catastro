package kamayuk.catastro.fiscalizacion.aplicacion;

import kamayuk.catastro.fiscalizacion.dominio.Candidato;

/**
 * El «antes» y el «despues» de un candidato, para la columna JSON de la auditoria.
 *
 * <p>Escrito a mano y no con un serializador, por lo mismo que en {@code RegistrarSector}: son
 * cinco campos, y traer Jackson hasta la capa de aplicacion la ataria a la de presentacion.
 *
 * <p>Vive en su propia clase y no repetido en las dos compuertas porque una {@code MODIFICACION}
 * cuyo antes y despues se compongan de dos maneras distintas no se puede leer: la mitad de las
 * filas diria que cambio un campo que no cambio.
 *
 * <p><b>Los insumos no entran.</b> Son un JSON entero y pueden traer la huella de una ortofoto: en
 * la bitacora ocuparian mas que todo lo demas junto y no cambian nunca, asi que su sitio es la fila
 * del candidato, donde ya estan.
 */
final class DescripcionDelCandidato {

    private DescripcionDelCandidato() {}

    static String de(Candidato candidato) {
        Candidato.Descarte descarte = candidato.descarte();
        return "{\"clase\":\""
                + candidato.clase()
                + "\",\"origen\":\""
                + candidato.origen()
                + "\",\"score\":"
                + candidato.score()
                + ",\"estado\":\""
                + candidato.estado()
                + "\",\"descarte\":"
                + (descarte == null
                        ? "null"
                        : "{\"etapa\":\""
                                + descarte.etapa()
                                + "\",\"motivo\":\""
                                + escapar(descarte.motivo())
                                + "\",\"quien\":\""
                                + escapar(descarte.quien())
                                + "\"}")
                + "}";
    }

    private static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
