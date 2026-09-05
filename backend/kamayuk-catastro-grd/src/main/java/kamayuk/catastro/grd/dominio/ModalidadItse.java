package kamayuk.catastro.grd.dominio;

import java.util.Locale;

/**
 * Si la inspeccion fue <b>antes</b> de operar o <b>despues</b> (#5).
 *
 * <p>De que nivel de riesgo depende cual toca lo dice la norma, y <b>este sistema no lo calcula</b>
 * —es la consecuencia, y la consecuencia es de quien emite (ADR-0024)—. Lo que se registra aqui es
 * cual fue, que es un hecho del certificado que alguien tiene en la mano.
 */
public enum ModalidadItse {
    PREVIA,
    POSTERIOR;

    public static ModalidadItse porNombre(String nombre) {
        String limpio = nombre.strip().toUpperCase(Locale.ROOT);
        try {
            return valueOf(limpio);
        } catch (IllegalArgumentException noExiste) {
            throw new IllegalArgumentException(
                    "La modalidad de la ITSE va entre PREVIA y POSTERIOR: llego '" + nombre + "'");
        }
    }
}
