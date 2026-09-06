#!/usr/bin/env node
/*
   Todo `Dockerfile` del arbol tiene quien publique su imagen.

   ## Que defecto cierra

   `frontend/` entro en #37 con su `Dockerfile` y su `nginx.conf`, y **nadie construia esa
   imagen, nadie la publicaba y ningun manifiesto la nombraba** (#40). Lo unico observable
   habria sido que la interfaz no esta: sin un error, sin un pod rojo, sin una linea de CI.
   El sintoma es la ausencia de sintoma, que es el mismo modo de fallo que C-6 midio con el
   Job que arrancaba, no cargaba nada y salia con codigo 0.

   ## Por que se deriva del DISCO y no de una lista

   Porque una lista escrita a mano se desincroniza el primer mes, y su modo de fallo es
   justo el que hay que impedir: **el `Dockerfile` nuevo no aparece en ella**. Es la leccion
   de C-7 con las variables del descriptor y la de C-20 con el censo de trabajos. Aqui los
   `Dockerfile` se buscan recorriendo el arbol, y la matriz se lee del propio workflow.

   ## Las cuatro cosas que comprueba

     1. Todo `Dockerfile` del arbol lo publica alguna entrada de la matriz, o esta EXENTO
        con su motivo escrito.
     2. Toda entrada de la matriz nombra un `archivo` que existe.
     3. Y un `destino` que es una etapa declarada en ese archivo. Un `target` que no existe
        no falla al construir: Docker construye la ULTIMA etapa, que puede ser otra imagen
        entera.
     4. La lista del trabajo `comprobar` —la que le pregunta al registro si la etiqueta se
        puede pedir— trae exactamente las mismas imagenes que la matriz. Son dos sitios con
        la misma verdad, que es la forma de defecto que C-17 encontro cinco veces; aqui su
        modo de fallo es que se publica una imagen que nadie comprueba.

   ## Uso

     node docs/00-gobierno/verificar-quien-publica-cada-imagen.mjs [--raiz <dir>]

   `--raiz` existe para su autoprueba: sin poder apuntarla a un arbol fabricado, demostrar
   que muerde exigiria romper el repositorio de verdad.

   ## Los codigos de salida son tres, y el tercero importa

     0  todo cuadra.
     1  hay hallazgos: se nombran uno a uno.
     2  NO SE PUDO MEDIR —cero `Dockerfile`, o cero entradas de matriz—. Una comprobacion
        que se queda sin sujeto pasaria en verde sin sujeto, que es peor que un rojo porque
        no habla de lo que vigila. Es la leccion que #32, #33 y #34 dejaron tres veces.
*/

import { readFileSync, readdirSync, existsSync, statSync } from 'node:fs';
import { join, relative, dirname, basename, sep } from 'node:path';

/** Lo que no se recorre. Ni codigo ni artefactos: solo ruido y volumen. */
const NO_SE_RECORRE = new Set(['.git', 'node_modules', 'build', 'dist', '.gradle', '.capturas', '.idea']);

/**
 * Los `Dockerfile` que NO tienen que publicarse, con su motivo.
 *
 * Nace vacia y es la lista de trabajo pendiente: quitarle una entrada tiene que ponerse
 * roja, igual que `busquedasDeTextoLibreConMotivo()` de T-0. Un `Dockerfile` exento sin
 * motivo no es una exencion, es un olvido con permiso.
 */
const EXENTOS = Object.freeze({
  // 'ejemplos/Dockerfile': 'motivo por el que este no se publica, y hasta cuando',
});

const WORKFLOW = '.github/workflows/publicar-imagenes.yml';

/** El primer ancestro con un `.git`. `existsSync` y no `isDirectory`: en un worktree es un ARCHIVO. */
function raizDelRepositorio(desde) {
  let actual = desde;
  while (actual && !existsSync(join(actual, '.git'))) {
    const padre = dirname(actual);
    actual = padre === actual ? null : padre;
  }
  if (!actual) throw new Error(`No se encontro la raiz del repositorio desde ${desde}`);
  return actual;
}

