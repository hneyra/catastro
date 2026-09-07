/**
 * `fiscalizacion` (#6, ADR-0035): el hallazgo catastral, con su acto y su
 * evidencia. **No es la fiscalizacion TRIBUTARIA**, que vive entera en `rentas`.
 *
 * Ninguna de sus cinco tablas tiene columna de importe y ninguna respuesta del
 * contrato la trae: aqui no se liquida, no se determina y no se emite un valor.
 *
 * <h2>Trece operaciones, cinco lecturas, y las que faltan se nombran</h2>
 *
 * `FiscalizacionCatastralController` publica **trece** operaciones y **cinco**
 * son lecturas. Hasta #71 esta interfaz declaraba cuatro rutas —las cuatro
 * lecturas de campania— y ninguna escritura: se podia mirar el embudo y no se
 * podia admitir un candidato, ni levantar un acta, ni dejar un hallazgo sin
 * efecto. El trabajo se hacia fuera del sistema.
 *
 * Lo que la interfaz sigue sin poder hacer, medido sobre el controlador:
 *
 * - **No hay listado de campanias.** `POST /fiscalizacion/campanias` crea una y
 *   no hay ningun `GET` que las enumere, asi que una pantalla de campanias no
 *   puede tener una tabla: necesita que le den el identificador —o que se acabe
 *   de crear una, que es lo que el alta devuelve—. Se dice en pantalla;
 *   inventar la lista seria inventar la operacion.
 * - **No hay ninguna lectura de actas.** `POST .../hallazgos/{id}/acta` levanta
 *   una y devuelve la que acaba de crear; no hay `GET` de actas ni por campania
 *   ni por hallazgo. La unica manera de LEER un acta es por el predio:
 *   `HallazgoDelPredioResource` la trae dentro.
 * - **No hay lectura de UN hallazgo ni de UN candidato.** Se leen en la lista de
 *   su campania, o —los hallazgos— por su predio.
 * - **La DETECCION no es un endpoint, y desde #30 no puede serlo.** Corre en el
 *   perfil `batch`, la lanza `DetectarEnCampania`, y el controlador ni siquiera
 *   inyecta `DetectarSubvaluadores`: lo vigila
 *   `LaDeteccionNoEstaEnElCaminoCalienteTest`. La corrida recorre el padron
 *   entero calculando areas geodesicas con un `ORDER BY` que obliga a
 *   calcularlo todo antes del `LIMIT`, y eso dentro de un `POST` sincrono agota
 *   el tiempo de espera del ingreso el dia que haya cartografia cargada. Por
 *   eso **esta interfaz no tiene ningun boton de «lanzar deteccion»**: ponerlo
 *   seria reintroducir por la pantalla lo que #30 saco del backend, y hoy no
 *   fallaria —no hay ni un poligono cargado— sino el dia de la primera carga.
 *
 * Son huecos del backend y se declaran; ninguno se rellena con la cifra de un
 * prototipo.
 */
import { camino, solicitar } from './cliente';
import type { CuerpoSinPareja, Paginacion, RespuestaPaginada } from './cliente';

export const RUTAS = {
  campanias: '/fiscalizacion/campanias',
  cierre: '/fiscalizacion/campanias/{campaniaId}/cierre',
  candidatos: '/fiscalizacion/campanias/{campaniaId}/candidatos',
  tasaDeDescarte: '/fiscalizacion/campanias/{campaniaId}/tasa-de-descarte',
  gabinete: '/fiscalizacion/candidatos/{candidatoId}/gabinete',
  campo: '/fiscalizacion/candidatos/{candidatoId}/campo',
  descarteEnCampo: '/fiscalizacion/candidatos/{candidatoId}/campo/descarte',
  hallazgos: '/fiscalizacion/campanias/{campaniaId}/hallazgos',
  hallazgosDelPredio: '/fiscalizacion/predios/{predioId}/hallazgos',
  evidencias: '/fiscalizacion/hallazgos/{hallazgoId}/evidencias',
  anulacion: '/fiscalizacion/hallazgos/{hallazgoId}/anulacion',
  acta: '/fiscalizacion/hallazgos/{hallazgoId}/acta',
} as const;

