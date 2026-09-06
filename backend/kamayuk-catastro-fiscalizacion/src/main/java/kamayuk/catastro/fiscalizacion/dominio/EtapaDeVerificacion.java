package kamayuk.catastro.fiscalizacion.dominio;

/**
 * Las dos compuertas humanas por las que pasa un candidato antes de producir nada (ADR-0035).
 *
 * <p>Existe como enumerado y no como un booleano porque lo que se cuenta es <b>en cual de las dos
 * se descarto</b>, y esa cifra es el unico indicador honesto de si el umbral de deteccion sirve:
 * muchos descartes en {@link #GABINETE} significa que el detector dispara sobre ruido; muchos en
 * {@link #CAMPO}, que el gabinete admite lo que la brigada no confirma. Son dos problemas distintos
 * con dos arreglos distintos, y un solo contador no los separa.
 */
public enum EtapaDeVerificacion {

    /** Alguien mira el insumo contra lo que el padron ya dice. */
    GABINETE,

    /** Alguien va y lo ve. */
    CAMPO
}
