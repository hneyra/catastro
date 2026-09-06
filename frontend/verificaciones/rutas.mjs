/**
 * Lo que esta interfaz dice del backend sigue siendo verdad.
 *
 *   node verificaciones/rutas.mjs
 *
 * Es el equivalente del arnes `prosa` del precedente, con una diferencia que
 * decide su forma: **este repositorio no tiene OpenAPI** —no hay springdoc ni un
 * `.yaml` de contrato, es el hueco 5 de C-5—, asi que no hay documento que leer.
 * Lo que hay es el `backend/` de este mismo clon, y de ahi se lee.
 *
 * Comprueba dos cosas, y las dos fallan por separado:
 *
 *   1. **Toda ruta nombrada en `src/api/*.ts` existe como `@RequestMapping` en
 *      `backend/`.** Una ruta inventada no da error en ningun sitio: compila,
 *      arranca, y solo se ve al abrir la pantalla contra el backend de verdad —y
 *      ahi sale como un 404 que parece un problema de despliegue—.
 *   2. **Todo `acceso` de `modulos.ts` existe como `@RequiereAcceso`.** Y ademas
 *      **nombra los que ningun `CatalogoDelSistema` declara**, en vez de
 *      callarlos: una lista escrita a mano que nadie contrasta es el segundo
 *      sitio donde el catalogo puede estar mal.
 *
 * No abre navegador y no necesita backend levantado: lee fuentes.
 */
import { readFile, readdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { leerRegistro } from './registro.mjs';

const RAIZ_DEL_FRONTEND = new URL('../', import.meta.url);
const BACKEND = new URL('../../backend/', import.meta.url).pathname;

if (!existsSync(BACKEND)) {
  console.error(
    `No se encontro «${BACKEND}».\n` +
      'Esta comprobacion lee el backend de ESTE clon: sin el no puede medir nada, y pasar en\n' +
      'verde sin haber comparado seria peor que no existir.',
  );
  process.exit(2);
}

/* ── Lo que declara el frontend ─────────────────────────────────────────── */

/** Los literales de ruta de `src/api/*.ts`: toda cadena que empieza por `/`. */
async function rutasDeclaradas() {
  const dir = new URL('src/api/', RAIZ_DEL_FRONTEND).pathname;
  const archivos = (await readdir(dir)).filter((f) => f.endsWith('.ts'));
  const encontradas = [];
  for (const archivo of archivos) {
    const texto = await readFile(join(dir, archivo), 'utf8');
    for (const casa of texto.matchAll(/'(\/[A-Za-z0-9_\-./{}]*)'/g)) {
      /* La raiz misma no es una ruta del contrato: es el prefijo. */
      if (casa[1] === '/catastro/api/v1') continue;
      encontradas.push({ ruta: casa[1], archivo });
    }
  }
  return encontradas;
}

/** Los accesos que `modulos.ts` declara. Se compila al vuelo: no se copia. */
async function accesosDeclarados() {
  const { ACCESOS, MODULOS } = await leerRegistro('.registro-rutas');
  return { ACCESOS, MODULOS };
}

/* ── Lo que declara el backend ──────────────────────────────────────────── */

async function javaDelBackend() {
  const archivos = [];
  async function recorrer(dir) {
    for (const entrada of await readdir(dir, { withFileTypes: true })) {
      const camino = join(dir, entrada.name);
      if (entrada.isDirectory()) {
        if (entrada.name === 'build' || entrada.name === 'test') continue;
        await recorrer(camino);
      } else if (entrada.name.endsWith('.java') && camino.includes('/src/main/')) {
        archivos.push(camino);
      }
    }
  }
  await recorrer(BACKEND);
  return archivos;
}

/** El literal de una anotacion de mapeo: `Api.RAIZ + "/x"` -> `/x`; `value = "/x"` -> `/x`. */
function caminoDe(argumentos) {
  if (argumentos === undefined || argumentos.trim() === '') return '';
  const cadenas = [...argumentos.matchAll(/"([^"]*)"/g)].map((c) => c[1]);
  if (cadenas.length === 0) return '';
  /* `params = "formato"` no es un camino: es un requisito de la peticion. Se
     descarta mirando el nombre del miembro que lo precede. */
  const sinParams = argumentos.replace(/params\s*=\s*"[^"]*"/g, '');
  const utiles = [...sinParams.matchAll(/"([^"]*)"/g)].map((c) => c[1]);
  return utiles.join('');
}

const MAPEOS = /@(Get|Post|Put|Patch|Delete|Request)Mapping(?:\(([^)]*)\))?/g;

