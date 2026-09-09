package kamayuk.catastro.verificaciones;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kamayuk.comun.verificaciones.contrato.ContratoDelConsumidor;
import kamayuk.comun.verificaciones.contrato.ContratoQueSePublicaTestBase;

/**
 * Lo que este backend pide y lee del buzon de {@code identidad} (ADR-0039 etapa 4, ADR-0030 §4).
 *
 * <p>Es la mitad del CONSUMIDOR del contrato: se publica en {@code
 * docs/50-api/contratos-que-consume/identidad.json} y lo comprueba el CI de {@code identidad}
 * contra sus controladores —{@code ContratoConCatastroTest}, que lleva {@code @Disabled} desde la
 * etapa 3 esperando a este archivo—. Un campo que {@code identidad} deje de publicar pone rojo SU
 * build, que es donde esta quien lo rompio.
 *
 * <p>Las dos operaciones son exactamente las que {@code ClienteHttpDelBuzonDeIdentidad} pide y lee.
 * El cuerpo del evento viaja como TEXTO con JSON dentro —{@code "cuerpo": "texto"}— y no como
 * objeto, a proposito: el consumidor lo guarda tal cual en la cola de muertos cuando no se puede
 * leer, y para eso tiene que llegar como llego.
 */
public class ContratoQueConsumeDeIdentidad extends ContratoQueSePublicaTestBase {

    /** Un evento, tal como lo lee {@code ClienteHttpDelBuzonDeIdentidad.leer}. */
    public static final Map<String, Object> EVENTO =
            ordenados(
                    Map.entry("eventoId", "texto"),
                    Map.entry("secuencia", "entero"),
                    Map.entry("tipo", "texto"),
                    Map.entry("sujetoId", "entero"),
                    Map.entry("cuerpo", "texto"),
                    Map.entry("huella", "texto"),
                    Map.entry("creadoEn", "texto"));

    @Override
    protected ContratoDelConsumidor contrato() {
        Map<String, ContratoDelConsumidor.OperacionEsperada> operaciones = new LinkedHashMap<>();

        operaciones.put(
                "GET /eventos/pendientes",
                ContratoDelConsumidor.OperacionEsperada.lectura(
                        Set.of("limite"),
                        ordenados(
                                Map.entry("eventos", List.of(EVENTO)),
                                Map.entry("quedan", "entero"))));

        // Una escritura cuya respuesta SI se lee, asi que se declaran las dos mitades: el cuerpo
        // que
        // se manda y lo que se espera de vuelta. `escritura(cuerpo)` dejaria la respuesta vacia y
        // `identidad` podria dejar de publicar `recibidos` sin que nadie lo notara.
        operaciones.put(
                "POST /eventos/acuses",
                new ContratoDelConsumidor.OperacionEsperada(
                        Set.of(),
                        ordenados(
                                Map.entry("recibidos", "entero"),
                                Map.entry("escritos", "entero"),
                                Map.entry("quedan", "entero")),
                        ordenados(Map.entry("eventos", List.of("texto")))));

        return new ContratoDelConsumidor("catastro", "identidad", "/identidad/api/v1", operaciones);
    }

    @SafeVarargs
    static Map<String, Object> ordenados(Map.Entry<String, Object>... campos) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (Map.Entry<String, Object> campo : campos) {
            mapa.put(campo.getKey(), campo.getValue());
        }
        // `unmodifiableMap` sobre un `LinkedHashMap`, no `Map.copyOf`: el archivo comprometido se
        // compara byte a byte, y el orden de `Map.copyOf` no esta especificado.
        return Collections.unmodifiableMap(mapa);
    }
}
