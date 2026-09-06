package kamayuk.catastro.fiscalizacion.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.Evidencia;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import kamayuk.catastro.fiscalizacion.dominio.HuellaDeEvidencia;
import kamayuk.catastro.fiscalizacion.dominio.TipoDeEvidencia;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adjunta una evidencia a un hallazgo, con su huella y sus dos relojes (ADR-0035 punto 3).
 *
 * <h2>El reloj del servidor lo pone este metodo, y el del aparato llega de fuera</h2>
 *
 * <p>{@code recibidoEn} sale del {@link Clock} inyectado —es el instante en que la fila entra— y
 * {@code capturadoEn} es un argumento, porque lo sabe el dispositivo y nadie mas. Derivar uno del
 * otro es exactamente lo que ADR-0035 prohibe: haria inauditable la captura en campo.
 *
 * <p><b>No se comprueba que el del aparato sea anterior</b>, y es deliberado: el reloj de una
 * tableta puede ir adelantado, y rechazar la evidencia por eso perderia la foto y el dato de que el
 * reloj va mal. Lo que se hace es guardar los dos y dejar que {@code Evidencia.desfaseDeLosRelojes}
 * lo diga.
 *
 * <h2>Una foto no sustenta dos actas</h2>
 *
 * <p>Lo sostiene {@code evidencia_sha256_uq} y no el {@code if} de aqui: dos cargas simultaneas del
 * mismo archivo leerian las dos «no esta» y las dos entrarian. La comprobacion previa se queda
 * porque <b>nombra</b> la huella repetida, que es lo unico que aporta —quitarla deja el conflicto
 * en pie con un mensaje generico—.
 */
@Service
public class RegistrarEvidencia {

    private final FiscalizacionRepository repositorio;
    private final Auditoria auditoria;
    private final Clock reloj;

    public RegistrarEvidencia(
            FiscalizacionRepository repositorio, Auditoria auditoria, Clock reloj) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Adjunta la evidencia.
     *
     * @param capturadoEn el reloj del APARATO, tal como llego
     */
    @Transactional
    public Evidencia adjuntar(
            long hallazgoId,
            TipoDeEvidencia tipo,
            HuellaDeEvidencia huella,
            String ruta,
            Instant capturadoEn,
            @Nullable String dispositivo,
            Observacion observacion) {

        Hallazgo hallazgo =
                repositorio
                        .hallazgoPorId(hallazgoId)
                        .orElseThrow(() -> new HallazgoInexistente(hallazgoId));
        if (!hallazgo.estaFirme()) {
            throw new HallazgoSinEfecto(hallazgoId);
        }

        Evidencia evidencia;
        try {
            evidencia =
                    repositorio.guardar(
                            new Evidencia(
                                    null,
                                    hallazgoId,
                                    tipo,
                                    huella,
                                    ruta,
                                    capturadoEn,
                                    reloj.instant(),
                                    dispositivo));
        } catch (DuplicateKeyException repetida) {
            throw new HuellaRepetida(huella);
        }

        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj),
                                "evidencia",
                                String.valueOf(evidencia.id()),
                                Operacion.ALTA,
                                observacion)
                        .con(
                                null,
                                "{\"hallazgoId\":"
                                        + hallazgoId
                                        + ",\"tipo\":\""
                                        + tipo
                                        + "\",\"sha256\":\""
                                        + huella
                                        + "\",\"capturadoEn\":\""
                                        + capturadoEn
                                        + "\",\"recibidoEn\":\""
                                        + evidencia.recibidoEn()
                                        + "\"}"));
        return evidencia;
    }

    /** No hay hallazgo con ese identificador en esta municipalidad. */
    public static final class HallazgoInexistente extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        private final long hallazgoId;

        public HallazgoInexistente(long hallazgoId) {
            super("No hay ningun hallazgo " + hallazgoId + " en esta municipalidad");
            this.hallazgoId = hallazgoId;
        }

        public long hallazgoId() {
            return hallazgoId;
        }
    }

    /**
     * El hallazgo esta dejado sin efecto: ya no sustenta nada, asi que no admite evidencia nueva.
     */
    public static final class HallazgoSinEfecto extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        public HallazgoSinEfecto(long hallazgoId) {
            super(
                    "El hallazgo "
                            + hallazgoId
                            + " esta dejado sin efecto: ya no habilita ningun acto, asi que"
                            + " sustentarlo con mas evidencia no cambia nada");
        }
    }

    /** Esa huella ya sustenta otro hallazgo: una foto no sustenta dos actas. */
    public static final class HuellaRepetida extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        HuellaRepetida(HuellaDeEvidencia huella) {
            super(
                    "El archivo con huella "
                            + huella
                            + " ya sustenta otro hallazgo en esta municipalidad: una foto no"
                            + " sustenta dos actas (ADR-0035 punto 3)");
        }
    }
}
