/**
 * Con el proxy APAGADO y la red cortada, ninguna pantalla puede ensenar una cifra.
 *
 *   node verificaciones/sin-red.mjs [modulo]
 *
 * Es la verificacion de la unica regla que gobierna esta interfaz: cuando el
 * backend no sostiene lo que la pantalla dice, se dibuja «—» y el motivo, nunca
 * la cifra del simulado. Y el momento en que esa regla se rompe sin que nadie lo
 * vea es justamente este —un 403, un 500, la red caida—, porque con datos delante
 * la pantalla parece correcta siempre.
 *
 * <h2>Por que construye su propia vista previa</h2>
 *
 * El proxy se apaga con `VITE_CATASTRO_PROXY_DE_DATOS=false`, y Vite resuelve
 * `import.meta.env` **al compilar**: no es una bandera de ejecucion que se pueda
 * poner desde el navegador. Asi que este arnes compila con la bandera puesta y
 * sirve ESE paquete. Correrlo contra la vista previa de siempre mediria el
 * paquete equivocado —con el proxy dentro— y pasaria en verde sin haber apagado
 * nada.
 *
 * Y de paso mide la segunda mitad de la decision: **con la bandera puesta, el
 * trozo del proxy no esta en el paquete**. Si estuviera, «se apaga» seria una
 * afirmacion sobre el comportamiento y no sobre lo que se despliega, y una
 * interfaz de produccion llevaria dentro los predios inventados esperando a que
 * alguien cambiara una variable.
 *
 * Necesita el Chromium de Playwright. No necesita backend: aborta las peticiones.
 */
import { chromium } from 'playwright-core';
import { spawn } from 'node:child_process';
import { readFile, readdir, rm } from 'node:fs/promises';
import { leerRegistro } from './registro.mjs';
import { VISTAS, comprobarVistas, hashDe } from './vistas.mjs';

const { DESTINOS } = await leerRegistro('.registro-sin-red');
const soloModulo = process.argv[2]?.startsWith('--') ? null : process.argv[2];

const desajustes = comprobarVistas(DESTINOS);
if (desajustes.length) {
  console.error(`\n${desajustes.length} vista(s) no cuadran con el registro:\n\n  ${desajustes.join('\n  ')}`);
  process.exit(2);
}

/* Los destinos y ademas sus ESTADOS: el detalle de un maestro-detalle y la
   pestana de una matriz no se dibujan con el destino a secas, y una cifra del
   simulado escondida ahi pasaria este arnes en verde. */
const RECORRIDO = [
  ...DESTINOS.map((d) => ({ modulo: d.modulo, hash: `#/${d.modulo}/${d.hoja}` })),
  ...VISTAS.map((v) => ({ modulo: v.modulo, hash: hashDe(v) })),
];

/**
 * La raiz de la API, tal como sale escrita en pantalla.
 *
 * Con el backend caido, una pantalla que solo diga «no se pudo leer» no dice el
 * QUE: la mitad util de una pantalla sin datos es nombrar la ruta que se lo
 * habria contestado. Lo pone `Servida`, que recibe las rutas de `src/api/*.ts`
 * —donde `rutas.mjs` las contrasta contra el backend—, asi que aqui basta con
 * comprobar que la pantalla nombra alguna.
 */
const RAIZ_EN_PANTALLA = '/catastro/api/v1';

const SALIDA = 'dist-sin-red';
const PUERTO = Number(process.env.CATASTRO_PUERTO_SIN_RED ?? 5211);
const BASE = `http://localhost:${PUERTO}`;

/** Lo que solo puede salir de un dato que aqui no se ha podido leer. */
const CIFRAS = [
  { nombre: 'importe', re: /S\/\s?-?\d[\d.,]*/g },
  { nombre: 'millares', re: /(?<![\d.,])\d{1,3}(?:,\d{3})+(?![\d.,])/g },
  { nombre: 'porcentaje', re: /\d+[.,]\d+\s?%/g },
  /* Un importe SIN su simbolo delante: exactamente dos decimales, que es la
     forma del dinero y del area medida. Un ano no casa y «1.0206» tampoco. */
  { nombre: 'decimal', re: /(?<![\d.,%])\d[\d,]*\.\d{2}(?![\d%])/g },
  /* Un identificador de documento o de predio. No es una cifra, y por eso se
     escapa de los de arriba: con la red cortada una pantalla podia seguir
     diciendo el numero de un certificado o el codigo de un predio —los del
     simulado— y este arnes informaba «ninguna ensena una cifra», en verde.
     Se pide un grupo de CUATRO digitos seguidos —el ano de un correlativo— o
     una tira larga de digitos —la forma de un codigo catastral de 23—, que es lo
     que separa un identificador de un rotulo. */
  { nombre: 'identificador', re: /\b[A-Z]{2,4}-\d{4}-\d{2,7}\b|\b\d{12,}\b/g },
  /* Una magnitud con su unidad: los tres de arriba exigen separador de millares
     o dos decimales, y una medida puede no llevar ninguno de los dos. Va anclada
     a la unidad —no a «cualquier numero»— porque ensancharlo a secas llena las
     pantallas de falsos positivos, y un escaner que grita deja de leerse. */
  { nombre: 'magnitud', re: /(?<![\d.,])\d[\d.,]*\s?(?:km|m²|m2|ha)\b/g },
];

