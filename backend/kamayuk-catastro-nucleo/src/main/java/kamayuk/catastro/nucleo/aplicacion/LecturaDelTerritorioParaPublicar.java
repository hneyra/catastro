package kamayuk.catastro.nucleo.aplicacion;

import java.util.List;
import kamayuk.catastro.nucleo.dominio.TerritorioParaPublicar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lee de una vez el territorio que hay que publicar (#7).
 *
 * <p>Mismo reparto que {@link LecturaDelPadronParaPublicar}: una transaccion de LECTURA, aparte de
 * la de cada escritura. Las tres consultas van juntas para que describan <b>el mismo instante</b>;
 * leidas por separado, entre la de manzanas y la de frentes cabe una inscripcion y lo publicado
 * seria una mezcla de dos momentos.
 *
 * <p><b>Sin transaccion no hay {@code SET LOCAL} y RLS no devuelve vacio: revienta</b> (#486). Por
 * eso la anotacion esta aqui y no es decorativa.
 */
@Service
public class LecturaDelTerritorioParaPublicar {

    private final TerritorioParaPublicar territorio;

    public LecturaDelTerritorioParaPublicar(TerritorioParaPublicar territorio) {
        this.territorio = territorio;
    }

    /** El territorio entero, en un solo instante. */
    @Transactional(readOnly = true)
    public Territorio leer() {
        return new Territorio(
                territorio.manzanas(), territorio.frentesPorPredio(), territorio.hallazgosFirmes());
    }

    /** Lo que hay que publicar, ya leido. */
    public record Territorio(
            List<TerritorioParaPublicar.ManzanaDelTerritorio> manzanas,
            List<TerritorioParaPublicar.FrentesDeUnPredio> frentesPorPredio,
            List<TerritorioParaPublicar.HallazgoFirme> hallazgosFirmes) {}
}
