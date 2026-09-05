package kamayuk.catastro.urbano.aplicacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.urbano.dominio.EstadoDelPredio;
import kamayuk.catastro.urbano.dominio.ParametroUrbanistico;
import kamayuk.catastro.urbano.dominio.UrbanoRepository;
import kamayuk.catastro.urbano.dominio.Zona;

/**
 * Un {@code urbano} en memoria para lo que no necesita base de datos: que el caso de uso distinga
 * las tres respuestas que no son la zona, y que el importador rechace por fila.
 *
 * <p><b>La contencion NO se simula.</b> Este doble decide con {@code rigeEn} y con un mapa de
 * predio a zona sembrado por la prueba: no hay geometria, y no puede haberla sin traer PostGIS a un
 * doble. Que el predio caiga DENTRO del poligono —y que el marco vaya delante del {@code
 * ST_Contains}— es lo que mide {@code ZonificacionFronteraTest} contra PostgreSQL real, que es el
 * unico sitio donde esa afirmacion significa algo.
 *
 * <p>Lo que no se usa lanza {@link UnsupportedOperationException} en vez de devolver vacio: una
 * prueba que pase porque un doble respondio «nada» a algo que nadie penso no verifica lo que dice
 * verificar.
 */
final class UrbanoEnMemoria implements UrbanoRepository {

    private final Map<Long, EstadoDelPredio> predios = new LinkedHashMap<>();
    private final Map<Long, Zona> zonas = new LinkedHashMap<>();
    private final Map<Long, List<ParametroUrbanistico>> parametros = new LinkedHashMap<>();
    private final Map<Long, Long> zonaDelPredio = new LinkedHashMap<>();
    private final List<Observacion> observaciones = new ArrayList<>();

    private long siguienteId = 1;

    void sembrarPredio(long predioId, EstadoDelPredio estado) {
        predios.put(predioId, estado);
    }

    /** Ata un predio ya sembrado a una zona ya guardada: es lo que la geometria haria. */
    void caeEn(long predioId, long zonificacionId) {
        zonaDelPredio.put(predioId, zonificacionId);
    }

    List<Observacion> observaciones() {
        return List.copyOf(observaciones);
    }

    List<Zona> zonasGuardadas() {
        return List.copyOf(zonas.values());
    }

    @Override
    public EstadoDelPredio estadoDelPredio(long predioId) {
        return predios.getOrDefault(predioId, EstadoDelPredio.NO_ESTA);
    }

    @Override
    public Optional<Zona> zonaQueContieneAlPredio(long predioId, LocalDate aLaFecha) {
        Long zonaId = zonaDelPredio.get(predioId);
        if (zonaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(zonas.get(zonaId)).filter(zona -> zona.rigeEn(aLaFecha));
    }

    @Override
    public List<ParametroUrbanistico> parametrosDe(long zonificacionId) {
        return parametros.getOrDefault(zonificacionId, List.of());
    }

    @Override
    public Optional<Zona> zonaPorCodigo(String plan, String codigo, LocalDate vigenciaDesde) {
        return zonas.values().stream()
                .filter(
                        zona ->
                                zona.plan().equals(plan)
                                        && zona.codigo().equals(codigo)
                                        && zona.vigenciaDesde().equals(vigenciaDesde))
                .findFirst();
    }

    @Override
    public long guardar(Zona zona, Observacion observacion) {
        long id = siguienteId++;
        zonas.put(
                id,
                new Zona(
                        id,
                        zona.plan(),
                        zona.ordenanza(),
                        zona.codigo(),
                        zona.nombre(),
                        zona.geometriaWkt(),
                        zona.vigenciaDesde(),
                        zona.vigenciaHasta()));
        observaciones.add(observacion);
        return id;
    }

    @Override
    public void guardarParametros(
            long zonificacionId, List<ParametroUrbanistico> nuevos, Observacion observacion) {
        parametros.put(zonificacionId, List.copyOf(nuevos));
        observaciones.add(observacion);
    }
}
