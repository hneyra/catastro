package kamayuk.catastro.fiscalizacion.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kamayuk.catastro.compartido.Pagina;
import kamayuk.catastro.compartido.Paginacion;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.Acta;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.ClaseDeHallazgo;
import kamayuk.catastro.fiscalizacion.dominio.CriterioDeCandidatos;
import kamayuk.catastro.fiscalizacion.dominio.EstadoDeCampania;
import kamayuk.catastro.fiscalizacion.dominio.EstadoDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.EstadoDelHallazgo;
import kamayuk.catastro.fiscalizacion.dominio.EtapaDeVerificacion;
import kamayuk.catastro.fiscalizacion.dominio.Evidencia;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import kamayuk.catastro.fiscalizacion.dominio.HallazgoDelPredio;
import kamayuk.catastro.fiscalizacion.dominio.HuellaDeEvidencia;
import kamayuk.catastro.fiscalizacion.dominio.OrigenDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import kamayuk.catastro.fiscalizacion.dominio.TasaDeDescarte;
import kamayuk.catastro.fiscalizacion.dominio.TipoDeEvidencia;
import kamayuk.catastro.persistencia.OrdenSeguro;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistencia del hallazgo catastral.
 *
 * <p>Cuatro cosas que este archivo repite del patron de {@code ViaRepositoryJdbc}, y una quinta que
 * es suya:
 *
 * <ol>
 *   <li><b>Ninguna lectura filtra por {@code municipalidad_id}</b> y ningun {@code INSERT} lo
 *       recibe de Java: lo pone el motor con {@code current_setting}, del mismo parametro que la
 *       politica RLS consulta (regla 2).
 *   <li><b>El SQL esta escrito, no generado.</b> Se ve lo que se ejecuta.
 *   <li><b>Ningun {@code DELETE}</b>, en ninguna de las cinco tablas. El descarte se conserva con
 *       su motivo (regla 4, ADR-0035 punto 5), y la aplicacion tampoco tiene el privilegio.
 *   <li>El orden se valida contra una lista blanca, porque {@code ORDER BY} no admite parametros.
 *   <li><b>Ningun {@code UPDATE} sobre {@code evidencia} ni sobre {@code acta}</b>, que es lo que
 *       las hace inmutables. Tampoco tienen el privilegio (V9), y el escaner de fuentes lo rechaza
 *       ademas antes de llegar a la base.
 * </ol>
 *
 * <p><b>La geometria entra y sale como WKT</b> y se convierte en la base con {@code
 * ST_GeogFromText} / {@code ST_AsText}. No se usa ningun operador espacial como condicion (ADR-0034
 * regla 2): estas consultas buscan por campania y por identificador, no por marco — y el dia que
 * alguna busque por area del plano, lo que hay que escribir son las cuatro columnas {@code marco_*}
 * que {@code V9} ya les dio.
 */
