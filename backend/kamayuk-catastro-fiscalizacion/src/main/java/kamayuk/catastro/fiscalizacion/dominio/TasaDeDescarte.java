package kamayuk.catastro.fiscalizacion.dominio;

/**
 * Cuantos candidatos cayo cada compuerta (ADR-0035 punto 5).
 *
 * <p><b>Es el unico indicador honesto de si el umbral de deteccion sirve</b>, y por eso el descarte
 * se conserva. Muchos descartes en gabinete significa que el detector dispara sobre ruido; muchos
 * en campo, que el gabinete admite lo que la brigada no confirma. Son dos problemas distintos con
 * dos arreglos distintos, y por eso se cuentan por separado: un solo contador de «descartados» no
 * los distingue.
 *
 * <h2>Cifras, y ningun porcentaje</h2>
 *
 * <p>Y eso no es una omision: se escribio con porcentajes y se retiraron, por dos motivos que
 * apuntan al mismo sitio.
 *
 * <p>El primero es que <b>un porcentaje esconde el denominador</b>: con veinte candidatos
 * detectados un 50 % de descarte y con veinte mil el mismo 50 % no significan lo mismo, y quien
 * decide si baja el umbral necesita las dos cifras. Con los cuatro recuentos, cualquier cociente
 * que alguien quiera lo puede hacer sabiendo sobre que.
 *
 * <p>El segundo lo puso el escaner de fuentes y es mejor razon que la primera: dividir aqui obliga
 * a escribir una escala y un modo de redondeo, y <b>D-03a y D-03b siguen abiertas</b>. Un {@code
 * setScale(4, HALF_UP)} escrito de paso en un indicador es una decision de redondeo tomada por
 * quien no la tenia que tomar, y ademas la primera de muchas: la siguiente se copia de esta.
 */
public record TasaDeDescarte(
        long detectados, long descartadosEnGabinete, long descartadosEnCampo, long verificados) {

    public TasaDeDescarte {
        if (detectados < 0
                || descartadosEnGabinete < 0
                || descartadosEnCampo < 0
                || verificados < 0) {
            throw new IllegalArgumentException("Ningun recuento de candidatos puede ser negativo");
        }
    }

    /** Cuantos siguen vivos y sin verificar: la cola que le queda a alguien por mirar. */
    public long enCurso() {
        return detectados - descartadosEnGabinete - descartadosEnCampo - verificados;
    }

    /**
     * Cuantos llegaron a la segunda compuerta.
     *
     * <p>Se publica porque es el denominador de los descartes de campo, y sin el las dos cifras de
     * descarte no se pueden comparar entre si: campo solo ve lo que gabinete admitio.
     */
    public long loQuePasoGabinete() {
        return detectados - descartadosEnGabinete;
    }

    /** Cuantos cayo la compuerta que se pregunte. */
    public long descartadosEn(EtapaDeVerificacion etapa) {
        return etapa == EtapaDeVerificacion.GABINETE ? descartadosEnGabinete : descartadosEnCampo;
    }
}
