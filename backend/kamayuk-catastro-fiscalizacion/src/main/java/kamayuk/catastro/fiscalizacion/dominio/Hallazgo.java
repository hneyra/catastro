package kamayuk.catastro.fiscalizacion.dominio;

import java.time.LocalDate;
import java.util.Objects;
import kamayuk.catastro.dominio.AreaM2;
import org.jspecify.annotations.Nullable;

/**
 * Lo que una <b>persona</b> verifico (ADR-0035 punto 2).
 *
 * <p>Es la otra mitad de la decision: {@link Candidato} es lo que la maquina cree y esto es lo que
 * alguien firmo. Lleva nombre —{@code inspector}— y fecha, que es la respuesta a la unica pregunta
 * que un {@code score} no puede contestar: «¿quien dijo esto?».
 *
 * <h2>Lo que un hallazgo firme NO hace</h2>
 *
 * <p><b>No corrige el area.</b> Habilita el acto que una persona ejecuta —versionar la ficha con su
 * observacion obligatoria—, y ese acto ya existe y esta en otro contexto. Esta clase no depende de
 * ningun camino de escritura de la ficha, y no es una promesa: lo comprueba {@code
 * NINGUN_HALLAZGO_CORRIGE_LA_FICHA}, que desde #6 mira este codigo y no solo su muestra.
 *
 * <p>El defecto que eso impide tiene una forma concreta: la campania deja cuatro mil hallazgos con
 * su delta de area calculado, alguien mira la cifra y le parece obvio «aplicarlos». Lo que produce
 * es un padron corregido sin acto administrativo detras — el contribuyente no recibe papel, no hay
 * plazo que impugnar, y el autovaluo de todo el distrito cambia sin que nadie lo haya decidido.
 *
 * <h2>Por que {@code fichaId} y por que el area va copiada</h2>
 *
 * <p>{@code fichaId} dice <b>que version</b> de ficha se contrasto. La ficha se versiona, asi que
 * sin esa referencia un hallazgo de marzo no se puede releer en julio: en julio la comparacion
 * daria otra diferencia, y no habria forma de saber cual de las dos vio el inspector. Es el mismo
 * motivo por el que {@code declaracion_jurada.ficha_catastral_id} la lleva.
 *
 * <p>Y {@link #areaDeLaFicha} se <b>copia al verificar</b> en vez de releerse: releerla despues
 * daria la de la version vigente entonces, y el hallazgo pasaria a afirmar una diferencia que nadie
 * hallo. Es la misma decision que congela el desglose de un recibo.
 *
 * <p>Los tres —{@code predioId}, {@code fichaId} y {@code areaDeLaFicha}— van juntos y atados a la
 * clase: un {@link ClaseDeHallazgo#SUBVALUADOR} los exige y un {@link
 * ClaseDeHallazgo#OMISO_CATASTRAL} exige que sean nulos, porque si hay predio no es un omiso
 * catastral. Lo sostiene tambien la base ({@code hallazgo_contraste_check}).
 *
 * <h2>Dejar sin efecto es un ACTO, con las columnas de un acto (#23)</h2>
 *
 * <p>Hasta #23 {@link #dejadoSinEfecto} no lo llamaba nadie —ni caso de uso, ni endpoint— y {@link
 * EstadoDelHallazgo#DEJADO_SIN_EFECTO} era inalcanzable. Y no guardaba <b>ni motivo, ni quien, ni
 * cuando</b>: lo unico que habria quedado era la {@code observacion} de la fila, que la anulacion
 * sobrescribe. Ahora lleva su {@link Anulacion}, como {@code candidato.descarte} lleva la suya y
 * como {@code itse} lleva su fecha y su motivo.
 *
 * <h2>Y NO lleva geometria, desde #23</h2>
 *
 * <p>La tenia y no se podia llenar por ninguna via: {@code TODA_GEOMETRIA_ENTRA_POR_BATCH}
 * (ADR-0021) cierra la entrada por HTTP, no hay ningun cargador que la escriba, y el unico camino
 * de produccion —{@code VerificarEnCampo}— recibia {@code null} del borde. Se midio antes de
 * retirarla: ningun {@code *Resource} la publica y el contrato que {@code rentas} declara de las
 * dos lecturas de hallazgos tiene once campos y ninguno es ella. La columna, sus cuatro columnas de
 * marco generadas y su GiST se pagaban en cada escritura y mentian sobre lo que la tabla guarda.
 * Vuelve el dia que haya un cargador que la llene, en otra migracion.
 *
 * @param id nulo mientras no se haya guardado
 * @param anulacion nulo mientras el hallazgo siga firme
 */
