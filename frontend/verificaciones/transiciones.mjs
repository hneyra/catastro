/**
 * La pantalla ofrece **exactamente** lo que el estado de la fila admite.
 *
 *   node verificaciones/transiciones.mjs
 *
 * <h2>Que existe para impedir</h2>
 *
 * El dominio de `fiscalizacion` es una maquina de estados con dos compuertas en
 * orden: `Candidato.verificadoEnCampo()` **exige venir de gabinete** y
 * `descartadoEn` rechaza la etapa que no toca. Una pantalla que ofreciera el
 * atajo no romperia nada —el backend contesta 409 y la fila no se mueve— y aun
 * asi seria el peor de los desenlaces: ensena un camino que no existe, y quien
 * lo recorre concluye que el sistema esta roto en vez de que ese acto no
 * procede. El AC-2 de #71 lo dice al reves: **no se ofrece lo que el estado no
 * admite**.
 *
 * Y el defecto es invisible a los otros arneses. `mirar` ve la pantalla llena;
 * `impedimentos` ve botones habilitados, que es lo correcto para el; `errores`
 * mide rechazos que aqui no llegan a producirse. Lo unico que lo delata es
 * comparar, fila a fila, lo ofrecido con lo declarado.
 *
 * <h2>Lo esperado se DERIVA del fuente y no se copia</h2>
 *
 * `TRANSICIONES_DEL_CANDIDATO`, `TRANSICIONES_DEL_HALLAZGO` y los dos
 * enumerados salen de `src/api/fiscalizacion.ts`, y los rotulos de
 * `src/datos/fiscalizacion.ts`, los dos compilados al vuelo. Con una copia aqui,
 * cambiar una transicion dejaria este arnes en verde afirmando sobre la de
 * antes — que es la forma de defecto que C-17 encontro cinco veces.
 *
 * <h2>Y mide tambien que el ciclo AVANZA</h2>
 *
 * Comparar la cola en reposo no basta: con la lista congelada, «lo ofrecido
 * cuadra con el estado» se cumple sola para siempre. Asi que despues del censo
 * **se ejecuta un acto** —admitir en gabinete al candidato que esta
 * `DETECTADO`— y se exige que la misma fila pase a `ADMITIDO_EN_GABINETE` y
 * ofrezca las dos de campo. Eso es lo que mide el AC-1 y, de paso, lo que
 * sostiene la decision de que el proxy de datos recuerde el recorrido: sin
 * memoria, la lectura siguiente contradice a la escritura anterior y la fila
 * vuelve a ofrecer lo que se acaba de hacer.
 *
 * Necesita una vista previa levantada y el Chromium de Playwright.
 */
import { chromium } from 'playwright-core';
import { leerModulo } from './registro.mjs';

const api = await leerModulo('src/api/fiscalizacion.ts', '.registro-transiciones-api');
const {
  ACTOS,
  COLUMNAS: COLUMNAS_DE_FISCALIZACION,
  IRREVERSIBLES,
} = await leerModulo('src/datos/fiscalizacion.ts', '.registro-transiciones-datos');
const COLUMNAS_ANULADO_POR = COLUMNAS_DE_FISCALIZACION.anuladoPor;
const COLUMNAS_ANULADO_EN = COLUMNAS_DE_FISCALIZACION.anuladoEn;
const { RAIZ } = await leerModulo('src/api/cliente.ts', '.registro-transiciones-cliente');

const BASE = process.env.CATASTRO_BASE ?? 'http://localhost:5190';

/**
 * Las dos tablas que se miden, con la pantalla en la que vive cada una.
 *
 * El sujeto es la unica campania que el padron de demostracion siembra. Las dos
 * paginas se abren por separado a proposito: cada carga vuelve a sembrar el
 * ciclo, asi que el censo mide siempre el mismo estado de partida.
 */
const TABLEROS = [
  {
    k: 'candidatos',
    que: 'Candidato',
    hash: '#/fiscalizacion/candidatos/1',
    estados: api.ESTADOS_DE_CANDIDATO,
    transiciones: api.TRANSICIONES_DEL_CANDIDATO,
  },
  {
    k: 'hallazgos',
    que: 'Hallazgo',
    hash: '#/fiscalizacion/hallazgos/1',
    estados: api.ESTADOS_DE_HALLAZGO,
    transiciones: api.TRANSICIONES_DEL_HALLAZGO,
  },
];

/**
 * El acto que se ejecuta para comprobar que el ciclo avanza.
 *
 * Es el primero del recorrido y el unico que no pide mas que su observacion:
 * asi lo que se mide es la transicion y no el relleno de un formulario.
 */
