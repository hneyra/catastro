/**
 * Recorre cada destino en un navegador de verdad y guarda una captura.
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
 * aqui: una lista copiada se queda vieja sin ruido.
 *
 * Necesita una vista previa levantada (`yarn dev` o `vite preview`) y el Chromium
 * de Playwright.
 */
import { chromium } from 'playwright-core';
import { mkdir } from 'node:fs/promises';
import { leerRegistro } from './registro.mjs';

const { DESTINOS } = await leerRegistro('.registro-mirar');

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

for (const d of DESTINOS) {
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

  const ruta = `#/${d.modulo}/${d.hoja}`;
  await pagina.goto(`${BASE}/${ruta}`, { waitUntil: 'networkidle' });
  await pagina.waitForTimeout(700);
  await pagina.screenshot({ path: `${SALIDA}/${d.modulo}-${d.hoja}.png` });
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
