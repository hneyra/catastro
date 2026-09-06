package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kamayuk.catastro.SgtmAplicacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Limites entre modulos (ADR-0003, ARQ-01 §4). Bloqueante.
 *
 * <p>Sin esto, "monolito modular" degrada a monolito en pocos meses: nada impide que un contexto
 * llame a las clases internas de otro, y cuando se nota ya hay cincuenta llamadas que desenredar.
 */
@DisplayName("ADR-0003 — Limites entre modulos")
class ModulosTest {

    private static final ApplicationModules MODULOS = ApplicationModules.of(SgtmAplicacion.class);

    @Test
    @DisplayName("los modulos esperados estan detectados")
    void losModulosEsperadosEstanDetectados() {
        List<String> detectados =
                MODULOS.stream().map(m -> m.getIdentifier().toString()).sorted().toList();

        // Si Modulith no detectara ningun modulo, verify() pasaria sin comprobar nada.
        //
        // Heredado del SRTM y verificado alli: un paquete con solo package-info.java
        // NO es un modulo para Modulith, hace falta al menos un tipo.
        //
        // La lista es la de ESTE sistema tras P5C. De los doce contextos del monolito aqui
        // queda uno —`catastro`— y dos puertos; los otros once viven en `rentas` y en `caja`, y
        // nombrarlos aqui seria pedir que se detecte lo que no esta.
        assertThat(detectados)
                .as("los modulos que ya tienen codigo")
                .contains(
                        // La plataforma y lo compartido, que viajan enteros con el sistema.
                        "dominio",
                        "compartido",
                        "plataforma",
                        "persistencia",
                        "auditoria",
                        "autorizacion",
                        // La copia local de usuarios, grupos y permisos (C-7, D-N5): el
                        // `ComprobadorDeAcceso` que el guardia pide y la implantacion que la
                        // siembra. Sin el, el contexto no levanta.
                        "seguridad",
                        "carga",
                        "documentos",
                        "web",
                        // El unico contexto acotado de este sistema. Se llama `nucleo` desde R-N:
                        // «kamayuk-catastro-catastro» repetia el nombre del sistema y la direccion
                        // pidio quitarlo. El identificador que Modulith detecta es el ULTIMO
                        // segmento del paquete, asi que renombrar el paquete lo renombra aqui — y
                        // esta lista es lo que lo puso en rojo al hacerlo.
                        "nucleo",
                        // El contexto acotado del urbanismo (#4). Publica la zona a la que cae
                        // un predio, y no toca ninguna tabla del padron por Java —solo por SQL, en
                        // la misma sentencia que el marco, que es lo que ADR-0034 regla 2 obliga—.
                        "urbano",
                        // La gestion del riesgo de desastres del predio (#5). No depende de
                        // `nucleo` -lo unico que necesita del predio es su poligono, y ese cruce se
                        // resuelve dentro de una sola sentencia SQL-, asi que lo que esta linea
                        // comprueba es que Modulith lo DETECTA: un contexto que no se detecta no lo
                        // revisa `verify()`.
                        "grd",
                        // El hallazgo catastral (ADR-0035, #6). No es la fiscalizacion TRIBUTARIA,
                        // que vive entera en `rentas`.
                        "fiscalizacion",
                        // Y sus dos puertas de salida: el padron de `rentas` y la normativa
                        // cacheada. Los dos son modulos para Modulith aunque sean puertos, y eso
                        // es lo que hace comprobable que `catastro` no toque un tipo interno
                        // suyo: `verify()` lo nombraria.
                        "contribuyentes",
                        "parametros");
    }

    @Test
    @DisplayName("no hay dependencias no declaradas ni ciclos entre modulos")
    void noHayDependenciasNoDeclaradasNiCiclos() {
        MODULOS.verify();
    }
}
