-- ============================================================================
--  V4 — `nombre_normalizado` deja de depender del `search_path` de la sesion (C-4)
--
--  EL DEFECTO, MEDIDO Y NO SUPUESTO
--  --------------------------------
--  `V1` declara la funcion asi —heredada tal cual del `V11` del monolito—:
--
--      SELECT regexp_replace(
--                 lower(unaccent('unaccent'::regdictionary, coalesce(texto, ''))),
--                 '\s+', ' ', 'g');
--
--  Los DOS nombres que ahi aparecen se resuelven por `search_path`: la funcion
--  `unaccent(regdictionary, text)`, que vive en `public` porque ahi la instala la
--  extension, y el literal `'unaccent'::regdictionary`, cuya conversion de entrada
--  busca el diccionario por `search_path` igual que un nombre de tabla.
--
--  Aqui esa funcion no alimenta ningun indice —el trigrama es del padron, y el
--  padron es de `rentas`—, sino la COLUMNA GENERADA `via.nombre_busqueda`. Y eso,
--  lejos de librar a `catastro`, lo deja PEOR: la expresion de una columna generada
--  tambien se inserta en linea, y se inserta al **crear la tabla**.
--
--  MEDIDO CONTRA PostgreSQL 16.15 —LA VERSION QUE ESTE PRODUCTO DESPLIEGA—. Todo
--  volcado de `pg_dump` empieza vaciando el `search_path`:
--
--      SELECT pg_catalog.set_config('search_path', '', false);
--
--  y al restaurarlo:
--
--      pg_restore: error: could not execute query:
--          ERROR:  text search dictionary "unaccent" does not exist
--        CONTEXT:  SQL function "nombre_normalizado" during inlining
--        Command was: CREATE TABLE public.via ( ... nombre_busqueda text
--                     GENERATED ALWAYS AS (public.nombre_normalizado((nombre)::text)) STORED );
--      ...
--      pg_restore: warning: errors ignored on restore: 85
--
--  **`via` no se crea**, y con ella se cae todo lo que la referencia: `predio`,
--  `arancel`, sus claves foraneas, sus comentarios y sus indices. **85 errores**, y
--  `pg_restore` termina con codigo de salida 0. La base restaurada tiene 82 indices
--  donde el original tiene 86, y no hay ningun rojo que lo diga.
--
--  Con esta migracion aplicada, la misma ida y vuelta da **0 errores y 86 -> 86
--  indices**.
--
--  Esto NO es un defecto de PostgreSQL 18. Se descubrio buscando por que el esquema
--  del monolito no aplica en 18, pero `catastro` **si aplica** en 18 —su uso es una
--  columna generada, no un indice— y aun asi su restauracion logica estaba rota en
--  16, que es donde corre.
--
--  POR QUE UNA MIGRACION NUEVA Y NO EDITAR `V1`
--  --------------------------------------------
--  Porque `V1` ya corrio. Editarla cambia su suma de Flyway y deja «la base de al
--  lado distinta sin que nada se ponga rojo», que es el modo de fallo que la propia
--  cabecera de `V1` describe. `CREATE OR REPLACE` sirve igual: la restauracion
--  reproduce el esquema FINAL, asi que lo que se vuelca es este cuerpo.
--
--  LO QUE ESTE REEMPLAZO **NO** CAMBIA, Y SE MIDIO
--  -----------------------------------------------
--  1. **El valor.** `nombre_normalizado('PEÑA  GARCÍA')` da `pena garcia` antes y
--     despues, asi que las filas ya almacenadas en `via.nombre_busqueda` siguen
--     siendo las correctas y la columna no se reescribe.
--  2. **El indice.** `via_nombre_busqueda_ix` es un btree sobre la COLUMNA, no sobre
--     la funcion (V66, #565: bajo RLS una funcion no leakproof envolviendo la columna
--     no llega al indice), asi que ni se toca.
--  3. **El plan.** Sin cambio: aqui la funcion no aparece en ninguna condicion.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.nombre_normalizado(texto text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE PARALLEL SAFE STRICT
AS $function$
    SELECT regexp_replace(
               lower(public.unaccent('public.unaccent'::regdictionary, coalesce(texto, ''))),
               '\s+', ' ', 'g');
$function$
;

COMMENT ON FUNCTION public.nombre_normalizado(text) IS
    'Minusculas, sin tildes y sin espacios repetidos. IMMUTABLE para poder indexarla. '
    'La funcion y el diccionario van CUALIFICADOS con su esquema desde C-4: los dos se '
    'resuelven por search_path, y pg_dump lo vacia, de modo que sin cualificar la '
    'restauracion logica no crea ni la tabla via';
