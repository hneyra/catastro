-- ============================================================================
--  V7 — LA ZONIFICACION URBANA (#4, ADR-0034, ADR-0024)
--
--  QUE FALTABA, MEDIDO Y NO SUPUESTO
--  ---------------------------------
--  `rentas` emite la licencia de funcionamiento y su tabla `ciiu` YA declara
--  `zonificacion_compatible` y `riesgo_itse`: el lado del GIRO esta modelado desde
--  el baseline. Lo que no existia en ningun sistema es el lado del TERRITORIO —a
--  que zona cae un predio—, asi que la pregunta «¿este giro es compatible con la
--  zona de este predio?» no la podia contestar nadie.
--
--  Aqui se publica LA ZONA. Quien es compatible con que sigue siendo dato de
--  `rentas` (`ciiu.zonificacion_compatible`) y esta migracion no lo toca: es la
--  frontera de ADR-0024, la misma que impide que `catastro` calcule un tributo.
--
--  LAS CUATRO TABLAS, Y POR QUE SON CUATRO
--  ---------------------------------------
--    zonificacion           el poligono de una zona, con su plan y su ordenanza.
--                           Es lo unico que el endpoint publica.
--    parametro_urbanistico  lo que la zona PERMITE: altura, area de lote, retiro,
--                           coeficiente, densidad, estacionamientos. Cuelga de la
--                           zona porque cambia con ella y con su ordenanza.
--    seccion_via            la seccion normativa de un tramo de via: ancho de via,
--                           calzada, vereda y retiro. Es lo que decide si un predio
--                           puede construir hasta el limite o tiene que retirarse.
--    habilitacion_urbana    el acto que convierte suelo rustico en urbano y produce
--                           lotes. Lleva su resolucion y su perimetro.
--
--  LO QUE ESTA MIGRACION NO CREA, Y ES LA MITAD DE LA DECISION
--  ----------------------------------------------------------
--  NO crea `licencia_edificacion` ni el FUE: las ocho tablas de edificacion
--  (`edificacion_terreno`, `edificacion_proyecto`, `edificacion_estructura`,
--  `edificacion_profesional`, `edificacion_requisito`, `edificacion_movimiento`,
--  `edificacion_vigencia`, `edificacion_correlativo`) viven en `rentas` desde su
--  baseline. Duplicarlas aqui seria un SEGUNDO PADRON de expedientes, y el dia que
--  las dos copias discreparan la que se leyera decidiria si una obra es legal.
--
--  LAS CUATRO COLUMNAS DE MARCO NO SON OPCIONALES (ADR-0034 regla 1)
--  ----------------------------------------------------------------
--  Bajo RLS `geography_overlaps` no es *leakproof*, no se promueve por encima de la
--  politica y el indice GiST no sirve al rol de la aplicacion: la consulta da el
--  resultado CORRECTO, el plan sigue diciendo «Index», y se lee el padron entero
--  del inquilino. Es el quinto hallazgo de RLS. `double precision` y no `numeric`:
--  `numeric_le` tampoco es *leakproof*, y las cuatro columnas dejarian de servir
--  sin que nada se ponga rojo.
--
--  Las llevan las dos tablas con geometria —`zonificacion` y `habilitacion_urbana`—.
--  `parametro_urbanistico` y `seccion_via` no tienen ninguna, y por eso no las
--  llevan: `RevisorDeEsquema` mira el TIPO de las columnas, no el nombre de la
--  tabla, asi que ponerselas seria carga muerta que nada exige.
--
--  POR QUE `plan WITH <>` EN LA EXCLUSION, Y NO LA FORMA INGENUA
--  ------------------------------------------------------------
--  Lo que hay que impedir es que DOS PLANES vigentes a la vez cubran el mismo
--  suelo: entonces «la zona de este predio» tiene dos respuestas y la licencia se
--  concede o se niega segun cual lea la consulta.
--
--  La forma ingenua —(municipalidad_id =, geometria &&, daterange &&)— NO sirve, y
--  no es una opinion: `&&` sobre `geography` compara CAJAS ENVOLVENTES, y dos zonas
--  ADYACENTES del mismo plan tienen cajas que se tocan. Medido contra PostgreSQL
--  16.13 con PostGIS 3.4.2 el 2026-09-05, insertando dos rectangulos que solo
--  comparten su arista:
--
--    ERROR:  conflicting key value violates exclusion constraint "sin_el_plan"
--
--  O sea que con la forma ingenua NO SE PUEDE CARGAR UN PLAN DE ZONIFICACION: la
--  segunda zona de la primera manzana ya se rechaza. Con `plan WITH <>` —que
--  `btree_gist` provee justamente para las restricciones de exclusion— la misma
--  pareja entra, y lo que se rechaza es el segundo plan que se pisa con el primero.
--  Las tres formas se midieron: dos zonas adyacentes del MISMO plan entran; un
--  segundo plan solapado en fecha y suelo se rechaza; y el plan que SUCEDE al
--  anterior —cerrado la vispera— entra.
--
--  DEFERRABLE INITIALLY DEFERRED por lo mismo que `ficha_vigencias_no_se_pisan`:
--  sustituir un plan por otro atraviesa un estado intermedio solapado —se abre el
--  nuevo y se cierra el viejo, en dos sentencias—, y sin el diferimiento la primera
--  de las dos fallaria y el relevo del plan seria imposible.
--
--  EL DATERANGE SE ESCRIBE COMO EN `V1`, Y NO ES COSMETICA
--  ------------------------------------------------------
--  `daterange(vigencia_desde, COALESCE(vigencia_hasta,'infinity'::date), '[]')` es
--  exactamente lo que `ficha_vigencias_no_se_pisan` y `titularidad_vigencias_no_se_pisan`
--  usan desde `V1`. `vigencia_hasta` es INCLUSIVA en todo este esquema —cerrar el
--  dia antes de abrir el siguiente es lo que hace `ActualizarFichaCatastral`—, y
--  escribir aqui el `[)` por omision dejaria dos convenciones de vigencia en la
--  misma base: la fecha de cierre significaria «cubierta» en una tabla y «no
--  cubierta» en otra, sin que nada lo dijera.
--
--  SIN UN SOLO `DELETE` (regla 4)
--  ------------------------------
--  Un plan no se borra: se cierra con su `vigencia_hasta` y el siguiente lo sucede.
--  La aplicacion recibe INSERT, SELECT y UPDATE, y nada mas.
-- ============================================================================

-- ── La zona ─────────────────────────────────────────────────────────────────

CREATE TABLE zonificacion (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    plan character varying(30) NOT NULL,
    ordenanza character varying(60) NOT NULL,
    codigo character varying(20) NOT NULL,
    nombre character varying(120) NOT NULL,
    geometria geography(MultiPolygon,4326) NOT NULL,
    vigencia_desde date NOT NULL,
    vigencia_hasta date,
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    marco_oeste double precision GENERATED ALWAYS AS (st_xmin(((geometria)::geometry)::box3d)) STORED,
    marco_sur double precision GENERATED ALWAYS AS (st_ymin(((geometria)::geometry)::box3d)) STORED,
    marco_este double precision GENERATED ALWAYS AS (st_xmax(((geometria)::geometry)::box3d)) STORED,
    marco_norte double precision GENERATED ALWAYS AS (st_ymax(((geometria)::geometry)::box3d)) STORED
);

COMMENT ON TABLE zonificacion IS
    'La zona a la que cae un suelo, segun el plan de desarrollo urbano vigente. Publica '
    'LA ZONA; quien es compatible con que es dato de `rentas` (ciiu.zonificacion_compatible), '
    'que es la frontera de ADR-0024';
COMMENT ON COLUMN zonificacion.plan IS
    'El plan de desarrollo urbano que la aprobo. Entra en la restriccion de exclusion con '
    '«<>»: lo que se impide es que DOS planes vigentes cubran el mismo suelo, no que dos '
    'zonas del mismo plan sean adyacentes —que es el caso corriente—';
COMMENT ON COLUMN zonificacion.ordenanza IS
    'La ordenanza municipal que aprobo el plan. No se deriva ni se inventa: sin ordenanza '
    'una zonificacion no rige, y negar una licencia por ella seria negarla sin norma';
COMMENT ON COLUMN zonificacion.vigencia_hasta IS
    'INCLUSIVA, como en toda vigencia de este esquema: el ultimo dia que la zona rige. '
    'Nula mientras el plan siga vigente';

ALTER TABLE zonificacion ADD CONSTRAINT zonificacion_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE zonificacion ADD CONSTRAINT zonificacion_vigencia_check
    CHECK ((vigencia_hasta IS NULL OR vigencia_hasta >= vigencia_desde));
ALTER TABLE zonificacion ADD CONSTRAINT zonificacion_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

-- El codigo de zona es unico DENTRO de su plan: «RDM» del PDU-2020 y «RDM» del
-- PDU-2026 son la misma etiqueta de dos planes distintos, y tienen que poder convivir
-- mientras uno sucede al otro.
CREATE UNIQUE INDEX zonificacion_codigo_uq ON zonificacion
    USING btree (municipalidad_id, plan, codigo, vigencia_desde);

-- Ver el encabezado: `plan WITH <>` es lo que separa «dos planes se pisan» —que es el
-- defecto— de «dos zonas del mismo plan son vecinas» —que es como se dibuja un plan—.
ALTER TABLE zonificacion ADD CONSTRAINT zonificacion_planes_no_se_pisan
    EXCLUDE USING gist (
        municipalidad_id WITH =,
        plan WITH <>,
        geometria WITH &&,
        daterange(vigencia_desde, COALESCE(vigencia_hasta, 'infinity'::date), '[]'::text) WITH &&
    ) DEFERRABLE INITIALLY DEFERRED;

-- Las dos de ADR-0034: el marco para el SQL de aplicacion bajo RLS, y el GiST para el
-- trabajo que corre fuera de ella.
CREATE INDEX zonificacion_marco_ix ON zonificacion
    USING btree (municipalidad_id, marco_oeste, marco_sur, marco_este, marco_norte);
CREATE INDEX zonificacion_geometria_gix ON zonificacion USING gist (geometria);

-- Y NO hay un indice por vigencia, que fue lo primero que se escribio. Se retiro al
-- medir: ninguna consulta de este sistema busca zonas por fecha sola —la de contencion
-- entra por el marco y la del cargador por (plan, codigo, vigencia_desde)—, y ademas
-- COMPETIA. Con las tablas pequenas el planificador lo preferia sobre
-- `zonificacion_marco_ix` y las cuatro comparaciones del marco volvian al `Join Filter`:
-- el quinto hallazgo de RLS reproducido por un indice de mas. Un indice que nadie
-- consulta se paga en cada escritura y, aqui, se pagaba dos veces.

ALTER TABLE zonificacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE zonificacion FORCE ROW LEVEL SECURITY;
CREATE POLICY zonificacion_tenant ON zonificacion FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

GRANT INSERT, SELECT, UPDATE ON zonificacion TO kamayuk_app;
GRANT SELECT ON zonificacion TO kamayuk_readonly;

-- ── Lo que la zona permite ──────────────────────────────────────────────────

CREATE TABLE parametro_urbanistico (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    zonificacion_id bigint NOT NULL,
    clave character varying(40) NOT NULL,
    valor character varying(120) NOT NULL,
    unidad character varying(20),
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL
);

COMMENT ON TABLE parametro_urbanistico IS
    'Lo que la zona permite: altura maxima, area de lote minima, coeficiente de '
    'edificacion, densidad, retiro y estacionamientos. Cuelga de la zona porque cambia '
    'con ella y con su ordenanza';
COMMENT ON COLUMN parametro_urbanistico.valor IS
    'TEXTO y no numero, a proposito: la mitad de los parametros urbanisticos de una '
    'ordenanza no son cifras («segun frente», «1/2 por vivienda», «3 pisos + azotea»), y '
    'convertirlos a numero seria inventar lo que la ordenanza no dice. Aqui no se calcula '
    'nada con ellos: se publican para que quien evalue el expediente los lea';

ALTER TABLE parametro_urbanistico ADD CONSTRAINT parametro_urbanistico_pk
    PRIMARY KEY (municipalidad_id, id);
ALTER TABLE parametro_urbanistico ADD CONSTRAINT parametro_urbanistico_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

-- NOT VALID, como el resto del esquema: validar una foranea es una consulta, y el
-- migrador corre SIN contexto de tenant (cuarto hallazgo de RLS). Con la politica puesta
-- y sin `app.municipalidad_id`, la validacion no ve NINGUNA fila y la migracion muere.
ALTER TABLE parametro_urbanistico ADD CONSTRAINT parametro_urbanistico_zonificacion_fk
    FOREIGN KEY (municipalidad_id, zonificacion_id)
    REFERENCES zonificacion(municipalidad_id, id) NOT VALID;

CREATE UNIQUE INDEX parametro_urbanistico_uq ON parametro_urbanistico
    USING btree (municipalidad_id, zonificacion_id, clave);

ALTER TABLE parametro_urbanistico ENABLE ROW LEVEL SECURITY;
ALTER TABLE parametro_urbanistico FORCE ROW LEVEL SECURITY;
CREATE POLICY parametro_urbanistico_tenant ON parametro_urbanistico FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

GRANT INSERT, SELECT, UPDATE ON parametro_urbanistico TO kamayuk_app;
GRANT SELECT ON parametro_urbanistico TO kamayuk_readonly;

-- ── La seccion normativa de un tramo de via ─────────────────────────────────

CREATE TABLE seccion_via (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    via_id bigint NOT NULL,
    tramo character varying(120) NOT NULL,
    plan character varying(30) NOT NULL,
    ordenanza character varying(60) NOT NULL,
    clasificacion character varying(20) NOT NULL,
    ancho_via_m numeric(6,2) NOT NULL,
    ancho_calzada_m numeric(6,2),
    ancho_vereda_m numeric(6,2),
    retiro_m numeric(6,2),
    vigencia_desde date NOT NULL,
    vigencia_hasta date,
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL
);

COMMENT ON TABLE seccion_via IS
    'La seccion normativa de un tramo de via: cuanto mide la via, su calzada, su vereda y '
    'el retiro que exige. Es lo que decide si un predio puede construir hasta el limite. '
    'Sin geometria propia: la via la identifica, y el tramo se nombra como lo nombra la '
    'ordenanza —«cuadra 3, entre Jr. Lima y Jr. Cusco»—, que es lo que una persona busca';
COMMENT ON COLUMN seccion_via.ancho_via_m IS
    'Metros de la seccion normativa, NO los que mide hoy la calle. Que las dos no coincidan '
    'es un hallazgo que se informa, no una correccion que se aplica (ADR-0021, ADR-0015)';

ALTER TABLE seccion_via ADD CONSTRAINT seccion_via_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE seccion_via ADD CONSTRAINT seccion_via_clasificacion_check
    CHECK (((clasificacion)::text = ANY ((ARRAY['EXPRESA'::character varying,
                                                'ARTERIAL'::character varying,
                                                'COLECTORA'::character varying,
                                                'LOCAL'::character varying,
                                                'PEATONAL'::character varying])::text[])));
ALTER TABLE seccion_via ADD CONSTRAINT seccion_via_ancho_check
    CHECK ((ancho_via_m > (0)::numeric));
ALTER TABLE seccion_via ADD CONSTRAINT seccion_via_partes_check
    CHECK ((ancho_calzada_m IS NULL OR ancho_calzada_m <= ancho_via_m));
ALTER TABLE seccion_via ADD CONSTRAINT seccion_via_retiro_check
    CHECK ((retiro_m IS NULL OR retiro_m >= (0)::numeric));
ALTER TABLE seccion_via ADD CONSTRAINT seccion_via_vigencia_check
    CHECK ((vigencia_hasta IS NULL OR vigencia_hasta >= vigencia_desde));
ALTER TABLE seccion_via ADD CONSTRAINT seccion_via_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);
ALTER TABLE seccion_via ADD CONSTRAINT seccion_via_via_fk
    FOREIGN KEY (municipalidad_id, via_id) REFERENCES via(municipalidad_id, id) NOT VALID;

