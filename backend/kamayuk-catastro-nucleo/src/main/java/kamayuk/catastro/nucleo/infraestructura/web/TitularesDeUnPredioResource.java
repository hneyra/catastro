package kamayuk.catastro.nucleo.infraestructura.web;

import java.util.List;
import kamayuk.catastro.nucleo.TitularDelPredio;

/** Las cuotas vigentes de un predio, agrupadas bajo el, dentro de la respuesta de varios (C-5). */
public record TitularesDeUnPredioResource(long predioId, List<CuotaDeUnTitularResource> cuotas) {

    public static TitularesDeUnPredioResource de(long predioId, List<TitularDelPredio> cuotas) {
        return new TitularesDeUnPredioResource(
                predioId, cuotas.stream().map(CuotaDeUnTitularResource::de).toList());
    }
}
