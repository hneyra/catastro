/**
 * La puerta de identidad, medida sobre el paquete QUE SE PUBLICA.
 *
 *   node verificaciones/identidad.mjs
 *
 * <h2>Por que esto es un arnes de navegador y no una prueba unitaria</h2>
 *
 * Porque el defecto que existe para atrapar **llego a produccion con su prueba unitaria en
 * verde**. En `rentas` ([#71](https://github.com/hneyra/rentas/issues/71)) el `redirect_uri` se
 * compone con `import.meta.env.BASE_URL`, y su entorno de pruebas no declaraba la misma `base`
 * que el de construccion: alli `BASE_URL` valia la raiz del sitio, la prueba afirmaba ese valor,
 * y las dos cosas eran ciertas a la vez. Quien se autenticaba volvia a la raiz y recibia un 404,
 * con el `code` y el `iss` correctos — o sea que la autenticacion funcionaba y el retorno no.
 *
 * Una prueba que simula el entorno puede equivocarse de entorno **de forma coherente**. Esto no
 * simula nada: compila el paquete con `vite build`, lo sirve con `vite preview`, abre Chromium y
 * **lee la URL con la que la aplicacion se va al emisor**. Si la base y el retorno se separaran,
 * no habria dos sitios que mantener sincronizados: habria un solo hecho, y este lo mide.
 *
 * <h2>Con el proxy de datos APAGADO, que es como se construye la imagen</h2>
 *
 * `frontend/Dockerfile` fija `VITE_CATASTRO_PROXY_DE_DATOS=false`. El arranque no tiene ninguna
 * rama que dependa de esa bandera —la puerta se cruza siempre— pero medir sobre el artefacto que
 * de verdad sale a `ghcr.io` quita la pregunta de en medio en vez de razonarla.
 *
 * <h2>Y SIN emisor de mentira, que es lo que lo distingue de los otros nueve</h2>
 *
 * Los demas arneses ponen un `emisor.mjs` al otro lado para poder llegar a las pantallas. Aqui no:
 * la ida al emisor se **intercepta y se lee**, y no se contesta con nada que la aplicacion pueda
 * aprovechar. Lo que se afirma es lo que sale de esta aplicacion, no lo que vuelve.
 *
 * <h2>Lo que NO comprueba, dicho en vez de descubierto</h2>
 *
 *   - Que Keycloak acepte ese `redirect_uri`. Eso depende de los `redirectUris` del cliente
 *     `kamayuk-backoffice`, que vive en el realm y lo declara `infrastructure`. Lo unico que se
 *     puede afirmar desde aqui es que la aplicacion pide volver a donde de verdad esta.
 *   - Que el canje funcione contra un emisor de verdad. Eso exige Keycloak levantado, que es el
 *     arranque en limpio de `infrastructure` y no un arnes de este repositorio.
 *   - Que el token sirva contra el backend. Ese es otro sistema.
 */
import { chromium } from 'playwright-core';
import { spawn } from 'node:child_process';
import { rm } from 'node:fs/promises';
import { baseDeLaApp, baseDeVite } from './base.mjs';
import { TOKEN_DE_MENTIRA, emisorDeMentira } from './emisor.mjs';

const SALIDA = 'dist-identidad';
const PUERTO = Number(process.env.CATASTRO_PUERTO_IDENTIDAD ?? 5212);
const ORIGEN = `http://localhost:${PUERTO}`;
const BASE = baseDeLaApp(ORIGEN);

/**
 * El emisor por omision, el cliente y el alcance: los tres escalones de `src/api/configuracion.ts`
 * cayendo al ultimo.
 *
 * Se escriben aqui porque **son el contrato con el realm** y no un detalle de implementacion: el
 * cliente `kamayuk-backoffice` es el mismo que usa `rentas`, es publico y admite PKCE. Cambiarlo
 * en `configuracion.ts` sin decirlo aqui pondria esto rojo, que es lo que se quiere: cambiar de
 * cliente es cambiar quien puede entrar.
 */
const CLIENTE = 'kamayuk-backoffice';
const ALCANCE = 'openid profile';
const EMISOR_POR_OMISION = 'http://localhost:8181/realms/kamayuk';

