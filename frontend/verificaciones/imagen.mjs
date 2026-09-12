/**
 * Los dos archivos que deciden COMO SE SIRVE la interfaz: `nginx.conf` y `Dockerfile`.
 *
 *   node verificaciones/imagen.mjs
 *
 * Los otros ocho arneses miran `src/`. Ninguno mira estos dos, y son los que deciden
 * que sale publicado en `ghcr.io`. La revision del armazon encontro ahi dos defectos,
 * arreglo los dos, y hasta este arnes **los dos podian volver sin que nada se pusiera
 * rojo**: lo unico que los sujetaba eran dos parrafos de comentario, y un comentario
 * no es una guarda.
 *
 * <h2>1. Las cabeceras, medidas LEVANTANDO NGINX y preguntando por HTTP</h2>
 *
 * `add_header` **no se hereda**: nginx toma el conjunto del nivel mas interno que
 * declare alguno y descarta el de arriba entero. En `nginx.conf` hay dos `location`
 * con los suyos —`/assets/` y `= /index.html`— y `location /` sirve por redireccion
 * interna a `/index.html`, de modo que TODAS las respuestas caen en uno de esos dos.
 * Juntar las tres cabeceras de seguridad «para no repetirlas» en un unico bloque
 * `server` las apaga: el archivo sigue diciendo lo correcto y no llega ninguna.
 *
 * Por eso esto **no es un analisis de texto**. Una guarda que solo comprobara que la
 * cabecera aparece en el archivo pasaria en verde con ese defecto exacto puesto —que
 * es el defecto que existe para atrapar—, y una que lo mirase «por bloque» tendria
 * que reimplementar la semantica de herencia de nginx, que es justo la regla que
 * nadie tenia clara. Preguntando por HTTP no puede divergir: lo contesta el mismo
 * motor que sirve en produccion.
 *
 * La imagen sobre la que se pregunta **sale del propio `Dockerfile`** (`FROM nginx:…`)
 * y el puerto, del `listen` de `nginx.conf`. Ninguno de los dos es un literal aqui:
 * es la leccion de `.nvmrc` en el flujo de CI —con el literal, el arnes y lo que se
 * despliega son dos numeros que pueden separarse sin que nada lo diga—.
 *
 * **No se construye la imagen del frontend**: se copia `nginx.conf` sobre la base de
 * nginx y se siembra una raiz de mentira. Son segundos, y asi el arnes no depende de
 * que `yarn build` haya corrido.
 *
 * <h2>2. `.gitignore` contra `.dockerignore`</h2>
 *
 * `Dockerfile` hace `COPY . .`, asi que lo que `.dockerignore` no excluya participa en
 * `yarn build` y **lo que Vite hornee ahi acaba en el paquete que se publica**. Un
 * archivo que esta en `.gitignore` y no en `.dockerignore` es la peor de las dos
 * combinaciones: git no lo ve, asi que nadie repara en el, y la imagen si lo usa. Es
 * lo que metio un `VITE_CATASTRO_TOKEN` en claro dentro de `assets/index-*.js`.
 *
 * **Aqui no hay exencion automatica para «directorios de trabajo»**, y esta medido por
 * que: `dist-sin-red/` y `dist-con-proxy/` son directorios de trabajo, los crea un
 * arnes, y viajaban al contexto de construccion. La exencion se DECLARA, en
 * `.dockerignore`, con esta forma y su motivo en la misma linea:
 *
 *   # excepcion: <entrada de .gitignore> — <por que no hace falta excluirla>
 *
 * Y esa comparacion **sola no basta, y esta medido**: quitar `.env.*` de
 * `.dockerignore` —la rotura con la que se rompe este punto— la pasa en VERDE, porque
 * `.env.*` no es una entrada de `.gitignore`; el unico archivo de entorno que git
 * ignora es `.env.local`, y lo cubre el `*.local` que sigue estando. `.env.<modo>` no
 * lo ignora git y lo hornea Vite igual. Asi que se anade la afirmacion que si lo caza:
 * **todos los archivos de entorno que Vite lee estan excluidos del contexto**, sea cual
 * sea el `--mode`. Cuales son no se escribe a mano: se le pregunta a `loadEnv` de la
 * propia Vite sembrando un directorio de mentira, asi que la lista no puede quedarse
 * vieja cuando Vite cambie.
 *
 * <h2>3. El `Dockerfile` no copia nada que git no vea</h2>
 *
 * Todo origen de un `COPY` que venga del contexto tiene que estar versionado. `COPY . .`
 * es el contexto entero y lo gobierna el punto 2. Y ningun `ARG`/`ENV` puede llamarse
 * como una credencial: Vite resuelve `import.meta.env` AL COMPILAR, asi que un
 * `ARG VITE_CATASTRO_TOKEN` queda horneado en el paquete —y el `Dockerfile` afirma por
 * escrito lo contrario—.
 *
 * <h2>Y se comprueba que la comprobacion mide algo</h2>
 *
 * Sale con **2** —y no con 0— si no encuentra ni un `location` con `add_header` propio,
 * si hay un `location` que no sabe sondear, si el `Dockerfile` no copia el contexto
 * entero, si nginx no llego a levantar, o si **el recorrido por HTTP no se hizo entero**.
 * Un arnes que se queda sin sujeto y pasa en verde es el defecto que esta serie ya
 * encontro cuatro veces, y este lo tenia: apartando el bloque que levanta nginx, el
 * informe seguia diciendo «las cabeceras llegan en todas las rutas» con **cero**
 * preguntas hechas, porque las comprobaciones de archivo salian limpias por su cuenta.
 *
 * Necesita Docker. **Sin Docker sale con 2, no se omite**: una comprobacion bloqueante
 * que se salta a si misma deja el flujo en verde sin haber verificado nada.
 *
 * <h2>Lo que este arnes NO mide, dicho en vez de descubierto</h2>
 *
 * La etapa final del `Dockerfile` endurece la imagen —quita `user nginx;`, manda el pid
 * a `/tmp` y da permisos por GRUPO para sobrevivir a un `runAsUser` aleatorio—. Eso
 * exige construir la imagen y aqui no se construye, asi que **no se mide**: lo que se
 * mide es la semantica de `nginx.conf`.
 */
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadEnv } from 'vite';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const leer = (nombre) => readFileSync(path.join(RAIZ, nombre), 'utf8');