const EL_PASO = {
  hash: '#/fiscalizacion/candidatos/1?acto=admitirEnGabinete&candidato=1',
  candidato: 1,
  acto: 'admitirEnGabinete',
  desde: 'DETECTADO',
  hasta: 'ADMITIDO_EN_GABINETE',
  observacion: 'Revisado contra la ortofoto del sector y merece visita de campo',
};

/**
 * Y el acto que NO se deshace, que se firma con DOS pulsaciones (AC-3).
 *
 * Lo que hay que medir no es que salga un aviso: es que la **primera** pulsacion
 * no escriba nada. Un panel de confirmacion que se dibuja y que ademas manda la
 * peticion cumpliria cualquier guarda que solo mirara la pantalla, y seria
 * exactamente el defecto —el hallazgo queda retirado antes de que nadie
 * confirme—. Por eso se mira `window.__peticiones`.
 *
 * Y por eso la confirmacion es un panel de la propia pagina y no un `confirm()`
 * del navegador: un dialogo modal bloquea el hilo y deja este arnes colgado
 * esperando a que alguien lo cierre.
 */
const EL_ACTO_QUE_NO_SE_DESHACE = {
  hash: '#/fiscalizacion/hallazgos/1?acto=dejarSinEfecto&hallazgo=1',
  hallazgo: 1,
  acto: 'dejarSinEfecto',
  desde: 'FIRME',
  hasta: 'DEJADO_SIN_EFECTO',
  ruta: '/fiscalizacion/hallazgos/1/anulacion',
  motivo: 'El techo resulto estar en el predio vecino, ya inscrito',
  observacion: 'Se retira el hallazgo tras la segunda visita de campo',
};

const navegador = await chromium.launch();
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 1400 } });
contexto.setDefaultTimeout(6000);

/**
 * Que peticiones salen, y **por que no vale `page.route`**.
 *
 * Con el proxy de datos encendido las peticiones no llegan a la red: el proxy
 * SUSTITUYE `fetch`, asi que el enrutador de Playwright no ve ninguna. Se
 * envuelve `globalThis.fetch` con un `get`/`set` antes de que la pagina cargue
 * —el proxy se instala encima, el `set` lo captura y el `get` lo devuelve
 * envuelto—, que es la misma tecnica que `errores.mjs` mide y explica.
 *
 * Hace falta para el acto irreversible: lo que hay que comprobar ahi es que la
 * PRIMERA pulsacion **no escribe nada**, y eso solo se ve mirando si salio la
 * peticion.
 */
await contexto.addInitScript(() => {
  window.__peticiones = [];
  let actual = globalThis.fetch;
  const envolver = (delegar) => (entrada, opciones) => {
    const href = typeof entrada === 'string' ? entrada : entrada instanceof URL ? entrada.href : entrada.url;
    const url = new URL(href, location.origin);
    window.__peticiones.push(`${(opciones?.method ?? 'GET').toUpperCase()} ${url.pathname}`);
    return delegar(entrada, opciones);
  };
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    get: () => envolver(actual),
    set: (nueva) => {
      actual = nueva;
    },
  });
});

const pagina = await contexto.newPage();

const problemas = [];
let filasMedidas = 0;
const estadosVistos = new Set();

/** Los grupos de acciones de una pantalla: `{ que, id, estado, botones }`. */
async function gruposDe(que) {
  return pagina.evaluate((quien) => {
    const grupos = [...document.querySelectorAll('[role="group"]')];
    return grupos
      .map((g) => {
        const nombre = g.getAttribute('aria-label') ?? '';
        const casa = nombre.match(/^(\w+) (\d+) · (\S+)$/);
        if (casa === null || casa[1] !== quien) return null;
        return {
          que: casa[1],
          id: Number(casa[2]),
          estado: casa[3],
          botones: [...g.querySelectorAll('button')].map((b) => (b.textContent ?? '').trim()),
        };
      })
      .filter((x) => x !== null);
    }, que);
}

const rotulosDe = (actos) => actos.map((a) => ACTOS[a]);

