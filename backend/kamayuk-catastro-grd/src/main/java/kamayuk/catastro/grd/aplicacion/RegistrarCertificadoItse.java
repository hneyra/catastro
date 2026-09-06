package kamayuk.catastro.grd.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.grd.dominio.CertificadoItse;
import kamayuk.catastro.grd.dominio.GestionDeRiesgoRepository;
import kamayuk.catastro.grd.dominio.PredioDesconocido;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El alta de un certificado ITSE sobre un predio (#5).
 *
 * <h2>Por que este SI entra por ventanilla y la zona de riesgo no</h2>
 *
 * <p>Una carta de peligro y una faja marginal las produce alguien de fuera —CENEPRED y la ANA— y
 * llegan como un archivo con miles de poligonos: entran por carga batch, igual que la geometria del
 * predio (ADR-0021), y por eso {@code TODA_GEOMETRIA_ENTRA_POR_BATCH} las cubre. Un ITSE lo emite
 * <b>la propia municipalidad</b>, de uno en uno, y no tiene geometria: si no entrara por aqui, la
 * tabla la escribiria solo un archivo y el certificado que Defensa Civil firmo esta manana no
 * existiria hasta la siguiente carga.
 *
 * <p><b>La observacion es obligatoria y esta en la firma</b> (regla 10, RNF-052): no se puede
 * llamar a este metodo sin decir por que.
 *
 * <p><b>El predio se comprueba DENTRO de la transaccion</b> (#486): leerlo es una consulta, y una
 * consulta fuera de transaccion corre sin el {@code SET LOCAL} que la politica RLS exige. Y no es
 * lo que impide la fila huerfana —eso lo hace {@code itse_predio_fk}—: lo que aporta es
 * <b>nombrar</b> el predio que no existe, en vez de un choque de restriccion que quien atiende no
 * sabe leer (la leccion de #188).
 */
@Service
public class RegistrarCertificadoItse {

    private final GestionDeRiesgoRepository repositorio;
    private final Auditoria auditoria;
    private final Clock reloj;

    public RegistrarCertificadoItse(
            GestionDeRiesgoRepository repositorio, Auditoria auditoria, Clock reloj) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional
    public CertificadoItse registrar(CertificadoItse certificado, Observacion observacion) {
        if (!repositorio.estadoDelLote(certificado.predioId()).existe()) {
            throw new PredioDesconocido(certificado.predioId());
        }
        CertificadoItse guardado = repositorio.guardar(certificado);
        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj),
                                "itse",
                                String.valueOf(guardado.id()),
                                Operacion.ALTA,
                                observacion)
                        .con(null, descripcion(guardado)));
        return guardado;
    }

    /** El estado resultante, en JSON, igual que hacen los demas casos de uso de este sistema. */
    private static String descripcion(CertificadoItse certificado) {
        return "{\"numero\":\""
                + certificado.numero()
                + "\",\"predioId\":"
                + certificado.predioId()
                + ",\"nivelRiesgo\":\""
                + certificado.nivelRiesgo()
                + "\",\"modalidad\":\""
                + certificado.modalidad()
                + "\",\"vigenciaDesde\":\""
                + certificado.vigenciaDesde()
                + "\",\"vigenciaHasta\":\""
                + certificado.vigenciaHasta()
                + "\"}";
    }
}
