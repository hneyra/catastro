/**
 * Ningun control impedido sin decir por que.
 *
 *   node verificaciones/impedimentos.mjs [modulo]
 *
 * <h2>Que es lo que sale mal</h2>
 *
 * Un boton apagado es la unica pieza de una interfaz que **no puede explicarse
 * a si misma**: no se puede pulsar, asi que no hay forma de averiguar que le
 * falta. Quien lo mira concluye lo que se le ocurra —«no tengo permiso», «esto
 * esta roto», «hay que rellenar algo»— y ninguna de las tres tiene por que ser
 * la verdadera. Y no lo delata nada: la pantalla se dibuja entera, no hay error
 * de consola, y `mirar` la da por buena.
 *
 * Asi que la regla es: **impedido y con motivo, o no impedido**. El motivo va en
 * `title` o en el texto al que apunte `aria-describedby`, que son los dos sitios
 * de los que un navegador y un lector de pantalla saben sacarlo.
 *
 * <h2>Y se comprueba que la comprobacion mide algo</h2>
 *
 * Si el recorrido no encuentra **ni un** control impedido, esto sale con 2 y no
 * con 0. Un arnes que solo puede pasar es la trampa que #32 midio con las marcas
 * del paquete: se cumple solo, y su verde no dice nada del defecto que existe
 * para atrapar.
 *
 * Necesita una vista previa levantada y el Chromium de Playwright.
 */
import { chromium } from 'playwright-core';
import { leerRegistro } from './registro.mjs';
import { VISTAS, comprobarVistas, hashDe } from './vistas.mjs';

const { DESTINOS } = await leerRegistro('.registro-impedimentos');

const desajustes = comprobarVistas(DESTINOS);
if (desajustes.length) {
  console.error(`\n${desajustes.length} vista(s) no cuadran con el registro:\n\n  ${desajustes.join('\n  ')}`);
  process.exit(2);
}

const RECORRIDO = [
  ...DESTINOS.map((d) => ({ modulo: d.modulo, hash: `#/${d.modulo}/${d.hoja}` })),
  ...VISTAS.map((v) => ({ modulo: v.modulo, hash: hashDe(v) })),
];

const BASE = process.env.CATASTRO_BASE ?? 'http://localhost:5190';
const soloModulo = process.argv[2]?.startsWith('--') ? null : process.argv[2];

const navegador = await chromium.launch();
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 1400 } });
const pagina = await contexto.newPage();

const mudos = [];
let impedidos = 0;
let vistas = 0;

for (const d of RECORRIDO) {
  if (soloModulo && d.modulo !== soloModulo) continue;
  await pagina.goto(`${BASE}/${d.hash}`, { waitUntil: 'networkidle' });
  await pagina.waitForTimeout(600);
  vistas++;

  const hallados = await pagina.evaluate(() => {
    const seleccion =
      'button[disabled], button[aria-disabled="true"], input[disabled], select[disabled],' +
      ' textarea[disabled], [role="button"][aria-disabled="true"]';
    return [...document.querySelectorAll(seleccion)].map((el) => {
      const descrito = (el.getAttribute('aria-describedby') ?? '')
        .split(/\s+/)
        .filter((x) => x !== '')
        .map((x) => document.getElementById(x)?.textContent?.trim() ?? '')
        .join(' ')
        .trim();
      return {
        que: `${el.tagName.toLowerCase()} «${(el.textContent ?? el.getAttribute('aria-label') ?? '').trim().slice(0, 48)}»`,
        titulo: (el.getAttribute('title') ?? '').trim(),
        descrito,
      };
    });
  });

  impedidos += hallados.length;
  for (const h of hallados) {
    if (h.titulo === '' && h.descrito === '') mudos.push({ ruta: d.hash, que: h.que });
  }
}

await navegador.close();

console.log(`${vistas} pantallas recorridas · ${impedidos} control(es) impedido(s)`);

if (impedidos === 0) {
  console.error(
    '\nNo se encontro NI UN control impedido en todo el recorrido, asi que esta comprobacion no midio\n' +
      'nada: pasaria en verde con el defecto exacto que existe para atrapar. O el recorrido no llega a\n' +
      'donde estan, o la interfaz dejo de tenerlos y este arnes sobra.',
  );
  process.exit(2);
}

if (mudos.length) {
  console.error(`\n${mudos.length} control(es) impedido(s) sin decir por que:\n`);
  for (const m of mudos) console.error(`  ${m.ruta.padEnd(46)} ${m.que}`);
  console.error(
    '\nUn control apagado no se puede pulsar, asi que no hay forma de averiguar que le falta: el motivo\n' +
      'va en «title» o en el texto al que apunte «aria-describedby».',
  );
  process.exit(1);
}

console.log('todos dicen por que lo estan');
