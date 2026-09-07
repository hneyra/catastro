package kamayuk.catastro.parametros.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.Ejercicio;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.parametros.aplicacion.EjerciciosSellados;
import kamayuk.catastro.parametros.dominio.CacheDeSnapshots;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #51 — Los ejercicios sellados de <b>este</b> inquilino, contra PostgreSQL de verdad.
 *
 * <h2>Por que va hasta la base y no bastaba un doble</h2>
 *
 * <p>Las cuatro cosas que este issue tiene que demostrar las sostiene el motor y no Java:
 *
 * <ul>
 *   <li><b>El aislamiento</b> (AC-4). Esta consulta no lleva ningun {@code WHERE municipalidad_id}
 *       —la regla 2 lo prohibe— asi que quien acota es la politica RLS de `V2`. Un doble devolveria
 *       lo que se le programe; lo que hay que medir es que la vecina no aparezca, y eso solo existe
 *       dentro de PostgreSQL y solo como {@code kamayuk_app}: un superusuario omite RLS incluso con
 *       {@code FORCE ROW LEVEL SECURITY} (primer hallazgo de RLS).
 *   <li><b>Que el {@code SET LOCAL} llega</b>. El caso de uso se envuelve aqui con el
 *       <b>interceptor transaccional de verdad</b>, no se llama a pelo: sin {@code @Transactional}
 *       no hay {@code SET LOCAL}, la politica no encuentra {@code app.municipalidad_id} y la
 *       consulta <b>revienta</b> en vez de devolver una lista (#486). Llamando al repositorio
 *       directamente desde la prueba ese defecto no se veria.
 *   <li><b>El colapso por ejercicio</b>. La tabla tiene una fila por ambito y puede tener varias
 *       versiones del mismo ejercicio; que quede una sola entrada, y que sea la que rige, depende
 *       del {@code DISTINCT ON} y de su desempate.
 *   <li><b>Que ese desempate es EL MISMO</b> que usa {@code conjuntoCacheadoDe}, que es lo que el
 *       calculo consulta. Si divergieran, la lista ofreceria un ejercicio y el calculo tomaria otro
 *       conjunto sin que nada lo dijera.
 * </ul>
 *
 * <h2>Lo que se siembra, y por que asi</h2>
 *
 * <p>Tres municipalidades, cada una con su papel:
 *
 * <ul>
 *   <li><b>A</b>, la que consulta: el 2026 en <b>dos versiones</b> —para que el colapso tenga algo
 *       que elegir— y con sus <b>dos ambitos</b> descargados, mas un 2025 con solo {@code
 *       OBLIGACION}. Sin las dos versiones, «devuelve la que rige» y «devuelve la primera que
 *       salga» dan la misma respuesta y ninguna prueba las separa.
 *   <li><b>B</b>, la vecina: ejercicios <b>distintos</b> —2024 y 2023—. Distintos a proposito: si
 *       las dos sembraran los mismos anios, una fuga se leeria igual que la respuesta correcta.
 *   <li><b>C</b>, recien implantada: <b>ninguno</b>. Es el caso del AC-2, y es el unico que dice
 *       que la lista vacia es una respuesta y no un fallo.
 * </ul>
 *
 * <p><b>Y se comprueba que hay sujeto antes de comparar nada.</b> «A no ve los ejercicios de B» es
 * cierto sobre dos listas vacias, asi que una siembra que fallara en silencio dejaria esta clase en
 * verde sin haber medido el aislamiento. Se afirma primero que las dos listas tienen filas, y
 * ademas se cuentan las filas de la tabla como {@code kamayuk_owner} —fuera de la lectura que se
 * mide— para que la premisa no dependa de la misma consulta que se esta probando.
 */
@DisplayName("#51 — Los ejercicios sellados de este inquilino, contra PostgreSQL")
class EjerciciosSelladosJdbcTest {

    /** El conjunto de 2026 de A: version 2, la que rige, y distinta de la de B. */
    private static final long CONJUNTO_DE_2026_DE_A = 51_002L;

    /**
     * Lo que se dice cuando la lista viene vacia y no debia.
     *
     * <p>Toda comprobacion que mire dentro de la lista lo afirma <b>antes</b> de mirar. Sin eso, el
     * defecto que deja la copia local sin filas —una fixture que falla en silencio, un {@code
     * DISTINCT ON} que no devuelve nada— sale como un {@code NoSuchElementException} pelado que
     * dice donde reviento y no que faltaba el sujeto. Medido rompiendo la siembra a proposito.
     */
    private static final String SIN_SUJETO =
            "la lista de la municipalidad que consulta esta vacia: lo de abajo se cumpliria sobre"
                    + " el vacio, asi que esto no habria medido nada";

    private static BaseDeDatosDePrueba base;
    private static long laQueConsulta;
    private static long laVecina;
    private static long recienImplantada;
    private static long elConjuntoDe2026DeA;
    private static long elConjuntoDe2026DeB;
    private static EjerciciosSellados sellados;
    private static CacheDeSnapshotsJdbc cache;
    private static TransactionTemplate enUnaTransaccion;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
        laQueConsulta = DatosDePrueba.crearMunicipalidad(base, "250101", "La que consulta");
        laVecina = DatosDePrueba.crearMunicipalidad(base, "250102", "La vecina");
        recienImplantada = DatosDePrueba.crearMunicipalidad(base, "250103", "Recien implantada");

        long parametroId = DatosDePrueba.crearParametroNacional(base);
        // La fixture deja en cada una un conjunto de 2026 version 1, ambito OBLIGACION.
        DatosDePrueba.sembrarTenant(base, laQueConsulta, parametroId, "EA");
        DatosDePrueba.sembrarTenant(base, laVecina, parametroId, "EB");
        // `recienImplantada` NO se siembra: ese es su papel.

        // A: la mitad de valuacion del 2026 que la fixture no trae, una VERSION 2 del mismo
        // ejercicio —para que el colapso tenga que elegir— y un 2025 con solo una mitad.
        anadirConjunto(laQueConsulta, elConjuntoSembradoDe(laQueConsulta), 2026, 1, "VALUACION");
        anadirConjunto(laQueConsulta, CONJUNTO_DE_2026_DE_A, 2026, 2, "OBLIGACION");
        anadirConjunto(laQueConsulta, CONJUNTO_DE_2026_DE_A, 2026, 2, "VALUACION");
        anadirConjunto(laQueConsulta, 51_003L, 2025, 1, "OBLIGACION");
        elConjuntoDe2026DeA = CONJUNTO_DE_2026_DE_A;

        // B: dos ejercicios que A NO tiene —para que una fuga se distinga de la respuesta
        // correcta— y ademas el MISMO 2026, con conjunto propio, que es el caso mas exigente: si
        // la politica RLS no acotara, la lista de B se quedaria con el conjunto de A —version 2,
        // la mayor— y el ano no cambiaria. Una fuga que no mueve la lista de anios solo se ve
        // mirando el conjunto.
        elConjuntoDe2026DeB = elConjuntoSembradoDe(laVecina);
        anadirConjunto(laVecina, 51_101L, 2024, 1, "OBLIGACION");
        anadirConjunto(laVecina, 51_102L, 2023, 1, "OBLIGACION");

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        JdbcClient jdbc = JdbcClient.create(pool);
        cache = new CacheDeSnapshotsJdbc(jdbc, java.time.Clock.systemUTC());
        TenantTransactionManager gestor = new TenantTransactionManager(pool);
        sellados = envolver(new EjerciciosSellados(cache), gestor);
        enUnaTransaccion = new TransactionTemplate(gestor);
    }

    /**
     * El caso de uso con su interceptor transaccional de verdad.
     *
     * <p>Es lo que hace que {@code @Transactional} sea parte de lo que se mide y no una anotacion
     * decorativa: con el, quitarla deja la consulta sin {@code SET LOCAL} y la politica RLS la hace
     * fallar. Es la misma envoltura que usa {@code SinNormativaFronteraTest}.
     */
    @SuppressWarnings("unchecked")
    private static <T> T envolver(T objetivo, TenantTransactionManager gestor) {
        ProxyFactory fabrica = new ProxyFactory(objetivo);
        fabrica.setProxyTargetClass(true);
        fabrica.addAdvice(
                new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        return (T) fabrica.getProxy();
    }

    @AfterAll
    static void cerrar() {
        if (base != null) {
            base.close();
        }
    }

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
    }

    // ------------------------------------------------------------------
    // AC-4 — el aislamiento, y la guarda de que hubo sujeto
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cada municipalidad ve los suyos y ninguno de la vecina")
    void cadaUnaVeLosSuyos() {
        List<CacheDeSnapshots.ConjuntoCacheado> deA = comoLa(laQueConsulta);
        List<CacheDeSnapshots.ConjuntoCacheado> deB = comoLa(laVecina);

        // PRIMERO el sujeto. «A no ve los de B» es cierto sobre dos listas vacias, asi que sin
        // esto una siembra que fallara en silencio dejaria el aislamiento sin medir — que es el
        // defecto que esta serie lleva seis veces encontrando.
        assertThat(deA)
                .as(
                        "la municipalidad que consulta tiene que tener conjuntos propios: sin"
                                + " ninguno, todo lo de abajo se cumple sobre el vacio y esta"
                                + " prueba no habria medido el aislamiento, solo la ausencia")
                .isNotEmpty();
        assertThat(deB)
                .as(
                        "y la vecina tambien: sin filas suyas, «A no ve las de B» es cierto porque"
                                + " no hay ninguna que ver")
                .isNotEmpty();

        assertThat(anios(deA))
                .as("los dos ejercicios de A, y ninguno de los que solo tiene B (2024 y 2023)")
                .containsExactly(2026, 2025);
        assertThat(anios(deB))
                .as("y los tres de B, incluido su propio 2026, sin el 2025 que solo tiene A")
                .containsExactly(2026, 2024, 2023);

        // La mitad que la lista de anios NO puede ver. Las dos tienen 2026, asi que una fuga
        // dejaria el ano en su sitio y cambiaria el CONJUNTO: sin la politica RLS, el `DISTINCT
        // ON` de B se quedaria con el conjunto de A, que es el de version mayor.
        assertThat(conjuntoDe(deB, 2026))
                .as(
                        "el 2026 de la vecina tiene que ser EL SUYO. Si saliera %s —el de A,"
                                + " version 2— la lista de anios seguiria diciendo lo mismo y la"
                                + " fuga no se veria",
                        elConjuntoDe2026DeA)
                .isEqualTo(elConjuntoDe2026DeB)
                .isNotEqualTo(elConjuntoDe2026DeA);
        assertThat(conjuntoDe(deA, 2026)).isEqualTo(elConjuntoDe2026DeA);
    }

    private static long conjuntoDe(List<CacheDeSnapshots.ConjuntoCacheado> lista, int anio) {
        return lista.stream()
                .filter(c -> c.ejercicio().valor() == anio)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hay ningun ejercicio " + anio))
                .conjuntoId();
    }

    @Test
    @DisplayName("y la premisa se cuenta fuera de la lectura que se mide")
    void laPremisaSeCuentaFueraDeLaLectura() throws SQLException {
        // Contar con la MISMA consulta que se prueba seria circular: si el `DISTINCT ON` se
        // rompiera devolviendo cero filas, la guarda de arriba y la respuesta caerian juntas y el
        // mensaje hablaria de la siembra en vez de la consulta.
        //
        // Se cuenta con la conexion de SUPERUSUARIO, que es la unica que omite RLS aun con `FORCE
        // ROW LEVEL SECURITY` (primer hallazgo de RLS) — y por eso mismo es la unica que NO se usa
        // para medir el aislamiento: `kamayuk_owner` tampoco vale, porque con `FORCE` el dueno
        // queda sujeto a la politica y sin contexto la consulta muere con «unrecognized
        // configuration parameter "app.municipalidad_id"». Medido al escribir esta clase.
        assertThat(filasDeLaTabla(laQueConsulta))
                .as("las filas que la siembra dejo para A, contadas sin pasar por el `DISTINCT ON`")
                .isEqualTo(5);
        assertThat(filasDeLaTabla(laVecina)).isEqualTo(3);
        assertThat(filasDeLaTabla(recienImplantada))
                .as("la recien implantada no tiene ninguna, que es lo que la hace util")
                .isZero();
    }

    // ------------------------------------------------------------------
    // AC-2 — la lista vacia es una respuesta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("una municipalidad recien implantada recibe una lista vacia, no un error")
    void laRecienImplantadaRecibeVacio() {
        assertThat(comoLa(recienImplantada))
                .as(
                        "no tiene ningun conjunto descargado y eso es una respuesta cierta: la ruta"
                                + " existe. Traducirlo a 404 diria que no hay donde preguntar, que"
                                + " es otra cosa y se arregla en otro sitio (C-5 §2.1)")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // AC-1 — el orden, el colapso y los ambitos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("del mas reciente al mas antiguo, que es como se ofrece un desplegable")
    void delMasRecienteAlMasAntiguo() {
        List<CacheDeSnapshots.ConjuntoCacheado> deA = comoLa(laQueConsulta);

        assertThat(deA).as(SIN_SUJETO).isNotEmpty();
        assertThat(anios(deA))
                .as(
                        "invertirlo no rompe nada visible —la lista sigue teniendo los mismos"
                                + " anios— y deja el ejercicio en curso al final del desplegable,"
                                + " donde nadie lo busca")
                .isSortedAccordingTo(Comparator.reverseOrder())
                .containsExactly(2026, 2025);
    }

    @Test
    @DisplayName("un elemento por ejercicio, y es el conjunto que rige")
    void unElementoPorEjercicio() {
        List<CacheDeSnapshots.ConjuntoCacheado> deA = comoLa(laQueConsulta);

        // La lista con sujeto, primero: sin esto un `getFirst()` sobre la lista vacia sale con un
        // `NoSuchElementException` pelado, que dice donde reviento y no que faltaba. Medido con la
        // rotura del contraste, que es la que lo destapo.
        assertThat(deA).as(SIN_SUJETO).isNotEmpty();
        assertThat(anios(deA))
                .as(
                        "A tiene DOS conjuntos de 2026 —version 1 y version 2— y cinco filas,"
                                + " porque cada conjunto tiene una por ambito. Sin colapsar, el"
                                + " desplegable ensenaria «2026» tres veces")
                .doesNotHaveDuplicates();

        CacheDeSnapshots.ConjuntoCacheado elDe2026 = deA.getFirst();
        assertThat(elDe2026.ejercicio()).isEqualTo(new Ejercicio(2026));
        assertThat(elDe2026.version())
                .as("de las dos versiones selladas del 2026 rige la mayor")
                .isEqualTo(2);
        assertThat(elDe2026.conjuntoId()).isEqualTo(CONJUNTO_DE_2026_DE_A);
    }

    @Test
    @DisplayName("el conjunto que la lista publica es el que el calculo tomaria")
    void elMismoConjuntoQueTomaElCalculo() {
        // El desempate esta escrito UNA vez y lo usan las dos consultas, pero una constante
        // compartida garantiza que el TEXTO sea el mismo, no que las dos lo usen igual: el
        // `DISTINCT ON` y el `LIMIT 1` son mecanismos distintos. Esto lo mide.
        List<CacheDeSnapshots.ConjuntoCacheado> deA = comoLa(laQueConsulta);
        assertThat(deA).as(SIN_SUJETO).isNotEmpty();

        for (CacheDeSnapshots.ConjuntoCacheado publicado : deA) {
            TenantContext.fijar(new MunicipalidadId(laQueConsulta));
            long queTomariaElCalculo =
                    enUnaTransaccionDe(
                            laQueConsulta,
                            () -> cache.conjuntoCacheadoDe(publicado.ejercicio()).orElseThrow());
            assertThat(queTomariaElCalculo)
                    .as(
                            "si divergieran, la interfaz ofreceria el ejercicio %s y el calculo"
                                    + " tomaria otro conjunto, sin que nada lo dijera",
                            publicado.ejercicio().valor())
                    .isEqualTo(publicado.conjuntoId());
        }
    }

    @Test
    @DisplayName("y dice que mitades del snapshot estan descargadas")
    void diceQueMitadesEstanDescargadas() {
        List<CacheDeSnapshots.ConjuntoCacheado> deA = comoLa(laQueConsulta);

        assertThat(deA).as(SIN_SUJETO).hasSize(2);
        assertThat(deA.getFirst().ambitos())
                .as("el 2026 que rige tiene las dos mitades descargadas")
                .containsExactly("OBLIGACION", "VALUACION");
        assertThat(deA.get(1).ambitos())
                .as(
                        "y el 2025 solo la de la obligacion: esta sellado, y los dos cuadros de la"
                                + " valuacion NO estan aqui. Callarlo dejaria a la lista"
                                + " prometiendo unos valores unitarios que este sistema no tiene")
                .containsExactly("OBLIGACION");
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static List<Integer> anios(List<CacheDeSnapshots.ConjuntoCacheado> conjuntos) {
        return conjuntos.stream().map(c -> c.ejercicio().valor()).toList();
    }

    /** La lectura de produccion, con el contexto de esa municipalidad puesto. */
    private static List<CacheDeSnapshots.ConjuntoCacheado> comoLa(long municipalidad) {
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        return sellados.todos();
    }

    /**
     * Una lectura del repositorio desde la prueba, dentro de su transaccion.
     *
     * <p>El repositorio no abre transacciones —ninguno lo hace (regla 2, {@code RepositorioJdbc})—
     * y sin transaccion no hay {@code SET LOCAL}, de modo que la politica RLS no encontraria {@code
     * app.municipalidad_id} y la consulta reventaria.
     */
    private static <T> T enUnaTransaccionDe(long municipalidad, Supplier<T> que) {
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        return enUnaTransaccion.execute(estado -> que.get());
    }

    private static long elConjuntoSembradoDe(long municipalidad) throws SQLException {
        try (Connection sinRls = base.conexionAdmin();
                PreparedStatement sentencia =
                        sinRls.prepareStatement(
                                "SELECT conjunto_id FROM normativa_conjunto"
                                        + " WHERE municipalidad_id = ?")) {
            sentencia.setLong(1, municipalidad);
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }

    private static int filasDeLaTabla(long municipalidad) throws SQLException {
        try (Connection sinRls = base.conexionAdmin();
                PreparedStatement sentencia =
                        sinRls.prepareStatement(
                                "SELECT count(*) FROM normativa_conjunto"
                                        + " WHERE municipalidad_id = ?")) {
            sentencia.setLong(1, municipalidad);
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getInt(1);
            }
        }
    }

    /** Una fila mas de la copia local, escrita como {@code kamayuk_app} y con su contexto. */
    private static void anadirConjunto(
            long municipalidad, long conjuntoId, int ejercicio, int version, String ambito)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (PreparedStatement sentencia =
                    app.prepareStatement(
                            "INSERT INTO normativa_conjunto (municipalidad_id, conjunto_id,"
                                    + " ejercicio, version, ambito, sha256, filas, origen,"
                                    + " descargado_en) VALUES (?, ?, ?, ?, ?, repeat('0', 64), 1,"
                                    + " 'fixture de #51', now())")) {
                sentencia.setLong(1, municipalidad);
                sentencia.setLong(2, conjuntoId);
                sentencia.setInt(3, ejercicio);
                sentencia.setInt(4, version);
                sentencia.setString(5, ambito);
                sentencia.executeUpdate();
            }
            app.commit();
        }
    }
}
