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
import { VISTAS, comprobarVistas, hashDe } from './vistas.mjs';

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

const BASE = process.env.CATASTRO_BASE ?? 'http://localhost:5190';
const SALIDA = process.env.CATASTRO_CAPTURAS ?? '.capturas';
const soloModulo = process.argv[2]?.startsWith('--') ? null : process.argv[2];
const alto = Number(process.argv.find((a) => a.startsWith('--alto='))?.slice(7) ?? 1600);

await mkdir(SALIDA, { recursive: true });
const navegador = await chromium.launch();
const contexto = await navegador.newContext({ viewport: { width: 1440, height: alto } });
const TOKEN = process.env.CATASTRO_TOKEN;
if (TOKEN) await contexto.addInitScript((t) => localStorage.setItem('catastro.token', t), TOKEN);
const pagina = await contexto.newPage();

const fallos = [];
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
  await pagina.waitForTimeout(700);
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
if (fallos.length) {
  console.error(`\n${fallos.length} con problema:\n\n${fallos.join('\n\n')}`);
  process.exit(1);
}
console.log('ninguna con errores de consola ni con el cuerpo vacio');
