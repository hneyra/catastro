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
  /* El alta: CUATRO rutas y no una, una por clase de ficha. No es una manera
     de organizar el contrato: cada `@PostMapping` exige el `REGISTRO` de SU
     opcion del menu —`ficha_urbana`, `ficha_economica`, `ficha_bienes`,
     `ficha_rural`—, asi que quien levanta el catastro rural no puede abrir una
     ficha economica. Con una sola ruta y el tipo en el cuerpo, ese permiso no
     se podria expresar. */
  altaUrbana: '/catastro/fichas/urbana',
  altaEconomica: '/catastro/fichas/economica',
  altaBienesComunes: '/catastro/fichas/bienes-comunes',
  altaRural: '/catastro/fichas/rural',
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

/* ── El alta de una ficha (#34) ─────────────────────────────────────────── */

/**
 * Como se compone el codigo de referencia catastral. **En un solo sitio.**
 *
 * <h2>El largo es del tenant, y por eso no hay ningun 23 por la pantalla</h2>
 *
 * ADR-0036 cierra D-10 diciendo dos cosas: que el codigo de referencia
 * catastral es **municipal y de largo del tenant**, y que el CUC del SNCP —12
 * posiciones— es **otro identificador**, con columna propia desde `V6`. El
 * backend lo sostiene con `ComposicionCatastral`, que es un parametro y no una
 * constante: `CodigoReferenciaCatastral.componer` recibe la composicion y
 * rellena cada tramo con ceros a la izquierda, precisamente para que nadie
 * escriba «dos para el sector, tres para la manzana» una segunda vez.
 *
 * Aqui se hace lo mismo: los ocho campos del formulario, su largo y el orden en
 * que se concatenan salen de esta lista y de ninguna otra parte. Cambiar la
 * composicion de un tenant es cambiar esta lista.
 *
 * <h2>Ocho tramos aqui y DIEZ en el backend, y los dos suman lo mismo</h2>
 *
 * El artboard teclea el **ubigeo entero** en un solo campo de seis digitos
 * —«Distrito»—, y `ComposicionCatastral.DEL_MANUAL` lo reparte en
 * `departamento`, `provincia` y `distrito`, de dos cada uno. No son dos
 * composiciones distintas: son la misma leida con distinto grano, y por eso
 * `CodigoReferenciaCatastral.ubigeo()` es exactamente esos seis digitos. `cubre`
 * dice que tramos del backend absorbe cada campo, y `verificaciones/rutas.mjs`
 * comprueba que el reparto cuadra tramo a tramo contra el fuente de Java: sin
 * eso, un campo de mas o de menos compondria un codigo plausible que no casa con
 * nada, que es el modo de fallo que `componer` existe para evitar.
 */
export const COMPOSICION_DEL_CODIGO = {
  constante: 'ComposicionCatastral.DEL_MANUAL',
  tramos: [
    { k: 'ubigeo', label: 'Distrito', digitos: 6, cubre: ['departamento', 'provincia', 'distrito'] },
    { k: 'sector', label: 'Sector', digitos: 2, cubre: ['sector'] },
    { k: 'manzana', label: 'Manzana', digitos: 3, cubre: ['manzana'] },
    { k: 'lote', label: 'Lote', digitos: 3, cubre: ['lote'] },
    { k: 'edificacion', label: 'Edific.', digitos: 2, cubre: ['edificacion'] },
    { k: 'entrada', label: 'Entr.', digitos: 2, cubre: ['entrada'] },
    { k: 'piso', label: 'Piso', digitos: 2, cubre: ['piso'] },
    { k: 'unidad', label: 'Unidad', digitos: 3, cubre: ['unidad'] },
  ],
} as const;

export type TramoDelCodigo = (typeof COMPOSICION_DEL_CODIGO.tramos)[number];

/** Cuantas posiciones tiene el codigo con la composicion vigente. */
export const LARGO_DEL_CODIGO: number = COMPOSICION_DEL_CODIGO.tramos.reduce((a, t) => a + t.digitos, 0);

/**
 * Arma el codigo tramo a tramo, rellenando con ceros a la izquierda.
 *
 * Es lo mismo que hace `CodigoReferenciaCatastral.componer` en el backend, y por
 * el mismo motivo: `fichas.csv` **no trae el codigo completo** —sus diez
 * primeras columnas son los tramos—, asi que quien lo escribe a mano acaba
 * rellenando ceros a ojo. Un tramo que no se teclea vale cero, que es lo
 * correcto para un predio sin edificacion, sin entrada, sin piso y sin unidad.
 */
export function componerCodigo(porTramo: Readonly<Record<string, string>>): string {
  return COMPOSICION_DEL_CODIGO.tramos
    .map((t) => (porTramo[t.k] ?? '').padStart(t.digitos, '0'))
    .join('');
}

/** Los cuatro `OrigenDeLaFicha`, letra por letra como los nombra el enumerado. */
export const ORIGENES_DE_FICHA = ['DECLARACION_JURADA', 'FISCALIZACION', 'RESOLUCION', 'MIGRACION'] as const;

