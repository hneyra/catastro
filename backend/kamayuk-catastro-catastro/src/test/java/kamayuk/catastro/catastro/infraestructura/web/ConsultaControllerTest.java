package kamayuk.catastro.catastro.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kamayuk.catastro.catastro.AcotacionPorPredio;
import kamayuk.catastro.catastro.aplicacion.ConsultaDeFichas;
import kamayuk.catastro.catastro.dominio.FichaCatastralRepository;
import kamayuk.catastro.catastro.dominio.FichaEncontrada;
import kamayuk.catastro.catastro.dominio.FiltroDeFichas;
import kamayuk.catastro.catastro.dominio.TipoFicha;
import kamayuk.catastro.compartido.Pagina;
import kamayuk.catastro.compartido.Paginacion;
import kamayuk.catastro.contribuyentes.DirectorioDeContribuyentes;
import kamayuk.catastro.contribuyentes.ResumenDeContribuyente;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.CodigoReferenciaCatastral;
import kamayuk.catastro.web.ConfiguracionDeJson;
import kamayuk.catastro.web.ManejadorDeErrores;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * El transporte de la consulta de fichas, por HTTP de verdad y sin base de datos.
 *
 * <p>Lo que se verifica aqui es lo que la base no puede decir: la forma del JSON y, sobre todo, que
 * un filtro que el sistema todavia no sabe responder <b>se rechace</b> en vez de ignorarse. Lo que
 * la base si verifica —el plan, el aislamiento, el titular unico por predio— tiene sus pruebas en
 * {@code ConsultaDeFichasTest}, contra PostgreSQL.
 */
