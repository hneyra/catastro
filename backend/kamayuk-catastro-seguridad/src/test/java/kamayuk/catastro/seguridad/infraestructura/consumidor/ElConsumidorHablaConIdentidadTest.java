package kamayuk.catastro.seguridad.infraestructura.consumidor;

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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kamayuk.catastro.seguridad.dominio.EventoRecibido;
import kamayuk.catastro.seguridad.dominio.FuenteDeEventosDeIdentidad;
import kamayuk.catastro.seguridad.dominio.FuenteDeEventosDeIdentidad.IdentidadNoContesta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * El cliente del buzon de {@code identidad} y su token de servicio, contra un servidor HTTP DE
 * VERDAD y no un doble (identidad#4 AC-1, #21 AC-2).
 *
 * <p>Un doble que devolviera «Bearer x» probaria que el codigo llama a un metodo; un emisor de
 * verdad prueba que la peticion se compone bien —{@code grant_type=client_credentials}, cliente y
 * clave por cuerpo, <b>medido sobre lo que LLEGO</b> y no sobre lo que se penso mandar— y que del
 * JSON sale el token que despues viaja. Escrito sobre {@code ServerSocket}, que es lo que
 * Checkstyle deja: {@code com.sun.net.httpserver} ata el arbol a una JDK concreta.
 */
@DisplayName("ADR-0039 etapa 4 — el consumidor habla con identidad, y pide su token")
class ElConsumidorHablaConIdentidadTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant AHORA = Instant.parse("2026-09-09T12:00:00Z");
    private static final String HUELLA = "c".repeat(64);

    @Nested
    @DisplayName("el buzon")
    class ElBuzon {

        @Test
        @DisplayName("pide los pendientes con su limite y su credencial, y lee los siete campos")
        void pendientes() throws IOException {
            try (ServidorDeMentira identidad = ServidorDeMentira.arranca()) {
                UUID id = UUID.randomUUID();
                identidad.responde(
                        200,
                        "{\"eventos\":[{\"eventoId\":\""
                                + id
                                + "\",\"secuencia\":42,\"tipo\":\"USUARIO_DADO_DE_ALTA\","
                                + "\"sujetoId\":7,\"cuerpo\":\"{\\\"cuenta\\\":\\\"jperez\\\"}\","
                                + "\"huella\":\""
                                + HUELLA
                                + "\",\"creadoEn\":\"2026-09-09T11:59:00Z\"}],\"quedan\":3}");
                FuenteDeEventosDeIdentidad cliente =
                        new ClienteHttpDelBuzonDeIdentidad(
                                JSON,
                                identidad.raiz() + "/",
                                CredencialDeServicio.fija("Bearer t"));

                FuenteDeEventosDeIdentidad.Lote lote = cliente.pendientes(200);

                assertThat(identidad.lineasDePeticion())
                        .containsExactly(
                                "GET /identidad/api/v1/eventos/pendientes?limite=200 HTTP/1.1");
                assertThat(identidad.autorizaciones()).containsExactly("Bearer t");
                assertThat(lote.quedan()).isEqualTo(3);
                EventoRecibido evento = lote.eventos().getFirst();
                assertThat(evento.eventoId()).isEqualTo(id);
                assertThat(evento.secuencia()).isEqualTo(42);
                assertThat(evento.tipoPublicado()).isEqualTo("USUARIO_DADO_DE_ALTA");
                assertThat(evento.sujetoId()).isEqualTo(7);
                assertThat(evento.cuerpo())
                        .as("el cuerpo es TEXTO con JSON dentro, y llega tal cual")
                        .isEqualTo("{\"cuenta\":\"jperez\"}");
                assertThat(evento.huella()).isEqualTo(HUELLA);
                assertThat(evento.creadoEn()).isEqualTo(Instant.parse("2026-09-09T11:59:00Z"));
            }
        }

        @Test
        @DisplayName("acusa con la forma que identidad lee, y un 200 basta")
        void acusa() throws IOException {
            try (ServidorDeMentira identidad = ServidorDeMentira.arranca()) {
                identidad.responde(200, "{\"recibidos\":2,\"escritos\":2,\"quedan\":0}");
                UUID a = UUID.randomUUID();
                UUID b = UUID.randomUUID();
                FuenteDeEventosDeIdentidad cliente =
                        new ClienteHttpDelBuzonDeIdentidad(
                                JSON, identidad.raiz(), CredencialDeServicio.fija("Bearer t"));

                cliente.acusar(List.of(a, b));

                assertThat(identidad.lineasDePeticion())
                        .containsExactly("POST /identidad/api/v1/eventos/acuses HTTP/1.1");
                assertThat(identidad.cuerpos())
                        .containsExactly("{\"eventos\":[\"" + a + "\",\"" + b + "\"]}");
            }
        }

        @Test
        @DisplayName("un acuse vacio no viaja")
        void unAcuseVacio() throws IOException {
            try (ServidorDeMentira identidad = ServidorDeMentira.arranca()) {
                new ClienteHttpDelBuzonDeIdentidad(
                                JSON, identidad.raiz(), CredencialDeServicio.fija(""))
                        .acusar(List.of());
                assertThat(identidad.peticiones()).isZero();
            }
        }

        @Test
        @DisplayName("un 401 es TRANSITORIO y dice que falta: sin credencial, cuales propiedades")
        void un401SinCredencial() throws IOException {
            try (ServidorDeMentira identidad = ServidorDeMentira.arranca()) {
                identidad.responde(401, "{\"codigo\":\"NO_AUTENTICADO\"}");
                FuenteDeEventosDeIdentidad cliente =
                        new ClienteHttpDelBuzonDeIdentidad(
                                JSON, identidad.raiz(), CredencialDeServicio.fija(""));

                Throwable error = catchThrowable(() -> cliente.pendientes(10));

                assertThat(error)
                        .as(
                                "un 401 cambia solo —en cuanto la cuenta exista— asi que esta del"
                                        + " lado de lo que se reintenta, y no mata ningun evento")
                        .isInstanceOf(IdentidadNoContesta.class)
                        .hasMessageContaining("401")
                        .hasMessageContaining("kamayuk.identidad.{token,cliente,credencial}");
                assertThat(identidad.autorizaciones()).containsExactly("");
            }
        }

        @Test
        @DisplayName("y un 403 dice que la cuenta existe y le falta el acceso `eventos`")
        void un403() throws IOException {
            try (ServidorDeMentira identidad = ServidorDeMentira.arranca()) {
                identidad.responde(403, "{\"codigo\":\"SIN_PRIVILEGIO\"}");
                FuenteDeEventosDeIdentidad cliente =
                        new ClienteHttpDelBuzonDeIdentidad(
                                JSON, identidad.raiz(), CredencialDeServicio.fija("Bearer t"));

                assertThat(catchThrowable(() -> cliente.acusar(List.of(UUID.randomUUID()))))
                        .isInstanceOf(IdentidadNoContesta.class)
                        .hasMessageContaining("Consumidores del buzon");
            }
        }

        @Test
        @DisplayName(
                "un 422 al acusar se registra y NO tumba la vuelta: lo acusado ya esta aplicado aqui")
        void un422AlAcusar() throws IOException {
            try (ServidorDeMentira identidad = ServidorDeMentira.arranca()) {
                identidad.responde(422, "{\"codigo\":\"VALIDACION\",\"detalle\":\"no es tuyo\"}");
                FuenteDeEventosDeIdentidad cliente =
                        new ClienteHttpDelBuzonDeIdentidad(
                                JSON, identidad.raiz(), CredencialDeServicio.fija("Bearer t"));

                assertThat(catchThrowable(() -> cliente.acusar(List.of(UUID.randomUUID()))))
                        .as("reintentar daria 422 otra vez; se dice con nivel ERROR y se sigue")
                        .isNull();
            }
        }

        @Test
        @DisplayName("lo que no tiene la forma de un evento, y lo que no es JSON, es TRANSITORIO")
        void loQueNoTieneForma() throws IOException {
            try (ServidorDeMentira identidad = ServidorDeMentira.arranca()) {
                FuenteDeEventosDeIdentidad cliente =
                        new ClienteHttpDelBuzonDeIdentidad(
                                JSON, identidad.raiz(), CredencialDeServicio.fija("Bearer t"));

                identidad.responde(200, "<html>un proxy</html>");
                assertThat(catchThrowable(() -> cliente.pendientes(10)))
                        .isInstanceOf(IdentidadNoContesta.class)
                        .hasMessageContaining("no es JSON");

                identidad.responde(
                        200, "{\"eventos\":[{\"eventoId\":\"no-es-uuid\"}],\"quedan\":0}");
                assertThat(catchThrowable(() -> cliente.pendientes(10)))
                        .isInstanceOf(IdentidadNoContesta.class)
                        .hasMessageContaining("forma de un evento");
            }
        }

        @Test
        @DisplayName("un puerto que nadie escucha es TRANSITORIO, y sin URL se dice cual falta")
        void nadieEscucha() throws IOException {
            int puerto;
            try (ServerSocket libre = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
                puerto = libre.getLocalPort();
            }
            FuenteDeEventosDeIdentidad apagado =
                    new ClienteHttpDelBuzonDeIdentidad(
                            JSON, "http://127.0.0.1:" + puerto, CredencialDeServicio.fija(""));
            assertThat(catchThrowable(() -> apagado.pendientes(10)))
                    .isInstanceOf(IdentidadNoContesta.class)
                    .hasMessageContaining("No se pudo leer el buzon");

            ClienteHttpDelBuzonDeIdentidad sinUrl =
                    new ClienteHttpDelBuzonDeIdentidad(JSON, "", CredencialDeServicio.fija(""));
            assertThat(sinUrl.configurado()).isFalse();
            assertThat(catchThrowable(() -> sinUrl.pendientes(10)))
                    .isInstanceOf(IdentidadNoContesta.class)
                    .hasMessageContaining("kamayuk.identidad.url");
        }
    }

    @Nested
    @DisplayName("el token de servicio")
    class ElToken {

        @Test
        @DisplayName(
                "se pide con client_credentials, cliente y clave POR CUERPO, y viaja en la cabecera")
        void sePide() throws IOException {
            try (ServidorDeMentira emisor = ServidorDeMentira.arranca()) {
                emisor.responde(200, "{\"access_token\":\"eyJ.token\",\"expires_in\":300}");
                TokenDeServicioDeKeycloak token =
                        new TokenDeServicioDeKeycloak(
                                JSON,
                                Clock.fixed(AHORA, ZoneOffset.UTC),
                                emisor.raiz() + "/realms/sgtm/protocol/openid-connect/token",
                                "kamayuk-catastro-servicio-200105",
                                "la clave");

                assertThat(token.configurada()).isTrue();
                assertThat(token.cabecera()).isEqualTo("Bearer eyJ.token");
                assertThat(emisor.cuerpos())
                        .containsExactly(
                                "grant_type=client_credentials&client_id="
                                        + "kamayuk-catastro-servicio-200105&client_secret=la+clave");
            }
        }

        @Test
        @DisplayName("se guarda hasta el margen, se renueva despues, y sin expires_in no se guarda")
        void seGuardaYSeRenueva() throws IOException {
            try (ServidorDeMentira emisor = ServidorDeMentira.arranca()) {
                RelojMovil reloj = new RelojMovil(AHORA);
                emisor.responde(200, "{\"access_token\":\"uno\",\"expires_in\":300}");
                TokenDeServicioDeKeycloak token =
                        new TokenDeServicioDeKeycloak(
                                JSON, reloj, emisor.raiz(), "cliente", "clave");

                assertThat(token.cabecera()).isEqualTo("Bearer uno");
                reloj.avanzaSegundos(200);
                assertThat(token.cabecera()).isEqualTo("Bearer uno");
                assertThat(emisor.peticiones()).as("antes del margen no se pide otro").isEqualTo(1);

                emisor.responde(200, "{\"access_token\":\"dos\",\"expires_in\":300}");
                reloj.avanzaSegundos(80);
                assertThat(token.cabecera())
                        .as("a 20 s del vencimiento ya se renovo")
                        .isEqualTo("Bearer dos");
                assertThat(emisor.peticiones()).isEqualTo(2);

                emisor.responde(200, "{\"access_token\":\"tres\"}");
                reloj.avanzaSegundos(1000);
                assertThat(token.cabecera()).isEqualTo("Bearer tres");
                assertThat(token.cabecera()).isEqualTo("Bearer tres");
                assertThat(emisor.peticiones())
                        .as("sin `expires_in` no se guarda: se pide cada vez")
                        .isEqualTo(4);
            }
        }

        @Test
        @DisplayName(
                "un emisor que rechaza la clave es TRANSITORIO, y el mensaje no copia el cuerpo")
        void elEmisorRechaza() throws IOException {
            try (ServidorDeMentira emisor = ServidorDeMentira.arranca()) {
                emisor.responde(401, "{\"error_description\":\"Invalid client secret la-clave\"}");
                TokenDeServicioDeKeycloak token =
                        new TokenDeServicioDeKeycloak(
                                JSON,
                                Clock.fixed(AHORA, ZoneOffset.UTC),
                                emisor.raiz(),
                                "cliente",
                                "la-clave");

                assertThat(catchThrowable(token::cabecera))
                        .isInstanceOf(IdentidadNoContesta.class)
                        .hasMessageContaining("401")
                        .hasMessageContaining("reconciliar-identidades.sh")
                        .hasMessageNotContaining("la-clave");
            }
        }

        @Test
        @DisplayName("sin identidad configurada devuelve cadena vacia y NO llama a nadie")
        void sinConfigurar() throws IOException {
            try (ServidorDeMentira emisor = ServidorDeMentira.arranca()) {
                TokenDeServicioDeKeycloak token =
                        new TokenDeServicioDeKeycloak(
                                JSON, Clock.fixed(AHORA, ZoneOffset.UTC), emisor.raiz(), "", "");
                assertThat(token.configurada()).isFalse();
                assertThat(token.cabecera()).isEmpty();
                assertThat(emisor.peticiones())
                        .as("el compose sin identidad no se pasa la vida pidiendo tokens")
                        .isZero();
            }
        }
    }

    // ------------------------------------------------------------------

    private static final class RelojMovil extends Clock {
        private Instant ahora;

        RelojMovil(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzaSegundos(long segundos) {
            ahora = ahora.plusSeconds(segundos);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    /** Un servidor HTTP de verdad sobre un `ServerSocket`, que recuerda lo que le llego. */
    private static final class ServidorDeMentira implements AutoCloseable {

        private final ServerSocket socket;
        private final Thread hilo;
        private final List<String> lineas = Collections.synchronizedList(new ArrayList<>());
        private final List<String> cuerpos = Collections.synchronizedList(new ArrayList<>());
        private final List<String> cabeceras = Collections.synchronizedList(new ArrayList<>());
        private volatile int estado = 200;
        private volatile String cuerpo = "{}";

        private ServidorDeMentira(ServerSocket socket) {
            this.socket = socket;
            this.hilo =
                    new Thread(
                            () -> {
                                while (!socket.isClosed()) {
                                    try (Socket cliente = socket.accept()) {
                                        Peticion recibida = leerPeticion(cliente);
                                        lineas.add(recibida.linea());
                                        cabeceras.add(recibida.autorizacion());
                                        cuerpos.add(recibida.cuerpo());
                                        responder(cliente, estado, cuerpo);
                                    } catch (IOException cerrado) {
                                        return;
                                    }
                                }
                            },
                            "identidad-de-mentira");
            this.hilo.setDaemon(true);
        }

        static ServidorDeMentira arranca() throws IOException {
            ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
            ServidorDeMentira servidor = new ServidorDeMentira(socket);
            servidor.hilo.start();
            return servidor;
        }

        void responde(int estado, String cuerpo) {
            this.estado = estado;
            this.cuerpo = cuerpo;
        }

        String raiz() {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }

        int peticiones() {
            return cuerpos.size();
        }

        List<String> lineasDePeticion() {
            return List.copyOf(lineas);
        }

        List<String> cuerpos() {
            return List.copyOf(cuerpos);
        }

        List<String> autorizaciones() {
            return List.copyOf(cabeceras);
        }

        private record Peticion(String linea, String autorizacion, String cuerpo) {}

        /**
         * Lee la peticion entera. Hay que DRENAR el cuerpo del POST, no basta con parar en la linea
         * en blanco: los bytes que quedaran en el buffer hacen que el cliente vea un RST en vez de
         * la respuesta que esta prueba viene a medir.
         */
        private static Peticion leerPeticion(Socket cliente) throws IOException {
            BufferedReader lector =
                    new BufferedReader(
                            new InputStreamReader(
                                    cliente.getInputStream(), StandardCharsets.UTF_8));
            String primera = lector.readLine();
            int longitud = 0;
            String autorizacion = "";
            String linea = lector.readLine();
            while (linea != null && !linea.isEmpty()) {
                String enMinusculas = linea.toLowerCase(Locale.ROOT);
                if (enMinusculas.startsWith("content-length:")) {
                    longitud = Integer.parseInt(linea.substring(linea.indexOf(':') + 1).trim());
                }
                if (enMinusculas.startsWith("authorization:")) {
                    autorizacion = linea.substring(linea.indexOf(':') + 1).trim();
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
                    primera == null ? "" : primera,
                    autorizacion,
                    new String(cuerpo, 0, Math.max(leidos, 0)));
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

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
