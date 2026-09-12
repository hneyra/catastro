/**
 * Lo que la interfaz NO puede saber cuando se construye.
 *
 * <h2>El problema, medido y no supuesto</h2>
 *
 * `vite build` sustituye cada `import.meta.env.VITE_*` por su valor **al construir** y despues
 * pliega lo que dependa de el: lo que queda en el paquete es una constante, no una lectura. Es la
 * propiedad de la que vive `main.tsx` para sacar el proxy de datos del paquete, y ahi es justo lo
 * que se quiere. Aqui es el defecto: la URL del emisor OIDC no es la misma en el puesto de quien
 * desarrolla (`localhost:8181`), en la marcha blanca y en la municipalidad, y una URL horneada
 * convierte la imagen en la imagen **de un ambiente**.
 *
 * <h2>Por que no una imagen por ambiente, que es lo que hizo el monolito</h2>
 *
 * `sgtm` etiquetaba la suya `sgtm-interfaz:${ambiente}-${version}`. Aqui eso choca con la
 * etiqueta que `publicar-imagenes.yml` ya usa para las tres imagenes de este repositorio —**el
 * `sha`**— y se pierden dos cosas a la vez: que la etiqueta resuelva contra un `git log` («que
 * corre en la municipalidad» deja de tener respuesta) y que lo verificado en la marcha blanca sea
 * **el mismo artefacto** que se promueve. Con una imagen por ambiente no se promueve nada: se
 * vuelve a construir.
 *
 * <h2>La salida: un archivo que se sirve, no un valor que se hornea</h2>
 *
 * El ambiente entra **al arrancar el contenedor** y no al construir la imagen. `index.html` carga
 * `configuracion.js` —un guion clasico, y por tanto antes que el modulo, que va diferido— y ese
 * archivo deja un objeto en `window`. En el cluster lo entrega un `ConfigMap` montado sobre el que
 * la imagen trae; en `yarn dev` y en la imagen sin montar, el que viaja en `public/` esta VACIO a
 * proposito y la cadena cae al escalon siguiente.
 *
 * <h2>Los tres escalones, y por que el ultimo no es «fallar»</h2>
 *
 * `servida` -> `de la construccion` -> `por omision`. El ultimo son las senias de la instalacion
 * local, que es donde corre `yarn dev` y donde corren los arneses: fallar ahi obligaria a que todo
 * arnes montara un `window.__KAMAYUK_CATASTRO__` para dibujar una pantalla que no entra a ninguna
 * puerta. Lo que **no** hace la cadena es tratar la cadena vacia como un valor: un `ConfigMap` con
 * la llave puesta y el valor en blanco es un error de despliegue, y heredar de el una URL vacia
 * daria un rebote a una ruta de la propia interfaz, que `try_files` contesta con **200 y el
 * `index.html` dentro**. Es el «200 que miente» de `nginx.conf` aplicado a la puerta de identidad:
 * por eso una cadena en blanco cuenta como ausencia.
 */

/** Las senias que se resuelven al arrancar y no al construir. */
export type ClaveDeConfiguracion = 'oidcRealm' | 'oidcCliente' | 'oidcAlcance';

declare global {
  interface Window {
    /**
     * Lo que deja `configuracion.js`. Opcional en el tipo porque de verdad puede no estar: un
     * arnes puede montar la aplicacion sin cargar ningun guion clasico.
     */
    __KAMAYUK_CATASTRO__?: Partial<Record<ClaveDeConfiguracion, string>>;
  }
}

/**
 * El tercer escalon: la instalacion local.
 *
 * `kamayuk-backoffice` y `localhost:8181` son los que hacen que `yarn dev` entre por la puerta sin
 * configurar nada. Son los mismos que usa `rentas`, y no por copiar: **el realm es uno solo para
 * los cinco sistemas** (ADR-0005, ADR-0030 §3), asi que un realm distinto aqui seria un segundo
 * emisor que nadie levanta.
 */
const POR_OMISION: Record<ClaveDeConfiguracion, string> = {
  oidcRealm: 'http://localhost:8181/realms/kamayuk',
  oidcCliente: 'kamayuk-backoffice',
  oidcAlcance: 'openid profile',
};

/**
 * El segundo escalon: lo que Vite horneo al construir.
 *
 * Se escriben las tres lecturas **literales**, una por linea, y no con un indice calculado: Vite
 * sustituye `import.meta.env.VITE_ALGO` reconociendolo en el texto, asi que
 * `import.meta.env[clave]` no se sustituiria y las tres saldrian `undefined` en el paquete —en
 * silencio, porque la cadena tiene un escalon mas debajo—.
 */
const DE_LA_CONSTRUCCION: Record<ClaveDeConfiguracion, string | undefined> = {
  oidcRealm: import.meta.env.VITE_CATASTRO_OIDC_REALM,
  oidcCliente: import.meta.env.VITE_CATASTRO_OIDC_CLIENTE,
  oidcAlcance: import.meta.env.VITE_CATASTRO_OIDC_ALCANCE,
};

/** Una cadena en blanco no es un valor: es una llave puesta sin rellenar. Ver la cabecera. */
function siTieneAlgo(valor: string | undefined): string | undefined {
  const limpio = valor?.trim();
  return limpio === undefined || limpio === '' ? undefined : limpio;
}

/** Lo que sirve el contenedor, si es que sirve algo. */
function servida(clave: ClaveDeConfiguracion): string | undefined {
  if (typeof window === 'undefined') return undefined;
  return siTieneAlgo(window.__KAMAYUK_CATASTRO__?.[clave]);
}

/**
 * El valor de una senia, resuelto por los tres escalones.
 *
 * Se lee **en tiempo de ejecucion** a proposito: si esto se resolviera en una constante de modulo,
 * quien la importara la congelaria en el orden de carga de los modulos, que es exactamente el
 * defecto contra el que existe este archivo.
 */
export function configuracion(clave: ClaveDeConfiguracion): string {
  return servida(clave) ?? siTieneAlgo(DE_LA_CONSTRUCCION[clave]) ?? POR_OMISION[clave];
}

/** De donde salio el valor. Existe para que un arnes pueda distinguir escalon de escalon. */
export function procedencia(clave: ClaveDeConfiguracion): 'servida' | 'construccion' | 'omision' {
  if (servida(clave) !== undefined) return 'servida';
  if (siTieneAlgo(DE_LA_CONSTRUCCION[clave]) !== undefined) return 'construccion';
  return 'omision';
}
