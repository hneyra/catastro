-- ============================================================================
--  V13 — HIGIENE DEL ESQUEMA (#27)
--
--  EL NUMERO, Y POR QUE ES EL TRECE
--  --------------------------------
--  Va detras de dos migraciones que estan en vuelo y no en `main`: `V11` es la de
--  #22 (zonas del mismo plan que no se pisan) y `V12` la de #23/#24/#25 (el criterio
--  y el ciclo del hallazgo). Se comprobo que NINGUNA de las dos toca lo que esta
--  toca: `V11` es de `zonificacion` entera, y `V12` de `campania`, `hallazgo` y los
--  dos CHECK cruzados de `catastro_evento` — mientras esta es de `frente_predio`,
--  `habilitacion_urbana` y `candidato`. Ni un objeto en comun, y las trece aplican
--  limpias en orden sobre una base de cero: medido, no supuesto.
--
--  Cinco cosas pequenias que la revision del PR #15 encontro, y las cinco son el
--  criterio del propio proyecto aplicado a lo que el proyecto dejo. Ninguna edita
--  una migracion ya aplicada: Flyway guarda el checksum y `validateOnMigrate` esta
--  activo, asi que cambiarle un comentario a `V6` o a `V7` dejaria toda base ya
--  migrada sin poder migrar. `V10` lo midio: «Migration checksum mismatch for
--  migration version 5». Lo que si se puede es reemplazar el `COMMENT ON`, que es
--  lo que un DBA lee.
--
--  UNA PREMISA DEL ENCARGO SALIO FALSA AL MEDIRLA, Y ESO CAMBIA UNA DE LAS CINCO
--  -----------------------------------------------------------------------------
--  El encargo daba por muerto `habilitacion_urbana_codigo_prefijo_ix` porque «una
--  comparacion de rango (>= / <) usa la clase por omision». **En este repositorio
--  la busqueda por prefijo NO se escribe asi**: `RangoDePrefijo.condicion` emite
--  `~>=~` y `~<~`, que son precisamente los operadores de `varchar_pattern_ops`, y
--  por eso `V1` ya creo `via_codigo_prefijo_ix` con `text_pattern_ops` para la
--  unica busqueda por prefijo viva del sistema. El indice sirve a la forma que este
--  codigo usa; lo que estaba flojo era el comentario, que no decia CUALES son esos
--  operadores. Se conserva y se explica. Las medidas, en el punto 2.
--
--  Y no es una lectura mia: DAT-01 §0 hallazgo 3 —el que `V7` cita— escribe la
--  mitigacion del proyecto con todas las letras, «un rango con los operadores de
--  `text_pattern_ops` —`~>=~` y `~<~`, los dos leakproof—, SOBRE UN INDICE DECLARADO
--  CON ESA CLASE DE OPERADORES», y `BusquedaDelCatalogoVialTest` la mide desde #565
--  sobre `via`.
-- ============================================================================

-- ── 1. EL INDICE QUE `V10` DEJO REDUNDANTE ──────────────────────────────────
--
--  `V6` creo `frente_predio_ix (municipalidad_id, predio_id)`. `V10` creo
--  `frente_predio_via_uq (municipalidad_id, predio_id, via_id)`, del que el primero
--  es PREFIJO ESTRICTO: toda busqueda que use uno usa el otro, y el planificador ya
--  prefiere el segundo aunque los dos esten.
--
--  MEDIDO contra PostgreSQL 16 + PostGIS con 18 000 frentes de 6 000 predios en dos
--  municipalidades y `ANALYZE`, como `kamayuk_app` y sin `enable_seqscan = off`,
--  sobre la consulta de `FrentesDelPredioJdbc.deUnPredio` —la unica que filtra por
--  `predio_id` a secas—:
--
--    CON el indice:  Index Scan using frente_predio_via_uq ... shared hit=23
--    SIN el indice:  Index Scan using frente_predio_via_uq ... shared hit=23
--
--  El mismo indice elegido, el mismo `Index Cond` con la politica y `predio_id`
--  juntos, y el mismo numero de bloques: la lectura no pierde nada. `pg_stat_user_
--  indexes` lo dice desde el otro lado: `frente_predio_ix` acumulo **0** recorridos
--  y `frente_predio_via_uq`, todos.
--
--  Lo que si cambia es la escritura, y se midio con WAL en vez de con reloj —el
--  reloj de una maquina compartida no distingue 200 ms de 300—: 3 000 altas de
--  frente cuestan **18 220 registros de WAL / 2,22 MB** sin el indice y **21 230 /
--  2,45 MB** con el. Los ~3 010 de diferencia son uno por fila insertada, que es
--  exactamente lo que `V7` escribio al retirar `zonificacion_vigencia_ix`: «un
--  indice que nadie consulta se paga en cada escritura».
--
--  NO se toca `frente_predio_via_uq` —es lo que hace idempotente al derivador—, ni
--  `frente_principal_uq`, que es PARCIAL (`WHERE es_principal`) y no lo cubre nadie.
DROP INDEX frente_predio_ix;

