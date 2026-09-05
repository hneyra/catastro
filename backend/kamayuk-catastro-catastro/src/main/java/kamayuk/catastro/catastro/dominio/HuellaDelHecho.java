package kamayuk.catastro.catastro.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * La huella del cuerpo de un hecho publicado, y la agregada de una corrida (C-8, ADR-0027).
 *
 * <h2>Para que sirve cada una</h2>
 *
 * <ul>
 *   <li><b>La del hecho</b> viaja dentro del evento y el receptor la copia en cada fila que escribe
 *       ({@code V9} de `rentas`, columnas {@code huella}). Alli <b>no se recalcula</b> —eso
 *       comprobaria que lo que se tiene es igual a lo que se tiene—, y lo que compra es que una
 *       reentrega del MISMO {@code eventoId} con OTRO contenido se pueda ver en vez de descartarse
 *       en silencio por la deduplicacion.
 *   <li><b>La agregada</b> viaja en el cierre de la corrida y es la mitad del candado de ADR-0027
 *       §2: `rentas` la compara contra la que calcula sobre lo que le llego. Las dos mitades vienen
 *       de sitios distintos a proposito.
 * </ul>
 *
 * <h2>La agregada ES UN CONTRATO ENTRE DOS REPOSITORIOS, y hay que decirlo aqui</h2>
 *
 * <p>Del otro lado la calcula {@code ValuacionRecibidaJdbc.huellaDeLoRecibido} <b>en SQL</b>:
 *
 * <pre>{@code
 * encode(sha256(convert_to(
 *          coalesce(string_agg(h.huella, ',' ORDER BY h.predio_id), ''), 'UTF8')), 'hex')
 * }</pre>
 *
 * <p>Asi que aqui hay que reproducirlo <b>hasta el byte</b>: las huellas de cada predio unidas por
 * <b>coma</b>, en orden ascendente de {@code predioId}, en UTF-8, sha256, hexadecimal minusculo; y
 * una corrida sin ningun predio da la huella de la <b>cadena vacia</b>, porque {@code string_agg}
 * de cero filas es {@code NULL} y el {@code coalesce} lo vuelve {@code ''}.
 *
 * <p>Si los dos calculos no coincidieran, el candado no fallaria ruidosamente: <b>se cerraria
 * siempre</b>, con el mensaje «llego el numero correcto de valuaciones y NO son las mismas», y la
 * emision quedaria bloqueada para siempre por un defecto de codigo que se lee como uno de datos. Es
 * el mismo modo de fallo que {@link HuellaDelLote} documenta para la anti-entropia, y por eso el
 * separador aqui es la <b>coma</b> y no el {@code U+001F} de aquella: no se elige, lo fija el SQL
 * que ya esta escrito del otro lado. Una coma es segura <b>en este sitio y solo aqui</b> porque lo
 * que se une son huellas hexadecimales, donde una coma no puede aparecer.
 *
 * <p>Que las dos implementaciones coinciden no se razona: lo mide el recorrido de extremo a extremo
 * de C-8, donde el candado <b>se abre</b> tras entregar la corrida entera y <b>se niega</b> con una
 * sola valuacion sin entregar.
 */
public final class HuellaDelHecho {

    /**
     * El separador entre huellas al componer la agregada de una corrida.
     *
     * <p>No es una preferencia: es el que {@code string_agg(h.huella, ',' ...)} usa del otro lado.
     */
    public static final char SEPARADOR_DE_LA_CORRIDA = ',';

    private HuellaDelHecho() {}

    /**
     * La huella del cuerpo de un hecho: sha256 hexadecimal minusculo de sus campos.
     *
     * <p>Los campos van unidos por {@code U+001F} —el mismo de {@link HuellaDelLote} y por el mismo
     * motivo: es la unica familia de caracteres que ningun campo de este dominio puede contener, y
     * con un separador que si pudiera aparecer dos hechos distintos producen la misma
     * concatenacion—. Un campo nulo es la <b>cadena vacia</b>, y por eso el separador importa
     * doble: sin el, un campo nulo y uno vacio serian indistinguibles de un desplazamiento.
     */
    public static String deLosCampos(List<@Nullable String> campos) {
        StringBuilder nombre = new StringBuilder();
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                nombre.append(HuellaDelLote.SEPARADOR);
            }
            String campo = campos.get(i);
            nombre.append(campo == null ? "" : campo);
        }
        return sha256(nombre.toString());
    }

    /**
     * La huella agregada de una corrida, reproduciendo el SQL del receptor.
     *
     * @param huellasEnOrdenDePredio las huellas de cada valuacion, <b>ya ordenadas por {@code
     *     predioId} ascendente</b>. El orden no se impone aqui a proposito: quien recorre los
     *     predios es quien sabe en que orden los leyo, y ordenar dos veces esconderia que el
     *     recorrido no estuviera ordenado
     */
    public static String deUnaCorrida(List<String> huellasEnOrdenDePredio) {
        return sha256(String.join(String.valueOf(SEPARADOR_DE_LA_CORRIDA), huellasEnOrdenDePredio));
    }

    private static String sha256(String texto) {
        try {
            byte[] resumen =
                    MessageDigest.getInstance("SHA-256")
                            .digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 es obligatorio en toda JVM", imposible);
        }
    }
}
