package kamayuk.catastro.fiscalizacion.dominio;

/**
 * Por donde va un candidato (ADR-0035).
 *
 * <p>El recorrido es {@link #DETECTADO} → {@link #ADMITIDO_EN_GABINETE} → {@link
 * #VERIFICADO_EN_CAMPO}, y desde los dos primeros se puede caer a {@link #DESCARTADO}. No hay
 * atajo: el hallazgo cuelga de un candidato y el acta cuelga de un hallazgo, asi que un acta sin
 * las dos compuertas no tiene de que colgar.
 *
 * <p><b>Este enumerado no llega hasta el acta, y es la decision.</b> Un solo estado que fuera
 * {@code DETECTADO → … → CON_ACTA} mezclaria en una tabla lo que la maquina cree con lo que una
 * persona firmo; el dia que haya que responder «¿quien dijo esto?», la respuesta tiene que ser una
 * fila con nombre y no un {@code score} (ADR-0035 §Alternativas descartadas).
 */
public enum EstadoDelCandidato {

    /** Lo escribio el detector. Sin efecto juridico ninguno. */
    DETECTADO,

    /** Paso la primera compuerta: alguien lo miro contra lo que el padron dice. */
    ADMITIDO_EN_GABINETE,

    /** Paso la segunda: alguien fue y lo vio. Es el unico estado que produce un hallazgo. */
    VERIFICADO_EN_CAMPO,

    /**
     * No prospero, y la fila se queda con su etapa y su motivo (regla 4, ADR-0035 punto 5).
     *
     * <p>Es terminal: un candidato descartado no se «reabre». Si la brigada vuelve y encuentra otra
     * cosa, eso es otro candidato con otro insumo — y contarlo como el mismo borraria uno de los
     * dos descartes de la tasa, que es la cifra que este estado existe para poder medir.
     */
    DESCARTADO;

    /** Un candidato descartado no admite ninguna transicion mas. */
    public boolean esTerminal() {
        return this == DESCARTADO || this == VERIFICADO_EN_CAMPO;
    }
}
