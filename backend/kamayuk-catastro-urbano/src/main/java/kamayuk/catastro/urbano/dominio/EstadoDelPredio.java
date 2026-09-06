package kamayuk.catastro.urbano.dominio;

/**
 * Lo unico que este modulo necesita saber del padron para contestar por la zona (#4).
 *
 * <p>Tres estados y no dos, porque los tres se contestan distinto y confundirlos es lo que este
 * issue existe para impedir: el predio que no esta, el que esta y no tiene poligono, y el que esta
 * y lo tiene. El segundo es hoy el caso corriente —no hay ni un poligono cargado en ninguna
 * instalacion— y es el que no puede salir como «zona nula».
 */
public enum EstadoDelPredio {

    /** No hay ningun predio con ese identificador en esta municipalidad. */
    NO_ESTA,

    /** El predio esta en el padron y no tiene poligono. */
    SIN_GEOMETRIA,

    /** El predio esta y tiene poligono: se le puede preguntar por su zona. */
    CON_GEOMETRIA
}
