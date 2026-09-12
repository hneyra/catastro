/**
 * Recorre cada destino —y cada VISTA— en un navegador de verdad y guarda una captura.
 *
 *   node verificaciones/mirar.mjs [modulo] [--alto=1600]
 *
 * No compara con nada: sirve para VER lo que se dibuja. Falla ante un error de
 * consola y **ante un `<main>` practicamente vacio**, que es como falla de verdad
 * una pantalla a medio hacer: en silencio, sin un solo error de consola. Mirar las
 * capturas es parte del trabajo — una pantalla que compila y no se parece al
 * artboard no esta portada.
 *
 * Los destinos salen del registro compilado al vuelo, no de una lista repetida
 * aqui: una lista copiada se queda vieja sin ruido. Las **vistas** —los estados
 * que un destino a secas no dibuja: el detalle de un maestro-detalle, la pestana
 * de una matriz— si son una lista a mano, en `vistas.mjs`, y por eso traen su
 * propia guarda: cada una tiene que nombrar un destino que el registro declare.
 *
 * Necesita una vista previa levantada (`yarn dev` o `vite preview`) y el Chromium
 * de Playwright.
 */
import { chromium } from 'playwright-core';
import { mkdir } from 'node:fs/promises';
import { leerRegistro } from './registro.mjs';
import { CONTAR_PETICIONES, cronometroDeEsperas } from './reposo.mjs';
import { VISTAS, comprobarVistas, hashDe } from './vistas.mjs';
import { baseDeLaApp } from './base.mjs';
import { emisorDeMentira } from './emisor.mjs';

const { DESTINOS } = await leerRegistro('.registro-mirar');

/* Las vistas: los ESTADOS de una pantalla que el destino a secas no dibuja —el
   detalle de un maestro-detalle, la pestana de una matriz—. Sin ellas este
   recorrido informa en verde sobre la mitad de las hojas nuevas. */
const desajustes = comprobarVistas(DESTINOS);
if (desajustes.length) {
  console.error(`\n${desajustes.length} vista(s) no cuadran con el registro:\n\n  ${desajustes.join('\n  ')}`);
  process.exit(2);
}
const RECORRIDO = [
  ...DESTINOS.map((d) => ({ ...d, hash: `#/${d.modulo}/${d.hoja}`, archivo: `${d.modulo}-${d.hoja}` })),
  ...VISTAS.map((v) => ({
    ...v,
    hash: hashDe(v),
    archivo: `${v.modulo}-${v.hoja}-${v.nombre.replace(/[^a-z0-9]+/gi, '-')}`,
  })),
];

/* `CATASTRO_BASE` es el ORIGEN; la base de la aplicacion —«/catastro/»— la pone
   `base.mjs` leyendola de `vite.config.ts`, que es quien la decide. Escribirla aqui
   seria el noveno literal que se queda viejo el dia que cambie (ver ese archivo). */
const BASE = baseDeLaApp(process.env.CATASTRO_BASE ?? 'http://localhost:5190');
const SALIDA = process.env.CATASTRO_CAPTURAS ?? '.capturas';
const soloModulo = process.argv[2]?.startsWith('--') ? null : process.argv[2];
const alto = Number(process.argv.find((a) => a.startsWith('--alto='))?.slice(7) ?? 1600);

await mkdir(SALIDA, { recursive: true });
const navegador = await chromium.launch();
const contexto = await navegador.newContext({ viewport: { width: 1440, height: alto } });
/* La aplicacion NO monta nada sin token: se va al emisor y vuelve. Aqui al otro lado
   hay un emisor de mentira, porque lo que este arnes mide son las pantallas y no la
   puerta —esa la mide `identidad.mjs`, sin nadie que la tape—. Ver `emisor.mjs`. */
await emisorDeMentira(contexto);
await contexto.addInitScript(CONTAR_PETICIONES);
/* Aqui habia un `CATASTRO_TOKEN` que se sembraba en `localStorage`. Se fue con la puerta de
   identidad: el token vive en memoria y se muere con la pestana, y sembrarlo en el
   almacenamiento seria pedirle a este arnes que hiciera justo lo que la prohibicion
   `token-en-almacenamiento` impide en `src/`. Quien lo da ahora es `emisor.mjs`. */
