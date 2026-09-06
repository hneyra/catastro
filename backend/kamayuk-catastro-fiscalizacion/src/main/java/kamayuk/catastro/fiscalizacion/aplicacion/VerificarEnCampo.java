package kamayuk.catastro.fiscalizacion.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.ClaseDeHallazgo;
import kamayuk.catastro.fiscalizacion.dominio.EtapaDeVerificacion;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import kamayuk.catastro.nucleo.LectorDeFichas;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>La segunda compuerta</b>: alguien fue y lo vio, y de ahi sale el hallazgo (ADR-0035).
 *
 * <h2>El atajo no existe, y esta es la clase donde no existe</h2>
 *
 * <p>{@link #confirmar} llama a {@link Candidato#verificadoEnCampo()}, que <b>rechaza</b> cualquier
 * candidato que no venga de gabinete. No es una comprobacion que se pueda quitar sin que se note:
 * el hallazgo cuelga del candidato por clave foranea y el acta cuelga del hallazgo, asi que un acta
 * sin las dos compuertas no tiene de que colgar. Lo mide {@code ElAtajoNoExisteTest}, que lo
 * intenta.
 *
 * <h2>Aqui se fija QUE VERSION de ficha se contrasto (AC 4)</h2>
 *
 * <p>La version se resuelve <b>al verificar</b> con {@link LectorDeFichas#fichaVigenteEn}, y su
 * area se <b>copia</b> con {@link LectorDeFichas#areaDeLaVersion}. Las dos cosas por el mismo
 * motivo: la ficha se versiona, asi que un hallazgo de marzo releido en julio compararia contra
 * otra cosa y afirmaria una diferencia que nadie hallo.
 *
 * <p>{@code LectorDeFichas} es la <b>unica</b> arista de este contexto hacia otro, devuelve
 * identificador y area —ni un metodo que escriba— y esta declarada una a una en {@code
 * tiposAjenosQueFiscalizacionSoloLee()}. Lo vigila {@code
 * SOLO_LA_TRANSFERENCIA_ESCRIBE_FUERA_DE_FISCALIZACION}.
 *
 * <h2>Lo que confirmar NO hace</h2>
 *
 * <p>No versiona la ficha, no inscribe el predio y no emite nada. Un hallazgo firme <b>habilita</b>
 * esos actos y no los ejecuta (ADR-0035 punto 4): quien los ejecuta es una persona, por el camino
 * que ya existe y con su propia observacion.
 */
@Service
public class VerificarEnCampo {

    private final FiscalizacionRepository repositorio;
    private final LectorDeFichas fichas;
    private final Auditoria auditoria;
    private final Clock reloj;

    public VerificarEnCampo(
            FiscalizacionRepository repositorio,
            LectorDeFichas fichas,
            Auditoria auditoria,
            Clock reloj) {
        this.repositorio = repositorio;
        this.fichas = fichas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Lo confirma: el candidato pasa a verificado y nace su hallazgo, en el mismo acto.
     *
     * <p>Las dos escrituras y sus dos filas de auditoria llevan la <b>misma</b> observacion: es un
     * acto, no dos (regla 10).
     *
     * @param areaVerificada lo que la brigada midio
     * @param geometria el poligono levantado en campo, en WKT; nulo si no se levanto ninguno
     * @throws Candidato.TransicionQueNoExiste si el candidato no paso antes por gabinete
     */
    @Transactional
    public Hallazgo confirmar(
            long candidatoId,
            AreaM2 areaVerificada,
            String inspector,
            @Nullable String geometria,
            Observacion observacion) {

        Candidato anterior = leer(candidatoId);
        Candidato verificado = repositorio.guardar(anterior.verificadoEnCampo());
        LocalDate hoy = LocalDate.now(reloj);

        Hallazgo hallazgo =
                repositorio.guardar(
                        anterior.clase() == ClaseDeHallazgo.SUBVALUADOR
                                ? deSubvaluador(anterior, areaVerificada, inspector, geometria, hoy)
                                : Hallazgo.deOmisoCatastral(
                                        idDe(anterior), areaVerificada, inspector, hoy, geometria));

        asentarCandidato(anterior, verificado, observacion, hoy);
        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                hoy,
                                "hallazgo",
                                String.valueOf(hallazgo.id()),
                                Operacion.ALTA,
                                observacion)
                        .con(null, descripcion(hallazgo)));
        return hallazgo;
    }

    /**
     * Lo descarta en campo, con su motivo.
     *
     * <p>Solo se puede descartar en campo lo que gabinete admitio, y {@link Candidato#descartadoEn}
     * lo rechaza si no: contar como «descarte de campo» algo que nadie miro en gabinete falsearia
     * la cifra que ADR-0035 punto 5 existe para poder medir.
     */
    @Transactional
    public Candidato descartar(long candidatoId, String motivo, Observacion observacion) {
        Candidato anterior = leer(candidatoId);
        Candidato descartado =
                repositorio.guardar(
                        anterior.descartadoEn(
                                EtapaDeVerificacion.CAMPO,
                                motivo,
                                kamayuk.catastro.auditoria.OrigenContext.actual().usuario(),
                                reloj.instant()));
        asentarCandidato(anterior, descartado, observacion, LocalDate.now(reloj));
        return descartado;
    }

    /**
     * El hallazgo de un subvaluador, con la version de ficha resuelta <b>dentro</b> de la
     * transaccion.
     *
     * <p>Resolverla fuera correria sin el {@code SET LOCAL} que RLS exige y contestaria un 500 en
     * vez de la ficha (#486). Es el mismo defecto que la marcha blanca destapo en {@code GET
     * /catastro/vias}.
     */
    private Hallazgo deSubvaluador(
            Candidato candidato,
            AreaM2 areaVerificada,
            String inspector,
            @Nullable String geometria,
            LocalDate hoy) {

        long predioId =
                Objects.requireNonNull(
                        candidato.predioId(), "Un candidato SUBVALUADOR siempre tiene predio");
        long fichaId =
                fichas.fichaVigenteEn(predioId, hoy)
                        .orElseThrow(() -> new PredioSinFichaQueContrastar(predioId, hoy));
        AreaM2 areaDeLaFicha =
                fichas.areaDeLaVersion(fichaId)
                        .orElseThrow(() -> new PredioSinFichaQueContrastar(predioId, hoy));

        return Hallazgo.deSubvaluador(
                idDe(candidato),
                predioId,
                fichaId,
                areaDeLaFicha,
                areaVerificada,
                inspector,
                hoy,
                geometria);
    }

    private Candidato leer(long candidatoId) {
        return repositorio
                .candidatoPorId(candidatoId)
                .orElseThrow(() -> new VerificarEnGabinete.CandidatoInexistente(candidatoId));
    }

    private static long idDe(Candidato candidato) {
        return Objects.requireNonNull(candidato.id(), "El candidato leido tiene identificador");
    }

    private void asentarCandidato(
            Candidato anterior, Candidato despues, Observacion observacion, LocalDate hoy) {
        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                hoy,
                                "candidato",
                                String.valueOf(despues.id()),
                                Operacion.MODIFICACION,
                                observacion)
                        .con(
                                DescripcionDelCandidato.de(anterior),
                                DescripcionDelCandidato.de(despues)));
    }

    private static String descripcion(Hallazgo hallazgo) {
        return "{\"clase\":\""
                + hallazgo.clase()
                + "\",\"candidatoId\":"
                + hallazgo.candidatoId()
                + ",\"fichaId\":"
                + (hallazgo.fichaId() == null ? "null" : hallazgo.fichaId())
                + ",\"areaDeLaFicha\":"
                + (hallazgo.areaDeLaFicha() == null ? "null" : hallazgo.areaDeLaFicha().valor())
                + ",\"areaVerificada\":"
                + hallazgo.areaVerificada().valor()
                + ",\"inspector\":\""
                + hallazgo.inspector()
                + "\"}";
    }

    /**
     * El predio no tiene ficha vigente a la fecha, asi que no hay version que contrastar.
     *
     * <p>No se inventa una: un hallazgo de subvaluacion sin la version que se contrasto es un
     * hallazgo que no se puede releer, y eso es lo que AC 4 existe para impedir. Lo que hay que
     * hacer entonces es ficharlo, que es otro acto.
     */
    public static final class PredioSinFichaQueContrastar extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        PredioSinFichaQueContrastar(long predioId, LocalDate fecha) {
            super(
                    "El predio "
                            + predioId
                            + " no tiene ficha vigente al "
                            + fecha
                            + ": no hay version que contrastar, y un hallazgo sin ella no se puede"
                            + " releer despues");
        }
    }
}
