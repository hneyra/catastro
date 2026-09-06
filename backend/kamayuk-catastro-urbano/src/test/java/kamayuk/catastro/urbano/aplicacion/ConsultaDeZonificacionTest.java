package kamayuk.catastro.urbano.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.urbano.ZonaVigente;
import kamayuk.catastro.urbano.ZonificacionDelPredio;
import kamayuk.catastro.urbano.dominio.EstadoDelPredio;
import kamayuk.catastro.urbano.dominio.ParametroUrbanistico;
import kamayuk.catastro.urbano.dominio.Zona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #4 — a que zona cae un predio, y las tres respuestas que no son una zona.
 *
 * <p>Lo que se mide aqui es la <b>decision</b>: que el caso de uso distinga «el predio no esta»,
 * «el predio no tiene poligono» y «ningun plan vigente lo cubre», y que no las funda en un valor
 * nulo. Que el predio caiga de verdad dentro del poligono lo mide {@code ZonificacionFronteraTest}
 * contra PostgreSQL con PostGIS, que es donde esa afirmacion significa algo.
 */
@DisplayName("#4 — La zona de un predio, y las tres respuestas que no son una zona")
class ConsultaDeZonificacionTest {

    private static final LocalDate HOY = LocalDate.of(2026, 6, 15);
    private static final String POLIGONO =
            "MULTIPOLYGON(((-80.69 -5.27,-80.67 -5.27,-80.67 -5.25,-80.69 -5.25,-80.69 -5.27)))";

    private UrbanoEnMemoria urbano;
    private ConsultaDeZonificacion consulta;

    @BeforeEach
    void preparar() {
        urbano = new UrbanoEnMemoria();
        consulta = new ConsultaDeZonificacion(urbano);
    }

    @Test
    @DisplayName(
            "un predio que no esta en el padron no es una zona vacia: es un predio que no esta")
    void unPredioQueNoEsta() {
        assertThatThrownBy(() -> consulta.zonaDe(404L, HOY))
                .isInstanceOf(ZonificacionDelPredio.PredioInexistente.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("un predio SIN POLIGONO tampoco: es el caso corriente hoy, y se dice")
    void unPredioSinGeometria() {
        urbano.sembrarPredio(7L, EstadoDelPredio.SIN_GEOMETRIA);

        assertThatThrownBy(() -> consulta.zonaDe(7L, HOY))
                .as(
                        "hoy no hay ni un poligono cargado en ninguna instalacion: este es el camino"
                                + " que se recorre siempre al principio, y «zona: null» seria"
                                + " indistinguible de «este predio esta en zona nula»")
                .isInstanceOf(ZonificacionDelPredio.PredioSinGeometria.class)
                .hasMessageContaining("plano catastral");
    }

    @Test
    @DisplayName("y un predio con poligono en suelo que ningun plan vigente cubre, tampoco")
    void unPredioSinZona() {
        urbano.sembrarPredio(7L, EstadoDelPredio.CON_GEOMETRIA);

        assertThatThrownBy(() -> consulta.zonaDe(7L, HOY))
                .as(
                        "las tres se arreglan distinto: dar de alta el predio, cargar el plano, o"
                                + " aprobar la zonificacion de esa area")
                .isInstanceOf(ZonificacionDelPredio.SinZonaVigente.class)
                .hasMessageContaining("2026-06-15");
    }

    @Test
    @DisplayName("EL CONTRASTE: con zona vigente devuelve su codigo, su norma y sus parametros")
    void conZonaVigente() {
        long zonaId = sembrarZona(LocalDate.of(2026, 1, 1), null);
        urbano.sembrarPredio(7L, EstadoDelPredio.CON_GEOMETRIA);
        urbano.caeEn(7L, zonaId);

        ZonaVigente zona = consulta.zonaDe(7L, HOY);

        assertThat(zona.codigo()).isEqualTo("RDM");
        assertThat(zona.nombre()).isEqualTo("Residencial de densidad media");
        assertThat(zona.plan()).isEqualTo("PDU-2026");
        assertThat(zona.ordenanza())
                .as(
                        "negar una licencia es un acto que se motiva: quien la niega tiene que poder"
                                + " citar la norma, y quien la recibe leerla")
                .isEqualTo("ORD-004-2026");
        assertThat(zona.parametros())
                .extracting("clave", "valor", "unidad")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("altura_maxima", "5", "pisos"),
                        org.assertj.core.groups.Tuple.tuple(
                                "retiro_frontal", "segun seccion de via", null));
    }

    @Test
    @DisplayName("no existe «la zona»: la de un plan ya relevado no contesta a la fecha de hoy")
    void laZonaEsLaVigenteAEsaFecha() {
        long zonaId = sembrarZona(LocalDate.of(2016, 3, 1), LocalDate.of(2025, 12, 31));
        urbano.sembrarPredio(7L, EstadoDelPredio.CON_GEOMETRIA);
        urbano.caeEn(7L, zonaId);

        assertThatThrownBy(() -> consulta.zonaDe(7L, HOY))
                .as("regla 9 sobre el territorio: un plan cerrado no decide una licencia de hoy")
                .isInstanceOf(ZonificacionDelPredio.SinZonaVigente.class);

        assertThat(consulta.zonaDe(7L, LocalDate.of(2020, 5, 5)).codigo())
                .as("y a una fecha en que SI regia, contesta")
                .isEqualTo("RDM");
    }

    @Test
    @DisplayName(
            "el ultimo dia de vigencia es un dia en que la zona rige (vigencia_hasta inclusiva)")
    void elUltimoDiaCuenta() {
        long zonaId = sembrarZona(LocalDate.of(2016, 3, 1), LocalDate.of(2025, 12, 31));
        urbano.sembrarPredio(7L, EstadoDelPredio.CON_GEOMETRIA);
        urbano.caeEn(7L, zonaId);

        assertThat(consulta.zonaDe(7L, LocalDate.of(2025, 12, 31)).codigo())
                .as(
                        "escrito con isBefore, el dia del relevo del plan contestaria «ninguna zona»"
                                + " — y ese dia es justo cuando alguien lo mira")
                .isEqualTo("RDM");
    }

    private long sembrarZona(LocalDate desde, java.time.LocalDate hasta) {
        Observacion observacion = Observacion.de("Carga del plan de la prueba de #4");
        long id =
                urbano.guardar(
                        new Zona(
                                null,
                                "PDU-2026",
                                "ORD-004-2026",
                                "RDM",
                                "Residencial de densidad media",
                                POLIGONO,
                                desde,
                                hasta),
                        observacion);
        urbano.guardarParametros(
                id,
                List.of(
                        new ParametroUrbanistico("altura_maxima", "5", "pisos"),
                        new ParametroUrbanistico("retiro_frontal", "segun seccion de via", null)),
                observacion);
        return id;
    }
}
