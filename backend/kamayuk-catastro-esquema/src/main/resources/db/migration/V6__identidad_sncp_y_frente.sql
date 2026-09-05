-- ============================================================================
--  V6 — LA IDENTIDAD DEL SNCP Y EL FRENTE DE PREDIO (ADR-0036, ADR-0034)
--
--  DOS CODIGOS, Y POR ESO D-10 NUNCA CUADRO
--  ----------------------------------------
--  D-10 lleva abierta porque los largos no cuadran: la plantilla del manual da 23
--  posiciones y los ejemplos del prototipo dan otra cosa, asi que `V1` declaro
--  `cod_catastral varchar(25)` para que entren las dos. No cuadran porque se estan
--  comparando DOS IDENTIFICADORES DISTINTOS (ADR-0036):
--
--    codigo_ref_catastral  es MUNICIPAL. Lo compone la municipalidad con su sector,
--                          manzana, lote y unidad, y su largo es una decision suya.
--                          Por eso el manual y el prototipo discrepan: describen dos
--                          municipalidades.
--
--    cuc                   lo asigna el Sistema Nacional Integrado de Catastro
--                          (Ley 28294). 12 posiciones: 8 de rango por distrito mas 4
--                          correlativos de unidad catastral (Directiva
--                          01-2023-SNCP-CNC). Hasta aqui no existia en el modelo.
--
--  El «codigo predial de rentas» NO es un tercero: ADR-0015 ya lo resolvio —es
--  sinonimo del de referencia catastral, y hay UN SOLO PADRON—.
--
--  POR QUE EL UNICO ES PARCIAL, Y NO SOLO POR COMODIDAD
--  ---------------------------------------------------
--  La inmensa mayoria de predios de una municipalidad no tiene CUC, y exigirlo
--  convertiria la inscripcion de una ficha en un tramite ante el SNCP. Ademas,
--  construir un indice unico SI funciona sin contexto de tenant —lee el monton y no
--  pasa por la politica, medido en #588, a diferencia de VALIDAR una foranea, que es
--  el cuarto hallazgo de RLS—, pero su fallo NO DICE cuales son los duplicados. Con
--  `WHERE cuc IS NOT NULL` toda fila anterior queda excluida POR CONSTRUCCION y esta
--  migracion no se puede parar sobre datos que ya existen.
--
--  EL FRENTE DE PREDIO, Y POR QUE ES UNA TABLA Y NO UNA COLUMNA
--  -----------------------------------------------------------
--  Un predio tiene tantos frentes como vias lo bordean —una esquina tiene dos—, cada
--  uno con su via, su longitud y su numeracion. Es la entidad que despues consumen
--  los arbitrios (metros lineales de limpieza publica, que los cobra `rentas` porque
--  son un tributo: ADR-0024), las obras publicas y `seguridad` por su buzon.
--
--  La `longitud_m` la mide el tecnico y NO SE DERIVA de la geometria, por lo mismo
--  que el area del terreno no se deriva del poligono (ADR-0021): derivarla cambiaria
--  una cifra de la que cuelga un cobro sin que nadie lo decidiera, y un metro es
--  indistinguible de otro al leerlo. La geometria esta para dibujar y para cruzar.
--
--  LAS CUATRO COLUMNAS DE MARCO NO SON OPCIONALES (ADR-0034)
--  --------------------------------------------------------
--  Bajo RLS `geography_overlaps` no es *leakproof*, no se promueve por encima de la
--  politica y el indice GiST no sirve al rol de la aplicacion: la consulta da el
--  resultado CORRECTO, el plan sigue diciendo «Index», y se lee el padron entero del
--  inquilino. Es el quinto hallazgo de RLS, y `V65` lo mitigo para `predio` con las
--  cuatro columnas generadas del marco. ADR-0034 lo convierte en regla porque el
--  defecto se repite EN SILENCIO en cada tabla nueva con geometria.
--
--  `double precision` y no `numeric`: `numeric_le` tampoco es *leakproof*, y las
--  cuatro columnas dejarian de servir sin que nada se ponga rojo.
--
--  El GiST espacial se conserva ADEMAS del marco, para el trabajo que corre FUERA de
--  RLS —el generador de teselas del carril de referencia de ADR-0037, que fija su
--  `app.municipalidad_id` una vez y barre—.
-- ============================================================================

-- ── El CUC del SNCP: identidad distinta, no un alias del codigo municipal ────

CREATE DOMAIN cuc_sncp AS character(12)
    CONSTRAINT cuc_sncp_check CHECK ((VALUE ~ '^[0-9A-Z]{12}$'::text));

COMMENT ON DOMAIN cuc_sncp IS
    'Codigo Unico Catastral del SNCP (Ley 28294): 8 posiciones de rango asignado por '
    'distrito mas 4 correlativos de unidad catastral, Directiva 01-2023-SNCP-CNC. NO es '
    'el codigo de referencia catastral, que es municipal y de largo decidido por la '
    'municipalidad (ADR-0036)';

ALTER TABLE predio ADD COLUMN cuc cuc_sncp;
ALTER TABLE predio ADD COLUMN nivel_sncp character varying(12);

ALTER TABLE predio ADD CONSTRAINT predio_nivel_sncp_check
    CHECK (((nivel_sncp)::text = ANY ((ARRAY['LOTE'::character varying,
                                             'SECCION'::character varying,
                                             'AIRE'::character varying,
                                             'SUBSUELO'::character varying,
                                             'BIEN_COMUN'::character varying])::text[])));

COMMENT ON COLUMN predio.cuc IS
    'Nulo mientras el SNCP no lo asigne, que es el estado de la inmensa mayoria de '
    'predios de una municipalidad. No se inventa: sin rango asignado no hay CUC';
