# ADR-0021 — La base modela la geometría del predio

| Campo | Valor |
|---|---|
| Estado | Aceptado |
| Fecha | 2026-08-30 |
| Decide | Dirección del proyecto |
| Revierte | La premisa de `importar_arancel_via_gpkg.py`: «no se carga en la base (**la base no modela geometría**)» |
| Implementa | `V61__geometria_del_predio.sql`, issue [#400](https://github.com/hneyra/sgtm/issues/400) |

## Decisión

`predio` gana una columna **`geometria geography(MultiPolygon, 4326)`**, nula, con índice GiST. La
extensión **PostGIS** se instala en el aprovisionamiento del motor, junto a `pg_trgm` y `unaccent`,
y la imagen de PostgreSQL pasa de `postgres:16-alpine` a `postgis/postgis:16-3.4-alpine`.

El alta de predios de una municipalidad real es una **importación cartográfica**, no un formulario:
el lote existe en el plano antes que en el padrón. Hasta aquí el sistema sabía cargar el arancel de
un GeoPackage tirando su geometría —el guion del MEF lo dice de sí mismo— y el polígono se perdía.
Con la columna, el predio que entra del plano conserva de dónde salió.

## Lo que la geometría NO hace, y es la mitad de esta decisión

**No valoriza.** `area_terreno` es la de la ficha: lo que midió el técnico y lo que declaró el
contribuyente. Derivarla del polígono cambiaría el autovalúo de **todo el padrón** sin que nadie lo
haya decidido, y el error sería invisible: una cifra de área es indistinguible de otra al leerla.

Que las dos áreas no coincidan es un **hallazgo que se informa**, no una corrección que se aplica.
Es la misma frontera que [ADR-0015](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0015-conciliacion-catastro-rentas.md) puso entre catastro y
rentas: el sistema dice que no cuadran y quién decide es una persona, con su acto y su observación.

Tampoco hace un visor de mapas, ni topología (que dos lotes no se solapen), ni geocodificación.
Nada de eso está en este ADR; lo que hay es una columna, su índice y el camino que la llena.

## Por qué `geography(…, 4326)` y no `geometry(…, <UTM>)`

Porque **una instalación atiende a muchas municipalidades**, y ésa es la restricción que define este
proyecto. El Perú abarca las zonas UTM 17S, 18S y 19S: una columna `geometry` con un SRID fijo
obliga a elegir una zona que es la equivocada para parte de los inquilinos, y un SRID por
municipalidad no se puede expresar en una columna tipada.

`geography` mide sobre el elipsoide, en metros, sin elegir zona. Cuesta algo de rendimiento y tiene
menos operadores que `geometry`; a cambio, ninguna municipalidad queda con sus predios en la
proyección de otra. Lo que se necesita —guardar, indizar, saber si un punto cae dentro, dar un área
informativa— lo cubre entero.

**MultiPolygon y no Polygon**: un predio puede tener partes disjuntas, y las capas catastrales en
GeoPackage se publican así de ordinario. Aceptar sólo `Polygon` rechazaría filas legítimas en la
carga y obligaría a partirlas, que es inventar predios.

**Nula, y sin plan de dejar de serlo.** Todos los predios de hoy no la tienen, y muchos no la
tendrán nunca: un predio declarado en ventanilla no trae plano. Exigirla convertiría la inscripción
de una ficha en una tarea de gabinete.

## Consecuencias

- **La imagen del motor cambia en los tres ambientes.** `postgis/postgis:16-3.4-alpine` es la misma
  PostgreSQL 16 con extensiones añadidas: el volumen de datos es compatible y no hay migración de
  datos. Se declara aquí y se aplica cuando la dirección lo decida, no como efecto secundario de un
  despliegue.

  **Hoy `stg` y `prod` sólo tienen datos de prueba** —confirmado por la dirección el 2026-08-30—,
  así que el camino más simple y mejor probado es **rehacer el volumen**: con el directorio de datos
  vacío, `crear-roles.sql` vuelve a correr entero y crea la extensión por el mismo camino que CI
  ejercita en cada PR. Eso deja de valer en cuanto haya un padrón real, y para ese día está lo que
  dice el punto siguiente.
- **Sobre un volumen que se conserva, la extensión no llega sola y la migración se cae.**
  `crear-roles.sql` corre desde `/docker-entrypoint-initdb.d`, o sea **una sola vez y con
  el volumen vacío**. En `stg` y `prod` no volverá a ejecutarse, así que `postgis` no
  estará creada y `V61` fallará con `type "geography" does not exist`. Y no lo arregla el
  migrador: `postgis` **no es una extensión *trusted*** —`SELECT trusted FROM
  pg_available_extension_versions WHERE name='postgis'` da `f`—, de modo que crearla
  exige un superusuario y `sgtm_owner` a propósito no lo es. CI nunca lo ve porque
  siempre parte de un volumen vacío, así que sale verde en todas partes y rojo la primera
  vez que alguien despliegue conservando los datos. Para eso está
  [`despliegue/crear-extensiones.sh`](https://github.com/hneyra/infrastructure/blob/main/despliegue/crear-extensiones.sh), que lleva
  al motor en marcha lo que el archivo declara y es idempotente —mismo hueco y misma
  forma que `asignar-claves.sh` en #435—. **Mientras haya sólo datos de prueba no hace
  falta: rehacer el volumen es más simple.** Lo que sí conviene correr siempre es
  `verificar-el-ambiente.sh`, que dice en cuál de las dos situaciones está el ambiente.
- **`spatial_ref_sys` aparece en el esquema `public`.** La crea la extensión y es un catálogo de
  sistemas de coordenadas: no lleva ni puede llevar dato municipal. Entra en `TABLAS_EXENTAS` de la
  prueba de aislamiento, que es donde tiene que verse.
- **`sgtm_app` no escribe la geometría por HTTP.** Ninguna operación del contrato la recibe: entra
  por la carga cartográfica, que es un proceso `batch`. Corregir un polígono a mano en una pantalla
  es dibujar, y para eso hace falta un editor que no existe.
- El índice GiST se paga en cada escritura del predio. Se acepta: sin él, «qué predios caen en esta
  manzana» recorre la tabla entera, que es la única pregunta por la que la columna existe.

  > **Medido en #536, y esta frase resultó ser falsa para quien hace esa pregunta.** El índice GiST
  > **no se usa** cuando quien consulta es `sgtm_app`: `geography_overlaps` no es *leakproof*, así
  > que bajo RLS PostgreSQL no lo promueve por encima de la política y el plan vuelve a ser un
  > `Seq Scan` — con el índice ahí, y usándose sólo si quien mide es un superusuario. Es el hallazgo
  > 3 de [`DAT-01`](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/docs/40-datos/modelo-logico-fisico.md) §0 con otro operador, y su §0 hallazgo 5
  > lo recoge con los dos planes. Lo que responde esa pregunta desde `V65` son cuatro columnas
  > generadas con el marco del lote, comparadas con operadores que sí lo son. El índice GiST se
  > conserva —retirarlo es revisar esta decisión, no un efecto secundario de publicar una lectura—
  > y sigue sirviendo a cualquier trabajo espacial que corra fuera de RLS.

## Alternativas descartadas

**Guardar el WKB en una columna de texto, sin PostGIS.** Evita la extensión y el cambio de imagen, y
deja la geometría como un dato opaco: no se puede indizar, ni consultar, ni comprobar que lo
guardado sea un polígono válido. Es la forma de tener el coste del dato sin ninguna de sus ventajas.

**Dejar la geometría fuera de la base, en los CSV de trazabilidad.** Es lo que hay hoy, y es lo que
este ADR cambia: el `arancel_<anio>_detalle.csv` del guion del MEF llega hasta el `fid` del gpkg
fuente y ahí se queda. Sirve para volver a la fuente y no sirve para responder ninguna pregunta del
sistema.

**Una tabla `predio_geometria` aparte.** Aísla PostGIS de la tabla caliente, y a cambio mete un
`JOIN` en toda consulta que quiera el polígono y una fila que se puede quedar huérfana. La geometría
es un atributo del predio, uno por predio, y su sitio es la fila del predio.
