package kamayuk.catastro.verificaciones;

import java.util.List;
import kamayuk.comun.verificaciones.ConfiguracionDeLasVerificaciones.CruceConsentido;

/**
 * Las consultas de este sistema que leen una tabla de otro, consentidas con su issue.
 *
 * <h2>Esta vacia, y tiene que estarlo (P5C)</h2>
 *
 * <p>No es que no se haya escrito: es que <b>este sistema no cruza ninguna frontera en SQL</b>, y
 * eso se comprueba solo. {@code ningunCruceConsentidoSobra} vuelve a escanear el arbol entero
 * <b>sin</b> esta lista y exige que cada entrada siga eximiendo un cruce de verdad, asi que una
 * entrada heredada del monolito pondria la prueba en rojo.
 *
 * <p>Las tres cosas que en el monolito habrian entrado aqui no entran, y por tres motivos distintos
 * que conviene tener escritos:
 *
 * <ul>
 *   <li>{@code ValuacionRepositoryJdbc} leia {@code valor_unitario_edificacion}, {@code
 *       depreciacion} y {@code conjunto_parametro_detalle} — {@code PENDIENTE-CRUCE-02}. Esas
 *       tablas se fueron a {@code normativa} en P5B y hoy lee la <b>copia local sellada</b> que
 *       crea {@code V2} de este repositorio. El identificador se cerro alli.
 *   <li>La grilla de fichas y el reporte del contribuyente <b>nunca</b> leyeron {@code
 *       contribuyente} en SQL: el limite entre contextos acotados ya obligaba a preguntarselo al
 *       puerto {@code DirectorioDeContribuyentes} (ARQ-01 §4), y por eso al pasar a ser otro
 *       sistema no hubo una sola consulta que reescribir. Lo unico que cambio es quien implementa
 *       el puerto: ahora un cliente HTTP.
 *   <li>{@code titularidad.contribuyente_id} se queda aqui y sigue siendo una columna de este
 *       sistema. Lo que se perdio es su clave foranea, que el generador del baseline ya dejo
 *       comentada como {@code [CRUZA LA FRONTERA]}: es literalmente el costo que ADR-0029 nombra,
 *       «se paga una clave foranea por una invariante».
 * </ul>
 *
 * <p>Los cuatro identificadores que siguen abiertos —{@code PENDIENTE-CRUCE-01}, {@code -04},
 * {@code -05} y {@code -06}— son de {@code rentas} y de {@code caja}, y su lista vive alli. La
 * clase se conserva aunque este vacia porque la configuracion la nombra, y una lista que existe y
 * esta a cero dice mas que una que no existe: dice que se midio.
 */
final class CrucesConsentidosDelSgtm {

    private CrucesConsentidosDelSgtm() {}

    static final List<CruceConsentido> LISTA = List.of();
}
