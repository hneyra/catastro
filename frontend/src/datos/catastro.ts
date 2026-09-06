/**
 * Los ROTULOS del modulo Catastro: lo que se lee en pantalla y no se calcula.
 *
 * <h2>Aqui no hay ni una cifra, y lo comprueba un arnes</h2>
 *
 * `verificaciones/datos.mjs` lee este directorio y falla ante cualquier numero
 * que no sea una **referencia** —`ADR-0024`, `D-11`, `RT-004`, `#7`—. La razon
 * es la leccion del precedente: en `sgtm/frontend`, `src/datos/inicio.ts` acabo
 * siendo un archivo con `export {}` y treinta lineas explicando por que se
 * borro lo que habia. Lo que habia eran las cifras del prototipo, y su problema
 * no era que estuvieran mal: era que **eran indistinguibles de las buenas**. Un
 * total de padron escrito a mano se pinta igual que uno leido del servidor, y
 * nadie que mire la pantalla puede decir cual es cual.
 *
 * Asi que la division es esta y no admite excepcion: los **rotulos, columnas,
 * motivos y enumerados** viven aqui; **toda cifra sale de una lectura**, y
 * cuando la lectura no se puede hacer, la pantalla dice que le falta.
 *
 * <h2>Y donde el artboard dice una cosa que este sistema no sabe</h2>
 *
 * Se escribe lo que este sistema si sabe, y **se dice por que no es lo otro**.
 * `CatastroV6.dc.html` dibuja el marco del monolito SGTM entero, donde predial y
 * catastro son el mismo sistema; aqui no lo son (ADR-0029), y la frontera de
 * ADR-0024 impide que `catastro` sepa lo que es una deuda. Los sitios concretos
 * estan marcados uno a uno en los motivos de abajo.
 */

/* ── El panel ───────────────────────────────────────────────────────────── */

export const PANEL = {
  cola: 'Cola de trabajo',
  cobertura: 'Cobertura del padron por sector',
  /* El artboard escribe «fichas conciliadas» en este mismo sitio, y este sistema
     NO puede contarlo. Lo dice el propio backend: `ConsultaController` declara
     `conciliadaConRentas` y REDIRIGE la peticion que lo trae, porque componer
     las dos mitades es de `rentas` desde #344 (ARQ-01, ADR-0015). Lo que la
     barra mide de verdad es lo que el `record` del padron publica: `fichado`. */
  medidaDeLaCobertura: 'predios con ficha',
  actividad: 'Actividad reciente',
  verTodos: 'Ver todos los predios',
} as const;

/** Las tres colas, con el filtro real al que lleva cada una. */
export const COLAS = [
  {
    k: 'sin-ficha',
    etiqueta: 'Sin ficha',
    tono: 'warn',
    titulo: 'Predios del padron sin ficha catastral',
    detalle:
      'Estan inscritos y no tienen ficha, asi que no hay area, ni uso, ni construcciones que valorizar.',
    destino: 'predios',
    filtros: { fichado: 'false' },
  },
  {
    k: 'dados-de-baja',
    etiqueta: 'De baja',
    tono: 'info',
    titulo: 'Predios dados de baja',
    detalle:
      'Siguen en el padron y no cuentan: los conteos del sector solo suman los activos, asi que un alta por error se ve aqui y no en la cobertura.',
    destino: 'predios',
    filtros: { estado: 'DADO_DE_BAJA' },
  },
  {
    k: 'sin-poligono',
    etiqueta: 'Sin poligono',
    tono: 'bad',
    titulo: 'Lotes sin poligono levantado',
    detalle:
      'Sin geometria no hay zonificacion, ni riesgo, ni frente lineal: las tres lecturas contestan que el predio existe y le falta el lote.',
    destino: 'plano',
    filtros: {},
  },
] as const;