public record Hallazgo(
        @Nullable Long id,
        long candidatoId,
        ClaseDeHallazgo clase,
        @Nullable Long predioId,
        @Nullable Long fichaId,
        @Nullable AreaM2 areaDeLaFicha,
        AreaM2 areaVerificada,
        String inspector,
        LocalDate verificadoEn,
        EstadoDelHallazgo estado,
        @Nullable Anulacion anulacion) {

    private static final int INSPECTOR_MAXIMO = 60;

    public Hallazgo {
        Objects.requireNonNull(clase, "El hallazgo necesita su clase");
        Objects.requireNonNull(areaVerificada, "El hallazgo necesita el area que se verifico");
        Objects.requireNonNull(inspector, "Un hallazgo lleva NOMBRE: quien lo verifico");
        Objects.requireNonNull(verificadoEn, "El hallazgo necesita la fecha en que se verifico");
        Objects.requireNonNull(estado, "El hallazgo necesita su estado");
        inspector = inspector.strip();
        if (inspector.isEmpty() || inspector.length() > INSPECTOR_MAXIMO) {
            throw new IllegalArgumentException(
                    "El inspector va de 1 a "
                            + INSPECTOR_MAXIMO
                            + " caracteres: '"
                            + inspector
                            + "'");
        }
        boolean contrasteCompleto = predioId != null && fichaId != null && areaDeLaFicha != null;
        boolean contrasteVacio = predioId == null && fichaId == null && areaDeLaFicha == null;
        if (clase == ClaseDeHallazgo.SUBVALUADOR && !contrasteCompleto) {
            throw new IllegalArgumentException(
                    "Un subvaluador contrasta una VERSION concreta de ficha: sin predio, sin ficha"
                            + " y sin el area copiada de esa version, un hallazgo de marzo no se"
                            + " puede releer en julio");
        }
        if (clase == ClaseDeHallazgo.OMISO_CATASTRAL && !contrasteVacio) {
            throw new IllegalArgumentException(
                    "Un omiso catastral es, por definicion, lo que NO tiene predio: si lo tuviera no"
                            + " seria un omiso catastral sino otra cosa");
        }
        // El estado y el acto van atados en los DOS sentidos, igual que en `candidato`: un
        // DEJADO_SIN_EFECTO sin motivo no explica nada, y un hallazgo FIRME con motivo de
        // anulacion es una contradiccion escrita en una fila. Lo sostiene tambien la base
        // (`hallazgo_anulacion_check` de `V12`).
        if ((estado == EstadoDelHallazgo.DEJADO_SIN_EFECTO) != (anulacion != null)) {
            throw new IllegalArgumentException(
                    "Un hallazgo dejado sin efecto lleva su motivo, quien lo decidio y cuando; y"
                            + " uno firme no lleva ninguna de las tres (#23)");
        }
    }

    /**
     * El acto de dejarlo sin efecto: por que, quien y cuando.
     *
     * <p>Las tres cosas y no una: «lo anularon» sin motivo no explica nada, sin nombre no responde
     * a quien lo decidio —que es la unica pregunta que un estado no contesta— y sin fecha no se
     * puede ordenar contra el acta que ese hallazgo ya habia producido.
     */
    public record Anulacion(String motivo, String quien, java.time.Instant cuando) {

        private static final int MOTIVO_MAXIMO = 500;
        private static final int QUIEN_MAXIMO = 60;

        public Anulacion {
            Objects.requireNonNull(motivo, "Una anulacion sin motivo no explica nada (regla 4)");
            Objects.requireNonNull(quien, "Una anulacion dice quien la decidio");
            Objects.requireNonNull(cuando, "Una anulacion dice cuando se decidio");
            motivo = motivo.strip();
            quien = quien.strip();
            if (motivo.isEmpty() || motivo.length() > MOTIVO_MAXIMO) {
                throw new IllegalArgumentException(
                        "El motivo de la anulacion va de 1 a "
                                + MOTIVO_MAXIMO
                                + " caracteres: '"
                                + motivo
                                + "'");
            }
            if (quien.isEmpty() || quien.length() > QUIEN_MAXIMO) {
                throw new IllegalArgumentException(
                        "Quien anula va de 1 a " + QUIEN_MAXIMO + " caracteres: '" + quien + "'");
            }
        }
    }

    /** El hallazgo de un subvaluador: la version que se contrasto y las dos areas. */
    public static Hallazgo deSubvaluador(
            long candidatoId,
            long predioId,
            long fichaId,
            AreaM2 areaDeLaFicha,
            AreaM2 areaVerificada,
            String inspector,
            LocalDate verificadoEn) {
        return new Hallazgo(
                null,
                candidatoId,
                ClaseDeHallazgo.SUBVALUADOR,
                predioId,
                fichaId,
                areaDeLaFicha,
                areaVerificada,
                inspector,
                verificadoEn,
                EstadoDelHallazgo.FIRME,
                null);
    }

    /**
     * El hallazgo de un omiso catastral: no hay predio, y por eso no hay ficha ni area de ficha.
     */
    public static Hallazgo deOmisoCatastral(
            long candidatoId, AreaM2 areaVerificada, String inspector, LocalDate verificadoEn) {
        return new Hallazgo(
                null,
                candidatoId,
                ClaseDeHallazgo.OMISO_CATASTRAL,
                null,
                null,
                null,
                areaVerificada,
                inspector,
                verificadoEn,
                EstadoDelHallazgo.FIRME,
                null);
    }

    public boolean esNuevo() {
        return id == null;
    }

    public boolean estaFirme() {
        return estado == EstadoDelHallazgo.FIRME;
    }

    /**
     * La diferencia hallada, o vacia cuando no hay con que compararla.
     *
     * <p>Devuelve el <b>exceso verificado sobre lo inscrito</b> y no un porcentaje: un porcentaje
     * invita a leerse como «cuanto se subvaluo», y eso es una afirmacion sobre el valor, que este
     * contexto no hace (ADR-0024). Aqui son dos superficies y su resta.
     *
     * <p>Vacia en un omiso catastral, y es correcto: no hay area inscrita de la que diferir, porque
     * no hay ficha. Devolver cero seria decir que coinciden.
     */
    public java.util.Optional<AreaM2> excesoVerificado() {
        AreaM2 inscrita = areaDeLaFicha;
        if (inscrita == null || areaVerificada.compareTo(inscrita) <= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new AreaM2(areaVerificada.valor().subtract(inscrita.valor())));
    }

    /**
     * Lo deja sin efecto, con su motivo, su nombre y su fecha (#23 AC-1).
     *
     * <p><b>No se borra</b> (regla 4): la fila se queda, su acta se queda donde esta —es INMUTABLE
     * y {@code V9} le revoca el {@code UPDATE}— y lo unico que cambia es que este hallazgo deja de
     * habilitar ningun acto nuevo.
     *
     * <p>Y no se reescribe nada de lo que el inspector verifico: las dos areas siguen diciendo lo
     * que dijeron. Corregirlas aqui dejaria su acta afirmando una cosa y la base otra.
     */
    public Hallazgo dejadoSinEfecto(String motivo, String quien, java.time.Instant cuando) {
        if (!estaFirme()) {
            throw new IllegalStateException(
                    "El hallazgo ya estaba dejado sin efecto; dejarlo dos veces escribiria dos"
                            + " actos donde hubo uno");
        }
        return new Hallazgo(
                id,
                candidatoId,
                clase,
                predioId,
                fichaId,
                areaDeLaFicha,
                areaVerificada,
                inspector,
                verificadoEn,
                EstadoDelHallazgo.DEJADO_SIN_EFECTO,
                new Anulacion(motivo, quien, cuando));
    }
}
