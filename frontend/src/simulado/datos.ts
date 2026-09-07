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
import { DETALLE_DE_FICHAS, MANZANAS, PADRON, SECTORES, VIAS } from './padron';
import type { FilaDelPadron } from './padron';
import type { Ficha, VersionDeLaFicha } from '../api/catastro';

export { MANZANAS, PADRON, SECTORES, VIAS };
export type { FilaDelPadron };

/* ── El detalle de las fichas, y sus DOS versiones ───────────────────────── */

/**
 * El detalle generado, indexado por codigo de referencia catastral.
 *
 * El tipo se escribe **aqui** y no en `padron.ts`, que es un archivo generado, y
 * se declara `| undefined` a proposito: uno de los 23 predios —Jiron Cusco 900,
 * un terreno sin construir— no tiene ni una fila en `detalle-de-fichas.csv`, y
 * `Record<string, T>` a secas afirmaria que si. Ese predio es un caso de verdad
 * y no un olvido: la pantalla tiene que saber dibujar una ficha sin nada
 * edificado.
 */
export const DETALLE: Readonly<Record<string, DetalleDeLaFicha | undefined>> = DETALLE_DE_FICHAS;

export type DetalleDeLaFicha = {
  tipoFicha: string;
  vigenciaDesde: string;
  origen: string;
  documentoOrigen: string;
  construcciones: Ficha['construcciones'];
  instalaciones: Ficha['instalaciones'];
  economico: Ficha['economico'];
  bienesComunes: Ficha['bienesComunes'];
  rural: Ficha['rural'];
};

/**
 * Con que nombre firma la auditoria cada una de las dos cargas, y por que.
 *
 * No se inventan: son los valores por omision que declaran
 * `DatosDeCargaFichasDemo` y `DatosDeCargaDetalleFichasDemo` cuando el guion no
 * los pasa, que es lo que hace `infra/carga-de-datos/`. La observacion es la
 * mitad util del historico —un diff dice que el area cambio; solo la observacion
 * dice por que—, asi que copiar aqui otra cosa dejaria la pestana de movimientos
 * ensenando una explicacion que la instalacion de verdad no da.
 */
const CARGA = {
  usuario: 'carga-demostracion',
  siembra: 'Siembra de predios y fichas ficticios para la demostracion (#290)',
  detalle: 'Siembra del detalle de las fichas ficticias para la demostracion',
} as const;

/** El identificador de una version de ficha. Distinto por version, no por predio. */
export function idDeLaVersion(predioId: number, version: number): number {
  return predioId * 10 + version;
}

/** El dia anterior a una fecha ISO. Es aritmetica sobre un dato, no un reloj. */
function elDiaAnterior(fecha: string): string {
  const dia = new Date(`${fecha}T00:00:00Z`);
  dia.setUTCDate(dia.getUTCDate() - 1);
  return dia.toISOString().slice(0, 10);
}

/**
 * Las versiones de la ficha de un predio, de la mas antigua a la vigente.
 *
 * **Son dos y no una, y eso es lo que carga el archivo de ejemplo**: su cabecera
 * lo dice con todas las letras —«VERSIONA, NO SOBRESCRIBE. Cada predio de este
 * archivo acaba con DOS versiones de ficha»—. La primera la inscribe
 * `fichas.csv`; la segunda la abre `detalle-de-fichas.csv` con su propio
 * documento de origen, y al abrirla `ActualizarFichaCatastral` **cierra la
 * anterior el dia antes** (`vigente.cerradaEl(desde.minusDays(1))`). El predio
 * sin detalle se queda con una sola, vigente y sin cerrar.
 *
 * `registradaEn` es lo unico sintetico: el archivo no trae marca de tiempo y
 * este proxy no tiene reloj, asi que se deriva de la vigencia para que la misma
 * peticion conteste siempre lo mismo.
 */
