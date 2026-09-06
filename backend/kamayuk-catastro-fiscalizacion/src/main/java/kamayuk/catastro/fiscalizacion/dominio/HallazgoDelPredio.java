package kamayuk.catastro.fiscalizacion.dominio;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Un hallazgo <b>visto desde el predio</b>: el hecho, la campania en que se hallo y su acta (#17).
 *
 * <h2>Por que es un registro propio y no {@link Hallazgo} con dos campos mas</h2>
 *
 * <p>Porque {@link Hallazgo} es lo que se ESCRIBE —lo que el inspector firmo— y esto es lo que se
 * LEE desde fuera del recorrido. La campania y el acta no son atributos del hallazgo: la primera
 * cuelga de su candidato y la segunda se levanta despues, si se levanta. Meterlas dentro de {@link
 * Hallazgo} las dejaria nulas en todos los caminos de escritura —{@code VerificarEnCampo} crea el
 * hallazgo <b>antes</b> de que exista acta ninguna— y un campo que significa una cosa al escribir y
 * otra al leer es el que alguien acaba leyendo por el lado equivocado. Es la misma asimetria, con
 * el mismo motivo, que la geometria de {@code ZonaDeRiesgo} en {@code grd}.
 *
 * <h2>Aqui nunca hay un omiso catastral, y no es un filtro</h2>
 *
 * <p>Es la clase la que lo decide: {@code hallazgo_contraste_check} de {@code V9} exige que un
 * {@link ClaseDeHallazgo#OMISO_CATASTRAL} tenga {@code predio_id} <b>nulo</b> —si lo tuviera no
 * seria un omiso—, asi que una lectura por predio no puede alcanzarlo por construccion. Se dice
 * aqui, en el borde y en el contrato porque quien lea «los hallazgos del predio» va a suponer que
 * estan todos, y los omisos de esa campania no estan en ninguno.
 *
 * <p><b>Ni un importe</b>, como en {@link Hallazgo}: dos superficies y su resta. Lo que se cobre lo
 * decide {@code rentas} (ADR-0024).
 *
 * @param hallazgo el hecho, tal como se guardo
 * @param campaniaId la campania en que se hallo, por su candidato
 * @param campaniaCodigo el codigo de esa campania: el identificador con el que se la nombra
 * @param acta el acta levantada sobre el, o {@code null} si todavia no se levanto ninguna. Nulo y
 *     no un acta vacia: un hallazgo firme sin acta es un estado legitimo del recorrido
 */
public record HallazgoDelPredio(
        Hallazgo hallazgo, long campaniaId, String campaniaCodigo, @Nullable Acta acta) {

    public HallazgoDelPredio {
        Objects.requireNonNull(hallazgo, "No hay hallazgo del predio sin hallazgo");
        Objects.requireNonNull(campaniaCodigo, "Un hallazgo se hallo en una campania con codigo");
        if (hallazgo.predioId() == null) {
            throw new IllegalArgumentException(
                    "Un hallazgo sin predio es un OMISO_CATASTRAL, y por definicion no es de ningun"
                            + " predio: esta lectura no puede devolverlo (V9,"
                            + " hallazgo_contraste_check)");
        }
    }

    /** El acta, si ya se levanto. */
    public Optional<Acta> actaLevantada() {
        return Optional.ofNullable(acta);
    }
}
