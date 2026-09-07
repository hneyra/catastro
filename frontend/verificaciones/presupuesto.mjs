/**
 * El presupuesto de reloj de un job, medido contra el tope que lo mata.
 *
 *   node verificaciones/presupuesto.mjs abrir <job>
 *   node verificaciones/presupuesto.mjs cerrar
 *
 * <h2>Que sale mal, y por que no lo arregla subir el tope</h2>
 *
 * `timeout-minutes` no es un presupuesto: es una guillotina. Solo se conoce
 * cuando se cruza, y cuando se cruza el desenlace es `cancelled` —sin registro,
 * sin causa y sin nada que leer—. Un check **bloqueante** que a veces acaba asi
 * ensena a **relanzar en vez de a leer**, y eso se lleva por delante el valor de
 * todos los rojos que este repositorio ha construido.
 *
 * Medido, que es de donde sale este archivo: el job «Los destinos, en un
 * navegador de verdad» consumia **682-790 s de 900**, hasta el **88 %**, y en el
 * PR #64 —que ni siquiera tocaba `frontend/`— quedo `cancelled`. Nadie lo vio
 * venir porque nada lo decia: las cinco corridas anteriores estaban en verde.
 *
 * <h2>Lo minimo que lo hace cierto</h2>
 *
 * Dos marcas de tiempo y **el numero que ya esta escrito**: el `timeout-minutes`
 * del propio job, que es el unico limite que de verdad manda. No hay una segunda
 * tabla de plazos escrita a mano —esa se queda vieja sola— ni un sistema de
 * medicion nuevo: **el reparto por paso ya lo publica Actions** y la API lo
 * sirve en `steps[]`, asi que cuando esto se pone rojo, quien mire tiene el
 * culpable a un clic.
 *
 * Dos umbrales, y los dos son fracciones de ese tope:
 *
 *   · **{@link AVISO}** — sale un `::warning::` y el job sigue en verde. Es el
 *     aviso que hoy no existe: «va justo», con reloj y con margen para leerlo.
 *   · **{@link ROJO}** — el job falla **diciendo por que**. No anade una
 *     cancelacion: la SUSTITUYE por algo que se puede leer, y por eso el umbral
 *     esta alto a proposito. Un rojo por reloj puesto pronto seria la misma
 *     intermitencia con otro nombre.
 *
 * <h2>Y se comprueba que mide el job ENTERO</h2>
 *
 * Un cronometro que arranca tarde o para pronto da un numero mas bajo y sale en
 * verde, que es la forma de defecto de la que este repositorio lleva doce
 * hallazgos. Asi que `cerrar` lee el flujo y exige que `abrir` sea el **primer**
 * paso que ejecuta algo y `cerrar` el **ultimo**, y que lo que venga detras sea
 * de `if: failure()`. Si no lo puede comprobar, sale con 2 —«no se pudo
 * comprobar»— y no con 0.
 *
 * Queda fuera del cronometro lo que ocurre antes del `checkout`, porque hasta
 * entonces este archivo no existe en el disco del runner: son los 1-2 s de
 * `Set up job` y del propio `checkout`, medidos en las cinco corridas.
 */