/** Los seis `MaterialEstructural`. */
export const MATERIALES = ['CONCRETO', 'LADRILLO', 'ADOBE', 'MADERA', 'QUINCHA', 'OTRO'] as const;

/** Los cinco `EstadoDeConservacion`. */
export const ESTADOS_DE_CONSERVACION = ['MUY_BUENO', 'BUENO', 'REGULAR', 'MALO', 'RUINOSO'] as const;

/** Las seis `CondicionDeTitularidad`. */
export const CONDICIONES_DE_TITULARIDAD = [
  'PROPIETARIO_UNICO',
  'COPROPIETARIO',
  'CONYUGE',
  'POSEEDOR',
  'SUCESION',
  'USUFRUCTUARIO',
] as const;

/** Las cuatro `Orientacion` de un colindante rural. */
export const ORIENTACIONES = ['NORTE', 'SUR', 'ESTE', 'OESTE'] as const;

/**
 * Las siete partidas de `CategoriasConstructivas`, en su orden.
 *
 * El artboard escribe cinco —«muros y columnas, techos, pisos, puertas y
 * ventanas, y revestimientos»— y el `record` del backend lleva **siete**: le
 * anade banios e instalaciones. Es el mismo desajuste que ya nombra
 * `MOTIVOS.sietePartidas` en la matriz de valores unitarios, y se resuelve
 * igual: se declaran las que el backend acepta.
 */
export const CATEGORIAS_CONSTRUCTIVAS = [
  { k: 'categoriaMuros', label: 'Muros y columnas' },
  { k: 'categoriaTechos', label: 'Techos' },
  { k: 'categoriaPisos', label: 'Pisos' },
  { k: 'categoriaPuertas', label: 'Puertas y ventanas' },
  { k: 'categoriaRevestimientos', label: 'Revestimientos' },
  { k: 'categoriaBanios', label: 'Banios' },
  { k: 'categoriaInstalaciones', label: 'Instalaciones' },
] as const;

/** Lo construido en un piso (`DeclaracionDeFicha.ConstruccionDeclarada`). Ningun importe. */
export type ConstruccionDeclarada = {
  piso?: string;
  /** `AreaM2` -> texto, nunca `Number`. */
  areaConstruida?: string;
  /** Un ANO, no una antiguedad: el backend lo lee con `new Ejercicio(anio)`. */
  anioConstruccion?: number;
  material?: string;
  estadoConservacion?: string;
  categoriaMuros?: string;
  categoriaTechos?: string;
  categoriaPisos?: string;
  categoriaPuertas?: string;
  categoriaRevestimientos?: string;
  categoriaBanios?: string;
  categoriaInstalaciones?: string;
};

/** Una obra complementaria (`DeclaracionDeFicha.InstalacionDeclarada`). */
export type InstalacionDeclarada = {
  descripcion?: string;
  cantidad?: string;
  unidad?: string;
  anioConstruccion?: number;
  estadoConservacion?: string;
};

/** Con quien linda un predio rustico por una orientacion. */
export type ColindanteDeclarado = { orientacion?: string; descripcion?: string };

/** El detalle de la ficha rural. Solo lo admite la ruta rural: en las otras es 422. */
export type RuralDeclarado = { tierras?: never[]; colindantes?: ColindanteDeclarado[] };

/** El titular inicial, por su codigo del padron de `rentas`. */
export type TitularDeclarado = {
  codigoContribuyente?: string;
  condicion?: string;
  /** `Porcentaje` -> texto. */
  porcentaje?: string;
  documentoOrigen?: string;
};

/**
 * El cuerpo de un alta de ficha, campo por campo como el `record` del backend.
 *
 * <h2>Los nombres son los del `record`, y hay quien lo comprueba</h2>
 *
 * `FichaController.PeticionDeAlta` es una **lista blanca**: lo que no esta en el
 * `record` no entra, **aunque llegue en el JSON**. O sea que un campo mal
 * escrito aqui no da error en ninguna parte —ni al compilar, ni al pedir, ni en
 * la respuesta—: el servidor contesta `201`, la ficha se crea, y el dato no esta
 * en ningun sitio. Por eso `verificaciones/rutas.mjs` compara los campos de este
 * tipo con los del `record` de Java, uno a uno.
 *
 * <h2>Todos son `@Nullable`, y aun asi hay seis obligatorios</h2>
 *
 * La validacion es **en tiempo de ejecucion**: `DeclaracionDeFicha.exigir` lanza
 * `422 VALIDACION` nombrando el campo. Los que exige son `codRefCatastral`,
 * `direccion`, `areaTerreno`, `uso`, `documentoOrigen` y la `observacion`
 * (regla 10) — y `titular.codigoContribuyente`, `titular.condicion` y
 * `titular.documentoOrigen` en cuanto el bloque `titular` viaja.
 *
 * **`direccion` es obligatoria y el issue no la lista.** Medido en
 * `FichaController.predioDeclarado`: `exigir(peticion.direccion(), "direccion")`.
 * Sin ella el alta es un 422 en el primer campo que el servidor mira, asi que se
 * compone con la via del catalogo y el numero municipal —igual que el artboard
 * compone el titulo de la ficha— y se ensena en el resumen antes de confirmar.
 */