export function versionesDe(p: FilaDelPadron): VersionDeLaFicha[] {
  const detalle = DETALLE[p.codRefCatastral];
  const primera: VersionDeLaFicha = {
    id: idDeLaVersion(p.predioId, 1),
    version: 1,
    areaTerreno: p.areaTerreno,
    uso: p.uso,
    vigenciaDesde: p.vigenciaDesde,
    vigenciaHasta: detalle === undefined ? null : elDiaAnterior(detalle.vigenciaDesde),
    vigente: detalle === undefined,
    origen: p.origen,
    documentoOrigen: p.documentoOrigen,
    observacion: CARGA.siembra,
    usuario: CARGA.usuario,
    registradaEn: `${p.vigenciaDesde}T00:00:00Z`,
  };
  if (detalle === undefined) return [primera];
  return [
    primera,
    {
      id: idDeLaVersion(p.predioId, 2),
      version: 2,
      /* El area y el uso NO cambian al versionar por detalle: `fichas.actualizar`
         no los recibe, los copia de la vigente. Lo que cambia es lo que hay
         DENTRO de la ficha. */
      areaTerreno: p.areaTerreno,
      uso: p.uso,
      vigenciaDesde: detalle.vigenciaDesde,
      vigenciaHasta: null,
      vigente: true,
      origen: detalle.origen,
      documentoOrigen: detalle.documentoOrigen,
      observacion: CARGA.detalle,
      usuario: CARGA.usuario,
      registradaEn: `${detalle.vigenciaDesde}T00:00:00Z`,
    },
  ];
}

/** La version vigente de la ficha de un predio: la ultima de las suyas. */
export function versionVigenteDe(p: FilaDelPadron): VersionDeLaFicha {
  const versiones = versionesDe(p);
  return versiones[versiones.length - 1]!;
}

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

/**
 * El ejercicio del que hablan los cuadros de abajo.
 *
 * **Es el unico ano que este proxy sella**, y desde #48 la barra global pide el
 * **ano en curso**: el 1 de enero de 2027 dejan de coincidir, y a partir de ese
 * dia la demostracion ensenara «el ejercicio 2027 no tiene un conjunto de
 * parametros sellado» en vez de tres cuadros. **Eso es correcto y es lo que hara
 * la instalacion de verdad** mientras `normativa` no selle 2027 —una lista de
 * anos congelada ensenando los cuadros del anterior como si fueran los del ano
 * en curso es justo el defecto que #48 cierra—, y `mirar` sigue en verde porque
 * un 404 es una respuesta.
 *
 * Tenia ademas un efecto de rebote, y **ya no**: con la aplicacion pidiendo un ano
 * que este proxy no sella, `yarn errores` daba **21 problemas sobre 36 renders**
 * —los 6 titulos de la superficie `aranceles-sin-el-catalogo-vial` mas sus 15
 * pares byte a byte, que es `C(6,2)`—, porque el 404 del cuadro tapaba los seis
 * rechazos que esa superficie existe para distinguir, y sus mensajes mandaban a
 * mirar al sitio equivocado. O sea que el flujo bloqueante se habria puesto rojo
 * el 1 de enero de 2027 **sin que nadie hubiera cambiado una linea**. Se cerro
 * fijandole a ese arnes el dia en que mira, derivado de esta misma constante: mide
 * como se ven los rechazos, no el calendario. Comprobado poniendo aqui otro ano:
 * antes 21 problemas, ahora 36 renders y verde.
 *
 * Se cierra con **#51** —preguntarle al backend que ejercicios tienen conjunto
 * sellado y ofrecer esos— o sellando aqui el ano en curso, que es una decision:
 * este proxy dice tres lineas mas abajo que **no tiene reloj** a proposito.
 */
export const EJERCICIO = 2026;

/**
 * La fecha con la que contestan las lecturas «a la fecha».
 *
 * Es una constante y no `new Date()` a proposito: el proxy no tiene reloj, y una
 * respuesta que cambia sola convierte cualquier captura del arnes en una que no
 * se puede volver a producir.
 */
