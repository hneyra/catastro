package kamayuk.catastro.fiscalizacion.infraestructura.web;

import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import org.jspecify.annotations.Nullable;

/**
 * Un candidato, como sale por HTTP.
 *
 * <h2>Lo que NO sale, y por que</h2>
 *
 * <p><b>La geometria no sale.</b> Un candidato se revisa en la cola de gabinete, que es una grilla:
 * mandar el poligono de cada fila multiplicaria por veinte el tamano de la respuesta para pintar
 * algo que la grilla no dibuja. El visor del plano es otra cosa y tiene su propia ruta (ADR-0022).
 *
 * <p><b>Los insumos SI salen</b>, enteros y como texto. Son lo que permite decidir en gabinete sin
 * abrir la ortofoto, y son tambien lo unico que hace explicable un descarte tres meses despues.
 *
 * <p>Y el descarte sale con su <b>etapa</b>: sin ella, «descartado» no dice cual de las dos
 * compuertas lo paro, que es justo lo que ADR-0035 punto 5 manda poder contar.
 */
public record CandidatoResource(
        long id,
        long campaniaId,
        @Nullable Long predioId,
        String clase,
        String origen,
        String score,
        String insumos,
        String estado,
        @Nullable String etapaDeDescarte,
        @Nullable String motivoDeDescarte,
        @Nullable String descartadoPor) {

    public static CandidatoResource de(Candidato candidato) {
        Candidato.Descarte descarte = candidato.descarte();
        return new CandidatoResource(
                candidato.id() == null ? 0 : candidato.id(),
                candidato.campaniaId(),
                candidato.predioId(),
                candidato.clase().name(),
                candidato.origen().name(),
                candidato.score().toString(),
                candidato.insumos(),
                candidato.estado().name(),
                descarte == null ? null : descarte.etapa().name(),
                descarte == null ? null : descarte.motivo(),
                descarte == null ? null : descarte.quien());
    }
}
