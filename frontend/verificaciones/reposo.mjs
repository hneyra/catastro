/**
 * Esperar A QUE LA PANTALLA SE ASIENTE, con el plazo fijo de antes como TOPE.
 *
 * <h2>Que sale mal</h2>
 *
 * Los cuatro arneses que recorren pantallas —`mirar`, `impedimentos`, `sin-red`
 * y `errores`— esperaban con `page.waitForTimeout(N)`: un plazo fijo, elegido
 * para el peor caso, que se paga ENTERO en todos los demas. Medido en este
 * puesto con los cuatro instrumentados, antes de tocar una linea:
 *
 * ```
 * arnes          total   navegar   esperas fijas   trabajo de verdad
 * errores       122,7 s     1,4 s        117,8 s   3,5 s
 * sin-red        58,7 s    0,08 s         56,2 s   2,4 s   (397 secciones: 1,9 s)
 * mirar          39,9 s     0,6 s          35,8 s   3,5 s   (51 capturas)
 * impedimentos   31,6 s     0,6 s          30,7 s   0,2 s   (51 recorridos del DOM)
 * ------------------------------------------------------------------------------
 * suma          252,9 s     2,7 s         240,5 s   9,6 s
 * ```
 *
 * **El 95 % del reloj de los cuatro es dormir**, y las 222 navegaciones que las
 * dos primeras direcciones de #86 querian ahorrar suman **2,7 s, el 1,1 %**.
 *
 * <h2>Lo que este archivo hace, y lo que NO cambia</h2>
 *
 * Sustituye «duerme N» por «espera HASTA N a que la pantalla deje de cambiar».
 * Dos propiedades, y las dos importan:
 *
 *   1. **El tope es exactamente el plazo que sustituye.** Una pantalla que
 *      necesitaba los 1 100 ms sigue teniendolos: el peor caso es identico al de
 *      antes, asi que ningun runner lento empeora.
 *   2. **La condicion NO es la afirmacion.** Se espera a que `document.body`
 *      deje de cambiar —y, donde el arnes ya lo sabe por otro motivo, a que la
 *      peticion rota haya sido contestada—, nunca a que salga el titulo, la cifra
 *      o el control que luego se comprueba. Un arnes que espera a lo que va a
 *      afirmar no puede ponerse rojo, que es el defecto que esta serie persigue.
 *
 * <h2>Por que leer PRONTO solo puede ir hacia el rojo</h2>
 *
 * Es lo que hace que este cambio sea seguro, y esta medido por arnes:
 *
 *   · `errores` afirma que la region DICE el titulo del codigo, el mensaje, la
 *     incidencia y los detalles. Leida antes de tiempo, la region trae el estado
 *     de carga y **falta todo eso**: sale rojo. Y si dos escenarios se leyeran a
 *     medias, saldrian byte a byte IGUALES, que es la comprobacion 6.
 *   · `sin-red` exige que `<main>` tenga texto y que NOMBRE la ruta del backend
 *     que no pudo leer. Las dos cosas aparecen al dibujarse el fallo, o sea
 *     DESPUES; leer antes da «muda» y «anonima», en rojo.
 *   · `impedimentos` PUBLICA cuantos controles midio —48 impedidos y 965 de
 *     `main`— y `sin-red` cuantas secciones plegadas abrio —397—. Leer antes
 *     baja esas cifras, y son justamente las que el AC-2 de #86 exige que no
 *     bajen. **El contador es la guarda.**
 *
 * Y la direccion peligrosa —que el detector se degrade y devuelva al instante—
 * **no la caza ninguna de esas tres**, y por eso hay una guarda aparte. Medido:
 * con el sondeo devolviendo `true` en la primera lectura, `impedimentos` seguia
 * diciendo 965 y `errores` sus 60 en verde, porque la puerta de las peticiones
 * aguanta sola. Lo que lo caza es {@link cronometroDeEsperas}: una espera que
 * vuelve antes de {@link MUESTRAS} sondeos no ha podido observar un solo
 * intervalo de quietud, y el arnes sale con **2** —«no se pudo comprobar»— en
 * lugar de con 0.
 *
 * <h2>El reposo se cuenta en MUESTRAS y no en milisegundos</h2>
 *
 * Porque `errores` corre con el reloj del navegador FIJADO (`clock.setFixedTime`,
 * #48), asi que `performance.now()` dentro de la pagina no avanza y un «lleva
 * 150 ms quieto» no se puede escribir ahi. Se piden N lecturas consecutivas
 * iguales al ritmo de sondeo que Playwright garantiza, que es tiempo real y no
 * depende de ningun reloj de la pagina.
 */

