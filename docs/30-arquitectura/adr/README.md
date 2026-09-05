# Decisiones de arquitectura (ADR)

Las decisiones del **Catastro Fiscal**: el predio, su geometria, su visor y la forma en que publica lo que valoriza.

Aloja **ADR-0027**, la valuacion sellada: lo que catastro publica es un hecho suyo, y rentas lo enlaza.

Un ADR registra una decision con su contexto y sus consecuencias. **No se editan una vez
aceptados**: si una decision cambia, se escribe otro ADR que declare obsoleto al anterior. El
historial de por que se hizo algo vale mas que la coherencia del documento.

## Los de este repositorio

| # | Decision | Estado |
|---|---|---|
| [0021](ADR-0021-la-geometria-del-predio.md) | La base modela la geometría del predio | Aceptado |
| [0022](ADR-0022-el-visor-del-plano-catastral.md) | El visor del plano catastral | Aceptado |
| [0027](ADR-0027-la-valuacion-es-un-hecho-sellado.md) | La valuación es un hecho sellado del ejercicio, no un estado del predio | Propuesto |
| [0034](ADR-0034-el-marco-y-el-operador-espacial.md) | Toda tabla de tenant con geometría lleva su marco, y el operador espacial no entra en el SQL de aplicación | Propuesto |
| [0035](ADR-0035-el-hallazgo-es-una-entidad.md) | El hallazgo catastral es una entidad con acto y evidencia, no un informe | Propuesto |
| [0036](ADR-0036-dos-codigos-y-no-uno.md) | El Código Único Catastral del SNCP es una identidad distinta del código de referencia municipal | Propuesto |
| [0037](ADR-0037-dos-carriles-de-mapa.md) | Dos carriles de mapa: lo publicado se tesela, lo vivo se sirve | Propuesto |

## Los que enlaza, y no copia

Viven en el repositorio de quien toma la decision. **Aqui solo esta el enlace**: una
copia seria un segundo ADR el dia que alguien edite uno de los dos.

| # | Decision | Vive en | Por que le importa a este repositorio |
|---|---|---|---|
| [0001](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0001-plataforma-backend.md) | Plataforma del backend: Spring Boot 4 sobre Java 25 | `infrastructure` | la plataforma del backend que corre |
| [0002](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0002-estrategia-multi-tenant.md) | Esquema compartido con Row Level Security | `infrastructure` | el aislamiento, que es el riesgo numero uno |
| [0004](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0004-almacenamiento-de-datos.md) | PostgreSQL, con particionado por ejercicio | `infrastructure` | el motor y su particionado |
| [0007](https://github.com/hneyra/normativa/blob/main/docs/30-arquitectura/adr/ADR-0007-parametros-versionados.md) | Parámetros tributarios versionados y sellados por ejercicio | `normativa` | el conjunto sellado con que valoriza |
| [0008](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0008-auditoria-heredada-del-manual.md) | Auditoría con observación obligatoria, como en el sistema original | `infrastructure` | la observacion obligatoria (regla 10) |
| [0015](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0015-conciliacion-catastro-rentas.md) | La conciliación catastro↔rentas: un derivado que publica rentas, no un estado que guarda catastro | `rentas` | la conciliacion, que **sirve rentas** y su ficha alimenta |
| [0017](https://github.com/hneyra/normativa/blob/main/docs/30-arquitectura/adr/ADR-0017-tablas-de-valuacion-nacionales.md) | Las tres tablas de valuación son nacionales | `normativa` | los tres cuadros nacionales que consume |
| [0018](https://github.com/hneyra/normativa/blob/main/docs/30-arquitectura/adr/ADR-0018-el-redondeo-decidido.md) | El redondeo, decidido: escala ratificada, `HALF_UP`, y ningún SRTM que imitar | `normativa` | el redondeo, que aplica al valorizar |
| [0019](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0019-titularidad-parcial.md) | La porción sin titular identificado no se determina a nadie | `rentas` | que la porcion sin titular no se determina: su titularidad es el insumo |
| [0024](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0024-la-frontera-del-calculo.md) | La frontera del calculo: catastro valoriza el predio, rentas determina la obligación | `rentas` | la frontera del calculo: hasta donde llega su fase 1 |
| [0028](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0028-el-tenant-no-cruza-por-http.md) | El contexto de municipalidad no cruza por HTTP: token delegado, jamás una cabecera | `infrastructure` | el tenant no cruza por HTTP |
| [0029](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0029-cuatro-sistemas-separados.md) | Cuatro sistemas separados: `catastro`, `rentas`, `normativa` y `caja` | `infrastructure` | por que hay cuatro sistemas |
| [0030](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0030-cuatro-interfaces-una-sesion.md) | Cuatro interfaces, una sesión, y las librerias comunes que impiden que sean cuatro productos | `infrastructure` | su frontend |
| [0032](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0032-el-esquema-nace-en-baseline.md) | El esquema de cada sistema nace en un baseline; la historia se queda en `sgtm` | `infrastructure` | su baseline |
| [0033](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0033-cinco-sistemas-el-territorio-y-la-calle.md) | Cinco sistemas: `catastro` absorbe el territorio y `seguridad` se separa | `infrastructure` | por que los seis modulos del territorio viven aqui |

El reparto entero, con su criterio, esta en [GOB-05 §4](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/docs/00-gobierno/inventario-del-corte.md).

Decisiones **pendientes**: [GOB-02](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/docs/00-gobierno/decisiones-abiertas.md).

## Plantilla

```markdown
# ADR-000X — Titulo

**Estado:** Propuesto | Aceptado | Obsoleto (reemplazado por ADR-000Y)
**Fecha:** AAAA-MM-DD

## Contexto
## Decision
## Consecuencias
## Alternativas consideradas
```

El estado tambien puede ir como fila de una tabla de metadatos (`| Estado | Aceptado |`), que es
la forma de ADR-0017 en adelante; lo que no cambia es el vocabulario: **Propuesto**, **Aceptado**
u **Obsoleto**, siempre con esa letra.

## La numeracion NO se reinicia

El ADR nuevo de este repositorio es el **0033**, no el 0001. Los treinta y dos existen y estan
repartidos; empezar de nuevo daria dos `ADR-0001` distintos en el mismo producto, y el dia que
alguien cite «ADR-0004» habria que preguntar de cual habla.
