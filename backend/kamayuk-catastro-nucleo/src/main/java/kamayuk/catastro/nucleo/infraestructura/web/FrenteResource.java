package kamayuk.catastro.nucleo.infraestructura.web;

import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import org.jspecify.annotations.Nullable;

/**
 * Un frente del predio, tal como sale por HTTP (#7, AC 3).
 *
 * <h2>{@code longitudEstado} no es un adorno: es la mitad del dato</h2>
 *
 * <p>{@code PROPUESTA} la corto una maquina contra el eje de la via y {@code CONFIRMADA} la firmo
 * una persona (ADR-0021). De esta cifra cuelga un arbitrio —que determina {@code rentas}, no este
 * sistema (ADR-0024)—, y sin este campo las dos llegan iguales: quien determine sobre metros que
 * nadie confirmo tiene que poder saberlo.
 *
 * <p>Por eso viajan tambien {@code confirmadoPor} y {@code confirmadoEn}: confirmar es un ACTO, y
 * un acto tiene autor y hora.
 *
 * <h2>La longitud sale con su unidad dentro</h2>
 *
 * <p>{@code "18.50 ML"}, como {@code frontis} en {@code FichaResource} y por lo mismo (#607): un
 * {@code AreaM2} lleva la unidad en la cabecera de su columna y una {@code Medida} la lleva dentro,
 * porque ahi la unidad <b>es</b> parte del dato. El barrido se determina sobre metros LINEALES y el
 * recojo sobre metros CUADRADOS, y leer unos por otros no falla: cobra otra cosa.
 *
 * <h2>Ni un importe</h2>
 *
 * <p>Ni tarifa, ni factor de barrido, ni el nombre de un servicio. Aqui se publica cuanto mide el
 * frontis y a que calle da; cuanto se cobra por el es de {@code rentas}.
 *
 * @param geometria el tramo en WKT, para poder dibujarlo. Sale y no entra: la geometria entra por
 *     la carga cartografica ({@code TODA_GEOMETRIA_ENTRA_POR_BATCH}, ADR-0021)
 */
public record FrenteResource(
        long id,
        long viaId,
        String viaCodigo,
        String viaNombre,
        String longitud,
        String longitudEstado,
        boolean esPrincipal,
        @Nullable String numeracion,
        @Nullable String retiro,
        @Nullable String confirmadoPor,
        @Nullable String confirmadoEn,
        @Nullable String geometria) {

    /** El frente del dominio, tal como sale por HTTP. */
    public static FrenteResource de(FrenteDelPredio frente) {
        return new FrenteResource(
                frente.id() == null ? 0L : frente.id(),
                frente.viaId(),
                frente.viaCodigo(),
                frente.viaNombre(),
                frente.longitud().toString(),
                frente.estado().name(),
                frente.esPrincipal(),
                frente.numeracion(),
                frente.retiro() == null ? null : frente.retiro().toString(),
                frente.confirmadoPor(),
                frente.confirmadoEn() == null ? null : frente.confirmadoEn().toString(),
                frente.geometriaWkt());
    }
}