/**
 * El sondeo, dentro de la pagina.
 *
 * `sello` reinicia la cuenta en cada espera: sin el, la segunda llamada sobre la
 * misma pagina encontraria la huella ya igual a la de la anterior y saldria en
 * la primera lectura, sin haber observado un solo intervalo de quietud.
 *
 * La huella son dos cosas, y las dos estan medidas:
 *
 *   · **el texto de `body`** — lo que se lee;
 *   · **cuantas peticiones se han hecho y cuantas siguen en vuelo** — que es el
 *     `networkidle` de verdad de esta interfaz. Ver {@link CONTAR_PETICIONES}.
 *
 * <h2>Y una tercera que se escribio y se retiro AL MEDIRLA</h2>
 *
 * `document.querySelectorAll('*').length`, con el argumento de que un `<input>`
 * —o un boton que solo tiene `aria-label`— aparece sin cambiar una letra del
 * texto. Suena bien y **no cambia ni una de las cuatro cifras**: quitandolo de
 * la huella, `mirar` sigue en 51, `impedimentos` en 48 y 965, `sin-red` en 397 y
 * `errores` en sus 60 renders verdes. Lo unico que hacia era **suavizar el
 * sintoma cuando falta la otra mitad**: sin el conteo de peticiones, con el da
 * 928 controles y sin el 902 — o sea que su unico efecto medible era hacer MENOS
 * visible que la mitad que si trabaja estuviera rota. Es la leccion de
 * `zonificacion_vigencia_ix` (#4) por el eje de un detector: lo que no cambia
 * ningun desenlace no se ha demostrado que proteja nada.
 *
 * Vuelve el dia que haya una pantalla que lo ponga rojo, y esa pantalla sera su
 * muestra.
 */
const QUIETO = ([sello, muestras]) => {
  const g = (globalThis.__reposo ||= {});
  if (g.sello !== sello) {
    g.sello = sello;
    g.huella = null;
    g.iguales = 0;
  }
  /* Con algo en vuelo no hay reposo que contar: la huella se anula, la cuenta se
     reinicia y se vuelve a empezar cuando la peticion conteste. */
  const huella =
    (globalThis.__enVuelo ?? 0) > 0
      ? null
      : `${globalThis.__hechas ?? 0}|${document.body ? document.body.innerText : ''}`;
  if (huella === null || huella !== g.huella) {
    g.huella = huella;
    g.iguales = 0;
    return false;
  }
  g.iguales += 1;
  return g.iguales >= muestras;
};

/**
 * El `networkidle` de una interfaz cuya red es una FUNCION.
 *
 * `waitUntil: 'networkidle'` no vale aqui y esta medido por que: con el proxy de
 * datos encendido **no hay ni una peticion de red** —el proxy SUSTITUYE `fetch`
 * y contesta desde la propia pagina, con 120-320 ms de latencia simulada—, asi
 * que Chromium declara la red en reposo mientras las lecturas siguen en curso.
 * Y las hojas maestro-detalle encadenan: la lista contesta, se dibuja, y **eso**
 * dispara la lectura del detalle. Entre las dos hay una ventana en la que la
 * pantalla esta quieta y todavia no ha terminado.
 *
 * Se instala con `addInitScript` ANTES de que la pagina cargue, con el mismo
 * `get`/`set` que `errores.mjs` documenta: el proxy se instala encima, el `set`
 * lo captura y el `get` lo devuelve envuelto.
 */
export const CONTAR_PETICIONES = () => {
  let enVuelo = 0;
  let hechas = 0;
  let actual = globalThis.fetch;
  const envolver = (delegar) =>
    async function (...args) {
      enVuelo += 1;
      hechas += 1;
      try {
        return await delegar.apply(this, args);
      } finally {
        enVuelo -= 1;
      }
    };
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    get: () => envolver(actual),
    set: (nueva) => {
      actual = nueva;
    },
  });
  Object.defineProperty(globalThis, '__enVuelo', { configurable: true, get: () => enVuelo });
  Object.defineProperty(globalThis, '__hechas', { configurable: true, get: () => hechas });
};

/** Cada cuanto se relee el texto de la pagina, en ms de reloj de verdad. */
export const INTERVALO = 60;
/** Lecturas consecutivas iguales que hacen falta para declarar el reposo. */
export const MUESTRAS = 3;

