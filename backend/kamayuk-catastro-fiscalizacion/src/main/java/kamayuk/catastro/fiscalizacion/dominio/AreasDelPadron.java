package kamayuk.catastro.fiscalizacion.dominio;

/**
 * El contraste entre lo que la ficha dice y lo que el poligono mide (AC 8 de #6, ADR-0021).
 *
 * <p>Es lo <b>unico</b> que este contexto lee del padron, y esta separado de {@link
 * FiscalizacionRepository} para que se vea: lo demas es de aqui, esto no. Son dos preguntas y las
 * dos son sobre el predio ajeno —donde difiere el area, y si el predio siquiera esta—, asi que
 * viven en el mismo puerto: un segundo puerto para una sola consulta esconderia justo lo que este
 * separa.
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
     * Los predios cuya area inscrita <b>alcanza</b> el umbral de diferencia, y el censo de lo que
     * se miro (#25).
     *
     * <p>El area del poligono se calcula <b>solo para comparar</b> y no se escribe en ninguna
     * parte: derivarla cambiaria el autovaluo de todo el padron sin que nadie lo decidiera, y un
     * area es indistinguible de otra al leerla (ADR-0021).
     *
     * <p><b>El umbral es el de la campania y no hay ningun segundo filtro</b> (#25 AC-1). Hasta #25
     * el {@code WHERE} usaba una {@code tolerancia} que venia en el cuerpo de la peticion y el caso
     * de uso volvia a filtrar por el umbral guardado: con {@code tolerancia > umbral} el segundo no
     * quitaba nada y la fila de la campania decia un criterio que no fue el que corrio. Ahora la
     * cifra es una, y la comparacion es la que {@link Score#alcanza} define —{@code &gt;=}, o sea
     * que alcanzar el umbral basta—: con {@code &gt;} estricto, el predio que difiere
     * <b>exactamente</b> lo que la campania declaro sospechoso se caeria del cruce sin que nada lo
     * dijera.
     *
     * @param umbral la diferencia relativa que hace sospechar; la declara la campania
     * @param tope cuantos como mucho; lo declara la campania, y queda en su fila
     * @throws SinCartografia si la municipalidad no tiene ni un predio con geometria
     */
    CruceDelPadron contrastar(Score umbral, int tope);

    /**
     * Lo que devuelve el cruce: los contrastes <b>y de que universo salen</b> (#25 AC-3).
     *
     * <p>Sin el censo, «0 candidatos» y «0 candidatos entre las fichas que miro» se leen igual, y
     * la segunda es la que cierra una campania diciendo que el padron esta bien. Es el criterio de
     * {@link SinCartografia} un escalon mas abajo: alli no se puede mirar nada, y aqui se mira una
     * parte.
     */
    record CruceDelPadron(java.util.List<ContrasteDeAreas> contrastes, Cobertura cobertura) {

        public CruceDelPadron {
            contrastes = java.util.List.copyOf(contrastes);
            java.util.Objects.requireNonNull(cobertura, "El cruce dice de que universo sale");
        }
    }

    /**
     * De cuantos predios salio el cruce, y cuantos se quedaron fuera y por que.
     *
     * <p>Las cifras se cuentan <b>en la base</b> y no sobre la lista devuelta: la lista viene
     * truncada por el tope, asi que contarla diria cuantos cupieron y no cuantos hay.
     *
     * @param prediosActivos los predios ACTIVOS de la municipalidad
     * @param sinGeometria cuantos de ellos no tienen poligono, asi que no hay con que contrastar
     * @param sinFichaVigente cuantos tienen poligono y ninguna ficha vigente con area mayor que
     *     cero — de cualquiera de las cuatro clases
     * @param contrastados cuantos se llegaron a comparar
     * @param superanElUmbral cuantos de los comparados alcanzan el umbral, ANTES del tope
     * @param devueltos cuantos caben en el tope de la campania
     */
    record Cobertura(
            long prediosActivos,
            long sinGeometria,
            long sinFichaVigente,
            long contrastados,
            long superanElUmbral,
            long devueltos) {

        /** Los que alcanzaban el umbral y no cupieron: el tope los dejo fuera. */
        public long truncadosPorElTope() {
            return superanElUmbral - devueltos;
        }
    }

    /**
     * Si el predio esta en el padron de esta municipalidad (#17, AC-3).
     *
     * <p>Lo necesita la lectura de hallazgos por predio, y por una razon que se ve en las dos
     * respuestas que separa: un predio que <b>esta</b> y no tiene hallazgos contesta {@code 200}
     * con lista vacia, y uno que <b>no esta</b> contesta {@code 404}. Sin esta pregunta las dos se
     * contestarian igual —lista vacia—, y entonces «este predio esta limpio» seria indistinguible
     * de «te equivocaste de identificador»: la primera cierra una revision y la segunda se arregla
     * tecleando bien.
     *
     * <p>Bajo RLS el predio de otra municipalidad no es «prohibido»: <b>no existe</b>, asi que este
     * metodo devuelve {@code false} sobre el y el borde contesta {@code 404}. Es la misma respuesta
     * que da {@code grd} sobre un lote ajeno, y por el mismo motivo.
     */
    boolean estaEnElPadron(long predioId);

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
