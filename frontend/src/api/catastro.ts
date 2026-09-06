/**
 * El contexto acotado `catastro` —`kamayuk-catastro-nucleo`— visto desde la
 * interfaz: los tipos, campo por campo, de los `record` que publica, y las
 * lecturas que el armazon usa.
 *
 * **Los tipos se escriben igual que el `record`**, con los nombres que viajan.
 * Y `Dinero`, `Alicuota`, `Porcentaje` y `AreaM2` se declaran `string`, porque
 * es lo que son en el cable: `ConfiguracionDeJson` los serializa con
 * `toPlainString()` —`"180.50"`, `"100.0000"`—. Pasarlos por `Number` para
 * volver a formatearlos es como se pierde un decimal, y lo prohibe ESLint.
 */
import { camino, solicitar } from './cliente';
import type { Paginacion, RespuestaPaginada } from './cliente';

/** Las rutas del contrato, con sus llaves y tal como las declara el backend. */
export const RUTAS = {
  predios: '/catastro/predios',
  predio: '/catastro/predios/{predioId}',
  caracteristicas: '/catastro/predios/{predioId}/caracteristicas',
  frentes: '/catastro/predios/{predioId}/frentes',
  confirmarFrente: '/catastro/predios/{predioId}/frentes/{frenteId}/confirmacion',
  plano: '/catastro/predios/plano',
  marcoDelPlano: '/catastro/predios/plano/marco',
  fichas: '/catastro/fichas',
  fichaUrbana: '/catastro/fichas/urbana/{codRefCatastral}',
  areaDeLaFicha: '/catastro/fichas/{fichaId}/area',
  sectores: '/catastro/sectores',
  manzanas: '/catastro/sectores/{codigo}/manzanas',
  vias: '/catastro/vias',
  aranceles: '/catastro/tablas/aranceles',
  valoresUnitarios: '/catastro/tablas/valores-unitarios',
  depreciacion: '/catastro/tablas/depreciacion',
} as const;

/**
 * Por que campos deja ordenar cada listado, y **de donde sale la lista**.
 *
 * <h2>Ofrecer una columna que da 422 es ofrecer un error</h2>
 *
 * `ParametrosDePaginacion.ordenarPor` no lo valida Spring: lo valida
 * `OrdenSeguro`, que tiene una lista blanca por consulta y lanza
 * `OrdenNoAdmitido` —**422 `ORDEN_NO_ADMITIDO`**— con cualquier otro campo. Un
 * desplegable que ofrezca «Autovaluo» no ordena mal: revienta la lectura.
 *
 * <h2>Los nombres son los CAMELCASE, y esta medido por que valen</h2>
 *
 * `OrdenSeguro.sobre("cod_ref_catastral", …)` mete cada columna **dos veces**:
 * la cruda y su `aCamelCase`. Asi que `codRefCatastral` es un campo admitido de
 * verdad y no una traduccion que esta interfaz se invente.
 *
 * **Y hay una trampa medida en las vias**: la columna es `tipo_via`, o sea que
 * el campo admitido es `tipoVia`, mientras que el `record` publica `tipo`.
 * Pedir `ordenarPor=tipo` —el nombre que se ve en la tabla— da 422. Es el
 * desajuste que `OrdenSeguro.publicandoComo` existe para arreglar y que en esta
 * consulta no se aplica; aqui se declara el nombre que el backend admite.
 *
 * `constante` nombra la constante del backend de la que sale cada lista, para
 * que `verificaciones/rutas.mjs` compare las dos y no una copia con otra copia.
 */
export type OrdenAdmitido = {
  readonly constante: string;
  readonly campos: readonly string[];
};

export const ORDENES = {
  predios: {
    constante: 'CatastroRepositoryJdbc.ORDEN_CATASTRO',
    campos: ['codRefCatastral', 'direccion', 'predioId'],
  },
  fichas: {
    constante: 'FichaCatastralRepositoryJdbc.ORDEN_CONSULTA',
    campos: ['codRefCatastral', 'direccion', 'uso', 'vigenciaDesde', 'id'],
  },
  sectores: {
    constante: 'CatastroRepositoryJdbc.ORDEN_SECTOR',
    campos: ['codigo', 'nombre', 'zona', 'id'],
  },
  manzanas: { constante: 'CatastroRepositoryJdbc.ORDEN_MANZANA', campos: ['codigo', 'id'] },
  vias: { constante: 'ViaRepositoryJdbc.ORDEN', campos: ['codigo', 'nombre', 'tipoVia', 'id'] },
} as const satisfies Record<string, OrdenAdmitido>;

