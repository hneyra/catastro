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
  /** Y por que la respuesta de una ESCRITURA no trae ninguna de las tres. */
  conteosDeLaEscritura:
    'Los conteos salen «—» porque el servidor no los mando: quien escribe un sector o una manzana no pidio contar nada, y esa peticion no cuenta. Un cero diria «no tiene ninguna», que al corregir un sector que ya tiene manzanas seria falso —y se pintaria igual que una cifra leida—. Con cifra salen en la lista, que es la lectura que si cuenta.',

  /** Por que el arancel de una via puede no poder ensenarse. */
  arancelConVariosTramos:
    'Esta via tiene mas de un arancel en el conjunto sellado y el predio no dice en que tramo esta. Elegir uno seria inventar el dato que falta, asi que se dicen cuantos hay y no se elige ninguno.',
  /** Por que la matriz de valores unitarios puede tener una casilla ambigua. */
  casillaConVariasFilas:
    'Esta casilla tiene mas de una fila en el conjunto sellado —el cuadro publica tramos de anio de construccion y la matriz del artboard no tiene donde ponerlos—, asi que se dice cuantas hay en vez de ensenar una de ellas como si fuera la casilla.',
  /**
   * Por que la tabla de obras complementarias no tiene columna de valor.
   *
   * **Es el motivo que la pantalla ENSENA**, no un comentario: sin el, una tabla
   * de obras sin importe se lee como una tabla a la que le falta una columna, y
   * el hueco se cierra a ojo con un cero. Nombra las dos cosas que faltan porque
   * son dos y se arreglan por separado: el cuadro —Anexo III, que el corpus no
   * transcribe— y la columna —`otra_instalacion` no declara ningun importe—.
   */
  obraSinImporte:
    'Estas obras salen sin su valor, y no es que la lectura lo recorte: no existe. El Anexo III de la R.M. 277-2025-VIVIENDA —los valores unitarios a costo directo de obras complementarias— no esta transcrito en el corpus, y la tabla «otra_instalacion» no tiene columna de importe. O sea que no hay ni cuadro del que leerlo ni dato declarado donde estuviera. Lo que si publica el backend es lo que el tecnico midio: que es, cuanto y en que unidad. Un cero aqui seria una base imponible inventada.',
  /** Por que la ficha se pide a una de cuatro rutas y no a una sola. */
  fichaPorSuClase:
    'La ficha se pide a la ruta de SU clase, que es como el backend las publica: cada una fija su tipo y exige su propio permiso de lectura. Pedirla por otra no devuelve un bloque vacio, contesta que ese predio no tiene ficha de esa clase, asi que el tipo sale de la grilla y no se supone.',
  /** Y que pasa cuando la grilla trae un tipo que esta interfaz no conoce. */
  tipoDeFichaDesconocido:
    'La grilla dice que este predio tiene una clase de ficha que esta interfaz no conoce, asi que no hay ruta a la que pedirla. No se prueba con la urbana: esa contesta la ficha unica y ninguna otra, y dibujar la que conteste como si fuera la suya seria ensenar la ficha equivocada.',
  /** Por que la ficha vigente no ensena los movimientos, y donde estan. */
  historicoNoSePide:
    'Esta lectura no pide el historico: viaja aparte, con «historico=true», porque son todas las versiones de la ficha y la pantalla que solo pinta la vigente no tiene por que pagarlas. Estan en la pestana de movimientos.',
  /** Que significa el historico nulo, que no es una lista vacia. */
  historicoNoPedido:
    'El servidor no mando ninguna version porque esta lectura no las pidio. No es que no haya: toda ficha tiene al menos la vigente, asi que una lista vacia aqui seria imposible y un «no hay movimientos» seria falso.',
  /** Que es un movimiento del predio, y que no lo es. */
  movimientosSonVersiones:
    'Cada fila es una version de la ficha, con quien la escribio y por que. La observacion es la mitad util: un cambio de area dice que paso de una cifra a otra, y solo la observacion dice si fue una fiscalizacion de campo o un error de tecleo. Una TRANSFERENCIA no sale aqui y no es un olvido: se anota como el cierre de una titularidad y la apertura de otra, no como un movimiento de la ficha.',
  /** Por que una actividad sin licencia se pinta distinta. */
  actividadSinLicencia:
    'Una actividad que no declara licencia no es un dato que falte: es el hallazgo, y de ahi sale una fiscalizacion. Por eso se cuenta y se marca, en vez de dejar la celda en blanco.',
  /** Que significa el reparto de los bienes comunes, y que no incluye. */
  bienesSinValor:
    'Las areas comunes van en metros cuadrados y el reparto en porcentaje. Ningun importe: cuanto vale un area comun sale del cuadro de valores unitarios, que es dato normativo y no sale de esta lectura.',
  /** Por que la superficie rural va en hectareas. */
  ruralEnHectareas:
    'La superficie rural viaja con su unidad dentro —hectareas— y no como numero suelto: el arancel rural es por hectarea, y quien la interprete en metros calcularia diez mil veces de menos.',
  /** Por que faltan columnas del artboard en los aranceles. */
  arancelSinZona:
    'El artboard pone una columna «Zona» que el cuadro no publica: «ArancelResource» trae la via, el tramo, el valor y el documento fuente, y nada mas. Se quedan las que existen.',
  /**
   * Que se pone en la columna «Via» cuando el catalogo vial no se pudo leer.
   *
   * `calles` es un `@RequiereAcceso` distinto de `aranceles`, asi que hay
   * usuarios con uno y sin el otro: el cuadro llega y el catalogo contesta 403.
   * Antes se pintaba `Via 1`, que **no se distingue del nombre de una via** —el
   * cuadro salia entero con «Via 1 — Sin tramo 388.00»—, que es el peor de los
   * tres desenlaces aplicado a una columna: ni el dato ni el error.
   */
  viaSinCatalogo: 'Sin el catalogo vial',
  /** Y cuando el catalogo SI se leyo y esa via no esta en el. */
  viaQueNoEstaEnElCatalogo: 'No esta en el catalogo vial',
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
  /* «Movimientos del Predio» del prototipo. Es una pestana aparte y no un bloque
     mas de la ficha porque es OTRA peticion —la misma ruta con
     `?historico=true`—: son todas las versiones, y la pantalla que solo pinta la
     vigente no tiene por que pagarlas. */
  { k: 'movimientos', label: 'Movimientos' },
  { k: 'frentes', label: 'Frentes' },
  /* Los hallazgos de fiscalizacion cuelgan del PREDIO y no de la campania (#71,
     AC-4): «que se le ha encontrado a este predio» es una pregunta que se hace
     mirando el predio, y la lectura que la contesta —`GET
     /fiscalizacion/predios/{predioId}/hallazgos`— existe desde #17 y no la leia
     ninguna pantalla. Es la unica ruta por la que se puede LEER un acta. */
  { k: 'hallazgos', label: 'Hallazgos' },
] as const;

