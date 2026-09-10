package kamayuk.catastro.seguridad.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kamayuk.catastro.auditoria.AuditoriaJdbc;
import kamayuk.catastro.autorizacion.ComprobadorDeAcceso;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import kamayuk.catastro.seguridad.dominio.CatalogoDelSistema;
import kamayuk.catastro.seguridad.dominio.FuenteDeEventosDeIdentidad;
import kamayuk.catastro.seguridad.infraestructura.ComprobadorDeAccesoJdbc;
import kamayuk.catastro.seguridad.infraestructura.RegistroDeMunicipalidadesJdbc;
import kamayuk.catastro.seguridad.infraestructura.consumidor.ClienteHttpDelBuzonDeIdentidad;
import kamayuk.catastro.seguridad.infraestructura.consumidor.CredencialDeServicio;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-2 y AC-3 de identidad#5: una implantacion <b>de cero</b>, contra PostgreSQL real y contra un
 * buzon que sirve por HTTP la corriente que {@code identidad} publica de verdad.
 *
 * <h2>Que se estaria midiendo sin esto</h2>
 *
 * <p>Hasta la etapa 4 el administrador lo escribia {@code SembradorDeLaCopiaLocal} con cuatro
 * {@code INSERT} directos, asi que una implantacion con el buzon apagado dejaba una copia <b>que
 * parecia completa</b>: habia un grupo, habia un administrador y habia permisos. Retirado el
 * sembrador (AC-1), lo unico que llena esas cuatro tablas es el buzon — y por eso lo que esta clase
 * cuenta son <b>las filas de la copia local</b> y <b>lo que el guardia contesta</b>, no que el
 * consumidor haya corrido.
 *
 * <h2>La corriente es la de `identidad`, y esta copiada campo a campo</h2>
 *
 * <p>{@link CorrienteDeLaImplantacion} reproduce lo que {@code ImplantarMunicipalidad} de {@code
 * identidad} emite, <b>en su orden</b> —leido de su fuente y de {@code
 * ImplantacionEmiteSusEventosTest}—: el grupo de administracion, el administrador, su afiliacion,
 * un {@code PERMISO_FIJADO} por cada opcion del catalogo UNIDO —empezando por {@code (identidad,
 * permisos)}, que es la que su propia guarda del ultimo administrador exige primero—, el grupo
 * «Consumidores del buzon» con su opcion, y las <b>cuatro</b> cuentas de servicio con sus cuatro
 * afiliaciones. El cuerpo de cada tipo, sus campos y su orden salen de {@code HechoDeIdentidad}; la
 * {@code huella} se calcula <b>igual que alli</b> —sha256 de la forma canonica separada por {@code
 * U+001F}—, asi que no es una cadena de relleno: es la del emisor.
 *
 * <p>Y las opciones ajenas no son un adorno: <b>trece de las dieciseis de este sistema son tambien
 * opciones de {@code rentas}</b> —{@code aranceles}, {@code calles}, {@code sectores}, {@code
 * ficha_urbana}…—, medido sobre los cinco catalogos que {@code identidad} siembra. Esta base no
 * tiene columna {@code sistema} en {@code acceso}, asi que aplicar un permiso por su codigo
 * concederia aqui la opcion homonima de otro sistema. Por eso la corriente los trae.
 *
 * <h2>Lo que NO se levanta, y se dice</h2>
 *
 * <p>{@code identidad} no se levanta: no hay demonio de Docker en esta maquina y su backend es otro
 * repositorio. Lo que hay es su <b>forma</b> —las dos operaciones, los siete campos del evento,
 * {@code quedan}, {@code limite} y el cuerpo del acuse— servida por HTTP de verdad sobre {@code
 * ServerSocket}, de modo que el camino que se ejerce es el de produccion entero: {@code
 * ClienteHttpDelBuzonDeIdentidad} → {@code IngestarEventosDeIdentidad} → {@code
 * AplicarUnEventoDeIdentidad} → PostgreSQL con RLS.
 */
@DisplayName("ADR-0039 etapa 5 — la implantacion de cero, y el orden que falla diciendolo")
class ImplantacionDeCeroTest {

    private static final String UBIGEO = "209905";
    private static final String ADMINISTRADOR = "administrador";
    private static final Instant AHORA = Instant.parse("2026-09-10T12:00:00Z");

    private static BaseDeDatosDePrueba base;
    private static DriverManagerDataSource pool;
    private static PlatformTransactionManager gestor;
    private static SembradorDelCatalogo sembrador;
    private static AplicarUnEventoDeIdentidad aplicador;
    private static ComprobadorDeAcceso guardia;
    private static RegistroDeMunicipalidadesJdbc registro;
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();

        pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        gestor = new TenantTransactionManager(pool);
        JdbcClient jdbc = JdbcClient.create(pool);
        sembrador =
                transaccional(
                        new SembradorDelCatalogo(jdbc, new AuditoriaJdbc(jdbc, RELOJ), RELOJ));
        aplicador =
                transaccional(new AplicarUnEventoDeIdentidad(jdbc, JsonMapper.builder().build()));
        guardia = transaccional(new ComprobadorDeAccesoJdbc(jdbc));
        registro =
                new RegistroDeMunicipalidadesJdbc(
                        base.url(),
                        BaseDeDatosDePrueba.OWNER,
                        base.clave(BaseDeDatosDePrueba.OWNER));
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    /**
     * Base VACIA antes de cada caso: es lo que AC-2 pide medir y no un detalle de higiene. Con
     * filas de una prueba anterior, «el administrador esta» seria cierto sin que el buzon hubiera
     * traido nada.
     */
    @BeforeEach
    void baseVacia() throws SQLException {
        try (Connection admin = base.conexionAdmin();
                Statement s = admin.createStatement()) {
            s.execute(
                    "TRUNCATE identidad_evento_aplicado, identidad_evento_muerto, permiso,"
                            + " miembro, usuario, grupo, acceso, modulo_sistema, auditoria"
                            + " RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
    }

    // ------------------------------------------------------------------
    // AC-2 — una implantacion de cero
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("AC-2 — de cero: el administrador llega por el buzon, no sembrado")
    class DeCero {

        @Test
        @DisplayName(
                "antes de la primera peticion, el administrador tiene sus SIETE privilegios sobre"
                        + " las opciones de este sistema")
        void unaImplantacionDeCero() throws IOException, SQLException {
            CorrienteDeLaImplantacion corriente = CorrienteDeLaImplantacion.deIdentidad(UBIGEO);
            try (BuzonDeMentira identidad = BuzonDeMentira.con(corriente.eventos())) {
                implantacion(identidad).run(new DefaultApplicationArguments());

                // 1. Las filas de la copia local. Ninguna la escribe ya este sistema.
                assertThat(contar("SELECT count(*) FROM usuario"))
                        .as(
                                "[AC-2: el administrador y las CUATRO cuentas de servicio. Ninguna"
                                        + " de las cinco la puede escribir este sistema desde la etapa"
                                        + " 5: si esta cifra es 0, el Job salio «Complete» sobre una"
                                        + " base en la que no puede entrar nadie]")
                        .isEqualTo(5);
                assertThat(contar("SELECT count(*) FROM grupo"))
                        .as("los dos que la implantacion de `identidad` crea")
                        .isEqualTo(2);
                assertThat(contar("SELECT count(*) FROM miembro"))
                        .as("el administrador en el suyo, y las cuatro cuentas en el del buzon")
                        .isEqualTo(5);
                assertThat(contar("SELECT count(*) FROM permiso"))
                        .as(
                                "[y SOLO los de este sistema: el catalogo unido trae %d opciones y"
                                        + " %d son de otros sistemas, trece de ellas con el MISMO"
                                        + " codigo que una de aqui. Esta base no tiene columna"
                                        + " `sistema` en `acceso`, asi que aplicarlas por su codigo"
                                        + " concederia aqui la opcion del vecino]",
                                corriente.opcionesDelCatalogoUnido(), corriente.opcionesAjenas())
                        .isEqualTo(CatalogoDelSistema.opciones().size());

                // 2. Que llegaron POR EL BUZON y no sembradas. Las cuatro cuentas de servicio son
                //    la prueba mas corta: ningun sembrador de este repositorio las escribio nunca.
                assertThat(filas("SELECT cuenta FROM usuario ORDER BY 1"))
                        .as(
                                "[las cuatro cuentas de servicio solo pueden venir del buzon: este"
                                        + " sistema no sabe componerlas, y la que se compone a si"
                                        + " misma es la de `catastro`]")
                        .containsExactly(
                                ADMINISTRADOR,
                                "service-account-kamayuk-caja-servicio-" + UBIGEO,
                                "service-account-kamayuk-catastro-servicio-" + UBIGEO,
                                "service-account-kamayuk-normativa-servicio-" + UBIGEO,
                                "service-account-kamayuk-rentas-servicio-" + UBIGEO);

                // 3. Y lo que de verdad importa: que el guardia DE PRODUCCION diga que si.
                assertThat(loQueElAdministradorNoPuede())
                        .as(
                                "[AC-2: los SIETE privilegios sobre las %d opciones de este"
                                        + " sistema, preguntados al mismo ComprobadorDeAcceso que"
                                        + " decide si una peticion pasa. Contar filas de `permiso` no"
                                        + " basta: entre la fila y el «si» estan la afiliacion, la"
                                        + " vigencia y `acceso.activo`]",
                                CatalogoDelSistema.opciones().size())
                        .isEmpty();

                // 4. Y las cuentas de servicio NO autorizan nada aqui, que es la otra cara de que
                //    los permisos ajenos se ignoren: su unica opcion es `identidad:eventos`.
                assertThat(
                                autoriza(
                                        "service-account-kamayuk-catastro-servicio-" + UBIGEO,
                                        "consulta_fichas"))
                        .as(
                                "[la cuenta de servicio esta dada de alta y no puede abrir nada de"
                                        + " este sistema: su unico permiso es sobre `identidad:eventos`,"
                                        + " que aqui se ignora]")
                        .isFalse();

                // 5. El buzon quedo vacio y acusado.
                assertThat(identidad.pendientes()).as("no queda nada por servir").isZero();
                assertThat(contar("SELECT count(*) FROM identidad_evento_aplicado"))
                        .as("los aplicados y los ignorados quedan anotados; los ajenos tambien")
                        .isEqualTo(corriente.eventos().size());
                assertThat(contar("SELECT count(*) FROM identidad_evento_muerto"))
                        .as("y ninguno se aparto: la corriente del emisor se aplica entera")
                        .isZero();
            }
        }

        @Test
        @DisplayName("el segundo despliegue no falla, con el buzon ya vacio")
        void elSegundoDespliegue() throws IOException, SQLException {
            CorrienteDeLaImplantacion corriente = CorrienteDeLaImplantacion.deIdentidad(UBIGEO);
            try (BuzonDeMentira identidad = BuzonDeMentira.con(corriente.eventos())) {
                implantacion(identidad).run(new DefaultApplicationArguments());

                // La segunda vez el buzon esta vacio —todo se acuso— y aun asi la implantacion
                // tiene que salir bien: la comprobacion final mira LA COPIA y no los eventos de
                // esta vuelta. Escrita sobre la vuelta, este caso saldria rojo en cada despliegue
                // a partir del segundo.
                assertThat(identidad.pendientes()).isZero();
                Throwable error =
                        catchThrowable(
                                () ->
                                        implantacion(identidad)
                                                .run(new DefaultApplicationArguments()));

                assertThat(error)
                        .as(
                                "[reimplantar con el buzon ya aplicado es lo normal en el segundo"
                                        + " despliegue: la copia sirve aunque la vuelta no traiga ni un"
                                        + " evento]")
                        .isNull();
                assertThat(contar("SELECT count(*) FROM usuario")).isEqualTo(5);
                assertThat(contar("SELECT count(*) FROM permiso"))
                        .isEqualTo(CatalogoDelSistema.opciones().size());
            }
        }
    }

    // ------------------------------------------------------------------
    // AC-3 — el orden equivocado falla diciendolo
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("AC-3 — implantar antes que `identidad` falla nombrando lo que falta")
    class ElOrdenEquivocado {

        @Test
        @DisplayName("sin consumidor cableado: dice que falta la url y no deja la copia vacia")
        void sinConsumidor() throws SQLException {
            Throwable error =
                    catchThrowable(
                            () ->
                                    new ImplantarMunicipalidad(
                                                    registro, sembrador, datos(), ninguno(),
                                                    guardia, RELOJ)
                                            .run(new DefaultApplicationArguments()));

            assertThat(error)
                    .as(
                            "[hasta la etapa 4 esto era un WARN y la implantacion seguia, porque el"
                                    + " sembrador dejaba un administrador escrito a mano. Retirado el"
                                    + " sembrador, la misma ausencia deja la copia VACIA y el Job sale"
                                    + " «Complete»: es el defecto de C-18 §5 con otra cara]")
                    .isInstanceOf(ImplantarMunicipalidad.LaCopiaDeLaAutorizacionNoLlego.class)
                    .hasMessageContaining("kamayuk.identidad.url")
                    .hasMessageContaining("CERO usuarios")
                    .hasMessageContaining("implantar `identidad` PRIMERO");
            assertThat(contar("SELECT count(*) FROM usuario"))
                    .as("y la copia efectivamente quedo sin nadie")
                    .isZero();
            assertThat(contar("SELECT count(*) FROM acceso"))
                    .as(
                            "[el catalogo SI se sembro: es lo unico que este sistema es dueno de"
                                    + " decir, y sin el un PERMISO_FIJADO que llegue despues no tiene"
                                    + " sobre que colgarse]")
                    .isEqualTo(CatalogoDelSistema.opciones().size());
        }

        @Test
        @DisplayName("el buzon contesta 401: dice que hay que implantar `identidad` primero")
        void elBuzonNoContesta() throws IOException, SQLException {
            try (BuzonDeMentira identidad = BuzonDeMentira.queRechaza()) {
                Throwable error =
                        catchThrowable(
                                () ->
                                        implantacion(identidad)
                                                .run(new DefaultApplicationArguments()));

                assertThat(error)
                        .as(
                                "[el mensaje del cliente dice QUE contesto `identidad`; lo que no"
                                        + " dice es que en una IMPLANTACION eso significa quedarse sin"
                                        + " autorizacion ninguna. Por eso se relanza con el remedio"
                                        + " dentro en vez de dejarla subir]")
                        .isInstanceOf(ImplantarMunicipalidad.LaCopiaDeLaAutorizacionNoLlego.class)
                        .hasMessageContaining("implantar `identidad` PRIMERO")
                        .hasMessageContaining("401");
                assertThat(contar("SELECT count(*) FROM usuario")).isZero();
            }
        }

        @Test
        @DisplayName(
                "el buzon contesta 200 y no trae nada: tambien falla, y esa es la que las otras dos"
                        + " no cubren")
        void elBuzonContestaYNoTraeNada() throws IOException, SQLException {
            try (BuzonDeMentira identidad = BuzonDeMentira.con(List.of())) {
                Throwable error =
                        catchThrowable(
                                () ->
                                        implantacion(identidad)
                                                .run(new DefaultApplicationArguments()));

                assertThat(error)
                        .as(
                                "[un buzon que no tiene nada para esta municipalidad contesta 200"
                                        + " con la cola vacia, que es indistinguible de uno ya"
                                        + " aplicado: la vuelta sale «sin progreso» y el consumidor"
                                        + " termina bien. Pasa cuando `identidad` no se ha implantado,"
                                        + " cuando se implanto para otro ubigeo, o cuando la cuenta de"
                                        + " servicio apunta a otra municipalidad — y en los tres casos,"
                                        + " sin esta comprobacion, el Job sale con codigo 0 sobre una"
                                        + " copia con cero usuarios]")
                        .isInstanceOf(ImplantarMunicipalidad.LaCopiaDeLaAutorizacionNoLlego.class)
                        .hasMessageContaining("no puede leer ninguna de las")
                        .hasMessageContaining("ni siquiera esta dada de alta aqui")
                        .hasMessageContaining("con el MISMO ubigeo");
                assertThat(contar("SELECT count(*) FROM usuario")).isZero();
            }
        }

        @Test
        @DisplayName(
                "y un POSPUESTO no la hace fallar: la copia sirve, y el CronJob sigue saliendo con"
                        + " rc=0")
        void unPospuestoNoLaHaceFallar() throws IOException, SQLException {
            // La corriente entera MAS una afiliacion a un grupo que `identidad` no ha publicado:
            // el consumidor la pospone, no la acusa y el emisor la vuelve a servir. Eso no es un
            // despliegue roto —es una dependencia que no ha llegado— y endurecerlo aqui
            // convertiria el CronJob de cada cinco minutos en un Job `Failed` (etapa 4, H7).
            CorrienteDeLaImplantacion corriente = CorrienteDeLaImplantacion.deIdentidad(UBIGEO);
            List<EventoPublicado> conHuerfana = new ArrayList<>(corriente.eventos());
            conHuerfana.add(CorrienteDeLaImplantacion.afiliacionHuerfana(conHuerfana.size() + 1L));

            try (BuzonDeMentira identidad = BuzonDeMentira.con(conHuerfana)) {
                Throwable error =
                        catchThrowable(
                                () ->
                                        implantacion(identidad)
                                                .run(new DefaultApplicationArguments()));

                assertThat(error)
                        .as(
                                "[«¿quedo algo por aplicar?» y «¿puede entrar alguien?» son dos"
                                        + " preguntas distintas, y esta implantacion contesta la"
                                        + " segunda. El pospuesto no se acuso: el emisor lo vuelve a"
                                        + " servir y la corrida siguiente lo aplica en cuanto llegue su"
                                        + " grupo]")
                        .isNull();
                assertThat(identidad.pendientes())
                        .as("y sigue pendiente en el buzon, sin acusar")
                        .isEqualTo(1);
                assertThat(loQueElAdministradorNoPuede()).isEmpty();
            }
        }
    }

    // ------------------------------------------------------------------
    // Los aparejos
    // ------------------------------------------------------------------

    private ImplantarMunicipalidad implantacion(BuzonDeMentira identidad) {
        FuenteDeEventosDeIdentidad fuente =
                new ClienteHttpDelBuzonDeIdentidad(
                        JsonMapper.builder().build(),
                        identidad.raiz(),
                        CredencialDeServicio.fija("Bearer el-de-catastro"));
        IngestarEventosDeIdentidad ingestor =
                new IngestarEventosDeIdentidad(fuente, aplicador, new AlertaQueCuenta(), RELOJ);
        return new ImplantarMunicipalidad(
                registro, sembrador, datos(), unProveedorDe(ingestor), guardia, RELOJ);
    }

    private static DatosDeImplantacion datos() {
        return new DatosDeImplantacion(
                UBIGEO,
                "Municipalidad de la prueba",
                "DISTRITAL",
                ADMINISTRADOR,
                "Administrador del Sistema",
                false,
                "implantacion");
    }

    /**
     * Los pares (opcion, privilegio) de este sistema que el administrador NO puede.
     *
     * <p>El contexto de tenant se fija aqui porque la implantacion lo LIMPIA al salir, que es lo
     * correcto: en el cluster ese proceso termina. Sin fijarlo, la politica RLS de las cinco tablas
     * no encuentra {@code app.municipalidad_id} y PostgreSQL no devuelve cero filas — falla con
     * «unrecognized configuration parameter» (DAT-01 §0). O sea que esto se pregunta como lo
     * preguntaria una peticion, con su inquilino puesto.
     */
    private List<String> loQueElAdministradorNoPuede() throws SQLException {
        LocalDate hoy = LocalDate.now(RELOJ);
        List<String> faltan = new ArrayList<>();
        conElInquilinoPuesto(
                () -> {
                    for (CatalogoDelSistema.Opcion opcion : CatalogoDelSistema.opciones()) {
                        for (Privilegio privilegio : Privilegio.values()) {
                            if (!guardia.autoriza(
                                    ADMINISTRADOR, opcion.codigo(), privilegio, hoy)) {
                                faltan.add(opcion.codigo() + "/" + privilegio.columna());
                            }
                        }
                    }
                });
        return faltan;
    }

    /** Si una cuenta puede leer una opcion de este sistema, con el inquilino puesto. */
    private boolean autoriza(String cuenta, String opcion) throws SQLException {
        boolean[] puede = {false};
        conElInquilinoPuesto(
                () ->
                        puede[0] =
                                guardia.autoriza(
                                        cuenta, opcion, Privilegio.LECTURA, LocalDate.now(RELOJ)));
        return puede[0];
    }

    private void conElInquilinoPuesto(Runnable que) throws SQLException {
        TenantContext.fijar(
                new MunicipalidadId(
                        contar("SELECT id FROM municipalidad WHERE ubigeo = '" + UBIGEO + "'")));
        try {
            que.run();
        } finally {
            TenantContext.limpiar();
        }
    }

    private static long contar(String sql) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                Statement s = admin.createStatement();
                ResultSet fila = s.executeQuery(sql)) {
            fila.next();
            return fila.getLong(1);
        }
    }

