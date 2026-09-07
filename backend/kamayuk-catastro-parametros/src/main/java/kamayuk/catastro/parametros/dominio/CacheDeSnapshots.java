package kamayuk.catastro.parametros.dominio;

import java.util.List;
import java.util.Optional;
import kamayuk.catastro.dominio.Ejercicio;

/**
 * La cache local de conjuntos sellados (ADR-0025 §1): lo que hace que este sistema calcule con
 * {@code normativa} apagada.
 *
 * <p><b>No hay ningun metodo que borre ni que actualice</b>, y no es una omision: lo que se guarda
 * aqui es un conjunto ya sellado, que por construccion no cambia —el disparador de {@code V9} de
 * {@code normativa} lo vuelve inmutable a el y a su contenido—. Una cache de contenido inmutable no
 * tiene invalidacion que disenar. {@code kamayuk_app} tampoco tiene el privilegio: {@code V3} le
 * concede {@code INSERT} y {@code SELECT} y nada mas.
 */
public interface CacheDeSnapshots {

    /** Si ese conjunto y ese ambito ya estan descargados. */
    boolean tiene(long conjuntoId, String ambito);

    /**
     * El conjunto cacheado de mayor version del ejercicio, si hay alguno.
     *
     * <p>Es el repliegue de {@code conjuntoVigenteEn} cuando {@code normativa} no contesta. No es
     * lo mismo que preguntarselo a {@code normativa} —puede haberse sellado una version mas nueva
     * que aqui no esta— y por eso quien lo usa lo <b>dice</b> en el registro en vez de callarlo.
     */
    Optional<Long> conjuntoCacheadoDe(Ejercicio ejercicio);

    /** El ejercicio y la version de un conjunto cacheado. */
    Optional<IdentidadDelConjunto> identidadDe(long conjuntoId);

    /**
     * Los ejercicios con conjunto en la copia local, del <b>mas reciente al mas antiguo</b> (#51).
     *
     * <p>Contesta la pregunta que faltaba. Lo que habia era «¿esta sellado el 2026?» —una lectura
     * por ejercicio, que obliga a saber cual preguntar—, asi que toda interfaz que ofreciera elegir
     * ejercicio tenia que escribir la lista a mano; y eso ya paso, {@code catastro-web} la llevo
     * congelada en cuatro literales hasta #48.
     *
     * <p><b>Describe lo que este sistema TIENE, no lo que {@code normativa} publica</b>, y la
     * diferencia importa: esta lectura no llama por red, asi que contesta igual con {@code
     * normativa} caido — que es lo mismo que hace posible calcular con el apagado (ADR-0025 §1). Lo
     * que cuesta es que un conjunto sellado alli y no descargado aqui todavia no aparece; es la
     * misma asimetria que {@code conjuntoCacheadoDe} declara, y por eso las dos se resuelven con el
     * mismo desempate.
     *
     * <p><b>Un elemento por ejercicio</b>, y no uno por fila. La tabla tiene una fila por
     * <b>ambito</b> —el snapshot se pide por mitades y cada mitad trae su huella— y puede tener
     * varias versiones del mismo ejercicio, porque {@code normativa} no impide sellar mas de un
     * conjunto por ejercicio: lo que hay es {@code conjunto_uq (municipalidad_id, ejercicio,
     * version)} y una lectura que toma la ultima version sellada. Devolver las filas en bruto
     * dejaria un desplegable con «2026» tres veces.
     *
     * <p><b>El orden es descendente y no es indiferente.</b> Quien consume esto pinta un
     * desplegable de ejercicios, y ahi el ultimo va primero: invertirlo no rompe nada visible —la
     * lista sigue teniendo los mismos anios— y deja el año en curso al final, donde nadie lo busca.
     * Por eso lo dice este javadoc y lo mide una prueba.
     */
    List<ConjuntoCacheado> conjuntosCacheados();

    /** Los parametros del conjunto, con su vigencia sin resolver. */
    List<SnapshotDeNormativa.Parametro> parametrosDe(long conjuntoId);

    /**
     * Guarda el snapshot entero, en una sola transaccion.
     *
     * <p>Toma un candado de transaccion sobre {@code (municipalidad, conjunto)} antes de escribir:
     * los parametros van en los <b>dos</b> ambitos y se escriben con el primero que llegue, asi que
     * dos descargas simultaneas —una de cada ambito— los meterian dos veces. El candado es de
     * transaccion y no de sesion, porque una sesion que se lo lleva al pool contamina la peticion
     * de otra municipalidad, que es la regla 3 aplicada a los candados.
     */
    void guardar(SnapshotDeNormativa snapshot);

    /**
     * Un ejercicio de la copia local: el conjunto que lo rige aqui y las mitades descargadas.
     *
     * @param ejercicio el ejercicio, que es lo que una interfaz ofrece elegir
     * @param conjuntoId el conjunto que este sistema usaria para ese ejercicio — el mismo que
     *     devuelve {@code conjuntoCacheadoDe}, y hay una prueba que lo exige: si divergieran, la
     *     lista ofreceria un ejercicio y el calculo tomaria otro conjunto sin que nada lo dijera
     * @param version la version sellada de ese conjunto
     * @param ambitos las mitades del snapshot que estan descargadas, ordenadas. <b>No es
     *     decorado</b>: los parametros van en los dos ambitos (ADR-0024) pero los dos cuadros de la
     *     valuacion solo viajan en {@code VALUACION}, asi que un ejercicio con solo {@code
     *     OBLIGACION} descargado contesta a «¿esta sellado?» que si, y a «damelos cuadros» que no
     *     hay. Publicarlo es lo que impide que esta lista prometa de mas
     */
    record ConjuntoCacheado(
            Ejercicio ejercicio, long conjuntoId, int version, List<String> ambitos) {}

    /** Ejercicio y version de un conjunto ya descargado. */
    record IdentidadDelConjunto(Ejercicio ejercicio, int version) {}
}
