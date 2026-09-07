/**
 * Que lo que `cliente.ts` distingue LLEGUE a la pantalla, y no solo a `Fallo`.
 *
 *   node verificaciones/errores.mjs [superficie]
 *
 * `src/api/cliente.ts` se toma el trabajo de distinguir doce codigos,
 * `faltaUnaCifraNormativa`, `reintentable`, `detalles` e `incidencia`. La
 * revision del armazon (#41) midio que de las 29 lecturas de `src/modulos`, 26
 * pasan por `<Lectura>` -> `Fallo` y ahi las cuatro llegan intactas. **Tres no
 * pasaban** (#49), y las tres eran invisibles de la peor manera:
 *
 *   1. el catalogo vial en 403 dejaba la tabla de aranceles ENTERA con «Via 1»,
 *      que no se distingue del nombre de una via — ni datos ni error;
 *   2. el «Reintentar» del Panel salia dos veces y re-pedia dos de las cuatro
 *      lecturas, asi que el padron y el plano quedaban muertos hasta recargar;
 *   3. `Consultas.bajar()` se quedaba con `e.mensaje` en el `catch` y tiraba el
 *      codigo, los detalles, la incidencia y el `reintentable`.
 *
 * <h2>Por que este arnes mira ESCENARIOS y no «hay un aviso»</h2>
 *
 * Los tres defectos pasan en verde ante una guarda que solo pregunte si sale
 * algo: el primero ensena datos plausibles, el segundo un boton que parece
 * funcionar, el tercero un mensaje que parece completo. Lo que hay que medir es
 * que **un 403, un 500 con incidencia y un 422 con `parametroQueFalta`
 * produzcan pantallas DISTINTAS**, que es la afirmacion que el defecto rompe.
 *
 * Y por eso **los seis escenarios comparten el mismo `mensaje`**. Es la decision
 * central de este archivo: con mensajes distintos, la comprobacion de
 * distinguibilidad se cumpliria sola —bastaria con pintar `error.mensaje`, que
 * es exactamente el defecto 3— y no mediria nada. Con el mensaje fijo, lo unico
 * que puede separar dos pantallas es lo que `cliente.ts` distingue.
 *
 * <h2>Y por que lo esperado se DERIVA del fuente</h2>
 *
 * Los titulos de los doce codigos, la regla de `reintentable` y la frase de una
 * linea salen de `src/api/cliente.ts` y de `src/ds/componentes.tsx`, compilados
 * al vuelo. Copiarlos aqui crearia un segundo sitio con la misma verdad —la
 * forma de defecto que C-17 encontro cinco veces— y el dia que alguien cambiara
 * un titulo, este arnes seguiria en verde afirmando sobre el titulo viejo.
 *
 * <h2>Como se inyecta el error</h2>
 *
 * `page.route` no sirve: con el proxy de datos encendido las peticiones no
 * llegan a la red —el proxy SUSTITUYE `fetch`—, asi que el enrutador de
 * Playwright no ve ninguna. Se envuelve `globalThis.fetch` con un `get`/`set`
 * antes de que la pagina cargue: el proxy se instala encima, el `set` lo captura
 * y el `get` devuelve el proxy envuelto. Con eso se rompe **una sola ruta** y
 * todo lo demas lo sigue contestando el proxy en 200, que es el caso que
 * importa: un usuario con `aranceles` y sin `calles`.
 *
 * Necesita una vista previa levantada (`yarn dev` o `vite preview`) y el
 * Chromium de Playwright.
 */
import { chromium } from 'playwright-core';
import { leerModulo } from './registro.mjs';

const { ErrorDeApi, RAIZ } = await leerModulo('src/api/cliente.ts', '.registro-errores-cliente');
const { tituloDeError, motivoCorto } = await leerModulo('src/ds/componentes.tsx', '.registro-errores-ds');
const { EJERCICIO } = await leerModulo('src/simulado/datos.ts', '.registro-errores-simulado');
/* Los rotulos de los campos de fiscalizacion salen de `src/datos/`, que es donde
   se escriben: con una copia aqui, renombrar un campo dejaria este arnes
   rellenando un formulario que ya no existe y midiendo el error equivocado. */
