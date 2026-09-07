/**
 * El catalogo territorial se puede MANTENER, y lo que se teclea llega entero.
 *
 *   CATASTRO_BASE=http://localhost:5210 node verificaciones/territorio.mjs [acto]
 *
 * <h2>Que existe para impedir, y por que no lo ve ningun otro arnes</h2>
 *
 * Las cinco escrituras de `SectorController` y `ViaController` fallan de tres
 * maneras que **se ven exactamente igual de bien cuando estan mal**, y ninguna
 * de las tres la caza `mirar`, `impedimentos` ni `errores`:
 *
 *   1. **Un control que el servidor descarta.** El `activo` del alta de un
 *      sector y el `codigo` del cuerpo de un `PUT` **estan en el `record`** —asi
 *      que el punto 9 de `rutas.mjs` los da por buenos, y con razon: viajan— y
 *      el controlador los lee y los tira. Ofrecerlos como campo es lo peor de
 *      los dos casos: el tecnico ve el control, lo rellena, guarda, y el
 *      servidor contesta `201`. No hay error, no hay aviso, y el dato no acaba
 *      en ningun sitio. Es el defecto que #34 midio con `documentoDeOrigen`, por
 *      el otro lado.
 *   2. **Un campo que se ofrece y no viaja**, que es el mismo defecto por el eje
 *      contrario: el control existe, se rellena y la peticion sale sin el.
 *   3. **Un conteo inventado.** `SectorResource` trae `manzanas`, `predios` y
 *      `lotes` **nulos en toda respuesta de escritura** y con cifra en el
 *      listado. Un `0` pintado ahi diria «no tiene ninguna manzana», que al
 *      corregir un sector con cuarenta es sencillamente falso — y se pinta
 *      **exactamente igual** que una cifra leida.
 *
 * <h2>Como se mide, y que haria pasar esto en verde con el defecto puesto</h2>
 *
 * Preguntar «¿sale un campo `activo`?» no vale: se cumple sola el dia que el
 * campo se llame de otra manera. Preguntar «¿la pantalla dice «—»?» tampoco: una
 * pantalla que dijera «—» en un sitio y `0` en otro pasaria igual.
 *
 * Asi que se mide **lo que de verdad viaja y lo que de verdad vuelve**:
 *
 *   · Se rellena **todo control editable** del formulario con una marca propia,
 *     se envia, y se exige que **cada marca aparezca en el cuerpo** que salio por
 *     `fetch`. Un control que no viaja deja su marca fuera y sale nombrado; y no
 *     hace falta ninguna tabla de «este rotulo es este campo», que seria la copia
 *     de siempre.
 *   · Se exige que el cuerpo **no** traiga las claves que
 *     `LO_QUE_EL_SERVIDOR_DESCARTA` declara para esa operacion, con el motivo que
 *     el backend escribe. Las dos mitades hacen falta y se rompen en direcciones
 *     opuestas: la primera caza el campo que se ofrece y no llega, la segunda el
 *     que llega y se tira.
 *   · Y **toda cifra del panel de «lo que el servidor contesto» tiene que estar
 *     en el JSON que la pagina recibio**, como hace `ficha.mjs` con las obras
 *     complementarias. Un cero de relleno sale en rojo con el numero que sobra.
 *
 * <h2>Y dos cosas mas que solo se ven CONDUCIENDO</h2>
 *
 *   · **Lo que no se deshace no escribe hasta que se confirma aparte.** Se pulsa
 *     el primario una vez y se cuenta: **cero** peticiones. Se confirma y se
 *     cuenta otra vez: una. Sin las dos cuentas, «hay un panel de confirmacion»
 *     se cumple con un panel decorativo que ya escribio.
 *   · **La lista siguiente trae lo que se acaba de escribir.** Es la decision de
 *     `src/simulado/catalogo.ts` hecha medida: sin memoria en el proxy, el alta
 *     contesta `201` y el arbol vuelve igual que antes, que es como se ve un alta
 *     que no se guardo.
 *
 * <h2>Y si no midio nada, sale con 2</h2>
 *
 * Sin un cuerpo capturado, sin una marca comprobada o sin una cifra comparada,
 * todas las afirmaciones de arriba son ciertas sobre el conjunto vacio. Y ademas
 * se exige que **toda** operacion de `LO_QUE_EL_SERVIDOR_DESCARTA` la ejercite
 * este recorrido: una declaracion que nadie ejercita es una exencion muerta, que
 * es la forma de defecto que este repositorio lleva encontrada por cuatro ejes.
 *
 * Necesita una vista previa levantada y el Chromium de Playwright.
 */
