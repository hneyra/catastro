package kamayuk.catastro.nucleo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.Ejercicio;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.nucleo.dominio.BuzonDeSalida;
import kamayuk.catastro.nucleo.dominio.EventoDeCatastro;
import kamayuk.catastro.nucleo.dominio.HuellaDelHecho;
import kamayuk.catastro.nucleo.dominio.TipoDeEventoDeCatastro;
import kamayuk.catastro.nucleo.infraestructura.BuzonDeSalidaJdbc;
import kamayuk.catastro.nucleo.infraestructura.ComponedorDeHechos;
import kamayuk.catastro.nucleo.infraestructura.PadronParaPublicarJdbc;
import kamayuk.catastro.nucleo.infraestructura.web.EventoResource;
import kamayuk.catastro.parametros.LectorDeParametros;
import kamayuk.catastro.parametros.aplicacion.LectorDeParametrosCacheados;
import kamayuk.catastro.parametros.infraestructura.CacheDeSnapshotsJdbc;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * El emisor de C-8, contra PostgreSQL de verdad y conectado como {@code kamayuk_app}.
 *
 * <h2>Y ademas PUBLICA EL LOTE que `rentas` lee</h2>
 *
 * <p>La ultima prueba escribe {@code docs/50-api/eventos/lote-de-eventos.json} con la serializacion
 * REAL de este lado —el mismo {@code JsonMapper} que la aplicacion registra, los mismos {@code
 * record}s que el controlador devuelve— y {@code rentas} lo lee tal cual en su propia prueba de
 * ingestion.
 *
 * <p>Es el mecanismo de los vectores de oro de la huella (P6 §4.2) aplicado al transporte, y con el
 * mismo reparto: <b>lo publica quien emite, y solo quien emite</b>. Si el consumidor pudiera
 * regenerarlo, quien cambiara la forma del evento regeneraria el archivo y el rojo se convertiria
 * en un diff que alguien acepta.
 *
 * <p>Lo que ese archivo hace posible medir, y no se puede medir de otra manera sin levantar los dos
 * procesos: que la <b>huella agregada</b> que este lado calcula en Java sea la que {@code rentas}
 * calcula en SQL. Si no coincidieran, el candado de ADR-0027 §2 no fallaria ruidosamente: se
 * cerraria SIEMPRE, y la emision quedaria bloqueada por un defecto de codigo que se lee como uno de
 * datos.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PublicacionDelPadronJdbcTest {

    /**
     * Donde vive el lote que `rentas` lee, relativo a la RAIZ del repositorio.
     *
     * <p>Se resuelve subiendo desde el directorio de trabajo hasta encontrar {@code docs/50-api} y
     * no con un numero fijo de «..»: el corredor de Gradle arranca en el directorio del modulo y
     * escribirlo a mano dejo el archivo en {@code backend/docs/}, donde nadie lo iba a buscar. Lo
     * encontro la primera ejecucion.
     */
    private static final Path LOTE_PUBLICADO =
            Path.of("docs", "50-api", "eventos", "lote-de-eventos.json");

    private static final Instant RELOJ = Instant.parse("2026-03-01T10:00:00Z");

    /**
     * La fecha de corte de la corrida.
     *
     * <p>Dentro de la vigencia de la ficha del escenario, que empieza el 2026-01-01. Con el
     * 2025-12-31 —el corte que uno teclea por instinto— la valuacion sale «el predio no tiene ficha
     * catastral vigente», que es <b>correcto</b> y no es lo que esta prueba mide. Lo dijo la
     * primera ejecucion.
     */
    private static final LocalDate CORTE = LocalDate.of(2026, 1, 1);

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;

    /**
     * Una segunda municipalidad, solo para el lote publicado.
     *
     * <p>Las cinco pruebas de arriba comparten buzon y lo dejan con varias corridas dentro —cada
     * una emite su cierre, porque dos corridas del mismo ejercicio SON dos hechos aunque produzcan
     * el mismo resultado—. El archivo que `rentas` lee tiene que ser el de una corrida limpia, asi
     * que se publica desde una municipalidad que no ha corrido nada.
     */
    private static long municipalidadDelLote;

    /**
     * Una tercera, y la UNICA a la que se le siembra el «% actualizacion» que espera firma.
     *
     * <p>Va aparte porque su premisa no se cumple hoy: el archivo del corpus que respaldaria esa
     * llave esta en {@code TRANSCRITO}. Sembrarla para todas dejaria el padron pareciendo
     * valorizable, que es lo contrario de lo que este repositorio mide.
     *
     * <p>Y tiene que ser una que no haya corrido: una valuacion es un hecho SELLADO, asi que volver
     * a correr el mismo ejercicio con otro resultado lo rechaza el buzon (ADR-0027 §1). Lo dijo la
     * primera ejecucion de ese caso.
     */
    private static long municipalidadConLaLlave;

    private static PublicacionDelPadron publicacion;
    private static BuzonDeSalida buzon;
    private static EntregaDeEventos entrega;
    private static TenantTransactionManager gestor;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "202201", "Municipalidad A");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidad, parametroId, "PA");
        municipalidadDelLote = DatosDePrueba.crearMunicipalidad(base, "202202", "Municipalidad B");
        DatosDePrueba.sembrarTenant(base, municipalidadDelLote, parametroId, "PB");
        municipalidadConLaLlave =
                DatosDePrueba.crearMunicipalidad(base, "202203", "Municipalidad C");
        DatosDePrueba.sembrarTenant(base, municipalidadConLaLlave, parametroId, "PC");
        unSegundoPredio();
        sellarElConjunto();

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        // COMO `kamayuk_app` Y NO COMO EL DUENO. Con `FORCE ROW LEVEL SECURITY` el dueno tambien
        // queda sujeto a la politica, asi que una prueba escrita con `kamayuk_owner` no medira el
        // aislamiento — pero si mediria privilegios que la aplicacion no tiene (#537, #545).
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        gestor = new TenantTransactionManager(pool);
        JdbcClient jdbc = JdbcClient.create(pool);
        JsonMapper json = mapa();

        buzon = new BuzonDeSalidaJdbc(jdbc, Clock.fixed(RELOJ, ZoneOffset.UTC));
        // La cadena de produccion entera: el lector resuelve el conjunto contra `normativa` —aqui
        // el fixture que lo sirve desde las tablas del escenario— y lee el resto de la cache local
        // de `V3`. La descarga va en NULO: el conjunto ya esta cacheado por la fixture, asi que
        // este camino no debe llegar a pedirlo; si llegara, reventaria aqui en vez de contestar
        // con algo inventado.
        LectorDeParametros parametros =
                new LectorDeParametrosCacheados(
                        new CacheDeSnapshotsJdbc(jdbc, Clock.fixed(RELOJ, ZoneOffset.UTC)),
                        new kamayuk.catastro.parametros.infraestructura.NormativaDePrueba(jdbc),
                        null);
        LecturaDelPadronParaPublicar lectura =
                envolver(
                        new LecturaDelPadronParaPublicar(
                                new PadronParaPublicarJdbc(jdbc), parametros));
        PublicarUnHecho publicador = envolver(new PublicarUnHecho(buzon));
        entrega = envolver(new EntregaDeEventos(buzon));
        publicacion =
                new PublicacionDelPadron(
                        lectura,
                        publicador,
                        new ComponedorDeHechos(json),
                        buzon,
                        jdbc,
                        Clock.fixed(RELOJ, ZoneOffset.UTC));
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

    @Test
    @Order(1)
    @DisplayName(
            "proyectar el padron escribe un hecho por predio, y volver a proyectar no escribe ninguno")
    void proyectarEsIdempotente() {
        conContexto();
        PublicacionDelPadron.Informe primera =
                publicacion.proyectarElPadron(LocalDate.of(2026, 3, 1));

        assertThat(primera.leidos()).as("el padron sembrado tiene un predio").isEqualTo(1);
        assertThat(primera.nuevos()).isEqualTo(1);
        assertThat(primera.yaEstaban()).isZero();

        // LA IDENTIDAD DE UNA PROYECCION SE DERIVA DEL CONTENIDO. Volver a proyectar un padron
        // que no cambio produce EL MISMO evento, que `catastro_evento_uq` deduplica: el receptor
        // no recibe 14 422 hechos «nuevos» cada dia para acabar escribiendo lo mismo.
        PublicacionDelPadron.Informe segunda =
                publicacion.proyectarElPadron(LocalDate.of(2026, 3, 1));
        assertThat(segunda.nuevos()).as("nada cambio: no se publica nada").isZero();
        assertThat(segunda.yaEstaban()).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("la corrida de valuacion cierra con su conteo y su huella agregada")
    void laCorridaCierra() {
        conContexto();
        PublicacionDelPadron.Informe informe =
                publicacion.correrLaValuacion(new Ejercicio(2026), CORTE);

        assertThat(informe.corridaId()).as("toda corrida tiene identidad").isNotNull();

        List<EventoDeCatastro> valuaciones = deTipo(TipoDeEventoDeCatastro.VALUACION_PUBLICADA);
        List<EventoDeCatastro> cierres = deTipo(TipoDeEventoDeCatastro.CORRIDA_CERRADA);
        assertThat(valuaciones).as("una valuacion por predio").hasSize(1);
        assertThat(cierres).as("un cierre por corrida").hasSize(1);

        // LA HUELLA AGREGADA ES LA DE LAS HUELLAS DE CADA VALUACION, EN ORDEN DE PREDIO. Es el
        // contrato con `ValuacionRecibidaJdbc.huellaDeLoRecibido`, que la calcula en SQL con
        // `string_agg(huella, ',' ORDER BY predio_id)`.
        List<String> huellas = new ArrayList<>();
        for (EventoDeCatastro valuacion : valuaciones) {
            huellas.add(valuacion.huella());
        }
        // El cuerpo vuelve de la columna `jsonb`, y PostgreSQL lo reserializa CON UN ESPACIO tras
        // los dos puntos: se compara con la forma que el motor devuelve, no con la que Jackson
        // escribio. Es el mismo detalle que #653 encontro con `datos_nuevos` de la auditoria.
        assertThat(cierres.get(0).cuerpo())
                .as("el cierre lleva dentro la huella agregada que este lado calculo")
                .contains(HuellaDelHecho.deUnaCorrida(huellas))
                .contains("\"conteo\": 1");
    }

    @Test
    @Order(3)
    @DisplayName("hoy NINGUN predio se valoriza, y la llave que lo impide es la que espera firma")
    void hoyNingunoSeValoriza() {
        conContexto();
        PublicacionDelPadron.Informe informe =
                publicacion.correrLaValuacion(new Ejercicio(2026), CORTE);

        // ESTE ES EL ESTADO DE HOY, y hay que leerlo junto al caso de abajo.
        //
        // #8 construyo la cuenta —el terreno se valoriza, y las ramas dicen predio a predio que
        // le falta— y publico en `normativa` los dos cuadros nacionales con sus firmas humanas de
        // verdad. Lo que NO llego es la segunda firma del `% actualizacion`: su archivo del corpus
        // sigue en `TRANSCRITO`, asi que ningun conjunto sellado puede traer esa llave y la rama
        // de D-11 sigue siendo la que se alcanza SIEMPRE. La fixture ya no la siembra, porque
        // sembrarla seria describir un estado del mundo que no existe.
        String cuerpo = deTipo(TipoDeEventoDeCatastro.VALUACION_PUBLICADA).get(0).cuerpo();
        assertThat(cuerpo)
                .as("la rama que se alcanza es la de D-11, y nombra su llave")
                .contains("\"llaveQueFalta\": \"PORCENTAJE_DE_ACTUALIZACION\"")
                .contains("\"valorDelPredio\": null");
        assertThat(cuerpo)
                .as("y el motivo dice que lo que falta es una FIRMA, no una cifra que nadie busco")
                .contains("segunda firma")
                .contains("art. 12")
                .contains("no hay valor por omision");
        assertThat(cuerpo)
                .as("y trae los titulares con su cuota vigente a la fecha de corte")
                .contains("\"contribuyenteId\": 900001");

        // El entregable de #8: el informe cuenta por llave, que es lo que convierte «no
        // valoriza» en algo que la direccion puede mirar. Hoy la respuesta es una sola llave.
        assertThat(informe.valorizados()).isZero();
        assertThat(informe.sinValorizar()).isEqualTo(1);
        assertThat(informe.sinCifraPorLlave())
                .containsExactly(
                        java.util.Map.entry(
                                kamayuk.catastro.nucleo.dominio.ValorizacionDelPredio
                                        .PORCENTAJE_DE_ACTUALIZACION,
                                1));
    }

    @Test
    @Order(7)
    @DisplayName("con la llave sellada el motivo deja de ser el mismo para todos: eso espera firma")
    void conLaLlaveSelladaElMotivoEsElDelPredio() throws SQLException {
        // El contrafactual, y va aparte y con su municipalidad propia a proposito: lo que aqui se
        // siembra —el `% actualizacion` de 2026 con su valor neutro— es lo que `normativa` podra
        // sellar EL DIA que una segunda persona firme §1.6 de
        // `predial-porcentaje-de-actualizacion.md`. Hasta entonces esta premisa no se cumple, y
        // por eso no vive en la fixture comun ni en el lote publicado.
        //
        // Corre sobre una TERCERA municipalidad, y no sobre una que ya corrio: reejecutar la
        // corrida de un ejercicio con otro resultado lo rechaza `BuzonDeSalida`, que es ADR-0027
        // §1 funcionando —«El hecho … ya se publico con la huella … y ahora se quiere publicar
        // con …»—. Lo dijo la primera ejecucion de este caso.
        sembrarLaLlaveQueEsperaFirma(municipalidadConLaLlave);

        TenantContext.fijar(new MunicipalidadId(municipalidadConLaLlave));
        PublicacionDelPadron.Informe informe =
                publicacion.correrLaValuacion(new Ejercicio(2026), CORTE);

        // ESTE predio NO llega a valorizarse, y se dice: su construccion no declara las tres
        // partidas que el cuadro suma, asi que cae en la rama 6. Lo que la llave desbloquea aqui
        // no es una cifra sino el DIAGNOSTICO — la corrida deja de decirle a todo el padron lo
        // mismo y pasa a decir lo que le falta a cada predio. Cuantos llegarian a la cifra sobre
        // un padron de verdad lo cuenta `ElPadronDeDemostracionSeValorizaTest`: 4 de 23.
        assertThat(informe.sinCifraPorLlave())
                .as("con la llave puesta, el motivo deja de ser el mismo para todos")
                .containsExactly(java.util.Map.entry(PublicacionDelPadron.SIN_LLAVE, 1));
        // Y el evento lo dice con todas las letras. Se lee el UNICO que esta municipalidad
        // publico —`pendientes` corre bajo su contexto de tenant, asi que no ve los de las otras
        // dos— y se comprueba las DOS cosas: que el motivo es el del predio, y que la llave que
        // hoy para al padron entero ya no aparece. La segunda es la que mide el cambio: sin ella,
        // un motivo cualquiera pasaria por bueno.
        assertThat(deTipo(TipoDeEventoDeCatastro.VALUACION_PUBLICADA))
                .singleElement()
                .extracting(
                        EventoDeCatastro::cuerpo,
                        org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("no declara la categoria de «TECHOS»")
                .doesNotContain("PORCENTAJE_DE_ACTUALIZACION");
    }

    /**
     * Siembra en la cache local la llave que espera la segunda firma de ADR-0007.
     *
     * <p>Con su valor neutro —CERO, medido contra una determinacion real del SRTM (#437)— y su
     * documento fuente, que es el art. 12 del TUO LTM. <b>No es un valor por omision</b>: es lo que
     * §1.6 del corpus razona para 2026 y que hoy no se puede publicar porque nadie lo firmo.
     */
    private static void sembrarLaLlaveQueEsperaFirma(long muni) throws SQLException {
        try (Connection admin = base.conexionAdmin()) {
            admin.setAutoCommit(false);
            try (PreparedStatement sentencia =
                    admin.prepareStatement(
                            "INSERT INTO normativa_parametro (municipalidad_id, conjunto_id, tipo,"
                                    + " clave, valor_numerico, vigencia_desde, vigencia_hasta,"
                                    + " documento_fuente)"
                                    + " SELECT c.municipalidad_id, c.conjunto_id,"
                                    + "        'PORCENTAJE_DE_ACTUALIZACION', NULL, 0.000000,"
                                    + "        DATE '2026-01-01', DATE '2026-12-31',"
                                    + "        'TUO de la Ley de Tributacion Municipal, art. 12'"
                                    + "   FROM normativa_conjunto c"
                                    + "  WHERE c.municipalidad_id = ?")) {
                sentencia.setLong(1, muni);
                sentencia.executeUpdate();
            }
            admin.commit();
        }
    }

    @Test
    @Order(4)
    @DisplayName("un ejercicio sin conjunto sellado no corre, y lo dice en vez de inventarse uno")
    void sinConjuntoSelladoNoCorre() {
        conContexto();
        assertThatThrownBy(
                        () ->
                                publicacion.correrLaValuacion(
                                        new Ejercicio(2099), LocalDate.of(2099, 12, 31)))
                .as("es el estado de HOY en toda municipalidad (D-02a)")
                .isInstanceOf(LectorDeParametros.EjercicioSinSellar.class);
    }

    @Test
    @Order(6)
    @DisplayName("el buzon se acusa, y acusar dos veces no mueve la hora de entrega")
    void elAcuseEsIdempotente() throws SQLException {
        conContexto();
        publicacion.proyectarElPadron(LocalDate.of(2026, 3, 1));

        List<EventoDeCatastro> pendientes = entrega.pendientes(10);
        assertThat(pendientes).isNotEmpty();
        List<java.util.UUID> ids = new ArrayList<>();
        for (EventoDeCatastro evento : pendientes) {
            ids.add(evento.eventoId());
        }

        Instant primera = Instant.parse("2026-03-01T11:00:00Z");
        assertThat(entrega.marcarEntregados(ids, primera)).isEqualTo(pendientes.size());
        // El segundo acuse marca CERO: el `AND estado = 'PENDIENTE'` es lo que impide que la hora
        // de entrega pase a ser la del ultimo acuse en vez de la de la entrega.
        assertThat(entrega.marcarEntregados(ids, Instant.parse("2026-03-01T12:00:00Z"))).isZero();
        assertThat(horaDeEntrega(ids.get(0))).isEqualTo(primera);
        assertThat(entrega.pendientesQueQuedan()).isZero();
    }

    @Test
    @Order(5)
    @DisplayName("y publica el lote que `rentas` lee, con la serializacion de este lado")
    void publicaElLoteQueRentasLee() throws IOException {
        TenantContext.fijar(new MunicipalidadId(municipalidadDelLote));
        publicacion.proyectarElPadron(LocalDate.of(2026, 3, 1));
        publicacion.correrLaValuacion(new Ejercicio(2026), CORTE);

        List<EventoDeCatastro> pendientes = entrega.pendientes(500);
        assertThat(pendientes)
                .as("DOS predios proyectados, sus dos valuaciones y el cierre de la corrida")
                .hasSize(5);

        List<EventoResource> recursos = new ArrayList<>();
        for (EventoDeCatastro evento : pendientes) {
            recursos.add(EventoResource.de(evento));
        }
        JsonMapper json = mapa();
        String lote =
                json.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(
                                new kamayuk.catastro.nucleo.infraestructura.web.EventosController
                                        .LoteDeEventosResource(
                                        List.copyOf(recursos), 0, "2026-03-01T11:00:00Z"));

        Path archivo = raizDelRepositorio().resolve(LOTE_PUBLICADO);
        Files.createDirectories(archivo.getParent());
        Files.writeString(archivo, lote + System.lineSeparator(), StandardCharsets.UTF_8);

        assertThat(lote)
                .as("el lote lleva los tres tipos, que es lo que el ingestor tiene que saber leer")
                .contains("PREDIO_PROYECTADO")
                .contains("VALUACION_PUBLICADA")
                .contains("CORRIDA_CERRADA");
        // Y sus DOS valuaciones siguen saliendo con los cuatro valores en `null` y con la misma
        // llave, que es el estado de HOY y lo que este archivo tiene que enseñar.
        //
        // catastro#8 llego a dejarlo con las dos formas —un predio con sus cuatro cifras y otro
        // con su motivo— porque la fixture sembraba el `% actualizacion`. Eso se apoyaba en una
        // firma que nadie puso, la direccion lo rechazo, y el archivo vuelve a decir la verdad:
        // este sistema tiene la cuenta construida y no publica ni una cifra, porque le falta una
        // llave que espera una segunda lectura humana (ADR-0007). El dia que llegue, este archivo
        // cambia solo — y ese diff es exactamente la demostracion.
        assertThat(lote)
                .as("las dos valuaciones se paran en la misma rama, y lo dicen")
                .contains("\\\"valorDelPredio\\\": null")
                .contains("\\\"llaveQueFalta\\\": \\\"PORCENTAJE_DE_ACTUALIZACION\\\"");
        assertThat(lote)
                .as("y NO llevan ninguna cifra: o las cuatro o el motivo, nunca a medias")
                .doesNotContain("\\\"valorTerreno\\\": \\\"");
        assertThat(instantesDe(lote))
                .as(
                        "y los «emitidoEn» son TODOS el reloj fijo de esta prueba (C-12). Con el"
                                + " now() de la base que el buzon usaba, este archivo se reescribia en"
                                + " cada corrida del banco —su unico diff eran esos cinco instantes—,"
                                + " de modo que git status salia sucio siempre y tapaba el cambio de"
                                + " forma del evento que el archivo existe para enseñar. El lote: %s",
                        lote)
                .isNotEmpty()
                .containsOnly(RELOJ.toString());
    }

    // ------------------------------------------------------------------

    /** Los instantes que el lote publica, uno por evento. */
    private static List<String> instantesDe(String lote) {
        List<String> instantes = new ArrayList<>();
        java.util.regex.Matcher marca =
                java.util.regex.Pattern.compile("\"emitidoEn\"\\s*:\\s*\"([^\"]+)\"").matcher(lote);
        while (marca.find()) {
            instantes.add(marca.group(1));
        }
        return instantes;
    }

    /**
     * Un SEGUNDO predio en la municipalidad del lote, y hace falta por un motivo medido.
     *
     * <p>La huella agregada de una corrida es {@code String.join(separador, huellas)}, y con UNA
     * sola huella <b>el separador no aparece</b>: {@code join(x)} vale {@code x} se ponga lo que se
     * ponga. Asi que un lote de un predio no puede distinguir la huella de este repositorio de la
     * que {@code rentas} calcula en SQL — y eso es exactamente lo que ese archivo existe para
     * comparar.
     *
     * <p><b>Se descubrio ejecutandolo</b>: con el lote de un predio se cambio el separador de la
     * huella agregada de coma a punto y coma, se republico el lote, y las cinco pruebas de
     * ingestion de {@code rentas} —el candado incluido— <b>siguieron en verde</b>. Es la misma
     * clase de hallazgo que P6 §4.3 con «0 de 0 y 0 de 4 se leen igual» y que #536 con una sola
     * municipalidad en la prueba de plan.
     */
    private static void unSegundoPredio() throws SQLException {
        try (Connection admin = base.conexionAdmin()) {
            admin.setAutoCommit(false);
            long predioId;
            try (PreparedStatement sentencia =
                    admin.prepareStatement(
                            "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo,"
                                    + " via_id, direccion, sector_id, lote)"
                                    + " SELECT ?, '202202000000000002', 'URBANO', v.id,"
                                    + "        'Jr. Union 2', s.id, '02'"
                                    + "   FROM via v, sector s"
                                    + "  WHERE v.municipalidad_id = ? AND s.municipalidad_id = ?"
                                    + "  LIMIT 1"
                                    + " RETURNING id")) {
                sentencia.setLong(1, municipalidadDelLote);
                sentencia.setLong(2, municipalidadDelLote);
                sentencia.setLong(3, municipalidadDelLote);
                try (ResultSet filas = sentencia.executeQuery()) {
                    filas.next();
                    predioId = filas.getLong(1);
                }
            }
            try (PreparedStatement sentencia =
                    admin.prepareStatement(
                            "INSERT INTO ficha_catastral (municipalidad_id, predio_id, tipo,"
                                    + " version, area_terreno, uso, vigencia_desde, origen,"
                                    + " documento_origen, observacion, usuario_registro)"
                                    + " VALUES (?, ?, 'UNICA', 1, 240.00, 'COMERCIO', DATE"
                                    + " '2026-01-01', 'DECLARACION_JURADA', 'DJ-002',"
                                    + " 'segundo predio del lote', 'prueba')")) {
                sentencia.setLong(1, municipalidadDelLote);
                sentencia.setLong(2, predioId);
                sentencia.executeUpdate();
            }
            try (PreparedStatement sentencia =
                    admin.prepareStatement(
                            "INSERT INTO titularidad (municipalidad_id, predio_id,"
                                    + " contribuyente_id, condicion, porcentaje, vigencia_desde,"
                                    + " documento_origen)"
                                    + " VALUES (?, ?, 900002, 'PROPIETARIO_UNICO', 100,"
                                    + "         DATE '2026-01-01', 'MINUTA-002')")) {
                sentencia.setLong(1, municipalidadDelLote);
                sentencia.setLong(2, predioId);
                sentencia.executeUpdate();
            }
            admin.commit();
        }
    }

    /**
     * Sella el conjunto del escenario, como hacen las otras veinte clases de prueba del sistema.
     *
     * <p>{@code sembrarTenant} lo deja ABIERTO a proposito —es el estado en que se cargan sus
     * aranceles— y {@code conjuntoVigenteEn} solo mira los SELLADOS. Sin este paso, la corrida se
     * niega con {@code EjercicioSinSellar}, que es lo que hace la prueba de mas abajo.
     */
    private static void sellarElConjunto() throws SQLException {
        try (Connection admin = base.conexionAdmin();
                PreparedStatement sentencia =
                        admin.prepareStatement(
                                "UPDATE conjunto_parametros_de_prueba SET estado = 'SELLADO'")) {
            sentencia.executeUpdate();
        }
    }

    private static void conContexto() {
        TenantContext.fijar(new MunicipalidadId(municipalidad));
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

    private static Instant horaDeEntrega(java.util.UUID eventoId) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                PreparedStatement sentencia =
                        admin.prepareStatement(
                                "SELECT entregado_en FROM catastro_evento WHERE evento_id = ?")) {
            sentencia.setObject(1, eventoId);
            try (ResultSet filas = sentencia.executeQuery()) {
                filas.next();
                return filas.getTimestamp(1).toInstant();
            }
        }
    }

    /** La raiz del repositorio: el primer directorio hacia arriba que tiene {@code docs/50-api}. */
    private static Path raizDelRepositorio() {
        Path aqui = Path.of("").toAbsolutePath();
        while (aqui != null) {
            if (Files.isDirectory(aqui.resolve("docs").resolve("50-api"))) {
                return aqui;
            }
            aqui = aqui.getParent();
        }
        throw new IllegalStateException(
                "No se encontro la raiz del repositorio desde " + Path.of("").toAbsolutePath());
    }

    /** El mismo mapa que registra la aplicacion: importes y areas como cadena (RNF-055). */
    private static JsonMapper mapa() {
        return JsonMapper.builder()
                .addModule(new kamayuk.catastro.web.ConfiguracionDeJson().moduloDeObjetosDeValor())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T envolver(T objetivo) {
        // OBEDECIENDO A LA ANOTACION, como el contenedor. Un `TransactionTemplate` incondicional
        // dejaria las pruebas pasando con la anotacion quitada, que es el modo de fallo que estas
        // envolturas existen para impedir (#535, #569).
        ProxyFactory fabrica = new ProxyFactory(objetivo);
        fabrica.setProxyTargetClass(true);
        fabrica.addAdvice(
                new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        return (T) fabrica.getProxy();
    }
}
