-- ============================================================================
--  V5 — EL BUZON DE SALIDA DE `catastro` (C-8, ADR-0026 §3 y ADR-0027)
--
--  QUE FALTABA
--  -----------
--  `rentas` tiene desde P5C la MITAD RECEPTORA entera —`catastro_evento_aplicado`,
--  `predio_ref`, `ficha_ref`, `valuacion_corrida`, `valuacion_predio`, el rol
--  `rol_ingestor_catastro` que las escribe y el candado que las comprueba antes de
--  emitir— y no habia nada al otro lado. Medido antes de escribir esto: en los seis
--  repositorios no aparece `ValuacionDePredioPublicada` ni una sola vez; en
--  `catastro/backend` la palabra «evento» sale en UN archivo y es un javadoc.
--
--  Consecuencia: dos de las seis cifras del tablero del corte (P6 §5.2) se dibujan
--  «sin fuente», la anti-entropia compara contra filas que nadie escribio, y el
--  candado de ADR-0027 §2 —lo unico que impide emitir un padron con la valuacion a
--  medias— no se puede abrir NUNCA, porque el cierre que lo abriria no llega.
--
--  QUE ES ESTA TABLA
--  -----------------
--  Un OUTBOX TRANSACCIONAL: la fila se escribe en la MISMA transaccion que el hecho
--  que la produjo, y un proceso aparte la entrega. Es la misma pieza que `V2` de
--  `caja` (`pago_evento`) y por el mismo motivo: si la fila esta, el hecho esta; si
--  el hecho esta, la fila esta. No hay ninguna llamada de red dentro de la
--  transaccion que escribe el padron.
--
--  POR QUE NO HAY MAS TABLAS QUE ESTA
--  ----------------------------------
--  Se considero anadir `valuacion_corrida` y `valuacion_predio` tambien aqui —el
--  espejo de `V5` de `rentas`— y NO se hace, porque el outbox ya es ese registro:
--  `cuerpo` guarda el evento entero congelado y ninguna fila se borra (regla 4,
--  RNF-051). Duplicarlo dejaria DOS verdades sobre el mismo hecho y la que se leyera
--  en una consulta seria la que nadie recalculo — el reparto que #397 midio para el
--  «Estado» de la infraccion administrativa y #481 para el uso hallado.
--
--  EL `evento_id` LO GENERA QUIEN EMITE, Y COMO LO DERIVA DECIDE COSAS
--  ------------------------------------------------------------------
--  Es un UUID **derivado** (RFC 4122 §4.3, version 5) y no aleatorio, y no se deriva
--  igual en los tres tipos porque los tres significan cosas distintas:
--
--    PREDIO_PROYECTADO   se deriva del CONTENIDO: (tipo, municipalidad, predio,
--                        huella). Reproyectar un predio que no ha cambiado produce
--                        EL MISMO evento, que el receptor deduplica; uno que si
--                        cambio produce otro. Una reproyeccion entera del padron
--                        cuesta, del lado del receptor, exactamente los predios que
--                        cambiaron.
--
--    CORRIDA_CERRADA     igual, del contenido: (tipo, municipalidad, ejercicio,
--                        huella agregada). El cierre SI se reemplaza en `rentas`
--                        (su `V5` le da UPDATE al ingestor sobre `valuacion_corrida`),
--                        asi que dos corridas con el mismo resultado son un solo
--                        hecho y con resultados distintos son dos.
--
--    VALUACION_PUBLICADA se deriva de la IDENTIDAD y NO del contenido: (tipo,
--                        municipalidad, ejercicio, predio). Es deliberado y es lo
--                        contrario de las otras dos. Una valuacion es un HECHO
--                        SELLADO (ADR-0027 §1): que la MISMA identidad llegue dos
--                        veces con CONTENIDO DISTINTO no es un hecho nuevo, es el
--                        emisor reescribiendo uno sellado, y tiene que VERSE. Con
--                        identidad derivada del contenido se veria como un evento
--                        mas y el receptor lo rechazaria por la clave primaria de
--                        `valuacion_predio` diciendo «ya hay una», que es cierto y
--                        no es la causa. Derivada de la identidad, el receptor
--                        compara la huella que `V9` le hizo guardar y dice lo que
--                        de verdad paso.
--
--  LA SECUENCIA
--  ------------
--  Es el `id` de esta tabla, que es `IDENTITY`: monotono y por eso comparable. Lo
--  que el receptor hace con ella esta en `V4` de `rentas`: un hecho VIEJO que llega
--  tarde no pisa a uno nuevo ya aplicado. Que dos eventos del MISMO predio salgan en
--  el orden en que se escribieron lo garantiza el motor, no esta tabla: los dos actos
--  bloquean la fila de `predio`, asi que el orden en que toman su `id` es el orden en
--  que se serializaron.
--
--  POR QUE `estado` Y NO UN CURSOR DEL CONSUMIDOR
--  ---------------------------------------------
--  Un consumidor con cursor —«dame lo que tenga secuencia mayor que N»— PIERDE
--  EVENTOS en silencio, y no es hipotetico: `id` se asigna al `INSERT` y no al
--  `COMMIT`, asi que una transaccion que toma el 100 y confirma despues de otra que
--  tomo el 101 queda por detras de un cursor que ya paso por 101. La fila esta, el
--  consumidor no la vera nunca, y nada lo dice. Con un ESTADO no hay posicion que
--  adelantar: lo pendiente sigue pendiente hasta que alguien lo acuse, confirme
--  cuando confirme.
-- ============================================================================

