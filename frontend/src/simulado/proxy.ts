/**
 * Proxy de datos: la API de `catastro`, simulada en el navegador.
 *
 * Hereda enteras las cuatro decisiones de ADR-0010 de `sgtm`.
 *
 * <h2>1. Intercepta en la frontera del TRANSPORTE, no en la de la aplicacion</h2>
 *
 * Sustituye `fetch` y devuelve `Response` de verdad, con su codigo de estado y
 * su `application/problem+json`. **No es un adaptador que la aplicacion elija.**
 * La salida facil habria sido que cada pantalla leyera sus datos de una
 * constante importada; la trampa de esa salida es que el dia que el backend
 * exista habria que reescribir las pantallas para que pidan por HTTP. Asi no:
 * la pantalla llama a `solicitar()` con la ruta real —`GET
 * /catastro/api/v1/catastro/predios`— y todo el camino se ejerce; la URL se
 * compone, los parametros viajan, el token se adjunta, el error se convierte en
 * `ErrorDeApi`.
 *
 * <h2>2. Se apaga con una bandera, y la rama entera desaparece del paquete</h2>
 *
 * `VITE_CATASTRO_PROXY_DE_DATOS=false`. Se carga con `import()` dinamico desde
 * `main.tsx`, asi que apagado no viaja: Vite lo deja en un trozo aparte que
 * nadie pide. Con latencia simulada, para que los estados de carga se vean en
 * desarrollo.
 *
 * <h2>3. Se apaga tambien operacion por operacion</h2>
 *
 * `servidas.ts` exporta `YA_SERVIDAS`, y lo que este ahi pasa al backend. Nace
 * vacia y crece hasta cubrir las 64.
 *
 * <h2>4. No finge lo que no sabe</h2>
 *
 * **No filtra, no ordena, no pagina, no valida y no persiste.** Un proxy que
 * fingiera la semantica de `?uso=Comercio` estaria inventando un comportamiento
 * que el backend todavia no ha decidido, y la interfaz acabaria construida
 * contra esa invencion. Filtrar es del servidor: aqui la peticion se hace de
 * verdad y la respuesta es siempre el juego de datos completo. Lo mismo con las
 * escrituras: un `POST` responde con el recurso y no guarda nada.
 *
 * Lo unico que el sujeto decide es **cual de las respuestas que el backend YA
 * TIENE ESCRITAS toca** —la zona o el 422 por predio sin poligono—, que es
 * reproducir una bifurcacion existente y no inventar una tercera.
 *
 * <h2>Por que no MSW</h2>
 *
 * Hace lo mismo y mejor, con un *service worker*. Descartada por una dependencia
 * mas para el encaminamiento de este archivo, cuyo unico trabajo es desaparecer.
 */
import { RAIZ } from '../api/cliente';
import { YA_SERVIDAS, laSirveElBackend } from './servidas';
import type { OperacionServida } from './servidas';
import * as D from './datos';

/** Latencia simulada, para que los estados de carga se vean en desarrollo. */
const LATENCIA_MINIMA_MS = 120;
const LATENCIA_MAXIMA_MS = 320;

type Parametros = Record<string, string>;
type Contexto = { parametros: Parametros; consulta: URLSearchParams };
type Respuesta = { estado: number; cuerpo: unknown };
type Manejador = (contexto: Contexto) => Respuesta;

const esperar = (ms: number) => new Promise((listo) => setTimeout(listo, ms));

function problema(
  codigo: string,
  estado: number,
  mensaje: string,
  parametroQueFalta?: { ejercicio: number; llave?: string },
): Respuesta {
  /* Con la forma COMPLETA del `ManejadorDeErrores` —con `type` y `detail`—
     porque estos rechazos vienen de un controlador y no de un filtro. Los 401 y
     403 de los filtros salen mas cortos, y quien los lee no puede confiar en
     esos dos campos: eso lo sujeta `cliente.ts`. */
  return {
    estado,
    cuerpo: {
      type: `https://sgtm.gob.pe/errores/${codigo.toLowerCase()}`,
      title: mensaje,
      status: estado,
      detail: mensaje,
      codigo,
      mensaje,
      /* `ManejadorDeErrores` lo pone con `setProperty` **solo cuando el problema
         lo trae**, y por eso significa algo: un rechazo con este miembro no se
         arregla desde la pantalla —hay que sellar el conjunto o publicar la
         fila—. Se omite cuando no lo hay, en vez de mandarlo nulo. */
      ...(parametroQueFalta === undefined ? {} : { parametroQueFalta }),
    },
  };
}

