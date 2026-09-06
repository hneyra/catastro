package kamayuk.catastro.fiscalizacion.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.ClaseDeHallazgo;
import kamayuk.catastro.fiscalizacion.dominio.ContrasteDeAreas;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.OrigenDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import kamayuk.catastro.fiscalizacion.dominio.Tolerancia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El detector de subvaluadores: contrasta {@code ficha_catastral.area_terreno} contra el area del
 * poligono y produce <b>candidatos</b> (AC 8 de #6, ADR-0021, ADR-0035 punto 1).
 *
 * <h2>Candidatos, nunca correcciones</h2>
 *
 * <p>Lo que este proceso escribe son filas de {@code candidato}, y ninguna otra cosa. No toca la
 * ficha, no toca el predio y no emite ningun acta. ADR-0021 lo dice con todas las letras: que las
 * dos areas no coincidan es un <b>hallazgo que se informa</b>, no una correccion que se aplica —y
 * ni siquiera un hallazgo todavia: es una sospecha que dos personas tienen que confirmar—.
 *
 * <h2>Sin poligonos dice que NO PUEDE, y no devuelve cero</h2>
 *
 * <p>Hoy no hay ni un poligono cargado en ninguna instalacion. Un detector que sobre esa base
 * devolviera una lista vacia estaria afirmando «no hay subvaluadores»: la campania se cerraria con
 * cero hallazgos, la conclusion seria que el padron esta bien, y <b>nadie va a revisar un cero</b>.
 * Por eso {@link AreasDelPadron#contrastar} lanza {@link AreasDelPadron.SinCartografia} y este caso
 * de uso la deja pasar en vez de atraparla: quien la recibe sabe que hacer, y una lista vacia no
 * dice nada.
 *
 * <h2>El score, y por que no lo inventa este metodo</h2>
 *
 * <p>El score de un contraste de areas <b>es</b> la diferencia relativa, acotada a 1: no hay ningun
 * modelo detras y no se finge que lo haya. Lo unico que decide es a quien mira primero el gabinete.
 * Ninguna de estas cifras es tributaria (regla 5): no entran en nada que se cobre.
 *
 * <p>Corre en el perfil {@code batch} —lo lanza quien abre la campania— y no en la web: recorre el
 * padron. Escribe, asi que exige su {@link Observacion} (regla 10): es la misma para las N filas,
 * porque es <b>un</b> acto —«corrida de deteccion de la campania X»— y no N.
 */
@Service
public class DetectarSubvaluadores {

    private final FiscalizacionRepository repositorio;
    private final AreasDelPadron areas;
    private final Auditoria auditoria;
    private final Clock reloj;

    public DetectarSubvaluadores(
            FiscalizacionRepository repositorio,
            AreasDelPadron areas,
            Auditoria auditoria,
            Clock reloj) {
        this.repositorio = repositorio;
        this.areas = areas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Corre la deteccion sobre la campania y deja sus candidatos.
     *
     * @param toleranciaEnPorciento cuanto se admite que difieran sin sospechar
     * @param tope cuantos predios como mucho
     * @throws AreasDelPadron.SinCartografia si la municipalidad no tiene un solo poligono
     */
    @Transactional
    public List<Candidato> detectar(
            long campaniaId, Tolerancia tolerancia, int tope, Observacion observacion) {

        Campania campania =
                repositorio
                        .campaniaPorId(campaniaId)
                        .orElseThrow(() -> new AbrirCampania.CampaniaInexistente(campaniaId));
        if (!campania.admiteCandidatos()) {
            throw new CampaniaCerradaParaDetectar(campaniaId);
        }

        // Deja pasar SinCartografia a proposito: ver el javadoc de la clase.
        List<ContrasteDeAreas> contrastes = areas.contrastar(tolerancia, tope);

        List<Candidato> detectados = new ArrayList<>();
        for (ContrasteDeAreas contraste : contrastes) {
            Score score = contraste.diferenciaRelativa();
            if (!score.alcanza(campania.umbral())) {
                continue;
            }
            Candidato guardado =
                    repositorio.guardar(
                            Candidato.detectado(
                                    campaniaId,
                                    contraste.predioId(),
                                    ClaseDeHallazgo.SUBVALUADOR,
                                    OrigenDelCandidato.CRUCE_DE_AREAS,
                                    score,
                                    insumosDe(contraste),
                                    contraste.geometria()));
            detectados.add(guardado);
        }

        // UNA fila de auditoria para la corrida, y no una por candidato. La observacion es la
        // misma —es un acto—, y N filas identicas salvo la clave no dicen nada mas que esta.
        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj),
                                "candidato",
                                "campania:" + campaniaId,
                                Operacion.ALTA,
                                observacion)
                        .con(
                                null,
                                "{\"campania\":"
                                        + campaniaId
                                        + ",\"contrastados\":"
                                        + contrastes.size()
                                        + ",\"detectados\":"
                                        + detectados.size()
                                        + ",\"tolerancia\":"
                                        + tolerancia
                                        + ",\"umbral\":"
                                        + campania.umbral()
                                        + "}"));
        return List.copyOf(detectados);
    }

    /**
     * Lo que disparo la sospecha, para poder volver a la fuente.
     *
     * <p>JSON escrito a mano, por lo mismo que en {@code RegistrarSector}: son cuatro campos, y
     * traer un serializador a la capa de aplicacion la ataria a la de presentacion. Guarda las dos
     * areas <b>tal como estaban al contrastar</b>: si dentro de un mes alguien versiona la ficha,
     * el descarte de este candidato tiene que poder explicarse con lo que se vio, no con lo que
     * hay.
     */
    private static String insumosDe(ContrasteDeAreas contraste) {
        return "{\"origen\":\"CRUCE_DE_AREAS\",\"codigoReferenciaCatastral\":\""
                + contraste.codigoReferenciaCatastral()
                + "\",\"fichaId\":"
                + contraste.fichaId()
                + ",\"areaDeLaFicha\":"
                + contraste.areaDeLaFicha().valor()
                + ",\"areaDelPoligono\":"
                + contraste.areaDelPoligono().valor()
                + ",\"diferenciaRelativa\":"
                + contraste.diferenciaRelativa()
                + "}";
    }

    /** La campania ya no admite candidatos: sus cifras estan cerradas. */
    public static final class CampaniaCerradaParaDetectar extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        CampaniaCerradaParaDetectar(long campaniaId) {
            super(
                    "La campania "
                            + campaniaId
                            + " esta cerrada: anadirle candidatos ahora cambiaria una tasa de"
                            + " descarte que alguien ya pudo citar");
        }
    }
}