/**
 * El conjunto completo, con su valor. Es un CONTRATO escrito una vez: cambiarlo es
 * cambiar una decision de seguridad, y entonces el diff tiene que verse aqui.
 *
 *   · `nosniff` — sin ella un navegador puede decidir por su cuenta que un archivo
 *     servido con un tipo es otra cosa, y ejecutarlo.
 *   · `DENY` — esta interfaz no se embebe en ningun sitio y el token de sesion vive en
 *     `localStorage` del mismo origen: dentro de un marco ajeno, un clic sobre un boton
 *     que no se ve es un clic de quien tiene la sesion abierta.
 *   · `no-referrer` — la aplicacion solo habla con su propio origen, asi que no hay
 *     ningun destino al que la direccion de esta pagina tenga que llegar.
 */
const CABECERAS_DE_SEGURIDAD = {
  'x-content-type-options': 'nosniff',
  'x-frame-options': 'DENY',
  'referrer-policy': 'no-referrer',
};

/**
 * Los nombres que se reconocen como «cabecera de seguridad».
 *
 * No decide nada por si mismo: sirve para que la lista de arriba no se quede vieja. Si
 * `nginx.conf` empieza a emitir una de estas y no esta en `CABECERAS_DE_SEGURIDAD`, se
 * quedaria sin vigilar —y entonces bastaria escribirla en UN bloque para que el arnes
 * la diera por buena en todos—.
 */
const CATALOGO_DE_SEGURIDAD = new Set([
  'content-security-policy',
  'content-security-policy-report-only',
  'cross-origin-embedder-policy',
  'cross-origin-opener-policy',
  'cross-origin-resource-policy',
  'origin-agent-cluster',
  'permissions-policy',
  'referrer-policy',
  'strict-transport-security',
  'x-content-type-options',
  'x-frame-options',
  'x-permitted-cross-domain-policies',
  'x-xss-protection',
]);

/** Nombres de `ARG`/`ENV` que no pueden aparecer: Vite los hornearia en el paquete. */
const OLOR_A_CREDENCIAL = /(TOKEN|SECRET|PASSWORD|PASSWD|CLAVE|CREDENCIAL|API_?KEY|PRIVATE)/i;

const fallos = [];
const sinMedir = [];
const rojo = (que) => fallos.push(que);
const noSeMidio = (que) => sinMedir.push(que);

// ---------------------------------------------------------------------------
// `nginx.conf`, con las llaves contadas

/** Bloques `{ … }` con sus directivas directas. Respeta comillas y comentarios. */
function analizarNginx(texto) {
  const bloques = [];
  const pila = [];
  let acumulado = '';
  let linea = 1;
  let comilla = null;
  let enComentario = false;
  for (const c of texto) {
    if (c === '\n') {
      linea++;
      enComentario = false;
      acumulado += ' ';
      continue;
    }
    if (enComentario) continue;
    if (comilla) {
      acumulado += c;
      if (c === comilla) comilla = null;
      continue;
    }
    if (c === '"' || c === "'") {
      comilla = c;
      acumulado += c;
      continue;
    }
    if (c === '#') {
      enComentario = true;
      continue;
    }
    if (c === '{') {
      const bloque = { cabecera: acumulado.trim().replace(/\s+/g, ' '), directivas: [], linea };
      bloques.push(bloque);
      pila.push(bloque);
      acumulado = '';
      continue;
    }
    if (c === '}') {
      pila.pop();
      acumulado = '';
      continue;
    }
    if (c === ';') {
      const texto = acumulado.trim().replace(/\s+/g, ' ');
      if (texto !== '' && pila.length > 0) pila[pila.length - 1].directivas.push({ texto, linea });
      acumulado = '';
      continue;
    }
    acumulado += c;
  }
  return bloques;
}

