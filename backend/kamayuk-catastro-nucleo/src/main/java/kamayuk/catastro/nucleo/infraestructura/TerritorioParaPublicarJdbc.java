package kamayuk.catastro.nucleo.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.nucleo.dominio.EstadoDeLaLongitud;
import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import kamayuk.catastro.nucleo.dominio.TerritorioParaPublicar;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lo que se lee para publicar el territorio (#7).
 *
 * <p>Tres consultas y ni un {@code JOIN} que salga de este sistema: {@code manzana}, {@code
 * sector}, {@code predio}, {@code via}, {@code frente_predio} y {@code hallazgo} son todas de
 * {@code catastro} (el reparto de la regla 11 lo dice, y el escaner de frontera lo comprueba).
 *
 * <p><b>Por que {@code hallazgo} se lee desde aqui</b> y no lo publica {@code fiscalizacion}: esta
 * escrito en {@link TerritorioParaPublicar}. En resumen, el buzon es de {@code nucleo}, {@code
 * fiscalizacion} no puede importarlo sin que la regla {@code
 * SOLO_LA_TRANSFERENCIA_ESCRIBE_FUERA_DE_FISCALIZACION} lo clasifique como puerto de escritura, y
 * el cruce se resuelve donde este proyecto ya lo resuelve: en una sentencia SQL, como {@code grd} y
 * {@code urbano} hacen con {@code predio}.
 *
 * <p>Ninguna consulta filtra por {@code municipalidad_id}: lo hace la politica RLS (regla 2).
 */
@Repository
public class TerritorioParaPublicarJdbc extends RepositorioJdbc implements TerritorioParaPublicar {

    public TerritorioParaPublicarJdbc(JdbcClient jdbc) {
        super(jdbc);
    }

    @Override
    public List<ManzanaDelTerritorio> manzanas() {
        return jdbc().sql(
                        "SELECT m.id, m.codigo, s.codigo AS sector_codigo,"
                                + "       s.nombre AS sector_nombre"
                                + "  FROM manzana m"
                                + "  JOIN sector s ON s.municipalidad_id = m.municipalidad_id"
                                + "               AND s.id = m.sector_id"
                                + " ORDER BY m.id")
                .query(
                        (ResultSet fila, int numero) ->
                                new ManzanaDelTerritorio(
                                        fila.getLong("id"),
                                        fila.getString("codigo"),
                                        fila.getString("sector_codigo"),
                                        fila.getString("sector_nombre")))
                .list();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Una sola consulta ordenada por predio y agrupada en memoria, y no una por predio: el
     * padron de Catacaos tiene 14 422 predios, y una consulta por cada uno es el defecto que este
     * proyecto ya midio en la publicacion del padron.
     */
    @Override
    public List<FrentesDeUnPredio> frentesPorPredio() {
        Map<Long, List<FrenteDelTerritorio>> porPredio = new LinkedHashMap<>();
        Map<Long, String> codigoDelPredio = new LinkedHashMap<>();

        jdbc().sql(
                        "SELECT f.id, f.predio_id, p.codigo_ref_catastral, f.via_id,"
                                + "       v.codigo AS via_codigo, v.nombre AS via_nombre,"
                                + "       f.longitud_m, f.longitud_estado, f.es_principal,"
                                + "       f.numeracion, f.retiro_m"
                                + "  FROM frente_predio f"
                                + "  JOIN predio p ON p.municipalidad_id = f.municipalidad_id"
                                + "               AND p.id = f.predio_id"
                                + "  JOIN via v ON v.municipalidad_id = f.municipalidad_id"
                                + "            AND v.id = f.via_id"
                                + " ORDER BY f.predio_id, f.via_id, f.id")
                .query(
                        (ResultSet fila, int numero) -> {
                            long predioId = fila.getLong("predio_id");
                            codigoDelPredio.putIfAbsent(
                                    predioId, fila.getString("codigo_ref_catastral"));
                            porPredio
                                    .computeIfAbsent(predioId, sinFrentes -> new ArrayList<>())
                                    .add(frenteDe(fila));
                            return predioId;
                        })
                .list();

        List<FrentesDeUnPredio> publicables = new ArrayList<>();
        for (Map.Entry<Long, List<FrenteDelTerritorio>> entrada : porPredio.entrySet()) {
            String codigo = codigoDelPredio.get(entrada.getKey());
            if (codigo == null) {
                // No puede pasar: las dos tablas se llenan en el mismo recorrido. Si pasara, lo
                // que NO se hace es publicar un predio con el codigo en blanco: un codigo vacio
                // en `predio_ref` de `rentas` es un predio que nadie puede volver a encontrar.
                throw new IllegalStateException(
                        "El predio " + entrada.getKey() + " tiene frentes y no tiene codigo");
            }
            publicables.add(new FrentesDeUnPredio(entrada.getKey(), codigo, entrada.getValue()));
        }
        return List.copyOf(publicables);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Solo los {@code FIRME}. Un hallazgo dejado sin efecto no se publica y tampoco se retira de
     * lo ya publicado: retirarlo seria borrar un hecho que ya salio, y aqui no se borra nada (regla
     * 4). Que un hallazgo se haya dejado sin efecto es OTRO hecho, y hoy no viaja — declarado, no
     * escondido.
     */
    @Override
    public List<HallazgoFirme> hallazgosFirmes() {
        return jdbc().sql(
                        "SELECT h.id, h.candidato_id, h.clase, h.predio_id, h.ficha_id,"
                                + "       h.area_de_la_ficha, h.area_verificada, h.inspector,"
                                + "       h.verificado_en"
                                + "  FROM hallazgo h"
                                + " WHERE h.estado = 'FIRME'"
                                + " ORDER BY h.id")
                .query(
                        (ResultSet fila, int numero) ->
                                new HallazgoFirme(
                                        fila.getLong("id"),
                                        fila.getLong("candidato_id"),
                                        fila.getString("clase"),
                                        (Long) fila.getObject("predio_id"),
                                        (Long) fila.getObject("ficha_id"),
                                        fila.getBigDecimal("area_de_la_ficha") == null
                                                ? null
                                                : new AreaM2(
                                                        fila.getBigDecimal("area_de_la_ficha")),
                                        new AreaM2(fila.getBigDecimal("area_verificada")),
                                        fila.getString("inspector"),
                                        fila.getDate("verificado_en").toLocalDate()))
                .list();
    }

    private static FrenteDelTerritorio frenteDe(ResultSet fila) throws SQLException {
        return new FrenteDelTerritorio(
                fila.getLong("id"),
                fila.getLong("via_id"),
                fila.getString("via_codigo"),
                fila.getString("via_nombre"),
                new Medida(fila.getBigDecimal("longitud_m"), FrenteDelPredio.UNIDAD),
                EstadoDeLaLongitud.valueOf(fila.getString("longitud_estado")),
                fila.getBoolean("es_principal"),
                fila.getString("numeracion"),
                fila.getBigDecimal("retiro_m") == null
                        ? null
                        : new Medida(fila.getBigDecimal("retiro_m"), FrenteDelPredio.UNIDAD));
    }
}