const { ACTOS, CAMPOS } = await leerModulo('src/datos/fiscalizacion.ts', '.registro-errores-fiscalizacion');

const BASE = process.env.CATASTRO_BASE ?? 'http://localhost:5190';

/**
 * El mismo mensaje en los seis. Es lo que hace que este arnes mida algo.
 *
 * Es el de verdad: lo emite `LectorDeParametros.EjercicioSinSellar`, que es la
 * condicion permanente de este sistema y por tanto el rechazo que mas se ve.
 */
const MENSAJE = `El ejercicio ${EJERCICIO} no tiene un conjunto de parametros sellado`;

/**
 * El dia en que se mira, y sale del **unico ano que el proxy sella**.
 *
 * No es cosmetica y no estaba: desde #48 la barra global pide el ANO EN CURSO, asi que
 * con el reloj de la maquina este arnes empezaria a pedir un ejercicio que el proxy no
 * tiene, y **el 404 del cuadro taparia los seis rechazos que la superficie de aranceles
 * existe para distinguir**. Medido poniendo el proxy a sellar otro ano: **21 problemas
 * sobre 36 renders** —los 6 titulos mas sus 15 pares byte a byte, que es `C(6,2)`—, y
 * sus mensajes mandan a mirar al sitio equivocado.
 *
 * Sin esto, el 1 de enero de 2027 el flujo bloqueante se pondria rojo **sin que nadie
 * hubiera cambiado una linea**. Este arnes mide como se ven los rechazos, no el
 * calendario: fijar su dia lo hace determinista sin darle un reloj al proxy, que
 * declara por escrito que no lo tiene.
 *
 * Se deriva de `EJERCICIO` y no se escribe: dos sitios con el mismo ano volverian a
 * separarse, que es de lo que trata #48.
 */
const EL_DIA = new Date(`${EJERCICIO}-06-15T12:00:00`);

/**
 * Los seis desenlaces que esta interfaz tiene que saber separar.
 *
 * **No hay un 422 «a secas»**, y no es un descuido: la tarjeta del Panel dice el
 * motivo en UNA LINEA —`motivoCorto`, que por diseno (#41) deja fuera `detalles`
 * e `incidencia`, que no caben—, asi que un 422 pelado y un 422 con detalles
 * saldrian iguales ahi y esta comprobacion estaria exigiendo lo que la decision
 * de #41 descarto. Los seis de aqui SI son separables en las dos superficies.
 */
const ESCENARIOS = [
  {
    k: 'sin-privilegio-403',
    /* Con la forma CORTA: los 403 los emite un filtro, antes del
       `DispatcherServlet`, y `RespuestaDeError` escribe cuatro campos a mano.
       Sin `type` y sin `detail`, que es lo que `cliente.ts` dice que no se
       puede leer. */
    estado: 403,
    cuerpo: { status: 403, title: MENSAJE, codigo: 'SIN_PRIVILEGIO', mensaje: MENSAJE },
  },
  {
    k: 'error-interno-500-con-incidencia',
    estado: 500,
    cuerpo: { codigo: 'ERROR_INTERNO', mensaje: MENSAJE, incidencia: 'c0ffee11-2233-4455-6677-8899aabbccdd' },
  },
  {
    k: 'validacion-422-con-detalles',
    estado: 422,
    cuerpo: {
      codigo: 'VALIDACION',
      mensaje: MENSAJE,
      detalles: ['El campo «uso» es obligatorio', 'El tramo no existe en el catalogo'],
    },
  },
  {
    k: 'validacion-422-sin-llave',
    estado: 422,
    cuerpo: { codigo: 'VALIDACION', mensaje: MENSAJE, parametroQueFalta: { ejercicio: 2026 } },
  },
  {
    k: 'validacion-422-con-llave',
    estado: 422,
    cuerpo: {
      codigo: 'VALIDACION',
      mensaje: MENSAJE,
      parametroQueFalta: { ejercicio: 2026, llave: 'VALOR_UNITARIO:MUROS:H' },
    },
  },
  /* El unico que no lo produce ningun servidor: la peticion no llego a tener
     respuesta. El mensaje lo pone `cliente.ts`, no el cable. */
  { k: 'sin-respuesta', red: 'cortada' },
];

