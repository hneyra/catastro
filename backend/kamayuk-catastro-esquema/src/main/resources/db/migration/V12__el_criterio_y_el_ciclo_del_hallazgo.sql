-- ============================================================================
--  V12 — El criterio de la campania se congela, y el ciclo del hallazgo se cierra
--
--  Cierra tres issues del mismo contexto acotado, y van en UNA migracion porque
--  son la misma tabla y el mismo recorrido: partirla en tres dejaria tres
--  archivos que hay que leer en orden para entender uno.
--
--    #25  `campania.tope`: la segunda mitad de «con que criterio detecto».
--    #23  `hallazgo`: dejar sin efecto pasa a ser un ACTO con sus columnas, la
--         geometria que nadie puede llenar se retira, y `catastro_evento` gana
--         el tipo con que se retracta un hecho ya publicado.
--
--  POR QUE `V12` Y NO `V11`
--  ------------------------
--  `V11` la tiene reservada otro trabajo de esta misma ola. El hueco es inocuo
--  y esta medido, no supuesto: `Migrador.java` declara `.outOfOrder(true)`, asi
--  que una base con `V10 + V12` aplicadas acepta `V11` mas tarde. Lo que NO se
--  hace es reutilizar el numero: dos ramas con la misma version es lo unico que
--  `outOfOrder` no arregla.
-- ============================================================================

-- ── 1. #25 · EL TOPE DE LA CORRIDA, EN LA FILA DE LA CAMPANIA ───────────────
--
--  `campania.umbral` existia para que dos campanias se pudieran comparar —lo
--  dice `V9` con todas las letras: «la tasa de descarte de dos campanias solo
--  se puede comparar sabiendo con que umbral detecto cada una»—. Y no describia
--  como detecto la campania, porque la deteccion aplicaba DOS filtros mas que
--  no quedaban en ninguna parte:
--
--    (a) una `tolerancia` que venia en el CUERPO de la peticion y era la que de
--        verdad filtraba en el `WHERE`. Con `tolerancia > umbral`, el umbral
--        guardado no quitaba ni una fila y la columna MENTIA;
--    (b) un `tope` con 500 por omision escrito en el borde, que recortaba el
--        conjunto sobre el que se calcula la tasa de descarte.
--
--  Desde #25 el criterio entero se declara al ABRIR la campania y vive aqui: la
--  peticion de deteccion no trae ninguna de las dos cifras.
--
--  EL RELLENO DE LAS FILAS QUE YA ESTEN, Y POR QUE ES VACUO
--  -------------------------------------------------------
--  Se rellena con 500, que es el unico tope que el borde podia poner sin que
--  nadie lo escribiera. Y describe cero filas en cualquier instalacion de hoy,
--  por una razon que se puede comprobar: sin cartografia `contrastar` lanza
--  `SinCartografia` antes de mirar una fila, y NO HAY NI UN POLIGONO CARGADO EN
--  NINGUNA INSTALACION, asi que ninguna campania ha corrido nunca una deteccion.
--
--  El DEFAULT se retira despues del relleno a proposito: de aqui en adelante,
--  quien abra una campania tiene que DECIR su tope. Un valor por omision es
--  exactamente lo que produjo el 500 invisible.
--
--  Y EL RELLENO ES DDL Y NO UN `UPDATE`, POR ALGO QUE HUBO QUE EJECUTAR PARA VER
--  ---------------------------------------------------------------------------
--  La primera version escribia `ALTER TABLE ... ADD COLUMN tope integer;` y
--  luego `UPDATE campania SET tope = 500 WHERE tope IS NULL;`. Muere:
--
--    ERROR: unrecognized configuration parameter "app.municipalidad_id"
--    Location: db/migration/V12__...sql   Line: 52
--
--  `campania` tiene FORCE ROW LEVEL SECURITY (`V9`), asi que la politica alcanza
--  TAMBIEN al dueno —que es quien corre las migraciones— y su `USING` evalua
--  `current_setting('app.municipalidad_id')`, que fuera de una peticion no
--  existe. O sea que NINGUN `UPDATE` ni `DELETE` de datos sobre una tabla de
--  tenant se puede escribir en una migracion sin recorrer las municipalidades
--  fijando el contexto en cada una.
--
--  `ADD COLUMN ... NOT NULL DEFAULT` no pasa por la politica: es DDL sobre la
--  definicion de la tabla, no una lectura de filas. Rellena las que haya, sean
--  de la municipalidad que sean, y no necesita contexto de tenant.
ALTER TABLE campania ADD COLUMN tope integer NOT NULL DEFAULT 500;
ALTER TABLE campania ALTER COLUMN tope DROP DEFAULT;
ALTER TABLE campania ADD CONSTRAINT campania_tope_check CHECK (tope > 0);

