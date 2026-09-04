package kamayuk.catastro.catastro.infraestructura;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kamayuk.catastro.catastro.dominio.HuellaDelLote;
import kamayuk.catastro.catastro.dominio.HuellasDelPadron;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Las huellas del padron, calculadas EN JAVA sobre las filas que el motor devuelve.
 *
 * <h2>Por que no se calcula en SQL, que seria mas barato</h2>
 *
 * <p>PostgreSQL sabe hacer un {@code sha256(...)} y un {@code string_agg(... ORDER BY ...)}, y
 * saldria una consulta y no un recorrido. Se calcula en Java a proposito, y el motivo es el que
 * hace util toda esta comprobacion: la huella tiene que ser <b>la misma</b> que calcula {@code
 * rentas}, y {@code rentas} la calcula con {@link HuellaDelLote} sobre filas de su proyeccion. Dos
 * implementaciones —una en SQL, otra en Java— son dos sitios donde el separador, el orden o la
 * codificacion pueden divergir, y divergir ahi no falla ruidosamente: o todos los sectores salen
 * discrepantes o ninguno.
 *
 * <p>Con la funcion pura compartida por vectores de oro ({@code huella-del-lote.json}), lo unico
 * que este archivo decide es <b>que filas</b> entran y <b>en que orden</b>, que es lo que el SQL
 * tiene que decir de todos modos.
 *
 * <p><b>Ninguna consulta filtra por {@code municipalidad_id}</b> (regla 2): lo hace la politica RLS
 * con el contexto que fijo la transaccion. Sin contexto la consulta falla en vez de devolver el
 * padron de todas, que es lo que hace que un olvido se vea.
 */
@Repository
public class HuellasDelPadronJdbc extends RepositorioJdbc implements HuellasDelPadron {

    /**
     * Las cinco columnas que la proyeccion de {@code rentas} copia, y el orden del recorrido.
     *
     * <p>{@code ORDER BY p.id} y no por el codigo: la huella del sector se compone por
     * identificador ascendente, y esa decision esta fijada en los vectores de oro con un caso de
     * dos lotes.
     */
    private static final String LOTES =
            """
            SELECT p.id            AS predio_id,
                   p.codigo_ref_catastral,
                   p.direccion,
                   s.codigo        AS sector_codigo,
                   p.estado
              FROM predio p
              LEFT JOIN sector s ON s.municipalidad_id = p.municipalidad_id AND s.id = p.sector_id
             ORDER BY p.id
            """;

    public HuellasDelPadronJdbc(JdbcClient jdbc) {
        super(jdbc);
    }

    @Override
    public List<HuellaDeSector> porSector() {
        List<Fila> filas = todasLasFilas();

        // Se agrupa recorriendo, no con un `Map`: el recorrido llega ordenado por `predio_id` y
        // agrupar con un `HashMap` perderia ese orden dentro de cada sector — que es justo lo
        // que decide la huella.
        List<HuellaDeSector> huellas = new ArrayList<>();
        List<String> deEsteSector = new ArrayList<>();
        String sectorEnCurso = null;
        boolean empezado = false;

        List<Fila> porSector = new ArrayList<>(filas);
        porSector.sort(
                java.util.Comparator.comparing(
                                (Fila fila) ->
                                        fila.sectorCodigo() == null ? "" : fila.sectorCodigo())
                        .thenComparingLong(Fila::predioId));

        for (Fila fila : porSector) {
            if (empezado && !Objects.equals(sectorEnCurso, fila.sectorCodigo())) {
                huellas.add(
                        new HuellaDeSector(
                                sectorEnCurso,
                                deEsteSector.size(),
                                HuellaDelLote.deUnSector(List.copyOf(deEsteSector))));
                deEsteSector.clear();
            }
            sectorEnCurso = fila.sectorCodigo();
            empezado = true;
            deEsteSector.add(fila.huella());
        }
        if (empezado) {
            huellas.add(
                    new HuellaDeSector(
                            sectorEnCurso,
                            deEsteSector.size(),
                            HuellaDelLote.deUnSector(List.copyOf(deEsteSector))));
        }
        return List.copyOf(huellas);
    }

    @Override
    public List<HuellaDeLote> deUnSector(@Nullable String sectorCodigo) {
        List<HuellaDeLote> lotes = new ArrayList<>();
        for (Fila fila : todasLasFilas()) {
            if (Objects.equals(fila.sectorCodigo(), sectorCodigo)) {
                lotes.add(new HuellaDeLote(fila.predioId(), fila.huella()));
            }
        }
        return List.copyOf(lotes);
    }

    private List<Fila> todasLasFilas() {
        return jdbc().sql(LOTES)
                .query(
                        (rs, fila) ->
                                new Fila(
                                        rs.getLong("predio_id"),
                                        rs.getString("codigo_ref_catastral"),
                                        rs.getString("direccion"),
                                        rs.getString("sector_codigo"),
                                        rs.getString("estado")))
                .list();
    }

    private record Fila(
            long predioId,
            String codRefCatastral,
            String direccion,
            @Nullable String sectorCodigo,
            String estado) {

        String huella() {
            return HuellaDelLote.deUnLote(
                    predioId, codRefCatastral, direccion, sectorCodigo, estado);
        }
    }
}
