/**
 * El desplegable de ejercicios sale del RELOJ, y se mide moviendolo.
 *
 *   CATASTRO_BASE=http://localhost:5210 node verificaciones/ejercicios.mjs
 *
 * <h2>Que existe para impedir</h2>
 *
 * La lista eran cuatro literales congelados en `Shell.tsx` —`['2026','2025',
 * '2024','2023']`— y el ejercicio no es cromo: decide de que conjunto sellado se
 * leen los tres cuadros y viaja como `?ejercicio=` a las tres lecturas. El 1 de
 * enero de 2027 nadie podia elegir 2027; la interfaz se quedaba en 2026,
 * ensenaba los cuadros de 2026 **y parecia correcta**, sin error y sin aviso.
 *
 * <h2>Por que este arnes mueve el reloj, y por que si no lo moviera no mediria nada</h2>
 *
 * Porque el defecto es **invisible el dia que se arregla**: hoy el ano en curso
 * es 2026 y la lista derivada del reloj sale letra por letra igual que los cuatro
 * literales. Asi que:
 *
 *   · una guarda que compruebe «el desplegable ofrece cuatro anos» pasa en verde
 *     con los literales puestos;
 *   · una que compruebe «ofrece el ano en curso» pasa en verde **hoy** con 2026
 *     escrito a mano, y se pondra roja sola el 1 de enero —o sea, avisa del
 *     defecto justo cuando ya ha ocurrido, que es lo mismo que no avisar—.
 *
 * La unica afirmacion que muerde hoy es «con el reloj en OTRO ano, el desplegable
 * ofrece los de ESE ano», y por eso el navegador se abre con el reloj fijado en
 * dos anos que no son este. La pregunta de vuelta —**que haria pasar en verde a
 * esta guarda con el defecto puesto**— tiene una sola respuesta: que el reloj no
 * se hubiera movido de verdad dentro de la pagina. Eso se comprueba antes de
 * comparar nada, y si no se movio esto sale con **2** y no con 0.
 *
 * <h2>Las dos mitades, y por que hacen falta las dos</h2>
 *
 *   1. **La funcion pura**, compilada al vuelo desde `src/shell/ejercicios.ts` y
 *      llamada con fechas fijadas —incluidos los dos lados de la medianoche del
 *      31 de diciembre—. Caza un desajuste de uno en el recuento sin abrir
 *      navegador.
 *   2. **La pagina**, con el reloj movido. La funcion pura sola pasaria en verde
 *      con `Shell.tsx` sin tocar: nada obliga a que la interfaz la USE. Y ademas
 *      es la unica que mide el valor **elegido**, que estaba congelado en otro
 *      archivo —`App.tsx`— con el mismo literal.
 *
 * <h2>Y el elegido se mide por lo que VIAJA, no por lo que ensena el desplegable</h2>
 *
 * Esto costo una medida y cambio el arnes. La primera version leia `select.value`
 * y **paso en verde** con el valor inicial congelado en `App.tsx`: un `<select>`
 * cuyo `value` no casa con ninguna opcion no se queda vacio —el navegador cae a
 * la **primera opcion**—, asi que con el reloj en 2031 la barra ensenaba «2031»
 * mientras la aplicacion seguia teniendo «2026» y pedia los tres cuadros con
 * `?ejercicio=2026`. Es peor que el defecto original: la pantalla dice un ano y
 * lee otro, y las dos cosas son plausibles.
 *
 * Asi que lo que se mide es el parametro que sale por la puerta: se envuelve
 * `globalThis.fetch` —el proxy de datos lo SUSTITUYE, asi que el enrutador de
 * Playwright no ve ninguna peticion (la leccion de `errores.mjs`)— y se recorre
 * la hoja que lee los tres cuadros. El ano que viaja en `?ejercicio=` es el que
 * decide de que conjunto sellado se lee, que es de lo que habla el issue.
 *
 * **Lo que se ENSENA y lo que VIAJA se comprueban los dos**, y hacen falta los
 * dos porque se rompen en direcciones opuestas. Medido con las dos mitades del
 * defecto, cada una puesta sola: con la lista congelada en `Shell.tsx`, la
 * pantalla PIDE 2031 y ENSENA 2026; con el valor inicial congelado en `App.tsx`,
 * ENSENA 2031 y PIDE 2026. Cualquiera de las dos comprobaciones sola deja pasar
 * la otra mitad, y las dos mitades son plausibles mirando la pantalla.
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

/** Donde vive el desplegable en la barra global. */
const DESPLEGABLE = 'select[aria-label="Ejercicio de trabajo"]';

