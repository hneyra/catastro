/**
 * Las reglas de `eslint.config.mjs` muerden.
 *
 *   node verificaciones/reglas.mjs
 *
 * Cada prohibicion tiene su muestra que la viola, y esto exige que ESLint la
 * detecte. **Una regla que no puede fallar no protege nada** — es la misma
 * exigencia que `ReglasDeArquitecturaMuerdenTest` impone en el backend, y el
 * mismo argumento por el que la prueba de aislamiento demuestra que el
 * superusuario omite RLS en vez de afirmarlo.
 *
 * Las muestras estan en `ignores` de la configuracion para que `yarn lint` no
 * las senale; aqui se lintan como TEXTO, con una ruta sintetica dentro de
 * `src/modulos/`, que es donde la regla tiene que aplicar de verdad.
 *
 * **Y se comprueba tambien lo contrario**, que es la mitad que se olvida: que la
 * puerta SI pueda llamar a `fetch` y el proxy SI pueda sustituirlo. Una
 * excepcion que no se mide se convierte en una prohibicion universal el dia que
 * alguien reordena los bloques de la configuracion, y entonces la regla no
 * protege: impide.
 *
 * No abre navegador y no necesita backend.
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ESLint } from 'eslint';

const AQUI = dirname(fileURLToPath(import.meta.url));
const RAIZ = join(AQUI, '..');
const eslint = new ESLint({ cwd: RAIZ });

async function mensajesDe(muestra, rutaRelativa) {
  const codigo = readFileSync(join(AQUI, 'muestras', muestra), 'utf8');
  const [resultado] = await eslint.lintText(codigo, { filePath: join(RAIZ, rutaRelativa) });
  return (resultado?.messages ?? []).map((m) => `${m.ruleId ?? '?'}: ${m.message}`);
}

/** Una ruta de pantalla: donde la regla tiene que aplicar. */
const EN_UNA_PANTALLA = 'src/modulos/catastro/Muestra.ts';

/** Cada prohibicion, su muestra y el texto que la delata. */
const PROHIBICIONES = [
  {
    prohibicion: 'identificador con tilde',
    muestra: 'identificador-con-tilde.ts',
    en: EN_UNA_PANTALLA,
    delata: /Sin tildes ni enie en identificadores/,
  },
  {
    prohibicion: 'fetch suelto en una pantalla',
    muestra: 'fetch-en-una-pantalla.ts',
    en: EN_UNA_PANTALLA,
    delata: /Ningun fetch suelto/,
  },
  {
    prohibicion: 'fetch sustituido fuera del proxy',
    muestra: 'fetch-sustituido.ts',
    en: EN_UNA_PANTALLA,
    delata: /Solo la puerta .* y el proxy de datos .* nombran fetch/,
  },
  {
    prohibicion: 'Number() sobre un campo que viaja como texto',
    muestra: 'numero-sobre-un-texto.ts',
    en: EN_UNA_PANTALLA,
    delata: /viajan como texto JSON con decimal plano/,
  },
  {
    prohibicion: 'aritmetica sobre un importe',
    muestra: 'aritmetica-sobre-un-importe.ts',
    en: EN_UNA_PANTALLA,
    delata: /Ninguna aritmetica sobre un importe/,
  },
  {
    prohibicion: 'any explicito',
    muestra: 'any-explicito.ts',
    en: EN_UNA_PANTALLA,
    delata: /no-explicit-any/,
  },
  /* La hermana de la de arriba, y no la misma: aquella se ancla al NOMBRE del
     campo y esta no se ancla a nada. Un `parseFloat` sobre una variable
     intermedia se escapa de la primera y pierde el decimal igual. */
  {
    prohibicion: 'parseFloat, sobre lo que sea',
    muestra: 'parsefloat-sobre-un-texto.ts',
    en: EN_UNA_PANTALLA,
    delata: /Nada de parseFloat/,
  },
  /* La regla del texto vale TAMBIEN dentro de la puerta: que `cliente.ts` pueda
     llamar a `fetch` no le da permiso para convertir un importe a numero. */
  {
    prohibicion: 'Number() tampoco se admite dentro de la puerta',
    muestra: 'numero-sobre-un-texto.ts',
    en: 'src/api/cliente.ts',
    delata: /viajan como texto JSON con decimal plano/,
  },
  /**
   * **El proxy puede SUSTITUIR `fetch` y no puede LLAMARLO.**
   *
   * Y este caso no estaba, hasta que se midio: ensanchar la excepcion del proxy
   * para que tambien pudiera llamar a `fetch` suelto pasaba en VERDE. Lo que
   * habia escrito era la mitad de la afirmacion —que el proxy puede nombrarlo—
   * y nada comprobaba la otra —que sigue sin poder llamarlo—, asi que la
   * excepcion se podia ensanchar sin ruido. Importa porque un proxy que llama a
   * `fetch` suelto **se llama a si mismo**: sustituyo `globalThis.fetch`, de modo
   * que delegar en `fetch` en vez de en la funcion que guardo es un bucle
   * infinito, y de los que no se ven al escribirlos.
   */
  {
    prohibicion: 'el proxy sustituye fetch, pero no lo llama',
    muestra: 'fetch-en-una-pantalla.ts',
    en: 'src/simulado/proxy.ts',
    delata: /Ningun fetch suelto/,
  },
];