export const MOTIVOS = {
  /** Por que las tres colas no se suman. */
  colaNoSeSuma:
    'Las tres colas no se suman: un predio puede estar en dos a la vez, y un total seria una cifra que no cuenta nada. Cada una se cuenta sobre el padron entero.',
  /** Por que la cuenta se rinde cuando el padron no cabe en una lectura. */
  padronNoCabe:
    'El padron no cabe en una sola lectura y estas colas se cuentan sobre lo leido, asi que no se cuentan: contar sobre la primera pagina y llamarlo total es la clase de cifra que parece correcta siempre. Se resuelve cuando el backend publique los conteos, como ya hace con los del sector.',
  /** Por que la barra mide «con ficha» y no «conciliadas». */
  coberturaNoEsConciliacion:
    'La barra mide predios con ficha sobre predios activos del sector, y no fichas conciliadas. No es una simplificacion: la conciliacion la sirve «rentas», y este backend la REDIRIGE —la grilla de fichas contesta un reenvio a «/catastro/fichas/conciliacion» en cuanto le llega ese filtro, en vez de responderlo leyendo la tabla de otro sistema (ARQ-01, ADR-0015)—. El numerador se cuenta sobre el padron leido; el denominador lo cuenta el servidor con su «SectorConConteos», y solo suma predios ACTIVOS.',
  /** Por que la actividad reciente son fichas y no un registro de auditoria. */
  actividadSonFichas:
    'No hay ninguna lectura de auditoria en este contrato, asi que «reciente» son las fichas con la vigencia mas nueva, pedidas al servidor por ese campo. Si el servidor no las devuelve en ese orden, se dice: ordenarlas aqui seria afirmar un orden que no se pidio.',

  /** Por que el buscador solo busca por codigo. */
  buscadorSoloCodigo:
    'El artboard busca por «codigo, direccion o titular» y esta lectura solo acota por PREFIJO del codigo de referencia catastral: no hay busqueda por texto libre sobre la direccion, y el titular no se publica en el listado. Ofrecer los tres seria teclear en dos que no filtran.',
  /** Por que la lista no lleva titular. */
  sinTitularEnLaLista:
    'El listado de predios no publica el titular, y no es un olvido: publicarlo convertiria «quien puede listar predios» en «quien puede cosechar la correlacion predio-persona de toda la municipalidad». Se resuelve al abrir el predio, de uno en uno.',
  /** Por que donde el artboard pone el autovaluo no hay ninguna cifra. */
  sinAutovaluo:
    'Donde el artboard pone el autovaluo aqui no hay nada que poner: este backend no publica ninguna lectura de valuacion, asi que no hay ruta de la que sacar la cifra. Su corrida si produce importes desde que se firmo D-11, y aun asi no llegan hasta aqui. Un cero seria una base imponible inventada.',
  /** Por que el selector de orden ofrece esas columnas y no otras. */
  ordenAcotado:
    'El selector solo ofrece las columnas que el servidor admite. Cualquier otra no ordena mal: contesta 422 «ORDEN_NO_ADMITIDO» y la lista no se dibuja. Por eso no estan «Autovaluo» ni «Titular», que son las otras dos del artboard.',
  /** Por que los chips no cambian la lista contra el proxy. */
  filtrosLosAplicaElServidor:
    'Los chips viajan como parametros y filtra el servidor. Contra el proxy de datos no cambian nada: el proxy no filtra, no ordena y no pagina a proposito, porque fingir la semantica de un filtro que el backend todavia no ha decidido acabaria construyendo la interfaz contra esa invencion.',

  /** Por que el catalogo vial no cuelga de un sector. */
  viasNoCuelganDelSector:
    'El artboard cuelga las vias de cada sector y aqui hay un solo catalogo, porque la tabla de vias no guarda el sector: el servidor RECHAZA el filtro con un 422 explicito en vez de ignorarlo, para que una lista sin filtrar no se lea como filtrada.',
  /** Que significan los conteos del sector. */
  conteosDelSector:
    'Las tres cifras las cuenta el servidor y significan cosas distintas: «manzanas» son todas las del sector; «predios» son los ACTIVOS; y «lotes» son los pares manzana-lote distintos de esos predios activos, sin contar los que no tienen lote. Un nulo es «no se conto», y se pinta «—»: nunca cero.',

  /** Por que el arancel de una via puede no poder ensenarse. */
  arancelConVariosTramos:
    'Esta via tiene mas de un arancel en el conjunto sellado y el predio no dice en que tramo esta. Elegir uno seria inventar el dato que falta, asi que se dicen cuantos hay y no se elige ninguno.',
  /** Por que la matriz de valores unitarios puede tener una casilla ambigua. */
  casillaConVariasFilas:
    'Esta casilla tiene mas de una fila en el conjunto sellado —el cuadro publica tramos de anio de construccion y la matriz del artboard no tiene donde ponerlos—, asi que se dice cuantas hay en vez de ensenar una de ellas como si fuera la casilla.',
  /** Por que faltan columnas del artboard en los aranceles. */
  arancelSinZona:
    'El artboard pone una columna «Zona» que el cuadro no publica: «ArancelResource» trae la via, el tramo, el valor y el documento fuente, y nada mas. Se quedan las que existen.',
  /** Por que hay siete partidas y no cinco. */
  sietePartidas:
    'El artboard dibuja cinco columnas y el cuadro publica las partidas que traiga el conjunto sellado, que en el Anexo I.2 son siete. Las columnas salen de la respuesta y no de una lista escrita aqui, para que la tabla no esconda una partida el dia que se publique otra.',
} as const;

