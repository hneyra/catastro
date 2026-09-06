/**
 * La unica puerta por la que salen las peticiones al backend de `catastro`.
 *
 * No hay `fetch` suelto en ninguna pantalla —lo vigila `eslint.config.mjs` y lo
 * demuestra `verificaciones/reglas.mjs`—, que es lo que permite cambiar el
 * origen, el token o el trato de los errores en un solo sitio.
 *
 * <h2>Lo medido contra `backend/`, que es de donde sale todo lo de aqui</h2>
 *
 * - La raiz es `Api.RAIZ = "/catastro/api/v1"`, y **es relativa a proposito**.
 *   El backend no tiene ni una linea de configuracion de CORS —cero ocurrencias
 *   de `cors` y de `allowedOrigins` en todo el arbol—, asi que la interfaz se
 *   sirve del mismo origen o no se sirve. En desarrollo lo reenvia Vite; en la
 *   imagen, nginx.
 * - Los errores son RFC 9457 con las extensiones `codigo`, `mensaje`,
 *   `detalles`, `incidencia` y `parametroQueFalta`, **al primer nivel del
 *   cuerpo** (`ManejadorDeErrores` las pone con `setProperty`).
 * - **Los 401 y 403 que emiten los filtros traen otro cuerpo, mas corto.**
 *   `RespuestaDeError` escribe el JSON a mano y emite exactamente cuatro campos
 *   —`status`, `title`, `codigo`, `mensaje`— **sin `type` y sin `detail`**,
 *   porque esos rechazos ocurren ANTES del `DispatcherServlet` y no pasan por el
 *   `@RestControllerAdvice`. De ahi la regla que gobierna la lectura de un
 *   error aqui: lo unico que esta siempre es `codigo`, y es lo que se mira.
 * - El `municipalidadId` **no se envia nunca**: sale del claim
 *   `municipalidad_id` del token (`TenantContextFilter`). Por eso un defecto de
 *   esta interfaz no puede filtrar entre municipalidades — no tiene por donde.
 */

/**
 * El catalogo de errores del backend, tal como lo declara su `CodigoDeError`.
 *
 * Es una **lista en tiempo de ejecucion**, y el tipo se deriva de ella y no al
 * reves. El motivo no es de estilo: lo que llega por el cable es una cadena
 * cualquiera —el `codigo` lo escribe el servidor— y sin una lista a la que
 * preguntar, la unica manera de meterla en la union seria un `as`. Eso compila,
 * y a partir de ahi **el tipo miente**: la union afirma que solo puede valer una
 * de estas cosas y por ahi entra cualquier cadena, ningun `switch` tiene su rama
 * y `tsc` no dice nada.
 *
 * Son los **once** de `CodigoDeError.java`, en su orden, mas `SIN_RESPUESTA`,
 * que es el unico que no esta en el enumerado del backend y **no puede estarlo**:
 * nombra que no hubo ninguna respuesta que clasificar.
 */
export const CODIGOS_DE_ERROR = [
  'NO_AUTENTICADO',
  'SIN_MUNICIPALIDAD',
  'SIN_DOCUMENTO',
  'SIN_PRIVILEGIO',
  'VALIDACION',
  'ORDEN_NO_ADMITIDO',
  /* El marco pedido tiene dentro mas lotes de los que el servidor dibuja. No es
     un error de quien pregunta ni un fallo del servidor: es LA RESPUESTA, y la
     resuelve acercar el marco. Por eso no comparte codigo con `VALIDACION`. */
  'MARCO_CON_DEMASIADOS_LOTES',
  'NO_ENCONTRADO',
  'METODO_NO_ADMITIDO',
  'CONFLICTO',
  'ERROR_INTERNO',
  /* No lo produce el servidor: es lo que se sabe cuando la peticion no llego a
     tener respuesta —red caida, servidor apagado, origen distinto—. Se distingue
     del ERROR_INTERNO porque ese si llego y trae incidencia con la que preguntar. */
  'SIN_RESPUESTA',
] as const;

export type CodigoDeError = (typeof CODIGOS_DE_ERROR)[number];

/**
 * Los que esta interfaz ANADE, y el motivo de cada uno.
 *
 * No es documentacion: lo lee `verificaciones/rutas.mjs`. La relacion entre las
 * dos listas **no es igualdad** —los del backend estan todos aqui, y ademas hay
 * estos—, asi que una guarda que exigiera conjuntos iguales pondria roja una
 * diferencia que es correcta. Lo que se exige es lo otro: que cada anadido este
 * declarado con su motivo, en vez de aparecer y que nadie sepa de donde salio.
 */
