package kamayuk.catastro.nucleo.aplicacion;

import java.util.List;
import kamayuk.catastro.nucleo.dominio.HuellasDelPadron;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las huellas del padron, para la anti-entropia con la proyeccion de {@code rentas} (P6, punto 4).
 *
 * <p>El {@code @Transactional} <b>no es opcional y no es una precaucion</b>: es lo que fija el
 * contexto de municipalidad con {@code SET LOCAL}, y sin el la politica RLS no puede evaluarse —la
 * consulta no devuelve vacio, revienta con «invalid input syntax for type bigint» (#486, y catorce
 * rutas mas que lo tuvieron)—. Ese defecto no lo ve ninguna prueba de repositorio, porque esas
 * abren su propia transaccion, ni ninguna de capa web, porque esas hablan con un doble.
 */
@Service
public class ConsultaDeHuellas {

    private final HuellasDelPadron huellas;

    public ConsultaDeHuellas(HuellasDelPadron huellas) {
        this.huellas = huellas;
    }

    /** Una cifra por sector. Es lo que se compara a diario. */
    @Transactional(readOnly = true)
    public List<HuellasDelPadron.HuellaDeSector> porSector() {
        return huellas.porSector();
    }

    /** El detalle de UN sector. Solo se pide del que no cuadro. */
    @Transactional(readOnly = true)
    public List<HuellasDelPadron.HuellaDeLote> deUnSector(@Nullable String sectorCodigo) {
        return huellas.deUnSector(sectorCodigo);
    }
}
