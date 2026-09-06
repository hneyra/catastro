/**
 * Fiscalizacion CATASTRAL: el hallazgo, con su acto y su evidencia (ADR-0035).
 *
 * <p><b>No es la fiscalizacion tributaria</b>, que vive entera en {@code
 * kamayuk-rentas-fiscalizacion} —liquidacion, resolucion de determinacion, programa, muestra— y que
 * este modulo no duplica ni contradice. La frontera la pone ADR-0024: aqui no se liquida, no se
 * determina y no se emite un valor.
 *
 * <p><b>Invariante:</b> un hallazgo firme <b>habilita</b> el acto y no lo ejecuta. Corregir el area
 * es versionar la ficha con su observacion, y eso lo hace una persona por el camino que ya existe
 * (ADR-0021, ADR-0035 punto 4). Lo vigila {@code NINGUN_HALLAZGO_CORRIGE_LA_FICHA}.
 *
 * <p><b>Dos tablas y no un estado:</b> {@code candidato} es lo que la maquina sospecha y {@code
 * hallazgo} lo que una persona verifico. Entre las dos hay dos compuertas humanas —gabinete y
 * campo— y ninguna se puede saltar.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.fiscalizacion;