-- ── 2. LA UNICA CONVENCION DE BUSQUEDA POR PREFIJO, DICHA ───────────────────
--
--  `habilitacion_urbana_codigo_prefijo_ix` SE CONSERVA, y el encargo pedia decidirlo
--  con la medida delante. Aqui esta, sobre 12 000 habilitaciones en dos
--  municipalidades, como `kamayuk_app` y sin forzar nada:
--
--    forma                         indice elegido                          bloques
--    ---------------------------   -------------------------------------   -------
--    codigo ~>=~ 'X' AND ~<~ 'Y'   habilitacion_urbana_codigo_prefijo_ix       102
--    codigo >=   'X' AND <   'Y'   habilitacion_urbana_codigo_uq               102
--    codigo =    'X'               habilitacion_urbana_codigo_prefijo_ix         3
--    codigo =    'X'  (sin el)     habilitacion_urbana_codigo_uq                 3
--
--  La primera fila es la que decide: es la que `RangoDePrefijo` escribe, y la sirve
--  ESTE indice y no el unico. La cuarta descarta la otra objecion posible —que
--  compitiera con el unico, que es lo que le costo el puesto a `zonificacion_
--  vigencia_ix` en `V7`—: para la igualdad los dos cuestan **3 bloques**, asi que
--  cual gane da igual.
--
--  Y RETIRARLO REPRODUCE EL QUINTO HALLAZGO DE RLS, LITERAL. Sin el, el mismo rango
--  de la primera fila da:
--
--    Bitmap Heap Scan on habilitacion_urbana ... Buffers: shared hit=133
--      Filter: ((codigo)::text ~>=~ 'HU-0007' AND (codigo)::text ~<~ 'HU-0008')
--      Rows Removed by Filter: 5900
--      ->  Bitmap Index Scan on habilitacion_urbana_marco_ix
--            Index Cond: (municipalidad_id = current_setting('app.municipalidad_id'))
--
--  o sea **el plan sigue diciendo «Index»** con solo la politica en el `Index Cond`,
--  el prefijo caido al `Filter` y la habilitacion entera del inquilino recorrida.
--  Ese es el defecto que este proyecto lleva seis hallazgos evitando, y retirar el
--  indice lo dejaria puesto para el dia que alguien escriba la primera consulta.
--
--  Lo que sigue siendo cierto y se dice: hoy NADA consulta `habilitacion_urbana`
--  —ningun `.java` de `src/main` la nombra, medido—, y si esa tabla debe tener
--  codigo es #31 y no esto.
COMMENT ON INDEX habilitacion_urbana_codigo_prefijo_ix IS
    'Sirve la busqueda por prefijo TAL COMO la escribe `RangoDePrefijo`: con los operadores '
    'de patron ~>=~ y ~<~, que son los de varchar_pattern_ops y SI son leakproof, no con >= '
    'y < de la clase por omision (tercer hallazgo de RLS: un LIKE no llega al indice bajo la '
    'politica). Es la misma convencion que via_codigo_prefijo_ix de `V1` y que el CUC de '
    '`V6`. Sin el, ese rango cae al Filter y el plan sigue diciendo «Index»: medido, 133 '
    'bloques y 5 900 filas descartadas contra 102 con el (#27)';

-- ── 3. EL COMENTARIO DE `longitud_m`, QUE `V10` VOLVIO MEDIO FALSO ──────────
--
--  `V6` escribio «La que midio el tecnico. NO se deriva de la geometria». Desde
--  `V10` el derivador SI la escribe, como PROPUESTA, y confirmarla es un acto de una
--  persona. La frase que un DBA lee tiene que distinguir las dos cosas, como ya hace
--  la de `longitud_estado`.
COMMENT ON COLUMN frente_predio.longitud_m IS
    'La longitud del frente en metros lineales. Que significa lo dice `longitud_estado` y no '
    'esta columna: CONFIRMADA es la que midio una persona, y PROPUESTA la que el derivador de '
    '`V10` corto contra el eje de la via. Lo que NO se deriva es la CONFIRMADA: de ella cuelga '
    'un cobro y un metro es indistinguible de otro al leerlo, por lo mismo que el area del '
    'terreno tampoco se deriva del poligono (ADR-0021). El comentario de `V6` decia «NO se '
    'deriva» a secas, y desde `V10` eso es media verdad (#27)';

