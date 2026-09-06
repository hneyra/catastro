package kamayuk.catastro.urbano.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kamayuk.catastro.urbano.ParametroDeLaZona;
import kamayuk.catastro.urbano.ZonaVigente;
import kamayuk.catastro.urbano.ZonificacionDelPredio;
import kamayuk.catastro.web.ConfiguracionDeJson;
import kamayuk.catastro.web.ManejadorDeErrores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * Capa web de la zonificacion (#4, AC-4 y AC-5): {@code GET /catastro/api/v1/urbano/zonificacion}.
 *
 * <p>Sin base de datos: lo que se verifica es el <b>transporte</b> —forma del JSON, parametros y
 * traduccion de errores—. Lo que solo la base puede verificar —que el predio caiga dentro del
 * poligono, que el marco vaya delante y que la politica RLS acote— tiene su prueba en {@code
 * ZonificacionFronteraTest}, contra PostgreSQL con PostGIS de verdad.
 *
 * <p><b>El caso que da nombre a esta clase es el 422</b>, no el 200. Hoy no hay ni un poligono
 * cargado en ninguna instalacion, asi que el predio sin geometria es el camino que se recorre
 * siempre al principio, y un {@code 200} con {@code "zona": null} seria indistinguible de «este
 * predio esta en zona nula»: quien evalua una licencia leeria eso y ninguna zona nula admite ningun
 * giro.
 */
@DisplayName("#4 — Capa web: la zona de un predio, y el 422 del predio sin poligono")
class ZonificacionControllerTest {

    private static final String RUTA = "/catastro/api/v1/urbano/zonificacion";

    /** Fijo, para no depender del dia en que corra: la fecha por omision sale de aqui. */
    private final Clock reloj =
            Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("America/Lima"));

    private final ZonificacionDeMentira zonificacion = new ZonificacionDeMentira();

    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new ZonificacionController(zonificacion, reloj))
                    .setControllerAdvice(new ManejadorDeErrores())
                    .setMessageConverters(
                            new JacksonJsonHttpMessageConverter(
                                    JsonMapper.builder()
                                            .addModule(
                                                    new ConfiguracionDeJson()
                                                            .moduloDeObjetosDeValor())
                                            .build()))
                    .build();

    @Test
    @DisplayName("un predio SIN POLIGONO contesta 422 y nombra el motivo, no 200 con zona nula")
    void predioSinGeometriaEs422() throws Exception {
        zonificacion.responde(7L, new ZonificacionDelPredio.PredioSinGeometria(7L));

        MvcResult respuesta = mvc.perform(get(RUTA).param("predioId", "7")).andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
        assertThat(respuesta.getResponse().getContentAsString())
                .as("el 422 dice QUE falta y como se arregla: cargar el plano catastral")
                .contains("no tiene poligono cargado")
                .contains("plano catastral")
                .doesNotContain("\"zona\":null");
    }

    @Test
    @DisplayName("EL CONTRASTE: con zona, 200 con codigo, nombre, norma y parametros")
    void conZonaEs200() throws Exception {
        zonificacion.responde(
                7L,
                new ZonaVigente(
                        "RDM",
                        "Residencial de densidad media",
                        "PDU-2026",
                        "ORD-004-2026-MDC",
                        LocalDate.of(2026, 1, 1),
                        null,
                        List.of(
                                new ParametroDeLaZona("altura_maxima", "5", "pisos"),
                                new ParametroDeLaZona(
                                        "retiro_frontal", "segun seccion de via", null))));

        MvcResult respuesta =
                mvc.perform(get(RUTA).param("predioId", "7").param("aLaFecha", "2026-03-10"))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
        String cuerpo = respuesta.getResponse().getContentAsString();
        assertThat(cuerpo)
                .contains("\"codigo\":\"RDM\"")
                .contains("\"plan\":\"PDU-2026\"")
                .contains("\"ordenanza\":\"ORD-004-2026-MDC\"")
                .contains("\"altura_maxima\"")
                .contains("\"pisos\"");
        assertThat(cuerpo)
                .as(
                        "regla 9: toda cifra mostrada indica su fecha, y aqui lo mostrado DEPENDE de"
                                + " ella — sin este campo, la respuesta pegada en un expediente no"
                                + " diria a que dia se refiere")
                .contains("\"aLaFecha\":\"2026-03-10\"");
        assertThat(zonificacion.ultimaFecha()).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    @DisplayName("sin aLaFecha se toma hoy, del reloj inyectado y no de LocalDate.now()")
    void sinFechaSeTomaLaDelReloj() throws Exception {
        zonificacion.responde(
                7L,
                new ZonaVigente(
                        "CZ",
                        "Comercio zonal",
                        "PDU-2026",
                        "ORD-004-2026-MDC",
                        LocalDate.of(2026, 1, 1),
                        null,
                        List.of()));

        MvcResult respuesta = mvc.perform(get(RUTA).param("predioId", "7")).andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
        assertThat(zonificacion.ultimaFecha()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(respuesta.getResponse().getContentAsString())
                .as("y la respuesta LO DICE, aunque quien pregunto no lo escribiera")
                .contains("\"aLaFecha\":\"2026-06-15\"");
    }

    @Test
    @DisplayName("un predio que no esta en el padron es 404, no 422: el dato que falta es otro")
    void predioInexistenteEs404() throws Exception {
        zonificacion.responde(9L, new ZonificacionDelPredio.PredioInexistente(9L));

        MvcResult respuesta = mvc.perform(get(RUTA).param("predioId", "9")).andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(404);
        assertThat(respuesta.getResponse().getContentAsString())
                .contains("No hay ningun predio con identificador 9");
    }

    @Test
    @DisplayName("y un predio con poligono que ningun plan vigente cubre, tambien 404")
    void sinZonaVigenteEs404() throws Exception {
        zonificacion.responde(
                7L, new ZonificacionDelPredio.SinZonaVigente(7L, LocalDate.of(2026, 6, 15)));

        MvcResult respuesta = mvc.perform(get(RUTA).param("predioId", "7")).andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(404);
        assertThat(respuesta.getResponse().getContentAsString())
                .as(
                        "el recurso no esta; el 422 se reserva para el predio que SI esta y no tiene plano")
                .contains("Ningun plan de zonificacion vigente");
    }

    @Test
    @DisplayName("sin predioId la peticion no llega al dominio: 422 y ninguna consulta")
    void sinPredioIdNoLlegaAlDominio() throws Exception {
        MvcResult respuesta = mvc.perform(get(RUTA)).andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
        assertThat(zonificacion.consultas()).isZero();
    }

    // ------------------------------------------------------------------

    /**
     * Un {@link ZonificacionDelPredio} que devuelve lo que la prueba le diga.
     *
     * <p>Guarda la fecha con que se le pregunto: es lo unico que puede demostrar que el valor por
     * omision sale del reloj inyectado y no de {@code LocalDate.now()} —las dos formas devolverian
     * un 200 y solo una es reproducible—.
     */
    private static final class ZonificacionDeMentira implements ZonificacionDelPredio {

        private final Map<Long, Object> respuestas = new LinkedHashMap<>();
        private LocalDate ultimaFecha = LocalDate.MIN;
        private int consultas;

        void responde(long predioId, Object zonaOExcepcion) {
            respuestas.put(predioId, zonaOExcepcion);
        }

        LocalDate ultimaFecha() {
            return ultimaFecha;
        }

        int consultas() {
            return consultas;
        }

        @Override
        public ZonaVigente zonaDe(long predioId, LocalDate aLaFecha) {
            consultas++;
            ultimaFecha = aLaFecha;
            Object respuesta = respuestas.get(predioId);
            if (respuesta instanceof RuntimeException excepcion) {
                throw excepcion;
            }
            if (respuesta instanceof ZonaVigente zona) {
                return zona;
            }
            throw new PredioInexistente(predioId);
        }
    }
}
