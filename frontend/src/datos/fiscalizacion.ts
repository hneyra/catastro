/**
 * Los ROTULOS del modulo Fiscalizacion: lo que se lee en pantalla y no se calcula.
 *
 * <h2>Aqui no hay ni una cifra, y lo comprueba un arnes</h2>
 *
 * `verificaciones/datos.mjs` lee este directorio y falla ante cualquier numero
 * que no sea una **referencia**. Por eso los limites que el dominio impone —el
 * largo de una observacion, la forma de una huella, el rango de un umbral— se
 * escriben con palabras o **se dejan decir al servidor**, que es quien los sabe:
 * copiarlos aqui crearia un segundo sitio con la misma verdad, y el dia que
 * cambiaran la pantalla seguiria afirmando el limite viejo.
 *
 * <h2>Y donde el backend no publica algo, se dice</h2>
 *
 * `fiscalizacion` no enumera campanias, no lee actas por campania ni por
 * hallazgo, y no publica la lectura de UN candidato ni de UN hallazgo. Las
 * pantallas lo escriben en vez de dibujar una tabla contra una operacion que no
 * existe.
 */

import { ACTO, IRREVERSIBLE, LA_OBSERVACION } from './actos';

/* ── Lo que el backend no publica ───────────────────────────────────────── */

export const MOTIVOS = {
  sinListadoDeCampanias:
    'El controlador publica trece operaciones y ninguna enumera las campanias: se crean con un POST y se leen por identificador. Dibujar aqui una tabla exigiria inventar la operacion que las lista, y esta interfaz no inventa endpoints. Lo que si se puede es abrir una y quedarse con el identificador que devuelve.',
  sinLecturaDeActas:
    'No hay ninguna lectura de actas —ni por campania ni por hallazgo—: un acta se levanta con un POST sobre su hallazgo y el servidor devuelve la que acaba de crear. La unica manera de LEER una es por el predio, donde viaja dentro del hallazgo.',
  sinLecturaDeUnHallazgo:
    'Tampoco hay lectura de UN hallazgo suelto. Su estado se ve en la lista de su campania o en la del predio, y por eso esta pantalla pide el identificador y deja que el servidor conteste: si el hallazgo esta dejado sin efecto, la respuesta es un rechazo con su motivo dentro.',
  laDeteccionCorreEnBatch:
    'La corrida de deteccion NO es un endpoint, y desde #30 no puede serlo: corre en el perfil «batch», la lanza «DetectarEnCampania» y el controlador ni siquiera inyecta el caso de uso. Recorre el padron entero calculando areas geodesicas con un orden que obliga a calcularlo todo antes del corte, y eso dentro de un POST sincrono agota el tiempo de espera del ingreso el dia que haya cartografia cargada. Un boton aqui reintroduciria por la pantalla lo que se saco del backend, y hoy no fallaria —no hay ni un poligono cargado— sino el dia de la primera carga.',
  sinAtajo:
    'Un candidato no se puede saltar el gabinete: verificarlo en campo exige haber pasado esa compuerta antes (ADR-0035). Esta pantalla no ofrece el acto que su estado no admite, en vez de ofrecerlo y recibir un rechazo — un camino que no existe es peor que ninguno.',
  terminal:
    'Este candidato ya no admite ningun acto. Un descarte no se reabre: si la brigada vuelve y encuentra otra cosa, eso es otro candidato con otro insumo, y contarlo como el mismo borraria uno de los dos descartes de la tasa (ADR-0035).',
  sinImporte:
    'Ninguna de las cinco tablas de fiscalizacion tiene columna de importe y ninguna respuesta del contrato la trae. Lo que se cobre —si se cobra— lo decide «rentas» (ADR-0024).',
  noCorrigeLaFicha:
    'Un hallazgo se INFORMA: ninguna operacion de este modulo escribe en el area del predio. Corregirla es versionar la ficha con su observacion, y ese acto lo ejecuta una persona (ADR-0021, ADR-0035).',
  sinOmisos:
    'Esta lectura no puede devolver un omiso catastral, y no es un filtro: un OMISO_CATASTRAL no le toca a ningun predio —la restriccion de contraste del esquema le exige el predio nulo, porque si lo tuviera no seria un omiso sino otra cosa—. Quien busque omisos los encuentra en la pagina de su campania.',
  laListaVaciaNoEsUnCuatrocientosCuatro:
    'Un predio sin hallazgos contesta con la lista vacia; uno que no esta en el padron de esta municipalidad contesta 404. No son la misma respuesta y no se arreglan igual: el primero cierra la revision y el segundo se teclea otra vez.',
  /* Las dos ultimas no son de fiscalizacion: son de cualquier escritura, y por
     eso viven en `actos.ts` desde #72. Se reexportan con el nombre que este
     modulo ya usaba para no dejar dos frases donde hay una. */
  observacion: ACTO.observacion,
  elProxyNoPersiste: ACTO.elProxyNoPersiste,
} as const;

