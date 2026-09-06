package kamayuk.catastro.fiscalizacion.infraestructura.web;

import java.util.List;

/**
 * Los hallazgos de un predio, con el predio por el que se pregunto (#17, AC-1 y AC-3).
 *
 * <h2>Un sobre y no una lista suelta</h2>
 *
 * <p>Porque la respuesta interesante de esta ruta es la <b>vacia</b>: un predio sin hallazgos
 * contesta {@code 200} con {@code hallazgos: []} (AC-3), y un array desnudo {@code []} no dice de
 * que predio es. Devolviendo el {@code predioId} dentro, quien lee puede comprobar que le
 * contestaron por el que pregunto — que es la disciplina que C-1 dejo escrita para las
 * caracteristicas y para el ITSE, y la unica forma de distinguir «este predio no tiene ninguno» de
 * «me contestaron por otro».
 *
 * <p><b>La lista vacia no es un 404.</b> El predio existe y no tiene hallazgos, que no es lo mismo
 * que no existir: el {@code 404} queda reservado para el predio que no esta en el padron de esta
 * municipalidad, y las dos se arreglan de maneras distintas —una no se arregla, la otra revisando
 * el identificador—.
 *
 * <p><b>Sin paginar, y hay que decir por que</b>: los hallazgos de UN predio son unidades, no miles
 * —un candidato produce como mucho un hallazgo ({@code hallazgo_candidato_uq}) y un predio entra
 * como candidato una vez por campania—. Paginar aqui obligaria a quien lee a recorrer paginas para
 * contestar «¿tiene alguno?», que es la pregunta entera.
 */
public record HallazgosDelPredioResource(long predioId, List<HallazgoDelPredioResource> hallazgos) {

    public static HallazgosDelPredioResource de(
            long predioId,
            List<kamayuk.catastro.fiscalizacion.dominio.HallazgoDelPredio> hallazgos) {
        return new HallazgosDelPredioResource(
                predioId, hallazgos.stream().map(HallazgoDelPredioResource::de).toList());
    }
}
