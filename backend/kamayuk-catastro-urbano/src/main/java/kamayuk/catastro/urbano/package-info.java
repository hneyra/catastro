/**
 * Urbanismo: la zonificacion vigente, sus parametros urbanisticos, la seccion normativa de las vias
 * y las habilitaciones urbanas (#4).
 *
 * <p>Este paquete <b>es la API publica del modulo</b> (ARQ-01 §4.1): lo que esta aqui lo puede
 * importar otro contexto acotado, y {@code .dominio}, {@code .aplicacion} e {@code
 * .infraestructura} no. Lo comprueba {@code ModulosTest} con {@code ApplicationModules.verify()}.
 *
 * <p><b>Invariante:</b> dos planes vigentes a la vez no cubren el mismo suelo. Lo rechaza el motor
 * —{@code zonificacion_planes_no_se_pisan}, {@code V7}— y no el codigo, porque una comprobacion
 * escrita en Java se salta con un {@code INSERT} de un cargador.
 *
 * <p><b>Lo que este modulo NO hace:</b> no decide si un giro es compatible con una zona. Publica la
 * zona; la compatibilidad es dato de {@code rentas} ({@code ciiu.zonificacion_compatible}) y la
 * licencia la emite {@code rentas}. Es la frontera de ADR-0024.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.urbano;
