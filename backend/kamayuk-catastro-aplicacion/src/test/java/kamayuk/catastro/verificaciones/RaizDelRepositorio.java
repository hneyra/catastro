package kamayuk.catastro.verificaciones;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Donde esta el repositorio, para las pruebas que leen fuera del build de Gradle.
 *
 * <p>Existe porque ya lo buscaban dos pruebas por su cuenta —el contrato y las formas— y ahora son
 * tres: tres recorridos escritos por separado empiezan iguales y acaban discrepando en el caso
 * raro, y entonces una prueba lee un archivo y otra lee otro.
 *
 * <h2>El ancla estaba rota, y se descubrio al usarla (#7)</h2>
 *
 * <p>Hasta #7 subia hasta encontrar {@code docs/50-api/openapi/rentas-v1.yaml}, <b>que en este
 * repositorio no existe</b>: ese contrato es de {@code rentas} y se quedo alli en P5C. O sea que
 * esta clase no podia devolver nada — lanzaba «No se encontro la raiz del repositorio»—, y nadie lo
 * vio porque al separarse los cinco repositorios se quedo <b>sin un solo llamador</b>. Es la misma
 * forma de defecto que #4 midio con {@code Files.isDirectory(".git")} en un <i>worktree</i>: no es
 * un rojo, es una guarda que no se puede correr, que es peor porque no habla de lo que vigila.
 *
 * <p>El ancla nueva es {@code backend/settings.gradle.kts}, que es la misma que usa {@code
 * ConfiguracionDeLasVerificaciones.raizDelCodigo()} de la libreria compartida —salvo que aquella
 * devuelve {@code backend/} y esta la raiz del repositorio, que es lo que hace falta para nombrar
 * un archivo en un mensaje de error como lo nombraria un {@code git status}—. Un archivo que
 * ninguna extraccion se puede llevar: si algun dia se lleva el build de Gradle, este repositorio ya
 * no tiene backend.
 */
final class RaizDelRepositorio {

    private RaizDelRepositorio() {}

    static Path ruta() {
        Path actual = Path.of("").toAbsolutePath();
        while (actual != null) {
            if (Files.exists(actual.resolve("backend/settings.gradle.kts"))) {
                return actual;
            }
            actual = actual.getParent();
        }
        throw new IllegalStateException(
                "No se encontro la raiz del repositorio: ningun ancestro de "
                        + Path.of("").toAbsolutePath()
                        + " tiene backend/settings.gradle.kts");
    }
}
