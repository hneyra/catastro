/**
 * La copia local de usuarios, grupos y permisos, y quien la trae.
 *
 * <h2>Por que este modulo existe en `catastro` y no solo en {@code identidad}</h2>
 *
 * <p>Lo decidio <b>D-N5</b> (2026-09-03): «usuarios, grupos y permisos se definen en Keycloak; cada
 * sistema guarda una copia local en tabla y su guardia la consulta». Con eso <b>D-19</b> quedo
 * contestada — el {@link kamayuk.catastro.autorizacion.ComprobadorDeAcceso} de cada sistema
 * pregunta a su propia tabla, <b>no a otro sistema por HTTP</b>.
 *
 * <p>La alternativa medida y descartada era preguntarle a otro sistema en cada peticion: el guardia
 * corre en un {@code preHandle}, asi que seria un viaje de red por peticion y, sobre todo, ese otro
 * sistema caido dejaria a {@code catastro} sin poder autorizar nada. Una comprobacion de acceso que
 * depende de la disponibilidad de otro despliegue no es una comprobacion de acceso: es un
 * acoplamiento con forma de politica de seguridad.
 *
 * <h2>Lo que aqui NO hay, y es deliberado</h2>
 *
 * <p>No hay <b>ninguna</b> escritura de administracion de seguridad, ni pantalla que la sirva: las
 * once viven en {@code identidad}, que es el dueno de la autorizacion desde la etapa 4 de ADR-0039.
 * Aqui hay dos cosas: quien <b>lee</b> la copia para autorizar, y quien la <b>trae</b>.
 *
 * <p><b>Y desde la etapa 5 (identidad#5) hay un solo camino por el que esta copia se llena</b>: el
 * consumidor del buzon. Hasta la etapa 4 la implantacion escribia ademas el grupo de
 * administracion, el primer administrador y sus permisos con {@code INSERT} directos —o sea que
 * habia <b>dos origenes para la misma tabla</b>, y el segundo solo agregaba, de modo que un permiso
 * revocado en {@code identidad} volvia a otorgarse en el despliegue siguiente sin que nada lo
 * dijera—. Lo que la implantacion siembra ahora es <b>el catalogo</b> ({@code
 * kamayuk.catastro.seguridad.aplicacion.SembradorDelCatalogo}: {@code modulo_sistema} y {@code
 * acceso}), que es lo unico que este sistema es dueno de decir (RF-122).
 *
 * <p>El resto lo aplica {@code kamayuk.catastro.seguridad.aplicacion.AplicarUnEventoDeIdentidad},
 * cada evento en su propia transaccion; a {@code CorrerElConsumidorDeIdentidad} lo despierta un
 * {@code CronJob} cada cinco minutos, y la implantacion termina con una pasada suya —que desde la
 * etapa 5 <b>no es opcional</b>: sin ella la copia quedaria vacia, y el {@code Job} saldria {@code
 * Complete} sobre una base en la que no puede entrar nadie—. Lo que un evento que no se pudo
 * aplicar deja detras —y a quien se le avisa— esta en {@code identidad_evento_muerto} ({@code V14})
 * y en {@code AlertaDeEventosSinAplicar}. Lo que sigue sin medir es la VENTANA: cuanto tarda un
 * permiso concedido en {@code identidad} en aparecer aqui, que es el AC-5 de identidad#4 y exige
 * las cinco aplicaciones levantadas.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.seguridad;
