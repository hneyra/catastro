/**
 * La copia local de usuarios, grupos y permisos, y lo que la siembra.
 *
 * <h2>Por que este modulo existe en `catastro` y no solo en {@code rentas}</h2>
 *
 * <p>Lo decidio <b>D-N5</b> (2026-09-03): «usuarios, grupos y permisos se definen en Keycloak; cada
 * sistema guarda una copia local en tabla y su guardia la consulta». Con eso <b>D-19</b> quedo
 * contestada — el {@link kamayuk.catastro.autorizacion.ComprobadorDeAcceso} de cada sistema
 * pregunta a su propia tabla, <b>no a otro sistema por HTTP</b>.
 *
 * <p>La alternativa medida y descartada era preguntarle a {@code rentas} en cada peticion: el
 * guardia corre en un {@code preHandle}, asi que seria un viaje de red por peticion y, sobre todo,
 * {@code rentas} caido dejaria a {@code catastro} sin poder autorizar nada. Una comprobacion de
 * acceso que depende de la disponibilidad de otro despliegue no es una comprobacion de acceso: es
 * un acoplamiento con forma de politica de seguridad.
 *
 * <h2>Lo que aqui NO hay, y es deliberado</h2>
 *
 * <p>No hay pantallas de administracion de seguridad. Las nueve escrituras de grupos, usuarios,
 * miembros y permisos viven <b>solo en {@code rentas}</b> (ADR-0030 §3: los cuatro frontends leen
 * {@code rentas/api/v1/sesion/permisos}). Aqui hay dos cosas: quien <b>lee</b> la copia para
 * autorizar, y quien la <b>siembra</b> al implantar la municipalidad.
 *
 * <p><b>El hueco que esta declaracion tuvo desde C-7 se cierra en la etapa 4 de ADR-0039</b>
 * (identidad#4). Hasta entonces la copia la escribia la implantacion y nadie mas, y un permiso
 * otorgado despues <b>no llegaba</b>. Desde la etapa 4 la copia la mantiene el buzon de {@code
 * identidad}: {@code kamayuk.catastro.seguridad.aplicacion.AplicarUnEventoDeIdentidad} aplica cada
 * evento en su propia transaccion, {@code CorrerElConsumidorDeIdentidad} lo despierta un {@code
 * CronJob} cada cinco minutos, y la implantacion termina con una pasada suya. Lo que un evento que
 * no se pudo aplicar deja detras —y a quien se le avisa— esta en {@code identidad_evento_muerto}
 * ({@code V14}) y en {@code AlertaDeEventosSinAplicar}. Lo que sigue sin medir es la VENTANA:
 * cuanto tarda un permiso concedido en {@code identidad} en aparecer aqui, que es el AC-5 de
 * identidad#4 y exige las cinco aplicaciones levantadas.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.seguridad;
