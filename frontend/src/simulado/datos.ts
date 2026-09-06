/**
 * Lo que el proxy contesta, con las formas de los `record` del backend.
 *
 * El padron sale de `padron.ts`, que se deriva de `infra/carga-de-datos/ejemplos/`.
 * Lo de aqui son los recursos que **no** tienen archivo de ejemplo —zonas,
 * riesgo, ITSE, campanias, cuadros del ejercicio— y se escriben con la forma
 * medida del DTO y valores del mismo distrito: Catacaos, ubigeo 200104,
 * longitud entre -80,69 y -80,65 y latitud entre -5,27 y -5,25.
 *
 * **Nada de esto es un dato de nadie.** Son predios inventados de una
 * municipalidad de demostracion, y por eso el arnes `sin-red` existe: el dia que
 * el proxy se apague, ninguna de estas cifras puede quedarse en pantalla.
 */
import { MANZANAS, PADRON, SECTORES, VIAS } from './padron';
import type { FilaDelPadron } from './padron';

export { MANZANAS, PADRON, SECTORES, VIAS };
export type { FilaDelPadron };

/**
 * El unico predio del padron simulado que tiene poligono.
 *
 * <h2>Por que hay uno, y solo uno</h2>
 *
 * En las instalaciones de verdad **no hay ni un poligono cargado**, asi que
 * `GET /urbano/zonificacion` y `GET /grd/riesgo` contestan 422 `VALIDACION`
 * —«el predio existe, le falta el poligono»— para todo predio real. Ese es el
 * unico camino que ocurre hoy, y si el proxy solo simulara ese, la pantalla del
 * camino feliz no se habria dibujado nunca y nadie sabria si funciona.
 *
 * Elegir la respuesta segun el sujeto **no es fingir semantica**: no filtra, no
 * ordena y no pagina —eso es lo que ADR-0010 prohibe—, reproduce la bifurcacion
 * que el backend ya tiene escrita. Lo que se simula es cual de sus dos
 * respuestas toca, no una tercera que el backend no da.
 */
export const PREDIO_CON_POLIGONO = 1;

/** El ejercicio del que hablan los cuadros de abajo. */
export const EJERCICIO = 2026;

/* ── Zonificacion (urbano) ──────────────────────────────────────────────── */

export const ZONA = {
  aLaFecha: '2026-09-06',
  codigo: 'RDM',
  nombre: 'Residencial de densidad media',
  plan: 'PDU-2026-DEMO',
  ordenanza: 'Ordenanza 012-2026-MDC',
  vigenciaDesde: '2026-01-01',
  vigenciaHasta: null,
  parametros: [
    { clave: 'ALTURA_MAXIMA', valor: '3', unidad: 'pisos' },
    { clave: 'COEFICIENTE_DE_EDIFICACION', valor: '2.1', unidad: null },
    { clave: 'AREA_LIBRE_MINIMA', valor: '30', unidad: '%' },
    { clave: 'RETIRO_FRONTAL', valor: '2', unidad: 'm' },
    { clave: 'LOTE_MINIMO', valor: '120', unidad: 'm2' },
  ],
};

/* ── Riesgo e ITSE (grd) ────────────────────────────────────────────────── */

export const RIESGO = {
  aLaFecha: '2026-09-06',
  hayRiesgoNoMitigable: false,
  zonas: [
    {
      id: 1,
      codigo: 'ZR-INU-01',
      fenomeno: 'INUNDACION',
      nivel: 'MEDIO',
      mitigable: true,
      fuente: 'CENEPRED',
      documentoOrigen: 'Informe 041-2025-CENEPRED',
      vigenciaDesde: '2025-06-01',
      vigenciaHasta: null,
    },
  ],
  fajasMarginales: [
    {
      id: 1,
      codigo: 'FM-PIURA-07',
      cuerpoDeAgua: 'Rio Piura',
      anchoM: '25.00',
      fuente: 'ANA',
      documentoOrigen: 'R.D. 218-2024-ANA-AAA-JZ',
      vigenciaDesde: '2024-11-15',
      vigenciaHasta: null,
    },
  ],
};

/**
 * Los certificados ITSE.
 *
 * **El ITSE no cuelga de la geometria sino del predio**, asi que es la unica de
 * las dos lecturas de `grd` que hoy contesta con datos en una instalacion de
 * verdad. Por eso el proxy lo devuelve para cualquier predio y no solo para el
 * que tiene poligono.
 */
