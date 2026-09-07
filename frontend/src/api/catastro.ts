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
  /* La lectura de una ficha son CUATRO rutas y no una, igual que el alta: cada
     `@GetMapping` fija su `TipoFicha` —`/urbana/{cod}` lee `TipoFicha.UNICA` y
     nada mas— y exige el acceso de LECTURA de su opcion del menu. Pedir la ficha
     de un predio por la ruta que no es su tipo NO devuelve el bloque vacio:
     contesta 404 «El predio no tiene ficha urbana vigente al …». El nombre del
     parametro tambien cambia —`codEdificacion`, `codUnidad`—, y por eso
     `RUTA_DE_LA_FICHA` lleva la llave al lado de la ruta. */
  fichaUrbana: '/catastro/fichas/urbana/{codRefCatastral}',
  fichaEconomica: '/catastro/fichas/economica/{codRefCatastral}',
  fichaBienesComunes: '/catastro/fichas/bienes-comunes/{codEdificacion}',
  fichaRural: '/catastro/fichas/rural/{codUnidad}',
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

/**
 * Si un `tipo` que llego por el cable es uno de los cuatro.
 *
 * Hace falta porque `FichaEncontrada.tipo` viaja como `string` y con el se elige
 * a que ruta pedir la ficha: un valor que el enumerado no reconozca no puede
 * caer a la urbana «por defecto», porque esa ruta contesta la ficha UNICA y
 * ninguna otra. Sin esta guarda, el desenlace de un tipo desconocido seria pedir
 * la ficha equivocada y dibujarla como si fuera la suya.
 */
export function esTipoDeFicha(valor: string): valor is TipoDeFicha {
  return (TIPOS_DE_FICHA as readonly string[]).includes(valor);
}

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
  /** `AreaM2` -> texto: la cifra sola, y la unidad la pone la cabecera. */
  areaConstruida: string;
  anioConstruccion: number | null;
  material: string | null;
  estadoConservacion: string | null;
  /** Las siete partidas en una tira: `"[CCDCCDC]"`, con guion donde no se declara. */
  categorias: string;
  /** `Porcentaje` -> texto CON su signo: `"60.00 %"`. Nulo no es cero. */
  porcentajeConstruido: string | null;
};

/**
 * Una obra complementaria: cerco, piscina, tanque, pavimento.
 *
 * **No trae su valor, y esa ausencia es el dato.** El Anexo III de la R.M.
 * 277-2025-VIVIENDA —los valores unitarios a costo directo de obras
 * complementarias— **no esta transcrito** en el corpus, y `otra_instalacion`
 * **no tiene columna de importe**: no hay ni cuadro del que leerlo ni columna
 * donde estuviera declarado. Asi que `InstalacionResource` publica seis
 * componentes y ninguno es un importe, y la pantalla dibuja los seis y dice que
 * falta el septimo. Escribir aqui un `valor: string` obligaria a la pantalla a
 * pintar `undefined` o un cero, y un cero se lee como «esta obra no vale nada».
 *
 * `cantidad` lleva su unidad DENTRO —`"42.00 ML"`— porque «42» no significa lo
 * mismo en metros lineales que en unidades; `unidad` la repite suelta, que es lo
 * que una grilla pinta en su columna sin partir una cadena.
 */
export type Instalacion = {
  id: number;
  descripcion: string;
  unidad: string;
  /** `Medida` -> texto CON la unidad dentro: `"42.00 ML"`. */
  cantidad: string;
  anioConstruccion: number | null;
  estadoConservacion: string | null;
};

/**
 * Una actividad economica, con su licencia por numero.
 *
 * `licenciaNumero` nulo **no es un dato que falte: es el hallazgo**. Un local que
 * declara actividad y no declara licencia es lo que una fiscalizacion busca, asi
 * que la pantalla lo pinta distinto en vez de dejar la celda en blanco.
 */
export type Actividad = {
  id: number;
  conductor: string;
  nombreComercial: string | null;
  ciiu: string | null;
  /** `AreaM2` -> texto. */
  areaOcupada: string | null;
  licenciaNumero: string | null;
  licenciaFecha: string | null;
  anuncioNumero: string | null;
  anuncioFecha: string | null;
  vigenciaDesde: string | null;
};

/** El bloque de la ficha ECONOMICA. `sinLicencia` es un RECUENTO, no una lista. */
export type Economico = {
  actividades: Actividad[];
  informacionComplementaria: string | null;
  sinLicencia: number;
};

