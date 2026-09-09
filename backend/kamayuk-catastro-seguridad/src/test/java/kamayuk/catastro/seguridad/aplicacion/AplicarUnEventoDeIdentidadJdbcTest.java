package kamayuk.catastro.seguridad.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import kamayuk.catastro.seguridad.dominio.AplicadorDeEventosDeIdentidad;
import kamayuk.catastro.seguridad.dominio.AplicadorDeEventosDeIdentidad.NoSePuedeAplicar;
import kamayuk.catastro.seguridad.dominio.AplicadorDeEventosDeIdentidad.NoSePuedeAplicarAhora;
import kamayuk.catastro.seguridad.dominio.AplicadorDeEventosDeIdentidad.Resultado;
import kamayuk.catastro.seguridad.dominio.EventoRecibido;
import kamayuk.catastro.seguridad.dominio.FuenteDeEventosDeIdentidad;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * El consumidor del buzon de {@code identidad}, contra PostgreSQL real y como {@code kamayuk_app}
 * (ADR-0039 etapa 4, identidad#4 AC-1 y AC-7).
 *
 * <p>El aplicador se envuelve con el interceptor transaccional DE VERDAD sobre el gestor de tenant,
 * como hace {@code SinNormativaFronteraTest} en {@code rentas}: llamarlo a pelo dejaria los
 * {@code @Transactional} como comentarios, y las tres propiedades que este archivo mide —una
 * transaccion por evento, el acuse despues del commit, y que el {@code SET LOCAL} sea el de CADA
 * evento— no las podria medir nadie.
 */
@DisplayName("ADR-0039 etapa 4 — el consumidor del buzon de identidad, contra PostgreSQL")
class AplicarUnEventoDeIdentidadJdbcTest {

    private static final Instant AHORA = Instant.parse("2026-09-09T12:00:00Z");
    private static final String HUELLA = "a".repeat(64);
    private static final String OTRA_HUELLA = "b".repeat(64);

    private static BaseDeDatosDePrueba base;
    private static TransactionTemplate transaccion;
    private static GestorQuePuedeNoConfirmar gestor;
    private static AplicadorDeEventosDeIdentidad aplicador;

    private static long municipalidadA;
    private static long municipalidadB;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidadA = crearMunicipalidad("209901", "Municipalidad A");
        municipalidadB = crearMunicipalidad("209902", "Municipalidad B");
        sembrarElCatalogo(municipalidadA);
        sembrarElCatalogo(municipalidadB);

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        gestor = new GestorQuePuedeNoConfirmar(new TenantTransactionManager(pool));
        transaccion = new TransactionTemplate(gestor);
        aplicador =
                transaccional(
                        new AplicarUnEventoDeIdentidad(
                                JdbcClient.create(pool), JsonMapper.builder().build()));
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @AfterEach
    void limpiarContexto() {
        gestor.confirmaTodo();
        TenantContext.limpiar();
    }

    // ------------------------------------------------------------------
    // Los siete tipos, sobre la copia local
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("los siete tipos llegan a la copia local")
    class LosSieteTipos {

        @Test
        @DisplayName(
                "un usuario dado de alta en identidad aparece aqui, y su reintento no hace nada")
        void unUsuarioDadoDeAlta() {
            TenantContext.fijar(new MunicipalidadId(municipalidadA));
            EventoRecibido alta = usuario("USUARIO_DADO_DE_ALTA", "mlopez", "Maria Lopez", true);

            assertThat(aplicador.aplicar(alta, AHORA)).isEqualTo(Resultado.APLICADO);
            assertThat(usuarioDe(municipalidadA, "mlopez")).containsExactly("Maria Lopez", "true");
            assertThat(aplicadosDe(municipalidadA)).contains(alta.eventoId().toString());

            assertThat(aplicador.aplicar(alta, AHORA))
                    .as("el emisor entrega AL MENOS una vez: la segunda no puede escribir nada")
                    .isEqualTo(Resultado.YA_APLICADO);
        }

        @Test
        @DisplayName("una baja se aplica como baja: el usuario queda deshabilitado, no borrado")
        void unaBajaEsUnaBaja() {
            TenantContext.fijar(new MunicipalidadId(municipalidadA));
            aplicador.aplicar(
                    usuario("USUARIO_DADO_DE_ALTA", "rquispe", "Rosa Quispe", true), AHORA);

            aplicador.aplicar(
                    usuario("USUARIO_MODIFICADO", "rquispe", "Rosa Quispe", false), AHORA);

            assertThat(usuarioDe(municipalidadA, "rquispe"))
                    .as("la fila sigue —regla 4— y dice que ya no esta habilitada")
                    .containsExactly("Rosa Quispe", "false");
        }

        @Test
        @DisplayName("un USUARIO_MODIFICADO de alguien que aqui no existe lo da de alta")
        void unModificadoDeAlguienNuevo() {
            // El cuerpo es la fila ENTERA tal como quedo (etapa 2): no falta nada para escribirla,
            // y rechazarla por no haber visto su alta dejaria la copia atras sin ganar nada.
            TenantContext.fijar(new MunicipalidadId(municipalidadA));
            assertThat(
                            aplicador.aplicar(
                                    usuario("USUARIO_MODIFICADO", "nuevo", "Nadie Antes", true),
                                    AHORA))
                    .isEqualTo(Resultado.APLICADO);
            assertThat(usuarioDe(municipalidadA, "nuevo")).containsExactly("Nadie Antes", "true");
        }

        @Test
        @DisplayName("un grupo, su afiliacion, y la desafiliacion como baja")
        void grupoYAfiliacion() {
            TenantContext.fijar(new MunicipalidadId(municipalidadA));
            aplicador.aplicar(usuario("USUARIO_DADO_DE_ALTA", "jperez", "Juan Perez", true), AHORA);
            aplicador.aplicar(grupo("GRUPO_DADO_DE_ALTA", "Mesa de Partes"), AHORA);

            assertThat(
                            aplicador.aplicar(
                                    miembro("MIEMBRO_AFILIADO", "Mesa de Partes", "jperez", true),
                                    AHORA))
                    .isEqualTo(Resultado.APLICADO);
            assertThat(miembroDe(municipalidadA, "Mesa de Partes", "jperez"))
                    .containsExactly("true", "admin.emisor");

            aplicador.aplicar(
                    miembro("MIEMBRO_DESAFILIADO", "Mesa de Partes", "jperez", false), AHORA);
            assertThat(miembroDe(municipalidadA, "Mesa de Partes", "jperez"))
                    .as("desafiliar deja la fila con activo=false y quien lo hizo; no la borra")
                    .containsExactly("false", "admin.emisor");
        }

        @Test
        @DisplayName(
                "un PERMISO_FIJADO sobre una opcion de catastro concede los privilegios que trae")
        void unPermisoPropio() {
            TenantContext.fijar(new MunicipalidadId(municipalidadA));
            aplicador.aplicar(grupo("GRUPO_DADO_DE_ALTA", "Fiscalizadores"), AHORA);

            assertThat(
                            aplicador.aplicar(
                                    permiso("catastro", "predios", "GRUPO", "Fiscalizadores", true),
                                    AHORA))
                    .isEqualTo(Resultado.APLICADO);
            assertThat(permisoDe(municipalidadA, "predios", "Fiscalizadores"))
                    .containsExactly("true", "true", "admin.emisor");

            // Y fijarlo otra vez con todo en falso es la revocacion: la fila se queda, en falso.
            UUID otro = UUID.randomUUID();
            aplicador.aplicar(
                    conId(permiso("catastro", "predios", "GRUPO", "Fiscalizadores", false), otro),
                    AHORA);
            assertThat(permisoDe(municipalidadA, "predios", "Fiscalizadores"))
                    .containsExactly("false", "false", "admin.emisor");
        }
    }

    // ------------------------------------------------------------------
    // AC-7 (4): un PERMISO_FIJADO ajeno
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "AC-7 (4): un PERMISO_FIJADO de una opcion de OTRO sistema se ignora, aunque el codigo coincida")
    void unPermisoAjenoSeIgnora() {
        // `predios` existe en el catalogo de catastro Y podria existir en el de rentas con otro
        // significado: esta base no tiene columna `sistema` en `acceso`, asi que aplicarlo por
        // codigo seria conceder aqui una pantalla que nadie concedio.
        TenantContext.fijar(new MunicipalidadId(municipalidadA));
        aplicador.aplicar(grupo("GRUPO_DADO_DE_ALTA", "Cajeros"), AHORA);
        EventoRecibido ajeno = permiso("rentas", "predios", "GRUPO", "Cajeros", true);

        assertThat(aplicador.aplicar(ajeno, AHORA))
                .as("se acusa —el emisor lo sirve a los cuatro— pero NO se aplica")
                .isEqualTo(Resultado.IGNORADO_AJENO);
        assertThat(permisoDe(municipalidadA, "predios", "Cajeros"))
                .as(
                        "aplicar un permiso ajeno como propio daria aqui LECTURA sobre `predios` a"
                                + " un grupo al que nadie se la dio en este sistema")
                .isEmpty();
        assertThat(aplicadosDe(municipalidadA))
                .as("y queda en la memoria: la segunda vez es YA_APLICADO y no otra decision")
                .contains(ajeno.eventoId().toString());
    }

    // ------------------------------------------------------------------
    // Lo que no se puede aplicar NUNCA y lo que no se puede AHORA
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("nunca y ahora-no son dos cosas distintas")
    class NuncaYAhora {

        @Test
        @DisplayName("una afiliacion cuyo grupo no ha llegado es AHORA NO, y no deja rastro")
        void unaDependenciaQueFalta() {
            TenantContext.fijar(new MunicipalidadId(municipalidadA));
            aplicador.aplicar(
                    usuario("USUARIO_DADO_DE_ALTA", "ptorres", "Pedro Torres", true), AHORA);
            EventoRecibido huerfano =
                    miembro("MIEMBRO_AFILIADO", "Grupo que no ha llegado", "ptorres", true);

            Throwable error = catchThrowable(() -> aplicador.aplicar(huerfano, AHORA));

            assertThat(error)
                    .as("el emisor publica en orden: lo que falta esta en camino, y se reintenta")
                    .isInstanceOf(NoSePuedeAplicarAhora.class)
                    .hasMessageContaining("Grupo que no ha llegado");
            assertThat(aplicadosDe(municipalidadA))
                    .as(
                            "la memoria se escribe en la MISMA transaccion: si el evento no se"
                                    + " aplico, no puede quedar anotado como aplicado")
                    .doesNotContain(huerfano.eventoId().toString());
            assertThat(muertosDe(municipalidadA)).doesNotContain(huerfano.eventoId().toString());
        }

        @Test
        @DisplayName("un cuerpo que no es JSON es NUNCA, y matar lo guarda tal cual, en text")
        void unCuerpoIlegible() {
            TenantContext.fijar(new MunicipalidadId(municipalidadA));
            EventoRecibido roto =
                    new EventoRecibido(
                            UUID.randomUUID(),
                            7,
                            "USUARIO_DADO_DE_ALTA",
                            1,
                            "<html>esto lo contesto un proxy</html>",
                            HUELLA,
                            AHORA);

            Throwable error = catchThrowable(() -> aplicador.aplicar(roto, AHORA));
            assertThat(error)
                    .isInstanceOf(NoSePuedeAplicar.class)
                    .hasMessageContaining("no es JSON");

            long antes = aplicador.muertosSinExplicar();
            aplicador.matar(roto, error.getMessage(), AHORA);
            assertThat(aplicador.muertosSinExplicar()).isEqualTo(antes + 1);
            assertThat(cuerpoMuertoDe(municipalidadA, roto.eventoId()))
                    .as("`text` y no `jsonb`: lo que no se pudo leer se guarda como llego")
                    .isEqualTo("<html>esto lo contesto un proxy</html>");
        }

        @Test
        @DisplayName("un tipo que este sistema no conoce es NUNCA, y el motivo lo nombra")
        void unTipoDesconocido() {
            TenantContext.fijar(new MunicipalidadId(municipalidadA));
            EventoRecibido octavo =
                    new EventoRecibido(
                            UUID.randomUUID(), 8, "CUENTA_BLOQUEADA", 1, "{}", HUELLA, AHORA);

            assertThat(catchThrowable(() -> aplicador.aplicar(octavo, AHORA)))
                    .isInstanceOf(NoSePuedeAplicar.class)
                    .hasMessageContaining("CUENTA_BLOQUEADA");
        }

        @Test
        @DisplayName("el mismo eventoId con OTRA huella no es un reintento: es NUNCA")
        void unEventoReescrito() {
            TenantContext.fijar(new MunicipalidadId(municipalidadA));
            EventoRecibido original = usuario("USUARIO_DADO_DE_ALTA", "areyes", "Ana Reyes", true);
            aplicador.aplicar(original, AHORA);
            EventoRecibido reescrito =
                    new EventoRecibido(
                            original.eventoId(),
                            original.secuencia(),
                            original.tipoPublicado(),
                            original.sujetoId(),
                            original.cuerpo().replace("Ana Reyes", "Otra Persona"),
                            OTRA_HUELLA,
                            AHORA);

            assertThat(catchThrowable(() -> aplicador.aplicar(reescrito, AHORA)))
                    .isInstanceOf(NoSePuedeAplicar.class)
                    .hasMessageContaining("reescribiendo");
            assertThat(usuarioDe(municipalidadA, "areyes"))
                    .as("y la copia se queda con lo primero que se aplico")
                    .containsExactly("Ana Reyes", "true");
        }
    }

    // ------------------------------------------------------------------
    // AC-7 (2): una transaccion por evento, con SU municipalidad
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "AC-7 (2): dos eventos de dos municipalidades van cada uno a la suya, aunque se apliquen seguidos dentro de otra transaccion")
    void unaTransaccionPorEvento() {
        EventoRecibido deA = usuario("USUARIO_DADO_DE_ALTA", "compartida", "Vecina de A", true);
        EventoRecibido deB = usuario("USUARIO_DADO_DE_ALTA", "compartida", "Vecina de B", true);

        // Una transaccion de fuera con el contexto de A, y dentro se aplican los dos. Con
        // REQUIRED en vez de REQUIRES_NEW, el segundo se UNIRIA a esta transaccion —cuyo
        // SET LOCAL es el de A— y la vecina de B quedaria escrita en A.
        TenantContext.fijar(new MunicipalidadId(municipalidadA));
        transaccion.executeWithoutResult(
                estado -> {
                    aplicador.aplicar(deA, AHORA);
                    TenantContext.fijar(new MunicipalidadId(municipalidadB));
                    aplicador.aplicar(deB, AHORA);
                });

        assertThat(usuarioDe(municipalidadB, "compartida"))
                .as("el segundo evento abrio SU transaccion, con el SET LOCAL de B")
                .containsExactly("Vecina de B", "true");
        assertThat(usuarioDe(municipalidadA, "compartida"))
                .as(
                        "y A no recibio el de B: un permiso de una municipalidad concedido en otra"
                                + " es exactamente lo que REQUIRES_NEW existe para impedir")
                .containsExactly("Vecina de A", "true");
    }

    // ------------------------------------------------------------------
    // AC-7 (1) y (3): el acuse va DESPUES del commit, y lo transitorio no se aparta
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "AC-7 (1): un evento cuyo commit NO confirma no se acusa ni queda aplicado; el que si confirmo, si")
    void unCommitQueNoConfirmaNoSeAcusa() {
        TenantContext.fijar(new MunicipalidadId(municipalidadA));
        EventoRecibido bueno = usuario("USUARIO_DADO_DE_ALTA", "confirma", "Si Confirma", true);
        EventoRecibido perdido = usuario("USUARIO_DADO_DE_ALTA", "noconfirma", "No Confirma", true);
        BuzonDeMentira buzon = new BuzonDeMentira(List.of(bueno, perdido));
        AlertaQueRecuerda alerta = new AlertaQueRecuerda();
        IngestarEventosDeIdentidad ingestor =
                new IngestarEventosDeIdentidad(
                        buzon, aplicador, alerta, Clock.fixed(AHORA, ZoneOffset.UTC));
        // El buzon sirve `bueno` y despues `perdido`: el primer commit confirma y el segundo no.
        gestor.noConfirmarElCommitNumero(2);

        IngestarEventosDeIdentidad.Vuelta vuelta = ingestor.unaVuelta();

        assertThat(buzon.acusados())
                .as(
                        "el acuse va DESPUES del commit: un evento acusado cuyo commit fallo es un"
                                + " evento que el emisor ya no vuelve a servir — se pierde, y con el"
                                + " el permiso que traia")
                .containsExactly(bueno.eventoId());
        assertThat(usuarioDe(municipalidadA, "noconfirma")).isEmpty();
        assertThat(aplicadosDe(municipalidadA)).doesNotContain(perdido.eventoId().toString());
        assertThat(muertosDe(municipalidadA))
                .as("AC-7 (3): un commit que no confirma es transitorio, no se aparta")
                .doesNotContain(perdido.eventoId().toString());
        assertThat(alerta.avisos).isEmpty();
        assertThat(vuelta.pospuestos()).isEqualTo(1);
        assertThat(vuelta.aplicados()).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "AC-7 (3): una dependencia que falta no se aparta ni se acusa; en la vuelta siguiente entra")
    void loTransitorioSeReintenta() {
        TenantContext.fijar(new MunicipalidadId(municipalidadA));
        EventoRecibido afiliacion =
                miembro("MIEMBRO_AFILIADO", "Grupo tardio", "usuario.tardio", true);
        EventoRecibido elGrupo = grupo("GRUPO_DADO_DE_ALTA", "Grupo tardio");
        EventoRecibido elUsuario =
                usuario("USUARIO_DADO_DE_ALTA", "usuario.tardio", "Usuario Tardio", true);
        // El buzon sirve la afiliacion ANTES que el grupo y el usuario de los que depende: es el
        // estado que deja un evento apartado antes, o un emisor que reordeno.
        BuzonDeMentira buzon = new BuzonDeMentira(List.of(afiliacion, elGrupo, elUsuario));
        AlertaQueRecuerda alerta = new AlertaQueRecuerda();
        IngestarEventosDeIdentidad ingestor =
                new IngestarEventosDeIdentidad(
                        buzon, aplicador, alerta, Clock.fixed(AHORA, ZoneOffset.UTC));

        IngestarEventosDeIdentidad.Vuelta primera = ingestor.unaVuelta();
        assertThat(primera.pospuestos())
                .as(
                        "AC-7 (3): una afiliacion cuyo grupo no ha llegado se POSPONE —no se acusa"
                                + " y el buzon la vuelve a servir—; apartarla como «nunca» la"
                                + " perderia, porque el emisor no la sirve dos veces")
                .isEqualTo(1);
        assertThat(primera.muertos()).as("apartar un fallo transitorio lo perderia").isZero();
        assertThat(buzon.acusados()).containsExactly(elGrupo.eventoId(), elUsuario.eventoId());
        assertThat(alerta.avisos).isEmpty();
        assertThat(muertosDe(municipalidadA)).doesNotContain(afiliacion.eventoId().toString());

        IngestarEventosDeIdentidad.Vuelta segunda = ingestor.unaVuelta();
        assertThat(segunda.aplicados()).isEqualTo(1);
        assertThat(segunda.pospuestos()).isZero();
        assertThat(miembroDe(municipalidadA, "Grupo tardio", "usuario.tardio"))
                .containsExactly("true", "admin.emisor");
        assertThat(buzon.acusados()).contains(afiliacion.eventoId());
    }

    @Test
    @DisplayName(
            "y lo que no se puede aplicar NUNCA se aparta, se acusa y se avisa con la cuenta entera")
    void loPermanenteSeApartaYSeAvisa() {
        TenantContext.fijar(new MunicipalidadId(municipalidadA));
        EventoRecibido roto =
                new EventoRecibido(
                        UUID.randomUUID(), 9, "PERMISO_FIJADO", 3, "no es json", HUELLA, AHORA);
        BuzonDeMentira buzon = new BuzonDeMentira(List.of(roto));
        AlertaQueRecuerda alerta = new AlertaQueRecuerda();
        IngestarEventosDeIdentidad ingestor =
                new IngestarEventosDeIdentidad(
                        buzon, aplicador, alerta, Clock.fixed(AHORA, ZoneOffset.UTC));

        IngestarEventosDeIdentidad.Vuelta vuelta = ingestor.unaVuelta();

        assertThat(vuelta.muertos()).isEqualTo(1);
        assertThat(buzon.acusados())
                .as("se acusa: no acusarlo bloquearia la cola detras de el para siempre")
                .containsExactly(roto.eventoId());
        assertThat(muertosDe(municipalidadA)).contains(roto.eventoId().toString());
        assertThat(alerta.avisos).hasSize(1);
        assertThat(alerta.avisos.getFirst())
                .as("el aviso lleva el motivo y CUANTOS hay sin explicar, no solo este")
                .contains("no es JSON")
                .contains("sin explicar: " + aplicador.muertosSinExplicar());
    }

    // ------------------------------------------------------------------
    // Dobles y utilidades
    // ------------------------------------------------------------------

    /** Un buzon que sirve siempre lo que no se ha acusado, y recuerda que se le acuso. */
    private static final class BuzonDeMentira implements FuenteDeEventosDeIdentidad {
        private final List<EventoRecibido> eventos;
        private final List<UUID> acusados = new ArrayList<>();

        BuzonDeMentira(List<EventoRecibido> eventos) {
            this.eventos = eventos;
        }

        @Override
        public Lote pendientes(int limite) {
            List<EventoRecibido> pendientes =
                    eventos.stream().filter(e -> !acusados.contains(e.eventoId())).toList();
            return new Lote(pendientes, 0);
        }

        @Override
        public void acusar(List<UUID> eventoIds) {
            acusados.addAll(eventoIds);
        }

        List<UUID> acusados() {
            return List.copyOf(acusados);
        }
    }

    private static final class AlertaQueRecuerda implements AlertaDeEventosSinAplicar {
        private final List<String> avisos = new ArrayList<>();

        @Override
        public void hayUnEventoSinAplicar(EventoRecibido evento, String motivo, long muertos) {
            avisos.add(evento.eventoId() + ": " + motivo + " — sin explicar: " + muertos);
        }
    }

    /**
     * Un gestor de transacciones que puede negarse a confirmar: delega en el de tenant, y cuando la
     * condicion se cumple deshace en vez de confirmar y lanza lo que Spring lanzaria si la base no
     * confirmara. Es la unica forma de medir «el acuse va despues del commit» sin tumbar la base.
     */
    private static final class GestorQuePuedeNoConfirmar implements PlatformTransactionManager {
        private final PlatformTransactionManager delegado;
        private volatile int commitQueNoConfirma = 0;
        private final java.util.concurrent.atomic.AtomicInteger commits =
                new java.util.concurrent.atomic.AtomicInteger();

        GestorQuePuedeNoConfirmar(PlatformTransactionManager delegado) {
            this.delegado = delegado;
        }

        /** A partir de ahora, el commit numero N (contando desde 1) no confirma. */
        void noConfirmarElCommitNumero(int numero) {
            commits.set(0);
            this.commitQueNoConfirma = numero;
        }

        void confirmaTodo() {
            this.commitQueNoConfirma = 0;
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definicion) {
            return delegado.getTransaction(definicion);
        }

        @Override
        public void commit(TransactionStatus estado) {
            // El gestor no sabe de eventos: se le dice desde fuera QUE commit tiene que fallar.
            if (commitQueNoConfirma > 0 && commits.incrementAndGet() == commitQueNoConfirma) {
                delegado.rollback(estado);
                throw new TransactionSystemException("la base no confirmo (simulado)");
            }
            delegado.commit(estado);
        }

        @Override
        public void rollback(TransactionStatus estado) {
            delegado.rollback(estado);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T transaccional(T objetivo) {
        ProxyFactory fabrica = new ProxyFactory(objetivo);
        fabrica.setProxyTargetClass(true);
        fabrica.addAdvice(
                new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        return (T) fabrica.getProxy();
    }

    private static EventoRecibido usuario(
            String tipo, String cuenta, String nombre, boolean habilitado) {
        return new EventoRecibido(
                UUID.randomUUID(),
                1,
                tipo,
                11,
                "{\"usuarioId\":11,\"cuenta\":\""
                        + cuenta
                        + "\",\"nombre\":\""
                        + nombre
                        + "\",\"correo\":null,\"habilitado\":"
                        + habilitado
                        + ",\"vigenciaDesde\":\"2026-01-01\",\"vigenciaHasta\":null}",
                HUELLA,
                AHORA);
    }

    private static EventoRecibido grupo(String tipo, String nombre) {
        return new EventoRecibido(
                UUID.randomUUID(),
                2,
                tipo,
                21,
                "{\"grupoId\":21,\"nombre\":\""
                        + nombre
                        + "\",\"descripcion\":\"de prueba\",\"habilitado\":true,"
                        + "\"vigenciaDesde\":null,\"vigenciaHasta\":null}",
                HUELLA,
                AHORA);
    }

    private static EventoRecibido miembro(
            String tipo, String grupo, String cuenta, boolean activo) {
        return new EventoRecibido(
                UUID.randomUUID(),
                3,
                tipo,
                21,
                "{\"grupoId\":21,\"grupoNombre\":\""
                        + grupo
                        + "\",\"usuarioId\":11,\"usuarioCuenta\":\""
                        + cuenta
                        + "\",\"activo\":"
                        + activo
                        + ",\"usuarioAlta\":"
                        + (activo ? "\"admin.emisor\"" : "null")
                        + ",\"usuarioBaja\":"
                        + (activo ? "null" : "\"admin.emisor\"")
                        + "}",
                HUELLA,
                AHORA);
    }

    private static EventoRecibido permiso(
            String sistema, String codigo, String sujeto, String sujetoNombre, boolean todos) {
        String v = String.valueOf(todos);
        return new EventoRecibido(
                UUID.randomUUID(),
                4,
                "PERMISO_FIJADO",
                21,
                "{\"sujeto\":\""
                        + sujeto
                        + "\",\"sujetoId\":21,\"sujetoNombre\":\""
                        + sujetoNombre
                        + "\",\"sistema\":\""
                        + sistema
                        + "\",\"codigo\":\""
                        + codigo
                        + "\",\"privilegios\":{\"ejecucion\":"
                        + v
                        + ",\"lectura\":"
                        + v
                        + ",\"registro\":"
                        + v
                        + ",\"modificacion\":"
                        + v
                        + ",\"eliminacion\":"
                        + v
                        + ",\"impresion\":"
                        + v
                        + ",\"especial\":"
                        + v
                        + "},\"usuarioRegistro\":\"admin.emisor\"}",
                HUELLA,
                AHORA);
    }

    private static EventoRecibido conId(EventoRecibido evento, UUID id) {
        return new EventoRecibido(
                id,
                evento.secuencia() + 1,
                evento.tipoPublicado(),
                evento.sujetoId(),
                evento.cuerpo(),
                evento.huella(),
                evento.creadoEn());
    }

    // Lecturas como superusuario, que omite RLS: lo que se mide es lo que quedo en CADA base.

    private static List<String> usuarioDe(long municipalidad, String cuenta) {
        return filas(
                "SELECT nombre, habilitado::text FROM usuario WHERE municipalidad_id = ? AND"
                        + " cuenta = ?",
                municipalidad,
                cuenta);
    }

    private static List<String> miembroDe(long municipalidad, String grupo, String cuenta) {
        return filas(
                "SELECT m.activo::text, coalesce(m.usuario_baja, m.usuario_alta) FROM miembro m"
                        + " JOIN grupo g ON g.municipalidad_id = m.municipalidad_id AND g.id ="
                        + " m.grupo_id JOIN usuario u ON u.municipalidad_id = m.municipalidad_id"
                        + " AND u.id = m.usuario_id WHERE m.municipalidad_id = ? AND g.nombre = ?"
                        + " AND u.cuenta = ?",
                municipalidad,
                grupo,
                cuenta);
    }

    private static List<String> permisoDe(long municipalidad, String codigo, String grupo) {
        return filas(
                "SELECT p.lectura::text, p.especial::text, p.usuario_registro FROM permiso p"
                        + " JOIN acceso a ON a.municipalidad_id = p.municipalidad_id AND a.id ="
                        + " p.acceso_id JOIN grupo g ON g.municipalidad_id = p.municipalidad_id"
                        + " AND g.id = p.grupo_id WHERE p.municipalidad_id = ? AND a.codigo = ?"
                        + " AND g.nombre = ?",
                municipalidad,
                codigo,
                grupo);
    }

    private static List<String> aplicadosDe(long municipalidad) {
        return filas(
                "SELECT evento_id::text FROM identidad_evento_aplicado WHERE municipalidad_id = ?",
                municipalidad);
    }

    private static List<String> muertosDe(long municipalidad) {
        return filas(
                "SELECT evento_id::text FROM identidad_evento_muerto WHERE municipalidad_id = ?",
                municipalidad);
    }

    private static String cuerpoMuertoDe(long municipalidad, UUID evento) {
        return filas(
                        "SELECT cuerpo FROM identidad_evento_muerto WHERE municipalidad_id = ? AND"
                                + " evento_id = ?",
                        municipalidad,
                        evento)
                .getFirst();
    }

    private static List<String> filas(String sql, Object... valores) {
        try (Connection admin = base.conexionAdmin();
                PreparedStatement sentencia = admin.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                sentencia.setObject(i + 1, valores[i]);
            }
            List<String> resultado = new ArrayList<>();
            try (ResultSet fila = sentencia.executeQuery()) {
                int columnas = fila.getMetaData().getColumnCount();
                while (fila.next()) {
                    for (int c = 1; c <= columnas; c++) {
                        resultado.add(fila.getString(c));
                    }
                }
            }
            return resultado;
        } catch (SQLException error) {
            throw new IllegalStateException(error);
        }
    }

    private static long crearMunicipalidad(String ubigeo, String nombre) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                Statement sentencia = admin.createStatement()) {
            sentencia.execute(
                    "INSERT INTO municipalidad (ubigeo, nombre, tipo) VALUES ('"
                            + ubigeo
                            + "', '"
                            + nombre
                            + "', 'DISTRITAL') ON CONFLICT (ubigeo) DO NOTHING");
            try (ResultSet fila =
                    sentencia.executeQuery(
                            "SELECT id FROM municipalidad WHERE ubigeo = '" + ubigeo + "'")) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }

    /**
     * El catalogo de este sistema lo siembra la implantacion, no el consumidor: se siembra aqui.
     */
    private static void sembrarElCatalogo(long municipalidad) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                Statement s = admin.createStatement()) {
            s.execute(
                    "INSERT INTO modulo_sistema (municipalidad_id, codigo, nombre) VALUES ("
                            + municipalidad
                            + ", 'CATASTRO', 'Catastro')");
            s.execute(
                    "INSERT INTO acceso (municipalidad_id, modulo_id, tipo, codigo, nombre)"
                            + " SELECT "
                            + municipalidad
                            + ", id, 'OPCION_MENU', 'predios', 'Predios' FROM modulo_sistema"
                            + " WHERE municipalidad_id = "
                            + municipalidad
                            + " AND codigo = 'CATASTRO'");
        }
    }
}
