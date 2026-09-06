package kamayuk.catastro.nucleo.infraestructura;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Porcentaje;
import kamayuk.catastro.dominio.ValorNormativo;
import kamayuk.catastro.nucleo.dominio.CuotaDeTitular;
import kamayuk.catastro.nucleo.dominio.PadronParaPublicar;
import kamayuk.catastro.nucleo.dominio.Partida;
import kamayuk.catastro.nucleo.dominio.ValorUnitarioEdificacion;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import org.jspecify.annotations.Nullable;
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
    public List<ConstruccionDeLaFicha> construcciones() {
        return jdbc().sql(
                        """
                        SELECT c.ficha_id, c.piso, c.area_construida, c.anio_construccion,
                               c.categoria_muros, c.categoria_techos, c.categoria_puertas
                          FROM construccion c
                         ORDER BY c.ficha_id, c.piso
                        """)
                .query(
                        (fila, numero) ->
                                new ConstruccionDeLaFicha(
                                        fila.getLong("ficha_id"),
                                        fila.getString("piso"),
                                        new AreaM2(fila.getBigDecimal("area_construida")),
                                        (Integer) fila.getObject("anio_construccion"),
                                        letra(fila.getString("categoria_muros")),
                                        letra(fila.getString("categoria_techos")),
                                        letra(fila.getString("categoria_puertas"))))
                .list();
    }

    /**
     * La letra de una categoria, o nulo si la ficha no la declara.
     *
     * <p>Nulo <b>no</b> es «sin techo» ni «sin puertas»: esas son las casillas {@code H} e {@code
     * I} del propio cuadro, con su cifra. Confundirlas seria valorizar al 0,00 una edificacion que
     * el tecnico no llego a describir.
     */
    private static @Nullable Character letra(@Nullable String celda) {
        return celda == null || celda.isBlank() ? null : celda.charAt(0);
    }

    @Override
    public List<ObrasDeLaFicha> obrasComplementarias() {
        return jdbc().sql(
                        """
                        SELECT o.ficha_id, count(*) AS cuantas
                          FROM otra_instalacion o
                         GROUP BY o.ficha_id
                         ORDER BY o.ficha_id
                        """)
                .query(
                        (fila, numero) ->
                                new ObrasDeLaFicha(
                                        fila.getLong("ficha_id"), fila.getInt("cuantas")))
                .list();
    }

    @Override
    public Map<Long, ValorNormativo> arancelSinTramoPorVia(long conjuntoId) {
        // `tramo IS NULL` es el arancel de la via entera, y `arancel_sin_tramo_uq` (V1) garantiza
        // que sea uno solo por via y conjunto. Los aranceles CON tramo no se leen aqui a
        // proposito: `predio` no dice en que tramo de su via esta, asi que elegir uno seria
        // inventar el dato que falta.
        //
        // Y el `merge` que revienta no es paranoia: quitar el `AND tramo IS NULL` de la consulta
        // de arriba —la rotura obvia— pasaba en VERDE, porque un `put` se queda con la ultima
        // fila que llegue y el orden lo decide el plan. Un arancel elegido por el planificador es
        // exactamente la clase de cifra que nadie audita, asi que aqui se prefiere el ruido.
        Map<Long, ValorNormativo> porVia = new HashMap<>();
        jdbc().sql(
                        """
                        SELECT via_id, valor_m2
                          FROM arancel
                         WHERE conjunto_id = :conjunto AND tramo IS NULL
                        """)
                .param("conjunto", conjuntoId)
                .query(
                        (fila, numero) ->
                                porVia.merge(
                                        fila.getLong("via_id"),
                                        new ValorNormativo(fila.getBigDecimal("valor_m2")),
                                        (uno, otro) -> {
                                            throw new IllegalStateException(
                                                    "La via tiene mas de un arancel aplicable en"
                                                            + " el conjunto sellado ("
                                                            + uno
                                                            + " y "
                                                            + otro
                                                            + "), y el predio no dice en que"
                                                            + " tramo esta: elegir uno seria"
                                                            + " inventar el dato que falta");
                                        }))
                .list();
        return Map.copyOf(porVia);
    }

    @Override
    public List<ValorUnitarioEdificacion> cuadroDeValoresUnitarios(long conjuntoId) {
        return jdbc().sql(
                        """
                        SELECT v.partida, v.categoria, v.anio_construccion_desde,
                               v.anio_construccion_hasta, v.valor_m2, v.documento_fuente
                          FROM normativa_valor_unitario v
                         WHERE v.conjunto_id = :conjunto
                         ORDER BY v.partida, v.categoria, v.anio_construccion_desde
                        """)
                .param("conjunto", conjuntoId)
                .query(
                        (fila, numero) ->
                                new ValorUnitarioEdificacion(
                                        // La copia local no lleva `id` propio: el de `normativa`
                                        // no significa nada aqui. Lo mismo que hace
                                        // `ValuacionRepositoryJdbc` con la misma tabla.
                                        0L,
                                        Partida.valueOf(fila.getString("partida")),
                                        fila.getString("categoria").charAt(0),
                                        fila.getInt("anio_construccion_desde"),
                                        (Integer) fila.getObject("anio_construccion_hasta"),
                                        new ValorNormativo(fila.getBigDecimal("valor_m2")),
                                        fila.getString("documento_fuente")))
                .list();
    }

    @Override
    public boolean hayCuadroDeDepreciacion(long conjuntoId) {
        Boolean hay =
                jdbc().sql(
                                "SELECT EXISTS (SELECT 1 FROM normativa_depreciacion"
                                        + " WHERE conjunto_id = :conjunto)")
                        .param("conjunto", conjuntoId)
                        .query(Boolean.class)
                        .single();
        return Boolean.TRUE.equals(hay);
    }

    @Override
    public long cuantosPredios() {
        return jdbc().sql("SELECT count(*) FROM predio").query(Long.class).single();
    }
}
