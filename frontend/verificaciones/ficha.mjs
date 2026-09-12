/**
 * La ficha dibuja lo que la respuesta trae, y **dice lo que no existe**.
 *
 *   CATASTRO_BASE=http://localhost:5210 node verificaciones/ficha.mjs
 *
 * <h2>Que existe para impedir</h2>
 *
 * `FichaResource` publica veintidos componentes y hasta #46 el tipo declaraba
 * diecisiete. Los cinco que faltaban —`instalaciones`, `economico`,
 * `bienesComunes`, `rural` e `historico`— **no daban ningun error**: el JSON
 * llegaba entero al navegador y `tsc` solo se quejaba el dia que alguien
 * intentaba leerlos. Declararlos es media hora; lo que este arnes vigila es lo
 * otro, que es donde estan las dos trampas.
 *
 * <h2>Trampa 1: una guarda que compruebe «se dibujan las obras» pasa en verde
 * con un importe inventado</h2>
 *
 * Una obra complementaria **no tiene cifra que ensenar**: el Anexo III de la
 * R.M. 277-2025-VIVIENDA no esta transcrito en el corpus y `otra_instalacion` no
 * tiene columna de importe. O sea que la unica manera de que la tabla ensene un
 * valor es que alguien lo invente, y un valor inventado se pinta EXACTAMENTE
 * igual que uno leido. Asi que no basta con exigir que la seccion exista: se
 * exige que **toda cifra de esa seccion venga de la respuesta**, comparando los
 * numeros del DOM contra los del JSON que la pagina recibio. Un `0.00` de
 * relleno, un `S/` o un producto calculado en Java Script salen en rojo con el
 * numero que sobra.
 *
 * Y se exige lo que el issue llama la mitad util: que la pantalla **diga que
 * falta y de que depende**. Se comprueba en dos sitios a proposito —que el
 * motivo nombre las dos cosas que faltan, y que ese motivo llegue a la
 * pantalla—, porque vaciar el motivo dejaria la primera mitad cumpliendose sola.
 *
 * <h2>Trampa 2: el `?historico=` es invisible desde la pantalla</h2>
 *
 * `historico` nulo significa «no lo pediste» y una lista vacia significaria «no
 * hay ninguna», que no puede pasar. Las dos respuestas salen de **la misma
 * ruta**, y lo unico que las separa es un parametro que ninguna pantalla ensena.
 * Con la peticion siempre `historico=true`, la ficha vigente pagaria todas las
 * versiones y **se veria idéntica**; sin el parametro, la pestana de movimientos
 * saldria diciendo «no se pidio» y tampoco reventaria nada. Por eso se miden las
 * dos direcciones sobre el mismo predio: lo que viaja en la URL y lo que vuelve
 * en el cuerpo, en la pestana que lo pinta y en la que no.
 *
 * <h2>Que haria pasar esto en verde con el defecto puesto</h2>
 *
 * Que no se pidiera ni una ficha. Si la pantalla volviera a componer el detalle
 * con la GRILLA —que es de donde salia antes de #46—, ninguna de las
 * comprobaciones de abajo tendria sobre que correr y todas serian ciertas sobre
 * el conjunto vacio. Por eso lo primero que se mide es cuantas filas de cada
 * clase se han visto, y si falta alguna esto sale con **2** y no con 0.
 *
 * Necesita una vista previa levantada y el Chromium de Playwright.
 */
import { chromium } from 'playwright-core';
import { leerModulo } from './registro.mjs';
import { baseDeLaApp } from './base.mjs';
import { emisorDeMentira } from './emisor.mjs';

/* `CATASTRO_BASE` es el ORIGEN; la base de la aplicacion —«/catastro/»— la pone
   `base.mjs` leyendola de `vite.config.ts`, que es quien la decide. Escribirla aqui
   seria el noveno literal que se queda viejo el dia que cambie (ver ese archivo). */
const BASE = baseDeLaApp(process.env.CATASTRO_BASE ?? 'http://localhost:5190');

const { RUTA_DE_LA_FICHA, TIPOS_DE_FICHA } = await leerModulo('src/api/catastro.ts', '.modulo-ficha-api');
const { RAIZ } = await leerModulo('src/api/cliente.ts', '.modulo-ficha-cliente');
const { FICHA, MOTIVOS } = await leerModulo('src/datos/catastro.ts', '.modulo-ficha-datos');

