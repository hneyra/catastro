package kamayuk.catastro.seguridad.aplicacion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kamayuk.catastro.seguridad.dominio.EventoRecibido;

/**
 * Le dice a una persona con nombre que la copia local de la autorizacion esta incompleta (ADR-0026
 * §4, aplicado a ADR-0039).
 *
 * <p>Un evento que no se pudo aplicar deja esta copia diciendo algo que {@code identidad} ya no
 * dice, y <b>ninguna pantalla lo delata</b>: el sintoma es un 403 a alguien a quien se le concedio
 * un permiso, o —peor— una revocacion que aqui no llego. Que el destinatario tenga nombre lo
 * sostiene {@code ResponsableDelConsumidor}, que se lee de la configuracion y no admite estar en
 * blanco.
 *
 * <p>Son dos avisos y no uno, porque son dos cosas distintas y se arreglan de maneras distintas: un
 * evento <b>apartado</b> no se va a aplicar nunca —hay que decidir que se hace con el—, y un
 * <b>pospuesto estancado</b> se aplicaria solo en cuanto llegue su dependencia, asi que lo que hay
 * que averiguar es por que no llega.
 */
public interface AlertaDeEventosSinAplicar {

    /**
     * @param evento el que no se pudo aplicar
     * @param motivo por que, en las palabras del consumidor
     * @param muertosSinExplicar cuantos hay en total en la municipalidad, no solo este: quien
     *     recibe el aviso tiene que ver el estado entero y no el incremento
     */
    void hayUnEventoSinAplicar(EventoRecibido evento, String motivo, long muertosSinExplicar);

    /**
     * Un aviso por corrida —no uno por evento y no uno por vuelta— con los pospuestos que llevan
     * estancados mas de lo que {@link IngestarEventosDeIdentidad#ANTIGUEDAD_QUE_SE_AVISA} admite.
     *
     * <p>Va con la lista entera y no con un recuento: lo primero que hay que saber es de que hechos
     * habla —el tipo, el sujeto y la secuencia—, porque es lo que permite ir al buzon del emisor y
     * ver que falta. La edad va calculada contra {@code ahora} para que el que lo lea no tenga que
     * restar dos instantes.
     *
     * @param viejos los pospuestos que pasan del umbral, en el orden del buzon
     * @param desdeHace el umbral que se aplico
     * @param ahora contra que instante se midio la edad
     */
    void hayPospuestosEstancados(
            List<IngestarEventosDeIdentidad.Pospuesto> viejos, Duration desdeHace, Instant ahora);
}
