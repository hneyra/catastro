package kamayuk.catastro.nucleo.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * De donde sale el {@code eventoId} de cada hecho publicado (C-8).
 *
 * <h2>Lo genera quien EMITE, y eso no es un detalle</h2>
 *
 * <p>El receptor deduplica por el ({@code catastro_evento_pk} de `V4` de `rentas`). Si lo generara
 * el transporte, dos entregas del mismo hecho serian dos hechos distintos y la deduplicacion seria
 * imposible — es exactamente lo que `V2` de `caja` dice de su {@code pagoId}.
 *
 * <h2>Es DERIVADO y no aleatorio, y los seis tipos no lo derivan igual</h2>
 *
 * <p>Un uuid aleatorio haria que volver a proyectar el padron produjera 14 422 hechos «nuevos» que
 * el receptor tendria que aplicar uno a uno para acabar escribiendo lo mismo. Derivandolo del
 * contenido, reproyectar cuesta —del lado del receptor— exactamente los predios que cambiaron.
 *
 * <p><b>La valuacion y el hallazgo firme son la excepcion, y es deliberada.</b> Se derivan de su
 * IDENTIDAD —ejercicio y predio la una, el identificador del hallazgo el otro— y no de su
 * contenido, porque son hechos firmados: que la misma identidad vuelva con otro contenido no es un
 * hecho nuevo, es el emisor reescribiendo uno sellado, y tiene que <b>verse</b>. El receptor lo ve
 * comparando la huella que `V9` le hizo guardar; con la identidad derivada del contenido, esa
 * comparacion nunca ocurriria —serian dos eventos distintos— y lo que fallaria seria la clave
 * primaria de {@code valuacion_predio} diciendo «ya hay una», que es cierto y no es la causa.
 *
 * <h2>Como se construye el uuid</h2>
 *
 * <p>sha256 del nombre, truncado a 16 bytes, con la version 8 de RFC 9562 («custom») y su variante.
 * <b>No es un uuid v5</b> —que exigiria SHA-1— y no hace falta que lo sea: esta derivacion no es un
 * contrato entre dos repositorios. Solo `catastro` la calcula; `rentas` recibe el valor y lo trata
 * como opaco, que es todo lo que un identificador tiene que ser.
 */
public final class IdentidadDelEvento {

    /**
     * Prefijo del nombre derivado. Existe para que dos sistemas que algun dia deriven identidades
     * con el mismo metodo no puedan producir la misma para hechos distintos.
     */
    private static final String ESPACIO = "kamayuk:catastro:evento:";

    /** El separador de campos del nombre: el mismo {@code U+001F} de {@link HuellaDelLote}. */
    private static final char SEPARADOR = HuellaDelLote.SEPARADOR;

    private IdentidadDelEvento() {}

    /**
     * La de una proyeccion de predio: derivada del CONTENIDO.
     *
     * <p>Reproyectar un predio que no cambio produce el mismo identificador, que el receptor
     * deduplica sin escribir nada.
     */
    public static UUID deUnPredioProyectado(long municipalidadId, long predioId, String huella) {
        return derivar(
                TipoDeEventoDeCatastro.PREDIO_PROYECTADO
                        + s()
                        + municipalidadId
                        + s()
                        + predioId
                        + s()
                        + huella);
    }

    /** La de una valuacion: derivada de la IDENTIDAD y NO del contenido. Ver la cabecera. */
    public static UUID deUnaValuacion(long municipalidadId, int ejercicio, long predioId) {
        return derivar(
                TipoDeEventoDeCatastro.VALUACION_PUBLICADA
                        + s()
                        + municipalidadId
                        + s()
                        + ejercicio
                        + s()
                        + predioId);
    }

