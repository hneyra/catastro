package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import java.util.ArrayList;
import java.util.List;
import kamayuk.comun.verificaciones.ReglasDeArquitectura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #30 AC-3: el recorrido del padron no cuelga de ninguna peticion HTTP.
 *
 * <h2>Que se impide, exactamente</h2>
 *
 * <p>{@code AreasDelPadronJdbc.CRUCE} calcula {@code ST_Area} geodesico sobre <b>todos</b> los
 * predios con geometria del inquilino y ordena por la diferencia, de modo que hay que calcularlo
 * todo antes de aplicar el {@code LIMIT}. No hay indice que lo evite: un area geodesica no esta en
 * ninguna columna, y ADR-0021 dice por que no debe estarlo. Dentro de un {@code POST} sincrono eso
 * es una peticion cuyo coste crece con el padron — hoy no se nota porque no hay ni un poligono
 * cargado en ninguna instalacion, y el dia que se cargue el plano de una municipalidad agota el
 * tiempo de espera del ingreso.
 *
 * <p>Desde #30 la corrida vive en {@code DetectarEnCampania}, del perfil {@code batch}, como {@code
 * DerivarFrentes} y los ocho cargadores. Esta guarda impide que vuelva: <b>ninguna clase del borde
 * HTTP puede depender del detector ni del puerto que recorre el padron</b>.
 *
 * <h2>Por que el bytecode y no un escaner de texto</h2>
 *
 * <p>Porque lo que hay que ver es una <b>dependencia</b>, y una dependencia si deja huella en el
 * bytecode —al reves que un cruce de SQL, que por eso lo vigila un escaner—. Un controlador que
 * inyectara el detector, o que lo alcanzara por un ayudante, sale aqui aunque el nombre no aparezca
 * escrito en su fuente.
 *
 * <h2>Lo que esta guarda NO dice</h2>
 *
 * <p>No dice que el recorrido sea barato: sigue sin cota y sigue siendo #25 quien decide el
 * algoritmo. Dice que quien lo paga es un proceso de vida corta con su registro y su codigo de
 * salida, y no una peticion con alguien esperando al otro lado.
 */
@DisplayName("#30 — la deteccion no esta en el camino caliente")
class LaDeteccionNoEstaEnElCaminoCalienteTest {

    /** El paquete del borde HTTP de todos los contextos acotados de este sistema. */
    private static final String BORDE = ".infraestructura.web.";

    /**
     * Lo que recorre el padron: el caso de uso y el puerto del que cuelga el cruce.
     *
     * <p>Los dos, y no solo el primero: alcanzar {@code AreasDelPadron} desde el borde sin pasar
     * por {@code DetectarSubvaluadores} seria el mismo recorrido con otro nombre.
     */
    private static final List<String> LO_QUE_RECORRE_EL_PADRON =
            List.of(
                    "kamayuk.catastro.fiscalizacion.aplicacion.DetectarSubvaluadores",
                    "kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron");

    @Test
    @DisplayName("ninguna clase del borde HTTP depende del detector ni del puerto del padron")
    void elBordeNoAlcanzaLaDeteccion() {
        List<JavaClass> delBorde =
                ReglasDeArquitectura.clasesDeProduccion().stream()
                        .filter(clase -> clase.getName().contains(BORDE))
                        .toList();

        // Sin sujeto esto se cumpliria solo: si el importador dejara de ver la capa web, la
        // afirmacion «ninguna depende» seria cierta sobre el conjunto vacio. Falla en vez de
        // saltarsela.
        assertThat(delBorde)
                .as(
                        "no se leyo ni una clase de «%s» del bytecode de este sistema: esto no es"
                                + " «esta bien» sino «no se pudo comprobar»",
                        BORDE)
                .isNotEmpty();

        List<String> culpables = new ArrayList<>();
        for (JavaClass clase : delBorde) {
            for (JavaClass alcanzada :
                    clase.getDirectDependenciesFromSelf().stream()
                            .map(d -> d.getTargetClass())
                            .toList()) {
                String nombre = alcanzada.getName();
                for (String prohibida : LO_QUE_RECORRE_EL_PADRON) {
                    if (nombre.equals(prohibida) || nombre.startsWith(prohibida + "$")) {
                        culpables.add(clase.getSimpleName() + " -> " + nombre);
                    }
                }
            }
        }

        assertThat(culpables)
                .as(
                        "la corrida de deteccion recorre el padron entero del inquilino calculando"
                                + " areas geodesicas, sin cota y sin indice posible: dentro de un"
                                + " POST sincrono es una peticion que agota el tiempo de espera del"
                                + " ingreso el dia que haya cartografia. La lanza"
                                + " DetectarEnCampania, en el perfil batch (#30, AC-3)")
                .isEmpty();
    }
}