COMMENT ON COLUMN predio.nivel_sncp IS
    'La clase de unidad catastral del SNCP. Acompana al CUC y es nula con el';

CREATE UNIQUE INDEX predio_cuc_uq ON predio USING btree (municipalidad_id, cuc)
    WHERE (cuc IS NOT NULL);

-- La busqueda por prefijo del CUC se escribe como RANGO, igual que la del codigo de
-- referencia (`predio_codigo_prefijo_ix`, V1): es el tercer hallazgo de RLS —bajo la
-- politica, `LIKE 'x%'` no llega al indice porque `text ~~` no es *leakproof*—, y que
-- sean dos codigos no lo cambia. Un prefijo del CUC no es un capricho: las ocho
-- primeras posiciones son el rango asignado al distrito, asi que «las unidades
-- catastrales de este rango» es una consulta por prefijo y no otra cosa.
--
-- Va sobre la EXPRESION `(cuc)::text` y no sobre la columna, y eso lo decidio el
-- motor y no una preferencia: `cuc_sncp` es un dominio sobre `character(12)`, y
-- `CREATE INDEX ... (cuc text_pattern_ops)` falla con «operator class
-- "text_pattern_ops" does not accept data type cuc_sncp». `bpchar_pattern_ops`
-- tambien sirve y NO se toma: dejaria DOS convenciones de busqueda por prefijo en la
-- misma tabla —una con relleno de blancos y otra sin el— y la consulta del CUC no se
-- podria escribir igual que la del codigo de referencia, que es lo unico que impide
-- que una de las dos se escriba con `LIKE` el dia que nadie mire.
CREATE INDEX predio_cuc_prefijo_ix ON predio
    USING btree (municipalidad_id, ((cuc)::text) text_pattern_ops)
    WHERE (cuc IS NOT NULL);

-- ── El frente de predio ─────────────────────────────────────────────────────

CREATE TABLE frente_predio (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    predio_id bigint NOT NULL,
    via_id bigint NOT NULL,
    geometria geography(LineString,4326) NOT NULL,
    longitud_m numeric(12,2) NOT NULL,
    es_principal boolean DEFAULT false NOT NULL,
    numeracion character varying(20),
    retiro_m numeric(6,2),
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    marco_oeste double precision GENERATED ALWAYS AS (st_xmin(((geometria)::geometry)::box3d)) STORED,
    marco_sur double precision GENERATED ALWAYS AS (st_ymin(((geometria)::geometry)::box3d)) STORED,
    marco_este double precision GENERATED ALWAYS AS (st_xmax(((geometria)::geometry)::box3d)) STORED,
    marco_norte double precision GENERATED ALWAYS AS (st_ymax(((geometria)::geometry)::box3d)) STORED
);

COMMENT ON TABLE frente_predio IS
    'El tramo de un predio que da a una via. Una esquina tiene dos. Es el insumo de '
    'los arbitrios (el importe lo pone `rentas`: ADR-0024), de las obras publicas y '
    'de `seguridad` por el buzon (ADR-0033)';
COMMENT ON COLUMN frente_predio.longitud_m IS
    'La que midio el tecnico. NO se deriva de la geometria, por lo mismo que el area '
    'del terreno tampoco (ADR-0021): de ella cuelga un cobro';

ALTER TABLE frente_predio ADD CONSTRAINT frente_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE frente_predio ADD CONSTRAINT frente_longitud_check CHECK ((longitud_m > (0)::numeric));
ALTER TABLE frente_predio ADD CONSTRAINT frente_retiro_check CHECK ((retiro_m IS NULL OR retiro_m >= (0)::numeric));
ALTER TABLE frente_predio ADD CONSTRAINT frente_municipalidad_id_fkey FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

-- NOT VALID, como el resto del esquema: validar una foranea es una consulta, y el
-- migrador corre SIN contexto de tenant (cuarto hallazgo de RLS). Con la politica
-- puesta y sin `app.municipalidad_id`, la validacion no ve NINGUNA fila y la
-- migracion muere.
ALTER TABLE frente_predio ADD CONSTRAINT frente_predio_fk
    FOREIGN KEY (municipalidad_id, predio_id) REFERENCES predio(municipalidad_id, id) NOT VALID;
ALTER TABLE frente_predio ADD CONSTRAINT frente_via_fk
    FOREIGN KEY (municipalidad_id, via_id) REFERENCES via(municipalidad_id, id) NOT VALID;

-- Un predio tiene como mucho UN frente principal: es el que da su direccion y su
-- numeracion municipal. Parcial, porque los demas no compiten entre si.
CREATE UNIQUE INDEX frente_principal_uq ON frente_predio USING btree (municipalidad_id, predio_id)
    WHERE es_principal;

CREATE INDEX frente_predio_ix ON frente_predio USING btree (municipalidad_id, predio_id);
CREATE INDEX frente_via_ix ON frente_predio USING btree (municipalidad_id, via_id);

-- Las dos de ADR-0034: el marco para el SQL de aplicacion bajo RLS, y el GiST para
-- el trabajo que corre fuera de ella.
CREATE INDEX frente_marco_ix ON frente_predio
    USING btree (municipalidad_id, marco_oeste, marco_sur, marco_este, marco_norte);
CREATE INDEX frente_geometria_gix ON frente_predio USING gist (geometria);

ALTER TABLE frente_predio ENABLE ROW LEVEL SECURITY;
ALTER TABLE frente_predio FORCE ROW LEVEL SECURITY;
CREATE POLICY frente_tenant ON frente_predio FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

GRANT INSERT, SELECT, UPDATE ON frente_predio TO kamayuk_app;
GRANT SELECT ON frente_predio TO kamayuk_readonly;