/** Todo archivo que se llame `Dockerfile` o `Dockerfile.<algo>`, en rutas relativas a la raiz. */
function dockerfilesDelArbol(raiz) {
  const encontrados = [];
  const recorrer = (dir) => {
    for (const entrada of readdirSync(dir, { withFileTypes: true })) {
      if (NO_SE_RECORRE.has(entrada.name)) continue;
      const ruta = join(dir, entrada.name);
      if (entrada.isDirectory()) recorrer(ruta);
      else if (/^Dockerfile(\..+)?$/.test(entrada.name)) encontrados.push(relative(raiz, ruta).split(sep).join('/'));
    }
  };
  recorrer(raiz);
  return encontrados.sort();
}

/** Las etapas con nombre de un Dockerfile: `FROM <lo que sea> AS <nombre>`. */
function etapasDe(contenido) {
  const etapas = [];
  for (const linea of contenido.split('\n')) {
    const coincidencia = /^\s*FROM\s+\S+\s+AS\s+([A-Za-z0-9_.-]+)/i.exec(linea);
    if (coincidencia) etapas.push(coincidencia[1]);
  }
  return etapas;
}

/**
 * Las entradas de la matriz, leidas del YAML como texto.
 *
 * Como en `ClonesHermanosDelWorkflowTest`: meter una dependencia de YAML en una guarda de
 * cuatro claves seguidas seria pagar mas de lo que se compra. Una entrada empieza por
 * `- ` y las siguientes lineas con la misma sangria le pertenecen.
 */
function entradasDeLaMatriz(yaml) {
  const entradas = [];
  let actual = null;
  let dentro = false;
  for (const cruda of yaml.split('\n')) {
    const linea = cruda.trim();
    if (linea === 'include:') { dentro = true; continue; }
    if (!dentro) continue;
    if (linea.startsWith('#') || linea === '') continue;
    // Otra clave del mismo nivel cierra el bloque: `steps:`, `runs-on:`…
    if (!linea.startsWith('- ') && !/^[a-z]+:/.test(linea)) continue;
    if (/^(steps|runs-on|name|jobs|strategy|fail-fast|matrix):/.test(linea)) { dentro = false; continue; }

    const cuerpo = linea.startsWith('- ') ? linea.slice(2) : linea;
    if (linea.startsWith('- ')) { actual = {}; entradas.push(actual); }
    const par = /^([a-z]+):\s*(\S.*)$/.exec(cuerpo);
    if (par && actual) actual[par[1]] = par[2].trim();
  }
  return entradas;
}

/** La lista del trabajo `comprobar`: `for imagen in a b c; do`. */
function imagenesQueSeComprueban(yaml) {
  const coincidencia = /for\s+imagen\s+in\s+([^;]+);\s*do/.exec(yaml);
  return coincidencia ? coincidencia[1].trim().split(/\s+/) : [];
}

