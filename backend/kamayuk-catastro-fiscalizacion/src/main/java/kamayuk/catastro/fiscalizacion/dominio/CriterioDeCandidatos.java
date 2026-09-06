package kamayuk.catastro.fiscalizacion.dominio;

import org.jspecify.annotations.Nullable;

/**
 * Que candidatos se piden.
 *
 * <p>Un {@code record} con nulos y no una cadena de {@code if} en el repositorio: lo que el usuario
 * no filtro es nulo, y «no lo filtro» no es lo mismo que «lo filtro a vacio».
 *
 * @param campaniaId la campania; siempre, porque una cola de gabinete es de una campania
 * @param estado nulo para «todos»
 * @param clase nulo para «las dos»
 */
public record CriterioDeCandidatos(
        long campaniaId, @Nullable EstadoDelCandidato estado, @Nullable ClaseDeHallazgo clase) {

    public static CriterioDeCandidatos deLaCampania(long campaniaId) {
        return new CriterioDeCandidatos(campaniaId, null, null);
    }

    public CriterioDeCandidatos con(@Nullable EstadoDelCandidato otro) {
        return new CriterioDeCandidatos(campaniaId, otro, clase);
    }

    public CriterioDeCandidatos de(@Nullable ClaseDeHallazgo otra) {
        return new CriterioDeCandidatos(campaniaId, estado, otra);
    }
}
