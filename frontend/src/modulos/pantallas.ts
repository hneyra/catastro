import type { ComponentType } from 'react';
import type { PantallaProps } from '../App';
import { Fichas, Panel, Plano, Predios, Territorio, Valores } from './catastro/Catastro';
import { Zonificacion } from './urbano/Urbano';
import { Itse, RiesgoDelPredio } from './riesgo/Riesgo';
import { Actas, Campanias, Candidatos, Hallazgos } from './fiscalizacion/Fiscalizacion';
import { FichaDelContribuyente, Resumen } from './consultas/Consultas';
import { Ejercicio } from './parametros/Parametros';

/**
 * Que componente dibuja cada destino.
 *
 * La llave es `modulo/hoja` y tiene que casar con `shell/modulos.ts`: un destino
 * que el panel lista y nadie dibuja es la mitad del trabajo, y se queda en
 * silencio —sin error de consola— hasta que alguien lo abre. Por eso
 * `verificaciones/mirar.mjs` recorre TODOS los destinos del registro y falla si
 * alguno deja el `<main>` practicamente vacio.
 */
export const PANTALLAS: Record<string, ComponentType<PantallaProps>> = {
  'catastro/panel': Panel,
  'catastro/predios': Predios,
  'catastro/fichas': Fichas,
  'catastro/territorio': Territorio,
  'catastro/plano': Plano,
  'catastro/valores': Valores,
  'urbano/zonificacion': Zonificacion,
  'riesgo/predio': RiesgoDelPredio,
  'riesgo/itse': Itse,
  'fiscalizacion/campanias': Campanias,
  'fiscalizacion/candidatos': Candidatos,
  'fiscalizacion/hallazgos': Hallazgos,
  'fiscalizacion/actas': Actas,
  'consultas/resumen': Resumen,
  'consultas/ficha-contribuyente': FichaDelContribuyente,
  'parametros/ejercicio': Ejercicio,
};

/**
 * Los destinos que se dibujan **a sangre**: sin el margen del `<main>`.
 *
 * Son los tres del artboard que ocupan el alto entero y llevan su propio
 * desplazamiento: los dos maestro-detalle —Predios y Territorio— y la hoja de
 * cuadros, cuya cabecera de tabla se queda fija mientras el cuerpo baja. Con el
 * margen de 18 px del armazon, el `data-split` no puede medir su alto y las dos
 * columnas se desplazan por separado dentro de un tercer desplazamiento.
 *
 * Es una lista y no una bandera de la pantalla porque quien pone el margen es el
 * armazon: la pantalla no puede quitarselo desde dentro sin margenes negativos,
 * que es como se rompe al primer cambio de medida.
 */
export const A_SANGRE: ReadonlySet<string> = new Set([
  'catastro/predios',
  'catastro/territorio',
  'catastro/valores',
]);
