package kamayuk.catastro.fiscalizacion.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron.Cobertura;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron.CruceDelPadron;
import kamayuk.catastro.fiscalizacion.dominio.ContrasteDeAreas;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * El cruce que sostiene el detector de subvaluadores (AC 8 de #6, ADR-0021).
 *
 * <h2>Una consulta y no un puerto por predio</h2>
 *
 * <p>La condicion —«el area inscrita difiere del poligono mas que la tolerancia»— <b>se deriva del
 * cruce</b> de dos tablas, asi que preguntarla predio a predio significaria traer el padron entero
 * de la municipalidad para descartar el 99 %. Es la misma forma, y el mismo motivo, que {@code
 * DeteccionRepositoryJdbc} en la fiscalizacion tributaria de {@code rentas}.
 *
 * <p>Las dos tablas que lee son de <b>este</b> sistema: {@code predio} y {@code ficha_catastral}
 * estan en {@code DE_CATASTRO}, asi que esto no cruza ninguna frontera de sistema (regla 11). Lo
 * que si cruzaria es leerlas por HTTP desde {@code rentas}, y eso no es lo que pasa aqui.
 *
 * <h2>{@code ST_Area} calcula, y no escribe</h2>
 *
 * <p>El area del poligono se calcula <b>para comparar</b> y no se guarda en ninguna columna.
 * Derivar el area del terreno del poligono cambiaria el autovaluo de todo el padron sin que nadie
 * lo decidiera, y un area es indistinguible de otra al leerla (ADR-0021). Aqui sale, se compara y
 * viaja dentro de los {@code insumos} del candidato — que es un registro de por que se sospecho, no
 * un dato del predio.
 *
 * <p><b>No hay operador espacial en el {@code WHERE}</b> (ADR-0034 regla 2). {@code ST_Area} es una
 * funcion de calculo sobre la fila que la politica ya dejo pasar, no un predicado que tenga que
 * llegar a un indice: la consulta acota por {@code geometria IS NOT NULL}, que es una condicion
 * sobre nulidad y no una comparacion espacial.
 *
 * <h2>Sin poligonos LANZA, y esa es la mitad que importa</h2>
 *
 * <p>Ver {@link AreasDelPadron}: hoy no hay ni un poligono cargado en ninguna instalacion, y
 * devolver una lista vacia seria afirmar «no hay subvaluadores».
 */
@Repository
public class AreasDelPadronJdbc extends RepositorioJdbc implements AreasDelPadron {

    /**
     * El cruce.
     *
     * <h2>Las CUATRO clases de ficha, y una fila por predio (#25 AC-3)</h2>
     *
     * <p>Hasta #25 el {@code WHERE} llevaba {@code AND f.tipo = 'UNICA'} y las otras tres clases
     * quedaban fuera <b>en silencio</b>: un subvaluador en una quinta, una galeria o un predio
     * rustico no se detectaba nunca, y la salida era «0 candidatos», que se lee como «no hay
     * subvaluadores».
     *
     * <p>Ahora entran las cuatro, y la decision de <b>que area contrasta cada una</b> esta medida y
     * no supuesta: {@code ficha_catastral.area_terreno} es {@code NOT NULL} en {@code V1} para las
     * cuatro (su {@code ficha_catastral_tipo_check} admite {@code UNICA}, {@code ECONOMICA}, {@code
     * BIENES_COMUNES} y {@code RURAL}), y es el area del terreno del <b>lote</b> en las cuatro. No
     * hay ninguna que contraste otra cosa.
     *
     * <p><b>Pero una fila por predio, no cuatro.</b> {@code ficha_vigente_uq} es por {@code
     * (municipalidad_id, predio_id, tipo)}, asi que un predio puede tener hasta cuatro fichas
     * vigentes a la vez; contrastarlas todas daria cuatro candidatos del mismo predio con la misma
     * diferencia, que es exactamente lo que el javadoc anterior temia y por lo que se habia quedado
     * en una sola clase. Se toma una con {@code DISTINCT ON (p.id)} y una <b>precedencia
     * escrita</b> —{@code UNICA}, {@code ECONOMICA}, {@code BIENES_COMUNES}, {@code RURAL}, y a
     * igualdad la mas antigua—: la {@code UNICA} primero porque es la ficha del predio en el
     * manual, y las otras cuando es la unica que hay. Escrita y no dejada al planificador, que es
     * lo que hace que dos corridas devuelvan lo mismo.
     *
     * <h2>El umbral es UNO, y la comparacion es {@code &gt;=}</h2>
     *
     * <p>{@code >= :umbral} y no {@code > :tolerancia} (#25 AC-1): la cifra es la que la campania
     * guarda, y la comparacion es la que {@link Score#alcanza} define. Con {@code >} estricto el
     * predio que difiere <b>exactamente</b> lo que la campania declaro sospechoso se caia del
     * cruce, y nada lo decia.
     *
     * <p>{@code ST_Area(geography)} devuelve <b>metros cuadrados sobre el elipsoide</b>, que es la
     * unidad de {@code area_terreno}: `geography` mide en metros sin elegir zona UTM, y eso es
     * justamente por lo que ADR-0021 la eligio.
     *
     * <p><b>La diferencia relativa se calcula UNA vez</b>, en la subconsulta, y de ahi salen las
     * tres cosas que hacen falta: el filtro por umbral, el orden y el valor que viaja al candidato
     * como su score. Escrita tres veces —o dos aqui y una en Java— seria la misma formula en tres
     * sitios que pueden divergir.
     *
     * <p>Se acota a 1 con {@code LEAST}: un predio inscrito con 1 m2 y un poligono de 300 daria 299
     * y ordenaria la cola por magnitud del error de digitacion en vez de por sospecha. El tipo
     * {@code Score} exige ese rango, y aqui es donde se respeta.
     *
     * <p>{@code count(*) OVER ()} cuenta los que alcanzan el umbral <b>antes</b> del {@code LIMIT}:
     * sin el, «cuantos quedaron fuera por el tope» habria que contarlo sobre la lista devuelta, que
     * es justamente la que el tope ya recorto.
     *
     * <p>Ningun operador espacial en el {@code WHERE} (ADR-0034 regla 2): {@code ST_Area} calcula
     * sobre la fila que la politica ya dejo pasar, y lo que acota es {@code geometria IS NOT NULL},
     * que es una condicion de nulidad y no una comparacion espacial.
     */
    private static final String CRUCE =
            "SELECT predio_id, ficha_id, ficha_tipo, codigo_ref_catastral, area_terreno,"
                    + "       area_poligono, LEAST(diferencia, 1) AS diferencia_relativa,"
                    + "       geometria_wkt, count(*) OVER () AS superan_el_umbral"
                    + "  FROM ("
                    + "   SELECT DISTINCT ON (p.id)"
                    + "          p.id AS predio_id,"
                    + "          f.id AS ficha_id,"
                    + "          f.tipo AS ficha_tipo,"
                    + "          p.codigo_ref_catastral,"
                    + "          f.area_terreno,"
                    + "          ROUND(ST_Area(p.geometria)::numeric, 2) AS area_poligono,"
                    + "          ROUND(abs(ROUND(ST_Area(p.geometria)::numeric, 2)"
                    + "                    - f.area_terreno) / f.area_terreno, 4) AS diferencia,"
                    + "          ST_AsText(p.geometria) AS geometria_wkt"
                    + "     FROM predio p"
                    + "     JOIN ficha_catastral f ON f.predio_id = p.id"
                    + "    WHERE p.geometria IS NOT NULL"
                    + "      AND p.estado = 'ACTIVO'"
                    + "      AND f.vigencia_desde <= :hoy"
                    + "      AND (f.vigencia_hasta IS NULL OR f.vigencia_hasta >= :hoy)"
                    + "      AND f.area_terreno > 0"
                    + "    ORDER BY p.id, "
                    + "        CASE f.tipo WHEN 'UNICA' THEN 0"
                    + "                       WHEN 'ECONOMICA' THEN 1"
                    + "                       WHEN 'BIENES_COMUNES' THEN 2"
                    + "                       ELSE 3 END, f.id"
                    + "  ) AS contraste"
                    + " WHERE diferencia >= :umbral"
                    + " ORDER BY diferencia_relativa DESC, predio_id"
                    + " LIMIT :tope";

    /**
     * El censo del universo, en una sola fila (#25 AC-3).
     *
     * <p>Tres cifras que no necesitan {@code ST_Area}: cuantos predios activos hay, cuantos de
     * ellos tienen poligono y cuantos de esos tienen ademas una ficha vigente contrastable —de
     * cualquiera de las cuatro clases—. Con ellas, «0 candidatos» deja de poder leerse como «no hay
     * subvaluadores»: se ve de cuantos predios salio ese cero.
     *
     * <p>{@code EXISTS} y no un {@code JOIN} para la tercera: un {@code JOIN} contaria fichas y
     * aqui se cuentan PREDIOS, y un predio con dos vigentes contaria dos veces.
     */
    private static final String CENSO =
            "SELECT count(*) AS activos,"
                    + "       count(*) FILTER (WHERE p.geometria IS NOT NULL) AS con_geometria,"
                    + "       count(*) FILTER (WHERE p.geometria IS NOT NULL AND EXISTS ("
                    + "           SELECT 1 FROM ficha_catastral f"
                    + "            WHERE f.predio_id = p.id"
                    + "              AND f.vigencia_desde <= :hoy"
                    + "              AND (f.vigencia_hasta IS NULL OR f.vigencia_hasta >= :hoy)"
                    + "              AND f.area_terreno > 0)) AS contrastables"
                    + "  FROM predio p"
                    + " WHERE p.estado = 'ACTIVO'";

    private final Clock reloj;

    public AreasDelPadronJdbc(JdbcClient jdbc, Clock reloj) {
        super(jdbc);
        this.reloj = reloj;
    }

    @Override
    public CruceDelPadron contrastar(Score umbral, int tope) {
        if (sinCartografia()) {
            throw new SinCartografia();
        }
        LocalDate hoy = LocalDate.now(reloj);
        List<Fila> filas =
                jdbc().sql(CRUCE)
                        .param("hoy", hoy)
                        .param("umbral", umbral.valor())
                        .param("tope", tope)
                        .query(AreasDelPadronJdbc::mapear)
                        .list();
        // Cuando no vuelve ni una fila, `count(*) OVER ()` no vuelve tampoco: cero es cero.
        long superanElUmbral = filas.isEmpty() ? 0 : filas.get(0).superanElUmbral();
        return new CruceDelPadron(
                filas.stream().map(Fila::contraste).toList(),
                censo(hoy, superanElUmbral, filas.size()));
    }

    /** El universo del que sale el cruce, contado en la base y no sobre la lista devuelta. */
    private Cobertura censo(LocalDate hoy, long superanElUmbral, int devueltos) {
        return jdbc().sql(CENSO)
                .param("hoy", hoy)
                .query(
                        (ResultSet fila, int numero) -> {
                            long activos = fila.getLong("activos");
                            long conGeometria = fila.getLong("con_geometria");
                            long contrastables = fila.getLong("contrastables");
                            return new Cobertura(
                                    activos,
                                    activos - conGeometria,
                                    conGeometria - contrastables,
                                    contrastables,
                                    superanElUmbral,
                                    devueltos);
                        })
                .single();
    }

    /**
     * Si el predio esta en el padron de esta municipalidad (#17, AC-3).
     *
     * <p>{@code EXISTS} y sin {@code WHERE municipalidad_id}: quien acota es la politica RLS con lo
     * que {@code SET LOCAL} fijo (regla 2). Sobre el predio de otra municipalidad la respuesta es
     * {@code false} —bajo RLS no es «prohibido», <b>no existe</b>—, y el borde la traduce a {@code
     * 404}. Preguntar por la clave primaria y no por {@code count(*)}: la respuesta es si o no.
     */
    @Override
    public boolean estaEnElPadron(long predioId) {
        return Boolean.TRUE.equals(
                jdbc().sql("SELECT EXISTS (SELECT 1 FROM predio WHERE id = :predioId)")
                        .param("predioId", predioId)
                        .query(Boolean.class)
                        .single());
    }

    /**
     * Si esta municipalidad tiene algun predio con geometria.
     *
     * <p>Es una consulta aparte y no un {@code count} del cruce, y es la diferencia entera: el
     * cruce vacio significa «los tengo y ninguno difiere», y esto significa «no puedo mirar». Las
     * dos cosas se arreglan de manera distinta —una no se arregla y la otra pide cargar la
     * cartografia— y confundirlas manda a quien atiende a buscar subvaluadores donde no hay ni un
     * plano.
     *
     * <p>{@code EXISTS} y no {@code count(*)}: la respuesta es si o no, y sobre un padron de
     * noventa mil predios contarlos todos para saber si hay al menos uno es leer la tabla entera.
     */
    private boolean sinCartografia() {
        return Boolean.FALSE.equals(
                jdbc().sql("SELECT EXISTS (SELECT 1 FROM predio WHERE geometria IS NOT NULL)")
                        .query(Boolean.class)
                        .single());
    }

    /**
     * Una fila del cruce: el contraste y el recuento que la ventana trae repetido en todas.
     *
     * <p>{@code count(*) OVER ()} devuelve el mismo numero en cada fila —cuantas alcanzan el umbral
     * antes del {@code LIMIT}—, asi que se lee de la primera y no se suma.
     */
    private record Fila(ContrasteDeAreas contraste, long superanElUmbral) {}

    private static Fila mapear(ResultSet fila, int numero) throws SQLException {
        return new Fila(
                new ContrasteDeAreas(
                        fila.getLong("predio_id"),
                        fila.getLong("ficha_id"),
                        fila.getString("codigo_ref_catastral"),
                        new AreaM2(fila.getBigDecimal("area_terreno")),
                        new AreaM2(fila.getBigDecimal("area_poligono")),
                        new Score(fila.getBigDecimal("diferencia_relativa")),
                        fila.getString("geometria_wkt")),
                fila.getLong("superan_el_umbral"));
    }
}