-- El mismo tramo de la misma via no puede tener dos secciones que cubran la misma
-- fecha: la que se leyera decidiria el retiro exigible. `btree_gist` presta el `=`
-- sobre `bigint` y sobre `varchar`; el diferimiento, por lo mismo que en `zonificacion`.
ALTER TABLE seccion_via ADD CONSTRAINT seccion_via_vigencias_no_se_pisan
    EXCLUDE USING gist (
        municipalidad_id WITH =,
        via_id WITH =,
        tramo WITH =,
        daterange(vigencia_desde, COALESCE(vigencia_hasta, 'infinity'::date), '[]'::text) WITH &&
    ) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX seccion_via_via_ix ON seccion_via USING btree (municipalidad_id, via_id);

ALTER TABLE seccion_via ENABLE ROW LEVEL SECURITY;
ALTER TABLE seccion_via FORCE ROW LEVEL SECURITY;
CREATE POLICY seccion_via_tenant ON seccion_via FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

GRANT INSERT, SELECT, UPDATE ON seccion_via TO kamayuk_app;
GRANT SELECT ON seccion_via TO kamayuk_readonly;

-- ── La habilitacion urbana ──────────────────────────────────────────────────

CREATE TABLE habilitacion_urbana (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    codigo character varying(30) NOT NULL,
    denominacion character varying(160) NOT NULL,
    resolucion character varying(60) NOT NULL,
    fecha_resolucion date NOT NULL,
    estado character varying(20) NOT NULL,
    lotes_aprobados integer,
    area_bruta_m2 numeric(14,2),
    geometria geography(MultiPolygon,4326),
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    marco_oeste double precision GENERATED ALWAYS AS (st_xmin(((geometria)::geometry)::box3d)) STORED,
    marco_sur double precision GENERATED ALWAYS AS (st_ymin(((geometria)::geometry)::box3d)) STORED,
    marco_este double precision GENERATED ALWAYS AS (st_xmax(((geometria)::geometry)::box3d)) STORED,
    marco_norte double precision GENERATED ALWAYS AS (st_ymax(((geometria)::geometry)::box3d)) STORED
);

