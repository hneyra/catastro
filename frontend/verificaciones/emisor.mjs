/**
 * Un emisor OIDC de mentira, para los arneses que quieren llegar a las pantallas.
 *
 * <h2>Por que hace falta, y por que la alternativa era peor</h2>
 *
 * Desde la puerta de identidad, `src/main.tsx` no monta nada sin token: se va al emisor y vuelve.
 * Eso deja a los arneses de navegador —que recorren pantallas— delante de un Keycloak que en una
 * vista previa no esta levantado, o sea delante de nada.
 *
 * La salida obvia era apagar la puerta cuando el proxy de datos esta encendido. **No se hizo, y
 * conviene decir por que**: (1) `sin-red.mjs` compila su vista previa con el proxy APAGADO, asi
 * que aun asi haria falta esto; y (2) una puerta de identidad que se apaga sola segun una bandera
 * de construccion es un rodeo que alguien puede encender en un despliegue. El codigo de produccion
 * no tiene ninguna rama para esto: **siempre** se pasa por la puerta, y lo que cambia en un arnes
 * es quien hay al otro lado.
 *
 * <h2>Que emula, que son exactamente dos rutas</h2>
 *
 * 1. `…/protocol/openid-connect/auth` — contesta un **302** de vuelta al `redirect_uri` que la
 *    aplicacion pidio, con el `code` y con el MISMO `state` que llego. Devolver otro `state`
 *    seria emular a un atacante, no a un emisor.
 * 2. `…/protocol/openid-connect/token` — contesta un JSON con `access_token`.
 *
 * Y nada mas. No valida el reto PKCE —no hay a quien enganar— ni emite un JWT que nadie va a
 * verificar: estos arneses no llegan a ningun backend, y el que llega (`territorio.mjs`,
 * `transiciones.mjs`) llega por el proxy de datos, que contesta desde la propia pagina.
 *
 * <h2>Lo que este archivo NO comprueba, dicho en vez de descubierto</h2>
 *
 * **No afirma nada sobre la ida.** Un `redirect_uri` equivocado, un reto que no fuera S256 o un
 * cliente que no existiera pasarian por aqui sin ruido, porque este emisor dice que si a todo.
 * Eso lo mide `verificaciones/identidad.mjs`, contra el paquete construido y sin emisor de
 * mentira que lo tape. Aqui la puerta es un peaje que hay que cruzar para llegar a las pantallas,
 * no lo que se esta midiendo.
 *
 * Lo unico que si afirma es que **se cruzo**: `visitas()` cuenta las idas, y un arnes que quiera
 * puede exigir que no sea cero. Sin eso, el dia que la puerta desapareciera del arranque estos
 * nueve arneses seguirian en verde sobre una aplicacion que ya no pide identidad a nadie.
 */

/** Lo que el emisor de mentira devuelve como token. No es un JWT: nadie lo valida aqui. */
export const TOKEN_DE_MENTIRA = 'token-del-arnes-sin-emisor-de-verdad';

/**
 * Instala las dos rutas sobre un contexto de Playwright.
 *
 * Se instala en el CONTEXTO y no en la pagina porque la ida al emisor es una navegacion de
 * primer nivel: con la ruta puesta solo en la pagina, la vuelta llega antes de que nadie la
 * intercepte cuando el arnes abre una pestana nueva.
 *
 * @returns `{ visitas }`, la cuenta de idas a la puerta.
 */
export async function emisorDeMentira(contexto) {
  let idas = 0;

  await contexto.route('**/protocol/openid-connect/auth*', async (ruta) => {
    idas += 1;
    const pedida = new URL(ruta.request().url());
    const vuelta = new URL(pedida.searchParams.get('redirect_uri') ?? '');
    vuelta.searchParams.set('code', 'codigo-del-arnes');
    /* El MISMO `state` que llego. `canjearSiVuelve` lo compara con el que guardo al salir, y ese
       contraste es lo unico que distingue nuestra vuelta de un codigo que alguien nos hizo
       llegar: devolver otro aqui seria emular a un atacante. */
    vuelta.searchParams.set('state', pedida.searchParams.get('state') ?? '');
    await ruta.fulfill({ status: 302, headers: { location: vuelta.toString() } });
  });

  await contexto.route('**/protocol/openid-connect/token', async (ruta) => {
    await ruta.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        access_token: TOKEN_DE_MENTIRA,
        token_type: 'Bearer',
        expires_in: 300,
      }),
    });
  });

  return { visitas: () => idas };
}
