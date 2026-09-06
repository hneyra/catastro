/**
 * La ventanilla: el resumen predial y la ficha del contribuyente.
 *
 * Las dos lecturas viven en `nucleo` pero cuelgan de otro `acceso`
 * —`consulta_resumen_predial` y `ficha_contribuyente_reporte`— y se ven como un
 * modulo aparte, que es como las agrupa `CatalogoDelSistema` (la unica opcion
 * cuyo `moduloCodigo` no es `CATASTRO` es `consulta_resumen_predial`).
 */
import { camino, descargar, solicitar } from './cliente';
import type { DocumentoEntregado, FormatoDeDocumento, Paginacion, RespuestaPaginada } from './cliente';

export const RUTAS = {
  resumenPredial: '/consultas/resumen-predial',
  fichaDelContribuyente: '/catastro/contribuyentes/{codigo}/ficha.pdf',
} as const;

export type PredioDelResumen = {
  fichaId: number;
  predioId: number;
  codCatastral: string;
  codPropietario: string | null;
  nombreDelPropietario: string | null;
  direccionDelPredio: string;
  uso: string;
  tipo: string;
  version: number;
  vigenciaDesde: string;
};

export type FiltrosDelResumen = {
  codCatastral?: string;
  codContribuyente?: string;
  /** `"TODOS"` lo traduce el backend a «sin filtro». */
  uso?: string;
  fecha?: string;
};

export function resumenPredial(
  filtros: FiltrosDelResumen,
  pagina: Paginacion,
  senal?: AbortSignal,
): Promise<RespuestaPaginada<PredioDelResumen>> {
  return solicitar(RUTAS.resumenPredial, { parametros: { ...filtros, ...pagina }, senal });
}

export type UnidadDelReporte = {
  codRefCatastral: string;
  direccion: string;
  condicion: string;
  porcentaje: string;
  /** `AreaM2` -> texto. */
  areaTerreno: string | null;
  uso: string | null;
  version: number | null;
};

export type Reporte = {
  aLaFecha: string;
  codigo: string;
  nombre: string;
  documento: string;
  domicilioFiscal: string | null;
  unidades: UnidadDelReporte[];
};

/**
 * La ficha del contribuyente **como datos**.
 *
 * <h2>La unica ruta del sistema con esta forma, y por eso son dos funciones</h2>
 *
 * `ReporteController` declara los dos manejadores sobre la MISMA URI y los
 * separa con `params = "formato"`: sin el parametro contesta `ReporteResource`
 * en JSON, y con el devuelve `byte[]` con su `Content-Disposition`. O sea que lo
 * que decide el tipo de respuesta no es la extension `.pdf` del camino ni la
 * cabecera `Accept`: es **que el parametro exista**.
 *
 * Una sola funcion con `formato?: string` haria eso confundible —pasar
 * `undefined` y pasar `'PDF'` devuelven cosas de tipos distintos— asi que son
 * dos, con dos tipos de retorno, y ninguna admite el parametro de la otra. La
 * firma hace imposible el error.
 */
export function fichaDelContribuyente(
  codigo: string,
  fecha?: string,
  senal?: AbortSignal,
): Promise<Reporte> {
  return solicitar(camino(RUTAS.fichaDelContribuyente, { codigo }), { parametros: { fecha }, senal });
}

/** La misma ficha **como documento**. Exige el formato; sin el seria la otra. */
export function documentoDeLaFicha(
  codigo: string,
  formato: FormatoDeDocumento,
  fecha?: string,
): Promise<DocumentoEntregado> {
  return descargar(
    camino(RUTAS.fichaDelContribuyente, { codigo }),
    { formato, fecha },
    `ficha-${codigo}.${formato.toLowerCase()}`,
  );
}