-- ── 4. QUE SIGNIFICA UN `PROPUESTA` ANTERIOR A `V10`, Y POR QUE NO SE SABE ──
--
--  `V10` anadio `longitud_estado NOT NULL DEFAULT 'PROPUESTA'`, asi que toda fila
--  escrita ANTES de `V10` quedo marcada como propuesta del derivador — y una fila
--  anterior a `V10` solo la pudo escribir una persona, porque el derivador no
--  existia. Es la etiqueta al reves.
--
--  NO se reetiqueta, y el motivo es que no se puede: `frente_predio` no guarda la
--  fecha de la primera escritura de `longitud_m` sino `fecha_registro` de la FILA, y
--  una fila de antes de `V10` que el derivador haya tocado despues no se distingue
--  de una que no. Reetiquetar por fecha convertiria una suposicion en una cifra
--  oficial, que es lo que ADR-0021 impide.
--
--  Hoy no cuesta nada y esta contado: CERO filas de `frente_predio` en cualquier
--  instalacion —no hay ni un poligono cargado en ninguna—, asi que este comentario
--  existe para el dia que las haya y no para arreglar nada de hoy.
COMMENT ON COLUMN frente_predio.longitud_estado IS
    'PROPUESTA la escribe el derivador cortando el lote contra el eje de la via; CONFIRMADA la '
    'escribe una persona, con su observacion. De esta cifra cuelga un cobro (ADR-0021), asi que '
    'las dos NO se leen igual y no se distinguen por convencion sino por columna. AVISO: el '
    'DEFAULT de `V10` marco PROPUESTA toda fila anterior a `V10`, y esas solo las pudo escribir '
    'una persona porque el derivador no existia; no se reetiquetan porque la tabla no guarda '
    'cuando se escribio la longitud, solo cuando se creo la fila. Al aplicar esta migracion no '
    'habia ni una fila en ninguna instalacion (#27)';

-- ── 5. `candidato` ATA UNA DIRECCION DONDE SU GEMELO ATA LAS DOS ────────────
--
--  `candidato_predio_de_la_clase_check` dice `SUBVALUADOR => predio_id NOT NULL` y
--  calla la otra mitad, asi que **un OMISO_CATASTRAL puede llevar un predio**.
--  Medido antes de escribir esto, como `kamayuk_app` contra el esquema de `V11`: la
--  fila entra y devuelve `4001 | OMISO_CATASTRAL | 1`.
--
--  Su gemelo `hallazgo_contraste_check` ata las dos, y la cabecera de `V9` escribio
--  por que: «Dejarlo "opcional" permitiria escribir las dos a la vez y nadie sabria
--  cual de las dos afirmaciones es la del inspector». Un omiso catastral es, por
--  definicion, techo sin fila de `predio`: si apunta a uno, o no es omiso o el
--  predio es de otro.
--
--  VA VALIDO Y NO `NOT VALID`, y se midio porque el encargo lo preguntaba. El cuarto
--  hallazgo de RLS es de las FORANEAS: validar una foranea ejecuta una CONSULTA
--  contra la tabla referenciada, y el migrador corre sin `app.municipalidad_id`, asi
--  que la politica la deja sin ver ni una fila. Validar un CHECK es un recorrido del
--  monton que hace el motor, no una consulta sujeta a la politica. Comprobado sobre
--  4 000 candidatos de dos municipalidades y SIN contexto de tenant: `convalidated`
--  sale `t`; y con UNA fila que lo viola —escrita en la municipalidad 2 y con el
--  contexto ya reseteado— el mismo `ALTER TABLE` falla con «check constraint
--  "candidato_omiso_sin_predio_ck" of relation "candidato" is violated by some row».
--  O sea que el recorrido ve las filas de los dos inquilinos y no valida en vacio.
--
--  Y el censo previo dio CERO filas que lo violen, que es lo que hace que esta
--  migracion no pueda morir sobre una instalacion.
ALTER TABLE candidato ADD CONSTRAINT candidato_omiso_sin_predio_ck
    CHECK ((clase)::text <> 'OMISO_CATASTRAL'::text OR predio_id IS NULL);

COMMENT ON COLUMN candidato.predio_id IS
    'NULO en el omiso catastral: hay techo en la ortofoto y no hay fila de `predio`, asi que no '
    'hay a que apuntar, y candidato_omiso_sin_predio_ck lo EXIGE desde `V13`. Un SUBVALUADOR si '
    'lo exige NO nulo, y lo sostiene candidato_predio_de_la_clase_check. Las dos direcciones '
    'atadas, como en hallazgo_contraste_check (#27)';
