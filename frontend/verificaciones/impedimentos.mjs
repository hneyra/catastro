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
import { CONTAR_PETICIONES, cronometroDeEsperas } from './reposo.mjs';
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

/**
 * Los dos conjuntos que este arnes cuenta, escritos UNA vez.
 *
 * Los lee el recorrido y los relee la calibracion, y por eso no se pueden
 * repetir: una copia dejaria la calibracion contando otra cosa que lo que se
 * publica, y entonces cuadrarian siempre.
 */
const IMPEDIDOS =
  'button[disabled], button[aria-disabled="true"], input[disabled], select[disabled],' +
  ' textarea[disabled], [role="button"][aria-disabled="true"]';
const CONTROLES_DE_MAIN = 'main button, main a[href], main input, main select';

/**
 * La espera adaptativa, calibrada CONTRA SI MISMA.
 *
 * <h2>Que agujero tapa</h2>
 *
 * Desde #86 la espera de este recorrido no es un plazo fijo sino «hasta 600 ms a
 * que la pantalla deje de cambiar». Si esa condicion se degrada, este arnes lee
 * la pantalla a medias y **cuenta de menos, en verde**: medido rompiendo la
 * huella de `reposo.mjs` para que ignore las peticiones, sale `exit 0` diciendo
 * 866, 879, 902, 911, 920 o 956 controles segun la corrida y la maquina, donde
 * el arbol sano dice **965**. La perdida no solo es silenciosa: es **no
 * determinista**, y quien revise no tiene con que compararla.
 *
 * Y escribir 965 aqui no vale: seria un segundo sitio con la misma verdad —la
 * forma de defecto que C-17 encontro cinco veces— que ademas habria que tocar
 * cada vez que entra una pantalla legitima, o sea que se tocaria sin pensar.
 *
 * <h2>Lo que se hace en su lugar</h2>
 *
 * En cada pantalla, DESPUES de contar, se vigila el DOM {@link VIGILANCIA} ms
 * con un `MutationObserver`. Si nada se movio, la espera acerto y no se paga
 * nada mas. **Si algo se movio, la espera volvio pronto**: entonces se esperan
 * {@link MARGEN} ms —un plazo fijo, que NO usa el detector que se esta
 * juzgando— y se vuelven a contar los mismos dos conjuntos. Si la cuenta
 * cambio, sale con 2.
 *
 * No se compara contra ningun numero escrito: se compara **la espera consigo
 * misma con mas tiempo**, que es la forma que `ejercicios.mjs` usa con el reloj
 * y `territorio.mjs` con el JSON que la pagina recibio.
 *
 * <h2>Por que las 51 y no una muestra, medido</h2>
 *
 * Porque **cual pantalla pierde depende de como caiga la carrera**. La primera
 * version calibraba cuatro maestro-detalle escritas a mano —las que perdian
 * controles con una degradacion concreta— y con la huella rota de otra manera
 * las que perdieron fueron `#/catastro/predios/1` (11 controles contra 42) y
 * `#/fiscalizacion/candidatos/1` (1 contra 7): **ninguna de las cuatro**, y el
 * arnes salio con 0. La segunda version tomaba las ocho de espera mas corta,
 * derivadas de la corrida, y cazo el mismo defecto **en 2 de 3 corridas**. Una
 * guarda que acierta dos de cada tres veces no es una guarda.
 *
 * Vigilarlas todas sale casi gratis porque **en el arbol sano no se mueve
 * ninguna**: medido, **0 de 51** tienen una sola mutacion en los 150 ms
 * siguientes a la espera, asi que el margen no se paga nunca y el coste es solo
 * la vigilancia — 7,6 s, o **0,15 s por pantalla nueva**, contra los 0,6 s que
 * costaria un margen fijo en todas.
 *
 * <h2>Y el observador se comprueba a si mismo</h2>
 *
 * «No se movio nada» y «el observador no mira» se ven igual —y en el arbol sano
 * lo primero es cierto en las 51—, asi que al instalarlo se hace **una mutacion
 * a proposito** y se exige verla. Sin eso, un observador roto dejaria esta
 * calibracion cumpliendose sola en todas las pantallas.
 */
const VIGILANCIA = 150;
const MARGEN = 1500;

const navegador = await chromium.launch();
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 1400 } });
await contexto.addInitScript(CONTAR_PETICIONES);
const pagina = await contexto.newPage();

const mudos = [];
const cortados = [];
const descalibradas = [];
/** Pantallas en las que el observador no vio ni la mutacion que se hizo aposta. */
const sinObservador = [];
const reloj = cronometroDeEsperas();
let impedidos = 0;
let controles = 0;
let vistas = 0;
let vigiladas = 0;
let conMovimiento = 0;

