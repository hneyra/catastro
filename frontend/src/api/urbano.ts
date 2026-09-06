/**
 * `urbano` (#4): la zonificacion vigente. Publica **la zona** a la que cae un
 * predio, y nada mas.
 *
 * Quien es compatible con que giro es dato de `rentas` y la licencia la emite
 * `rentas`: es la misma frontera de ADR-0024 que impide calcular un tributo
 * aqui. Esta interfaz no puede, por tanto, contestar «¿puedo abrir una bodega?»,
 * y su pantalla lo dice en vez de insinuarlo.
 */
import { solicitar } from './cliente';

export const RUTAS = {
  zonificacion: '/urbano/zonificacion',
} as const;

export type ParametroUrbanistico = {
  clave: string;
  valor: string;
  unidad: string | null;
};

export type Zona = {
  aLaFecha: string;
  codigo: string;
  nombre: string;
  plan: string;
  ordenanza: string;
  vigenciaDesde: string;
  vigenciaHasta: string | null;
  parametros: ParametroUrbanistico[];
};

/**
 * La zona a la que cae un predio.
 *
 * **Contesta 422 `VALIDACION` para todo predio real**, y no 404: el predio
 * existe, lo que le falta es el poligono. Hoy no hay ni uno cargado en ninguna
 * instalacion, asi que ese es el unico desenlace que ocurre de verdad, y la
 * pantalla lo distingue del «no existe» a proposito.
 */
export function zonificacion(
  predioId: number,
  aLaFecha?: string,
  senal?: AbortSignal,
): Promise<Zona> {
  return solicitar(RUTAS.zonificacion, { parametros: { predioId, aLaFecha }, senal });
}
