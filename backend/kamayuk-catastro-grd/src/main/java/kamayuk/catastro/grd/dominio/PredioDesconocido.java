package kamayuk.catastro.grd.dominio;

/**
 * No hay ningun predio con ese identificador <b>en esta municipalidad</b> (#5).
 *
 * <p>La coletilla no sobra: bajo RLS el predio de otra municipalidad no es «prohibido», es que no
 * existe —la politica lo esconde antes de que la consulta lo vea—, y decir otra cosa filtraria que
 * ese identificador si esta en alguna parte.
 *
 * <p>Se distingue de {@link PredioSinGeometria} porque se arreglan de maneras opuestas: este dice
 * «ese predio no es de aqui, revisa el identificador» y aquel «el predio es correcto, falta cargar
 * el plano».
 */
public final class PredioDesconocido extends RuntimeException {

    @java.io.Serial private static final long serialVersionUID = 1L;

    private final long predioId;

    public PredioDesconocido(long predioId) {
        super(mensajeDe(predioId));
        this.predioId = predioId;
    }

    /**
     * El mismo texto que {@code getMessage()}, declarado no nulo. Ver {@link PredioSinGeometria}.
     */
    public String mensaje() {
        return mensajeDe(predioId);
    }

    private static String mensajeDe(long predioId) {
        return "No hay ningun predio con el identificador " + predioId + " en esta municipalidad";
    }
}