/** Las palabras que delatan una credencial guardada. Las mismas que vigila `eslint.config.mjs`. */
const OLOR_A_CREDENCIAL = /token|jwt|bearer|credencial|contrasena|acceso|sesion/i;

const fallos = [];
const rojo = (que) => fallos.push(que);

/* ── Compilar y servir ──────────────────────────────────────────────────── */

console.log(`Compilando en «${SALIDA}» con el proxy de datos APAGADO …`);
await rm(SALIDA, { recursive: true, force: true });
await new Promise((listo, mal) => {
  const hijo = spawn('npx', ['vite', 'build', '--outDir', SALIDA], {
    env: { ...process.env, VITE_CATASTRO_PROXY_DE_DATOS: 'false' },
    stdio: ['ignore', 'ignore', 'inherit'],
  });
  hijo.on('exit', (c) => (c === 0 ? listo() : mal(new Error(`vite build salio con ${c}`))));
});

/**
 * Se lanza el binario de `vite` DIRECTAMENTE y no por `npx`, y es una linea que costo tiempo.
 *
 * `npx` es un proceso envoltorio: `servidor.kill()` mata al envoltorio y **deja vivo al `node`
 * que de verdad escucha**. El puerto se queda ocupado despues de terminar —lo que hace que la
 * siguiente corrida choque contra la guarda de abajo— y, peor, ese hijo heredo el `stderr` de
 * este proceso: cualquier tuberia que lea la salida de este arnes se queda esperando a que se
 * cierre un descriptor que ya no es de nadie. Medido: el arnes imprimia su resultado y la
 * tuberia no terminaba nunca.
 */
const vite = new URL('../node_modules/.bin/vite', import.meta.url).pathname;
const servidor = spawn(
  vite,
  ['preview', '--outDir', SALIDA, '--port', String(PUERTO), '--strictPort'],
  { stdio: ['ignore', 'ignore', 'inherit'] },
);
process.on('exit', () => servidor.kill());

/**
 * Si la vista previa muere sola, se falla **en vez de preguntarle al puerto**.
 *
 * Medido aqui mismo: con otra vista previa ya escuchando en el 5212, `--strictPort` hace que la
 * nuestra salga con «Port 5212 is already in use» y el bucle de abajo la encuentra VIVA —porque
 * el puerto contesta— y mide el paquete de otro. El arnes salio en verde sobre un `dist` que no
 * habia construido.
 */
let murio = null;
servidor.on('exit', (codigo) => (murio = codigo));

let vivo = false;
for (let i = 0; i < 60 && !vivo; i++) {
  await new Promise((r) => setTimeout(r, 500));
  /* Con la barra final: `vite preview` sirve la aplicacion en «/catastro/» y contesta 404 al
     mismo camino sin ella —medido—, asi que preguntarlo sin barra diria que no levanto nunca. */
  vivo = await fetch(`${BASE}/`)
    .then((r) => r.ok)
    .catch(() => false);
}
if (murio !== null) {
  console.error(
    `La vista previa de este arnes salio con ${murio} y algo mas esta escuchando en el ${PUERTO}.\n` +
      'No se mide contra eso: seria comprobar un paquete que este arnes no construyo. Cierra lo\n' +
      `que ocupa el puerto, o apunta este arnes a otro con CATASTRO_PUERTO_IDENTIDAD.`,
  );
  process.exit(2);
}
if (!vivo) {
  console.error(`La vista previa no levanto en ${BASE}`);
  servidor.kill();
  process.exit(2);
}

/* ── Mirar la ida a la puerta ───────────────────────────────────────────── */

const navegador = await chromium.launch();
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 900 } });

/**
 * La ida al emisor, capturada.
 *
 * Se contesta con un 200 y una pagina muda: la aplicacion se queda ahi, que es exactamente lo
 * que hace delante de un formulario de Keycloak.
 *
 * **Abortarla no vale, y costo una medida averiguarlo.** Con `route.abort()` la pestana se queda
 * en una pagina de error de Chromium, cuyo documento tiene origen opaco: leerle el
 * almacenamiento lanza «SecurityError: Failed to read the 'localStorage' property from 'Window':
 * Access is denied for this document». Y dejar que la navegacion ocurra tiene el problema
 * simetrico —la pestana acaba en el origen del emisor, donde el almacenamiento es otro—. La
 * salida esta mas abajo: cuando ya se leyo la URL, se vuelve al origen de la aplicacion por una
 * ruta que no es la aplicacion.
 */
