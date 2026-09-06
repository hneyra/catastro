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