/** El sobre de un listado, sin paginar: se devuelve todo lo que hay. */
function pagina<T>(contenido: readonly T[]): Respuesta {
  return {
    estado: 200,
    cuerpo: {
      contenido,
      pagina: 0,
      tamano: contenido.length,
      totalElementos: contenido.length,
      totalPaginas: contenido.length === 0 ? 0 : 1,
      hayMas: false,
    },
  };
}

const ok = (cuerpo: unknown): Respuesta => ({ estado: 200, cuerpo });

function predioDe(contexto: Contexto, clave = 'predioId'): D.FilaDelPadron | undefined {
  const id = Number(contexto.parametros[clave] ?? contexto.consulta.get(clave) ?? '');
  return D.PADRON.find((p) => p.predioId === id);
}

/**
 * La tabla de operaciones.
 *
 * Son las que el armazon lee. Las 64 del contrato no estan todas: lo que falta
 * no se simula «por si acaso», porque una respuesta simulada que nadie ha
 * mirado es indistinguible de una que nadie ha escrito.
 */
const TABLA: readonly { metodo: string; ruta: string; responder: Manejador }[] = [
  /* ── Padron y fichas ────────────────────────────────────────────────── */
  { metodo: 'GET', ruta: '/catastro/predios', responder: () => pagina(D.PADRON) },
  {
    metodo: 'GET',
    ruta: '/catastro/predios/{predioId}',
    responder: (c) => {
      const p = predioDe(c);
      return p
        ? ok({ predioId: p.predioId, enElPadron: true })
        : problema('NO_ENCONTRADO', 404, 'No hay ningun predio con ese identificador');
    },
  },
  {
    metodo: 'GET',
    ruta: '/catastro/predios/{predioId}/frentes',
    responder: (c) => {
      const p = predioDe(c);
      if (!p) return problema('NO_ENCONTRADO', 404, 'No hay ningun predio con ese identificador');
      /* Los dos desenlaces de #7: el predio con lote levantado propone frentes;
         el resto —o sea, todos los de una instalacion de verdad— salen con el
         MOTIVO por el que no se propuso ninguno. */
      const tiene = p.predioId === D.PREDIO_CON_POLIGONO;
      return ok({
        predioId: p.predioId,
        frentes: tiene ? D.FRENTES : [],
        derivadoEn: '2026-09-05T03:14:00Z',
        frentesDerivados: tiene ? D.FRENTES.length : 0,
        motivoDeLaDerivacion: tiene ? null : D.SIN_FRENTES,
      });
    },
  },
  {
    metodo: 'GET',
    ruta: '/catastro/predios/plano',
    responder: () =>
      /* Ni un lote con geometria, que es el estado real de toda instalacion. La
         pantalla ensena `sinGeometria` en vez de un lienzo vacio. */
      ok({ lotes: [], sinGeometria: D.PADRON.length }),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/predios/plano/marco',
    responder: () =>
      ok({
        marco: null,
        lotes: 0,
        notaDelMarco: 'Ningun predio de este ambito tiene poligono levantado, asi que no hay marco que componer.',
      }),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/fichas',
    responder: () =>
      pagina(
        D.PADRON.map((p) => ({
          fichaId: p.predioId,
          predioId: p.predioId,
          codRefCatastral: p.codRefCatastral,
          direccion: p.direccion,
          manzana: p.codigoDeManzana,
          lote: p.lote,
          tipo: p.tipoFicha,
          version: 1,
          areaTerreno: p.areaTerreno,
          areaConstruida: null,
          uso: p.uso,
          vigenciaDesde: p.vigenciaDesde,
          titular: D.contribuyenteDe(p.contribuyente).nombre,
        })),
      ),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/fichas/urbana/{codRefCatastral}',
    responder: (c) => {
      const codigo = c.parametros.codRefCatastral;
      const p = D.PADRON.find((x) => x.codRefCatastral === codigo);
      if (!p) return problema('NO_ENCONTRADO', 404, 'No hay ninguna ficha urbana con ese codigo');
      return ok({
        id: p.predioId,
        predioId: p.predioId,
        tipo: p.tipoFicha,
        version: 1,
        areaTerreno: p.areaTerreno,
        uso: p.uso,
        frontis: null,
        condicionPropiedad: p.condicion,
        tipoEdificacion: null,
        vigenciaDesde: p.vigenciaDesde,
        vigenciaHasta: null,
        vigente: true,
        origen: p.origen,
        documentoOrigen: p.documentoOrigen,
        observacion: 'Carga inicial del padron de demostracion',
        denominacion: p.denominacion,
        construcciones: [],
      });
    },
  },

  /* ── Territorio ─────────────────────────────────────────────────────── */
  { metodo: 'GET', ruta: '/catastro/sectores', responder: () => pagina(D.SECTORES) },
  {
    metodo: 'GET',
    ruta: '/catastro/sectores/{codigo}/manzanas',
    responder: (c) => pagina(D.MANZANAS.filter((m) => m.sectorCodigo === c.parametros.codigo)),
  },
  { metodo: 'GET', ruta: '/catastro/vias', responder: () => pagina(D.VIAS) },

  /* ── Cuadros del ejercicio ──────────────────────────────────────────── */
  {
    metodo: 'GET',
    ruta: '/catastro/tablas/aranceles',
    responder: (c) => cuadroDe(c, D.ARANCELES),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/tablas/valores-unitarios',
    responder: (c) => cuadroDe(c, D.VALORES_UNITARIOS),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/tablas/depreciacion',
    responder: (c) => cuadroDe(c, D.DEPRECIACION),
  },
  {
    metodo: 'GET',
    ruta: '/seguridad/parametros/ejercicios/{ejercicio}',
    responder: (c) => {
      const anio = Number(c.parametros.ejercicio);
      /* Un ejercicio sin sellar contesta **200 con `sellado: false`**, no 404:
         medido en `EjercicioParametrizadoController`. Es una respuesta, no un
         fallo, y la pantalla la dibuja como tal. */
      return anio === D.EJERCICIO
        ? ok(D.EJERCICIO_SELLADO)
        : ok({ ejercicio: anio, sellado: false, conjuntoId: null, version: null });
    },
  },

  /* ── Urbano y GRD: los dos caminos ──────────────────────────────────── */
  {
    metodo: 'GET',
    ruta: '/urbano/zonificacion',
    responder: (c) => sinPoligono(c) ?? ok({ ...D.ZONA }),
  },
  {
    metodo: 'GET',
    ruta: '/grd/riesgo',
    responder: (c) => {
      const p = predioDe(c);
      return sinPoligono(c) ?? ok({ predioId: p!.predioId, ...D.RIESGO });
    },
  },
  {
    metodo: 'GET',
    ruta: '/grd/itse',
    responder: (c) => {
      const p = predioDe(c);
      if (!p) return problema('NO_ENCONTRADO', 404, 'No hay ningun predio con ese identificador');
      return ok({ predioId: p.predioId, ...D.ITSE });
    },
  },

  /* ── Fiscalizacion ──────────────────────────────────────────────────── */
  {
    metodo: 'GET',
    ruta: '/fiscalizacion/campanias/{campaniaId}/candidatos',
    responder: (c) => enLaCampania(c, D.CANDIDATOS),
  },
  {
    metodo: 'GET',
    ruta: '/fiscalizacion/campanias/{campaniaId}/tasa-de-descarte',
    responder: (c) =>
      Number(c.parametros.campaniaId) === D.CAMPANIA.id
        ? ok(D.TASA_DE_DESCARTE)
        : problema('NO_ENCONTRADO', 404, 'No hay ninguna campania con ese identificador'),
  },
  {
    metodo: 'GET',
    ruta: '/fiscalizacion/campanias/{campaniaId}/hallazgos',
    responder: (c) => enLaCampania(c, D.HALLAZGOS),
  },
  {
    metodo: 'GET',
    ruta: '/fiscalizacion/hallazgos/{hallazgoId}/evidencias',
    responder: (c) => {
      const id = Number(c.parametros.hallazgoId);
      if (!D.HALLAZGOS.some((h) => h.id === id)) {
        return problema('NO_ENCONTRADO', 404, 'No hay ningun hallazgo con ese identificador');
      }
      return ok(D.EVIDENCIAS.filter((e) => e.hallazgoId === id));
    },
  },

  /* ── Ventanilla ─────────────────────────────────────────────────────── */
  {
    metodo: 'GET',
    ruta: '/consultas/resumen-predial',
    responder: () =>
      pagina(
        D.PADRON.map((p) => ({
          fichaId: p.predioId,
          predioId: p.predioId,
          codCatastral: p.codRefCatastral,
          codPropietario: p.contribuyente,
          nombreDelPropietario: D.contribuyenteDe(p.contribuyente).nombre,
          direccionDelPredio: p.direccion,
          uso: p.uso,
          tipo: p.tipoFicha,
          version: 1,
          vigenciaDesde: p.vigenciaDesde,
        })),
      ),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/contribuyentes/{codigo}/ficha.pdf',
    responder: (c) => {
      const codigo = c.parametros.codigo;
      const suyos = D.PADRON.filter((p) => p.contribuyente === codigo);
      if (suyos.length === 0) return problema('NO_ENCONTRADO', 404, 'No hay ningun contribuyente con ese codigo');
      const quien = D.contribuyenteDe(codigo);
      return ok({
        aLaFecha: '2026-09-06',
        codigo,
        nombre: quien.nombre,
        documento: quien.documento,
        domicilioFiscal: suyos[0]!.direccion,
        unidades: suyos.map((p) => ({
          codRefCatastral: p.codRefCatastral,
          direccion: p.direccion,
          condicion: p.condicion,
          porcentaje: '100.0000',
          areaTerreno: p.areaTerreno,
          uso: p.uso,
          version: 1,
        })),
      });
    },
  },
];