/* ── Los enumerados del dominio ─────────────────────────────────────────── */

/** `EstadoDeCampania`. Una campania cerrada deja de admitir candidatos (#23). */
export const ESTADOS_DE_CAMPANIA = ['ABIERTA', 'CERRADA'] as const;
export type EstadoDeCampania = (typeof ESTADOS_DE_CAMPANIA)[number];

/**
 * `EstadoDelCandidato`, en el orden del recorrido.
 *
 * `DETECTADO` -> `ADMITIDO_EN_GABINETE` -> `VERIFICADO_EN_CAMPO`, y desde los
 * dos primeros se puede caer a `DESCARTADO`. **No hay atajo**, y de ahi sale
 * `TRANSICIONES_DEL_CANDIDATO`.
 */
export const ESTADOS_DE_CANDIDATO = [
  'DETECTADO',
  'ADMITIDO_EN_GABINETE',
  'VERIFICADO_EN_CAMPO',
  'DESCARTADO',
] as const;
export type EstadoDelCandidato = (typeof ESTADOS_DE_CANDIDATO)[number];

/** `EstadoDelHallazgo` (#23). */
export const ESTADOS_DE_HALLAZGO = ['FIRME', 'DEJADO_SIN_EFECTO'] as const;
export type EstadoDelHallazgo = (typeof ESTADOS_DE_HALLAZGO)[number];

/** `ClaseDeHallazgo`. */
export const CLASES_DE_HALLAZGO = ['OMISO_CATASTRAL', 'SUBVALUADOR'] as const;
export type ClaseDeHallazgo = (typeof CLASES_DE_HALLAZGO)[number];

/** `OrigenDelCandidato`: de donde salio la sospecha. */
export const ORIGENES_DE_CANDIDATO = [
  'ORTOFOTO',
  'DRON',
  'CRUCE_DE_AREAS',
  'DENUNCIA',
  'BARRIDO_DE_CAMPO',
] as const;
export type OrigenDelCandidato = (typeof ORIGENES_DE_CANDIDATO)[number];

/** `EtapaDeVerificacion`: cual de las dos compuertas. */
export const ETAPAS_DE_VERIFICACION = ['GABINETE', 'CAMPO'] as const;
export type EtapaDeVerificacion = (typeof ETAPAS_DE_VERIFICACION)[number];

/** `TipoDeEvidencia`: con que se sustenta un hallazgo. */
export const TIPOS_DE_EVIDENCIA = ['FOTO', 'VIDEO', 'ORTOFOTO', 'DOCUMENTO', 'CROQUIS'] as const;
export type TipoDeEvidencia = (typeof TIPOS_DE_EVIDENCIA)[number];

/* ── Que admite cada estado, que es lo que la pantalla puede ofrecer ─────── */

/** Los actos que un candidato admite, por su clave. */
export const ACTOS_DEL_CANDIDATO = [
  'admitirEnGabinete',
  'descartarEnGabinete',
  'verificarEnCampo',
  'descartarEnCampo',
] as const;
export type ActoDelCandidato = (typeof ACTOS_DEL_CANDIDATO)[number];

/**
 * Que se le puede hacer a un candidato segun donde este. **AC-2 de #71.**
 *
 * El dominio ya lo impide —`Candidato.verificadoEnCampo()` exige venir de
 * gabinete y `descartadoEn` rechaza la etapa que no toca—, asi que la pantalla
 * **no ofrece el atajo** en vez de ofrecerlo y recibir un 409. Ofrecerlo y que
 * el servidor lo niegue es peor que no ofrecerlo: ensena un camino que no
 * existe, y quien lo recorre concluye que el sistema esta roto.
 *
 * Es un `Record` COMPLETO sobre `EstadoDelCandidato`, y eso es la mitad del
 * trabajo: el dia que el backend anada un estado, `ESTADOS_DE_CANDIDATO` lo
 * gana —lo exige el punto 8 de `rutas.mjs`— y esta tabla **no compila** hasta
 * que alguien decida que admite. Sin el `Record` completo, el estado nuevo se
 * quedaria sin fila y la pantalla no ofreceria nada, en silencio.
 *
 * Los dos estados terminales no admiten ninguno: `esTerminal()` incluye
 * `VERIFICADO_EN_CAMPO`, y no por descuido —reabrir un candidato borraria uno
 * de los dos descartes de la tasa, que es la cifra que ese estado existe para
 * poder medir (ADR-0035 punto 5)—.
 */
