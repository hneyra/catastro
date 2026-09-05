-- ============================================================================
--  SGTM — Roles de base de datos (ARQ-03 §4)
--
--  NO es una migracion de Flyway. Se ejecuta ANTES de la primera migracion, con
--  una conexion de superusuario, porque:
--    - las politicas RLS de V6 nombran roles y estos deben existir;
--    - kamayuk_owner necesita CREATE sobre el esquema para poder migrar;
--    - un rol no puede crearse a si mismo.
--
--  Idempotente: se puede volver a ejecutar sobre una base ya provisionada.
--
--  Las CLAVES NO ESTAN AQUI. Los roles se crean sin LOGIN; quien provisiona el
--  ambiente asigna la clave con `ALTER ROLE ... LOGIN PASSWORD ...` desde su
--  gestor de secretos. La prueba de aislamiento hace lo mismo con claves
--  generadas al vuelo.
--
--  NOSUPERUSER y NOBYPASSRLS son explicitos y no decorativos: un superusuario
--  omite RLS incluso con FORCE ROW LEVEL SECURITY (DAT-01 §0, hallazgo 1).
-- ============================================================================

DO $roles$
DECLARE
    r text;
BEGIN
    FOREACH r IN ARRAY ARRAY['kamayuk_owner', 'kamayuk_app', 'kamayuk_readonly', 'rol_carga_parametros']
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('CREATE ROLE %I NOLOGIN', r);
        END IF;
        EXECUTE format(
            'ALTER ROLE %I NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION', r);
    END LOOP;
END
$roles$;

-- Solo kamayuk_owner hace DDL. La aplicacion nunca.
GRANT USAGE, CREATE ON SCHEMA public TO kamayuk_owner;
GRANT USAGE           ON SCHEMA public TO kamayuk_app, kamayuk_readonly, rol_carga_parametros;

-- Sin GRANT de pertenencia entre roles: kamayuk_owner concede privilegios sobre sus
-- propias tablas sin necesitarla, y ser miembro de kamayuk_app le permitiria un
-- SET ROLE que borra la separacion.

-- ---------- Extensiones ----------
-- Van aqui por el mismo motivo que los roles: kamayuk_owner no puede instalarlas
-- —no tiene CREATE sobre la base y no queremos darselo—, y la migracion que las
-- usa necesita que ya existan. Instalar una extension es provisionar el ambiente,
-- no versionar el esquema.
--
--   unaccent  para que «PEÑA» y «PENA» sean el mismo nombre. La usa la funcion
--             `nombre_normalizado(text)` del catalogo vial (V1, V4).
--
-- Es trusted desde PostgreSQL 13, asi que en un ambiente donde kamayuk_owner sea
-- dueño de la base tampoco harian falta privilegios especiales.
--
-- **pg_trgm SALIO de aqui en C-13**, y conviene decir por que en vez de dejar la
-- linea: la busqueda por aproximacion de nombre es del PADRON de contribuyentes
-- (RF-014), que es de `rentas`. Ninguna migracion de este esquema llama a
-- `similarity()`, `word_similarity()` ni `show_trgm()`, ni indexa con
-- `gin_trgm_ops` o `gist_trgm_ops` — medido, y vigilado en las dos direcciones
-- por `extensiones-de-las-migraciones.ts` de `infrastructure`: el dia que una
-- migracion de aqui la use, esa guarda se pone roja nombrandola.
--
-- Venia del archivo que P3 copio del monolito, y que P5D si podo en `caja` y P5E
-- en `rentas`. Retirarla no toca ningun ambiente ya provisionado —aqui no hay
-- ningun DROP EXTENSION—: lo que cambia es que una base NUEVA no la recibe.
--   postgis   la geometria del predio (ADR-0021, V61). A diferencia de las dos
--             anteriores NO es trusted, asi que hace falta un superusuario: no
--             hay forma de que la instale la migracion, que corre como
--             kamayuk_owner. Trae consigo la tabla `spatial_ref_sys`, un catalogo
--             de sistemas de coordenadas sin dato municipal, que por eso figura
--             entre las TABLAS_EXENTAS de la prueba de aislamiento.
--   btree_gist  compara bigint y varchar con `=` DENTRO de un indice GiST, que es
--             lo que `EXCLUDE USING gist` necesita para decir «dos vigencias del
--             mismo predio no se pisan» (#669, V72). Es *trusted* —medido:
--             `SELECT trusted FROM pg_available_extension_versions WHERE
--             name='btree_gist'` da `t`— y aun asi va AQUI y no en la migracion,
--             porque una extension trusted la crea quien tiene CREATE sobre la
--             BASE, y `kamayuk_owner` no es su dueño: intentarlo desde la migracion
--             da «permission denied to create extension "btree_gist"». Medido
--             ejecutando, no supuesto.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------- CONNECT sobre esta base ----------
--  PostgreSQL concede `CONNECT` a PUBLIC al crear una base, asi que TODO rol del cluster puede
--  conectarse a la de cualquier sistema sin que nadie se lo haya dado. Se midio (C-7 §6): sobre
--  una base recien creada, `has_database_privilege('<un rol cualquiera>', '<esa base>', 'CONNECT')`
--  devuelve `true`; tras el `REVOKE ... FROM PUBLIC`, `false`.
--
--  Los roles son del CLUSTER y los cuatro sistemas lo comparten, de modo que sin esto la
--  credencial de carga de valores normativos —y la de la aplicacion de cualquier otro sistema—
--  puede abrir una sesion contra esta base. No veria filas —RLS esta forzada— pero seria una
--  credencial de mas apuntando a un padron, que es exactamente lo que #155 midio con el rol del
--  respaldo y lo que `30-base-de-keycloak.sh` ya hace con la base del monolito.
--
--  `rol_carga_parametros` NO esta: en este esquema aparece una sola vez, en un
--  comentario de `V3`, y no tiene ni una politica ni un `GRANT` sobre ninguna tabla. La normativa
--  que `catastro` usa es su copia local sellada, y la escribe `kamayuk_app` (ADR-0025 §1).
--
--  Va aqui y no en una migracion porque `REVOKE ... ON DATABASE` solo lo puede hacer quien la
--  posee, y `kamayuk_owner` —que es quien migra— a proposito NO es dueno de la base (#722 lo midio:
--  «permission denied for database»). Este guion corre como superusuario.
DO $connect$
DECLARE
    base text := current_database();
BEGIN
    EXECUTE format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', base);
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO kamayuk_owner, kamayuk_app, kamayuk_readonly', base);
END
$connect$;