/**
 * Un cuadro del ejercicio: solo el sellado lo tiene; los demas, 404.
 *
 * **Con su `parametroQueFalta`, y sin `llave`.** Es lo que el backend emite,
 * medido: los tres controladores solo atrapan
 * `LectorDeParametros.EjercicioSinSellar`, cuyo `llave()` es `Optional.empty()`,
 * asi que estas tres rutas nunca nombran una fila del corpus — lo que falta es
 * el conjunto entero del ano. Omitir el miembro aqui dejaria a la pantalla sin
 * el unico discriminador que separa «falta un campo de la peticion», que quien
 * atiende arregla, de «falta publicar», que no arregla nadie desde la pantalla.
 */
function cuadroDe(contexto: Contexto, filas: readonly unknown[]): Respuesta {
  const ejercicio = Number(contexto.consulta.get('ejercicio') ?? '');
  if (ejercicio !== D.EJERCICIO) {
    return problema(
      'NO_ENCONTRADO',
      404,
      `El ejercicio ${ejercicio} no tiene un conjunto de parametros sellado. Calcular con uno abierto` +
        ' produciria una cifra que manana puede ser otra, y el contribuyente ya tendria el recibo (ADR-0007)',
      { ejercicio },
    );
  }
  return ok(filas);
}

/** Lo de una campania, o el 404 si no es la que hay. */
function enLaCampania(contexto: Contexto, filas: readonly unknown[]): Respuesta {
  const id = Number(contexto.parametros.campaniaId);
  if (id !== D.CAMPANIA.id) return problema('NO_ENCONTRADO', 404, 'No hay ninguna campania con ese identificador');
  return pagina(filas);
}

