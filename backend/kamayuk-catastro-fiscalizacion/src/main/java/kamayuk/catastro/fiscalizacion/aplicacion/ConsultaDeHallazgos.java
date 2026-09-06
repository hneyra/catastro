package kamayuk.catastro.fiscalizacion.aplicacion;

import java.util.List;
import java.util.Optional;
import kamayuk.catastro.compartido.Pagina;
import kamayuk.catastro.compartido.Paginacion;
import kamayuk.catastro.fiscalizacion.dominio.Acta;
import kamayuk.catastro.fiscalizacion.dominio.Evidencia;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que se hallo en una campania, con su evidencia y su acta.
 *
 * <h2>Esta clase es la que la regla vigila, y por eso conviene decir lo que NO tiene</h2>
 *
 * <p>No tiene un metodo {@code aplicar}. Es exactamente el camino corto que ADR-0035 punto 4
 * describe: la campania deja cuatro mil hallazgos con su delta de area calculado, alguien mira la
 * cifra y le parece obvio «aplicarlos» — y lo que produce es un padron corregido sin acto
 * administrativo detras: el contribuyente no recibe papel, no hay plazo que impugnar, y el
 * autovaluo de todo el distrito cambia sin que nadie lo haya decidido.
 *
 * <p>Que no lo tenga no depende de que nadie lo escriba: {@code NINGUN_HALLAZGO_CORRIGE_LA_FICHA}
 * mira las clases de {@code ..fiscalizacion..} que se llaman como el hallazgo —esta es una— y pone
 * el build rojo si dependen de un camino de escritura de la ficha. Se midio anadiendole aqui esa
 * dependencia.
 *
 * <p>Corregir el area es versionar la ficha con su observacion, y ese acto lo ejecuta una PERSONA
 * por el camino que ya existe.
 */
@Service
public class ConsultaDeHallazgos {

    private final FiscalizacionRepository repositorio;

    public ConsultaDeHallazgos(FiscalizacionRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public Pagina<Hallazgo> deLaCampania(long campaniaId, Paginacion paginacion) {
        return repositorio.hallazgos(campaniaId, paginacion);
    }

    @Transactional(readOnly = true)
    public Optional<Hallazgo> porId(long hallazgoId) {
        return repositorio.hallazgoPorId(hallazgoId);
    }

    /**
     * Lo que sustenta el hallazgo. Vacia no es un error: un hallazgo puede no tener foto todavia.
     */
    @Transactional(readOnly = true)
    public List<Evidencia> evidenciasDe(long hallazgoId) {
        return repositorio.evidenciasDe(hallazgoId);
    }

    /** El acta, si ya se levanto. */
    @Transactional(readOnly = true)
    public Optional<Acta> actaDe(long hallazgoId) {
        return repositorio.actaDelHallazgo(hallazgoId);
    }
}