import { chromium } from 'playwright-core';
import { leerModulo } from './registro.mjs';

const BASE = process.env.CATASTRO_BASE ?? 'http://localhost:5190';

const { LO_QUE_EL_SERVIDOR_DESCARTA, RUTAS } = await leerModulo('src/api/catastro.ts', '.modulo-territorio-api');
const { RAIZ } = await leerModulo('src/api/cliente.ts', '.modulo-territorio-cliente');
const { ACTOS_DEL_TERRITORIO, CAMPOS_DEL_TERRITORIO, NOTAS_DE_LOS_ACTOS_DEL_TERRITORIO, MOTIVOS, QUE_HACER } =
  await leerModulo('src/datos/catastro.ts', '.modulo-territorio-datos');
const { ACTO, IRREVERSIBLE, LA_OBSERVACION } = await leerModulo('src/datos/actos.ts', '.modulo-territorio-actos');

/**
 * Los siete formularios, con la operacion que cada uno tiene que producir.
 *
 * Son siete y no cinco porque dos rutas se alcanzan por dos actos distintos: la
 * correccion de un sector y su baja son el MISMO `PUT`, y ahi esta justamente lo
 * que hay que medir —que la pantalla las separe, porque el privilegio no es el
 * mismo—. Los sujetos son los del catalogo de demostracion, que se deriva de
 * `infra/carga-de-datos/ejemplos/`.
 */
const RECORRIDO = [
  {
    k: 'altaDeSector',
    hash: '#/catastro/territorio/01?acto=altaDeSector',
    metodo: 'POST',
    plantilla: RUTAS.sectores,
    camino: RUTAS.sectores,
    /**
     * Tras el alta, la lista siguiente tiene que traerlo — **y se mira LA LISTA**.
     *
     * El arbol de sectores, por su contenedor. Medirlo sobre `<main>` entero
     * pasaba en VERDE con el defecto puesto y esta medido: el panel de «lo que el
     * servidor contesto» esta DENTRO de `<main>` y ya trae el codigo recien
     * creado, asi que «la pagina lo dice» es cierto aunque la lista no lo traiga.
     */
    apareceEn: '[data-lista="1"]',
  },
  {
    k: 'corregirSector',
    hash: '#/catastro/territorio/01?acto=corregirSector',
    metodo: 'PUT',
    plantilla: RUTAS.sector,
    camino: '/catastro/sectores/01',
  },
  {
    k: 'bajaDeSector',
    hash: '#/catastro/territorio/01?acto=bajaDeSector',
    metodo: 'PUT',
    plantilla: RUTAS.sector,
    camino: '/catastro/sectores/01',
    confirmaAparte: true,
  },
  {
    k: 'altaDeManzana',
    hash: '#/catastro/territorio/01?acto=altaDeManzana',
    metodo: 'POST',
    plantilla: RUTAS.manzanas,
    camino: '/catastro/sectores/01/manzanas',
  },
  {
    k: 'altaDeVia',
    hash: '#/catastro/territorio/vias?acto=altaDeVia',
    metodo: 'POST',
    plantilla: RUTAS.vias,
    camino: RUTAS.vias,
    /* Y aqui la TABLA del catalogo vial, por lo mismo. */
    apareceEn: 'table[data-sticky="1"]',
  },
  {
    k: 'corregirVia',
    hash: '#/catastro/territorio/vias?acto=corregirVia&via=V-0003',
    metodo: 'PUT',
    plantilla: RUTAS.via,
    camino: '/catastro/vias/V-0003',
  },
  {
    k: 'bajaDeVia',
    hash: '#/catastro/territorio/vias?acto=bajaDeVia&via=V-0003',
    metodo: 'PUT',
    plantilla: RUTAS.via,
    camino: '/catastro/vias/V-0003',
    confirmaAparte: true,
  },
];

const fallos = [];

/* ── 1. Que el recorrido ejercite todo lo declarado ──────────────────────── */

const ejercitadas = new Set(RECORRIDO.map((c) => `${c.metodo} ${c.plantilla}`));
const sinEjercitar = Object.keys(LO_QUE_EL_SERVIDOR_DESCARTA).filter((o) => !ejercitadas.has(o));
if (sinEjercitar.length > 0) {
  console.error(
    `\n${sinEjercitar.length} operacion(es) declaran campos que el servidor descarta y este recorrido no las\n` +
      `conduce: ${sinEjercitar.join(', ')}.\n` +
      'Una declaracion que nadie ejercita no protege de nada y no se ve: el dia que alguien ofrezca ese\n' +
      'control, la pantalla lo mandara y el servidor lo tirara sin que ningun arnes lo diga.',
  );
  process.exit(2);
}
if (Object.keys(LO_QUE_EL_SERVIDOR_DESCARTA).length === 0) {
  console.error(
    '\n`LO_QUE_EL_SERVIDOR_DESCARTA` esta vacio, asi que la mitad de este arnes que comprueba que el\n' +
      'cuerpo no lleva lo que el servidor tira no tiene nada que comprobar y se cumpliria sola.',
  );
  process.exit(2);
}