/**
 * Los predios que se miran, uno por clase de ficha y con lo que cada uno trae.
 *
 * **No es una lista por completismo.** Los tres bloques de detalle son nulos
 * salvo el que toca y la ficha UNICA no tiene ninguno, asi que mirando solo el
 * predio 1 los tres bloques que #46 dibuja no se dibujarian NUNCA y este arnes
 * informaria en verde sobre codigo que ninguna vista alcanza. Los sujetos son
 * los del padron de demostracion, que se deriva de `infra/carga-de-datos/`.
 */
const RECORRIDO = [
  { sujeto: '1', vista: 'ficha', clase: 'UNICA', nombre: 'ficha unica con obra complementaria' },
  { sujeto: '5', vista: 'ficha', clase: 'ECONOMICA', nombre: 'ficha economica con obra y actividad' },
  { sujeto: '14', vista: 'ficha', clase: 'BIENES_COMUNES', nombre: 'ficha de bienes comunes' },
  { sujeto: '21', vista: 'ficha', clase: 'RURAL', nombre: 'ficha rural' },
  { sujeto: '20', vista: 'ficha', clase: 'UNICA', nombre: 'ficha de un terreno sin construir' },
  { sujeto: '1', vista: 'movimientos', clase: 'UNICA', nombre: 'movimientos del predio' },
];

/** Cuanto tiene que haberse visto para que esto haya medido algo. */
const MINIMOS = {
  'obras complementarias': 2,
  actividades: 1,
  'bienes comunes': 2,
  participaciones: 2,
  tierras: 2,
  colindantes: 4,
  versiones: 2,
  construcciones: 3,
};

const fallos = [];

/* ── 1. El motivo dice QUE falta, antes de mirar ninguna pantalla ───────── */

/**
 * Las dos cosas que faltan para que una obra complementaria tenga valor.
 *
 * Se comprueban sobre el motivo declarado y no sobre el DOM, y hacen falta las
 * dos: son dos huecos distintos que se cierran por separado —transcribir el
 * Anexo III es de `normativa`, anadir la columna es una migracion— y un motivo
 * que solo nombrara uno mandaria a arreglar la mitad.
 */
const LO_QUE_FALTA = [
  { que: 'el cuadro que no esta transcrito', re: /Anexo\s+III/ },
  { que: 'la columna que no existe', re: /otra_instalacion/ },
];

if (typeof MOTIVOS?.obraSinImporte !== 'string' || MOTIVOS.obraSinImporte.trim() === '') {
  console.error(
    '`MOTIVOS.obraSinImporte` no existe o esta vacio en `src/datos/catastro.ts`. Sin el, la seccion de\n' +
      'obras complementarias sale sin columna de valor y SIN DECIR POR QUE, que se lee como una tabla a\n' +
      'la que le falta una columna — y ese hueco se acaba cerrando con un cero.',
  );
  process.exit(2);
}
for (const { que, re } of LO_QUE_FALTA) {
  if (!re.test(MOTIVOS.obraSinImporte)) {
    fallos.push(
      `el motivo de las obras sin importe no nombra ${que} (${re}).\n      ` +
        'Son dos huecos distintos y se cierran por separado: sin nombrar los dos, la pantalla manda a ' +
        'arreglar la mitad.',
    );
  }
}

/* ── 2. La pagina ───────────────────────────────────────────────────────── */

/** Los cuatro caminos de lectura de ficha, sin su parametro, con la raiz puesta. */
const CAMINOS = TIPOS_DE_FICHA.map((t) => RAIZ + RUTA_DE_LA_FICHA[t].ruta.replace(/\{\w+\}.*$/, ''));

const navegador = await chromium.launch();
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 2200 } });
/* La aplicacion NO monta nada sin token: se va al emisor y vuelve. Aqui al otro lado
   hay un emisor de mentira, porque lo que este arnes mide son las pantallas y no la
   puerta —esa la mide `identidad.mjs`, sin nadie que la tape—. Ver `emisor.mjs`. */
await emisorDeMentira(contexto);