/** Las rutas del contrato, reconstruidas clase a clase. */
async function rutasDelBackend(archivos) {
  const rutas = new Set();
  for (const camino of archivos) {
    const texto = await readFile(camino, 'utf8');
    if (!texto.includes('@RestController')) continue;
    /* El `@RequestMapping` de la CLASE es el que va antes de `public class`. */
    const corte = texto.search(/\n(?:public\s+|final\s+)*class\s/);
    const cabecera = corte < 0 ? texto : texto.slice(0, corte);
    const cuerpo = corte < 0 ? '' : texto.slice(corte);
    const deLaClase = [...cabecera.matchAll(/@RequestMapping\(([^)]*)\)/g)].map((c) => caminoDe(c[1]));
    const base = deLaClase.length ? deLaClase[deLaClase.length - 1] : '';
    for (const casa of cuerpo.matchAll(MAPEOS)) {
      const sufijo = caminoDe(casa[2]);
      rutas.add((base + sufijo) || '/');
    }
  }
  return rutas;
}

/** Los `acceso` que el backend declara: literales, constantes y centinelas. */
async function accesosDelBackend(archivos) {
  const accesos = new Set();
  const constantes = new Map();
  const sentinelas = new Map();
  for (const camino of archivos) {
    const texto = await readFile(camino, 'utf8');
    const clase = camino.split('/').pop().replace('.java', '');
    for (const casa of texto.matchAll(/(?:private\s+)?static\s+final\s+String\s+ACCESO\s*=\s*"([^"]+)"/g)) {
      constantes.set(`${clase}.ACCESO`, casa[1]);
    }
    if (clase === 'RequiereAcceso') {
      for (const casa of texto.matchAll(/String\s+(\w+)\s*=\s*"(__\w+__)"/g)) {
        sentinelas.set(`RequiereAcceso.${casa[1]}`, casa[2]);
      }
    }
  }
  for (const camino of archivos) {
    const texto = await readFile(camino, 'utf8');
    /* Tres formas, y **hay que leer las tres**: la guarda del propio backend
       —`CatalogoDelSistemaTest`— solo lee la primera, y por eso no ve `sectores`
       ni `calles`. Leer solo literales aqui repetiria su punto ciego. */
    for (const casa of texto.matchAll(/acceso\s*=\s*"([a-z0-9_]+)"/g)) accesos.add(casa[1]);
    for (const casa of texto.matchAll(/acceso\s*=\s*(\w+\.ACCESO)\b/g)) {
      const valor = constantes.get(casa[1]);
      if (valor) accesos.add(valor);
    }
    for (const casa of texto.matchAll(/acceso\s*=\s*(RequiereAcceso\.\w+)\b/g)) {
      const valor = sentinelas.get(casa[1]);
      if (valor) accesos.add(valor);
    }
  }
  return accesos;
}

/** Los codigos que `CatalogoDelSistema.opciones()` declara. */
async function opcionesDelCatalogo(archivos) {
  const camino = archivos.find((f) => f.endsWith('/CatalogoDelSistema.java'));
  if (!camino) return null;
  const texto = await readFile(camino, 'utf8');
  const codigos = new Set();
  /* `new Opcion(modulo, nombre, codigo, titulo)`: el tercero. Se admite el salto
     de linea porque el formateador parte las llamadas largas. */
  for (const casa of texto.matchAll(/new Opcion\(\s*"[^"]*",\s*"[^"]*",\s*"([^"]+)"/g)) {
    codigos.add(casa[1]);
  }
  return codigos;
}

