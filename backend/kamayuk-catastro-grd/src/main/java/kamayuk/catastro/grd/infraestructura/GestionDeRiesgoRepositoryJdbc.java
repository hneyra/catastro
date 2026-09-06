package kamayuk.catastro.grd.infraestructura;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.grd.dominio.CertificadoItse;
import kamayuk.catastro.grd.dominio.FajaMarginal;
import kamayuk.catastro.grd.dominio.GestionDeRiesgoRepository;
import kamayuk.catastro.grd.dominio.ModalidadItse;
import kamayuk.catastro.grd.dominio.NivelDeRiesgo;
import kamayuk.catastro.grd.dominio.ZonaDeRiesgo;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * El SQL de la gestion del riesgo (#5). Copia el patron de {@code ViaRepositoryJdbc}: ninguna
 * lectura filtra por {@code municipalidad_id} y ningun {@code INSERT} lo recibe de Java.
 *
 * <h2>El cruce con el lote se resuelve DENTRO de una sola sentencia, y no es comodidad</h2>
 *
 * <p>La pregunta —«que zonas cruzan el lote del predio 42»— necesita dos poligonos: el de la zona y
 * el del predio. Sacar el del predio a Java para volver a meterlo como parametro moveria cientos de
 * vertices por la red para preguntar lo que el motor contesta en el sitio, y —lo que de verdad
 * importa— dejaria el refinado espacial <b>sin la condicion de marco delante</b>, que es justamente
 * lo que ADR-0034 regla 2 existe para impedir. Por eso el {@code JOIN} contra {@code predio}: es
 * una tabla de <b>este mismo sistema</b> (la regla 11 vigila la frontera entre sistemas, no entre
 * contextos), y su poligono no sale de la base.
 *
 * <h2>Marco primero, operador espacial detras (ADR-0034 regla 2)</h2>
 *
 * <p>Bajo RLS ni {@code geography_overlaps} ni {@code ST_Intersects} son <i>leakproof</i>: no se
 * promueven por encima de la politica, el indice GiST no sirve al rol de la aplicacion y la
 * consulta lee la tabla entera del inquilino <b>con el plan diciendo «Index»</b> —el de la
 * politica—. Medido en ADR-0034: 4 530 bloques contra los 347 del marco.
 *
 * <p>Asi que las cuatro desigualdades van primero, con {@code float8le}/{@code float8ge}, que si lo
 * son y llegan a {@code zona_riesgo_marco_ix}. El {@code ST_Intersects} va detras y solo como
 * <b>refinado exacto</b>: aqui la respuesta si lo exige —dos rectangulos que se solapan no son dos
 * poligonos que se tocan, y decir que un lote esta en una zona MUY ALTO porque sus cajas se cruzan
 * es exactamente el falso positivo que acaba negando una licencia—. Las dos condiciones viven en la
 * <b>misma sentencia de Java</b>, que es la unidad que el escaner de fuentes mide.
 */
@Repository
public class GestionDeRiesgoRepositoryJdbc extends RepositorioJdbc
        implements GestionDeRiesgoRepository {

    /**
     * El lote contra el que se cruza, y la condicion que lo cruza.
     *
     * <p>Se escribe una sola vez y la usan las dos lecturas espaciales: dos copias de este
     * predicado divergirian, y la que divergiera seguiria dando un resultado plausible.
     *
     * <p>El {@code JOIN} lleva {@code p.municipalidad_id = c.municipalidad_id} aunque la politica
     * ya garantice que las dos filas son del mismo inquilino: sin el, el plan no tiene por donde
     * enlazar las tablas y el marco deja de servir para acotar.
     */
    private static final String LOTE_Y_SU_MARCO =
            " JOIN predio p"
                    + " ON p.municipalidad_id = c.municipalidad_id"
                    + " AND p.id = :predioId"
                    + " WHERE p.geometria IS NOT NULL"
                    + " AND c.marco_oeste <= p.marco_este"
                    + " AND c.marco_sur   <= p.marco_norte"
                    + " AND c.marco_este  >= p.marco_oeste"
                    + " AND c.marco_norte >= p.marco_sur"
                    + " AND c.vigencia_desde <= :aLaFecha"
                    + " AND (c.vigencia_hasta IS NULL OR c.vigencia_hasta >= :aLaFecha)"
                    + " AND ST_Intersects(c.geometria, p.geometria)";

    private static final String COLUMNAS_ZONA =
            "c.id, c.codigo, c.fenomeno, c.nivel, c.mitigable, c.fuente, c.documento_origen,"
                    + " c.vigencia_desde, c.vigencia_hasta, c.observacion";

    private static final String COLUMNAS_FAJA =
            "c.id, c.codigo, c.cuerpo_agua, c.ancho_m, c.fuente, c.documento_origen,"
                    + " c.vigencia_desde, c.vigencia_hasta, c.observacion";

    private static final String COLUMNAS_ITSE =
            "id, predio_id, numero, nivel_riesgo, modalidad, vigencia_desde, vigencia_hasta,"
                    + " fecha_anulacion, motivo_anulacion, observacion";

    public GestionDeRiesgoRepositoryJdbc(JdbcClient jdbc) {
        super(jdbc);
    }

    @Override
    public EstadoDelLote estadoDelLote(long predioId) {
        Optional<Boolean> conGeometria =
                jdbc().sql("SELECT geometria IS NOT NULL FROM predio WHERE id = :predioId")
                        .param("predioId", predioId)
                        .query(Boolean.class)
                        .optional();
        // «No hay fila» y «hay fila sin poligono» se distinguen a proposito: se arreglan de
        // maneras opuestas -revisando el identificador o cargando el plano-, y contestarlas igual
        // mandaria a quien atiende a buscar lo que no es.
        return new EstadoDelLote(conGeometria.isPresent(), conGeometria.orElse(false));
    }

    @Override
    public List<ZonaDeRiesgo> zonasQueCruzanElLote(long predioId, LocalDate aLaFecha) {
        return jdbc().sql(
                        "SELECT "
                                + COLUMNAS_ZONA
                                + " FROM zona_riesgo c"
                                + LOTE_Y_SU_MARCO
                                + " ORDER BY c.codigo")
                .param("predioId", predioId)
                .param("aLaFecha", aLaFecha)
                .query(GestionDeRiesgoRepositoryJdbc::mapearZona)
                .list();
    }

    @Override
    public List<FajaMarginal> fajasQueCruzanElLote(long predioId, LocalDate aLaFecha) {
        return jdbc().sql(
                        "SELECT "
                                + COLUMNAS_FAJA
                                + " FROM faja_marginal c"
                                + LOTE_Y_SU_MARCO
                                + " ORDER BY c.codigo")
                .param("predioId", predioId)
                .param("aLaFecha", aLaFecha)
                .query(GestionDeRiesgoRepositoryJdbc::mapearFaja)
                .list();
    }

    /**
     * Los certificados vigentes a una fecha, <b>filtrados por la base</b>.
     *
     * <p>Los tres extremos van en el {@code WHERE} y no en un {@code filter} de Java, y la
     * diferencia no es de rendimiento: traer todos y descartar en memoria deja el vencido a un
     * refactor de distancia de volver a salir, y el sintoma seria una licencia emitida contra un
     * certificado caducado. El {@code fecha_anulacion > :aLaFecha} es el mismo criterio que {@code
     * CertificadoItse.vigenteA}: una anulacion vale desde su fecha y no hacia atras.
     */
    @Override
    public List<CertificadoItse> itseVigenteA(long predioId, LocalDate aLaFecha) {
        return jdbc().sql(
                        "SELECT "
                                + COLUMNAS_ITSE
                                + " FROM itse"
                                + " WHERE predio_id = :predioId"
                                + " AND vigencia_desde <= :aLaFecha"
                                + " AND vigencia_hasta >= :aLaFecha"
                                + " AND (fecha_anulacion IS NULL OR fecha_anulacion > :aLaFecha)"
                                + " ORDER BY vigencia_hasta DESC, id DESC")
                .param("predioId", predioId)
                .param("aLaFecha", aLaFecha)
                .query(GestionDeRiesgoRepositoryJdbc::mapearItse)
                .list();
    }

    @Override
    public List<CertificadoItse> itseDelPredio(long predioId) {
        return jdbc().sql(
                        "SELECT "
                                + COLUMNAS_ITSE
                                + " FROM itse WHERE predio_id = :predioId"
                                + " ORDER BY vigencia_desde DESC, id DESC")
                .param("predioId", predioId)
                .query(GestionDeRiesgoRepositoryJdbc::mapearItse)
                .list();
    }

    /**
     * El alta de una zona, con su poligono.
     *
     * <p>{@code ST_GeogFromText} interpreta el WKT como WGS84, que es el SRID de la columna: si el
     * texto no es un {@code MULTIPOLYGON} valido falla aqui y no guarda medio poligono. Las cuatro
     * columnas de marco no se escriben —son {@code GENERATED ALWAYS ... STORED} y las deriva el
     * motor—, que es lo que impide que se queden viejas si alguien corrige la geometria.
     */
    @Override
    public ZonaDeRiesgo guardar(ZonaDeRiesgo zona, String geometriaWkt) {
        Long id =
                jdbc().sql(
                                "INSERT INTO zona_riesgo (municipalidad_id, codigo, fenomeno,"
                                        + " nivel, mitigable, fuente, documento_origen,"
                                        + " vigencia_desde, vigencia_hasta, geometria,"
                                        + " observacion, usuario_registro)"
                                        + " VALUES ("
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", :codigo, :fenomeno, :nivel, :mitigable, :fuente,"
                                        + " :documento, :desde, :hasta,"
                                        + " ST_GeogFromText(:geometria), :observacion, :usuario)"
                                        + " RETURNING id")
                        .param("codigo", zona.codigo())
                        .param("fenomeno", zona.fenomeno())
                        .param("nivel", zona.nivel().name())
                        .param("mitigable", zona.mitigable())
                        .param("fuente", zona.fuente())
                        .param("documento", zona.documentoOrigen())
                        .param("desde", zona.vigenciaDesde())
                        .param("hasta", zona.vigenciaHasta())
                        .param("geometria", geometriaWkt)
                        .param("observacion", zona.observacion().texto())
                        .param("usuario", usuarioActual())
                        .query(Long.class)
                        .single();
        return conId(zona, id);
    }

    @Override
    public FajaMarginal guardar(FajaMarginal faja, String geometriaWkt) {
        Long id =
                jdbc().sql(
                                "INSERT INTO faja_marginal (municipalidad_id, codigo, cuerpo_agua,"
                                        + " ancho_m, fuente, documento_origen, vigencia_desde,"
                                        + " vigencia_hasta, geometria, observacion,"
                                        + " usuario_registro)"
                                        + " VALUES ("
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", :codigo, :cuerpo, :ancho, :fuente, :documento,"
                                        + " :desde, :hasta, ST_GeogFromText(:geometria),"
                                        + " :observacion, :usuario)"
                                        + " RETURNING id")
                        .param("codigo", faja.codigo())
                        .param("cuerpo", faja.cuerpoDeAgua())
                        .param("ancho", faja.ancho().magnitud())
                        .param("fuente", faja.fuente())
                        .param("documento", faja.documentoOrigen())
                        .param("desde", faja.vigenciaDesde())
                        .param("hasta", faja.vigenciaHasta())
                        .param("geometria", geometriaWkt)
                        .param("observacion", faja.observacion().texto())
                        .param("usuario", usuarioActual())
                        .query(Long.class)
                        .single();
        return new FajaMarginal(
                id,
                faja.codigo(),
                faja.cuerpoDeAgua(),
                faja.ancho(),
                faja.fuente(),
                faja.documentoOrigen(),
                faja.vigenciaDesde(),
                faja.vigenciaHasta(),
                faja.observacion());
    }

    @Override
    public CertificadoItse guardar(CertificadoItse certificado) {
        Long id =
                jdbc().sql(
                                "INSERT INTO itse (municipalidad_id, predio_id, numero,"
                                        + " nivel_riesgo, modalidad, vigencia_desde,"
                                        + " vigencia_hasta, fecha_anulacion, motivo_anulacion,"
                                        + " observacion, usuario_registro)"
                                        + " VALUES ("
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", :predio, :numero, :nivel, :modalidad, :desde, :hasta,"
                                        + " :anulacion, :motivo, :observacion, :usuario)"
                                        + " RETURNING id")
                        .param("predio", certificado.predioId())
                        .param("numero", certificado.numero())
                        .param("nivel", certificado.nivelRiesgo().name())
                        .param("modalidad", certificado.modalidad().name())
                        .param("desde", certificado.vigenciaDesde())
                        .param("hasta", certificado.vigenciaHasta())
                        .param("anulacion", certificado.fechaAnulacion())
                        .param("motivo", certificado.motivoAnulacion())
                        .param("observacion", certificado.observacion().texto())
                        .param("usuario", usuarioActual())
                        .query(Long.class)
                        .single();
        return new CertificadoItse(
                id,
                certificado.predioId(),
                certificado.numero(),
                certificado.nivelRiesgo(),
                certificado.modalidad(),
                certificado.vigenciaDesde(),
                certificado.vigenciaHasta(),
                certificado.fechaAnulacion(),
                certificado.motivoAnulacion(),
                certificado.observacion());
    }

    private static ZonaDeRiesgo conId(ZonaDeRiesgo zona, Long id) {
        return new ZonaDeRiesgo(
                id,
                zona.codigo(),
                zona.fenomeno(),
                zona.nivel(),
                zona.mitigable(),
                zona.fuente(),
                zona.documentoOrigen(),
                zona.vigenciaDesde(),
                zona.vigenciaHasta(),
                zona.observacion());
    }

    private static ZonaDeRiesgo mapearZona(ResultSet fila, int numero) throws SQLException {
        return new ZonaDeRiesgo(
                fila.getLong("id"),
                fila.getString("codigo"),
                fila.getString("fenomeno"),
                NivelDeRiesgo.porNombre(fila.getString("nivel")),
                fila.getBoolean("mitigable"),
                fila.getString("fuente"),
                fila.getString("documento_origen"),
                fila.getObject("vigencia_desde", LocalDate.class),
                fila.getObject("vigencia_hasta", LocalDate.class),
                Observacion.de(fila.getString("observacion")));
    }

    private static FajaMarginal mapearFaja(ResultSet fila, int numero) throws SQLException {
        BigDecimal ancho =
                Objects.requireNonNull(
                        fila.getBigDecimal("ancho_m"), "ancho_m es NOT NULL en el esquema");
        return new FajaMarginal(
                fila.getLong("id"),
                fila.getString("codigo"),
                fila.getString("cuerpo_agua"),
                new Medida(ancho, "ML"),
                fila.getString("fuente"),
                fila.getString("documento_origen"),
                fila.getObject("vigencia_desde", LocalDate.class),
                fila.getObject("vigencia_hasta", LocalDate.class),
                Observacion.de(fila.getString("observacion")));
    }

    private static CertificadoItse mapearItse(ResultSet fila, int numero) throws SQLException {
        return new CertificadoItse(
                fila.getLong("id"),
                fila.getLong("predio_id"),
                fila.getString("numero"),
                NivelDeRiesgo.porNombre(fila.getString("nivel_riesgo")),
                ModalidadItse.porNombre(fila.getString("modalidad")),
                fila.getObject("vigencia_desde", LocalDate.class),
                fila.getObject("vigencia_hasta", LocalDate.class),
                fila.getObject("fecha_anulacion", LocalDate.class),
                fila.getString("motivo_anulacion"),
                Observacion.de(fila.getString("observacion")));
    }

    /**
     * Con que nombre firma quien escribe.
     *
     * <p>Sale de {@link OrigenContext} y no de la firma del metodo, igual que en {@code
     * FichaCatastralRepositoryJdbc}: el usuario entra una vez en el borde de la aplicacion, y sin
     * el la escritura <b>falla</b> en vez de plantar una fila sin autor.
     */
    private static String usuarioActual() {
        Origen origen = OrigenContext.actual();
        return origen.usuario();
    }
}
