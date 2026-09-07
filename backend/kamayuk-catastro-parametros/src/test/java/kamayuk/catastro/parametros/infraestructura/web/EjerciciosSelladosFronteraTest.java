package kamayuk.catastro.parametros.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import kamayuk.catastro.dominio.Ejercicio;
import kamayuk.catastro.parametros.IdentificadorDeConjunto;
import kamayuk.catastro.parametros.LectorDeParametros;
import kamayuk.catastro.parametros.ParametrosSellados;
import kamayuk.catastro.parametros.aplicacion.EjerciciosSellados;
import kamayuk.catastro.parametros.dominio.CacheDeSnapshots;
import kamayuk.catastro.parametros.dominio.SnapshotDeNormativa;
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
 * Capa web de #51 — {@code GET /catastro/api/v1/seguridad/parametros/ejercicios}.
 *
 * <h2>Que se mide aqui y que no</h2>
 *
 * <p>Aqui el <b>transporte</b>: el estado HTTP, la forma del JSON, el orden en que salen los
 * elementos y que la fecha viaje. El aislamiento y la consulta van contra PostgreSQL, en {@code
 * EjerciciosSelladosJdbcTest}: un doble devolveria lo que se le programe, y lo que hay que medir
 * alli lo sostiene la politica RLS.
 *
 * <p><b>Lo que esta clase existe sobre todo para fijar es el estado HTTP de la lista vacia</b>
 * (AC-2). Un inquilino recien implantado no tiene ningun conjunto descargado, y traducir eso a 404
 * seria decir «no hay donde preguntar» cuando lo cierto es «no hay ninguno todavia»: se arreglan en
 * sitios distintos —uno descargando un conjunto, el otro revisando la ruta— y quien consume no
 * puede distinguirlos si los dos llegan como 404.
 */
@DisplayName("Capa web — /seguridad/parametros/ejercicios")
class EjerciciosSelladosFronteraTest {

    /** Fijo, para que la fecha de la respuesta no dependa del dia en que se ejecute. */
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-07T15:00:00Z"), ZoneId.of("America/Lima"));