let idaALaPuerta = null;
let laPuertaSeCruzo;
const cruzada = new Promise((listo) => (laPuertaSeCruzo = listo));
await contexto.route('**/protocol/openid-connect/auth*', async (ruta) => {
  idaALaPuerta ??= ruta.request().url();
  laPuertaSeCruzo();
  await ruta.fulfill({
    status: 200,
    contentType: 'text/html',
    body: '<!doctype html><title>emisor</title><p>aqui estaria el formulario</p>',
  });
});

const pagina = await contexto.newPage();
/* Se entra por una ruta PROFUNDA a proposito: el retorno tiene que ser la raiz de la aplicacion
   aunque se entrara por otro sitio —una sola URI que declarar en el cliente— y el destino viaja
   aparte, en `sessionStorage`. Entrando por la raiz, las dos cosas se verian iguales. */
await pagina.goto(`${BASE}/#/catastro/predios`, { waitUntil: 'domcontentloaded' });
await Promise.race([cruzada, new Promise((r) => setTimeout(r, 15_000))]);

if (idaALaPuerta === null) {
  console.error(
    '\nLa aplicacion NO fue a la puerta de identidad: se cargo sin token y sin pedirle nada a\n' +
      'nadie. Este arnes no puede medir un rebote que no ocurre, y lo que hay detras es peor que\n' +
      'un rojo: una interfaz que monta sus pantallas sin identificar a quien las abre. Mira\n' +
      '`src/main.tsx` —el orden de los tres pasos— y `src/api/identidad.ts`.',
  );
  await navegador.close();
  servidor.kill();
  process.exit(1);
}

const ida = new URL(idaALaPuerta);
const p = ida.searchParams;
const dice = (clave) => p.get(clave) ?? '(no viaja)';

/* ── Lo que tiene que decir esa URL ─────────────────────────────────────── */

/**
 * **La afirmacion de este arnes**, y la que cierra `rentas`#71.
 *
 * La raiz de la APLICACION, no la del sitio. Con `base` puesta y esto sin poner, todo lo demas
 * sale bien —el `code` llega, el `iss` es el correcto— y quien se autentica recibe un 404.
 */
const RETORNO = `${ORIGEN}${baseDeVite()}/`;
if (p.get('redirect_uri') !== RETORNO) {
  rojo(
    `el «redirect_uri» es «${dice('redirect_uri')}» y tiene que ser «${RETORNO}».\n` +
      `  Es la raiz de la APLICACION —«${baseDeVite()}/»—, no la del sitio. Confundirlas es\n` +
      '  `rentas`#71: el emisor autentica, devuelve el «code» al sitio, y quien entra recibe un\n' +
      '  404 en una direccion donde no hay ninguna aplicacion. Sale de\n' +
      '  `import.meta.env.BASE_URL`, que es la `base` de `vite.config.ts`.',
  );
}
if (p.get('redirect_uri') === `${ORIGEN}/`) {
  rojo(
    'el «redirect_uri» es la raiz del SITIO, que es exactamente el defecto de `rentas`#71.\n' +
      `  Esta interfaz se sirve bajo «${baseDeVite()}/» porque ADR-0030 §2 pone el sistema delante\n` +
      '  de la ruta y el mismo Traefik sirve las cinco.',
  );
}

/** El reto, que es lo que hace que un codigo interceptado no sirva de nada. */
if (p.get('code_challenge_method') !== 'S256') {
  rojo(
    `el «code_challenge_method» es «${dice('code_challenge_method')}» y tiene que ser «S256».\n` +
      '  Con «plain» el verificador viaja en claro en la URL de ida, asi que quien la vea puede\n' +
      '  canjear el codigo: PKCE deja de proteger de lo unico de lo que protege.',
  );
}
/* 43 caracteres es el largo de `BASE64URL(SHA256(...))` sin relleno, y no hay otro. Un reto mas
   corto seria un verificador corto —o el verificador mismo—, y esto lo dice antes que el emisor. */
