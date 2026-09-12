import { StrictMode } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { Puerta } from './shell/Puerta';
import {
  canjearSiVuelve,
  entrar,
  hayPuerta,
  olvidarLaParada,
  puedeIrALaPuerta,
  token,
  vieneDeSalir,
} from './api/identidad';
import './ds/global.css';

/**
 * El proxy de datos se enciende ANTES de montar React.
 *
 * Y se carga con `import()` dinamico a proposito: apagado, la rama entera —el proxy, sus fixtures
 * y el padron de demostracion— **no viaja en el paquete**. Con un `import` estatico viajaria
 * siempre, y una interfaz de produccion llevaria dentro 23 predios inventados esperando a que
 * alguien cambiara una bandera.
 */
const PROXY = import.meta.env.VITE_CATASTRO_PROXY_DE_DATOS !== 'false';

/**
 * El arranque: primero quien pregunta, despues quien contesta, y solo entonces quien dibuja.
 *
 * <h2>El orden es el criterio, no un detalle</h2>
 *
 * Son tres pasos y ninguno se puede adelantar:
 *
 *   1. **El canje, si venimos del emisor.** La URL trae un `?code=` que hay que cambiar por un
 *      token antes de nada. Es una ida a la red, y por eso este arranque es `async`.
 *   2. **La puerta, si no hay token.** Ver abajo.
 *   3. **El proxy de datos, si la bandera lo pide** — y DESPUES del canje, no antes. El proxy
 *      **sustituye `fetch`** (ADR-0010), asi que instalarlo primero pondria el canje —una
 *      peticion al emisor, que el proxy no conoce— en manos de un interceptor que existe para
 *      contestar rutas de este backend.
 *
 * Solo entonces se monta. React monta y las pantallas piden datos en su primer efecto: montar
 * antes de instalar el proxy dejaria la primera lectura saliendo al `fetch` de verdad, que en una
 * vista previa contesta el `index.html` con un 200 —no un error, una pagina— donde la pantalla
 * espera JSON.
 *
 * <h2>NO hay ninguna rama que apague la puerta, y es deliberado</h2>
 *
 * Lo comodo habria sido saltarsela cuando el proxy de datos esta encendido: con el proxy no hay
 * servidor al que presentarle nada. No se hizo por dos motivos. `sin-red.mjs` compila su vista
 * previa con el proxy **apagado**, asi que aun asi habria que resolver lo mismo; y una puerta de
 * identidad que se apaga sola segun una bandera de construccion es un rodeo que alguien puede
 * encender en un despliegue. Lo que hacen los arneses es poner al otro lado un emisor de mentira
 * (`verificaciones/emisor.mjs`), que es cambiar el decorado y no el camino.
 *
 * <h2>La ida a la puerta la decide el arranque, no la pantalla</h2>
 *
 * Sin token no hay nada que ensenar, asi que se va al emisor directamente en vez de montar la
 * aplicacion para que ella descubra el 401. La diferencia se ve: con la sesion de Keycloak viva,
 * ir a la puerta va y vuelve sin dibujar nada; montar primero ensenaria un error de identidad **a
 * alguien que si esta identificado**, durante el tiempo que tarda la ida.
 *
 * Con dos frenos, y los dos hacen falta: el **tope de tres idas**, porque un canje que falla
 * siempre —un `redirect_uri` mal declarado, que es `rentas`#71— convierte esto en un rebote
 * infinito; y la **marca de salida**, porque `post_logout_redirect_uri` trae de vuelta sin token y
 * sin ella quien acaba de cerrar sesion se encuentra DENTRO OTRA VEZ con la misma cuenta.
 *
 * Cuando uno de los dos frena, o cuando el emisor dijo que no, se dibuja `Puerta` con el motivo.
 * Una pagina en blanco con la causa escrita solo en la consola no es una respuesta.
 */
async function arrancar() {
  const raiz = document.getElementById('raiz');
  if (!raiz) throw new Error('Falta el nodo #raiz en index.html');
  const montar = (que: ReactNode) => createRoot(raiz).render(<StrictMode>{que}</StrictMode>);

  const volverAIdentificarse = () => {
    olvidarLaParada();
    void entrar();
  };

  const vuelta = await canjearSiVuelve();
  if (vuelta.estado === 'fallo') {
    montar(
      <Puerta
        motivo={vuelta.motivo}
        detalle={vuelta.detalle}
        remedio="Vuelve a identificarte. Si se repite, avisa a quien administra el sistema con la hora exacta: el emisor guarda el motivo de cada rechazo."
        ofreceVolver
        alVolverAIdentificarse={volverAIdentificarse}
      />,
    );
    return;
  }

  if (token() === null) {
    if (!hayPuerta()) {
      montar(
        <Puerta
          motivo="Este navegador no puede identificarte"
          detalle="No expone «crypto.subtle», que es lo que calcula el reto PKCE. Pasa en navegadores muy antiguos y, sobre todo, cuando la página se sirve por HTTP sin cifrar: fuera de un origen seguro el navegador esconde esa parte de la API."
          remedio="Abre esta pantalla por HTTPS. Si ya es HTTPS, hace falta un navegador actualizado; volver a intentarlo aquí daría lo mismo."
          ofreceVolver={false}
          alVolverAIdentificarse={volverAIdentificarse}
        />,
      );
      return;
    }
    if (vieneDeSalir()) {
      montar(
        <Puerta
          motivo="Se cerró la sesión"
          detalle="La sesión de este puesto se cerró. No se vuelve a entrar solo: con la sesión del emisor todavía viva, eso dejaría dentro a la misma cuenta sin que nadie hubiera tecleado nada."
          remedio="Pulsa «Volver a identificarse» para entrar, con esta cuenta o con otra."
          ofreceVolver
          alVolverAIdentificarse={volverAIdentificarse}
        />,
      );
      return;
    }
    if (!puedeIrALaPuerta()) {
      montar(
        <Puerta
          motivo="La entrada no se completó"
          detalle="Se fue al emisor tres veces seguidas y ninguna volvió con una sesión utilizable. Tres idas sin canjear son un bucle, no mala suerte, así que se para aquí en vez de seguir rebotando."
          remedio="Puedes volver a intentarlo. Si vuelve a pararse, lo que suele fallar es la URI de retorno declarada en el cliente del emisor: quien administra el sistema la puede comparar con la dirección de esta página."
          ofreceVolver
          alVolverAIdentificarse={volverAIdentificarse}
        />,
      );
      return;
    }
    await entrar();
    return;
  }

  if (PROXY) {
    const { instalarProxyDeDatos } = await import('./simulado/proxy');
    instalarProxyDeDatos();
  }
  montar(<App />);
}

void arrancar();