COMMENT ON TABLE habilitacion_urbana IS
    'El acto que convierte suelo rustico en urbano y produce lotes, con su resolucion. '
    'NO es el expediente de la licencia de edificacion: esas ocho tablas viven en `rentas` '
    'desde su baseline, y duplicarlas aqui seria un segundo padron';
COMMENT ON COLUMN habilitacion_urbana.geometria IS
    'NULA mientras no este digitalizado el plano, que es el estado de casi todas las '
    'habilitaciones antiguas: existen por su resolucion, no por su poligono. Exigirla '
    'dejaria fuera del padron precisamente a las que llevan cuarenta anos habitadas';
COMMENT ON COLUMN habilitacion_urbana.area_bruta_m2 IS
    'La que dice la resolucion. NO se deriva del poligono, por lo mismo que el area del '
    'terreno tampoco (ADR-0021): un area es indistinguible de otra al leerla';

ALTER TABLE habilitacion_urbana ADD CONSTRAINT habilitacion_urbana_pk
    PRIMARY KEY (municipalidad_id, id);
ALTER TABLE habilitacion_urbana ADD CONSTRAINT habilitacion_urbana_estado_check
    CHECK (((estado)::text = ANY ((ARRAY['APROBADA'::character varying,
                                         'EJECUTADA'::character varying,
                                         'RECEPCIONADA'::character varying,
                                         'CADUCA'::character varying])::text[])));
