package kamayuk.catastro.urbano.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import kamayuk.catastro.urbano.aplicacion.ConsultaDeZonificacion;
import kamayuk.catastro.urbano.infraestructura.web.ZonificacionController;
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
 * La zona de un predio, de HTTP a PostgreSQL con PostGIS y sin un doble por el camino (#4).
 *
 * <h2>Por que va hasta la base</h2>
 *
 * <p>Porque lo que este issue tiene que demostrar no se puede demostrar de otro modo. La
 * <b>contencion</b> la decide {@code ST_Contains} sobre un poligono de verdad, no un {@code if} de
 * Java. El <b>filtro por marco</b> es una afirmacion sobre el PLAN de ejecucion, y un plan solo lo
 * tiene un motor. Y el <b>aislamiento</b> lo sostiene la politica RLS, que un doble no tiene.
 *
 * <p>La conexion es la de {@code kamayuk_app}. Un superusuario omite RLS incluso con {@code FORCE
 * ROW LEVEL SECURITY} —primer hallazgo de RLS—, asi que una prueba escrita sobre el no verificaria
 * ningun aislamiento; y ademas es <b>el unico rol para el que el defecto de ADR-0034 existe</b>: el
 * operador espacial no es <i>leakproof</i> y no se promueve por encima de la politica solo cuando
 * hay politica que atravesar.
 */
@DisplayName("#4 — La zona de un predio, de HTTP a PostgreSQL con PostGIS")
class ZonificacionFronteraTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    private static final String RUTA = "/catastro/api/v1/urbano/zonificacion";

    /**
     * Dos zonas VECINAS, que comparten la arista de longitud -80.60.
     *
     * <p>Vecinas a proposito: sus cajas envolventes se tocan, asi que el filtro por marco devuelve
     * las dos y es el {@code ST_Contains} el que decide. Con una sola zona, quitar el refinado
     * exacto pasaria en verde y esta prueba no diria nada.
     */
    private static final String ZONA_OESTE =
            "MULTIPOLYGON(((-80.70 -5.30,-80.60 -5.30,-80.60 -5.20,-80.70 -5.20,-80.70 -5.30)))";

    private static final String ZONA_ESTE =
            "MULTIPOLYGON(((-80.60 -5.30,-80.50 -5.30,-80.50 -5.20,-80.60 -5.20,-80.60 -5.30)))";

    /** Un lote pequeno, dentro de la zona OESTE y lejos de la arista. */
    private static final String LOTE_AL_OESTE =
            "MULTIPOLYGON(((-80.68 -5.28,-80.679 -5.28,-80.679 -5.279,-80.68 -5.279,"
                    + "-80.68 -5.28)))";

    /** Y otro dentro de la zona ESTE, para que la respuesta pueda ser la otra. */
    private static final String LOTE_AL_ESTE =
            "MULTIPOLYGON(((-80.52 -5.28,-80.519 -5.28,-80.519 -5.279,-80.52 -5.279,"
                    + "-80.52 -5.28)))";

    /** Fuera de las dos zonas: el predio tiene poligono y ningun plan lo cubre. */
    private static final String LOTE_FUERA =
            "MULTIPOLYGON(((-79.10 -5.28,-79.099 -5.28,-79.099 -5.279,-79.10 -5.279,"
                    + "-79.10 -5.28)))";

    /**
     * Cuantas zonas de relleno se siembran por municipalidad.
     *
     * <p>La cifra no es una preferencia: con dos zonas el planificador invierte el bucle y las
     * comparaciones del marco caen en el {@code Join Filter} en vez de en el {@code Index Cond}.
     * Con este volumen el plan es el de produccion, y es el que esta prueba tiene que medir.
     */
    private static final int ZONAS_DE_RELLENO = 3000;

    private static final java.util.concurrent.atomic.AtomicInteger CORRELATIVO =
            new java.util.concurrent.atomic.AtomicInteger();

    private static BaseDeDatosDePrueba base;
    private static long municipalidadA;
    private static long municipalidadB;
    private static long predioAlOeste;
    private static long predioAlEste;
    private static long predioFuera;
    private static long predioSinGeometria;
    private static long predioDeLaVecina;
    private static MockMvc mvc;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidadA = DatosDePrueba.crearMunicipalidad(base, "200604", "Municipalidad de #4");
        municipalidadB = DatosDePrueba.crearMunicipalidad(base, "200605", "Municipalidad vecina");

        sembrar(municipalidadA, "A");
        sembrar(municipalidadB, "B");

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        JdbcClient jdbc = JdbcClient.create(pool);
        TenantTransactionManager gestor = new TenantTransactionManager(pool);

        mvc =
                MockMvcBuilders.standaloneSetup(
                                new ZonificacionController(
                                        envolver(
                                                new ConsultaDeZonificacion(
                                                        new UrbanoRepositoryJdbc(jdbc)),
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
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @BeforeEach
    void fijarContexto() {
        TenantContext.fijar(new MunicipalidadId(municipalidadA));
        OrigenContext.fijar(new Origen("tecnico.urbano", "PC-04", "10.0.0.4"));
    }

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
        OrigenContext.limpiar();
    }

    @Test
    @DisplayName("un predio dentro de la zona oeste contesta esa zona, con su norma y su parametro")
    void elPredioDeLaZonaOeste() throws Exception {
        MvcResult respuesta =
                mvc.perform(get(RUTA).param("predioId", Long.toString(predioAlOeste))).andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
        assertThat(respuesta.getResponse().getContentAsString())
                .contains("\"codigo\":\"RDM\"")
                .contains("\"ordenanza\":\"ORD-004-2026-A\"")
                .contains("\"altura_maxima\"");
    }

    @Test
    @DisplayName("EL CONTRASTE: el predio vecino, al otro lado de la arista, contesta la OTRA zona")
    void elPredioDeLaZonaEste() throws Exception {
        MvcResult respuesta =
                mvc.perform(get(RUTA).param("predioId", Long.toString(predioAlEste))).andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
        assertThat(respuesta.getResponse().getContentAsString())
                .as(
                        "las dos zonas comparten arista y sus MARCOS se cortan, asi que el filtro"
                                + " por marco devuelve las dos: quien decide cual es el"
                                + " ST_Contains. Sin el, esta prueba y la anterior darian la misma")
                .contains("\"codigo\":\"CZ\"");
    }

    @Test
    @DisplayName("un predio SIN POLIGONO contesta 422, no 200 con zona nula (AC-5)")
    void elPredioSinGeometria() throws Exception {
        MvcResult respuesta =
                mvc.perform(get(RUTA).param("predioId", Long.toString(predioSinGeometria)))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
        assertThat(respuesta.getResponse().getContentAsString()).contains("plano catastral");
    }

    @Test
    @DisplayName("y uno con poligono fuera de todo plan vigente, 404 con su motivo")
    void elPredioFueraDeTodoPlan() throws Exception {
        MvcResult respuesta =
                mvc.perform(get(RUTA).param("predioId", Long.toString(predioFuera))).andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(404);
        assertThat(respuesta.getResponse().getContentAsString())
                .contains("Ningun plan de zonificacion vigente");
    }

    @Test
    @DisplayName("EL AISLAMIENTO: con contexto de A, el predio de B no existe")
    void elPredioDeLaVecinaNoSeVe() throws Exception {
        MvcResult respuesta =
                mvc.perform(get(RUTA).param("predioId", Long.toString(predioDeLaVecina)))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus())
                .as(
                        "el predio existe en la base y la politica RLS lo esconde: quien lo filtra"
                                + " no es ningun WHERE de esta consulta")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("y la zona de B tampoco decide sobre un predio de A que caiga en su suelo")
    void laZonaDeLaVecinaNoDecideSobreA() throws Exception {
        // El lote de A esta dentro del rectangulo de la zona de A Y del de la zona de B: las dos
        // municipalidades dibujaron sobre el mismo suelo, que es lo que pasa en dos distritos
        // limitrofes con el mismo plano de referencia. La respuesta tiene que ser la de A.
        MvcResult respuesta =
                mvc.perform(get(RUTA).param("predioId", Long.toString(predioAlOeste))).andReturn();

        assertThat(respuesta.getResponse().getContentAsString())
                .as("dos zonas identicas sobre el mismo suelo: la que contesta es la del inquilino")
                .contains("ORD-004-2026-A")
                .doesNotContain("ORD-004-2026-B");
    }

    @Test
    @DisplayName(
            "ADR-0034 regla 2: la consulta usa el indice del marco y no un recorrido del padron")
    void elMarcoLlegaAlIndice() throws Exception {
        List<String> plan = planDeLaConsulta();

        assertThat(String.join("\n", plan))
                .as(
                        "lo que hay que exigir NUNCA es la palabra «Index» —la del quinto hallazgo"
                                + " de RLS tambien la decia—: es que las condiciones DEL FILTRO"
                                + " salgan en el Index Cond. Plan medido:\n%s",
                        String.join("\n", plan))
                .contains("zonificacion_marco_ix");

        String condicionDelIndice =
                plan.stream()
                        .filter(linea -> linea.contains("Index Cond"))
                        .reduce("", (a, b) -> a + "\n" + b);
        assertThat(condicionDelIndice)
                .as(
                        "las cuatro columnas del marco y la condicion de la politica, juntas en el"
                                + " Index Cond. Plan medido:\n%s",
                        String.join("\n", plan))
                .contains("marco_oeste")
                .contains("marco_sur")
                .contains("marco_este")
                .contains("marco_norte")
                .contains("municipalidad_id");
    }

    // ------------------------------------------------------------------

    /**
     * El plan de la consulta de contencion, pedido como {@code kamayuk_app} y con contexto.
     *
     * <p>Se le pide a la <b>constante del repositorio</b> y no a una copia escrita aqui: un plan
     * medido sobre una consulta de la prueba seguiria verde si alguien devolviera la de produccion
     * al operador espacial suelto, que es exactamente el cambio que no se ve en el resultado.
     *
     * <p><b>Y se mide sobre una tabla con volumen</b>, que es lo que este trabajo aprendio
     * ejecutando: con las dos zonas del escenario el planificador ponia {@code zonificacion} de
     * lado EXTERNO del bucle y las cuatro comparaciones del marco caian en el {@code Join Filter}
     * —el sintoma exacto del quinto hallazgo de RLS, con la palabra «Index» en el plan—. Con {@link
     * #ZONAS_DE_RELLENO} zonas el planificador la pone de lado INTERNO y las cuatro columnas, junto
     * con la condicion de la politica, salen en el {@code Index Cond}. O sea que una prueba de plan
     * sobre dos filas no mide el plan: mide el tamano.
     *
     * <p><b>Sin {@code enable_seqscan = off} a proposito.</b> Forzar la preferencia mediria si el
     * indice es ALCANZABLE; sin forzarla se mide ademas que el planificador lo ELIGE, que es la
     * afirmacion que importa y la que el quinto hallazgo demuestra que no se puede dar por hecha.
     */
    private static List<String> planDeLaConsulta() throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadA);
            String sql =
                    UrbanoRepositoryJdbc.ZONA_QUE_CONTIENE
                            .replace(":predio", Long.toString(predioAlOeste))
                            .replace(":fecha", "DATE '2026-06-15'");
            List<String> lineas = new ArrayList<>();
            try (PreparedStatement explicar = app.prepareStatement("EXPLAIN " + sql);
                    ResultSet filas = explicar.executeQuery()) {
                while (filas.next()) {
                    lineas.add(filas.getString(1));
                }
            }
            return lineas;
        }
    }

    /** Las dos zonas, sus parametros y los cuatro predios de esa municipalidad. */
    private static void sembrar(long muni, String sufijo) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, muni);
            long viaId =
                    insertar(
                            app,
                            "INSERT INTO via (municipalidad_id, codigo, tipo_via, nombre)"
                                    + " VALUES (?, ?, 'AVENIDA', ?) RETURNING id",
                            muni,
                            "V-" + sufijo,
                            "Avenida del plan " + sufijo);

            long oeste =
                    zona(app, muni, sufijo, "RDM", "Residencial de densidad media", ZONA_OESTE);
            long este = zona(app, muni, sufijo, "CZ", "Comercio zonal", ZONA_ESTE);
            parametro(app, muni, oeste, "altura_maxima", "5", "pisos");
            parametro(app, muni, este, "altura_maxima", "6", "pisos");

            rellenar(app, muni, sufijo);

            long alOeste = predio(app, muni, viaId, sufijo + "1", LOTE_AL_OESTE);
            long alEste = predio(app, muni, viaId, sufijo + "2", LOTE_AL_ESTE);
            long fuera = predio(app, muni, viaId, sufijo + "3", LOTE_FUERA);
            long sinPlano = predio(app, muni, viaId, sufijo + "4", null);
            app.commit();

            if ("A".equals(sufijo)) {
                predioAlOeste = alOeste;
                predioAlEste = alEste;
                predioFuera = fuera;
                predioSinGeometria = sinPlano;
            } else {
                predioDeLaVecina = alOeste;
            }
        }
    }

    /**
     * Relleno: zonas y predios, lejos de los del escenario.
     *
     * <p><b>Las dos tablas, y lo enseno ejecutar.</b> Con volumen solo en {@code zonificacion} el
     * planificador seguia poniendola de lado EXTERNO del bucle —porque {@code predio} tenia cinco
     * filas y ni siquiera usaba su clave primaria para {@code id = ?}— y las cuatro comparaciones
     * del marco volvian al {@code Join Filter}. Con las dos pobladas, {@code predio} entra por su
     * PK y {@code zonificacion} por {@code zonificacion_marco_ix}, que es el plan de produccion.
     *
     * <p>Las zonas van <b>del mismo plan</b> porque {@code zonificacion_planes_no_se_pisan} lleva
     * {@code plan WITH <>}: dentro de un plan las zonas pueden ser vecinas, y si el relleno
     * declarara otro plan cada fila chocaria con la anterior. Y <b>en otra banda de latitud</b>,
     * para que ninguna contenga a los predios del escenario y las respuestas no cambien.
     */
    private static void rellenar(Connection app, long muni, String sufijo) throws SQLException {
        ejecutar(
                app,
                "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo, direccion,"
                        + " geometria)"
                        + " SELECT ?, ? || to_char(g, 'FM0000000'), 'URBANO', 'Relleno ' || g,"
                        + "        ST_Multi(ST_MakeEnvelope(-81 + (g % 100) * 0.01 + 0.004,"
                        + "                                 -6 + (g / 100) * 0.01 + 0.004,"
                        + "                                 -81 + (g % 100) * 0.01 + 0.006,"
                        + "                                 -6 + (g / 100) * 0.01 + 0.006,"
                        + "                                 4326))::geography"
                        + "   FROM generate_series(0, ?) g",
                muni,
                "20060401010010" + ("A".equals(sufijo) ? "1" : "2"),
                ZONAS_DE_RELLENO - 1);
        ejecutar(
                app,
                "INSERT INTO zonificacion (municipalidad_id, plan, ordenanza, codigo, nombre,"
                        + " geometria, vigencia_desde, observacion, usuario_registro)"
                        + " SELECT ?, ?, ?, 'R' || g, 'Zona de relleno ' || g,"
                        + "        ST_Multi(ST_MakeEnvelope(-81 + (g % 100) * 0.01,"
                        + "                                 -6 + (g / 100) * 0.01,"
                        + "                                 -81 + (g % 100) * 0.01 + 0.01,"
                        + "                                 -6 + (g / 100) * 0.01 + 0.01,"
                        + "                                 4326))::geography,"
                        + "        DATE '2026-01-01', 'relleno de la prueba de plan de #4',"
                        + "        'prueba'"
                        + "   FROM generate_series(0, ?) g",
                muni,
                "PDU-2026-" + sufijo,
                "ORD-004-2026-" + sufijo,
                ZONAS_DE_RELLENO - 1);
        ejecutar(app, "ANALYZE zonificacion");
        ejecutar(app, "ANALYZE predio");
    }

    private static long zona(
            Connection app, long muni, String sufijo, String codigo, String nombre, String wkt)
            throws SQLException {
        return insertar(
                app,
                "INSERT INTO zonificacion (municipalidad_id, plan, ordenanza, codigo, nombre,"
                        + " geometria, vigencia_desde, observacion, usuario_registro)"
                        + " VALUES (?, ?, ?, ?, ?, ST_GeogFromText(?), DATE '2026-01-01',"
                        + "         'zona de la prueba de frontera de #4', 'prueba') RETURNING id",
                muni,
                "PDU-2026-" + sufijo,
                "ORD-004-2026-" + sufijo,
                codigo,
                nombre,
                wkt);
    }

    private static void parametro(
            Connection app, long muni, long zona, String clave, String valor, String unidad)
            throws SQLException {
        ejecutar(
                app,
                "INSERT INTO parametro_urbanistico (municipalidad_id, zonificacion_id, clave,"
                        + " valor, unidad, observacion, usuario_registro)"
                        + " VALUES (?, ?, ?, ?, ?, 'parametro de la prueba de #4', 'prueba')",
                muni,
                zona,
                clave,
                valor,
                unidad);
    }

    private static long predio(Connection app, long muni, long viaId, String sufijo, String wkt)
            throws SQLException {
        return insertar(
                app,
                "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo, via_id,"
                        + " direccion, lote, geometria)"
                        + " VALUES (?, ?, 'URBANO', ?, ?, '01',"
                        + "         ST_GeogFromText(CAST(? AS text)))"
                        + " RETURNING id",
                muni,
                codigoDe(sufijo),
                viaId,
                "Jr. Zonificacion " + sufijo,
                wkt);
    }

    /**
     * Un codigo de referencia catastral distinto por predio, de un correlativo.
     *
     * <p>De un contador y no del {@code hashCode} del sufijo, y lo enseno ejecutar: la primera
     * version componia el codigo con un prefijo que <b>ya media las 21 posiciones</b>, asi que el
     * {@code substring} devolvia el prefijo solo y los cuatro predios salian con el mismo codigo.
     * El rojo era {@code duplicate key value violates unique constraint "predio_codigo_uq"} —una
     * frase sobre la unicidad del padron en una prueba que habla de zonificacion—.
     */
    private static String codigoDe(String sufijo) {
        return String.format("2006040101001001%05d", CORRELATIVO.incrementAndGet());
    }

    private static long insertar(Connection app, String sql, Object... argumentos)
            throws SQLException {
        try (PreparedStatement sentencia = app.prepareStatement(sql)) {
            for (int i = 0; i < argumentos.length; i++) {
                sentencia.setObject(i + 1, argumentos[i]);
            }
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }

    private static void ejecutar(Connection app, String sql, Object... argumentos)
            throws SQLException {
        try (PreparedStatement sentencia = app.prepareStatement(sql)) {
            for (int i = 0; i < argumentos.length; i++) {
                sentencia.setObject(i + 1, argumentos[i]);
            }
            sentencia.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T envolver(T objetivo, TenantTransactionManager gestor) {
        ProxyFactory fabrica = new ProxyFactory(objetivo);
        fabrica.setProxyTargetClass(true);
        fabrica.addAdvice(
                new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        return (T) fabrica.getProxy();
    }
}
