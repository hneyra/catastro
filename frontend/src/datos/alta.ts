/**
 * Los ROTULOS del alta de una ficha: los seis pasos del artboard, letra por letra.
 *
 * <h2>Aqui tampoco hay ni una cifra, y lo comprueba el mismo arnes</h2>
 *
 * `verificaciones/datos.mjs` lee este directorio entero. Por eso el largo de
 * cada tramo del codigo **no esta aqui**: vive en `COMPOSICION_DEL_CODIGO`, en
 * `src/api/catastro.ts`, que es donde viven las cosas que se contrastan contra
 * el backend. No es una manera de esquivar el arnes: el largo del codigo es
 * parte del contrato —ADR-0036 dice que lo decide el tenant— y ponerlo aqui lo
 * habria convertido en un rotulo.
 *
 * <h2>Los seis pasos se portan ENTEROS, y no todos sus campos viajan</h2>
 *
 * El artboard dibuja **cuarenta y ocho** campos y `FichaController.PeticionDeAlta`
 * es una **lista blanca** que admite menos de la mitad: lo que no esta en el
 * `record` no entra **aunque llegue en el JSON**, y el servidor contesta que la
 * ficha se creo. O sea que el modo de fallo aqui no es un error: es un campo que
 * el tecnico rellena, que la pantalla acepta y que no esta en ningun sitio.
 *
 * Asi que cada campo declara `viaja`: el nombre del campo de `PeticionDeAlta`
 * que llena, o `null` con el **motivo medido** de por que no. La pantalla lo
 * ensena por campo, el resumen de «lo que se va a registrar» ensena **solo lo
 * que viaja**, y ningun campo que no viaja puede bloquear el alta.
 *
 * Se portan y no se borran porque el artboard describe **la ficha catastral del
 * manual**, que es lo que el tecnico tiene delante en campo; lo que falta no es
 * el dato, es la ruta que lo reciba. Borrarlos escondería ese hueco, que es lo
 * contrario de lo que hace el resto de esta interfaz.
 */

/* ── Lo que un campo puede ser ──────────────────────────────────────────── */

export type TipoDeCampo = 'texto' | 'sel' | 'fecha' | 'area' | 'ro' | 'chk';

export type CampoDelPaso = {
  /** La llave del artboard, que es la del borrador. */
  readonly k: string;
  readonly label: string;
  readonly tipo?: TipoDeCampo;
  /** Las opciones de un desplegable, cuando son del artboard y no del contrato. */
  readonly opciones?: readonly string[];
  /** Que enumerado del contrato llena el desplegable, cuando lo llena uno. */
  readonly delContrato?: 'tipoFicha' | 'tipoPredio' | 'origen' | 'condicionDelTitular';
  readonly ayuda?: string;
  readonly marcador?: string;
  readonly opcional?: boolean;
  /** Ocupa la fila entera de la rejilla. */
  readonly ancho?: boolean;
  /** El campo de `PeticionDeAlta` que llena, o `null` si no viaja. */
  readonly viaja: string | null;
  /** Por que no viaja. Obligatorio cuando `viaja` es `null`. */
  readonly motivo?: string;
};

export type PasoDelAlta = {
  readonly id: string;
  readonly label: string;
  readonly nota: string;
  readonly campos: readonly CampoDelPaso[];
};

/* ── Los motivos, escritos una vez y citados por los campos ─────────────── */

