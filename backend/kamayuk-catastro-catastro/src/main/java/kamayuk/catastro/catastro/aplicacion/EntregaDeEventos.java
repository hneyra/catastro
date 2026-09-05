package kamayuk.catastro.catastro.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kamayuk.catastro.catastro.dominio.BuzonDeSalida;
import kamayuk.catastro.catastro.dominio.EventoDeCatastro;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sirve el buzon de salida y recoge su acuse (C-8).
 *
 * <h2>Existe para que el controlador no sostenga un repositorio</h2>
 *
 * <p>Y no es una formalidad: ningun {@code *RepositoryJdbc} de este sistema anota
 * {@code @Transactional} —no tiene por que, la transaccion es del caso de uso—, asi que un
 * controlador que llamara al repositorio correria sin {@code SET LOCAL} y la politica RLS <b>no
 * devolveria vacio: reventaria</b> con «invalid input syntax for type bigint: ""». Es el defecto de
 * clase que #486 censo en veinticuatro rutas de seis modulos.
 */
@Service
public class EntregaDeEventos {

    private final BuzonDeSalida buzon;

    public EntregaDeEventos(BuzonDeSalida buzon) {
        this.buzon = buzon;
    }

    /** Lo pendiente, en el orden en que se emitio. */
    @Transactional(readOnly = true)
    public List<EventoDeCatastro> pendientes(int limite) {
        return buzon.pendientes(limite);
    }

    /**
     * Marca entregado lo que el consumidor acuso.
     *
     * @return cuantos se marcaron, que puede ser menos de los acusados
     */
    @Transactional
    public int marcarEntregados(List<UUID> eventoIds, Instant cuando) {
        return buzon.marcarEntregados(eventoIds, cuando);
    }

    /** Cuantos quedan sin entregar. Es el «retraso del buzon» de este lado. */
    @Transactional(readOnly = true)
    public long pendientesQueQuedan() {
        return buzon.pendientesQueQuedan();
    }
}