export const CODIGOS_QUE_ANADE_EL_CLIENTE: Readonly<Record<string, string>> = {
  SIN_RESPUESTA:
    'No lo produce ningun servidor: nombra que no hubo ninguna respuesta que clasificar —red caida, ' +
    'servidor apagado, origen distinto—. No puede estar en el enumerado del backend.',
};

/**
 * Los del backend, derivados y no repetidos.
 *
 * Se quita lo que esta interfaz declara como propio; lo que queda tiene que ser
 * `CodigoDeError.java` letra por letra, y eso lo comprueba `rutas.mjs` contra el
 * fuente de verdad.
 */
export const CODIGOS_DEL_BACKEND = CODIGOS_DE_ERROR.filter((c) => !(c in CODIGOS_QUE_ANADE_EL_CLIENTE));

/**
 * El miembro `parametroQueFalta` de un cuerpo `problem+json`.
 *
 * `ejercicio` va siempre; `llave` **solo cuando el backend sabe cual es**
 * —`TIPO:CLAVE` si falta una fila, el `TIPO` solo si falta el bloque entero— y
 * desaparece del cuerpo cuando lo que falta es el conjunto sellado del ano.
 * Medido en `ParametroQueFalta.comoMiembro()`: no llega como `null`, no llega.
 *
 * **Las tres lecturas de cuadro nunca nombran una llave**, y esta medido: sus
 * controladores solo atrapan `LectorDeParametros.EjercicioSinSellar`, cuyo
 * `llave()` es `Optional.empty()`. Asi que quien lo lea tiene que saber decir
 * las dos cosas: «falta esta fila» y «falta el conjunto del ano».
 */
export type ParametroQueFalta = {
  readonly ejercicio: number;
  readonly llave?: string;
};

export class ErrorDeApi extends Error {
  constructor(
    readonly codigo: CodigoDeError,
    readonly mensaje: string,
    readonly estado: number,
    /** Solo en los 500. Es lo que se le da a quien atiende para preguntar. */
    readonly incidencia?: string,
    /**
     * Las cifras del rechazo, como dato y no dentro de la frase.
     *
     * `ManejadorDeErrores` no escribe el campo cuando la lista esta vacia, asi
     * que quien lo lea tiene que admitir que no este: la ausencia es «este
     * rechazo no publica cifras», nunca un cero.
     */
    readonly detalles?: string[],
    /**
     * Lo que hay que publicar, cuando lo que falta es una cifra normativa.
     *
     * Es el discriminador de dos cosas que salen con el mismo `codigo` y el
     * mismo `estado`: «falta un campo de la peticion» lo arregla quien atiende,
     * en la misma pantalla; «el ejercicio no tiene un conjunto sellado» **no lo
     * arregla nadie desde la pantalla**. Si esta, no se arregla aqui — esa es
     * toda la regla, y por eso se mira su PRESENCIA antes que su contenido.
     *
     * **Es un OBJETO, y esto se corrigio midiendo.** El javadoc anterior decia
     * «lo emite como cadena» y la lectura preguntaba `typeof === 'string'`, asi
     * que `faltaUnaCifraNormativa` valia `false` SIEMPRE y el aviso que existe
     * para nombrar la llave no salia nunca. Medido en
     * `ParametroQueFalta.comoMiembro()`, que compone un `LinkedHashMap` con
     * `ejercicio` siempre y `llave` **solo cuando la hay** —a proposito, para
     * que su ausencia no llegue como un `null`, que es un valor—.
     */
    readonly parametroQueFalta?: ParametroQueFalta,
  ) {
    super(mensaje);
    this.name = 'ErrorDeApi';
  }

  /** Si lo que falta es una cifra normativa y no un dato de la peticion. */
  get faltaUnaCifraNormativa(): boolean {
    return this.parametroQueFalta !== undefined;
  }

  /**
   * Si tiene sentido volver a intentarlo tal cual, sin cambiar nada.
   *
   * «Reintentar» solo se ofrece donde reintentar puede cambiar algo. Un
   * privilegio que falta sale igual las veces que se pulse; un verbo que la ruta
   * no admite no puede funcionar nunca.
   */
  get reintentable(): boolean {
    return this.codigo === 'SIN_RESPUESTA' || this.codigo === 'ERROR_INTERNO';
  }
}

/**
 * La raiz de la API.
 *
 * Vale una RUTA y nunca una URL absoluta: Vite resuelve `import.meta.env` **al
 * compilar**, asi que un dominio escrito aqui queda horneado en el paquete
 * estatico y, como la misma imagen se despliega en varias municipalidades, las
 * dos mitades acabarian apuntando a servidores distintos, en verde y sin un solo
 * sintoma.
 */