if (!/^[A-Za-z0-9\-_]{43}$/.test(p.get('code_challenge') ?? '')) {
  rojo(
    `el «code_challenge» no tiene la forma de un SHA-256 en base64url sin relleno (43 caracteres):\n` +
      `  «${dice('code_challenge')}».`,
  );
}
if (p.get('response_type') !== 'code') {
  rojo(
    `el «response_type» es «${dice('response_type')}» y tiene que ser «code».\n` +
      '  Cualquier otro devuelve el token EN LA URL, o sea en el historial del navegador, en el\n' +
      '  «Referer» y en el registro de cualquier intermediario.',
  );
}
if (p.get('client_id') !== CLIENTE) {
  rojo(
    `el «client_id» es «${dice('client_id')}» y este arnes esperaba «${CLIENTE}».\n` +
      '  Es el cliente publico del realm, el mismo que usa `rentas`, y su comodin de retorno ya\n' +
      '  cubre esta ruta. Cambiarlo es cambiar quien puede entrar: si fue a proposito, la cifra se\n' +
      '  cambia aqui y se dice en el PR.',
  );
}
if (p.get('scope') !== ALCANCE) {
  rojo(
    `el «scope» es «${dice('scope')}» y este arnes esperaba «${ALCANCE}».\n` +
      '  Sin `offline_access` ni nada que pida un «refresh_token»: el token de esta interfaz vive\n' +
      '  en memoria y muere con la pestana (ADR-0030 §3), asi que una credencial de vida larga\n' +
      '  seria justo lo que ese diseno evita.',
  );
}
if ((p.get('state') ?? '').length < 16) {
  rojo(
    `el «state» es «${dice('state')}».\n` +
      '  Es lo unico que distingue nuestra vuelta de un codigo que alguien nos hizo llegar, asi\n' +
      '  que tiene que ser largo y aleatorio.',
  );
}
/** El emisor sale de la cadena de `configuracion.ts`, y aqui no hay `ConfigMap` que la sirva. */
if (ida.origin + ida.pathname !== `${EMISOR_POR_OMISION}/protocol/openid-connect/auth`) {
  rojo(
    `la ida va a «${ida.origin}${ida.pathname}» y sin senias servidas tenia que ir al emisor por\n` +
      `  omision, «${EMISOR_POR_OMISION}/protocol/openid-connect/auth». El realm es UNO para los\n` +
      '  cinco sistemas (ADR-0005, ADR-0030 §3).',
  );
}

/* ── Y donde NO esta el token ───────────────────────────────────────────── */

/**
 * Se vuelve al ORIGEN de la aplicacion antes de leer el almacenamiento, y por una ruta que no es
 * la aplicacion.
 *
 * `localStorage` y `sessionStorage` son **por origen**, y la pestana esta en el del emisor: leer
 * ahi devolveria dos almacenes vacios y este arnes daria por buena una puerta que guarda el token
 * donde no debe. `sessionStorage` es ademas **por pestana**, asi que abrir una pagina nueva
 * tampoco serviria: tiene que ser esta, que es la que cruzo la puerta.
 *
 * Y una ruta que no existe —un 404 de `vite preview`— y no la aplicacion, porque volver a
 * cargarla la mandaria otra vez a la puerta, sumando una ida al tope de tres y pisando el
 * verificador que se quiere leer.
 */
await pagina.goto(`${ORIGEN}/ruta-que-no-es-la-aplicacion`, { waitUntil: 'domcontentloaded' });

const almacenado = await pagina.evaluate(() => {
  const leer = (almacen) =>
    Object.fromEntries(
      Array.from({ length: almacen.length }, (_, i) => almacen.key(i)).map((k) => [
        k,
        almacen.getItem(k) ?? '',
      ]),
    );
  return { local: leer(localStorage), sesion: leer(sessionStorage) };
});