const pagina = await contexto.newPage();

const fallos = [];
const reloj = cronometroDeEsperas();
let vistas = 0;

for (const d of RECORRIDO) {
  if (soloModulo && d.modulo !== soloModulo) continue;
  const errores = [];
  /* Que el servidor NIEGUE una peticion no es que la interfaz este rota: es una
     respuesta, y la pantalla tiene que saber dibujarla. Lo que si se cuenta es
     cualquier otro error de consola. */
  const esRespuestaDelApi = (t) => /Failed to load resource/.test(t) && /40[13]|404|409|422|500|502/.test(t);
  const oyeConsola = (msg) => msg.type() === 'error' && !esRespuestaDelApi(msg.text()) && errores.push(msg.text());
  const oyePagina = (e) => errores.push('PAGEERROR: ' + e.message);
  pagina.on('console', oyeConsola);
  pagina.on('pageerror', oyePagina);

  const ruta = d.hash;
  await pagina.goto(`${BASE}/${ruta}`, { waitUntil: 'networkidle' });
  /* Hasta 700 ms —el plazo fijo de antes— a que la pantalla deje de cambiar y
     no quede ninguna lectura en vuelo. La captura sale de lo que se lea aqui, y
     leer antes de tiempo deja el `<main>` a medias, que es lo que la afirmacion
     de abajo pone en rojo. */
  await reloj.esperar(pagina, { tope: 700 });
  await pagina.screenshot({ path: `${SALIDA}/${d.archivo}.png` });
  pagina.off('console', oyeConsola);
  pagina.off('pageerror', oyePagina);
  vistas++;

  if (errores.length) fallos.push(`${ruta}\n  ${errores.join('\n  ')}`);

  /* Una pantalla que no dibuja nada bajo el armazon no falla: se queda en
     blanco, y eso no lo dice ningun error de consola. */
  const cuerpo = await pagina
    .locator('main')
    .innerText()
    .catch(() => '');
  if (cuerpo.trim().length < 40) fallos.push(`${ruta}\n  el <main> esta practicamente vacio`);
}

await navegador.close();

console.log(`${vistas} pantallas recorridas · capturas en ${SALIDA}/`);
console.log(reloj.resumen);

/* Y que el detector de reposo haya medido algo. Una espera que vuelve antes de
   poder haber observado un intervalo de quietud deja este arnes leyendo la
   pantalla a medias, en verde: es la unica forma en que cambiar una espera fija
   por una espera a una condicion puede perder una afirmacion. */
if (reloj.precoces) {
  console.error(reloj.queja);
  process.exit(2);
}

/**
 * Un recorrido que no mira nada no informa de nada.
 *
 * Medido antes de escribir esta guarda: `node verificaciones/mirar.mjs
 * modulo-que-no-existe` imprimia «0 pantallas recorridas» y «ninguna con errores
 * de consola ni con el cuerpo vacio», y **salia con 0** — sin abrir una sola
 * pagina y sin necesitar siquiera una vista previa levantada. El mismo desenlace
 * tendria un registro que se quedara sin hojas: el paso de CI se llama «Los
 * destinos se dibujan» y estaria en verde sin haber dibujado ninguno.
 *
 * `impedimentos.mjs` ya se protegia asi —«no se encontro NI UN control
 * impedido»— y esto es la misma exigencia por el otro eje.
 */
if (vistas === 0) {
  console.error(
    '\nNo se recorrio NI UNA pantalla, asi que este arnes no midio nada: pasaria en verde con el\n' +
      'defecto exacto que existe para atrapar. O el registro se quedo sin destinos, o el modulo que\n' +
      `se pidio en la linea de ordenes —«${soloModulo ?? '(ninguno)'}»— no es ninguno de los que hay.`,
  );
  process.exit(2);
}

if (fallos.length) {
  console.error(`\n${fallos.length} con problema:\n\n${fallos.join('\n\n')}`);
  process.exit(1);
}
console.log('ninguna con errores de consola ni con el cuerpo vacio');