for (const d of RECORRIDO) {
  if (soloModulo && d.modulo !== soloModulo) continue;
  await pagina.goto(`${BASE}/${d.hash}`, { waitUntil: 'networkidle' });
  /* Hasta 600 ms —el plazo fijo de antes— a que la pantalla deje de cambiar.
     Leer antes de tiempo no puede pasar en verde: bajaria el numero de controles
     que este arnes PUBLICA, que es la cifra que el AC-2 de #86 exige que no baje. */
  await reloj.esperar(pagina, { tope: 600 });
  vistas++;

  const hallados = await pagina.evaluate((seleccion) => {
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
  }, IMPEDIDOS);

  impedidos += hallados.length;
  for (const h of hallados) {
    if (h.titulo === '' && h.descrito === '') mudos.push({ ruta: d.hash, que: h.que });
  }

  /* Y los que no se ven. Se recorre `<main>` y no la pagina entera: la barra de
     pestanas del armazon se desplaza a proposito. */
  const recorte = await pagina.evaluate((seleccion) => {
    const sueltos = [...document.querySelectorAll(seleccion)];
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
  }, CONTROLES_DE_MAIN);
  controles += recorte.mirados;
  for (const c of recorte.cortados) cortados.push({ ruta: d.hash, ...c });

  /* Y la espera, contra si misma: se vigila el DOM y solo se paga el margen
     donde algo se movio despues de que la espera dijera que ya no se movia. */
  await pagina.evaluate(() => {
    globalThis.__obs?.disconnect();
    globalThis.__mutaciones = 0;
    globalThis.__obs = new MutationObserver((lote) => (globalThis.__mutaciones += lote.length));
    globalThis.__obs.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      characterData: true,
    });
    /* El contraste del propio observador: una mutacion a proposito, para que
       «no se movio nada» no se confunda con «no estaba mirando». */
    document.body.setAttribute('data-calibracion-de-impedimentos', '1');
  });
  await pagina.waitForTimeout(VIGILANCIA);
  const vigilancia = await pagina.evaluate(() => {
    globalThis.__obs.disconnect();
    document.body.removeAttribute('data-calibracion-de-impedimentos');
    return globalThis.__mutaciones;
  });
  vigiladas++;
  if (vigilancia === 0) sinObservador.push(d.hash);
  else if (vigilancia > 1) {
    conMovimiento++;
    await pagina.waitForTimeout(MARGEN - VIGILANCIA);
    const conMasTiempo = await pagina.evaluate(
      ([a, b]) => ({
        impedidos: document.querySelectorAll(a).length,
        controles: document.querySelectorAll(b).length,
      }),
      [IMPEDIDOS, CONTROLES_DE_MAIN],
    );
    if (conMasTiempo.impedidos !== hallados.length || conMasTiempo.controles !== recorte.mirados) {
      descalibradas.push({
        ruta: d.hash,
        antes: `${hallados.length} impedido(s) y ${recorte.mirados} control(es)`,
        despues: `${conMasTiempo.impedidos} y ${conMasTiempo.controles}`,
      });
    }
  }
}

await navegador.close();

console.log(
  `${vistas} pantallas recorridas · ${impedidos} control(es) impedido(s) · ` +
    `${controles} control(es) de «main» medidos a la anchura del artboard`,
);
console.log(
  `${reloj.resumen}\n${vigiladas} pantalla(s) vigiladas ${VIGILANCIA} ms despues de contarlas · ` +
    `${conMovimiento} se movieron y se recontaron con ${MARGEN} ms de margen`,
);

/* Y que el detector de reposo haya medido algo. Una espera que vuelve antes de
   poder haber observado un intervalo de quietud deja este arnes leyendo la
   pantalla a medias, en verde: es la unica forma en que cambiar una espera fija
   por una espera a una condicion puede perder una afirmacion. */
if (reloj.precoces) {
  console.error(reloj.queja);
  process.exit(2);
}

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

/**
 * Y que la vigilancia haya mirado, y que el observador funcione.
 *
 * «No se movio nada» y «no estaba mirando» se ven igual: en el arbol sano las 51
 * pantallas dicen lo primero, asi que sin este contraste un observador roto
 * dejaria la calibracion cumpliendose sola. Por eso al instalarlo se hace una
 * mutacion a proposito y aqui se exige que la haya visto.
 */
if (vigiladas === 0) {
  console.error(
    '\nNo se vigilo NI UNA pantalla despues de contarla, asi que nadie comprobo que la espera\n' +
      'adaptativa no vuelva pronto: con el detector degradado este arnes cuenta de menos y sale en\n' +
      'VERDE, con un numero que ademas cambia de una corrida a otra.',
  );
  process.exit(2);
}

if (sinObservador.length) {
  console.error(
    `\nEn ${sinObservador.length} de ${vigiladas} pantalla(s) el observador no vio ni la mutacion que este arnes\n` +
      'hace a proposito al instalarlo, asi que no estaba mirando y su silencio no dice nada. La\n' +
      `calibracion de la espera se estaria cumpliendo sola ahi. La primera: ${sinObservador[0]}`,
  );
  process.exit(2);
}

if (descalibradas.length) {
  console.error(
    `\n${descalibradas.length} de ${vigiladas} pantalla(s) cuentan distinto con ${MARGEN} ms mas de margen:\n`,
  );
  for (const c of descalibradas) {
    console.error(`  ${c.ruta.padEnd(44)} con la espera: ${c.antes}   ·   con mas tiempo: ${c.despues}`);
  }
  console.error(
    '\nLa espera a que la pantalla se asiente esta volviendo ANTES de que termine de dibujarse, asi\n' +
      'que este arnes lee la pantalla a medias y cuenta de menos — en verde, y con un numero que\n' +
      'cambia de una corrida a otra. No es que la interfaz este mal: es que esto no la ha medido.\n' +
      'Lo que hay que mirar es la huella de `reposo.mjs`, que es lo que decide cuando la pantalla\n' +
      'esta quieta.',
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