export const RAIZ: string = import.meta.env.VITE_CATASTRO_API ?? '/catastro/api/v1';

/**
 * El token con el que se firma cada peticion.
 *
 * ADR-0030 §3 pone la sesion y los permisos en `rentas`, y en este backend no
 * hay ningun endpoint de «quien soy» —los 64 medidos son de catastro, y ninguno
 * de seguridad—. Asi que mientras no exista esa puerta, el token sale de
 * `VITE_CATASTRO_TOKEN` o de `localStorage`. Cuando exista, cambia esta funcion
 * y nada mas.
 */
export function token(): string | null {
  const deEntorno = import.meta.env.VITE_CATASTRO_TOKEN;
  if (deEntorno) return deEntorno;
  try {
    return localStorage.getItem('catastro.token');
  } catch {
    /* Una ventana privada puede prohibir el almacenamiento, y eso no es motivo
       para que la aplicacion no arranque. */
    return null;
  }
}

export function fijarToken(valor: string | null): void {
  try {
    if (valor === null) localStorage.removeItem('catastro.token');
    else localStorage.setItem('catastro.token', valor);
  } catch {
    /* Ventana privada: no se puede guardar, y no es motivo para reventar. */
  }
  sesion += 1;
  oyentes.forEach((f) => f());
}

/**
 * Cuantas veces ha cambiado la credencial.
 *
 * Sirve de llave a las lecturas: al dar un token, **todas** vuelven a pedirse y
 * no solo la que se esta mirando.
 */
let sesion = 0;
const oyentes = new Set<() => void>();

export function sesionActual(): number {
  return sesion;
}

export function alCambiarLaSesion(f: () => void): () => void {
  oyentes.add(f);
  return () => {
    oyentes.delete(f);
  };
}

/**
 * Sustituye los `{parametro}` de una ruta del contrato por sus valores.
 *
 * Las rutas se escriben con sus llaves —`/catastro/predios/{predioId}`— y no
 * interpoladas, y no es cosmetica: asi la ruta que este archivo declara es la
 * MISMA cadena que el `@RequestMapping` del backend, letra por letra, y
 * `verificaciones/rutas.mjs` puede compararlas. Con una plantilla interpolada,
 * lo unico comparable seria un prefijo, y una ruta que no existe en el backend
 * pasaria en verde.
 */
export function camino(plantilla: string, valores: Record<string, string | number> = {}): string {
  return plantilla.replace(/\{(\w+)\}/g, (_todo, clave: string) => {
    const valor = valores[clave];
    if (valor === undefined) throw new Error(`Falta el parametro «${clave}» de la ruta «${plantilla}»`);
    return encodeURIComponent(String(valor));
  });
}

export type ValorDeParametro = string | number | boolean | null | undefined;

export type Opciones = {
  metodo?: 'GET' | 'POST' | 'PUT' | 'PATCH';
  /** Los parametros de consulta. Los `undefined`, `null` y `''` no viajan. */
  parametros?: Record<string, ValorDeParametro | readonly ValorDeParametro[]>;
  cuerpo?: unknown;
  senal?: AbortSignal;
};

/** El sobre en que sale todo listado. Uno solo, medido en `RespuestaPaginada.java`. */
export type RespuestaPaginada<T> = {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
  hayMas: boolean;
};

/** Lo que `ParametrosDePaginacion` acepta, con los nombres que viajan. */
export type Paginacion = {
  pagina?: number;
  tamano?: number;
  ordenarPor?: string;
  direccion?: 'ASCENDENTE' | 'DESCENDENTE';
};

function componer(ruta: string, parametros: Opciones['parametros']): URL {
  const url = new URL(RAIZ + ruta, window.location.origin);
  for (const [clave, valor] of Object.entries(parametros ?? {})) {
    if (Array.isArray(valor)) {
      /* Los repetidos —`?predio=1&predio=2`, que es como
         `TitularidadDelPredioController` recibe su `List<Long>`— van uno a uno:
         un `append` por elemento y no un `set` con comas. */
      for (const uno of valor) {
        if (uno === undefined || uno === null || uno === '') continue;
        url.searchParams.append(clave, String(uno));
      }
      continue;
    }
    if (valor === undefined || valor === null || valor === '') continue;
    url.searchParams.set(clave, String(valor));
  }
  return url;
}

