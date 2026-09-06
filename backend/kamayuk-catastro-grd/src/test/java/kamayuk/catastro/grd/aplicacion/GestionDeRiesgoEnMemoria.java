package kamayuk.catastro.grd.aplicacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.grd.dominio.CertificadoItse;
import kamayuk.catastro.grd.dominio.FajaMarginal;
import kamayuk.catastro.grd.dominio.GestionDeRiesgoRepository;
import kamayuk.catastro.grd.dominio.ZonaDeRiesgo;

/**
 * Un repositorio en memoria para las pruebas del analizador de la carta, que no necesitan base de
 * datos: lo que se verifica es que el importador lee el archivo y rechaza por fila, no como
 * persiste PostgreSQL —eso lo prueba {@code GestionDeRiesgoFronteraTest} contra el motor real—.
 *
 * <p>Lo que no usan estas pruebas lanza {@link UnsupportedOperationException} en vez de devolver
 * vacio: una prueba que pase porque un doble respondio «nada» a algo que nadie penso no verifica lo
 * que dice verificar.
 */
final class GestionDeRiesgoEnMemoria implements GestionDeRiesgoRepository {

    private final List<ZonaDeRiesgo> zonas = new ArrayList<>();
    private final List<FajaMarginal> fajas = new ArrayList<>();
    private final List<String> geometrias = new ArrayList<>();

    private long siguienteId = 1;

    List<ZonaDeRiesgo> zonas() {
        return List.copyOf(zonas);
    }

    List<FajaMarginal> fajas() {
        return List.copyOf(fajas);
    }

    /** Los WKT tal y como llegaron, para poder afirmar que el poligono no se toca por el camino. */
    List<String> geometrias() {
        return List.copyOf(geometrias);
    }

    @Override
    public ZonaDeRiesgo guardar(ZonaDeRiesgo zona, String geometriaWkt) {
        geometrias.add(geometriaWkt);
        ZonaDeRiesgo conId =
                new ZonaDeRiesgo(
                        siguienteId++,
                        zona.codigo(),
                        zona.fenomeno(),
                        zona.nivel(),
                        zona.mitigable(),
                        zona.fuente(),
                        zona.documentoOrigen(),
                        zona.vigenciaDesde(),
                        zona.vigenciaHasta(),
                        zona.observacion());
        zonas.add(conId);
        return conId;
    }

    @Override
    public FajaMarginal guardar(FajaMarginal faja, String geometriaWkt) {
        geometrias.add(geometriaWkt);
        FajaMarginal conId =
                new FajaMarginal(
                        siguienteId++,
                        faja.codigo(),
                        faja.cuerpoDeAgua(),
                        faja.ancho(),
                        faja.fuente(),
                        faja.documentoOrigen(),
                        faja.vigenciaDesde(),
                        faja.vigenciaHasta(),
                        faja.observacion());
        fajas.add(conId);
        return conId;
    }

    @Override
    public EstadoDelLote estadoDelLote(long predioId) {
        throw new UnsupportedOperationException("esta prueba no mira ningun lote");
    }

    @Override
    public List<ZonaDeRiesgo> zonasQueCruzanElLote(long predioId, LocalDate aLaFecha) {
        throw new UnsupportedOperationException("el cruce espacial se prueba contra PostGIS");
    }

    @Override
    public List<FajaMarginal> fajasQueCruzanElLote(long predioId, LocalDate aLaFecha) {
        throw new UnsupportedOperationException("el cruce espacial se prueba contra PostGIS");
    }

    @Override
    public List<CertificadoItse> itseVigenteA(long predioId, LocalDate aLaFecha) {
        throw new UnsupportedOperationException("el filtro de vigencia lo hace el WHERE");
    }

    @Override
    public List<CertificadoItse> itseDelPredio(long predioId) {
        throw new UnsupportedOperationException("esta prueba no lee certificados");
    }

    @Override
    public CertificadoItse guardar(CertificadoItse certificado) {
        throw new UnsupportedOperationException("esta prueba no registra certificados");
    }
}