/**
 * El 422 por predio sin poligono, que es el desenlace que ocurre de verdad.
 *
 * Devuelve `null` cuando el predio SI tiene lote levantado y hay que seguir. Y
 * distingue los dos rechazos: un predio que no existe es 404, y un predio que
 * existe sin poligono es **422 y no 404**, a proposito — el backend lo escribe
 * asi porque las dos cosas se arreglan de manera distinta.
 */
function sinPoligono(contexto: Contexto): Respuesta | null {
  const p = predioDe(contexto);
  if (!p) return problema('NO_ENCONTRADO', 404, 'No hay ningun predio con ese identificador');
  if (p.predioId === D.PREDIO_CON_POLIGONO) return null;
  return problema(
    'VALIDACION',
    422,
    'El predio existe y no tiene poligono levantado: sin geometria no se puede decir a que zona cae',
  );
}

function compilar(ruta: string): { patron: RegExp; nombres: string[] } {
  const nombres: string[] = [];
  const escapado = ruta
    .split(/(\{\w+\})/)
    .map((trozo) => {
      const llave = trozo.match(/^\{(\w+)\}$/);
      if (llave) {
        nombres.push(llave[1]!);
        return '([^/]+)';
      }
      return trozo.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    })
    .join('');
  return { patron: new RegExp(`^${RAIZ.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}${escapado}$`), nombres };
}

