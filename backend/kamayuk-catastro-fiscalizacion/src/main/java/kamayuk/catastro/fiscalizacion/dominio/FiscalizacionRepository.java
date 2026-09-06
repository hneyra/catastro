package kamayuk.catastro.fiscalizacion.dominio;

import java.util.List;
import java.util.Optional;
import kamayuk.catastro.compartido.Pagina;
import kamayuk.catastro.compartido.Paginacion;

/**
 * El puerto de persistencia del hallazgo catastral.
 *
 * <p><b>Uno y no cinco</b>, y el motivo es el de siempre en este proyecto: las cinco tablas se
 * escriben juntas dentro de la misma transaccion —verificar en campo guarda el candidato y crea el
 * hallazgo en un solo acto— y repartirlas en cinco puertos no las separaria, solo obligaria a
 * inyectar cinco cosas en cada caso de uso. Lo que si esta separado es {@link AreasDelPadron}, y
 * eso porque es lo unico que <b>no</b> es de este contexto.
 *
 * <p>Sin Spring y sin JPA (regla 7). Ningun metodo recibe {@code municipalidadId} (regla 2): sale
 * del token, se fija con {@code SET LOCAL} y lo aplica la politica RLS.
 *
 * <p><b>Ningun metodo borra.</b> No hay {@code eliminar}, ni {@code borrar}, ni {@code purgar}: el
 * descarte se conserva con su motivo, que es la mitad de ADR-0035 punto 5 que no es la regla 4 —su
 * tasa por etapa es el unico indicador honesto de si el umbral de deteccion sirve—.
 */
public interface FiscalizacionRepository {

    // ── Campania ───────────────────────────────────────────────────────

    Campania guardar(Campania campania);

    Optional<Campania> campaniaPorId(long id);

    Optional<Campania> campaniaPorCodigo(String codigo);

    // ── Candidato ──────────────────────────────────────────────────────

    Candidato guardar(Candidato candidato);

    Optional<Candidato> candidatoPorId(long id);

    /** Los candidatos de una campania, filtrados por estado si se pide alguno. */
    Pagina<Candidato> candidatos(CriterioDeCandidatos criterio, Paginacion paginacion);

    /**
     * Cuantos candidatos hay de cada estado en una campania, y cuantos descartados en cada etapa.
     *
     * <p>Se cuenta en la base y no en Java a proposito: una campania son decenas de miles de
     * candidatos, y traerlos para contarlos seria leer el padron entero de la municipalidad para
     * imprimir cinco cifras.
     */
    TasaDeDescarte tasaDeDescarte(long campaniaId);

    // ── Hallazgo ───────────────────────────────────────────────────────

    Hallazgo guardar(Hallazgo hallazgo);

    Optional<Hallazgo> hallazgoPorId(long id);

    Optional<Hallazgo> hallazgoDelCandidato(long candidatoId);

    Pagina<Hallazgo> hallazgos(long campaniaId, Paginacion paginacion);

    /**
     * Los hallazgos de UN predio, con su campania y su acta (#17, AC-1 y AC-2).
     *
     * <p>Va por {@code hallazgo.predio_id} y no recorriendo campanias: filtrar del lado de Java una
     * pagina de {@link #hallazgos} devolveria los que cupieron en ella, que sobre una campania de
     * cuatro mil candidatos es una respuesta plausible, incompleta y muda.
     *
     * <p><b>Nunca devuelve un omiso catastral</b>, y no porque lo filtre: {@code
     * hallazgo_contraste_check} de {@code V9} le exige {@code predio_id} nulo a un {@link
     * ClaseDeHallazgo#OMISO_CATASTRAL} —si lo tuviera no seria un omiso—, asi que un {@code WHERE
     * predio_id = ?} no puede alcanzarlo. Se dice porque quien lea «los hallazgos del predio» va a
     * suponer que estan todos.
     *
     * <p>Sin paginacion: un candidato produce como mucho un hallazgo ({@code
     * hallazgo_candidato_uq}) y un predio entra como candidato una vez por campania, asi que son
     * unidades. Paginar obligaria a recorrer paginas para contestar «¿tiene alguno?».
     */
    List<HallazgoDelPredio> hallazgosDelPredio(long predioId);

    // ── Evidencia y acta ───────────────────────────────────────────────

    Evidencia guardar(Evidencia evidencia);

    List<Evidencia> evidenciasDe(long hallazgoId);

    Acta guardar(Acta acta);

    Optional<Acta> actaDelHallazgo(long hallazgoId);
}