    private MockMvc conLaCache(List<CacheDeSnapshots.ConjuntoCacheado> loQueHay) {
        return MockMvcBuilders.standaloneSetup(
                        new EjercicioParametrizadoController(
                                new LectorQueNadieUsaAqui(),
                                new EjerciciosSellados(new CacheDeMentira(loQueHay)),
                                RELOJ))
                .setControllerAdvice(new ManejadorDeErrores())
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(
                                JsonMapper.builder()
                                        .addModule(
                                                new ConfiguracionDeJson().moduloDeObjetosDeValor())
                                        .build()))
                .build();
    }

    @Test
    @DisplayName("devuelve los ejercicios con su conjunto, su version y sus ambitos")
    void devuelveLosEjercicios() throws Exception {
        MvcResult respuesta =
                conLaCache(
                                List.of(
                                        conjunto(2026, 51_002L, 2, "OBLIGACION", "VALUACION"),
                                        conjunto(2025, 51_003L, 1, "OBLIGACION")))
                        .perform(get("/catastro/api/v1/seguridad/parametros/ejercicios"))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
        assertThat(respuesta.getResponse().getContentAsString())
                .as(
                        "los campos de la API van en espanol camelCase, y el orden de los elementos"
                                + " es el que da la consulta: del mas reciente al mas antiguo")
                .isEqualTo(
                        "{\"aLaFecha\":\"2026-09-07\",\"ejercicios\":["
                                + "{\"ejercicio\":2026,\"conjuntoId\":51002,\"version\":2,"
                                + "\"ambitos\":[\"OBLIGACION\",\"VALUACION\"]},"
                                + "{\"ejercicio\":2025,\"conjuntoId\":51003,\"version\":1,"
                                + "\"ambitos\":[\"OBLIGACION\"]}]}");
    }

    @Test
    @DisplayName("y sin ningun conjunto descargado contesta 200 con la lista vacia, no 404")
    void sinConjuntosContesta200() throws Exception {
        MvcResult respuesta =
                conLaCache(List.of())
                        .perform(get("/catastro/api/v1/seguridad/parametros/ejercicios"))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus())
                .as(
                        "un 404 diria que la ruta no existe, y existe. Lo que no hay es ningun"
                                + " conjunto descargado, que es una respuesta y se arregla"
                                + " descargando uno (C-5 §2.1)")
                .isEqualTo(200);
        assertThat(respuesta.getResponse().getContentAsString())
                .as(
                        "y la fecha sale igual con la lista vacia: sin ella, «no hay ninguno» no se"
                                + " distingue de «no se pudo leer» (AC-3)")
                .isEqualTo("{\"aLaFecha\":\"2026-09-07\",\"ejercicios\":[]}");
    }

    @Test
    @DisplayName("la ruta nueva no le tapa la suya a la lectura por ejercicio")
    void noLeTapaLaRutaALaLecturaPorEjercicio() throws Exception {
        // `/ejercicios` y `/ejercicios/{ejercicio}` conviven en el mismo controlador, y una ruta
        // literal que tapara a la parametrizada —o al reves— no rompe nada al compilar: contesta
        // 200 con la forma equivocada. Es el defecto que #33 midio en el proxy del frontend, por
        // el otro lado de la frontera.
        MvcResult respuesta =
                conLaCache(List.of(conjunto(2026, 51_002L, 2, "OBLIGACION")))
                        .perform(get("/catastro/api/v1/seguridad/parametros/ejercicios/2026"))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
        assertThat(respuesta.getResponse().getContentAsString())
                .as("la lectura por ejercicio sigue contestando LO SUYO, con su `sellado`")
                .contains("\"sellado\":false")
                .doesNotContain("aLaFecha");
    }

    private static CacheDeSnapshots.ConjuntoCacheado conjunto(
            int ejercicio, long conjuntoId, int version, String... ambitos) {
        return new CacheDeSnapshots.ConjuntoCacheado(
                new Ejercicio(ejercicio), conjuntoId, version, List.of(ambitos));
    }

    /**
     * La copia local, fabricada.
     *
     * <p>Las demas lecturas <b>lanzan</b> en vez de devolver un valor de relleno: si alguna se
     * llegara a llamar desde este camino, lo que hay que ver es el error y no una respuesta
     * plausible que oculte que el controlador esta pidiendo algo que no le toca.
     */
    private record CacheDeMentira(List<CacheDeSnapshots.ConjuntoCacheado> loQueHay)
            implements CacheDeSnapshots {

        @Override
        public List<ConjuntoCacheado> conjuntosCacheados() {
            return loQueHay;
        }

        @Override
        public boolean tiene(long conjuntoId, String ambito) {
            throw new UnsupportedOperationException("esta ruta no descarga nada");
        }

        @Override
        public Optional<Long> conjuntoCacheadoDe(Ejercicio ejercicio) {
            throw new UnsupportedOperationException("esta ruta no resuelve «lo vigente»");
        }

        @Override
        public Optional<IdentidadDelConjunto> identidadDe(long conjuntoId) {
            throw new UnsupportedOperationException("esta ruta no resuelve un conjunto");
        }

        @Override
        public List<SnapshotDeNormativa.Parametro> parametrosDe(long conjuntoId) {
            throw new UnsupportedOperationException("esta lectura NO trae ninguna cifra");
        }

        @Override
        public void guardar(SnapshotDeNormativa snapshot) {
            throw new UnsupportedOperationException("esta ruta no escribe");
        }
    }

    /**
     * El lector de la OTRA lectura de este controlador.
     *
     * <p>Contesta que ningun ejercicio esta sellado, que es lo que hace falta para comprobar que la
     * ruta parametrizada sigue siendo suya. Lo que esa lectura hace de verdad ya lo prueba #605.
     */
    private static final class LectorQueNadieUsaAqui implements LectorDeParametros {

        @Override
        public ParametrosSellados vigenteEn(Ejercicio ejercicio) {
            throw new UnsupportedOperationException("no se pide ninguna cifra en estas pruebas");
        }

        @Override
        public ParametrosSellados porConjunto(IdentificadorDeConjunto identificador) {
            throw new UnsupportedOperationException("no se pide ninguna cifra en estas pruebas");
        }

        @Override
        public IdentificadorDeConjunto conjuntoVigenteEn(Ejercicio ejercicio) {
            throw new EjercicioSinSellar(ejercicio);
        }
    }
}
