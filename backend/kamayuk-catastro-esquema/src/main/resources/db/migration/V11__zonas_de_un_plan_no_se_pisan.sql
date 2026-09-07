-- ============================================================================
--  V11 — DOS ZONAS DEL MISMO PLAN NO PUEDEN CUBRIR EL MISMO SUELO (#22)
--
--  QUE FALTABA, MEDIDO Y NO SUPUESTO
--  ---------------------------------
--  `V7` trajo `zonificacion_planes_no_se_pisan`, que lleva `plan WITH <>`: una
--  restriccion de exclusion conflictua cuando TODOS sus operadores se satisfacen,
--  asi que dos filas con el MISMO plan no chocan nunca, se solapen como se solapen.
--  Lo que impide es que dos PLANES se pisen; dentro de un plan no impide nada.
--
--  `zonificacion_codigo_uq` tampoco: es (municipalidad_id, plan, codigo,
--  vigencia_desde), y dos zonas solapadas tienen codigos distintos por definicion.
--
--  Reproducido contra PostgreSQL 16.13 + PostGIS 3.4.2: tres zonas del mismo plan
--  sobre el mismo punto ENTRAN LAS TRES. Y entonces pasa exactamente lo que la
--  cabecera de `V7` escribio del defecto que su restriccion existe para impedir:
--
--      «entonces "la zona de este predio" tiene dos respuestas y la licencia se
--       concede o se niega segun cual lea la consulta»
--
--  POR QUE ESTO **NO** ES UNA RESTRICCION DE EXCLUSION, Y SE MIDIO ANTES DE DECIDIR
--  -------------------------------------------------------------------------------
--  La forma que se escribe sola es la simetrica de la de `V7`:
--
--      EXCLUDE USING gist (municipalidad_id WITH =, plan WITH =, codigo WITH <>,
--                          geometria WITH &&, daterange(...) WITH &&)
--
--  **No sirve, y es el mismo hallazgo que le costo a `V7` su primera forma, por el
--  otro eje.** `&&` sobre `geography` compara CAJAS ENVOLVENTES, y dos zonas
--  ADYACENTES del mismo plan —que es como se dibuja un plan: el distrito entero,
--  partido— tienen cajas que se tocan. Medido el 2026-09-07 con esa restriccion
--  puesta sobre la tabla de verdad, insertando dos rectangulos que solo comparten
--  su arista:
--
--      ERROR:  conflicting key value violates exclusion constraint
--              "candidata_con_cajas"
--
--  O sea que la candidata rechaza LAS DOS COSAS: el solape que hay que impedir y la
--  adyacencia que hay que dejar pasar. Con ella no se podria cargar ningun plan.
--
--  Y no hay una tercera forma de exclusion: el operador que hace falta —«los
--  INTERIORES se cortan»— no es un operador indexable por GiST, asi que no se puede
--  escribir en un `EXCLUDE`. Medido sobre las tres relaciones que importan:
--
--      caso                       &&    ST_Intersects   ST_Relate(a,b,'T********')
--      ADYACENTE (solo arista)    t     t               f
--      SOLAPADA de verdad         t     t               t
--      CONTENIDA del todo         t     t               t
--
--  `&&` y `ST_Intersects` valen `t` en los tres: ninguno de los dos distingue «son
--  vecinas» de «se pisan», que es la unica distincion que aqui decide. El patron
--  DE-9IM `'T********'` —la primera casilla es interior contra interior— si.
--
--  Asi que la restriccion se apoya en el operador EXACTO, y eso obliga a un
--  disparador de restriccion. **Sigue siendo el motor quien lo impide**, que es lo
--  que importa: este dato entra por un cargador, y una comprobacion escrita en Java
--  se la salta el `INSERT` siguiente.
--
--  EL MARCO VA DELANTE (ADR-0034 regla 2), TAMBIEN AQUI
--  ----------------------------------------------------
--  La consulta del disparador filtra por las cuatro columnas de marco ANTES del
--  predicado exacto. `zonificacion` lleva RLS con `FORCE` y este disparador **no es
--  `SECURITY DEFINER`**: corre con el rol y el contexto de quien escribe, asi que la
--  politica se le aplica y `ST_Relate` —que no es *leakproof*— no se promueve por
--  encima de ella. Sin el marco delante, cada alta de zona recorreria la
--  zonificacion entera del inquilino.
--
--  DEFERRABLE INITIALLY DEFERRED, POR LO MISMO QUE LA DE `V7`
--  ---------------------------------------------------------
--  Corregir el dibujo de una zona atraviesa un estado intermedio solapado —se abre
--  la nueva y se cierra la vieja, en dos sentencias—, y sin el diferimiento la
--  primera de las dos fallaria. Es la misma decision que `ficha_vigencias_no_se_pisan`
--  y que `zonificacion_planes_no_se_pisan`.
--
--  LO QUE ESTA MIGRACION NO TOCA
--  -----------------------------
--  NO toca `zonificacion_planes_no_se_pisan`: hace lo que promete y sus tres formas
--  estan medidas desde `V7`. Lo que faltaba es la restriccion de DENTRO de un plan,
--  no cambiar aquella. Y no crea ninguna tabla, asi que el reparto de la regla 11 no
--  se mueve.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.zonas_del_plan_no_se_pisan()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
DECLARE
    v_otra character varying(20);
BEGIN
    SELECT z.codigo
      INTO v_otra
      FROM public.zonificacion z
     WHERE z.municipalidad_id = NEW.municipalidad_id
       AND z.plan = NEW.plan
       AND z.id <> NEW.id
       -- ADR-0034 regla 2: las cuatro del marco primero —son leakproof y llegan a
       -- `zonificacion_marco_ix`—, y el predicado exacto detras, como refinado.
       AND z.marco_oeste <= NEW.marco_este
       AND z.marco_sur   <= NEW.marco_norte
       AND z.marco_este  >= NEW.marco_oeste
       AND z.marco_norte >= NEW.marco_sur
       AND daterange(z.vigencia_desde, COALESCE(z.vigencia_hasta, 'infinity'::date), '[]')
           && daterange(NEW.vigencia_desde, COALESCE(NEW.vigencia_hasta, 'infinity'::date), '[]')
       -- «Los interiores se cortan», y no `ST_Intersects`: dos zonas vecinas SE TOCAN
       -- y eso es como se dibuja un plan. Cualificado con `public.` por lo que midio
       -- `V4`: pg_dump vacia el search_path al restaurar.
       AND public.ST_Relate(
               CAST(z.geometria AS geometry), CAST(NEW.geometria AS geometry), 'T********')
     LIMIT 1;

    IF v_otra IS NOT NULL THEN
        RAISE EXCEPTION
            'Las zonas % y % del plan % cubren el mismo suelo a la vez: «la zona de este'
            ' predio» tendria dos respuestas y la licencia se concederia o se negaria segun'
            ' cual leyera la consulta. Dos zonas del mismo plan pueden ser VECINAS; no pueden'
            ' solaparse (zonas_del_plan_no_se_pisan, #22)',
            NEW.codigo, v_otra, NEW.plan
            USING ERRCODE = 'exclusion_violation';
    END IF;

    RETURN NULL;
END;
$function$
;

COMMENT ON FUNCTION public.zonas_del_plan_no_se_pisan() IS
    'La mitad de la unicidad de la zona que `zonificacion_planes_no_se_pisan` NO cubre: su '
    '«plan WITH <>» impide que dos PLANES se pisen y deja pasar cualquier solape DENTRO de '
    'un plan. No es una restriccion de exclusion porque el operador que hace falta —los '
    'interiores se cortan, DE-9IM T********— no es indexable por GiST, y «&&» rechazaria '
    'ademas las zonas adyacentes, que es como se dibuja un plan (ver la cabecera de V11)';

CREATE CONSTRAINT TRIGGER zonas_del_plan_no_se_pisan_trg
    AFTER INSERT OR UPDATE ON public.zonificacion
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION public.zonas_del_plan_no_se_pisan();
