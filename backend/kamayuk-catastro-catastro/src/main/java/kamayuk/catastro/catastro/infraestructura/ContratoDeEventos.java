package kamayuk.catastro.catastro.infraestructura;

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
}
