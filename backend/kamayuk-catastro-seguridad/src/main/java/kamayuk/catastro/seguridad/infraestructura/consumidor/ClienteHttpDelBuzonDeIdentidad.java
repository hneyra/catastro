package kamayuk.catastro.seguridad.infraestructura.consumidor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kamayuk.catastro.seguridad.dominio.EventoRecibido;
import kamayuk.catastro.seguridad.dominio.FuenteDeEventosDeIdentidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Trae los eventos del buzon de {@code identidad} y los acusa (ADR-0039 etapas 3 y 4).
 *
 * <p>Lo que pide y lo que lee es exactamente lo que declara {@code
 * docs/50-api/contratos-que-consume/identidad.json}, que este repositorio publica y el CI de {@code
 * identidad} comprueba (ADR-0030 §4): {@code GET /eventos/pendientes?limite=} con {@code eventos[]}
 * y {@code quedan}, y {@code POST /eventos/acuses} con {@code {"eventos": [...]}}.
 *
 * <h2>Corre SIN USUARIO DELANTE, y eso decide como se autentica</h2>
 *
 * <p>Lo llama un proceso por lotes: no hay ninguna peticion en curso de la que sacar un {@code
 * Authorization}. <b>Pide el suyo</b> con {@code client_credentials} y la clave de su cliente
 * confidencial —uno por sistema y municipalidad, ADR-0028 §2— a traves de {@link
 * CredencialDeServicio}. Sin identidad configurada la llamada sale sin credencial y el destino la
 * rechaza con 401, que sigue siendo lo correcto: es lo que hace que el compose sin identidad de
 * servicio no se pase la vida pidiendo tokens que nadie va a dar.
 *
 * <h2>Todo fallo de aqui es TRANSITORIO, los 401 y 403 incluidos</h2>
 *
 * <p>Todo lo que este cliente lanza es {@link FuenteDeEventosDeIdentidad.IdentidadNoContesta}. Un
 * 401 o un 403 <b>si</b> cambia solo —en cuanto la cuenta de servicio se cree, se le conceda el
 * acceso {@code eventos} o se afilie al grupo de consumidores—, asi que esta del lado de lo que se
 * reintenta (la leccion de {@code caja}#21 AC-3). Lo que <b>no</b> puede pasar es que un fallo de
 * transporte mate un evento.
 *
 * <h2>Un 4xx de negocio al ACUSAR se registra y no tumba la vuelta</h2>
 *
 * <p>{@code identidad} contesta 422 a un acuse que nombra un evento que no es de este consumidor o
 * que no existe. Los eventos que iban en ese acuse YA estan aplicados aqui, asi que lo que hay que
 * hacer no es reintentar —volveria a dar 422— sino decirlo con nivel ERROR y seguir: el emisor los
 * volvera a servir y la deduplicacion los descartara.
 */
public class ClienteHttpDelBuzonDeIdentidad implements FuenteDeEventosDeIdentidad {

    private static final Logger log = LoggerFactory.getLogger(ClienteHttpDelBuzonDeIdentidad.class);

    private static final Duration ESPERA_DE_CONEXION = Duration.ofSeconds(5);
    private static final Duration ESPERA_DE_LECTURA = Duration.ofSeconds(30);

    /** La raiz de la API de `identidad`, tal como la publica su `Api.RAIZ`. */
    public static final String RAIZ_DE_LA_API = "/identidad/api/v1";

    private static final String PENDIENTES = RAIZ_DE_LA_API + "/eventos/pendientes";
    private static final String ACUSES = RAIZ_DE_LA_API + "/eventos/acuses";

    private final HttpClient cliente;
    private final JsonMapper json;
    private final String raiz;
    private final CredencialDeServicio credencial;

    /**
     * @param raiz el anfitrion de `identidad`, sin la ruta de la API: {@code
     *     http://kamayuk-identidad-web.kamayuk-identidad-stg} en el cluster, {@code
     *     http://identidad-sistema:8080} en el compose
     */
    public ClienteHttpDelBuzonDeIdentidad(
            JsonMapper json, String raiz, CredencialDeServicio credencial) {
        this.json = json;
        this.raiz = raiz.endsWith("/") ? raiz.substring(0, raiz.length() - 1) : raiz;
        this.credencial = credencial;
        this.cliente = HttpClient.newBuilder().connectTimeout(ESPERA_DE_CONEXION).build();
    }

    /** Si este despliegue dice donde esta `identidad`. Sin eso, el consumidor no corre. */
    public boolean configurado() {
        return !raiz.isBlank();
    }

    @Override
    public Lote pendientes(int limite) {
        JsonNode cuerpo = pedir(PENDIENTES + "?limite=" + limite, "leer el buzon de identidad");
        List<EventoRecibido> eventos = new ArrayList<>();
        for (JsonNode evento : cuerpo.path("eventos")) {
            eventos.add(leer(evento));
        }
        return new Lote(eventos, cuerpo.path("quedan").asLong(0));
    }

    @Override
    public void acusar(List<UUID> eventoIds) {
        if (eventoIds.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (UUID id : eventoIds) {
            ids.add(id.toString());
        }
        HttpRequest.Builder peticion =
                HttpRequest.newBuilder(URI.create(raiz + ACUSES))
                        .timeout(ESPERA_DE_LECTURA)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        escribir(new PeticionDeAcuse(List.copyOf(ids)))));
        conCredencial(peticion);
        HttpResponse<String> respuesta = enviar(peticion, "acusar los eventos aplicados");
        int estado = respuesta.statusCode();
        if (estado == 200) {
            return;
        }
        if (estado >= 400 && estado < 500 && estado != 401 && estado != 403) {
            log.error(
                    "`identidad` contesto {} al acusar {} evento(s). Los eventos SI estan aplicados"
                            + " aqui: se volveran a servir y se descartaran por deduplicacion. Lo"
                            + " que dijo: {}",
                    estado,
                    ids.size(),
                    respuesta.body());
            return;
        }
        throw new IdentidadNoContesta(
                "`identidad` contesto "
                        + estado
                        + " al acusar"
                        + porQueLaCredencial(estado)
                        + ". Los eventos SI estan aplicados aqui: se volveran a servir y se"
                        + " descartaran por deduplicacion");
    }

    // ------------------------------------------------------------------

    private EventoRecibido leer(JsonNode evento) {
        try {
            return new EventoRecibido(
                    UUID.fromString(evento.path("eventoId").asString("")),
                    evento.path("secuencia").asLong(-1),
                    evento.path("tipo").asString(""),
                    evento.path("sujetoId").asLong(0),
                    evento.path("cuerpo").asString(""),
                    evento.path("huella").asString(""),
                    Instant.parse(evento.path("creadoEn").asString("")));
        } catch (IllegalArgumentException | DateTimeParseException malFormado) {
            throw new IdentidadNoContesta(
                    "El buzon de `identidad` contesto algo que no tiene la forma de un evento: "
                            + malFormado.getMessage());
        }
    }

    private JsonNode pedir(String ruta, String que) {
        if (raiz.isBlank()) {
            throw new IdentidadNoContesta(que + ": kamayuk.identidad.url no esta configurada");
        }
        HttpRequest.Builder peticion =
                HttpRequest.newBuilder(URI.create(raiz + ruta))
                        .timeout(ESPERA_DE_LECTURA)
                        .header("Accept", "application/json")
                        .GET();
        conCredencial(peticion);
        HttpResponse<String> respuesta = enviar(peticion, que);
        if (respuesta.statusCode() != 200) {
            throw new IdentidadNoContesta(
                    "`identidad` contesto "
                            + respuesta.statusCode()
                            + " al "
                            + que
                            + porQueLaCredencial(respuesta.statusCode()));
        }
        try {
            return json.readTree(respuesta.body());
        } catch (JacksonException ilegible) {
            throw new IdentidadNoContesta(
                    "`identidad` contesto algo que no es JSON al " + que, ilegible);
        }
    }

    /** Un 401 o un 403 se arreglan en el despliegue, y el mensaje dice donde. */
    private String porQueLaCredencial(int estado) {
        if (estado == 401) {
            return credencial.cabecera().isBlank()
                    ? " porque este consumidor no manda ninguna credencial:"
                            + " kamayuk.identidad.{token,cliente,credencial} estan vacias"
                    : " porque la credencial que este consumidor manda no vale para `identidad`";
        }
        if (estado == 403) {
            return ": la cuenta de servicio existe y `identidad` no le concede el acceso"
                    + " `eventos` —hay que afiliarla al grupo «Consumidores del buzon»— o el token"
                    + " no es de una cuenta de servicio";
        }
        return "";
    }

    /**
     * Pone la cabecera si la hay.
     *
     * <p>Se pide AQUI y no en el constructor: un token caduca, y uno pedido al construir el bean
     * estaria muerto en la vuelta de dentro de cinco minutos.
     */
    private void conCredencial(HttpRequest.Builder peticion) {
        String cabecera = credencial.cabecera();
        if (!cabecera.isBlank()) {
            peticion.header("Authorization", cabecera);
        }
    }

    private HttpResponse<String> enviar(HttpRequest.Builder peticion, String que) {
        try {
            return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException noContesta) {
            throw new IdentidadNoContesta("No se pudo " + que, noContesta);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            throw new IdentidadNoContesta("Se interrumpio al " + que, interrumpido);
        }
    }

    private String escribir(Object cuerpo) {
        try {
            return json.writeValueAsString(cuerpo);
        } catch (JacksonException noSePuede) {
            throw new IllegalStateException("No se pudo componer el acuse", noSePuede);
        }
    }

    /** Lo que se manda al acusar. Es la forma que `EventosController` de `identidad` lee. */
    private record PeticionDeAcuse(List<String> eventos) {}
}
