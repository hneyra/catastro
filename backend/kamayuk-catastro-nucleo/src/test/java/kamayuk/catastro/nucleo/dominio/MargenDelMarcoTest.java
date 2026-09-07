package kamayuk.catastro.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import kamayuk.catastro.compartido.MarcoGeografico;
import kamayuk.catastro.dominio.Medida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El margen con que se ensancha el marco antes de cortar (#7, ADR-0034; #29).
 *
 * <p>Lo que aqui se mide no es una conversion: es que el margen sea <b>suficiente</b>. Un margen
 * corto no falla —no lanza, no devuelve mal— sino que descarta la via antes de que el {@code
 * ST_DWithin} metrico la vea, y el predio de esquina sale con un frente en vez de dos.
 *
 * <h2>Lo que #29 cambio, y por que</h2>
 *
 * <p>Habia una prueba que afirmaba {@code LATITUD_MAXIMA == 26,0} comparandola con un {@code 26.0}
 * escrito dos lineas mas arriba: comprobaba que alguien habia escrito 26 dos veces. Se retira, y en
 * su sitio la constante queda <b>pinzada contra la geodesia</b>: al limite que declara el margen
 * cubre la tolerancia, y una decima mas alla deja de cubrirla. Escribir cualquier otro numero
 * —26,2, que es el que estuvo escrito, o 40, o 10— pone rojo uno de los dos lados.
 */
@DisplayName("#7 — El margen del marco: grados que cubren unos metros")
class MargenDelMarcoTest {

    /** Ocho metros: media calzada de una via local, mas la holgura de un levantamiento. */
    private static final Medida OCHO_METROS = Medida.enMetrosLineales("8.00");

    /**
     * Metros que mide un grado de longitud en el ecuador, segun WGS84.
     *
     * <p>Es la constante geodesica contra la que se mide el supuesto, y no puede salir de la clase
     * que se esta comprobando: comparar {@code MargenDelMarco} consigo mismo no diria nada.
     */
    private static final double METROS_POR_GRADO_EN_EL_ECUADOR = 111_320.0;

    @Test
    @DisplayName("ocho metros son 0,00008 grados, y la division es exacta")
    void ochoMetrosSonOchoCienmilesimasDeGrado() {
        Medida margen = MargenDelMarco.enGrados(OCHO_METROS);

        assertThat(margen.magnitud())
                .as("8,00 / 100 000, sin redondear: dividir por una potencia de diez termina")
                .isEqualByComparingTo(new BigDecimal("0.00008"));
        assertThat(margen.unidad())
                .as(
                        "con su unidad dentro: el marco esta en grados y la tolerancia en metros, y"
                                + " confundirlos no falla — busca a 0,00008 metros de la via")
                .isEqualTo(MargenDelMarco.GRADOS);
    }

    /**
     * La propiedad que este calculo existe para tener, comprobada donde vive el producto.
     *
     * <p>Un grado de longitud mide {@code 111 320 × cos(latitud)} metros. El margen en grados es
     * suficiente cuando cubre la tolerancia en metros. Se comprueba a varias latitudes del Peru; el
     * borde lo mide {@link #elLimiteDeclaradoEsElBordeDeVerdad()}, que lo lee de la constante en
     * vez de repetirlo aqui.
     */
    @ParameterizedTest(name = "a {0} grados de latitud, el margen cubre los metros pedidos")
    @ValueSource(doubles = {0.0, 5.2, 12.0, 18.4})
    @DisplayName("el margen cubre la tolerancia en toda latitud del Peru")
    void elMargenCubreLaToleranciaEnLasLatitudesDelPeru(double latitud) {
        assertThat(metrosQueCubreEnLongitud(latitud))
                .as(
                        "a %s grados, el margen tiene que cubrir los 8 m de tolerancia: si no, la"
                                + " via cercana se descarta ANTES del ST_DWithin y el predio de"
                                + " esquina sale con un frente en vez de dos",
                        latitud)
                .isGreaterThanOrEqualTo(8.0);
    }

    @Test
    @DisplayName("el limite declarado es el borde de verdad: una decima mas alla deja de cubrir")
    void elLimiteDeclaradoEsElBordeDeVerdad() {
        // ESTO es lo que sujeta la constante (#29). No compara `LATITUD_MAXIMA` con un numero
        // escrito aqui —eso comprobaria que alguien tecleo 26 dos veces—: la LEE y la pinza contra
        // la geodesia por los dos lados. Lo que la prueba afirma es la frase del javadoc, no su
        // cifra: «hasta aqui un grado de longitud mide al menos 100 000 m, y a partir de aqui no».
        //
        // Y por eso el paso es una DECIMA: el limite se declara con un decimal, asi que el borde
        // tiene que estar dentro de esa decima. Con 26,2 —el valor que estuvo escrito y que la
        // medida de #7 corrigio— el primer lado sale rojo; con 26,5 o con 40, tambien; con 25,9 o
        // con 10, sale rojo el segundo.
        double limite = MargenDelMarco.LATITUD_MAXIMA.doubleValue();

        assertThat(metrosQueCubreEnLongitud(limite))
                .as(
                        "en el limite que %s declara (%s grados) el margen TIENE que cubrir la"
                                + " tolerancia, o el javadoc promete una banda que no vale",
                        MargenDelMarco.class.getSimpleName(), limite)
                .isGreaterThanOrEqualTo(8.0);
        assertThat(metrosQueCubreEnLongitud(limite + 0.1))
                .as(
                        "y una decima mas alla tiene que dejar de cubrirla, o el limite esta puesto"
                                + " mas aca de donde de verdad esta y sobra banda declarada de menos")
                .isLessThan(8.0);
    }

    @Test
    @DisplayName("sobre el marco de un padron del Peru, la constante vale y no hay nada que decir")
    void sobreUnPadronDelPeruNoHayAviso() {
        MarcoGeografico sullana = marco("-80.71", "-4.92", "-80.66", "-4.87");

        assertThat(MargenDelMarco.cubre(sullana)).isTrue();
        assertThat(MargenDelMarco.avisoSiNoCubre(sullana))
                .as(
                        "es el contraste: si el aviso saliera siempre, «lo dice» no significaria"
                                + " nada y quien lo lee dejaria de leerlo (#437)")
                .isNull();
    }

    @Test
    @DisplayName("y fuera de la banda LO DICE, en vez de proponer de menos en silencio")
    void fueraDeLaBandaLoDice() {
        // El norte de Mexico, que es el ejemplo que el javadoc nombra. Antes de #29 esto no lo
        // preguntaba nadie: el corte descartaba vias que si bordean el lote y el unico sintoma era
        // un predio de esquina con un frente en vez de dos.
        MarcoGeografico chihuahua = marco("-106.20", "28.55", "-106.00", "28.75");

        assertThat(MargenDelMarco.cubre(chihuahua)).isFalse();
        assertThat(MargenDelMarco.avisoSiNoCubre(chihuahua))
                .as("y el aviso nombra la latitud que se salio, el limite y lo que va a pasar")
                .isNotNull()
                .contains("28.75")
                .contains(MargenDelMarco.LATITUD_MAXIMA.toPlainString())
                .contains("descarta vias");
    }

    @Test
    @DisplayName("decide la latitud mas lejana del ecuador, no la del sur")
    void decideLaLatitudMasLejanaDelEcuador() {
        // Un marco que empieza dentro de la banda y termina fuera: basta que UN borde se salga
        // para que el margen se quede corto en esa franja. Mirar solo el sur daria por bueno un
        // padron que llega a los 40 grados.
        MarcoGeografico deSurAFuera = marco("-106.20", "25.00", "-106.00", "40.00");

        assertThat(MargenDelMarco.cubre(deSurAFuera)).isFalse();
        assertThat(MargenDelMarco.avisoSiNoCubre(deSurAFuera)).contains("40.00");
    }

    @Test
    @DisplayName("una tolerancia en metros cuadrados se rechaza: no es una distancia")
    void unaToleranciaEnOtraUnidadSeRechaza() {
        assertThatThrownBy(() -> MargenDelMarco.enGrados(Medida.enMetrosCuadrados("8.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("se mide en ML")
                .hasMessageContaining("M2");
    }

    @Test
    @DisplayName("una tolerancia de cero se rechaza: no acotaria nada")
    void unaToleranciaDeCeroSeRechaza() {
        assertThatThrownBy(() -> MargenDelMarco.enGrados(Medida.enMetrosLineales("0.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no acota nada");
    }

    /** Cuantos metros de longitud cubre a esa latitud el margen que la clase calcula. */
    private static double metrosQueCubreEnLongitud(double latitud) {
        double margenEnGrados = MargenDelMarco.enGrados(OCHO_METROS).magnitud().doubleValue();
        return margenEnGrados * METROS_POR_GRADO_EN_EL_ECUADOR * Math.cos(Math.toRadians(latitud));
    }

    private static MarcoGeografico marco(String oeste, String sur, String este, String norte) {
        return new MarcoGeografico(
                new BigDecimal(oeste),
                new BigDecimal(sur),
                new BigDecimal(este),
                new BigDecimal(norte));
    }
}