/* ── Las columnas ───────────────────────────────────────────────────────── */

export const COLUMNAS = {
  predio: 'Predio',
  clase: 'Clase',
  origen: 'Origen',
  score: 'Score',
  estado: 'Estado',
  etapaDelDescarte: 'Etapa del descarte',
  motivo: 'Motivo',
  acciones: 'Acciones',
  areaDeLaFicha: 'Area de la ficha',
  areaVerificada: 'Area verificada',
  exceso: 'Exceso',
  inspector: 'Inspector',
  verificado: 'Verificado',
  anulacion: 'Anulacion',
  anuladoPor: 'Anulado por',
  anuladoEn: 'Anulado el',
  tipo: 'Tipo',
  ruta: 'Ruta',
  huella: 'Huella',
  capturado: 'Capturado',
  recibido: 'Recibido',
  desfase: 'Desfase en segundos',
  dispositivo: 'Dispositivo',
  campania: 'Campania',
  acta: 'Acta',
} as const;

export const PIES = {
  candidatos:
    'La cola se abre por lo mas sospechoso y no por lo mas antiguo: quien revisa tiene un dia y muchos candidatos, y el orden decide cuales se miran de verdad. El motivo del descarte se guarda con quien lo firmo y con la compuerta en la que ocurrio.',
  hallazgos:
    'Las tres areas llegan como texto y se pintan como texto: el exceso lo calcula el servidor, y una segunda resta aqui podria divergir de la suya sin que nada lo dijera.',
  evidencias:
    'El desfase entre capturar y recibir lo calcula el servidor y viaja como dato: es lo que separa una fotografia tomada en el predio de una anadida despues. La evidencia no se corrige en el sitio; si algo cambia, se agrega otro registro (ADR-0006, ADR-0008).',
  hallazgosDelPredio:
    'Cada hallazgo dice en que campania se le encontro y si llego a acto. El acta es la unica que esta interfaz puede leer, porque viaja dentro de esta respuesta.',
} as const;

/* ── Los actos, y como se llaman en pantalla ────────────────────────────── */

/**
 * El rotulo de cada acto que esta interfaz ofrece.
 *
 * Las claves son las de `ACTOS_DEL_CANDIDATO` y `ACTOS_DEL_HALLAZGO` en
 * `src/api/fiscalizacion.ts`, que es donde se declara **que estado admite
 * cual**. Aqui solo esta como se lee.
 */
export const ACTOS = {
  admitirEnGabinete: 'Admitir en gabinete',
  descartarEnGabinete: 'Descartar en gabinete',
  verificarEnCampo: 'Verificar en campo',
  descartarEnCampo: 'Descartar en campo',
  dejarSinEfecto: 'Dejar sin efecto',
  adjuntarEvidencia: 'Adjuntar evidencia',
  levantarActa: 'Levantar el acta',
  abrirCampania: 'Abrir la campania',
  cerrarCampania: 'Cerrar la campania',
} as const;

