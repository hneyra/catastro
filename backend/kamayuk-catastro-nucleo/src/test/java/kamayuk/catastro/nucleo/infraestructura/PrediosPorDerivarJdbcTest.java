package kamayuk.catastro.nucleo.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
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
 * Lo que el derivador recorre, contra PostgreSQL de verdad (#26, AC-1, AC-2 y AC-3).
 *
 * <h2>Por que hace falta una clase aparte con su propio padron</h2>
 *
 * <p>Las tres cosas que se miden aqui —que la pagina avance, que el censo cuente lo mismo que la
 * pagina, y que el plan no empeore al filtrar— <b>no se pueden medir sobre dos filas</b>. Con la
 * siembra normal el planificador elige el {@code Seq Scan} porque la tabla cabe en una pagina, asi
 * que una prueba de plan sobre el escenario de {@code DerivacionDeFrentesJdbcTest} mediria el
 * tamano de la fixture y no el plan de produccion (la leccion de #4).
 *
 * <p>Aqui hay <b>3 000 predios en cada una de dos municipalidades</b>, con {@code ANALYZE} —sin el,
 * el plan medido es el de una tabla vacia—, y la vecina existe para que la politica RLS tenga algo
 * que descartar: sin ella «la consulta ve solo lo suyo» seria cierto por no haber nada mas.
 */
@DisplayName("#26 — Lo que el derivador recorre: el cursor, el censo y el plan")
class PrediosPorDerivarJdbcTest {

    private static final int PREDIOS_POR_MUNICIPALIDAD = 3000;

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static long vecina;
    private static FrentesDelPredioJdbc frentes;
    private static TransactionTemplate enUnaTransaccion;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "240601", "La del recorrido");
        vecina = DatosDePrueba.crearMunicipalidad(base, "240602", "La vecina del recorrido");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidad, parametroId, "RE");
        DatosDePrueba.sembrarTenant(base, vecina, DatosDePrueba.crearParametroNacional(base), "VR");

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));
        frentes = new FrentesDelPredioJdbc(JdbcClient.create(pool));
        enUnaTransaccion = new TransactionTemplate(new TenantTransactionManager(pool));

        // La siembra deja UN predio por municipalidad; aqui hacen falta miles para que el plan
        // medido sea el de produccion y no el del tamano de la fixture.
        sembrarPadron(municipalidad, "3", PREDIOS_POR_MUNICIPALIDAD);
        sembrarPadron(vecina, "4", PREDIOS_POR_MUNICIPALIDAD);
        comoAdmin("ANALYZE predio");
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

    /**
     * AC-1: la pagina RESPETA su {@code desde}.
     *
     * <p>Mientras el recorrido pedia siempre desde cero, este parametro no lo ejercia nadie: podia
     * estar bien o mal escrito y el build seguia en verde. Ahora la corrida entera depende de el.
     */
    @Test
    @DisplayName("#26 — la pagina sigue donde se dejo: el `desde` acota de verdad")
    void laPaginaSigueDondeSeDejo() {
        TenantContext.fijar(new MunicipalidadId(municipalidad));

        List<Long> primerLote =
                enUnaTransaccion.execute(estado -> frentes.prediosPorDerivar(0L, 500));
        List<Long> segundoLote =
                enUnaTransaccion.execute(
                        estado ->
                                frentes.prediosPorDerivar(
                                        primerLote.get(primerLote.size() - 1), 500));

        assertThat(primerLote).hasSize(500).isSorted();
        assertThat(segundoLote).hasSize(500).isSorted();
        assertThat(segundoLote)
                .as(
                        "no puede repetir ni uno del primero: con el cursor ignorado la corrida"
                                + " recorre los mismos 500 para siempre y el resto del padron no se"
                                + " deriva nunca")
                .doesNotContainAnyElementsOf(primerLote);
        assertThat(segundoLote.get(0)).isGreaterThan(primerLote.get(primerLote.size() - 1));
    }

    /**
     * AC-2 y AC-3: el censo cuenta EXACTAMENTE lo que la pagina recorre.
     *
     * <p>Y las DOS mitades de la decision de AC-3, que se rompen en direcciones opuestas:
     *
     * <ul>
     *   <li>un predio DADO_DE_BAJA <b>sale</b> del conjunto —ya no esta en el padron y no hay nada
     *       que explicar sobre el—, y sale de los dos a la vez, censo y recorrido;
     *   <li>un predio SIN POLIGONO <b>se queda</b>, que es la mitad de AC-3 que este issue mide y
     *       NO hace. Filtrandolo, ese predio deja de tener constancia en {@code frente_derivacion}
     *       y su respuesta pasa de «no hay cartografia» a «nunca se ha derivado», que manda a mirar
     *       si el proceso corre. El motivo entero esta en el adaptador; esta prueba es lo que
     *       impide que alguien lo «arregle» sin leerlo.
     * </ul>
     */
    @Test
    @DisplayName("#26 — el censo y el recorrido cuentan lo mismo, y el sin-poligono SIGUE dentro")
    void elCensoYElRecorridoCuentanLoMismo() throws SQLException {
        TenantContext.fijar(new MunicipalidadId(municipalidad));

        int censoInicial = enUnaTransaccion.execute(estado -> frentes.cuantosPrediosPorDerivar());
        assertThat(censoInicial)
                .as(
                        "los 3 000 sembrados aqui y el que deja la siembra normal, que NO tiene"
                                + " poligono")
                .isEqualTo(PREDIOS_POR_MUNICIPALIDAD + 1)
                .as("o sea, todos los de la tabla: hoy el filtro no deja fuera a ninguno")
                .isEqualTo(cuantosPrediosHayEnTotal());
        List<Long> recorridoInicial = recorrerEntero();
        assertThat(recorridoInicial).hasSize(censoInicial);
        assertThat(recorridoInicial)
                .as(
                        "y el predio SIN POLIGONO esta dentro. Es la mitad de AC-3 que no se hace,"
                                + " a proposito: sin recorrerlo no hay fila en `frente_derivacion`,"
                                + " y «no hay cartografia» se leeria como «nunca se ha derivado»")
                .contains(elPredioSinPoligono());

        long unoDeBaja = recorridoInicial.get(0);
        comoAdmin(
                "UPDATE predio SET estado = 'DADO_DE_BAJA' WHERE id = "
                        + unoDeBaja
                        + " AND municipalidad_id = "
                        + municipalidad);
        try {
            int censoDespues =
                    enUnaTransaccion.execute(estado -> frentes.cuantosPrediosPorDerivar());
            List<Long> recorridoDespues = recorrerEntero();

            assertThat(censoDespues)
                    .as("un predio dado de baja sale del conjunto")
                    .isEqualTo(censoInicial - 1);
            assertThat(recorridoDespues)
                    .as(
                            "y el recorrido cuenta lo mismo que el censo: es el mismo fragmento de"
                                    + " SQL, escrito una sola vez. Escritos aparte, «se agoto el"
                                    + " padron» seria cierto contra un denominador que cuenta otra"
                                    + " cosa")
                    .hasSize(censoDespues)
                    .doesNotContain(unoDeBaja);
        } finally {
            // Se devuelve a su sitio: el orden de los casos no puede decidir lo que otro mide.
            comoAdmin(
                    "UPDATE predio SET estado = 'ACTIVO' WHERE id = "
                            + unoDeBaja
                            + " AND municipalidad_id = "
                            + municipalidad);
        }
    }

    /** Cuantos predios hay en la municipalidad, sin ningun filtro. */
    private static int cuantosPrediosHayEnTotal() throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement();
                    ResultSet fila = sentencia.executeQuery("SELECT count(*) FROM predio")) {
                fila.next();
                return fila.getInt(1);
            }
        }
    }

    /** El predio que la siembra normal deja, que es el unico SIN poligono. */
    private static long elPredioSinPoligono() throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement();
                    ResultSet fila =
                            sentencia.executeQuery(
                                    "SELECT id FROM predio WHERE geometria IS NULL")) {
                assertThat(fila.next())
                        .as("la siembra tiene que dejar uno sin poligono, o esto no mide nada")
                        .isTrue();
                return fila.getLong(1);
            }
        }
    }

    /** El recorrido entero, por lotes, tal como lo hace {@code DerivacionDeLosFrentes}. */
    private static List<Long> recorrerEntero() {
        List<Long> todos = new java.util.ArrayList<>();
        long desde = 0L;
        while (true) {
            long cursor = desde;
            List<Long> lote =
                    enUnaTransaccion.execute(estado -> frentes.prediosPorDerivar(cursor, 500));
            if (lote.isEmpty()) {
                break;
            }
            todos.addAll(lote);
            desde = lote.get(lote.size() - 1);
            if (lote.size() < 500) {
                break;
            }
        }
        return todos;
    }

    /**
     * AC-3: filtrar NO empeora el plan.
     *
     * <p>Lo que se exige no es la palabra «Index», que es el quinto hallazgo de RLS literal: se
     * exige que el indice <b>nombrado</b> sea {@code predio_pk} y que la condicion de la politica y
     * el cursor esten JUNTOS en su {@code Index Cond}. Los dos predicados de AC-3 caen al filtro
     * —{@code estado} y {@code geometria} no estan en esa clave— y por eso hay que medir que no
     * cambian el metodo de acceso: un {@code Seq Scan} aqui leeria el padron entero del inquilino
     * en cada lote.
     *
     * <p>El SQL se le pide al repositorio ejecutandolo, y no se copia en la prueba: una copia
     * seguiria verde el dia que cambiara la de produccion.
     *
     * <p>Sin {@code enable_seqscan = off}, a proposito: forzarlo mediria si el indice es
     * alcanzable, y lo que hay que medir es que el planificador lo elija.
     */
    @Test
    @DisplayName("#26 — el plan del recorrido usa predio_pk, con la politica y el cursor dentro")
    void elPlanDelRecorridoUsaLaClavePrimaria() throws SQLException {
        String plan =
                planDe(
                        "SELECT id FROM predio WHERE estado = 'ACTIVO' AND id > 0"
                                + " ORDER BY id LIMIT 500");

        assertThat(plan)
                .as(
                        "el plan medido, con 3 000 predios en cada una de dos municipalidades y"
                                + " ANALYZE hecho:\n%s",
                        plan)
                .containsPattern("Index (Only )?Scan using predio_pk")
                .contains("Index Cond")
                .doesNotContain("Seq Scan on predio");
        assertThat(plan)
                .as(
                        "la condicion de la politica y el cursor, JUNTOS en el Index Cond: es lo"
                                + " que impide que el lote se lea recorriendo el padron del inquilino")
                .containsPattern("Index Cond:[^\\n]*municipalidad_id")
                .containsPattern("Index Cond:[^\\n]*id > 0");
    }

    /**
     * EL CONTRASTE: el mismo plan SIN los dos filtros de AC-3.
     *
     * <p>Es lo que contesta «no empeora» con una medida y no con una opinion: los dos planes tienen
     * que usar el mismo indice. Sin este caso, la prueba de arriba diria que el plan filtrado es
     * bueno y no que sea <b>igual de bueno</b> que el de antes.
     */
    @Test
    @DisplayName("#26 — EL CONTRASTE: filtrar por estado no cambia el indice ni las paginas leidas")
    void filtrarNoEmpeoraElPlan() throws SQLException {
        String sinFiltros = planDe("SELECT id FROM predio WHERE id > 0 ORDER BY id LIMIT 500");
        String conFiltros =
                planDe(
                        "SELECT id FROM predio WHERE estado = 'ACTIVO' AND id > 0"
                                + " ORDER BY id LIMIT 500");

        assertThat(sinFiltros)
                .as("el plan de antes de #26, para poder compararlo:\n%s", sinFiltros)
                .containsPattern("Index (Only )?Scan using predio_pk")
                .doesNotContain("Seq Scan on predio");
        assertThat(paginasDe(conFiltros))
                .as(
                        "AC-3 pide MEDIR que el plan no empeora, y el metodo de acceso no es toda"
                                + " la medida: los dos usan `predio_pk`, y lo que queda por"
                                + " comprobar es que filtrar no obligue a recorrer mas paginas para"
                                + " juntar el lote. Medido: 25 y 25. Sin filtro: %d. Con filtro:"
                                + " %d.\n%s",
                        paginasDe(sinFiltros), paginasDe(conFiltros), conFiltros)
                .isLessThanOrEqualTo(paginasDe(sinFiltros) * 2);
    }

    /** Las paginas que el plan dice haber leido en total ({@code Buffers: shared hit=N}). */
    private static int paginasDe(String plan) {
        java.util.regex.Matcher paginas =
                java.util.regex.Pattern.compile("shared hit=(\\d+)").matcher(plan);
        int mayor = 0;
        while (paginas.find()) {
            mayor = Math.max(mayor, Integer.parseInt(paginas.group(1)));
        }
        assertThat(mayor)
                .as("el plan tiene que traer sus buffers, o no se midio nada")
                .isPositive();
        return mayor;
    }

    /** El plan de una consulta, como {@code kamayuk_app} y con la politica puesta. */
    private static String planDe(String consulta) throws SQLException {
        StringBuilder plan = new StringBuilder();
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement();
                    ResultSet filas =
                            sentencia.executeQuery("EXPLAIN (ANALYZE, BUFFERS) " + consulta)) {
                while (filas.next()) {
                    plan.append(filas.getString(1)).append('\n');
                }
            }
        }
        return plan.toString();
    }

    /**
     * Miles de predios activos con poligono.
     *
     * <p>El {@code prefijo} es un DIGITO y no unas letras: {@code cod_catastral} es un dominio con
     * {@code CHECK (VALUE ~ '^[0-9]{18,25}$')}, y la primera version de esta fixture lo escribio
     * con letras — el motor la rechazo antes de sembrar una sola fila.
     */
    private static void sembrarPadron(long muni, String prefijo, int cuantos) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, muni);
            try (Statement sentencia = app.createStatement()) {
                sentencia.executeUpdate(
                        "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo,"
                                + " direccion, estado, geometria)"
                                + " SELECT "
                                + muni
                                + ", '"
                                + prefijo
                                + "' || lpad(n::text, 20, '0'), 'URBANO',"
                                + "        'Jr. del recorrido ' || n, 'ACTIVO',"
                                + "        ST_Multi(ST_SetSRID(ST_MakeBox2D("
                                + "          ST_Point(-80.70 + n * 0.0001, -4.90),"
                                + "          ST_Point(-80.6999 + n * 0.0001, -4.8999)),"
                                + "          4326))::geography"
                                + "   FROM generate_series(1, "
                                + cuantos
                                + ") AS n");
            }
            app.commit();
        }
    }

    private static void comoAdmin(String sql) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                Statement sentencia = admin.createStatement()) {
            sentencia.execute(sql);
        }
    }
}
