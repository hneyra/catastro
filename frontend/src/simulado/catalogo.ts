/**
 * El catalogo territorial simulado: las cinco escrituras que #72 ofrece.
 *
 * <h2>Esto tambien RECUERDA, y la decision se tomo midiendo, no heredando</h2>
 *
 * La cuarta decision de ADR-0010 dice que este proxy **no persiste**: «un POST
 * responde con el recurso y no guarda nada». `ciclo.ts` (#71) hizo la primera
 * excepcion porque simula una maquina de estados, donde lo ofrecible sale del
 * estado de la fila. Un catalogo **no es una maquina de estados**, asi que la
 * excepcion no se hereda por parecido: se vuelve a decidir.
 *
 * Y la medida dice que aqui hace falta igual, por una razon distinta y mas
 * simple. La hoja Territorio es un **maestro-detalle donde el maestro ES la
 * lista**: el arbol de la izquierda son los sectores leidos de
 * `GET /catastro/sectores`, y la tabla de la derecha son sus manzanas o el
 * catalogo vial. Sin memoria, el par de respuestas que el proxy produce es uno
 * que el backend no puede producir:
 *
 *   · `POST /catastro/sectores` contesta `201` con el sector nuevo, y
 *   · el `GET` siguiente devuelve el arbol **sin el**.
 *
 * Quien acaba de darlo de alta ve la respuesta arriba y el arbol igual que
 * antes, que es exactamente como se ve un alta que no se guardo. Y con la
 * correccion es peor: se cambia el nombre, el servidor contesta con el nombre
 * nuevo, y la lista sigue diciendo el viejo — o sea que la pantalla ensena las
 * dos versiones a la vez y ninguna de las dos es «lo que hay». Con la baja
 * logica, la fila que se acaba de retirar sigue saliendo vigente, que es la
 * afirmacion contraria a la que se acaba de firmar.
 *
 * Medido, con la memoria quitada: `verificaciones/territorio.mjs` da «se dio de
 * alta el sector «91» y la lista siguiente no lo trae», y la lista vuelve con
 * las mismas cuatro filas de siempre.
 *
 * <h2>Lo que la memoria NO hace</h2>
 *
 * **Dura lo que la pagina.** Es estado de modulo, sembrado al importar desde
 * `datos.ts`: una recarga lo devuelve al principio, asi que cada captura de los
 * arneses se puede volver a producir.
 *
 * **No filtra, no ordena, no pagina y no valida.** Sigue sin hacer las cuatro
 * cosas que ADR-0010 prohibe: el unico `422` que contesta es el de la
 * observacion, que es una regla del sistema entero (RNF-052, ADR-0008), y los
 * `409` son los tres que los controladores tienen escritos.
 *
 * <h2>Y reproduce las tres cosas que el backend hace y no se ven</h2>
 *
 *   1. **El `activo` del alta se ignora**: un sector nace activo pase lo que
 *      pase en el cuerpo.
 *   2. **El `codigo` del cuerpo de un `PUT` se ignora**: manda el de la ruta.
 *   3. **Los conteos llegan NULOS en toda respuesta de escritura**, y con cifra
 *      en el listado. Es la diferencia entre `SectorResource.de(Sector)` y
 *      `SectorResource.de(SectorConConteos)`, y por eso las filas se guardan
 *      **sin** sus conteos y el listado se los pone: con el conteo dentro de la
 *      fila, devolver la fila tal cual habria hecho que la respuesta de un alta
 *      trajera un `0` que el backend no manda, y esa es justo la cifra que #72
 *      existe para que la pantalla no pinte.
 *
 * Lo que NO reproduce es el `403 SIN_PRIVILEGIO` de la baja logica: aqui no hay
 * token, ni tabla de accesos, ni quien pregunte. La pantalla lo trata igual que
 * cualquier otro rechazo del servidor, y ese camino lo mide
 * `verificaciones/errores.mjs` inyectando el rechazo de verdad.
 */
import * as D from './datos';
import { creado, faltaLaObservacion, ok, pagina, problema } from './respuestas';
import type { Respuesta } from './respuestas';

type Fila = Record<string, unknown>;

