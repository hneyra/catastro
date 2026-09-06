package kamayuk.catastro.nucleo.dominio;

import java.math.BigDecimal;
import kamayuk.catastro.dominio.Medida;

/**
 * Cuantos GRADOS hay que ensanchar el marco para que quepan unos METROS (#7, ADR-0034).
 *
 * <h2>Por que hace falta, dicho con el defecto que evita</h2>
 *
 * <p>ADR-0034 obliga a filtrar por las cuatro columnas de marco antes de tocar ningun operador
 * espacial. El marco esta en <b>grados</b> —es el rectangulo envolvente en WGS84— y la tolerancia
 * con la que se busca una via cerca de un lote esta en <b>metros</b>, porque {@code ST_DWithin}
 * sobre {@code geography} trabaja en metros.
 *
 * <p>Comparar los dos marcos <b>sin ensanchar</b> es un defecto silencioso: la via que pasa a dos
 * metros del lote pero cuyo rectangulo no llega a tocarlo queda descartada por el marco y el {@code
 * ST_DWithin} de detras <b>nunca la ve</b>. El resultado es un predio de esquina con un frente en
 * vez de dos, sin ningun error, sin ninguna traza, y con un arbitrio determinado sobre la mitad de
 * su frontis.
 *
 * <h2>El factor es 100 000 y no 111 320, y eso es lo unico que hay que entender</h2>
 *
 * <p>Un grado de LATITUD mide siempre unos 110 574 m. Un grado de LONGITUD mide {@code 111 320 ×
 * cos(latitud)}: se acorta al alejarse del ecuador, asi que unos metros dados ocupan <b>mas</b>
 * grados cuanto mas al norte o al sur. Dividir por 111 320 —el numero que uno teclea— daria un
 * margen <b>corto</b> en longitud fuera del ecuador, o sea la fuga de arriba, reducida pero
 * presente.
 *
 * <p>Se divide por <b>100 000</b>, que es cota inferior de los dos: mientras {@code |latitud| ≤
 * 26,0°} un grado de longitud mide al menos 100 000 m ({@code cos 26,0° ≈ 0,8988}), y un grado de
 * latitud siempre mide mas. El margen que sale es por tanto <b>igual o mayor</b> que el que hace
 * falta, en las dos direcciones. Peru entero cae entre 0° y 18,4° de latitud sur.
 *
 * <p><b>Donde deja de valer, dicho antes de que alguien lo descubra:</b> por encima de 26,0° de
 * latitud —el norte de Mexico, el sur de Chile— el margen se queda corto y vuelve la fuga. Si algun
 * dia este producto se instala ahi, esta constante hay que recalcularla; lo que <b>no</b> puede
 * pasar es que se descubra por una cifra de arbitrio que no cuadra, y por eso esta escrito aqui y
 * lo comprueba una prueba.
 *
 * <p>Ensanchar de mas no produce ningun frente equivocado: lo que decide sigue siendo el {@code
 * ST_DWithin} metrico de detras. Lo unico que cuesta un margen generoso es leer alguna via mas por
 * lote, que es exactamente el reparto que ADR-0034 quiere —acotar barato y refinar exacto—.
 *
 * <p>El resultado sale como {@link Medida} en {@link #GRADOS} y no como un {@code BigDecimal}
 * desnudo, por lo mismo que la longitud del frente sale en {@code ML}: un decimal suelto no dice de
 * que es la cifra, y grados y metros se parecen lo bastante como para intercambiarse sin que nada
 * falle. Lo comprueba, ademas, {@code NINGUNA_FIRMA_DE_DOMINIO_EXPONE_BIGDECIMAL}.
 *
 * <p>Es una funcion pura: sin base de datos, sin reloj y sin configuracion global (regla 6).
 */
public final class MargenDelMarco {

    /**
     * Metros que mide, como minimo, un grado —de latitud o de longitud— mientras {@code |latitud| ≤
     * 26,0°}.
     *
     * <p>No es un valor tributario y no cae bajo la regla 5: es una constante geodesica, la misma
     * que {@code ST_DWithin} usa por dentro, y no sale de ninguna ordenanza.
     */
    public static final BigDecimal METROS_POR_GRADO = new BigDecimal("100000");

    /**
     * Hasta donde vale {@link #METROS_POR_GRADO}, en grados de latitud.
     *
     * <p><b>El numero lo fijo la medida y no el razonamiento.</b> Se escribio primero 26,2 —de
     * {@code acos(0,897)} redondeado a ojo— y la prueba lo puso rojo: a 26,2° un grado de longitud
     * mide 99 883 m, o sea MENOS de 100 000, y el margen se queda corto justo donde el javadoc
     * decia que aun valia. El limite real es 26,0° (100 054 m).
     */
    public static final BigDecimal LATITUD_MAXIMA = new BigDecimal("26.0");

    /** La unidad del margen. El marco esta en grados WGS84, no en metros. */
    public static final String GRADOS = "GRADOS";

    private MargenDelMarco() {}

    /**
     * El margen en grados que corresponde a una tolerancia en metros.
     *
     * @param tolerancia la distancia con la que se busca; tiene que estar en metros lineales
     * @throws IllegalArgumentException si la tolerancia no viene en {@code ML} o no es positiva
     */
    public static Medida enGrados(Medida tolerancia) {
        if (!FrenteDelPredio.UNIDAD.equals(tolerancia.unidad())) {
            throw new IllegalArgumentException(
                    "La tolerancia del corte se mide en "
                            + FrenteDelPredio.UNIDAD
                            + " y viene en "
                            + tolerancia.unidad());
        }
        if (tolerancia.esCero()) {
            throw new IllegalArgumentException(
                    "Una tolerancia de cero metros no acota nada: el corte no encontraria ninguna"
                            + " via, ni siquiera la que pasa por encima del lote");
        }
        // La division es EXACTA y por eso no lleva escala ni modo de redondeo: 100 000 es una
        // potencia de diez, asi que el cociente siempre termina y `divide` sin contexto no puede
        // lanzar. Es la respuesta honesta a D-03a/D-03b y no un rodeo: aqui no hay ninguna
        // politica de redondeo que decidir, y escribir una la habria decidido por descuido.
        return new Medida(tolerancia.magnitud().divide(METROS_POR_GRADO), GRADOS);
    }
}