/** El `ErrorDeApi` que `cliente.ts` compondria con ese cuerpo. Sale de `cliente.ts`. */
function errorDe(escenario) {
  if (escenario.red === 'cortada') return new ErrorDeApi('SIN_RESPUESTA', 'No se pudo contactar con el servidor', 0);
  const c = escenario.cuerpo;
  return new ErrorDeApi(c.codigo, c.mensaje, escenario.estado, c.incidencia, c.detalles, c.parametroQueFalta);
}

/**
 * Las superficies que NO pasan por `<Lectura>` -> `Fallo`, una por defecto de #49.
 *
 * `detalle` dice cuanto sitio tiene la superficie, y de ahi sale lo que se le
 * exige: `completo` es un `Fallo` entero —titulo, mensaje, detalles, incidencia,
 * lo que falta y el boton—; `una-linea` es la nota de una tarjeta, donde solo
 * cabe `motivoCorto` y por eso es lo unico que se le pide.
 */
const SUPERFICIES = [
  {
    k: 'aranceles-sin-el-catalogo-vial',
    que: 'El cuadro de aranceles cuando `/catastro/vias` no se puede leer',
    hash: '#/catastro/valores?cuadro=aranceles',
    rompe: { camino: '/catastro/vias' },
    region: (pagina) => pagina.locator('main'),
    detalle: 'completo',
    /* El dato degradado que PARECE legitimo, que es lo que hace este defecto
       peor que una pantalla en blanco: `Via 1` no se distingue del nombre de
       una via, y con el se lee «Via 1 — Sin tramo 388.00» como si fuera un
       arancel completo. */
    prohibido: [
      {
        re: /\bVia \d+\b/,
        porque:
          'es el identificador crudo pintado como si fuera el nombre de la via. Con el catalogo sin leer, ' +
          'la celda tiene que decir que no se pudo leer, no inventar un rotulo que se lee como un dato.',
      },
    ],
  },
  {
    k: 'panel-padron',
    que: 'La tarjeta «Predios en el padron» del Panel',
    hash: '#/catastro/panel',
    rompe: { camino: '/catastro/predios', exacto: true },
    region: (pagina) => pagina.getByRole('region', { name: 'Predios en el padron' }),
    detalle: 'una-linea',
  },
  {
    k: 'panel-sectores',
    que: 'La tarjeta «Sectores» del Panel',
    hash: '#/catastro/panel',
    rompe: { camino: '/catastro/sectores', exacto: true },
    region: (pagina) => pagina.getByRole('region', { name: 'Sectores' }),
    detalle: 'una-linea',
  },
  {
    k: 'panel-fichas',
    que: 'La tarjeta «Fichas versionadas» del Panel',
    hash: '#/catastro/panel',
    rompe: { camino: '/catastro/fichas', exacto: true },
    region: (pagina) => pagina.getByRole('region', { name: 'Fichas versionadas' }),
    detalle: 'una-linea',
  },
  {
    k: 'panel-plano',
    que: 'La tarjeta «Lotes con poligono» del Panel',
    hash: '#/catastro/panel',
    rompe: { camino: '/catastro/predios/plano', exacto: true },
    region: (pagina) => pagina.getByRole('region', { name: 'Lotes con poligono' }),
    detalle: 'una-linea',
  },
  /**
   * Las dos ESCRITURAS de #71, que no pasan por `<Lectura>` y no pueden.
   *
   * Una lectura fallida la dibuja `Fallo` porque `<Lectura>` la envuelve; una
   * escritura no tiene envoltorio —no hay `useRecurso` detras de un `POST`—, asi
   * que cada formulario decide por su cuenta que hace con el rechazo. Es
   * exactamente la clase de superficie donde #49 encontro sus tres defectos, y
   * en un modulo donde **los 409 son muchos y cada uno significa otra cosa**:
   * campania ya abierta, ya cerrada, transicion que no existe, hallazgo ya sin
   * efecto, huella repetida, acta repetida, sin las dos compuertas, predio sin
   * ficha que contrastar. Si el mensaje del servidor no llega entero, los ocho
   * se leen igual.
   *
   * Se eligen una de cada clase: la que **crea** —y por tanto es la unica del
   * modulo que no cuelga de ningun sujeto— y la que **retira**, que ademas se
   * confirma aparte y es la que mas cara sale de entender mal.
   */
  {
    k: 'campania-al-abrirla',
    que: 'El alta de una campania cuando `POST /fiscalizacion/campanias` la rechaza',
    hash: '#/fiscalizacion/campanias?acto=abrirCampania',
    /* Exacto: la lectura del embudo cuelga de la misma raiz —
       `/fiscalizacion/campanias/{id}/tasa-de-descarte`— y romperla mediria otra
       cosa, la `Lectura` que ya pasa por `Fallo`. */
    rompe: { camino: '/fiscalizacion/campanias', exacto: true },
    preparar: async (pagina) => {
      const panel = elPanel(pagina, ACTOS.abrirCampania);
      await panel.getByLabel(CAMPOS.codigo.rotulo).fill('CAM-2026-002');
      await panel.getByLabel(CAMPOS.nombre.rotulo).fill('Barrido del cercado');
      await panel.getByLabel(CAMPOS.umbral.rotulo).fill('0.20');
      await panel.getByLabel(CAMPOS.tope.rotulo).fill('500');
      await panel.getByLabel(CAMPOS.observacion.rotulo).fill('Se abre la campania del segundo semestre');
      await panel.getByRole('button', { name: ACTOS.abrirCampania }).click();
      await pagina.waitForTimeout(900);
    },
    region: (pagina) => elPanel(pagina, ACTOS.abrirCampania),
    detalle: 'completo',
  },
  {
    k: 'hallazgo-al-dejarlo-sin-efecto',
    que: 'La anulacion de un hallazgo cuando `POST .../anulacion` la rechaza',
    hash: '#/fiscalizacion/hallazgos/1?acto=dejarSinEfecto&hallazgo=1',
    rompe: { camino: '/fiscalizacion/hallazgos/1/anulacion', exacto: true },
    preparar: async (pagina) => {
      const panel = elPanel(pagina, ACTOS.dejarSinEfecto);
      await panel.getByLabel(CAMPOS.motivo.rotulo).fill('El techo resulto estar en el predio vecino');
      await panel.getByLabel(CAMPOS.observacion.rotulo).fill('Se retira tras la segunda visita de campo');
      /* Dos pulsaciones, que es lo que un acto irreversible exige: la primera
         abre la confirmacion y la segunda la firma. */
      await panel.getByRole('button', { name: ACTOS.dejarSinEfecto }).click();
      await panel.getByRole('button', { name: 'Si, confirmar' }).click();
      await pagina.waitForTimeout(900);
    },
    region: (pagina) => elPanel(pagina, ACTOS.dejarSinEfecto),
    detalle: 'completo',
  },
  {
    k: 'documento-de-la-ficha',
    que: 'La descarga de la ficha del contribuyente',
    hash: '#/consultas/ficha-contribuyente/C-000001',
    /* Solo la peticion que lleva `formato`: la misma URI sirve el JSON de la
       hoja sin el. Romper las dos mediria otra cosa —la `Lectura` de arriba, que
       ya pasa por `Fallo`— y taparia justo esta. */
    rompe: { camino: '/catastro/contribuyentes/', conParametro: 'formato' },
    preparar: async (pagina) => {
      await pagina.getByRole('button', { name: 'Descargar la ficha' }).click();
      await pagina.waitForTimeout(900);
    },
    region: (pagina) => pagina.locator('section').last(),
    detalle: 'completo',
  },
];