/** Un area comun con su antiguedad: se valoriza como una construccion mas. */
export type BienComun = {
  id: number;
  descripcion: string;
  /** `AreaM2` -> texto. */
  area: string;
  material: string | null;
  estadoConservacion: string | null;
  anioConstruccion: number | null;
};

/** Cuanto del area comun le toca a un predio de la edificacion. */
export type Participacion = {
  predioId: number;
  /** `Porcentaje` -> texto CON su signo: `"50.00 %"`. */
  porcentaje: string;
};

/** El bloque de la ficha de BIENES_COMUNES. */
export type BienesComunes = {
  bienes: BienComun[];
  participaciones: Participacion[];
  /** `AreaM2` -> texto. Lo SUMA el dominio, no esta pantalla. */
  areaComunTotal: string;
};

/** Un grupo de tierra de un predio rustico. En hectareas, nunca en metros. */
export type Tierra = {
  id: number;
  clasificacion: string;
  calidadAgrologica: string | null;
  riego: string;
  /** `Medida` -> texto CON la unidad dentro: `"1.0500 HA"`. */
  hectareas: string;
  /** Nulo es «esta ficha no reparte area comun», que no es repartir cero. */
  hectareasComunes: string | null;
};

/** Con quien linda el predio rustico por una orientacion. */
export type Colindante = { orientacion: string; descripcion: string };

/** El bloque de la ficha RURAL. */
export type Rural = {
  tierras: Tierra[];
  colindantes: Colindante[];
  /** `Medida` -> texto CON la unidad dentro. Lo SUMA el dominio. */
  hectareasTotales: string;
};

/**
 * Una fila del historico: **que rigio, cuando, quien lo escribio y por que**.
 *
 * La `observacion` es la mitad util. Un diff dice que el area paso de 120 a 180;
 * solo la observacion dice que fue una fiscalizacion de campo y no un error de
 * tecleo, y es lo que se lee en voz alta cuando el contribuyente pregunta por que
 * le subio el recibo.
 */
export type VersionDeLaFicha = {
  id: number;
  version: number;
  /** `AreaM2` -> texto. */
  areaTerreno: string;
  uso: string;
  vigenciaDesde: string;
  vigenciaHasta: string | null;
  vigente: boolean;
  origen: string;
  documentoOrigen: string;
  observacion: string;
  usuario: string;
  registradaEn: string;
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
  /** Viaja SIEMPRE: no es anulable y se construye en toda respuesta de ficha. */
  instalaciones: Instalacion[];
  /* Los tres bloques de detalle son NULOS SALVO EL QUE TOCA, y por eso son tres
     campos y no uno: una ficha rural no publica un bloque economico vacio, asi
     que «este predio no declara actividad» y «esta ficha no es de las que la
     declaran» se distinguen. La ficha UNICA no tiene ninguno —lo suyo son las
     construcciones—, que es lo que dice `DetalleDeLaFicha` con todas las letras. */
  economico: Economico | null;
  bienesComunes: BienesComunes | null;
  rural: Rural | null;
  /**
   * Todas las versiones de la ficha, **solo si se pidieron**.
   *
   * `null` significa «no lo pediste» —la peticion no llevo `?historico=true`— y
   * una lista vacia significaria «no hay ninguna», que no puede pasar: toda
   * ficha tiene al menos la version vigente. La distincion es del backend y esta
   * pantalla la conserva: pintar un «no hay movimientos» sobre un nulo seria
   * afirmar algo que nadie pregunto.
   */
  historico: VersionDeLaFicha[] | null;
};

/**
 * Los campos de la ficha, como lista en tiempo de ejecucion.
 *
 * Misma forma y mismo motivo que `CAMPOS_DEL_ALTA`, por el otro sentido del
 * cable: `verificaciones/rutas.mjs` la compara con los componentes de
 * `FichaResource.java`, y `CampoDeFichaSinPareja` impide que la lista y el tipo
 * se separen.
 *
 * **Y hace falta**: un campo que el servidor manda y el tipo no declara no da
 * ningun error —el JSON llega entero y `tsc` solo se queja el dia que alguien
 * intenta leerlo—, asi que el hueco es invisible hasta que alguien lo necesita y
 * descubre que tiene que escribir un `as`. Con #46 los veintidos componentes
 * estan declarados; el que declare el vigesimotercero se encuentra esta lista.
 */
export const CAMPOS_DE_FICHA = [
  'id',
  'predioId',
  'tipo',
  'version',
  'areaTerreno',
  'uso',
  'frontis',
  'condicionPropiedad',
  'tipoEdificacion',
  'vigenciaDesde',
  'vigenciaHasta',
  'vigente',
  'origen',
  'documentoOrigen',
  'observacion',
  'denominacion',
  'construcciones',
  'instalaciones',
  'economico',
  'bienesComunes',
  'rural',
  'historico',
] as const;