/* Anotar lo que sale por la puerta Y lo que vuelve. Con `page.route` no se veria
   nada: el proxy de datos SUSTITUYE `fetch`, asi que se envuelve con un
   `get`/`set` y el proxy se instala encima (la leccion de `errores.mjs`). */
await contexto.addInitScript((caminos) => {
  window.__fichasLeidas = [];
  let actual = globalThis.fetch;
  const envolver = (delegar) => async (entrada, opciones) => {
    const href = typeof entrada === 'string' ? entrada : entrada instanceof URL ? entrada.href : entrada.url;
    const url = new URL(href, location.origin);
    const respuesta = await delegar(entrada, opciones);
    if (caminos.some((c) => url.pathname.startsWith(c))) {
      let cuerpo = null;
      try {
        cuerpo = await respuesta.clone().json();
      } catch {
        cuerpo = null;
      }
      window.__fichasLeidas.push({
        camino: url.pathname,
        historico: url.searchParams.get('historico'),
        estado: respuesta.status,
        cuerpo,
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
}, CAMINOS);

const pagina = await contexto.newPage();

/** La `<section>` cuyo titulo es este. `Seccion` dibuja el titulo en un `h2`. */
const seccionDe = (titulo) =>
  pagina.locator('section').filter({ has: pagina.locator('h2', { hasText: titulo }) });

/** Todo numero de un texto, como el ojo lo lee: `"42.00"`, `"1,234.00"`, `"1998"`. */
const cifrasDe = (texto) => [...texto.matchAll(/\d[\d.,]*/g)].map((x) => x[0].replace(/[.,]$/, ''));

/** Y todas las que un valor del JSON contiene, para poder compararlas. */
function cifrasDelDato(valor) {
  if (valor === null || valor === undefined) return [];
  return cifrasDe(String(valor));
}

const vistos = Object.fromEntries(Object.keys(MINIMOS).map((k) => [k, 0]));
let medidas = 0;
/** Lo que contesto la misma ruta en cada pestana, para contrastar el parametro. */
const historicoPorVista = {};

for (const caso of RECORRIDO) {
  const hash = `#/catastro/predios/${caso.sujeto}?ver=${caso.vista}`;
  await pagina.goto(`${BASE}/${hash}`, { waitUntil: 'networkidle' });
  await pagina.waitForTimeout(900);

  const leidas = await pagina.evaluate(() => window.__fichasLeidas ?? []);
  await pagina.evaluate(() => {
    window.__fichasLeidas = [];
  });
  const suya = leidas.at(-1);

  if (suya === undefined) {
    await navegador.close();
    console.error(
      `\nEn «${hash}» no se pidio NINGUNA ficha: ninguna peticion salio a las cuatro rutas de lectura\n` +
        `—${CAMINOS.join(', ')}—.\n` +
        'Asi que este arnes no midio nada y pasaria en verde con el defecto exacto que existe para\n' +
        'atrapar: componer el detalle con la GRILLA, que es de donde salia antes de #46 y que no trae\n' +
        'ni obras complementarias, ni bloque de detalle, ni historico.',
    );
    process.exit(2);
  }
  medidas++;

  if (suya.estado !== 200) {
    fallos.push(
      `«${caso.nombre}» (${hash}): la lectura de la ficha contesto ${suya.estado} y no 200 — ` +
        `«${suya.camino}».\n      Con el tipo mal elegido el backend no devuelve un bloque vacio: ` +
        'contesta que ese predio no tiene ficha de esa clase.',
    );
    continue;
  }

  const ficha = suya.cuerpo ?? {};
  const detalle = pagina.locator('[data-split="1"] > div:nth-child(2)');
  const textoDelDetalle = await detalle.innerText();

  /* ── El `?historico=`, en las dos direcciones ─────────────────────────── */

  const pidioHistorico = suya.historico === 'true';
  const deberia = caso.vista === 'movimientos';
  if (pidioHistorico !== deberia) {
    fallos.push(
      `«${caso.nombre}» (${hash}): la peticion ${pidioHistorico ? 'LLEVA' : 'NO lleva'} «historico=true» y ` +
        `tenia que ${deberia ? 'llevarlo' : 'no llevarlo'}.\n      ` +
        (deberia
          ? 'Sin el parametro esa pestana no recibe ninguna version y sale diciendo «no se pidio», que es ' +
            'una pantalla vacia sin ningun error.'
          : 'Con el parametro puesto siempre, la ficha vigente paga la ficha entera repetida una vez por ' +
            'version y se ve exactamente igual: no lo delata nada.'),
    );
  }
  if (deberia && !Array.isArray(ficha.historico)) {
    fallos.push(
      `«${caso.nombre}» (${hash}): esta pestana pinta las versiones y el cuerpo trae «${ficha.historico}».\n` +
        '      Nulo significa «no lo pediste», asi que lo que sale es ese aviso y no los movimientos.',
    );
  }
  if (!deberia && ficha.historico !== null) {
    fallos.push(
      `«${caso.nombre}» (${hash}): NO se pidio el historico y el cuerpo trae ${
        Array.isArray(ficha.historico) ? `${ficha.historico.length} version(es)` : `«${ficha.historico}»`
      }.\n      Una lista vacia no puede pasar —toda ficha tiene al menos la vigente—, asi que nulo y ` +
        'lista nunca significan lo mismo.',
    );
  }
  if (caso.sujeto === '1') historicoPorVista[caso.vista] = { camino: suya.camino, historico: ficha.historico };

  /* ── Lo que la respuesta trae esta dibujado ───────────────────────────── */

  const dibuja = (que, seccion, filas, comoSeVe) => {
    vistos[que] += filas.length;
    for (const fila of filas) {
      const rastro = comoSeVe(fila);
      if (!seccion.includes(rastro)) {
        fallos.push(
          `«${caso.nombre}» (${hash}): la respuesta trae «${rastro}» en ${que} y la pantalla no lo dibuja.\n` +
            '      El servidor lo manda, el tipo lo declara y nadie lo ensena: es el hueco de #46 por el ' +
            'otro lado.',
        );
      }
    }
  };

  const textoDe = async (titulo) => {
    const s = seccionDe(titulo);
    return (await s.count()) === 0 ? null : await s.innerText();
  };

  const construidas = await textoDe(FICHA.construcciones);
  if (caso.vista === 'ficha') {
    if (construidas === null) {
      fallos.push(`«${caso.nombre}» (${hash}): no hay ninguna seccion «${FICHA.construcciones}».`);
    } else {
      dibuja('construcciones', construidas, ficha.construcciones ?? [], (c) => c.categorias);
    }
  }

  /* ── Las obras complementarias: sin importe, y diciendo por que ───────── */

  if (caso.vista === 'ficha') {
    const obras = await textoDe(FICHA.instalaciones);
    if (obras === null) {
      fallos.push(
        `«${caso.nombre}» (${hash}): no hay ninguna seccion «${FICHA.instalaciones}», y ` +
          '«instalaciones» viaja en TODA respuesta de ficha —no es anulable—.',
      );
    } else {
      const declaradas = ficha.instalaciones ?? [];
      dibuja('obras complementarias', obras, declaradas, (o) => o.descripcion);

      if (declaradas.length > 0 && !obras.includes(MOTIVOS.obraSinImporte)) {
        fallos.push(
          `«${caso.nombre}» (${hash}): la seccion dibuja ${declaradas.length} obra(s) y NO ensena el motivo ` +
            'de que no tengan valor.\n      Una tabla de obras sin columna de importe y sin explicacion se ' +
            'lee como una tabla a la que le falta una columna, y ese hueco se cierra con un cero.',
        );
      }

      /* La comprobacion que no se puede pasar inventando: toda cifra de la
         seccion tiene que venir del cuerpo. Se quita antes el motivo, que
         nombra una resolucion ministerial y un anexo. */
      const admitidas = new Set([String(declaradas.length)]);
      for (const o of declaradas) {
        for (const v of [o.descripcion, o.unidad, o.cantidad, o.anioConstruccion, o.estadoConservacion]) {
          for (const cifra of cifrasDelDato(v)) admitidas.add(cifra);
        }
      }
      const sinElMotivo = obras.split(MOTIVOS.obraSinImporte).join(' ');
      const inventadas = [...new Set(cifrasDe(sinElMotivo))].filter((c) => !admitidas.has(c));
      if (inventadas.length > 0) {
        fallos.push(
          `«${caso.nombre}» (${hash}): la seccion de obras complementarias ensena ${inventadas.length} cifra(s) ` +
            `que la respuesta NO trae: ${inventadas.join(', ')}.\n      ` +
            'Una obra complementaria no tiene valor que ensenar —el Anexo III no esta transcrito y ' +
            '«otra_instalacion» no tiene columna de importe—, asi que cualquier cifra de mas aqui esta ' +
            'inventada, y se pinta igual que una leida.',
        );
      }
      if (/S\/\s?\d/.test(obras)) {
        fallos.push(
          `«${caso.nombre}» (${hash}): la seccion de obras complementarias ensena un IMPORTE. Aqui no hay ` +
            'ninguno que ensenar.',
        );
      }
    }
  }

  /* ── Los tres bloques de detalle, y su ausencia ───────────────────────── */

  const bloques = [
    { campo: 'economico', titulo: FICHA.economico, clase: 'ECONOMICA' },
    { campo: 'bienesComunes', titulo: FICHA.bienesComunes, clase: 'BIENES_COMUNES' },
    { campo: 'rural', titulo: FICHA.rural, clase: 'RURAL' },
  ];
  if (caso.vista === 'ficha') {
    for (const bloque of bloques) {
      const cuerpo = ficha[bloque.campo] ?? null;
      const texto = await textoDe(bloque.titulo);
      if (cuerpo === null && texto !== null) {
        fallos.push(
          `«${caso.nombre}» (${hash}): el cuerpo trae «${bloque.campo}» NULO y la pantalla dibuja su seccion.\n` +
            '      Un nulo significa «esta ficha no es de las que lo declaran», no «no declara nada»: una ' +
            'tabla de actividades vacia sobre una ficha rural se leeria como un local sin licencia.',
        );
      }
      if (cuerpo !== null && texto === null) {
        fallos.push(
          `«${caso.nombre}» (${hash}): el cuerpo trae «${bloque.campo}» y la pantalla no dibuja su seccion.`,
        );
      }
      if (cuerpo !== null && texto !== null) {
        if (bloque.campo === 'economico') {
          dibuja('actividades', texto, cuerpo.actividades ?? [], (a) => a.conductor);
        }
        if (bloque.campo === 'bienesComunes') {
          dibuja('bienes comunes', texto, cuerpo.bienes ?? [], (b) => b.descripcion);
          dibuja('participaciones', texto, cuerpo.participaciones ?? [], (p) => p.porcentaje);
          if (!texto.includes(cuerpo.areaComunTotal)) {
            fallos.push(
              `«${caso.nombre}» (${hash}): el area comun total «${cuerpo.areaComunTotal}» no se dibuja. ` +
                'La suma la hace el dominio, no esta pantalla.',
            );
          }
        }
        if (bloque.campo === 'rural') {
          dibuja('tierras', texto, cuerpo.tierras ?? [], (t) => t.hectareas);
          dibuja('colindantes', texto, cuerpo.colindantes ?? [], (c) => c.descripcion);
          if (!texto.includes(cuerpo.hectareasTotales)) {
            fallos.push(
              `«${caso.nombre}» (${hash}): la superficie total «${cuerpo.hectareasTotales}» no se dibuja, ` +
                'y va CON su unidad: quien lea hectareas como metros calcularia diez mil veces de menos.',
            );
          }
        }
      }
    }
  }

  /* ── Los movimientos ──────────────────────────────────────────────────── */

  if (caso.vista === 'movimientos' && Array.isArray(ficha.historico)) {
    const texto = await textoDe(FICHA.movimientos);
    if (texto === null) {
      fallos.push(`«${caso.nombre}» (${hash}): no hay ninguna seccion «${FICHA.movimientos}».`);
    } else {
      dibuja('versiones', texto, ficha.historico, (v) => v.documentoOrigen);
      for (const v of ficha.historico) {
        if (!texto.includes(v.observacion)) {
          fallos.push(
            `«${caso.nombre}» (${hash}): la version ${v.version} se dibuja sin su observacion.\n      ` +
              'Es la mitad util del historico: un cambio de area dice que paso de una cifra a otra, y solo ' +
              'la observacion dice si fue una fiscalizacion de campo o un error de tecleo.',
          );
        }
      }
    }
  }

  /* Y en ninguna parte del detalle sale un importe: esta ficha no publica uno. */
  if (/S\/\s?\d/.test(textoDelDetalle)) {
    fallos.push(
      `«${caso.nombre}» (${hash}): el detalle del predio ensena un importe con simbolo de moneda. ` +
        'Ninguna lectura de ficha publica un solo importe.',
    );
  }
}

await navegador.close();

/* ── 3. El contraste que hace que el parametro signifique algo ──────────── */

const enFicha = historicoPorVista.ficha;
const enMovimientos = historicoPorVista.movimientos;
if (enFicha === undefined || enMovimientos === undefined) {
  console.error(
    '\nNo se midieron las dos pestanas del MISMO predio, asi que no se comparo la respuesta de la misma\n' +
      'ruta con y sin «historico=true» — que es lo unico que hace que el parametro signifique algo.',
  );
  process.exit(2);
}
if (enFicha.camino !== enMovimientos.camino) {
  console.error(
    `\nLas dos pestanas del predio 1 leyeron rutas distintas —«${enFicha.camino}» y «${enMovimientos.camino}»—,\n` +
      'asi que lo que se comparo no es el efecto del parametro sino el de la ruta. Este arnes no midio\n' +
      'lo que existe para medir.',
  );
  process.exit(2);
}
if (enFicha.historico !== null || !Array.isArray(enMovimientos.historico)) {
  const comoVino = (h) => (h === null ? 'nulo' : `${Array.isArray(h) ? h.length : '?'} version(es)`);
  fallos.push(
    'la MISMA ruta contesta lo mismo con y sin «historico=true», asi que el parametro no decide nada:\n' +
      `      sin el vino ${comoVino(enFicha.historico)} y con el ${comoVino(enMovimientos.historico)}.\n` +
      '      Nulo es «no lo pediste» y una lista vacia seria «no hay ninguna», que no puede pasar: si las ' +
      'dos respuestas son iguales, una de las dos miente.',
  );
}

/* ── 4. Y que esto haya medido algo ─────────────────────────────────────── */

console.log(
  `${medidas} vista(s) de ficha medidas sobre ${new Set(RECORRIDO.map((c) => c.sujeto)).size} predios y las ` +
    `${TIPOS_DE_FICHA.length} clases · ` +
    Object.entries(vistos)
      .map(([que, n]) => `${n} ${que}`)
      .join(' · '),
);

/* Los problemas se imprimen ANTES de decidir el codigo de salida, y no despues:
   una rotura que ademas deja de dibujar filas —quitarle el `?historico=`, por
   ejemplo— haria saltar las dos cosas, y con el «no midio nada» delante los
   problemas concretos, que son los que dicen QUE se rompio, no se verian. */
if (fallos.length) {
  console.error(`\n${fallos.length} problema(s) con lo que la ficha dibuja:\n`);
  for (const f of fallos) console.error('  - ' + f + '\n');
  console.error(
    '  Una obra complementaria no tiene valor que ensenar y el historico no viaja si no se pide: las dos\n' +
      '  cosas se ven igual de bien cuando estan mal.',
  );
}

const cortos = Object.entries(MINIMOS).filter(([que, minimo]) => vistos[que] < minimo);
if (medidas < RECORRIDO.length || cortos.length > 0) {
  console.error(
    `\nY ademas, se midieron ${medidas} de las ${RECORRIDO.length} vistas` +
      (cortos.length
        ? ` y faltaron filas que mirar:\n${cortos.map(([q, m]) => `  · ${q}: ${vistos[q]} de ${m}`).join('\n')}`
        : '') +
      '\n\nUna comprobacion sobre el conjunto vacio es cierta siempre: sin filas de cada clase, «lo que la\n' +
      'respuesta trae esta dibujado» se cumpliria sola. O el padron de demostracion cambio, o la pantalla\n' +
      'dejo de pedir la ficha entera.',
  );
  process.exit(2);
}
if (fallos.length) process.exit(1);

console.log(
  'toda fila que la respuesta trae esta dibujada, las obras salen sin importe diciendo que falta, y el\n' +
    'historico viaja solo en la pestana que lo pinta',
);