export async function solicitar<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const { metodo = 'GET', parametros, cuerpo, senal } = opciones;
  const url = componer(ruta, parametros);

  const cabeceras: Record<string, string> = { Accept: 'application/json' };
  const jwt = token();
  if (jwt) cabeceras.Authorization = `Bearer ${jwt}`;
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';

  let respuesta: Response;
  try {
    respuesta = await fetch(url, {
      method: metodo,
      headers: cabeceras,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      signal: senal,
    });
  } catch (fallo) {
    /* Una cancelacion no es un fallo: se propaga tal cual para que quien la
       pidio la distinga y no dibuje un error por haber cambiado de pantalla. */
    if (fallo instanceof DOMException && fallo.name === 'AbortError') throw fallo;
    throw new ErrorDeApi('SIN_RESPUESTA', 'No se pudo contactar con el servidor', 0);
  }

  if (respuesta.status === 204) return undefined as T;

  const texto = await respuesta.text();
  const datos: unknown = texto ? intentarLeer(texto) : null;

  if (!respuesta.ok) throw errorDe(respuesta.status, datos);

  /* Un 200 cuyo cuerpo no es JSON no es una respuesta vacia: es OTRA COSA
     contestando. Pasa con un reenvio mal configurado, que devuelve el
     `index.html` de la propia interfaz con 200, y tambien con un portal cautivo.
     Sin esta guarda la pantalla se queda sin datos y sin error, y se dibuja EN
     BLANCO: el peor de los tres desenlaces, porque no hay nada que leer ni nada
     que reintentar. */
  if (texto !== '' && datos === null) {
    throw new ErrorDeApi(
      'SIN_RESPUESTA',
      'El servidor contesto algo que no es la API. Revisa a donde apunta el reenvio de /catastro.',
      respuesta.status,
    );
  }
  return datos as T;
}

function intentarLeer(texto: string): unknown {
  try {
    return JSON.parse(texto) as unknown;
  } catch {
    return null;
  }
}

/**
 * Traduce la respuesta de error a un `ErrorDeApi`.
 *
 * **No se confia ni en `type` ni en `detail`**: los 401 y 403 de los filtros no
 * los traen. Si el cuerpo no trae `codigo` —una pagina de error de nginx, un 502
 * de un reenvio— se deduce del estado. Lo que no se hace nunca es ensenar el
 * cuerpo crudo: puede traer una traza.
 */
function errorDe(estado: number, datos: unknown): ErrorDeApi {
  const cuerpo = (datos ?? {}) as Record<string, unknown>;
  const codigo = esCodigoConocido(cuerpo.codigo) ? cuerpo.codigo : porEstado(estado);
  const mensaje =
    typeof cuerpo.mensaje === 'string'
      ? cuerpo.mensaje
      : typeof cuerpo.title === 'string'
        ? cuerpo.title
        : 'No se pudo completar la operacion';
  const incidencia = typeof cuerpo.incidencia === 'string' ? cuerpo.incidencia : undefined;
  const detalles = Array.isArray(cuerpo.detalles) ? (cuerpo.detalles as string[]) : undefined;
  const falta = leerParametroQueFalta(cuerpo.parametroQueFalta);
  return new ErrorDeApi(codigo, mensaje, estado, incidencia, detalles, falta);
}

/**
 * El miembro `parametroQueFalta`, leido como el OBJETO que el backend emite.
 *
 * Antes se leia con `typeof === 'string'` y el resultado era que
 * `faltaUnaCifraNormativa` valia `false` siempre: el discriminador que existe
 * para distinguir «falta un campo de la peticion» de «falta publicar una cifra»
 * no llegaba a ninguna pantalla, y las dos se veian igual. No lo delataba nada
 * —ni un error de consola, ni un tipo— porque el camino de la ausencia y el de
 * «no lo entendi» son el mismo `undefined`.
 *
 * Un miembro que no sea un objeto con `ejercicio` numerico se descarta entero:
 * inventarle un ejercicio seria peor que no tenerlo.
 */
function leerParametroQueFalta(valor: unknown): ParametroQueFalta | undefined {
  if (valor === null || typeof valor !== 'object') return undefined;
  const miembro = valor as Record<string, unknown>;
  if (typeof miembro.ejercicio !== 'number') return undefined;
  return typeof miembro.llave === 'string'
    ? { ejercicio: miembro.ejercicio, llave: miembro.llave }
    : { ejercicio: miembro.ejercicio };
}

/**
 * Si el `codigo` del cuerpo es uno de los que esta interfaz conoce.
 *
 * Un codigo que no conozca **no se cuela en la union**: se deduce del estado.
 * Dejarlo pasar es peor de lo que parece, porque el desenlace no se distingue de
 * uno correcto — no casaria con ninguna rama, el aviso saldria con el titulo por
 * omision, y `reintentable` diria que no aunque el estado fuese un 500.
 */
