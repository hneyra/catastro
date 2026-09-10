/**
 * La forma de las respuestas que el proxy de datos devuelve.
 *
 * Vive aparte de `proxy.ts` desde #71, y por un motivo concreto: el ciclo de
 * fiscalizacion (`ciclo.ts`) compone las mismas tres formas —el sobre de un
 * listado, el cuerpo `problem+json` de un rechazo y el `200` pelado— y con
 * `problema()` escrito dos veces habria **dos sobres de error** que podrian
 * separarse sin que nada lo dijera. Es la forma de defecto que C-17 encontro
 * cinco veces, y aqui costaria caro: `cliente.ts` decide el codigo, el titulo y
 * si se ofrece «Reintentar» leyendo justo estos campos.
 *
 * No nombra `fetch` ni instala nada: son funciones puras que componen objetos.
 */

/** Lo que un manejador del proxy contesta: el estado HTTP y el cuerpo. */
export type Respuesta = { estado: number; cuerpo: unknown };

/**
 * Un rechazo, con la forma COMPLETA de `ManejadorDeErrores`.
 *
 * Con `type` y `detail` porque estos rechazos vienen de un controlador y no de
 * un filtro. Los 401 y 403 de los filtros salen mas cortos —`RespuestaDeError`
 * escribe cuatro campos a mano—, y quien los lee no puede confiar en esos dos
 * campos: eso lo sujeta `cliente.ts`.
 */
export function problema(
  codigo: string,
  estado: number,
  mensaje: string,
  parametroQueFalta?: { ejercicio: number; llave?: string },
): Respuesta {
  return {
    estado,
    cuerpo: {
      type: `https://kamayuk.gob.pe/errores/${codigo.toLowerCase()}`,
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
export function pagina<T>(contenido: readonly T[]): Respuesta {
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

export const ok = (cuerpo: unknown): Respuesta => ({ estado: 200, cuerpo });

/** Lo recien creado. Es `201` y no `200`: seis operaciones de este backend lo son. */
export const creado = (cuerpo: unknown): Respuesta => ({ estado: 201, cuerpo });

/**
 * El `422` de la observacion que falta, que es la unica validacion que este
 * proxy reproduce.
 *
 * No depende de ningun dato: es una regla del sistema entero (RNF-052,
 * ADR-0008), y el mensaje es el que `FiscalizacionCatastralController` y
 * `DeclaracionDeFicha` emiten. Las demas validaciones —el largo de un codigo, el
 * rango de un umbral, la forma de una huella— **no se reproducen**: el proxy no
 * valida (ADR-0010, decision 4), y fingirlas seria construir la interfaz contra
 * una semantica que el backend no ha decidido aqui.
 */
export function faltaLaObservacion(cuerpo: unknown): Respuesta | null {
  const campos = (cuerpo ?? {}) as Record<string, unknown>;
  const observacion = typeof campos.observacion === 'string' ? campos.observacion.trim() : '';
  if (observacion !== '') return null;
  return problema(
    'VALIDACION',
    422,
    'Falta la observacion: toda escritura la exige, y sin ella no se guarda (regla 10, ADR-0008)',
  );
}