export const HOY = '2026-09-06';

/* ── Zonificacion (urbano) ──────────────────────────────────────────────── */

export const ZONA = {
  aLaFecha: HOY,
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
  aLaFecha: HOY,
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
  aLaFecha: HOY,
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

/**
 * El ciclo de fiscalizacion, sembrado **en todos sus estados a la vez**.
 *
 * <h2>Por que hay un candidato en cada uno de los cuatro estados</h2>
 *
 * Porque lo que la pantalla ofrece se deriva del estado de la fila
 * (`TRANSICIONES_DEL_CANDIDATO`), y con un solo estado sembrado las demas ramas
 * serian codigo que ninguna vista alcanza: los arneses informarian en verde
 * sobre las tres cuartas partes de la cola. Con los cuatro, una sola carga de la
 * pagina de candidatos ensena las dos compuertas, el descarte con su etapa y los
 * dos terminales, y `verificaciones/transiciones.mjs` puede comparar lo ofrecido
 * con lo declarado **en los cuatro**.
 *
 * <h2>Y los valores son los del enumerado, que antes NO lo eran</h2>
 *
 * Hasta #71 esta siembra contestaba `EN_CURSO`, `PASO_GABINETE`, `GABINETE` como
 * origen, `CRUCE`, `OMISO` y `FOTOGRAFIA`: **seis valores que ningun enumerado
 * del backend admite**, pintados tal cual en pantalla. No lo cazaba nada porque
 * `src/api/fiscalizacion.ts` no declaraba ni una lista, y el punto 8 de
 * `rutas.mjs` solo mira las que estan declaradas. Ahora las declara las siete, y
 * ademas el estado de cada fila decide que se le puede hacer — de modo que un
 * estado inventado deja la fila sin ningun acto y sale rojo.
 */
export const CAMPANIA = {
  id: 1,
  codigo: 'CAM-2026-001',
  nombre: 'Subvaluacion en el cercado',
  estado: 'ABIERTA',
  inicio: '2026-03-02',
  fin: null,
  umbral: '0.15',
  /* Lo exige el borde y no tiene valor por omision (#25): sin el, la tasa de
     descarte sale de un conjunto recortado por una cifra que nadie puede leer. */
  tope: 500,
};

export const CANDIDATOS = [
  {
    id: 1,
    campaniaId: 1,
    predioId: 3,
    clase: 'SUBVALUADOR',
    origen: 'ORTOFOTO',
    score: '0.82',
    insumos: 'Area declarada frente a huella levantada',
    estado: 'DETECTADO',
    etapaDeDescarte: null,
    motivoDeDescarte: null,
    descartadoPor: null,
  },
  {
    id: 2,
    campaniaId: 1,
    predioId: 7,
    clase: 'SUBVALUADOR',
    origen: 'CRUCE_DE_AREAS',
    score: '0.61',
    insumos: 'Area de la ficha frente a la del lote levantado',
    estado: 'ADMITIDO_EN_GABINETE',
    etapaDeDescarte: null,
    motivoDeDescarte: null,
    descartadoPor: null,
  },
  {
    id: 3,
    campaniaId: 1,
    predioId: 3,
    clase: 'SUBVALUADOR',
    origen: 'DRON',
    score: '0.91',
    insumos: 'Vuelo del sector, techos no declarados en el patio interior',
    estado: 'VERIFICADO_EN_CAMPO',
    etapaDeDescarte: null,
    motivoDeDescarte: null,
    descartadoPor: null,
  },
  {
    id: 4,
    campaniaId: 1,
    predioId: 12,
    clase: 'SUBVALUADOR',
    origen: 'DENUNCIA',
    score: '0.55',
    insumos: 'Denuncia vecinal por ampliacion sin licencia',
    estado: 'DESCARTADO',
    etapaDeDescarte: 'GABINETE',
    motivoDeDescarte: 'La diferencia cae dentro del error de restitucion',
    descartadoPor: 'v.reto',
  },
  /* Los dos omisos van SIN predio, y no es un hueco: un omiso catastral es un
     techo sin fila de predio, asi que exigirle uno obligaria a inventar el
     predio que se afirma que falta. */
  {
    id: 5,
    campaniaId: 1,
    predioId: null,
    clase: 'OMISO_CATASTRAL',
    origen: 'BARRIDO_DE_CAMPO',
    score: '0.74',
    insumos: 'Edificacion levantada sin ninguna ficha vigente en la manzana',
    estado: 'DETECTADO',
    etapaDeDescarte: null,
    motivoDeDescarte: null,
    descartadoPor: null,
  },
  {
    id: 6,
    campaniaId: 1,
    predioId: null,
    clase: 'OMISO_CATASTRAL',
    origen: 'ORTOFOTO',
    score: '0.68',
    insumos: 'Techo en la ortofoto sin predio inscrito debajo',
    estado: 'VERIFICADO_EN_CAMPO',
    etapaDeDescarte: null,
    motivoDeDescarte: null,
    descartadoPor: null,
  },
];

export const TASA_DE_DESCARTE = {
  detectados: 6,
  descartadosEnGabinete: 1,
  loQuePasoGabinete: 3,
  descartadosEnCampo: 0,
  verificados: 2,
  enCurso: 3,
};

/**
 * Los dos hallazgos, uno firme y otro dejado sin efecto (#23).
 *
 * El segundo existe para que el acto de la anulacion —su motivo, quien y
 * cuando— tenga donde verse: sin el, los tres campos que #23 anadio al recurso
 * saldrian nulos en todas las filas y la pantalla que los dibuja no se
 * ejerceria nunca.
 */
export const HALLAZGOS = [
  {
    id: 1,
    candidatoId: 3,
    clase: 'SUBVALUADOR',
    predioId: 3,
    fichaId: idDeLaVersion(3, 2),
    areaDeLaFicha: '265.75',
    areaVerificada: '318.40',
    excesoVerificado: '52.65',
    inspector: 'v.reto',
    verificadoEn: '2026-04-18',
    estado: 'FIRME',
    motivoAnulacion: null,
    anuladoPor: null,
    anuladoEn: null,
  },
  {
    id: 2,
    candidatoId: 6,
    clase: 'OMISO_CATASTRAL',
    /* Nulo, y por construccion: la restriccion de contraste del esquema le exige
       el predio nulo a un omiso catastral. Es tambien lo que hace que
       `GET /fiscalizacion/predios/{id}/hallazgos` no pueda alcanzarlo. */
    predioId: null,
    fichaId: null,
    areaDeLaFicha: null,
    areaVerificada: '96.20',
    excesoVerificado: null,
    inspector: 'm.castillo',
    verificadoEn: '2026-04-20',
    estado: 'DEJADO_SIN_EFECTO',
    motivoAnulacion: 'La edificacion resulto estar en el predio vecino, ya inscrito y con ficha vigente',
    anuladoPor: 'j.alburqueque',
    anuladoEn: '2026-05-04T15:12:33Z',
  },
];

export const EVIDENCIAS = [
  {
    id: 1,
    hallazgoId: 1,
    tipo: 'FOTO',
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

/**
 * El acta del hallazgo firme.
 *
 * Ninguna lectura del backend la enumera —no hay `GET` de actas—, asi que la
 * unica ruta por la que sale es dentro de `HallazgosDelPredioResource`. Aqui
 * esta por eso: para que la pestana de hallazgos del predio tenga un acta que
 * ensenar, que es el unico sitio donde se puede leer una.
 */
export const ACTAS = [
  {
    id: 1,
    numero: 'ACT-2026-0041',
    hallazgoId: 1,
    fecha: '2026-04-25',
    inspector: 'v.reto',
    detalle:
      'Se verifica en campo una ampliacion de dos niveles en el patio interior que la ficha vigente no declara',
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