import { readFile, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/** Fraccion del tope a partir de la cual sale un aviso, con el job en verde. */
const AVISO = 0.6;
/** Fraccion del tope a partir de la cual el job falla EXPLICANDOSE. */
const ROJO = 0.85;

const FLUJO = new URL('../../.github/workflows/frontend.yml', import.meta.url);
const MARCA = join(process.env.RUNNER_TEMP ?? tmpdir(), 'kamayuk-presupuesto-del-job');

const [orden, jobPedido] = process.argv.slice(2);

/**
 * El job tal como el flujo lo declara: su tope y sus pasos.
 *
 * Se lee el YAML a mano y por indentacion, sin dependencia nueva: es el mismo
 * archivo —y el mismo trato— que `node.mjs` ya le da. Lo que hace falta de el
 * son tres cosas contables, no un arbol.
 */
async function leerElJob(clave) {
  if (!existsSync(FLUJO)) return { fallo: 'no se encontro `.github/workflows/frontend.yml`' };
  const lineas = (await readFile(FLUJO, 'utf8')).split('\n');

  const iJobs = lineas.findIndex((l) => l === 'jobs:');
  if (iJobs < 0) return { fallo: '`frontend.yml` no declara `jobs:`' };

  const iJob = lineas.findIndex((l, i) => i > iJobs && l === `  ${clave}:`);
  if (iJob < 0) return { fallo: `«${clave}» no es ningun job de \`frontend.yml\`` };
  let fin = lineas.length;
  for (let i = iJob + 1; i < lineas.length; i++) {
    if (/^ {2}\S/.test(lineas[i])) {
      fin = i;
      break;
    }
  }
  const bloque = lineas.slice(iJob, fin);

  const tope = bloque.map((l) => l.match(/^ {4}timeout-minutes:\s*(\d+)\s*$/)).find(Boolean);
  if (!tope) {
    return {
      fallo:
        `el job «${clave}» no declara \`timeout-minutes\`. Sin tope no hay presupuesto que repartir: ` +
        'GitHub aplicaria su omision de 360 minutos, que no es un limite sino la ausencia de uno',
    };
  }

  const iPasos = bloque.findIndex((l) => l === '    steps:');
  if (iPasos < 0) return { fallo: `el job «${clave}» no declara \`steps:\`` };
  /** @type {{lineas: string[]}[]} */
  const pasos = [];
  for (const linea of bloque.slice(iPasos + 1)) {
    if (/^ {6}- /.test(linea)) pasos.push({ lineas: [linea] });
    else if (pasos.length) pasos.at(-1).lineas.push(linea);
  }
  if (!pasos.length) return { fallo: `el job «${clave}» no tiene ni un paso` };

  return {
    topeEnSegundos: Number(tope[1]) * 60,
    pasos: pasos.map((p) => ({
      texto: p.lineas.join('\n'),
      ejecuta: p.lineas.some((l) => /^\s+(- )?run:/.test(l)),
      soloSiFalla: p.lineas.some((l) => /^\s+(- )?if:\s*failure\(\)/.test(l)),
    })),
  };
}

/* ── abrir ──────────────────────────────────────────────────────────────── */

if (orden === 'abrir') {
  if (!jobPedido) {
    console.error('Uso: node verificaciones/presupuesto.mjs abrir <clave del job en frontend.yml>');
    process.exit(2);
  }
  await writeFile(MARCA, `${jobPedido}\n${Math.floor(Date.now() / 1000)}\n`, 'utf8');
  console.log(`presupuesto abierto para el job «${jobPedido}»`);
  process.exit(0);
}

/* ── cerrar ─────────────────────────────────────────────────────────────── */

if (orden !== 'cerrar') {
  console.error('Uso: node verificaciones/presupuesto.mjs abrir <job> | cerrar');
  process.exit(2);
}

if (!existsSync(MARCA)) {
  console.error(
    `No hay marca de apertura en ${MARCA}, asi que no se puede decir cuanto lleva este job.\n` +
      'El primer paso del job tiene que ser `node verificaciones/presupuesto.mjs abrir <job>`; sin el,\n' +
      'esto saldria en verde sin haber medido nada, que es exactamente el defecto que existe para atrapar.',
  );
  process.exit(2);
}

const [clave, desde] = (await readFile(MARCA, 'utf8')).trim().split('\n');
const consumido = Math.floor(Date.now() / 1000) - Number(desde);
const job = await leerElJob(clave);
if (job.fallo) {
  console.error(`No se pudo comprobar el presupuesto: ${job.fallo}.`);
  process.exit(2);
}

/* Que el cronometro cubra el job ENTERO: si arranca tarde o para pronto, el
   numero sale mas bajo y este paso se pone verde sin haber medido lo que mide.
   Es la leccion de la regla 11 por el mismo eje —lo que no entra en el reparto
   deja de revisarse, en verde—. */
const quejas = [];
const iAbrir = job.pasos.findIndex((p) => /presupuesto\.mjs abrir/.test(p.texto));
const iCerrar = job.pasos.findIndex((p) => /presupuesto\.mjs cerrar/.test(p.texto));
const primeroQueEjecuta = job.pasos.findIndex((p) => p.ejecuta);
if (iAbrir < 0) quejas.push(`el job «${clave}» no llama a \`presupuesto.mjs abrir\``);
else if (iAbrir !== primeroQueEjecuta) {
  quejas.push(
    `\`abrir\` es el paso ${iAbrir + 1} y el primero que ejecuta algo es el ${primeroQueEjecuta + 1}: ` +
      'lo que hay entre medias no entra en el presupuesto y el numero sale mas bajo de lo que es',
  );
}
if (iCerrar < 0) quejas.push(`el job «${clave}» no llama a \`presupuesto.mjs cerrar\``);
else {
  const detras = job.pasos.slice(iCerrar + 1).filter((p) => !p.soloSiFalla);
  if (detras.length) {
    quejas.push(
      `hay ${detras.length} paso(s) detras de \`cerrar\` que no son de \`if: failure()\`: su tiempo no se ` +
        'cuenta, asi que el presupuesto diria menos de lo que el job consume',
    );
  }
}
if (quejas.length) {
  console.error(`El presupuesto no cubre el job entero, asi que no mide lo que dice medir:\n`);
  for (const q of quejas) console.error(`  · ${q}`);
  process.exit(2);
}

const tope = job.topeEnSegundos;
const fraccion = consumido / tope;
const porciento = Math.round(fraccion * 100);
const linea = `el job «${clave}» consumio ${consumido} s de los ${tope} s de su tope: ${porciento} %`;

const resumen = process.env.GITHUB_STEP_SUMMARY;
if (resumen) await writeFile(resumen, `\n${linea}\n`, { flag: 'a' });

if (fraccion >= ROJO) {
  console.error(
    `${linea} — por encima del ${Math.round(ROJO * 100)} %.\n\n` +
      'Esto NO es una cancelacion: es el aviso que la sustituye. Al ritmo actual la proxima corrida\n' +
      'puede acabar en `cancelled`, sin registro y sin causa, y un check bloqueante que acaba asi\n' +
      'ensena a relanzar en vez de a leer.\n\n' +
      'El reparto por paso lo publica la propia corrida: `gh api repos/<duenio>/<repo>/actions/jobs/<id>`\n' +
      'trae `steps[]` con `started_at` y `completed_at`. Se arregla el paso que crecio, no el tope:\n' +
      'subir `timeout-minutes` compra unos meses y deja el mismo problema con un numero mayor.',
  );
  process.exit(1);
}

if (fraccion >= AVISO) {
  const aviso = `${linea} — por encima del ${Math.round(AVISO * 100)} %, y a partir del ${Math.round(ROJO * 100)} % este paso falla`;
  console.log(`::warning title=El job va justo de tiempo::${aviso}`);
  console.log(aviso);
  process.exit(0);
}

console.log(`${linea} (avisa al ${Math.round(AVISO * 100)} %, falla al ${Math.round(ROJO * 100)} %)`);