function esCodigoConocido(valor: unknown): valor is CodigoDeError {
  return typeof valor === 'string' && (CODIGOS_DE_ERROR as readonly string[]).includes(valor);
}

function porEstado(estado: number): CodigoDeError {
  if (estado === 401) return 'NO_AUTENTICADO';
  if (estado === 403) return 'SIN_PRIVILEGIO';
  if (estado === 404) return 'NO_ENCONTRADO';
  if (estado === 405) return 'METODO_NO_ADMITIDO';
  if (estado === 409) return 'CONFLICTO';
  if (estado === 422) return 'VALIDACION';
  return 'ERROR_INTERNO';
}

/**
 * Los tres formatos de documento que `FormatoDeDocumento` admite.
 *
 * Se declara la lista y no una union suelta por el mismo motivo que los codigos
 * de error: es lo que se ofrece en pantalla y lo que se valida antes de pedir.
 * El backend contesta 422 «El formato va entre PDF, XLS y RTF» a cualquier otro.
 */
export const FORMATOS_DE_DOCUMENTO = ['PDF', 'XLS', 'RTF'] as const;
export type FormatoDeDocumento = (typeof FORMATOS_DE_DOCUMENTO)[number];

/** Lo que el servidor acabo de entregar. */
export type DocumentoEntregado = { nombre: string; tipoDeMedio: string };

/**
 * Descarga un documento del backend y lo entrega al navegador.
 *
 * `solicitar()` no sirve: parsea JSON y un PDF no cabe por ahi. Va aparte —y no
 * con un `fetch` suelto en la pantalla— para que el token, la raiz de la API y
 * el trato de los errores sigan estando en un solo sitio.
 *
 * **Y no vale un `<a href>`.** El token viaja en una cabecera, asi que un enlace
 * a la misma ruta sale sin `Authorization` y el navegador se baja el 401 con
 * nombre de PDF.
 */
export async function descargar(
  ruta: string,
  parametros: Record<string, ValorDeParametro> = {},
  nombre?: string,
): Promise<DocumentoEntregado> {
  const url = componer(ruta, parametros);
  const cabeceras: Record<string, string> = {};
  const jwt = token();
  if (jwt) cabeceras.Authorization = `Bearer ${jwt}`;

  let respuesta: Response;
  try {
    respuesta = await fetch(url, { method: 'GET', headers: cabeceras });
  } catch {
    throw new ErrorDeApi('SIN_RESPUESTA', 'No se pudo contactar con el servidor', 0);
  }
  if (!respuesta.ok) {
    /* El error SI viene en JSON, asi que se lee con el trato de siempre: un 500
       de aqui tiene que decir lo mismo que un 500 de cualquier lectura. */
    const texto = await respuesta.text();
    throw errorDe(respuesta.status, texto ? intentarLeer(texto) : null);
  }

  /* Un 200 que trae JSON no es un documento, y en esta ruta pasa de verdad: la
     misma URI sirve JSON o binario segun EXISTA el parametro `formato`
     (`params = "formato"` en `ReporteController`). Sin esta guarda el navegador
     se baja un archivo llamado `.pdf` con JSON dentro, que es el peor de los
     tres desenlaces porque parece que funciono y el error aparece al abrirlo. */
  const tipo = respuesta.headers.get('Content-Type') ?? '';
  if (tipo.includes('json')) {
    throw new ErrorDeApi(
      'SIN_RESPUESTA',
      'El servidor devolvio datos y no un documento: esta ruta no emite archivo sin el parametro «formato».',
      respuesta.status,
    );
  }

  const blob = await respuesta.blob();
  const enlace = document.createElement('a');
  const objeto = URL.createObjectURL(blob);
  const archivo = nombre ?? deLaCabecera(respuesta) ?? 'documento';
  enlace.href = objeto;
  enlace.download = archivo;
  document.body.appendChild(enlace);
  enlace.click();
  document.body.removeChild(enlace);
  URL.revokeObjectURL(objeto);
  return { nombre: archivo, tipoDeMedio: tipo };
}

/** El nombre que el propio backend propone en `Content-Disposition`. */
function deLaCabecera(respuesta: Response): string | null {
  const cabecera = respuesta.headers.get('Content-Disposition');
  const encontrado = cabecera?.match(/filename="?([^";]+)"?/);
  return encontrado ? encontrado[1]! : null;
}
