/**
 * `fiscalizacion` (#6, ADR-0035): el hallazgo catastral, con su acto y su
 * evidencia. **No es la fiscalizacion TRIBUTARIA**, que vive entera en `rentas`.
 *
 * Ninguna de sus cinco tablas tiene columna de importe y ninguna respuesta del
 * contrato la trae: aqui no se liquida, no se determina y no se emite un valor.
 *
 * <h2>Cuatro lecturas de once operaciones, y las que faltan se nombran</h2>
 *
 * El controlador publica **once** operaciones y solo **cuatro** son lecturas.
 * Lo que la interfaz NO puede hacer, medido sobre `FiscalizacionCatastralController`:
 *
 * - **No hay listado de campanias.** `POST /fiscalizacion/campanias` crea una y
 *   no hay ningun `GET` que las enumere, asi que una pantalla de campanias no
 *   puede tener una tabla: necesita que le den el identificador. Se dice en
 *   pantalla; inventar la lista seria inventar la operacion.
 * - **No hay ninguna lectura de actas.** `POST .../hallazgos/{id}/acta` levanta
 *   una y devuelve la que acaba de crear; no hay `GET` de actas ni por campania
 *   ni por hallazgo. Un listado de actas aqui seria una pantalla contra una
 *   operacion que no existe.
 *
 * Las dos son huecos del backend y se declaran; ninguna se rellena con la cifra
 * de un prototipo.
 */
import { camino, solicitar } from './cliente';
import type { Paginacion, RespuestaPaginada } from './cliente';

export const RUTAS = {
  candidatos: '/fiscalizacion/campanias/{campaniaId}/candidatos',
  tasaDeDescarte: '/fiscalizacion/campanias/{campaniaId}/tasa-de-descarte',
  hallazgos: '/fiscalizacion/campanias/{campaniaId}/hallazgos',
  evidencias: '/fiscalizacion/hallazgos/{hallazgoId}/evidencias',
} as const;

export type Campania = {
  id: number;
  codigo: string;
  nombre: string;
  estado: string;
  inicio: string;
  fin: string | null;
  umbral: string;
};

export type Candidato = {
  id: number;
  campaniaId: number;
  predioId: number | null;
  clase: string;
  origen: string;
  score: string;
  insumos: string;
  estado: string;
  etapaDeDescarte: string | null;
  motivoDeDescarte: string | null;
  descartadoPor: string | null;
};

export function candidatos(
  campaniaId: number,
  filtros: { estado?: string; clase?: string },
  pagina: Paginacion,
  senal?: AbortSignal,
): Promise<RespuestaPaginada<Candidato>> {
  return solicitar(camino(RUTAS.candidatos, { campaniaId }), {
    parametros: { ...filtros, ...pagina },
    senal,
  });
}

/**
 * El embudo de la campania, en cifras y **sin ningun porcentaje**.
 *
 * El backend publica las seis cuentas y no la tasa: un porcentaje esconde el
 * denominador —50 % de 20 y de 20 000 no dicen lo mismo— y ademas calcularlo
 * aqui exigiria decidir un redondeo que D-03b no ha decidido. La pantalla ensena
 * las cifras.
 */
export type TasaDeDescarte = {
  detectados: number;
  descartadosEnGabinete: number;
  loQuePasoGabinete: number;
  descartadosEnCampo: number;
  verificados: number;
  enCurso: number;
};

export function tasaDeDescarte(campaniaId: number, senal?: AbortSignal): Promise<TasaDeDescarte> {
  return solicitar(camino(RUTAS.tasaDeDescarte, { campaniaId }), { senal });
}

export type Hallazgo = {
  id: number;
  candidatoId: number;
  clase: string;
  predioId: number | null;
  fichaId: number | null;
  /** `AreaM2` -> texto. */
  areaDeLaFicha: string | null;
  areaVerificada: string;
  excesoVerificado: string | null;
  inspector: string;
  verificadoEn: string;
  estado: string;
};

export function hallazgos(
  campaniaId: number,
  pagina: Paginacion,
  senal?: AbortSignal,
): Promise<RespuestaPaginada<Hallazgo>> {
  return solicitar(camino(RUTAS.hallazgos, { campaniaId }), { parametros: { ...pagina }, senal });
}

export type Evidencia = {
  id: number;
  hallazgoId: number;
  tipo: string;
  sha256: string;
  ruta: string;
  capturadoEn: string;
  recibidoEn: string;
  desfaseEnSegundos: number;
  dispositivo: string | null;
};

export function evidencias(hallazgoId: number, senal?: AbortSignal): Promise<Evidencia[]> {
  return solicitar(camino(RUTAS.evidencias, { hallazgoId }), { senal });
}
