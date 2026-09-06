package kamayuk.catastro.nucleo.dominio;

/**
 * Los seis hechos que {@code catastro} publica al resto del sistema (C-8, ADR-0027, #7).
 *
 * <p>Eran tres hasta #7, que anade los del TERRITORIO: la manzana, el frente y el hallazgo firme.
 *
 * <p>Son varios y no uno porque el receptor los escribe en tablas distintas y con reglas distintas:
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
    CORRIDA_CERRADA,

    /**
     * Una manzana con su sector: la unidad territorial sobre la que se agrupa el barrido (#7).
     *
     * <p>No lleva predio ni ejercicio, y por eso `V10` tuvo que reescribir los dos {@code CHECK}
     * cruzados de {@code catastro_evento}: estaban escritos «todo lo que no es X», y con seis tipos
     * eso obligaba a una manzana a nombrar un predio que no es suyo.
     *
     * <p>Se deriva del CONTENIDO: republicar el catalogo territorial entero cuesta —de los dos
     * lados— exactamente las manzanas que cambiaron de codigo o de sector.
     */
    MANZANA_PUBLICADA,

    /**
     * Los frentes de un predio: a que vias da y cuantos metros lineales (#7).
     *
     * <p><b>Cada frente viaja con el estado de su longitud</b>, y eso es la mitad del hecho: una
     * longitud PROPUESTA la corto una maquina contra el eje de la via y una CONFIRMADA la firmo una
     * persona (ADR-0021). Quien determine un arbitrio sobre metros que nadie confirmo tiene que
     * poder saberlo; sin ese campo, las dos cifras llegan iguales.
     *
     * <p>No lleva ejercicio: el frente que se midio en 2026 sigue siendo el mismo en 2027, y
     * versionarlo por ano seria una decision tributaria (ADR-0024).
     *
     * <p>Se deriva del CONTENIDO, como la proyeccion del predio: es una proyeccion.
     */
    FRENTE_PUBLICADO,

    /**
     * Un hallazgo catastral que quedo firme (#6, #7, ADR-0035).
     *
     * <p>Es lo que una <b>persona</b> verifico, con su nombre y su fecha. Y por eso se deriva de la
     * IDENTIDAD y no del contenido, igual que {@link #VALUACION_PUBLICADA}: que el mismo hallazgo
     * vuelva con otra area verificada no es un hallazgo nuevo, es alguien reescribiendo lo que otro
     * firmo, y tiene que <b>verse</b>.
     *
     * <p>Es el unico tipo que admite predio y no-predio: un {@code SUBVALUADOR} contrasta un predio
     * concreto y un {@code OMISO_CATASTRAL} es, por definicion, lo que no tiene predio.
     *
     * <p><b>Informa; no corrige nada.</b> Corregir el area es versionar la ficha con su
     * observacion, y ese acto lo ejecuta una persona por {@code TransferenciaDeFiscalizacion}
     * (ADR-0035 punto 4). Publicar el hecho no lo ejecuta ni lo habilita.
     */
    HALLAZGO_FIRME
}