/* ── La medida ──────────────────────────────────────────────────────────── */

const archivos = await javaDelBackend();
const delBackend = await rutasDelBackend(archivos);
const accesosBackend = await accesosDelBackend(archivos);
const catalogo = await opcionesDelCatalogo(archivos);
const declaradas = await rutasDeclaradas();
const { ACCESOS, MODULOS } = await accesosDeclarados();

const fallos = [];
const sinParametros = (r) => r.replace(/\{\w+\}/g, '{}');
const backendSinParametros = new Map();
for (const r of delBackend) backendSinParametros.set(sinParametros(r), r);

for (const { ruta, archivo } of declaradas) {
  if (delBackend.has(ruta)) continue;
  const parecida = backendSinParametros.get(sinParametros(ruta));
  if (parecida) {
    fallos.push(
      `«${ruta}» (${archivo}) existe en el backend con OTRO nombre de parametro: «${parecida}».\n` +
        '      No revienta al compilar y tampoco al pedir —la URL se compone igual—, pero el dia que\n' +
        '      alguien lea el contrato por el nombre del parametro, los dos textos ya no dicen lo mismo.',
    );
  } else {
    fallos.push(
      `«${ruta}» (${archivo}) NO existe como @RequestMapping en el backend.\n` +
        '      Una ruta inventada compila, arranca, y solo se ve contra el backend de verdad, donde\n' +
        '      sale como un 404 que parece un problema de despliegue.',
    );
  }
}

for (const acceso of ACCESOS) {
  if (!accesosBackend.has(acceso)) {
    const quien = MODULOS.flatMap((m) =>
      m.hojas.filter((h) => h.acceso === acceso || (h.tambien ?? []).includes(acceso)).map((h) => `${m.k}/${h.k}`),
    );
    fallos.push(
      `el acceso «${acceso}» que declara ${quien.join(', ')} no existe como @RequiereAcceso en el backend.\n` +
        '      Un permiso que el backend no conoce no se puede conceder: la pantalla contestara 403 siempre.',
    );
  }
}

/* Lo que NO es un fallo pero hay que decir: los accesos que existen y que ningun
   catalogo declara. No se callan — es el segundo sitio donde el catalogo puede
   estar mal, y callarlo es como se descubre dos veces el mismo hallazgo. */
const huerfanos = catalogo === null ? [] : [...accesosBackend].filter((a) => !a.startsWith('__') && !catalogo.has(a)).sort();

console.log(
  `${declaradas.length} rutas declaradas contra ${delBackend.size} del backend · ` +
    `${ACCESOS.length} accesos contra ${accesosBackend.size} · ` +
    `${catalogo === null ? '?' : catalogo.size} opciones en el catalogo`,
);

if (huerfanos.length) {
  console.log(
    `\nAVISO — ${huerfanos.length} acceso(s) que el backend exige y que «CatalogoDelSistema» NO declara:\n`,
  );
  for (const a of huerfanos) {
    const usado = ACCESOS.includes(a) ? ' ← y esta interfaz lo necesita' : '';
    console.log(`  · ${a}${usado}`);
  }
  console.log(
    '\n  No es un fallo de este frontend y por eso no pone el arnes en rojo, pero tampoco se calla.\n' +
      '  La causa esta medida: sus controladores pasan el acceso como CONSTANTE —«SectorController.ACCESO»—\n' +
      '  y «CatalogoDelSistemaTest» busca literales de cadena, asi que no los ve y sigue en verde.\n' +
      '  Mientras no se siembren, nadie puede recibir esos permisos y sus pantallas contestaran 403.',
  );
}

if (fallos.length) {
  console.error(`\n${fallos.length} desajuste(s) con el backend:\n`);
  for (const f of fallos) console.error('  - ' + f + '\n');
  process.exit(1);
}
console.log('\ntoda ruta declarada existe en el backend, y todo acceso tambien');