export const TRANSICIONES_DEL_CANDIDATO: Readonly<Record<EstadoDelCandidato, readonly ActoDelCandidato[]>> = {
  DETECTADO: ['admitirEnGabinete', 'descartarEnGabinete'],
  ADMITIDO_EN_GABINETE: ['verificarEnCampo', 'descartarEnCampo'],
  VERIFICADO_EN_CAMPO: [],
  DESCARTADO: [],
};

/** Los actos que un hallazgo admite, por su clave. */
export const ACTOS_DEL_HALLAZGO = ['dejarSinEfecto', 'adjuntarEvidencia', 'levantarActa'] as const;
export type ActoDelHallazgo = (typeof ACTOS_DEL_HALLAZGO)[number];

/**
 * Y que se le puede hacer a un hallazgo segun su estado.
 *
 * Un hallazgo `DEJADO_SIN_EFECTO` **no admite ninguno de los tres**, y los tres
 * caminos estan cerrados en el backend por separado:
 * `RegistrarEvidencia.HallazgoSinEfecto` para la evidencia y para el acta, y
 * `DejarSinEfectoElHallazgo` para la segunda anulacion. Sustentar con mas
 * evidencia algo que ya no habilita ningun acto no cambia nada.
 */
export const TRANSICIONES_DEL_HALLAZGO: Readonly<Record<EstadoDelHallazgo, readonly ActoDelHallazgo[]>> = {
  FIRME: ['dejarSinEfecto', 'adjuntarEvidencia', 'levantarActa'],
  DEJADO_SIN_EFECTO: [],
};

/* ── Lo que se lee ──────────────────────────────────────────────────────── */

/**
 * La campania, con las DOS cifras con las que detecto.
 *
 * `tope` lo anadio #25 y esta interfaz no lo declaraba: sin el, dos campanias
 * con el mismo porcentaje de descarte parecen iguales y pueden haber detectado
 * con criterios opuestos —una con umbral bajo sobre quinientos predios y otra
 * con umbral alto sobre cinco mil—. El borde ademas lo **exige** en el alta y
 * no le pone valor por omision, a proposito.
 */
export type Campania = {
  id: number;
  codigo: string;
  nombre: string;
  estado: string;
  inicio: string;
  fin: string | null;
  /** `Score` -> texto. Va de cero a uno. */
  umbral: string;
  tope: number;
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

/**
 * El hallazgo, **con el acto de su anulacion** (#23).
 *
 * `motivoAnulacion`, `anuladoPor` y `anuladoEn` viajan desde #23 y esta interfaz
 * los descartaba: un `estado` que dice `DEJADO_SIN_EFECTO` sin ellos no contesta
 * la unica pregunta que hace falta para atenderlo —por que dejo de valer, y
 * quien lo decidio—. Son nulos mientras el hallazgo siga firme.
 */
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
  motivoAnulacion: string | null;
  anuladoPor: string | null;
  anuladoEn: string | null;
};

export function hallazgos(
  campaniaId: number,
  pagina: Paginacion,
  senal?: AbortSignal,
): Promise<RespuestaPaginada<Hallazgo>> {
  return solicitar(camino(RUTAS.hallazgos, { campaniaId }), { parametros: { ...pagina }, senal });
}

/** El acta, tal como sale por HTTP. Sin importe: lo que se cobre lo decide `rentas`. */
export type Acta = {
  id: number;
  numero: string;
  hallazgoId: number;
  fecha: string;
  inspector: string;
  detalle: string;
};

/**
 * Un hallazgo visto desde SU PREDIO: el hallazgo mas su campania y su acta.
 *
 * Es `Hallazgo` con dos cosas mas y una menos. Las dos mas son lo que hace util
 * la pregunta —en que campania se le hallo algo a este predio, y si eso llego a
 * acto—; la de menos es la anulacion, que este `record` no publica.
 */
