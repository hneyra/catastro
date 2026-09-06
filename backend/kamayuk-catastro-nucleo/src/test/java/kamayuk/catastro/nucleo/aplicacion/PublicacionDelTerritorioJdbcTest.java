package kamayuk.catastro.nucleo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.nucleo.dominio.BuzonDeSalida;
import kamayuk.catastro.nucleo.dominio.EventoDeCatastro;
import kamayuk.catastro.nucleo.dominio.TipoDeEventoDeCatastro;
import kamayuk.catastro.nucleo.infraestructura.BuzonDeSalidaJdbc;
import kamayuk.catastro.nucleo.infraestructura.ComponedorDeHechos;
import kamayuk.catastro.nucleo.infraestructura.TerritorioParaPublicarJdbc;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * El buzon del territorio, contra PostgreSQL de verdad y como {@code kamayuk_app} (#7, AC 5 y AC
 * 8).
 *
 * <h2>Lo que estas pruebas miden, y por que hay que medirlo aqui</h2>
 *
 * <p>Que la identidad de un hecho se derive de donde tiene que derivarse no se ve en una firma: se
 * ve en <b>cuantas filas hay en el buzon</b> despues de publicar dos veces. Y las dos direcciones
 * importan y se rompen distinto:
 *
 * <ul>
 *   <li>Un frente o una manzana que no cambiaron y se republican tienen que producir <b>un</b>
 *       evento y no dos. Derivada de un identificador —o peor, aleatoria—, cada corrida diaria
 *       mandaria el padron entero otra vez y el receptor lo aplicaria fila a fila para acabar
 *       escribiendo lo mismo.
 *   <li>Un frente que <b>si</b> cambio —confirmar su longitud es un cambio, y de los que mas pesan—
 *       tiene que producir uno nuevo. Con la identidad derivada de menos campos de los que el hecho
 *       tiene, esa confirmacion no llegaria nunca y {@code rentas} seguiria determinando sobre
 *       metros que una maquina propuso.
 * </ul>
 *
 * <p>Y el hallazgo firme al reves que los dos anteriores: su identidad sale del hallazgo, asi que
 * el mismo hallazgo con otro contenido <b>se para</b> en vez de publicarse encima.
 *
 * <p>Como {@code PublicacionDelPadronJdbcTest}: conectado como {@code kamayuk_app} y no como el
 * dueno, porque con {@code FORCE ROW LEVEL SECURITY} el dueno tambien queda sujeto a la politica
 * pero tiene privilegios que la aplicacion no tiene (#537, #545).
 */
@DisplayName("#7 — El buzon del territorio, contra PostgreSQL")
class PublicacionDelTerritorioJdbcTest {

    private static final Instant RELOJ = Instant.parse("2026-09-06T12:00:00Z");

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static TenantTransactionManager gestor;
    private static PublicacionDelTerritorio publicacion;
    private static BuzonDeSalida buzon;
    private static EntregaDeEventos entrega;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "240601", "La del territorio");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidad, parametroId, "TE");

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        gestor = new TenantTransactionManager(pool);
        JdbcClient jdbc = JdbcClient.create(pool);

        buzon = new BuzonDeSalidaJdbc(jdbc, Clock.fixed(RELOJ, ZoneOffset.UTC));
        entrega = envolver(new EntregaDeEventos(buzon));
        publicacion =
                new PublicacionDelTerritorio(
                        envolver(
                                new LecturaDelTerritorioParaPublicar(
                                        new TerritorioParaPublicarJdbc(jdbc))),
                        envolver(new PublicarUnHecho(buzon)),
                        new ComponedorDeHechos(mapa()));
    }

    @AfterAll
    static void cerrar() {
        if (base != null) {
            base.close();
        }
    }

    @BeforeEach
    void elBuzonVacio() throws SQLException {
        // El buzon se vacia entre pruebas: cada una cuenta cuantos hechos produjo, y `V5` no deja
        // borrar a la aplicacion (ni debe). Por eso limpia el administrador y no `kamayuk_app`.
        limpiarComoAdmin("DELETE FROM catastro_evento");
    }

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
    }

    @Test
    @DisplayName("AC 8 — publicar dos veces lo mismo produce UN evento y no dos")
    void publicarDosVecesProduceUnSoloEvento() {
        TenantContext.fijar(new MunicipalidadId(municipalidad));

        PublicacionDelTerritorio.Informe primera = publicacion.publicar();
        PublicacionDelTerritorio.Informe segunda = publicacion.publicar();

        assertThat(primera.frentesNuevos())
                .as("el predio sembrado tiene un frente: un hecho")
                .isEqualTo(1);
        assertThat(primera.manzanasNuevas()).as("y una manzana: otro").isEqualTo(1);
        assertThat(segunda.frentesNuevos())
                .as(
                        "la identidad de un frente se deriva de su CONTENIDO: republicar lo que no"
                                + " cambio produce EL MISMO identificador, `catastro_evento_uq` lo"
                                + " deduplica y no se escribe una segunda fila")
                .isZero();
        assertThat(segunda.manzanasNuevas()).isZero();
        assertThat(deTipo(TipoDeEventoDeCatastro.FRENTE_PUBLICADO)).hasSize(1);
        assertThat(deTipo(TipoDeEventoDeCatastro.MANZANA_PUBLICADA)).hasSize(1);
    }

    @Test
    @DisplayName("EL CONTRASTE: confirmar la longitud SI produce un hecho nuevo")
    void confirmarLaLongitudProduceUnHechoNuevo() throws SQLException {
        // Sin este caso, la prueba de arriba pasaria con una identidad derivada del `predio_id` a
        // secas —que nunca cambia—, y entonces ningun cambio de un frente llegaria jamas a
        // `rentas`: seguiria determinando sobre los metros que una maquina propuso, sin saberlo.
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        publicacion.publicar();

        confirmarElFrenteAMano();
        PublicacionDelTerritorio.Informe despues = publicacion.publicar();

        assertThat(despues.frentesNuevos())
                .as(
                        "confirmar es exactamente el cambio que tiene que viajar: separa una cifra"
                                + " con la que se puede determinar de una con la que no (ADR-0021)")
                .isEqualTo(1);
        List<EventoDeCatastro> publicados = deTipo(TipoDeEventoDeCatastro.FRENTE_PUBLICADO);
        assertThat(publicados).hasSize(2);
        assertThat(publicados.get(0).cuerpo()).contains("PROPUESTA");
        assertThat(publicados.get(1).cuerpo())
                .as("y el estado viaja dentro del cuerpo, que es donde `rentas` lo lee")
                .contains("CONFIRMADA")
                .contains("\"longitud\": \"33.00 ML\"");
    }

    @Test
    @DisplayName("un frente no lleva ejercicio, y una manzana no lleva ni ejercicio ni predio")
    void laFormaDeCadaHechoEsLaQueV10Declara() {
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        publicacion.publicar();

        EventoDeCatastro frente = deTipo(TipoDeEventoDeCatastro.FRENTE_PUBLICADO).get(0);
        EventoDeCatastro manzana = deTipo(TipoDeEventoDeCatastro.MANZANA_PUBLICADA).get(0);

        assertThat(frente.predioId()).as("los frentes son DE un predio").isNotNull();
        assertThat(frente.ejercicio())
                .as(
                        "el frente medido en 2026 sigue siendo el mismo en 2027: versionarlo por"
                                + " ano seria una decision tributaria (ADR-0024)")
                .isNull();
        assertThat(manzana.predioId()).isNull();
        assertThat(manzana.ejercicio())
                .as(
                        "con los CHECK de `V5` escritos en negativo, esta fila no se habria podido"
                                + " insertar: exigian ejercicio a todo lo que no fuera"
                                + " PREDIO_PROYECTADO")
                .isNull();
    }

    @Test
    @DisplayName("un hallazgo firme viaja con su inspector, y sin un solo importe")
    void elHallazgoFirmeViajaConSuInspector() {
        TenantContext.fijar(new MunicipalidadId(municipalidad));

        PublicacionDelTerritorio.Informe informe = publicacion.publicar();

        assertThat(informe.hallazgosNuevos())
                .as("la siembra deja un hallazgo FIRME (#6)")
                .isEqualTo(1);
        EventoDeCatastro hallazgo = deTipo(TipoDeEventoDeCatastro.HALLAZGO_FIRME).get(0);
        assertThat(hallazgo.cuerpo())
                .contains("\"inspector\"")
                .contains("\"verificadoEn\"")
                .contains("\"areaVerificada\"");
        assertThat(hallazgo.cuerpo())
                .as(
                        "ni un importe: un hallazgo informa una diferencia de superficie, y cuanto"
                                + " se cobra por ella lo decide `rentas` (ADR-0024)")
                .doesNotContain("importe")
                .doesNotContain("S/");
        assertThat(hallazgo.ejercicio()).as("un hallazgo no es de ningun ejercicio").isNull();
    }

    @Test
    @DisplayName("y el mismo hallazgo con otro contenido SE PARA: es un acto que alguien firmo")
    void elMismoHallazgoConOtroContenidoSePara() throws SQLException {
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        publicacion.publicar();

        // Alguien cambia el area verificada de un hallazgo YA PUBLICADO. Con la identidad derivada
        // del contenido esto seria «otro hecho» y `rentas` lo aplicaria encima sin decir nada; con
        // la identidad derivada del hallazgo, es lo que es: la reescritura de un acto firmado.
        ejecutarComoApp("UPDATE hallazgo SET area_verificada = area_verificada + 1");

        assertThatThrownBy(() -> publicacion.publicar())
                .isInstanceOf(BuzonDeSalida.HechoSelladoReescrito.class)
                .hasMessageContaining("HALLAZGO_FIRME")
                .hasMessageContaining("ya se publico con la huella")
                // Y EL MENSAJE NOMBRA EL HECHO QUE ES. Hasta #7 decia «Una valuacion es un HECHO
                // SELLADO» para cualquier tipo —era cierto: solo la valuacion podia llegar aqui—,
                // y con un segundo tipo firmado ese diagnostico manda a quien atiende a mirar la
                // corrida de valuacion por un acta de fiscalizacion. Lo destapo una de las roturas
                // de AC 8.
                .hasMessageContaining("Un hallazgo firme es lo que una PERSONA verifico")
                .hasMessageContaining("dejarlo sin efecto y levantar otro")
                .hasMessageNotContaining("Una valuacion es un HECHO SELLADO");
    }

    @Test
    @DisplayName("y la municipalidad vecina no publica nada de esta: lo acota la politica")
    void elAislamientoSeSostiene() throws SQLException, IOException {
        long vecina = DatosDePrueba.crearMunicipalidad(base, "240602", "La vecina");
        DatosDePrueba.sembrarTenant(base, vecina, DatosDePrueba.crearParametroNacional(base), "VT");
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        publicacion.publicar();

        TenantContext.limpiar();
        TenantContext.fijar(new MunicipalidadId(vecina));
        PublicacionDelTerritorio.Informe deLaVecina = publicacion.publicar();

        assertThat(deLaVecina.manzanasLeidas())
                .as("la vecina tiene lo suyo, y solo lo suyo")
                .isEqualTo(1);
        assertThat(deLaVecina.manzanasNuevas())
                .as(
                        "y son hechos NUEVOS: la identidad lleva la municipalidad dentro, asi que"
                                + " dos manzanas con el mismo codigo en dos municipalidades no se"
                                + " deduplican entre si")
                .isEqualTo(1);
    }

    // ── Fixtures ───────────────────────────────────────────────────────

    /**
     * Confirma la longitud del frente sembrado por SQL.
     *
     * <p>Por SQL y no por el caso de uso a proposito: lo que esta prueba mide es el buzon, y
     * meterle {@code ConfirmarElFrente} por delante haria que un fallo del buzon se leyera como un
     * fallo de la confirmacion. El acto tiene su propia prueba.
     */
    private void confirmarElFrenteAMano() throws SQLException {
        ejecutarComoApp(
                "UPDATE frente_predio SET longitud_m = 33.00, longitud_estado = 'CONFIRMADA',"
                        + " confirmado_por = 'tecnico.catastro', confirmado_en = now()");
    }

    private List<EventoDeCatastro> deTipo(TipoDeEventoDeCatastro tipo) {
        List<EventoDeCatastro> suyos = new ArrayList<>();
        for (EventoDeCatastro evento : entrega.pendientes(500)) {
            if (evento.tipo() == tipo) {
                suyos.add(evento);
            }
        }
        return suyos;
    }

    private static void ejecutarComoApp(String sql) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement()) {
                sentencia.executeUpdate(sql);
            }
            app.commit();
        }
    }

    /** Lo que la aplicacion no puede hacer y la fixture si: vaciar el buzon (regla 4). */
    private static void limpiarComoAdmin(String sql) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                Statement sentencia = admin.createStatement()) {
            sentencia.executeUpdate(sql);
        }
    }

    private static JsonMapper mapa() {
        return JsonMapper.builder()
                .addModule(new kamayuk.catastro.web.ConfiguracionDeJson().moduloDeObjetosDeValor())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T envolver(T objetivo) {
        // OBEDECIENDO A LA ANOTACION, como el contenedor: un `TransactionTemplate` incondicional
        // dejaria estas pruebas pasando con el `@Transactional` quitado (#535, #569).
        ProxyFactory fabrica = new ProxyFactory(objetivo);
        fabrica.setProxyTargetClass(true);
        fabrica.addAdvice(
                new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        return (T) fabrica.getProxy();
    }
}
