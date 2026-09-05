package kamayuk.catastro.nucleo.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kamayuk.catastro.auditoria.AuditoriaJdbc;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.nucleo.aplicacion.ConsultaDeCaracteristicas;
import kamayuk.catastro.nucleo.aplicacion.GestorDeTitularidadCatastro;
import kamayuk.catastro.nucleo.aplicacion.LectorDeCaracteristicasCatastro;
import kamayuk.catastro.nucleo.aplicacion.LectorDeFichasCatastro;
import kamayuk.catastro.nucleo.aplicacion.LectorDeFichasEconomicasCatastro;
import kamayuk.catastro.nucleo.aplicacion.PrediosDelContribuyenteCatastro;
import kamayuk.catastro.nucleo.aplicacion.RegistrarPredio;
import kamayuk.catastro.nucleo.aplicacion.TitularesDelPredioCatastro;
import kamayuk.catastro.nucleo.infraestructura.CatastroRepositoryJdbc;
import kamayuk.catastro.nucleo.infraestructura.FichaCatastralRepositoryJdbc;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import kamayuk.catastro.web.ConfiguracionDeJson;
import kamayuk.catastro.web.ManejadorDeErrores;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las cinco lecturas que le faltaban a la frontera con {@code rentas}, de HTTP a PostgreSQL (C-5).
 *
 * <h2>Por que va hasta la base</h2>
 *
 * <p>Porque lo que hay que demostrar aqui <b>es la resolucion por fecha</b>, y esa vive en el
 * {@code WHERE} de dos consultas: {@code f.vigencia_desde &lt;= :fecha AND (f.vigencia_hasta IS
 * NULL OR f.vigencia_hasta &gt;= :fecha)} para la ficha, y su gemela para la titularidad. Un doble
 * del repositorio devolveria lo que se le pidiera y la prueba diria que la llamada se hizo, no que
 * la version que contesta es la que regia.
 *
 * <p>Es el defecto de #24 —una notificacion de marzo con la direccion de setiembre—, el de #366
 * —preguntar por marzo y recibir al comprador de julio— y el que C-1 encontro <b>ya servido por
 * HTTP</b>: el nombre del parametro no coincidia, se descartaba en silencio y la grilla se resolvia
 * con el reloj del servidor. Estas rutas nacen con la fecha obligatoria para que un olvido sea
 * ruidoso.
 *
 * <p>La conexion es la de {@code kamayuk_app}: un superusuario omite RLS incluso con {@code FORCE
 * ROW LEVEL SECURITY}, y con el la municipalidad vecina veria estos predios.
 */
@DisplayName("C-5 — Las lecturas de la frontera, de HTTP a PostgreSQL")
class LecturasDeLaFronteraFronteraTest {

