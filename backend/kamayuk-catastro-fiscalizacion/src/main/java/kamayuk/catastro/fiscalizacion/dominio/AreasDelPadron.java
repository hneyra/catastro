package kamayuk.catastro.fiscalizacion.dominio;

import java.util.List;

/**
 * El contraste entre lo que la ficha dice y lo que el poligono mide (AC 8 de #6, ADR-0021).
 *
 * <p>Es el <b>unico</b> insumo que este contexto lee del padron, y esta separado de {@link
 * FiscalizacionRepository} para que se vea: lo demas es de aqui, esto no.
 *
 * <h2>Por que es una consulta y no un puerto por predio</h2>
 *
 * <p>Porque la condicion —«el area inscrita difiere del poligono mas que la tolerancia»— <b>se
 * deriva del cruce</b> de dos tablas, asi que acotarla predio a predio en Java significaria traer
 * el padron entero de la municipalidad para descartar el 99 %. Es la misma decision, con el mismo
 * motivo, que {@code DeteccionRepositoryJdbc} en la fiscalizacion tributaria de {@code rentas}.
 *
 * <h2>Sin poligonos NO devuelve vacio: dice que no puede</h2>
 *
 * <p>Hoy no hay <b>ni un poligono cargado en ninguna instalacion</b>: {@code V61} trajo la columna
 * y nada la llena todavia. Sobre esa base, un detector que devolviera una lista vacia estaria
 * afirmando «no hay subvaluadores», que es indistinguible de «no pude mirar» y que nadie va a
 * revisar — la campania se cerraria con cero hallazgos y la conclusion seria que el padron esta
 * bien. Por eso {@link #contrastar} <b>lanza</b> {@link SinCartografia} cuando la municipalidad no
 * tiene un solo predio con geometria.
 *
 * <p>Es el criterio de #48 —«un cero se leeria como que no aplicaron nada, indistinguible de un dia
 * sin cobros»— aplicado a la deteccion.
 */
public interface AreasDelPadron {

    /**
     * Los predios cuya area inscrita difiere de la del poligono por encima de la tolerancia.
     *
     * <p>El area del poligono se calcula <b>solo para comparar</b> y no se escribe en ninguna
     * parte: derivarla cambiaria el autovaluo de todo el padron sin que nadie lo decidiera, y un
     * area es indistinguible de otra al leerla (ADR-0021).
     *
     * @param tolerancia cuanto se admite que difieran sin sospechar; ver {@link Tolerancia}
     * @param tope cuantos como mucho; una campania no se lanza sobre el padron entero de una vez
     * @throws SinCartografia si la municipalidad no tiene ni un predio con geometria
     */
    List<ContrasteDeAreas> contrastar(Tolerancia tolerancia, int tope);

    /**
     * La municipalidad no tiene un solo poligono cargado.
     *
     * <p>No es un fallo tecnico: es la respuesta correcta a «¿cuantos subvaluadores hay?» cuando no
     * se puede mirar. Quien la atiende sabe exactamente que hacer —cargar la cartografia— y no se
     * queda con una lista vacia que parece una respuesta.
     */
    final class SinCartografia extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        public SinCartografia() {
            super(
                    "Esta municipalidad no tiene ni un predio con geometria cargada, asi que el"
                            + " area inscrita no se puede contrastar contra nada. Devolver cero"
                            + " subvaluadores seria afirmar que no los hay, que es otra cosa"
                            + " (ADR-0021: la carga cartografica es un proceso batch)");
        }
    }
}