/**
 * La ficha de un predio: los rotulos de sus secciones.
 *
 * Los cinco bloques que la ficha publica y que hasta #46 no se dibujaban —las
 * obras complementarias, la actividad economica, los bienes comunes, el detalle
 * rural y el historico— mas lo construido, que tampoco tenia tabla.
 */
export const FICHA = {
  cabecera: 'La version vigente',
  construcciones: 'Lo construido',
  notaDeConstrucciones:
    'Un piso por fila, con su area, su antiguedad y las siete categorias constructivas en una tira. Ningun importe: lo que vale cada categoria es dato normativo y esta lectura no lo trae.',
  instalaciones: 'Obras complementarias',
  economico: 'Actividad economica',
  bienesComunes: 'Bienes comunes',
  rural: 'Detalle rural',
  movimientos: 'Movimientos del predio',
  sinConstrucciones: 'Esta ficha no declara ninguna construccion: es un terreno sin construir.',
  sinInstalaciones: 'Esta ficha no declara ninguna obra complementaria.',
  sinActividades: 'Esta ficha economica no declara ninguna actividad.',
  sinBienes: 'Esta ficha no declara ningun bien comun.',
  sinParticipaciones: 'Esta ficha no declara ningun reparto del area comun.',
  sinTierras: 'Esta ficha rural no declara ningun grupo de tierra.',
  sinColindantes: 'Esta ficha rural no declara ningun colindante.',
  sinMovimientos: 'Esta ficha no tiene ninguna version anterior.',
  soloSuBloque:
    'Los bloques de detalle son nulos salvo el que le toca a la clase de ficha: una ficha rural no publica un bloque economico vacio, para que «este predio no declara actividad» y «esta ficha no es de las que la declaran» no se confundan.',
} as const;

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
  sectores: 'Sectores y catalogo vial',
  acciones: 'Acciones',
  activa: 'Activa',
  laVia: 'La via elegida',
  ningunaVia: 'Elija una via de la tabla para corregirla o retirarla del catalogo.',
  columnaDeLaVia: 'Elegir',
  elegirLaVia: 'Elegir',
  laVigente: 'Vigente',
  laRetirada: 'Retirada',
} as const;

