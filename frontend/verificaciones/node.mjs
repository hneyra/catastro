/**
 * La version de Node se declara en dos sitios, y los dos tienen que decir lo mismo.
 *
 *   node verificaciones/node.mjs
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

if (!fallos.length) {
  console.log(`.nvmrc dice ${nvmrc} · engines pide ${rango} · vite pide ${deVite ?? '—'}: los tres cuadran`);
  process.exit(0);
}
console.log(`${fallos.length} problema(s) con la version de Node:\n`);
for (const f of fallos) console.log('  - ' + f);
process.exit(1);
