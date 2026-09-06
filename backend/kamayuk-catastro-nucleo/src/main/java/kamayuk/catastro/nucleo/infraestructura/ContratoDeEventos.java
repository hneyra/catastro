package kamayuk.catastro.nucleo.infraestructura;

import java.util.List;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Dinero;
import kamayuk.catastro.dominio.Porcentaje;
import org.jspecify.annotations.Nullable;

/**
 * La forma del cuerpo de cada evento: <b>el contrato con {@code rentas}</b> (C-8, ADR-0027).
 *
 * <h2>Los importes y las areas van TIPADOS, y las fechas como cadena ISO</h2>
 *
 * <p>Un importe viaja como CADENA en el JSON (RNF-055, regla 1) —leerlo como numero de coma
 * flotante al otro lado volveria a introducir por la puerta de atras el defecto que {@code Dinero}
 * existe para evitar—, pero <b>quien lo convierte es el serializador</b> que {@code
 * ConfiguracionDeJson} registra, no este archivo. Escribir aqui la cadena a mano seria una segunda
 * convencion para lo mismo, que es exactamente el defecto que #607 midio con el area del mismo
 * predio dicha de tres formas.
 *
 * <p>Las fechas si van como cadena ISO, y a proposito: una fecha sin zona que se convierte es el
 * defecto que el arnes {@code fechas} del frontend mide, y {@code LocalDate} no tiene serializador
 * propio en esta configuracion.
 *
 * <p>Se declara con {@code record}s y no con un mapa porque el orden de las claves de un {@code
 * record} es el de su declaracion, y eso hace que el cuerpo congelado sea reproducible: el mismo
 * hecho serializado dos veces da los mismos bytes.
 *
 * <p><b>La huella NO se calcula sobre este JSON</b> sino sobre los campos canonicos que {@link
 * ComponedorDeHechos} enumera. Calcularla sobre el JSON ataria el hecho sellado al formateo de una
 * libreria: cambiar de version de Jackson cambiaria la huella de todo lo ya publicado.
 */
public final class ContratoDeEventos {

    private ContratoDeEventos() {}

    /** El cuerpo de {@code PREDIO_PROYECTADO}. */
    public record PredioProyectado(
            long predioId,
            String codigoRefCatastral,
            String direccion,
            @Nullable String sectorCodigo,
            String estado,
            List<FichaProyectada> fichas) {}

    /** Una version de ficha dentro de la proyeccion de su predio. */
    public record FichaProyectada(
            long fichaId,
            String tipo,
            int version,
            String vigenciaDesde,
            @Nullable String vigenciaHasta,
            AreaM2 areaTerreno,
            String uso) {}

    /**
     * El cuerpo de {@code VALUACION_PUBLICADA}: el hecho sellado de ADR-0027 §1.
     *
     * <p>Lleva los titulares con su cuota vigente a la fecha de corte, y {@code rentas} <b>no los
     * proyecta</b>: `V4` decidio a proposito no replicar la titularidad —se resuelve por lote
     * despues de paginar y nunca entra en un predicado—. Viajan igualmente porque son parte del
     * hecho: el {@code %} pondera la base imponible de cada predio (NEG-05 §1), asi que una
     * valuacion que no dijera con que cuotas se calculo no se podria recalcular en 2036.
     */
    public record ValuacionPublicada(
            long predioId,
            int ejercicio,
            String fechaDeCorte,
            @Nullable Dinero valorTerreno,
            @Nullable Dinero valorConstruccion,
            @Nullable Dinero valorObras,
            @Nullable Dinero valorDelPredio,
            @Nullable String motivo,
            @Nullable String llaveQueFalta,
            @Nullable Long fichaCatastralId,
            long conjuntoId,
            String reglasVersion,
            String reglasAplicadas,
            List<TitularConCuota> titulares) {}