COMMENT ON COLUMN campania.tope IS
    'Cuantos predios como mucho mira una corrida de esta campania, congelado igual que el umbral '
    '(#25). Las dos cifras juntas son «con que criterio detecto»: una tasa de descarte calculada '
    'sobre un conjunto truncado por un numero que no queda en ninguna parte no se puede comparar '
    'con la de otra corrida. Sin DEFAULT a proposito: quien abre la campania lo dice';

COMMENT ON COLUMN campania.umbral IS
    'La diferencia relativa minima que hace sospechar, congelada. Es la UNICA cifra que filtra '
    'desde #25: hasta entonces el WHERE usaba una `tolerancia` que venia en el cuerpo de la '
    'peticion, y con `tolerancia > umbral` esta columna decia un criterio que no fue el que corrio. '
    'La comparacion es >= (Score.alcanza): alcanzar el umbral basta';

-- ── 2. #23 · DEJAR SIN EFECTO UN HALLAZGO ES UN ACTO ────────────────────────
--
--  `Hallazgo.dejadoSinEfecto()` existia y NO LO LLAMABA NADIE: ni caso de uso,
--  ni endpoint, y `EstadoDelHallazgo.DEJADO_SIN_EFECTO` era inalcanzable. Y el
--  acto no guardaba ni motivo, ni quien, ni cuando —lo unico que habria quedado
--  es la `observacion` de la fila, que la propia anulacion sobrescribe—.
--
--  Las tres columnas y los dos CHECK cruzados, en la forma que `candidato` y
--  `itse` ya usan: el estado y el acto van atados EN LOS DOS SENTIDOS. Un
--  DEJADO_SIN_EFECTO mudo no explica nada, y un FIRME con motivo de anulacion es
--  una contradiccion escrita en una fila.
ALTER TABLE hallazgo ADD COLUMN motivo_anulacion character varying(500);
ALTER TABLE hallazgo ADD COLUMN anulado_por character varying(60);
ALTER TABLE hallazgo ADD COLUMN anulado_en timestamp with time zone;

ALTER TABLE hallazgo ADD CONSTRAINT hallazgo_anulacion_check
    CHECK ((anulado_en IS NULL) = (motivo_anulacion IS NULL)
           AND (anulado_por IS NULL) = (motivo_anulacion IS NULL));
ALTER TABLE hallazgo ADD CONSTRAINT hallazgo_estado_anulacion_check
    CHECK (((estado)::text = 'DEJADO_SIN_EFECTO'::text) = (motivo_anulacion IS NOT NULL));

COMMENT ON COLUMN hallazgo.motivo_anulacion IS
    'Por que este hallazgo dejo de valer. Un hallazgo NO SE BORRA (regla 4): se deja sin efecto, y '
    'su acta se queda donde esta —es inmutable—. Sin motivo, «lo anularon» no explica nada';
COMMENT ON COLUMN hallazgo.anulado_por IS
    'Quien lo decidio. Es la unica pregunta que un estado no contesta, y la misma que `inspector` '
    'contesta del otro lado del recorrido';
COMMENT ON COLUMN hallazgo.anulado_en IS
    'Cuando. Hace falta para poder ordenar la anulacion contra el acta que ese hallazgo ya produjo';

