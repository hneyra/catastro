package kamayuk.catastro.fiscalizacion.aplicacion;

import kamayuk.catastro.auditoria.DatosDeAuditoria;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;

/**
 * El «antes» y el «despues» de un candidato, para la columna JSON de la auditoria.
 *
 * <p>Lo escribe el serializador de {@code ConfiguracionDeJson} desde #20. Se escribia a mano, con
 * un {@code escapar()} identico byte a byte al de {@code AbrirCampania} y tan incompleto como el:
 * un motivo de descarte con un salto de linea dentro producia texto que la columna {@code jsonb}
 * rechaza, y con el la transaccion entera se deshacia.
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

    static DatosDeAuditoria de(Candidato candidato) {
        Candidato.Descarte descarte = candidato.descarte();
        return DatosDeAuditoria.objeto()
                .campo("clase", candidato.clase())
                .campo("origen", candidato.origen())
                .campo("score", candidato.score().valor())
                .campo("estado", candidato.estado())
                .campo("descarte", descarte == null ? null : descarteDe(descarte))
                .componer();
    }

    /** El descarte va anidado y no aplanado: es opcional entero, no campo a campo. */
    private static DatosDeAuditoria.Composicion descarteDe(Candidato.Descarte descarte) {
        return DatosDeAuditoria.objeto()
                .campo("etapa", descarte.etapa())
                .campo("motivo", descarte.motivo())
                .campo("quien", descarte.quien());
    }
}