/**
 * Lo que `FichaResource` publica y este tipo NO declara, con su motivo.
 *
 * **Esta vacia, y es correcto que se vea asi.** Nace de #41, donde nombraba los
 * cinco componentes que el tipo no declaraba —`instalaciones`, `economico`,
 * `bienesComunes`, `rural` e `historico`—, y #46 los declaro y los dibujo los
 * cinco. Lo que queda es el mecanismo: `rutas.mjs` exige que todo componente del
 * `record` este **o en `CAMPOS_DE_FICHA` o aqui con su motivo**, asi que el
 * componente numero veintitres que alguien anada al backend saldra en rojo con
 * su nombre en vez de llegar al navegador sin que nadie lo declare. Un hueco
 * declarado se puede discutir; uno callado se descubre el dia que hace falta.
 */
export const CAMPOS_DE_FICHA_QUE_NO_SE_LEEN: Readonly<Record<string, string>> = {};

type CampoDeFicha = (typeof CAMPOS_DE_FICHA)[number];
type FichaSoloEnElTipo = Exclude<keyof Ficha, CampoDeFicha>;
type FichaSoloEnLaLista = Exclude<CampoDeFicha, keyof Ficha>;

/** Que el tipo y la lista digan lo mismo. Si divergen, esto no compila. */
export type CampoDeFichaSinPareja = [FichaSoloEnElTipo] extends [never]
  ? [FichaSoloEnLaLista] extends [never]
    ? true
    : ['sobra en CAMPOS_DE_FICHA', FichaSoloEnLaLista]
  : ['falta en CAMPOS_DE_FICHA', FichaSoloEnElTipo];

export const LOS_CAMPOS_DE_FICHA_CUADRAN: CampoDeFichaSinPareja = true;

/**
 * A que ruta se le pide la ficha segun su clase, y **con que nombre de camino**.
 *
 * Las cuatro devuelven el MISMO `FichaResource`; lo que cambia es el `TipoFicha`
 * que la ruta fija y el acceso de LECTURA que exige. Elegir mal no da un bloque
 * vacio: `FichaController.leer` contesta **404** «El predio no tiene ficha
 * urbana vigente al …», porque `/urbana/{cod}` busca `TipoFicha.UNICA` y ninguna
 * otra. Y la ficha UNICA **no tiene ningun bloque de detalle** —lo dice
 * `DetalleDeLaFicha`: «lo suyo son las construcciones»—, asi que pedirlo todo
 * por la ruta urbana devolveria `economico`, `bienesComunes` y `rural` nulos
 * SIEMPRE, y una pantalla que los dibujara nunca se ejercitaria.
 *
 * La llave del camino cambia con la ruta —`codEdificacion` en bienes comunes,
 * `codUnidad` en rural— y por eso viaja al lado: `camino()` exige el nombre
 * exacto y lanza si falta, que es como se ve el error al escribirlo y no al
 * pedirlo.
 */
export const RUTA_DE_LA_FICHA: Record<TipoDeFicha, { ruta: string; llave: string }> = {
  UNICA: { ruta: RUTAS.fichaUrbana, llave: 'codRefCatastral' },
  ECONOMICA: { ruta: RUTAS.fichaEconomica, llave: 'codRefCatastral' },
  BIENES_COMUNES: { ruta: RUTAS.fichaBienesComunes, llave: 'codEdificacion' },
  RURAL: { ruta: RUTAS.fichaRural, llave: 'codUnidad' },
};

export type OpcionesDeLaFicha = {
  /**
   * Que traiga tambien todas las versiones (`?historico=true`).
   *
   * **Solo viaja cuando se pide.** El backend ya toma `false` por omision, y
   * mandar `historico=false` costaria las versiones de todas las lecturas que no
   * las pintan: son la ficha entera repetida una vez por version, y la pantalla
   * que solo dibuja la vigente no tiene por que pagarlas.
   */
  historico?: boolean;
  /** La ficha VIGENTE a esta fecha. Sin ella, la vigente al reloj del servidor. */
  fecha?: string;
};

/**
 * La ficha vigente de un predio, entera.
 *
 * Sustituye a `fichaUrbana`, que servia una sola de las cuatro clases y **no
 * ofrecia `?historico=`** aunque `FichaController.urbana` lo declara: sin el
 * parametro, la pestana «Movimientos del Predio» —que `ResumenPredialController`
 * documenta como ya publicada por esta ruta— no se podia pedir desde esta capa.
 */
