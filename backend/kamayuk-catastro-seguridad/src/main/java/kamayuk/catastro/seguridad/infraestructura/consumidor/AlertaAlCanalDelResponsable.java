package kamayuk.catastro.seguridad.infraestructura.consumidor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import kamayuk.catastro.seguridad.aplicacion.AlertaDeEventosSinAplicar;
import kamayuk.catastro.seguridad.dominio.EventoRecibido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * La alerta, escrita con nivel ERROR y —cuando el canal lo admite— ENTREGADA con un {@code POST}
 * (ADR-0026 §4).
 *
 * <p>Las dos cosas, y por que la segunda es condicional, esta en {@link ResponsableDelConsumidor}.
 *
 * <h2>Un canal que no contesta NO tumba la vuelta</h2>
 *
 * <p>El evento ya esta apartado y acusado, o sea que la cola sigue corriendo. Dejar que un webhook
 * caido lanzara desde aqui pararia el consumidor entero por no poder avisar de <b>un</b> evento. Lo
 * que se hace es registrar el fallo de entrega, tambien con nivel ERROR.
 */
public class AlertaAlCanalDelResponsable implements AlertaDeEventosSinAplicar {

    private static final Logger REGISTRO =
            LoggerFactory.getLogger(AlertaAlCanalDelResponsable.class);

    private static final Duration ESPERA = Duration.ofSeconds(10);

    private final HttpClient cliente;
    private final JsonMapper json;
    private final ResponsableDelConsumidor responsable;

    public AlertaAlCanalDelResponsable(JsonMapper json, ResponsableDelConsumidor responsable) {
        this.json = json;
        this.responsable = responsable;
        this.cliente = HttpClient.newBuilder().connectTimeout(ESPERA).build();
    }

    @Override
    public void hayUnEventoSinAplicar(
            EventoRecibido evento, String motivo, long muertosSinExplicar) {
        String texto =
                "LA COPIA LOCAL DE LA AUTORIZACION ESTA INCOMPLETA: el evento "
                        + evento.eventoId()
                        + " ("
                        + evento.tipoPublicado()
                        + ", sujeto "
                        + evento.sujetoId()
                        + ", secuencia "
                        + evento.secuencia()
                        + ") de `identidad` no se pudo aplicar y se aparto. Motivo: "
                        + motivo
                        + ". Hay "
                        + muertosSinExplicar
                        + " evento(s) apartados sin explicar. Mientras esten ahi, `catastro` dice"
                        + " de los permisos algo que `identidad` ya no dice, y lo que se ve es un"
                        + " 403 a quien lo tiene —o una revocacion que no llego— (ADR-0039,"
                        + " ADR-0026 §4).";
        REGISTRO.error("{} Responsable: {}", texto, responsable);
        if (responsable.seLeEntrega()) {
            entregar(
                    new Aviso(
                            responsable.nombre(),
                            evento.eventoId().toString(),
                            motivo,
                            muertosSinExplicar,
                            texto));
        }
    }

    /**
     * Entrega el aviso, y si no se puede lo dice.
     *
     * <p>Se atrapa {@code RuntimeException} a proposito: lo que se atrapa aqui no es un defecto
     * sino <b>un canal que no contesta</b>, y la alternativa es que un webhook caido pare el
     * consumidor entero. No se traga: se registra con nivel ERROR.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void entregar(Aviso aviso) {
        try {
            HttpRequest peticion =
                    HttpRequest.newBuilder(URI.create(responsable.canal()))
                            .timeout(ESPERA)
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            json.writeValueAsString(aviso)))
                            .build();
            HttpResponse<String> respuesta =
                    cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 300) {
                REGISTRO.error(
                        "El canal {} contesto {} al aviso: el responsable NO se ha enterado por"
                                + " ahi, y la unica constancia es la linea de arriba",
                        responsable.canal(),
                        respuesta.statusCode());
            }
        } catch (IOException | RuntimeException noSePudo) {
            REGISTRO.error(
                    "Y el aviso NO se pudo entregar en {}: {}. La unica constancia es la linea de"
                            + " arriba",
                    responsable.canal(),
                    noSePudo.toString());
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            REGISTRO.error("Se interrumpio al entregar el aviso en {}", responsable.canal());
        }
    }

    /** Lo que se manda al canal. */
    record Aviso(
            String responsable,
            String eventoId,
            String motivo,
            long muertosSinExplicar,
            String texto) {}
}