    /** Un titular del predio con su cuota, a la fecha de corte. */
    public record TitularConCuota(long contribuyenteId, String condicion, Porcentaje cuota) {}

    /**
     * El cuerpo de {@code CORRIDA_CERRADA}.
     *
     * <p>{@code conteo} y {@code huella} son la mitad del candado de ADR-0027 §2 que viene de aqui;
     * la otra la calcula {@code rentas} sobre lo que le llego. Las dos mitades vienen de sitios
     * distintos a proposito: derivadas del mismo sitio, la comprobacion diria que lo que se tiene
     * es igual a lo que se tiene.
     */
    public record CorridaCerrada(
            long corridaId,
            int ejercicio,
            String fechaDeCorte,
            long conjuntoId,
            String reglasVersion,
            int conteo,
            String huella,
            String cerradaEn) {}

    /** El cuerpo de {@code MANZANA_PUBLICADA} (#7). */
    public record ManzanaPublicada(
            long manzanaId, String codigo, String sectorCodigo, String sectorNombre) {}

    /**
     * El cuerpo de {@code FRENTE_PUBLICADO}: los metros lineales de un predio y a que vias dan
     * (#7).
     *
     * <p><b>Ni un importe y ni un servicio.</b> Este es el insumo de los arbitrios y el importe lo
     * determina {@code rentas} (ADR-0024): aqui no aparece ninguno de los tres valores del
     * enumerado {@code Servicio} de aquel repositorio, ni ningun factor de barrido. Lo comprueba
     * {@code CatastroNoNombraUnArbitrioTest} sobre el arbol entero — y este parrafo los nombraba
     * uno a uno hasta que esa prueba lo puso rojo.
     */
    public record FrentePublicado(
            long predioId, String codigoRefCatastral, List<FrenteDelPredio> frentes) {}

    /**
     * Un frente dentro del hecho de su predio.
     *
     * <p>{@code longitudEstado} viaja porque separa dos cifras que se leen igual: {@code PROPUESTA}
     * la corto una maquina contra el eje de la via y {@code CONFIRMADA} la firmo una persona
     * (ADR-0021). Determinar sobre la primera es legitimo mientras se sepa que se esta haciendo, y
     * sin este campo no hay forma de saberlo.
     *
     * <p><b>La longitud sale con su unidad dentro —{@code "18.50 ML"}— y no tipada</b>, que es lo
     * que este sistema ya hace con toda {@code Medida} (ver {@code FichaResource}): un {@code
     * AreaM2} lleva la unidad en la cabecera de su columna y una medida la lleva dentro, porque ahi
     * la unidad <b>es</b> parte del dato. Aqui importa doble: el barrido se determina sobre metros
     * LINEALES y el recojo sobre metros CUADRADOS, y leer unos por otros no falla —cobra otra
     * cosa—.
     */
    public record FrenteDelPredio(
            long frenteId,
            long viaId,
            String viaCodigo,
            String viaNombre,
            String longitud,
            String longitudEstado,
            boolean esPrincipal,
            @Nullable String numeracion,
            @Nullable String retiro) {}

    /**
     * El cuerpo de {@code HALLAZGO_FIRME}: lo que una persona verifico (#7, ADR-0035).
     *
     * <p>Lleva {@code inspector} y {@code verificadoEn} porque son la respuesta a la unica pregunta
     * que un contraste de areas no puede contestar —«¿quien dijo esto?»— y porque quien reciba el
     * hecho va a notificar a alguien con el.
     *
     * <p><b>Ni un importe.</b> Un hallazgo informa una diferencia de superficie; cuanto se cobra
     * por ella es de {@code rentas}.
     */
    public record HallazgoFirme(
            long hallazgoId,
            long candidatoId,
            String clase,
            @Nullable Long predioId,
            @Nullable Long fichaId,
            @Nullable AreaM2 areaDeLaFicha,
            AreaM2 areaVerificada,
            String inspector,
            String verificadoEn) {}
}