export function ficha(
  tipo: TipoDeFicha,
  codigo: string,
  opciones: OpcionesDeLaFicha = {},
  senal?: AbortSignal,
): Promise<Ficha> {
  const { ruta, llave } = RUTA_DE_LA_FICHA[tipo];
  return solicitar(camino(ruta, { [llave]: codigo }), {
    parametros: { fecha: opciones.fecha, historico: opciones.historico === true ? true : undefined },
    senal,
  });
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

/* ── De donde sale cada lista de este modulo ─────────────────────────────── */

/**
 * El enumerado del backend del que sale cada lista, por su nombre de clase.
 *
 * No es documentacion: lo lee `verificaciones/rutas.mjs`, que abre ese `.java`,
 * le saca las constantes y compara los dos conjuntos **en los dos sentidos**.
 *
 * <h2>Por que el pareo se declara y no se adivina</h2>
 *
 * Porque adivinarlo por el nombre no funciona, y esta medido: `TITULARIDADES`
 * se parece a `CondicionDeTitularidad` y **no tiene nada que ver con el** —es el
 * filtro del padron, `TitularidadDelPredio`—, asi que un pareo por parecido de
 * nombre da un desajuste falso de seis valores donde no hay ninguno. Y una
 * guarda que grita en lo correcto se acaba apagando.
 *
 * <h2>Por que los dos sentidos, y por que no se arreglan en el mismo sitio</h2>
 *
 * Un valor que el enumerado tiene y la lista no ofrece es una opcion que **no se
 * puede elegir**: no hay error, no hay aviso, y el tecnico simplemente no la
 * encuentra. Se arregla AQUI, anadiendolo a la lista. Un valor que la lista
 * ofrece y el enumerado no admite es un **422 al enviar**, despues de rellenar
 * los seis pasos del asistente: se ve, pero tarde y en el sitio equivocado, y se
 * arregla quitandolo de aqui o anadiendolo al enumerado, que es `backend/`.
 */
export const LISTAS_DERIVADAS_DE_UN_ENUM: Readonly<Record<string, string>> = {
  ESTADOS_DE_PREDIO: 'EstadoPredio',
  TIPOS_DE_PREDIO: 'TipoPredio',
  TITULARIDADES: 'TitularidadDelPredio',
  TIPOS_DE_FICHA: 'TipoFicha',
  ORIGENES_DE_FICHA: 'OrigenDeLaFicha',
  MATERIALES: 'MaterialEstructural',
  ESTADOS_DE_CONSERVACION: 'EstadoDeConservacion',
  CONDICIONES_DE_TITULARIDAD: 'CondicionDeTitularidad',
  ORIENTACIONES: 'Orientacion',
};

/**
 * Las listas de este modulo que NO salen de ningun enumerado, con su motivo.
 *
 * `rutas.mjs` exige que **toda** lista exportada por `src/api/` este o aqui o en
 * `LISTAS_DERIVADAS_DE_UN_ENUM`: es lo que impide que la siguiente nazca sin
 * nadie que la mire, que es como llegaron estas nueve. Un motivo que dice quien
 * la contrasta se puede discutir; una lista callada se descubre el dia que el
 * backend anade un valor y el desplegable sigue ofreciendo los de antes.
 */
export const LISTAS_QUE_NO_SALEN_DE_UN_ENUM: Readonly<Record<string, string>> = {
  CAMPOS_DE_FICHA:
    'Son los componentes del `record` «FichaResource», no un enumerado. Los contrasta el punto 7 ' +
    'de `rutas.mjs`, que ademas admite huecos declarados en «CAMPOS_DE_FICHA_QUE_NO_SE_LEEN».',
  CAMPOS_DEL_ALTA:
    'Son los componentes del `record` «FichaController.PeticionDeAlta», no un enumerado. Los ' +
    'contrasta el punto 4 de `rutas.mjs`, y ademas «CampoDelAltaSinPareja» los ata al tipo ' +
    '«PeticionDeAlta» de este mismo modulo en tiempo de compilacion.',
  CATEGORIAS_CONSTRUCTIVAS:
    'Son las siete partidas del `record` «CategoriasConstructivas» y no un enumerado, y ademas ' +
    'sus claves NO se llaman como sus componentes —«categoriaMuros» aqui, «muros» alli—: viajan ' +
    'con ese prefijo dentro de «ConstruccionDeclarada». No hay conjunto que comparar letra por ' +
    'letra sin inventar la traduccion, que es justo lo que una guarda no debe hacer.',
};
