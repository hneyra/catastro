package kamayuk.catastro.seguridad.dominio;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Los siete tipos de evento que {@code identidad} publica por su buzon (ADR-0039, etapa 2), tal
 * como este sistema los sabe aplicar.
 *
 * <p>Declarar no es aplicar: lo que hace {@link #declarado(String)} es traducir el nombre que el
 * emisor escribio a una constante, y un nombre que no esta aqui devuelve {@code null} en vez de
 * lanzar. Quien decide que se hace con el es el consumidor, evento a evento —y lo que decide es
 * apartarlo a la cola de muertos con su motivo, porque un tipo que este sistema no conoce es un
 * evento que no va a poder aplicar nunca, y acusarlo sin apartarlo lo perderia (identidad#4)—.
 *
 * <p>La lista es la de {@code kamayuk.identidad.nucleo.dominio.TipoDeEventoDeIdentidad}, copiada:
 * el enumerado del emisor no cruza la frontera de sistema, y lo que ata las dos listas es el
 * contrato que este repositorio publica en {@code docs/50-api/contratos-que-consume/identidad.json}
 * y el propio consumidor, que se para nombrando el octavo tipo el dia que llegue.
 */
public enum TipoDeEventoDeIdentidad {
    USUARIO_DADO_DE_ALTA,
    USUARIO_MODIFICADO,
    GRUPO_DADO_DE_ALTA,
    GRUPO_MODIFICADO,
    MIEMBRO_AFILIADO,
    MIEMBRO_DESAFILIADO,
    PERMISO_FIJADO;

    /** El tipo que corresponde al nombre publicado, o {@code null} si este sistema no lo conoce. */
    public static @Nullable TipoDeEventoDeIdentidad declarado(String publicado) {
        String nombre = publicado.strip().toUpperCase(Locale.ROOT);
        for (TipoDeEventoDeIdentidad tipo : values()) {
            if (tipo.name().equals(nombre)) {
                return tipo;
            }
        }
        return null;
    }
}