/* ── El mantenimiento del catalogo territorial (#72) ─────────────────────── */

/**
 * Los actos que esta hoja OFRECE, con el rotulo que se lee en el boton.
 *
 * Las claves son las que viajan en la ruta —`?acto=altaDeSector`—, para que el
 * formulario abierto sea enlazable y para que los arneses puedan llegar a el sin
 * pulsar nada, igual que hace el asistente de alta de ficha.
 */
export const ACTOS_DEL_TERRITORIO = {
  altaDeSector: 'Registrar el sector',
  corregirSector: 'Corregir el sector',
  bajaDeSector: 'Retirar el sector del catalogo',
  reactivarSector: 'Devolver el sector al catalogo',
  altaDeManzana: 'Registrar la manzana',
  altaDeVia: 'Registrar la via',
  corregirVia: 'Corregir la via',
  bajaDeVia: 'Retirar la via del catalogo',
  reactivarVia: 'Devolver la via al catalogo',
} as const;

export const NOTAS_DE_LOS_ACTOS_DEL_TERRITORIO = {
  altaDeSector:
    'Un sector nuevo del catastro. Nace activo, y por eso no hay casilla de estado: darlo de alta ya retirado del catalogo seria un alta y una baja en un solo acto, y dejaria la auditoria con un ALTA donde hubo dos cosas.',
  corregirSector:
    'Cambia el nombre o la zona. El codigo no: es uno de los tramos del codigo de referencia catastral, y cambiarlo desalinearia el de todos los predios del sector. Lo que no se rellene, no cambia.',
  bajaDeSector:
    'El sector deja de estar en el catalogo. No se borra —sus predios siguen citandolo en codigos ya emitidos— y sus manzanas se quedan donde estan.',
  reactivarSector:
    'El sector vuelve al catalogo, con el nombre y la zona que tenia. Es la misma operacion que la baja con el estado al reves, y por eso no exige el privilegio de retirar: devolver algo al catalogo no retira nada.',
  altaDeManzana:
    'Una manzana nueva dentro de este sector. Su codigo es unico dentro del sector y no se edita despues: es otro tramo del codigo catastral de sus predios. Una manzana equivocada se resuelve dando de alta la correcta y moviendo los predios.',
  altaDeVia:
    'Una via nueva del catalogo vial. Hace falta antes de poder inscribir un predio en ella: el alta de ficha exige elegir la via del catalogo, y una que no este da un rechazo por via inexistente.',
  corregirVia:
    'Cambia el tipo, el nombre o el ubigeo. El codigo no: es lo que la direccion de cada predio cita. Lo que no se rellene, no cambia.',
  bajaDeVia:
    'La via deja de estar en el catalogo y deja de poder elegirse para un predio nuevo. No se borra: aparece en direcciones ya emitidas.',
  reactivarVia:
    'La via vuelve al catalogo y puede volver a elegirse. Como en el sector, devolver al catalogo no exige el privilegio de retirar.',
} as const;

/**
 * Lo que se advierte antes de retirar algo del catalogo, y **se confirma aparte**.
 *
 * Una baja logica no se deshace por la misma via por la que se hizo: quien la
 * revierta necesita el privilegio de modificar, y ademas el catalogo ya cambio
 * para todo el mundo mientras tanto —una via retirada deja de poder elegirse en
 * el alta de un predio—. Es el mismo trato que la anulacion de un hallazgo.
 */
export const RETIRADAS = {
  sector:
    'El sector deja de estar en el catalogo para todo el mundo. Sus predios y sus manzanas se quedan donde estan —el codigo del sector viaja dentro del codigo de referencia catastral de cada predio y no se borra nunca—, pero el sector deja de ofrecerse. Y no lo puede deshacer cualquiera: devolverlo al catalogo es otra escritura.',
  via:
    'La via deja de estar en el catalogo para todo el mundo, y con ella deja de poder elegirse al inscribir un predio nuevo. Las direcciones ya emitidas la siguen citando: aqui no se borra nada. Devolverla al catalogo es otra escritura.',
} as const;

