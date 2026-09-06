-- ============================================================================
--  V9 — EL HALLAZGO CATASTRAL: CAMPANIA, CANDIDATO, HALLAZGO, EVIDENCIA Y ACTA
--       (ADR-0035, y con el marco de ADR-0034 en las dos que llevan geometria)
--
--  LA MITAD DE ADR-0021 QUE NO ESTABA IMPLEMENTADA
--  -----------------------------------------------
--  ADR-0021 cierra con una frase exacta: «que las dos areas no coincidan es un
--  HALLAZGO QUE SE INFORMA, no una correccion que se aplica». La primera mitad esta:
--  `predio.geometria` existe desde `V61` y `ficha_catastral.area_terreno` es la del
--  tecnico. La segunda no: no habia tabla donde informarlo, ni acto, ni evidencia.
--  Hoy el hallazgo no se informa — SE PIERDE.
--
--  DOS TABLAS Y NO UN ESTADO (ADR-0035 puntos 1 y 2)
--  ------------------------------------------------
--  `candidato` es lo que la MAQUINA sospecha: origen, clase, score, los insumos que
--  lo dispararon y su geometria. No tiene efecto juridico ninguno.
--  `hallazgo` es lo que una PERSONA verifico: el inspector, la fecha, la version de
--  ficha que contrasto y las dos areas.
--
--  Mezclarlas en una sola tabla con un `estado` que llegue hasta el acta es la
--  alternativa que ADR-0035 descarta, y su motivo cabe en una linea: el dia que haya
--  que responder «¿quien dijo esto?», la respuesta tiene que ser una fila con NOMBRE,
--  no un `score`.
--
--  POR QUE `candidato.predio_id` ES NULABLE Y `hallazgo` LO ATA A SU CLASE
--  ----------------------------------------------------------------------
--  En el OMISO CATASTRAL —hay techo en la ortofoto y no hay fila de `predio`— no hay,
--  POR DEFINICION, predio al que apuntar. Por eso `candidato.predio_id` es nulable.
--
--  Y por eso `hallazgo` no puede exigir `predio_id NOT NULL` a secas: lo que hace en
--  su lugar es ATARLO A LA CLASE con un CHECK, que dice mas que un `NOT NULL` y dice
--  mas que un nulo suelto:
--
--    SUBVALUADOR      exige predio_id, ficha_id y area_de_la_ficha. Sin la version de
--                     ficha, un hallazgo de marzo NO SE PUEDE RELEER EN JULIO —la
--                     ficha se versiona— y la comparacion que lo sustenta deja de
--                     poder reproducirse. Es el mismo motivo por el que
--                     `declaracion_jurada.ficha_catastral_id` la lleva (#28).
--    OMISO_CATASTRAL  exige que los tres sean NULOS. Un predio ahi seria una
--                     contradiccion: si hay predio no es un omiso catastral, es otra
--                     cosa. Dejarlo «opcional» permitiria escribir las dos a la vez y
--                     nadie sabria cual de las dos afirmaciones es la del inspector.
--
--  LAS DOS COMPUERTAS HUMANAS, EN EL ESQUEMA Y NO SOLO EN EL CODIGO
--  ---------------------------------------------------------------
--  Un candidato recorre DETECTADO -> ADMITIDO_EN_GABINETE -> VERIFICADO_EN_CAMPO, y
--  desde cualquiera de los dos primeros puede caer a DESCARTADO. `hallazgo` cuelga de
--  un candidato y `acta` cuelga de un hallazgo, de modo que un acta sin las dos
--  compuertas NO TIENE DE QUE COLGAR: no es un `if` que alguien pueda quitar.
--
--  Lo que el esquema no puede sostener por si solo es que el hallazgo se cree
--  UNICAMENTE desde un candidato ya admitido en gabinete —eso es una transicion, y
--  una transicion no cabe en un CHECK sobre una fila—. Lo sostiene `VerificarEnCampo`
--  y lo mide `ElAtajoNoExisteTest`: el atajo se INTENTA y se comprueba que no puede.
--
--  EL DESCARTE SE CONSERVA, CON SU MOTIVO Y CON SU ETAPA (ADR-0035 punto 5)
--  -----------------------------------------------------------------------
--  Regla 4: aqui no se borra nada. Y ademas —esta es la razon que no es la regla— la
--  TASA DE DESCARTE POR ETAPA es el unico indicador honesto de si el umbral de
--  deteccion sirve: muchos descartes en gabinete significa que el detector dispara
--  sobre ruido; muchos en campo, que el gabinete admite lo que la brigada no
--  confirma. Un descarte borrado es un modelo que nadie puede medir. Por eso
--  `etapa_de_descarte` es una columna y no se deduce del estado anterior, que no se
--  guarda en ninguna parte.
--
--  LAS CUATRO COLUMNAS DE MARCO NO SON OPCIONALES (ADR-0034 regla 1)
--  ----------------------------------------------------------------
--  Bajo RLS `geography_overlaps` no es *leakproof*, no se promueve por encima de la
--  politica y el indice GiST no sirve al rol de la aplicacion: la consulta da el
--  resultado CORRECTO, el plan sigue diciendo «Index», y se lee el padron entero del
--  inquilino. Es el quinto hallazgo de RLS. `candidato` y `hallazgo` llevan geometria,
--  asi que llevan las cuatro columnas generadas y su indice compuesto, en
--  `double precision` —`numeric_le` tampoco es *leakproof* y las columnas dejarian de
--  servir sin que nada se ponga rojo—.
--
--  LO QUE ESTA MIGRACION NO TRAE, Y ES LA MITAD DE LA DECISION
--  ----------------------------------------------------------
--  Ninguna columna de importe, ninguna alicuota y ningun tributo. Un hallazgo firme
--  HABILITA el acto —versionar la ficha con su observacion, que ya existe— y no lo
--  ejecuta; y lo que se cobre de ahi lo decide `rentas` (ADR-0024). Si algun dia una
--  de estas cinco tablas gana una columna `monto`, lo que hay que revisar es la
--  frontera y no la columna.
-- ============================================================================

-- ── La campania: el lote de deteccion, con su umbral escrito ────────────────

CREATE TABLE campania (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    codigo character varying(20) NOT NULL,
    nombre character varying(160) NOT NULL,
    estado character varying(20) DEFAULT 'ABIERTA'::character varying NOT NULL,
    inicio date NOT NULL,
    fin date,
    -- El umbral con el que ESTA campania detecto, congelado. No es un parametro
    -- tributario (regla 5): no entra en ninguna cifra que se cobre, y por eso vive
    -- aqui y no en `normativa`. Lo que compra guardarlo es que la tasa de descarte de
    -- dos campanias solo se puede comparar sabiendo con que umbral detecto cada una.
    umbral numeric(5,4) NOT NULL,
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE campania ADD CONSTRAINT campania_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE campania ADD CONSTRAINT campania_estado_check
    CHECK (((estado)::text = ANY ((ARRAY['ABIERTA'::character varying,
                                         'CERRADA'::character varying])::text[])));
ALTER TABLE campania ADD CONSTRAINT campania_umbral_check
    CHECK ((umbral >= (0)::numeric AND umbral <= (1)::numeric));
ALTER TABLE campania ADD CONSTRAINT campania_fin_check
    CHECK ((fin IS NULL OR fin >= inicio));
ALTER TABLE campania ADD CONSTRAINT campania_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

CREATE UNIQUE INDEX campania_codigo_uq ON campania USING btree (municipalidad_id, codigo);

COMMENT ON TABLE campania IS
    'El lote de deteccion catastral (ADR-0035). Agrupa los candidatos de una corrida y guarda el '
    'umbral con que se detectaron: sin el, la tasa de descarte de dos campanias no es comparable';
COMMENT ON COLUMN campania.umbral IS
    'El umbral de score de ESTA campania, congelado. NO es un parametro tributario (regla 5): no '
    'entra en ninguna cifra que se cobre';

-- ── El candidato: lo que la MAQUINA sospecha ───────────────────────────────

CREATE TABLE candidato (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    campania_id bigint NOT NULL,
    -- NULABLE, y es el punto entero: en el omiso catastral no hay predio al que
    -- apuntar. Un NOT NULL aqui obligaria a inventar un predio para poder sospechar
    -- que falta, que es exactamente lo contrario de lo que la tabla afirma.
    predio_id bigint,
    clase character varying(20) NOT NULL,
    origen character varying(20) NOT NULL,
    score numeric(5,4) NOT NULL,
    -- Lo que lo disparo, tal como lo dijo el detector. `jsonb` y no columnas: los
    -- insumos de una ortofoto y los de un cruce de areas no se parecen en nada, y
    -- una tabla con las columnas de los dos tendria la mitad nulas siempre.
    insumos jsonb NOT NULL,
    geometria geography(MultiPolygon,4326),
    estado character varying(24) DEFAULT 'DETECTADO'::character varying NOT NULL,
    -- El descarte, con su etapa y su motivo (ADR-0035 punto 5). Los tres van juntos:
    -- un descarte sin motivo no explica nada y un descarte sin etapa no se puede
    -- contar por compuerta, que es para lo que sirve contarlos.
    etapa_de_descarte character varying(10),
    motivo_de_descarte character varying(500),
    descartado_por character varying(60),
    descartado_en timestamp with time zone,
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    marco_oeste double precision GENERATED ALWAYS AS (st_xmin(((geometria)::geometry)::box3d)) STORED,
    marco_sur double precision GENERATED ALWAYS AS (st_ymin(((geometria)::geometry)::box3d)) STORED,
    marco_este double precision GENERATED ALWAYS AS (st_xmax(((geometria)::geometry)::box3d)) STORED,
    marco_norte double precision GENERATED ALWAYS AS (st_ymax(((geometria)::geometry)::box3d)) STORED
);

ALTER TABLE candidato ADD CONSTRAINT candidato_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE candidato ADD CONSTRAINT candidato_clase_check
    CHECK (((clase)::text = ANY ((ARRAY['OMISO_CATASTRAL'::character varying,
                                        'SUBVALUADOR'::character varying])::text[])));
ALTER TABLE candidato ADD CONSTRAINT candidato_origen_check
    CHECK (((origen)::text = ANY ((ARRAY['ORTOFOTO'::character varying,
                                         'DRON'::character varying,
                                         'CRUCE_DE_AREAS'::character varying,
                                         'DENUNCIA'::character varying,
                                         'BARRIDO_DE_CAMPO'::character varying])::text[])));
ALTER TABLE candidato ADD CONSTRAINT candidato_estado_check
    CHECK (((estado)::text = ANY ((ARRAY['DETECTADO'::character varying,
                                         'ADMITIDO_EN_GABINETE'::character varying,
                                         'VERIFICADO_EN_CAMPO'::character varying,
                                         'DESCARTADO'::character varying])::text[])));
ALTER TABLE candidato ADD CONSTRAINT candidato_score_check
    CHECK ((score >= (0)::numeric AND score <= (1)::numeric));
-- Un SUBVALUADOR es, por definicion, un predio cuya ficha dice otra area: sin predio
-- no hay ficha que contrastar y la sospecha no significa nada.
ALTER TABLE candidato ADD CONSTRAINT candidato_predio_de_la_clase_check
    CHECK ((clase)::text <> 'SUBVALUADOR'::text OR predio_id IS NOT NULL);
-- Descartado si y solo si tiene etapa y motivo. Es la forma que impide las dos
-- mitades del mismo defecto: un descarte mudo, y una etapa de descarte colgando de
-- un candidato que sigue vivo.
ALTER TABLE candidato ADD CONSTRAINT candidato_descarte_check
    CHECK (((estado)::text = 'DESCARTADO'::text)
           = (etapa_de_descarte IS NOT NULL AND motivo_de_descarte IS NOT NULL
              AND descartado_por IS NOT NULL AND descartado_en IS NOT NULL));
ALTER TABLE candidato ADD CONSTRAINT candidato_etapa_check
    CHECK (etapa_de_descarte IS NULL
           OR (etapa_de_descarte)::text = ANY ((ARRAY['GABINETE'::character varying,
                                                      'CAMPO'::character varying])::text[]));
ALTER TABLE candidato ADD CONSTRAINT candidato_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

-- NOT VALID, como el resto del esquema: validar una foranea es una consulta, y el
-- migrador corre SIN contexto de tenant (cuarto hallazgo de RLS). Con la politica
-- puesta y sin `app.municipalidad_id`, la validacion no ve NINGUNA fila y la
-- migracion muere.
ALTER TABLE candidato ADD CONSTRAINT candidato_campania_fk
    FOREIGN KEY (municipalidad_id, campania_id) REFERENCES campania(municipalidad_id, id) NOT VALID;
ALTER TABLE candidato ADD CONSTRAINT candidato_predio_fk
    FOREIGN KEY (municipalidad_id, predio_id) REFERENCES predio(municipalidad_id, id) NOT VALID;

CREATE INDEX candidato_campania_ix ON candidato USING btree (municipalidad_id, campania_id);
CREATE INDEX candidato_predio_ix ON candidato USING btree (municipalidad_id, predio_id)
    WHERE (predio_id IS NOT NULL);
-- Lo que la pantalla de gabinete abre: la cola de lo que nadie ha mirado todavia.
CREATE INDEX candidato_por_revisar_ix ON candidato USING btree (municipalidad_id, campania_id, id)
    WHERE ((estado)::text <> 'DESCARTADO'::text);

-- Las dos de ADR-0034: el marco para el SQL de aplicacion bajo RLS, y el GiST para
-- el trabajo que corre fuera de ella.
CREATE INDEX candidato_marco_ix ON candidato
    USING btree (municipalidad_id, marco_oeste, marco_sur, marco_este, marco_norte);
CREATE INDEX candidato_geometria_gix ON candidato USING gist (geometria);

COMMENT ON TABLE candidato IS
    'Lo que la MAQUINA sospecha (ADR-0035 punto 1): origen, clase, score, insumos y geometria. Sin '
    'efecto juridico ninguno. Lo que una persona verifica es `hallazgo`, que es otra tabla y no un '
    'estado de esta';
COMMENT ON COLUMN candidato.predio_id IS
    'NULO en el omiso catastral: hay techo en la ortofoto y no hay fila de `predio`, asi que no hay '
    'a que apuntar. Un SUBVALUADOR si lo exige, y lo sostiene candidato_predio_de_la_clase_check';
COMMENT ON COLUMN candidato.etapa_de_descarte IS
    'GABINETE o CAMPO. Es columna y no se deduce: la tasa de descarte POR ETAPA es el unico '
    'indicador honesto de si el umbral de deteccion sirve (ADR-0035 punto 5)';

-- ── El hallazgo: lo que una PERSONA verifico ───────────────────────────────

CREATE TABLE hallazgo (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    candidato_id bigint NOT NULL,
    clase character varying(20) NOT NULL,
    predio_id bigint,
    -- QUE VERSION de ficha se contrasto. Sin esto un hallazgo de marzo no se puede
    -- releer en julio: la ficha se versiona, y la de hoy ya no es la que el inspector
    -- tuvo delante. Mismo motivo que `declaracion_jurada.ficha_catastral_id` (#28).
    ficha_id bigint,
    -- El area de ESA version, COPIADA al verificar. No se relee: releerla mas tarde
    -- daria la de la version vigente entonces, y entonces el hallazgo diria una
    -- diferencia que nadie hallo.
    area_de_la_ficha area_m2,
    area_verificada area_m2 NOT NULL,
    inspector character varying(60) NOT NULL,
    verificado_en date NOT NULL,
    estado character varying(20) DEFAULT 'FIRME'::character varying NOT NULL,
    geometria geography(MultiPolygon,4326),
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    marco_oeste double precision GENERATED ALWAYS AS (st_xmin(((geometria)::geometry)::box3d)) STORED,
    marco_sur double precision GENERATED ALWAYS AS (st_ymin(((geometria)::geometry)::box3d)) STORED,
    marco_este double precision GENERATED ALWAYS AS (st_xmax(((geometria)::geometry)::box3d)) STORED,
    marco_norte double precision GENERATED ALWAYS AS (st_ymax(((geometria)::geometry)::box3d)) STORED
);

ALTER TABLE hallazgo ADD CONSTRAINT hallazgo_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE hallazgo ADD CONSTRAINT hallazgo_clase_check
    CHECK (((clase)::text = ANY ((ARRAY['OMISO_CATASTRAL'::character varying,
                                        'SUBVALUADOR'::character varying])::text[])));
ALTER TABLE hallazgo ADD CONSTRAINT hallazgo_estado_check
    CHECK (((estado)::text = ANY ((ARRAY['FIRME'::character varying,
                                         'DEJADO_SIN_EFECTO'::character varying])::text[])));
-- La clase manda sobre los tres campos del contraste, en los DOS sentidos. Ver la
-- cabecera: un SUBVALUADOR sin version de ficha no se puede releer, y un
-- OMISO_CATASTRAL con predio es una contradiccion escrita en una fila.
ALTER TABLE hallazgo ADD CONSTRAINT hallazgo_contraste_check
    CHECK ((((clase)::text = 'SUBVALUADOR'::text
             AND predio_id IS NOT NULL AND ficha_id IS NOT NULL AND area_de_la_ficha IS NOT NULL)
            OR ((clase)::text = 'OMISO_CATASTRAL'::text
             AND predio_id IS NULL AND ficha_id IS NULL AND area_de_la_ficha IS NULL)));
ALTER TABLE hallazgo ADD CONSTRAINT hallazgo_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

ALTER TABLE hallazgo ADD CONSTRAINT hallazgo_candidato_fk
    FOREIGN KEY (municipalidad_id, candidato_id) REFERENCES candidato(municipalidad_id, id) NOT VALID;
ALTER TABLE hallazgo ADD CONSTRAINT hallazgo_predio_fk
    FOREIGN KEY (municipalidad_id, predio_id) REFERENCES predio(municipalidad_id, id) NOT VALID;
ALTER TABLE hallazgo ADD CONSTRAINT hallazgo_ficha_fk
    FOREIGN KEY (municipalidad_id, ficha_id) REFERENCES ficha_catastral(municipalidad_id, id) NOT VALID;

-- Un candidato produce COMO MUCHO un hallazgo. Verificar dos veces en campo el mismo
-- candidato no son dos hallazgos: es el mismo hecho contado dos veces, y con el la
-- tasa de descarte deja de sumar uno.
CREATE UNIQUE INDEX hallazgo_candidato_uq ON hallazgo USING btree (municipalidad_id, candidato_id);
CREATE INDEX hallazgo_predio_ix ON hallazgo USING btree (municipalidad_id, predio_id)
    WHERE (predio_id IS NOT NULL);

CREATE INDEX hallazgo_marco_ix ON hallazgo
    USING btree (municipalidad_id, marco_oeste, marco_sur, marco_este, marco_norte);
CREATE INDEX hallazgo_geometria_gix ON hallazgo USING gist (geometria);

COMMENT ON TABLE hallazgo IS
    'Lo que una PERSONA verifico (ADR-0035 punto 2). Un hallazgo firme HABILITA el acto —versionar '
    'la ficha con su observacion— y NO LO EJECUTA: esta tabla no escribe `ficha_catastral` nunca, y '
    'lo vigila NINGUN_HALLAZGO_CORRIGE_LA_FICHA';
COMMENT ON COLUMN hallazgo.ficha_id IS
    'QUE VERSION de ficha se contrasto. Sin ella un hallazgo de marzo no se puede releer en julio, '
    'porque la ficha se versiona. Mismo motivo que declaracion_jurada.ficha_catastral_id (#28)';
COMMENT ON COLUMN hallazgo.area_de_la_ficha IS
    'La de esa version, COPIADA al verificar. Releerla despues daria otra cifra y el hallazgo diria '
    'una diferencia que nadie hallo';

-- ── La evidencia: su huella y sus DOS relojes ──────────────────────────────

CREATE TABLE evidencia (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    hallazgo_id bigint NOT NULL,
    tipo character varying(20) NOT NULL,
    -- La huella se calcula EN EL DISPOSITIVO y viaja con el archivo. Aqui se guarda,
    -- no se recalcula: recalcularla sobre lo que llego comprobaria que lo que se
    -- tiene es igual a lo que se tiene.
    sha256 character(64) NOT NULL,
    ruta character varying(500) NOT NULL,
    -- LOS DOS RELOJES, y son dos hechos distintos. `capturado_en` es el del aparato
    -- —cuando se tomo la foto— y `recibido_en` el del servidor —cuando entro—.
    -- Confundirlos hace inauditable la captura en campo: una brigada sin cobertura
    -- sube por la tarde lo que fotografio por la manana, y con un solo reloj esa
    -- foto pasa a estar tomada por la tarde sin que nadie lo haya decidido.
    capturado_en timestamp with time zone NOT NULL,
    recibido_en timestamp with time zone NOT NULL,
    dispositivo character varying(80),
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE evidencia ADD CONSTRAINT evidencia_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE evidencia ADD CONSTRAINT evidencia_tipo_check
    CHECK (((tipo)::text = ANY ((ARRAY['FOTO'::character varying,
                                       'VIDEO'::character varying,
                                       'ORTOFOTO'::character varying,
                                       'DOCUMENTO'::character varying,
                                       'CROQUIS'::character varying])::text[])));
ALTER TABLE evidencia ADD CONSTRAINT evidencia_sha256_check
    CHECK ((sha256 ~ '^[0-9a-f]{64}$'::text));
ALTER TABLE evidencia ADD CONSTRAINT evidencia_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);
ALTER TABLE evidencia ADD CONSTRAINT evidencia_hallazgo_fk
    FOREIGN KEY (municipalidad_id, hallazgo_id) REFERENCES hallazgo(municipalidad_id, id) NOT VALID;

-- UNA FOTO NO SUSTENTA DOS ACTAS (ADR-0035 punto 3). Es del MOTOR y no de un `if`:
-- dos cargas simultaneas del mismo archivo leerian las dos «no esta» y las dos
-- entrarian. Y no lleva `WHERE`: aqui no hay filas anteriores de las que protegerse
-- —la tabla nace en esta migracion—, asi que el indice no puede pararla (sexto
-- parrafo del hallazgo 4 de RLS).
CREATE UNIQUE INDEX evidencia_sha256_uq ON evidencia USING btree (municipalidad_id, sha256);
CREATE INDEX evidencia_hallazgo_ix ON evidencia USING btree (municipalidad_id, hallazgo_id);

COMMENT ON TABLE evidencia IS
    'Lo que sustenta un hallazgo (ADR-0035 punto 3). Se hashea en el dispositivo y se guarda en '
    'almacenamiento inmutable. UNIQUE (municipalidad_id, sha256): una foto no sustenta dos actas';
COMMENT ON COLUMN evidencia.capturado_en IS
    'El reloj del APARATO. Separado de `recibido_en` a proposito: son dos hechos distintos y '
    'confundirlos hace inauditable la captura en campo';
COMMENT ON COLUMN evidencia.recibido_en IS
    'El reloj del SERVIDOR. Ver `capturado_en`';

-- ── El acta: el acto ───────────────────────────────────────────────────────

CREATE TABLE acta (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    numero character varying(20) NOT NULL,
    hallazgo_id bigint NOT NULL,
    fecha date NOT NULL,
    inspector character varying(60) NOT NULL,
    detalle character varying(1000) NOT NULL,
    observacion character varying(500) NOT NULL,
    usuario_registro character varying(60) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE acta ADD CONSTRAINT acta_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE acta ADD CONSTRAINT acta_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);
ALTER TABLE acta ADD CONSTRAINT acta_hallazgo_fk
    FOREIGN KEY (municipalidad_id, hallazgo_id) REFERENCES hallazgo(municipalidad_id, id) NOT VALID;

CREATE UNIQUE INDEX acta_numero_uq ON acta USING btree (municipalidad_id, numero);
-- Un hallazgo levanta UN acta. Dos actas del mismo hallazgo serian dos papeles que
-- dicen lo mismo con numeros distintos, y el administrado tendria dos plazos.
CREATE UNIQUE INDEX acta_hallazgo_uq ON acta USING btree (municipalidad_id, hallazgo_id);

COMMENT ON TABLE acta IS
    'El acto: lo que se levanta sobre un hallazgo firme. INMUTABLE —un acta equivocada no se '
    'edita: se deja sin efecto el hallazgo y se levanta otra—. Ni un importe: lo que se cobre lo '
    'decide `rentas` (ADR-0024)';

-- ----------------------------------------------------------------------------
--  RLS. Sin valor por omision: sin contexto de tenant, la consulta FALLA.
-- ----------------------------------------------------------------------------

ALTER TABLE campania ENABLE ROW LEVEL SECURITY;
ALTER TABLE campania FORCE ROW LEVEL SECURITY;
CREATE POLICY campania_tenant ON campania FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

ALTER TABLE candidato ENABLE ROW LEVEL SECURITY;
ALTER TABLE candidato FORCE ROW LEVEL SECURITY;
CREATE POLICY candidato_tenant ON candidato FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

ALTER TABLE hallazgo ENABLE ROW LEVEL SECURITY;
ALTER TABLE hallazgo FORCE ROW LEVEL SECURITY;
CREATE POLICY hallazgo_tenant ON hallazgo FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

ALTER TABLE evidencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE evidencia FORCE ROW LEVEL SECURITY;
CREATE POLICY evidencia_tenant ON evidencia FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

ALTER TABLE acta ENABLE ROW LEVEL SECURITY;
ALTER TABLE acta FORCE ROW LEVEL SECURITY;
CREATE POLICY acta_tenant ON acta FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

-- ----------------------------------------------------------------------------
--  PRIVILEGIOS
--
--  Ningun DELETE en ninguna de las cinco: el descarte se conserva con su motivo
--  (ADR-0035 punto 5, regla 4). Y `evidencia` y `acta` tampoco reciben UPDATE, que
--  es lo que las hace INMUTABLES: una foto corregida en el sitio deja de ser la que
--  se hasheo, y un acta editada deja al administrado con un papel que ya no dice lo
--  que la base dice. Se corrigen agregando: otra evidencia, y —dejando sin efecto el
--  hallazgo— otra acta.
-- ----------------------------------------------------------------------------

GRANT INSERT, SELECT, UPDATE ON campania TO kamayuk_app;
GRANT INSERT, SELECT, UPDATE ON candidato TO kamayuk_app;
GRANT INSERT, SELECT, UPDATE ON hallazgo  TO kamayuk_app;
GRANT INSERT, SELECT         ON evidencia TO kamayuk_app;
GRANT INSERT, SELECT         ON acta      TO kamayuk_app;

GRANT SELECT ON campania  TO kamayuk_readonly;
GRANT SELECT ON candidato TO kamayuk_readonly;
GRANT SELECT ON hallazgo  TO kamayuk_readonly;
GRANT SELECT ON evidencia TO kamayuk_readonly;
GRANT SELECT ON acta      TO kamayuk_readonly;
