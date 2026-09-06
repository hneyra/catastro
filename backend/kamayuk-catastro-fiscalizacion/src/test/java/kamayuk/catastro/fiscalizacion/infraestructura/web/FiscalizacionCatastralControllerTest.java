package kamayuk.catastro.fiscalizacion.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.fiscalizacion.aplicacion.AbrirCampania;
import kamayuk.catastro.fiscalizacion.aplicacion.ConsultaDeCandidatos;
import kamayuk.catastro.fiscalizacion.aplicacion.ConsultaDeHallazgos;
import kamayuk.catastro.fiscalizacion.aplicacion.DetectarSubvaluadores;
import kamayuk.catastro.fiscalizacion.aplicacion.LevantarActa;
import kamayuk.catastro.fiscalizacion.aplicacion.RegistrarEvidencia;
import kamayuk.catastro.fiscalizacion.aplicacion.VerificarEnCampo;
import kamayuk.catastro.fiscalizacion.aplicacion.VerificarEnGabinete;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.nucleo.LectorDeFichas;
import kamayuk.catastro.web.ConfiguracionDeJson;
import kamayuk.catastro.web.ManejadorDeErrores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * El borde de la fiscalizacion catastral: forma del JSON, lista blanca y traduccion de errores.
 *
 * <p>Sin base de datos: lo que la base verifica —RLS, las claves foraneas, la unicidad de la
 * huella— tiene sus propias pruebas contra el motor de verdad, y separarlas hace que cada fallo
 * diga que se rompio.
 */