if (Object.keys(almacenado.local).length > 0) {
  rojo(
    `«localStorage» tiene ${Object.keys(almacenado.local).length} llave(s): ` +
      `${Object.keys(almacenado.local).join(', ')}.\n` +
      '  Esta interfaz no guarda NADA ahi. En una PC de ventanilla que varios turnos comparten,\n' +
      '  lo que se guarda en «localStorage» sobrevive al cierre del navegador (ADR-0030 §3).',
  );
}
const sospechosas = Object.keys(almacenado.sesion).filter((k) => OLOR_A_CREDENCIAL.test(k));
if (sospechosas.length > 0) {
  rojo(
    `«sessionStorage» guarda algo que suena a credencial: ${sospechosas.join(', ')}.\n` +
      '  Lo unico que puede sobrevivir al rebote es el verificador PKCE —sin el no hay canje al\n' +
      '  volver— y no es una credencial: es un secreto de un solo uso.',
  );
}
/* Y el contraste, sin el cual lo de arriba se cumple solo: el verificador TIENE que estar. Un
   `sessionStorage` vacio pasaria las dos comprobaciones de arriba y significaria que la ida se
   hizo sin guardar con que canjear la vuelta, o sea una puerta que nunca podra abrirse. */
if (almacenado.sesion['catastro.pkce.verificador'] === undefined) {
  rojo(
    'la ida a la puerta no dejo ningun verificador PKCE en «sessionStorage».\n' +
      '  Sin el no hay canje al volver del emisor: el navegador se va y vuelve, y lo unico que\n' +
      '  demuestra que quien canja es quien pidio se habria perdido por el camino. Las llaves que\n' +
      `  hay: ${Object.keys(almacenado.sesion).join(', ') || '(ninguna)'}.`,
  );
}

/* ── El ConfigMap manda: las senias servidas ganan ──────────────────────── */

/**
 * La segunda mitad, y no es opcional: **en el cluster el emisor lo pone un `ConfigMap`**.
 *
 * Si la cadena de `src/api/configuracion.ts` no leyera lo servido, todo lo de arriba seguiria en
 * verde y cada municipalidad rebotaria al `localhost:8181` del puesto de quien desarrolla. El
 * sintoma seria un navegador que no llega a ningun sitio, con la interfaz perfectamente sana.
 */
const EMISOR_DE_OTRO_AMBIENTE = 'https://stg.kamayuk.example/keycloak/realms/kamayuk';
const contextoServido = await navegador.newContext({ viewport: { width: 1440, height: 900 } });
let idaServida = null;
let laSegundaSeCruzo;
const segundaCruzada = new Promise((listo) => (laSegundaSeCruzo = listo));
await contextoServido.route('**/protocol/openid-connect/auth*', async (ruta) => {
  idaServida ??= ruta.request().url();
  laSegundaSeCruzo();
  await ruta.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><title>e</title>' });
});
await contextoServido.route('**/configuracion.js', async (ruta) => {
  await ruta.fulfill({
    status: 200,
    contentType: 'application/javascript',
    body: `window.__KAMAYUK_CATASTRO__ = ${JSON.stringify({ oidcRealm: EMISOR_DE_OTRO_AMBIENTE })};\n`,
  });
});
const paginaServida = await contextoServido.newPage();
await paginaServida.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
await Promise.race([segundaCruzada, new Promise((r) => setTimeout(r, 15_000))]);

if (idaServida === null || !idaServida.startsWith(`${EMISOR_DE_OTRO_AMBIENTE}/`)) {
  rojo(
    `con «configuracion.js» sirviendo otro emisor, la ida fue a «${idaServida ?? '(ninguna)'}»\n` +
      `  y tenia que ir a «${EMISOR_DE_OTRO_AMBIENTE}/…».\n` +
      '  Ese archivo es el que el `ConfigMap` del cluster monta ENCIMA del que viaja vacio en la\n' +
      '  imagen, y es lo unico que hace que una sola imagen sirva para todas las municipalidades.\n' +
      '  Si no se lee, el emisor queda HORNEADO en el paquete y cada despliegue rebota al\n' +
      '  emisor del puesto donde se construyo.',
  );
}

/* ── Y la VUELTA: con el token ya canjeado, ¿donde esta? ───────────────── */

/**
 * **La mitad que faltaba, y se descubrio midiendo.**
 *
 * Lo de arriba lee el almacenamiento despues de la IDA, cuando todavia no hay token que
 * guardar: medido, poner un `localStorage.setItem('catastro.token', …)` dentro de `fijarToken`
 * deja esa comprobacion **en VERDE** —la prohibicion de ESLint si lo caza, pero este arnes no—.
 * O sea que la afirmacion «el token no toca el almacenamiento» solo la sostenia un escaner de
 * fuentes, y un escaner no ve lo que hace una dependencia ni una via indirecta.
 *
 * Aqui se cruza la puerta ENTERA con un emisor de mentira —el mismo que usan los nueve arneses
 * de pantalla—, se deja que el canje termine, y se busca el token **por su valor**: asi da igual
 * bajo que llave se guardara. Es lo unico que puede afirmar donde acaba una credencial de verdad.
 */
