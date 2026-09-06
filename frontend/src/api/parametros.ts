/**
 * El ejercicio sellado: la copia local del conjunto de parametros de `normativa`.
 *
 * `catastro` **no sella un valor normativo** —eso es `normativa`—: aqui se
 * consume un conjunto ya sellado, y esta lectura contesta si el ejercicio tiene
 * uno y cual.
 */
import { camino, solicitar } from './cliente';

export const RUTAS = {
  ejercicio: '/seguridad/parametros/ejercicios/{ejercicio}',
} as const;

/**
 * El acceso que exige esta lectura.
 *
 * No es una opcion del catalogo: es el centinela `RequiereAcceso.SESION_PROPIA`,
 * que `GuardiaDeAcceso` trata aparte —basta con tener sesion—. Se escribe aqui
 * para que `modulos.ts` lo declare como lo declara el backend y no invente una
 * opcion que nadie puede conceder.
 */
export const SESION_PROPIA = '__sesion_propia__';

export type EjercicioParametrizado = {
  ejercicio: number;
  sellado: boolean;
  conjuntoId: number | null;
  version: number | null;
};

/**
 * Si el ejercicio tiene conjunto sellado.
 *
 * **Un ejercicio sin sellar contesta 200 y no 404**, con `sellado: false` y los
 * otros dos campos nulos. Es deliberado y cambia la pantalla: «todavia no se ha
 * sellado» es una respuesta, no un fallo, y dibujarla como error mandaria a
 * quien atiende a buscar una averia que no existe.
 */
export function ejercicio(anio: number, senal?: AbortSignal): Promise<EjercicioParametrizado> {
  return solicitar(camino(RUTAS.ejercicio, { ejercicio: anio }), { senal });
}