/**
 * El tamano maximo de pagina que el backend admite.
 *
 * `Paginacion.TAMANO_MAXIMO`. Pedir mas es un 422 `VALIDACION`, asi que una
 * pantalla que quiera contar sobre el padron entero tiene que saber si le cabe:
 * cuando `totalElementos` supera lo que trajo la pagina, la cuenta **no se
 * hace** y se dice, en vez de contar sobre un trozo y llamarlo total.
 */
export const TAMANO_MAXIMO = 500;

/** Los dos estados de un predio, letra por letra como los nombra `EstadoPredio`. */
export const ESTADOS_DE_PREDIO = ['ACTIVO', 'DADO_DE_BAJA'] as const;
export type EstadoDePredio = (typeof ESTADOS_DE_PREDIO)[number];

/** Los dos tipos de predio (`TipoPredio`). */
export const TIPOS_DE_PREDIO = ['URBANO', 'RUSTICO'] as const;

/**
 * Lo que admite el filtro `titularidad` (`TitularidadDelPredio`).
 *
 * El controlador lo pasa por `toUpperCase` y contesta 422 a cualquier otra
 * cosa, asi que se ofrecen estos tres y ninguno mas.
 */
export const TITULARIDADES = ['SIN_TITULAR', 'INCOMPLETA', 'COMPLETA'] as const;
export type Titularidad = (typeof TITULARIDADES)[number];

/* ── El padron ──────────────────────────────────────────────────────────── */

export type PredioDelCatastro = {
  predioId: number;
  codRefCatastral: string;
  tipo: string;
  direccion: string;
  numeroMunicipal: string | null;
  codigoDeVia: string | null;
  via: string | null;
  codigoDeSector: string | null;
  codigoDeManzana: string | null;
  lote: string | null;
  ubigeo: string | null;
  estado: string;
  fichado: boolean;
};

export type FiltrosDePredios = {
  /** Acota por PREFIJO del codigo de referencia catastral. */
  codRefCatastral?: string;
  codigoDeSector?: string;
  estado?: string;
  fichado?: boolean;
  titularidad?: string;
};

export function predios(
  filtros: FiltrosDePredios,
  pagina: Paginacion,
  senal?: AbortSignal,
): Promise<RespuestaPaginada<PredioDelCatastro>> {
  return solicitar(RUTAS.predios, { parametros: { ...filtros, ...pagina }, senal });
}

export type PredioEnElPadron = { predioId: number; enElPadron: boolean };

export function predio(predioId: number, senal?: AbortSignal): Promise<PredioEnElPadron> {
  return solicitar(camino(RUTAS.predio, { predioId }), { senal });
}

export type CaracteristicasDelPredio = {
  predioId: number;
  enElPadron: boolean;
  fichaId: number | null;
  fichaEconomicaId: number | null;
  uso: string | null;
  sectorCodigo: string | null;
  /** `AreaM2` -> texto. */
  areaTerreno: string | null;
  aLaFecha: string;
};

export function caracteristicas(
  predioId: number,
  fecha: string,
  senal?: AbortSignal,
): Promise<CaracteristicasDelPredio> {
  return solicitar(camino(RUTAS.caracteristicas, { predioId }), { parametros: { fecha }, senal });
}

/* ── Las fichas ─────────────────────────────────────────────────────────── */

export type FichaEncontrada = {
  fichaId: number;
  predioId: number;
  codRefCatastral: string;
  direccion: string;
  manzana: string | null;
  lote: string | null;
  tipo: string;
  version: number;
  areaTerreno: string;
  areaConstruida: string | null;
  uso: string;
  vigenciaDesde: string;
  titular: string | null;
};