function comparar(tablero, fila) {
  const declarados = tablero.transiciones[fila.estado];
  if (declarados === undefined) {
    problemas.push(
      `${tablero.k}: la fila «${fila.que} ${fila.id}» dice estar en «${fila.estado}», que no es ninguno de` +
        ` los estados que el backend declara (${tablero.estados.join(', ')}).\n` +
        '    Un estado que el enumerado no tiene no admite ningun acto y la fila se queda muda, sin que\n' +
        '    nada lo diga: es como la siembra de este proxy contestaba «PASO_GABINETE» y «EN_CURSO».',
    );
    return;
  }
  estadosVistos.add(`${tablero.k}:${fila.estado}`);
  const esperados = rotulosDe(declarados);
  const ofrecidos = fila.botones;
  const sobran = ofrecidos.filter((b) => !esperados.includes(b));
  const faltan = esperados.filter((b) => !ofrecidos.includes(b));
  if (sobran.length) {
    problemas.push(
      `${tablero.k}: «${fila.que} ${fila.id}», en «${fila.estado}», OFRECE ${sobran.map((s) => `«${s}»`).join(', ')}` +
        ` y ese estado no lo admite.\n` +
        `    Lo que admite: ${esperados.length ? esperados.join(', ') : '(ninguno: es terminal)'}.\n` +
        '    Ofrecerlo y recibir un 409 es peor que no ofrecerlo: ensena un camino que no existe.',
    );
  }
  if (faltan.length) {
    problemas.push(
      `${tablero.k}: «${fila.que} ${fila.id}», en «${fila.estado}», NO ofrece ${faltan.map((s) => `«${s}»`).join(', ')}` +
        `, que es lo que ese estado admite.\n` +
        '    Es el fallo invisible de los dos: el acto no se puede hacer, no hay error y no hay aviso —el\n' +
        '    trabajo se hace fuera del sistema, que es lo que #71 existe para cerrar.',
    );
  }
}

/* ── 1 · El censo: lo ofrecido contra lo declarado, en reposo ────────────── */

for (const tablero of TABLEROS) {
  await pagina.goto(`${BASE}/${tablero.hash}`, { waitUntil: 'domcontentloaded' });
  await pagina.waitForTimeout(1100);
  const filas = await gruposDe(tablero.que);
  filasMedidas += filas.length;
  for (const fila of filas) comparar(tablero, fila);
}

/* ── 2 · Y el ciclo avanza: un acto, y la fila cambia de lo que ofrece ───── */

let elPasoSeMidio = false;
await pagina.goto(`${BASE}/${EL_PASO.hash}`, { waitUntil: 'domcontentloaded' });
await pagina.waitForTimeout(1100);

const panel = pagina
  .locator('section')
  .filter({ has: pagina.getByRole('heading', { name: ACTOS[EL_PASO.acto] }) })
  .first();

const antes = (await gruposDe('Candidato')).find((f) => f.id === EL_PASO.candidato);
if (antes === undefined || antes.estado !== EL_PASO.desde) {
  problemas.push(
    `el paso del ciclo no pudo empezar: el candidato ${EL_PASO.candidato} tenia que estar en` +
      ` «${EL_PASO.desde}» y esta en «${antes?.estado ?? '(no esta en la cola)'}».`,
  );
} else if ((await panel.count()) === 0) {
  problemas.push(
    `el paso del ciclo no pudo empezar: «${EL_PASO.hash}» no dibuja el formulario de «${ACTOS[EL_PASO.acto]}».`,
  );
} else {
  await panel.getByLabel('Observacion').fill(EL_PASO.observacion);
  await panel.getByRole('button', { name: ACTOS[EL_PASO.acto] }).click();
  await pagina.waitForTimeout(1100);

  const contestado = await panel.innerText();
  if (!contestado.includes(EL_PASO.hasta)) {
    problemas.push(
      `tras «${ACTOS[EL_PASO.acto]}», lo que el servidor contesto no dice «${EL_PASO.hasta}».\n` +
        `    Lo que dice el panel: «${contestado.replace(/\s+/g, ' ').trim().slice(0, 200)}»`,
    );
  }

  const volver = panel.getByRole('button', { name: 'Volver a leer la lista' });
  if ((await volver.count()) === 0) {
    problemas.push(
      'el desenlace del acto no ofrece «Volver a leer la lista»: la fila de al lado sale de OTRA lectura,\n' +
        '    y sin volver a pedirla la pantalla ensenaria el estado de antes al lado del acto que se acaba\n' +
        '    de hacer.',
    );
  } else {
    await volver.click();
    await pagina.waitForTimeout(1100);
    const despues = (await gruposDe('Candidato')).find((f) => f.id === EL_PASO.candidato);
    elPasoSeMidio = true;
    if (despues === undefined) {
      problemas.push(`tras el acto, el candidato ${EL_PASO.candidato} ya no esta en la cola.`);
    } else if (despues.estado !== EL_PASO.hasta) {
      problemas.push(
        `tras «${ACTOS[EL_PASO.acto]}», el candidato ${EL_PASO.candidato} sigue en «${despues.estado}» y` +
          ` tenia que estar en «${EL_PASO.hasta}», y sigue ofreciendo ${despues.botones.map((b) => `«${b}»`).join(', ')}.\n` +
          '    La lectura siguiente contradice a la escritura anterior: la pantalla vuelve a ofrecer lo que\n' +
          '    se acaba de hacer, que es el defecto que el AC-2 existe para impedir.',
      );
    } else {
      comparar(TABLEROS[0], despues);
    }
  }
}

