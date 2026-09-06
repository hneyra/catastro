package kamayuk.catastro.fiscalizacion.dominio;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Lo que la <b>maquina</b> sospecha (ADR-0035 punto 1).
 *
 * <p>No tiene efecto juridico ninguno. Es una fila que dice «aqui hay algo raro» con el origen que
 * lo dijo, el score que le puso y los insumos que lo dispararon, para que una persona la mire.
 *
 * <h2>{@code predioId} es nulable, y ahi esta la mitad del ADR</h2>
 *
 * <p>En el <b>omiso catastral</b> —hay techo en la ortofoto y no hay fila de {@code predio}— no
 * hay, por definicion, predio al que apuntar. Exigirlo obligaria a inventar un predio para poder
 * sospechar que falta, que es lo contrario de lo que la fila afirma. Un {@link
 * ClaseDeHallazgo#SUBVALUADOR} si lo exige y lo sostiene tambien la base ({@code
 * candidato_predio_de_la_clase_check}): sin predio no hay ficha que contrastar.
 *
 * <h2>Las dos compuertas viven en los metodos, no en un {@code setEstado}</h2>
 *
 * <p>{@link #admitidoEnGabinete()} y {@link #verificadoEnCampo()} <b>rechazan</b> la transicion que
 * no toca, y {@link #descartadoEn} exige la etapa y el motivo. Que no exista ninguna forma de
 * escribir un estado suelto es lo que impide que el atajo se escriba sin querer: pasar de {@code
 * DETECTADO} a {@code VERIFICADO_EN_CAMPO} no es una linea mas larga, es una excepcion.
 *
 * @param id nulo mientras no se haya guardado
 * @param predioId nulo en el omiso catastral, por definicion
 * @param geometria el poligono de lo sospechado, en WKT; nulo cuando el insumo no trae ninguno —una
 *     denuncia por telefono no lo trae—
 * @param descarte nulo mientras el candidato siga vivo
 */
public record Candidato(
        @Nullable Long id,
        long campaniaId,
        @Nullable Long predioId,
        ClaseDeHallazgo clase,
        OrigenDelCandidato origen,
        Score score,
        String insumos,
        @Nullable String geometria,
        EstadoDelCandidato estado,
        @Nullable Descarte descarte) {

    public Candidato {
        Objects.requireNonNull(clase, "El candidato necesita su clase");
        Objects.requireNonNull(origen, "El candidato necesita su origen");
        Objects.requireNonNull(score, "El candidato necesita su score");
        Objects.requireNonNull(insumos, "El candidato necesita los insumos que lo dispararon");
        Objects.requireNonNull(estado, "El candidato necesita su estado");
        if (insumos.isBlank()) {
            throw new IllegalArgumentException(
                    "Los insumos son lo que permite volver a la fuente de la sospecha: sin ellos,"
                            + " un descarte no se puede explicar y el detector no se puede"
                            + " calibrar");
        }
        if (clase == ClaseDeHallazgo.SUBVALUADOR && predioId == null) {
            throw new IllegalArgumentException(
                    "Un subvaluador es un predio cuya ficha dice otra area: sin predio no hay ficha"
                            + " que contrastar");
        }
        if ((estado == EstadoDelCandidato.DESCARTADO) != (descarte != null)) {
            throw new IllegalArgumentException(
                    "Un candidato esta descartado si y solo si lleva su descarte con etapa y"
                            + " motivo (ADR-0035 punto 5): llego estado "
                            + estado
                            + " con descarte "
                            + (descarte == null ? "ausente" : "presente"));
        }
    }

    /** Uno recien detectado, que es lo unico que el detector puede producir. */
    public static Candidato detectado(
            long campaniaId,
            @Nullable Long predioId,
            ClaseDeHallazgo clase,
            OrigenDelCandidato origen,
            Score score,
            String insumos,
            @Nullable String geometria) {
        return new Candidato(
                null,
                campaniaId,
                predioId,
                clase,
                origen,
                score,
                insumos,
                geometria,
                EstadoDelCandidato.DETECTADO,
                null);
    }

    public boolean esNuevo() {
        return id == null;
    }

    /**
     * La primera compuerta.
     *
     * @throws TransicionQueNoExiste si el candidato no esta recien detectado
     */
    public Candidato admitidoEnGabinete() {
        exigirEstado(EstadoDelCandidato.DETECTADO, "admitirlo en gabinete");
        return conEstado(EstadoDelCandidato.ADMITIDO_EN_GABINETE, null);
    }

    /**
     * La segunda compuerta. <b>Este es el metodo que el atajo tendria que saltarse.</b>
     *
     * @throws TransicionQueNoExiste si el candidato no paso antes por gabinete
     */
    public Candidato verificadoEnCampo() {
        exigirEstado(EstadoDelCandidato.ADMITIDO_EN_GABINETE, "verificarlo en campo");
        return conEstado(EstadoDelCandidato.VERIFICADO_EN_CAMPO, null);
    }

    /** El descarte, en la etapa que sea, con quien lo descarto y por que. */
    public Candidato descartadoEn(
            EtapaDeVerificacion etapa, String motivo, String quien, Instant cuando) {
        if (estado.esTerminal()) {
            throw new TransicionQueNoExiste(estado, "descartarlo");
        }
        if (etapa == EtapaDeVerificacion.CAMPO
                && estado != EstadoDelCandidato.ADMITIDO_EN_GABINETE) {
            throw new TransicionQueNoExiste(estado, "descartarlo en campo");
        }
        return conEstado(EstadoDelCandidato.DESCARTADO, new Descarte(etapa, motivo, quien, cuando));
    }

    private Candidato conEstado(EstadoDelCandidato nuevo, @Nullable Descarte descarteNuevo) {
        return new Candidato(
                id,
                campaniaId,
                predioId,
                clase,
                origen,
                score,
                insumos,
                geometria,
                nuevo,
                descarteNuevo);
    }

    private void exigirEstado(EstadoDelCandidato esperado, String acto) {
        if (estado != esperado) {
            throw new TransicionQueNoExiste(estado, acto);
        }
    }

    /**
     * El descarte conservado: etapa, motivo, quien y cuando (ADR-0035 punto 5).
     *
     * <p>La <b>etapa</b> no se deduce del estado anterior porque el estado anterior no se guarda en
     * ninguna parte, y es justo lo que hay que contar: la tasa de descarte por compuerta es el
     * unico indicador honesto de si el umbral de deteccion sirve.
     */
    public record Descarte(EtapaDeVerificacion etapa, String motivo, String quien, Instant cuando) {

        private static final int MOTIVO_MAXIMO = 500;

        public Descarte {
            Objects.requireNonNull(etapa, "Un descarte dice en que compuerta ocurrio");
            Objects.requireNonNull(motivo, "Un descarte sin motivo no explica nada (regla 4)");
            Objects.requireNonNull(quien, "Un descarte dice quien lo decidio");
            Objects.requireNonNull(cuando, "Un descarte dice cuando se decidio");
            motivo = motivo.strip();
            if (motivo.isEmpty() || motivo.length() > MOTIVO_MAXIMO) {
                throw new IllegalArgumentException(
                        "El motivo del descarte va de 1 a "
                                + MOTIVO_MAXIMO
                                + " caracteres: '"
                                + motivo
                                + "'");
            }
        }
    }

    /**
     * Se pidio una transicion que el recorrido no tiene.
     *
     * <p>Su caso principal es el atajo que ADR-0035 existe para impedir: verificar en campo un
     * candidato que nadie miro en gabinete. El mensaje nombra los dos estados a proposito — «no se
     * puede» sin decir desde donde manda a quien atiende a adivinar.
     */
    public static final class TransicionQueNoExiste extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        private final EstadoDelCandidato desde;

        TransicionQueNoExiste(EstadoDelCandidato desde, String acto) {
            super(
                    "Un candidato "
                            + desde
                            + " no admite "
                            + acto
                            + ": las dos compuertas —gabinete y campo— se pasan en orden, y"
                            + " saltarse una deja un acta sin nadie que la sostenga (ADR-0035)");
            this.desde = desde;
        }

        public EstadoDelCandidato desde() {
            return desde;
        }
    }
}