/* ── 2. La pagina, con lo que sale por la puerta anotado ─────────────────── */

const navegador = await chromium.launch();
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 1600 } });

/* `page.route` no sirve: el proxy de datos SUSTITUYE `fetch`, asi que las
   peticiones no llegan a la red. Se envuelve con un `get`/`set` y el proxy se
   instala encima (la leccion de `errores.mjs`). */
await contexto.addInitScript((raiz) => {
  window.__escrituras = [];
  /* El rechazo que se inyecta, cuando toca. Se lee en CADA llamada y no al
     instalar, para que la misma pagina pueda medir primero el camino feliz y
     despues el rechazo sin recargar el proxy. */
  window.__inyeccion = null;
  let actual = globalThis.fetch;
  const envolver = (delegar) => async (entrada, opciones) => {
    const href = typeof entrada === 'string' ? entrada : entrada instanceof URL ? entrada.href : entrada.url;
    const url = new URL(href, location.origin);
    /* El metodo, sin `??` sobre un booleano: `false ?? 'GET'` vale `false` —el
       operador solo cae con null o undefined— y `false.toUpperCase()` revienta.
       Con `solicitar()` no pasa, porque siempre manda `method`; con cualquier
       otro `fetch` de la pagina, si. */
    const metodo = String(opciones?.method ?? (entrada instanceof Request ? entrada.method : 'GET')).toUpperCase();
    const inyectada = window.__inyeccion;
    const respuesta =
      inyectada && inyectada.metodo === metodo && url.pathname === raiz + inyectada.camino
        ? new Response(JSON.stringify(inyectada.cuerpo), {
            status: inyectada.estado,
            headers: { 'Content-Type': 'application/problem+json' },
          })
        : await delegar(entrada, opciones);
    if (metodo !== 'GET' && url.pathname.startsWith(raiz)) {
      let cuerpo = null;
      try {
        cuerpo = JSON.parse(opciones?.body ?? 'null');
      } catch {
        cuerpo = null;
      }
      let devuelto = null;
      try {
        devuelto = await respuesta.clone().json();
      } catch {
        devuelto = null;
      }
      window.__escrituras.push({
        metodo,
        camino: url.pathname,
        estado: respuesta.status,
        cuerpo,
        devuelto,
      });
    }
    return respuesta;
  };
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    get: () => envolver(actual),
    set: (nueva) => {
      actual = nueva;
    },
  });
}, RAIZ);

const pagina = await contexto.newPage();

/** La `<section>` del acto, por el titulo que `Seccion` dibuja en su `h2`. */
const panelDe = (titulo) =>
  pagina.locator('section').filter({ has: pagina.locator('h2', { hasText: titulo }) }).first();

/** Todo numero de un texto, como el ojo lo lee: `"42.00"`, `"1,234"`, `"1998"`. */
const cifrasDe = (texto) => [...texto.matchAll(/\d[\d.,]*/g)].map((x) => x[0].replace(/[.,]$/, ''));

/** Y todas las que un valor del JSON contiene, para poder compararlas. */
function cifrasDelDato(valor) {
  if (valor === null || valor === undefined) return [];
  return cifrasDe(String(valor));
}

const soloActo = process.argv[2] ?? null;
const elegidos = RECORRIDO.filter((c) => !soloActo || c.k === soloActo);

const operacionesQueViajaron = new Set();
let cuerposMedidos = 0;
let marcasComprobadas = 0;
let cifrasComparadas = 0;
let confirmacionesMedidas = 0;
let listasComprobadas = 0;

