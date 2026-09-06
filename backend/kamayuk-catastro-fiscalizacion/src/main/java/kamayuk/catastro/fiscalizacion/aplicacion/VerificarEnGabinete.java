package kamayuk.catastro.fiscalizacion.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.EtapaDeVerificacion;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>La primera compuerta</b>: alguien mira el insumo contra lo que el padron ya dice (ADR-0035).
 *
 * <p>Lo que se descarta aqui es lo que una ortofoto no puede distinguir y una persona con el padron
 * delante si: una ampliacion ya declarada, un predio conciliado con otro codigo, un toldo. Es la
 * mitad barata del filtro —no hay que mover una brigada— y por eso va primero.
 *
 * <p><b>Admitir y descartar son dos metodos y no un booleano</b>, por lo mismo que {@code
 * RegistrarSector} tiene {@code registrar} y {@code editar}: son dos operaciones de auditoria
 * distintas, y un solo metodo con bandera acabaria asentando la misma para las dos.
 *
 * <p>Las dos escrituras exigen su {@link Observacion} (regla 10) — cada transicion de estado de un
 * candidato es una escritura, y la de un descarte es <b>ademas</b> el motivo que ADR-0035 punto 5
 * manda conservar.
 */
@Service
public class VerificarEnGabinete {

    private final FiscalizacionRepository repositorio;
    private final Auditoria auditoria;
    private final Clock reloj;

    public VerificarEnGabinete(
            FiscalizacionRepository repositorio, Auditoria auditoria, Clock reloj) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Lo admite: pasa a la cola de campo.
     *
     * <p>Admitir <b>no</b> produce ningun hallazgo y no habilita ningun acto. Lo unico que dice es
     * que merece que alguien vaya a verlo.
     */
    @Transactional
    public Candidato admitir(long candidatoId, Observacion observacion) {
        Candidato anterior = leer(candidatoId);
        Candidato admitido = repositorio.guardar(anterior.admitidoEnGabinete());
        asentar(anterior, admitido, observacion);
        return admitido;
    }

    /**
     * Lo descarta en gabinete, con su motivo.
     *
     * <p>La fila <b>se queda</b>. Es la regla 4, y ademas es lo unico que permite medir si el
     * umbral de deteccion sirve: un descarte borrado es un modelo que nadie puede calibrar.
     *
     * @param motivo por que no prospera; se guarda en la fila, ademas de en la auditoria, porque la
     *     tasa de descarte se consulta desde la tabla y no desde la bitacora
     */
    @Transactional
    public Candidato descartar(long candidatoId, String motivo, Observacion observacion) {
        Candidato anterior = leer(candidatoId);
        Candidato descartado =
                repositorio.guardar(
                        anterior.descartadoEn(
                                EtapaDeVerificacion.GABINETE,
                                motivo,
                                usuarioDelActo(),
                                reloj.instant()));
        asentar(anterior, descartado, observacion);
        return descartado;
    }

    private Candidato leer(long candidatoId) {
        return repositorio
                .candidatoPorId(candidatoId)
                .orElseThrow(() -> new CandidatoInexistente(candidatoId));
    }

    /**
     * Quien decide, del contexto de origen y no de un argumento.
     *
     * <p>Un {@code String usuario} en la firma se rellena con lo que sea el dia que corra prisa, y
     * ademas seria la segunda fuente de un dato que la auditoria ya toma del borde de la
     * aplicacion. Aqui se lee del mismo sitio.
     */
    private static String usuarioDelActo() {
        return kamayuk.catastro.auditoria.OrigenContext.actual().usuario();
    }

    private void asentar(Candidato anterior, Candidato despues, Observacion observacion) {
        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj),
                                "candidato",
                                String.valueOf(despues.id()),
                                Operacion.MODIFICACION,
                                observacion)
                        .con(
                                DescripcionDelCandidato.de(anterior),
                                DescripcionDelCandidato.de(despues)));
    }

    /** No hay candidato con ese identificador en esta municipalidad. */
    public static final class CandidatoInexistente extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        private final long candidatoId;

        public CandidatoInexistente(long candidatoId) {
            super("No hay ningun candidato " + candidatoId + " en esta municipalidad");
            this.candidatoId = candidatoId;
        }

        public long candidatoId() {
            return candidatoId;
        }
    }
}
