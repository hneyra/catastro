-- ============================================================================
--  V3 — La guarda del arancel, reconstruida (P5C; hueco 3 de P5B)
--
--  QUE SE PERDIO Y CUANDO
--  ----------------------
--  En el monolito, `V18` puso sobre `arancel` el disparador
--  `arancel_de_conjunto_sellado_inmutable`, que rechazaba insertar o modificar un arancel
--  cuyo conjunto de parametros ya estuviera SELLADO. Es la reproducibilidad de ADR-0007
--  aplicada al valor del terreno: una determinacion emitida contra un conjunto tiene que
--  poder recalcularse con las mismas cifras diez anios despues, y un arancel que se cuela
--  en un conjunto ya usado cambia el autovaluo de todo un padron sin que ninguna cifra
--  parezca mal.
--
--  Ese disparador consultaba `conjunto_parametros`. En P5B esa tabla se fue a `normativa`,
--  asi que `V2` de `rentas` retiro el disparador Y su funcion —una funcion que consulta una
--  tabla inexistente no protege nada: revienta en el primer INSERT— y lo dejo anotado como
--  hueco 3: «Hoy nada impide cargar un arancel contra un conjunto ya sellado. Hay que
--  reconstruirlo en `catastro` (P5C)». Entre P5B y esta migracion, eso es exactamente lo que
--  pasaba.
--
--  CONTRA QUE SE COMPRUEBA AHORA
--  -----------------------------
--  Contra `normativa_conjunto`, la copia local de conjuntos sellados que crea `V2`. La
--  equivalencia no es una suposicion, es una propiedad del contrato de ADR-0025 §1:
--  **`normativa` no sirve un conjunto abierto**. `GET /conjuntos/{id}/snapshot` contesta 404
--  mientras no este sellado, asi que una fila en la cache SIGNIFICA «este conjunto esta
--  sellado». No hace falta guardar un estado que solo puede tener un valor, y por eso
--  `normativa_conjunto` no tiene columna `estado`.
--
--  LO QUE ESTA GUARDA VE MENOS QUE LA DE V18, DICHO AQUI
--  ----------------------------------------------------
--  Tres cosas, y ninguna se descubre mas tarde:
--
--  1. **Un conjunto que esta sellado en `normativa` y que esta base todavia no ha
--     descargado NO se detecta.** La guarda solo puede hablar de lo que ve. En la practica
--     el orden es el contrario —se descarga para poder calcular, y se calcula despues de
--     sellar—, pero el hueco existe y es real: la unica forma de cerrarlo seria preguntar
--     por HTTP desde un disparador, que es peor que el problema.
--  2. **Un `conjunto_id` que no existe en ninguna parte ya no lo rechaza nadie.** Antes lo
--     paraba `arancel_conjunto_fk`; esa clave foranea la retiro el propio generador del
--     baseline —esta comentada en `V1` como `[CRUZA LA FRONTERA]`— porque no hay tabla a la
--     que apuntar. Es literalmente el costo que ADR-0029 nombra: «se paga una clave foranea
--     por una invariante».
--  3. Sigue sin comprobar que la via sea del mismo conjunto, porque el arancel no cuelga de
--     la via por conjunto: `arancel_uq` es (municipalidad, conjunto, via, tramo).
--
--  POR QUE UN DISPARADOR Y NO UN `if` EN JAVA
--  ------------------------------------------
--  Por lo que #188 midio y quedo escrito en el javadoc de `PublicarParametros`: quitar la
--  guarda de Java dejaba once pruebas en VERDE porque quien rechazaba de verdad era la
--  restriccion de la base. Aqui es lo mismo: `ValuacionRepository#guardarArancel` documenta
--  a proposito que «inserta sin comprobar el estado del conjunto: esa comprobacion no vive
--  en Java». Si la guarda fuera un `if`, la carga masiva por `rol_carga_parametros` o
--  cualquier ruta futura la esquivaria sin ruido.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.arancel_de_conjunto_sellado_es_inmutable()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
DECLARE
    v_conjunto bigint;
    v_muni     bigint;
BEGIN
    v_conjunto := COALESCE(NEW.conjunto_id, OLD.conjunto_id);
    v_muni     := COALESCE(NEW.municipalidad_id, OLD.municipalidad_id);

    -- Una fila en la cache es un conjunto SELLADO: `normativa` no sirve otra cosa
    -- (ADR-0025 §1).
    --
    -- El `ambito` se IGNORA a proposito: el conjunto se descarga en dos mitades (VALUACION y
    -- OBLIGACION) y basta una para saber que esta sellado. Mirarlo dejaria entrar un arancel
    -- mientras solo estuviera descargada la otra mitad, que es exactamente cuando el conjunto
    -- ya esta sellado. Eso SI tiene una prueba que lo caza: anadir `AND c.ambito = ...` deja en
    -- rojo las dos pruebas del rechazo.
    --
    -- `municipalidad_id` va en el WHERE porque `conjunto_parametros` es de tenant en
    -- `normativa` —cada municipalidad abre y sella el suyo, y un `conjunto_id` solo es unico
    -- dentro de su municipalidad—, PERO SE MIDIO Y ES REDUNDANTE POR EL CAMINO NORMAL, y
    -- conviene decirlo en vez de dejar creer que protege algo que no puede fallar: quitarlo
    -- deja las cinco pruebas de `GuardaDelArancelTest` en VERDE. El motivo es que
    -- `normativa_conjunto` lleva RLS con `FORCE` (V2) y este disparador NO es `SECURITY
    -- DEFINER`, asi que corre con el rol y el contexto de quien escribe: la fila de la vecina
    -- no la ve. La clausula solo cambia algo para una conexion que omita RLS —un superusuario—,
    -- y ahi no hay prueba que la ejercite porque un superusuario tampoco deberia escribir
    -- aranceles. Se conserva por eso y no por costumbre.
    IF EXISTS (
        SELECT 1
          FROM normativa_conjunto c
         WHERE c.municipalidad_id = v_muni
           AND c.conjunto_id = v_conjunto
    ) THEN
        RAISE EXCEPTION
            'El conjunto de parametros % esta sellado: su contenido no cambia, y el arancel'
            ' de terreno es contenido suyo. Corregir un arancel es publicar otra version del'
            ' conjunto, no editar la que ya se uso para determinar (ADR-0007)',
            v_conjunto
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$function$
;

COMMENT ON FUNCTION public.arancel_de_conjunto_sellado_es_inmutable() IS
    'Reconstruccion en `catastro` de la guarda que `V18` tenia contra `conjunto_parametros` '
    '(P5C, hueco 3 de P5B). Comprueba contra la copia local de conjuntos sellados, que es lo '
    'unico que este sistema sabe del sellado. Lo que ve MENOS que la original esta escrito en '
    'la cabecera de V3, no en un issue';

CREATE TRIGGER arancel_de_conjunto_sellado_inmutable
    BEFORE INSERT OR UPDATE ON public.arancel
    FOR EACH ROW EXECUTE FUNCTION arancel_de_conjunto_sellado_es_inmutable();