export const NOTAS_DE_LOS_ACTOS = {
  admitirEnGabinete:
    'La primera compuerta: alguien miro el candidato contra lo que el padron dice y decide que merece una visita.',
  descartarEnGabinete:
    'El candidato no prospera y la fila se queda con su etapa y su motivo. Es terminal: no se reabre.',
  verificarEnCampo:
    'La segunda compuerta, y la unica que produce un hallazgo. Exige haber pasado gabinete, y el area verificada es la que midio la brigada.',
  descartarEnCampo:
    'La brigada fue y no encontro lo que la maquina sospechaba. Exige haber pasado gabinete: descartar en campo lo que campo nunca vio no dice nada.',
  dejarSinEfecto:
    'El hallazgo deja de habilitar ningun acto. La fila se queda y su acta se queda: lo que deja de valer es lo que sostenia.',
  adjuntarEvidencia:
    'Una pieza que sustenta el hallazgo. No se sube ningun archivo: viajan su huella, su ruta y el instante en que el aparato la capturo.',
  levantarActa:
    'El acto de una persona sobre lo que las dos compuertas confirmaron. Sin importe y sin tributo: dice que se hallo, quien y cuando.',
  abrirCampania:
    'Una campania declara las dos cifras con las que va a detectar, y las dos se guardan con ella: sin las dos, su tasa de descarte no se puede comparar con la de ninguna otra corrida.',
  cerrarCampania:
    'Cerrarla no borra nada ni consolida ninguna cifra: lo unico que hace es que sus recuentos dejen de moverse, que es lo que permite citarlos.',
} as const;

/* ── Los dos actos que no se deshacen ───────────────────────────────────── */

/**
 * Lo que se dice antes de un acto irreversible, y **se confirma aparte**.
 *
 * Dos pulsaciones y no una, y ninguna de las dos es un dialogo del navegador:
 * `confirm()` bloquea el hilo, no se puede leer con un lector de pantalla y deja
 * los arneses colgados. La segunda confirmacion es un panel de la propia
 * pantalla, con lo que se va a hacer escrito delante.
 */
export const IRREVERSIBLES = {
  ...IRREVERSIBLE,
  anulacion:
    'El hallazgo deja de estar firme. No hay ninguna operacion que lo devuelva: su fila se queda con el motivo, con quien lo decidio y con cuando, y eso es todo lo que quedara para explicarlo. Y exige el privilegio de ELIMINACION y no el de MODIFICACION: es lo que permite darle la compuerta de campo a una brigada sin darle con ella la de retirar un hallazgo firme.',
  cierre:
    'La campania deja de admitir candidatos. No hay ninguna operacion que la reabra, y sus recuentos dejan de moverse a partir de aqui.',
} as const;

/* ── Los campos de cada formulario ──────────────────────────────────────── */

/**
 * Los rotulos y las ayudas de cada campo de escritura.
 *
 * **Ningun limite se copia aqui.** El largo de una observacion, el rango de un
 * umbral y la forma de una huella los sabe el dominio, y quien los enuncia es el
 * servidor cuando rechaza: escribirlos tambien aqui daria dos sitios con la
 * misma verdad, que es la forma de defecto que este repositorio ha encontrado
 * cinco veces. Lo que si se dice es **que es** cada campo.
 */