export const MOTIVOS_DEL_ALTA = {
  sinSitioEnLaPeticion:
    'El `record` de la peticion no tiene donde ponerlo: es una lista blanca y lo que no esta en ella se descarta en silencio, con la ficha creada igual. Se recoge en la ficha de campo y se declara aqui en vez de fingir que llega.',
  cucEsOtroIdentificador:
    'ADR-0036 le da columna propia desde la migracion del SNCP y el alta todavia no lo recibe: `PeticionDeAlta` no declara ningun campo para el. Es el identificador del SNCP y no el de referencia catastral, asi que no se puede meter por el otro.',
  catalogoNoPublicado:
    'Este backend no publica ningun catalogo del que sacar las opciones, asi que se teclea libre en vez de ofrecer una lista inventada: un desplegable con valores que nadie sembro es una eleccion entre datos que no existen.',
  esUnImporte:
    'Ni un importe viaja en el cuerpo del alta, y es la quinta regla de `CLAUDE.md`: la peticion lleva categorias, areas, superficies y porcentajes. El valor sale del conjunto sellado del ejercicio, no de lo que teclee quien ficha.',
  sinLecturaDeValuacion:
    'Este backend no publica ninguna lectura de valuacion —ninguna ruta sirve el hecho sellado de ADR-0027—, asi que no hay de donde sacar la cifra. Su corrida si produce importes; no llegan hasta aqui.',
  colindantesSoloRural:
    'Los linderos viajan como colindantes del bloque `rural`, y ese bloque solo lo admite la ruta rural: mandarlo en una ficha de otra clase es 422 «una ficha UNICA no lleva el bloque rural». Se envian cuando la clase de ficha es rural y no en las otras tres.',
  areaDeclaradaEsDeLaFicha:
    'El area declarada es la de la version anterior de la ficha, y en un alta no hay version anterior. En el artboard sale del predio que ya existe; aqui sale «—» a proposito.',
  laVerificacionEsOtroActo:
    'La verificacion en campo es un acto distinto del alta —lo que sostiene una fiscalizacion es la diferencia entre lo medido y lo declarado (ADR-0035)— y el alta no la recibe: no hay campo en la peticion ni ruta que la reciba.',
  elTitularViajaPorSuCodigo:
    'El titular viaja por su codigo del padron y nada mas: `TitularDeclarado` lleva codigo, condicion, porcentaje y documento de origen. El documento de identidad es del padron de contribuyentes, que es de «rentas», y esta interfaz no lo escribe.',
  manzanaUrbanisticaNoEsLaDelCodigo:
    'La manzana y el lote urbanisticos son los de la habilitacion urbana, y los que viajan son los del codigo de referencia catastral —los tramos «manzana» y «lote»—. Son dos numeraciones distintas y confundirlas emparejaria la ficha con otra manzana.',
} as const;

/* ── Los avisos y rotulos del asistente ─────────────────────────────────── */

