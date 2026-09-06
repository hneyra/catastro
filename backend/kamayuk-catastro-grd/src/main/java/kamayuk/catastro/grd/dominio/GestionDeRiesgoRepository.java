package kamayuk.catastro.grd.dominio;

import java.time.LocalDate;
import java.util.List;

/**
 * El puerto de persistencia del contexto (ARQ-04 §1). Sin Spring y sin JPA (regla 7).
 *
 * <p><b>Ningun metodo recibe {@code municipalidadId}</b> (regla 2): el filtrado lo hace la politica
 * RLS con el valor que {@code SET LOCAL} fijo al abrir la transaccion.
 *
 * <p><b>Y la fecha entra siempre como argumento</b> (regla 6, regla 9): las tres entidades de este
 * contexto tienen vigencia, asi que ninguna lectura puede contestar sin saber a que dia se
 * pregunta.
 */
public interface GestionDeRiesgoRepository {

    /** Si el predio existe en esta municipalidad, y si tiene poligono levantado. */
    EstadoDelLote estadoDelLote(long predioId);

    /**
     * Las zonas de riesgo vigentes que <b>intersectan</b> el lote.
     *
     * <p>Quien la implementa tiene que filtrar por marco primero y usar el operador espacial solo
     * detras, como refinado exacto y en la misma sentencia (ADR-0034 regla 2).
     */
    List<ZonaDeRiesgo> zonasQueCruzanElLote(long predioId, LocalDate aLaFecha);

    /** Las fajas marginales vigentes que intersectan el lote, con la misma forma. */
    List<FajaMarginal> fajasQueCruzanElLote(long predioId, LocalDate aLaFecha);

    /**
     * Los certificados ITSE del predio <b>vigentes a esa fecha</b>.
     *
     * <p>El filtro va en el {@code WHERE} y no en Java: traer todos y descartar en memoria es como
     * un vencido acaba saliendo el dia que alguien toca el bucle.
     */
    List<CertificadoItse> itseVigenteA(long predioId, LocalDate aLaFecha);

    /** Todos los del predio, vigentes o no, para la pantalla que los administra. */
    List<CertificadoItse> itseDelPredio(long predioId);

    /**
     * Guarda la zona con su poligono.
     *
     * <p>El WKT va <b>fuera</b> del registro de dominio y no dentro, y la asimetria es deliberada:
     * la lectura NO trae el poligono —lo que devuelve es que zonas cruzan el lote, y para eso el
     * poligono ya se uso dentro de la propia sentencia—, asi que un campo de geometria en {@link
     * ZonaDeRiesgo} estaria nulo en todas las lecturas y lleno solo en la carga. Un campo con dos
     * significados segun por donde se entre es el que alguien acaba leyendo por el lado equivocado.
     */
    ZonaDeRiesgo guardar(ZonaDeRiesgo zona, String geometriaWkt);

    FajaMarginal guardar(FajaMarginal faja, String geometriaWkt);

    CertificadoItse guardar(CertificadoItse certificado);

    /**
     * Que sabe la base del lote de un predio.
     *
     * @param existe si hay un predio con ese identificador en esta municipalidad
     * @param conGeometria si ademas tiene poligono. Son dos cosas distintas y se arreglan distinto:
     *     una revisando el identificador y la otra cargando el plano
     */
    record EstadoDelLote(boolean existe, boolean conGeometria) {}
}
