/**
 * En `src/datos/` no hay ni una cifra.
 *
 *   node verificaciones/datos.mjs
 *
 * <h2>Que existe para impedir</h2>
 *
 * En el precedente, `sgtm/frontend/src/datos/inicio.ts` acabo siendo un archivo
 * con `export {}` y treinta lineas explicando por que se borro lo que habia. Lo
 * que habia eran las cifras del prototipo, y su problema no era estar mal: era
 * ser **indistinguibles de las buenas**. Un total de padron escrito a mano se
 * pinta exactamente igual que uno leido del servidor, y quien mira la pantalla
 * no tiene como decir cual es cual. `sin-red.mjs` caza la que llega hasta la
 * pantalla; esto caza la que se escribe, que es antes.
 *
 * <h2>Que se admite, y por que esa lista y no otra</h2>
 *
 * Una **referencia** no es una cifra: `ADR-0024` nombra una decision, `D-11`
 * una decision abierta, `422` un codigo de protocolo. Ninguna de las tres se
 * puede leer como un dato del padron ni acaba dentro de una tabla. Todo lo
 * demas —un decimal, un total, un ano suelto, un porcentaje— es un dato, y un
 * dato sale de una lectura.
 *
 * La lista se imprime entera cuando algo falla, para que quien la lea pueda
 * decidir si lo suyo es una referencia mas o una cifra que sobra.
 *
 * No abre navegador y no necesita backend: lee fuentes.
 */
