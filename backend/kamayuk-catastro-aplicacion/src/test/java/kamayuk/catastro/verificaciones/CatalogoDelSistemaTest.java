package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import kamayuk.catastro.autorizacion.RequiereAcceso;
import kamayuk.catastro.seguridad.dominio.CatalogoDelSistema;
import kamayuk.comun.verificaciones.ReglasDeArquitectura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El catalogo de este sistema es <b>exactamente</b> lo que sus endpoints exigen.
 *
 * <p>{@link CatalogoDelSistema} es una lista escrita, y una lista escrita se desincroniza. Lo que
 * la ata a la realidad es esta prueba: lee el <b>bytecode</b> de todas las clases de produccion,
 * junta el {@code acceso} de cada {@code RequiereAcceso} y exige que sean los mismos codigos.
 *
 * <p>Los dos sentidos importan, y por motivos distintos:
 *
 * <ul>
 *   <li><b>Un acceso que el catalogo no tiene</b> es una pantalla a la que nadie puede dar permiso
 *       —el guardia niega lo que no encuentra en {@code acceso}—, que es el defecto que RF-122
 *       existe para impedir.
 *   <li><b>Un acceso que sobra</b> es una fila que la implantacion siembra y un permiso que se
 *       otorga sobre algo que no existe: ruido en la pantalla de permisos y una promesa falsa.
 * </ul>
 *
 * <h2>Por que el bytecode y no el fuente (#43)</h2>
 *
 * <p>Hasta #43 esta guarda buscaba el texto de la anotacion con una expresion regular anclada a la
 * comilla: {@code acceso\s*=\s*"([a-z0-9_]+)"}. O sea que solo veia una de las dos formas de
 * escribir lo mismo. {@code SectorController} y {@code ViaController} escriben la suya como
 * <b>constante</b> —{@code acceso = SectorController.ACCESO}, que vale {@code "sectores"}—, asi que
 * {@code sectores} y {@code calles} nunca estuvieron en el catalogo, sus <b>ocho</b> endpoints
 * contestaron 403 a todo el mundo —administrador incluido— y <b>esta prueba paso en verde</b>
 * durante todo el corte. Una guarda que solo sabe leer una de las dos formas volvera a fallar en
 * cuanto alguien use la otra, asi que no se arregla la expresion regular: se cambia de sujeto.
 *
 * <p>El sujeto bueno lo tiene {@code javac} ya resuelto. {@code acceso} es un {@code String}, y una
 * constante de compilacion se graba en el pool del {@code class} <b>como su valor</b>: en el
 * bytecode el literal y la constante son indistinguibles, que es justo la distincion que no debia
 * existir. Es la misma leccion que T-0 dejo escrita cuando ArchUnit no exponia el nombre de un
 * parametro y hubo que ir a {@code JavaMethod.reflect()}.
 *
 * <p><b>Y por eso vive aqui y no en {@code kamayuk-catastro-seguridad}</b>, donde estaba. Leer el
 * bytecode de todos los modulos exige tenerlos en el classpath, y {@code
 * kamayuk-catastro-aplicacion} es el unico que los tiene —lo dice su propio {@code
 * build.gradle.kts}: «es tambien donde corren las verificaciones que necesitan ver todo el sistema
 * a la vez»—. Se gana ademas lo que la version anterior no podia tener: <b>las clases de los otros
 * modulos son entrada declarada de esta tarea</b>. La de antes recorria {@code src/main} de todo el
 * repositorio desde un modulo que no depende de ninguno, asi que anadir un {@code RequiereAcceso}
 * en {@code nucleo} dejaba {@code :kamayuk-catastro-seguridad:test} en UP-TO-DATE y el defecto
 * pasaba en <b>verde rancio</b> —medido antes de mover la prueba, con un acceso inventado en {@code
 * DepreciacionController}: «12 actionable tasks: 12 up-to-date»—. Es la leccion de #192 punto 2.
 */
@DisplayName("C-7 y #43 — el catalogo de este sistema es el de sus endpoints")
class CatalogoDelSistemaTest {

    /**
     * Los dos centinelas de {@link RequiereAcceso}, excluidos <b>por su valor</b>.
     *
     * <p>No son opciones del catalogo y no deben estarlo: {@code SESION_PROPIA} es la lectura de la
     * propia sesion —no hay privilegio que configurar— y {@code CIUDADANO} es el portal, y un
     * ciudadano no tiene fila en {@code usuario} (ADR-0013, ADR-0020). Meterlos en el catalogo
     * seria la otra mitad del defecto: un permiso que se otorga sobre algo que no existe.
     *
     * <p>Se excluyen por el <b>valor</b> —{@code __sesion_propia__}, {@code __ciudadano__}— y no
     * por una lista de nombres de clase ni de controladores. Un centinela nuevo se declara en la
     * anotacion y se anade aqui; una clase que use uno de estos dos no hay que censarla en ningun
     * sitio, que es lo que impide que la exclusion se convierta en una lista de excepciones.
     */
    private static final Set<String> CENTINELAS =
            Set.of(RequiereAcceso.SESION_PROPIA, RequiereAcceso.CIUDADANO);

    /**
     * Lo que se leyo del bytecode: los codigos, y <b>cuantas anotaciones se leyeron</b>.
     *
     * <p>La segunda cifra no es decorado. Sin ella, una lectura que no encuentra nada —el paquete
     * raiz mal escrito, las clases sin compilar, la anotacion renombrada— produciria un conjunto
     * vacio, y comparar el catalogo contra el vacio es una prueba <b>sin sujeto</b>: no dice «esta
     * bien», dice «no mire». Se afirma que hay sujeto antes de comparar nada.
     */
    private record Lectura(Set<String> accesos, int anotaciones) {}

    @Test
    @DisplayName("los mismos codigos, en los dos sentidos")
    void losMismosCodigos() {
        Lectura lectura = loQueExigenLosEndpoints();

        assertThat(lectura.anotaciones())
                .as(
                        "no se leyo ni una @RequiereAcceso del bytecode de este sistema. O el"
                                + " paquete raiz dejo de ser el suyo, o las clases no estan"
                                + " compiladas, o la anotacion cambio de nombre: en los tres casos"
                                + " la comparacion de abajo pasaria sin comprobar nada, que no es"
                                + " «esta bien» sino «no se pudo comprobar»")
                .isPositive();

        Set<String> delCatalogo =
                CatalogoDelSistema.opciones().stream()
                        .map(CatalogoDelSistema.Opcion::codigo)
                        .collect(Collectors.toCollection(TreeSet::new));

        // Los dos sentidos, por separado y con su propio motivo. Un unico
        // `containsExactlyElementsOf` los cazaria igual, pero diria «esta lista no es esa otra» y
        // dejaria al que lo lea deducir cual de los dos defectos tiene delante — que no son el
        // mismo ni se arreglan en el mismo sitio.
        Set<String> sinFilaEnElCatalogo = new TreeSet<>(lectura.accesos());
        sinFilaEnElCatalogo.removeAll(delCatalogo);
        assertThat(sinFilaEnElCatalogo)
                .as(
                        "estos accesos los exige un endpoint con @RequiereAcceso y el catalogo no"
                                + " los tiene. La implantacion no siembra su fila en `acceso`, asi"
                                + " que no hay nada que otorgar: el guardia niega SIEMPRE y esos"
                                + " endpoints contestan 403 a todo el mundo, administrador"
                                + " incluido. Es el defecto que RF-122 existe para impedir")
                .isEmpty();

        Set<String> sinEndpointQueLoExija = new TreeSet<>(delCatalogo);
        sinEndpointQueLoExija.removeAll(lectura.accesos());
        assertThat(sinEndpointQueLoExija)
                .as(
                        "estas opciones estan en el catalogo y ningun endpoint de este sistema las"
                                + " exige. La implantacion siembra su fila y la pantalla de permisos"
                                + " ofrece otorgarlas: un permiso sobre algo que no existe, que es"
                                + " ruido y una promesa falsa")
                .isEmpty();
    }

    @Test
    @DisplayName("y ninguna opcion se declara dos veces")
    void ningunaDosVeces() {
        List<String> codigos =
                CatalogoDelSistema.opciones().stream()
                        .map(CatalogoDelSistema.Opcion::codigo)
                        .toList();
        assertThat(codigos).doesNotHaveDuplicates();
    }

    /**
     * Los accesos que exige la capa web, leidos del bytecode de produccion.
     *
     * <p>{@code clasesDeProduccion()} es el mismo importador que usan las reglas de ARQ-04 §2: deja
     * fuera las pruebas y los {@code testFixtures}, de modo que un controlador de mentira escrito
     * para una prueba no puede pedir una fila en el catalogo de verdad.
     */
    private static Lectura loQueExigenLosEndpoints() {
        Set<String> accesos = new TreeSet<>();
        int anotaciones = 0;
        for (JavaClass clase : ReglasDeArquitectura.clasesDeProduccion()) {
            anotaciones += recoger(clase.tryGetAnnotationOfType(RequiereAcceso.class), accesos);
            for (JavaMethod metodo : clase.getMethods()) {
                anotaciones +=
                        recoger(metodo.tryGetAnnotationOfType(RequiereAcceso.class), accesos);
            }
        }
        return new Lectura(accesos, anotaciones);
    }

    /**
     * Un requisito leido: su acceso y sus alternativas.
     *
     * <p>{@code oTambien} entra por el mismo motivo que {@code acceso}, y no es celo: {@code
     * GuardiaDeAcceso} las pregunta al <b>mismo</b> {@code ComprobadorDeAcceso} y contra la misma
     * tabla, asi que una alternativa que el catalogo no tiene es la misma pantalla que nadie puede
     * autorizar — solo que escondida detras de la que si esta. Hoy no la usa ningun endpoint de
     * este sistema; el dia que la use, la guarda ya la mira.
     *
     * @return 1 si habia anotacion, 0 si no; es lo que cuenta el sujeto de la prueba
     */
    private static int recoger(Optional<RequiereAcceso> requisito, Set<String> accesos) {
        if (requisito.isEmpty()) {
            return 0;
        }
        RequiereAcceso exigido = requisito.get();
        anadirSiNoEsCentinela(exigido.acceso(), accesos);
        for (String alternativa : exigido.oTambien()) {
            anadirSiNoEsCentinela(alternativa, accesos);
        }
        return 1;
    }

    private static void anadirSiNoEsCentinela(String acceso, Set<String> accesos) {
        if (!CENTINELAS.contains(acceso)) {
            accesos.add(acceso);
        }
    }
}