/** Cuantos `{parametro}` tiene una ruta del contrato. */
function cuantosParametros(ruta: string): number {
  return (ruta.match(/\{\w+\}/g) ?? []).length;
}

/**
 * La tabla, ordenada de lo LITERAL a lo parametrizado.
 *
 * <h2>Un defecto que se encontro midiendo, y cuyo sintoma era una respuesta
 * plausible</h2>
 *
 * El encaminamiento recorria la tabla en el orden en que esta escrita, y
 * `/catastro/predios/{predioId}` esta ANTES que `/catastro/predios/plano`. Asi
 * que `GET /catastro/predios/plano` casaba con el primero, `Number('plano')`
 * daba `NaN`, y el proxy contestaba **404 «No hay ningun predio con ese
 * identificador»** a la lectura del plano catastral.
 *
 * Lo caro no es el 404: es que en ESA pantalla un 404 se lee como una respuesta
 * correcta —«aqui no hay lotes» y «no existe» se parecen mucho cuando lo que se
 * espera es un plano vacio—, asi que la pantalla llevaba desde #32 ensenando el
 * error de otra ruta y nadie tenia como notarlo. `/catastro/predios/plano/marco`
 * se salvaba de milagro: tiene un segmento mas y ningun patron lo tapa.
 *
 * Se ordena por numero de parametros y **se comprueba que el orden funciona**:
 * toda ruta sin parametros tiene que casar consigo misma y no con otra. Un
 * arreglo por reordenacion se deshace solo en cuanto alguien anade una entrada
 * al final, y esta comprobacion lo dice al importar el modulo, no en produccion.
 */
const COMPILADAS = TABLA.map((entrada) => ({ ...entrada, ...compilar(entrada.ruta) })).sort(
  (a, b) => cuantosParametros(a.ruta) - cuantosParametros(b.ruta),
);

for (const entrada of COMPILADAS) {
  if (cuantosParametros(entrada.ruta) > 0) continue;
  const camino = RAIZ + entrada.ruta;
  const primera = COMPILADAS.find((otra) => otra.metodo === entrada.metodo && otra.patron.test(camino));
  if (primera !== entrada) {
    throw new Error(
      `El proxy encamina «${entrada.metodo} ${entrada.ruta}» a «${primera?.ruta}»: una ruta literal la` +
        ' esta tapando un patron con parametro. Ordenar por numero de parametros no basta para este par,' +
        ' y contestar la respuesta de otra ruta es un fallo que la pantalla lee como si fuera suyo.',
    );
  }
}

/** Cuantas operaciones responde el proxy. */
export const OPERACIONES_SIMULADAS = COMPILADAS.length;

/** Las rutas que simula, para quien quiera contrastarlas con el backend. */
export const RUTAS_SIMULADAS: readonly { metodo: string; ruta: string }[] = TABLA.map((t) => ({
  metodo: t.metodo,
  ruta: t.ruta,
}));

function json(cuerpo: unknown, estado: number): Response {
  return new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: {
      'content-type': estado >= 400 ? 'application/problem+json' : 'application/json',
    },
  });
}

