package kamayuk.catastro.urbano.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import kamayuk.catastro.urbano.ParametroDeLaZona;
import kamayuk.catastro.urbano.ZonaVigente;
import kamayuk.catastro.urbano.ZonificacionDelPredio;
import kamayuk.catastro.urbano.dominio.EstadoDelPredio;
import kamayuk.catastro.urbano.dominio.ParametroUrbanistico;
import kamayuk.catastro.urbano.dominio.UrbanoRepository;
import kamayuk.catastro.urbano.dominio.ZonaQueRige;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A que zona cae un predio, a una fecha (#4, #22).
 *
 * <h2>Las CUATRO respuestas que no son la zona, y por que son cuatro</h2>
 *
 * <p>Un predio puede no estar, estar sin poligono, estar con poligono en suelo que ningun plan
 * vigente cubre, o —desde #22— caer en <b>mas de una</b> zona vigente. Las cuatro se parecen
 * —ninguna devuelve una zona— y significan cosas distintas que se arreglan de forma distinta: dar
 * de alta el predio, cargar el plano catastral, aprobar la zonificacion de esa area, o corregir dos
 * filas del plan que se pisan. Contestarlas con el mismo «zona: null» manda a quien atiende a
 * buscar lo que no falta, que es la misma leccion que P5B dejo escrita para {@code
 * NormativaInalcanzable} frente a {@code EjercicioSinSellar}.
 *
 * <p><b>Y la cuarta no se contestaba: se tapaba.</b> La consulta cerraba con {@code LIMIT 1} sin
 * {@code ORDER BY}, asi que con dos zonas encima del mismo suelo devolvia una —la que el plan de
 * ejecucion pusiera primero, que puede cambiar entre corridas sobre los mismos datos—. El javadoc
 * de la consulta prometia justo lo contrario: «cuando el lote cruza dos zonas lo que hay es un
 * hallazgo que se informa, no una respuesta que el sistema se inventa».
 *
 * <p>Y <b>hoy no hay ni un poligono cargado en ninguna instalacion</b>, asi que el segundo caso es
 * el que se va a recorrer siempre al principio: es el que menos puede salir como una zona vacia.
 *
 * <h2>Todo dentro de la misma transaccion</h2>
 *
 * <p>{@code @Transactional(readOnly = true)} y no tres lecturas sueltas: es aqui donde se emite el
 * {@code SET LOCAL app.municipalidad_id} que las politicas de {@code predio}, {@code zonificacion}
 * y {@code parametro_urbanistico} consultan. Resolver el predio fuera y la zona dentro seria el
 * defecto que la marcha blanca destapo en {@code GET /catastro/vias}, y ademas dejaria las tres
 * lecturas viendo instantaneas distintas: el plan podria relevarse entre la primera y la tercera.
 *
 * <p>Ningun metodo recibe el identificador de municipalidad (regla 2).
 */
@Service
public class ConsultaDeZonificacion implements ZonificacionDelPredio {

    private final UrbanoRepository urbano;

    public ConsultaDeZonificacion(UrbanoRepository urbano) {
        this.urbano = Objects.requireNonNull(urbano, "La consulta necesita su repositorio");
    }

    @Override
    @Transactional(readOnly = true)
    public ZonaVigente zonaDe(long predioId, LocalDate aLaFecha) {
        Objects.requireNonNull(aLaFecha, "No existe «la zona»: existe la zona vigente a una fecha");

        EstadoDelPredio estado = urbano.estadoDelPredio(predioId);
        if (estado == EstadoDelPredio.NO_ESTA) {
            throw new PredioInexistente(predioId);
        }
        if (estado == EstadoDelPredio.SIN_GEOMETRIA) {
            throw new PredioSinGeometria(predioId);
        }

        List<ZonaQueRige> zonas = urbano.zonasQueContienenAlPredio(predioId, aLaFecha);
        if (zonas.isEmpty()) {
            throw new SinZonaVigente(predioId, aLaFecha);
        }
        if (zonas.size() > 1) {
            // No se elige, y no es cautela: de esta respuesta cuelga si una licencia se concede o
            // se niega, y elegir sin criterio es inventar el dato que falta. Lo dice la cabecera
            // de V7 del defecto que su restriccion existe para impedir.
            throw new ZonaAmbigua(
                    predioId, aLaFecha, zonas.stream().map(ZonaQueRige::codigo).toList());
        }
        ZonaQueRige zona = zonas.getFirst();
        long zonificacionId = zona.id();

        return new ZonaVigente(
                zona.codigo(),
                zona.nombre(),
                zona.plan(),
                zona.ordenanza(),
                zona.vigenciaDesde(),
                zona.vigenciaHasta(),
                publicar(urbano.parametrosDe(zonificacionId)));
    }

    private static List<ParametroDeLaZona> publicar(List<ParametroUrbanistico> parametros) {
        return parametros.stream()
                .map(p -> new ParametroDeLaZona(p.clave(), p.valor(), p.unidad()))
                .toList();
    }
}
