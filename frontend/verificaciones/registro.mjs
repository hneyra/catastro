/**
 * El registro de modulos, compilado al vuelo desde `src/shell/modulos.ts`.
 *
 * Los arneses lo leen de ahi y **no repiten la lista**: una lista copiada se
 * queda vieja sin ruido, y entonces el recorrido informa en verde sin haber
 * mirado el destino que alguien acaba de anadir.
 *
 * `import.meta.env` se define como objeto vacio porque el grafo de `modulos.ts`
 * llega hasta `api/cliente.ts`, que lee la raiz de la API de ahi. En Node ese
 * objeto no existe y la importacion revienta; el valor no se usa para nada aqui,
 * asi que basta con que exista.
 */
import { rm } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';

export async function leerRegistro(nombre = '.registro') {
  const temporal = new URL(`./${nombre}.mjs`, import.meta.url);
  await build({
    entryPoints: ['src/shell/modulos.ts'],
    outfile: temporal.pathname,
    bundle: true,
    format: 'esm',
    platform: 'node',
    logLevel: 'silent',
    define: { 'import.meta.env': '{}' },
  });
  const modulo = await import(pathToFileURL(temporal.pathname).href);
  await rm(temporal, { force: true });
  return modulo;
}