import { readFile, readdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

const DIRECTORIO = new URL('../src/datos/', import.meta.url).pathname;

/** Lo que un numero puede estar nombrando sin ser una cifra. */
const REFERENCIAS = [
  { nombre: 'una decision de arquitectura', re: /ADR-\d{4}/g },
  { nombre: 'un requisito no funcional', re: /RNF-\d{3}/g },
  { nombre: 'un documento de arquitectura', re: /ARQ-\d{2}/g },
  { nombre: 'un riesgo tecnico', re: /RT-\d{3}/g },
  { nombre: 'un documento de gobierno', re: /GOB-\d{2}/g },
  { nombre: 'una decision abierta', re: /D-\d{2}[a-z]?/g },
  { nombre: 'un issue', re: /#\d{1,4}/g },
  { nombre: 'un paragrafo', re: /§\d+(?:\.\d+)*/g },
  { nombre: 'una resolucion ministerial', re: /R\.M\.\s?\d{3}-\d{4}/g },
  { nombre: 'un anexo', re: /Anexo\s+[IVX]+(?:\.\d+)?/g },
  /* Los estados que este backend emite, uno a uno y no «cualquier 4xx»: con el
     patron ancho, el «412» de un importe de 18,412 se comia como si fuera un
     codigo y el mensaje senalaba «18,» en vez de la cifra entera. Medido al
     romperlo a proposito. La lista es la de `CodigoDeError`, mas el 307 del
     reenvio de conciliacion. */
  { nombre: 'un estado HTTP del backend', re: /\b(?:307|401|403|404|405|409|422|500|502)\b/g },
];

/**
 * Parte un fuente en codigo, cadenas y comentarios.
 *
 * Hace falta el recorrido a mano y no basta un `replace`: un `//` dentro de una
 * cadena no abre un comentario, y una comilla dentro de un comentario no abre
 * una cadena. Con expresiones regulares sueltas, las dos confusiones dejan
 * pasar justo lo que se busca.
 */
function partir(fuente) {
  const codigo = [];
  const cadenas = [];
  let i = 0;
  while (i < fuente.length) {
    const c = fuente[i];
    const siguiente = fuente[i + 1];
    if (c === '/' && siguiente === '/') {
      while (i < fuente.length && fuente[i] !== '\n') i++;
      continue;
    }
    if (c === '/' && siguiente === '*') {
      i += 2;
      while (i < fuente.length && !(fuente[i] === '*' && fuente[i + 1] === '/')) i++;
      i += 2;
      continue;
    }
    if (c === "'" || c === '"' || c === '`') {
      const cierre = c;
      let dentro = '';
      i++;
      while (i < fuente.length && fuente[i] !== cierre) {
        if (fuente[i] === '\\') {
          dentro += fuente[i + 1] ?? '';
          i += 2;
          continue;
        }
        dentro += fuente[i];
        i++;
      }
      i++;
      cadenas.push(dentro);
      continue;
    }
    codigo.push(c);
    i++;
  }
  return { codigo: codigo.join(''), cadenas };
}

/** Lo que queda de un texto cuando se le quitan las referencias conocidas. */
function sinReferencias(texto) {
  let resto = texto;
  for (const { re } of REFERENCIAS) resto = resto.replace(re, ' ');
  return resto;
}

if (!existsSync(DIRECTORIO)) {
  console.error(
    `No existe «${DIRECTORIO}».\n` +
      'Esta comprobacion mide el directorio de rotulos; sin el no puede medir nada, y pasar en verde\n' +
      'sin haber mirado seria peor que no existir.',
  );
  process.exit(2);
}

const archivos = (await readdir(DIRECTORIO)).filter((f) => f.endsWith('.ts')).sort();
if (archivos.length === 0) {
  console.error(
    `«${DIRECTORIO}» no tiene ningun archivo: esta comprobacion se estaria cumpliendo sola.\n` +
      'Los rotulos del modulo viven ahi; si de verdad no queda ninguno, quita este arnes.',
  );
  process.exit(2);
}

const fallos = [];
let cadenasMiradas = 0;

for (const archivo of archivos) {
  const fuente = await readFile(join(DIRECTORIO, archivo), 'utf8');
  const { codigo, cadenas } = partir(fuente);

  const enElCodigo = codigo.match(/\d[\d._]*/g) ?? [];
  for (const hallado of new Set(enElCodigo)) {
    fallos.push(
      `${archivo}: el literal numerico «${hallado}» esta en CODIGO.\n` +
        '      Un numero aqui es un dato, y los datos salen de una lectura: una cifra escrita a mano se\n' +
        '      pinta igual que una leida del servidor y nadie que mire la pantalla puede decir cual es cual.',
    );
  }

  for (const cadena of cadenas) {
    cadenasMiradas++;
    const resto = sinReferencias(cadena);
    const sueltos = resto.match(/\d[\d.,]*/g) ?? [];
    for (const hallado of new Set(sueltos)) {
      fallos.push(
        `${archivo}: la cadena «${cadena.slice(0, 72)}${cadena.length > 72 ? '…' : ''}» trae «${hallado}».\n` +
          '      No casa con ninguna referencia conocida, asi que se lee como una cifra del dominio.',
      );
    }
  }
}

/* La guarda sobre la guarda: si no se miro ni una cadena, esto no mide nada.
   Es la trampa que #32 midio con las marcas del paquete —medir la ausencia de
   un nombre no es medir la ausencia del codigo—, por el otro eje. */
if (cadenasMiradas === 0) {
  console.error(
    `Se leyeron ${archivos.length} archivo(s) y ninguna cadena: el troceador no encontro nada que mirar,\n` +
      'asi que esta comprobacion se estaria cumpliendo sola. Revisa `partir()`.',
  );
  process.exit(2);
}

if (fallos.length) {
  console.error(`\n${fallos.length} cifra(s) en «src/datos/»:\n`);
  for (const f of fallos) console.error('  - ' + f + '\n');
  console.error('  Lo unico que puede llevar digitos aqui es una referencia, y son estas:');
  for (const r of REFERENCIAS) console.error(`    · ${r.nombre.padEnd(34)} ${r.re.source}`);
  process.exit(1);
}

console.log(
  `${archivos.length} archivo(s) de datos y ${cadenasMiradas} cadenas: ni una cifra que no sea una referencia`,
);
