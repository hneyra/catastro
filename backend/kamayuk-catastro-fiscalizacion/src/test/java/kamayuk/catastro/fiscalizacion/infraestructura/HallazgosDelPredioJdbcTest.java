package kamayuk.catastro.fiscalizacion.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.fiscalizacion.dominio.HallazgoDelPredio;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #17 — Los hallazgos de UN predio, contra PostgreSQL de verdad.
 *
 * <h2>Por que va hasta la base y no bastaba un doble</h2>
 *
 * <p>Las tres cosas que este issue tiene que demostrar las sostiene el motor y no Java:
 *
 * <ul>
 *   <li><b>Que la consulta va por {@code hallazgo.predio_id} y llega a su indice</b> (AC-2). Un
 *       doble devolveria lo que se le programe; lo que hay que medir es el PLAN, y un plan solo
 *       existe dentro de PostgreSQL — y solo dice la verdad con volumen: la leccion de #4 es que
 *       una prueba de plan sobre dos filas no mide el plan, mide el tamano.
 *   <li><b>Que devuelve los de ESE predio y no los de otro</b>, que es lo que un {@code WHERE} mal
 *       escrito rompe sin cambiar la forma de la respuesta.
 *   <li><b>El aislamiento entre municipalidades</b>, que lo sostiene la politica RLS y ningun
 *       {@code WHERE}.
 * </ul>
 *
 * <p>La conexion de la lectura es la de {@code kamayuk_app}: un superusuario omite RLS incluso con
 * {@code FORCE ROW LEVEL SECURITY} (primer hallazgo de RLS), asi que una prueba escrita sobre el no
 * verificaria ningun aislamiento — y el plan que midiera seria otro, porque sin la politica delante
 * el {@code Index Cond} no lleva la {@code municipalidad_id}.
 */
@DisplayName("#17 — Los hallazgos de un predio, contra PostgreSQL")
class HallazgosDelPredioJdbcTest {

    /**
     * Cuantos predios con su hallazgo se plantan en cada municipalidad para medir el plan.
     *
     * <p>Con las dos filas que siembra la fixture, el planificador recorre la tabla porque leerla
     * entera es mas barato que abrir un indice — y el plan medido diria «Seq Scan» sobre un
     * escenario que no es el de produccion. Es exactamente lo que #4 aprendio ejecutando.
     */
    private static final int HALLAZGOS_DE_RELLENO = 3000;