export type PeticionDeAlta = {
  /** Regla 10: sin observacion no se guarda. `422` si llega vacia. */
  observacion?: string;
  codRefCatastral?: string;
  tipoPredio?: string;
  direccion?: string;
  codigoDeVia?: string;
  numeroMunicipal?: string;
  codigoDeSector?: string;
  codigoDeManzana?: string;
  lote?: string;
  ubigeo?: string;
  /** `AreaM2` -> texto. */
  areaTerreno?: string;
  uso?: string;
  denominacion?: string;
  vigenciaDesde?: string;
  origen?: string;
  documentoOrigen?: string;
  construcciones?: ConstruccionDeclarada[];
  instalaciones?: InstalacionDeclarada[];
  rural?: RuralDeclarado;
  titular?: TitularDeclarado;
};

/**
 * Los campos del `record`, como lista en tiempo de ejecucion.
 *
 * Existe para que `verificaciones/rutas.mjs` pueda compararlos con el fuente de
 * Java sin analizar TypeScript, y **no puede separarse del tipo**: si divergen,
 * `LOS_CAMPOS_DEL_ALTA_CUADRAN` deja de compilar.
 *
 * Faltan tres a proposito —`economico`, `bienesComunes` y el `tierras` de
 * `rural`—: el artboard no dibuja ningun paso que los recoja, y declararlos aqui
 * sin sitio de donde sacarlos seria prometer un contrato que esta pantalla no
 * sabe llenar. `rutas.mjs` los nombra en vez de callarlos.
 */
export const CAMPOS_DEL_ALTA = [
  'observacion',
  'codRefCatastral',
  'tipoPredio',
  'direccion',
  'codigoDeVia',
  'numeroMunicipal',
  'codigoDeSector',
  'codigoDeManzana',
  'lote',
  'ubigeo',
  'areaTerreno',
  'uso',
  'denominacion',
  'vigenciaDesde',
  'origen',
  'documentoOrigen',
  'construcciones',
  'instalaciones',
  'rural',
  'titular',
] as const;

type CampoDelAlta = (typeof CAMPOS_DEL_ALTA)[number];
type SoloEnElTipo = Exclude<keyof PeticionDeAlta, CampoDelAlta>;
type SoloEnLaLista = Exclude<CampoDelAlta, keyof PeticionDeAlta>;

/**
 * Que el tipo y la lista digan lo mismo. Si divergen, esto no compila.
 *
 * Una lista de nombres al lado de un tipo es una copia, y una copia se queda
 * vieja en silencio: quien anada un campo al tipo y no a la lista dejaria el
 * arnes comparando dieciocho de diecinueve, en verde.
 */
export type CampoDelAltaSinPareja = [SoloEnElTipo] extends [never]
  ? [SoloEnLaLista] extends [never]
    ? true
    : ['sobra en CAMPOS_DEL_ALTA', SoloEnLaLista]
  : ['falta en CAMPOS_DEL_ALTA', SoloEnElTipo];

export const LOS_CAMPOS_DEL_ALTA_CUADRAN: CampoDelAltaSinPareja = true;

/**
 * A que ruta va el alta segun la clase de ficha.
 *
 * Las cuatro reciben el **mismo** cuerpo; lo que cambia es el `TipoFicha` que la
 * ruta declara y el acceso que exige. Un bloque de detalle que no sea el del
 * tipo es `422` y no un campo ignorado en silencio.
 */
export const RUTA_DEL_ALTA: Record<TipoDeFicha, string> = {
  UNICA: RUTAS.altaUrbana,
  ECONOMICA: RUTAS.altaEconomica,
  BIENES_COMUNES: RUTAS.altaBienesComunes,
  RURAL: RUTAS.altaRural,
};

/**
 * Inscribe la primera version de la ficha, y el predio si no estaba (`201`).
 *
 * Los tres rechazos se distinguen por su `codigo` y **hay que tratarlos por
 * separado**, porque quien atiende tiene que hacer cosas distintas:
 * `VALIDACION` (422) se arregla corrigiendo un campo de esta pantalla;
 * `CONFLICTO` (409) dice que el codigo ya esta inscrito —o que ese predio ya
 * tiene ficha de este tipo, y entonces lo que toca es actualizarla—; y
 * `NO_ENCONTRADO` (404) dice que la via, el sector o la manzana no existen
 * todavia, que se arregla en Territorio y no aqui.
 */
export function inscribirFicha(
  tipo: TipoDeFicha,
  peticion: PeticionDeAlta,
  senal?: AbortSignal,
): Promise<Ficha> {
  return solicitar(RUTA_DEL_ALTA[tipo], { metodo: 'POST', cuerpo: peticion, senal });
}