export const ITSE = {
  aLaFecha: '2026-09-06',
  vigentes: [
    {
      id: 1,
      numero: 'ITSE-2026-000118',
      nivelRiesgo: 'RIESGO_BAJO',
      modalidad: 'POSTERIOR',
      vigenciaDesde: '2026-02-10',
      vigenciaHasta: '2028-02-09',
      fechaAnulacion: null,
    },
  ],
};

/* ── Fiscalizacion ──────────────────────────────────────────────────────── */

export const CAMPANIA = {
  id: 1,
  codigo: 'CAM-2026-001',
  nombre: 'Subvaluacion en el cercado',
  estado: 'EN_CURSO',
  inicio: '2026-03-02',
  fin: null,
  umbral: '0.15',
};

export const CANDIDATOS = [
  {
    id: 1,
    campaniaId: 1,
    predioId: 3,
    clase: 'SUBVALUADOR',
    origen: 'GABINETE',
    score: '0.82',
    insumos: 'Area declarada frente a huella levantada',
    estado: 'PASO_GABINETE',
    etapaDeDescarte: null,
    motivoDeDescarte: null,
    descartadoPor: null,
  },
  {
    id: 2,
    campaniaId: 1,
    predioId: 7,
    clase: 'SUBVALUADOR',
    origen: 'GABINETE',
    score: '0.61',
    insumos: 'Area declarada frente a huella levantada',
    estado: 'DESCARTADO',
    etapaDeDescarte: 'GABINETE',
    motivoDeDescarte: 'La diferencia cae dentro del error de restitucion',
    descartadoPor: 'v.reto',
  },
  {
    id: 3,
    campaniaId: 1,
    predioId: 12,
    clase: 'OMISO',
    origen: 'CRUCE',
    score: '0.74',
    insumos: 'Predio levantado sin ficha vigente',
    estado: 'EN_CURSO',
    etapaDeDescarte: null,
    motivoDeDescarte: null,
    descartadoPor: null,
  },
];

export const TASA_DE_DESCARTE = {
  detectados: 3,
  descartadosEnGabinete: 1,
  loQuePasoGabinete: 2,
  descartadosEnCampo: 0,
  verificados: 1,
  enCurso: 1,
};

export const HALLAZGOS = [
  {
    id: 1,
    candidatoId: 1,
    clase: 'SUBVALUADOR',
    predioId: 3,
    fichaId: 3,
    areaDeLaFicha: '265.75',
    areaVerificada: '318.40',
    excesoVerificado: '52.65',
    inspector: 'v.reto',
    verificadoEn: '2026-04-18',
    estado: 'FIRME',
  },
];

export const EVIDENCIAS = [
  {
    id: 1,
    hallazgoId: 1,
    tipo: 'FOTOGRAFIA',
    sha256: 'b1f4c2a09d6e8f3517ac4d0b2e97615833f0a4cd8b21e7695fd0c34a8e1b7d92',
    ruta: 'evidencias/2026/CAM-2026-001/h-1/frente.jpg',
    capturadoEn: '2026-04-18T14:32:11Z',
    recibidoEn: '2026-04-18T19:04:52Z',
    desfaseEnSegundos: 16361,
    dispositivo: 'GNSS-CAT-04',
  },
  {
    id: 2,
    hallazgoId: 1,
    tipo: 'CROQUIS',
    sha256: '4d90e17b3c58a26f0b41de9a7c3852016fbd4e07a9c135826d4e0fb937a5c18e',
    ruta: 'evidencias/2026/CAM-2026-001/h-1/croquis.pdf',
    capturadoEn: '2026-04-18T14:48:03Z',
    recibidoEn: '2026-04-18T19:04:52Z',
    desfaseEnSegundos: 15409,
    dispositivo: 'GNSS-CAT-04',
  },
];

/* ── Los cuadros del ejercicio ──────────────────────────────────────────── */

export const EJERCICIO_SELLADO = {
  ejercicio: EJERCICIO,
  sellado: true,
  conjuntoId: 41,
  version: 3,
};

export const ARANCELES = VIAS.slice(0, 6).map((via, i) => ({
  id: i + 1,
  viaId: via.id,
  tramo: null,
  valorM2: ['388.00', '312.00', '246.00', '188.00', '204.50', '171.00'][i]!,
  documentoFuente: 'R.M. 276-2025-VIVIENDA',
}));