CREATE TABLE catastro_evento (
    municipalidad_id bigint       NOT NULL REFERENCES municipalidad (id),
    id               bigint       GENERATED ALWAYS AS IDENTITY,
    evento_id        uuid         NOT NULL,
    tipo             varchar(40)  NOT NULL,
    predio_id        bigint,
    ejercicio        ejercicio,
    cuerpo           jsonb        NOT NULL,
    huella           char(64)     NOT NULL,
    estado           varchar(20)  NOT NULL,
    intentos         integer      NOT NULL DEFAULT 0,
    ultimo_error     varchar(400),
    creado_en        timestamptz  NOT NULL,
    entregado_en     timestamptz,

    CONSTRAINT catastro_evento_pk PRIMARY KEY (municipalidad_id, id),
    -- LA IDEMPOTENCIA DEL EMISOR. Reproyectar el padron entero no produce un solo
    -- evento nuevo si nada cambio, y volver a correr la valuacion de un ejercicio
    -- no produce una segunda valuacion del mismo predio. Es del MOTOR y no de un
    -- `if`: dos corridas simultaneas leerian las dos «no esta» y las dos emitirian.
    CONSTRAINT catastro_evento_uq UNIQUE (municipalidad_id, evento_id),
    CONSTRAINT catastro_evento_tipo_ck CHECK (tipo IN ('PREDIO_PROYECTADO',
                                                       'VALUACION_PUBLICADA',
                                                       'CORRIDA_CERRADA')),
    CONSTRAINT catastro_evento_estado_ck CHECK (estado IN ('PENDIENTE', 'ENTREGADO')),
    CONSTRAINT catastro_evento_intentos_ck CHECK (intentos >= 0),
    -- Entregado es un hecho CON HORA. Sin ella, «se entrego» no se puede conciliar
    -- contra nada y el retraso del buzon no se puede medir.
    CONSTRAINT catastro_evento_entregado_ck CHECK (
        (estado = 'ENTREGADO') = (entregado_en IS NOT NULL)),
    -- Una valuacion y un cierre nombran su ejercicio; una proyeccion de predio no
    -- tiene ninguno. Sin esto, un cierre sin ejercicio se emitiria y el receptor lo
    -- aplicaria sobre `NULL`, que en `valuacion_corrida` es la clave primaria.
    CONSTRAINT catastro_evento_ejercicio_ck CHECK (
        (tipo = 'PREDIO_PROYECTADO' AND ejercicio IS NULL)
        OR (tipo <> 'PREDIO_PROYECTADO' AND ejercicio IS NOT NULL)),
    CONSTRAINT catastro_evento_predio_ck CHECK (
        (tipo = 'CORRIDA_CERRADA' AND predio_id IS NULL)
        OR (tipo <> 'CORRIDA_CERRADA' AND predio_id IS NOT NULL))
);

-- El que lee el publicador: lo pendiente, en el orden en que se emitio. Parcial
-- porque lo entregado no se vuelve a mirar nunca y el buzon crece para siempre
-- (regla 4: aqui no se borra nada).
CREATE INDEX catastro_evento_pendiente_ix ON catastro_evento (municipalidad_id, id)
    WHERE estado = 'PENDIENTE';

