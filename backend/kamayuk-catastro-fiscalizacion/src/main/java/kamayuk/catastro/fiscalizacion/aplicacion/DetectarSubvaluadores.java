package kamayuk.catastro.fiscalizacion.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.DatosDeAuditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.ClaseDeHallazgo;
import kamayuk.catastro.fiscalizacion.dominio.ContrasteDeAreas;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.OrigenDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.Score;
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
     * Corre la deteccion sobre la campania y deja sus candidatos, con el censo de lo que se miro.
     *
     * <p><b>El criterio no entra por aqui: sale de la campania</b> (#25). Ni tolerancia ni tope:
     * los dos los declaro quien la abrio y los dos estan en su fila, que es lo que hace comparable
     * su tasa de descarte con la de otra corrida.
     *
     * @throws AreasDelPadron.SinCartografia si la municipalidad no tiene un solo poligono
     */
    @Transactional
    public Deteccion detectar(long campaniaId, Observacion observacion) {

        Campania campania =
                repositorio
                        .campaniaPorId(campaniaId)
                        .orElseThrow(() -> new AbrirCampania.CampaniaInexistente(campaniaId));
        if (!campania.admiteCandidatos()) {
            throw new CampaniaCerradaParaDetectar(campaniaId);
        }

        // Deja pasar SinCartografia a proposito: ver el javadoc de la clase.
        //
        // El umbral se aplica en UN solo sitio —el `WHERE` del cruce— y no se vuelve a aplicar
        // aqui (#25 AC-1). Volver a filtrar en Java era lo que permitia que las dos cifras
        // divergieran: con `tolerancia > umbral` el filtro de la base era el estricto y este no
        // quitaba nada, de modo que la fila de la campania decia un criterio que no fue el que
        // corrio. La comparacion que el cruce implementa es la de `Score.alcanza` (>=), y
        // `ContrasteDeAreasJdbcTest` la fija por los dos lados del borde.
        AreasDelPadron.CruceDelPadron cruce = areas.contrastar(campania.umbral(), campania.tope());

        List<Candidato> detectados = new ArrayList<>();
        for (ContrasteDeAreas contraste : cruce.contrastes()) {
            Score score = contraste.diferenciaRelativa();
            Candidato guardado =
                    repositorio.guardar(
                            Candidato.detectado(
                                    campaniaId,
                                    contraste.predioId(),
                                    ClaseDeHallazgo.SUBVALUADOR,
                                    OrigenDelCandidato.CRUCE_DE_AREAS,
                                    score,
                                    insumosDe(contraste),
                                    contraste.geometria()),
                            observacion);
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
                                DatosDeAuditoria.campos()
                                        .mas("campania", campaniaId)
                                        .mas("contrastados", cruce.cobertura().contrastados())
                                        .mas("detectados", detectados.size())
                                        .mas("umbral", campania.umbral().valor())
                                        .mas("tope", campania.tope())
                                        .mas("sinGeometria", cruce.cobertura().sinGeometria())
                                        .mas("sinFichaVigente", cruce.cobertura().sinFichaVigente())
                                        .mas("truncadosPorElTope", cruce.cobertura().truncadosPorElTope())
                                        .datos()));
        return new Deteccion(List.copyOf(detectados), cruce.cobertura());
    }

    /**
     * Lo que la corrida produjo <b>y de que universo salio</b> (#25 AC-3).
     *
     * <p>Una lista pelada deja «0 candidatos» indistinguible de «0 candidatos entre las fichas que
     * miro», y la segunda cierra una campania afirmando que el padron esta bien. El censo viaja
     * pegado al resultado por lo mismo que {@link AreasDelPadron.SinCartografia} lanza en vez de
     * devolver vacio: un cero sin su denominador no es una respuesta.
     */
    public record Deteccion(List<Candidato> candidatos, AreasDelPadron.Cobertura cobertura) {

        public Deteccion {
            candidatos = List.copyOf(candidatos);
            java.util.Objects.requireNonNull(cobertura, "La deteccion dice de donde salio");
        }
    }

    /**
     * Lo que disparo la sospecha, para poder volver a la fuente.
     *
     * <p>Guarda las dos areas <b>tal como estaban al contrastar</b>: si dentro de un mes alguien
     * versiona la ficha, el descarte de este candidato tiene que poder explicarse con lo que se
     * vio, no con lo que hay.
     *
     * <h2>Por que devuelve {@code String} y no {@code DatosDeAuditoria}</h2>
     *
     * <p>Porque esto no es una fila de la bitacora: es la columna {@code candidato.insumos}, y en
     * el dominio {@link Candidato} la guarda como texto. {@code candidato.insumos} tambien es
     * {@code jsonb} —o sea que el defecto de #20 era el mismo aqui: un codigo de referencia
     * catastral con una comilla mataba la corrida entera— y por eso lo compone el serializador; lo
     * que no se hace es meter un tipo de la bitacora dentro de un contexto acotado para
     * conseguirlo.
     *
     * <p>Las dos areas van tipadas como {@code AreaM2} y las escribe el serializador: por eso esta
     * clase salio de {@code componenElAreaAManoConMotivo()}.
     */
    private static String insumosDe(ContrasteDeAreas contraste) {
        return SerializadorDeJson.texto(
                new InsumosDelCruceDeAreas(
                        OrigenDelCandidato.CRUCE_DE_AREAS.name(),
                        contraste.codigoReferenciaCatastral(),
                        contraste.fichaId(),
                        contraste.areaDeLaFicha(),
                        contraste.areaDelPoligono(),
                        contraste.diferenciaRelativa().valor()));
    }

    /** Lo que el cruce de areas vio, congelado en la fila del candidato. */
    private record InsumosDelCruceDeAreas(
            String origen,
            String codigoReferenciaCatastral,
            long fichaId,
            AreaM2 areaDeLaFicha,
            AreaM2 areaDelPoligono,
            java.math.BigDecimal diferenciaRelativa) {}

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