for (const caso of elegidos) {
  const titulo = ACTOS_DEL_TERRITORIO[caso.k];
  await pagina.goto(`${BASE}/${caso.hash}`, { waitUntil: 'networkidle' });
  await pagina.waitForTimeout(700);
  await pagina.evaluate(() => {
    window.__escrituras = [];
  });

  const panel = panelDe(titulo);
  if ((await panel.count()) === 0) {
    fallos.push(
      `«${caso.k}» (${caso.hash}): no hay ningun formulario titulado «${titulo}».\n      ` +
        'El acto se abre desde la ruta; si no se dibuja, esta direccion no lleva a ningun sitio y lo que ' +
        'venga despues se estaria comprobando sobre una pantalla vacia.',
    );
    continue;
  }

  /* ── Se rellena TODO control editable, cada uno con su marca ──────────── */

  const marcas = await panel.evaluate((raiz) => {
    const puestas = [];
    const controles = [...raiz.querySelectorAll('input, select, textarea')];
    controles.forEach((el, i) => {
      const rotulo = (el.closest('label')?.querySelector('span')?.textContent ?? '').trim();
      if (el.tagName === 'SELECT') {
        /* La ULTIMA opcion y no la primera: la primera es «Elija uno», que vale
           cadena vacia y no viajaria — con ella, «la marca no llego» seria cierto
           por como se relleno y no por el defecto. */
        const opciones = [...el.options].filter((o) => o.value !== '');
        if (opciones.length === 0) return;
        el.value = opciones[opciones.length - 1].value;
        el.dispatchEvent(new Event('change', { bubbles: true }));
        puestas.push({ rotulo, marca: el.value });
        return;
      }
      const marca = `MARCA-${String.fromCharCode(65 + i)}`;
      const ponerValor = Object.getOwnPropertyDescriptor(
        el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype,
        'value',
      ).set;
      ponerValor.call(el, marca);
      el.dispatchEvent(new Event('input', { bubbles: true }));
      puestas.push({ rotulo, marca });
    });
    return puestas;
  });

  if (marcas.length === 0) {
    fallos.push(
      `«${caso.k}» (${caso.hash}): el formulario no tiene NI UN control editable.\n      ` +
        'Toda escritura de este sistema exige al menos la observacion (RNF-052), asi que un formulario ' +
        'sin controles no puede enviarse — y lo de abajo se comprobaria sobre el conjunto vacio.',
    );
    continue;
  }

  /* ── Lo que no se deshace: una pulsacion NO escribe ───────────────────── */

  await panel.getByRole('button', { name: titulo }).click();
  await pagina.waitForTimeout(500);
  if (caso.confirmaAparte) {
    const antes = await pagina.evaluate(() => window.__escrituras.length);
    if (antes !== 0) {
      fallos.push(
        `«${caso.k}» (${caso.hash}): el primario escribio SIN confirmar —${antes} peticion(es)—.\n      ` +
          'Retirar algo del catalogo no se deshace por la misma via por la que se hizo, asi que se ' +
          'confirma aparte. Un panel de confirmacion que aparece cuando ya se escribio es decorativo.',
      );
    }
    confirmacionesMedidas++;
    const confirmar = panel.getByRole('button', { name: IRREVERSIBLE.confirmar });
    if ((await confirmar.count()) > 0) {
      await confirmar.click();
      await pagina.waitForTimeout(600);
    }
  } else {
    /* Y el contraste por el otro lado: lo que SI se deshace no puede pedir una
       segunda pulsacion, o el arnes estaria midiendo dos flujos distintos con la
       misma regla. */
    const confirmar = panel.getByRole('button', { name: IRREVERSIBLE.confirmar });
    if ((await confirmar.count()) > 0) {
      fallos.push(
        `«${caso.k}» (${caso.hash}): este acto abre una confirmacion aparte y no la necesita.\n      ` +
          'La confirmacion es para lo que no se deshace; ponerla en todo la convierte en un tramite que ' +
          'se pulsa sin leer, y entonces deja de proteger lo que si lo necesita.',
      );
    }
    await pagina.waitForTimeout(300);
  }

  const escrituras = await pagina.evaluate(() => window.__escrituras);
  const suya = escrituras.find((e) => e.metodo === caso.metodo && e.camino === RAIZ + caso.camino);
  if (suya === undefined) {
    fallos.push(
      `«${caso.k}» (${caso.hash}): no salio ninguna «${caso.metodo} ${RAIZ}${caso.camino}».\n      ` +
        `Lo que salio: ${escrituras.length ? escrituras.map((e) => `${e.metodo} ${e.camino}`).join(', ') : 'nada'}.`,
    );
    continue;
  }
  cuerposMedidos++;
  operacionesQueViajaron.add(`${caso.metodo} ${RAIZ}${caso.plantilla}`);

  const cuerpo = suya.cuerpo ?? {};
  const enElCuerpo = JSON.stringify(cuerpo);

  /* ── Toda marca tecleada llega al cuerpo ──────────────────────────────── */

  for (const { rotulo, marca } of marcas) {
    marcasComprobadas++;
    if (enElCuerpo.includes(marca)) continue;
    fallos.push(
      `«${caso.k}» (${caso.hash}): el campo «${rotulo}» se rellena y NO viaja.\n      ` +
        `Se escribio «${marca}» y el cuerpo que salio es ${enElCuerpo}.\n      ` +
        'Un control que se rellena y no llega es el peor de los dos errores: no hay error, no hay aviso, ' +
        'el servidor contesta que se guardo, y el dato no acaba en ningun sitio.',
    );
  }

  /* ── Y el cuerpo no trae lo que el servidor tira ──────────────────────── */

  const descartado = LO_QUE_EL_SERVIDOR_DESCARTA[`${caso.metodo} ${caso.plantilla}`];
  for (const campo of descartado?.campos ?? []) {
    if (!Object.hasOwn(cuerpo, campo)) continue;
    fallos.push(
      `«${caso.k}» (${caso.hash}): el cuerpo manda «${campo}», que esta operacion DESCARTA.\n      ` +
        `${descartado.motivo}\n      ` +
        'Viaja, el servidor contesta que se hizo, y no se aplica: es un campo que la pantalla afirma ' +
        'haber guardado y no esta en ningun sitio.',
    );
  }

  /* ── Y ninguna cifra del panel se inventa ─────────────────────────────── */

  if (suya.estado >= 400) {
    fallos.push(
      `«${caso.k}» (${caso.hash}): la escritura contesto ${suya.estado} y no un exito, asi que no hay ` +
        `panel de resultado que mirar. El cuerpo devuelto: ${JSON.stringify(suya.devuelto)}`,
    );
    continue;
  }

  const texto = await panel.innerText();
  const prosa = [NOTAS_DE_LOS_ACTOS_DEL_TERRITORIO[caso.k], MOTIVOS.conteosDeLaEscritura, ACTO.elProxyNoPersiste];
  const sinProsa = prosa.reduce((t, p) => (p ? t.split(p).join(' ') : t), texto);

  const admitidas = new Set();
  for (const valor of Object.values(suya.devuelto ?? {})) {
    for (const cifra of cifrasDelDato(valor)) admitidas.add(cifra);
  }
  const inventadas = [...new Set(cifrasDe(sinProsa))].filter((c) => !admitidas.has(c));
  cifrasComparadas += new Set(cifrasDe(sinProsa)).size;
  if (inventadas.length > 0) {
    fallos.push(
      `«${caso.k}» (${caso.hash}): el panel de «${ACTO.loQueSeAcabaDeHacer}» ensena ${inventadas.length} ` +
        `cifra(s) que la respuesta NO trae: ${inventadas.join(', ')}.\n      ` +
        `Lo que el servidor contesto: ${JSON.stringify(suya.devuelto)}.\n      ` +
        'Los tres conteos de un sector y los dos de una manzana llegan NULOS en toda respuesta de ' +
        'escritura —quien escribe no pidio contar nada—, y un cero ahi diria «no tiene ninguna». Se pinta ' +
        'exactamente igual que una cifra leida.',
    );
  }

  /* ── Y la lista siguiente trae lo que se acaba de escribir ─────────────── */

  if (caso.apareceEn) {
    const suCodigo = marcas.find((m) => /codigo/i.test(m.rotulo))?.marca;
    if (suCodigo === undefined) {
      fallos.push(
        `«${caso.k}» (${caso.hash}): no se pudo saber con que codigo se dio de alta, asi que no se ` +
          'comprobo si la lista lo trae.',
      );
    } else {
      await panel.getByRole('button', { name: 'Volver a leer el catalogo' }).click();
      await pagina.waitForTimeout(800);
      listasComprobadas++;
      const lista = pagina.locator(caso.apareceEn);
      if ((await lista.count()) === 0) {
        fallos.push(
          `«${caso.k}» (${caso.hash}): no se encontro «${caso.apareceEn}», que es donde tiene que salir lo\n` +
            '      recien escrito. Sin ese sujeto, «la lista lo trae» se cumpliria sobre el conjunto vacio.',
        );
        continue;
      }
      const despues = await lista.first().innerText();
      if (!despues.includes(suCodigo)) {
        fallos.push(
          `«${caso.k}» (${caso.hash}): se dio de alta «${suCodigo}» y «${caso.apareceEn}» NO lo trae.\n      ` +
            'La respuesta dice que se creo y el catalogo vuelve igual que antes, que es exactamente como ' +
            'se ve un alta que no se guardo. Contra el backend lo arregla el backend; contra el proxy de ' +
            'datos lo arregla su memoria, y por eso `src/simulado/catalogo.ts` la tiene.',
        );
      }
    }
  }
}

