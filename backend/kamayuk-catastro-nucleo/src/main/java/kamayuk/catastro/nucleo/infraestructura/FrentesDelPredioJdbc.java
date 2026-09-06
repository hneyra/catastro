package kamayuk.catastro.nucleo.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.nucleo.dominio.DerivacionDeFrentes;
import kamayuk.catastro.nucleo.dominio.EstadoDeLaLongitud;
import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import kamayuk.catastro.nucleo.dominio.FrentePropuesto;
import kamayuk.catastro.nucleo.dominio.FrentesDelPredio;
import kamayuk.catastro.nucleo.dominio.MargenDelMarco;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Los frentes de un predio contra PostgreSQL con PostGIS (#7, `V6`, `V10`).
 *
 * <p>Como el resto: ninguna lectura filtra por {@code municipalidad_id} —lo hace la politica RLS
 * con lo que {@code SET LOCAL} fijo—, el {@code INSERT} lo toma del motor con {@link
 * #MUNICIPALIDAD_ACTUAL}, el SQL esta escrito y no generado, y no hay un solo {@code DELETE}.
 */
@Repository
public class FrentesDelPredioJdbc extends RepositorioJdbc implements FrentesDelPredio {

    private static final String COLUMNAS =
            "f.id, f.predio_id, f.via_id, v.codigo AS via_codigo, v.nombre AS via_nombre,"
                    + " ST_AsText(f.geometria) AS geometria, f.longitud_m, f.longitud_estado,"
                    + " f.es_principal, f.numeracion, f.retiro_m, f.confirmado_por,"
                    + " f.confirmado_en";

    /**
     * El corte del lote contra el eje de calzada: el marco delante, el operador espacial detras
     * (ADR-0034 regla 2).
     *
     * <h2>Que hace, en una frase</h2>
     *
     * <p>Toma el BORDE del lote, lo cruza con una franja de {@code :tolerancia} metros alrededor
     * del eje de cada via cercana, y se queda con el tramo continuo mas largo que sale. Eso es «la
     * parte de este lote que da a esta calle», y su longitud sobre el elipsoide es la que se
     * <b>propone</b> — nunca la que se cobra (ADR-0021, ver {@code EstadoDeLaLongitud}).
     *
     * <h2>Las dos mitades de ADR-0034, y por que en la misma sentencia</h2>
     *
     * <p>El marco es lo que <b>acota</b>: bajo RLS ningun predicado espacial es <i>leakproof</i>,
     * asi que {@code ST_DWithin} no se promueve por encima de la politica, el GiST no sirve al rol
     * de la aplicacion, la consulta seria CORRECTA y el plan diria «Index» leyendo el catalogo vial
     * entero del inquilino. El {@code ST_DWithin} es lo que <b>decide</b>, y aqui hace falta de
     * verdad: un rectangulo envolvente de una calle diagonal cubre media manzana.
     *
     * <p><b>El marco va ENSANCHADO por {@code :margen}, y sin eso la consulta pierde frentes en
     * silencio</b>: la via que pasa a dos metros del lote pero cuyo rectangulo no llega a tocarlo
     * quedaria descartada antes de que el {@code ST_DWithin} la viera, y el predio de esquina
     * saldria con un frente en vez de dos. De donde sale ese margen —y hasta que latitud vale— lo
     * dice {@link MargenDelMarco}. Ensanchar de mas no produce ningun frente equivocado: lo que
     * decide sigue siendo el predicado metrico de detras.
     *
     * <h2>{@code CROSS JOIN LATERAL} y no un {@code JOIN} llano</h2>
     *
     * <p>Por lo que {@code UrbanoRepositoryJdbc.ZONA_QUE_CONTIENE} midio en #4: con el {@code JOIN}
     * llano las cuatro comparaciones son columna contra columna, o sea condiciones de UNION, y solo
     * son {@code Index Cond} si el planificador pone {@code via} del lado interno del bucle — con
     * el mismo SQL y otro tamano de tablas caen al {@code Join Filter} y el plan vuelve a decir
     * «Index» leyendo el catalogo entero. Con {@code LATERAL}, el predio va fuera —una fila, por su
     * clave primaria— y las desigualdades pasan a ser comparaciones contra un valor.
     *
     * <h2>Tres detalles de PostGIS que decidio el motor y no una preferencia</h2>
     *
     * <ul>
     *   <li>{@code ST_CollectionExtract(..., 2)} antes de {@code ST_LineMerge}: cortar un borde
     *       contra una franja puede dar una {@code GEOMETRYCOLLECTION} con PUNTOS dentro —el lote
     *       que solo toca la franja en una esquina—, y un punto no es un frente.
     *   <li>El tramo mas largo y no la suma: un lote que toca la misma calle en dos tramos
     *       separados tiene UN frente (`V10`, {@code frente_predio_via_uq}), y una suma seria una
     *       longitud que no corresponde a ninguna geometria. Lo que sobra es un hallazgo que
     *       resuelve quien confirma, nunca una cifra que el sistema se inventa.
     *   <li>{@code ST_Length} sobre {@code geography} da METROS sobre el elipsoide; sobre {@code
     *       geometry} en 4326 daria GRADOS. Confundirlos no falla: propone un frente de 0,0002.
     * </ul>
     */
    private static final String CORTE_CONTRA_LAS_VIAS =
            "SELECT c.via_id, ST_AsText(c.tramo) AS geometria,"
                    + "       ST_Length(CAST(c.tramo AS geography)) AS longitud_m"
                    + "  FROM predio p"
                    + "  CROSS JOIN LATERAL ("
                    + "       SELECT vv.id AS via_id,"
                    + "              (SELECT d.geom"
                    + "                 FROM ST_Dump(ST_LineMerge(ST_CollectionExtract("
                    + "                        CAST(ST_Intersection("
                    + "                               CAST(ST_Boundary("
                    + "                                      CAST(p.geometria AS geometry))"
                    + "                                    AS geography),"
                    + "                               ST_Buffer(vv.eje,"
                    + "                                   CAST(:tolerancia AS double precision)))"
                    + "                             AS geometry), 2))) d"
                    + "                ORDER BY ST_Length(CAST(d.geom AS geography)) DESC"
                    + "                LIMIT 1) AS tramo"
                    + "         FROM via vv"
                    + "        WHERE vv.eje IS NOT NULL"
                    + "          AND vv.activa"
                    + "          AND vv.marco_oeste <= p.marco_este  + CAST(:margen AS double precision)"
                    + "          AND vv.marco_sur   <= p.marco_norte + CAST(:margen AS double precision)"
                    + "          AND vv.marco_este  >= p.marco_oeste - CAST(:margen AS double precision)"
                    + "          AND vv.marco_norte >= p.marco_sur   - CAST(:margen AS double precision)"
                    + "          AND ST_DWithin(vv.eje, p.geometria,"
                    + "                         CAST(:tolerancia AS double precision))) c"
                    + " WHERE p.id = :predio"
                    + "   AND p.geometria IS NOT NULL"
                    + "   AND c.tramo IS NOT NULL"
                    + "   AND ST_Length(CAST(c.tramo AS geography)) > 0"
                    + " ORDER BY c.via_id";

    public FrentesDelPredioJdbc(JdbcClient jdbc) {
        super(jdbc);
    }

    @Override
    public List<FrenteDelPredio> deUnPredio(long predioId) {
        return jdbc().sql(
                        "SELECT "
                                + COLUMNAS
                                + " FROM frente_predio f"
                                + " JOIN via v ON v.municipalidad_id = f.municipalidad_id"
                                + "           AND v.id = f.via_id"
                                + " WHERE f.predio_id = :predio"
                                + " ORDER BY f.es_principal DESC, f.via_id, f.id")
                .param("predio", predioId)
                .query(FrentesDelPredioJdbc::mapearFrente)
                .list();
    }

    @Override
    public Optional<DerivacionDeFrentes> ultimaDerivacion(long predioId) {
        return jdbc().sql(
                        "SELECT predio_id, derivado_en, propuestos, motivo"
                                + " FROM frente_derivacion WHERE predio_id = :predio")
                .param("predio", predioId)
                .query(
                        (ResultSet fila, int numero) ->
                                new DerivacionDeFrentes(
                                        fila.getLong("predio_id"),
                                        fila.getTimestamp("derivado_en").toInstant(),
                                        fila.getInt("propuestos"),
                                        fila.getString("motivo")))
                .optional();
    }

    @Override
    public boolean existeElPredio(long predioId) {
        return jdbc().sql("SELECT count(*) FROM predio WHERE id = :predio")
                        .param("predio", predioId)
                        .query(Long.class)
                        .single()
                > 0;
    }

    @Override
    public List<Long> prediosPorDerivar(long desde, int tope) {
        return jdbc().sql("SELECT id FROM predio WHERE id > :desde ORDER BY id LIMIT :tope")
                .param("desde", desde)
                .param("tope", tope)
                .query((ResultSet fila, int numero) -> fila.getLong("id"))
                .list();
    }

    @Override
    public List<FrentePropuesto> cortarContraLasVias(long predioId, Medida tolerancia) {
        return jdbc().sql(CORTE_CONTRA_LAS_VIAS)
                .param("predio", predioId)
                .param("tolerancia", tolerancia.magnitud())
                .param("margen", MargenDelMarco.enGrados(tolerancia).magnitud())
                .query(
                        (ResultSet fila, int numero) ->
                                new FrentePropuesto(
                                        predioId,
                                        fila.getLong("via_id"),
                                        fila.getString("geometria"),
                                        // Sin redondear: lo que el corte midio, con los decimales
                                        // que dio sobre el elipsoide. Con cuantos se GUARDA lo
                                        // decide la columna —`numeric(12,2)`, dato versionado
                                        // (ADR-0032)— y no este codigo: ver
                                        // `FrentesDelPredio.proponer`, que devuelve lo guardado.
                                        new Medida(
                                                fila.getBigDecimal("longitud_m"),
                                                FrenteDelPredio.UNIDAD)))
                .list();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>{@code ON CONFLICT DO NOTHING} sobre {@code frente_predio_via_uq}, no un {@code SELECT}
     * antes.</b> Dos corridas simultaneas del derivador leerian las dos «no esta» y las dos
     * insertarian; aqui la segunda no escribe y lo sabe porque el {@code RETURNING} viene vacio. Es
     * la misma decision que {@code BuzonDeSalidaJdbc.publicar}.
     *
     * <p>Y por eso <b>volver a derivar no pisa una longitud confirmada</b>: no hay ninguna rama que
     * actualice. Lo unico que puede cambiar una longitud es el acto de confirmarla.
     */
    @Override
    public Optional<Medida> proponer(FrentePropuesto propuesto, Observacion observacion) {
        return jdbc().sql(
                        "INSERT INTO frente_predio (municipalidad_id, predio_id, via_id,"
                                + " geometria, longitud_m, longitud_estado, es_principal,"
                                + " observacion, usuario_registro)"
                                + " VALUES ("
                                + MUNICIPALIDAD_ACTUAL
                                + ", :predio, :via, ST_GeogFromText(:wkt), :longitud,"
                                + "  'PROPUESTA', false, :observacion, :usuario)"
                                + " ON CONFLICT (municipalidad_id, predio_id, via_id)"
                                + " DO NOTHING"
                                + " RETURNING longitud_m")
                .param("predio", propuesto.predioId())
                .param("via", propuesto.viaId())
                .param("wkt", "SRID=4326;" + propuesto.geometriaWkt())
                .param("longitud", propuesto.longitud().magnitud())
                .param("observacion", observacion.texto())
                .param("usuario", usuarioActual())
                .query(java.math.BigDecimal.class)
                .optional()
                .map(guardada -> new Medida(guardada, FrenteDelPredio.UNIDAD));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Un {@code UPSERT} y no un {@code INSERT}: el derivador se vuelve a correr, y lo que esta
     * fila dice es <b>cuando fue la ultima vez</b>. Guardar una fila por corrida convertiria una
     * respuesta —«se derivo el martes»— en un historial que nadie pidio y que crece con el padron.
     */
    @Override
    public void anotarDerivacion(DerivacionDeFrentes derivacion) {
        jdbc().sql(
                        "INSERT INTO frente_derivacion (municipalidad_id, predio_id, derivado_en,"
                                + " propuestos, motivo)"
                                + " VALUES ("
                                + MUNICIPALIDAD_ACTUAL
                                + ", :predio, :cuando, :propuestos, :motivo)"
                                + " ON CONFLICT (municipalidad_id, predio_id) DO UPDATE"
                                + " SET derivado_en = EXCLUDED.derivado_en,"
                                + "     propuestos = EXCLUDED.propuestos,"
                                + "     motivo = EXCLUDED.motivo")
                .param("predio", derivacion.predioId())
                .param("cuando", java.sql.Timestamp.from(derivacion.derivadoEn()))
                .param("propuestos", derivacion.propuestos())
                .param("motivo", derivacion.motivo())
                .update();
    }

    @Override
    public FrenteDelPredio confirmar(
            long frenteId, Medida longitud, Observacion observacion, Instant cuando) {
        int filas =
                jdbc().sql(
                                "UPDATE frente_predio"
                                        + " SET longitud_m = :longitud,"
                                        + "     longitud_estado = 'CONFIRMADA',"
                                        + "     confirmado_por = :usuario,"
                                        + "     confirmado_en = :cuando,"
                                        + "     observacion = :observacion"
                                        + " WHERE id = :id")
                        .param("id", frenteId)
                        .param("longitud", longitud.magnitud())
                        .param("usuario", usuarioActual())
                        .param("cuando", java.sql.Timestamp.from(cuando))
                        .param("observacion", observacion.texto())
                        .update();
        if (filas == 0) {
            throw new FrenteInexistente(frenteId);
        }
        return jdbc().sql(
                        "SELECT "
                                + COLUMNAS
                                + " FROM frente_predio f"
                                + " JOIN via v ON v.municipalidad_id = f.municipalidad_id"
                                + "           AND v.id = f.via_id"
                                + " WHERE f.id = :id")
                .param("id", frenteId)
                .query(FrentesDelPredioJdbc::mapearFrente)
                .single();
    }

    private static FrenteDelPredio mapearFrente(ResultSet fila, int numero) throws SQLException {
        java.sql.Timestamp confirmado = fila.getTimestamp("confirmado_en");
        return new FrenteDelPredio(
                fila.getLong("id"),
                fila.getLong("predio_id"),
                fila.getLong("via_id"),
                fila.getString("via_codigo"),
                fila.getString("via_nombre"),
                fila.getString("geometria"),
                new Medida(fila.getBigDecimal("longitud_m"), FrenteDelPredio.UNIDAD),
                EstadoDeLaLongitud.valueOf(fila.getString("longitud_estado")),
                fila.getBoolean("es_principal"),
                fila.getString("numeracion"),
                fila.getBigDecimal("retiro_m") == null
                        ? null
                        : new Medida(fila.getBigDecimal("retiro_m"), FrenteDelPredio.UNIDAD),
                fila.getString("confirmado_por"),
                confirmado == null ? null : confirmado.toInstant());
    }

    private static String usuarioActual() {
        Origen origen = OrigenContext.actual();
        return origen.usuario();
    }
}