function noLaSirve(metodo: string, camino: string, estado: number): Response {
  return json(
    {
      type: 'https://sgtm.gob.pe/errores/operacion-declarada-y-no-servida',
      title: 'La operacion esta declarada como servida y el backend no la sirve',
      status: 502,
      detail: `«${metodo} ${camino}» esta en «src/simulado/servidas.ts» y el backend respondio ${estado}. Quita la ruta de esa lista o implementa la operacion: caer al proxy en silencio esconderia el desajuste.`,
      codigo: 'ERROR_INTERNO',
      mensaje: `«${metodo} ${camino}» esta declarada como servida y el backend respondio ${estado}.`,
    },
    502,
  );
}

function noSimulada(metodo: string, camino: string): Response {
  return json(
    {
      type: 'https://sgtm.gob.pe/errores/operacion-no-simulada',
      title: 'La operacion no existe en el proxy de datos',
      status: 404,
      detail: `El proxy de datos no conoce «${metodo} ${camino}». El backend publica 64 operaciones y este proxy simula ${OPERACIONES_SIMULADAS}: las que el armazon lee. Anadela a «src/simulado/proxy.ts» o enciendela en «servidas.ts».`,
      codigo: 'NO_ENCONTRADO',
      mensaje: `El proxy de datos no conoce «${metodo} ${camino}».`,
    },
    404,
  );
}

let original: typeof fetch | null = null;

export type OpcionesDelProxy = {
  /** Latencia simulada. Encendida en desarrollo; se apaga para medir. */
  readonly latencia?: boolean;
  readonly yaServidas?: readonly OperacionServida[];
};

/**
 * Sustituye `fetch` por el proxy. Devuelve la funcion que lo desinstala.
 *
 * Solo intercepta lo que cuelga de la raiz de la API; cualquier otra peticion
 * —una fuente tipografica, un recurso— sigue su camino sin tocarse.
 */
export function instalarProxyDeDatos({
  latencia = true,
  yaServidas = YA_SERVIDAS,
}: OpcionesDelProxy = {}): () => void {
  if (original) return desinstalarProxyDeDatos;
  original = globalThis.fetch;
  /* Para delegar hace falta ligarlo; para restaurar, no: devolver el envoltorio
     ligado en vez de la funcion original dejaria una capa pegada en cada ciclo. */
  const anterior = original.bind(globalThis);

  globalThis.fetch = async (entrada: RequestInfo | URL, opciones?: RequestInit): Promise<Response> => {
    const href =
      typeof entrada === 'string' ? entrada : entrada instanceof URL ? entrada.href : entrada.url;
    const url = new URL(href, globalThis.location?.origin ?? 'http://localhost');
    if (!url.pathname.startsWith(RAIZ)) return anterior(entrada, opciones);

    const metodo = (
      opciones?.method ??
      (typeof entrada === 'object' && 'method' in entrada ? entrada.method : 'GET')
    ).toUpperCase();

    if (laSirveElBackend(yaServidas, RAIZ, metodo, url.pathname)) {
      const respuesta = await anterior(entrada, opciones);
      return respuesta.status === 404 || respuesta.status === 501
        ? noLaSirve(metodo, url.pathname, respuesta.status)
        : respuesta;
    }

    if (latencia) {
      await esperar(LATENCIA_MINIMA_MS + Math.random() * (LATENCIA_MAXIMA_MS - LATENCIA_MINIMA_MS));
    }

    for (const entradaDeLaTabla of COMPILADAS) {
      if (entradaDeLaTabla.metodo !== metodo) continue;
      const casa = entradaDeLaTabla.patron.exec(url.pathname);
      if (!casa) continue;
      const parametros: Parametros = {};
      entradaDeLaTabla.nombres.forEach((nombre, i) => {
        parametros[nombre] = decodeURIComponent(casa[i + 1]!);
      });
      const { estado, cuerpo } = entradaDeLaTabla.responder({ parametros, consulta: url.searchParams });
      return json(cuerpo, estado);
    }

    return noSimulada(metodo, url.pathname);
  };

  return desinstalarProxyDeDatos;
}

export function desinstalarProxyDeDatos(): void {
  if (!original) return;
  globalThis.fetch = original;
  original = null;
}

export function proxyDeDatosInstalado(): boolean {
  return original !== null;
}