export const ALTA = {
  titulo: 'Ficha nueva',
  estado: 'Borrador',
  sinTitular: 'Sin titular asignado · nada se registra hasta el ultimo paso',
  conTitular: 'borrador, nada se registra hasta el ultimo paso',
  descartar: 'Descartar',
  registrar: 'Registrar la ficha',
  continuar: 'Continuar',
  anterior: 'Anterior',
  primerPaso: 'Este es el primer paso: no hay ninguno anterior',
  borradorAlAvanzar: 'El borrador vive en esta pantalla y no se guarda en ningun sitio hasta registrar.',

  /* El codigo por tramos. */
  codigo: 'Codigo de referencia catastral',
  codigoNota:
    'Ocho tramos de longitud fija. El sector y la manzana tienen que existir en Territorio, y el largo del codigo lo decide el tenant (ADR-0036).',
  codigoCompleto: 'Completo',
  codigoIncompleto: 'tramos completos',
  codigoUsado: 'Codigo ya usado',
  codigoAyuda:
    'Cada tramo se rellena con ceros a la izquierda hasta su longitud. Si no conoce el lote, busquelo en el plano catastral.',
  /* El mensaje del artboard, con el predio que lo ocupa puesto por el servidor y
     no inventado aqui: el artboard nombra «Calle Bolivar 539» porque lo sabe de
     sus propios datos, y aqui lo dice el 409. */
  codigoDuplicado:
    'Ese codigo ya esta asignado a otro predio. Dos fichas sobre el mismo lote acaban en dos deudas por el mismo predio.',

  /* «Lo que se va a registrar». */
  resumen: 'Lo que se va a registrar',
  resumenNota:
    'Solo esto viaja. Una ficha registrada entra en el padron y desde ese momento el predio tiene ficha catastral.',
  noViaja: 'no viaja',
  noViajanTitulo: 'Lo que se rellena y no viaja',
  noViajanNota:
    'El artboard dibuja la ficha catastral del manual y el cuerpo del alta admite menos de la mitad de sus campos. Ninguno de estos bloquea el registro, y ninguno se envia: lo que falta no es el dato sino la ruta que lo reciba.',

  /* Los tres desenlaces, cada uno con lo que hay que hacer. */
  falloValidacion: 'El servidor rechazo un campo',
  falloValidacionQueHacer:
    'Es 422 VALIDACION: hay un campo que corregir en este mismo asistente. El servidor dice cual.',
  falloConflicto: 'Ese codigo ya esta inscrito',
  falloConflictoQueHacer:
    'Es 409 CONFLICTO. O el codigo ya es de otro predio, o ese predio ya tiene ficha de esta clase — y entonces lo que toca no es un alta sino actualizarla, que es otra operacion.',
  falloNoEncontrado: 'La via, el sector o la manzana no existen',
  falloNoEncontradoQueHacer:
    'Es 404 NO_ENCONTRADO, y no se arregla aqui: hay que darlos de alta en Territorio antes de fichar el predio.',
  registrada: 'Ficha registrada',

  /* La via, que sale del catalogo. */
  viaBuscar: 'Buscar la via en el catalogo',
  viaMarcador: 'Nombre de la via, por ejemplo Bolivar',
  viaSinBuscar: 'Escriba parte del nombre para buscarla en el catalogo vial.',
  viaSinResultados: 'Ninguna via del catalogo coincide. Se da de alta en Territorio.',
  viaElegida: 'Via elegida',
  viaQuitar: 'Elegir otra via',

  /* Las construcciones. */
  pisos: 'Pisos declarados',
  anadirPiso: 'Anadir piso',
  quitarPiso: 'Quitar este piso',
  sinPisos: 'Todavia no hay pisos declarados. Anada uno por cada nivel construido.',
  notaDeLasCategorias:
    'Las letras son muros y columnas, techos, pisos, puertas y ventanas, revestimientos, banios e instalaciones: siete partidas y no cinco, que son las que el `record` del backend admite.',
  notaDelAnio:
    'El artboard pide la antiguedad en anios y el backend recibe el ANO de construccion: derivar uno del otro necesitaria un reloj, y una regla que depende del reloj no vuelve a dar la misma cifra diez anios despues.',
} as const;

/* ── Los rotulos de los enumerados del contrato ─────────────────────────── */

/** `TipoFicha` -> como se lee. Son las cuatro clases, y cada una tiene su ruta. */
export const ROTULO_DE_TIPO_DE_FICHA: Record<string, string> = {
  UNICA: 'Urbana (unica)',
  ECONOMICA: 'Economica',
  BIENES_COMUNES: 'Bienes comunes',
  RURAL: 'Rural',
};

export const ROTULO_DE_TIPO_DE_PREDIO: Record<string, string> = {
  URBANO: 'Urbano',
  RUSTICO: 'Rustico',
};

/**
 * `OrigenDeLaFicha` -> como se lee.
 *
 * El artboard llama a este campo «Fuente del dato» y ofrece «INSPECCION DE
 * CAMPO», «DECLARACION JURADA» y «RESTITUCION FOTOGRAMETRICA». **Solo la del
 * medio existe**: el enumerado del backend tiene estos cuatro, y cualquier otro
 * valor es 422 «el campo 'origen' no admite el valor». Ofrecer los del artboard
 * seria ofrecer un error en dos de las tres opciones.
 */
export const ROTULO_DE_ORIGEN: Record<string, string> = {
  DECLARACION_JURADA: 'Declaracion jurada',
  FISCALIZACION: 'Fiscalizacion',
  RESOLUCION: 'Resolucion',
  MIGRACION: 'Migracion',
};

