package kamayuk.catastro.contribuyentes.infraestructura;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import kamayuk.catastro.contribuyentes.DirectorioDeContribuyentes;
import kamayuk.catastro.contribuyentes.ResumenDeContribuyente;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * El padron de contribuyentes, preguntado por HTTP a {@code rentas} (ADR-0029, ADR-0030).
 *
 * <h2>Por que el padron no esta aqui</h2>
 *
 * <p>Porque ADR-0029 lo dejo en {@code rentas} a proposito: «es la base del grafo y no depende de
 * nadie; sacarlo multiplica por cuatro las llamadas de cada pantalla sin resolver ningun problema
 * existente». De el, {@code catastro} necesita <b>una sola cosa</b>: como se llama el titular de un
 * predio, para pintar la grilla de fichas y el reporte de ficha del contribuyente. Ni su deuda, ni
 * sus papeletas, ni su domicilio salvo para el papel.
 *
 * <h2>El contexto de municipalidad NO viaja en ningun parametro</h2>
 *
 * <p>Ni en el cuerpo, ni en la ruta, ni en una cabecera propia (ADR-0028). Los cuatro metodos de
 * {@link DirectorioDeContribuyentes} no reciben {@code municipalidadId} —la regla 2 lo prohibe— y
 * este cliente no lo inventa: reenvia el {@code Authorization} de la peticion que se esta
 * atendiendo, y {@code rentas} valida ESE token y fija su propio {@code SET LOCAL}.
 *
 * <p><b>HUECO DECLARADO, y conviene tenerlo escrito aqui y no descubrirlo en produccion:</b> lo que
 * ADR-0028 §1 pide es un token <b>delegado</b> por intercambio (RFC 8693), con la audiencia de
 * {@code rentas}. Eso exige un emisor configurado para el intercambio y no esta construido. Lo que
 * hace hoy este cliente es <b>reenviar</b> el token del funcionario tal cual, que conserva el
 * sujeto y el claim {@code municipalidad_id} —o sea, es correcto en lo que importa, el aislamiento—
 * y pierde lo que la delegacion aporta: que la bitacora de {@code rentas} pueda distinguir «lo
 * pidio catastro en nombre de fulano» de «lo pidio fulano». Cuando el intercambio exista, lo unico
 * que cambia es {@link #token()}.
 *
 * <p>Y en una corrida sin usuario delante —la valuacion— no hay peticion de la que sacar el token:
 * ahi {@link #token()} devuelve vacio, la llamada sale sin credencial y {@code rentas} la rechaza.
 * Es deliberado: preferimos que falle a que una corrida nocturna se invente una identidad. ADR-0028
 * §2 dice como se cierra —un token acotado a la municipalidad y a la operacion, entregado al abrir
 * la corrida—, y eso tampoco esta construido.
 *
 * <h2>Ningun metodo que pregunte por uno dentro de un bucle</h2>
 *
 * <p>{@link #porIds(Set)} recibe el conjunto entero, igual que en el monolito, y por eso una pagina
 * de veinte fichas cuesta <b>una</b> peticion y no veinte. Lo sostiene la forma del puerto, que no
 * cambio al cruzar la frontera: es exactamente el argumento que P5B dejo escrito para {@code
 * PublicadorDeNormativa}.
 */
@Component
public class DirectorioHttpDeRentas implements DirectorioDeContribuyentes {

    private static final Duration ESPERA_DE_CONEXION = Duration.ofSeconds(5);
    private static final Duration ESPERA_DE_LECTURA = Duration.ofSeconds(30);

    private final HttpClient cliente;
    private final ObjectMapper json;
    private final String raiz;

    public DirectorioHttpDeRentas(ObjectMapper json, @Value("${kamayuk.rentas.url:}") String raiz) {
        this.json = json;
        this.raiz = raiz.endsWith("/") ? raiz.substring(0, raiz.length() - 1) : raiz;
        this.cliente = HttpClient.newBuilder().connectTimeout(ESPERA_DE_CONEXION).build();
    }

    @Override
    public List<ResumenDeContribuyente> buscar(String texto, int maximo) {
        JsonNode cuerpo =
                pedir(
                        "/rentas/contribuyentes?texto="
                                + URLEncoder.encode(texto, StandardCharsets.UTF_8)
                                + "&tamano="
                                + maximo,
                        "buscar contribuyentes");
        List<ResumenDeContribuyente> encontrados = new ArrayList<>();
        for (JsonNode fila : cuerpo.path("contenido")) {
            encontrados.add(resumen(fila));
        }
        return List.copyOf(encontrados);
    }

    @Override
    public Optional<ResumenDeContribuyente> porCodigo(String codigo) {
        JsonNode cuerpo =
                pedirOVacio(
                        "/rentas/contribuyentes/"
                                + URLEncoder.encode(codigo, StandardCharsets.UTF_8),
                        "resolver el contribuyente " + codigo);
        return cuerpo == null ? Optional.empty() : Optional.of(resumen(cuerpo));
    }

    @Override
    public Map<Long, ResumenDeContribuyente> porIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        StringJoiner lista = new StringJoiner(",");
        ids.forEach(id -> lista.add(Long.toString(id)));
        JsonNode cuerpo =
                pedir(
                        "/rentas/contribuyentes?ids=" + lista,
                        "resolver " + ids.size() + " titulares");
        Map<Long, ResumenDeContribuyente> porId = new LinkedHashMap<>();
        for (JsonNode fila : cuerpo.path("contenido")) {
            ResumenDeContribuyente uno = resumen(fila);
            porId.put(uno.id(), uno);
        }
        return Map.copyOf(porId);
    }

    @Override
    public Optional<String> domicilioFiscalDe(long contribuyenteId, LocalDate fecha) {
        JsonNode cuerpo =
                pedirOVacio(
                        "/rentas/contribuyentes/"
                                + contribuyenteId
                                + "/domicilio?aLaFecha="
                                + fecha,
                        "resolver el domicilio de " + contribuyenteId);
        if (cuerpo == null || cuerpo.path("direccion").isMissingNode()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cuerpo.path("direccion").asText(null));
    }

    private static ResumenDeContribuyente resumen(JsonNode fila) {
        return new ResumenDeContribuyente(
                fila.path("id").asLong(),
                fila.path("codigo").asText(""),
                fila.path("nombre").asText(""),
                fila.path("documento").asText(""));
    }

    private JsonNode pedir(String ruta, String que) {
        JsonNode cuerpo = pedirOVacio(ruta, que);
        if (cuerpo == null) {
            throw new PadronInalcanzable(que + ": el padron contesto 404", null);
        }
        return cuerpo;
    }

    /** Devuelve {@code null} para un 404, que aqui significa «no esta en el padron». */
    private @Nullable JsonNode pedirOVacio(String ruta, String que) {
        if (raiz.isBlank()) {
            throw new PadronInalcanzable(que + ": kamayuk.rentas.url no esta configurada", null);
        }
        HttpRequest.Builder peticion =
                HttpRequest.newBuilder(URI.create(raiz + ruta))
                        .timeout(ESPERA_DE_LECTURA)
                        .header("Accept", "application/json")
                        .GET();
        token().ifPresent(t -> peticion.header("Authorization", t));
        try {
            HttpResponse<String> respuesta =
                    cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() == 404) {
                return null;
            }
            if (respuesta.statusCode() != 200) {
                throw new PadronInalcanzable(
                        que + " (contesto " + respuesta.statusCode() + ")", null);
            }
            return json.readTree(respuesta.body());
        } catch (IOException noContesta) {
            throw new PadronInalcanzable(que, noContesta);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            throw new PadronInalcanzable(que, interrumpido);
        }
    }

    /**
     * El {@code Authorization} de la peticion que se esta atendiendo, si hay una.
     *
     * <p>Vacio en una corrida sin usuario delante. Ver el javadoc de la clase: es deliberado que
     * entonces la llamada salga sin credencial y {@code rentas} la rechace.
     */
    private static Optional<String> token() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (!(atributos instanceof ServletRequestAttributes servlet)) {
            return Optional.empty();
        }
        return Optional.ofNullable(servlet.getRequest().getHeader("Authorization"));
    }

    /** El padron no contesta. No es «esa persona no existe»: es que no se pudo preguntar. */
    public static final class PadronInalcanzable extends RuntimeException {
        public PadronInalcanzable(String que, @Nullable Throwable causa) {
            super("No se pudo " + que + ". El padron de contribuyentes vive en `rentas`", causa);
        }
    }
}
