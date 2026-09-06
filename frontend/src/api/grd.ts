/**
 * `grd` (#5): el peligro, las fajas marginales y el ITSE del predio.
 *
 * El dato que decide es `mitigable`, no el nivel: por eso la respuesta lo
 * publica derivado arriba (`hayRiesgoNoMitigable`) ademas de por zona, y la
 * pantalla lee eso.
 */
import { solicitar } from './cliente';

export const RUTAS = {
  riesgo: '/grd/riesgo',
  itse: '/grd/itse',
} as const;

export type ZonaDeRiesgo = {
  id: number;
  codigo: string;
  fenomeno: string;
  nivel: string;
  mitigable: boolean;
  fuente: string;
  documentoOrigen: string;
  vigenciaDesde: string;
  vigenciaHasta: string | null;
};

export type FajaMarginal = {
  id: number;
  codigo: string;
  cuerpoDeAgua: string;
  /** Ya llega como texto desde el backend. */
  anchoM: string;
  fuente: string;
  documentoOrigen: string;
  vigenciaDesde: string;
  vigenciaHasta: string | null;
};

export type RiesgoDelPredio = {
  predioId: number;
  aLaFecha: string;
  hayRiesgoNoMitigable: boolean;
  zonas: ZonaDeRiesgo[];
  fajasMarginales: FajaMarginal[];
};

/** Mismo 422 por predio sin poligono que la zonificacion, y por el mismo motivo. */
export function riesgo(predioId: number, senal?: AbortSignal): Promise<RiesgoDelPredio> {
  return solicitar(RUTAS.riesgo, { parametros: { predioId }, senal });
}

export type CertificadoItse = {
  id: number;
  numero: string;
  nivelRiesgo: string;
  modalidad: string;
  vigenciaDesde: string;
  vigenciaHasta: string;
  fechaAnulacion: string | null;
};

export type ItseDelPredio = {
  predioId: number;
  aLaFecha: string;
  vigentes: CertificadoItse[];
};

/**
 * Los certificados ITSE vigentes de un predio a una fecha.
 *
 * El ITSE **no necesita poligono** —cuelga del predio, no de la geometria—, asi
 * que es la unica de las dos lecturas de este modulo que contesta hoy con datos.
 */
export function itse(
  predioId: number,
  aLaFecha?: string,
  senal?: AbortSignal,
): Promise<ItseDelPredio> {
  return solicitar(RUTAS.itse, { parametros: { predioId, aLaFecha }, senal });
}