    private static BaseDeDatosDePrueba base;
    private static long municipalidadA;
    private static long municipalidadB;
    private static long predioConHallazgo;
    private static long predioSinHallazgo;
    private static long predioDeLaVecina;
    private static FiscalizacionRepositoryJdbc repositorio;
    private static TransactionTemplate enUnaTransaccion;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidadA = DatosDePrueba.crearMunicipalidad(base, "240501", "La que consulta");
        municipalidadB = DatosDePrueba.crearMunicipalidad(base, "240502", "La vecina");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidadA, parametroId, "HA");
        DatosDePrueba.sembrarTenant(base, municipalidadB, parametroId, "HB");

        // La fixture deja UN predio con UN hallazgo y su acta en cada municipalidad.
        predioConHallazgo = elPredioSembradoDe(municipalidadA);
        predioDeLaVecina = elPredioSembradoDe(municipalidadB);

        rellenar(municipalidadA);
        rellenar(municipalidadB);
        // Sin ANALYZE el planificador trabaja sobre estimaciones de una tabla vacia y elige por
        // una cardinalidad que ya no es la que hay. Va como OWNER: `kamayuk_app` no es dueno.
        analizar();

        predioSinHallazgo = unPredioDeRellenoSinHallazgoDe(municipalidadA);

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        repositorio = new FiscalizacionRepositoryJdbc(JdbcClient.create(pool));
        enUnaTransaccion = new TransactionTemplate(new TenantTransactionManager(pool));
    }

    @AfterAll
    static void cerrar() {
        if (base != null) {
            base.close();
        }
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
    }

    // ── AC-1: que devuelve ─────────────────────────────────────────────

    @Test
    @DisplayName("AC-1 — el hallazgo del predio sale con su campania, su ficha y su acta")
    void elHallazgoSaleConSuCampaniaYSuActa() {
        TenantContext.fijar(new MunicipalidadId(municipalidadA));

        List<HallazgoDelPredio> hallados = leerLosDe(predioConHallazgo);

        assertThat(hallados).hasSize(1);
        HallazgoDelPredio hallado = hallados.get(0);
        assertThat(hallado.hallazgo().predioId()).isEqualTo(predioConHallazgo);
        assertThat(hallado.hallazgo().clase().name()).isEqualTo("SUBVALUADOR");
        assertThat(hallado.hallazgo().estado().name()).isEqualTo("FIRME");
        assertThat(hallado.hallazgo().fichaId())
                .as("QUE VERSION se contrasto: sin ella el exceso no significa nada (regla 9)")
                .isNotNull();
        assertThat(hallado.campaniaCodigo())
                .as("la campania en que se hallo, por su codigo y no solo por su numero")
                .isEqualTo("CAM-HA");
        assertThat(hallado.campaniaId()).isPositive();
        assertThat(hallado.actaLevantada())
                .as("y su acta, que es el acto que lo sostiene (ADR-0035)")
                .isPresent();
        assertThat(hallado.acta().numero()).isEqualTo("ACT-HA-001");
    }

    @Test
    @DisplayName("EL CONTRASTE del acta: un hallazgo sin acta llega con acta NULA, no inventada")
    void unHallazgoSinActaLlegaSinActa() throws SQLException {
        // Sin este caso, un mapeador que compusiera siempre un acta pasaria la prueba de arriba.
        long deRelleno = unPredioDeRellenoConHallazgoDe(municipalidadA);
        TenantContext.fijar(new MunicipalidadId(municipalidadA));

        List<HallazgoDelPredio> hallados = leerLosDe(deRelleno);

        assertThat(hallados).hasSize(1);
        assertThat(hallados.get(0).actaLevantada())
                .as("un hallazgo firme sin acta es un estado legitimo del recorrido, no un fallo")
                .isEmpty();
        assertThat(hallados.get(0).acta()).isNull();
    }

    @Test
    @DisplayName("y devuelve los de ESE predio, no los del vecino de al lado")
    void devuelveLosDeEsePredioYNoLosDeOtro() throws SQLException {
        long otro = unPredioDeRellenoConHallazgoDe(municipalidadA);
        TenantContext.fijar(new MunicipalidadId(municipalidadA));

        List<Long> delSembrado = prediosDe(leerLosDe(predioConHallazgo));
        List<Long> delOtro = prediosDe(leerLosDe(otro));

        assertThat(delSembrado).containsExactly(predioConHallazgo);
        assertThat(delOtro)
                .as(
                        "el mismo padron, la misma campania y dos respuestas distintas: es lo que"
                                + " un WHERE mal escrito rompe sin cambiar la FORMA de la"
                                + " respuesta, y por eso hace falta preguntarle a dos predios")
                .containsExactly(otro);
    }

    // ── AC-3: sin hallazgos, lista vacia ───────────────────────────────

    @Test
    @DisplayName("AC-3 — un predio del padron SIN hallazgos devuelve la lista vacia")
    void unPredioSinHallazgosDevuelveVacio() {
        TenantContext.fijar(new MunicipalidadId(municipalidadA));

        assertThat(leerLosDe(predioSinHallazgo))
                .as("el predio existe y esta limpio; que no exista lo contesta el borde con 404")
                .isEmpty();
    }

    // ── AC-4: aqui no hay omisos, y no porque se filtren ────────────────

    @Test
    @DisplayName(
            "AC-4 — un omiso catastral de la misma campania no sale por ninguna ruta de predio")
    void unOmisoNoSalePorNingunPredio() throws SQLException {
        long omisoId = plantarUnOmisoEn(municipalidadA);
        TenantContext.fijar(new MunicipalidadId(municipalidadA));

        List<Long> vistos = new ArrayList<>();
        for (long predio : new long[] {predioConHallazgo, predioSinHallazgo}) {
            leerLosDe(predio).forEach(h -> vistos.add(h.hallazgo().id()));
        }

        assertThat(vistos)
                .as(
                        "no lo filtra ningun WHERE de clase: hallazgo_contraste_check (V9) le exige"
                                + " predio_id NULO a un OMISO_CATASTRAL, asi que «= :predioId» no"
                                + " puede alcanzarlo. Se mide porque quien lea «los hallazgos del"
                                + " predio» va a suponer que estan todos")
                .doesNotContain(omisoId);
        assertThat(existeElHallazgo(municipalidadA, omisoId))
                .as("y el contraste: el omiso ESTA en la base, no es que no se haya escrito")
                .isTrue();
    }

    // ── El aislamiento ─────────────────────────────────────────────────

    @Test
    @DisplayName("EL AISLAMIENTO: con contexto de A, el predio de B no tiene hallazgos")
    void elPredioDeLaVecinaNoTraeSusHallazgos() {
        TenantContext.fijar(new MunicipalidadId(municipalidadA));

        assertThat(leerLosDe(predioDeLaVecina))
                .as(
                        "el predio de B SI tiene su hallazgo con su acta, y aun asi desde A no sale"
                                + " ninguno: lo que lo impide no es ningun WHERE, es la politica")
                .isEmpty();
    }

    // ── AC-2: el plan ──────────────────────────────────────────────────

    @Test
    @DisplayName("AC-2 — la consulta llega a hallazgo_predio_ix, que V9 ya trae: no hace falta V11")
    void laConsultaLlegaAlIndicePorPredio() throws SQLException {
        List<String> plan = planDeLaConsulta();
        String entero = String.join("\n", plan);

        assertThat(entero)
                .as(
                        "lo que hay que exigir NUNCA es la palabra «Index» —la del quinto hallazgo"
                                + " de RLS tambien la decia—: es que el indice que se nombra sea el"
                                + " del filtro. Plan medido:\n%s",
                        entero)
                .contains("hallazgo_predio_ix");

        String condicionDelIndice =
                plan.stream()
                        .filter(linea -> linea.contains("Index Cond"))
                        .reduce("", (a, b) -> a + "\n" + b);
        assertThat(condicionDelIndice)
                .as(
                        "el predio y la condicion de la politica, JUNTOS en el Index Cond: si la"
                                + " municipalidad cayera al Filter, la consulta estaria leyendo los"
                                + " hallazgos de todas las municipalidades para descartarlos"
                                + " despues. Plan medido:\n%s",
                        entero)
                .contains("predio_id")
                .contains("municipalidad_id");
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private static List<HallazgoDelPredio> leerLosDe(long predioId) {
        return enUnaTransaccion.execute(estado -> repositorio.hallazgosDelPredio(predioId));
    }

    private static List<Long> prediosDe(List<HallazgoDelPredio> hallados) {
        return hallados.stream().map(h -> h.hallazgo().predioId()).toList();
    }

    /**
     * El plan de la consulta de produccion, pedido como {@code kamayuk_app} y con contexto.
     *
     * <p>Se le pide a la <b>constante del repositorio</b> y no a una copia escrita aqui: un plan
     * medido sobre una consulta de la prueba seguiria verde el dia que alguien cambiara la de
     * produccion, que es justo el cambio que no se ve en el resultado.
     *
     * <p><b>Sin {@code enable_seqscan = off} a proposito.</b> Forzarlo mediria si el indice es
     * ALCANZABLE; sin forzarlo se mide ademas que el planificador lo ELIGE, que es la afirmacion
     * que importa y la que el quinto hallazgo de RLS demuestra que no se puede dar por hecha.
     */
    private static List<String> planDeLaConsulta() throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadA);
            String sql =
                    FiscalizacionRepositoryJdbc.HALLAZGOS_DEL_PREDIO.replace(
                            ":predioId", Long.toString(predioConHallazgo));
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

    // ── Fixtures ───────────────────────────────────────────────────────

    private static long elPredioSembradoDe(long municipalidadId) throws SQLException {
        return unaCifraDe(
                municipalidadId, "SELECT h.predio_id FROM hallazgo h ORDER BY h.id LIMIT 1");
    }

    private static long unPredioDeRellenoSinHallazgoDe(long municipalidadId) throws SQLException {
        return unaCifraDe(
                municipalidadId,
                "SELECT p.id FROM predio p"
                        + " WHERE p.direccion LIKE 'Jr. Relleno %'"
                        + "   AND NOT EXISTS (SELECT 1 FROM hallazgo h WHERE h.predio_id = p.id)"
                        + " ORDER BY p.id LIMIT 1");
    }

    private static long unPredioDeRellenoConHallazgoDe(long municipalidadId) throws SQLException {
        return unaCifraDe(
                municipalidadId,
                "SELECT h.predio_id FROM hallazgo h JOIN predio p ON p.id = h.predio_id"
                        + " WHERE p.direccion LIKE 'Jr. Relleno %'"
                        + " ORDER BY h.id LIMIT 1");
    }

    private static boolean existeElHallazgo(long municipalidadId, long hallazgoId)
            throws SQLException {
        return unaCifraDe(municipalidadId, "SELECT count(*) FROM hallazgo WHERE id = " + hallazgoId)
                == 1L;
    }

    /**
     * Un omiso catastral de la misma campania: candidato sin predio y hallazgo sin predio.
     *
     * <p>No se puede escribir de otra forma —{@code candidato} y {@code hallazgo} tienen su
     * comprobacion—, y eso es justamente lo que AC-4 afirma.
     */
    private static long plantarUnOmisoEn(long municipalidadId) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            long campaniaId = unaCifra(app, "SELECT id FROM campania ORDER BY id LIMIT 1");
            long candidatoId =
                    unaCifra(
                            app,
                            "INSERT INTO candidato (municipalidad_id, campania_id, predio_id,"
                                    + " clase, origen, score, insumos, estado, observacion,"
                                    + " usuario_registro)"
                                    + " VALUES ("
                                    + municipalidadId
                                    + ", "
                                    + campaniaId
                                    + ", NULL, 'OMISO_CATASTRAL', 'ORTOFOTO', 0.9500,"
                                    + " '{\"fuente\":\"prueba\"}'::jsonb,"
                                    + " 'VERIFICADO_EN_CAMPO', 'omiso de prueba', 'prueba')"
                                    + " RETURNING id");
            long hallazgoId =
                    unaCifra(
                            app,
                            "INSERT INTO hallazgo (municipalidad_id, candidato_id, clase,"
                                    + " predio_id, ficha_id, area_de_la_ficha, area_verificada,"
                                    + " inspector, verificado_en, observacion, usuario_registro)"
                                    + " VALUES ("
                                    + municipalidadId
                                    + ", "
                                    + candidatoId
                                    + ", 'OMISO_CATASTRAL', NULL, NULL, NULL, 300.00,"
                                    + " 'inspector.prueba', DATE '2026-05-12',"
                                    + " 'omiso de prueba', 'prueba') RETURNING id");
            app.commit();
            return hallazgoId;
        }
    }

    /**
     * Los predios de relleno, con su candidato y su hallazgo, para que el plan sea el de verdad.
     *
     * <p>Uno por predio, que es la forma real: un candidato produce como mucho un hallazgo ({@code
     * hallazgo_candidato_uq}) y un predio entra como candidato una vez por campania. Rellenar con
     * miles de hallazgos sobre el MISMO predio mediria una distribucion que no existe.
     */
    private static void rellenar(long municipalidadId) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            long campaniaId = unaCifra(app, "SELECT id FROM campania ORDER BY id LIMIT 1");
            long fichaId = unaCifra(app, "SELECT id FROM ficha_catastral ORDER BY id LIMIT 1");
            long viaId = unaCifra(app, "SELECT id FROM via ORDER BY id LIMIT 1");
            long sectorId = unaCifra(app, "SELECT id FROM sector ORDER BY id LIMIT 1");
            long manzanaId = unaCifra(app, "SELECT id FROM manzana ORDER BY id LIMIT 1");

            ejecutar(
                    app,
                    "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo, via_id,"
                            + " direccion, sector_id, manzana_id, lote)"
                            + " SELECT "
                            + municipalidadId
                            + ", lpad((("
                            + municipalidadId
                            + " * 1000000) + g)::text, 18, '7'), 'URBANO', "
                            + viaId
                            + ", 'Jr. Relleno ' || g, "
                            + sectorId
                            + ", "
                            + manzanaId
                            + ", '01'"
                            + " FROM generate_series(1, "
                            + HALLAZGOS_DE_RELLENO
                            + ") g");

            ejecutar(
                    app,
                    "INSERT INTO candidato (municipalidad_id, campania_id, predio_id, clase,"
                            + " origen, score, insumos, estado, observacion, usuario_registro)"
                            + " SELECT p.municipalidad_id, "
                            + campaniaId
                            + ", p.id, 'SUBVALUADOR', 'ORTOFOTO', 0.9100,"
                            + " '{\"fuente\":\"relleno\"}'::jsonb, 'VERIFICADO_EN_CAMPO',"
                            + " 'relleno', 'prueba'"
                            + " FROM predio p WHERE p.direccion LIKE 'Jr. Relleno %'");

            // Uno de cada dos: la mitad de los predios de relleno queda SIN hallazgo, que es de
            // donde sale el predio limpio de AC-3 sin tener que plantar otro aparte.
            ejecutar(
                    app,
                    "INSERT INTO hallazgo (municipalidad_id, candidato_id, clase, predio_id,"
                            + " ficha_id, area_de_la_ficha, area_verificada, inspector,"
                            + " verificado_en, observacion, usuario_registro)"
                            + " SELECT c.municipalidad_id, c.id, 'SUBVALUADOR', c.predio_id, "
                            + fichaId
                            + ", 120.00, 180.00, 'inspector.relleno', DATE '2026-01-01',"
                            + " 'relleno', 'prueba'"
                            + " FROM candidato c WHERE c.observacion = 'relleno'"
                            + "   AND c.id % 2 = 0");
            app.commit();
        }
    }

    /** {@code ANALYZE} como OWNER: sin estadisticas el plan medido es el de una tabla vacia. */
    private static void analizar() throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER);
                Statement sentencia = owner.createStatement()) {
            sentencia.execute("ANALYZE predio, candidato, hallazgo, campania, acta");
        }
    }

    private static long unaCifraDe(long municipalidadId, String consulta) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            return unaCifra(app, consulta);
        }
    }

    private static long unaCifra(Connection conexion, String consulta) throws SQLException {
        try (Statement sentencia = conexion.createStatement();
                ResultSet fila = sentencia.executeQuery(consulta)) {
            if (!fila.next()) {
                throw new IllegalStateException(
                        "La fixture no dejo lo que la prueba necesita: " + consulta);
            }
            return fila.getLong(1);
        }
    }

    private static void ejecutar(Connection conexion, String sql) throws SQLException {
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.executeUpdate(sql);
        }
    }
}
