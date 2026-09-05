# ADR-0035 — El hallazgo catastral es una entidad con acto y evidencia, no un informe

| Campo | Valor |
|---|---|
| Estado | Propuesto |
| Fecha | 2026-09-05 |
| Decide | Dirección del proyecto |
| Completa | [ADR-0021](ADR-0021-la-geometria-del-predio.md) §«Lo que la geometría NO hace» |
| Depende de | [ADR-0015](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0015-conciliacion-catastro-rentas.md), [ADR-0033](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0033-cinco-sistemas-el-territorio-y-la-calle.md) |

## Contexto

ADR-0021 decide que la geometría no valoriza y cierra con una frase exacta: «que las dos áreas no
coincidan es un **hallazgo que se informa**, no una corrección que se aplica». La primera mitad está
implementada. La segunda no: no hay tabla donde informarlo, ni acto que lo produzca, ni evidencia
que lo sostenga. Hoy el hallazgo no se informa, se pierde.

Y ADR-0015 resolvió un caso vecino que conviene no confundir con éste. `conciliadoA(ejercicio)`
detecta al **omiso declarativo**: hay `predio`, no hay declaración jurada del ejercicio. Quedan
fuera —y no es un olvido de aquel ADR, es que no era su pregunta— los otros dos:

| Caso | Cómo se ve en los datos | Quién lo detecta hoy |
|---|---|---|
| Omiso declarativo | hay `predio`, falta la DJ del ejercicio | resuelto: `ConsultaDeConciliacion` |
| **Omiso catastral** | hay techo en la ortofoto y **no hay fila de `predio`** | nadie |
| **Subvaluador** | `ficha_catastral.area_terreno` ≠ el área medida en campo | nadie |

Los dos que faltan no son consultas: no se pueden derivar de lo que hay, porque su insumo entra de
fuera —una ortofoto, una brigada— y necesita que una persona lo confirme antes de tener efecto.

## Decisión

**Dos tablas, no un estado, y dos compuertas humanas entre ellas.**

1. **`candidato` es lo que la máquina sospecha.** Origen (ortofoto, dron, cruce de áreas, denuncia,
   barrido de campo), clase, geometría, `score`, los insumos que lo dispararon, y `predio_id`
   **nulable** — porque en el omiso catastral, por definición, no hay predio al que apuntar.
   No tiene efecto jurídico ninguno.

2. **`hallazgo` es lo que una persona verificó.** Lleva `predio_id` y `ficha_id` —**qué versión de
   ficha** se contrastó, por el mismo motivo que `declaracion_jurada.ficha_catastral_id` la lleva—,
   el área de la ficha copiada al verificar, el área verificada, el inspector y la fecha.

3. **`evidencia` se hashea en el dispositivo y se guarda en almacenamiento inmutable**, con los
   **dos relojes** separados: `capturado_en` es el del aparato y `recibido_en` el del servidor.
   `UNIQUE (municipalidad_id, sha256)`: una foto no sustenta dos actas.

4. **Un hallazgo firme NO corrige el área.** Habilita el acto que una persona ejecuta, y ese acto
   es el que ya existe: versionar la ficha con su observación obligatoria. Es la mitad de ADR-0021
   que esta decisión no toca.

5. **El descarte se conserva, con su motivo.** Regla 4, y además su tasa por etapa es el único
   indicador honesto de si el umbral de detección sirve. Un descarte borrado es un modelo que nadie
   puede medir.

`evidencia` y `acta` entran en `TABLAS_INMUTABLES`; `hallazgo` y `candidato`, en
`TABLAS_PROTEGIDAS`.

## Lo que esta decisión NO hace

- **No emite actas automáticas.** «Techo en la ortofoto y no en el padrón = omiso» es cierto como
  intuición y falso como regla de producción: una ortofoto detecta techos, no predios — puede ser
  una ampliación ya declarada, un predio conciliado con otro código, o un toldo. Sin las dos
  compuertas, la municipalidad emite miles de valores que se caen en reclamación, y eso cuesta más
  que lo que recupera.
- **No inscribe predios.** Un omiso catastral confirmado habilita `InscribirFicha`, que es el acto
  que ya existe y que crea predio y ficha en el mismo acto.
- **No mira rentas.** El hallazgo es un hecho del catastro sobre sí mismo. Que un predio tenga o no
  declaración jurada lo contesta `conciliadoA(ejercicio)` desde `rentas`, y sigue haciéndolo.

## Alternativas descartadas

- **Una columna `observado` en `ficha_catastral`.** Contradice su invariante —se versiona, no se
  sobrescribe— y además el hallazgo no es de la versión de la ficha: es del predio, y a veces ni
  siquiera hay predio.
- **Un solo estado en `candidato` que llegue hasta el acta.** Mezcla en una tabla lo que la máquina
  cree con lo que una persona firmó. El día que haya que responder «¿quién dijo esto?», la
  respuesta tiene que ser una fila con nombre, no un `score`.