/**
 * Que hay que HACER con cada rechazo, por operacion.
 *
 * <h2>El `403` es el que obliga a que esto exista</h2>
 *
 * La baja logica de un sector o de una via exige `ELIMINACION`, que **no es el
 * privilegio de la ruta**: el guardia comprueba `MODIFICACION` —que es lo que la
 * anotacion declara— y el controlador comprueba el otro a mano, porque cual de
 * los dos actos es depende del cuerpo y el guardia no lo lee. Asi que quien
 * puede corregir y no retirar recibe un `403` en una pantalla donde acaba de
 * guardar sin problema.
 *
 * **Y esta interfaz no puede saberlo por adelantado**: ADR-0030 §3 pone la
 * sesion y los permisos en `rentas`, y en este backend no hay ningun endpoint de
 * «quien soy» ni de «que privilegios tengo» —medido sobre los veintidos
 * `@RequestMapping` de `backend/`—. O sea que las dos salidas eran ofrecerlo
 * siempre y explicar el rechazo, o esconder el boton adivinando. Se ofrece, y el
 * rechazo dice **de que privilegio se trata y quien lo concede**, que es lo que
 * separa «no le toca a usted» de «el sistema se rompio».
 */
export const QUE_HACER = {
  codigoDeSectorRepetido:
    'Ese codigo ya lo tiene otro sector de esta municipalidad. No se arregla reintentando: se arregla con otro codigo, o corrigiendo el sector que ya lo tiene, que esta en la lista de la izquierda.',
  codigoDeManzanaRepetido:
    'Ese codigo de manzana ya esta usado EN ESTE SECTOR. El mismo codigo en otro sector es otra manzana y entra sin problema, asi que lo que hay que comprobar es en que sector se esta dando de alta.',
  codigoDeViaRepetido:
    'Ese codigo ya lo tiene otra via de esta municipalidad. Se arregla con otro codigo, o corrigiendo la via que ya lo tiene: dos vias con el mismo nombre y distinto codigo producen dos direcciones que nadie cruza.',
  sectorQueNoEsta:
    'No hay ningun sector con ese codigo en esta municipalidad. Se elige uno de la lista de la izquierda; si el que hace falta no esta, primero se da de alta.',
  viaQueNoEsta:
    'No hay ninguna via con ese codigo en esta municipalidad. Puede que se haya escrito a mano o que la lista de la tabla este vieja: conviene volver a leerla antes de insistir.',
  campoRechazado:
    'El servidor rechazo un campo del formulario y dice cual arriba. Se corrige aqui mismo y se vuelve a enviar: nada se ha guardado.',
  sinPrivilegioDeRetirar:
    'Retirar algo del catalogo exige el privilegio de ELIMINACION, que es distinto del de modificar: es lo que permite dar el mantenimiento del catalogo a quien corrige nombres sin darle con ello la potestad de retirar un sector o una via del padron. Esta pantalla no puede saber de antemano quien lo tiene —este backend no publica ninguna lectura de «que privilegios tengo»—, asi que ofrece el acto y el servidor decide. No es una averia y no se arregla desde aqui: lo concede quien administra los permisos.',
  sinPrivilegioDeEscribir:
    'Su cuenta puede leer el catalogo territorial y no escribirlo. No es una averia: es otro permiso sobre la misma pantalla, y lo concede quien administra los accesos.',
} as const;

export const CAMPOS_DEL_TERRITORIO = {
  codigoDeSector: {
    rotulo: 'Codigo del sector',
    ayuda: 'Unico en la municipalidad, y uno de los tramos del codigo de referencia catastral de sus predios. No se puede cambiar despues.',
  },
  nombreDelSector: { rotulo: 'Nombre', ayuda: 'Como se le llama en el plano y en la lista.' },
  zona: {
    rotulo: 'Zona',
    ayuda: 'Opcional. Al corregir, dejarlo en blanco conserva la que tiene: para borrarla hay que escribir un espacio, que es una instruccion y no una omision.',
  },
  codigoDeManzana: {
    rotulo: 'Codigo de la manzana',
    ayuda: 'Unico DENTRO de este sector: la misma numeracion en otro sector es otra manzana. Es otro tramo del codigo catastral, asi que tampoco se edita despues.',
  },
  codigoDeVia: {
    rotulo: 'Codigo de la via',
    ayuda: 'Unico en la municipalidad. Es lo que la direccion de cada predio cita, y no se puede cambiar despues.',
  },
  tipoDeVia: {
    rotulo: 'Tipo de via',
    ayuda: 'Del catalogo del manual. Es un enumerado y no texto libre: con texto libre la misma calle entra tres veces y el padron acaba con tres vias donde hay una.',
  },
  nombreDeLaVia: { rotulo: 'Nombre de la via', ayuda: 'Sin el tipo delante: el tipo va en su propio campo.' },
  ubigeo: {
    rotulo: 'Ubigeo',
    ayuda: 'Opcional, y son las posiciones que el INEI da al distrito. Al corregir, en blanco conserva el que tiene.',
  },
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