    private static List<String> filas(String sql) throws SQLException {
        List<String> valores = new ArrayList<>();
        try (Connection admin = base.conexionAdmin();
                Statement s = admin.createStatement();
                ResultSet filas = s.executeQuery(sql)) {
            while (filas.next()) {
                valores.add(filas.getString(1));
            }
        }
        return valores;
    }

    @SuppressWarnings("unchecked")
    private static <T> T transaccional(T objetivo) {
        ProxyFactory fabrica = new ProxyFactory(objetivo);
        fabrica.setProxyTargetClass(true);
        fabrica.addAdvice(
                new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        return (T) fabrica.getProxy();
    }

    private static ObjectProvider<IngestarEventosDeIdentidad> unProveedorDe(
            IngestarEventosDeIdentidad ingestor) {
        return new ObjectProvider<>() {
            @Override
            public IngestarEventosDeIdentidad getObject() {
                return ingestor;
            }
        };
    }

    /** El estado exacto de un despliegue sin `kamayuk.identidad.url`: el bean no existe. */
    private static ObjectProvider<IngestarEventosDeIdentidad> ninguno() {
        return new ObjectProvider<>() {
            @Override
            public IngestarEventosDeIdentidad getObject() {
                throw new org.springframework.beans.factory.NoSuchBeanDefinitionException(
                        IngestarEventosDeIdentidad.class);
            }
        };
    }

    /** No hay nada que avisar en estos casos; se cuenta para que no pase inadvertido si lo hay. */
    private static final class AlertaQueCuenta implements AlertaDeEventosSinAplicar {

        @Override
        public void hayUnEventoSinAplicar(
                kamayuk.catastro.seguridad.dominio.EventoRecibido evento,
                String motivo,
                long muertosSinExplicar) {
            throw new AssertionError(
                    "La corriente de la implantacion de `identidad` no aparta ningun evento, y esta"
                            + " aparto uno: "
                            + motivo);
        }

        @Override
        public void hayPospuestosEstancados(
                List<IngestarEventosDeIdentidad.Pospuesto> viejos,
                java.time.Duration desdeHace,
                Instant ahora) {
            // Con el reloj fijo, un pospuesto recien emitido no llega al umbral. No se afirma
            // aqui: eso lo mide `IngestarEventosDeIdentidadTest`.
        }
    }

    // ------------------------------------------------------------------
    // La corriente de `identidad`, copiada de su emisor
    // ------------------------------------------------------------------

    /** Un evento tal como el buzon de `identidad` lo sirve: sus siete campos. */
    private record EventoPublicado(
            UUID eventoId,
            long secuencia,
            String tipo,
            long sujetoId,
            String cuerpo,
            String huella,
            Instant creadoEn) {

        String comoJson() {
            return "{\"eventoId\":\""
                    + eventoId
                    + "\",\"secuencia\":"
                    + secuencia
                    + ",\"tipo\":\""
                    + tipo
                    + "\",\"sujetoId\":"
                    + sujetoId
                    + ",\"cuerpo\":\""
                    + escaparParaJson(cuerpo)
                    + "\",\"huella\":\""
                    + huella
                    + "\",\"creadoEn\":\""
                    + creadoEn
                    + "\"}";
        }
    }

    /**
     * Lo que la implantacion de {@code identidad} deja en el buzon, en su orden.
     *
     * <p>Los cuerpos y la forma canonica de la huella salen de {@code HechoDeIdentidad} de {@code
     * identidad}, campo a campo y en su orden de declaracion. El <b>orden de emision</b> sale de su
     * {@code ImplantarMunicipalidad}: grupo de administracion → administrador → afiliacion → {@code
     * (identidad, permisos)} → el resto del catalogo unido → grupo de consumidores → su opcion →
     * las cuatro cuentas de servicio con sus afiliaciones.
     *
     * <p>El catalogo unido de aqui es <b>una parte</b> del suyo —las {@code N} opciones de este
     * sistema mas las que hacen falta para que la mitad ajena tenga sujeto—, y se dice: el catalogo
     * real trae 157 opciones y lo que esta prueba mide no es su tamano sino que un permiso de otro
     * sistema no se aplique aqui. Las ajenas <b>no son inventadas</b>: son codigos reales de los
     * otros cuatro catalogos, y trece de ellos son homonimos de una opcion de este sistema.
     */
    private record CorrienteDeLaImplantacion(
            List<EventoPublicado> eventos, int opcionesDelCatalogoUnido, int opcionesAjenas) {

        static final String GRUPO_DE_ADMINISTRACION = "Administracion del sistema";
        static final String GRUPO_DE_CONSUMIDORES = "Consumidores del buzon";
        static final String QUIEN = "implantacion";

        /** Los cuatro que consumen el buzon. `identidad` no se consume a si mismo. */
        static final List<String> CONSUMIDORES = List.of("caja", "catastro", "normativa", "rentas");

        /**
         * Las opciones de los otros cuatro catalogos que esta corriente trae.
         *
         * <p>Las trece primeras son las <b>homonimas</b>: mismo codigo aqui y en {@code rentas},
         * medido sobre los cinco catalogos de {@code identidad}.
         *
         * <p><b>Y lo que se ve al aplicar por el codigo NO es lo que uno espera</b>, medido
         * quitandole al aplicador su filtro por {@code sistema}: el homonimo cae sobre <b>la misma
         * fila</b> de {@code permiso} —misma opcion, mismo grupo— y su {@code ON CONFLICT … DO
         * UPDATE} la reescribe con los mismos siete privilegios, asi que <b>ni una fila cambia</b>.
         * Lo que si se ve son las <b>seis</b> ajenas que NO tienen homonimo aqui —{@code
         * identidad:permisos}, {@code identidad:eventos}, {@code normativa:parametros} y las tres
         * de {@code caja}—, mas la del grupo del buzon: sin opcion sobre la que colgarse el
         * aplicador las pospone, no las acusa y el buzon las vuelve a servir <b>para siempre</b>.
         * La cola no drena, y esa es la senal. Que el homonimo se aplique con la misma matriz y no
         * se note lo cubre {@code AplicarUnEventoDeIdentidadJdbcTest}, que lo mide con dos matrices
         * distintas.
         */
        static final List<String[]> AJENAS =
                List.of(
                        new String[] {"rentas", "actualizacion_catastro"},
                        new String[] {"rentas", "aranceles"},
                        new String[] {"rentas", "calles"},
                        new String[] {"rentas", "consulta_fichas"},
                        new String[] {"rentas", "consulta_resumen_predial"},
                        new String[] {"rentas", "depreciacion"},
                        new String[] {"rentas", "ficha_bienes"},
                        new String[] {"rentas", "ficha_contribuyente_reporte"},
                        new String[] {"rentas", "ficha_economica"},
                        new String[] {"rentas", "ficha_rural"},
                        new String[] {"rentas", "ficha_urbana"},
                        new String[] {"rentas", "sectores"},
                        new String[] {"rentas", "valores_unitarios"},
                        new String[] {"identidad", "permisos"},
                        new String[] {"identidad", "eventos"},
                        new String[] {"normativa", "parametros"},
                        new String[] {"caja", "caja_tasas"},
                        new String[] {"caja", "caja_tributaria"},
                        new String[] {"caja", "cierre_caja"});

        static CorrienteDeLaImplantacion deIdentidad(String ubigeo) {
            List<EventoPublicado> eventos = new ArrayList<>();
            long grupoAdministracion = 1;
            long grupoConsumidores = 2;
            long administrador = 1;

            eventos.add(
                    grupo(
                            eventos.size() + 1L,
                            "GRUPO_DADO_DE_ALTA",
                            grupoAdministracion,
                            GRUPO_DE_ADMINISTRACION,
                            "Creado por la implantacion: administra este sistema entero"));
            eventos.add(
                    usuario(
                            eventos.size() + 1L,
                            administrador,
                            ADMINISTRADOR,
                            "Administrador del Sistema"));
            eventos.add(
                    miembro(
                            eventos.size() + 1L,
                            grupoAdministracion,
                            GRUPO_DE_ADMINISTRACION,
                            administrador,
                            ADMINISTRADOR));

            // El PRIMERO es (identidad, permisos): es lo que la guarda del ultimo administrador de
            // `identidad` exige antes que ningun otro permiso.
            eventos.add(
                    permiso(
                            eventos.size() + 1L,
                            grupoAdministracion,
                            GRUPO_DE_ADMINISTRACION,
                            "identidad",
                            "permisos",
                            todosLosPrivilegios()));
            for (String[] ajena : AJENAS) {
                if ("identidad".equals(ajena[0]) && "permisos".equals(ajena[1])) {
                    continue;
                }
                eventos.add(
                        permiso(
                                eventos.size() + 1L,
                                grupoAdministracion,
                                GRUPO_DE_ADMINISTRACION,
                                ajena[0],
                                ajena[1],
                                todosLosPrivilegios()));
            }
            for (CatalogoDelSistema.Opcion opcion : CatalogoDelSistema.opciones()) {
                eventos.add(
                        permiso(
                                eventos.size() + 1L,
                                grupoAdministracion,
                                GRUPO_DE_ADMINISTRACION,
                                "catastro",
                                opcion.codigo(),
                                todosLosPrivilegios()));
            }

            eventos.add(
                    grupo(
                            eventos.size() + 1L,
                            "GRUPO_DADO_DE_ALTA",
                            grupoConsumidores,
                            GRUPO_DE_CONSUMIDORES,
                            "Creado por la implantacion: desde aqui los cuatro sistemas leen y"
                                    + " acusan el buzon de identidad"));
            eventos.add(
                    permiso(
                            eventos.size() + 1L,
                            grupoConsumidores,
                            GRUPO_DE_CONSUMIDORES,
                            "identidad",
                            "eventos",
                            leerYAcusar()));

            long siguiente = administrador;
            for (String sistema : CONSUMIDORES) {
                siguiente++;
                String cuenta = "service-account-kamayuk-" + sistema + "-servicio-" + ubigeo;
                eventos.add(
                        usuario(
                                eventos.size() + 1L,
                                siguiente,
                                cuenta,
                                "Cuenta de servicio de " + sistema + " (buzon)"));
                eventos.add(
                        miembro(
                                eventos.size() + 1L,
                                grupoConsumidores,
                                GRUPO_DE_CONSUMIDORES,
                                siguiente,
                                cuenta));
            }
            int unido = CatalogoDelSistema.opciones().size() + AJENAS.size();
            return new CorrienteDeLaImplantacion(List.copyOf(eventos), unido, AJENAS.size());
        }

        /** Una afiliacion a un grupo que el emisor no ha publicado: el consumidor la pospone. */
        static EventoPublicado afiliacionHuerfana(long secuencia) {
            return miembro(secuencia, 99, "Un grupo que no llego", 99, "una-cuenta-que-no-llego");
        }

        private static boolean[] todosLosPrivilegios() {
            boolean[] siete = new boolean[Privilegio.values().length];
            java.util.Arrays.fill(siete, true);
            return siete;
        }

        private static boolean[] leerYAcusar() {
            boolean[] dos = new boolean[Privilegio.values().length];
            Privilegio[] todos = Privilegio.values();
            for (int i = 0; i < todos.length; i++) {
                dos[i] = todos[i] == Privilegio.LECTURA || todos[i] == Privilegio.REGISTRO;
            }
            return dos;
        }

        private static EventoPublicado usuario(
                long secuencia, long id, String cuenta, String nombre) {
            Campos campos =
                    new Campos()
                            .numero("usuarioId", id)
                            .texto("cuenta", cuenta)
                            .texto("nombre", nombre)
                            .texto("correo", null)
                            .booleano("habilitado", true)
                            .texto("vigenciaDesde", null)
                            .texto("vigenciaHasta", null);
            return campos.evento("USUARIO_DADO_DE_ALTA", secuencia, id);
        }

        private static EventoPublicado grupo(
                long secuencia, String tipo, long id, String nombre, String descripcion) {
            Campos campos =
                    new Campos()
                            .numero("grupoId", id)
                            .texto("nombre", nombre)
                            .texto("descripcion", descripcion)
                            .booleano("habilitado", true)
                            .texto("vigenciaDesde", null)
                            .texto("vigenciaHasta", null);
            return campos.evento(tipo, secuencia, id);
        }

        private static EventoPublicado miembro(
                long secuencia,
                long grupoId,
                String grupoNombre,
                long usuarioId,
                String usuarioCuenta) {
            Campos campos =
                    new Campos()
                            .numero("grupoId", grupoId)
                            .texto("grupoNombre", grupoNombre)
                            .numero("usuarioId", usuarioId)
                            .texto("usuarioCuenta", usuarioCuenta)
                            .booleano("activo", true)
                            .texto("usuarioAlta", QUIEN)
                            .texto("usuarioBaja", null);
            return campos.evento("MIEMBRO_AFILIADO", secuencia, grupoId);
        }

        private static EventoPublicado permiso(
                long secuencia,
                long sujetoId,
                String sujetoNombre,
                String sistema,
                String codigo,
                boolean[] privilegios) {
            Campos campos =
                    new Campos()
                            .texto("sujeto", "GRUPO")
                            .numero("sujetoId", sujetoId)
                            .texto("sujetoNombre", sujetoNombre)
                            .texto("sistema", sistema)
                            .texto("codigo", codigo);
            campos.abrirPrivilegios();
            Privilegio[] todos = Privilegio.values();
            for (int i = 0; i < todos.length; i++) {
                campos.booleano(todos[i].columna(), privilegios[i]);
            }
            campos.cerrarPrivilegios();
            campos.texto("usuarioRegistro", QUIEN);
            return campos.evento("PERMISO_FIJADO", secuencia, sujetoId);
        }
    }

    /**
     * El compositor del cuerpo y de la huella, copiado de {@code HechoDeIdentidad.Campos}.
     *
     * <p>Se copia y no se simplifica: la huella que viaja es el sha256 de la forma canonica —los
     * campos en el orden en que se declaran, separados por {@code U+001F}— y el consumidor la
     * guarda para compararla con la del reintento. Una huella de relleno pasaria igual y dejaria
     * sin ejercer esa comparacion.
     */
    private static final class Campos {

        private static final char SEPARADOR = (char) 0x1F;

        private final StringBuilder json = new StringBuilder("{");
        private final List<String> canonico = new ArrayList<>();
        private boolean primero = true;

        Campos texto(String nombre, String valor) {
            coma();
            json.append('"').append(nombre).append("\":");
            if (valor == null) {
                json.append("null");
            } else {
                json.append('"').append(valor).append('"');
            }
            canonico.add(nombre + "=" + (valor == null ? "" : valor));
            return this;
        }

        Campos numero(String nombre, long valor) {
            coma();
            json.append('"').append(nombre).append("\":").append(valor);
            canonico.add(nombre + "=" + valor);
            return this;
        }

        Campos booleano(String nombre, boolean valor) {
            coma();
            json.append('"').append(nombre).append("\":").append(valor);
            canonico.add(nombre + "=" + valor);
            return this;
        }

        void abrirPrivilegios() {
            coma();
            json.append("\"privilegios\":{");
            primero = true;
        }

        void cerrarPrivilegios() {
            json.append('}');
            primero = false;
        }

        EventoPublicado evento(String tipo, long secuencia, long sujetoId) {
            String cuerpo = json.append('}').toString();
            StringBuilder forma = new StringBuilder(tipo);
            for (String campo : canonico) {
                forma.append(SEPARADOR).append(campo);
            }
            return new EventoPublicado(
                    UUID.randomUUID(),
                    secuencia,
                    tipo,
                    sujetoId,
                    cuerpo,
                    sha256(forma.toString()),
                    AHORA.minusSeconds(60));
        }

        private void coma() {
            if (!primero) {
                json.append(',');
            }
            primero = false;
        }
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 es obligatorio en toda JVM", imposible);
        }
    }