/* ── El estado, sembrado de `datos.ts` y vivo mientras dure la pagina ────── */

/** Un sector como el DOMINIO lo tiene: sin ningun conteo. */
const sectores: Fila[] = D.SECTORES.map((s) => ({
  id: s.id,
  codigo: s.codigo,
  nombre: s.nombre,
  zona: s.zona,
  activo: s.activo,
}));

/** Y sus conteos, que la BASE calcula sobre las filas que cuelgan del sector. */
const prediosDelSector = new Map<string, { predios: number; lotes: number }>(
  D.SECTORES.map((s) => [s.codigo, { predios: s.predios, lotes: s.lotes }]),
);

const manzanas: Fila[] = D.MANZANAS.map((m) => ({
  id: m.id,
  sectorId: m.sectorId,
  sectorCodigo: m.sectorCodigo,
  codigo: m.codigo,
}));

const prediosDeLaManzana = new Map<string, { predios: number; lotes: number }>(
  D.MANZANAS.map((m) => [`${m.sectorCodigo}/${m.codigo}`, { predios: m.predios, lotes: m.lotes }]),
);

const vias: Fila[] = D.VIAS.map((v) => ({ ...v }));

/** El identificador que seguiria al ultimo. Lo unico que se inventa. */
function siguienteId(filas: readonly Fila[]): number {
  return filas.reduce((mayor, f) => Math.max(mayor, Number(f.id)), 0) + 1;
}

function texto(cuerpo: unknown, campo: string): string {
  const campos = (cuerpo ?? {}) as Record<string, unknown>;
  const valor = campos[campo];
  return typeof valor === 'string' ? valor.trim() : '';
}

/** Lo que el cuerpo trae, o `undefined` si no lo trae: «lo que no viene, no cambia». */
function siViene(cuerpo: unknown, campo: string): unknown {
  const campos = (cuerpo ?? {}) as Record<string, unknown>;
  return campos[campo];
}

/* ── Los sectores ───────────────────────────────────────────────────────── */

/** `SectorResource.de(SectorConConteos)`: el sector del LISTADO, con sus tres cifras. */
function conConteos(sector: Fila): Fila {
  const codigo = String(sector.codigo);
  const suyos = prediosDelSector.get(codigo) ?? { predios: 0, lotes: 0 };
  return {
    ...sector,
    manzanas: manzanas.filter((m) => m.sectorCodigo === codigo).length,
    predios: suyos.predios,
    lotes: suyos.lotes,
  };
}

/** `SectorResource.de(Sector)`: el sector solo, que es lo que devuelve la escritura. */
function sinContarNada(sector: Fila): Fila {
  return { ...sector, manzanas: null, predios: null, lotes: null };
}

export function listarSectores(): Respuesta {
  return pagina(sectores.map(conConteos));
}

export function registrarSector(cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const codigo = texto(cuerpo, 'codigo');
  if (sectores.some((s) => s.codigo === codigo)) {
    return problema(
      'CONFLICTO',
      409,
      `Ya existe un sector con el codigo '${codigo}' en esta municipalidad`,
    );
  }
  const nuevo: Fila = {
    id: siguienteId(sectores),
    codigo,
    nombre: texto(cuerpo, 'nombre'),
    zona: texto(cuerpo, 'zona') === '' ? null : texto(cuerpo, 'zona'),
    /* Nace activo, y el `activo` del cuerpo NO se lee: darlo de alta ya retirado
       seria un alta y una baja en un solo acto. */
    activo: true,
  };
  sectores.push(nuevo);
  prediosDelSector.set(codigo, { predios: 0, lotes: 0 });
  return creado(sinContarNada(nuevo));
}

export function modificarSector(codigo: string, cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const sector = sectores.find((s) => s.codigo === codigo);
  if (!sector) return sectorInexistente(codigo);

  /* El `codigo` del cuerpo se ignora: manda el de la ruta. */
  const nombre = siViene(cuerpo, 'nombre');
  const zona = siViene(cuerpo, 'zona');
  const activo = siViene(cuerpo, 'activo');
  if (typeof nombre === 'string' && nombre.trim() !== '') sector.nombre = nombre.trim();
  if (typeof zona === 'string') sector.zona = zona.trim() === '' ? null : zona.trim();
  if (typeof activo === 'boolean') sector.activo = activo;
  return ok(sinContarNada(sector));
}

