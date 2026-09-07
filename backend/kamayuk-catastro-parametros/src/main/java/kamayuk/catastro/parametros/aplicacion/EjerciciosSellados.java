package kamayuk.catastro.parametros.aplicacion;

import java.util.List;
import kamayuk.catastro.parametros.dominio.CacheDeSnapshots;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Que ejercicios tiene sellados <b>este inquilino</b> (#51).
 *
 * <h2>La pregunta que faltaba, y lo que costaba no tenerla</h2>
 *
 * <p>Este sistema sabia contestar «¿esta sellado el 2026?» —{@code
 * EjercicioParametrizadoController} desde #605— y no sabia contestar «¿cuales lo estan?». Sin esa
 * segunda, toda interfaz que ofrezca elegir ejercicio tiene que escribir la lista a mano, y eso ya
 * paso: {@code catastro-web} la llevo congelada en cuatro literales, de modo que el <b>1 de enero
 * de 2027</b> se habria quedado en 2026 ensenando los cuadros de 2026 y pareciendo correcta — sin
 * error, sin aviso, y con la unica senal de que alguien se fijara en que el desplegable no ofrece
 * el ano en curso. #48 lo tapo derivando la lista del <b>reloj</b>, que arregla la fecha y no el
 * fondo: el reloj no sabe que ejercicios hay sellados.
 *
 * <h2>Sale de la copia local, y por eso contesta con `normativa` caido</h2>
 *
 * <p>La fuente es {@code normativa_conjunto} (`V2`), no una llamada a {@code normativa}. Lo que
 * esta lectura describe es <b>lo que este sistema tiene descargado</b>, que es exactamente el
 * conjunto de ejercicios sobre los que puede contestar cualquier otra cosa; preguntarselo al otro
 * despliegue haria que la lista dejara de existir cuando el se cae, justo cuando el resto del
 * sistema sigue calculando (ADR-0025 §1). El coste esta dicho en el puerto: un ejercicio sellado
 * alli y no descargado aqui todavia no aparece.
 *
 * <h2>Por que es un caso de uso y no una llamada desde el controlador</h2>
 *
 * <p>Porque aqui vive el {@code @Transactional}, y con el el {@code SET LOCAL app.municipalidad_id}
 * que la politica RLS de la tabla consulta. Un controlador que llamara al repositorio correria
 * fuera de transaccion y la consulta <b>no</b> devolveria vacio: reventaria, porque {@code
 * current_setting('app.municipalidad_id')::bigint} sobre la cadena vacia no se puede evaluar (#486)
 * — un 500 donde el criterio pide una lista.
 *
 * <p><b>No escribe nada</b>, asi que no exige observacion (regla 10) ni deja auditoria: leer que
 * ejercicios hay no modifica un dato.
 */
@Service
public class EjerciciosSellados {

    private final CacheDeSnapshots cache;

    public EjerciciosSellados(CacheDeSnapshots cache) {
        this.cache = cache;
    }

    /**
     * Los ejercicios con conjunto en la copia local, del mas reciente al mas antiguo.
     *
     * <p>Una municipalidad recien implantada no tiene ninguno, y eso es una <b>lista vacia</b>: no
     * es un error ni una ausencia que haya que traducir a 404. La ruta existe y la respuesta es
     * «este inquilino no tiene ninguno todavia», que es lo que quien pregunta necesita saber para
     * no ofrecer ningun ejercicio.
     */
    @Transactional(readOnly = true)
    public List<CacheDeSnapshots.ConjuntoCacheado> todos() {
        return cache.conjuntosCacheados();
    }
}