/**
 * Espera a que la pantalla se asiente, como mucho `tope` ms.
 *
 * @param pagina la pagina de Playwright
 * @param tope el plazo fijo que esta espera sustituye, en ms. Es un TOPE: nunca
 *   se espera mas, y por eso el peor caso es el de antes.
 * @param puerta funcion de pagina que tiene que ser cierta ANTES de empezar a
 *   contar el reposo. Se usa donde el arnes ya sabe por otro motivo que algo
 *   tiene que haber pasado —`errores` cuenta las peticiones que rompio—, y
 *   nunca para esperar a lo que se va a afirmar.
 * @param arg el argumento de `puerta`
 * @returns `{ms, agotado}` — lo que costo y si se agoto el tope. Los arneses
 *   publican cuantas se agotaron: si un dia se agotan todas, el detector dejo de
 *   funcionar y el reloj vuelve al de antes, que es ruidoso pero no falso.
 */
export async function enReposo(pagina, { tope, puerta = null, arg = undefined, muestras = MUESTRAS } = {}) {
  const arranque = Date.now();
  const restante = () => Math.max(20, tope - (Date.now() - arranque));
  let agotado = false;

  if (puerta) {
    await pagina
      .waitForFunction(puerta, arg, { timeout: restante(), polling: INTERVALO })
      .catch(() => (agotado = true));
  }
  await pagina
    .waitForFunction(QUIETO, [`${arranque}-${Math.random()}`, muestras], {
      timeout: restante(),
      polling: INTERVALO,
    })
    .catch(() => (agotado = true));

  return { ms: Date.now() - arranque, agotado };
}

/**
 * Lleva la cuenta de lo que costaron las esperas de un arnes, para publicarla.
 *
 * No es decoracion: es lo que hace que la proxima regresion de reloj se lea en
 * la salida del propio arnes en vez de tener que instrumentarlo otra vez.
 */
export function cronometroDeEsperas() {
  let ms = 0;
  let cuantas = 0;
  let agotadas = 0;
  let precoces = 0;
  let elMasCorto = Infinity;
  return {
    async esperar(pagina, opciones) {
      const r = await enReposo(pagina, opciones);
      ms += r.ms;
      cuantas += 1;
      if (r.agotado) agotadas += 1;
      if (r.ms < minimo(opciones?.muestras ?? MUESTRAS)) precoces += 1;
      elMasCorto = Math.min(elMasCorto, r.ms);
      return r;
    },
    get resumen() {
      const media = cuantas ? Math.round(ms / cuantas) : 0;
      return (
        `${cuantas} espera(s) a que la pantalla se asentara: ${(ms / 1000).toFixed(1)} s en total, ` +
        `${media} ms de media, ${agotadas} agotaron su tope`
      );
    },
    /** Cuantas volvieron antes de que se pudiera haber observado un solo reposo. */
    get precoces() {
      return precoces;
    },
    /**
     * Lo que hay que decir cuando alguna volvio antes de tiempo, y por que sale
     * con 2 y no con 1: no es que la pantalla este mal, es que este arnes no
     * llego a mirarla.
     */
    get queja() {
      return (
        `\n${precoces} de ${cuantas} espera(s) volvieron antes de los ${minimo(MUESTRAS)} ms que hacen falta para\n` +
        `observar un solo reposo —la mas corta, ${elMasCorto} ms—, asi que el detector de «la pantalla dejo de\n` +
        'cambiar» no esta midiendo: devuelve al instante y este arnes lee la pantalla A MEDIAS. Con eso pasaria\n' +
        'en verde toda afirmacion sobre lo que todavia no se ha dibujado, que es el defecto exacto que sustituir\n' +
        'una espera fija por una espera a una condicion podria introducir.'
      );
    },
    get agotadas() {
      return agotadas;
    },
    get cuantas() {
      return cuantas;
    },
  };
}

/**
 * Lo minimo que puede tardar una espera honrada, en ms de reloj de verdad.
 *
 * `waitForFunction` evalua una vez y despues cada {@link INTERVALO} ms, asi que
 * declarar el reposo exige {@link MUESTRAS} sondeos y por tanto ese tiempo. Una
 * espera que vuelve antes no ha podido observar ni un intervalo de quietud.
 */
function minimo(muestras) {
  return muestras * INTERVALO;
}