/**
 * La hoja que lee los tres cuadros del ejercicio.
 *
 * No vale un destino cualquiera: el armazon esta en todos, pero `?ejercicio=`
 * solo sale por la puerta donde alguien lo lee, y es aqui —aranceles, valores
 * unitarios y depreciacion—.
 */
const HASH = '#/catastro/valores';

const { ejerciciosOfrecidos, ejercicioEnCurso } = await leerModulo('src/shell/ejercicios.ts', '.modulo-ejercicios');
const { RAIZ } = await leerModulo('src/api/cliente.ts', '.modulo-ejercicios-cliente');

if (typeof ejerciciosOfrecidos !== 'function' || typeof ejercicioEnCurso !== 'function') {
  console.error(
    '`src/shell/ejercicios.ts` no exporta `ejerciciosOfrecidos` y `ejercicioEnCurso`: sin las dos\n' +
      'funciones puras este arnes no tiene con que comparar lo que dibuja la pagina, y comparar la\n' +
      'pagina consigo misma seria cumplirse solo.',
  );
  process.exit(2);
}

const fallos = [];

/* ── 1. La funcion pura, con la fecha fijada ────────────────────────────── */

/**
 * Los dos lados de la medianoche que el issue nombra, un ano lejano y un 29 de
 * febrero. Las fechas se escriben SIN zona a proposito: asi se leen como hora
 * local, que es de donde sale `getFullYear()` — el ejercicio de una municipalidad
 * empieza a su medianoche y no a la de Greenwich.
 */
const CASOS = [
  { cuando: '2026-12-31T23:59:59', ofrece: ['2026', '2025', '2024', '2023'] },
  { cuando: '2027-01-01T00:00:01', ofrece: ['2027', '2026', '2025', '2024'] },
  { cuando: '2031-06-15T12:00:00', ofrece: ['2031', '2030', '2029', '2028'] },
  { cuando: '2024-02-29T12:00:00', ofrece: ['2024', '2023', '2022', '2021'] },
];

for (const caso of CASOS) {
  const hoy = new Date(caso.cuando);
  if (Number.isNaN(hoy.getTime())) {
    console.error(`«${caso.cuando}» no es una fecha que Node sepa leer: este caso no mediria nada.`);
    process.exit(2);
  }
  const ofrecidos = [...ejerciciosOfrecidos(hoy)];
  if (ofrecidos.join(' ') !== caso.ofrece.join(' ')) {
    fallos.push(
      `la funcion pura, el ${caso.cuando}: ofrece [${ofrecidos.join(', ')}] y tenia que ofrecer ` +
        `[${caso.ofrece.join(', ')}]`,
    );
  }
  const enCurso = ejercicioEnCurso(hoy);
  if (enCurso !== caso.ofrece[0]) {
    fallos.push(
      `la funcion pura, el ${caso.cuando}: el ejercicio en curso es «${enCurso}» y tenia que ser ` +
        `«${caso.ofrece[0]}». El elegido tiene que ser el PRIMERO de los ofrecidos: si no, el ` +
        'desplegable se dibuja con un valor que no es ninguna de sus opciones',
    );
  }
}

/* ── 2. La pagina, con el reloj movido ──────────────────────────────────── */

const ESTE_ANIO = new Date().getFullYear();

/**
 * Los anos a los que se mueve el reloj.
 *
 * Dos, y ninguno es este. Con uno solo, un arnes que se equivocara en el mismo
 * sentido que el codigo —un desplazamiento fijo, pongamos— no se distinguiria de
 * uno correcto. Uno hacia adelante y otro hacia atras, y los dos lo bastante
 * lejos como para que su lista no comparta **ni un ano** con la de hoy: si
 * compartiera alguno, una coincidencia parcial podria leerse como acierto.
 */
const RELOJES = [ESTE_ANIO + 5, ESTE_ANIO - 7];