/** Y las excepciones, que hay que medir igual: aqui la regla NO debe disparar. */
const EXCEPCIONES = [
  {
    excepcion: 'la puerta si puede llamar a fetch',
    muestra: 'fetch-en-una-pantalla.ts',
    en: 'src/api/cliente.ts',
    calla: /Ningun fetch suelto/,
  },
  {
    excepcion: 'la puerta si puede nombrar fetch',
    muestra: 'fetch-sustituido.ts',
    en: 'src/api/cliente.ts',
    calla: /nombran fetch/,
  },
  {
    excepcion: 'el proxy si puede sustituir fetch',
    muestra: 'fetch-sustituido.ts',
    en: 'src/simulado/proxy.ts',
    calla: /nombran fetch/,
  },
];

const fallos = [];

for (const caso of PROHIBICIONES) {
  const mensajes = await mensajesDe(caso.muestra, caso.en);
  if (!mensajes.some((m) => caso.delata.test(m))) {
    fallos.push(
      `${caso.prohibicion}\n  la muestra «${caso.muestra}» en «${caso.en}» no disparo ${caso.delata}\n` +
        (mensajes.length === 0
          ? '  (ningun mensaje)'
          : mensajes.map((m) => '  · ' + m).join('\n')),
    );
  }
}

for (const caso of EXCEPCIONES) {
  const mensajes = await mensajesDe(caso.muestra, caso.en);
  const indebidos = mensajes.filter((m) => caso.calla.test(m));
  if (indebidos.length) {
    fallos.push(
      `${caso.excepcion}\n  «${caso.en}» tendria que poder hacerlo y la regla lo impide:\n` +
        indebidos.map((m) => '  · ' + m).join('\n'),
    );
  }
}

/* Y una guarda sobre la propia guarda: si un dia alguien borra una muestra, esta
   lista se queda corta y no lo dice nadie. Se cuenta contra los archivos que
   hay. */
const { readdirSync } = await import('node:fs');
const muestras = readdirSync(join(AQUI, 'muestras')).filter((f) => f.endsWith('.ts'));
const usadas = new Set([...PROHIBICIONES, ...EXCEPCIONES].map((c) => c.muestra));
for (const m of muestras) {
  if (!usadas.has(m)) fallos.push(`la muestra «${m}» no la comprueba nadie: es una regla sin verificacion`);
}

/**
 * Y **la otra direccion**, que es la que faltaba: toda regla tiene su muestra.
 *
 * `ReglasDeArquitecturaMuerdenTest` exige las dos —regla sin muestra y muestra
 * sin regla—, y aqui solo estaba escrita la segunda. La diferencia no es
 * simetrica: una muestra huerfana es un archivo de mas, y una regla sin muestra
 * es **una prohibicion que nadie ha comprobado que muerda**, que es justo lo que
 * este arnes existe para impedir.
 *
 * Medido antes de escribirlo: borrar de `eslint.config.mjs` la prohibicion de
 * `parseFloat` entera dejaba `yarn reglas` en VERDE con el mismo mensaje —«8
 * prohibiciones muerden sobre sus 6 muestras»— y `yarn lint` tambien, porque
 * ningun fuente la viola hoy. O sea que la prohibicion podia desaparecer sin que
 * nada lo dijera.
 *
 * Las prohibiciones se leen de la CONFIGURACION de verdad y no de una lista
 * escrita aqui. Las entradas propias de este repositorio son las que declaran
 * `files` y **no** traen `name`: los presets —`js.configs.recommended` y los
 * cuatro de `typescript-eslint`— o no acotan archivos o vienen con su nombre
 * puesto, y sus cientos de reglas no son prohibiciones de esta casa.
 */
const { default: configuracion } = await import('../eslint.config.mjs');
const propias = configuracion.filter((bloque) => bloque.files !== undefined && bloque.name === undefined);
const prohibicionesDeclaradas = new Map();
for (const bloque of propias) {
  for (const [regla, valor] of Object.entries(bloque.rules ?? {})) {
    if (regla !== 'no-restricted-syntax') {
      prohibicionesDeclaradas.set(regla, `${regla}: `);
      continue;
    }
    for (const opcion of Array.isArray(valor) ? valor.slice(1) : []) {
      const mensaje = typeof opcion === 'string' ? opcion : opcion.message;
      prohibicionesDeclaradas.set(`no-restricted-syntax :: ${mensaje}`, `no-restricted-syntax: ${mensaje}`);
    }
  }
}

if (prohibicionesDeclaradas.size === 0) {
  console.error(
    'No se leyo ni una prohibicion de «eslint.config.mjs», asi que esta mitad se estaria cumpliendo\n' +
      'sola. O el criterio de que bloques son propios dejo de valer, o la configuracion cambio de forma.',
  );
  process.exit(2);
}

for (const [nombre, comoLaVeriaEslint] of prohibicionesDeclaradas) {
  if (PROHIBICIONES.some((caso) => caso.delata.test(comoLaVeriaEslint))) continue;
  fallos.push(
    `«${nombre}» esta en «eslint.config.mjs» y ninguna muestra la ejerce.\n` +
      '  Una prohibicion sin muestra no se ha comprobado que muerda: se puede borrar entera y este\n' +
      '  arnes sigue en verde, y `yarn lint` tambien mientras ningun fuente la viole.',
  );
}

if (!fallos.length) {
  console.log(
    `${PROHIBICIONES.length} prohibiciones muerden sobre sus ${muestras.length} muestras · ` +
      `${EXCEPCIONES.length} excepciones siguen siendo excepciones · ` +
      `${prohibicionesDeclaradas.size} prohibiciones de «eslint.config.mjs», todas con muestra`,
  );
  process.exit(0);
}
console.log(`${fallos.length} reglas de ESLint no hacen lo que dicen:\n`);
for (const f of fallos) console.log('  - ' + f + '\n');
process.exit(1);
