package kamayuk.catastro.grd.dominio;

import java.util.Locale;

/**
 * Los cuatro niveles de riesgo, <b>los mismos que {@code rentas}</b> (#5).
 *
 * <p>Es literalmente el vocabulario de {@code kamayuk.rentas.licencias.dominio.RiesgoItse} y de la
 * columna {@code ciiu.riesgo_itse}, y esa coincidencia no es estetica: alli se registra el nivel
 * que un GIRO exige y aqui el que una ZONA tiene y el que un CERTIFICADO acredita. El dia que
 * alguien cruce las dos cosas —«este giro exige ALTO y el local acredita MEDIO»— tiene que poder
 * compararlas sin traducir. Una traduccion entre dos listas de cuatro valores se escribe mal una
 * vez y no la ve nadie, porque las dos columnas siguen siendo texto valido.
 *
 * <p><b>No hay valor por omision y no lo habra.</b> Si la carta de peligro no dice el nivel, la
 * fila se rechaza: inventarle BAJO autorizaria por descuido lo que nadie clasifico, que es
 * exactamente lo que el javadoc de {@code RiesgoItse} ya dejo dicho del otro lado.
 *
 * <p>El orden de las constantes es de menor a mayor gravedad, y {@link #esMasGraveQue} lo usa.
 * Comparar por el nombre daria «ALTO &lt; BAJO» y «MEDIO &lt; MUY_ALTO», que son las dos respuestas
 * equivocadas.
 */
public enum NivelDeRiesgo {
    BAJO,
    MEDIO,
    ALTO,
    MUY_ALTO;

    public static NivelDeRiesgo porNombre(String nombre) {
        String limpio = nombre.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return valueOf(limpio);
        } catch (IllegalArgumentException noExiste) {
            throw new IllegalArgumentException(
                    "El nivel de riesgo va entre BAJO, MEDIO, ALTO y MUY_ALTO: llego '"
                            + nombre
                            + "'");
        }
    }

    public boolean esMasGraveQue(NivelDeRiesgo otro) {
        return ordinal() > otro.ordinal();
    }
}
