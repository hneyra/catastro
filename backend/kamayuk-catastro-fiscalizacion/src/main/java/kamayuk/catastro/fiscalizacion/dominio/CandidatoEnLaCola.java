package kamayuk.catastro.fiscalizacion.dominio;

import java.util.Objects;
import kamayuk.catastro.fiscalizacion.dominio.Candidato.Descarte;
import org.jspecify.annotations.Nullable;

/**
 * Un candidato <b>como sale en la cola de gabinete</b>: todo lo suyo menos el poligono (#30).
 *
 * <h2>Por que es un registro propio y no {@link Candidato}</h2>
 *
 * <p>Por lo mismo que {@link HallazgoDelPredio} no es un {@link Hallazgo} con dos campos mas:
 * {@link Candidato} es lo que se <b>ESCRIBE</b> —lo que el detector produjo, con el poligono de lo
 * sospechado dentro— y esto es lo que se <b>LEE</b> por paginas.
 *
 * <p>Y no vale reusar {@link Candidato} con el poligono en {@code null}, que es el atajo obvio: ahi
 * {@code null} ya significa algo —«el insumo no traia ninguno; una denuncia por telefono no lo
 * trae»— y una pagina que lo dejara nulo estaria afirmando eso de cada fila. Un campo que significa
 * una cosa al escribir y otra al leer es el que alguien acaba leyendo por el lado equivocado.
 *
 * <h2>Lo que costaba traerlo, medido</h2>
 *
 * <p>#30, AC-4: con poligonos de PDU de 5 001 vertices sembrados en {@code candidato}, una pagina
 * de <b>20 filas</b> pesaba <b>3 772 822 bytes</b> —3,6 MiB por pantalla de una grilla que no
 * dibuja ningun poligono—. {@code CandidatoResource} nunca lo publico: su javadoc lo dice desde #6
 * («la geometria no sale … el visor del plano es otra cosa y tiene su propia ruta, ADR-0022»), asi
 * que la conversion a texto se hacia para tirarla.
 *
 * <p><b>Y el poligono de UNO sigue disponible</b>: {@code candidatoPorId} devuelve el {@link
 * Candidato} entero, con su geometria. La geometria de una fila es una lectura por identificador,
 * no una columna de la pagina.
 *
 * @param predioId nulo en el omiso catastral, por definicion
 * @param descarte nulo mientras el candidato siga vivo
 */
public record CandidatoEnLaCola(
        long id,
        long campaniaId,
        @Nullable Long predioId,
        ClaseDeHallazgo clase,
        OrigenDelCandidato origen,
        Score score,
        String insumos,
        EstadoDelCandidato estado,
        @Nullable Descarte descarte) {

    public CandidatoEnLaCola {
        Objects.requireNonNull(clase, "El candidato leido trae su clase");
        Objects.requireNonNull(origen, "El candidato leido trae su origen");
        Objects.requireNonNull(score, "El candidato leido trae su score");
        Objects.requireNonNull(insumos, "El candidato leido trae los insumos que lo dispararon");
        Objects.requireNonNull(estado, "El candidato leido trae su estado");
        if ((estado == EstadoDelCandidato.DESCARTADO) != (descarte != null)) {
            throw new IllegalArgumentException(
                    "Un candidato esta descartado si y solo si lleva su descarte con etapa y"
                            + " motivo (ADR-0035 punto 5): llego estado "
                            + estado
                            + " con descarte "
                            + (descarte == null ? "ausente" : "presente"));
        }
    }
}