/* ── 3. Los tres desenlaces de una escritura, cada uno con QUE HACER ─────── */

/**
 * Los cinco rechazos que esta hoja tiene que saber separar, y por que estos.
 *
 * `Fallo` ya dice lo que PASO —el titulo del codigo, el mensaje del servidor—, y
 * eso lo mide `errores.mjs` sobre las dos superficies nuevas. Lo que aqui se
 * mide es lo otro: **lo que quien lo lee tiene que hacer**, que depende de la
 * operacion y no del codigo. El mismo `409` significa «elija otro codigo de
 * sector» en un sitio y «ese codigo de manzana ya esta usado EN ESTE SECTOR» en
 * el de al lado; el mismo `403` significa «su cuenta no escribe el catalogo» en
 * un alta y «le falta ELIMINACION, que es otro privilegio» en una retirada.
 *
 * **Los dos `409` no se inyectan: son de verdad.** Los produce el proxy con el
 * mensaje que el controlador tiene escrito, dando de alta un codigo que ya esta
 * en el catalogo de demostracion. Los otros tres se inyectan porque desde la
 * pantalla **no se pueden alcanzar**, y eso es una propiedad del diseno y no un
 * hueco: el sujeto de una correccion sale de la lista leida, asi que un `404`
 * solo ocurre si el catalogo cambio por debajo; el `422` de la observacion no
 * llega porque el primario nace apagado sin ella; y el `403` depende de un
 * privilegio que este backend no publica.
 *
 * El texto esperado se LEE de `src/datos/catastro.ts`. Con una copia aqui,
 * cambiar la frase dejaria este arnes en verde afirmando sobre la vieja.
 */