function principal(argv, exentos = EXENTOS) {
  const iRaiz = argv.indexOf('--raiz');
  const raiz = iRaiz >= 0 ? argv[iRaiz + 1] : raizDelRepositorio(process.cwd());

  const rutaDelWorkflow = join(raiz, WORKFLOW);
  if (!existsSync(rutaDelWorkflow)) {
    console.error(`No esta «${WORKFLOW}»: sin el no hay nada contra lo que contrastar.`);
    return 2;
  }
  const yaml = readFileSync(rutaDelWorkflow, 'utf8');

  const dockerfiles = dockerfilesDelArbol(raiz);
  const entradas = entradasDeLaMatriz(yaml);
  const comprobadas = imagenesQueSeComprueban(yaml);

  // El contraste. Sin el, «todo cuadra» seria compatible con no haber encontrado nada.
  if (dockerfiles.length === 0) {
    console.error('No se encontro ni un `Dockerfile` en el arbol: esta comprobacion no midio nada.');
    return 2;
  }
  if (entradas.length === 0) {
    console.error(
      `No se leyo ni una entrada de la matriz de «${WORKFLOW}»: o el workflow dejo de usarla` +
        ' —y entonces esta guarda sobra— o cambio su forma y hay que ensenarsela.',
    );
    return 2;
  }

  const hallazgos = [];

  // 1. Todo Dockerfile tiene quien lo publique, o esta exento con su motivo.
  const publicados = new Set(entradas.map((e) => e.archivo));
  for (const dockerfile of dockerfiles) {
    if (publicados.has(dockerfile)) continue;
    const motivo = exentos[dockerfile];
    if (motivo) continue;
    hallazgos.push(
      `«${dockerfile}» esta en el arbol y ninguna entrada de la matriz lo publica. Un Dockerfile` +
        ' sin publicador no da error: la imagen simplemente no llega a ningun ambiente, y lo unico' +
        ' observable es que el servicio no esta. Si es a proposito, declaralo en EXENTOS con su' +
        ' motivo y hasta cuando.',
    );
  }

  // 1b. Y una exencion de algo que ya no existe es ruido que oculta la siguiente.
  for (const exento of Object.keys(exentos)) {
    if (!dockerfiles.includes(exento)) {
      hallazgos.push(`«${exento}» esta declarado EXENTO y no existe en el arbol: sobra la exencion.`);
    }
  }

  for (const entrada of entradas) {
    const { archivo, destino, imagen } = entrada;
    if (!archivo || !destino || !imagen) {
      hallazgos.push(
        `Una entrada de la matriz no declara archivo, destino e imagen: ${JSON.stringify(entrada)}`,
      );
      continue;
    }

    // 2. El archivo que nombra existe.
    const ruta = join(raiz, archivo);
    if (!existsSync(ruta) || !statSync(ruta).isFile()) {
      hallazgos.push(`La matriz publica «${imagen}» desde «${archivo}», que no existe.`);
      continue;
    }

    // 3. Y el destino es una etapa declarada de ese archivo.
    const etapas = etapasDe(readFileSync(ruta, 'utf8'));
    if (!etapas.includes(destino)) {
      hallazgos.push(
        `La matriz publica «${imagen}» desde la etapa «${destino}» de «${archivo}», y ese archivo` +
          ` declara ${etapas.length ? etapas.map((e) => `«${e}»`).join(', ') : 'ninguna'}. Un` +
          ' `target` que no existe NO falla: Docker construye la ultima etapa, que puede ser otra' +
          ' imagen entera, y se publica con el nombre de esta.',
      );
    }
  }

  // 4. Lo que se publica es lo que se comprueba.
  const deLaMatriz = [...new Set(entradas.map((e) => e.imagen).filter(Boolean))].sort();
  const delComprobador = [...new Set(comprobadas)].sort();
  if (delComprobador.length === 0) {
    console.error(
      'No se leyo la lista del trabajo `comprobar`. Sin ella, publicar sin comprobar pasaria en' +
        ' verde: cambio su forma y hay que ensenarsela.',
    );
    return 2;
  }
  for (const imagen of deLaMatriz) {
    if (!delComprobador.includes(imagen)) {
      hallazgos.push(
        `«${imagen}» se publica y el trabajo \`comprobar\` no le pregunta al registro si su` +
          ' etiqueta se puede pedir. Un `build-push-action` en verde solo dice que el `push` no' +
          ' devolvio error.',
      );
    }
  }
  for (const imagen of delComprobador) {
    if (!deLaMatriz.includes(imagen)) {
      hallazgos.push(
        `El trabajo \`comprobar\` pregunta por «${imagen}» y la matriz no la publica: preguntara` +
          ' por una etiqueta que nadie sube, y fallara siempre con 404.',
      );
    }
  }

  if (hallazgos.length > 0) {
    for (const hallazgo of hallazgos) console.error(`- ${hallazgo}`);
    return 1;
  }

  console.log(
    `${dockerfiles.length} Dockerfile(s) y ${entradas.length} entrada(s) de matriz: ` +
      `cada imagen tiene quien la publique y quien la compruebe (${deLaMatriz.join(', ')})`,
  );
  return 0;
}

const esteArchivo = process.argv[1] ?? '';
if (basename(esteArchivo) === 'verificar-quien-publica-cada-imagen.mjs') {
  process.exit(principal(process.argv.slice(2)));
}

export { principal, entradasDeLaMatriz, etapasDe, dockerfilesDelArbol, imagenesQueSeComprueban };
