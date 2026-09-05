# ADR-0034 — Toda tabla de tenant con geometría lleva su marco, y el operador espacial no entra en el SQL de aplicación

| Campo | Valor |
|---|---|
| Estado | Propuesto |
| Fecha | 2026-09-05 |
| Decide | Dirección del proyecto |
| Generaliza | [ADR-0021](ADR-0021-la-geometria-del-predio.md) y el quinto hallazgo de [`hallazgos-de-rls.md`](../../40-datos/hallazgos-de-rls.md) |

## Contexto

`V65` resolvió para `predio` un defecto del motor: bajo RLS, `geography_overlaps` no es *leakproof*,
no se promueve por encima de la política y el índice GiST no sirve al rol de la aplicación. La
mitigación —cuatro columnas generadas con el marco, en `double precision`, comparadas con `<=` y
`>=`— funciona.

El problema no es `predio`: es que **el defecto se repite en silencio en cada tabla nueva con
geometría**, porque la consulta da el resultado correcto y el plan sigue diciendo «Index». Con
`zonificacion`, `zona_riesgo`, `faja_marginal`, `frente_predio`, `candidato`, `hallazgo`,
`intervencion`, `establecimiento` y `bien_municipal` en camino, son nueve oportunidades de volver a
descubrir el mismo hallazgo.

**Medido de nuevo antes de decidir**, con 90 000 predios en tres municipalidades sobre PostgreSQL 16
y PostGIS 3.4, rol `NOSUPERUSER NOBYPASSRLS`, tesela z16, mediana de siete corridas:

| Variante | Bloques | ms | ¿El filtro en el `Index Cond`? |
|---|---|---|---|
| `geography &&` con GiST simple | 4 530 | 12,81 | no |
| `geography &&` con GiST multicolumna `(municipalidad_id, geometria)` | 4 530 | 12,56 | no |
| `geometry &&` con GiST simple | 4 530 | 14,82 | no |
| `geometry &&` con GiST multicolumna | 4 530 | 14,64 | no |
| `ST_Intersects(geometry)` | 4 530 | 63,20 | no |
| celda Morton + escalera de ancestros | 474 | 2,08 | sí |
| **`marco_*` ×4 (V65)** | **347** | **1,32** | **sí** |
| sin RLS, superusuario — el techo | 306 | 1,39 | sí |

Dos resultados que conviene no volver a descubrir:

- **Migrar de `geography` a `geometry` no arregla nada.** `geometry_overlaps` tampoco es
  *leakproof*. ADR-0021 no hay que revisarlo por este motivo, y ahora tiene una razón más para
  quedarse: cambiar el tipo no compra el índice y cuesta un 15 % más.
- **Un GiST multicolumna con `btree_gist` tampoco.** La barrera no es que falte un índice capaz de
  servir a las dos condiciones: es que una condición de nivel de seguridad superior no se promueve
  por encima de la política aunque el mismo índice pudiera resolverla.

Y `marco_*` está **dentro del ruido del techo sin RLS**: 347 bloques contra 306.

## Decisión

1. **Toda tabla de tenant con columna `geography` lleva las cuatro columnas generadas
   `marco_oeste`, `marco_sur`, `marco_este`, `marco_norte` en `double precision`, y el índice
   `(municipalidad_id, marco_oeste, marco_sur, marco_este, marco_norte)`.** `numeric` no vale:
   `numeric_le` tampoco es *leakproof*, y las columnas dejarían de servir sin que nada se ponga
   rojo. El índice GiST espacial se conserva, para el trabajo que corre fuera de RLS.

2. **Ningún SQL de aplicación usa `&&`, `ST_Intersects` ni ningún operador espacial como condición
   principal contra una tabla de tenant.** Se escribe con el marco. El operador espacial se admite
   solo como refinado exacto después del marco, y únicamente cuando la respuesta lo exija —para una
   tesela no lo exige: la caja es lo que se dibuja—.

3. **Las dos cosas las vigila `comun-verificaciones`**, junto al escáner de `SET SESSION`: una
   comprobación de esquema que exige el marco y su índice en toda tabla con `geography`, y un
   escáner de fuentes que rechaza el operador espacial en SQL de aplicación. Y cada una viaja con
   su clase de muestra que la viola, como exige `ReglasDeArquitecturaMuerdenTest`.

## Lo que esta decisión NO hace

- **No promete que el plan sea siempre el bueno.** La sobreestimación que el quinto hallazgo
  documenta sigue ahí: el planificador trata las cuatro desigualdades como independientes y son un
  rectángulo (estima 2 413 filas donde hay 1 204). Con más de una municipalidad el índice gana
  solo; **una instalación de una sola municipalidad grande merece su propia medición.**
- **No toca `ALTER FUNCTION … LEAKPROOF`.** Sigue descartada por lo que ya está escrito: es un acto
  de superusuario que no cabe en una migración, y afirmar que una función en C de un tercero no
  puede revelar la fila de otra municipalidad es una afirmación que nadie verificó.

## Alternativas descartadas

- **Celda Morton (quadkey) con escalera de ancestros.** Convierte el filtro espacial en un rango
  único sobre `bigint` —*leakproof* y bien estimado—, y es correcto. Medido: 474 bloques contra 347,
  y en la tesela completa con `ST_AsMVT` pierde 7 792 contra 1 120, porque necesita una rama aparte
  para los ancestros y el `JOIN` de vuelta se come la ventaja.
- **Una función `SECURITY DEFINER` sobre un rol con `BYPASSRLS`.** Funciona y cuesta 1 200 bloques.
  Y mueve el aislamiento del motor al SQL que uno escribe, que es justamente lo que ADR-0002 evita.
  La segunda razón basta sola.