/**
 * Los cuatro tipos de ficha, letra por letra como los nombra el backend.
 *
 * No se traducen ni se aproximan: `ConsultaController` compara con el `name()`
 * del enumerado, asi que «Unica» no es `UNICA` y ofrecer un valor que el
 * enumerado no reconoce es dibujar un desplegable que no filtra.
 */
export const TIPOS_DE_FICHA = ['UNICA', 'ECONOMICA', 'BIENES_COMUNES', 'RURAL'] as const;
export type TipoDeFicha = (typeof TIPOS_DE_FICHA)[number];

export type FiltrosDeFichas = {
  codRefCatastral?: string;
  contribuyente?: string;
  manzana?: string;
  lote?: string;
  tipo?: TipoDeFicha;
  fecha?: string;
};

export function fichas(
  filtros: FiltrosDeFichas,
  pagina: Paginacion,
  senal?: AbortSignal,
): Promise<RespuestaPaginada<FichaEncontrada>> {
  return solicitar(RUTAS.fichas, { parametros: { ...filtros, ...pagina }, senal });
}

export type Construccion = {
  id: number;
  piso: string;
  areaConstruida: string;
  anioConstruccion: number | null;
  material: string | null;
  estadoConservacion: string | null;
  categorias: string;
  porcentajeConstruido: string | null;
};

export type Ficha = {
  id: number;
  predioId: number;
  tipo: string;
  version: number;
  areaTerreno: string;
  uso: string;
  frontis: string | null;
  condicionPropiedad: string | null;
  tipoEdificacion: string | null;
  vigenciaDesde: string;
  vigenciaHasta: string | null;
  vigente: boolean;
  origen: string;
  documentoOrigen: string;
  observacion: string;
  denominacion: string | null;
  construcciones: Construccion[];
};

export function fichaUrbana(
  codRefCatastral: string,
  senal?: AbortSignal,
): Promise<Ficha> {
  return solicitar(camino(RUTAS.fichaUrbana, { codRefCatastral }), { senal });
}

/* ── El territorio ──────────────────────────────────────────────────────── */

export type Sector = {
  id: number;
  codigo: string;
  nombre: string;
  zona: string | null;
  activo: boolean;
  /** Los tres conteos solo los trae la LISTA; la escritura los deja nulos. */
  manzanas: number | null;
  predios: number | null;
  lotes: number | null;
};

export function sectores(pagina: Paginacion, senal?: AbortSignal): Promise<RespuestaPaginada<Sector>> {
  return solicitar(RUTAS.sectores, { parametros: { ...pagina }, senal });
}

export type Manzana = {
  id: number;
  sectorId: number;
  sectorCodigo: string;
  codigo: string;
  predios: number | null;
  lotes: number | null;
};

export function manzanas(
  codigo: string,
  pagina: Paginacion,
  senal?: AbortSignal,
): Promise<RespuestaPaginada<Manzana>> {
  return solicitar(camino(RUTAS.manzanas, { codigo }), { parametros: { ...pagina }, senal });
}

export type Via = {
  id: number;
  codigo: string;
  tipo: string;
  nombre: string;
  ubigeo: string | null;
  activa: boolean;
};

/**
 * Lo que esta interfaz puede mandar a `GET /catastro/vias`.
 *
 * **`sector` NO esta, y es a proposito.** El controlador lo declara y lo
 * **rechaza**: `ViaController` lanza 422 `VALIDACION` en cuanto llega con valor
 * —«el filtro 'sector' no se sirve: la tabla de vias no guarda el sector y esta
 * lectura no lo publica»—, y lo hace en vez de ignorarlo porque una lista sin
 * filtrar bajo un filtro tecleado se lee como filtrada. Dejarlo en este tipo
 * seria dejar a mano el unico parametro de esta ruta que revienta la lectura.
 */
export type FiltrosDeVias = {
  codigoDeVia?: string;
  nombreDeCalle?: string;
  tipoDeVia?: string;
  activa?: boolean;
};

export function vias(
  filtros: FiltrosDeVias,
  pagina: Paginacion,
  senal?: AbortSignal,
): Promise<RespuestaPaginada<Via>> {
  return solicitar(RUTAS.vias, { parametros: { ...filtros, ...pagina }, senal });
}