/* ── Predios ────────────────────────────────────────────────────────────── */

/** El rotulo de cada campo de ordenacion que el backend admite. */
export const ROTULO_DE_ORDEN: Record<string, string> = {
  codRefCatastral: 'Codigo',
  direccion: 'Direccion',
  predioId: 'Identificador',
  uso: 'Uso',
  vigenciaDesde: 'Vigencia',
  codigo: 'Codigo',
  nombre: 'Nombre',
  zona: 'Zona',
  tipoVia: 'Tipo de via',
  id: 'Identificador',
};

/**
 * Los chips del listado, con el filtro que manda cada uno.
 *
 * Los del artboard —«Sin conciliar», «En verificacion», «Con licencia de obra»—
 * nombran estados que este sistema no tiene: los tres son de `rentas` o de
 * licencias. Los de aqui son los que `PredioController` valida de verdad, y por
 * eso ninguno puede dar un 422.
 */
export const CHIPS_DE_PREDIOS = [
  { k: 'todos', label: 'Todos', filtros: {} },
  { k: 'sin-ficha', label: 'Sin ficha', filtros: { fichado: 'false' } },
  { k: 'de-baja', label: 'Dados de baja', filtros: { estado: 'DADO_DE_BAJA' } },
  { k: 'sin-titular', label: 'Sin titular', filtros: { titularidad: 'SIN_TITULAR' } },
  { k: 'titularidad-incompleta', label: 'Titularidad incompleta', filtros: { titularidad: 'INCOMPLETA' } },
] as const;

/** Las secciones del detalle de un predio. */
export const VISTAS_DEL_PREDIO = [
  { k: 'identificacion', label: 'Identificacion' },
  { k: 'ficha', label: 'Ficha vigente' },
  { k: 'frentes', label: 'Frentes' },
] as const;