@DisplayName("Capa web — GET /api/v1/catastro/fichas")
class ConsultaControllerTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-08-19T10:00:00Z"), ZoneId.of("America/Lima"));

    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria();

    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(
                            new ConsultaController(
                                    new ConsultaDeFichas(repositorio, new PadronVacio()), RELOJ))
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
    @DisplayName("devuelve la pagina en la forma unica, con campos en español camelCase")
    void devuelveLaPaginaEnLaFormaUnica() throws Exception {
        MvcResult resultado = mvc.perform(get("/catastro/api/v1/catastro/fichas")).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(resultado.getResponse().getContentAsString())
                .contains("\"contenido\"")
                .contains("\"totalElementos\":2")
                .contains("\"codRefCatastral\":\"27010100100100101010001\"")
                .contains("\"version\":1");
    }

    @Test
    @DisplayName("el area construida viaja SUMADA, y sin unidad dentro: la pone la cabecera (#607)")
    void elAreaConstruidaViajaSumada() throws Exception {
        MvcResult resultado = mvc.perform(get("/catastro/api/v1/catastro/fichas")).andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .as(
                        "si la fila no la publicara, la pantalla tendria que pedir cada ficha con sus"
                                + " construcciones y sumarlas: veinte peticiones por pagina y una suma"
                                + " distinta en cada pantalla que la necesite")
                .contains("\"areaConstruida\":\"210.50\"");
    }

    @Test
    @DisplayName("y ninguna area de la grilla lleva los «m2» dentro (#607)")
    void ningunAreaLlevaLaUnidadDentro() throws Exception {
        MvcResult resultado = mvc.perform(get("/catastro/api/v1/catastro/fichas")).andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .as(
                        "hasta #607 esta misma respuesta decia «360.00 m2» del predio del que"
                                + " GET /fiscalizacion/omisos decia «360.00»: la misma superficie"
                                + " con dos formas segun a que modulo se le preguntara")
                .doesNotContain("m2");
    }

    @Test
    @DisplayName("un terreno sin construir sale con area construida nula, que no es cero")
    void unTerrenoSinConstruirSaleNulo() throws Exception {
        MvcResult resultado = mvc.perform(get("/catastro/api/v1/catastro/fichas")).andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .as(
                        "un 0 seria un area declarada; la pantalla pinta un guion, y un guion no es"
                                + " un cero")
                .contains("\"areaConstruida\":null");
    }

    @Test
    @DisplayName("el filtro de conciliacion redirige a la ruta que si lo sabe responder (#344)")
    void elFiltroDeConciliacionRedirige() throws Exception {
        MvcResult resultado =
                mvc.perform(
                                get("/catastro/api/v1/catastro/fichas")
                                        .param("conciliadaConRentas", "Sí"))
                        .andReturn();

        assertThat(resultado.getResponse().getStatus())
                .as(
                        "hasta #344 esto era un 422 deliberado, porque la lectura compuesta no"
                                + " existia. Ahora existe en rentas, y lo que no puede pasar es lo"
                                + " de siempre: aceptar el filtro y devolver el listado completo,"
                                + " que daria un resultado plausible y equivocado")
                .isEqualTo(307);
        assertThat(resultado.getResponse().getHeader("Location"))
                .as("y el valor viaja codificado, no crudo, dentro de la cabecera")
                .isEqualTo(
                        "/catastro/api/v1/catastro/fichas/conciliacion?conciliadaConRentas=S%C3%AD");
        assertThat(resultado.getResponse().getContentAsString())
                .as("y no devuelve ni una fila: quien contesta es la otra ruta")
                .isEmpty();
    }

    @Test
    @DisplayName("el redirigido conserva la peticion entera, no solo el filtro que lo provoco")
    void elRedirigidoConservaLaPeticionEntera() throws Exception {
        MvcResult resultado =
                mvc.perform(
                                get("/catastro/api/v1/catastro/fichas")
                                        .param("codRefCatastral", "270101001")
                                        .param("conciliadaConRentas", "No")
                                        .param("pagina", "3")
                                        .param("tamano", "50"))
                        .andReturn();

        assertThat(resultado.getResponse().getHeader("Location"))
                .as(
                        "perder el filtro o la pagina por el camino devolveria otra grilla con"
                                + " apariencia de la pedida, que es peor que no contestar")
                .startsWith("/catastro/api/v1/catastro/fichas/conciliacion?")
                .contains("codRefCatastral=270101001")
                .contains("conciliadaConRentas=No")
                .contains("pagina=3")
                .contains("tamano=50");
    }

    @Test
    @DisplayName("un valor en blanco no redirige: es no haber elegido nada")
    void unValorEnBlancoNoRedirige() throws Exception {
        MvcResult resultado =
                mvc.perform(
                                get("/catastro/api/v1/catastro/fichas")
                                        .param("conciliadaConRentas", "  "))
                        .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("un tipo de ficha que no existe es 422, sin nombrar columnas")
    void unTipoInexistenteEs422() throws Exception {
        MvcResult resultado =
                mvc.perform(get("/catastro/api/v1/catastro/fichas").param("tipo", "MARCIANA"))
                        .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(422);
        assertThat(resultado.getResponse().getContentAsString())
                .contains("UNICA")
                .doesNotContain("ficha_catastral");
    }

    @Test
    @DisplayName("una fecha mal formada es 422, no 500")
    void unaFechaMalFormadaEs422() throws Exception {
        MvcResult resultado =
                mvc.perform(get("/catastro/api/v1/catastro/fichas").param("fecha", "19-08-2026"))
                        .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(422);
        assertThat(resultado.getResponse().getContentAsString()).contains("AAAA-MM-DD");
    }

    @Test
    @DisplayName("no devuelve la municipalidad, porque no la conoce ni la necesita")
    void noDevuelveLaMunicipalidad() throws Exception {
        MvcResult resultado = mvc.perform(get("/catastro/api/v1/catastro/fichas")).andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .as("el identificador de municipalidad no sale ni entra por HTTP (ADR-0005)")
                .doesNotContain("municipalidad");
    }

    // ------------------------------------------------------------------
    //  C-1: los desajustes de frontera que este endpoint pagaba
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C-1 (1) — el identificador de la fila se llama «fichaId», no «id»")
    void elIdentificadorDeLaFilaSeLlamaFichaId() throws Exception {
        MvcResult resultado = mvc.perform(get("/catastro/api/v1/catastro/fichas")).andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .as(
                        "la fila lleva DOS identificadores; «id» al lado de «predioId» no dice"
                                + " cual, y el consumidor que lee «fichaId» recibe un CERO sin que"
                                + " nada falle")
                .contains("\"fichaId\":1")
                .contains("\"predioId\":10")
                .doesNotContain("\"id\":");
    }

    @Test
    @DisplayName("C-1 (4) — «soloPredio» acota la grilla a esos lotes, y no se descarta")
    void soloPredioAcotaLaGrilla() throws Exception {
        mvc.perform(
                        get("/catastro/api/v1/catastro/fichas")
                                .param("soloPredio", "11")
                                .param("soloPredio", "12"))
                .andReturn();

        assertThat(repositorio.filtroRecibido().acotacion())
                .as(
                        "hasta C-1 viajaba en la URL y se descartaba: la conciliacion pedia unos"
                                + " lotes y recibia la pagina del padron entero, con"
                                + " «totalElementos» del padron entero (#631)")
                .isEqualTo(AcotacionPorPredio.soloEstos(List.of(11L, 12L)));
    }

    @Test
    @DisplayName("C-1 (5) — «exceptoPredio» acota al complemento")
    void exceptoPredioAcotaAlComplemento() throws Exception {
        mvc.perform(get("/catastro/api/v1/catastro/fichas").param("exceptoPredio", "13"))
                .andReturn();

        assertThat(repositorio.filtroRecibido().acotacion())
                .isEqualTo(AcotacionPorPredio.todosMenosEstos(List.of(13L)));
    }

    @Test
    @DisplayName("y sin ninguno de los dos, la grilla no se acota: el padron paginado")
    void sinAcotacionLaGrillaEsLaDelPadron() throws Exception {
        mvc.perform(get("/catastro/api/v1/catastro/fichas")).andReturn();

        assertThat(repositorio.filtroRecibido().acotacion())
                .as("el contraste: si acotara siempre, las dos pruebas de arriba no dirian nada")
                .isEqualTo(AcotacionPorPredio.ninguna());
    }

    @Test
    @DisplayName("los dos a la vez son 422, no una eleccion silenciosa")
    void losDosALaVezSeRechazan() throws Exception {
        MvcResult resultado =
                mvc.perform(
                                get("/catastro/api/v1/catastro/fichas")
                                        .param("soloPredio", "11")
                                        .param("exceptoPredio", "13"))
                        .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(422);
        assertThat(resultado.getResponse().getContentAsString())
                .as("quedarse con uno devolveria una grilla plausible que acota por otra cosa")
                .contains("soloPredio")
                .contains("exceptoPredio");
    }

    @Test
    @DisplayName("C-1 (3) — «fecha» no se declara y se ignora: llega hasta el repositorio")
    void laFechaDeCorteLlegaHastaElRepositorio() throws Exception {
        mvc.perform(get("/catastro/api/v1/catastro/fichas").param("fecha", "2026-03-15"))
                .andReturn();

        assertThat(repositorio.fechaRecibida())
                .as(
                        "el registro de P6 decia que este endpoint «declara el parametro y lo"
                                + " ignora». No lo ignora: pedir marzo devolvia la ficha de hoy"
                                + " porque el consumidor lo mandaba con OTRO nombre, y entonces se"
                                + " tomaba el valor por omision del reloj")
                .isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    @DisplayName("y sin fecha se resuelve con el reloj, que es el contraste de la anterior")
    void sinFechaSeResuelveConElReloj() throws Exception {
        mvc.perform(get("/catastro/api/v1/catastro/fichas")).andReturn();

        assertThat(repositorio.fechaRecibida()).isEqualTo(LocalDate.of(2026, 8, 19));
    }

    // ------------------------------------------------------------------

    /** El padron no hace falta para probar el transporte: aqui nadie filtra por titular. */
    private static final class PadronVacio implements DirectorioDeContribuyentes {

        @Override
        public List<ResumenDeContribuyente> buscar(String texto, int maximo) {
            return List.of();
        }

        @Override
        public Optional<ResumenDeContribuyente> porCodigo(String codigo) {
            return Optional.empty();
        }

        @Override
        public Map<Long, ResumenDeContribuyente> porIds(Set<Long> ids) {
            return Map.of();
        }

        @Override
        public Optional<String> domicilioFiscalDe(long contribuyenteId, LocalDate fecha) {
            return Optional.empty();
        }
    }

    /** Solo lo que el controlador llama; el resto no se implementa porque no se usa. */
    private static final class RepositorioEnMemoria implements FichaCatastralRepository {

        @Override
        public java.util.Optional<kamayuk.catastro.catastro.dominio.FichaCatastral> porId(
                long fichaId) {
            throw new UnsupportedOperationException("esta prueba no lee una version por id");
        }

        /** Con su area construida ya sumada por la base: es asi como llega la fila. */
        private static final FichaEncontrada UNA =
                new FichaEncontrada(
                        1L,
                        10L,
                        CodigoReferenciaCatastral.de("27010100100100101010001"),
                        "AV. GRAU 100",
                        "MZ-A",
                        "01",
                        TipoFicha.UNICA,
                        1,
                        AreaM2.de("120.00"),
                        AreaM2.de("210.50"),
                        "CASA HABITACION",
                        LocalDate.of(2026, 1, 1),
                        null,
                        null);

        /** Un terreno sin construir: la version no declara construcciones y no hay suma. */
        private static final FichaEncontrada SIN_CONSTRUIR =
                new FichaEncontrada(
                        2L,
                        11L,
                        CodigoReferenciaCatastral.de("27010100100100101010002"),
                        "AV. GRAU 200",
                        "MZ-A",
                        "02",
                        TipoFicha.UNICA,
                        1,
                        AreaM2.de("300.00"),
                        null,
                        "TERRENO SIN CONSTRUIR",
                        LocalDate.of(2026, 1, 1),
                        null,
                        null);

        /** Con que criterio y a que fecha se le pregunto. Es lo que mide si un filtro LLEGA. */
        private @Nullable FiltroDeFichas ultimoFiltro;

        private @Nullable LocalDate ultimaFecha;

        @Override
        public Pagina<FichaEncontrada> consultar(
                FiltroDeFichas filtro,
                List<Long> titulares,
                LocalDate fecha,
                Paginacion paginacion) {
            this.ultimoFiltro = filtro;
            this.ultimaFecha = fecha;
            return Pagina.de(List.of(UNA, SIN_CONSTRUIR), paginacion, 2);
        }

        private FiltroDeFichas filtroRecibido() {
            return java.util.Objects.requireNonNull(
                    ultimoFiltro, "el controlador no llego a consultar el repositorio");
        }

        private LocalDate fechaRecibida() {
            return java.util.Objects.requireNonNull(
                    ultimaFecha, "el controlador no llego a consultar el repositorio");
        }

        @Override
        public List<kamayuk.catastro.catastro.dominio.VersionDeLaFicha> versionesDe(
                long predioId, TipoFicha tipo) {
            return List.of();
        }

        @Override
        public Optional<kamayuk.catastro.catastro.dominio.FichaCatastral> vigenteA(
                long predioId, TipoFicha tipo, LocalDate fecha) {
            return Optional.empty();
        }

        @Override
        public List<kamayuk.catastro.catastro.dominio.FichaCatastral> historial(
                long predioId, TipoFicha tipo) {
            return List.of();
        }

        @Override
        public Optional<kamayuk.catastro.catastro.dominio.FichaCatastral> ultimaVersion(
                long predioId, TipoFicha tipo) {
            return Optional.empty();
        }

        @Override
        public kamayuk.catastro.catastro.dominio.FichaCatastral insertar(
                kamayuk.catastro.catastro.dominio.FichaCatastral ficha) {
            throw new UnsupportedOperationException("El transporte no escribe");
        }

        @Override
        public kamayuk.catastro.catastro.dominio.FichaCatastral cerrar(
                kamayuk.catastro.catastro.dominio.FichaCatastral ficha) {
            throw new UnsupportedOperationException("El transporte no escribe");
        }

        @Override
        public List<kamayuk.catastro.catastro.dominio.Construccion> construccionesDe(long fichaId) {
            return List.of();
        }

        @Override
        public List<kamayuk.catastro.catastro.dominio.OtraInstalacion> instalacionesDe(
                long fichaId) {
            return List.of();
        }

        @Override
        public Optional<kamayuk.catastro.catastro.dominio.DetalleDeLaFicha> detalleDe(
                long fichaId, TipoFicha tipo) {
            return Optional.empty();
        }
    }
}
