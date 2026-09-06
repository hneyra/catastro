package kamayuk.catastro.fiscalizacion.dominio;

/**
 * Si el hallazgo sigue en pie.
 *
 * <p>Un hallazgo <b>no se borra</b> (regla 4): se deja sin efecto, con su observacion. Y dejarlo
 * sin efecto no borra su acta —el acta es INMUTABLE—: lo que hace es que ese hallazgo deje de
 * habilitar ningun acto nuevo.
 */
public enum EstadoDelHallazgo {

    /**
     * En pie. Habilita el acto —versionar la ficha, inscribir el predio— que ejecuta una persona.
     */
    FIRME,

    /** Retirado con su motivo. Ya no habilita nada, y su acta se queda donde esta. */
    DEJADO_SIN_EFECTO
}