/* ── El plano ───────────────────────────────────────────────────────────── */

export type LoteDelPlano = {
  predioId: number;
  codRefCatastral: string;
  direccion: string;
  codigoDeSector: string | null;
  codigoDeManzana: string | null;
  lote: string | null;
  estado: string;
  /** GeoJSON tal cual. Nadie lo dibuja todavia: el mapa no entra en este issue. */
  geometria: Record<string, unknown>;
};

export type PlanoCatastral = { lotes: LoteDelPlano[]; sinGeometria: number };

export function plano(
  filtros: { bbox?: string; codigoDeSector?: string; codigoDeManzana?: string; limite?: string },
  senal?: AbortSignal,
): Promise<PlanoCatastral> {
  return solicitar(RUTAS.plano, { parametros: { ...filtros }, senal });
}

/** `MarcoGeografico` NO pasa por `ConfiguracionDeJson`: sus cuatro `BigDecimal` viajan como NUMERO. */
export type MarcoGeografico = { oeste: number; sur: number; este: number; norte: number };

export type MarcoDelPlano = {
  marco: MarcoGeografico | null;
  lotes: number;
  notaDelMarco: string | null;
};

export function marcoDelPlano(
  filtros: { codigoDeSector?: string; codigoDeManzana?: string },
  senal?: AbortSignal,
): Promise<MarcoDelPlano> {
  return solicitar(RUTAS.marcoDelPlano, { parametros: { ...filtros }, senal });
}

/* ── Los frentes (ADR-0021, #7) ─────────────────────────────────────────── */

export type Frente = {
  id: number;
  viaId: number;
  viaCodigo: string;
  viaNombre: string;
  /** Ya llega como texto desde el backend: `frente.longitud().toString()`. */
  longitud: string;
  /** `PROPUESTA` mientras la derivo una maquina; confirmarla es un acto. */
  longitudEstado: string;
  esPrincipal: boolean;
  numeracion: string | null;
  retiro: string | null;
  confirmadoPor: string | null;
  confirmadoEn: string | null;
  geometria: string | null;
};

export type FrentesDelPredio = {
  predioId: number;
  frentes: Frente[];
  derivadoEn: string | null;
  frentesDerivados: number | null;
  /**
   * Por que la derivacion no propuso nada, por predio.
   *
   * No es decoracion: hoy no hay ni un eje de calzada cargado en ninguna
   * instalacion, asi que este campo es lo que de verdad se lee, y por eso la
   * pantalla lo ensena en vez de dibujar una lista vacia.
   */
  motivoDeLaDerivacion: string | null;
};

export function frentes(predioId: number, senal?: AbortSignal): Promise<FrentesDelPredio> {
  return solicitar(camino(RUTAS.frentes, { predioId }), { senal });
}

/* ── Los cuadros del ejercicio ──────────────────────────────────────────── */

export type Arancel = {
  id: number;
  viaId: number;
  tramo: string | null;
  valorM2: string;
  documentoFuente: string;
};

export function aranceles(ejercicio: number, senal?: AbortSignal): Promise<Arancel[]> {
  return solicitar(RUTAS.aranceles, { parametros: { ejercicio }, senal });
}

export type ValorUnitario = {
  id: number;
  partida: string;
  categoria: string;
  anioConstruccionDesde: number;
  anioConstruccionHasta: number | null;
  valorM2: string;
  documentoFuente: string;
};

export function valoresUnitarios(ejercicio: number, senal?: AbortSignal): Promise<ValorUnitario[]> {
  return solicitar(RUTAS.valoresUnitarios, { parametros: { ejercicio }, senal });
}

export type Depreciacion = {
  id: number;
  uso: string;
  material: string;
  estadoConservacion: string;
  antiguedadHasta: number | null;
  porcentaje: string;
  documentoFuente: string;
};

export function depreciacion(ejercicio: number, senal?: AbortSignal): Promise<Depreciacion[]> {
  return solicitar(RUTAS.depreciacion, { parametros: { ejercicio }, senal });
}