export const PREDIOS = {
  marcador: 'Codigo de referencia catastral, por prefijo',
  /* La accion primaria de la lista, que es de donde se abre el alta. Es la
     UNICA escritura que el artboard dibuja, y por eso el boton esta aqui y no
     entre las acciones de un predio ya abierto. */
  registrar: 'Registrar un predio',
  sinSeleccion: 'Elija un predio de la lista',
  sinSeleccionDetalle: 'La ficha se abre aqui al lado, sin salir de la lista.',
  sinCoincidencias: 'Ningun predio coincide',
  sinCoincidenciasDetalle:
    'Puede estar con otro codigo o con la direccion antigua. Si acaba de subdividirse, todavia no tiene ficha.',
  fueraDeLaPagina: 'Ese predio no esta en esta pagina de la lista',
  fueraDeLaPaginaDetalle:
    'El detalle se compone con la fila que trajo el listado, y el listado viene paginado y filtrado. Quite el filtro o pase de pagina; si aun asi no sale, es que el servidor no lo devuelve.',
  padronVacio: 'El padron no tiene ningun predio',
  padronVacioDetalle:
    'No es que la busqueda no encuentre nada: el servidor contesta que no hay ni un predio inscrito en esta municipalidad.',
} as const;

/* ── Territorio ─────────────────────────────────────────────────────────── */

export const TERRITORIO = {
  catalogoVial: 'Catalogo vial',
  notaDeManzanas:
    'Manzanas del sector, con los predios activos que cuelgan de cada una y los lotes distintos que ocupan.',
  notaDeVias:
    'Las vias con su arancel por metro cuadrado. Este arancel es el que la ficha de un predio aplica a su area de terreno.',
} as const;

/* ── Valores del ejercicio ──────────────────────────────────────────────── */

/**
 * Los tres cuadros, con la nota y el pie del artboard letra por letra.
 *
 * `k` es lo que viaja en la ruta, para que la pestana abierta sea enlazable.
 */
export const CUADROS = [
  {
    k: 'aranceles',
    label: 'Aranceles de terreno',
    nota: 'Precio del metro cuadrado de terreno por via y por cuadra. Es la tabla que mas cambia de un anio a otro y la que decide el valor del suelo.',
    pie: 'Aprobados por el Ministerio de Vivienda para el ejercicio. La municipalidad no los fija: los aplica.',
  },
  {
    k: 'unitarios',
    label: 'Valores unitarios',
    nota: 'Precio por metro cuadrado de construccion segun las partidas del cuadro: muros y columnas, techos, pisos, puertas y ventanas, y revestimientos.',
    pie: 'Un piso se describe con una letra por partida. La suma de los valores de sus partidas es su valor unitario por metro cuadrado.',
  },
  {
    k: 'depreciacion',
    label: 'Depreciacion',
    nota: 'Porcentaje que se descuenta al valor unitario segun el material, el estado de conservacion y la antiguedad de la construccion.',
    pie: 'La depreciacion se aplica sobre el valor unitario de cada piso, nunca sobre el valor total de la ficha. Que tabla del Anexo I le toca a cada uso de ficha sigue sin decidirse (RT-004): traducirlo es criterio y no transcripcion, y por eso el backend no lo inventa.',
  },
] as const;

export const VALORES = {
  soloLectura: 'Solo lectura',
  noSeSellaAqui:
    'Aqui no se sella ningun valor normativo: eso es «normativa». Lo que se lee es la copia local del conjunto sellado del ejercicio, y si ese ejercicio no tiene conjunto, las tres lecturas contestan que no hay de donde leer.',
} as const;

/**
 * El rotulo corto de cada partida del cuadro de valores unitarios.
 *
 * Son los del artboard. Una partida que no este en este mapa se pinta con su
 * nombre tal cual: un rotulo que falta no puede esconder una columna.
 */
export const ROTULO_DE_PARTIDA: Record<string, string> = {
  MUROS_Y_COLUMNAS: 'Muros',
  TECHOS: 'Techos',
  PISOS: 'Pisos',
  PUERTAS_Y_VENTANAS: 'Puertas',
  REVESTIMIENTOS: 'Revest.',
  BANIOS: 'Banios',
  INSTALACIONES_ELECTRICAS_Y_SANITARIAS: 'Inst. electricas',
};