@Repository
public class FiscalizacionRepositoryJdbc extends RepositorioJdbc
        implements FiscalizacionRepository {

    private static final String COLUMNAS_CAMPANIA =
            "id, codigo, nombre, estado, inicio, fin, umbral, tope";

    private static final String COLUMNAS_CANDIDATO =
            "id, campania_id, predio_id, clase, origen, score, insumos::text AS insumos,"
                    + " ST_AsText(geometria) AS geometria_wkt, estado, etapa_de_descarte,"
                    + " motivo_de_descarte, descartado_por, descartado_en";

    private static final String COLUMNAS_HALLAZGO =
            "id, candidato_id, clase, predio_id, ficha_id, area_de_la_ficha, area_verificada,"
                    + " inspector, verificado_en, estado, motivo_anulacion, anulado_por,"
                    + " anulado_en";

    /**
     * Los hallazgos de UN predio, con su campania y su acta (#17, AC-1 y AC-2).
     *
     * <p><b>Se le pide a esta constante y no a una copia</b>: la prueba de plan mide la sentencia
     * que corre en produccion, y una copia escrita en la prueba seguiria en verde el dia que
     * alguien cambiara esta.
     *
     * <h2>Por que llega al indice, medido y no supuesto</h2>
     *
     * <p>{@code hallazgo_predio_ix} —{@code (municipalidad_id, predio_id) WHERE predio_id IS NOT
     * NULL}— ya existe desde {@code V9}, y AC-2 pedia decidir <b>midiendo</b> si hacia falta otro.
     * Se midio: con volumen, el plan lo elige y pone las dos columnas en el {@code Index Cond}
     * junto con la condicion de la politica. No se anade ninguno: un indice que nadie consulta se
     * paga en cada escritura y ademas COMPITE —la leccion de {@code zonificacion_vigencia_ix} en
     * #4—.
     *
     * <p>El {@code = :predioId} satisface por si solo el predicado parcial del indice: un valor
     * comparado con {@code =} no es nulo, asi que el planificador puede usarlo sin que la consulta
     * escriba {@code IS NOT NULL}.
     *
     * <h2>Los JOIN llevan su municipalidad, aunque la politica ya la garantice</h2>
     *
     * <p>Igual que {@code GestionDeRiesgoRepositoryJdbc}: sin la igualdad de {@code
     * municipalidad_id} el plan no tiene por donde enlazar las tablas por su clave primaria —que es
     * {@code (municipalidad_id, id)} en las cuatro—, y el bucle acaba comparando en el {@code Join
     * Filter} lo que deberia estar en el {@code Index Cond}.
     *
     * <p>El acta entra con {@code LEFT JOIN} y no con una segunda consulta por fila: un hallazgo
     * firme sin acta es un estado legitimo del recorrido, y preguntarla aparte por cada hallazgo
     * seria N+1 sobre una lectura que existe para contestarse de una vez.
     */
    public static final String HALLAZGOS_DEL_PREDIO =
            "SELECT h.id, h.candidato_id, h.clase, h.predio_id, h.ficha_id, h.area_de_la_ficha,"
                    + " h.area_verificada, h.inspector, h.verificado_en, h.estado,"
                    + " h.motivo_anulacion, h.anulado_por, h.anulado_en,"
                    + " c.campania_id, m.codigo AS campania_codigo,"
                    + " a.id AS acta_id, a.numero AS acta_numero, a.fecha AS acta_fecha,"
                    + " a.inspector AS acta_inspector, a.detalle AS acta_detalle"
                    + " FROM hallazgo h"
                    + " JOIN candidato c"
                    + "   ON c.municipalidad_id = h.municipalidad_id AND c.id = h.candidato_id"
                    + " JOIN campania m"
                    + "   ON m.municipalidad_id = c.municipalidad_id AND m.id = c.campania_id"
                    + " LEFT JOIN acta a"
                    + "   ON a.municipalidad_id = h.municipalidad_id AND a.hallazgo_id = h.id"
                    + " WHERE h.predio_id = :predioId"
                    + " ORDER BY h.verificado_en DESC, h.id DESC";

    private static final String COLUMNAS_EVIDENCIA =
            "id, hallazgo_id, tipo, sha256, ruta, capturado_en, recibido_en, dispositivo";

    private static final String COLUMNAS_ACTA =
            "id, numero, hallazgo_id, fecha, inspector, detalle";

    private static final OrdenSeguro ORDEN_CANDIDATOS =
            OrdenSeguro.sobre("score", "id", "estado", "clase");

    private static final OrdenSeguro ORDEN_HALLAZGOS =
            OrdenSeguro.sobre("verificado_en", "id", "clase");

    public FiscalizacionRepositoryJdbc(JdbcClient jdbc) {
        super(jdbc);
    }

    // ── Campania ───────────────────────────────────────────────────────

    @Override
    public Campania guardar(Campania campania, Observacion observacion) {
        return campania.esNueva()
                ? insertar(campania, observacion)
                : actualizar(campania, observacion);
    }

    private Campania insertar(Campania campania, Observacion observacion) {
        Long id =
                jdbc().sql(
                                "INSERT INTO campania (municipalidad_id, codigo, nombre, estado,"
                                        + " inicio, fin, umbral, tope, observacion,"
                                        + " usuario_registro)"
                                        + " VALUES ("
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", :codigo, :nombre, :estado, :inicio, :fin, :umbral,"
                                        + " :tope, :observacion, :usuario) RETURNING id")
                        .param("codigo", campania.codigo())
                        .param("nombre", campania.nombre())
                        .param("estado", campania.estado().name())
                        .param("inicio", campania.inicio())
                        .param("fin", campania.fin())
                        .param("umbral", campania.umbral().valor())
                        .param("tope", campania.tope())
                        .param("observacion", observacion.texto())
                        .param("usuario", usuarioDelOrigen())
                        .query(Long.class)
                        .single();
        return conId(campania, id);
    }

    /**
     * El nombre, el estado, el cierre —y la observacion del acto (#24, regla 10).
     *
     * <p>La columna se reescribe a proposito: la fila dice por que esta como esta AHORA, y el
     * historico de como llego lo lleva la bitacora, que es donde no se pisa nada. Cerrar una
     * campania es una modificacion y la regla 10 no distingue entre alta y modificacion.
     */
    private Campania actualizar(Campania campania, Observacion observacion) {
        jdbc().sql(
                        "UPDATE campania SET nombre = :nombre, estado = :estado, fin = :fin,"
                                + " observacion = :observacion"
                                + " WHERE id = :id")
                .param("nombre", campania.nombre())
                .param("estado", campania.estado().name())
                .param("fin", campania.fin())
                .param("observacion", observacion.texto())
                .param("id", campania.id())
                .update();
        return campania;
    }

    private static Campania conId(Campania campania, Long id) {
        return new Campania(
                id,
                campania.codigo(),
                campania.nombre(),
                campania.estado(),
                campania.inicio(),
                campania.fin(),
                campania.umbral(),
                campania.tope());
    }

    @Override
    public Optional<Campania> campaniaPorId(long id) {
        return jdbc().sql("SELECT " + COLUMNAS_CAMPANIA + " FROM campania WHERE id = :id")
                .param("id", id)
                .query(FiscalizacionRepositoryJdbc::mapearCampania)
                .optional();
    }

    @Override
    public Optional<Campania> campaniaPorCodigo(String codigo) {
        return jdbc().sql("SELECT " + COLUMNAS_CAMPANIA + " FROM campania WHERE codigo = :codigo")
                .param("codigo", codigo)
                .query(FiscalizacionRepositoryJdbc::mapearCampania)
                .optional();
    }

    // ── Candidato ──────────────────────────────────────────────────────

    @Override
    public Candidato guardar(Candidato candidato, Observacion observacion) {
        return candidato.esNuevo()
                ? insertar(candidato, observacion)
                : actualizar(candidato, observacion);
    }

    private Candidato insertar(Candidato candidato, Observacion observacion) {
        Long id =
                jdbc().sql(
                                "INSERT INTO candidato (municipalidad_id, campania_id, predio_id,"
                                        + " clase, origen, score, insumos, geometria, estado,"
                                        + " observacion, usuario_registro)"
                                        + " VALUES ("
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", :campania, :predio, :clase, :origen, :score,"
                                        + " CAST(:insumos AS jsonb),"
                                        + " CASE WHEN CAST(:wkt AS text) IS NULL THEN NULL"
                                        + "      ELSE ST_GeogFromText(CAST(:wkt AS text)) END,"
                                        + " :estado, :observacion, :usuario) RETURNING id")
                        .param("campania", candidato.campaniaId())
                        .param("predio", candidato.predioId())
                        .param("clase", candidato.clase().name())
                        .param("origen", candidato.origen().name())
                        .param("score", candidato.score().valor())
                        .param("insumos", candidato.insumos())
                        .param("wkt", conSrid(candidato.geometria()))
                        .param("estado", candidato.estado().name())
                        .param("observacion", observacion.texto())
                        .param("usuario", usuarioDelOrigen())
                        .query(Long.class)
                        .single();
        return conId(candidato, id);
    }

    /**
     * La transicion de estado y su descarte, y nada mas.
     *
     * <p>No reescribe clase, origen, score, insumos ni geometria: eso es lo que la maquina dijo, y
     * una compuerta no lo cambia. Si alguna vez hiciera falta corregirlo, seria otro candidato con
     * otro insumo — y contarlo como el mismo borraria uno de los dos de la tasa de descarte.
     */
    private Candidato actualizar(Candidato candidato, Observacion observacion) {
        Candidato.Descarte descarte = candidato.descarte();
        jdbc().sql(
                        "UPDATE candidato SET estado = :estado, etapa_de_descarte = :etapa,"
                                + " motivo_de_descarte = :motivo, descartado_por = :quien,"
                                + " descartado_en = :cuando, observacion = :observacion"
                                + " WHERE id = :id")
                .param("estado", candidato.estado().name())
                .param("etapa", descarte == null ? null : descarte.etapa().name())
                .param("motivo", descarte == null ? null : descarte.motivo())
                .param("quien", descarte == null ? null : descarte.quien())
                .param("cuando", descarte == null ? null : Timestamp.from(descarte.cuando()))
                .param("observacion", observacion.texto())
                .param("id", candidato.id())
                .update();
        return candidato;
    }

    private static Candidato conId(Candidato candidato, Long id) {
        return new Candidato(
                id,
                candidato.campaniaId(),
                candidato.predioId(),
                candidato.clase(),
                candidato.origen(),
                candidato.score(),
                candidato.insumos(),
                candidato.geometria(),
                candidato.estado(),
                candidato.descarte());
    }

    @Override
    public Optional<Candidato> candidatoPorId(long id) {
        return jdbc().sql("SELECT " + COLUMNAS_CANDIDATO + " FROM candidato WHERE id = :id")
                .param("id", id)
                .query(FiscalizacionRepositoryJdbc::mapearCandidato)
                .optional();
    }

    @Override
    public Pagina<Candidato> candidatos(CriterioDeCandidatos criterio, Paginacion paginacion) {
        StringBuilder donde = new StringBuilder(" WHERE campania_id = :campania");
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("campania", criterio.campaniaId());

        if (criterio.estado() != null) {
            donde.append(" AND estado = :estado");
            parametros.put("estado", criterio.estado().name());
        }
        if (criterio.clase() != null) {
            donde.append(" AND clase = :clase");
            parametros.put("clase", criterio.clase().name());
        }

        String filtro = donde.toString();
        return paginar(
                "SELECT " + COLUMNAS_CANDIDATO + " FROM candidato" + filtro,
                "SELECT count(*) FROM candidato" + filtro,
                Map.copyOf(parametros),
                paginacion,
                ORDEN_CANDIDATOS,
                FiscalizacionRepositoryJdbc::mapearCandidato);
    }

    /**
     * Los cuatro recuentos de una campania, en <b>una</b> consulta.
     *
     * <p>Cuatro consultas darian cuatro fotos de momentos distintos: entre la primera y la cuarta
     * alguien puede descartar, y entonces {@code enCurso()} saldria negativo. Un solo {@code
     * SELECT} las toma de la misma instantanea.
     */
    @Override
    public TasaDeDescarte tasaDeDescarte(long campaniaId) {
        return jdbc().sql(
                        "SELECT count(*) AS detectados,"
                                + " count(*) FILTER (WHERE estado = 'DESCARTADO'"
                                + "                    AND etapa_de_descarte = 'GABINETE')"
                                + "   AS en_gabinete,"
                                + " count(*) FILTER (WHERE estado = 'DESCARTADO'"
                                + "                    AND etapa_de_descarte = 'CAMPO')"
                                + "   AS en_campo,"
                                + " count(*) FILTER (WHERE estado = 'VERIFICADO_EN_CAMPO')"
                                + "   AS verificados"
                                + " FROM candidato WHERE campania_id = :campania")
                .param("campania", campaniaId)
                .query(
                        (ResultSet fila, int numero) ->
                                new TasaDeDescarte(
                                        fila.getLong("detectados"),
                                        fila.getLong("en_gabinete"),
                                        fila.getLong("en_campo"),
                                        fila.getLong("verificados")))
                .single();
    }

    // ── Hallazgo ───────────────────────────────────────────────────────

    @Override
    public Hallazgo guardar(Hallazgo hallazgo, Observacion observacion) {
        return hallazgo.esNuevo()
                ? insertar(hallazgo, observacion)
                : actualizar(hallazgo, observacion);
    }

    private Hallazgo insertar(Hallazgo hallazgo, Observacion observacion) {
        Long id =
                jdbc().sql(
                                "INSERT INTO hallazgo (municipalidad_id, candidato_id, clase,"
                                        + " predio_id, ficha_id, area_de_la_ficha, area_verificada,"
                                        + " inspector, verificado_en, estado,"
                                        + " observacion, usuario_registro)"
                                        + " VALUES ("
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", :candidato, :clase, :predio, :ficha, :areaFicha,"
                                        + " :areaVerificada, :inspector, :verificadoEn, :estado,"
                                        + " :observacion, :usuario) RETURNING id")
                        .param("candidato", hallazgo.candidatoId())
                        .param("clase", hallazgo.clase().name())
                        .param("predio", hallazgo.predioId())
                        .param("ficha", hallazgo.fichaId())
                        .param(
                                "areaFicha",
                                hallazgo.areaDeLaFicha() == null
                                        ? null
                                        : hallazgo.areaDeLaFicha().valor())
                        .param("areaVerificada", hallazgo.areaVerificada().valor())
                        .param("inspector", hallazgo.inspector())
                        .param("verificadoEn", hallazgo.verificadoEn())
                        .param("estado", hallazgo.estado().name())
                        .param("observacion", observacion.texto())
                        .param("usuario", usuarioDelOrigen())
                        .query(Long.class)
                        .single();
        return conId(hallazgo, id);
    }

    /**
     * Solo el estado y el acto de anulacion.
     *
     * <p>Lo que el inspector verifico no se reescribe: dejar sin efecto un hallazgo es un acto
     * sobre el, no una correccion de lo que dijo. Corregir las dos areas aqui dejaria su acta —que
     * es inmutable— diciendo una cosa y la base otra.
     *
     * <p>La observacion se reescribe (#24, regla 10): anular es una modificacion, y la fila tiene
     * que decir por que esta como esta ahora. El antes y el despues los guarda la bitacora.
     */
    private Hallazgo actualizar(Hallazgo hallazgo, Observacion observacion) {
        Hallazgo.Anulacion anulacion = hallazgo.anulacion();
        jdbc().sql(
                        "UPDATE hallazgo SET estado = :estado, motivo_anulacion = :motivo,"
                                + " anulado_por = :quien, anulado_en = :cuando,"
                                + " observacion = :observacion"
                                + " WHERE id = :id")
                .param("estado", hallazgo.estado().name())
                .param("motivo", anulacion == null ? null : anulacion.motivo())
                .param("quien", anulacion == null ? null : anulacion.quien())
                .param("cuando", anulacion == null ? null : Timestamp.from(anulacion.cuando()))
                .param("observacion", observacion.texto())
                .param("id", hallazgo.id())
                .update();
        return hallazgo;
    }

    private static Hallazgo conId(Hallazgo hallazgo, Long id) {
        return new Hallazgo(
                id,
                hallazgo.candidatoId(),
                hallazgo.clase(),
                hallazgo.predioId(),
                hallazgo.fichaId(),
                hallazgo.areaDeLaFicha(),
                hallazgo.areaVerificada(),
                hallazgo.inspector(),
                hallazgo.verificadoEn(),
                hallazgo.estado(),
                hallazgo.anulacion());
    }

    @Override
    public Optional<Hallazgo> hallazgoPorId(long id) {
        return jdbc().sql("SELECT " + COLUMNAS_HALLAZGO + " FROM hallazgo WHERE id = :id")
                .param("id", id)
                .query(FiscalizacionRepositoryJdbc::mapearHallazgo)
                .optional();
    }

    @Override
    public Optional<Hallazgo> hallazgoDelCandidato(long candidatoId) {
        return jdbc().sql(
                        "SELECT " + COLUMNAS_HALLAZGO + " FROM hallazgo WHERE candidato_id = :cand")
                .param("cand", candidatoId)
                .query(FiscalizacionRepositoryJdbc::mapearHallazgo)
                .optional();
    }

    @Override
    public Pagina<Hallazgo> hallazgos(long campaniaId, Paginacion paginacion) {
        String desde =
                " FROM hallazgo h JOIN candidato c ON c.id = h.candidato_id"
                        + " WHERE c.campania_id = :campania";
        return paginar(
                "SELECT h.id, h.candidato_id, h.clase, h.predio_id, h.ficha_id,"
                        + " h.area_de_la_ficha, h.area_verificada, h.inspector, h.verificado_en,"
                        + " h.estado, h.motivo_anulacion, h.anulado_por, h.anulado_en"
                        + desde,
                "SELECT count(*)" + desde,
                Map.of("campania", campaniaId),
                paginacion,
                ORDEN_HALLAZGOS,
                FiscalizacionRepositoryJdbc::mapearHallazgo);
    }

    @Override
    public List<HallazgoDelPredio> hallazgosDelPredio(long predioId) {
        return jdbc().sql(HALLAZGOS_DEL_PREDIO)
                .param("predioId", predioId)
                .query(FiscalizacionRepositoryJdbc::mapearHallazgoDelPredio)
                .list();
    }

    /**
     * Una fila de {@link #HALLAZGOS_DEL_PREDIO}: el hallazgo, su campania y —si la hay— su acta.
     *
     * <p>El acta se reconoce por {@code acta_id} nulo y no por su numero: el {@code LEFT JOIN} deja
     * todas sus columnas nulas cuando no hay fila, y {@code numero} es {@code NOT NULL} en la tabla
     * — asi que preguntarle a la clave es preguntarle a lo unico que no puede ser nulo por otro
     * motivo.
     */
    private static HallazgoDelPredio mapearHallazgoDelPredio(ResultSet fila, int numero)
            throws SQLException {
        long actaId = fila.getLong("acta_id");
        Acta acta =
                fila.wasNull()
                        ? null
                        : new Acta(
                                actaId,
                                fila.getString("acta_numero"),
                                fila.getLong("id"),
                                fila.getObject("acta_fecha", java.time.LocalDate.class),
                                fila.getString("acta_inspector"),
                                fila.getString("acta_detalle"));
        return new HallazgoDelPredio(
                mapearHallazgo(fila, numero),
                fila.getLong("campania_id"),
                fila.getString("campania_codigo"),
                acta);
    }

    // ── Evidencia y acta: solo entran, nunca cambian ───────────────────

    @Override
    public Evidencia guardar(Evidencia evidencia, Observacion observacion) {
        if (!evidencia.esNueva()) {
            throw new IllegalArgumentException(
                    "Una evidencia no se modifica: se hasheo en el dispositivo, y corregirla en el"
                            + " sitio la dejaria distinta del archivo que la huella describe. La"
                            + " base tampoco lo permite (V9: sin UPDATE)");
        }
        Long id =
                jdbc().sql(
                                "INSERT INTO evidencia (municipalidad_id, hallazgo_id, tipo,"
                                        + " sha256, ruta, capturado_en, recibido_en, dispositivo,"
                                        + " observacion, usuario_registro)"
                                        + " VALUES ("
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", :hallazgo, :tipo, :sha256, :ruta, :capturado,"
                                        + " :recibido, :dispositivo, :observacion, :usuario)"
                                        + " RETURNING id")
                        .param("hallazgo", evidencia.hallazgoId())
                        .param("tipo", evidencia.tipo().name())
                        .param("sha256", evidencia.huella().valor())
                        .param("ruta", evidencia.ruta())
                        .param("capturado", Timestamp.from(evidencia.capturadoEn()))
                        .param("recibido", Timestamp.from(evidencia.recibidoEn()))
                        .param("dispositivo", evidencia.dispositivo())
                        .param("observacion", observacion.texto())
                        .param("usuario", usuarioDelOrigen())
                        .query(Long.class)
                        .single();
        return new Evidencia(
                id,
                evidencia.hallazgoId(),
                evidencia.tipo(),
                evidencia.huella(),
                evidencia.ruta(),
                evidencia.capturadoEn(),
                evidencia.recibidoEn(),
                evidencia.dispositivo());
    }

    @Override
    public List<Evidencia> evidenciasDe(long hallazgoId) {
        return jdbc().sql(
                        "SELECT "
                                + COLUMNAS_EVIDENCIA
                                + " FROM evidencia WHERE hallazgo_id = :hallazgo"
                                + " ORDER BY capturado_en, id")
                .param("hallazgo", hallazgoId)
                .query(FiscalizacionRepositoryJdbc::mapearEvidencia)
                .list();
    }

    /**
     * El acta se inserta y nunca se modifica.
     *
     * <h2>Y lo que ocurre cuando esta equivocada, dicho como es (#23 AC-3)</h2>
     *
     * <p>El mensaje de abajo decia «se corrige dejando sin efecto su hallazgo y levantando otra», y
     * <b>ese remedio no se puede recorrer</b>. El issue lo atribuia a una sola puerta —{@code
     * hallazgo_candidato_uq} sin {@code WHERE estado = 'FIRME'}, o sea que tras anular el hallazgo
     * hace falta un candidato nuevo—, y midiendolo hay <b>dos, y la segunda es de dominio</b>:
     * {@code EstadoDelCandidato.esTerminal()} incluye {@code VERIFICADO_EN_CAMPO} y {@code
     * Candidato.verificadoEnCampo()} solo admite venir de {@code ADMITIDO_EN_GABINETE}. De modo que
     * hacer parcial el indice <b>no basta</b>: haria falta ademas devolver el candidato a la cola.
     *
     * <p>Y devolverlo contradice un motivo ya escrito, que es de ADR-0035 punto 5 y esta en {@code
     * EstadoDelCandidato}: «si la brigada vuelve y encuentra otra cosa, eso es otro candidato con
     * otro insumo — y contarlo como el mismo borraria uno de los dos descartes de la tasa». La tasa
     * de descarte por etapa es el unico indicador honesto de si el umbral sirve, y reutilizar un
     * candidato para un segundo hallazgo la falsea.
     *
     * <p><b>Asi que lo que se corrige es la frase, y no el motor.</b> Lo que de verdad ocurre: el
     * acta equivocada <b>se queda</b> —es inmutable, y {@code V9} le revoca el {@code UPDATE}—, su
     * hallazgo <b>se deja sin efecto</b> con su motivo, quien y cuando (#23), y con eso ese
     * hallazgo deja de habilitar nada y su retractacion viaja al consumidor. Levantar OTRA acta
     * sobre el mismo predio exige otro hallazgo, y otro hallazgo exige otro candidato: o sea, otra
     * deteccion. No es gratis y por eso se dice, en vez de prometer un atajo que el motor rechaza.
     */
    @Override
    public Acta guardar(Acta acta, Observacion observacion) {
        if (!acta.esNueva()) {
            throw new IllegalArgumentException(
                    "Un acta no se modifica: el administrado se lleva el papel, y editarla dejaria"
                            + " al papel y al sistema diciendo cosas distintas. Lo que se hace es"
                            + " dejar sin efecto su hallazgo —el acta se queda donde esta— y, si"
                            + " hace falta otra, detectar de nuevo: un candidato verificado no"
                            + " vuelve a la cola (ADR-0035 punto 5)");
        }
        Long id =
                jdbc().sql(
                                "INSERT INTO acta (municipalidad_id, numero, hallazgo_id, fecha,"
                                        + " inspector, detalle, observacion, usuario_registro)"
                                        + " VALUES ("
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", :numero, :hallazgo, :fecha, :inspector, :detalle,"
                                        + " :observacion, :usuario) RETURNING id")
                        .param("numero", acta.numero())
                        .param("hallazgo", acta.hallazgoId())
                        .param("fecha", acta.fecha())
                        .param("inspector", acta.inspector())
                        .param("detalle", acta.detalle())
                        .param("observacion", observacion.texto())
                        .param("usuario", usuarioDelOrigen())
                        .query(Long.class)
                        .single();
        return new Acta(
                id,
                acta.numero(),
                acta.hallazgoId(),
                acta.fecha(),
                acta.inspector(),
                acta.detalle());
    }

    @Override
    public Optional<Acta> actaDelHallazgo(long hallazgoId) {
        return jdbc().sql("SELECT " + COLUMNAS_ACTA + " FROM acta WHERE hallazgo_id = :hallazgo")
                .param("hallazgo", hallazgoId)
                .query(FiscalizacionRepositoryJdbc::mapearActa)
                .optional();
    }

    // ── Mapeo ──────────────────────────────────────────────────────────

    private static Campania mapearCampania(ResultSet fila, int numero) throws SQLException {
        return new Campania(
                fila.getLong("id"),
                fila.getString("codigo"),
                fila.getString("nombre"),
                EstadoDeCampania.valueOf(fila.getString("estado")),
                fila.getObject("inicio", java.time.LocalDate.class),
                fila.getObject("fin", java.time.LocalDate.class),
                new Score(fila.getBigDecimal("umbral")),
                fila.getInt("tope"));
    }

    private static Candidato mapearCandidato(ResultSet fila, int numero) throws SQLException {
        String etapa = fila.getString("etapa_de_descarte");
        Candidato.Descarte descarte =
                etapa == null
                        ? null
                        : new Candidato.Descarte(
                                EtapaDeVerificacion.valueOf(etapa),
                                fila.getString("motivo_de_descarte"),
                                fila.getString("descartado_por"),
                                instanteDe(fila, "descartado_en"));
        return new Candidato(
                fila.getLong("id"),
                fila.getLong("campania_id"),
                idOpcional(fila, "predio_id"),
                ClaseDeHallazgo.valueOf(fila.getString("clase")),
                OrigenDelCandidato.valueOf(fila.getString("origen")),
                new Score(fila.getBigDecimal("score")),
                fila.getString("insumos"),
                fila.getString("geometria_wkt"),
                EstadoDelCandidato.valueOf(fila.getString("estado")),
                descarte);
    }

    private static Hallazgo mapearHallazgo(ResultSet fila, int numero) throws SQLException {
        java.math.BigDecimal areaFicha = fila.getBigDecimal("area_de_la_ficha");
        return new Hallazgo(
                fila.getLong("id"),
                fila.getLong("candidato_id"),
                ClaseDeHallazgo.valueOf(fila.getString("clase")),
                idOpcional(fila, "predio_id"),
                idOpcional(fila, "ficha_id"),
                areaFicha == null ? null : new AreaM2(areaFicha),
                new AreaM2(fila.getBigDecimal("area_verificada")),
                fila.getString("inspector"),
                fila.getObject("verificado_en", java.time.LocalDate.class),
                EstadoDelHallazgo.valueOf(fila.getString("estado")),
                anulacionDe(fila));
    }

    /**
     * El acto de anulacion, o nulo si el hallazgo sigue firme.
     *
     * <p>Se decide por el motivo y no por el estado: {@code hallazgo_anulacion_check} de {@code
     * V12} ata las dos cosas, asi que preguntar por la columna que la fila tiene o no tiene es
     * preguntar por lo mismo, y no obliga a este mapeador a repetir la regla.
     */
    private static Hallazgo.@Nullable Anulacion anulacionDe(ResultSet fila) throws SQLException {
        String motivo = fila.getString("motivo_anulacion");
        if (motivo == null) {
            return null;
        }
        return new Hallazgo.Anulacion(
                motivo, fila.getString("anulado_por"), instanteDe(fila, "anulado_en"));
    }

    private static Evidencia mapearEvidencia(ResultSet fila, int numero) throws SQLException {
        return new Evidencia(
                fila.getLong("id"),
                fila.getLong("hallazgo_id"),
                TipoDeEvidencia.valueOf(fila.getString("tipo")),
                new HuellaDeEvidencia(fila.getString("sha256")),
                fila.getString("ruta"),
                instanteDe(fila, "capturado_en"),
                instanteDe(fila, "recibido_en"),
                fila.getString("dispositivo"));
    }

    private static Acta mapearActa(ResultSet fila, int numero) throws SQLException {
        return new Acta(
                fila.getLong("id"),
                fila.getString("numero"),
                fila.getLong("hallazgo_id"),
                fila.getObject("fecha", java.time.LocalDate.class),
                fila.getString("inspector"),
                fila.getString("detalle"));
    }

    /**
     * Un identificador que puede faltar.
     *
     * <p>{@code getLong} devuelve 0 sobre un {@code NULL}, y un cero se leeria como el predio
     * numero cero: exactamente lo que el omiso catastral no tiene. Se pregunta por {@code wasNull}
     * justo despues de leer ESA columna, que es el defecto que P5D midio en {@code pagadorDe}.
     */
    private static @Nullable Long idOpcional(ResultSet fila, String columna) throws SQLException {
        long valor = fila.getLong(columna);
        return fila.wasNull() ? null : valor;
    }

    /**
     * Un instante que la fila tiene que traer, y que si no trae se DICE (#24, AC-4).
     *
     * <p>Devolvia {@link Instant#EPOCH} cuando la columna venia nula, y un {@code capturado_en} de
     * 1970-01-01 en una evidencia es una fecha <b>plausible y falsa</b>: la evidencia existe para
     * ser auditable, y una fecha inventada es peor que ninguna porque nadie la va a mirar dos
     * veces. Hoy los {@code CHECK} de {@code V9} hacen este camino inalcanzable —las cinco columnas
     * de instante son {@code NOT NULL}—, asi que lanzar aqui no cambia ninguna respuesta; lo que
     * cambia es el dia que una migracion afloje una de ellas.
     *
     * <p><b>Visible para la prueba, y hay que decir por que.</b> Ninguna consulta de esta clase
     * puede traer ese nulo mientras los {@code CHECK} esten puestos, asi que la unica forma de
     * ejercer la decision es entregarle una fila que SI lo traiga. {@code InstanteDeLaFilaTest} lo
     * hace con un {@code ResultSet} de PostgreSQL de verdad —{@code SELECT NULL::timestamptz}— y no
     * con un doble: lo que se mide es como se comporta este metodo ante la fila, no ante una
     * imitacion suya.
     */
    static Instant instanteDe(ResultSet fila, String columna) throws SQLException {
        Timestamp momento = fila.getTimestamp(columna);
        if (momento == null) {
            throw new IllegalStateException(
                    "La columna '"
                            + columna
                            + "' vino nula y esta fila necesita su instante: devolver 1970-01-01"
                            + " seria una fecha plausible y falsa en un registro que existe para"
                            + " ser auditable (#24)");
        }
        return momento.toInstant();
    }

    /**
     * El WKT con su SRID delante.
     *
     * <p>{@code ST_GeogFromText} exige el sistema de coordenadas y ADR-0021 fija 4326 para todo el
     * sistema. Se antepone aqui y no se pide al llamador para que no haya dos convenciones —una con
     * SRID y otra sin el— en la misma tabla.
     */
    private static @Nullable String conSrid(@Nullable String wkt) {
        if (wkt == null || wkt.isBlank()) {
            return null;
        }
        return wkt.toUpperCase(java.util.Locale.ROOT).startsWith("SRID=")
                ? wkt
                : "SRID=4326;" + wkt;
    }

    /**
     * Quien escribe la fila, del contexto de origen (ARQ-03).
     *
     * <p>No entra por la firma: seria la segunda fuente de un dato que la auditoria ya toma del
     * borde de la aplicacion, y el dia que alguien escriba un repositorio nuevo lo rellenaria con
     * lo que tenga a mano.
     */
    private static String usuarioDelOrigen() {
        return kamayuk.catastro.auditoria.OrigenContext.actual().usuario();
    }
}
