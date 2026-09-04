package kamayuk.catastro.catastro.aplicacion;

import java.time.LocalDate;
import java.util.Optional;
import kamayuk.catastro.catastro.LectorDeFichas;
import kamayuk.catastro.catastro.dominio.FichaCatastralRepository;
import kamayuk.catastro.catastro.dominio.TipoFicha;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementa {@link LectorDeFichas} sobre {@link FichaCatastralRepository} (#28). */
@Service
public class LectorDeFichasCatastro implements LectorDeFichas {

    private final FichaCatastralRepository repositorio;

    public LectorDeFichasCatastro(FichaCatastralRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> fichaVigenteEn(long predioId, LocalDate fecha) {
        return repositorio.vigenteA(predioId, TipoFicha.UNICA, fecha).map(ficha -> ficha.id());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<kamayuk.catastro.dominio.AreaM2> areaDeLaVersion(long fichaId) {
        return repositorio.porId(fichaId).map(ficha -> ficha.areaTerreno());
    }
}