/**
 * El panel de un acto, por el titulo de su `Seccion`.
 *
 * Los formularios de fiscalizacion son secciones con su `h2`, y el rotulo del
 * boton primario es el MISMO que el de la fila que lo abrio: sin acotar por la
 * seccion, un `getByRole('button')` encontraria los dos y pulsaria el que no es.
 */
function elPanel(pagina, titulo) {
  return pagina
    .locator('section')
    .filter({ has: pagina.getByRole('heading', { name: titulo }) })
    .first();
}

const soloSuperficie = process.argv[2] ?? null;
const elegidas = SUPERFICIES.filter((s) => !soloSuperficie || s.k === soloSuperficie);

/* Se dice QUE se va a medir antes de medirlo: un arnes que solo imprime el
   resultado deja a quien lo lee sin saber sobre que conjunto habla, y ese es
   justo el hueco que las guardas de recorrido vacio existen para tapar. */
for (const s of elegidas) console.log(`  · ${s.k.padEnd(32)} ${s.que}`);

const navegador = await chromium.launch();
const fallos = [];
let observaciones = 0;

for (const superficie of elegidas) {
  /* Lo que se ve en cada escenario, para compararlos entre si al final. */
  const pantallas = new Map();

  for (const escenario of ESCENARIOS) {
    const error = errorDe(escenario);
    const contexto = await navegador.newContext({ viewport: { width: 1440, height: 1800 } });
    /* Corto a proposito: aqui una region que NO existe es un resultado —es el
       defecto que se busca—, no algo que este por llegar. Con los 30 s de
       Playwright, medir los seis escenarios de una tarjeta que aun no ofrece
       nada tarda tres minutos en decir lo que ya se sabia al segundo. */
    contexto.setDefaultTimeout(4000);

    await contexto.addInitScript(
      ({ rompe, escenario: esc, raiz }) => {
        window.__peticiones = [];
        window.__rotas = 0;
        const casa = (url) => {
          const camino = raiz + rompe.camino;
          const cuadra = rompe.exacto ? url.pathname === camino : url.pathname.startsWith(camino);
          return cuadra && (!rompe.conParametro || url.searchParams.has(rompe.conParametro));
        };
        let actual = globalThis.fetch;
        const envolver = (delegar) => async (entrada, opciones) => {
          const href =
            typeof entrada === 'string' ? entrada : entrada instanceof URL ? entrada.href : entrada.url;
          const url = new URL(href, location.origin);
          if (url.pathname.startsWith(raiz)) window.__peticiones.push(url.pathname);
          if (!casa(url)) return delegar(entrada, opciones);
          window.__rotas += 1;
          /* La red caida: `fetch` rechaza con un `TypeError`, que es lo que
             `cliente.ts` traduce a SIN_RESPUESTA. Un 502 no vale: eso SI es una
             respuesta y trae otro codigo. */
          if (esc.red === 'cortada') throw new TypeError('Failed to fetch');
          return new Response(JSON.stringify(esc.cuerpo), {
            status: esc.estado,
            headers: { 'Content-Type': 'application/problem+json' },
          });
        };
        /* El proxy de datos se instala DESPUES y captura `globalThis.fetch`; con
           el `set` se recoge el suyo y el `get` lo devuelve envuelto, asi que la
           ruta rota se rompe y las demas las sigue contestando el proxy. */
        Object.defineProperty(globalThis, 'fetch', {
          configurable: true,
          get: () => envolver(actual),
          set: (nueva) => {
            actual = nueva;
          },
        });
      },
      { rompe: superficie.rompe, escenario, raiz: RAIZ },
    );

    const pagina = await contexto.newPage();
    // Antes de navegar: la lista de ejercicios se deriva al montar la aplicacion.
    await pagina.clock.setFixedTime(EL_DIA);
    await pagina.goto(`${BASE}/${superficie.hash}`, { waitUntil: 'domcontentloaded' });
    await pagina.waitForTimeout(1100);
    if (superficie.preparar) await superficie.preparar(pagina);

    const nombre = `${superficie.k} · ${escenario.k}`;
    const region = superficie.region(pagina);
    const texto = await region.innerText().catch(() => '');
    observaciones += 1;

    const rotas = await pagina.evaluate(() => window.__rotas);
    if (rotas === 0) {
      /* Sin esto, un `rompe.camino` que se quedara viejo dejaria la pantalla
         entera en 200 y las comprobaciones de abajo se pondrian rojas hablando
         de un titulo que falta, que manda a mirar al sitio equivocado. */
      fallos.push(
        `${nombre}\n  no se rompio NI UNA peticion: «${RAIZ}${superficie.rompe.camino}» no es ninguna de las que\n` +
          `  pide esta pantalla. Las que pidio: ${[...new Set(await pagina.evaluate(() => window.__peticiones))].join(', ')}`,
      );
      await contexto.close();
      continue;
    }

    const problemas = [];

    /* 1 · Lo NOMBRA. El titulo del codigo es lo que separa «se rompio algo» de
       «no tiene permiso» y de «falta publicar una cifra». */
    const titulo = tituloDeError(error.codigo);
    if (!texto.includes(titulo)) {
      problemas.push(`no dice «${titulo}», que es el titulo de ${error.codigo}. Lo que dice: «${unaLinea(texto)}»`);
    }

    /* 2 · «Reintentar» exactamente donde reintentar puede cambiar algo, y solo
       uno: el de ESTA lectura. */
    const botones = await region.getByRole('button', { name: 'Reintentar' }).count();
    const esperados = error.reintentable ? 1 : 0;
    if (botones !== esperados) {
      problemas.push(
        error.reintentable
          ? `${error.codigo} es reintentable y esta superficie ofrece ${botones} «Reintentar», no 1`
          : `${error.codigo} NO es reintentable y aun asi ofrece ${botones} «Reintentar»: insistir sale igual`,
      );
    }

    /* 3 · Y reintenta LO SUYO. Un boton que existe y re-pide otra lectura es el
       defecto 2 de #49 exactamente: parece que funciona. */
    if (error.reintentable && botones === 1) {
      await pagina.evaluate(() => {
        window.__peticiones = [];
      });
      await region.getByRole('button', { name: 'Reintentar' }).click();
      await pagina.waitForTimeout(1100);
      const dePaso = await pagina.evaluate(() => window.__peticiones);
      const camino = RAIZ + superficie.rompe.camino;
      const laSuya = dePaso.some((p) => (superficie.rompe.exacto ? p === camino : p.startsWith(camino)));
      if (!laSuya) {
        problemas.push(
          `el «Reintentar» de esta superficie NO vuelve a pedir «${camino}»; pidio ${
            dePaso.length ? [...new Set(dePaso)].join(', ') : 'nada'
          }`,
        );
      }
    }

    /* 4 · Y las cifras del rechazo llegan enteras donde hay sitio para ellas. */
    if (superficie.detalle === 'completo') {
      if (!texto.includes(error.mensaje)) problemas.push(`no dice el mensaje del servidor: «${error.mensaje}»`);
      if (error.incidencia && !texto.includes(error.incidencia)) {
        problemas.push(
          `pierde la incidencia «${error.incidencia}», que es lo unico con lo que quien atiende puede preguntar`,
        );
      }
      for (const d of error.detalles ?? []) {
        if (!texto.includes(d)) problemas.push(`pierde el detalle «${d}», que es la cifra del rechazo`);
      }
      const falta = error.parametroQueFalta;
      if (falta?.llave && !texto.includes(falta.llave)) {
        problemas.push(`pierde la llave «${falta.llave}»: es lo que hay que publicar, y sin ella no se sabe que`);
      }
      if (falta && !texto.includes(String(falta.ejercicio))) {
        problemas.push(`no dice el ejercicio ${falta.ejercicio} del que falta la cifra`);
      }
    } else {
      const linea = motivoCorto(error);
      if (!texto.includes(linea)) {
        problemas.push(`no dice «${linea}», que es lo que el sistema de diseno compone para una linea`);
      }
    }

    /* 5 · Y no inventa un dato para tapar el hueco. */
    for (const p of superficie.prohibido ?? []) {
      const hallado = texto.match(p.re);
      if (hallado) problemas.push(`ensena «${hallado[0]}», y ${p.porque}`);
    }

    pantallas.set(escenario.k, texto);
    if (problemas.length) fallos.push(`${nombre}\n  ${problemas.join('\n  ')}`);
    await contexto.close();
  }

  /* 6 · Y las pantallas son DISTINTAS entre si. Es la comprobacion que caza el
     defecto 3, donde los cuatro escenarios salian byte a byte iguales porque el
     `catch` se quedaba con `e.mensaje` — y el mensaje es el mismo en los seis. */
  const vistos = [...pantallas.entries()];
  for (let i = 0; i < vistos.length; i++) {
    for (let j = i + 1; j < vistos.length; j++) {
      if (vistos[i][1] === vistos[j][1]) {
        fallos.push(
          `${superficie.k} · ${vistos[i][0]} vs ${vistos[j][0]}\n` +
            '  las dos pantallas dicen BYTE A BYTE lo mismo. Los seis escenarios comparten el mensaje a\n' +
            '  proposito, asi que dos iguales significan que de este error solo llego el mensaje y se\n' +
            `  perdio lo demas. Lo que sale: «${unaLinea(vistos[i][1])}»`,
        );
      }
    }
  }
}