    /** El `cuerpo` viaja como CADENA dentro del JSON del evento: sus comillas van escapadas. */
    private static String escaparParaJson(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ------------------------------------------------------------------
    // El buzon
    // ------------------------------------------------------------------

    /**
     * Un buzon de {@code identidad} que se comporta como el suyo: sirve por secuencia, retira lo
     * acusado y publica {@code quedan} <b>contando los de esta pagina</b>.
     *
     * <p>Ese ultimo detalle no es un adorno: la medicion de la etapa 4 encontro que tres de los
     * cuatro consumidores tenian su doble sirviendo {@code quedan} como «ademas de estos», o sea
     * confirmando la lectura equivocada del campo que el consumidor imprime. Lo que dice {@code
     * EventosController} de {@code identidad} es «cuantos le faltan en total, contando los de esta
     * pagina», y eso es lo que este doble hace.
     */
    private static final class BuzonDeMentira implements AutoCloseable {

        private static final Pattern UUID_EN_EL_CUERPO =
                Pattern.compile(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                                + "-[0-9a-fA-F]{12}");

        private final ServerSocket socket;
        private final Thread hilo;
        private final List<EventoPublicado> cola;
        private final boolean rechaza;

        private BuzonDeMentira(
                ServerSocket socket, List<EventoPublicado> eventos, boolean rechaza) {
            this.socket = socket;
            this.cola = Collections.synchronizedList(new ArrayList<>(eventos));
            this.rechaza = rechaza;
            this.hilo =
                    new Thread(
                            () -> {
                                while (!socket.isClosed()) {
                                    try (Socket cliente = socket.accept()) {
                                        atender(cliente);
                                    } catch (IOException cerrado) {
                                        return;
                                    }
                                }
                            },
                            "buzon-de-identidad-de-mentira");
            this.hilo.setDaemon(true);
        }

        static BuzonDeMentira con(List<EventoPublicado> eventos) throws IOException {
            return arrancar(eventos, false);
        }

        /** Sin cuenta de servicio dada de alta en `identidad`, el buzon contesta 401. */
        static BuzonDeMentira queRechaza() throws IOException {
            return arrancar(List.of(), true);
        }

        private static BuzonDeMentira arrancar(List<EventoPublicado> eventos, boolean rechaza)
                throws IOException {
            ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
            BuzonDeMentira buzon = new BuzonDeMentira(socket, eventos, rechaza);
            buzon.hilo.start();
            return buzon;
        }

        String raiz() {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }

        int pendientes() {
            return cola.size();
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException yaEsta) {
                // Nada que hacer: el hilo sale por el mismo camino.
            }
        }

        private void atender(Socket cliente) throws IOException {
            Peticion peticion = leer(cliente);
            if (rechaza) {
                responder(
                        cliente,
                        401,
                        "{\"codigo\":\"NO_AUTENTICADO\",\"detail\":\"El token no vale\"}");
                return;
            }
            if (peticion.linea().startsWith("GET")) {
                responder(cliente, 200, pagina(limiteDe(peticion.linea())));
                return;
            }
            responder(cliente, 200, acusar(peticion.cuerpo()));
        }

        private static int limiteDe(String linea) {
            Matcher limite = Pattern.compile("limite=(\\d+)").matcher(linea);
            return limite.find() ? Integer.parseInt(limite.group(1)) : 200;
        }

        private String pagina(int limite) {
            StringBuilder eventos = new StringBuilder("[");
            int total;
            synchronized (cola) {
                total = cola.size();
                for (int i = 0; i < Math.min(limite, total); i++) {
                    if (i > 0) {
                        eventos.append(',');
                    }
                    eventos.append(cola.get(i).comoJson());
                }
            }
            // `quedan` cuenta los de esta pagina: es como lo publica `identidad`.
            return "{\"eventos\":" + eventos.append(']') + ",\"quedan\":" + total + "}";
        }

        private String acusar(String cuerpo) {
            List<UUID> acusados = new ArrayList<>();
            Matcher ids = UUID_EN_EL_CUERPO.matcher(cuerpo);
            while (ids.find()) {
                acusados.add(UUID.fromString(ids.group()));
            }
            int escritos;
            int quedan;
            synchronized (cola) {
                int antes = cola.size();
                cola.removeIf(evento -> acusados.contains(evento.eventoId()));
                escritos = antes - cola.size();
                quedan = cola.size();
            }
            return "{\"recibidos\":"
                    + acusados.size()
                    + ",\"escritos\":"
                    + escritos
                    + ",\"quedan\":"
                    + quedan
                    + "}";
        }

        private record Peticion(String linea, String cuerpo) {}

        private static Peticion leer(Socket cliente) throws IOException {
            BufferedReader lector =
                    new BufferedReader(
                            new InputStreamReader(
                                    cliente.getInputStream(), StandardCharsets.UTF_8));
            String primera = lector.readLine();
            int longitud = 0;
            String linea = lector.readLine();
            while (linea != null && !linea.isEmpty()) {
                if (linea.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                    longitud = Integer.parseInt(linea.substring(linea.indexOf(':') + 1).trim());
                }
                linea = lector.readLine();
            }
            char[] cuerpo = new char[longitud];
            int leidos = 0;
            while (leidos < longitud) {
                int n = lector.read(cuerpo, leidos, longitud - leidos);
                if (n < 0) {
                    break;
                }
                leidos += n;
            }
            return new Peticion(
                    primera == null ? "" : primera, new String(cuerpo, 0, Math.max(leidos, 0)));
        }

        private static void responder(Socket cliente, int estado, String cuerpo)
                throws IOException {
            byte[] datos = cuerpo.getBytes(StandardCharsets.UTF_8);
            String cabeceras =
                    "HTTP/1.1 "
                            + estado
                            + " \r\nContent-Type: application/json\r\nContent-Length: "
                            + datos.length
                            + "\r\nConnection: close\r\n\r\n";
            OutputStream salida = cliente.getOutputStream();
            salida.write(cabeceras.getBytes(StandardCharsets.US_ASCII));
            salida.write(datos);
            salida.flush();
        }
    }
}
