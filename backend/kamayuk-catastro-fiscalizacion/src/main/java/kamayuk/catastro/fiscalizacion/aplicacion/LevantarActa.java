package kamayuk.catastro.fiscalizacion.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.Acta;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.EstadoDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Levanta el acta de un hallazgo firme.
 *
 * <h2>Las dos compuertas, comprobadas donde se pueden comprobar</h2>
 *
 * <p>El acta cuelga de un hallazgo y el hallazgo cuelga de un candidato {@code
 * VERIFICADO_EN_CAMPO}, asi que las dos compuertas ya estan detras por construccion. Lo que este
 * metodo <b>ademas</b> comprueba —y por eso lo comprueba— es que el candidato del que salio siga
 * verificado: es la unica afirmacion que la clave foranea no sostiene, porque una foranea dice que
 * la fila existe y no en que estado esta.
 *
 * <p>Y no hay ningun camino que salte esto: no existe un metodo que levante un acta desde un
 * candidato. Lo mide {@code ElAtajoNoExisteTest}, que lo intenta desde los dos lados.
 *
 * <h2>Nunca automatica</h2>
 *
 * <p>Este caso de uso no lo llama ningun proceso por lotes, y esa es la mitad del ADR que no es una
 * tabla: «techo en la ortofoto y no en el padron = omiso» es cierto como intuicion y falso como
 * regla de produccion. Sin las dos compuertas la municipalidad emite miles de valores que se caen
 * en reclamacion, y eso cuesta mas de lo que recupera.
 *
 * <p><b>Ni un importe.</b> El acta dice que se hallo y quien lo hallo. Lo que se cobre lo decide
 * `rentas` (ADR-0024).
 */
@Service
public class LevantarActa {

    private final FiscalizacionRepository repositorio;
    private final Auditoria auditoria;
    private final Clock reloj;

    public LevantarActa(FiscalizacionRepository repositorio, Auditoria auditoria, Clock reloj) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Levanta el acta.
     *
     * @param numero el numero del papel; lo pone quien lo levanta, y es unico por municipalidad
     */
    @Transactional
    public Acta levantar(
            long hallazgoId,
            String numero,
            String inspector,
            String detalle,
            Observacion observacion) {

        Hallazgo hallazgo =
                repositorio
                        .hallazgoPorId(hallazgoId)
                        .orElseThrow(() -> new RegistrarEvidencia.HallazgoInexistente(hallazgoId));
        if (!hallazgo.estaFirme()) {
            throw new RegistrarEvidencia.HallazgoSinEfecto(hallazgoId);
        }
        exigirLasDosCompuertas(hallazgo);

        Acta acta;
        try {
            acta =
                    repositorio.guardar(
                            Acta.nueva(
                                    numero, hallazgoId, LocalDate.now(reloj), inspector, detalle));
        } catch (DuplicateKeyException repetida) {
            throw new ActaRepetida(numero, hallazgoId);
        }

        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj),
                                "acta",
                                String.valueOf(acta.id()),
                                Operacion.ALTA,
                                observacion)
                        .con(
                                null,
                                "{\"numero\":\""
                                        + numero
                                        + "\",\"hallazgoId\":"
                                        + hallazgoId
                                        + ",\"inspector\":\""
                                        + inspector
                                        + "\"}"));
        return acta;
    }

    /**
     * El candidato del que salio el hallazgo sigue verificado en campo.
     *
     * <p>Es lo unico que la clave foranea no dice: una foranea afirma que la fila existe, no en que
     * estado esta. Sin esto, dejar el candidato en otro estado y levantar el acta despues seria un
     * acta sostenida por algo que ya no sostiene nada.
     */
    private void exigirLasDosCompuertas(Hallazgo hallazgo) {
        Candidato candidato =
                repositorio
                        .candidatoPorId(hallazgo.candidatoId())
                        .orElseThrow(
                                () ->
                                        new VerificarEnGabinete.CandidatoInexistente(
                                                hallazgo.candidatoId()));
        if (candidato.estado() != EstadoDelCandidato.VERIFICADO_EN_CAMPO) {
            throw new SinLasDosCompuertas(candidato.estado());
        }
    }

    /** Se intento levantar un acta sobre algo que no paso las dos compuertas. */
    public static final class SinLasDosCompuertas extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        private final EstadoDelCandidato estado;

        SinLasDosCompuertas(EstadoDelCandidato estado) {
            super(
                    "Su candidato esta "
                            + estado
                            + " y no VERIFICADO_EN_CAMPO: un acta se levanta sobre lo que gabinete"
                            + " admitio y campo confirmo. Una ortofoto detecta techos, no predios"
                            + " (ADR-0035)");
            this.estado = estado;
        }

        public EstadoDelCandidato estado() {
            return estado;
        }
    }

    /** Ese numero de acta ya esta tomado, o el hallazgo ya tiene la suya. */
    public static final class ActaRepetida extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        ActaRepetida(String numero, long hallazgoId) {
            super(
                    "El acta '"
                            + numero
                            + "' ya existe en esta municipalidad, o el hallazgo "
                            + hallazgoId
                            + " ya tiene la suya: dos actas del mismo hallazgo serian dos papeles"
                            + " que dicen lo mismo y dos plazos para el administrado");
        }
    }
}
