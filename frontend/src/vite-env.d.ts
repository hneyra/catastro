/// <reference types="vite/client" />

/**
 * Las variables de entorno que esta interfaz lee.
 *
 * Vite las resuelve AL COMPILAR: no son configuracion de ejecucion, y cambiarlas
 * exige reconstruir. Por eso `VITE_CATASTRO_API` vale una RUTA del mismo origen
 * y nunca una URL absoluta.
 *
 * **Las tres de OIDC son el escalon del medio y casi nunca se usan.** Lo que el
 * ambiente decide se sirve al arrancar el contenedor, no se hornea: ver
 * `src/api/configuracion.ts`. Estan declaradas para que una imagen construida a
 * mano —una vista previa contra otro emisor— no tenga que montar un `ConfigMap`.
 *
 * **`VITE_CATASTRO_TOKEN` ya no existe, y es una ausencia deliberada.** Vite la
 * horneaba en `assets/index-*.js`, que es el artefacto que se publica; el token
 * ahora vive en memoria y sale de la puerta de identidad.
 */
interface ImportMetaEnv {
  /** La raiz de la API. Por omision `/catastro/api/v1`, que es `Api.RAIZ`. */
  readonly VITE_CATASTRO_API?: string;
  /** `'false'` apaga el proxy de datos y la rama entera desaparece del paquete. */
  readonly VITE_CATASTRO_PROXY_DE_DATOS?: string;
  /** El realm del emisor OIDC, con su URL completa. Ver `src/api/configuracion.ts`. */
  readonly VITE_CATASTRO_OIDC_REALM?: string;
  /** El cliente publico con el que entra esta interfaz. Por omision `kamayuk-backoffice`. */
  readonly VITE_CATASTRO_OIDC_CLIENTE?: string;
  /** El alcance que se pide. Por omision `openid profile`; nada que pida un `refresh_token`. */
  readonly VITE_CATASTRO_OIDC_ALCANCE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