ALTER TABLE habilitacion_urbana ADD CONSTRAINT habilitacion_urbana_lotes_check
    CHECK ((lotes_aprobados IS NULL OR lotes_aprobados > 0));
ALTER TABLE habilitacion_urbana ADD CONSTRAINT habilitacion_urbana_area_check
    CHECK ((area_bruta_m2 IS NULL OR area_bruta_m2 > (0)::numeric));
ALTER TABLE habilitacion_urbana ADD CONSTRAINT habilitacion_urbana_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

CREATE UNIQUE INDEX habilitacion_urbana_codigo_uq ON habilitacion_urbana
    USING btree (municipalidad_id, codigo);

-- La busqueda por prefijo del codigo se escribe como RANGO y no con LIKE (tercer
-- hallazgo de RLS: `textlike` no es *leakproof* y no llega al indice bajo la politica),
-- asi que el indice se declara con la clase de operadores que el rango usa.
CREATE INDEX habilitacion_urbana_codigo_prefijo_ix ON habilitacion_urbana
    USING btree (municipalidad_id, codigo varchar_pattern_ops);

CREATE INDEX habilitacion_urbana_marco_ix ON habilitacion_urbana
    USING btree (municipalidad_id, marco_oeste, marco_sur, marco_este, marco_norte);
CREATE INDEX habilitacion_urbana_geometria_gix ON habilitacion_urbana USING gist (geometria);

ALTER TABLE habilitacion_urbana ENABLE ROW LEVEL SECURITY;
ALTER TABLE habilitacion_urbana FORCE ROW LEVEL SECURITY;
CREATE POLICY habilitacion_urbana_tenant ON habilitacion_urbana FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

GRANT INSERT, SELECT, UPDATE ON habilitacion_urbana TO kamayuk_app;
GRANT SELECT ON habilitacion_urbana TO kamayuk_readonly;