const RECHAZOS = [
  {
    k: 'sector-con-codigo-repetido',
    hash: '#/catastro/territorio/01?acto=altaDeSector',
    acto: 'altaDeSector',
    rellenar: [
      [CAMPOS_DEL_TERRITORIO.codigoDeSector.rotulo, '01'],
      [CAMPOS_DEL_TERRITORIO.nombreDelSector.rotulo, 'Cercado bis'],
    ],
    espera: QUE_HACER.codigoDeSectorRepetido,
    estado: 409,
  },
  {
    k: 'manzana-con-codigo-repetido',
    hash: '#/catastro/territorio/01?acto=altaDeManzana',
    acto: 'altaDeManzana',
    rellenar: [[CAMPOS_DEL_TERRITORIO.codigoDeManzana.rotulo, '001']],
    espera: QUE_HACER.codigoDeManzanaRepetido,
    estado: 409,
  },
  {
    k: 'via-que-ya-no-esta',
    hash: '#/catastro/territorio/vias?acto=corregirVia&via=V-0003',
    acto: 'corregirVia',
    rellenar: [[CAMPOS_DEL_TERRITORIO.nombreDeLaVia.rotulo, 'Comercio Norte']],
    inyecta: {
      metodo: 'PUT',
      camino: '/catastro/vias/V-0003',
      estado: 404,
      cuerpo: { codigo: 'NO_ENCONTRADO', mensaje: "No hay ninguna via con codigo 'V-0003' en esta municipalidad" },
    },
    espera: QUE_HACER.viaQueNoEsta,
    estado: 404,
  },
  {
    k: 'campo-rechazado',
    hash: '#/catastro/territorio/vias?acto=altaDeVia',
    acto: 'altaDeVia',
    rellenar: [
      [CAMPOS_DEL_TERRITORIO.codigoDeVia.rotulo, 'V-9999'],
      [CAMPOS_DEL_TERRITORIO.nombreDeLaVia.rotulo, 'Los Algarrobos'],
    ],
    /* El tipo sale de un enumerado y hay que elegirlo: sin el, el primario nace
       apagado diciendo que falta —que es lo que `impedimentos` mide— y este caso
       no llegaria a producir ningun rechazo. */
    elegir: [[CAMPOS_DEL_TERRITORIO.tipoDeVia.rotulo, 'CALLE']],
    inyecta: {
      metodo: 'POST',
      camino: '/catastro/vias',
      estado: 422,
      cuerpo: { codigo: 'VALIDACION', mensaje: "Falta el campo 'tipo'" },
    },
    espera: QUE_HACER.campoRechazado,
    estado: 422,
  },
  {
    /* El que da nombre al AC-2: quien puede corregir y no retirar recibe un 403
       en una pantalla en la que acaba de guardar sin problema. */
    k: 'sin-el-privilegio-de-retirar',
    hash: '#/catastro/territorio/vias?acto=bajaDeVia&via=V-0003',
    acto: 'bajaDeVia',
    rellenar: [],
    confirmaAparte: true,
    inyecta: {
      metodo: 'PUT',
      camino: '/catastro/vias/V-0003',
      estado: 403,
      cuerpo: { codigo: 'SIN_PRIVILEGIO', mensaje: 'No tiene el privilegio ELIMINACION sobre calles' },
    },
    espera: QUE_HACER.sinPrivilegioDeRetirar,
    estado: 403,
  },
];