export const CAMPOS = {
  codigo: { rotulo: 'Codigo de la campania', ayuda: 'Unico en la municipalidad. Si ya existe, el servidor contesta 409.' },
  nombre: { rotulo: 'Nombre', ayuda: 'Como se la va a nombrar cuando se cite su tasa de descarte.' },
  umbral: {
    rotulo: 'Umbral de deteccion',
    ayuda: 'El score a partir del cual un predio entra en la cola. Va de cero a uno y viaja como texto.',
  },
  tope: {
    rotulo: 'Tope de la corrida',
    ayuda: 'Cuantos predios como mucho mira una corrida suya. Se exige y no tiene valor por omision: sin el, la tasa de descarte saldria de un conjunto recortado por una cifra que nadie podria leer despues.',
  },
  motivo: {
    rotulo: 'Motivo',
    ayuda: 'Por que deja de valer. Viaja en la fila y sale al consumidor, asi que es lo que quedara para explicarlo.',
  },
  motivoDelDescarte: {
    rotulo: 'Motivo del descarte',
    ayuda: 'Un descarte sin motivo no se puede escribir, y lo rechaza el dominio antes de llegar a la base.',
  },
  areaVerificada: {
    rotulo: 'Area verificada',
    ayuda: 'La que midio la brigada, en metros cuadrados. Viaja como texto: es la mitad de la diferencia que sostiene el hallazgo.',
  },
  inspector: { rotulo: 'Inspector', ayuda: 'Quien fue y lo vio. Es un nombre, no un score.' },
  tipoDeEvidencia: { rotulo: 'Tipo de evidencia', ayuda: 'Con que se sustenta.' },
  huella: {
    rotulo: 'Huella del archivo',
    ayuda: 'La que el aparato calculo del archivo, en hexadecimal minuscula. El servidor la rechaza si no tiene la forma que exige, y tambien si esa misma huella ya sustenta otro hallazgo: una foto no sustenta dos actas.',
  },
  rutaDelArchivo: {
    rotulo: 'Ruta del archivo',
    ayuda: 'Donde esta guardado. Esta operacion no sube ningun archivo: si hiciera falta subirlo, eso es otra frontera.',
  },
  capturadoEn: {
    rotulo: 'Capturado el',
    ayuda: 'El reloj del APARATO, tal como lo dio, en la norma ISO de fecha y hora, con zona. No se sustituye por el del servidor cuando falta: si se sustituyera, la diferencia entre los dos relojes saldria siempre cero y la captura seguiria sin poder auditarse, esta vez en silencio.',
  },
  dispositivo: { rotulo: 'Dispositivo', ayuda: 'Con que se capturo. Es el unico campo opcional de esta operacion.' },
  numeroDelActa: { rotulo: 'Numero del acta', ayuda: 'Unico en la municipalidad. Si ya existe —o el hallazgo ya tiene la suya—, el servidor contesta 409.' },
  detalle: { rotulo: 'Detalle', ayuda: 'Que se hallo. Sin importe y sin tributo.' },
  /* Tampoco es de fiscalizacion: la observacion la exige toda escritura, y su
     rotulo lo pone `Acto` cuando la anade. Se conserva aqui el nombre porque
     `verificaciones/errores.mjs` rellena el formulario por el. */
  observacion: LA_OBSERVACION,
} as const;

/* ── Los sujetos que estas pantallas piden a mano ───────────────────────── */

export const SUJETOS = {
  campania: {
    rotulo: 'Identificador de la campania',
    marcador: 'El numero que devolvio el alta',
    ayuda: 'Hay que escribirlo: el backend no publica el listado',
  },
  hallazgo: {
    rotulo: 'Identificador del hallazgo',
    marcador: 'El numero del hallazgo',
    ayuda: 'Sale de la pantalla de hallazgos, o de los del predio',
  },
} as const;

export const TITULOS = {
  laCampania: 'La campania',
  elEmbudo: 'El embudo',
  notaDelEmbudo: 'Detectado, descartado y verificado',
  candidatos: 'Candidatos de la campania',
  hallazgos: 'Hallazgos de la campania',
  elHallazgo: 'El hallazgo',
  evidencia: 'Evidencia del hallazgo',
  hallazgosDelPredio: 'Hallazgos de este predio',
  loQueSeAcabaDeHacer: 'Lo que el servidor contesto',
} as const;

export const ESPERAS = {
  embudo:
    'Escriba el identificador de una campania y aqui saldra su embudo: cuanto se detecto, cuanto se descarto en cada compuerta y cuanto llego a hallazgo.',
  candidatos:
    'Escriba el identificador de una campania y aqui saldran sus candidatos, con la compuerta en la que esta cada uno y lo que se le puede hacer.',
  hallazgos:
    'Escriba el identificador de una campania y aqui saldran sus hallazgos, con el area de la ficha frente a la verificada.',
  evidencia:
    'Escriba el identificador de un hallazgo y aqui saldra su evidencia, con la huella de cada pieza y el desfase entre capturarla y recibirla.',
} as const;

export const VACIOS = {
  candidatos: 'Esta campania no tiene ningun candidato.',
  hallazgos: 'Esta campania no tiene ningun hallazgo.',
  evidencias: 'Este hallazgo no tiene ninguna evidencia registrada.',
  hallazgosDelPredio: 'Este predio no tiene ningun hallazgo. Existe en el padron y ninguna campania le encontro nada.',
} as const;

/** Lo que dice el boton primario cuando esta apagado, delante de lo que falta. */
export { FALTA } from './actos';