const deHoy = new Set(ejerciciosOfrecidos(new Date()));
for (const anio of RELOJES) {
  if (anio === ESTE_ANIO) {
    console.error(
      `Se iba a mover el reloj a ${anio}, que es EL ANO EN CURSO: con el reloj sin mover, cuatro\n` +
        'literales congelados dan exactamente la misma lista que el reloj, asi que esta comprobacion\n' +
        'pasaria en verde con el defecto que existe para atrapar.',
    );
    process.exit(2);
  }
  const compartidos = [...ejerciciosOfrecidos(new Date(`${anio}-06-15T12:00:00`))].filter((a) => deHoy.has(a));
  if (compartidos.length) {
    console.error(
      `El reloj movido a ${anio} ofreceria [${compartidos.join(', ')}], que tambien ofrece el ano en\n` +
        'curso. Con anos compartidos, una lista congelada podria acertar por casualidad y este arnes\n' +
        'lo leeria como un acierto. Aleja el reloj.',
    );
    process.exit(2);
  }
}

const navegador = await chromium.launch();
let medidos = 0;

for (const anio of RELOJES) {
  const contexto = await navegador.newContext({ viewport: { width: 1440, height: 900 } });
  /* La aplicacion NO monta nada sin token: se va al emisor y vuelve. Aqui al otro lado
     hay un emisor de mentira, porque lo que este arnes mide son las pantallas y no la
     puerta —esa la mide `identidad.mjs`, sin nadie que la tape—. Ver `emisor.mjs`. */
  await emisorDeMentira(contexto);

  /* Anotar lo que sale por la puerta. Con `page.route` no se veria nada: el
     proxy de datos SUSTITUYE `fetch` y las peticiones no llegan a la red, asi
     que se envuelve con un `get`/`set` y el proxy se instala encima. */
  await contexto.addInitScript((raiz) => {
    window.__ejerciciosPedidos = [];
    let actual = globalThis.fetch;
    const envolver = (delegar) => (entrada, opciones) => {
      const href = typeof entrada === 'string' ? entrada : entrada instanceof URL ? entrada.href : entrada.url;
      const url = new URL(href, location.origin);
      if (url.pathname.startsWith(raiz) && url.searchParams.has('ejercicio')) {
        window.__ejerciciosPedidos.push(`${url.pathname}?ejercicio=${url.searchParams.get('ejercicio')}`);
      }
      return delegar(entrada, opciones);
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

  /* Antes de navegar: lo que se mide es lo que la aplicacion decide AL MONTAR, y
     el reloj se lee ahi. `setFixedTime` deja los temporizadores funcionando —React
     los necesita— y solo congela la respuesta de `Date`. */
  const fijado = new Date(`${anio}-06-15T12:00:00`);
  await pagina.clock.setFixedTime(fijado);
  await pagina.goto(`${BASE}/${HASH}`, { waitUntil: 'networkidle' });
  await pagina.waitForTimeout(700);

  /* La guarda que decide si esto mide algo: si el reloj no se movio DENTRO de la
     pagina, lo que se compare a continuacion es la lista de este ano contra la
     lista de este ano, y los cuatro literales la pasan en verde. */
  const anioDeLaPagina = await pagina.evaluate(() => new Date().getFullYear());
  if (anioDeLaPagina !== anio) {
    await navegador.close();
    console.error(
      `\nSe fijo el reloj del navegador en ${anio} y la pagina dice que esta en ${anioDeLaPagina}: el\n` +
        'reloj NO se movio, asi que este arnes no midio nada y pasaria en verde con el defecto exacto\n' +
        'que existe para atrapar —cuatro anos escritos a mano que hoy coinciden con el reloj—.\n' +
        'O `page.clock` dejo de instalarse antes de navegar, o la pagina lee la fecha de otro sitio.',
    );
    process.exit(2);
  }

  const desplegable = await pagina.$(DESPLEGABLE);
  if (desplegable === null) {
    await navegador.close();
    console.error(
      `\nNo hay ningun «${DESPLEGABLE}» en «${HASH}»: no se encontro el desplegable de ejercicios, asi\n` +
        'que no se midio nada. O la barra global dejo de dibujarlo, o cambio su etiqueta —y entonces\n' +
        'hay que cambiarla tambien aqui, que es el precio de buscarlo por etiqueta—.',
    );
    process.exit(2);
  }

  const dibujado = await pagina.evaluate((sel) => {
    const select = document.querySelector(sel);
    return {
      opciones: [...select.options].map((o) => o.value),
      elegido: select.value,
      /* -1 es el `<select>` cuyo valor no casa con ninguna opcion: no ensena
         nada elegido, no avisa, y es lo que pasaria si la lista viniera del
         reloj y el valor inicial siguiera congelado. */
      indice: select.selectedIndex,
    };
  }, DESPLEGABLE);
  medidos++;

  const esperados = [...ejerciciosOfrecidos(fijado)];
  if (dibujado.opciones.join(' ') !== esperados.join(' ')) {
    fallos.push(
      `con el reloj en ${anio}, el desplegable ofrece [${dibujado.opciones.join(', ')}] y tenia que ` +
        `ofrecer [${esperados.join(', ')}].\n      La lista no sale del reloj: el ${anio} nadie podria ` +
        'elegir su propio ejercicio, y la interfaz no diria nada.',
    );
  }

  const enCurso = ejercicioEnCurso(fijado);
  if (dibujado.elegido !== enCurso) {
    fallos.push(
      `con el reloj en ${anio}, el desplegable ENSENA «${dibujado.elegido || '(nada)'}» elegido y tenia ` +
        `que ensenar «${enCurso}».\n      El valor inicial tambien sale del reloj, y esta en \`App.tsx\`: ` +
        'es el segundo sitio donde el mismo ano estaba escrito a mano.',
    );
  }
  if (dibujado.indice < 0) {
    fallos.push(
      `con el reloj en ${anio}, el valor elegido «${dibujado.elegido}» NO es ninguna de las opciones ` +
        `[${dibujado.opciones.join(', ')}]:\n      el desplegable se dibuja sin nada elegido y sin decir ` +
        'nada. Los dos —la lista y el elegido— tienen que salir del mismo instante.',
    );
  }

  /* Y lo que de verdad decide de que conjunto sellado se lee: el ano que VIAJA.
     Lo que ensena el desplegable no basta y esta medido —con el valor inicial
     congelado, el navegador cae a la primera opcion y la barra ensena el ano
     bueno mientras la aplicacion pide otro—. */
  const pedidos = await pagina.evaluate(() => window.__ejerciciosPedidos ?? []);
  if (pedidos.length === 0) {
    await navegador.close();
    console.error(
      `\nNinguna lectura de «${HASH}» llevo «?ejercicio=», asi que no se midio el unico dato que decide\n` +
        'de que conjunto sellado se leen los cuadros. O la hoja dejo de pedirlos, o la envoltura de\n' +
        '`fetch` dejo de ver las peticiones —el proxy de datos SUSTITUYE `fetch`, y si se instala antes\n' +
        'que esta envoltura, pasan por debajo—. En cualquiera de los dos casos esto no mide nada.',
    );
    process.exit(2);
  }
  const conOtroAnio = [...new Set(pedidos)].filter((p) => !p.endsWith(`?ejercicio=${enCurso}`));
  if (conOtroAnio.length) {
    fallos.push(
      `con el reloj en ${anio}, ${conOtroAnio.length} lectura(s) piden un ejercicio que no es «${enCurso}»:\n` +
        `      ${conOtroAnio.join('\n      ')}\n      Es el ano que decide de que conjunto sellado se lee, ` +
        'y el desplegable puede estar ensenando otro: un `<select>` cuyo valor\n      no case con ninguna ' +
        'opcion cae a la primera y no avisa de nada.',
    );
  }

  await contexto.close();
}

await navegador.close();

/* Un recorrido que no mira ni una pagina no informa de nada. Es la leccion de
   `mirar.mjs` y de `impedimentos.mjs`, por el mismo eje. */
if (medidos < RELOJES.length) {
  console.error(
    `\nSe midieron ${medidos} de los ${RELOJES.length} relojes movidos, asi que este arnes no llego a\n` +
      'comprobar lo que existe para comprobar. Un recorrido a medias no es un recorrido en verde.',
  );
  process.exit(2);
}

console.log(
  `${CASOS.length} fecha(s) fijadas sobre la funcion pura y ${medidos} reloj(es) movido(s) en el ` +
    `navegador —${RELOJES.join(' y ')}, y el ano en curso es ${ESTE_ANIO}—`,
);

if (fallos.length) {
  console.error(`\n${fallos.length} problema(s) con los ejercicios que se ofrecen:\n`);
  for (const f of fallos) console.error('  - ' + f + '\n');
  console.error(
    '  El ejercicio decide de que conjunto sellado se leen los tres cuadros: una lista congelada deja\n' +
      '  de ofrecer el ano en curso el 1 de enero, sin error y sin aviso.',
  );
  process.exit(1);
}

console.log('el desplegable ofrece el ejercicio en curso y los tres cerrados, y los dos salen del reloj');
