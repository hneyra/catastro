/**
 * El motor: la version de Node, y que el flujo pueda llamar de verdad a los arneses.
 *
 *   node verificaciones/node.mjs
 *
 * <h2>Por que la segunda mitad esta aqui</h2>
 *
 * Porque este archivo es el que la sufrio. `package.json` declaraba el arnes con
 * el nombre `node` y `.github/workflows/frontend.yml` lo llamaba `yarn node`, que
 * **no es este arnes**: es el comando `node` de yarn 1, que arranca Node y sale.
 * Medido: `yarn node` imprime «yarn node v1.22.22 · Done in 0.04s» y sale con 0
 * sin ejecutar una linea de aqui, mientras que `yarn run node` si lo ejecuta. O
 * sea que el paso «La version de Node dice lo mismo en los dos sitios» llevaba en
 * verde desde que existe **sin haber comprobado nada**, y su sintoma era la
 * ausencia de sintoma.
 *
 * El arreglo de fondo no es cambiar el flujo —eso se puede volver a escribir
 * igual— sino que **ningun guion se llame como un comando de yarn**. Y la lista
 * de comandos no se copia aqui: se le pregunta a yarn con `yarn help`, que es la
 * fuente de verdad. Copiada, se quedaria vieja sin ruido, que es la forma de
 * defecto que este repositorio lleva contadas ocho veces.
 *
 * `.nvmrc` es lo que instalan CI y quien clona; `engines` de `package.json` es lo
 * que **yarn comprueba al instalar** —gracias a `engine-strict=true` en
 * `.npmrc`—. Si se desincronizan, CI instala una version que el propio proyecto
 * rechaza —o, peor, una que acepta y que no es la que se probo—, y el sintoma
 * llega como una tanda de pruebas rojas cuyo mensaje no se parece a la causa.
 *
 * De donde sale el rango, medido y no elegido: de lo que las dependencias piden.
 * `vite` es el que manda (`^18 || ^20 || >=22`), `playwright-core` descarta la 18
 * (`>=20`). La interseccion es `^20 || >=22`, con los minimos de parche que Vite 6
 * documenta. **La 21 queda fuera** y no por descuido: Vite no la admite, y es la
 * clase de version que alguien instala porque «es mas nueva que la 20».
 *
 * No abre navegador ni necesita backend.
 */
import { readFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';

const raiz = new URL('../', import.meta.url);
const nvmrc = (await readFile(new URL('.nvmrc', raiz), 'utf8')).trim();
const paquete = JSON.parse(await readFile(new URL('package.json', raiz), 'utf8'));
const rango = paquete.engines?.node;
const npmrc = await readFile(new URL('.npmrc', raiz), 'utf8').catch(() => '');

const fallos = [];
if (rango === undefined) {
  fallos.push('`package.json` no declara `engines.node`: yarn no comprueba nada al instalar');
}
if (nvmrc === '') fallos.push('`.nvmrc` esta vacio');
if (!/^\s*engine-strict\s*=\s*true\s*$/m.test(npmrc)) {
  fallos.push(
    '`.npmrc` no dice `engine-strict=true`: sin eso, `engines` es una NOTA y no una guarda —npm avisa y sigue—',
  );
}

/**
 * Si la version que `.nvmrc` nombra cae dentro del rango declarado.
 *
 * Se comparan MAYORES, que es lo que `.nvmrc` nombra: «22» no dice que parche se
 * instalara —lo elige `actions/setup-node`—, asi que exigir el parche aqui seria
 * comprobar algo que este archivo no puede saber. Lo que si puede saber, y es lo
 * que falla en la practica, es que alguien escriba «18» o «21».
 */
function mayorAdmitido(mayor, r) {
  return r.split('||').some((parte) => {
    const t = parte.trim();
    const n = Number((t.match(/(\d+)/) ?? [])[1]);
    if (Number.isNaN(n)) return false;
    if (t.startsWith('^')) return mayor === n;
    if (t.startsWith('>=')) return mayor >= n;
    if (t.startsWith('>')) return mayor > n;
    return mayor === n;
  });
}

const mayor = Number(nvmrc.replace(/^v/, '').split('.')[0]);
if (rango !== undefined && !Number.isNaN(mayor) && !mayorAdmitido(mayor, rango)) {
  fallos.push(
    `«.nvmrc» dice Node ${nvmrc} y «engines.node» pide ${rango}: CI instalaria una version que ` +
      'el propio proyecto rechaza al instalar, y el error llegaria como una tanda de rojos que no se parecen a la causa',
  );
}

/* Y que el rango siga cubriendo lo que las dependencias piden: si vite sube su
   minimo y aqui no, se instala una version que el no admite y el fallo aparece al
   compilar, no al instalar. Se lee de `node_modules`, que es lo que de verdad
   esta puesto, y no de una copia. */
let deVite = null;
try {
  deVite = JSON.parse(await readFile(new URL('node_modules/vite/package.json', raiz), 'utf8')).engines?.node ?? null;
} catch {
  fallos.push('no se pudo leer `node_modules/vite/package.json`: sin el esta comprobacion no mide nada');
}
if (deVite !== null && !mayorAdmitido(mayor, deVite)) {
  fallos.push(`vite pide Node ${deVite} y «.nvmrc» dice ${nvmrc}: la version que CI instala no la admite vite`);
}

/* ── Y que el flujo pueda llamar a los arneses ──────────────────────────── */

/**
 * Los comandos de yarn, preguntados a yarn.
 *
 * `yarn <x>` ejecuta el guion `<x>` **solo si `<x>` no es un comando suyo**; si
 * lo es, gana el comando y el guion no corre. `test` no sale en esta lista a
 * proposito —yarn lo reenvia al guion— y por eso preguntar vale mas que escribir
 * la lista a mano.
 */
function comandosDeYarn() {
  const salida = execFileSync('yarn', ['help'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
  const bloque = salida.slice(salida.indexOf('Commands:'));
  const nombres = new Set();
  for (const casa of bloque.matchAll(/^\s*-\s+(.+)$/gm)) {
    for (const alias of casa[1].split('/')) nombres.add(alias.trim());
  }
  return nombres;
}

let comandos = null;
try {
  comandos = comandosDeYarn();
} catch {
  fallos.push('no se pudo leer `yarn help`: sin la lista de comandos de yarn esta mitad no mide nada');
}
if (comandos !== null && comandos.size === 0) {
  fallos.push('`yarn help` no nombro ni un comando: la lectura dejo de valer y esta mitad se cumpliria sola');
}

const guiones = Object.keys(paquete.scripts ?? {});
if (guiones.length === 0) fallos.push('`package.json` no declara ni un guion: no hay nada que comprobar');
if (comandos !== null) {
  for (const guion of guiones) {
    if (!comandos.has(guion)) continue;
    fallos.push(
      `el guion «${guion}» se llama como un comando de yarn: «yarn ${guion}» ejecuta EL COMANDO y no el ` +
        `guion, que no corre y no dice que no ha corrido —sale con 0—. Renombralo: «yarn run ${guion}» ` +
        'funciona, pero deja el defecto a una linea de volver',
    );
  }
}

/* Y que el flujo llame a guiones que existen. Un `yarn` a un nombre que no esta
   declarado revienta y se ve; el caso que NO se ve es el de arriba, y este cierra
   la otra mitad: que un guion se renombre y el flujo se quede nombrando el
   anterior. */
const FLUJO = new URL('../../.github/workflows/frontend.yml', import.meta.url);
const flujo = await readFile(FLUJO, 'utf8').catch(() => null);
if (flujo === null) {
  fallos.push('no se encontro `.github/workflows/frontend.yml`: no se pudo comprobar como llama a los arneses');
} else {
  const llamados = [...flujo.matchAll(/^\s*run:\s*yarn\s+([a-z][a-z0-9-]*)/gm)].map((c) => c[1]);
  if (llamados.length === 0) {
    fallos.push(
      '`frontend.yml` no llama a ni un `yarn <guion>`: o cambio de forma, o dejo de correr los arneses. ' +
        'Sin ninguna llamada que mirar, esta comprobacion se estaria cumpliendo sola',
    );
  }
  for (const llamado of new Set(llamados)) {
    if (llamado === 'install') continue;
    if (!guiones.includes(llamado)) {
      fallos.push(`«frontend.yml» corre \`yarn ${llamado}\` y «package.json» no declara ese guion`);
    }
  }
}

if (!fallos.length) {
  console.log(
    `.nvmrc dice ${nvmrc} · engines pide ${rango} · vite pide ${deVite ?? '—'}: los tres cuadran · ` +
      `${guiones.length} guiones, ninguno se llama como uno de los ${comandos?.size ?? '?'} comandos de yarn`,
  );
  process.exit(0);
}
console.log(`${fallos.length} problema(s) con la version de Node:\n`);
for (const f of fallos) console.log('  - ' + f);
process.exit(1);
