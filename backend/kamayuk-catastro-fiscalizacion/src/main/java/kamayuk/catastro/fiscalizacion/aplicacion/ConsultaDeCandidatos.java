package kamayuk.catastro.fiscalizacion.aplicacion;

import kamayuk.catastro.compartido.Pagina;
import kamayuk.catastro.compartido.Paginacion;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.CriterioDeCandidatos;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.TasaDeDescarte;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La cola de gabinete y la tasa de descarte de una campania (AC 7 de #6).
 *
 * <p>{@code @Transactional(readOnly = true)} y no sin transaccion: sin ella no hay {@code SET
 * LOCAL} y la politica RLS rechaza la consulta con «unrecognized configuration parameter», que
 * llega al cliente como un 500 y no como una lista vacia (#486).
 *
 * <p><b>La tasa se cuenta en la base.</b> Una campania son decenas de miles de candidatos: traerlos
 * para contarlos seria leer el padron entero de la municipalidad para imprimir cuatro cifras, que
 * es exactamente el defecto que {@code EL_PANEL_NO_HABLA_CON_LA_BASE} describe por su otra cara.
 */
@Service
public class ConsultaDeCandidatos {

    private final FiscalizacionRepository repositorio;

    public ConsultaDeCandidatos(FiscalizacionRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public Pagina<Candidato> buscar(CriterioDeCandidatos criterio, Paginacion paginacion) {
        return repositorio.candidatos(criterio, paginacion);
    }

    /**
     * Cuantos cayo cada compuerta.
     *
     * <p>Es el unico indicador honesto de si el umbral de deteccion sirve, y por eso el descarte se
     * conserva con su etapa (ADR-0035 punto 5).
     */
    @Transactional(readOnly = true)
    public TasaDeDescarte tasaDeDescarte(long campaniaId) {
        if (repositorio.campaniaPorId(campaniaId).isEmpty()) {
            throw new AbrirCampania.CampaniaInexistente(campaniaId);
        }
        return repositorio.tasaDeDescarte(campaniaId);
    }
}