export const VALORES_UNITARIOS = [
  { partida: 'MUROS_Y_COLUMNAS', categoria: 'C', valorM2: '412.35' },
  { partida: 'TECHOS', categoria: 'D', valorM2: '188.42' },
  { partida: 'PISOS', categoria: 'E', valorM2: '96.11' },
  { partida: 'PUERTAS_Y_VENTANAS', categoria: 'F', valorM2: '74.28' },
  { partida: 'REVESTIMIENTOS', categoria: 'E', valorM2: '132.90' },
  { partida: 'BANIOS', categoria: 'F', valorM2: '41.06' },
  { partida: 'INSTALACIONES_ELECTRICAS_Y_SANITARIAS', categoria: 'E', valorM2: '118.73' },
].map((v, i) => ({
  id: i + 1,
  ...v,
  anioConstruccionDesde: 1900,
  anioConstruccionHasta: null,
  documentoFuente: 'R.M. 277-2025-VIVIENDA, Anexo I.2',
}));

export const DEPRECIACION = [
  { uso: 'CASA_HABITACION', material: 'CONCRETO', estadoConservacion: 'MUY_BUENO', antiguedadHasta: 5, porcentaje: '0.03' },
  { uso: 'CASA_HABITACION', material: 'CONCRETO', estadoConservacion: 'BUENO', antiguedadHasta: 10, porcentaje: '0.10' },
  { uso: 'CASA_HABITACION', material: 'LADRILLO', estadoConservacion: 'REGULAR', antiguedadHasta: 20, porcentaje: '0.32' },
  { uso: 'CASA_HABITACION', material: 'ADOBE', estadoConservacion: 'REGULAR', antiguedadHasta: 30, porcentaje: '0.55' },
].map((d, i) => ({ id: i + 1, ...d, documentoFuente: 'R.M. 277-2025-VIVIENDA, Anexo I' }));

/* ── La ventanilla ──────────────────────────────────────────────────────── */

/** Los nombres de los titulares de demostracion, por su codigo de contribuyente. */
export const CONTRIBUYENTES: Record<string, { nombre: string; documento: string }> = {
  'C-000001': { nombre: 'AYALA CHUNGA, MERCEDES', documento: '02657188' },
  'C-000002': { nombre: 'SANDOVAL YARLEQUE, TEODORO', documento: '02651340' },
  'C-000003': { nombre: 'FIESTAS QUEREVALU, ROSA', documento: '02660712' },
  'C-000004': { nombre: 'PAIVA NAMUCHE, SEGUNDO', documento: '02648905' },
  'C-000006': { nombre: 'CERAMICAS NARIHUALA E.I.R.L.', documento: '20525118034' },
  'C-000012': { nombre: 'ECA VALLADARES, JULIA', documento: '02663251' },
  'C-000013': { nombre: 'MORE ANTON, LUIS ALBERTO', documento: '02659430' },
  'C-000014': { nombre: 'ZAPATA IPANAQUE, CARMEN', documento: '02655017' },
};

export function contribuyenteDe(codigo: string): { nombre: string; documento: string } {
  return CONTRIBUYENTES[codigo] ?? { nombre: 'SIN NOMBRE EN EL PADRON DE DEMOSTRACION', documento: '—' };
}

/* ── Los frentes ────────────────────────────────────────────────────────── */

export const FRENTES = [
  {
    id: 1,
    viaId: VIAS[2]!.id,
    viaCodigo: VIAS[2]!.codigo,
    viaNombre: VIAS[2]!.nombre,
    longitud: '12.40',
    longitudEstado: 'PROPUESTA',
    esPrincipal: true,
    numeracion: '245',
    retiro: null,
    confirmadoPor: null,
    confirmadoEn: null,
    geometria: 'LINESTRING(-80.6812 -5.2604, -80.6811 -5.2603)',
  },
];

/**
 * El motivo de la derivacion cuando NO propone nada.
 *
 * Es el caso de verdad: sin ejes de calzada cargados no hay con que cortar el
 * lote, y `DerivarFrentes` deja constancia del motivo por predio en vez de dejar
 * una lista vacia sin explicar (#7, AC 3).
 */
export const SIN_FRENTES = 'El predio no tiene poligono levantado: sin lote no hay nada que cortar contra el eje de la via.';
