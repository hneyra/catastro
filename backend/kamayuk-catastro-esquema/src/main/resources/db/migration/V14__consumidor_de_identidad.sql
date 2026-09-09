-- ============================================================================
--  V14 — EL CONSUMIDOR DEL BUZON DE `identidad` (etapa 4 de infrastructure#52,
--        ADR-0039, identidad#4)
--
--  QUE CIERRA
--  ----------
--  Desde C-7 la copia local de usuarios, grupos y permisos de este sistema la escribe
--  LA IMPLANTACION y nadie mas: un permiso concedido en `identidad` despues de eso NO
--  LLEGABA, y eso estaba declarado como hueco en `package-info.java` de `seguridad`
--  con todas las letras. Esta migracion trae las dos tablas que un consumidor de
--  buzon necesita para poder ser idempotente y para no bloquearse: lo que YA aplico y
--  lo que NO PODRA aplicar nunca. Es la misma forma que `rentas` construyo para el
--  buzon de `catastro` (`V4` y `V12` suyas), leida desde este lado.
--
--  Las cuatro tablas que el consumidor ESCRIBE —`usuario`, `grupo`, `miembro` y
--  `permiso`— ya estan en el baseline y `kamayuk_app` ya tiene INSERT, SELECT y UPDATE
--  sobre ellas: no hace falta ni rol ni GRANT nuevo para aplicar, y por eso esta
--  migracion no los toca. Lo unico nuevo es lo que aqui se crea.
--
--  LOS DOS FALLOS NO SON EL MISMO, Y SOLO UNO LLEGA A `identidad_evento_muerto`
--  ------------------------------------------------------------------------------
--    NO SE PUDO AHORA   la base no contesta, o el evento nombra un grupo o una cuenta
--                       que todavia no ha llegado. NO se acusa y NO se escribe nada
--                       aqui: la vuelta siguiente lo vuelve a intentar, porque el
--                       emisor lo sigue teniendo pendiente.
--
--    NO SE PUEDE NUNCA  el cuerpo no se puede leer, el tipo no se conoce, o el emisor
--                       esta reescribiendo un evento ya aplicado con otra huella.
--                       Ninguno cambia solo. Se escribe aqui, SE ACUSA para que deje
--                       de servirse, y se avisa a una persona con nombre (ADR-0026 §4).
--
--  `cuerpo` ES `text` Y NO `jsonb`, A PROPOSITO (la leccion de `V12` de `rentas`): una
--  de las causas de muerte es precisamente que el cuerpo NO SEA JSON, y con `jsonb` la
--  unica tabla que existe para guardar lo que no se pudo leer no podria guardarlo.
--
--  NO SE BORRA NINGUNA FILA (regla 4, RNF-051). Un evento muerto se EXPLICA.
-- ============================================================================

-- ── 1. LO APLICADO: la memoria que hace idempotente al consumidor ────────────

CREATE TABLE identidad_evento_aplicado (
    municipalidad_id bigint      NOT NULL REFERENCES municipalidad (id),
    evento_id        uuid        NOT NULL,
    secuencia        bigint      NOT NULL,
    tipo             varchar(40) NOT NULL,
    sujeto_id        bigint      NOT NULL,
    -- La huella con la que el emisor firmo el evento, copiada y no recalculada. Es lo
    -- que permite distinguir «me sirvieron dos veces el mismo evento» —se descarta—
    -- de «me sirvieron otro evento con el mismo identificador» —no se aplica nunca—.
    huella           char(64)    NOT NULL,
    aplicado_en      timestamptz NOT NULL,
    CONSTRAINT identidad_evento_aplicado_pk PRIMARY KEY (municipalidad_id, evento_id),
    CONSTRAINT identidad_evento_aplicado_secuencia_ck CHECK (secuencia >= 0)
);

CREATE INDEX identidad_evento_aplicado_secuencia_ix
    ON identidad_evento_aplicado (municipalidad_id, secuencia DESC);

COMMENT ON TABLE identidad_evento_aplicado IS
    'Una fila por evento de `identidad` APLICADO a la copia local de la autorizacion (ADR-0039, '
    'etapa 4). El consumidor la escribe ANTES de tocar `usuario`, `grupo`, `miembro` o `permiso`, '
    'en la MISMA transaccion, y por eso reprocesar la cola no duplica nada. No se borra ninguna '
    'fila (regla 4): es lo unico que contesta «por que esta copia dice esto».';

-- ── 2. LO QUE NO SE PUEDE APLICAR NUNCA ──────────────────────────────────────

CREATE TABLE identidad_evento_muerto (
    municipalidad_id bigint       NOT NULL REFERENCES municipalidad (id),
    evento_id        uuid         NOT NULL,
    secuencia        bigint       NOT NULL,
    tipo             varchar(40)  NOT NULL,
    sujeto_id        bigint       NOT NULL,
    cuerpo           text         NOT NULL,
    huella           char(64)     NOT NULL,
    motivo           varchar(400) NOT NULL,
    recibido_en      timestamptz  NOT NULL,
    explicacion      varchar(400),
    explicado_en     timestamptz,

    CONSTRAINT identidad_evento_muerto_pk PRIMARY KEY (municipalidad_id, evento_id),
    CONSTRAINT identidad_evento_muerto_secuencia_ck CHECK (secuencia >= 0),
    -- Un motivo en blanco es un muerto sin causa.
    CONSTRAINT identidad_evento_muerto_motivo_ck CHECK (length(btrim(motivo)) >= 5),
    -- Explicado es un hecho CON HORA y CON TEXTO, o no lo es.
    CONSTRAINT identidad_evento_muerto_explicacion_ck CHECK (
        (explicacion IS NULL AND explicado_en IS NULL)
        OR (explicado_en IS NOT NULL AND length(btrim(explicacion)) >= 5))
);

CREATE INDEX identidad_evento_muerto_sin_explicar_ix
    ON identidad_evento_muerto (municipalidad_id, recibido_en)
    WHERE explicacion IS NULL;

COMMENT ON TABLE identidad_evento_muerto IS
    'Los eventos de `identidad` que este sistema NO PUEDE aplicar (ADR-0039 etapa 4, ADR-0026 §4). '
    'Solo llegan aqui los fallos permanentes: los transitorios no se acusan y se reintentan solos. '
    'Cada fila dispara una alerta a una persona con nombre, porque mientras este aqui la copia '
    'local de la autorizacion dice algo que `identidad` ya no dice y ninguna pantalla lo delata: '
    'un permiso que no llego se ve como un 403 a alguien que lo tiene.';
COMMENT ON COLUMN identidad_evento_muerto.cuerpo IS
    'El evento entero tal como llego, y es `text` Y NO `jsonb` A PROPOSITO: una de las causas de '
    'muerte es que el cuerpo NO SEA JSON, y con `jsonb` el INSERT que lo guarda fallaria con '
    '«invalid input syntax for type json». Se guarda porque es lo unico que permite volver a '
    'aplicarlo a mano una vez arreglada la causa: el emisor ya lo dio por entregado.';
COMMENT ON COLUMN identidad_evento_muerto.motivo IS
    'Por que no se pudo aplicar, en las palabras del consumidor. Separa «el cuerpo no se puede '
    'leer» de «el emisor reescribio un evento ya aplicado», que se arreglan de maneras distintas.';
COMMENT ON COLUMN identidad_evento_muerto.explicacion IS
    'Quien se hizo cargo y que hizo. Nulo mientras nadie lo haya mirado. No hay DELETE (regla 4): '
    'un evento que no se pudo aplicar se explica, no se borra.';

-- ── 3. RLS. Sin valor por omision: sin contexto de tenant, la consulta FALLA ─

ALTER TABLE identidad_evento_aplicado ENABLE ROW LEVEL SECURITY;
ALTER TABLE identidad_evento_aplicado FORCE ROW LEVEL SECURITY;
CREATE POLICY identidad_evento_aplicado_tenant ON identidad_evento_aplicado FOR ALL TO PUBLIC
    USING (municipalidad_id = current_setting('app.municipalidad_id')::bigint)
    WITH CHECK (municipalidad_id = current_setting('app.municipalidad_id')::bigint);

ALTER TABLE identidad_evento_muerto ENABLE ROW LEVEL SECURITY;
ALTER TABLE identidad_evento_muerto FORCE ROW LEVEL SECURITY;
CREATE POLICY identidad_evento_muerto_tenant ON identidad_evento_muerto FOR ALL TO PUBLIC
    USING (municipalidad_id = current_setting('app.municipalidad_id')::bigint)
    WITH CHECK (municipalidad_id = current_setting('app.municipalidad_id')::bigint);

-- ── 4. Privilegios ──────────────────────────────────────────────────────────
--
--  Quien escribe es el consumidor, y corre como `kamayuk_app` —basta, porque las
--  cuatro tablas de la autorizacion ya le dan INSERT, SELECT y UPDATE—. Lo aplicado
--  solo se inserta: una fila que dice «esto se aplico» no se corrige. Lo muerto se
--  inserta y se EXPLICA encima, que es un UPDATE. Ninguna de las dos recibe DELETE, y
--  no lo van a recibir (regla 4).

GRANT INSERT, SELECT         ON identidad_evento_aplicado TO kamayuk_app;
GRANT SELECT                 ON identidad_evento_aplicado TO kamayuk_readonly;
GRANT INSERT, SELECT, UPDATE ON identidad_evento_muerto   TO kamayuk_app;
GRANT SELECT                 ON identidad_evento_muerto   TO kamayuk_readonly;
