# ADR-0037 — Dos carriles de mapa: lo publicado se tesela, lo vivo se sirve

| Campo | Valor |
|---|---|
| Estado | Propuesto |
| Fecha | 2026-09-05 |
| Decide | Dirección del proyecto |
| Extiende | [ADR-0022](ADR-0022-el-visor-del-plano-catastral.md), sin revertirlo |
| Depende de | [ADR-0034](ADR-0034-el-marco-y-el-operador-espacial.md), [ADR-0030](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0030-cuatro-interfaces-una-sesion.md) |

## Contexto

ADR-0022 publica GeoJSON sin reproyectar, sin simplificar y sin teselar, y niega con 422 si el marco
excede un tope. Es correcto para un visor que aún no existe y no compromete nada.

Su límite no es la lectura —ADR-0034 la resuelve en 1,3 ms— sino **lo que viaja**: 30 000 lotes en
GeoJSON son decenas de megabytes, y el 422 lo único que consigue es decirle al usuario que no puede
ver su distrito. Con el territorio entero encima (zonificación, riesgo, licencias, hallazgos) son
diez capas con el mismo problema.

**Medido**, mismo banco que ADR-0034: barrer los 30 000 predios de una municipalidad y convertirlos
a MVT en una sola pasada, bajo RLS, cuesta **95 ms**. Servir una tesela viva z16 cuesta 18 ms; una
z14, 43 ms.

## Decisión

**Dos carriles, separados por si el dato cambia mientras el usuario mira.**

1. **Carril de referencia — un archivo de teselas por municipalidad.** Manzanas, sectores, vías,
   zonificación aprobada, límites y el lote como fondo. Lo genera un `CronJob` del perfil `batch`,
   uno por municipalidad activa, que fija su `app.municipalidad_id` **una vez**, barre su padrón y
   escribe un archivo **inmutable** en el almacenamiento de objetos, publicando el puntero al
   final. El aislamiento se resuelve **al generar**, no al leer: un archivo por tenant.

2. **Carril vivo — MVT desde la aplicación.** Lo que cambia mientras se mira: el lote en edición,
   los candidatos de la campaña, el hallazgo de hoy. Va por `marco_*`, bajo RLS, con el
   `@RequiereAcceso` del endpoint, y con caché de vida corta.

3. **El GeoJSON de ADR-0022 se conserva** para lo que hoy sirve —un predio, un puñado— y su 422
   deja de ser la respuesta al distrito entero, porque el distrito entero ya está en el carril 1.

## Lo que esta decisión NO hace

- **No expone un servidor de teselas genérico.** Martin o pg_tileserv delante de PostGIS no conocen
  el token, no pueden aplicar `@RequiereAcceso` y para funcionar necesitarían un rol que evada RLS.
  Es la misma objeción que ADR-0034 usa para descartar `SECURITY DEFINER`, y por la misma razón.
- **No reproyecta el acervo.** La geometría sigue en `geography(…, 4326)`; la proyección a 3857 la
  hace `ST_AsMVTGeom` al generar la tesela, y no se guarda.

## Consecuencias

- **La elección de librería del visor cambia, y todavía no cuesta nada.** ADR-0022 eligió Leaflet,
  correcto para GeoJSON crudo y equivocado para teselas vectoriales: no renderiza por WebGL y a
  partir de unos miles de polígonos el navegador se arrastra. **MapLibre GL JS** consume los dos
  carriles y simboliza por atributo sin volver a pedir el dato. Como `catastro-web` no existe, esto
  no es una migración: es elegir bien la primera vez, y le toca a ADR-0030 porque el visor no puede
  ser una librería distinta de la del resto del producto.
- El descriptor de infraestructura gana un `CronJob`, una entrada en `claves()` y un destino de
  egreso. La prueba que hoy afirma que el egreso es **exactamente** `["normativa","rentas"]` se
  actualiza en el mismo commit, o se pone roja — que es lo que uno quiere.
- **Un archivo de teselas viejo no da error, da un mapa viejo.** La antigüedad del archivo publicado
  por capa y municipalidad es una métrica de la observabilidad, no un detalle de operación.
