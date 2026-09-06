package kamayuk.catastro.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import kamayuk.catastro.dominio.Medida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El margen con que se ensancha el marco antes de cortar (#7, ADR-0034).
 *
 * <p>Lo que aqui se mide no es una conversion: es que el margen sea <b>suficiente</b>. Un margen
 * corto no falla —no lanza, no devuelve mal— sino que descarta la via antes de que el {@code
 * ST_DWithin} metrico la vea, y el predio de esquina sale con un frente en vez de dos.
 */
@DisplayName("#7 — El margen del marco: grados que cubren unos metros")
class MargenDelMarcoTest {

    @Test
    @DisplayName("ocho metros son 0,00008 grados, y la division es exacta")
    void ochoMetrosSonOchoCienmilesimasDeGrado() {
        Medida margen = MargenDelMarco.enGrados(Medida.enMetrosLineales("8.00"));

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
     * suficiente cuando cubre la tolerancia en metros, o sea cuando {@code margen × 111 320 ×
     * cos(lat) >= tolerancia}. Se comprueba a varias latitudes del Peru y en el borde declarado.
     */
    @ParameterizedTest(name = "a {0} grados de latitud, el margen cubre los metros pedidos")
    @ValueSource(doubles = {0.0, 5.2, 12.0, 18.4, 26.0})
    @DisplayName("el margen cubre la tolerancia en toda latitud donde vale la constante")
    void elMargenCubreLaToleranciaEnLasLatitudesDeclaradas(double latitud) {
        double tolerancia = 8.0;
        double margenEnGrados =
                MargenDelMarco.enGrados(Medida.enMetrosLineales("8.00")).magnitud().doubleValue();

        double metrosQueCubreEnLongitud =
                margenEnGrados * 111_320.0 * Math.cos(Math.toRadians(latitud));

        assertThat(metrosQueCubreEnLongitud)
                .as(
                        "a %s grados, el margen tiene que cubrir los %s m de tolerancia: si no, la"
                                + " via cercana se descarta ANTES del ST_DWithin y el predio de"
                                + " esquina sale con un frente en vez de dos",
                        latitud, tolerancia)
                .isGreaterThanOrEqualTo(tolerancia);
    }

    @Test
    @DisplayName("y por encima del limite declarado deja de cubrir, que es lo que el javadoc dice")
    void porEncimaDelLimiteDeclaradoDejaDeCubrir() {
        // El contraste, y no es un adorno: sin el, la constante podria ser diez veces mayor de lo
        // necesario y las cinco latitudes de arriba seguirian pasando. Lo que esto fija es que
        // 26,0 grados es el borde de verdad y no un numero escrito al azar en el javadoc — y que
        // el dia que este producto se instale en el norte de Mexico, hay que recalcularla.
        //
        // Y lo fijo la MEDIDA: el javadoc decia 26,2 —acos(0,897) redondeado a ojo— y la prueba de
        // arriba salio roja a esa latitud, porque alli un grado de longitud mide 99 883 m.
        double margenEnGrados =
                MargenDelMarco.enGrados(Medida.enMetrosLineales("8.00")).magnitud().doubleValue();

        double aTreintaGrados = margenEnGrados * 111_320.0 * Math.cos(Math.toRadians(30.0));

        assertThat(aTreintaGrados)
                .as("a 30 grados de latitud la constante se queda corta, y esta declarado")
                .isLessThan(8.0);
        assertThat(MargenDelMarco.LATITUD_MAXIMA)
                .as("y el limite esta escrito, no supuesto")
                .isEqualByComparingTo(new BigDecimal("26.0"));
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
}