/* ── 3 · Y el acto que no se deshace se firma DOS veces ──────────────────── */

let laConfirmacionSeMidio = false;
const A = EL_ACTO_QUE_NO_SE_DESHACE;
await pagina.goto(`${BASE}/${A.hash}`, { waitUntil: 'domcontentloaded' });
await pagina.waitForTimeout(1100);

const panelDelActo = pagina
  .locator('section')
  .filter({ has: pagina.getByRole('heading', { name: ACTOS[A.acto] }) })
  .first();

const escrituras = () =>
  pagina.evaluate((r) => window.__peticiones.filter((p) => p.startsWith('POST ') && p.endsWith(r)), RAIZ + A.ruta);

if ((await panelDelActo.count()) === 0) {
  problemas.push(
    `el acto irreversible no pudo medirse: «${A.hash}» no dibuja el formulario de «${ACTOS[A.acto]}».`,
  );
} else {
  await panelDelActo.getByLabel('Motivo').fill(A.motivo);
  await panelDelActo.getByLabel('Observacion').fill(A.observacion);
  await panelDelActo.getByRole('button', { name: ACTOS[A.acto] }).click();
  await pagina.waitForTimeout(700);

  const tras_la_primera = await escrituras();
  const confirmar = panelDelActo.getByRole('button', { name: IRREVERSIBLES.confirmar });
  const cancelar = panelDelActo.getByRole('button', { name: IRREVERSIBLES.cancelar });

  if (tras_la_primera.length > 0) {
    problemas.push(
      `«${ACTOS[A.acto]}» ESCRIBIO a la primera pulsacion: salio «${tras_la_primera[0]}» sin que nadie` +
        ' confirmara.\n' +
        '    Un acto que no se deshace se confirma aparte (AC-3 de #71): si la primera pulsacion ya lo\n' +
        '    hace, el panel de confirmacion es un adorno y el hallazgo queda retirado igual.',
    );
  }
  if ((await confirmar.count()) === 0 || (await cancelar.count()) === 0) {
    problemas.push(
      `«${ACTOS[A.acto]}» no abrio ninguna confirmacion aparte: falta «${IRREVERSIBLES.confirmar}» o` +
        ` «${IRREVERSIBLES.cancelar}».\n` +
        '    Y no vale un `confirm()` del navegador: bloquea el hilo, no lo lee un lector de pantalla y\n' +
        '    deja este arnes colgado esperando a que alguien cierre un dialogo.',
    );
  } else {
    await confirmar.click();
    await pagina.waitForTimeout(1100);
    laConfirmacionSeMidio = true;

    if ((await escrituras()).length === 0) {
      problemas.push(
        `tras confirmar, «POST ${RAIZ}${A.ruta}» no salio: la segunda pulsacion tampoco escribio nada.`,
      );
    }
    /* Se compara en MAYUSCULAS porque los rotulos de un `Dato` se pintan con
       `text-transform: uppercase` y `innerText` devuelve lo RENDERIZADO: sin
       normalizar, esta comprobacion estaria midiendo la hoja de estilos. */
    const contestado = (await panelDelActo.innerText()).toUpperCase();
    for (const { que, porque } of [
      { que: A.hasta, porque: 'es el estado nuevo del hallazgo' },
      /* El motivo que se tecleo, DE VUELTA: es lo que demuestra que el acto de
         la anulacion viajo entero y no solo que la pantalla cambio de color. */
      { que: A.motivo, porque: 'es el motivo que se acaba de escribir, contestado por el servidor' },
      { que: COLUMNAS_ANULADO_POR, porque: 'es QUIEN lo decidio' },
      { que: COLUMNAS_ANULADO_EN, porque: 'es CUANDO se decidio' },
    ]) {
      if (contestado.includes(que.toUpperCase())) continue;
      problemas.push(
        `tras «${ACTOS[A.acto]}», lo que el servidor contesto no dice «${que}», que ${porque}.\n` +
          '    Una vez hecho, la pantalla tiene que ensenar por que dejo de valer, quien lo decidio y\n' +
          '    cuando: es lo unico que quedara para explicarlo, y es justo lo que #23 anadio al recurso\n' +
          '    y esta interfaz descartaba.',
      );
    }

    const volver = panelDelActo.getByRole('button', { name: 'Volver a leer la lista' });
    if ((await volver.count()) > 0) {
      await volver.click();
      await pagina.waitForTimeout(1100);
      const despues = (await gruposDe('Hallazgo')).find((f) => f.id === A.hallazgo);
      if (despues === undefined || despues.estado !== A.hasta) {
        problemas.push(
          `tras la anulacion, el hallazgo ${A.hallazgo} sigue en «${despues?.estado ?? '(no esta)'}» y tenia` +
            ` que estar en «${A.hasta}».`,
        );
      } else {
        comparar(TABLEROS[1], despues);
      }
    }
  }
}

