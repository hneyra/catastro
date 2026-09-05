package kamayuk.catastro.catastro.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Decide la valuacion de un predio. <b>Funcion pura</b> (regla 6): sin base, sin reloj, sin
 * configuracion, y la fecha entra como argumento.
 *
 * <h2>Hoy NO VALORIZA NINGUNO, y eso no es una limitacion de esta clase: es el estado del sistema
 * </h2>
 *
 * <p>Y conviene decirlo con todas las letras, porque una clase que se llama «valorizacion» y
 * devuelve siempre un motivo invita a que alguien la «arregle» poniendo una cuenta:
 *
 * <ul>
 *   <li><b>El cuadro de valores unitarios no se puede cargar</b> (GOB-03, H-14). Sin el no hay
 *       valor de construccion.
 *   <li><b>Los aranceles son de ordenanza local y su ratificacion provincial</b> (D-02b). Sin ellos
 *       no hay valor de terreno.
 *   <li><b>El {@code % actualizacion} SIGUE SIN FUENTE IDENTIFICADA</b> (D-11, NEG-05 §0.1). Es el
 *       ultimo de los cuatro factores que quedaba abierto y es el que decide esta clase: multiplica
 *       —o mas exactamente incrementa, que #437 midio contra la captura del manual: su valor neutro
 *       es CERO y no uno— el autovaluo de <b>todo el padron</b>, y un valor inventado escala el
 *       error por cada predio. Por eso las reglas que lo llevan no se implementan <b>ni
 *       estructuralmente</b>, y esta clase no las implementa.
 * </ul>
 *
 * <p>Asi que lo que hace es lo unico honesto que se puede hacer: <b>decir cual es el primer insumo
 * que falta</b>, en un orden declarado, para que quien opera sepa que publicar y no reciba un cero.
 *
 * <p>El dia que los cuatro se cierren, lo que cambia aqui es que las ramas dejen de dispararse y
 * aparezca la cuenta —<i>valor unitario → +5 % → − depreciacion → × area</i>, NEG-05—; lo que no
 * cambia es la firma, ni que la fecha entre como argumento, ni que el resultado siga trayendo la
 * identidad de sus insumos.
 */
public final class ValorizacionDelPredio {

    /**
     * La llave del factor que hoy para a todos los predios, cargado o no lo demas.
     *
     * <p>No es un valor tributario —es el <b>nombre</b> de uno que falta— y por eso no lo caza el
     * escaner de la regla 5, que vigila literales numericos. Nombrarlo es lo contrario de
     * inventarlo.
     */
    public static final String PORCENTAJE_DE_ACTUALIZACION = "PORCENTAJE_DE_ACTUALIZACION";

    /**
     * La version del procedimiento de valuacion de este repositorio, tal como viaja en cada hecho
     * sellado y en el cierre de la corrida ({@code reglas_version}, {@code varchar(40)}).
     *
     * <p>No es «la version del catalogo de reglas tributarias»: aqui no corre ninguna, y decir que
     * si seria mentir sobre lo que produjo la cifra. Es la version de <b>esto</b> —de la funcion
     * que decide—, y lo que compra es que {@code rentas} pueda distinguir dos corridas hechas con
     * procedimientos distintos sin tener que mirar sus cifras: hoy todas dicen «no se pudo», y el
     * dia que unas digan una cosa y otras otra, esta cadena es lo unico que las separa.
     *
     * <p><b>Cambia el dia que la cuenta exista</b>, y ese dia las valuaciones ya publicadas no se
     * reescriben: se publica otra corrida (ADR-0027 §1).
     */
    public static final String VERSION = "sin-valorizacion-v1";

    private ValorizacionDelPredio() {}

