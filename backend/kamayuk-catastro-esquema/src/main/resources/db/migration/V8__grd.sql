-- ============================================================================
--  V8 — LA GESTION DEL RIESGO DE DESASTRES: ZONA DE RIESGO, FAJA MARGINAL E ITSE
--       (#5, ADR-0034, ADR-0024)
--
--  QUE ENTRA AQUI, Y QUE SE QUEDA EN `rentas`
--  ------------------------------------------
--  `rentas` ya sabe QUE RIESGO EXIGE UN GIRO: `ciiu.riesgo_itse` con el mismo
--  vocabulario de cuatro niveles que `RiesgoItse.java`. Lo que no sabe nadie —ni
--  aqui ni alli— es si el ESTABLECIMIENTO tiene el certificado, ni si el lote cae
--  en una zona de riesgo. Eso es un hecho del PREDIO, y el predio es de este
--  sistema.
--
--  Y aqui se queda como HECHO: este esquema no emite licencias, no las niega y no
--  sabe lo que es un giro (ADR-0024). Publica «este lote intersecta una zona MUY
--  ALTA no mitigable» y «este predio no tiene ITSE vigente al 12 de marzo»; quien
--  decide es quien emite.
--
--  LOS NIVELES SE ESCRIBEN IGUAL QUE EN `rentas`, Y NO ES ESTETICA
--  --------------------------------------------------------------
--  BAJO, MEDIO, ALTO y MUY_ALTO, con el mismo `character varying(10)` que
--  `ciiu.riesgo_itse`. Si aqui se escribieran «MUY ALTO» o «4», el dia que alguien
--  cruce el riesgo que el giro exige con el que el certificado acredita tendria que
--  traducir, y una traduccion entre dos vocabularios de cuatro valores se escribe
--  mal una vez y no la ve nadie: las dos columnas siguen siendo texto valido.
--
--  `MITIGABLE` ES EL DATO QUE DECIDE, NO EL NIVEL
--  ----------------------------------------------
--  Una zona de riesgo MUY ALTO **mitigable** no impide nada: se construye la obra de
--  mitigacion. Una NO mitigable si. Por eso `mitigable` es `NOT NULL` y no admite
--  valor por omision: una carta de peligro que no lo diga es una carta que no se
--  puede usar para decidir, y ponerle `true` autorizaria y ponerle `false` negaria
--  —las dos cosas por descuido—.
--
--  LAS CUATRO COLUMNAS DE MARCO NO SON OPCIONALES (ADR-0034 regla 1)
--  ----------------------------------------------------------------
--  Bajo RLS `geography_overlaps` no es *leakproof*, no se promueve por encima de la
--  politica y el indice GiST no sirve al rol de la aplicacion: la consulta da el
--  resultado CORRECTO, el plan sigue diciendo «Index», y se lee el padron entero del
--  inquilino. Es el quinto hallazgo de RLS. `zona_riesgo` y `faja_marginal` llevan
--  las cuatro y su indice compuesto; `RevisorDeEsquema` lo comprueba solo, sin base
--  de datos, en las tres verificaciones bloqueantes.
--
--  `itse` NO las lleva, y tampoco es un descuido: no tiene geometria. Un certificado
--  no ocupa un poligono —cuelga del predio, que ya tiene el suyo—, asi que anadirle
--  un marco seria cuatro columnas que no se pueden derivar de nada.
--
--  `double precision` y no `numeric`: `numeric_le` tampoco es *leakproof*, y las
--  cuatro columnas dejarian de servir sin que nada se ponga rojo.
--
--  NI UN `DELETE`, Y `itse` ADEMAS EN `TABLAS_PROTEGIDAS` (RNF-051, regla 4)
--  ------------------------------------------------------------------------
--  Un certificado de seguridad se ENTREGA: el administrado lo exhibe en el local y
--  lo presenta ante quien le pide la licencia. Borrarlo en la base deja al papel y
--  al sistema diciendo cosas distintas, y quien tiene el papel gana la discusion.
--  Uno emitido por error se ANULA —`fecha_anulacion` y su motivo, que son un acto
--  con su fecha— y la fila queda. Ninguna de las tres tablas recibe `DELETE`.
-- ============================================================================

-- ── Zona de riesgo: la carta de peligro de CENEPRED ─────────────────────────

CREATE TABLE zona_riesgo (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    codigo character varying(30) NOT NULL,
    fenomeno character varying(40) NOT NULL,
    nivel character varying(10) NOT NULL,
    mitigable boolean NOT NULL,
    fuente character varying(120) NOT NULL,
    documento_origen character varying(120) NOT NULL,
    vigencia_desde date NOT NULL,
    vigencia_hasta date,
    geometria geography(MultiPolygon,4326) NOT NULL,
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    marco_oeste double precision GENERATED ALWAYS AS (st_xmin(((geometria)::geometry)::box3d)) STORED,
    marco_sur double precision GENERATED ALWAYS AS (st_ymin(((geometria)::geometry)::box3d)) STORED,
    marco_este double precision GENERATED ALWAYS AS (st_xmax(((geometria)::geometry)::box3d)) STORED,
    marco_norte double precision GENERATED ALWAYS AS (st_ymax(((geometria)::geometry)::box3d)) STORED
);

COMMENT ON TABLE zona_riesgo IS
    'Un poligono de la carta de peligro que la municipalidad recibio de CENEPRED. NO '
    'lo produce este sistema y NO se digitaliza en ventanilla: entra por carga batch, '
    'como la geometria del predio (ADR-0021)';
COMMENT ON COLUMN zona_riesgo.mitigable IS
    'EL DATO QUE DECIDE. Una zona MUY_ALTO mitigable no impide nada -se construye la '
    'obra-; una no mitigable si. Sin valor por omision a proposito: ponerlo autorizaria '
    'o negaria por descuido';
COMMENT ON COLUMN zona_riesgo.fenomeno IS
    'Inundacion, sismo, deslizamiento, huaico... El catalogo es de CENEPRED y no de '
    'este sistema, asi que va como texto y no como enumerado: inventar aqui una lista '
    'cerrada haria rechazar una carta valida por traer un fenomeno que nadie previo';
COMMENT ON COLUMN zona_riesgo.nivel IS
    'El mismo vocabulario que `ciiu.riesgo_itse` de `rentas` (RiesgoItse.java): BAJO, '
    'MEDIO, ALTO, MUY_ALTO. Dos vocabularios obligarian a traducir, y una traduccion '
    'entre dos listas de cuatro valores se escribe mal una vez y no la ve nadie';
COMMENT ON COLUMN zona_riesgo.vigencia_hasta IS
    'Nula mientras la carta siga vigente. Una carta nueva no borra la anterior: la '
    'cierra, y las dos quedan (regla 4)';

ALTER TABLE zona_riesgo ADD CONSTRAINT zona_riesgo_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE zona_riesgo ADD CONSTRAINT zona_riesgo_nivel_check
    CHECK (((nivel)::text = ANY ((ARRAY['BAJO'::character varying,
                                        'MEDIO'::character varying,
                                        'ALTO'::character varying,
                                        'MUY_ALTO'::character varying])::text[])));
ALTER TABLE zona_riesgo ADD CONSTRAINT zona_riesgo_vigencia_check
    CHECK ((vigencia_hasta IS NULL OR vigencia_hasta >= vigencia_desde));
ALTER TABLE zona_riesgo ADD CONSTRAINT zona_riesgo_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

CREATE UNIQUE INDEX zona_riesgo_codigo_uq ON zona_riesgo USING btree (municipalidad_id, codigo);

-- Las dos de ADR-0034: el marco para el SQL de aplicacion bajo RLS, y el GiST para
-- el trabajo que corre FUERA de ella.
CREATE INDEX zona_riesgo_marco_ix ON zona_riesgo
    USING btree (municipalidad_id, marco_oeste, marco_sur, marco_este, marco_norte);
CREATE INDEX zona_riesgo_geometria_gix ON zona_riesgo USING gist (geometria);

ALTER TABLE zona_riesgo ENABLE ROW LEVEL SECURITY;
ALTER TABLE zona_riesgo FORCE ROW LEVEL SECURITY;
CREATE POLICY zona_riesgo_tenant ON zona_riesgo FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

GRANT INSERT, SELECT, UPDATE ON zona_riesgo TO kamayuk_app;
GRANT SELECT ON zona_riesgo TO kamayuk_readonly;

-- ── Faja marginal: la que delimita la ANA ───────────────────────────────────

CREATE TABLE faja_marginal (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    codigo character varying(30) NOT NULL,
    cuerpo_agua character varying(120) NOT NULL,
    ancho_m numeric(8,2) NOT NULL,
    fuente character varying(120) NOT NULL,
    documento_origen character varying(120) NOT NULL,
    vigencia_desde date NOT NULL,
    vigencia_hasta date,
    geometria geography(MultiPolygon,4326) NOT NULL,
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    marco_oeste double precision GENERATED ALWAYS AS (st_xmin(((geometria)::geometry)::box3d)) STORED,
    marco_sur double precision GENERATED ALWAYS AS (st_ymin(((geometria)::geometry)::box3d)) STORED,
    marco_este double precision GENERATED ALWAYS AS (st_xmax(((geometria)::geometry)::box3d)) STORED,
    marco_norte double precision GENERATED ALWAYS AS (st_ymax(((geometria)::geometry)::box3d)) STORED
);

COMMENT ON TABLE faja_marginal IS
    'La faja marginal de un cuerpo de agua, delimitada por la Autoridad Nacional del '
    'Agua. Es una tabla aparte de `zona_riesgo` y no un fenomeno mas: la ANA no declara '
    'un NIVEL sino una RESTRICCION de dominio publico hidraulico, con su ancho y su '
    'resolucion. Meterla en la otra obligaria a inventarle un nivel y un `mitigable`';
COMMENT ON COLUMN faja_marginal.ancho_m IS
    'El ancho que la resolucion de la ANA fija, en metros. NO se deriva del poligono, '
    'por lo mismo que el area del terreno tampoco (ADR-0021): lo fija un acto';

ALTER TABLE faja_marginal ADD CONSTRAINT faja_marginal_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE faja_marginal ADD CONSTRAINT faja_marginal_ancho_check CHECK ((ancho_m > (0)::numeric));
ALTER TABLE faja_marginal ADD CONSTRAINT faja_marginal_vigencia_check
    CHECK ((vigencia_hasta IS NULL OR vigencia_hasta >= vigencia_desde));
ALTER TABLE faja_marginal ADD CONSTRAINT faja_marginal_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

CREATE UNIQUE INDEX faja_marginal_codigo_uq ON faja_marginal USING btree (municipalidad_id, codigo);

CREATE INDEX faja_marginal_marco_ix ON faja_marginal
    USING btree (municipalidad_id, marco_oeste, marco_sur, marco_este, marco_norte);
CREATE INDEX faja_marginal_geometria_gix ON faja_marginal USING gist (geometria);

ALTER TABLE faja_marginal ENABLE ROW LEVEL SECURITY;
ALTER TABLE faja_marginal FORCE ROW LEVEL SECURITY;
CREATE POLICY faja_marginal_tenant ON faja_marginal FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

GRANT INSERT, SELECT, UPDATE ON faja_marginal TO kamayuk_app;
GRANT SELECT ON faja_marginal TO kamayuk_readonly;

-- ── ITSE: el certificado, que cuelga del predio y no tiene geometria ────────

CREATE TABLE itse (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    predio_id bigint NOT NULL,
    numero character varying(30) NOT NULL,
    nivel_riesgo character varying(10) NOT NULL,
    modalidad character varying(12) NOT NULL,
    vigencia_desde date NOT NULL,
    vigencia_hasta date NOT NULL,
    fecha_anulacion date,
    motivo_anulacion character varying(200),
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL
);

COMMENT ON TABLE itse IS
    'La Inspeccion Tecnica de Seguridad en Edificaciones de un predio. NO tiene '
    'geometria: cuelga del predio, que ya tiene la suya. Este sistema publica el hecho '
    '-hay o no hay certificado vigente a una fecha-; quien emite la licencia decide '
    '(ADR-0024)';
COMMENT ON COLUMN itse.vigencia_hasta IS
    'NOT NULL, al reves que en las dos tablas de arriba: un certificado SIEMPRE vence, '
    'y dejar la fecha nula lo volveria eterno. Un ITSE vencido no se devuelve como '
    'vigente, y hay prueba que inserta uno vencido y comprueba que no sale';
COMMENT ON COLUMN itse.fecha_anulacion IS
    'La fecha del acto que lo dejo sin efecto, con su motivo. Un certificado no se '
    'borra: se anula, y la fila queda (RNF-051, regla 4). Va junto con el motivo o no '
    'va: una anulacion sin por que no es un acto';
COMMENT ON COLUMN itse.nivel_riesgo IS
    'El nivel que el certificado ACREDITA, con el mismo vocabulario que '
    '`ciiu.riesgo_itse` de `rentas` acredita EXIGIR. Las dos columnas se comparan';

ALTER TABLE itse ADD CONSTRAINT itse_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE itse ADD CONSTRAINT itse_nivel_check
    CHECK (((nivel_riesgo)::text = ANY ((ARRAY['BAJO'::character varying,
                                               'MEDIO'::character varying,
                                               'ALTO'::character varying,
                                               'MUY_ALTO'::character varying])::text[])));
ALTER TABLE itse ADD CONSTRAINT itse_modalidad_check
    CHECK (((modalidad)::text = ANY ((ARRAY['PREVIA'::character varying,
                                            'POSTERIOR'::character varying])::text[])));
ALTER TABLE itse ADD CONSTRAINT itse_vigencia_check CHECK ((vigencia_hasta > vigencia_desde));
ALTER TABLE itse ADD CONSTRAINT itse_anulacion_check
    CHECK (((fecha_anulacion IS NULL) = (motivo_anulacion IS NULL)));
ALTER TABLE itse ADD CONSTRAINT itse_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

-- NOT VALID, como el resto del esquema: validar una foranea es una consulta, y el
-- migrador corre SIN contexto de tenant (cuarto hallazgo de RLS). Con la politica
-- puesta y sin `app.municipalidad_id`, la validacion no ve NINGUNA fila y la
-- migracion muere con «unrecognized configuration parameter».
ALTER TABLE itse ADD CONSTRAINT itse_predio_fk
    FOREIGN KEY (municipalidad_id, predio_id) REFERENCES predio(municipalidad_id, id) NOT VALID;

CREATE UNIQUE INDEX itse_numero_uq ON itse USING btree (municipalidad_id, numero);

-- La consulta de este issue: los certificados de un predio, filtrados por fecha.
--
-- NO hay restriccion de solape sobre (predio_id, vigencia), y es deliberado: en un
-- mismo predio puede haber varios establecimientos -una galeria, un mercado- y cada
-- uno tiene su propio ITSE al mismo tiempo. Prohibir el solape aqui haria imposible
-- registrar el segundo local de un lote, que es el caso corriente y no el raro. El
-- dia que exista `establecimiento` (ADR-0034 la nombra), la restriccion sera suya.
CREATE INDEX itse_predio_ix ON itse USING btree (municipalidad_id, predio_id, vigencia_hasta);

ALTER TABLE itse ENABLE ROW LEVEL SECURITY;
ALTER TABLE itse FORCE ROW LEVEL SECURITY;
CREATE POLICY itse_tenant ON itse FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

-- UPDATE si, DELETE no: la anulacion se escribe encima de la propia fila -es un acto
-- con su fecha y su motivo- y el certificado no se borra nunca (RNF-051, regla 4).
GRANT INSERT, SELECT, UPDATE ON itse TO kamayuk_app;
GRANT SELECT ON itse TO kamayuk_readonly;
