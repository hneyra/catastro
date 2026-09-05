package kamayuk.catastro.nucleo.aplicacion;

import java.time.LocalDate;
import java.util.Optional;
import kamayuk.catastro.nucleo.CaracteristicasDelPredio;
import kamayuk.catastro.nucleo.LectorDeCaracteristicas;
import kamayuk.catastro.nucleo.dominio.CatastroRepository;
import kamayuk.catastro.nucleo.dominio.FichaCatastralRepository;
import kamayuk.catastro.nucleo.dominio.Predio;
import kamayuk.catastro.nucleo.dominio.Sector;
import kamayuk.catastro.nucleo.dominio.TipoFicha;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementacion de {@link LectorDeCaracteristicas}. */
@Service
public class LectorDeCaracteristicasCatastro implements LectorDeCaracteristicas {

    private final CatastroRepository catastro;
    private final FichaCatastralRepository fichas;

    public LectorDeCaracteristicasCatastro(
            CatastroRepository catastro, FichaCatastralRepository fichas) {
        this.catastro = catastro;
        this.fichas = fichas;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaracteristicasDelPredio> de(long predioId, LocalDate fecha) {
        Optional<Predio> predio = catastro.predio(predioId);
        if (predio.isEmpty()) {
            return Optional.empty();
        }

        // Una sola lectura de la ficha para el uso y el area: son de la misma version, y pedirla
        // dos veces abriria la puerta a que el uso saliera de una y el area de otra.
        Optional<kamayuk.catastro.nucleo.dominio.FichaCatastral> vigente =
                fichas.vigenteA(predioId, TipoFicha.UNICA, fecha);
        String uso = vigente.map(ficha -> ficha.uso()).orElse(null);
        kamayuk.catastro.dominio.AreaM2 area =
                vigente.map(ficha -> ficha.areaTerreno()).orElse(null);

        Long sectorId = predio.get().sectorId();
        String sectorCodigo =
                sectorId == null
                        ? null
                        : catastro.sectorPorId(sectorId).map(Sector::codigo).orElse(null);

        return Optional.of(new CaracteristicasDelPredio(uso, sectorCodigo, area));
    }
}
