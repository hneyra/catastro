package kamayuk.catastro.nucleo.dominio;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Las huellas del padron de predios, para que otro sistema compare su proyeccion (P6, punto 4).
 *
 * <p>Dos lecturas y una escalera: primero <b>por sector</b>, que son decenas de cifras, y solo del
 * sector que no cuadre se pide el <b>detalle</b>, que son sus lotes. Pedir siempre el detalle seria
 * leer el catastro entero cada dia; pedir solo el resumen no diria nunca cual lote difiere.
 */
public interface HuellasDelPadron {

    /**
     * Una cifra por sector, con cuantos lotes la componen.
     *
     * <p>El recuento va al lado de la huella y no sobra: cuando dos huellas difieren, saber si
     * ademas difieren los recuentos separa «una fila cambio» de «faltan filas», que se arreglan de
     * dos maneras distintas.
     *
     * @param sector el codigo del sector, o {@code null} para los predios sin sectorizar
     */
    List<HuellaDeSector> porSector();

    /** Los lotes de un sector con su huella, ordenados por identificador ascendente. */
    List<HuellaDeLote> deUnSector(@Nullable String sectorCodigo);

    /** La huella de un sector entero. */
    record HuellaDeSector(@Nullable String sector, int lotes, String huella) {}

    /** La huella de un lote. */
    record HuellaDeLote(long predioId, String huella) {}
}