/* ── Compilar con el proxy apagado, y comprobar que de verdad se fue ────── */

console.log(`Compilando con VITE_CATASTRO_PROXY_DE_DATOS=false en ${SALIDA}/ …`);
await rm(SALIDA, { recursive: true, force: true });
await new Promise((listo, mal) => {
  const hijo = spawn('npx', ['vite', 'build', '--outDir', SALIDA], {
    env: { ...process.env, VITE_CATASTRO_PROXY_DE_DATOS: 'false' },
    stdio: ['ignore', 'ignore', 'inherit'],
  });
  hijo.on('exit', (codigo) => (codigo === 0 ? listo() : mal(new Error(`vite build salio con ${codigo}`))));
});

/**
 * Que el simulado NO viaje en el paquete.
 *
 * <h2>Se busca el CODIGO, no un nombre de archivo, y esta medido por que</h2>
 *
 * La primera version de esta guarda miraba que no hubiera ningun activo llamado
 * `proxy-*.js`, y **pasaba en verde con el defecto exacto que existe para
 * atrapar**: devolver el `import()` dinamico de `main.tsx` a un `import`
 * estatico. Con el estatico, Rollup no crea un trozo aparte — mete el proxy
 * DENTRO del paquete principal, asi que no hay ningun archivo que se llame
 * «proxy» y la comprobacion se cumplia sola. Medir la ausencia de un nombre no
 * es medir la ausencia del codigo.
 *
 * Se buscan marcas de los datos inventados, que es lo que de verdad no puede
 * salir en un artefacto que se publica: el nombre de una zona, el codigo de un
 * predio de demostracion, el nombre de una campania.
 *
 * Y **se comprueba que las marcas se encuentran cuando el proxy SI esta**: una
 * marca que no aparece nunca convierte esto en otra guarda que se cumple sola,
 * que es la trampa que acaba de caer una vez.
 */
const MARCAS = [
  /* Una zona, un codigo de predio y el numero de un certificado. Los tres viajan
     ENTEROS al navegador —el proxy los devuelve como cuerpo—, que es lo que hace
     que se puedan buscar en el paquete.

     Y elegirlos costo una medida: la primera terna traia «Subvaluacion en el
     cercado», el nombre de la campania simulada, y **no aparece en el paquete ni
     con el proxy encendido**. El proxy solo lee `CAMPANIA.id` —no hay ningun
     endpoint que liste campanias, asi que el objeto no se serializa nunca— y el
     minificador se queda con el uno y tira el resto. Lo caza el contraste de
     abajo; sin el, esa marca habria hecho que un tercio de esta guarda se
     cumpliera sola. */
  'Residencial de densidad media',
  '20010401001001000000000',
  'ITSE-2026-000118',
];

async function marcasEnElPaquete(directorio) {
  const encontradas = new Set();
  const activos = await readdir(`${directorio}/assets`);
  for (const activo of activos) {
    if (!activo.endsWith('.js')) continue;
    const texto = await readFile(`${directorio}/assets/${activo}`, 'utf8');
    for (const marca of MARCAS) if (texto.includes(marca)) encontradas.add(marca);
  }
  return { encontradas, activos };
}

/* El contraste: con el proxy encendido las marcas TIENEN que estar. */
console.log('Compilando con el proxy encendido, para comprobar que las marcas se ven …');
await rm('dist-con-proxy', { recursive: true, force: true });
await new Promise((listo, mal) => {
  const hijo = spawn('npx', ['vite', 'build', '--outDir', 'dist-con-proxy'], {
    env: { ...process.env, VITE_CATASTRO_PROXY_DE_DATOS: 'true' },
    stdio: ['ignore', 'ignore', 'inherit'],
  });
  hijo.on('exit', (codigo) => (codigo === 0 ? listo() : mal(new Error(`vite build salio con ${codigo}`))));
});
const conProxy = await marcasEnElPaquete('dist-con-proxy');
await rm('dist-con-proxy', { recursive: true, force: true });
if (conProxy.encontradas.size !== MARCAS.length) {
  const perdidas = MARCAS.filter((m) => !conProxy.encontradas.has(m));
  console.error(
    `\nCon el proxy ENCENDIDO no se encuentran ${perdidas.length} de las ${MARCAS.length} marcas: ${perdidas.join(', ')}.\n` +
      'Esta comprobacion no mide nada: una marca que no aparece nunca hace que la mitad de abajo se\n' +
      'cumpla sola. Actualiza las marcas a datos que el simulado siga trayendo.',
  );
  process.exit(2);
}