    /**
     * Lo que se sabe de un predio en el momento de valorizarlo.
     *
     * @param fichaCatastralId la ficha VIGENTE A LA FECHA DE CORTE, o nulo si no tiene ninguna
     * @param hayCuadroDeValoresUnitarios si el conjunto sellado trae el cuadro (GOB-03 H-14)
     * @param hayCuadroDeDepreciacion si trae la depreciacion (H-15, cargable desde #188)
     * @param hayArancelDeLaVia si la via del predio tiene arancel publicado (D-02b)
     */
    public record Insumos(
            long predioId,
            int ejercicio,
            LocalDate fechaDeCorte,
            @Nullable Long fichaCatastralId,
            long conjuntoId,
            String reglasVersion,
            boolean hayCuadroDeValoresUnitarios,
            boolean hayCuadroDeDepreciacion,
            boolean hayArancelDeLaVia,
            List<CuotaDeTitular> titulares) {

        public Insumos {
            Objects.requireNonNull(fechaDeCorte, "La fecha entra como argumento (regla 6)");
            Objects.requireNonNull(reglasVersion, "La corrida dice que catalogo de reglas usa");
            titulares = List.copyOf(Objects.requireNonNull(titulares, "La lista, o vacia"));
        }
    }

    /** La valuacion que corresponde a esos insumos. */
    public static ValuacionDelPredio valorizar(Insumos insumos) {
        Objects.requireNonNull(insumos, "No se valoriza sin insumos");
        String motivo;
        String llave;
        if (insumos.fichaCatastralId() == null) {
            // No es «falta publicar»: es que este predio no tiene con que valorizarse. Se
            // distingue de las otras tres a proposito, porque se arregla fichando el predio y
            // no publicando una cifra — y mandar a quien opera a buscar una ordenanza que no
            // le falta es peor que no decirle nada.
            motivo =
                    "El predio no tiene ficha catastral vigente al "
                            + insumos.fechaDeCorte()
                            + ": no hay area, ni uso, ni construcciones con que valorizarlo";
            llave = null;
        } else if (!insumos.hayCuadroDeValoresUnitarios()) {
            motivo =
                    "El conjunto sellado del ejercicio no trae el cuadro de valores unitarios de"
                            + " edificacion (GOB-03 H-14): sin el no hay valor de construccion";
            llave = "VALOR_UNITARIO:" + insumos.ejercicio();
        } else if (!insumos.hayCuadroDeDepreciacion()) {
            motivo =
                    "El conjunto sellado del ejercicio no trae el cuadro de depreciacion"
                            + " (GOB-03 H-15): sin el, la construccion se valorizaria sin depreciar";
            llave = "DEPRECIACION:" + insumos.ejercicio();
        } else if (!insumos.hayArancelDeLaVia()) {
            motivo =
                    "La via del predio no tiene arancel publicado para el ejercicio (D-02b, de"
                            + " ordenanza local con su ratificacion provincial): sin el no hay"
                            + " valor de terreno";
            llave = "ARANCEL:" + insumos.ejercicio();
        } else {
            // LA RAMA QUE SIEMPRE SE ALCANZA SI LAS OTRAS TRES NO. Ver la cabecera: D-11.
            motivo =
                    "El «% actualizacion» sigue sin fuente identificada (D-11, NEG-05 §0.1). Es un"
                            + " incremento sobre el autovaluo de TODO el padron —su valor neutro es"
                            + " cero, no uno (#437)— y no se inventa: mientras no se publique, este"
                            + " sistema no valoriza ningun predio";
            llave = PORCENTAJE_DE_ACTUALIZACION;
        }
        return new ValuacionDelPredio(
                insumos.predioId(),
                insumos.ejercicio(),
                insumos.fechaDeCorte(),
                null,
                null,
                null,
                null,
                motivo,
                llave,
                insumos.fichaCatastralId(),
                insumos.conjuntoId(),
                insumos.reglasVersion(),
                // Ninguna. Y se dice vacio en vez de omitirlo: «no corrio ninguna regla» y «no se
                // anoto cual corrio» son cosas distintas, y la segunda no se puede auditar.
                "",
                insumos.titulares());
    }
}