/** Los argumentos de una directiva, con las comillas quitadas. */
function partir(directiva) {
  const piezas = directiva.match(/"[^"]*"|'[^']*'|\S+/g) ?? [];
  return piezas.map((p) => (/^["']/.test(p) ? p.slice(1, -1) : p));
}

/**
 * La ruta con la que se sonda un `location`, y el archivo que hay que sembrar para que
 * exista. Devuelve `null` si el patron no se sabe sondear —y entonces el arnes sale con
 * 2 en vez de callarse—.
 *
 * Recibe el BLOQUE y no solo su cabecera: lo que se sirve en un `location` no siempre sale
 * de un archivo. Ver el caso del `return` dentro.
 */
function sondaDe(bloque) {
  const cabecera = bloque.cabecera;
  const resto = cabecera.slice('location'.length).trim();
  const conModificador = resto.match(/^(=|\^~|~\*|~)\s*(.*)$/);
  const modificador = conModificador ? conModificador[1] : '';
  const patron = conModificador ? conModificador[2] : resto;
  if (modificador === '~' || modificador === '~*') return null;
  if (!patron.startsWith('/')) return null;

  /**
   * Un `location` que solo DEVUELVE un codigo no sirve ningun archivo.
   *
   * Lo estreno el bloque `/catastro/`, que contesta 404 explicando que el ingreso no quito el
   * prefijo. Sin este caso, la sonda de abajo le sembraria un archivo y le exigiria 200: el
   * arnes saldria rojo sobre un bloque que hace exactamente lo que dice hacer, y el remedio
   * habria sido quitarle las cabeceras —o sea apagar la unica parte que este arnes vigila—.
   *
   * Las dos rutas se conservan aunque las dos esperen lo mismo: una existe como archivo y la
   * otra no, y que las DOS den el mismo codigo es justo lo que afirma este bloque.
   */
  const devuelve = bloque.directivas.find((d) => /^return\s+\d/.test(d.texto));
  if (devuelve) {
    const estado = Number(partir(devuelve.texto)[1]);
    const base = patron.endsWith('/') ? patron : `${patron}/`;
    const rutas =
      modificador === '='
        ? [{ ruta: patron, sembrar: null, estado }]
        : [
            { ruta: `${base}sonda-del-arnes.txt`, sembrar: `${base}sonda-del-arnes.txt`, estado },
            { ruta: `${base}ausente-del-arnes.txt`, sembrar: null, estado },
          ];
    return { rutas };
  }

  if (modificador === '=') return { rutas: [{ ruta: patron, sembrar: patron, estado: 200 }] };
  if (patron === '/') return { rutas: [{ ruta: '/', sembrar: '/index.html', estado: 200 }] };
  const base = patron.endsWith('/') ? patron : `${patron}/`;
  return {
    rutas: [
      { ruta: `${base}sonda-del-arnes.txt`, sembrar: `${base}sonda-del-arnes.txt`, estado: 200 },
      // El 404 es lo unico que mide el `always`: sin el, las cabeceras siguen saliendo
      // en las respuestas buenas y solo desaparecen cuando algo va mal.
      { ruta: `${base}ausente-del-arnes.txt`, sembrar: null, estado: 404 },
    ],
  };
}

const bloques = analizarNginx(leer('nginx.conf'));
const servidor = bloques.find((b) => b.cabecera === 'server');
if (!servidor) noSeMidio('«nginx.conf» no tiene ningun bloque «server»: no hay nada que levantar.');

const escuchas = servidor?.directivas.filter((d) => d.texto.startsWith('listen ')) ?? [];
const puerto = escuchas.map((d) => partir(d.texto)[1]?.match(/(\d+)$/)?.[1]).find(Boolean);
if (!puerto) noSeMidio('No se pudo leer el puerto del «listen» de «nginx.conf»: sin puerto no hay a quien preguntar.');

const conCabeceras = bloques.filter(
  (b) => b.cabecera.startsWith('location') && b.directivas.some((d) => d.texto.startsWith('add_header ')),
);

if (conCabeceras.length === 0) {
  noSeMidio(
    'Ni un «location» de «nginx.conf» declara «add_header» propio. La regla por bloque —la que existe\n' +
      '  porque «add_header» no se hereda— se queda SIN SUJETO y se cumpliria sola, asi que este arnes\n' +
      '  no puede afirmar que la midio: pasaria en verde con el defecto exacto que existe para atrapar.\n' +
      '  Si el archivo se reordeno a proposito, lo que hay que rehacer es este arnes; dejarlo pasar en\n' +
      '  verde es lo unico que no vale.',
  );
}

// La lista vigilada no se queda vieja: cualquier cabecera de seguridad que el archivo
// emita y esta lista no nombre se estaria sirviendo sin que nadie compruebe donde.
const emitidas = new Set(
  bloques
    .flatMap((b) => b.directivas)
    .filter((d) => d.texto.startsWith('add_header '))
    .map((d) => partir(d.texto)[1]?.toLowerCase())
    .filter(Boolean),
);
for (const nombre of emitidas) {
  if (CATALOGO_DE_SEGURIDAD.has(nombre) && !(nombre in CABECERAS_DE_SEGURIDAD)) {
    rojo(
      `«nginx.conf» emite «${nombre}», que es una cabecera de seguridad, y este arnes no la vigila.\n` +
        '  Escrita en un solo bloque se apaga en todos los demas sin que nada lo diga: anadela a\n' +
        '  CABECERAS_DE_SEGURIDAD con su valor, que es donde vive el contrato.',
    );
  }
}

const sondas = [
  // Las dos rutas que sirve la aplicacion de verdad. La segunda no es un archivo: la
  // resuelve `try_files`, que es como se recarga una ruta del navegador.
  { ruta: '/', sembrar: '/index.html', estado: 200, de: 'la aplicacion' },
  { ruta: '/una/ruta/que/no/es/un/archivo', sembrar: null, estado: 200, de: 'la aplicacion' },
];
for (const bloque of conCabeceras) {
  const sonda = sondaDe(bloque);
  if (!sonda) {
    noSeMidio(
      `«${bloque.cabecera}» (linea ${bloque.linea}) declara «add_header» y este arnes no sabe sondearlo,\n` +
        '  asi que sus cabeceras se quedarian sin medir. Ensenale a componer una ruta para ese patron.',
    );
    continue;
  }
  for (const r of sonda.rutas) sondas.push({ ...r, de: bloque.cabecera });
}

// ---------------------------------------------------------------------------
// `.gitignore` contra `.dockerignore`

const entradas = (texto) =>
  texto
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l !== '' && !l.startsWith('#'));

const excepciones = new Map(
  leer('.dockerignore')
    .split('\n')
    .map((l) => l.trim().match(/^#\s*excepcion:\s*(\S+)\s*(?:—|--|-)\s*(.+)$/))
    .filter(Boolean)
    .map((m) => [m[1], m[2].trim()]),
);

const patronesDocker = entradas(leer('.dockerignore'));
const reincluye = patronesDocker.filter((p) => p.startsWith('!'));
if (reincluye.length > 0) {
  noSeMidio(
    `«.dockerignore» reincluye con «!» (${reincluye.join(', ')}) y este arnes no modela esa vuelta atras:\n` +
      '  daria por excluido algo que si viaja al contexto.',
  );
}

// `**` se aparta con un caracter que no puede aparecer en un patron, y no con un
// espacio: un espacio SI puede estar en un nombre de archivo, y entonces el patron se
// convertiria en «cualquier cosa» sin que nadie lo notara.
const CUALQUIER_PROFUNDIDAD = '\u0000';

const aExpresion = (patron) =>
  new RegExp(
    `^${patron
      .replace(/[.+^${}()|[\]\\]/g, '\\$&')
      .replaceAll('**', CUALQUIER_PROFUNDIDAD)
      .replace(/\*/g, '[^/]*')
      .replace(/\?/g, '[^/]')
      .replaceAll(CUALQUIER_PROFUNDIDAD, '.*')}$`,
  );

/** El patron de `.dockerignore` que cubre una entrada, o `undefined`. */
function cubiertaPor(entrada, patrones) {
  const partes = entrada.split('/');
  const ancestros = partes.map((_, i) => partes.slice(0, i + 1).join('/'));
  return patrones.find((p) => ancestros.some((a) => a === p || aExpresion(p).test(a)));
}

const deGit = entradas(leer('.gitignore'))
  .filter((l) => !l.startsWith('!'))
  .map((l) => l.replace(/^\/+/, '').replace(/\/+$/, ''))
  .filter((l) => l !== '');

const descubiertas = [];
for (const entrada of deGit) {
  const patron = cubiertaPor(entrada, patronesDocker);
  if (patron) continue;
  const motivo = excepciones.get(entrada);
  if (motivo) continue;
  descubiertas.push(entrada);
}

if (descubiertas.length > 0) {
  rojo(
    `${descubiertas.length} entrada(s) de «.gitignore» que «.dockerignore» no excluye:\n\n` +
      descubiertas.map((e) => `    ${e}`).join('\n') +
      '\n\n  Es la peor de las dos combinaciones: git no las ve, asi que nadie repara en ellas, y el\n' +
      '  «COPY . .» del Dockerfile SI las copia —y lo que Vite hornee con ellas acaba en el paquete\n' +
      '  que se publica—. Es lo que metio un token en claro dentro de «assets/index-*.js».\n' +
      '  Si alguna no hace falta excluirla, se declara en «.dockerignore» con su motivo:\n' +
      '    # excepcion: <entrada> — <por que>',
  );
}

/**
 * Los archivos de entorno que Vite lee, PREGUNTANDOSELO A VITE.
 *
 * El modo es inventado a proposito: `--mode <lo que sea>` es un argumento de linea de
 * comandos, asi que `.env.<modo>` no es un nombre concreto sino una familia, y por eso
 * `.dockerignore` necesita `.env.*` y no la lista de los modos que hoy se usan.
 */
function archivosDeEntornoDeVite() {
  const modo = 'modo-del-arnes';
  const candidatos = ['.env', '.env.local', `.env.${modo}`, `.env.${modo}.local`];
  const directorio = mkdtempSync(path.join(tmpdir(), 'catastro-entorno-'));
  candidatos.forEach((nombre, i) => writeFileSync(path.join(directorio, nombre), `VITE_SONDA_DEL_ARNES_${i}=si\n`));
  const leidas = loadEnv(modo, directorio, 'VITE_SONDA_DEL_ARNES_');
  rmSync(directorio, { recursive: true, force: true });
  return candidatos.filter((_, i) => `VITE_SONDA_DEL_ARNES_${i}` in leidas);
}

const deEntorno = archivosDeEntornoDeVite();
if (deEntorno.length === 0) {
  noSeMidio(
    'Vite no leyo NINGUNO de los archivos de entorno que este arnes le puso delante, asi que la\n' +
      '  comprobacion de que ninguno viaja al contexto se quedo sin sujeto y pasaria en verde sin\n' +
      '  mirar nada. O «loadEnv» cambio de forma, o dejo de leer archivos.',
  );
}

const deEntornoDescubiertos = deEntorno.filter((nombre) => !cubiertaPor(nombre, patronesDocker));
if (deEntornoDescubiertos.length > 0) {
  rojo(
    `${deEntornoDescubiertos.length} archivo(s) de entorno que Vite lee y «.dockerignore» no excluye:\n\n` +
      deEntornoDescubiertos.map((e) => `    ${e}`).join('\n') +
      '\n\n  Vite resuelve «import.meta.env» AL COMPILAR: lo que haya ahi cuando corre el «yarn build»\n' +
      '  de la imagen queda EN CLARO dentro de «assets/index-*.js», que es lo que se publica en\n' +
      '  «ghcr.io». Y basta con que exista en la maquina de quien construye: no hace falta que este\n' +
      '  versionado, ni siquiera que git lo ignore. El modo es inventado a proposito —«--mode» lo\n' +
      '  elige quien construye—, asi que lo que hace falta es el patron «.env.*» y no una lista.',
  );
}

for (const [entrada, motivo] of excepciones) {
  if (!deGit.includes(entrada)) {
    rojo(`«.dockerignore» declara la excepcion «${entrada}» («${motivo}») y «.gitignore» ya no la nombra.`);
  } else if (cubiertaPor(entrada, patronesDocker)) {
    rojo(`«.dockerignore» declara la excepcion «${entrada}» y ademas la excluye: sobra una de las dos.`);
  }
}

// ---------------------------------------------------------------------------
// `Dockerfile`

/** Las instrucciones, con las continuaciones «\» ya unidas. */
function instruccionesDe(texto) {
  const salida = [];
  const lineas = texto.split('\n');
  let acumulado = null;
  let linea = 0;
  for (let i = 0; i < lineas.length; i++) {
    const cruda = lineas[i];
    if (acumulado === null) {
      if (/^\s*(#|$)/.test(cruda)) continue;
      acumulado = cruda.trim();
      linea = i + 1;
    } else {
      if (/^\s*#/.test(cruda)) continue;
      acumulado += ` ${cruda.trim()}`;
    }
    if (acumulado.endsWith('\\')) {
      acumulado = acumulado.slice(0, -1);
      continue;
    }
    salida.push({ texto: acumulado.replace(/\s+/g, ' ').trim(), linea });
    acumulado = null;
  }
  if (acumulado !== null) salida.push({ texto: acumulado.replace(/\s+/g, ' ').trim(), linea });
  return salida;
}

const dockerfile = instruccionesDe(leer('Dockerfile'));

const desdeNginx = dockerfile
  .map((i) => i.texto.match(/^FROM\s+(nginx:\S+)/i)?.[1])
  .filter(Boolean)
  .pop();
if (!desdeNginx) {
  noSeMidio('«Dockerfile» no tiene ningun «FROM nginx:…»: este arnes sondea la base que el Dockerfile usa.');
}

const versionado = (ruta) =>
  spawnSync('git', ['ls-files', '--error-unmatch', '--', ruta], { cwd: RAIZ, encoding: 'utf8' }).status === 0;

// Sin git, «lo que git ve» no se puede contestar y TODO saldria rojo por el mismo
// motivo falso. Decirlo es lo unico que distingue «esta mal» de «no lo se».
if (spawnSync('git', ['rev-parse', '--is-inside-work-tree'], { cwd: RAIZ, encoding: 'utf8' }).status !== 0) {
  noSeMidio('«' + RAIZ + '» no es un arbol de git, y la mitad de este arnes pregunta que ve git y que no.');
}

let copiaElContexto = false;
for (const instruccion of dockerfile) {
  if (!/^COPY\s/i.test(instruccion.texto)) continue;
  const piezas = partir(instruccion.texto).slice(1);
  if (piezas.some((p) => p.startsWith('--from='))) continue;
  const origenes = piezas.filter((p) => !p.startsWith('--')).slice(0, -1);
  for (const origen of origenes) {
    if (origen === '.') {
      copiaElContexto = true;
      continue;
    }
    if (!versionado(origen)) {
      rojo(
        `«Dockerfile» linea ${instruccion.linea} copia «${origen}», que git no versiona.\n` +
          '  Lo que entra en la etapa de construccion participa en «yarn build», y lo que Vite hornee\n' +
          '  ahi se publica. Un origen que git no ve no lo revisa nadie.',
      );
    }
  }
}

if (!copiaElContexto) {
  noSeMidio(
    'Ningun «COPY» del «Dockerfile» copia el contexto entero, y las dos comprobaciones de arriba\n' +
      '  —«.gitignore» contra «.dockerignore» y los archivos de entorno de Vite— existen porque algo\n' +
      '  lo copia: sin un «COPY . .» se cumplen solas y no dicen nada. Si el Dockerfile paso a copiar\n' +
      '  solo lo que nombra, lo que hay que rehacer es este arnes.',
  );
}

for (const instruccion of dockerfile) {
  const declara = instruccion.texto.match(/^(ARG|ENV)\s+(.*)$/i);
  if (!declara) continue;
  for (const nombre of partir(declara[2]).map((p) => p.split('=')[0])) {
    if (OLOR_A_CREDENCIAL.test(nombre)) {
      rojo(
        `«Dockerfile» linea ${instruccion.linea} declara «${nombre}».\n` +
          '  Vite resuelve «import.meta.env» AL COMPILAR, asi que eso queda EN CLARO dentro de\n' +
          '  «assets/index-*.js», que es el archivo que se publica en «ghcr.io». El propio Dockerfile\n' +
          '  afirma lo contrario: «NINGUN token se hornea en la imagen».',
      );
    }
  }
}

/**
 * El `USER` de la imagen, **EN NUMERO**.
 *
 * `infrastructure/src/descriptor.ts` le pone a este contenedor `runAsNonRoot: true` y NO le pone
 * `runAsUser`, y eso solo es correcto porque la imagen declara su uid en numero: el kubelet no
 * puede comprobar que un `USER` nombrado no sea root —tendria que leer `/etc/passwd` dentro de
 * una imagen que aun no ha arrancado—, asi que se niega y el pod queda en
 * `CreateContainerConfigError`.
 *
 * Es un fallo que **solo aparece al desplegar**, sobre una imagen que localmente arranca
 * perfectamente. Se comprueba aqui y no en el descriptor porque aquel paquete **no declara
 * `@types/node` a proposito** —un descriptor es una funcion pura que no lee ni el disco ni el
 * entorno (ADR-0031 §2)— y la mitad que si puede afirmar, que no haya `runAsUser`, la afirma alli.
 */
const users = dockerfile
  .map((i) => ({ linea: i.linea, valor: i.texto.match(/^USER\s+(\S+)/i)?.[1] }))
  .filter((u) => u.valor !== undefined);

if (users.length === 0) {
  rojo(
    'El «Dockerfile» no declara ningun «USER»: el contenedor correria como root.\n' +
      '  Y con `runAsNonRoot: true` en el descriptor, el kubelet ni siquiera lo arrancaria.',
  );
}
for (const u of users) {
  if (!/^\d+$/.test(u.valor)) {
    rojo(
      `«Dockerfile» linea ${u.linea} dice «USER ${u.valor}», que no es un numero.\n` +
        '  El descriptor le pone «runAsNonRoot: true» sin «runAsUser», y el kubelet no puede\n' +
        '  verificar un usuario nombrado: se niega a arrancar el contenedor con un\n' +
        '  «CreateContainerConfigError» que NO aparece aqui ni al construir la imagen, solo al\n' +
        '  desplegar. El uid de «nginx» en esta imagen base es 101, que es lo que usan las\n' +
        '  interfaces de `rentas` y de `caja`.',
    );
  }
}

/**
 * Y que este nginx **no reenvie a ningun sitio**.
 *
 * Aqui habia `proxy_pass http://catastro:8080` —el nombre del servicio del `compose.yaml`— y en
 * Kubernetes no existe ningun `Service` que se llame asi: el del backend es
 * `kamayuk-catastro-web`. Nginx resuelve el anfitrion de un `proxy_pass` **AL ARRANCAR**, asi que
 * el pod no habria arrancado, con un `[emerg] host not found in upstream "catastro"` que habla de
 * nginx y no del manifiesto. Es `catastro`#102, y lo tiene medido `infrastructure` en
 * `infra/verificaciones/upstream-de-la-interfaz.ts`.
 *
 * El mismo origen —que es lo que aquel reenvio conseguia, porque el backend no publica ni una
 * cabecera de CORS— lo da ahora el ingreso, que parte `/catastro` en dos.
 *
 * **Se lee sin comentarios**: la cabecera de `nginx.conf` NOMBRA la directiva para explicar por
 * que ya no esta, y contarla dejaria esta guarda roja sobre un archivo correcto. Es la leccion
 * que este proyecto lleva anotada tres veces —el `grep -c proxy_pass` de `caja#16`, los rotulos
 * del panel de `catastro#10` y el escaner que se cazo a si mismo de `caja#37`—.
 */
const reenvios = leer('nginx.conf')
  .split('\n')
  .map((l, i) => ({ linea: i + 1, texto: l.includes('#') ? l.slice(0, l.indexOf('#')) : l }))
  .filter((l) => /^\s*proxy_pass\s/.test(l.texto));

for (const r of reenvios) {
  rojo(
    `«nginx.conf» linea ${r.linea} reenvia: «${r.texto.trim()}».\n` +
      '  Esta interfaz no habla con nadie: el mismo origen lo da el ingreso, que manda\n' +
      '  «/catastro/api/v1» al backend y «/catastro» aqui dentro del mismo Host. Un «proxy_pass»\n' +
      '  aqui apunta a un nombre que el cluster no resuelve —nginx lo resuelve AL ARRANCAR, asi\n' +
      '  que el pod no arranca— y ademas exigiria abrirle a este pod una salida de red hacia el\n' +
      '  backend que su NetworkPolicy le niega a proposito.',
  );
}

// ---------------------------------------------------------------------------
// Levantar nginx y preguntar

const docker = (args, opciones = {}) => spawnSync('docker', args, { encoding: 'utf8', ...opciones });

if (docker(['version', '--format', '{{.Server.Version}}']).status !== 0) {
  noSeMidio(
    'No hay un Docker que responda, y esta comprobacion se mide levantando nginx de verdad.\n' +
      '  No se omite: una comprobacion que se salta a si misma deja el flujo en verde sin haber\n' +
      '  verificado nada.',
  );
}

let contenedor = null;
let raizSembrada = null;
let sondeadas = 0;
let comprobadas = 0;

// Si algo revienta a media medicion, el contenedor no se queda vivo: uno huerfano por
// corrida convierte «se me acabo el disco» en el sintoma de un arnes que nadie mira.
process.on('exit', () => {
  if (contenedor) docker(['rm', '-f', contenedor]);
  if (raizSembrada) rmSync(raizSembrada, { recursive: true, force: true });
});

if (sinMedir.length === 0) {
  raizSembrada = mkdtempSync(path.join(tmpdir(), 'catastro-imagen-'));
  for (const s of sondas) {
    if (!s.sembrar) continue;
    const destino = path.join(raizSembrada, s.sembrar);
    mkdirSync(path.dirname(destino), { recursive: true });
    writeFileSync(destino, 'sonda del arnes\n');
  }

  if (docker(['image', 'inspect', desdeNginx]).status !== 0) {
    docker(['pull', desdeNginx], { stdio: 'inherit', encoding: undefined });
  }

  // Sin `--add-host`, y es una linea que se fue con un defecto. Aqui habia
  // `--add-host catastro:127.0.0.1` porque `nginx.conf` llevaba un `proxy_pass http://catastro:8080`
  // y nginx resuelve el upstream AL ARRANCAR: sin esa entrada el contenedor moria con
  // «host not found in upstream "catastro"» y no habia a quien preguntar. Ese reenvio ya no
  // existe —el ingreso parte la ruta, ver la cabecera de `nginx.conf`—, asi que la entrada
  // sobra. Y sobra bien: mientras estuviera, un `proxy_pass` nuevo a un nombre que el cluster
  // no resuelve arrancaria AQUI en verde y solo fallaria al desplegar.
  const creado = docker(['create', desdeNginx]);
  if (creado.status !== 0) {
    noSeMidio(`No se pudo crear el contenedor de «${desdeNginx}»: ${creado.stderr.trim()}`);
  } else {
    contenedor = creado.stdout.trim();
    // Si una de las dos copias fallara en silencio, nginx serviria SU configuracion por
    // omision y las cabeceras faltarian: saldria rojo por un motivo inventado.
    const copias = [
      docker(['cp', path.join(RAIZ, 'nginx.conf'), `${contenedor}:/etc/nginx/conf.d/default.conf`]),
      docker(['cp', `${raizSembrada}/.`, `${contenedor}:/usr/share/nginx/html`]),
    ];
    const copiaRota = copias.find((c) => c.status !== 0);
    if (copiaRota) noSeMidio(`No se pudo meter «nginx.conf» ni la raiz en el contenedor: ${copiaRota.stderr.trim()}`);
    const arrancado = docker(['start', contenedor]);
    if (arrancado.status !== 0) noSeMidio(`nginx no arranco: ${arrancado.stderr.trim()}`);
  }
}

/** Una peticion cruda desde dentro del contenedor. Devuelve `null` si no contesta. */
function pedir(ruta) {
  const guion =
    'printf "GET %s HTTP/1.0\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n" "$RUTA"' +
    ` | busybox nc 127.0.0.1 ${puerto}`;
  const r = docker(['exec', '-e', `RUTA=${ruta}`, contenedor, 'sh', '-c', guion]);
  const cabeza = (r.stdout ?? '').split(/\r?\n\r?\n/)[0];
  const lineas = cabeza.split(/\r?\n/).filter((l) => l !== '');
  if (lineas.length === 0 || !lineas[0].startsWith('HTTP/')) return null;
  const cabeceras = new Map();
  for (const l of lineas.slice(1)) {
    const dosPuntos = l.indexOf(':');
    if (dosPuntos < 0) continue;
    const nombre = l.slice(0, dosPuntos).trim().toLowerCase();
    if (!cabeceras.has(nombre)) cabeceras.set(nombre, []);
    cabeceras.get(nombre).push(l.slice(dosPuntos + 1).trim());
  }
  return { estado: Number(lineas[0].split(' ')[1]), cabeceras };
}

/**
 * Las dos formas de que una cabecera declarada no llegue, distinguidas por la medida y
 * no por lo que parezca: si el bloque la declara y aun asi no sale en una respuesta de
 * error, lo que falta es el `always`; si no la declara, lo que falta es la propia
 * linea, porque el nivel de arriba no se hereda.
 */
function porQueFalta(sonda, nombre, estado) {
  const bloque = conCabeceras.find((b) => b.cabecera === sonda.de);
  const declarada = bloque?.directivas.find(
    (d) => d.texto.startsWith('add_header ') && partir(d.texto)[1]?.toLowerCase() === nombre,
  );
  if (declarada && estado >= 300) {
    return (
      `«${sonda.de}» la declara (linea ${declarada.linea}) y aun asi no llega en un ${estado}: le falta\n` +
      '  el «always». Sin el, la cabecera sale en las respuestas buenas y desaparece justo cuando algo\n' +
      '  va mal, que es cuando un navegador esta a punto de interpretar lo que le mandan.'
    );
  }
  return (
    '«add_header» no se hereda: nginx toma el conjunto del nivel mas interno que declare\n' +
    '  alguno y descarta el de arriba entero, asi que escribirla mas arriba no la enciende.\n' +
    '  Va REPETIDA dentro de cada «location» que declare cabeceras propias.'
  );
}

if (contenedor && sinMedir.length === 0) {
  let viva = null;
  for (let intento = 0; intento < 60 && !viva; intento++) {
    viva = pedir('/');
    if (viva) break;
    // Si el proceso ya murio no hay nada que esperar: nginx resuelve el upstream AL
    // ARRANCAR, asi que un nombre que no resuelve mata el contenedor en el primer
    // segundo y esperar medio minuto solo retrasa el mensaje que lo explica.
    if (docker(['inspect', '-f', '{{.State.Running}}', contenedor]).stdout.trim() !== 'true') break;
    spawnSync('sh', ['-c', 'sleep 0.5']);
  }
  if (!viva) {
    const logs = docker(['logs', '--tail', '20', contenedor]);
    noSeMidio(
      `nginx no llego a levantar con este «nginx.conf», asi que NO SE MIDIO NI UNA CABECERA.\n\n` +
        `${(logs.stdout + logs.stderr)
          .split('\n')
          .map((l) => `    ${l}`)
          .join('\n')}`,
    );
  } else {
    for (const s of sondas) {
      const r = pedir(s.ruta);
      sondeadas++;
      if (!r) {
        rojo(`«${s.ruta}» (${s.de}) no contesto nada.`);
        continue;
      }
      if (r.estado !== s.estado) {
        rojo(`«${s.ruta}» (${s.de}) contesto ${r.estado} y se esperaba ${s.estado}.`);
        continue;
      }
      for (const [nombre, valor] of Object.entries(CABECERAS_DE_SEGURIDAD)) {
        comprobadas++;
        const servido = r.cabeceras.get(nombre);
        if (!servido) {
          rojo(
            `«${s.ruta}» (${r.estado}, de «${s.de}») NO devuelve «${nombre}».\n  ${porQueFalta(s, nombre, r.estado)}`,
          );
        } else if (!servido.includes(valor)) {
          rojo(`«${s.ruta}» devuelve «${nombre}: ${servido.join(', ')}» y el contrato dice «${valor}».`);
        }
      }
    }
  }
}

// La ultima trampa es la del propio arnes, y se destapo apartando el bloque de arriba
// para probar la mitad que no necesita Docker: **el informe seguia diciendo «las
// cabeceras llegan en todas las rutas» con CERO preguntas hechas**, porque las tres
// comprobaciones de archivo salian limpias y nadie contaba las sondas. Hoy ese camino
// no es alcanzable —todo lo que salta el bloque pasa antes por `noSeMidio`—, pero eso
// es una propiedad del orden de las lineas y no una afirmacion: se cuenta.
if (sondeadas !== sondas.length || comprobadas === 0) {
  noSeMidio(
    `Se sondearon ${sondeadas} de las ${sondas.length} ruta(s) y se comprobaron ${comprobadas} cabecera(s):\n` +
      '  el recorrido por HTTP no llego a hacerse entero, asi que no hay nada que afirmar de las\n' +
      '  cabeceras. Un recorrido vacio no es un recorrido en el que todo estuviera bien.',
  );
}

// ---------------------------------------------------------------------------

if (sinMedir.length > 0) {
  // Lo que SI se pudo medir se dice igual, y esto no es cosmetica. Dos de las tres
  // afirmaciones de este arnes —`.gitignore` contra `.dockerignore`, y lo que el
  // `Dockerfile` copia— no necesitan Docker, y sin esto se perdian enteras en cuanto
  // faltaba: quien no tiene Docker recibia CERO senal de una comprobacion que podia
  // darle dos tercios. Callar un defecto que si se vio, porque otra cosa no se pudo
  // ver, es la forma de silencio que este arnes existe para impedir.
  if (fallos.length > 0) {
    console.error(`\n${fallos.length} defecto(s) que SI se pudieron medir:\n`);
    for (const f of fallos) console.error(`  ${f}\n`);
  }
  console.error(`\nY ademas, esta comprobacion NO MIDIO lo que existe para medir:\n`);
  for (const q of sinMedir) console.error(`  ${q}\n`);
  // Sale con 2 y no con 1 a proposito, aunque haya defectos: «no se pudo medir» tiene
  // que seguir distinguiendose de «se midio y esta mal», que es lo que decide si el
  // rojo se arregla mirando el codigo o mirando el entorno.
  process.exit(2);
}

console.log(
  `${desdeNginx} en el ${puerto} · ${sondeadas} ruta(s) sondeada(s) y ${comprobadas} cabecera(s) comprobada(s) ` +
    `sobre ${conCabeceras.length} «location» con cabeceras propias · ` +
    `${deGit.length} entrada(s) de .gitignore y los ${deEntorno.length} archivos de entorno que lee Vite, ` +
    `contra ${patronesDocker.length} patron(es) de .dockerignore · ` +
    `${users.length} «USER» y ${reenvios.length} reenvio(s)`,
);

if (fallos.length > 0) {
  console.error(`\n${fallos.length} defecto(s) en como se sirve la interfaz:\n`);
  for (const f of fallos) console.error(`  ${f}\n`);
  process.exit(1);
}

console.log(
  'las cabeceras llegan en todas las rutas, el uid esta en numero, este nginx no reenvia a nadie, y\n' +
    'nada que git no vea entra en la imagen',
);
