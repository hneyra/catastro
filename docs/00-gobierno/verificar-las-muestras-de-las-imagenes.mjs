#!/usr/bin/env node
/*
   La autoprueba de `verificar-quien-publica-cada-imagen.mjs`.

   Una guarda que nunca se ha visto fallar no protege nada, y una que se prueba contra el
   repositorio de verdad solo se puede probar rompiendolo. Asi que aqui se fabrican arboles
   completos en un directorio temporal —un `.git`, un workflow y sus `Dockerfile`— y se
   ejecuta la guarda contra ellos.

   Es el mismo reparto que `verificar-las-muestras-del-registro.mjs`: casos ROJOS, casos que
   NO SE PUEDEN MEDIR, y casos VERDES. Y los rojos no basta con que salgan rojos: tienen que
   **nombrar lo que falla**, porque un rojo que no dice cual de las cuatro cosas se rompio
   manda a mirar el sitio equivocado.

     node docs/00-gobierno/verificar-las-muestras-de-las-imagenes.mjs
*/

import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { principal } from './verificar-quien-publica-cada-imagen.mjs';

const DOCKERFILE_BACKEND = [
  'FROM eclipse-temurin:25-jdk AS construccion',
  'FROM eclipse-temurin:25-jre AS aplicacion',
  'FROM eclipse-temurin:25-jre AS migrador',
].join('\n');

const DOCKERFILE_FRONTEND = ['FROM node:22-alpine AS construccion', 'FROM nginx:1.31.5-alpine AS interfaz'].join('\n');

/** Un workflow con la forma real: la matriz y el bucle del comprobador. */
function workflow(entradas, imagenesComprobadas) {
  const filas = entradas
    .map((e) => `          - contexto: ${e.contexto}\n            archivo: ${e.archivo}\n            destino: ${e.destino}\n            imagen: ${e.imagen}`)
    .join('\n');
  return [
    'name: Publicar imágenes',
    'jobs:',
    '  publicar:',
    '    strategy:',
    '      matrix:',
    '        include:',
    filas,
    '    steps:',
    '      - uses: docker/build-push-action@v6',
    '  comprobar:',
    '    steps:',
    '      - run: |',
    `          for imagen in ${imagenesComprobadas.join(' ')}; do`,
    '            echo "$imagen"',
    '          done',
    '',
  ].join('\n');
}

/** Fabrica un arbol y devuelve su raiz. `archivos` es {ruta relativa: contenido}. */
function arbol(archivos) {
  const raiz = mkdtempSync(join(tmpdir(), 'muestras-imagenes-'));
  writeFileSync(join(raiz, '.git'), 'gitdir: ninguno\n');
  for (const [ruta, contenido] of Object.entries(archivos)) {
    const destino = join(raiz, ruta);
    mkdirSync(dirname(destino), { recursive: true });
    writeFileSync(destino, contenido);
  }
  return raiz;
}

const LAS_TRES = [
  { contexto: '.', archivo: 'backend/Dockerfile', destino: 'aplicacion', imagen: 'kamayuk-catastro' },
  { contexto: '.', archivo: 'backend/Dockerfile', destino: 'migrador', imagen: 'kamayuk-catastro-migrador' },
  { contexto: 'frontend', archivo: 'frontend/Dockerfile', destino: 'interfaz', imagen: 'kamayuk-catastro-web' },
];
const NOMBRES = LAS_TRES.map((e) => e.imagen);
const RUTA_DEL_WORKFLOW = '.github/workflows/publicar-imagenes.yml';