    /** Agosto de 2026: bien despues de las dos versiones que la siembra deja. */
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC);

    /** La fecha con la que regia la PRIMERA version de todo lo que esta prueba siembra. */
    private static final String EN_2024 = "2024-06-30";

    /** Y la fecha con la que rige la SEGUNDA. */
    private static final String EN_2026 = "2026-06-30";

    private static BaseDeDatosDePrueba base;
    private static long municipalidadA;
    private static long municipalidadB;
    private static MockMvc mvc;

    private static long predio;
    private static long predioSinFicha;
    private static long predioAjeno;
    private static long fichaDe2024;
    private static long fichaDe2026;
    private static long fichaEconomica;
    private static long duenoAntiguo;
    private static long duenoNuevo;
    private static long titularidadDe2026;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidadA = crearMunicipalidad("260101", "Municipalidad de la frontera");
        municipalidadB = crearMunicipalidad("260102", "Municipalidad vecina");

        duenoAntiguo = contribuyente(municipalidadA, "C-5001", "45000001", "ANTIGUO, EL");
        duenoNuevo = contribuyente(municipalidadA, "C-5002", "45000002", "NUEVO, EL");

        long sector = sector(municipalidadA, "S-05", "SECTOR CINCO");
        predio = predio(municipalidadA, "26010100010001000100001", sector);
        predioSinFicha = predio(municipalidadA, "26010100010001000100002", null);
        predioAjeno = predio(municipalidadB, "26010200010001000100001", null);

        // Dos versiones de la ficha unica: la de 2024 se cierra el dia antes de que abra la
        // de 2026. Es lo que hace que «la vigente a una fecha» y «la ultima» sean respuestas
        // distintas — sin dos versiones, la mutacion que resuelve «la ultima» pasa en verde.
        fichaDe2024 =
                ficha(predio, "UNICA", 1, "120.00", "CASA HABITACION", "2024-01-01", "2025-12-31");
        fichaDe2026 = ficha(predio, "UNICA", 2, "180.50", "COMERCIO", "2026-01-01", null);
        fichaEconomica = ficha(predio, "ECONOMICA", 1, "180.50", "COMERCIO", "2026-01-01", null);

        // Y dos duenos: el de 2024 cierra su cuota cuando abre la del de 2026.
        titularidad(predio, duenoAntiguo, "PROPIETARIO_UNICO", "100", "2024-01-01", "2025-12-31");
        titularidadDe2026 =
                titularidad(predio, duenoNuevo, "PROPIETARIO_UNICO", "100", "2026-01-01", null);

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        JdbcClient jdbc = JdbcClient.create(pool);
        TenantTransactionManager gestor = new TenantTransactionManager(pool);
        CatastroRepositoryJdbc catastro = new CatastroRepositoryJdbc(jdbc);
        FichaCatastralRepositoryJdbc fichas = new FichaCatastralRepositoryJdbc(jdbc);

        LectorDeFichasCatastro lectorDeFichas =
                envolver(new LectorDeFichasCatastro(fichas), gestor);
        LectorDeFichasEconomicasCatastro lectorDeEconomicas =
                envolver(new LectorDeFichasEconomicasCatastro(fichas), gestor);
        LectorDeCaracteristicasCatastro lectorDeCaracteristicas =
                envolver(new LectorDeCaracteristicasCatastro(catastro, fichas), gestor);
        TitularesDelPredioCatastro titulares =
                envolver(new TitularesDelPredioCatastro(catastro), gestor);
        PrediosDelContribuyenteCatastro prediosDe =
                envolver(new PrediosDelContribuyenteCatastro(catastro), gestor);
        GestorDeTitularidadCatastro titularidad =
                envolver(
                        new GestorDeTitularidadCatastro(
                                catastro,
                                new RegistrarPredio(
                                        catastro, new AuditoriaJdbc(jdbc, RELOJ), RELOJ)),
                        gestor);

        mvc =
                MockMvcBuilders.standaloneSetup(
                                new CaracteristicasDelPredioController(
                                        envolver(
                                                new ConsultaDeCaracteristicas(
                                                        lectorDeCaracteristicas,
                                                        lectorDeFichas,
                                                        lectorDeEconomicas),
                                                gestor),
                                        lectorDeFichas,
                                        titulares),
                                new TitularidadDelPredioController(
                                        titulares, titularidad, prediosDe))
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

    @AfterAll
    static void cerrar() {
        if (base != null) {
            base.close();
        }
    }

    @BeforeEach
    void contexto() {
        TenantContext.fijar(new MunicipalidadId(municipalidadA));
        OrigenContext.fijar(new Origen("tecnico.catastro", "PC-05", "10.0.0.5"));
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
        OrigenContext.limpiar();
    }

    // ---------- La fecha manda (criterio 3 de C-5) ----------

    @Test
    @DisplayName("las caracteristicas de 2024 son las de 2024, no las de hoy")
    void laFichaSeResuelveALaFechaPedida() throws Exception {
        MvcResult enteonces = caracteristicas(predio, EN_2024);
        MvcResult ahora = caracteristicas(predio, EN_2026);

        assertThat(cuerpo(enteonces))
                .as(
                        "resolver «la ultima» en vez de «la vigente a la fecha» devuelve la ficha de"
                                + " 2026 a quien pregunta por 2024, que es #24 y #366 servidos por"
                                + " HTTP")
                .contains("\"fichaId\":" + fichaDe2024)
                .contains("\"uso\":\"CASA HABITACION\"")
                .contains("\"areaTerreno\":\"120.00\"")
                .contains("\"aLaFecha\":\"" + EN_2024 + "\"");

        assertThat(cuerpo(ahora))
                .contains("\"fichaId\":" + fichaDe2026)
                .contains("\"uso\":\"COMERCIO\"")
                .contains("\"areaTerreno\":\"180.50\"");
    }

    @Test
    @DisplayName("de quien era el predio en 2024 no es de quien es hoy")
    void laTitularidadSeResuelveALaFechaPedida() throws Exception {
        MvcResult enteonces = titularesDe(predio, EN_2024);
        MvcResult ahora = titularesDe(predio, EN_2026);

        assertThat(cuerpo(enteonces))
                .contains("\"contribuyenteId\":" + duenoAntiguo)
                .doesNotContain("\"contribuyenteId\":" + duenoNuevo)
                .contains("\"aLaFecha\":\"" + EN_2024 + "\"");
        assertThat(cuerpo(ahora))
                .contains("\"contribuyenteId\":" + duenoNuevo)
                .doesNotContain("\"contribuyenteId\":" + duenoAntiguo);
    }

    @Test
    @DisplayName("y los predios de una persona son los que eran suyos esa fecha")
    void losPrediosDelTitularSeResuelvenALaFechaPedida() throws Exception {
        MvcResult delAntiguoEn2024 = prediosDe(duenoAntiguo, EN_2024);
        MvcResult delAntiguoHoy = prediosDe(duenoAntiguo, EN_2026);

        assertThat(cuerpo(delAntiguoEn2024))
                .contains("\"predioId\":" + predio)
                .contains("\"porcentajeTitularidad\":\"100.0000\"")
                .contains("\"porcentajeRegistradoDelPredio\":\"100.0000\"")
                .contains("\"contribuyenteId\":" + duenoAntiguo)
                .contains("\"aLaFecha\":\"" + EN_2024 + "\"");
        assertThat(cuerpo(delAntiguoHoy))
                .as("vendio en 2025: hoy no tiene ninguno, y eso es un dato y no un fallo")
                .contains("\"predios\":[]");
    }

    @Test
    @DisplayName("sin fecha no se contesta: 422, y no la ficha de hoy")
    void laFechaEsObligatoria() throws Exception {
        MvcResult sinFecha =
                mvc.perform(get("/catastro/api/v1/catastro/predios/" + predio + "/caracteristicas"))
                        .andReturn();

        assertThat(sinFecha.getResponse().getStatus())
                .as(
                        "con un valor por omision del reloj, un cliente que la olvidara recibiria la"
                                + " ficha de hoy con 200 delante y nada lo diria")
                .isEqualTo(422);
        assertThat(
                        mvc.perform(get("/catastro/api/v1/catastro/titularidad?predio=" + predio))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(422);
    }

    // ---------- Lo que no esta se dice, y no con un 404 ----------

    @Test
    @DisplayName("un predio que no esta en el padron se dice con un campo, no con un 404")
    void elPredioQueNoEstaSeDice() throws Exception {
        MvcResult esta = mvc.perform(get(RUTA_PREDIO + predio)).andReturn();
        MvcResult noEsta = mvc.perform(get(RUTA_PREDIO + 9_999_999L)).andReturn();

        assertThat(esta.getResponse().getStatus()).isEqualTo(200);
        assertThat(cuerpo(esta)).contains("\"enElPadron\":true");
        assertThat(noEsta.getResponse().getStatus())
                .as(
                        "si «ese predio no esta» fuera 404, una ruta mal escrita se leeria igual: el"
                                + " 404 de esta frontera tiene que seguir queriendo decir «esa ruta"
                                + " no existe»")
                .isEqualTo(200);
        assertThat(cuerpo(noEsta)).contains("\"enElPadron\":false");
    }

    @Test
    @DisplayName("un predio sin ficha esta en el padron y no tiene fichaId: son dos cosas")
    void elPredioSinFichaNoEsUnPredioQueNoEsta() throws Exception {
        assertThat(cuerpo(caracteristicas(predioSinFicha, EN_2026)))
                .contains("\"enElPadron\":true")
                .contains("\"fichaId\":null")
                .contains("\"fichaEconomicaId\":null")
                .contains("\"areaTerreno\":null");
    }

    @Test
    @DisplayName("la ficha economica sale por su campo y no se confunde con la unica")
    void laFichaEconomicaTieneSuCampo() throws Exception {
        assertThat(cuerpo(caracteristicas(predio, EN_2026)))
                .contains("\"fichaId\":" + fichaDe2026)
                .contains("\"fichaEconomicaId\":" + fichaEconomica);
        assertThat(cuerpo(caracteristicas(predio, EN_2024)))
                .as("en 2024 no habia ficha economica, y eso no vuelve nula la unica")
                .contains("\"fichaId\":" + fichaDe2024)
                .contains("\"fichaEconomicaId\":null");
    }

    @Test
    @DisplayName("el area de UNA version es la de esa version, no la del predio hoy")
    void elAreaEsLaDeLaVersion() throws Exception {
        assertThat(cuerpo(mvc.perform(get(RUTA_FICHA + fichaDe2024 + "/area")).andReturn()))
                .as(
                        "una declaracion jurada de 2024 guarda ESTE identificador para poder"
                                + " contrastar contra lo que constaba entonces (#49, RF-055)")
                .contains("\"existe\":true")
                .contains("\"areaTerreno\":\"120.00\"");
        assertThat(cuerpo(mvc.perform(get(RUTA_FICHA + 9_999_999L + "/area")).andReturn()))
                .contains("\"existe\":false")
                .contains("\"areaTerreno\":null");
    }

    @Test
    @DisplayName("la cuota de un titular trae su titularidadId; la de quien no lo es, ninguno")
    void laCuotaTraeElIdentificadorConElQueSeTransfiere() throws Exception {
        MvcResult suya = cuota(predio, duenoNuevo, EN_2026);
        MvcResult ajena = cuota(predio, duenoAntiguo, EN_2026);

        assertThat(cuerpo(suya))
                .contains("\"tieneCuota\":true")
                .contains("\"titularidadId\":" + titularidadDe2026)
                .contains("\"porcentaje\":\"100.0000\"")
                .contains("\"predioId\":" + predio)
                .contains("\"contribuyenteId\":" + duenoNuevo);
        assertThat(ajena.getResponse().getStatus())
                .as(
                        "«no es titular» es un dato: con 404 no se distinguiria de una ruta que no esta")
                .isEqualTo(200);
        assertThat(cuerpo(ajena)).contains("\"tieneCuota\":false").contains("\"titularidadId\":0");
    }

    // ---------- Una peticion para varios predios ----------

    @Test
    @DisplayName("varios predios se piden en UNA peticion, y el orden es el que se pidio")
    void variosPrediosEnUnaPeticion() throws Exception {
        MvcResult varios =
                mvc.perform(
                                get("/catastro/api/v1/catastro/titularidad")
                                        .param("predio", String.valueOf(predioSinFicha))
                                        .param("predio", String.valueOf(predio))
                                        .param("fecha", EN_2026))
                        .andReturn();

        String cuerpo = cuerpo(varios);
        assertThat(cuerpo)
                .as(
                        "el predio sin titulares no sale, igual que hacia el puerto dentro del"
                                + " proceso: esta frontera traslada comportamiento, no lo cambia")
                .contains("\"predioId\":" + predio)
                .doesNotContain("\"predioId\":" + predioSinFicha);
        assertThat(varios.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("sin ningun predio no se contesta el padron entero: 422")
    void sinPredioNoHaySujeto() throws Exception {
        MvcResult ninguno =
                mvc.perform(get("/catastro/api/v1/catastro/titularidad").param("fecha", EN_2026))
                        .andReturn();

        assertThat(ninguno.getResponse().getStatus())
                .as(
                        "un parametro repetido cero veces llega igual que uno ausente: «estos, y"
                                + " ninguno» y «todos» serian la misma URL")
                .isEqualTo(422);
    }

    // ---------- El aislamiento ----------

    @Test
    @DisplayName("el predio de la vecina no esta en el padron de esta municipalidad")
    void elAislamientoSeSostiene() throws Exception {
        assertThat(cuerpo(mvc.perform(get(RUTA_PREDIO + predioAjeno)).andReturn()))
                .as(
                        "con el pool conectado como superusuario esto diria «true», y una"
                                + " determinacion se apoyaria en el predio de la vecina")
                .contains("\"enElPadron\":false");
        assertThat(cuerpo(caracteristicas(predioAjeno, EN_2026))).contains("\"enElPadron\":false");
        assertThat(cuerpo(titularesDe(predioAjeno, EN_2026))).contains("\"predios\":[]");
    }

    // ------------------------------------------------------------------

    private static final String RUTA_PREDIO = "/catastro/api/v1/catastro/predios/";
    private static final String RUTA_FICHA = "/catastro/api/v1/catastro/fichas/";

    private static MvcResult caracteristicas(long predioId, String fecha) throws Exception {
        return mvc.perform(get(RUTA_PREDIO + predioId + "/caracteristicas").param("fecha", fecha))
                .andReturn();
    }

    private static MvcResult titularesDe(long predioId, String fecha) throws Exception {
        return mvc.perform(
                        get("/catastro/api/v1/catastro/titularidad")
                                .param("predio", String.valueOf(predioId))
                                .param("fecha", fecha))
                .andReturn();
    }

    private static MvcResult cuota(long predioId, long contribuyenteId, String fecha)
            throws Exception {
        return mvc.perform(
                        get("/catastro/api/v1/catastro/titularidad/cuota")
                                .param("predio", String.valueOf(predioId))
                                .param("contribuyente", String.valueOf(contribuyenteId))
                                .param("fecha", fecha))
                .andReturn();
    }

    private static MvcResult prediosDe(long contribuyenteId, String fecha) throws Exception {
        return mvc.perform(
                        get("/catastro/api/v1/catastro/titularidad/predios")
                                .param("contribuyente", String.valueOf(contribuyenteId))
                                .param("fecha", fecha))
                .andReturn();
    }

    private static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString();
    }

    @SuppressWarnings("unchecked")
    private static <T> T envolver(T objetivo, TenantTransactionManager gestor) {
        ProxyFactory fabrica = new ProxyFactory(objetivo);
        fabrica.setProxyTargetClass(true);
        fabrica.addAdvice(
                new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        return (T) fabrica.getProxy();
    }

    private static long crearMunicipalidad(String ubigeo, String nombre) throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER);
                PreparedStatement sentencia =
                        owner.prepareStatement(
                                "INSERT INTO municipalidad (ubigeo, nombre, tipo)"
                                        + " VALUES (?, ?, 'DISTRITAL') RETURNING id")) {
            sentencia.setString(1, ubigeo);
            sentencia.setString(2, nombre);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                long id = resultado.getLong(1);
                owner.commit();
                return id;
            }
        }
    }

    private static long contribuyente(
            long municipalidadId, String codigo, String documento, String nombre)
            throws SQLException {
        return sembrar(
                municipalidadId,
                "INSERT INTO contribuyente_de_prueba (municipalidad_id, codigo_contribuyente,"
                        + " tipo_documento, numero_documento, tipo_persona, nombre_razon_social,"
                        + " usuario_registro) VALUES (?, ?, 'DNI', ?, 'NATURAL', ?, 'siembra')"
                        + " RETURNING id",
                sentencia -> {
                    sentencia.setLong(1, municipalidadId);
                    sentencia.setString(2, codigo);
                    sentencia.setString(3, documento);
                    sentencia.setString(4, nombre);
                });
    }

    private static long sector(long municipalidadId, String codigo, String nombre)
            throws SQLException {
        return sembrar(
                municipalidadId,
                "INSERT INTO sector (municipalidad_id, codigo, nombre) VALUES (?, ?, ?)"
                        + " RETURNING id",
                sentencia -> {
                    sentencia.setLong(1, municipalidadId);
                    sentencia.setString(2, codigo);
                    sentencia.setString(3, nombre);
                });
    }

    private static long predio(long municipalidadId, String codigo, Long sectorId)
            throws SQLException {
        return sembrar(
                municipalidadId,
                "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo, direccion,"
                        + " estado, sector_id) VALUES (?, ?, 'URBANO', 'AV. FRONTERA 1', 'ACTIVO',"
                        + " ?) RETURNING id",
                sentencia -> {
                    sentencia.setLong(1, municipalidadId);
                    sentencia.setString(2, codigo);
                    if (sectorId == null) {
                        sentencia.setNull(3, java.sql.Types.BIGINT);
                    } else {
                        sentencia.setLong(3, sectorId);
                    }
                });
    }

    private static long ficha(
            long predioId,
            String tipo,
            int version,
            String area,
            String uso,
            String desde,
            String hasta)
            throws SQLException {
        return sembrar(
                municipalidadA,
                "INSERT INTO ficha_catastral (municipalidad_id, predio_id, tipo, version,"
                        + " area_terreno, uso, vigencia_desde, vigencia_hasta, origen,"
                        + " documento_origen, observacion, usuario_registro)"
                        + " VALUES (?, ?, ?, ?, CAST(? AS numeric), ?, CAST(? AS date),"
                        + " CAST(? AS date), 'DECLARACION_JURADA', 'SIEMBRA', 'Siembra de C-5',"
                        + " 'siembra') RETURNING id",
                sentencia -> {
                    sentencia.setLong(1, municipalidadA);
                    sentencia.setLong(2, predioId);
                    sentencia.setString(3, tipo);
                    sentencia.setInt(4, version);
                    sentencia.setString(5, area);
                    sentencia.setString(6, uso);
                    sentencia.setString(7, desde);
                    sentencia.setString(8, hasta);
                });
    }

    private static long titularidad(
            long predioId,
            long contribuyenteId,
            String condicion,
            String porcentaje,
            String desde,
            String hasta)
            throws SQLException {
        return sembrar(
                municipalidadA,
                "INSERT INTO titularidad (municipalidad_id, predio_id, contribuyente_id, condicion,"
                        + " porcentaje, vigencia_desde, vigencia_hasta, documento_origen)"
                        + " VALUES (?, ?, ?, ?, CAST(? AS numeric), CAST(? AS date),"
                        + " CAST(? AS date), 'SIEMBRA') RETURNING id",
                sentencia -> {
                    sentencia.setLong(1, municipalidadA);
                    sentencia.setLong(2, predioId);
                    sentencia.setLong(3, contribuyenteId);
                    sentencia.setString(4, condicion);
                    sentencia.setString(5, porcentaje);
                    sentencia.setString(6, desde);
                    sentencia.setString(7, hasta);
                });
    }

    /**
     * Un {@code INSERT … RETURNING id} como {@code kamayuk_app}, con el contexto de tenant fijado.
     */
    private static long sembrar(long municipalidadId, String sql, Parametros parametros)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (PreparedStatement sentencia = app.prepareStatement(sql)) {
                parametros.poner(sentencia);
                try (ResultSet resultado = sentencia.executeQuery()) {
                    resultado.next();
                    long id = resultado.getLong(1);
                    app.commit();
                    return id;
                }
            }
        }
    }

    @FunctionalInterface
    private interface Parametros {
        void poner(PreparedStatement sentencia) throws SQLException;
    }
}