-- ── 3. #23 · LA GEOMETRIA DEL HALLAZGO SE RETIRA ────────────────────────────
--
--  No se podia llenar por NINGUNA via, y esto se midio antes de escribirlo:
--
--    (a) `TODA_GEOMETRIA_ENTRA_POR_BATCH` (ADR-0021) prohibe que un parametro de
--        controlador lleve geometria, asi que la entrada por HTTP esta cerrada
--        por una regla con su muestra;
--    (b) no hay ningun cargador batch que escriba esta columna —el unico que
--        escribe geometria es el de la carga cartografica, sobre `predio`—;
--    (c) el unico camino de produccion que crea un hallazgo es
--        `FiscalizacionCatastralController.enCampo`, que pasaba `null`: la
--        columna es NULA en toda fila que la aplicacion haya escrito;
--    (d) NINGUN `*Resource` la publica —la unica mencion en los diez es una
--        frase de `CandidatoResource` que dice que no sale— y el contrato que
--        `rentas` declara de las DOS lecturas de hallazgos tiene once campos y
--        ninguno es ella.
--
--  O sea: cinco columnas generadas y un indice GiST que se pagan en cada
--  escritura y mienten sobre lo que la tabla guarda. Y no es gratis del todo:
--  ademas quitan de la consulta de #36 un `ST_AsText(h.geometria)` que hoy es
--  `ST_AsText(NULL)` en todas las filas (#30).
--
--  LO QUE CUESTA, DICHO: si algun dia la brigada tiene que poder adjuntar el
--  poligono de lo verificado, la columna vuelve en otra migracion —con sus
--  cuatro columnas de marco y su GiST, que ADR-0034 exige— Y con el cargador
--  que la llene. Lo que no vale es tenerla vacia para no tener que decidirlo.
--  Retirarla no pierde un solo dato: es NULA en todas las filas por construccion.
DROP INDEX hallazgo_geometria_gix;
DROP INDEX hallazgo_marco_ix;
ALTER TABLE hallazgo DROP COLUMN marco_oeste;
ALTER TABLE hallazgo DROP COLUMN marco_sur;
ALTER TABLE hallazgo DROP COLUMN marco_este;
ALTER TABLE hallazgo DROP COLUMN marco_norte;
ALTER TABLE hallazgo DROP COLUMN geometria;

COMMENT ON TABLE hallazgo IS
    'Lo que una PERSONA verifico (ADR-0035 punto 2). Un hallazgo firme HABILITA el acto —versionar '
    'la ficha con su observacion— y NO LO EJECUTA: esta tabla no escribe `ficha_catastral` nunca, y '
    'lo vigila NINGUN_HALLAZGO_CORRIGE_LA_FICHA. No se borra: se deja sin efecto, con su motivo, '
    'quien y cuando (#23). Y no guarda geometria desde #23: no habia por donde llenarla';

-- ── 4. #23 · EL HECHO PUBLICADO SE PUEDE RETRACTAR ──────────────────────────
--
--  `TerritorioParaPublicarJdbc.hallazgosFirmes()` filtra `WHERE estado='FIRME'`,
--  asi que un hallazgo ya publicado como `HALLAZGO_FIRME` que se anulara
--  DEJABA DE APARECER en la proyeccion y nada se lo decia a `rentas`. Y
--  republicarlo tampoco servia: `V10` deriva la identidad de `HALLAZGO_FIRME` de
--  (tipo, municipalidad, hallazgo), asi que el buzon lo pararia con
--  `HechoSelladoReescrito`. La frontera tenia una puerta de una sola direccion.
--
--  DE DONDE SALE LA IDENTIDAD DEL TIPO NUEVO, y por que
--  ---------------------------------------------------
--    HALLAZGO_DEJADO_SIN_EFECTO  de la IDENTIDAD: (tipo, municipalidad, hallazgo).
--
--  Igual que `HALLAZGO_FIRME` y por el mismo motivo: retractar un hecho firmado
--  es OTRO hecho que alguien firma, y que la misma identidad vuelva con otro
--  contenido —otro motivo, otro nombre— no es una retractacion nueva sino
--  alguien reescribiendo lo que otro firmo, y tiene que VERSE. Derivada del
--  contenido serian dos hechos distintos y el receptor aplicaria el segundo
--  encima del primero sin decir nada.
--
--  No colisiona con la de `HALLAZGO_FIRME` del mismo hallazgo: el TIPO entra en
--  el nombre del que sale el sha256, asi que son dos identidades distintas —y
--  tienen que serlo, porque son dos hechos y el receptor recibe los dos—.
ALTER TABLE catastro_evento DROP CONSTRAINT catastro_evento_tipo_ck;
ALTER TABLE catastro_evento ADD CONSTRAINT catastro_evento_tipo_ck
    CHECK (tipo IN ('PREDIO_PROYECTADO',
                    'VALUACION_PUBLICADA',
                    'CORRIDA_CERRADA',
                    'MANZANA_PUBLICADA',
                    'FRENTE_PUBLICADO',
                    'HALLAZGO_FIRME',
                    'HALLAZGO_DEJADO_SIN_EFECTO'));

-- `catastro_evento_ejercicio_ck` NO SE TOCA, y es correcto: esta escrito como
-- una igualdad contra un `IN (...)`, de modo que un tipo nuevo queda obligado a
-- `ejercicio IS NULL` sin decir nada mas. Una retractacion no es de ningun
-- ejercicio, igual que el hallazgo que retracta.

-- El predio, tipo por tipo. La retractacion admite predio Y no-predio por lo
-- mismo que `HALLAZGO_FIRME`: un SUBVALUADOR contrasta un predio concreto y un
-- OMISO_CATASTRAL es, por definicion, lo que NO tiene predio. El `ELSE false`
-- de `V10` es lo que obliga a decidirlo aqui en vez de heredar la forma del
-- vecino: sin esta linea el tipo nuevo no se puede insertar NUNCA.
ALTER TABLE catastro_evento DROP CONSTRAINT catastro_evento_predio_ck;
ALTER TABLE catastro_evento ADD CONSTRAINT catastro_evento_predio_ck
    CHECK (CASE tipo
               WHEN 'PREDIO_PROYECTADO'          THEN predio_id IS NOT NULL
               WHEN 'VALUACION_PUBLICADA'        THEN predio_id IS NOT NULL
               WHEN 'FRENTE_PUBLICADO'           THEN predio_id IS NOT NULL
               WHEN 'CORRIDA_CERRADA'            THEN predio_id IS NULL
               WHEN 'MANZANA_PUBLICADA'          THEN predio_id IS NULL
               WHEN 'HALLAZGO_FIRME'             THEN true
               WHEN 'HALLAZGO_DEJADO_SIN_EFECTO' THEN true
               ELSE false
           END);
