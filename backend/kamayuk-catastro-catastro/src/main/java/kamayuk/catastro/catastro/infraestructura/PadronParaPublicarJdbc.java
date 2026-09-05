package kamayuk.catastro.catastro.infraestructura;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kamayuk.catastro.catastro.dominio.CuotaDeTitular;
import kamayuk.catastro.catastro.dominio.PadronParaPublicar;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Porcentaje;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lee el padron para publicarlo (C-8).
 *
 * <p><b>Ninguna consulta filtra por {@code municipalidad_id}</b> (regla 2): lo hace la politica RLS
 * con el contexto que fijo la transaccion. Sin contexto no devuelven vacio, <b>revientan</b>, que
 * es lo que hace que un olvido se vea (#486).
 */
@Repository
public class PadronParaPublicarJdbc extends RepositorioJdbc implements PadronParaPublicar {

    public PadronParaPublicarJdbc(JdbcClient jdbc) {
        super(jdbc);
    }

    @Override
    public List<LoteDelPadron> lotes() {
        return jdbc().sql(
                        """
                        SELECT p.id AS predio_id, p.codigo_ref_catastral, p.direccion,
                               s.codigo AS sector_codigo, p.estado, p.via_id
                          FROM predio p
                          LEFT JOIN sector s
                            ON s.municipalidad_id = p.municipalidad_id AND s.id = p.sector_id
                         ORDER BY p.id
                        """)
                .query(
                        (fila, numero) ->
                                new LoteDelPadron(
                                        fila.getLong("predio_id"),
                                        fila.getString("codigo_ref_catastral").strip(),
                                        fila.getString("direccion"),
                                        fila.getString("sector_codigo"),
                                        fila.getString("estado"),
                                        (Long) fila.getObject("via_id")))
                .list();
    }

    @Override
    public List<VersionDeFicha> versionesDeFicha() {
        return jdbc().sql(
                        """
                        SELECT f.id AS ficha_id, f.predio_id, f.tipo, f.version,
                               f.vigencia_desde, f.vigencia_hasta, f.area_terreno, f.uso
                          FROM ficha_catastral f
                         ORDER BY f.predio_id, f.version
                        """)
                .query(
                        (fila, numero) ->
                                new VersionDeFicha(
                                        fila.getLong("ficha_id"),
                                        fila.getLong("predio_id"),
                                        fila.getString("tipo"),
                                        fila.getInt("version"),
                                        fila.getDate("vigencia_desde").toLocalDate(),
                                        fila.getDate("vigencia_hasta") == null
                                                ? null
                                                : fila.getDate("vigencia_hasta").toLocalDate(),
                                        new AreaM2(fila.getBigDecimal("area_terreno")),
                                        fila.getString("uso")))
                .list();
    }

    @Override
    public List<FichaVigente> fichasVigentesA(LocalDate fecha) {
        // «Vigente A LA FECHA» y no «la ultima»: el rango se compara contra la fecha de corte
        // (regla 9). `DISTINCT ON` con el orden de version descendente resuelve el empate de dos
        // versiones abiertas —que `V72` de `rentas` ya no admite y este esquema todavia si—
        // quedandose con la mayor, en vez de devolver dos filas para el mismo predio.
        return jdbc().sql(
                        """
                        SELECT DISTINCT ON (f.predio_id) f.predio_id, f.id AS ficha_id
                          FROM ficha_catastral f
                         WHERE f.vigencia_desde <= :fecha
                           AND (f.vigencia_hasta IS NULL OR f.vigencia_hasta >= :fecha)
                         ORDER BY f.predio_id, f.version DESC
                        """)
                .param("fecha", fecha)
                .query(
                        (fila, numero) ->
                                new FichaVigente(
                                        fila.getLong("predio_id"), fila.getLong("ficha_id")))
                .list();
    }

    @Override
    public List<TitularDelPredio> titularesA(LocalDate fecha) {
        return jdbc().sql(
                        """
                        SELECT t.predio_id, t.contribuyente_id, t.condicion, t.porcentaje
                          FROM titularidad t
                         WHERE t.vigencia_desde <= :fecha
                           AND (t.vigencia_hasta IS NULL OR t.vigencia_hasta >= :fecha)
                         ORDER BY t.predio_id, t.contribuyente_id
                        """)
                .param("fecha", fecha)
                .query(
                        (fila, numero) ->
                                new TitularDelPredio(
                                        fila.getLong("predio_id"),
                                        new CuotaDeTitular(
                                                fila.getLong("contribuyente_id"),
                                                fila.getString("condicion"),
                                                new Porcentaje(fila.getBigDecimal("porcentaje")))))
                .list();
    }

    @Override
    public Set<Long> viasConArancel(long conjuntoId) {
        return new HashSet<>(
                jdbc().sql("SELECT DISTINCT via_id FROM arancel WHERE conjunto_id = :conjunto")
                        .param("conjunto", conjuntoId)
                        .query(Long.class)
                        .list());
    }

    @Override
    public boolean hayCuadroDeValoresUnitarios(long conjuntoId) {
        return hayAlgunaFilaEn("normativa_valor_unitario", conjuntoId);
    }

    @Override
    public boolean hayCuadroDeDepreciacion(long conjuntoId) {
        return hayAlgunaFilaEn("normativa_depreciacion", conjuntoId);
    }

    @Override
    public long cuantosPredios() {
        return jdbc().sql("SELECT count(*) FROM predio").query(Long.class).single();
    }

    /**
     * Las dos tablas de la cache sellada, preguntadas igual.
     *
     * <p>El nombre de la tabla se interpola y no viaja como parametro —no se puede—, y por eso este
     * metodo es {@code private} y solo lo llaman los dos de arriba con literales suyos: no hay
     * ninguna cadena de fuera que pueda llegar hasta aqui.
     */
    private boolean hayAlgunaFilaEn(String tabla, long conjuntoId) {
        Boolean hay =
                jdbc().sql(
                                "SELECT EXISTS (SELECT 1 FROM "
                                        + tabla
                                        + " WHERE conjunto_id = :conjunto)")
                        .param("conjunto", conjuntoId)
                        .query(Boolean.class)
                        .single();
        return Boolean.TRUE.equals(hay);
    }
}