function sectorInexistente(codigo: string): Respuesta {
  return problema(
    'NO_ENCONTRADO',
    404,
    `No hay ningun sector con codigo '${codigo}' en esta municipalidad`,
  );
}

/* ── Las manzanas ───────────────────────────────────────────────────────── */

function manzanaConConteos(manzana: Fila): Fila {
  const suyos = prediosDeLaManzana.get(`${String(manzana.sectorCodigo)}/${String(manzana.codigo)}`) ?? {
    predios: 0,
    lotes: 0,
  };
  return { ...manzana, predios: suyos.predios, lotes: suyos.lotes };
}

export function listarManzanas(codigoDeSector: string): Respuesta {
  /* Un sector que no existe es 404 y no una pagina vacia: las dos se parecen
     mucho —cero filas— y significan lo contrario. */
  if (!sectores.some((s) => s.codigo === codigoDeSector)) return sectorInexistente(codigoDeSector);
  return pagina(manzanas.filter((m) => m.sectorCodigo === codigoDeSector).map(manzanaConConteos));
}

export function registrarManzana(codigoDeSector: string, cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const sector = sectores.find((s) => s.codigo === codigoDeSector);
  if (!sector) return sectorInexistente(codigoDeSector);

  const codigo = texto(cuerpo, 'codigo');
  if (manzanas.some((m) => m.sectorCodigo === codigoDeSector && m.codigo === codigo)) {
    return problema(
      'CONFLICTO',
      409,
      `Ya existe una manzana con el codigo '${codigo}' en el sector '${codigoDeSector}'`,
    );
  }
  const nueva: Fila = {
    id: siguienteId(manzanas),
    sectorId: sector.id,
    sectorCodigo: codigoDeSector,
    codigo,
  };
  manzanas.push(nueva);
  prediosDeLaManzana.set(`${codigoDeSector}/${codigo}`, { predios: 0, lotes: 0 });
  /* Los dos conteos llegan NULOS en el alta, igual que los tres del sector. */
  return creado({ ...nueva, predios: null, lotes: null });
}

/* ── Las vias ───────────────────────────────────────────────────────────── */

export function listarVias(): Respuesta {
  return pagina(vias.map((v) => ({ ...v })));
}

export function registrarVia(cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const codigo = texto(cuerpo, 'codigo');
  if (vias.some((v) => v.codigo === codigo)) {
    return problema('CONFLICTO', 409, `Ya existe una via con el codigo '${codigo}' en esta municipalidad`);
  }
  const ubigeo = texto(cuerpo, 'ubigeo');
  const nueva: Fila = {
    id: siguienteId(vias),
    codigo,
    tipo: texto(cuerpo, 'tipo'),
    nombre: texto(cuerpo, 'nombre'),
    ubigeo: ubigeo === '' ? null : ubigeo,
    /* `Via.nueva` la construye activa, y el `activa` del cuerpo no se lee. */
    activa: true,
  };
  vias.push(nueva);
  return creado({ ...nueva });
}

export function modificarVia(codigo: string, cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const via = vias.find((v) => v.codigo === codigo);
  if (!via) {
    return problema('NO_ENCONTRADO', 404, `No hay ninguna via con codigo '${codigo}' en esta municipalidad`);
  }
  const tipo = siViene(cuerpo, 'tipo');
  const nombre = siViene(cuerpo, 'nombre');
  const ubigeo = siViene(cuerpo, 'ubigeo');
  const activa = siViene(cuerpo, 'activa');
  if (typeof tipo === 'string' && tipo.trim() !== '') via.tipo = tipo.trim();
  if (typeof nombre === 'string' && nombre.trim() !== '') via.nombre = nombre.trim();
  if (typeof ubigeo === 'string') via.ubigeo = ubigeo.trim() === '' ? null : ubigeo.trim();
  if (typeof activa === 'boolean') via.activa = activa;
  return ok({ ...via });
}
