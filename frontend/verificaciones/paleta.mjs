/**
 * La paleta de comandos, solo con el teclado.
 *
 *   node verificaciones/paleta.mjs
 *
 * Existe porque esto ya se rompio una vez en el precedente: la paleta se abria con
 * Ctrl-K, se tecleaba para filtrar… y sin flechas ni Intro no habia forma de
 * elegir nada — el atajo llevaba a un callejon. Quien navega con teclado, y quien
 * no tiene raton a mano en una ventanilla, se quedaba fuera.
 *
 * Lo que comprueba, en orden:
 *
 *   1. Ctrl-K abre.
 *   2. Flecha abajo y arriba mueven el foco, y arriba vuelve donde estaba.
 *   3. Home y End van al primero y al ultimo.
 *   4. Al filtrar, el foco vuelve al primero. **Este caso hay que medirlo con
 *      VARIOS resultados**: con uno solo, acotar el indice al ultimo ya salva la
 *      situacion y la comprobacion pasaria con la guarda quitada.
 *   5. Intro abre la entrada ENFOCADA —no la primera de la lista anterior— y
 *      cierra la paleta.
 *   6. Esc cierra.
 *
 * Necesita la vista previa levantada y el Chromium de Playwright.
 */
import { chromium } from 'playwright-core';

const BASE = process.env.CATASTRO_BASE ?? 'http://localhost:5190';

const navegador = await chromium.launch();
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 900 } });
const pagina = await contexto.newPage();
await pagina.goto(`${BASE}/#/catastro/panel`, { waitUntil: 'domcontentloaded' });
await pagina.waitForTimeout(1000);

const fallos = [];
const enfocada = () => pagina.locator('[role=option][aria-selected=true]').first().innerText();

await pagina.keyboard.press('Control+k');
await pagina.waitForTimeout(300);
if (!(await pagina.locator('[role=listbox]').count())) fallos.push('Ctrl-K no abre la paleta');

const uno = await enfocada().catch(() => '');
await pagina.keyboard.press('ArrowDown');
await pagina.waitForTimeout(120);
const dos = await enfocada();
await pagina.keyboard.press('ArrowDown');
await pagina.waitForTimeout(120);
const tres = await enfocada();
await pagina.keyboard.press('ArrowUp');
await pagina.waitForTimeout(120);
const vuelta = await enfocada();
if (uno === dos || dos === tres) fallos.push('las flechas no mueven el foco');
if (vuelta !== dos) fallos.push('la flecha arriba no vuelve a la entrada anterior');

await pagina.keyboard.press('End');
await pagina.waitForTimeout(120);
const ultima = await enfocada();
const ultimaDeLaLista = await pagina.locator('[role=option]').last().innerText();
if (ultima !== ultimaDeLaLista) fallos.push(`End no lleva a la ultima entrada: se quedo en «${ultima}»`);
await pagina.keyboard.press('Home');
await pagina.waitForTimeout(120);
const primeraTrasHome = await enfocada();
const primeraDeLaLista = await pagina.locator('[role=option]').first().innerText();
if (primeraTrasHome !== primeraDeLaLista) fallos.push('Home no lleva a la primera entrada');

/* Filtrar a VARIOS: es el unico caso que distingue tener la guarda de no tenerla.
   Se baja primero, para que el foco NO este ya en el primero cuando se filtre. */
await pagina.keyboard.press('ArrowDown');
await pagina.waitForTimeout(120);
await pagina.keyboard.type('ficha');
await pagina.waitForTimeout(400);
const cuantos = await pagina.locator('[role=option]').count();
const primera = await pagina.locator('[role=option]').first().innerText();
const trasFiltrar = await enfocada();
if (cuantos < 2) fallos.push(`el filtro «ficha» deberia dejar varias entradas, dejo ${cuantos}`);
else if (trasFiltrar !== primera) {
  fallos.push(`al filtrar, el foco se queda en una fila que nadie eligio: «${trasFiltrar}»`);
}
for (let i = 0; i < 5; i++) await pagina.keyboard.press('Backspace');
await pagina.waitForTimeout(300);

/* Y que Intro abra la ENFOCADA, con el filtro puesto y tras haber movido. */
await pagina.keyboard.type('itse');
await pagina.waitForTimeout(400);
const elegida = await enfocada();
await pagina.keyboard.press('Enter');
await pagina.waitForTimeout(700);
const destino = new URL(pagina.url()).hash;
if (!/itse/i.test(destino)) fallos.push(`Intro no abrio «${elegida}»: fue a ${destino}`);
if (await pagina.locator('[role=listbox]').count()) fallos.push('la paleta no se cierra al elegir');

/* Y Esc cierra lo que este abierto. */
await pagina.keyboard.press('Control+k');
await pagina.waitForTimeout(250);
if (!(await pagina.locator('[role=listbox]').count())) fallos.push('Ctrl-K no vuelve a abrir la paleta');
await pagina.keyboard.press('Escape');
await pagina.waitForTimeout(250);
if (await pagina.locator('[role=listbox]').count()) fallos.push('Esc no cierra la paleta');

await navegador.close();

if (!fallos.length) {
  console.log('la paleta se opera solo con el teclado: abre, mueve, salta, filtra, elige y cierra');
  process.exit(0);
}
console.log('la paleta no se puede operar con el teclado:\n');
for (const f of fallos) console.log('  - ' + f);
process.exit(1);
