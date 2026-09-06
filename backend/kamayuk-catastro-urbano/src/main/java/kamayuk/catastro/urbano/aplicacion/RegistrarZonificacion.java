package kamayuk.catastro.urbano.aplicacion;

import java.util.List;
import java.util.Objects;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.urbano.dominio.ParametroUrbanistico;
import kamayuk.catastro.urbano.dominio.UrbanoRepository;
import kamayuk.catastro.urbano.dominio.Zona;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Da de alta una zona del plan con sus parametros urbanisticos, en <b>un solo acto</b> (#4).
 *
 * <h2>Una transaccion, dos escrituras y una sola observacion</h2>
 *
 * <p>La zona y sus parametros van juntos porque una zona sin sus parametros no permite ni prohibe
 * nada: publicaria un codigo y ninguna regla. Las dos filas llevan la <b>misma</b> observacion —es
 * un acto, no dos (regla 10, RNF-052)— y la misma transaccion, de modo que si un parametro se
 * rechaza no queda la zona a medias.
 *
 * <h2>Repetir la carga no duplica el plan</h2>
 *
 * <p>Una zona que ya esta con el mismo plan, el mismo codigo y la misma fecha de inicio <b>no se
 * vuelve a escribir</b>: se devuelve la que hay. Volver a correr el cargador es lo corriente —se
 * corrige una fila del archivo y se repite—, y sin esto la segunda corrida chocaria contra {@code
 * zonificacion_codigo_uq} en la primera zona y no llegaria a la fila corregida.
 *
 * <p><b>Lo que no hace es RESCRIBIR la que ya esta.</b> Un plan aprobado por ordenanza no se
 * corrige pisandolo desde un CSV: se cierra con su {@code vigencia_hasta} y se aprueba el
 * siguiente, que es lo que la restriccion de exclusion de {@code V7} deja hacer y lo unico que la
 * regla 4 admite.
 *
 * <p>Ningun argumento es el identificador de municipalidad (regla 2).
 */
@Service
public class RegistrarZonificacion {

    private final UrbanoRepository urbano;

    public RegistrarZonificacion(UrbanoRepository urbano) {
        this.urbano = Objects.requireNonNull(urbano, "El caso de uso necesita su repositorio");
    }

    /**
     * @return {@code true} si la zona nacio en este acto; {@code false} si ya estaba
     */
    @Transactional
    public boolean registrar(
            Zona zona, List<ParametroUrbanistico> parametros, Observacion observacion) {
        Objects.requireNonNull(zona, "No hay zona que registrar");
        Objects.requireNonNull(parametros, "Los parametros pueden venir vacios, no nulos");
        Objects.requireNonNull(observacion, "Toda escritura exige la observacion del usuario");

        if (urbano.zonaPorCodigo(zona.plan(), zona.codigo(), zona.vigenciaDesde()).isPresent()) {
            return false;
        }
        long id = urbano.guardar(zona, observacion);
        urbano.guardarParametros(id, parametros, observacion);
        return true;
    }
}