COMMENT ON TABLE catastro_evento IS
    'El buzon de salida de `catastro` (C-8, ADR-0026 §3). Se escribe EN LA MISMA TRANSACCION que '
    'el hecho que lo produjo. Un proceso aparte lo entrega y lo marca. Lo que esto compra es que '
    'el padron se escriba con `rentas` apagado; lo que cuesta es que la proyeccion del vecino '
    'tenga un desfase visible, que es lo que la anti-entropia mide.';
COMMENT ON COLUMN catastro_evento.evento_id IS
    'La identidad del hecho, derivada (UUID v5) y no aleatoria. Ver la cabecera de la migracion: '
    'los tres tipos NO la derivan igual, y esa diferencia es lo que separa «este hecho ya lo '
    'mande» de «el emisor esta reescribiendo un hecho sellado».';
COMMENT ON COLUMN catastro_evento.cuerpo IS
    'El evento entero, congelado. No se recompone al entregar: dentro de dos anios el predio '
    'podria estar dado de baja y su ficha versionada tres veces, y lo que se entrego tiene que '
    'poder explicarse solo. Misma decision que `pago_evento.cuerpo` y `recibo_movimiento.importe`.';
COMMENT ON COLUMN catastro_evento.huella IS
    'sha256 del cuerpo canonico, calculada AQUI. Es la que viaja en el evento y la que el receptor '
    'copia en cada fila que escribe (`V9` de `rentas`): alli NO se recalcula, porque recalcularla '
    'sobre lo proyectado comprobaria que lo que se tiene es igual a lo que se tiene.';
COMMENT ON COLUMN catastro_evento.id IS
    'La SECUENCIA que viaja en el evento. Monotona por ser IDENTITY. El receptor descarta con ella '
    'un hecho viejo que llega tarde (`V4` de `rentas`).';

-- ----------------------------------------------------------------------------
--  RLS. Sin valor por omision: sin contexto de tenant, la consulta FALLA.
-- ----------------------------------------------------------------------------

ALTER TABLE catastro_evento ENABLE ROW LEVEL SECURITY;
ALTER TABLE catastro_evento FORCE ROW LEVEL SECURITY;
CREATE POLICY catastro_evento_tenant ON catastro_evento FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

-- ----------------------------------------------------------------------------
--  Privilegios
--
--  `UPDATE` si, porque el publicador marca la entrega; `DELETE` no, y no lo va a
--  recibir: un evento entregado es la unica prueba de que la proyeccion del vecino
--  salio de aqui, y la anti-entropia lo necesita para explicar una discrepancia.
-- ----------------------------------------------------------------------------

GRANT INSERT, SELECT, UPDATE ON catastro_evento TO sgtm_app;
GRANT SELECT                  ON catastro_evento TO sgtm_readonly;

-- ----------------------------------------------------------------------------
--  LA IDENTIDAD DE UNA CORRIDA DE VALUACION
--
--  `valuacion_corrida.corrida_id` de `V5` de `rentas` es `NOT NULL`: el receptor
--  quiere saber CUAL corrida dejo el cierre que tiene puesto, porque el cierre se
--  sustituye. Aqui no hay tabla de corridas —el buzon ya guarda el cierre entero y
--  congelado (ver la cabecera)— asi que lo unico que hace falta es un identificador
--  que no se repita.
--
--  Una secuencia y no un `max(...) + 1`: dos corridas simultaneas leerian el mismo
--  maximo y se darian el mismo numero, y entonces dos cierres distintos del mismo
--  ejercicio tendrian la misma identidad. Es el hueco que #44 midio con
--  `siguienteCorrelativo` y `count(*) + 1`.
--
--  NO lleva RLS y no puede llevarla: una secuencia no es una tabla. Y no hace falta,
--  porque el numero no dice nada de nadie — lo unico que se le pide es no repetirse.
-- ----------------------------------------------------------------------------

CREATE SEQUENCE catastro_corrida_seq AS bigint START WITH 1 INCREMENT BY 1;

GRANT USAGE, SELECT ON SEQUENCE catastro_corrida_seq TO sgtm_app;

COMMENT ON SEQUENCE catastro_corrida_seq IS
    'La identidad de una corrida de valuacion (C-8). Viaja en el cierre y el receptor la guarda en '
    '`valuacion_corrida.corrida_id` para poder decir cual de dos cierres del mismo ejercicio es el '
    'que esta puesto.';
