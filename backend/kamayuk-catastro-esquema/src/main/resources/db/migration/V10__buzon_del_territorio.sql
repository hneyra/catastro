-- ============================================================================
--  V10 — EL FRENTE LINEAL Y EL BUZON DEL TERRITORIO (#7, ADR-0021, ADR-0034)
--
--  QUE APORTA `catastro` A LOS ARBITRIOS, Y QUE NO
--  ----------------------------------------------
--  El importe de un arbitrio lo determina `rentas`: es un tributo, y ADR-0024 pone
--  ahi la frontera. Lo que este sistema aporta es EL INSUMO —cuantos metros lineales
--  de frente tiene un predio y a que via dan—, y nada mas. En esta migracion no hay
--  ni una columna de importe, ni un factor de barrido, ni el nombre de un servicio
--  de arbitrio: los tres del enumerado `Servicio` de `rentas` son vocabulario suyo y
--  no se escriben aqui, ni siquiera dentro de un comentario. Lo comprueba
--  `CatastroNoNombraUnArbitrioTest` sobre el arbol entero, y este parrafo los llevaba
--  DENTRO hasta que esa prueba lo puso rojo: una regla que se salta a si misma en el
--  comentario que la explica no protege nada.
--
--  LAS TRES COSAS QUE ESTA MIGRACION HACE
--  --------------------------------------
--   1. `via.eje`: el eje de calzada. Sin el no hay contra que cortar el lote.
--   2. `frente_predio` aprende a decir si su longitud es PROPUESTA o CONFIRMADA, y
--      nace `frente_derivacion`, que dice CUANDO se derivo por ultima vez.
--   3. `catastro_evento` admite tres tipos mas —`MANZANA_PUBLICADA`,
--      `FRENTE_PUBLICADO` y `HALLAZGO_FIRME`— y sus dos CHECK cruzados se reescriben,
--      porque estaban escritos «todo lo que no es X» y con seis tipos eso es falso.
--
--  POR QUE `longitud_m` SIGUE SIN DERIVARSE (ADR-0021, y `V6` ya lo decia)
--  ----------------------------------------------------------------------
--  De esta cifra cuelga un cobro, y un metro es indistinguible de otro al leerlo.
--  Derivarla y dejarla como oficial cambiaria la base de los arbitrios de todo el
--  padron sin que nadie lo decidiera — exactamente lo que ADR-0021 evita al negarse a
--  derivar el area del terreno del poligono.
--
--  Asi que el derivador PROPONE: escribe la fila con `longitud_estado = 'PROPUESTA'`,
--  y confirmarla es un acto de una persona, con su usuario, su hora y su observacion
--  (regla 10). Una propuesta y una medida se distinguen por columna y no por
--  convencion, que es lo unico que impide que se lean igual dentro de dos anios.
--
--  El valor por omision es 'PROPUESTA' y no 'CONFIRMADA' A PROPOSITO: una longitud
--  que nadie confirmo no puede pasar por confirmada por descuido de un INSERT.
--
--  LA CORRECCION DE DOS COMENTARIOS FALSOS DE `V5`, Y POR QUE NO SE EDITA `V5`
--  --------------------------------------------------------------------------
--  `V5` dice dos cosas que el codigo desmiente:
--
--    (a) «Es un UUID derivado (RFC 4122 §4.3, version 5)». NO lo es.
--        `IdentidadDelEvento` deriva con **sha256**, trunca a 16 bytes y marca la
--        **version 8 de RFC 9562** («custom»). Lo dice con todas las letras: «no es un
--        uuid v5 —que exigiria SHA-1—». Un uuid v5 y este valor son bytes distintos
--        para el mismo nombre, asi que quien leyera el comentario y reprodujera la
--        derivacion en otro sitio obtendria OTRA identidad para el mismo hecho, y el
--        receptor lo deduplicaria mal.
--
--    (b) «CORRIDA_CERRADA se deriva de (tipo, municipalidad, ejercicio, huella
--        agregada)». No: se deriva del `corridaId`, y el propio javadoc del metodo
--        explica por que se cambio —derivada del contenido, dos corridas identicas
--        habrian salido como «la misma identidad con otro contenido», o sea el aviso
--        de hecho sellado reescrito disparado por dos corridas que no reescribieron
--        nada—.
--
--  `V5` NO se edita, y no es pereza: Flyway guarda el checksum del archivo y
--  `validateOnMigrate` esta activo por omision, asi que cambiarle un comentario deja
--  toda base ya migrada sin poder migrar. Medido contra PostgreSQL 16.13 sobre una
--  base con `V1..V9` aplicadas: «Validate failed: Migrations have failed validation.
--  Migration checksum mismatch for migration version 5». Un comentario mejor no vale
--  una base que no arranca.
--
--  Lo que si se corrige es (1) el comentario que un DBA lee de verdad —el `COMMENT ON
--  COLUMN` de `catastro_evento.evento_id`, que esta migracion reemplaza— y (2) esta
--  cabecera, que es la que describe la derivacion de los SEIS tipos a partir de hoy.
--
--  DE DONDE SALE LA IDENTIDAD DE CADA UNO DE LOS SEIS TIPOS
--  -------------------------------------------------------
--  sha256 del nombre, truncado a 16 bytes, version 8 de RFC 9562 y variante RFC. El
--  nombre es distinto en cada tipo, y la diferencia NO es un detalle:
--
--    PREDIO_PROYECTADO   del CONTENIDO: (tipo, municipalidad, predio, huella).
--    MANZANA_PUBLICADA   del CONTENIDO: (tipo, municipalidad, manzana, huella).
--    FRENTE_PUBLICADO    del CONTENIDO: (tipo, municipalidad, predio, huella).
--        Los tres son proyecciones: republicar lo que no cambio produce EL MISMO
--        identificador, el buzon no escribe una segunda fila y el receptor no aplica
--        nada. Reproyectar el territorio entero cuesta —de los dos lados— exactamente
--        lo que cambio.
--
--    VALUACION_PUBLICADA de la IDENTIDAD: (tipo, municipalidad, ejercicio, predio).
--    HALLAZGO_FIRME      de la IDENTIDAD: (tipo, municipalidad, hallazgo).
--        Los dos son hechos que alguien firmo. Que la MISMA identidad vuelva con OTRO
--        contenido no es un hecho nuevo: es el emisor reescribiendo lo firmado, y
--        tiene que VERSE —el buzon lanza `HechoSelladoReescrito`—. Derivada del
--        contenido se veria como un evento mas y se aplicaria encima en silencio.
--        Un hallazgo es lo que una PERSONA verifico (ADR-0035 punto 2): si el area
--        verificada de un hallazgo ya publicado cambia, o alguien lo esta corrigiendo
--        sin acta o alguien se equivoco, y las dos cosas hay que verlas.
--
--    CORRIDA_CERRADA     de la CORRIDA: (tipo, municipalidad, ejercicio, corridaId).
--        Ni del contenido ni de la identidad del ejercicio: dos corridas del mismo
--        ejercicio SON dos hechos aunque den el mismo resultado, y el receptor
--        sustituye su cierre por el ultimo.
-- ============================================================================

-- ── 1. EL EJE DE CALZADA DE LA VIA ──────────────────────────────────────────
--
--  Sin geometria, `via` era un catalogo de nombres: codigo, tipo y nombre. Con ella
--  se puede contestar la unica pregunta que el frente necesita —«¿que parte del
--  borde de este lote da a esta calle?»— sin pedirle a nadie que la dibuje a mano.
--
--  Es el EJE y no la calzada entera: es lo que un levantamiento vial produce y lo que
--  el catastro de una municipalidad tiene. El ancho, cuando existe, es dato
--  NORMATIVO y vive en `seccion_via` (`V7`), que dice cuanto DEBE medir la via segun
--  la ordenanza y no cuanto mide hoy — dos cosas que ADR-0021 ya separo para el area.
--
--  Nulo mientras nadie levante el trazo, que es el estado de HOY en todas las
--  instalaciones: no hay ni un poligono cargado en ninguna. El derivador lo dice en
--  vez de devolver cero.
ALTER TABLE via ADD COLUMN eje geography(LineString,4326);

COMMENT ON COLUMN via.eje IS
    'El eje de calzada, tal como lo levanto el catastro vial. Nulo mientras nadie lo '
    'levante. NO es la seccion normativa —cuanto DEBE medir la via lo dice `seccion_via`, '
    'V7—, y de el no sale ninguna longitud oficial: el frente que se corta contra el es '
    'una PROPUESTA (ADR-0021)';

-- Las cuatro columnas del marco (ADR-0034 regla 1). No son opcionales y no son una
-- optimizacion: bajo RLS ningun predicado espacial es *leakproof*, asi que no se
-- promueve por encima de la politica, el GiST no sirve al rol de la aplicacion, la
-- consulta da el resultado CORRECTO y el plan sigue diciendo «Index» mientras lee el
-- catalogo vial entero del inquilino. Es el quinto hallazgo de RLS.
--
-- `double precision` y no `numeric`: `numeric_le` tampoco es *leakproof*, y las
-- cuatro columnas dejarian de servir sin que nada se pusiera rojo.
ALTER TABLE via ADD COLUMN marco_oeste double precision GENERATED ALWAYS AS (st_xmin(((eje)::geometry)::box3d)) STORED;
ALTER TABLE via ADD COLUMN marco_sur   double precision GENERATED ALWAYS AS (st_ymin(((eje)::geometry)::box3d)) STORED;
ALTER TABLE via ADD COLUMN marco_este  double precision GENERATED ALWAYS AS (st_xmax(((eje)::geometry)::box3d)) STORED;
ALTER TABLE via ADD COLUMN marco_norte double precision GENERATED ALWAYS AS (st_ymax(((eje)::geometry)::box3d)) STORED;

CREATE INDEX via_marco_ix ON via
    USING btree (municipalidad_id, marco_oeste, marco_sur, marco_este, marco_norte);

-- El GiST se conserva ADEMAS del marco, para el trabajo que corre FUERA de RLS: el
-- generador de teselas del carril de referencia de ADR-0037 fija su
-- `app.municipalidad_id` una vez y barre.
CREATE INDEX via_eje_gix ON via USING gist (eje);

-- ── 2. LA LONGITUD PROPUESTA, Y CUANDO SE DERIVO ────────────────────────────

ALTER TABLE frente_predio ADD COLUMN longitud_estado character varying(12) NOT NULL DEFAULT 'PROPUESTA';
ALTER TABLE frente_predio ADD COLUMN confirmado_por character varying(60);
ALTER TABLE frente_predio ADD COLUMN confirmado_en timestamp with time zone;

ALTER TABLE frente_predio ADD CONSTRAINT frente_longitud_estado_ck
    CHECK (((longitud_estado)::text = ANY ((ARRAY['PROPUESTA'::character varying,
                                                  'CONFIRMADA'::character varying])::text[])));

-- Confirmar es un ACTO: lleva nombre y hora, o no es confirmacion. Sin esta
-- igualdad, `longitud_estado = 'CONFIRMADA'` con `confirmado_por` nulo seria una
-- cifra oficial que nadie firmo — que es exactamente lo que ADR-0021 impide.
ALTER TABLE frente_predio ADD CONSTRAINT frente_confirmacion_ck
    CHECK (((longitud_estado)::text = 'CONFIRMADA')
           = (confirmado_por IS NOT NULL AND confirmado_en IS NOT NULL));

COMMENT ON COLUMN frente_predio.longitud_estado IS
    'PROPUESTA la escribe el derivador cortando el lote contra el eje de la via; '
    'CONFIRMADA la escribe una persona, con su observacion. De esta cifra cuelga un '
    'cobro (ADR-0021), asi que las dos NO se leen igual y no se distinguen por '
    'convencion sino por columna';
COMMENT ON COLUMN frente_predio.confirmado_por IS
    'Quien confirmo la longitud. Nulo mientras siga siendo una propuesta, y el CHECK '
    'cruzado no admite la otra combinacion';

-- UN frente por (predio, via), y es lo que hace IDEMPOTENTE al derivador: volver a
-- correrlo no escribe una segunda propuesta del mismo tramo. La garantia es del
-- MOTOR y no de un `if` —dos corridas simultaneas leerian las dos «no esta» y las
-- dos insertarian—, que es la misma decision que `catastro_evento_uq` de `V5`.
--
-- Lo que cuesta, dicho: un lote que toca la MISMA calle en dos tramos separados
-- —una calle que rodea una esquina— tiene un solo frente, y el derivador propone el
-- tramo mas largo. No suma los dos: una suma seria una longitud que no corresponde a
-- ninguna geometria, y entonces la fila diria dos cosas distintas. Lo que sobra es un
-- hallazgo que el tecnico resuelve al confirmar, nunca una cifra que el sistema se
-- inventa.
CREATE UNIQUE INDEX frente_predio_via_uq ON frente_predio
    USING btree (municipalidad_id, predio_id, via_id);

CREATE TABLE frente_derivacion (
    municipalidad_id bigint NOT NULL,
    predio_id bigint NOT NULL,
    derivado_en timestamp with time zone NOT NULL,
    propuestos integer NOT NULL,
    motivo character varying(200)
);

COMMENT ON TABLE frente_derivacion IS
    'Cuando se derivaron por ultima vez los frentes de un predio, y cuantos salieron. '
    'Existe para que «este predio no tiene frentes» y «a este predio no le ha pasado el '
    'derivador» sean respuestas DISTINTAS: sin esta tabla las dos son una lista vacia, y '
    'la primera se arregla midiendo en campo y la segunda cargando la cartografia';
COMMENT ON COLUMN frente_derivacion.motivo IS
    'Por que no salio ninguno: el lote no tiene poligono, ninguna via cercana tiene eje, '
    'el corte no dio ningun tramo. Obligatorio cuando `propuestos` es cero — un cero sin '
    'motivo se lee como «no da a ninguna calle», que de un predio urbano es falso';

ALTER TABLE frente_derivacion ADD CONSTRAINT frente_derivacion_pk
    PRIMARY KEY (municipalidad_id, predio_id);
ALTER TABLE frente_derivacion ADD CONSTRAINT frente_derivacion_propuestos_ck
    CHECK ((propuestos >= 0));
ALTER TABLE frente_derivacion ADD CONSTRAINT frente_derivacion_motivo_ck
    CHECK ((propuestos = 0) = (motivo IS NOT NULL));
ALTER TABLE frente_derivacion ADD CONSTRAINT frente_derivacion_municipalidad_id_fkey
    FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

-- NOT VALID, como el resto del esquema: validar una foranea es una consulta, y el
-- migrador corre SIN contexto de tenant (cuarto hallazgo de RLS). Con la politica
-- puesta y sin `app.municipalidad_id`, la validacion no ve NINGUNA fila y muere.
ALTER TABLE frente_derivacion ADD CONSTRAINT frente_derivacion_predio_fk
    FOREIGN KEY (municipalidad_id, predio_id) REFERENCES predio(municipalidad_id, id) NOT VALID;

-- Sin geometria propia y por eso sin marco: lo que guarda es una fecha y un conteo.
ALTER TABLE frente_derivacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE frente_derivacion FORCE ROW LEVEL SECURITY;
CREATE POLICY frente_derivacion_tenant ON frente_derivacion FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

GRANT INSERT, SELECT, UPDATE ON frente_derivacion TO kamayuk_app;
GRANT SELECT ON frente_derivacion TO kamayuk_readonly;

-- ── 3. LOS TRES TIPOS NUEVOS DEL BUZON ──────────────────────────────────────
--
--  LOS DOS CHECK CRUZADOS ESTABAN ESCRITOS EN NEGATIVO, Y ESO NO ESCALA
--  --------------------------------------------------------------------
--  `V5` los escribio como «`PREDIO_PROYECTADO` no lleva ejercicio y TODO LO DEMAS
--  si» y «`CORRIDA_CERRADA` no lleva predio y TODO LO DEMAS si». Con tres tipos eran
--  correctos; con seis son FALSOS, y de la peor manera: obligarian a
--  `MANZANA_PUBLICADA` a nombrar un ejercicio que no tiene y un predio que no es
--  suyo. Anadir el tipo al `tipo_ck` sin tocar estos dos habria dejado un tipo que no
--  se puede insertar nunca, y el diagnostico —«viola catastro_evento_ejercicio_ck»—
--  no dice por que.
--
--  Se reescriben en POSITIVO y con `ELSE false`: cada tipo dice que lleva, y un
--  septimo tipo que alguien anada al `tipo_ck` sin decidir su forma NO entra. Falla
--  ruidosamente en el primer INSERT en vez de colarse con la forma del vecino.
ALTER TABLE catastro_evento DROP CONSTRAINT catastro_evento_tipo_ck;
ALTER TABLE catastro_evento ADD CONSTRAINT catastro_evento_tipo_ck
    CHECK (tipo IN ('PREDIO_PROYECTADO',
                    'VALUACION_PUBLICADA',
                    'CORRIDA_CERRADA',
                    'MANZANA_PUBLICADA',
                    'FRENTE_PUBLICADO',
                    'HALLAZGO_FIRME'));

-- Solo lo que es DE UN EJERCICIO lo nombra. Un frente, una manzana y un hallazgo no
-- son de ningun ejercicio: el frente que se midio en 2026 sigue siendo el mismo en
-- 2027, y darle un ejercicio invitaria a versionarlo por ano, que es una decision
-- tributaria y no catastral (ADR-0024).
ALTER TABLE catastro_evento DROP CONSTRAINT catastro_evento_ejercicio_ck;
ALTER TABLE catastro_evento ADD CONSTRAINT catastro_evento_ejercicio_ck
    CHECK ((tipo IN ('VALUACION_PUBLICADA', 'CORRIDA_CERRADA')) = (ejercicio IS NOT NULL));

-- El predio, tipo por tipo. `HALLAZGO_FIRME` es el unico que lo admite NULO Y NO
-- NULO, y no es una excepcion comoda: un hallazgo de `SUBVALUADOR` contrasta un
-- predio concreto y uno de `OMISO_CATASTRAL` es, por definicion, lo que NO tiene
-- predio (ADR-0035; `hallazgo_contraste_check` de `V9` dice lo mismo del otro lado).
-- Exigirlo dejaria fuera del buzon justo la mitad que a `rentas` mas le interesa.
ALTER TABLE catastro_evento DROP CONSTRAINT catastro_evento_predio_ck;
ALTER TABLE catastro_evento ADD CONSTRAINT catastro_evento_predio_ck
    CHECK (CASE tipo
               WHEN 'PREDIO_PROYECTADO'   THEN predio_id IS NOT NULL
               WHEN 'VALUACION_PUBLICADA' THEN predio_id IS NOT NULL
               WHEN 'FRENTE_PUBLICADO'    THEN predio_id IS NOT NULL
               WHEN 'CORRIDA_CERRADA'     THEN predio_id IS NULL
               WHEN 'MANZANA_PUBLICADA'   THEN predio_id IS NULL
               WHEN 'HALLAZGO_FIRME'      THEN true
               ELSE false
           END);

-- La correccion de (a): el comentario que un DBA lee. Reemplaza al de `V5`, que
-- decia «UUID v5» de un valor que no lo es.
COMMENT ON COLUMN catastro_evento.evento_id IS
    'La identidad del hecho, DERIVADA y no aleatoria: sha256 del nombre canonico, '
    'truncado a 16 bytes, con la version 8 de RFC 9562 («custom») y la variante RFC. NO '
    'es un uuid v5 —eso exigiria SHA-1— y no hace falta que lo sea: solo `catastro` la '
    'calcula y `rentas` la trata como opaca. Los SEIS tipos NO la derivan igual, y esa '
    'diferencia es lo que separa «este hecho ya lo mande» de «el emisor esta '
    'reescribiendo un hecho firmado»: ver la cabecera de V10';
