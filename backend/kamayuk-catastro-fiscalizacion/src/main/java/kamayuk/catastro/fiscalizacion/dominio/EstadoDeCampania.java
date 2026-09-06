package kamayuk.catastro.fiscalizacion.dominio;

/** Si la campania sigue admitiendo candidatos. */
public enum EstadoDeCampania {

    /** Admite candidatos nuevos. */
    ABIERTA,

    /** Ya no los admite: sus cifras estan cerradas y su tasa de descarte se puede citar. */
    CERRADA
}
