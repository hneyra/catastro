package kamayuk.catastro.nucleo.dominio;

/**
 * Si la longitud del frente la propuso una maquina o la confirmo una persona (#7, ADR-0021).
 *
 * <p>Es la misma decision que ADR-0021 toma sobre el area del terreno, aplicada a los metros
 * lineales: <b>de esta cifra cuelga un cobro</b> —el arbitrio de barrido se determina sobre el
 * frente, y quien lo determina es {@code rentas} (ADR-0024)—, y un metro es indistinguible de otro
 * al leerlo. Si la propuesta del derivador y la medida del tecnico se guardaran en la misma columna
 * sin decir cual es cual, dentro de dos anios nadie podria contestar de donde salio la cifra con la
 * que se cobro.
 *
 * <p>Que sean dos estados y no una columna {@code boolean derivado} es deliberado: lo que hace
 * oficial a una longitud es un ACTO —con su usuario, su hora y su observacion (regla 10)—, y un
 * booleano invita a ponerlo a cierto sin ninguna de las tres cosas.
 */
public enum EstadoDeLaLongitud {

    /**
     * La propuso el derivador cortando el lote contra el eje de la via.
     *
     * <p>Sirve para trabajar —para saber a que calle da el predio y por donde ir a medir—, y
     * <b>no</b> para cobrar. Es el estado en que nace toda fila que escribe el derivador, y el
     * valor por omision de la columna: una longitud que nadie confirmo no puede pasar por
     * confirmada por descuido de un {@code INSERT}.
     */
    PROPUESTA,

    /**
     * La confirmo una persona, que queda nombrada con su hora y su observacion.
     *
     * <p>Confirmar no es «aceptar lo que salio»: es afirmar una medida. Por eso el acto admite una
     * longitud distinta de la propuesta —lo normal, cuando alguien va con la cinta— y por eso queda
     * quien lo hizo.
     */
    CONFIRMADA
}