@DisplayName("#6 — El borde de la fiscalizacion catastral")
class FiscalizacionCatastralControllerTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);

    private MockMvc mvc;

    /** El padron sin ningun poligono: la situacion REAL de hoy en todas las instalaciones. */
    private static final AreasDelPadron SIN_CARTOGRAFIA =
            (tolerancia, tope) -> {
                throw new AreasDelPadron.SinCartografia();
            };

    private static final LectorDeFichas NINGUNA_FICHA =
            new LectorDeFichas() {
                @Override
                public java.util.Optional<Long> fichaVigenteEn(
                        long predioId, java.time.LocalDate fecha) {
                    return java.util.Optional.empty();
                }

                @Override
                public java.util.Optional<AreaM2> areaDeLaVersion(long fichaId) {
                    return java.util.Optional.empty();
                }
            };

    @BeforeEach
    void montarElBorde() {
        FiscalizacionRepository repositorio = new RepositorioQueNadieLlama();
        Auditoria auditoria = registro -> {};

        mvc =
                MockMvcBuilders.standaloneSetup(
                                new FiscalizacionCatastralController(
                                        new AbrirCampania(repositorio, auditoria, RELOJ),
                                        new DetectarSubvaluadores(
                                                repositorio, SIN_CARTOGRAFIA, auditoria, RELOJ),
                                        new VerificarEnGabinete(repositorio, auditoria, RELOJ),
                                        new VerificarEnCampo(
                                                repositorio, NINGUNA_FICHA, auditoria, RELOJ),
                                        new RegistrarEvidencia(repositorio, auditoria, RELOJ),
                                        new LevantarActa(repositorio, auditoria, RELOJ),
                                        new ConsultaDeCandidatos(repositorio),
                                        new ConsultaDeHallazgos(repositorio)))
                        .setControllerAdvice(new ManejadorDeErrores())
                        .setMessageConverters(
                                new JacksonJsonHttpMessageConverter(
                                        JsonMapper.builder()
                                                .addModule(
                                                        new ConfiguracionDeJson()
                                                                .moduloDeObjetosDeValor())
                                                .build()))
                        .build();
    }

    @Test
    @DisplayName("sin observacion no se abre una campania: 422, y lo dice nombrando el campo")
    void sinObservacionNoSeEscribe() throws Exception {
        MvcResult rechazada =
                mvc.perform(
                                post("/catastro/api/v1/fiscalizacion/campanias")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"codigo":"CAM-1","nombre":"Barrido 2026",
                                                 "umbral":"0.20"}
                                                """))
                        .andReturn();

        assertThat(rechazada.getResponse().getStatus()).isEqualTo(422);
        assertThat(rechazada.getResponse().getContentAsString())
                .as("regla 10: sin observacion no se guarda, y el mensaje dice cual falta")
                .contains("observacion");
    }

    @Test
    @DisplayName("SIN POLIGONOS la deteccion contesta 409 y NO un 200 con lista vacia")
    void sinCartografiaContesta409() throws Exception {
        MvcResult sinPlanos =
                mvc.perform(
                                post("/catastro/api/v1/fiscalizacion/campanias/1/deteccion")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"tolerancia":"0.10",
                                                 "observacion":"corrida de deteccion de prueba"}
                                                """))
                        .andReturn();

        assertThat(sinPlanos.getResponse().getStatus())
                .as(
                        "un 200 con [] se leeria como «no hay subvaluadores», que es indistinguible"
                                + " de «no pude mirar» y que nadie va a revisar")
                .isEqualTo(409);
        assertThat(sinPlanos.getResponse().getContentAsString())
                .contains("no tiene ni un predio con geometria");
    }

    @Test
    @DisplayName("la tolerancia va como FRACCION de 1: un 10 se rechaza, y dice por que")
    void laToleranciaEsUnaFraccion() throws Exception {
        MvcResult rechazada =
                mvc.perform(
                                post("/catastro/api/v1/fiscalizacion/campanias/1/deteccion")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"tolerancia":"10",
                                                 "observacion":"corrida de deteccion de prueba"}
                                                """))
                        .andReturn();

        assertThat(rechazada.getResponse().getStatus()).isEqualTo(422);
        assertThat(rechazada.getResponse().getContentAsString())
                .as("0,10 y 10 son la misma tolerancia escritas de dos maneras, y no lo son")
                .contains("como fraccion y no como porcentaje");
    }

    @Test
    @DisplayName("un descarte sin motivo es 422: la fila se queda, pero con su por que")
    void elDescarteExigeSuMotivo() throws Exception {
        MvcResult rechazado =
                mvc.perform(
                                post("/catastro/api/v1/fiscalizacion/candidatos/1/gabinete")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"admite":false,
                                                 "observacion":"revision de gabinete de prueba"}
                                                """))
                        .andReturn();

        assertThat(rechazado.getResponse().getStatus()).isEqualTo(422);
        assertThat(rechazado.getResponse().getContentAsString()).contains("motivo");
    }

    @Test
    @DisplayName("una huella que no es un sha256 es 422, y no llega a la base")
    void laHuellaSeValidaEnElBorde() throws Exception {
        MvcResult rechazada =
                mvc.perform(
                                post("/catastro/api/v1/fiscalizacion/hallazgos/1/evidencias")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"tipo":"FOTO","sha256":"no-es-una-huella",
                                                 "ruta":"s3://x/y.jpg",
                                                 "capturadoEn":"2026-09-05T09:00:00Z",
                                                 "observacion":"evidencia de la visita de campo"}
                                                """))
                        .andReturn();

        assertThat(rechazada.getResponse().getStatus()).isEqualTo(422);
        assertThat(rechazada.getResponse().getContentAsString()).contains("64 hexadigitos");
    }

    @Test
    @DisplayName("el instante de captura es OBLIGATORIO: no se sustituye por el del servidor")
    void elRelojDelAparatoNoSeInventa() throws Exception {
        MvcResult rechazada =
                mvc.perform(
                                post("/catastro/api/v1/fiscalizacion/hallazgos/1/evidencias")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"tipo":"FOTO","sha256":"%s",
                                                 "ruta":"s3://x/y.jpg",
                                                 "observacion":"evidencia de la visita de campo"}
                                                """
                                                        .formatted("a".repeat(64))))
                        .andReturn();

        assertThat(rechazada.getResponse().getStatus()).isEqualTo(422);
        assertThat(rechazada.getResponse().getContentAsString())
                .as(
                        "sustituirlo por el del servidor dejaria el desfase de los dos relojes"
                                + " siempre en cero, y la captura sin poder auditarse — en silencio")
                .contains("capturadoEn");
    }

    @Test
    @DisplayName("NINGUN cuerpo de este contrato admite geometria (ADR-0021)")
    void ningunCuerpoAdmiteGeometria() {
        List<Class<?>> cuerpos =
                List.of(
                        FiscalizacionCatastralController.PeticionDeCampania.class,
                        FiscalizacionCatastralController.PeticionDeDeteccion.class,
                        FiscalizacionCatastralController.PeticionDeCompuerta.class,
                        FiscalizacionCatastralController.PeticionDeCampo.class,
                        FiscalizacionCatastralController.PeticionDeEvidencia.class,
                        FiscalizacionCatastralController.PeticionDeActa.class);

        for (Class<?> cuerpo : cuerpos) {
            for (java.lang.reflect.RecordComponent componente : cuerpo.getRecordComponents()) {
                assertThat(componente.getName().toLowerCase(java.util.Locale.ROOT))
                        .as(
                                "un poligono que entra por HTTP cambia el area de un predio sin"
                                        + " brigada, sin plano y sin acto (%s)",
                                cuerpo.getSimpleName())
                        .doesNotContain("geometr")
                        .doesNotContain("poligono")
                        .doesNotContain("wkt")
                        .doesNotContain("geojson");
            }
        }
    }

    @Test
    @DisplayName("y NINGUNA respuesta lleva un importe: la frontera de ADR-0024, en el contrato")
    void ningunaRespuestaLlevaUnImporte() {
        List<Class<?>> recursos =
                List.of(
                        CampaniaResource.class,
                        CandidatoResource.class,
                        HallazgoResource.class,
                        EvidenciaResource.class,
                        ActaResource.class,
                        TasaDeDescarteResource.class);

        for (Class<?> recurso : recursos) {
            for (java.lang.reflect.RecordComponent componente : recurso.getRecordComponents()) {
                String nombre = componente.getName().toLowerCase(java.util.Locale.ROOT);
                assertThat(nombre)
                        .as(
                                "si uno de estos recursos gana un campo de dinero, lo que hay que"
                                        + " revisar es la frontera y no el campo (%s)",
                                recurso.getSimpleName())
                        .doesNotContain("importe")
                        .doesNotContain("monto")
                        .doesNotContain("deuda")
                        .doesNotContain("alicuota")
                        .doesNotContain("tributo");
                assertThat(componente.getType())
                        .as("y ningun tipo de dinero, tampoco (%s)", recurso.getSimpleName())
                        .isNotEqualTo(kamayuk.catastro.dominio.Dinero.class);
            }
        }
    }

    @Test
    @DisplayName("la cola de candidatos exige su campania en la ruta, no un filtro suelto")
    void laColaEsDeUnaCampania() throws Exception {
        MvcResult sinCampania =
                mvc.perform(get("/catastro/api/v1/fiscalizacion/candidatos")).andReturn();

        assertThat(sinCampania.getResponse().getStatus())
                .as("no hay ruta que devuelva los candidatos de todas las campanias juntas")
                .isEqualTo(404);
    }

    /**
     * Un repositorio que nadie llega a llamar en estas pruebas.
     *
     * <p>Todo lanza en vez de devolver vacio: una prueba que pase porque un doble respondio «nada»
     * a algo que nadie penso no verifica lo que dice verificar. Si alguna de estas pruebas llegara
     * a la persistencia, se veria.
     */
    private static final class RepositorioQueNadieLlama implements FiscalizacionRepository {

        private static UnsupportedOperationException noSeLlama() {
            return new UnsupportedOperationException(
                    "estas pruebas se paran en el borde: no llegan a la persistencia");
        }

        @Override
        public kamayuk.catastro.fiscalizacion.dominio.Campania guardar(
                kamayuk.catastro.fiscalizacion.dominio.Campania campania) {
            throw noSeLlama();
        }

        /**
         * La campania 1 existe y esta abierta; ninguna otra.
         *
         * <p>Hace falta que exista para que la deteccion llegue a preguntar por la cartografia: la
         * comprobacion de la campania va primero, y con la campania ausente la respuesta seria un
         * 404 —correcto, y otra cosa—. Se midio: sin esto, la prueba de «sin poligonos» daba 404 en
         * vez de 409 y habria pasado por buena una respuesta que no dice lo que hace falta.
         */
        @Override
        public java.util.Optional<kamayuk.catastro.fiscalizacion.dominio.Campania> campaniaPorId(
                long id) {
            if (id != 1L) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(
                    new kamayuk.catastro.fiscalizacion.dominio.Campania(
                            1L,
                            "CAM-1",
                            "Barrido 2026",
                            kamayuk.catastro.fiscalizacion.dominio.EstadoDeCampania.ABIERTA,
                            java.time.LocalDate.of(2026, 9, 1),
                            null,
                            kamayuk.catastro.fiscalizacion.dominio.Score.de("0.20")));
        }

        @Override
        public java.util.Optional<kamayuk.catastro.fiscalizacion.dominio.Campania>
                campaniaPorCodigo(String codigo) {
            return java.util.Optional.empty();
        }

        @Override
        public kamayuk.catastro.fiscalizacion.dominio.Candidato guardar(
                kamayuk.catastro.fiscalizacion.dominio.Candidato candidato) {
            throw noSeLlama();
        }

        @Override
        public java.util.Optional<kamayuk.catastro.fiscalizacion.dominio.Candidato> candidatoPorId(
                long id) {
            return java.util.Optional.empty();
        }

        @Override
        public kamayuk.catastro.compartido.Pagina<kamayuk.catastro.fiscalizacion.dominio.Candidato>
                candidatos(
                        kamayuk.catastro.fiscalizacion.dominio.CriterioDeCandidatos criterio,
                        kamayuk.catastro.compartido.Paginacion paginacion) {
            throw noSeLlama();
        }

        @Override
        public kamayuk.catastro.fiscalizacion.dominio.TasaDeDescarte tasaDeDescarte(
                long campaniaId) {
            throw noSeLlama();
        }

        @Override
        public kamayuk.catastro.fiscalizacion.dominio.Hallazgo guardar(
                kamayuk.catastro.fiscalizacion.dominio.Hallazgo hallazgo) {
            throw noSeLlama();
        }

        @Override
        public java.util.Optional<kamayuk.catastro.fiscalizacion.dominio.Hallazgo> hallazgoPorId(
                long id) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<kamayuk.catastro.fiscalizacion.dominio.Hallazgo>
                hallazgoDelCandidato(long candidatoId) {
            return java.util.Optional.empty();
        }

        @Override
        public kamayuk.catastro.compartido.Pagina<kamayuk.catastro.fiscalizacion.dominio.Hallazgo>
                hallazgos(long campaniaId, kamayuk.catastro.compartido.Paginacion paginacion) {
            throw noSeLlama();
        }

        @Override
        public kamayuk.catastro.fiscalizacion.dominio.Evidencia guardar(
                kamayuk.catastro.fiscalizacion.dominio.Evidencia evidencia) {
            throw noSeLlama();
        }

        @Override
        public java.util.List<kamayuk.catastro.fiscalizacion.dominio.Evidencia> evidenciasDe(
                long hallazgoId) {
            throw noSeLlama();
        }

        @Override
        public kamayuk.catastro.fiscalizacion.dominio.Acta guardar(
                kamayuk.catastro.fiscalizacion.dominio.Acta acta) {
            throw noSeLlama();
        }

        @Override
        public java.util.Optional<kamayuk.catastro.fiscalizacion.dominio.Acta> actaDelHallazgo(
                long hallazgoId) {
            return java.util.Optional.empty();
        }
    }
}
