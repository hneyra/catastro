package kamayuk.catastro.grd.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import kamayuk.catastro.grd.aplicacion.ConsultaDeItse;
import kamayuk.catastro.grd.aplicacion.ConsultaDeRiesgo;
import kamayuk.catastro.grd.aplicacion.RegistrarCertificadoItse;
import kamayuk.catastro.grd.infraestructura.GestionDeRiesgoRepositoryJdbc;
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
import org.springframework.http.MediaType;
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
 * El riesgo y el ITSE de un predio, de HTTP a PostgreSQL y sin un doble por el camino (#5).
 *
 * <h2>Por que va hasta la base, y no se puede probar de otro modo</h2>
 *
 * <p>Las tres cosas que este issue tiene que demostrar las sostiene el motor y no Java:
 *
 * <ul>
 *   <li><b>Que el marco filtra y el operador espacial refina</b> (ADR-0034 regla 2). Un doble
 *       devolveria lo que se le programe; lo que hay que medir es que dos poligonos cuyas CAJAS se
 *       cruzan y cuyos bordes no se tocan <b>no</b> salen como intersectados. Esa distincion no
 *       existe fuera de PostGIS.
 *   <li><b>Que un ITSE vencido no se devuelve como vigente.</b> El filtro esta en el {@code WHERE},
 *       asi que probarlo contra un doble probaria el doble.
 *   <li><b>El aislamiento entre municipalidades</b>, que lo sostiene la politica RLS.
 * </ul>
 *
 * <p>La conexion es la de {@code kamayuk_app}. Un superusuario omite RLS incluso con {@code FORCE
 * ROW LEVEL SECURITY} (primer hallazgo de RLS), asi que una prueba escrita sobre el no verificaria
 * ningun aislamiento.
 */
@DisplayName("#5 — El riesgo y el ITSE del predio, de HTTP a PostgreSQL")
class GestionDeRiesgoFronteraTest {

    /** Hoy, para las lecturas que no reciben fecha. Fijo, para no depender del dia de ejecucion. */
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    /**
     * El lote: un cuadrado de unos 550 m de lado al oeste de Catacaos.
     *
     * <p>Las coordenadas importan y por eso estan aqui arriba: las zonas de abajo se construyen
     * <b>en relacion a este cuadrado</b>, y una de ellas —la que da nombre al refinado— toca su
     * caja envolvente sin tocarlo a el.
     */
    private static final String LOTE =
            "MULTIPOLYGON(((-80.6900 -5.2700,-80.6850 -5.2700,-80.6850 -5.2660,"
                    + "-80.6900 -5.2660,-80.6900 -5.2700)))";

    private static BaseDeDatosDePrueba base;
    private static long municipalidadA;
    private static long municipalidadB;
    private static long predioConLote;
    private static long predioSinLote;
    private static long predioDeLaVecina;
    private static MockMvc mvc;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidadA = crearMunicipalidad("240301", "Municipalidad que consulta");
        municipalidadB = crearMunicipalidad("240302", "Municipalidad vecina");

        predioConLote = crearPredio(municipalidadA, "24030100010001000100001", LOTE);
        predioSinLote = crearPredio(municipalidadA, "24030100010001000100002", null);
        predioDeLaVecina = crearPredio(municipalidadB, "24030200010001000100001", LOTE);

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        JdbcClient jdbc = JdbcClient.create(pool);
        TenantTransactionManager gestor = new TenantTransactionManager(pool);
        GestionDeRiesgoRepositoryJdbc repositorio = new GestionDeRiesgoRepositoryJdbc(jdbc);
        AuditoriaJdbc auditoria = new AuditoriaJdbc(jdbc, RELOJ);

        mvc =
                MockMvcBuilders.standaloneSetup(
                                new GrdController(
                                        envolver(new ConsultaDeRiesgo(repositorio), gestor),
                                        envolver(new ConsultaDeItse(repositorio), gestor),
                                        envolver(
                                                new RegistrarCertificadoItse(
                                                        repositorio, auditoria, RELOJ),
                                                gestor),
                                        RELOJ))
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
        OrigenContext.fijar(new Origen("tecnico.grd", "PC-GRD-01", "10.0.0.7"));
    }

    /**
     * El escenario se deja como estaba <b>despues de cada prueba</b>, no dentro de cada una.
     *
     * <p>Dentro de la prueba solo se limpia lo que la prueba consiguio llegar a limpiar: la primera
     * que falla deja sus filas puestas y la siguiente mide sobre un escenario que no monto. Costo
     * cuatro rojos por arrastre antes de escribirlo asi.
     */
    @AfterEach
    void limpiar() throws SQLException {
        for (long municipalidad : new long[] {municipalidadA, municipalidadB}) {
            borrarComoOwner("zona_riesgo", municipalidad);
            borrarComoOwner("faja_marginal", municipalidad);
            borrarComoOwner("itse", municipalidad);
        }
        TenantContext.limpiar();
        OrigenContext.limpiar();
    }

    // ── AC-3: las zonas que cruzan el lote ─────────────────────────────

    @Test
    @DisplayName("una zona que cubre el lote sale, con su nivel y con si es mitigable")
    void laZonaQueCubreElLoteSale() throws Exception {
        zonaDeRiesgo(
                municipalidadA,
                "ZR-CUBRE",
                "INUNDACION",
                "MUY_ALTO",
                false,
                "MULTIPOLYGON(((-80.7000 -5.2800,-80.6800 -5.2800,-80.6800 -5.2600,"
                        + "-80.7000 -5.2600,-80.7000 -5.2800)))");

        String cuerpo = riesgoDe(predioConLote, 200);

        assertThat(cuerpo)
                .contains("\"codigo\":\"ZR-CUBRE\"")
                .contains("\"nivel\":\"MUY_ALTO\"")
                .as("el dato que decide es «mitigable», no el nivel")
                .contains("\"mitigable\":false")
                .contains("\"hayRiesgoNoMitigable\":true");
        assertThat(cuerpo)
                .as("regla 9: la respuesta dice a que fecha esta, porque una carta caduca")
                .contains("\"aLaFecha\":\"2026-06-15\"");
    }

    @Test
    @DisplayName("una zona lejos del lote no sale: la filtra el marco antes del operador espacial")
    void laZonaLejosNoSale() throws Exception {
        zonaDeRiesgo(
                municipalidadA,
                "ZR-LEJOS",
                "SISMO",
                "ALTO",
                false,
                "MULTIPOLYGON(((-80.5000 -5.1000,-80.4900 -5.1000,-80.4900 -5.0900,"
                        + "-80.5000 -5.0900,-80.5000 -5.1000)))");

        assertThat(riesgoDe(predioConLote, 200))
                .doesNotContain("ZR-LEJOS")
                .contains("\"zonas\":[]")
                .contains("\"hayRiesgoNoMitigable\":false");
    }

    /**
     * EL REFINADO EXACTO, que es lo unico que el marco solo no puede dar.
     *
     * <p>La zona es un triangulo cuya <b>caja envolvente se solapa</b> con la del lote —asi que las
     * cuatro desigualdades del marco la dejan pasar— y cuyo interior queda <b>fuera</b> del
     * cuadrado. Con el marco solo, esta zona saldria: y decir que un lote esta en una zona MUY ALTO
     * porque sus rectangulos se cruzan es el falso positivo que acaba negando una licencia que
     * procede.
     *
     * <p>La prueba afirma las dos mitades a proposito: que el marco la deja pasar y que la
     * respuesta no la trae. Sin la primera, quitar el {@code ST_Intersects} seguiria en verde
     * porque la fila podria estar siendo descartada por el marco.
     */
    @Test
    @DisplayName("una zona cuya CAJA se solapa y cuyo poligono no toca el lote no sale (refinado)")
    void laZonaQueSoloSolapaLaCajaNoSale() throws Exception {
        zonaDeRiesgo(
                municipalidadA,
                "ZR-SOLO-CAJA",
                "HUAICO",
                "MUY_ALTO",
                false,
                "MULTIPOLYGON(((-80.6870 -5.2620,-80.6820 -5.2620,-80.6820 -5.2680,"
                        + "-80.6870 -5.2620)))");

        assertThat(marcoDeLaZonaSolapaElDelLote("ZR-SOLO-CAJA"))
                .as(
                        "si el marco ya la descartara, quitar el ST_Intersects seguiria en verde y"
                                + " esta prueba no mediria el refinado sino el marco")
                .isTrue();
        assertThat(riesgoDe(predioConLote, 200))
                .as("las cajas se cruzan y los poligonos no: el refinado exacto la deja fuera")
                .doesNotContain("ZR-SOLO-CAJA");
    }

    @Test
    @DisplayName("una zona cuya vigencia ya termino no sale")
    void laZonaVencidaNoSale() throws Exception {
        zonaVencida(
                municipalidadA,
                "ZR-VIEJA",
                "MULTIPOLYGON(((-80.7000 -5.2800,-80.6800 -5.2800,-80.6800 -5.2600,"
                        + "-80.7000 -5.2600,-80.7000 -5.2800)))");

        assertThat(riesgoDe(predioConLote, 200))
                .as("una carta sustituida no se borra: se cierra, y deja de contestar")
                .doesNotContain("ZR-VIEJA");
    }

    // ── #18: el riesgo A UNA FECHA ─────────────────────────────────────

    /** El poligono que cubre el lote entero. Todas las zonas de #18 lo usan. */
    private static final String CUBRE_EL_LOTE =
            "MULTIPOLYGON(((-80.7000 -5.2800,-80.6800 -5.2800,-80.6800 -5.2600,"
                    + "-80.7000 -5.2600,-80.7000 -5.2800)))";

    @Test
    @DisplayName(
            "#18 AC-1 — la zona cerrada en 2024 sale al preguntar por 2024, y no al no pedirlo")
    void laZonaCerradaEn2024SaleAlPreguntarPor2024() throws Exception {
        zonaVigenteEntre(
                municipalidadA,
                "ZR-2024",
                "MUY_ALTO",
                false,
                "2019-01-01",
                "2024-12-31",
                CUBRE_EL_LOTE);

        assertThat(riesgoDe(predioConLote, "2024-06-15", 200))
                .as(
                        "quien evalua hoy una licencia denegada en 2024 necesita saber que decia la"
                                + " carta ENTONCES, y con la ruta anterior esa pregunta no se podia"
                                + " hacer")
                .contains("\"codigo\":\"ZR-2024\"")
                .contains("\"aLaFecha\":\"2024-06-15\"");

        assertThat(riesgoDe(predioConLote, 200))
                .as("y sin fecha sigue contestando HOY, del reloj inyectado y no de LocalDate.now")
                .doesNotContain("ZR-2024")
                .contains("\"aLaFecha\":\"2026-06-15\"");
    }

    @Test
    @DisplayName("#18 AC-2 — vigencia_hasta es INCLUSIVA: el ultimo dia sale y el siguiente no")
    void laVigenciaHastaEsInclusiva() throws Exception {
        zonaVigenteEntre(
                municipalidadA,
                "ZR-CIERRA",
                "ALTO",
                true,
                "2019-01-01",
                "2024-12-31",
                CUBRE_EL_LOTE);

        assertThat(riesgoDe(predioConLote, "2024-12-31", 200))
                .as(
                        "el ultimo dia de vigencia SI cuenta: es el borde, y aqui se decide en cual cae")
                .contains("ZR-CIERRA");
        assertThat(riesgoDe(predioConLote, "2025-01-01", 200))
                .as("y el dia siguiente ya no. Sin el extremo superior, este par no se distingue")
                .doesNotContain("ZR-CIERRA");
    }

    @Test
    @DisplayName("#18 AC-2 — y por el otro extremo: una zona que abre manana no sale hoy")
    void laZonaQueAbreDespuesNoSaleAntes() throws Exception {
        zonaVigenteEntre(
                municipalidadA,
                "ZR-FUTURA",
                "MUY_ALTO",
                false,
                "2026-06-16",
                "2030-12-31",
                CUBRE_EL_LOTE);

        assertThat(riesgoDe(predioConLote, "2026-06-15", 200))
                .as(
                        "una carta aprobada y con vigencia futura no puede decidir una licencia de"
                                + " hoy: publicarla seria negar por una norma que aun no rige")
                .doesNotContain("ZR-FUTURA");
        assertThat(riesgoDe(predioConLote, "2026-06-16", 200))
                .as("EL CONTRASTE: el primer dia de su vigencia si")
                .contains("ZR-FUTURA");
    }

    @Test
    @DisplayName("#18 AC-2 — la faja marginal acota por fecha IGUAL que la zona, en las dos puntas")
    void laFajaMarginalTambienAcotaPorFecha() throws Exception {
        fajaVigenteEntre(
                municipalidadA,
                "FM-DEROGADA",
                "2019-01-01",
                "2024-12-31",
                "MULTIPOLYGON(((-80.6890 -5.2690,-80.6860 -5.2690,-80.6860 -5.2670,"
                        + "-80.6890 -5.2670,-80.6890 -5.2690)))");

        assertThat(riesgoDe(predioConLote, "2024-12-31", 200))
                .as("una resolucion de la ANA se deroga, y hasta ese dia rige")
                .contains("FM-DEROGADA");
        assertThat(riesgoDe(predioConLote, "2025-01-01", 200))
                .as(
                        "y despues no. La faja tiene sus dos fechas igual que la zona, y una mitad"
                                + " de la respuesta que no acotara seria la mitad que se lee mal")
                .doesNotContain("FM-DEROGADA")
                .contains("\"fajasMarginales\":[]");
    }

    @Test
    @DisplayName("#18 AC-4 — hayRiesgoNoMitigable se recalcula a la fecha, y a veces se invierte")
    void elRiesgoNoMitigableSeRecalculaALaFecha() throws Exception {
        // El escenario que separa las dos respuestas: la carta de 2019 declaraba el lote MUY_ALTO
        // NO mitigable; la de 2025 la sustituye y lo declara ALTO mitigable, porque entretanto se
        // construyo la defensa riberena. El suelo es el mismo y la respuesta es la contraria.
        zonaVigenteEntre(
                municipalidadA,
                "ZR-VIEJA-NM",
                "MUY_ALTO",
                false,
                "2019-01-01",
                "2024-12-31",
                CUBRE_EL_LOTE);
        zonaVigenteEntre(
                municipalidadA,
                "ZR-NUEVA-M",
                "ALTO",
                true,
                "2025-01-01",
                "2030-12-31",
                CUBRE_EL_LOTE);

        assertThat(riesgoDe(predioConLote, 200))
                .as("hoy rige la nueva: hay riesgo, y se puede mitigar")
                .contains("ZR-NUEVA-M")
                .contains("\"hayRiesgoNoMitigable\":false");

        assertThat(riesgoDe(predioConLote, "2024-06-15", 200))
                .as(
                        "y en 2024 regia la vieja: el dato que DECIDE es el contrario. Calculado"
                                + " sobre las zonas de hoy, esta respuesta seria plausible y"
                                + " estaria mal —el defecto que #5 encontro en el ITSE—")
                .contains("ZR-VIEJA-NM")
                .contains("\"hayRiesgoNoMitigable\":true");
    }

    @Test
    @DisplayName("#18 AC-1 — una fecha ilegible en el riesgo es 422 y no «hoy» en silencio")
    void laFechaIlegibleDelRiesgoEs422() throws Exception {
        String problema = riesgoDe(predioConLote, "30-02-2026", 422);

        assertThat(problema)
                .as(
                        "quien pidio el 30 de febrero recibiria la respuesta de hoy creyendo que es"
                                + " la de febrero, y eso es peor que un error")
                .contains("aLaFecha")
                .contains("AAAA-MM-DD");
    }

    @Test
    @DisplayName("la faja marginal que cruza el lote sale con su ancho, y en su propia lista")
    void laFajaMarginalSale() throws Exception {
        fajaMarginal(
                municipalidadA,
                "FM-CRUZA",
                "Rio Piura",
                "25.00",
                "MULTIPOLYGON(((-80.6890 -5.2690,-80.6860 -5.2690,-80.6860 -5.2670,"
                        + "-80.6890 -5.2670,-80.6890 -5.2690)))");

        assertThat(riesgoDe(predioConLote, 200))
                .contains("\"codigo\":\"FM-CRUZA\"")
                .contains("\"cuerpoDeAgua\":\"Rio Piura\"")
                .as(
                        "el ancho sale como cadena: lo fija una resolucion y redondearlo mueve un"
                                + " lindero")
                .contains("\"anchoM\":\"25.00\"")
                .as("no se mezcla con las zonas: la ANA no declara un nivel")
                .contains("\"zonas\":[]");
    }

    // ── AC-5: sin poligono no hay respuesta ────────────────────────────

    @Test
    @DisplayName("un predio SIN geometria es 422, no 200 con cero zonas")
    void sinGeometriaEs422() throws Exception {
        MvcResult respuesta =
                mvc.perform(
                                get("/catastro/api/v1/grd/riesgo")
                                        .param("predioId", String.valueOf(predioSinLote)))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus())
                .as("«cero zonas» sobre un lote sin levantar se lee como «no cae en ninguna»")
                .isEqualTo(422);
        assertThat(respuesta.getResponse().getContentAsString())
                .contains("no tiene poligono levantado");
    }

    @Test
    @DisplayName("un predio que no es de esta municipalidad es 404, no 422")
    void elPredioAjenoEs404() throws Exception {
        MvcResult respuesta =
                mvc.perform(
                                get("/catastro/api/v1/grd/riesgo")
                                        .param("predioId", String.valueOf(predioDeLaVecina)))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus())
                .as(
                        "bajo RLS el predio de la vecina no es «prohibido»: no existe. Y se"
                                + " distingue del 422 porque se arreglan distinto —uno revisando el"
                                + " identificador y otro cargando el plano—")
                .isEqualTo(404);
    }

    // ── AC-4: el ITSE vigente a una fecha ──────────────────────────────

    @Test
    @DisplayName("un ITSE vencido NO se devuelve como vigente, y el mismo dato a su fecha si")
    void elItseVencidoNoSaleComoVigente() throws Exception {
        certificado(municipalidadA, predioConLote, "ITSE-2024-001", "2024-01-10", "2025-01-09");

        assertThat(itseDe(predioConLote, null))
                .as("vencio el 9 de enero de 2025 y hoy es el 15 de junio de 2026")
                .contains("\"vigentes\":[]")
                .contains("\"aLaFecha\":\"2026-06-15\"");

        assertThat(itseDe(predioConLote, "2024-06-01"))
                .as("y el mismo certificado, preguntado a una fecha en la que regia, si sale")
                .contains("\"numero\":\"ITSE-2024-001\"")
                .contains("\"aLaFecha\":\"2024-06-01\"");

        assertThat(itseDe(predioConLote, "2025-01-09"))
                .as("el ultimo dia de vigencia entra: los dos extremos son inclusivos")
                .contains("\"numero\":\"ITSE-2024-001\"");
        assertThat(itseDe(predioConLote, "2025-01-10"))
                .as("y el siguiente ya no")
                .contains("\"vigentes\":[]");
    }

    @Test
    @DisplayName("un ITSE anulado deja de salir desde su fecha, y sigue saliendo antes de ella")
    void elItseAnuladoDejaDeSalirDesdeSuFecha() throws Exception {
        certificado(municipalidadA, predioConLote, "ITSE-2026-009", "2026-01-01", "2026-12-31");
        anular(
                municipalidadA,
                "ITSE-2026-009",
                "2026-05-01",
                "Se detecto observacion no levantada");

        assertThat(itseDe(predioConLote, "2026-03-01"))
                .as(
                        "una licencia emitida en marzo se emitio con un certificado que en marzo"
                                + " estaba vigente: preguntar hoy por marzo tiene que dar la"
                                + " respuesta de marzo (regla 9)")
                .contains("\"numero\":\"ITSE-2026-009\"");
        assertThat(itseDe(predioConLote, "2026-06-15"))
                .as("y desde la anulacion, no")
                .contains("\"vigentes\":[]");
    }

    @Test
    @DisplayName("un predio sin ningun ITSE es 200 con lista vacia, aunque no tenga poligono")
    void sinItseEs200ConListaVacia() throws Exception {
        assertThat(itseDe(predioSinLote, null))
                .as("un certificado cuelga del predio y no de su plano: aqui no hay 422")
                .contains("\"vigentes\":[]")
                .contains("\"predioId\":" + predioSinLote);
    }

    @Test
    @DisplayName("una fecha ilegible es 422 y no «hoy» en silencio")
    void laFechaIlegibleEs422() throws Exception {
        MvcResult respuesta =
                mvc.perform(
                                get("/catastro/api/v1/grd/itse")
                                        .param("predioId", String.valueOf(predioConLote))
                                        .param("aLaFecha", "2026-02-30"))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus())
                .as("quien pidio febrero recibiria la respuesta de hoy creyendo que es la suya")
                .isEqualTo(422);
    }

    // ── El alta, con su auditoria en la misma transaccion ──────────────

    @Test
    @DisplayName("el alta de un ITSE deja su fila de auditoria con la observacion del usuario")
    void elAltaDejaSuAuditoria() throws Exception {
        // La auditoria NO se limpia entre pruebas —es una tabla protegida y particionada, y
        // vaciarla desde aqui seria ensenar a hacerlo—: se mide el incremento.
        long auditoriaAntes = filasDeAuditoriaSobre(municipalidadA, "itse");

        MvcResult creado =
                mvc.perform(
                                post("/catastro/api/v1/grd/itse")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"predioId": %d,
                                                 "numero": "ITSE-2026-100",
                                                 "nivelRiesgo": "ALTO",
                                                 "modalidad": "PREVIA",
                                                 "vigenciaDesde": "2026-01-01",
                                                 "vigenciaHasta": "2026-12-31",
                                                 "observacion": "Inspeccion del 2026-01-02, acta 44"}
                                                """
                                                        .formatted(predioConLote)))
                        .andReturn();

        assertThat(creado.getResponse().getStatus())
                .as("cuerpo: %s", creado.getResponse().getContentAsString())
                .isEqualTo(201);
        assertThat(filasDeAuditoriaSobre(municipalidadA, "itse") - auditoriaAntes)
                .as("regla 10: la observacion del usuario viaja a la auditoria, no una fija")
                .isEqualTo(1);
        assertThat(itseDe(predioConLote, "2026-06-15")).contains("\"numero\":\"ITSE-2026-100\"");
    }

    @Test
    @DisplayName("un alta sin observacion es 422 y no escribe nada (regla 10)")
    void sinObservacionEs422() throws Exception {
        MvcResult respuesta =
                mvc.perform(
                                post("/catastro/api/v1/grd/itse")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"predioId": %d,
                                                 "numero": "ITSE-SIN-OBS",
                                                 "nivelRiesgo": "BAJO",
                                                 "modalidad": "POSTERIOR",
                                                 "vigenciaDesde": "2026-01-01",
                                                 "vigenciaHasta": "2026-12-31"}
                                                """
                                                        .formatted(predioConLote)))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
        assertThat(certificadosDe(municipalidadA)).isZero();
    }

    // ── El aislamiento, que lo sostiene RLS y no un WHERE ──────────────

    @Test
    @DisplayName("la zona de la municipalidad vecina no cruza el lote de esta")
    void laZonaDeLaVecinaNoSeVe() throws Exception {
        zonaDeRiesgo(
                municipalidadB,
                "ZR-VECINA",
                "INUNDACION",
                "MUY_ALTO",
                false,
                "MULTIPOLYGON(((-80.7000 -5.2800,-80.6800 -5.2800,-80.6800 -5.2600,"
                        + "-80.7000 -5.2600,-80.7000 -5.2800)))");

        assertThat(riesgoDe(predioConLote, 200))
                .as(
                        "el poligono es el MISMO que cubre el lote, y aun asi no sale: lo que lo"
                                + " impide no es ningun WHERE, es la politica")
                .doesNotContain("ZR-VECINA")
                .contains("\"zonas\":[]");
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private static String riesgoDe(long predioId, int estadoEsperado) throws Exception {
        return riesgoDe(predioId, null, estadoEsperado);
    }

    /** El riesgo a una fecha; con {@code aLaFecha} nulo, sin mandar el parametro (#18). */
    private static String riesgoDe(long predioId, String aLaFecha, int estadoEsperado)
            throws Exception {
        var peticion =
                get("/catastro/api/v1/grd/riesgo").param("predioId", String.valueOf(predioId));
        if (aLaFecha != null) {
            peticion = peticion.param("aLaFecha", aLaFecha);
        }
        MvcResult respuesta = mvc.perform(peticion).andReturn();
        assertThat(respuesta.getResponse().getStatus())
                .as("cuerpo: %s", respuesta.getResponse().getContentAsString())
                .isEqualTo(estadoEsperado);
        return respuesta.getResponse().getContentAsString();
    }

    private static String itseDe(long predioId, String aLaFecha) throws Exception {
        var peticion = get("/catastro/api/v1/grd/itse").param("predioId", String.valueOf(predioId));
        if (aLaFecha != null) {
            peticion = peticion.param("aLaFecha", aLaFecha);
        }
        MvcResult respuesta = mvc.perform(peticion).andReturn();
        assertThat(respuesta.getResponse().getStatus())
                .as("cuerpo: %s", respuesta.getResponse().getContentAsString())
                .isEqualTo(200);
        return respuesta.getResponse().getContentAsString();
    }

    /**
     * Si el marco de la zona y el del lote se solapan, preguntado con las MISMAS cuatro
     * desigualdades que el repositorio usa.
     *
     * <p>Se pregunta como superusuario para que la respuesta sea sobre la geometria y no sobre la
     * politica: aqui lo que se mide es el rectangulo, no el aislamiento.
     */
    private static boolean marcoDeLaZonaSolapaElDelLote(String codigo) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadA);
            try (PreparedStatement sentencia =
                    app.prepareStatement(
                            "SELECT z.marco_oeste <= p.marco_este"
                                    + "   AND z.marco_sur   <= p.marco_norte"
                                    + "   AND z.marco_este  >= p.marco_oeste"
                                    + "   AND z.marco_norte >= p.marco_sur"
                                    + " FROM zona_riesgo z, predio p"
                                    + " WHERE z.codigo = ? AND p.id = ?")) {
                sentencia.setString(1, codigo);
                sentencia.setLong(2, predioConLote);
                try (ResultSet fila = sentencia.executeQuery()) {
                    fila.next();
                    return fila.getBoolean(1);
                }
            }
        }
    }

    private static void zonaDeRiesgo(
            long municipalidadId,
            String codigo,
            String fenomeno,
            String nivel,
            boolean mitigable,
            String wkt)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (PreparedStatement sentencia =
                    app.prepareStatement(
                            "INSERT INTO zona_riesgo (municipalidad_id, codigo, fenomeno, nivel,"
                                    + " mitigable, fuente, documento_origen, vigencia_desde,"
                                    + " geometria, observacion, usuario_registro)"
                                    + " VALUES (?, ?, ?, ?, ?, 'CENEPRED', 'CARTA-2025',"
                                    + "         DATE '2025-01-01', ST_GeogFromText(?),"
                                    + "         'Siembra de la prueba', 'prueba')")) {
                sentencia.setLong(1, municipalidadId);
                sentencia.setString(2, codigo);
                sentencia.setString(3, fenomeno);
                sentencia.setString(4, nivel);
                sentencia.setBoolean(5, mitigable);
                sentencia.setString(6, wkt);
                sentencia.executeUpdate();
            }
            app.commit();
        }
    }

    private static void zonaVencida(long municipalidadId, String codigo, String wkt)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (PreparedStatement sentencia =
                    app.prepareStatement(
                            "INSERT INTO zona_riesgo (municipalidad_id, codigo, fenomeno, nivel,"
                                    + " mitigable, fuente, documento_origen, vigencia_desde,"
                                    + " vigencia_hasta, geometria, observacion, usuario_registro)"
                                    + " VALUES (?, ?, 'INUNDACION', 'MUY_ALTO', false, 'CENEPRED',"
                                    + "         'CARTA-2019', DATE '2019-01-01',"
                                    + "         DATE '2024-12-31', ST_GeogFromText(?),"
                                    + "         'Siembra de la prueba', 'prueba')")) {
                sentencia.setLong(1, municipalidadId);
                sentencia.setString(2, codigo);
                sentencia.setString(3, wkt);
                sentencia.executeUpdate();
            }
            app.commit();
        }
    }

    /**
     * Una zona con sus DOS fechas puestas, para poder preguntar por un dia y no por otro (#18).
     *
     * <p>{@code zonaVencida} sirve para «esto ya no vale hoy» y no para lo que #18 mide, que es que
     * el mismo suelo conteste una cosa a una fecha y otra a otra.
     */
    private static void zonaVigenteEntre(
            long municipalidadId,
            String codigo,
            String nivel,
            boolean mitigable,
            String desde,
            String hasta,
            String wkt)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (PreparedStatement sentencia =
                    app.prepareStatement(
                            "INSERT INTO zona_riesgo (municipalidad_id, codigo, fenomeno, nivel,"
                                    + " mitigable, fuente, documento_origen, vigencia_desde,"
                                    + " vigencia_hasta, geometria, observacion, usuario_registro)"
                                    + " VALUES (?, ?, 'INUNDACION', ?, ?, 'CENEPRED',"
                                    + "         'CARTA-DE-PRUEBA', CAST(? AS date),"
                                    + "         CAST(? AS date), ST_GeogFromText(?),"
                                    + "         'Siembra de la prueba', 'prueba')")) {
                sentencia.setLong(1, municipalidadId);
                sentencia.setString(2, codigo);
                sentencia.setString(3, nivel);
                sentencia.setBoolean(4, mitigable);
                sentencia.setString(5, desde);
                sentencia.setString(6, hasta);
                sentencia.setString(7, wkt);
                sentencia.executeUpdate();
            }
            app.commit();
        }
    }

    /** Una faja marginal con sus dos fechas: la otra mitad de AC-2, que tambien las tiene. */
    private static void fajaVigenteEntre(
            long municipalidadId, String codigo, String desde, String hasta, String wkt)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (PreparedStatement sentencia =
                    app.prepareStatement(
                            "INSERT INTO faja_marginal (municipalidad_id, codigo, cuerpo_agua,"
                                    + " ancho_m, fuente, documento_origen, vigencia_desde,"
                                    + " vigencia_hasta, geometria, observacion, usuario_registro)"
                                    + " VALUES (?, ?, 'Rio Piura', 25.00, 'ANA', 'RD-DE-PRUEBA',"
                                    + "         CAST(? AS date), CAST(? AS date),"
                                    + "         ST_GeogFromText(?), 'Siembra de la prueba',"
                                    + "         'prueba')")) {
                sentencia.setLong(1, municipalidadId);
                sentencia.setString(2, codigo);
                sentencia.setString(3, desde);
                sentencia.setString(4, hasta);
                sentencia.setString(5, wkt);
                sentencia.executeUpdate();
            }
            app.commit();
        }
    }

    private static void fajaMarginal(
            long municipalidadId, String codigo, String cuerpo, String ancho, String wkt)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (PreparedStatement sentencia =
                    app.prepareStatement(
                            "INSERT INTO faja_marginal (municipalidad_id, codigo, cuerpo_agua,"
                                    + " ancho_m, fuente, documento_origen, vigencia_desde,"
                                    + " geometria, observacion, usuario_registro)"
                                    + " VALUES (?, ?, ?, CAST(? AS numeric), 'ANA', 'RD-2023',"
                                    + "         DATE '2023-01-01', ST_GeogFromText(?),"
                                    + "         'Siembra de la prueba', 'prueba')")) {
                sentencia.setLong(1, municipalidadId);
                sentencia.setString(2, codigo);
                sentencia.setString(3, cuerpo);
                sentencia.setString(4, ancho);
                sentencia.setString(5, wkt);
                sentencia.executeUpdate();
            }
            app.commit();
        }
    }

    private static void certificado(
            long municipalidadId, long predioId, String numero, String desde, String hasta)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (PreparedStatement sentencia =
                    app.prepareStatement(
                            "INSERT INTO itse (municipalidad_id, predio_id, numero, nivel_riesgo,"
                                    + " modalidad, vigencia_desde, vigencia_hasta, observacion,"
                                    + " usuario_registro)"
                                    + " VALUES (?, ?, ?, 'ALTO', 'PREVIA', CAST(? AS date),"
                                    + "         CAST(? AS date), 'Siembra de la prueba',"
                                    + "         'prueba')")) {
                sentencia.setLong(1, municipalidadId);
                sentencia.setLong(2, predioId);
                sentencia.setString(3, numero);
                sentencia.setString(4, desde);
                sentencia.setString(5, hasta);
                sentencia.executeUpdate();
            }
            app.commit();
        }
    }

    private static void anular(long municipalidadId, String numero, String fecha, String motivo)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (PreparedStatement sentencia =
                    app.prepareStatement(
                            "UPDATE itse SET fecha_anulacion = CAST(? AS date),"
                                    + " motivo_anulacion = ? WHERE numero = ?")) {
                sentencia.setString(1, fecha);
                sentencia.setString(2, motivo);
                sentencia.setString(3, numero);
                sentencia.executeUpdate();
            }
            app.commit();
        }
    }

    /**
     * Deja la tabla como estaba entre pruebas.
     *
     * <p>Se borra <b>desde la prueba y como propietario</b>, no desde la aplicacion: {@code
     * kamayuk_app} no tiene el privilegio de {@code DELETE} sobre ninguna de las tres (RNF-051), y
     * eso es justamente lo que se quiere. Limpiar el escenario de una prueba no es una operacion
     * del sistema.
     */
    private static void borrarComoOwner(String tabla, long municipalidadId) throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER)) {
            // El propietario TAMBIEN queda sujeto a la politica: las tres tablas llevan `FORCE ROW
            // LEVEL SECURITY`, asi que sin contexto esto no borra de menos — falla con
            // «unrecognized configuration parameter». Es el primer hallazgo de RLS por su otra
            // cara, y costo dos rojos antes de escribirlo.
            ContextoDeTenant.fijar(owner, municipalidadId);
            try (PreparedStatement sentencia =
                    owner.prepareStatement(
                            "DELETE FROM " + tabla + " WHERE municipalidad_id = ?")) {
                sentencia.setLong(1, municipalidadId);
                sentencia.executeUpdate();
            }
            owner.commit();
        }
    }

    private static long certificadosDe(long municipalidadId) throws SQLException {
        return contar("SELECT count(*) FROM itse", municipalidadId);
    }

    private static long filasDeAuditoriaSobre(long municipalidadId, String tabla)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (PreparedStatement sentencia =
                    app.prepareStatement("SELECT count(*) FROM auditoria WHERE tabla = ?")) {
                sentencia.setString(1, tabla);
                try (ResultSet fila = sentencia.executeQuery()) {
                    fila.next();
                    return fila.getLong(1);
                }
            }
        }
    }

    private static long contar(String sql, long municipalidadId) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (PreparedStatement sentencia = app.prepareStatement(sql);
                    ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }

    private static long crearMunicipalidad(String ubigeo, String nombre) throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER);
                PreparedStatement sentencia =
                        owner.prepareStatement(
                                "INSERT INTO municipalidad (ubigeo, nombre, tipo)"
                                        + " VALUES (?, ?, 'DISTRITAL') RETURNING id")) {
            sentencia.setString(1, ubigeo);
            sentencia.setString(2, nombre);
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                long id = fila.getLong(1);
                owner.commit();
                return id;
            }
        }
    }

    private static long crearPredio(long municipalidadId, String codigo, String wkt)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (PreparedStatement sentencia =
                    app.prepareStatement(
                            "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo,"
                                    + " direccion, estado, geometria)"
                                    + " VALUES (?, ?, 'URBANO', 'AV. DE PRUEBA 100', 'ACTIVO',"
                                    + "         CASE WHEN ? IS NULL THEN NULL"
                                    + "              ELSE ST_GeogFromText(?) END)"
                                    + " RETURNING id")) {
                sentencia.setLong(1, municipalidadId);
                sentencia.setString(2, codigo);
                sentencia.setString(3, wkt);
                sentencia.setString(4, wkt);
                try (ResultSet fila = sentencia.executeQuery()) {
                    fila.next();
                    long id = fila.getLong(1);
                    app.commit();
                    return id;
                }
            }
        }
    }

    /**
     * El proxy que obedece a la anotacion, como el contenedor.
     *
     * <p>Es lo que convierte esta prueba en una medida y no en un montaje: quitarle el
     * {@code @Transactional} a un caso de uso deja al proxy sin nada que hacer, y la lectura se cae
     * con el error de RLS de verdad —«unrecognized configuration parameter»—. Un {@code
     * TransactionTemplate} incondicional la habria dejado pasando con la anotacion quitada.
     */
    @SuppressWarnings("unchecked")
    private static <T> T envolver(T objetivo, TenantTransactionManager gestor) {
        ProxyFactory fabrica = new ProxyFactory(objetivo);
        fabrica.setProxyTargetClass(true);
        fabrica.addAdvice(
                new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        return (T) fabrica.getProxy();
    }
}