export const ROTULO_DE_MATERIAL: Record<string, string> = {
  CONCRETO: 'Concreto',
  LADRILLO: 'Ladrillo o bloque de cemento',
  ADOBE: 'Adobe o tapial',
  MADERA: 'Madera',
  QUINCHA: 'Quincha',
  OTRO: 'Otro',
};

export const ROTULO_DE_ESTADO_DE_CONSERVACION: Record<string, string> = {
  MUY_BUENO: 'Muy bueno',
  BUENO: 'Bueno',
  REGULAR: 'Regular',
  MALO: 'Malo',
  RUINOSO: 'Ruinoso',
};

/**
 * `CondicionDeTitularidad` -> como se lee.
 *
 * El artboard ofrece cuatro —«Propietario», «Poseedor», «Copropietario»,
 * «Usufructuario»— y el enumerado tiene seis: le anade el conyuge y la sucesion,
 * que son las dos condiciones por las que una titularidad se reparte sin que
 * nadie compre nada. «Propietario» es `PROPIETARIO_UNICO`, que es la unica que
 * responde `esPorElTotal`.
 */
export const ROTULO_DE_CONDICION_DEL_TITULAR: Record<string, string> = {
  PROPIETARIO_UNICO: 'Propietario unico',
  COPROPIETARIO: 'Copropietario',
  CONYUGE: 'Conyuge',
  POSEEDOR: 'Poseedor',
  SUCESION: 'Sucesion',
  USUFRUCTUARIO: 'Usufructuario',
};

/**
 * El rotulo de cada campo de `PeticionDeAlta` en el resumen.
 *
 * El resumen se compone **recorriendo la peticion ya armada**, no una lista
 * escrita al lado: asi «lo que se va a registrar» no puede decir una cosa y el
 * cuerpo llevar otra. Un campo sin rotulo aqui sale con su nombre del contrato,
 * que es feo y correcto; inventarle uno seria peor.
 */
export const ROTULO_DEL_CAMPO: Record<string, string> = {
  observacion: 'Observacion',
  codRefCatastral: 'Codigo de referencia catastral',
  tipoPredio: 'Tipo de predio',
  direccion: 'Direccion',
  codigoDeVia: 'Via del catalogo',
  numeroMunicipal: 'Numero municipal',
  codigoDeSector: 'Sector',
  codigoDeManzana: 'Manzana',
  lote: 'Lote',
  ubigeo: 'Ubigeo',
  areaTerreno: 'Area del terreno',
  uso: 'Uso del predio',
  denominacion: 'Denominacion',
  vigenciaDesde: 'Vigente desde',
  origen: 'Origen de la ficha',
  documentoOrigen: 'Documento de origen',
  construcciones: 'Construcciones',
  instalaciones: 'Obras complementarias',
  rural: 'Colindantes',
  titular: 'Titular',
};

export const ROTULO_DE_ORIENTACION: Record<string, string> = {
  NORTE: 'Lindero norte',
  SUR: 'Lindero sur',
  ESTE: 'Lindero este',
  OESTE: 'Lindero oeste',
};

/* ── Los seis pasos ─────────────────────────────────────────────────────── */

