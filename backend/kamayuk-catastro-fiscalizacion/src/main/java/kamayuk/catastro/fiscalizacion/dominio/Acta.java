package kamayuk.catastro.fiscalizacion.dominio;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * El acto que se levanta sobre un hallazgo firme.
 *
 * <p>Es <b>inmutable</b>, y por el mismo motivo que un recibo lo es: el administrado se lleva el
 * papel. Corregir el acta en la base deja al papel y al sistema diciendo cosas distintas, y quien
 * tenga el papel gana la discusion. Un acta equivocada se corrige dejando sin efecto su hallazgo y
 * levantando otra.
 *
 * <p><b>Ni un importe.</b> Un acta catastral dice que se hallo y quien lo hallo; lo que se cobre
 * —si se cobra algo— lo decide {@code rentas} sobre la resolucion que corresponda (ADR-0024). El
 * dia que este {@code record} gane un campo de dinero, lo que hay que revisar es la frontera.
 *
 * <p><b>Y no se emite automaticamente.</b> Una ortofoto detecta techos, no predios: puede ser una
 * ampliacion ya declarada, un predio conciliado con otro codigo, o un toldo. Sin las dos compuertas
 * la municipalidad emite miles de valores que se caen en reclamacion, y eso cuesta mas de lo que
 * recupera (ADR-0035 §Lo que esta decision NO hace).
 *
 * @param id nulo mientras no se haya guardado
 */
public record Acta(
        @Nullable Long id,
        String numero,
        long hallazgoId,
        LocalDate fecha,
        String inspector,
        String detalle) {

    private static final int NUMERO_MAXIMO = 20;
    private static final int INSPECTOR_MAXIMO = 60;
    private static final int DETALLE_MAXIMO = 1000;

    public Acta {
        Objects.requireNonNull(numero, "El acta necesita su numero");
        Objects.requireNonNull(fecha, "El acta necesita su fecha");
        Objects.requireNonNull(inspector, "El acta lleva NOMBRE: quien la levanto");
        Objects.requireNonNull(detalle, "El acta necesita decir que se hallo");
        numero = numero.strip();
        inspector = inspector.strip();
        detalle = detalle.strip();
        exigirLargo("numero", numero, NUMERO_MAXIMO);
        exigirLargo("inspector", inspector, INSPECTOR_MAXIMO);
        exigirLargo("detalle", detalle, DETALLE_MAXIMO);
    }

    private static void exigirLargo(String campo, String valor, int maximo) {
        if (valor.isEmpty() || valor.length() > maximo) {
            throw new IllegalArgumentException(
                    "El campo '" + campo + "' del acta va de 1 a " + maximo + " caracteres");
        }
    }

    public static Acta nueva(
            String numero, long hallazgoId, LocalDate fecha, String inspector, String detalle) {
        return new Acta(null, numero, hallazgoId, fecha, inspector, detalle);
    }

    public boolean esNueva() {
        return id == null;
    }
}