const CASOS = [
  // ── ROJOS ────────────────────────────────────────────────────────────────────────────
  {
    nombre: 'un Dockerfile que nadie publica — el defecto de #40',
    esperado: 1,
    nombra: /frontend\/Dockerfile.*ninguna entrada de la matriz lo publica/s,
    archivos: {
      'backend/Dockerfile': DOCKERFILE_BACKEND,
      'frontend/Dockerfile': DOCKERFILE_FRONTEND,
      [RUTA_DEL_WORKFLOW]: workflow(LAS_TRES.slice(0, 2), NOMBRES.slice(0, 2)),
    },
  },
  {
    nombre: 'la matriz nombra un archivo que no existe',
    esperado: 1,
    nombra: /desde «frontend\/Dockerfile», que no existe/,
    archivos: {
      'backend/Dockerfile': DOCKERFILE_BACKEND,
      [RUTA_DEL_WORKFLOW]: workflow(LAS_TRES, NOMBRES),
    },
  },
  {
    nombre: 'la matriz nombra una etapa que el Dockerfile no declara',
    esperado: 1,
    nombra: /etapa «interfaz».*declara «construccion», «aplicacion», «migrador»/s,
    archivos: {
      'backend/Dockerfile': DOCKERFILE_BACKEND,
      'frontend/Dockerfile': DOCKERFILE_FRONTEND,
      [RUTA_DEL_WORKFLOW]: workflow(
        [...LAS_TRES.slice(0, 2), { ...LAS_TRES[2], archivo: 'backend/Dockerfile' }],
        NOMBRES,
      ),
    },
  },
  {
    nombre: 'se publica una imagen que el comprobador no comprueba',
    esperado: 1,
    nombra: /«kamayuk-catastro-web» se publica y el trabajo `comprobar` no le pregunta al registro/,
    archivos: {
      'backend/Dockerfile': DOCKERFILE_BACKEND,
      'frontend/Dockerfile': DOCKERFILE_FRONTEND,
      [RUTA_DEL_WORKFLOW]: workflow(LAS_TRES, NOMBRES.slice(0, 2)),
    },
  },
  {
    nombre: 'el comprobador pregunta por una imagen que nadie publica',
    esperado: 1,
    nombra: /pregunta por «kamayuk-catastro-fantasma» y la matriz no la publica/,
    archivos: {
      'backend/Dockerfile': DOCKERFILE_BACKEND,
      'frontend/Dockerfile': DOCKERFILE_FRONTEND,
      [RUTA_DEL_WORKFLOW]: workflow(LAS_TRES, [...NOMBRES, 'kamayuk-catastro-fantasma']),
    },
  },
  {
    nombre: 'una exencion de un Dockerfile que ya no existe',
    esperado: 1,
    nombra: /«ejemplos\/Dockerfile» esta declarado EXENTO y no existe/,
    exentos: { 'ejemplos/Dockerfile': 'un motivo cualquiera' },
    archivos: {
      'backend/Dockerfile': DOCKERFILE_BACKEND,
      'frontend/Dockerfile': DOCKERFILE_FRONTEND,
      [RUTA_DEL_WORKFLOW]: workflow(LAS_TRES, NOMBRES),
    },
  },

  // ── NO SE PUDO MEDIR ─────────────────────────────────────────────────────────────────
  {
    nombre: 'EL CONTRASTE: sin un solo Dockerfile no se da por buena, se dice que no midio',
    esperado: 2,
    nombra: /no midio nada/,
    archivos: { [RUTA_DEL_WORKFLOW]: workflow(LAS_TRES, NOMBRES) },
  },
  {
    nombre: 'EL CONTRASTE: si la matriz cambia de forma, tampoco',
    esperado: 2,
    nombra: /No se leyo ni una entrada de la matriz/,
    archivos: {
      'backend/Dockerfile': DOCKERFILE_BACKEND,
      [RUTA_DEL_WORKFLOW]: 'name: Publicar imágenes\njobs:\n  publicar:\n    steps: []\n',
    },
  },
  {
    nombre: 'EL CONTRASTE: si el bucle del comprobador cambia de forma, tampoco',
    esperado: 2,
    nombra: /No se leyo la lista del trabajo `comprobar`/,
    archivos: {
      'backend/Dockerfile': DOCKERFILE_BACKEND,
      'frontend/Dockerfile': DOCKERFILE_FRONTEND,
      [RUTA_DEL_WORKFLOW]: workflow(LAS_TRES, NOMBRES).replace(/for imagen in [^;]+; do/, 'for i in a b c; do'),
    },
  },

  // ── VERDES ───────────────────────────────────────────────────────────────────────────
  {
    nombre: 'las tres imagenes, publicadas y comprobadas',
    esperado: 0,
    archivos: {
      'backend/Dockerfile': DOCKERFILE_BACKEND,
      'frontend/Dockerfile': DOCKERFILE_FRONTEND,
      [RUTA_DEL_WORKFLOW]: workflow(LAS_TRES, NOMBRES),
    },
  },
  {
    nombre: 'un Dockerfile exento CON su motivo no es un hallazgo',
    esperado: 0,
    exentos: { 'ejemplos/Dockerfile': 'es un ejemplo del manual y no se despliega en ningun ambiente' },
    archivos: {
      'backend/Dockerfile': DOCKERFILE_BACKEND,
      'frontend/Dockerfile': DOCKERFILE_FRONTEND,
      'ejemplos/Dockerfile': 'FROM alpine AS demostracion',
      [RUTA_DEL_WORKFLOW]: workflow(LAS_TRES, NOMBRES),
    },
  },
  {
    nombre: 'lo que no se recorre no cuenta: un Dockerfile dentro de node_modules',
    esperado: 0,
    archivos: {
      'backend/Dockerfile': DOCKERFILE_BACKEND,
      'frontend/Dockerfile': DOCKERFILE_FRONTEND,
      'frontend/node_modules/alguna-dependencia/Dockerfile': 'FROM alpine AS lo-que-sea',
      [RUTA_DEL_WORKFLOW]: workflow(LAS_TRES, NOMBRES),
    },
  },
];

let fallos = 0;
for (const caso of CASOS) {
  const raiz = arbol(caso.archivos);
  const dicho = [];
  const logOriginal = console.log;
  const errOriginal = console.error;
  console.log = (...a) => dicho.push(a.join(' '));
  console.error = (...a) => dicho.push(a.join(' '));
  let codigo;
  try {
    codigo = principal(['--raiz', raiz], caso.exentos ?? {});
  } finally {
    console.log = logOriginal;
    console.error = errOriginal;
    rmSync(raiz, { recursive: true, force: true });
  }
  const salida = dicho.join('\n');

  if (codigo !== caso.esperado) {
    console.error(`ROJO  ${caso.nombre}\n      esperaba salir con ${caso.esperado} y salio con ${codigo}\n      dijo: ${salida}`);
    fallos += 1;
  } else if (caso.nombra && !caso.nombra.test(salida)) {
    console.error(`ROJO  ${caso.nombre}\n      salio con ${codigo}, que es lo esperado, pero NO nombra lo que falla\n      dijo: ${salida}`);
    fallos += 1;
  } else {
    console.log(`ok    ${caso.nombre}`);
  }
}

if (fallos > 0) {
  console.error(`\n${fallos} de ${CASOS.length} muestras no se comportan como dicen.`);
  process.exit(1);
}
console.log(`\n${CASOS.length} muestras: la guarda muerde donde debe, dice que no midio cuando no midio, y calla donde procede.`);
