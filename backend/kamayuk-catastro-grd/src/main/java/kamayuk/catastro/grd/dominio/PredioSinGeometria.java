package kamayuk.catastro.grd.dominio;

/**
 * El predio existe y <b>no tiene poligono</b>, asi que no se puede decir en que zona de riesgo cae
 * (#5, mismo motivo que en #4).
 *
 * <p>No es un caso de borde raro: hoy <b>no hay ni un poligono cargado</b> en ninguna instalacion
 * —{@code V61} trajo la columna y nada la llena todavia—, asi que este es el camino que la lectura
 * recorre casi siempre.
 *
 * <p>Y por eso es una excepcion y no una lista vacia. «Cero zonas de riesgo» sobre un predio sin
 * geometria es una respuesta <b>falsa</b>, indistinguible de «este lote no cae en ninguna zona», y
 * acaba autorizando lo que no debe. Se contesta con un {@code 422} que lo nombra.
 */
public final class PredioSinGeometria extends RuntimeException {

    @java.io.Serial private static final long serialVersionUID = 1L;

    private final long predioId;

    public PredioSinGeometria(long predioId) {
        super(mensajeDe(predioId));
        this.predioId = predioId;
    }

    public long predioId() {
        return predioId;
    }

    /**
     * El mismo texto que {@code getMessage()}, pero declarado no nulo.
     *
     * <p>Existe por el verificador: {@code Throwable.getMessage()} es {@code @Nullable} y el borde
     * HTTP lo pasa a un {@code ProblemaDeNegocio}, que exige un mensaje. Un {@code mensaje == null
     * ? "algo" : mensaje} en el controlador seria una rama que no puede ocurrir y que nadie puede
     * probar; aqui la construccion y la lectura son la misma cadena.
     */
    public String mensaje() {
        return mensajeDe(predioId);
    }

    private static String mensajeDe(long predioId) {
        return "El predio "
                + predioId
                + " no tiene poligono levantado, asi que no se puede decir que zonas de riesgo lo"
                + " cruzan. «Ninguna» seria una respuesta falsa: se lee igual que «no cae en"
                + " ninguna»";
    }
}