    /**
     * La de un cierre de corrida: derivada de la CORRIDA.
     *
     * <p>Dos corridas del mismo ejercicio son dos hechos aunque produzcan el mismo resultado —lo
     * son: se corrieron dos veces— y el receptor sustituye su cierre por el ultimo, que es
     * exactamente lo que {@code valuacion_corrida} de `V5` de `rentas` admite al darle {@code
     * UPDATE} al ingestor.
     *
     * <p>Se derivo del contenido en una primera version y <b>no funciona</b>: el cuerpo del cierre
     * lleva el {@code corridaId}, que cambia en cada corrida, asi que dos corridas identicas
     * habrian salido como «la misma identidad con otro contenido» — el aviso de hecho sellado
     * reescrito, disparado por dos corridas que no reescribieron nada.
     */
    public static UUID deUnCierreDeCorrida(long municipalidadId, int ejercicio, long corridaId) {
        return derivar(
                TipoDeEventoDeCatastro.CORRIDA_CERRADA
                        + s()
                        + municipalidadId
                        + s()
                        + ejercicio
                        + s()
                        + corridaId);
    }

    /**
     * La de una manzana: derivada del CONTENIDO (#7).
     *
     * <p>Republicar el catalogo territorial entero no produce ni un hecho nuevo si nada cambio, que
     * es lo que hace que se pueda correr todos los dias.
     */
    public static UUID deUnaManzanaPublicada(long municipalidadId, long manzanaId, String huella) {
        return derivar(
                TipoDeEventoDeCatastro.MANZANA_PUBLICADA
                        + s()
                        + municipalidadId
                        + s()
                        + manzanaId
                        + s()
                        + huella);
    }

    /**
     * La de los frentes de un predio: derivada del CONTENIDO (#7).
     *
     * <p>El hecho es el conjunto de frentes del predio y no cada frente por separado: «a que da
     * este predio» es una respuesta, y partirla en N eventos dejaria al receptor sin poder saber
     * cuando la tiene entera —le llegaria el segundo frente de una esquina sin nada que le dijera
     * que faltaba, o que sobraba uno que se retiro—. Con el conjunto, un frente que desaparece
     * cambia la huella y produce otro hecho.
     */
    public static UUID deLosFrentesDeUnPredio(long municipalidadId, long predioId, String huella) {
        return derivar(
                TipoDeEventoDeCatastro.FRENTE_PUBLICADO
                        + s()
                        + municipalidadId
                        + s()
                        + predioId
                        + s()
                        + huella);
    }

    /**
     * La de un hallazgo firme: derivada de la IDENTIDAD y NO del contenido (#7, ADR-0035).
     *
     * <p>Por el mismo motivo que la valuacion, y hay que decirlo entero: un hallazgo es lo que una
     * <b>persona</b> verifico, con su nombre y su fecha. Que la misma identidad vuelva con otra
     * area verificada no es un hallazgo nuevo —es alguien reescribiendo lo que otro firmo— y el
     * buzon lo <b>para</b> con {@code HechoSelladoReescrito} en vez de mandarlo. Derivada del
     * contenido serian dos hechos distintos, el receptor aplicaria el segundo encima del primero
     * sin decir nada, y la unica traza de que alguien cambio un acta firmada se perderia.
     *
     * <p>No entra el candidato: un hallazgo cuelga de su candidato y ese vinculo no cambia, asi que
     * anadirlo no distinguiria dos hallazgos que hoy no se distingan.
     */
    public static UUID deUnHallazgoFirme(long municipalidadId, long hallazgoId) {
        return derivar(
                TipoDeEventoDeCatastro.HALLAZGO_FIRME + s() + municipalidadId + s() + hallazgoId);
    }

    private static String s() {
        return String.valueOf(SEPARADOR);
    }

    private static UUID derivar(String nombre) {
        byte[] resumen = sha256(ESPACIO + nombre);
        byte[] bytes = new byte[16];
        System.arraycopy(resumen, 0, bytes, 0, 16);
        // Version 8 (RFC 9562 §5.8) y variante RFC. Sin estos dos retoques el valor seria un
        // entero de 128 bits disfrazado de uuid, y `uuid` de PostgreSQL lo aceptaria igual —
        // pero cualquier herramienta que lea la version diria «desconocida» en vez de «derivado».
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x80);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);
        long alto = 0;
        long bajo = 0;
        for (int i = 0; i < 8; i++) {
            alto = (alto << 8) | (bytes[i] & 0xFFL);
        }
        for (int i = 8; i < 16; i++) {
            bajo = (bajo << 8) | (bytes[i] & 0xFFL);
        }
        return new UUID(alto, bajo);
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 es obligatorio en toda JVM", imposible);
        }
    }
}
