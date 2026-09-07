package kamayuk.catastro.fiscalizacion.infraestructura.web;

import java.util.List;
import kamayuk.catastro.fiscalizacion.aplicacion.DetectarSubvaluadores;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;

/**
 * Lo que devuelve una corrida de deteccion: los candidatos <b>y de cuantos predios salen</b> (#25
 * AC-3).
 *
 * <p>Era una lista pelada, y por eso «0 candidatos» era indistinguible de «0 candidatos entre las
 * fichas que miro»: hasta #25 el cruce solo contrastaba fichas {@code UNICA} y las otras tres
 * clases quedaban fuera en silencio. La segunda lectura cierra una campania afirmando que el padron
 * esta bien, que es el defecto que {@link AreasDelPadron.SinCartografia} ya impedia un escalon mas
 * arriba —alli no se puede mirar nada; aqui se miraba una parte—.
 *
 * <p><b>Ni un importe</b> (ADR-0024): un candidato es una sospecha, y lo que se cobre —si se cobra—
 * lo decide `rentas`.
 */
public record DeteccionResource(List<CandidatoResource> candidatos, CoberturaResource cobertura) {

    public static DeteccionResource de(DetectarSubvaluadores.Deteccion deteccion) {
        return new DeteccionResource(
                deteccion.candidatos().stream().map(CandidatoResource::de).toList(),
                CoberturaResource.de(deteccion.cobertura()));
    }

    /**
     * El censo del universo mirado.
     *
     * @param prediosActivos los predios ACTIVOS de la municipalidad
     * @param sinGeometria cuantos no tienen poligono con el que contrastar
     * @param sinFichaVigente cuantos tienen poligono y ninguna ficha vigente con area, de las
     *     cuatro clases
     * @param contrastados cuantos se llegaron a comparar
     * @param superanElUmbral cuantos alcanzan el umbral de la campania, antes de su tope
     * @param truncadosPorElTope cuantos lo alcanzaban y no cupieron en el tope
     */
    public record CoberturaResource(
            long prediosActivos,
            long sinGeometria,
            long sinFichaVigente,
            long contrastados,
            long superanElUmbral,
            long truncadosPorElTope) {

        static CoberturaResource de(AreasDelPadron.Cobertura cobertura) {
            return new CoberturaResource(
                    cobertura.prediosActivos(),
                    cobertura.sinGeometria(),
                    cobertura.sinFichaVigente(),
                    cobertura.contrastados(),
                    cobertura.superanElUmbral(),
                    cobertura.truncadosPorElTope());
        }
    }
}