await navegador.close();

function unaLinea(texto) {
  const limpio = texto.replace(/\s+/g, ' ').trim();
  return limpio.length > 180 ? `${limpio.slice(0, 180)}…` : limpio;
}

console.log(
  `${observaciones} render(s) medidos · ${elegidas.length} superficie(s) × ${ESCENARIOS.length} escenario(s)`,
);

/**
 * Un arnes que no midio ni un render no afirma nada.
 *
 * La misma guarda que `mirar.mjs` y `sin-red.mjs`, por el mismo motivo: sin
 * ella, «ninguna superficie pierde nada» es cierto sobre el conjunto vacio y
 * este archivo saldria con 0 con los tres defectos puestos.
 */
if (observaciones === 0) {
  console.error(
    '\nNo se midio NI UN render, asi que esta comprobacion no dice nada de las superficies de error.\n' +
      `O no hay superficies declaradas, o la que se pidio —«${soloSuperficie ?? '(ninguna)'}»— no es ninguna.`,
  );
  process.exit(2);
}

if (fallos.length) {
  console.error(`\n${fallos.length} superficie(s)×escenario con problema:\n\n${fallos.join('\n\n')}`);
  console.error(
    '\nLo que `cliente.ts` distingue —el codigo, `reintentable`, `detalles`, `incidencia` y\n' +
      '`parametroQueFalta`— tiene que llegar a la pantalla. Donde no llega, dos rechazos que piden\n' +
      'trabajos distintos se leen igual.',
  );
  process.exit(1);
}
console.log('las cuatro distinciones de `cliente.ts` llegan a las tres superficies, y cada escenario se ve distinto');