export type HallazgoDelPredio = {
  id: number;
  candidatoId: number;
  campaniaId: number;
  campaniaCodigo: string;
  clase: string;
  /** **No es anulable aqui**, y esa es la afirmacion: ver `hallazgosDelPredio`. */
  predioId: number;
  fichaId: number | null;
  areaDeLaFicha: string | null;
  areaVerificada: string;
  excesoVerificado: string | null;
  inspector: string;
  verificadoEn: string;
  estado: string;
  acta: Acta | null;
};

/**
 * Los hallazgos de UN predio, en un sobre que dice de que predio son.
 *
 * <h2>El sobre no es adorno</h2>
 *
 * La respuesta interesante de esta ruta es la **vacia**, y un array desnudo
 * `[]` no dice de que predio es. Con el `predioId` dentro, quien lee puede
 * comprobar que le contestaron por el que pregunto —que es la unica forma de
 * distinguir «este predio no tiene ninguno» de «me contestaron por otro»—.
 *
 * <h2>Nunca trae un omiso catastral, y hay que decirlo</h2>
 *
 * Un `OMISO_CATASTRAL` **no le toca a ningun predio**: `hallazgo_contraste_check`
 * de `V9` le exige `predio_id` nulo, porque si lo tuviera no seria un omiso sino
 * otra cosa. Esta ruta no puede alcanzarlos **por construccion**, y quien busque
 * omisos los encuentra en la pagina de su campania.
 *
 * <h2>La lista vacia no es un 404</h2>
 *
 * Un predio sin hallazgos contesta **200 con la lista vacia**; uno que no esta
 * en el padron de esta municipalidad, **404**. No son la misma respuesta y no se
 * arreglan igual: el predio limpio cierra la revision y el identificador
 * equivocado se teclea otra vez. Bajo RLS el predio de la municipalidad vecina
 * cae en el 404, que es lo correcto: no es «prohibido», no existe.
 */
export type HallazgosDelPredio = {
  predioId: number;
  hallazgos: HallazgoDelPredio[];
};

