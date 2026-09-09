package kamayuk.catastro.seguridad.aplicacion;

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
 */
public interface AlertaDeEventosSinAplicar {

    /**
     * @param evento el que no se pudo aplicar
     * @param motivo por que, en las palabras del consumidor
     * @param muertosSinExplicar cuantos hay en total en la municipalidad, no solo este: quien
     *     recibe el aviso tiene que ver el estado entero y no el incremento
     */
    void hayUnEventoSinAplicar(EventoRecibido evento, String motivo, long muertosSinExplicar);
}
