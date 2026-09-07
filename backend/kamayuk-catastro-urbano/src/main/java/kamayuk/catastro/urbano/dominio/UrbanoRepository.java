package kamayuk.catastro.urbano.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import kamayuk.catastro.dominio.Observacion;

/**
 * El puerto de persistencia de {@code urbano} (ARQ-04 §1).
 *
 * <p>Sin Spring y sin JPA (regla 7): la implementacion vive en {@code
 * kamayuk.catastro.urbano.infraestructura} y esta interfaz se puede doblar en una prueba unitaria
 * sin levantar ningun contexto.
 *
 * <p><b>Ningun metodo recibe el identificador de municipalidad</b> (regla 2): lo pone el motor con
 * {@code current_setting}, del mismo parametro que consulta la politica RLS.
 */
public interface UrbanoRepository {

    /**
     * Que sabe el padron de ese predio: si esta, y si tiene poligono.
     *
     * <p>Es una consulta a {@code predio}, que es una tabla de <b>este mismo sistema</b> —regla 11
     * mira la frontera de SISTEMA, no la de modulo—, y no un puerto hacia {@code nucleo}: lo que
     * hace falta es una condicion del {@code WHERE} de la consulta de contencion, no un objeto.
     */
    EstadoDelPredio estadoDelPredio(long predioId);

    /**
     * Las zonas vigentes a esa fecha que <b>cubren</b> al predio (ADR-0034 regla 2).
     *
     * <p>Vacio significa «ningun plan vigente a esa fecha cubre ese predio», que no es lo mismo que
     * «el predio no tiene poligono»: eso lo dice {@link #estadoDelPredio}, y por eso son dos
     * preguntas y no una con un nulo dentro.
     *
     * <p><b>Devuelve una LISTA y no un {@code Optional}, desde #22</b>, y no es una comodidad: el
     * esquema puede tener dos zonas del mismo plan sobre el mismo suelo —lo impide {@code
     * zonas_del_plan_no_se_pisan} desde {@code V11}, y no impedia nada antes—, y una fila cargada
     * antes de esa migracion sigue ahi. Un {@code Optional} obliga a la consulta a elegir una, y
     * elegirla sin criterio es inventar la respuesta: quien decide que hacer con dos es el caso de
     * uso, que lanza {@code ZonificacionDelPredio.ZonaAmbigua} con los dos codigos.
     */
    List<Zona> zonasQueContienenAlPredio(long predioId, LocalDate aLaFecha);

    /** Lo que esa zona permite, en el orden en que se cargo. */
    List<ParametroUrbanistico> parametrosDe(long zonificacionId);

    /** La zona de ese plan con ese codigo y esa fecha de inicio, si ya esta cargada. */
    Optional<Zona> zonaPorCodigo(String plan, String codigo, LocalDate vigenciaDesde);

    /**
     * Guarda la zona y devuelve su identificador.
     *
     * <p>No hay {@code borrar} ni lo va a haber: un plan no se borra, se cierra con su {@code
     * vigencia_hasta} y el siguiente lo sucede (regla 4). La aplicacion tampoco tiene el
     * privilegio.
     */
    long guardar(Zona zona, Observacion observacion);

    /**
     * Guarda los parametros de una zona ya guardada, con la misma observacion que la zona.
     *
     * <p>La MISMA y no otra: cargar una zona con sus parametros es <b>un acto</b>, no siete (regla
     * 10, RNF-052). Es lo que {@code InscribirFicha} hace con el predio, la ficha y la titularidad.
     */
    void guardarParametros(
            long zonificacionId, List<ParametroUrbanistico> parametros, Observacion observacion);
}