await navegador.close();

console.log(
  `${filasMedidas} fila(s) medidas en ${TABLEROS.length} tablero(s) · ` +
    `${estadosVistos.size} estado(s) cubiertos · ` +
    `2 actos ejecutados (${EL_PASO.desde} → ${EL_PASO.hasta}, y ${A.desde} → ${A.hasta} con su confirmacion aparte)`,
);

/* ── Y las guardas de que esto midio algo ───────────────────────────────── */

if (filasMedidas === 0) {
  console.error(
    '\nNo se midio NI UNA fila: ninguna pantalla dibujo un grupo de acciones con su estado en el\n' +
      'nombre, asi que este arnes no dice nada de lo que se ofrece. Pasaria en verde con el defecto\n' +
      'exacto que existe para atrapar. O la cola dejo de tener filas, o el armazon cambio como marca\n' +
      'el grupo de acciones de una fila y hay que ensenarselo.',
  );
  process.exit(2);
}

/**
 * Y **todos** los estados declarados tienen que haberse visto.
 *
 * Sin esto, una siembra con un solo estado dejaria las demas ramas sin mirar y
 * el arnes informaria en verde sobre una cuarta parte de la tabla: los dos
 * terminales —que no admiten nada— son justo los que hacen falta para que
 * «no ofrece de mas» signifique algo.
 */
const declarados = TABLEROS.flatMap((t) => t.estados.map((e) => `${t.k}:${e}`));
const sinCubrir = declarados.filter((e) => !estadosVistos.has(e));
if (sinCubrir.length) {
  console.error(
    `\n${sinCubrir.length} de ${declarados.length} estado(s) declarados no aparecieron en ninguna fila:` +
      `\n\n  ${sinCubrir.join('\n  ')}\n\n` +
      'Lo ofrecido por esos estados no se comparo con nada, asi que esta comprobacion no los cubre.\n' +
      'Los dos terminales son los que dan sentido a «no ofrece de mas»: sin ellos, una pantalla que\n' +
      'ofreciera todos los actos en todas las filas pasaria la mitad de este arnes.',
  );
  process.exit(2);
}

if (!elPasoSeMidio) {
  console.error(
    '\nEl acto del ciclo NO se llego a ejecutar, asi que este arnes solo ha mirado una cola en reposo:\n' +
      'con la lista congelada, «lo ofrecido cuadra con el estado» se cumple sola para siempre.',
  );
  if (problemas.length) console.error(`\n  ${problemas.join('\n\n  ')}`);
  process.exit(2);
}

if (!laConfirmacionSeMidio) {
  console.error(
    '\nEl acto irreversible NO se llego a confirmar, asi que no se midio que haga falta una segunda\n' +
      'pulsacion: la mitad del AC-3 se estaria cumpliendo sola.',
  );
  if (problemas.length) console.error(`\n  ${problemas.join('\n\n  ')}`);
  process.exit(2);
}

if (problemas.length) {
  console.error(`\n${problemas.length} desajuste(s) entre lo que se ofrece y lo que el estado admite:\n`);
  for (const p of problemas) console.error('  - ' + p + '\n');
  console.error(
    'Lo ofrecible sale de `TRANSICIONES_DEL_CANDIDATO` y `TRANSICIONES_DEL_HALLAZGO`, que son `Record`\n' +
      'completos sobre los enumerados del backend. Un acto de mas ensena un camino que no existe; uno de\n' +
      'menos deja un trabajo que hay que hacer fuera del sistema.',
  );
  process.exit(1);
}

console.log(
  'cada fila ofrece exactamente lo que su estado admite, los terminales no ofrecen ninguno, tras un\n' +
    'acto la fila cambia de estado y de lo que ofrece, y el que no se deshace no escribe nada hasta\n' +
    'que se confirma aparte',
);
