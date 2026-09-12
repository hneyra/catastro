/**
 * Donde vive la aplicacion dentro del sitio, **leido de `vite.config.ts` y no escrito aqui**.
 *
 * <h2>El defecto que esto existe para impedir, y ya ocurrio en un repositorio hermano</h2>
 *
 * Esta interfaz no se sirve en la raiz del sitio: ADR-0030 §2 pone el sistema delante de la ruta
 * —«/catastro/»— porque el mismo Traefik sirve las cinco. Eso aparece en dos sitios: la `base` de
 * Vite, que decide de donde pide el paquete sus activos, y `import.meta.env.BASE_URL`, que es el
 * `redirect_uri` con el que la puerta de identidad vuelve del emisor.
 *
 * En `rentas` esos dos sitios se separaron y **el defecto llego a produccion con su prueba en
 * verde** ([`rentas`#71](https://github.com/hneyra/rentas/issues/71)): el entorno de pruebas no
 * declaraba la misma `base` que el de construccion, asi que alli `BASE_URL` valia la raiz del
 * sitio, la prueba afirmaba ese valor, y las dos cosas eran ciertas a la vez. Quien se autenticaba
 * en produccion volvia a la raiz y recibia un 404.
 *
 * Aqui no hay un segundo archivo de configuracion que pueda separarse —no hay `vitest.config.ts`:
 * los arneses son guiones de Node contra un navegador de verdad— pero SI habia ocho literales
 * `http://localhost:5190` repartidos por `verificaciones/`, que es la misma trampa con otra forma.
 * Asi que la base sale de un solo sitio, y de **el que de verdad construye el paquete**.
 *
 * <h2>Por que se lee con una expresion regular y no importando el modulo</h2>
 *
 * `vite.config.ts` es TypeScript y exporta el resultado de `defineConfig`, asi que importarlo
 * desde Node exige compilarlo primero. Se lee como texto, y **se falla si no se encuentra**: un
 * valor por omision aqui seria exactamente el defecto de arriba —la base desde la que se mide
 * dejaria de ser la base con la que se construye, en silencio—.
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..');

/**
 * La `base` que declara `vite.config.ts`, sin la barra final.
 *
 * Sin barra porque todos los arneses componen `${BASE}/${hash}`: con ella saldria una doble.
 */
export function baseDeVite() {
  const texto = readFileSync(join(RAIZ, 'vite.config.ts'), 'utf8');
  /* Sin comentarios: el docblock de ese archivo NOMBRA la base varias veces para explicarla, y
     leer la primera aparicion del texto crudo tomaria una de esas. Es la misma leccion que el
     escaner de `imagen.mjs` y la de `upstream-de-la-interfaz.ts` de `infrastructure`. */
  const sinComentarios = texto.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
  const casa = /(?:^|[\s{,])base:\s*'([^']*)'/m.exec(sinComentarios);
  if (casa === null) {
    console.error(
      'No se pudo leer la «base» de «vite.config.ts», asi que ningun arnes sabe donde vive la\n' +
        'aplicacion. NO se supone la raiz del sitio: eso es justo el defecto de `rentas`#71 —una\n' +
        'medicion coherente consigo misma sobre la ruta equivocada—. O la base se quito a proposito\n' +
        '—y entonces hay que rehacer este archivo y el `redirect_uri` de `src/api/identidad.ts`— o\n' +
        'se escribio de una forma que esto no sabe leer.',
    );
    process.exit(2);
  }
  return casa[1].replace(/\/+$/, '');
}

/**
 * La direccion de la aplicacion dentro de un origen.
 *
 * `CATASTRO_BASE` sigue siendo el ORIGEN —`http://localhost:5210`—, que es lo que el flujo de CI
 * ya le pasa: lo que se anade es la base, que este archivo sabe y el flujo no tiene por que.
 */
export function baseDeLaApp(origen) {
  return origen.replace(/\/+$/, '') + baseDeVite();
}
