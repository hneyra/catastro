package kamayuk.catastro.fiscalizacion.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;
import kamayuk.catastro.fiscalizacion.dominio.ContrasteDeAreas;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import kamayuk.catastro.fiscalizacion.dominio.Tolerancia;
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
     * <p>La ficha es la <b>{@code UNICA} vigente hoy</b>: es la que lleva el area de terreno del
     * predio, y las otras tres —economica, bienes comunes, rural— la repiten. Contrastarlas todas
     * daria cuatro candidatos del mismo predio con la misma diferencia.
     *
     * <p>{@code ST_Area(geografia)} devuelve <b>metros cuadrados sobre el elipsoide</b>, que es la
     * unidad de {@code area_terreno}: `geography` mide en metros sin elegir zona UTM, y eso es
     * justamente por lo que ADR-0021 la eligio.
     */
    /**
     * El cruce.
     *
     * <p>La ficha es la <b>{@code UNICA} vigente hoy</b>: es la que lleva el area de terreno del
     * predio, y las otras tres —economica, bienes comunes, rural— la repiten. Contrastarlas todas
     * daria cuatro candidatos del mismo predio con la misma diferencia.
     *
     * <p>{@code ST_Area(geography)} devuelve <b>metros cuadrados sobre el elipsoide</b>, que es la
     * unidad de {@code area_terreno}: `geography` mide en metros sin elegir zona UTM, y eso es
     * justamente por lo que ADR-0021 la eligio.
     *
     * <p><b>La diferencia relativa se calcula UNA vez</b>, en la subconsulta, y de ahi salen las
     * tres cosas que hacen falta: el filtro por tolerancia, el orden y el valor que viaja al
     * candidato como su score. Escrita tres veces —o dos aqui y una en Java— seria la misma formula
     * en tres sitios que pueden divergir.
     *
     * <p>Se acota a 1 con {@code LEAST}: un predio inscrito con 1 m2 y un poligono de 300 daria 299
     * y ordenaria la cola por magnitud del error de digitacion en vez de por sospecha. El tipo
     * {@code Score} exige ese rango, y aqui es donde se respeta.
     *
     * <p>Ningun operador espacial en el {@code WHERE} (ADR-0034 regla 2): {@code ST_Area} calcula
     * sobre la fila que la politica ya dejo pasar, y lo que acota es {@code geometria IS NOT NULL},
     * que es una condicion de nulidad y no una comparacion espacial.
     */
    private static final String CRUCE =
            "SELECT predio_id, ficha_id, codigo_ref_catastral, area_terreno, area_poligono,"
                    + "       LEAST(diferencia, 1) AS diferencia_relativa, geometria_wkt"
                    + "  FROM ("
                    + "   SELECT p.id AS predio_id,"
                    + "          f.id AS ficha_id,"
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
                    + "      AND f.tipo = 'UNICA'"
                    + "      AND f.vigencia_desde <= :hoy"
                    + "      AND (f.vigencia_hasta IS NULL OR f.vigencia_hasta >= :hoy)"
                    + "      AND f.area_terreno > 0"
                    + "  ) AS contraste"
                    + " WHERE diferencia > :tolerancia"
                    + " ORDER BY diferencia DESC, predio_id"
                    + " LIMIT :tope";

    private final Clock reloj;

    public AreasDelPadronJdbc(JdbcClient jdbc, Clock reloj) {
        super(jdbc);
        this.reloj = reloj;
    }

    @Override
    public List<ContrasteDeAreas> contrastar(Tolerancia tolerancia, int tope) {
        if (sinCartografia()) {
            throw new SinCartografia();
        }
        return jdbc().sql(CRUCE)
                .param("hoy", LocalDate.now(reloj))
                .param("tolerancia", tolerancia.valor())
                .param("tope", tope)
                .query(AreasDelPadronJdbc::mapear)
                .list();
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

    private static ContrasteDeAreas mapear(ResultSet fila, int numero) throws SQLException {
        return new ContrasteDeAreas(
                fila.getLong("predio_id"),
                fila.getLong("ficha_id"),
                fila.getString("codigo_ref_catastral"),
                new AreaM2(fila.getBigDecimal("area_terreno")),
                new AreaM2(fila.getBigDecimal("area_poligono")),
                new Score(fila.getBigDecimal("diferencia_relativa")),
                fila.getString("geometria_wkt"));
    }
}
