/// <reference types="vite/client" />

/**
 * Las variables de entorno que esta interfaz lee.
 *
 * Vite las resuelve AL COMPILAR: no son configuracion de ejecucion, y cambiarlas
 * exige reconstruir. Por eso `VITE_CATASTRO_API` vale una RUTA del mismo origen
 * y nunca una URL absoluta.
 */
interface ImportMetaEnv {
  /** La raiz de la API. Por omision `/catastro/api/v1`, que es `Api.RAIZ`. */
  readonly VITE_CATASTRO_API?: string;
  /** El token con el que se firma cada peticion, mientras no haya puerta de sesion. */
  readonly VITE_CATASTRO_TOKEN?: string;
  /** `'false'` apaga el proxy de datos y la rama entera desaparece del paquete. */
  readonly VITE_CATASTRO_PROXY_DE_DATOS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
