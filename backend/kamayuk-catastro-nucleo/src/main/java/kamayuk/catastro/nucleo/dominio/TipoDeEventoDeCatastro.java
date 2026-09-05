package kamayuk.catastro.nucleo.dominio;

/**
 * Los tres hechos que {@code catastro} publica al resto del sistema (C-8, ADR-0027).
 *
 * <p>Son tres y no uno porque el receptor los escribe en tablas distintas y con reglas distintas:
 * la proyeccion del predio se <b>reemplaza</b> cuando llega una version mas nueva, el cierre de la
 * corrida se <b>sustituye</b> entero, y la valuacion de un predio <b>no se toca nunca</b> (ADR-0027
 * §1; `V5` de `rentas` le da {@code UPDATE} al ingestor sobre la corrida y no sobre la valuacion).
 *
 * <p><b>Y por eso su identidad no se deriva igual</b>: ver {@link IdentidadDelEvento}. Un enumerado
 * con tres valores es el sitio donde esa diferencia se lee de una vez.
 */
public enum TipoDeEventoDeCatastro {

    /**
     * Un predio y las versiones de su ficha, tal como el padron los tiene.
     *
     * <p>Alimenta {@code predio_ref} y {@code ficha_ref} de `rentas` (`V4`), que son las dos tablas
     * sin las que la deteccion de omisos no cabe en un solo {@code WHERE} — lo que #631 midio que
     * no se puede componer en memoria.
     */
    PREDIO_PROYECTADO,

    /**
     * La valuacion de un predio en un ejercicio: un HECHO SELLADO.
     *
     * <p>O trae las cuatro cifras, o trae el motivo por el que no se pudo valorizar. Nunca las dos
     * cosas y nunca ninguna: un cero es indistinguible de un predio que no vale nada, que es lo que
     * #48 midio con la licencia de obra que salia con «valor de obra 0,00».
     */
    VALUACION_PUBLICADA,

    /**
     * Que la corrida de valuacion de un ejercicio se cerro, con su conteo y su huella agregada.
     *
     * <p>Es lo unico que abre el candado de ADR-0027 §2. Y su conteo y su huella <b>viajan con
     * el</b>: si `rentas` los derivara de lo que recibio, comprobaria que lo que tiene es igual a
     * lo que tiene.
     */
    CORRIDA_CERRADA
}
