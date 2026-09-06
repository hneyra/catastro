package kamayuk.catastro.urbano.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.carga.InformeDeImportacion;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import kamayuk.catastro.urbano.infraestructura.UrbanoRepositoryJdbc;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * {@code infra/carga-de-datos/ejemplos/zonificacion.csv} pasa <b>por el cargador de verdad y contra
 * PostgreSQL de verdad</b> (#4, AC-6).
 *
 * <h2>Por que aqui y no en {@code ArchivosDeEjemploTest}</h2>
 *
 * <p>Aquella prueba vive en {@code kamayuk-catastro-nucleo} y no puede ver ni una clase de este
 * modulo: {@code nucleo} no depende de {@code urbano} —ni debe—. Lo que si puede comprobar desde
 * alli, y comprueba, es que el archivo <b>este</b> y que ninguna de sus filas traiga un valor
 * normativo (regla 5). Analizarlo con su analizador es cosa del repositorio de su proceso, que es
 * el mismo argumento con que C-6 movio cada guion al lado de su cargador.
 *
 * <h2>Y por que contra la base y no contra un doble</h2>
 *
 * <p>Porque la afirmacion que importa de este archivo <b>solo la puede juzgar el motor</b>: sus
 * siete zonas son dos planes que se pisan en el suelo, y lo unico que los deja convivir es que el
 * primero este cerrado la vispera del segundo. Con un doble en memoria, un archivo con esa fecha de
 * cierre borrada se cargaria sin protestar y esta prueba pasaria en verde sobre un archivo que en
 * una instalacion de verdad rechaza la mitad de sus filas.
 */
@DisplayName("#4 — El archivo de zonificacion de ejemplo se carga entero, sin una fila rechazada")
class ArchivosDeEjemploDeUrbanoTest {

    /** Palabras de valor normativo: ninguna puede aparecer en una fila de datos (regla 5). */
    private static final List<String> VALORES_NORMATIVOS =
            List.of("arancel", "valor unitario", "valorm2", "depreciacion", "uit", "alicuota");

    /**
     * Una municipalidad por prueba, y lo enseno ejecutar.
     *
     * <p>Las cinco comparten la base, asi que con una sola municipalidad la que corriera segunda
     * encontraba el plan ya cargado y media «0 zonas nuevas» — {@code expected: 7 but was: 0}—. El
     * rojo hablaba del archivo cuando el problema era el orden de las pruebas.
     */
    private static final java.util.concurrent.atomic.AtomicInteger CORRELATIVO =
            new java.util.concurrent.atomic.AtomicInteger();

    private static BaseDeDatosDePrueba base;
    private static ImportarZonificacion importar;

    private long municipalidad;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        JdbcClient jdbc = JdbcClient.create(pool);
        TenantTransactionManager gestor = new TenantTransactionManager(pool);
        importar =
                new ImportarZonificacion(
                        envolver(
                                new RegistrarZonificacion(new UrbanoRepositoryJdbc(jdbc)), gestor));
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @BeforeEach
    void fijarContexto() throws Exception {
        municipalidad =
                DatosDePrueba.crearMunicipalidad(
                        base,
                        String.format("2001%02d", CORRELATIVO.incrementAndGet()),
                        "Municipalidad de Catacaos");
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        OrigenContext.fijar(Origen.deProceso("carga-zonificacion"));
    }

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
        OrigenContext.limpiar();
    }

    @Test
    @DisplayName("el archivo y su guion estan donde el README dice")
    void elArchivoYSuGuionExisten() {
        assertThat(ejemplos().resolve("zonificacion.csv")).isRegularFile();
        assertThat(ejemplos().getParent().resolve("cargar-zonificacion.sh"))
                .as(
                        "el guion vive en el repositorio de su proceso (C-6): lanzado contra la"
                                + " imagen de otro sistema, la aplicacion arranca, no carga nada y"
                                + " sale con codigo 0")
                .isRegularFile();
    }

    @Test
    @DisplayName("se carga entero contra PostgreSQL, y ninguna de sus siete zonas se rechaza")
    void seCargaEntero() throws IOException {
        InformeDeImportacion informe =
                importar.importar(abrir(), Observacion.de("Carga del ejemplo de #4"));

        assertThat(informe.rechazadas())
                .as(
                        "una zona que se pise con un plan vigente saldria aqui, con su motivo: el"
                                + " archivo cierra el PDU-2016 la vispera del PDU-2026 justamente"
                                + " por eso")
                .isEmpty();
        assertThat(informe.nuevas()).isEqualTo(informe.totalFilas()).isGreaterThanOrEqualTo(7);
    }

    @Test
    @DisplayName("volver a cargarlo no duplica ni una zona: es lo que hace `--desde N`")
    void volverACargarloNoDuplica() throws IOException {
        importar.importar(abrir(), Observacion.de("Primera carga del ejemplo de #4"));
        InformeDeImportacion segunda =
                importar.importar(abrir(), Observacion.de("Segunda carga del ejemplo de #4"));

        assertThat(segunda.nuevas()).isZero();
        assertThat(segunda.rechazadas()).isEmpty();
    }

    @Test
    @DisplayName("ninguna fila de dato trae un valor normativo (regla 5)")
    void ningunaFilaTraeUnValorNormativo() throws IOException {
        for (String linea : Files.readAllLines(ejemplos().resolve("zonificacion.csv"))) {
            if (linea.stripLeading().startsWith("#")) {
                continue; // los comentarios explican justamente que no se siembran
            }
            String minuscula = linea.toLowerCase(Locale.ROOT);
            for (String prohibida : VALORES_NORMATIVOS) {
                assertThat(minuscula)
                        .as(
                                "un parametro urbanistico no es un valor normativo, y un valor"
                                        + " normativo inventado no se distingue de uno real")
                        .doesNotContain(prohibida);
            }
        }
    }

    @Test
    @DisplayName("y sus ordenanzas dicen DEMO: una ordenanza inventada tiene que reconocerse")
    void lasOrdenanzasSonDeMentira() throws IOException {
        List<String> datos =
                Files.readAllLines(ejemplos().resolve("zonificacion.csv")).stream()
                        .filter(linea -> !linea.stripLeading().startsWith("#"))
                        .skip(1)
                        .filter(linea -> !linea.isBlank())
                        .toList();

        assertThat(datos).isNotEmpty();
        assertThat(datos)
                .as(
                        "igual que los DNI de contribuyentes.csv: negar una licencia citando una"
                                + " ordenanza que no existe es peor que no citar ninguna")
                .allSatisfy(linea -> assertThat(linea.split(",")[1]).contains("DEMO"));
    }

    // ------------------------------------------------------------------

    private static Reader abrir() throws IOException {
        return Files.newBufferedReader(
                ejemplos().resolve("zonificacion.csv"), StandardCharsets.UTF_8);
    }

    /** {@code infra/carga-de-datos/ejemplos}, buscando la raiz del repositorio hacia arriba. */
    private static Path ejemplos() {
        Path actual = Path.of("").toAbsolutePath();
        while (actual != null) {
            Path candidato = actual.resolve("infra/carga-de-datos/ejemplos");
            if (Files.isDirectory(candidato)) {
                return candidato;
            }
            actual = actual.getParent();
        }
        throw new IllegalStateException(
                "No se encontro infra/carga-de-datos/ejemplos desde "
                        + Path.of("").toAbsolutePath());
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