const sinProxy = await marcasEnElPaquete(SALIDA);
if (sinProxy.encontradas.size > 0) {
  console.error(
    `\nEl simulado sigue DENTRO del paquete con la bandera apagada: ${[...sinProxy.encontradas].join(', ')}.\n` +
      'La segunda decision de ADR-0010 dice que la rama entera desaparece, y para eso el proxy se\n' +
      'carga con `import()` dinamico. Con un `import` estatico Rollup lo mete en el paquete principal\n' +
      '—sin crear ningun archivo que se llame «proxy»—, y una interfaz de produccion llevaria dentro\n' +
      'los 23 predios inventados esperando a que alguien cambiara una variable.',
  );
  process.exit(1);
}
console.log(
  `el simulado no viaja: ${sinProxy.activos.length} activos y ninguna de las ${MARCAS.length} marcas ` +
    '(las tres se encuentran con el proxy encendido, asi que la busqueda mide algo)',
);

/* ── Servir y mirar ─────────────────────────────────────────────────────── */

const servidor = spawn('npx', ['vite', 'preview', '--outDir', SALIDA, '--port', String(PUERTO), '--strictPort'], {
  stdio: ['ignore', 'ignore', 'inherit'],
});
process.on('exit', () => servidor.kill());

/* Sin `wait-on`: es una dependencia mas para lo que hacen cinco lineas. */
let vivo = false;
for (let i = 0; i < 60 && !vivo; i++) {
  await new Promise((r) => setTimeout(r, 500));
  vivo = await fetch(BASE)
    .then((r) => r.ok)
    .catch(() => false);
}
if (!vivo) {
  console.error(`La vista previa no levanto en ${BASE}`);
  servidor.kill();
  process.exit(2);
}

const navegador = await chromium.launch();
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 1400 } });
const pagina = await contexto.newPage();
await pagina.route('**/catastro/api/v1/**', (r) => r.abort());

/**
 * Abre lo que nace plegado antes de mirar.
 *
 * `innerText` no ve lo que esta oculto, asi que una seccion cerrada seria un
 * escondite perfecto para una cifra.
 */
async function desplegarlo() {
  for (let vuelta = 0; vuelta < 3; vuelta++) {
    const plegados = pagina.locator('[aria-expanded="false"]');
    const cuantos = await plegados.count();
    if (cuantos === 0) break;
    for (let i = 0; i < cuantos; i++) {
      await plegados
        .nth(i)
        .click({ timeout: 900 })
        .catch(() => {});
    }
    await pagina.waitForTimeout(160);
  }
  await pagina.evaluate(() => document.querySelectorAll('details').forEach((d) => (d.open = true)));
  await pagina.waitForTimeout(200);
}

const sucias = [];
let vistas = 0;

for (const d of RECORRIDO) {
  if (soloModulo && d.modulo !== soloModulo) continue;
  const ruta = d.hash;
  await pagina.goto(`${BASE}/${ruta}`, { waitUntil: 'domcontentloaded' });
  await pagina.waitForTimeout(900);
  vistas++;
  await desplegarlo();

  const texto = await pagina.locator('body').innerText();
  for (const { nombre, re } of CIFRAS) {
    const halladas = [...texto.matchAll(re)].map((x) => x[0]).filter((x) => !/^S\/\s?[—-]$/.test(x));
    if (halladas.length) {
      const unicas = [...new Set(halladas)];
      sucias.push({ ruta, nombre, halladas: unicas.slice(0, 6), total: unicas.length });
    }
  }

  /* Y que la pantalla no se haya quedado MUDA: sin datos tiene que decir por
     que, no quedarse en blanco. Un blanco pasaria este arnes en verde, y es el
     desenlace peor de los tres. */
  const cuerpo = await pagina
    .locator('main')
    .innerText()
    .catch(() => '');
  if (cuerpo.trim().length < 40) {
    sucias.push({ ruta, nombre: 'muda', halladas: ['el <main> se queda en blanco'], total: 1 });
  }

  /* Y que diga QUE no pudo leer. «No se pudo contactar con el servidor» sin la
     ruta deja a quien mira sin nada que comprobar ni a quien preguntar. */
  if (!cuerpo.includes(RAIZ_EN_PANTALLA)) {
    sucias.push({
      ruta,
      nombre: 'anonima',
      halladas: ['no nombra ninguna ruta del backend'],
      total: 1,
    });
  }
}

await navegador.close();
servidor.kill();

console.log(`\n${vistas} pantallas recorridas con el proxy apagado y la red cortada`);
if (!sucias.length) {
  console.log('ninguna ensena una cifra: lo que no se puede leer, no se afirma');
  process.exit(0);
}
console.log(`\n${sucias.length} pantalla(s) afirman algo que no han podido leer:\n`);
for (const s of sucias) {
  const mas = s.total > s.halladas.length ? ` … y ${s.total - s.halladas.length} mas` : '';
  console.log(`  ${s.ruta.padEnd(34)} ${s.nombre.padEnd(14)} ${s.halladas.join(' · ')}${mas}`);
}
console.log('\nCon el backend caido solo puede salir «—» y el motivo. Una cifra aqui es del simulado.');
process.exit(1);