const contextoCompleto = await navegador.newContext({ viewport: { width: 1440, height: 900 } });
await emisorDeMentira(contextoCompleto);
const paginaCompleta = await contextoCompleto.newPage();
/* Con la API cortada: este paquete se compila con el proxy de datos APAGADO, asi que sus
   pantallas piden de verdad y `vite preview` reenvia a un backend que aqui no hay. Lo que
   saldria es una rafaga de «ECONNREFUSED» en la salida de este arnes —ruido que se lee como un
   fallo y no lo es—. Lo que se mide aqui es donde acaba el token, no que las pantallas tengan
   datos. */
await paginaCompleta.route('**/catastro/api/v1/**', (r) => r.abort());
await paginaCompleta.goto(`${BASE}/`, { waitUntil: 'networkidle' });
/* Que la aplicacion llego a MONTAR, sin lo cual lo de abajo se cumple solo: un almacenamiento
   vacio porque la puerta no se cruzo se ve exactamente igual que uno vacio porque el token vive
   en memoria. */
const monto = await paginaCompleta
  .locator('main')
  .first()
  .innerText()
  .catch(() => '');
if (monto.trim().length < 40) {
  rojo(
    'con el emisor de mentira al otro lado, la aplicacion no llego a dibujar nada.\n' +
      '  Sin eso no se puede afirmar donde acaba el token: un almacenamiento vacio porque la\n' +
      '  puerta no se cruzo se ve igual que uno vacio porque el token vive en memoria.',
  );
}

const trasCanjear = await paginaCompleta.evaluate(() => {
  const leer = (almacen) =>
    Array.from({ length: almacen.length }, (_, i) => almacen.key(i)).map((k) => ({
      almacen: almacen === localStorage ? 'localStorage' : 'sessionStorage',
      llave: k,
      valor: almacen.getItem(k) ?? '',
    }));
  return [...leer(localStorage), ...leer(sessionStorage)];
});

const conElToken = trasCanjear.filter((e) => e.valor.includes(TOKEN_DE_MENTIRA));
if (conElToken.length > 0) {
  rojo(
    'despues de canjear, el token esta GUARDADO en el navegador: ' +
      `${conElToken.map((e) => `${e.almacen}[«${e.llave}»]`).join(', ')}.\n` +
      '  Vive en una variable de modulo y se muere con la pestana, y esa es la decision (ADR-0030\n' +
      '  §3): en una PC de ventanilla que varios turnos comparten, lo que se persiste sobrevive al\n' +
      '  cierre del navegador y el token del turno de la manana sigue sirviendo por la tarde.\n' +
      '  Se busca por VALOR y no por llave a proposito: bajo un nombre inocente pasaria igual.',
  );
}
const enLocalTrasCanjear = trasCanjear.filter((e) => e.almacen === 'localStorage');
if (enLocalTrasCanjear.length > 0) {
  rojo(
    `despues de canjear, «localStorage» tiene ${enLocalTrasCanjear.length} llave(s): ` +
      `${enLocalTrasCanjear.map((e) => e.llave).join(', ')}.\n` +
      '  Esta interfaz no guarda NADA ahi.',
  );
}

await navegador.close();
servidor.kill();
await rm(SALIDA, { recursive: true, force: true });

if (fallos.length > 0) {
  console.error(`\n${fallos.length} cosa(s) que la puerta de identidad no hace bien:\n`);
  for (const f of fallos) console.error('  - ' + f + '\n');
  console.error(`  La ida que se leyo fue:\n    ${idaALaPuerta}\n`);
  process.exit(1);
}

console.log(
  `la puerta va a «${ida.origin}${ida.pathname}» con PKCE S256, cliente «${CLIENTE}» y vuelve a ` +
    `«${RETORNO}»; el token no toca el almacenamiento —ni antes ni DESPUES de canjearlo— y las ` +
    'senias servidas mandan sobre las horneadas',
);