let rechazosMedidos = 0;

for (const caso of RECHAZOS) {
  if (soloActo && caso.k !== soloActo) continue;
  const titulo = ACTOS_DEL_TERRITORIO[caso.acto];
  await pagina.goto(`${BASE}/${caso.hash}`, { waitUntil: 'networkidle' });
  await pagina.waitForTimeout(700);
  await pagina.evaluate((i) => {
    window.__escrituras = [];
    window.__inyeccion = i ?? null;
  }, caso.inyecta ?? null);

  const panel = panelDe(titulo);
  if ((await panel.count()) === 0) {
    fallos.push(`«${caso.k}» (${caso.hash}): no hay ningun formulario titulado «${titulo}».`);
    continue;
  }
  for (const [rotulo, valor] of caso.rellenar) await panel.getByLabel(rotulo).fill(valor);
  for (const [rotulo, valor] of caso.elegir ?? []) await panel.getByLabel(rotulo).selectOption(valor);
  await panel.getByLabel(LA_OBSERVACION.rotulo).fill('Se comprueba como se lee el rechazo');

  /* Si el primario sigue apagado, este caso no puede producir su rechazo: se
     dice con el motivo que el propio boton lleva escrito, en vez de esperar
     medio minuto a un control que nadie va a encender. */
  const primario = panel.getByRole('button', { name: titulo });
  if (await primario.isDisabled()) {
    fallos.push(
      `«${caso.k}» (${caso.hash}): el primario sigue apagado tras rellenar el formulario, asi que este\n` +
        `      caso no llega a producir su ${caso.estado}. El boton dice: «${await primario.getAttribute('title')}».`,
    );
    continue;
  }
  await primario.click();
  await pagina.waitForTimeout(400);
  if (caso.confirmaAparte) {
    const confirmar = panel.getByRole('button', { name: IRREVERSIBLE.confirmar });
    if ((await confirmar.count()) > 0) await confirmar.click();
  }
  await pagina.waitForTimeout(800);

  const escrituras = await pagina.evaluate(() => window.__escrituras);
  const suya = escrituras.at(-1);
  if (suya === undefined) {
    fallos.push(
      `«${caso.k}» (${caso.hash}): no salio ninguna escritura, asi que no hubo rechazo que leer y lo de
` +
        '      abajo se comprobaria sobre una pantalla que no llego a intentarlo.',
    );
    continue;
  }
  if (suya.estado !== caso.estado) {
    fallos.push(
      `«${caso.k}» (${caso.hash}): se esperaba un ${caso.estado} y el servidor contesto ${suya.estado}.
      ` +
        `Lo que devolvio: ${JSON.stringify(suya.devuelto)}.
      ` +
        'Sin el rechazo que este caso existe para provocar, lo de abajo mide otra cosa.',
    );
    continue;
  }
  rechazosMedidos++;

  const texto = await panel.innerText();
  if (!texto.includes(caso.espera)) {
    fallos.push(
      `«${caso.k}» (${caso.hash}): el ${caso.estado} no dice QUE HAY QUE HACER.
      ` +
        `Falta: «${caso.espera.slice(0, 120)}…».
      ` +
        `Lo que sale: «${texto.replace(/\s+/g, ' ').trim().slice(0, 220)}…».
      ` +
        'El titulo del codigo dice lo que PASO y no lo que hay que hacer, y eso depende de la operacion: ' +
        'el mismo 409 se arregla con otro codigo aqui y mirando en que sector se esta ahi, y el mismo 403 ' +
        'es «no escribe el catalogo» en un alta y «le falta ELIMINACION» en una retirada. Sin la frase, ' +
        'los cinco se leen como que el sistema se rompio.',
    );
  }
}


/* ── 4. El pie nombra las escrituras CON SU VERBO ────────────────────────── */