export function hallazgosDelPredio(predioId: number, senal?: AbortSignal): Promise<HallazgosDelPredio> {
  return solicitar(camino(RUTAS.hallazgosDelPredio, { predioId }), { senal });
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

/* ── Lo que se escribe ──────────────────────────────────────────────────── */

/**
 * Los siete cuerpos de escritura, y **los siete son listas blancas**.
 *
 * Lo que el `record` del backend no declare **se descarta aunque llegue en el
 * JSON**, y el servidor contesta que la operacion se hizo. O sea que un campo
 * mal escrito aqui no da error en ningun sitio: el dato simplemente no esta. Es
 * el mismo modo de fallo que #34 midio en el alta de ficha, siete veces mas.
 *
 * Por eso cada uno viaja con su lista de campos en `CUERPOS_DE_ESCRITURA`, que
 * el punto 9 de `verificaciones/rutas.mjs` compara contra el `record` del
 * backend, y que `LOS_CUERPOS_DE_ESCRITURA_CUADRAN` ata a estos tipos en tiempo
 * de compilacion.
 */
export type PeticionDeCampania = {
  codigo: string;
  nombre: string;
  /** Va como texto: `Score` lo lee con `BigDecimal` y un `double` aqui perderia precision. */
  umbral: string;
  /** Cuantos predios como mucho mira una corrida suya. Es un entero de verdad, no una medida. */
  tope: number;
  observacion: string;
};

/** Cerrar la campania: solo su observacion. Lo que se cierra ya lo dice la ruta. */
export type PeticionDeCierre = {
  observacion: string;
};

/**
 * Una compuerta: admitir o descartar.
 *
 * `admite` y `motivo` van juntos a proposito —un descarte sin motivo no se puede
 * escribir, y lo rechaza el dominio antes de llegar a la base (ADR-0035 punto
 * 5)—. El `motivo` viaja tambien cuando se admite: el `record` lo declara
 * anulable y el caso de uso solo lo lee al descartar.
 */
export type PeticionDeCompuerta = {
  admite: boolean;
  motivo?: string;
  observacion: string;
};

/**
 * La verificacion en campo. **No trae geometria**, y no es un olvido: un
 * poligono que entra por HTTP cambia el padron sin brigada, sin plano y sin acto
 * (ADR-0021).
 */
export type PeticionDeCampo = {
  /** `AreaM2` -> texto, en metros cuadrados y no negativa. */
  areaVerificada: string;
  inspector: string;
  observacion: string;
};

/** La evidencia. `capturadoEn` es el reloj del APARATO; el del servidor lo pone el caso de uso. */
export type PeticionDeEvidencia = {
  tipo: string;
  sha256: string;
  ruta: string;
  capturadoEn: string;
  dispositivo?: string;
  observacion: string;
};

/**
 * Dejar sin efecto un hallazgo (#23).
 *
 * `motivo` y `observacion` son dos campos y no uno: el primero dice por que ese
 * hallazgo ya no vale —viaja en la fila y sale al consumidor— y el segundo por
 * que se hizo esta escritura (regla 10). Uno solo obligaria a elegir cual de las
 * dos se contesta.
 */
export type PeticionDeAnulacion = {
  motivo: string;
  observacion: string;
};

/** El acta. Sin importe: lo que se cobre lo decide `rentas` (ADR-0024). */
export type PeticionDeActa = {
  numero: string;
  inspector: string;
  detalle: string;
  observacion: string;
};

/**
 * Abre la campania (`201`), con las dos cifras con las que va a detectar.
 *
 * `409` dice que ya hay una campania con ese codigo en esta municipalidad, y no
 * se arregla reintentando: se arregla con otro codigo.
 */
export function abrirCampania(peticion: PeticionDeCampania, senal?: AbortSignal): Promise<Campania> {
  return solicitar(RUTAS.campanias, { metodo: 'POST', cuerpo: peticion, senal });
}

/**
 * Cierra la campania: **deja de admitir candidatos**, y no se deshace.
 *
 * No borra nada ni consolida ninguna cifra; lo unico que hace es que sus
 * recuentos dejen de moverse, que es lo que permite citarlos. `409` dice que ya
 * estaba cerrada.
 */
export function cerrarCampania(
  campaniaId: number,
  peticion: PeticionDeCierre,
  senal?: AbortSignal,
): Promise<Campania> {
  return solicitar(camino(RUTAS.cierre, { campaniaId }), { metodo: 'POST', cuerpo: peticion, senal });
}

/** La primera compuerta: admitir o descartar en gabinete. */
export function enGabinete(
  candidatoId: number,
  peticion: PeticionDeCompuerta,
  senal?: AbortSignal,
): Promise<Candidato> {
  return solicitar(camino(RUTAS.gabinete, { candidatoId }), { metodo: 'POST', cuerpo: peticion, senal });
}

/**
 * La segunda compuerta, y la unica que produce un hallazgo (`201`).
 *
 * Los dos `409` dicen cosas distintas: `TransicionQueNoExiste` —el candidato no
 * paso por gabinete, o ya es terminal— y `PredioSinFichaQueContrastar`, que no
 * se arregla aqui sino inscribiendo la ficha del predio.
 */
export function enCampo(
  candidatoId: number,
  peticion: PeticionDeCampo,
  senal?: AbortSignal,
): Promise<Hallazgo> {
  return solicitar(camino(RUTAS.campo, { candidatoId }), { metodo: 'POST', cuerpo: peticion, senal });
}

/** El descarte de la segunda compuerta: exige haber pasado la primera. */
export function descartarEnCampo(
  candidatoId: number,
  peticion: PeticionDeCompuerta,
  senal?: AbortSignal,
): Promise<Candidato> {
  return solicitar(camino(RUTAS.descarteEnCampo, { candidatoId }), {
    metodo: 'POST',
    cuerpo: peticion,
    senal,
  });
}

/**
 * Adjunta una evidencia al hallazgo (`201`).
 *
 * **No sube ningun archivo**: el cuerpo lleva la huella, la ruta y el instante
 * de captura. Si hiciera falta subirlo, eso es otra frontera y otro issue.
 */
export function adjuntarEvidencia(
  hallazgoId: number,
  peticion: PeticionDeEvidencia,
  senal?: AbortSignal,
): Promise<Evidencia> {
  return solicitar(camino(RUTAS.evidencias, { hallazgoId }), { metodo: 'POST', cuerpo: peticion, senal });
}

/**
 * Deja sin efecto un hallazgo firme. **No se deshace.**
 *
 * Exige el privilegio `ELIMINACION` y no `MODIFICACION`, y el javadoc del borde
 * dice por que: separarlo permite que una municipalidad le de la compuerta de
 * campo a una brigada **sin darle con ella la de retirar un hallazgo firme**.
 * La fila se queda y su acta se queda; lo que deja de valer es lo que el
 * hallazgo habilitaba (RNF-051).
 */
export function dejarSinEfecto(
  hallazgoId: number,
  peticion: PeticionDeAnulacion,
  senal?: AbortSignal,
): Promise<Hallazgo> {
  return solicitar(camino(RUTAS.anulacion, { hallazgoId }), { metodo: 'POST', cuerpo: peticion, senal });
}

/**
 * Levanta el acta del hallazgo (`201`).
 *
 * Tres `409` distintos: el hallazgo esta sin efecto, su candidato no paso las
 * dos compuertas, o ese numero de acta ya existe —o el hallazgo ya tiene la
 * suya—. Los tres se arreglan de manera distinta y el mensaje del servidor los
 * distingue.
 */
export function levantarActa(
  hallazgoId: number,
  peticion: PeticionDeActa,
  senal?: AbortSignal,
): Promise<Acta> {
  return solicitar(camino(RUTAS.acta, { hallazgoId }), { metodo: 'POST', cuerpo: peticion, senal });
}

/* ── Los cuerpos, contra los `record` que los reciben ───────────────────── */

/**
 * Que campos lleva cada cuerpo de escritura, por el `record` que lo recibe.
 *
 * No es documentacion: lo lee el punto 9 de `verificaciones/rutas.mjs`, que abre
 * `FiscalizacionCatastralController.java`, saca los componentes de cada `record`
 * y los compara **en los dos sentidos**. Los dos fallan distinto y ninguno
 * revienta:
 *
 *   · **Un campo que este aqui y no en el `record`** se descarta en silencio.
 *     El servidor contesta `201`, la pantalla dice que se guardo, y el dato no
 *     acaba en ningun sitio. Es el modo de fallo que #34 midio con
 *     `documentoDeOrigen`: `tsc` y `eslint` en verde, y solo el arnes lo ve.
 *   · **Un campo que el `record` declare y no este aqui** es la mitad del
 *     contrato que esta interfaz no llena: no es un fallo si esta decidido, y
 *     por eso el arnes lo NOMBRA en vez de callarlo.
 *
 * Es un objeto y no siete listas sueltas a proposito: siete arrays exportados
 * obligarian a declarar siete pareos en las tablas de abajo para decir siete
 * veces lo mismo.
 */
export const CUERPOS_DE_ESCRITURA = {
  PeticionDeCampania: ['codigo', 'nombre', 'umbral', 'tope', 'observacion'],
  PeticionDeCierre: ['observacion'],
  PeticionDeCompuerta: ['admite', 'motivo', 'observacion'],
  PeticionDeCampo: ['areaVerificada', 'inspector', 'observacion'],
  PeticionDeEvidencia: ['tipo', 'sha256', 'ruta', 'capturadoEn', 'dispositivo', 'observacion'],
  PeticionDeAnulacion: ['motivo', 'observacion'],
  PeticionDeActa: ['numero', 'inspector', 'detalle', 'observacion'],
} as const satisfies Readonly<Record<string, readonly string[]>>;

/* `CuerpoSinPareja` vivia aqui hasta #72 y se mudo a `cliente.ts`: con un
   segundo modulo que escribe —el catalogo territorial— copiarlo habria sido la
   misma clase de defecto que el tipo existe para atrapar, un escalon mas arriba. */

export const LOS_CUERPOS_DE_ESCRITURA_CUADRAN: {
  PeticionDeCampania: CuerpoSinPareja<PeticionDeCampania, typeof CUERPOS_DE_ESCRITURA.PeticionDeCampania>;
  PeticionDeCierre: CuerpoSinPareja<PeticionDeCierre, typeof CUERPOS_DE_ESCRITURA.PeticionDeCierre>;
  PeticionDeCompuerta: CuerpoSinPareja<PeticionDeCompuerta, typeof CUERPOS_DE_ESCRITURA.PeticionDeCompuerta>;
  PeticionDeCampo: CuerpoSinPareja<PeticionDeCampo, typeof CUERPOS_DE_ESCRITURA.PeticionDeCampo>;
  PeticionDeEvidencia: CuerpoSinPareja<PeticionDeEvidencia, typeof CUERPOS_DE_ESCRITURA.PeticionDeEvidencia>;
  PeticionDeAnulacion: CuerpoSinPareja<PeticionDeAnulacion, typeof CUERPOS_DE_ESCRITURA.PeticionDeAnulacion>;
  PeticionDeActa: CuerpoSinPareja<PeticionDeActa, typeof CUERPOS_DE_ESCRITURA.PeticionDeActa>;
} = {
  PeticionDeCampania: true,
  PeticionDeCierre: true,
  PeticionDeCompuerta: true,
  PeticionDeCampo: true,
  PeticionDeEvidencia: true,
  PeticionDeAnulacion: true,
  PeticionDeActa: true,
};

/* ── De donde sale cada lista de este modulo ─────────────────────────────── */

/**
 * El enumerado del backend del que sale cada lista, por su nombre de clase.
 *
 * Lo lee el punto 8 de `verificaciones/rutas.mjs`, que compara los dos conjuntos
 * **en los dos sentidos**: lo que falta aqui es una opcion que no se puede
 * elegir —invisible— y lo que sobra es un 422 al enviar, que se ve tarde y en el
 * sitio equivocado.
 *
 * Los siete de este modulo no los miraba nadie hasta #71, y **eso tenia
 * consecuencias medidas**: el proxy de datos contestaba con estados que ningun
 * enumerado del backend admite —`EN_CURSO`, `PASO_GABINETE`, `OMISO`,
 * `FOTOGRAFIA`—, y la pantalla los pintaba tal cual.
 */
export const LISTAS_DERIVADAS_DE_UN_ENUM: Readonly<Record<string, string>> = {
  ESTADOS_DE_CAMPANIA: 'EstadoDeCampania',
  ESTADOS_DE_CANDIDATO: 'EstadoDelCandidato',
  ESTADOS_DE_HALLAZGO: 'EstadoDelHallazgo',
  CLASES_DE_HALLAZGO: 'ClaseDeHallazgo',
  ORIGENES_DE_CANDIDATO: 'OrigenDelCandidato',
  ETAPAS_DE_VERIFICACION: 'EtapaDeVerificacion',
  TIPOS_DE_EVIDENCIA: 'TipoDeEvidencia',
};

/** Las listas de este modulo que NO salen de ningun enumerado, con su motivo. */
export const LISTAS_QUE_NO_SALEN_DE_UN_ENUM: Readonly<Record<string, string>> = {
  ACTOS_DEL_CANDIDATO:
    'Son las claves de los actos que esta interfaz OFRECE, no un enumerado del backend: el dominio ' +
    'los escribe como metodos de «Candidato» —«admitidoEnGabinete()», «verificadoEnCampo()», ' +
    '«descartadoEn(etapa, …)»— y no hay ninguna clase que los enumere. Lo que si se contrasta es su ' +
    'reparto por estado: «TRANSICIONES_DEL_CANDIDATO» es un «Record» completo sobre ' +
    '«ESTADOS_DE_CANDIDATO», que si sale de un enumerado, y «verificaciones/transiciones.mjs» mide ' +
    'en el navegador que la pantalla ofrezca exactamente eso.',
  ACTOS_DEL_HALLAZGO:
    'Lo mismo por el otro lado: los tres actos de un hallazgo son tres casos de uso —' +
    '«DejarSinEfectoElHallazgo», «RegistrarEvidencia», «LevantarActa»— y no las constantes de ' +
    'ningun enumerado. Su reparto por estado lo ata «TRANSICIONES_DEL_HALLAZGO».',
};
