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
 * <h2>Y la otra mitad: un control CORTADO por el borde</h2>
 *
 * Un boton apagado no se puede pulsar y **si** se puede ver: quien mira sabe al
 * menos que esta ahi. Uno que queda fuera del ancho visible de su contenedor no
 * se puede ni lo uno ni lo otro, asi que no se concluye nada — ni siquiera que
 * haya algo. Y este arnes no lo cazaba, porque el control **no esta impedido**.
 *
 * Se mide a la anchura del ARTBOARD, que es la del recorrido: 1 440 px es la
 * medida del diseno que se porta, asi que un control que no cabe ahi no es
 * «responsive pendiente», es que no cabe en el diseno del que sale. Ocurrio de
 * verdad en #71: la tabla de hallazgos llego a diez columnas, se fue a 1 166 px
 * dentro de una caja de 1 150, y «Adjuntar evidencia» quedaba cortado por el
 * borde derecho.
 *
 * **Solo se miran los controles de `<main>`.** La barra de pestanas del armazon
 * es un desplazador horizontal a proposito —las pestanas se acumulan y se
 * empujan—, y medirla pondria rojo el recorrido entero por su propio diseno.
 *
 * Y se mira el CONTROL y no la tabla: una tabla ancha que se desplaza dentro de
 * su `overflow-x` es lo que el sistema de diseno hace con el contenido ancho, y
 * un dato al que hay que desplazarse sigue siendo legible. Un boton al que hay
 * que desplazarse **no se sabe que existe**.
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
const cortados = [];
let impedidos = 0;
let controles = 0;
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

  /* Y los que no se ven. Se recorre `<main>` y no la pagina entera: la barra de
     pestanas del armazon se desplaza a proposito. */
  const recorte = await pagina.evaluate(() => {
    const sueltos = [...document.querySelectorAll('main button, main a[href], main input, main select')];
    const cortados = [];
    for (const el of sueltos) {
      const suyo = el.getBoundingClientRect();
      if (suyo.width === 0 && suyo.height === 0) continue;
      let caja = el.parentElement;
      while (caja && caja.tagName !== 'MAIN') {
        const como = getComputedStyle(caja).overflowX;
        if (como === 'auto' || como === 'scroll') break;
        caja = caja.parentElement;
      }
      const limite =
        caja && caja.tagName !== 'MAIN'
          ? caja.getBoundingClientRect()
          : { left: 0, right: document.documentElement.clientWidth };
      if (suyo.right > limite.right + 0.5 || suyo.left < limite.left - 0.5) {
        cortados.push({
          que: (el.textContent ?? '').trim().slice(0, 40) || el.getAttribute('aria-label') || el.tagName,
          borde: Math.round(suyo.right),
          hasta: Math.round(limite.right),
        });
      }
    }
    return { mirados: sueltos.length, cortados };
  });
  controles += recorte.mirados;
  for (const c of recorte.cortados) cortados.push({ ruta: d.hash, ...c });
}

await navegador.close();

console.log(
  `${vistas} pantallas recorridas · ${impedidos} control(es) impedido(s) · ` +
    `${controles} control(es) de «main» medidos a la anchura del artboard`,
);

if (impedidos === 0) {
  console.error(
    '\nNo se encontro NI UN control impedido en todo el recorrido, asi que esta comprobacion no midio\n' +
      'nada: pasaria en verde con el defecto exacto que existe para atrapar. O el recorrido no llega a\n' +
      'donde estan, o la interfaz dejo de tenerlos y este arnes sobra.',
  );
  process.exit(2);
}

/* La misma exigencia que la de abajo, por el eje del recorte: sin ni un control
   que medir, «ninguno queda cortado» es cierto sobre el conjunto vacio. */
if (controles === 0) {
  console.error(
    '\nNo se midio NI UN control dentro de «main» en todo el recorrido, asi que la mitad de este arnes\n' +
      'que mira si algo queda cortado por el borde no comprobo nada: pasaria en verde con el defecto\n' +
      'exacto que existe para atrapar.',
  );
  process.exit(2);
}

if (cortados.length) {
  console.error(`\n${cortados.length} control(es) fuera del ancho visible de su contenedor:\n`);
  for (const c of cortados) {
    console.error(`  ${c.ruta.padEnd(46)} «${c.que}» llega a ${c.borde} px y la caja acaba en ${c.hasta}`);
  }
  console.error(
    '\nSe mide a 1 440 px, que es la anchura del artboard: lo que no cabe ahi no es «responsive\n' +
      'pendiente», es que no cabe en el diseno del que sale. Un control cortado por el borde esta un\n' +
      'escalon por debajo de uno apagado sin motivo: no se puede pulsar Y no se puede ver, asi que\n' +
      'quien mira no concluye nada — ni siquiera que hay algo ahi.',
  );
  process.exit(1);
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