/**
 * Que el pie de la hoja diga el metodo que de verdad viaja.
 *
 * `Servida` escribia «POST» fijo hasta #72, porque las nueve escrituras de
 * fiscalizacion lo son. Aqui dos son `PUT`, y un pie que las anunciara como
 * `POST` estaria afirmando un metodo que esa ruta **no admite**: pedirla asi
 * contesta `405 METODO_NO_ADMITIDO`, uno de los doce codigos que este cliente
 * distingue. El pie existe para decir la verdad sobre el contrato; con la mitad
 * inventada deja de servir para lo unico que sirve.
 *
 * Lo esperado **no se escribe aqui**: son las operaciones que este mismo
 * recorrido acaba de ver salir por `fetch`.
 */
let verbosComprobados = 0;
if (!soloActo) {
  await pagina.goto(`${BASE}/#/catastro/territorio/01`, { waitUntil: 'networkidle' });
  await pagina.waitForTimeout(600);
  const pie = (await pagina.locator('main').innerText()).replace(/\s+/g, ' ');
  for (const operacion of [...operacionesQueViajaron].sort()) {
    verbosComprobados++;
    if (pie.includes(operacion)) continue;
    fallos.push(
      `el pie de la hoja no nombra «${operacion}», que es una de las escrituras que acaban de salir por\n` +
        '      la puerta con ESE verbo. Un pie que anuncia «POST» sobre una ruta que solo admite «PUT» ' +
        'afirma\n      un metodo que contesta 405, y es el unico sitio de la pantalla que dice que ruta la sirve.',
    );
  }
  if (verbosComprobados === 0) {
    fallos.push(
      'no viajo ninguna escritura, asi que no se comprobo ni un verbo del pie: esa mitad se estaria\n' +
        '      cumpliendo sobre el conjunto vacio.',
    );
  }
}

await navegador.close();

console.log(
  `${cuerposMedidos} escritura(s) conducidas de ${elegidos.length} · ${marcasComprobadas} campo(s) ` +
    `tecleados contra el cuerpo que viajo · ${cifrasComparadas} cifra(s) del panel contra el JSON · ` +
    `${confirmacionesMedidas} confirmacion(es) aparte · ${listasComprobadas} lista(s) releidas · ` +
    `${rechazosMedidos} rechazo(s) leidos de ${RECHAZOS.length} —dos «409» de verdad y tres inyectados— · ` +
    `${verbosComprobados} verbo(s) del pie contra lo que viajo · ` +
    `${Object.keys(LO_QUE_EL_SERVIDOR_DESCARTA).length} operacion(es) con campos que el servidor descarta`,
);

/* Los problemas se imprimen ANTES de decidir el codigo de salida: una rotura que
   ademas deje de conducir formularios haria saltar las dos cosas, y con el «no
   midio nada» delante no se veria QUE se rompio (la leccion de #46). */
if (fallos.length) {
  console.error(`\n${fallos.length} problema(s) con el mantenimiento del catalogo:\n`);
  for (const f of fallos) console.error('  - ' + f + '\n');
}

/**
 * Con un solo caso pedido no se exige el recorrido entero —seria imposible de
 * cumplir—, pero si que ese caso EXISTA: un nombre que se quedo viejo dejaria el
 * arnes recorriendo el conjunto vacio y saliendo con 0.
 */
if (soloActo) {
  const conocido =
    RECORRIDO.some((c) => c.k === soloActo) || RECHAZOS.some((c) => c.k === soloActo);
  if (!conocido) {
    console.error(
      `\n«${soloActo}» no es ninguno de los casos de este arnes. Los hay: ` +
        `${[...RECORRIDO, ...RECHAZOS].map((c) => c.k).join(', ')}.`,
    );
    process.exit(2);
  }
} else if (cuerposMedidos === 0 || marcasComprobadas === 0 || cifrasComparadas === 0 || rechazosMedidos === 0) {
  console.error(
    `\nY ademas: ${cuerposMedidos} cuerpo(s) capturados, ${marcasComprobadas} marca(s) comprobadas, ` +
      `${cifrasComparadas} cifra(s) comparadas y ${rechazosMedidos} rechazo(s) leidos.\n\n` +
      'Sin una de las cuatro cosas, todo lo que este arnes afirma es cierto sobre el conjunto vacio y\n' +
      'saldria en verde con el defecto exacto que existe para atrapar. O el recorrido no llega a los\n' +
      'formularios, o los formularios dejaron de enviar, o ningun rechazo llego a producirse.',
  );
  process.exit(2);
}
if (fallos.length) process.exit(1);

console.log(
  'todo lo que se teclea llega al cuerpo, ningun cuerpo manda lo que el servidor descarta, ninguna\n' +
    'cifra del panel se inventa, lo que no se deshace no escribe hasta confirmarse, la lista siguiente\n' +
    'trae lo que se acaba de escribir, y cada rechazo dice que hay que hacer con el',
);
