package kamayuk.catastro.grd.aplicacion;

import java.time.LocalDate;
import kamayuk.catastro.grd.dominio.GestionDeRiesgoRepository;
import kamayuk.catastro.grd.dominio.GestionDeRiesgoRepository.EstadoDelLote;
import kamayuk.catastro.grd.dominio.PredioDesconocido;
import kamayuk.catastro.grd.dominio.PredioSinGeometria;
import kamayuk.catastro.grd.dominio.RiesgoDelPredio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Que zonas de riesgo y que fajas marginales cruzan el lote de un predio (#5, AC-3).
 *
 * <h2>Sin poligono no hay respuesta, y esa es la decision</h2>
 *
 * <p>Un predio sin geometria no produce «cero zonas de riesgo»: produce {@link PredioSinGeometria},
 * que el borde traduce a {@code 422}. El motivo es el mismo que en #4 y conviene tenerlo escrito:
 * hoy <b>no hay ni un poligono cargado</b> en ninguna instalacion, asi que la lista vacia seria la
 * respuesta habitual y se leeria como «este lote no cae en ninguna zona» — que es la frase con la
 * que se autoriza lo que no se debe autorizar.
 *
 * <p><b>Las dos ausencias se distinguen</b>: el predio que no existe en esta municipalidad es
 * {@link PredioDesconocido} y se arregla revisando el identificador; el que existe sin poligono se
 * arregla cargando el plano. Contestarlas igual mandaria a quien atiende a buscar lo que no es.
 *
 * <p><b>La transaccion es de solo lectura y no sobra</b>: es donde se emite el {@code SET LOCAL}
 * que la politica RLS necesita. Sin ella la consulta falla con «unrecognized configuration
 * parameter», que es el defecto que la marcha blanca destapo en {@code GET /catastro/vias} (#486).
 *
 * <p>Ningun argumento es el identificador de municipalidad (regla 2), y la fecha entra como
 * argumento y no se lee del reloj (regla 6).
 */
@Service
public class ConsultaDeRiesgo {

    private final GestionDeRiesgoRepository repositorio;

    public ConsultaDeRiesgo(GestionDeRiesgoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public RiesgoDelPredio delPredio(long predioId, LocalDate aLaFecha) {
        EstadoDelLote lote = repositorio.estadoDelLote(predioId);
        if (!lote.existe()) {
            throw new PredioDesconocido(predioId);
        }
        if (!lote.conGeometria()) {
            throw new PredioSinGeometria(predioId);
        }
        return new RiesgoDelPredio(
                predioId,
                aLaFecha,
                repositorio.zonasQueCruzanElLote(predioId, aLaFecha),
                repositorio.fajasQueCruzanElLote(predioId, aLaFecha));
    }
}
