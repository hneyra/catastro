package kamayuk.catastro.urbano.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import kamayuk.catastro.urbano.dominio.EstadoDelPredio;
import kamayuk.catastro.urbano.dominio.ParametroUrbanistico;
import kamayuk.catastro.urbano.dominio.UrbanoRepository;
import kamayuk.catastro.urbano.dominio.Zona;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementacion JDBC de {@code urbano} (#4).
 *
 * <p>Como el resto: ninguna lectura filtra por {@code municipalidad_id} —lo hace la politica RLS
 * con lo que {@code SET LOCAL} fijo—, el {@code INSERT} lo toma del motor con {@link
 * #MUNICIPALIDAD_ACTUAL}, el SQL esta escrito y no generado, y no hay un solo {@code DELETE}.
 */
@Repository
public class UrbanoRepositoryJdbc extends RepositorioJdbc implements UrbanoRepository {

    /**
     * Las columnas de una zona, con el alias {@code z.} delante.
     *
     * <p>Calificadas y no a secas, y no es cosmetica: mientras la consulta de contencion fue un
     * {@code JOIN} llano contra {@code predio} —las dos tablas tienen {@code id} y {@code
     * geometria}— PostgreSQL contestaba «column reference "id" is ambiguous». Hoy esa consulta
     * escribe las suyas dentro del {@code LATERAL} y esta constante la usa solo {@link
     * #zonaPorCodigo}; el alias se conserva porque las dos hablan de lo mismo y una de las dos sin
     * el invitaria a quitarselo a la otra.
     */
    private static final String COLUMNAS_DE_ZONA =
            "z.id, z.plan, z.ordenanza, z.codigo, z.nombre, ST_AsText(z.geometria) AS geometria,"
                    + " z.vigencia_desde, z.vigencia_hasta";

    /**
     * A que zona cae ESTE predio: el marco delante, el operador espacial detras (ADR-0034 regla 2).
     *
     * <h2>Las dos mitades, y por que en la misma sentencia</h2>
     *
     * <p>El marco es lo que <b>acota</b>: bajo RLS ningun predicado espacial es <i>leakproof</i>,
     * asi que no se promueve por encima de la politica y el GiST no sirve al rol de la aplicacion
     * —la consulta seria CORRECTA, el plan diria «Index» y leeria la zonificacion entera del
     * inquilino—. Con {@code zonificacion_marco_ix}, las cuatro desigualdades y la condicion de la
     * politica salen juntas en el {@code Index Cond}.
     *
     * <p>El {@code ST_Contains} es lo que <b>decide</b>, y aqui si hace falta de verdad: dos zonas
     * vecinas tienen marcos que se cortan —un rectangulo envolvente no es el poligono— y quedarse
     * en el marco devolveria las dos. Es exactamente la salida que ADR-0034 regla 2 admite: «el
     * operador solo detras, como refinado exacto». Escrita sin el marco delante, {@code
     * revisarEspacial} pone el build en rojo, y con razon.
     *
     * <h2>Por que {@code LATERAL} y no un {@code JOIN} llano, que fue lo primero que se escribio
     * </h2>
     *
     * <p>Porque con el {@code JOIN} llano <b>el marco no llega al indice, y de que llegue decide el
     * planificador</b>. Las cuatro comparaciones son columna contra columna —{@code z.marco_oeste
     * <= p.marco_este}—, o sea condiciones de UNION: solo pueden ser {@code Index Cond} sobre
     * {@code zonificacion} si el planificador la pone del lado INTERNO del bucle. Medido contra
     * PostgreSQL 16.13 con PostGIS 3.4.2, el mismo SQL da los dos planes segun el tamano de las
     * tablas: con {@code zonificacion} fuera, las cuatro caen al {@code Join Filter} y el plan
     * <b>vuelve a decir «Index»</b> mientras lee toda la zonificacion del inquilino — el quinto
     * hallazgo de RLS reproducido por la puerta de atras, sin que ningun operador espacial este mal
     * escrito.
     *
     * <p>{@code CROSS JOIN LATERAL} lo fija: el predio va fuera —una fila, por su clave primaria— y
     * la zonificacion dentro, <b>parametrizada</b> por sus cuatro columnas. Entonces las
     * desigualdades dejan de ser condiciones de union y pasan a ser lo que ya eran en {@code
     * CatastroRepositoryJdbc.EN_EL_MARCO}: comparaciones contra un valor. Medido con 18 000 zonas
     * en tres municipalidades: {@code Index Scan using zonificacion_marco_ix}, las cuatro columnas
     * y la politica en el {@code Index Cond}, {@code st_contains} en el {@code Filter} detras, 65
     * bloques y 0,86 ms — contra 219 bloques escribiendo solo el {@code ST_Contains} y 216
     * escribiendo {@code &&}, que en los dos casos es {@code Seq Scan} y el GiST sin usar.
     *
     * <h2>Un punto representativo y no el poligono entero</h2>
     *
     * <p>{@code ST_PointOnSurface} da un punto <b>garantizado dentro</b> del lote —el centroide no
     * lo garantiza: el de una L cae fuera—. Se usa el punto y no {@code ST_Contains(zona, lote)}
     * porque un lote que asoma un centimetro sobre la zona vecina no esta contenido en ninguna, y
     * entonces un predio de esquina no tendria zona; y no {@code ST_Intersects} porque un lote que
     * toca dos zonas devolveria dos y «la zona de este predio» dejaria de tener una respuesta.
     *
     * <p><b>La regla queda dicha:</b> la zona de un predio es la del punto interior de su lote. Es
     * determinista, es la que usa cualquier oficina de desarrollo urbano al leer un plano, y cuando
     * el lote cruza dos zonas lo que hay es un hallazgo que se informa —como el area que no cuadra
     * (ADR-0021)—, no una respuesta que el sistema se inventa.
     *
     * <h2>La fecha entra como argumento (regla 9)</h2>
     *
     * <p>No existe «la zona»: existe la zona vigente a una fecha. {@code vigencia_hasta} es
     * inclusiva, como en todo este esquema, y por eso la condicion es {@code >=} y no {@code >}.
     */
    static final String ZONA_QUE_CONTIENE =
            "SELECT z.id, z.plan, z.ordenanza, z.codigo, z.nombre, z.geometria,"
                    + " z.vigencia_desde, z.vigencia_hasta"
                    + " FROM predio p"
                    + " CROSS JOIN LATERAL ("
                    + "   SELECT zz.id, zz.plan, zz.ordenanza, zz.codigo, zz.nombre,"
                    + "          ST_AsText(zz.geometria) AS geometria,"
                    + "          zz.vigencia_desde, zz.vigencia_hasta"
                    + "     FROM zonificacion zz"
                    + "    WHERE zz.marco_oeste <= p.marco_este"
                    + "      AND zz.marco_sur   <= p.marco_norte"
                    + "      AND zz.marco_este  >= p.marco_oeste"
                    + "      AND zz.marco_norte >= p.marco_sur"
                    + "      AND zz.vigencia_desde <= :fecha"
                    + "      AND (zz.vigencia_hasta IS NULL OR zz.vigencia_hasta >= :fecha)"
                    + "      AND ST_Contains("
                    + "            CAST(zz.geometria AS geometry),"
                    + "            ST_PointOnSurface(CAST(p.geometria AS geometry)))"
                    + "    LIMIT 1) z"
                    + " WHERE p.id = :predio AND p.geometria IS NOT NULL";

    public UrbanoRepositoryJdbc(JdbcClient jdbc) {
        super(jdbc);
    }

    @Override
    public EstadoDelPredio estadoDelPredio(long predioId) {
        return jdbc().sql("SELECT geometria IS NOT NULL FROM predio WHERE id = :id")
                .param("id", predioId)
                .query(Boolean.class)
                .optional()
                .map(tiene -> tiene ? EstadoDelPredio.CON_GEOMETRIA : EstadoDelPredio.SIN_GEOMETRIA)
                .orElse(EstadoDelPredio.NO_ESTA);
    }

    @Override
    public Optional<Zona> zonaQueContieneAlPredio(long predioId, LocalDate aLaFecha) {
        return jdbc().sql(ZONA_QUE_CONTIENE)
                .param("predio", predioId)
                .param("fecha", aLaFecha)
                .query(UrbanoRepositoryJdbc::mapearZona)
                .optional();
    }

    @Override
    public List<ParametroUrbanistico> parametrosDe(long zonificacionId) {
        return jdbc().sql(
                        "SELECT clave, valor, unidad FROM parametro_urbanistico"
                                + " WHERE zonificacion_id = :zona ORDER BY id")
                .param("zona", zonificacionId)
                .query(
                        (ResultSet fila, int numero) ->
                                new ParametroUrbanistico(
                                        fila.getString("clave"),
                                        fila.getString("valor"),
                                        fila.getString("unidad")))
                .list();
    }

    @Override
    public Optional<Zona> zonaPorCodigo(String plan, String codigo, LocalDate vigenciaDesde) {
        return jdbc().sql(
                        "SELECT "
                                + COLUMNAS_DE_ZONA
                                + " FROM zonificacion z"
                                + " WHERE z.plan = :plan AND z.codigo = :codigo"
                                + " AND z.vigencia_desde = :desde")
                .param("plan", plan)
                .param("codigo", codigo)
                .param("desde", vigenciaDesde)
                .query(UrbanoRepositoryJdbc::mapearZona)
                .optional();
    }

    @Override
    public long guardar(Zona zona, Observacion observacion) {
        // ST_GeogFromText interpreta el WKT como WGS84, que es el SRID de la columna. Si el texto
        // no es un MULTIPOLYGON valido, falla aqui y no guarda media zona.
        return jdbc().sql(
                        "INSERT INTO zonificacion"
                                + " (municipalidad_id, plan, ordenanza, codigo, nombre, geometria,"
                                + "  vigencia_desde, vigencia_hasta, observacion, usuario_registro)"
                                + " VALUES ("
                                + MUNICIPALIDAD_ACTUAL
                                + ", :plan, :ordenanza, :codigo, :nombre,"
                                + "  ST_GeogFromText(:wkt), :desde, :hasta, :observacion, :usuario)"
                                + " RETURNING id")
                .param("plan", zona.plan())
                .param("ordenanza", zona.ordenanza())
                .param("codigo", zona.codigo())
                .param("nombre", zona.nombre())
                .param("wkt", zona.geometriaWkt())
                .param("desde", zona.vigenciaDesde())
                .param("hasta", zona.vigenciaHasta())
                .param("observacion", observacion.texto())
                .param("usuario", usuarioActual())
                .query(Long.class)
                .single();
    }

    @Override
    public void guardarParametros(
            long zonificacionId, List<ParametroUrbanistico> parametros, Observacion observacion) {
        for (ParametroUrbanistico parametro : parametros) {
            jdbc().sql(
                            "INSERT INTO parametro_urbanistico"
                                    + " (municipalidad_id, zonificacion_id, clave, valor, unidad,"
                                    + "  observacion, usuario_registro)"
                                    + " VALUES ("
                                    + MUNICIPALIDAD_ACTUAL
                                    + ", :zona, :clave, :valor, :unidad, :observacion, :usuario)")
                    .param("zona", zonificacionId)
                    .param("clave", parametro.clave())
                    .param("valor", parametro.valor())
                    .param("unidad", parametro.unidad())
                    .param("observacion", observacion.texto())
                    .param("usuario", usuarioActual())
                    .update();
        }
    }

    private static Zona mapearZona(ResultSet fila, int numero) throws SQLException {
        LocalDate hasta =
                fila.getDate("vigencia_hasta") == null
                        ? null
                        : fila.getDate("vigencia_hasta").toLocalDate();
        return new Zona(
                fila.getLong("id"),
                fila.getString("plan"),
                fila.getString("ordenanza"),
                fila.getString("codigo"),
                fila.getString("nombre"),
                fila.getString("geometria"),
                fila.getDate("vigencia_desde").toLocalDate(),
                hasta);
    }

    private static String usuarioActual() {
        Origen origen = OrigenContext.actual();
        return origen.usuario();
    }
}
