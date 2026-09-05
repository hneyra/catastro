# ADR-0036 — El Código Único Catastral del SNCP es una identidad distinta del código de referencia municipal

| Campo | Valor |
|---|---|
| Estado | Propuesto |
| Fecha | 2026-09-05 |
| Decide | Dirección del proyecto |
| Contesta | **D-10** — «longitud exacta del código de referencia catastral» |

## Contexto

D-10 lleva abierta porque los largos no cuadran: la plantilla del manual da 23 posiciones, los
ejemplos del prototipo dan otra cosa, y `V1__baseline.sql` declaró `cod_catastral varchar(25)` para
que entren las dos. No cuadran porque **se están comparando dos identificadores distintos**:

- **El código de referencia catastral** es municipal. Lo compone la municipalidad con su sector,
  manzana, lote y unidad, y su largo es una decisión suya. Por eso el manual y el prototipo
  discrepan: describen dos municipalidades.
- **El Código Único Catastral (CUC)** lo asigna el Sistema Nacional Integrado de Catastro
  (Ley 28294). Tiene **12 posiciones**: 8 de rango asignado por distrito más 4 correlativos de
  unidad catastral, según la Directiva 01-2023-SNCP-CNC. Hoy no existe en el modelo.

Y hay un tercero que no lo es: el «código predial de rentas». ADR-0015 ya lo resolvió — es sinónimo
del de referencia catastral, y hay un solo padrón.

## Decisión

1. **`codigo_ref_catastral` se declara municipal y de largo configurable por municipalidad.** El
   patrón que lo valida vive en la fila de `municipalidad`, no en un dominio de tipo global. Con
   eso D-10 deja de ser una ambigüedad y pasa a ser una característica del tenant.

2. **El CUC entra como columna propia, `predio.cuc`, con dominio `char(12)` y nulable**, más
   `predio.nivel_sncp` para la clase de unidad catastral. Índice único **parcial**
   (`WHERE cuc IS NOT NULL`): la inmensa mayoría de predios de una municipalidad no lo tiene, y
   exigirlo convertiría la inscripción de una ficha en un trámite ante el SNCP.

3. **La búsqueda por cualquiera de los dos se escribe como rango** con `~>=~` y `~<~` sobre un
   índice `text_pattern_ops`, por el tercer hallazgo de RLS. Que sean dos códigos no cambia eso.

## Lo que esta decisión NO hace

- **No hace obligatorio el CUC ni promete interoperar con el SNCP.** Lo que hace es que el día que
  la municipalidad reciba sus rangos, haya dónde ponerlos sin migrar el padrón.
- **No unifica los dos códigos.** Son de dos dueños distintos y cambian por motivos distintos: una
  subdivisión municipal renumera el primero y el segundo solo cambia cuando el SNCP lo dice.

## Consecuencias

- El índice único parcial se puede crear sin contexto de tenant —construir un índice lee el montón
  y no pasa por la política, medido en #588—, pero **su fallo no dice cuáles son los duplicados**.
  Como toda fila anterior tiene `cuc` nulo, el predicado los excluye por construcción y la
  migración no puede pararse. Es el mismo camino que `V75` usó en `sgtm`.
- La búsqueda de la consulta de fichas gana un criterio y no cambia de forma.