export const PASOS: readonly PasoDelAlta[] = [
  {
    id: 'ident',
    label: 'Identificacion',
    nota: 'Para que se usa el predio y de que habilitacion urbana forma parte. El uso decide que tabla de valores unitarios se le aplica despues.',
    campos: [
      {
        k: 'tipoFicha',
        label: 'Clase de ficha',
        tipo: 'sel',
        delContrato: 'tipoFicha',
        ayuda: 'Decide a cual de las cuatro rutas va el alta, y que permiso exige',
        viaja: null,
        motivo:
          'No es un campo del cuerpo: es la ruta. Cada clase de ficha tiene su `@PostMapping` y su propio acceso, asi que la clase no se manda, se elige a donde se manda.',
      },
      {
        k: 'uso',
        label: 'Uso del predio',
        tipo: 'sel',
        opciones: ['Casa habitacion', 'Comercio', 'Industria', 'Terreno sin construir', 'Servicios', 'Otros'],
        ayuda: 'Decide la tabla de valores unitarios',
        viaja: 'uso',
      },
      {
        k: 'tipoPredio',
        label: 'Tipo de predio',
        tipo: 'sel',
        delContrato: 'tipoPredio',
        ayuda: 'Sin declararlo, el servidor lo toma urbano',
        viaja: 'tipoPredio',
      },
      {
        k: 'cuc',
        label: 'Codigo unico catastral',
        opcional: true,
        ayuda: 'El que asigna el SNCP, si ya lo tiene',
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.cucEsOtroIdentificador,
      },
      {
        k: 'hojaCat',
        label: 'Hoja catastral',
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.catalogoNoPublicado,
      },
      {
        k: 'habUrb',
        label: 'Habilitacion urbana',
        marcador: 'Ej. Urb. Santa Rosa',
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.sinSitioEnLaPeticion,
      },
      {
        k: 'condPredio',
        label: 'Condicion del predio',
        tipo: 'sel',
        opciones: ['En propiedad exclusiva', 'En propiedad horizontal', 'En copropiedad', 'Posesion informal'],
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.sinSitioEnLaPeticion,
      },
      {
        k: 'denominacion',
        label: 'Denominacion del predio',
        marcador: 'Ej. Mercado Modelo',
        opcional: true,
        ayuda: 'Como se le conoce, cuando tiene nombre propio',
        viaja: 'denominacion',
      },
      {
        k: 'fecha',
        label: 'Fecha del levantamiento',
        tipo: 'fecha',
        opcional: true,
        ayuda: 'Es desde cuando rige la version uno de la ficha; sin ella, el servidor pone la de hoy',
        viaja: 'vigenciaDesde',
      },
      {
        k: 'fuente',
        label: 'Fuente del dato',
        tipo: 'sel',
        delContrato: 'origen',
        opcional: true,
        ayuda: 'Sin declararla, el servidor la toma como declaracion jurada',
        viaja: 'origen',
      },
      {
        k: 'documentoOrigen',
        label: 'Documento de origen',
        marcador: 'Ej. el numero de la declaracion jurada',
        ayuda: 'El documento que sostiene el alta. El servidor lo exige y el artboard no lo pide',
        viaja: 'documentoOrigen',
      },
      {
        k: 'obsIdent',
        label: 'Observaciones',
        tipo: 'area',
        ancho: true,
        opcional: true,
        marcador: 'Lo que haya que saber antes de volver al predio',
        viaja: null,
        motivo:
          'La peticion lleva UNA observacion, y es la de RNF-052 que justifica la escritura: se pide en el ultimo paso, junto al resumen. Esta es una nota sobre el predio, que es otra cosa y no tiene campo.',
      },
    ],
  },
  {
    id: 'ubic',
    label: 'Ubicacion',
    nota: 'La via sale del catalogo de Territorio, no se escribe libre: dos formas de escribir la misma calle producen dos direcciones que nadie cruza.',
    campos: [
      {
        k: 'dep',
        label: 'Departamento',
        tipo: 'ro',
        ayuda: 'Sale del codigo: las dos primeras posiciones',
        viaja: null,
        motivo:
          'Se deriva del codigo y no se teclea: el ubigeo son los tres primeros tramos, que es lo que devuelve `CodigoReferenciaCatastral.ubigeo()`. Viaja entero como `ubigeo`.',
      },
      {
        k: 'prov',
        label: 'Provincia',
        tipo: 'ro',
        ayuda: 'Sale del codigo: la tercera y la cuarta',
        viaja: null,
        motivo:
          'Se deriva del codigo, igual que el departamento. Viaja dentro de `ubigeo` y no por separado.',
      },
      {
        k: 'dist',
        label: 'Distrito',
        tipo: 'ro',
        ayuda: 'Sale del codigo: la quinta y la sexta',
        viaja: null,
        motivo:
          'Se deriva del codigo, igual que el departamento. Viaja dentro de `ubigeo` y no por separado.',
      },
      {
        k: 'numMun',
        label: 'Numero municipal',
        opcional: true,
        ayuda: 'Con la via, compone la direccion que el servidor exige',
        viaja: 'numeroMunicipal',
      },
      {
        k: 'numAd',
        label: 'Numero adicional',
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.sinSitioEnLaPeticion,
      },
      {
        k: 'manzUrb',
        label: 'Manzana urbanistica',
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.manzanaUrbanisticaNoEsLaDelCodigo,
      },
      {
        k: 'loteUrb',
        label: 'Lote urbanistico',
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.manzanaUrbanisticaNoEsLaDelCodigo,
      },
      {
        k: 'referencia',
        label: 'Referencia',
        ancho: true,
        opcional: true,
        marcador: 'Ej. frente al mercado modelo',
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.sinSitioEnLaPeticion,
      },
    ],
  },
  {
    id: 'terreno',
    label: 'Terreno',
    nota: 'El area y el frontis se toman en campo. Con el arancel de la via, esta seccion ya da el valor del terreno.',
    campos: [
      {
        k: 'areaTerreno',
        label: 'Area del terreno (m²)',
        ayuda: 'Como figura en el titulo o como se midio',
        viaja: 'areaTerreno',
      },
      {
        k: 'frontis',
        label: 'Metros de frontis',
        opcional: true,
        ayuda: 'De el dependen los arbitrios de barrido',
        viaja: null,
        motivo:
          'El alta no lo recibe: `DatosDeLaFicha` no declara frontis, aunque la lectura de la ficha si lo publique. Y este sistema no determina ningun arbitrio con el —eso es «rentas», ADR-0024—: el frente lineal se deriva cortando el lote contra el eje de la via, y se confirma como un acto aparte.',
      },
      {
        k: 'fondo',
        label: 'Fondo (m)',
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.sinSitioEnLaPeticion,
      },
      {
        k: 'forma',
        label: 'Forma del lote',
        tipo: 'sel',
        opciones: ['Regular', 'Irregular', 'En esquina'],
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.sinSitioEnLaPeticion,
      },
      {
        k: 'arancel',
        label: 'Arancel de la via (S/ por m²)',
        tipo: 'ro',
        ayuda: 'Lo pone la tabla del ejercicio',
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.esUnImporte,
      },
      {
        k: 'valTerreno',
        label: 'Valor del terreno (S/)',
        tipo: 'ro',
        ayuda: 'Area por arancel',
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.sinLecturaDeValuacion,
      },
    ],
  },
  {
    id: 'const',
    label: 'Construcciones',
    nota: 'Cada piso se describe por sus categorias constructivas y su ano de construccion. La depreciacion se aplica sobre el valor unitario, no sobre el total.',
    campos: [
      {
        k: 'numPisos',
        label: 'Numero de pisos',
        tipo: 'ro',
        ayuda: 'Cada piso se describe por separado, en la tabla de abajo',
        viaja: null,
        motivo:
          'No se teclea: son los pisos que la tabla declara, y contarlos aparte permitiria decir «tres» con dos filas. Los pisos viajan como `construcciones`.',
      },
      {
        k: 'areaConst',
        label: 'Area construida total (m²)',
        tipo: 'ro',
        opcional: true,
        ayuda: 'La suma de los pisos declarados',
        viaja: null,
        motivo:
          'No se teclea ni viaja: el area construida de la ficha es la de sus construcciones, y un total escrito a mano puede no cuadrar con las filas que lo componen.',
      },
      {
        k: 'valConst',
        label: 'Valor de las construcciones (S/)',
        tipo: 'ro',
        ayuda: 'Suma de los pisos, ya depreciada',
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.sinLecturaDeValuacion,
      },
    ],
  },
  {
    id: 'titular',
    label: 'Titularidad',
    nota: 'Sin titular la ficha existe pero no puede emitir: la deuda no tendria a quien cobrarse. El contribuyente sale del padron de Rentas.',
    campos: [
      {
        k: 'contrib',
        label: 'Contribuyente',
        ancho: true,
        opcional: true,
        marcador: 'Codigo del padron de Rentas',
        ayuda: 'Se deja vacio cuando se ficha el predio antes de identificar al propietario',
        viaja: 'titular.codigoContribuyente',
      },
      {
        k: 'docTit',
        label: 'Documento',
        tipo: 'sel',
        opciones: ['DNI', 'RUC', 'Carnet de extranjeria'],
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.elTitularViajaPorSuCodigo,
      },
      {
        k: 'numDocTit',
        label: 'Numero de documento',
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.elTitularViajaPorSuCodigo,
      },
      {
        k: 'porcProp',
        label: 'Porcentaje de propiedad (%)',
        opcional: true,
        ayuda: 'La suma de todos los titulares da el total',
        viaja: 'titular.porcentaje',
      },
      {
        k: 'formaAdq',
        label: 'Forma de adquisicion',
        tipo: 'sel',
        opciones: ['Compra-venta', 'Donacion', 'Herencia', 'Adjudicacion', 'Prescripcion adquisitiva'],
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.sinSitioEnLaPeticion,
      },
      {
        k: 'fechaAdq',
        label: 'Fecha de adquisicion',
        tipo: 'fecha',
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.sinSitioEnLaPeticion,
      },
      {
        k: 'partida',
        label: 'Partida registral',
        opcional: true,
        ayuda: 'Es el documento de origen del titular, y el servidor lo exige en cuanto hay titular',
        viaja: 'titular.documentoOrigen',
      },
      {
        k: 'condTit',
        label: 'Condicion del titular',
        tipo: 'sel',
        delContrato: 'condicionDelTitular',
        opcional: true,
        viaja: 'titular.condicion',
      },
    ],
  },
  {
    id: 'verif',
    label: 'Verificacion',
    nota: 'Lo que el tecnico midio frente a lo que el titular declaro. La diferencia es lo que sostiene una fiscalizacion.',
    campos: [
      /* La observacion de RNF-052 vive en el ULTIMO paso, junto al resumen: es lo
         que justifica la escritura, y se escribe cuando ya se sabe que se va a
         escribir. El artboard no la tiene —su asistente no llega a guardar
         nada—, y sin ella el servidor contesta 422 y no guarda ni una fila. */
      {
        k: 'observacion',
        label: 'Observacion del alta',
        tipo: 'area',
        ancho: true,
        marcador: 'Por que se registra esta ficha, y con que documento',
        ayuda:
          'RNF-052: toda modificacion de datos exige la observacion del usuario. Sin ella el servidor contesta 422 y no guarda nada — ni la ficha, ni el predio, ni la titularidad',
        viaja: 'observacion',
      },
      {
        k: 'areaDecl',
        label: 'Area construida declarada (m²)',
        tipo: 'ro',
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.areaDeclaradaEsDeLaFicha,
      },
      {
        k: 'areaVer',
        label: 'Area construida verificada (m²)',
        opcional: true,
        ayuda: 'Lo que midio el verificador en campo',
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.laVerificacionEsOtroActo,
      },
      {
        k: 'tecnico',
        label: 'Tecnico verificador',
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.catalogoNoPublicado,
      },
      {
        k: 'fechaVer',
        label: 'Fecha de verificacion',
        tipo: 'fecha',
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.laVerificacionEsOtroActo,
      },
      {
        k: 'conforme',
        label: 'Resultado',
        tipo: 'chk',
        marcador: 'Lo verificado coincide con lo declarado',
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.laVerificacionEsOtroActo,
      },
      {
        k: 'obsVer',
        label: 'Observaciones de la verificacion',
        tipo: 'area',
        ancho: true,
        opcional: true,
        viaja: null,
        motivo: MOTIVOS_DEL_ALTA.laVerificacionEsOtroActo,
      },
    ],
  },
];
