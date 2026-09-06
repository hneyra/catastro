package kamayuk.catastro.seguridad.dominio;

import java.util.List;

/**
 * Las opciones del menu que sirve <b>este</b> sistema (NEG-03, RF-122).
 *
 * <h2>Por que aqui hay una lista y en {@code rentas} se lee un documento</h2>
 *
 * <p>{@code rentas} lee {@code docs/10-negocio/catalogo-de-opciones.md} —las 134 opciones del
 * manual— porque ese documento vive en su repositorio. Aqui no vive: leerlo obligaria a que el
 * build de {@code catastro} dependiera del clon de {@code rentas} <b>en produccion</b>, y no solo
 * en las pruebas. Lo que se hace en su lugar es lo que el inventario del corte ya decia: «cada
 * sistema siembra <b>su parte</b>».
 *
 * <p>Cual es su parte no se elige a ojo: es <b>el conjunto de {@code acceso} que sus propios
 * endpoints declaran</b> con {@code @RequiereAcceso}. Y eso no se deja a la buena memoria — {@code
 * CatalogoDelSistemaTest} recorre {@code src/main}, junta los valores de la anotacion y exige que
 * sean exactamente estos. Una opcion de mas seria un permiso que nadie puede usar; una de menos,
 * una pantalla a la que no se le puede dar permiso, que es el defecto que RF-122 existe para
 * impedir.
 *
 * <p>El nombre y el modulo estan transcritos de {@code
 * rentas/docs/10-negocio/catalogo-de-opciones.md}, que sigue siendo la fuente del manual. Se copian
 * y no se derivan por lo dicho arriba, y por eso la prueba compara <b>codigos</b>: es lo unico que
 * este sistema puede comprobar por si mismo.
 *
 * <p>Es una clase de dominio: sin Spring, sin base de datos y sin reloj (regla 7).
 */
public final class CatalogoDelSistema {

    private CatalogoDelSistema() {}

    /** Una opcion del menu, con el modulo al que pertenece. */
    public record Opcion(String moduloCodigo, String moduloNombre, String codigo, String nombre) {}

    private static final List<Opcion> OPCIONES =
            List.of(
                    new Opcion(
                            "CATASTRO",
                            "Catastro",
                            "ficha_urbana",
                            "Ficha catastral urbana individual"),
                    new Opcion(
                            "CATASTRO", "Catastro", "ficha_economica", "Ficha catastral economica"),
                    new Opcion("CATASTRO", "Catastro", "ficha_bienes", "Ficha de bienes comunes"),
                    new Opcion("CATASTRO", "Catastro", "ficha_rural", "Ficha catastral rural"),
                    new Opcion(
                            "CATASTRO",
                            "Catastro",
                            "consulta_fichas",
                            "Consulta de fichas catastrales"),
                    new Opcion(
                            "CATASTRO",
                            "Catastro",
                            "actualizacion_catastro",
                            "Actualizacion del catastro"),
                    new Opcion(
                            "CATASTRO",
                            "Catastro",
                            "ficha_contribuyente_reporte",
                            "Reporte de ficha del contribuyente"),
                    new Opcion("CATASTRO", "Catastro", "aranceles", "Aranceles de terreno"),
                    new Opcion(
                            "CATASTRO",
                            "Catastro",
                            "valores_unitarios",
                            "Valores unitarios de edificacion"),
                    new Opcion("CATASTRO", "Catastro", "depreciacion", "Tabla de depreciacion"),
                    // #4 — la zonificacion. Es la opcion que declara `ZonificacionController`
                    // con `@RequiereAcceso`, y `CatalogoDelSistemaTest` exige que las dos listas
                    // sean el mismo conjunto: sin esta fila, el guardia negaria una pantalla a la
                    // que nadie podria dar permiso (RF-122).
                    new Opcion("CATASTRO", "Catastro", "zonificacion", "Zonificacion urbana"),
                    // #5. Es una de las dos opciones de esta lista que no estan transcritas del
                    // catalogo del manual —la otra es `fiscalizacion_catastral`, de #6—, y se dice:
                    // el SGTM de Sullana no sabe lo que es una zona de
                    // riesgo ni un ITSE, asi que no hay de donde copiarla. El codigo se elige aqui
                    // y el nombre tambien; lo que no se elige es que exista, porque el endpoint la
                    // declara y `CatalogoDelSistemaTest` compara los dos conjuntos: sin esta fila
                    // el guardia negaria una pantalla que nadie puede autorizar (RF-122).
                    new Opcion(
                            "CATASTRO",
                            "Catastro",
                            "gestion_del_riesgo",
                            "Gestion del riesgo de desastres del predio"),
                    // Con #6 (ADR-0035). Es UNA opcion y no cinco: el recorrido —campania,
                    // candidatos, las dos compuertas, evidencia y acta— lo hace la misma area con
                    // los mismos permisos, y lo que separa quien puede hacer que dentro de el es el
                    // PRIVILEGIO (lectura para la cola, modificacion para las compuertas, ejecucion
                    // para la deteccion), no una opcion de menu por paso.
                    new Opcion(
                            "CATASTRO",
                            "Catastro",
                            "fiscalizacion_catastral",
                            "Fiscalizacion catastral: hallazgos y actas"),
                    new Opcion(
                            "CONSULTAS",
                            "Consultas",
                            "consulta_resumen_predial",
                            "Consulta resumen predial-arbitrios"));

    /** Las opciones de este sistema, en el orden del catalogo del manual. */
    public static List<Opcion> opciones() {
        return OPCIONES;
    }
}
