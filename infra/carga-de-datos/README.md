# Carga de datos de `catastro`

Los seis archivos de este directorio son **la parte de `catastro`** de la siembra de la
municipalidad de demostración. Los otros cuatro —`contribuyentes.csv`, `cajas.csv`,
`vehiculos.csv`, `deuda.csv`— se quedaron donde vive su sistema: `rentas` y `caja`.

## El orden, y por qué sigue habiendo uno

Cada archivo nombra por código algo que otro tuvo que escribir antes, y **la fila que nombre algo
inexistente se rechaza sola** — no revienta la carga. Dentro de `catastro` el orden es:

| # | Guion | Archivo | Necesita antes |
|---|---|---|---|
| 1 | `cargar-catalogo-vial.sh` | `ejemplos/vias.csv` | — |
| 2 | `cargar-sectores.sh` | `ejemplos/sectores.csv` | — |
| 3 | `cargar-manzanas.sh` | `ejemplos/manzanas.csv` | los sectores |
| 4 | `cargar-fichas-demo.sh` | `ejemplos/fichas.csv` | vías, manzanas **y el padrón de `rentas`** |
| 5 | `cargar-detalle-fichas-demo.sh` | `ejemplos/detalle-de-fichas.csv` | las fichas |
| 6 | `cargar-transferencias-demo.sh` | `ejemplos/transferencias.csv` | las fichas y el padrón |

`cargar-predios.sh` y `cargar-arancel-vial.sh` no siembran datos de demostración: el primero
importa un catastro real desde un GeoPackage (ADR-0021) y el segundo el arancel del MEF.

## Lo que este directorio NO puede hacer solo, dicho aquí (P5C)

**El paso 4 cruza la frontera.** `fichas.csv` nombra a sus titulares por su código de
contribuyente, y ese padrón vive en `rentas` desde P5C. Sembrar `catastro` sin haber sembrado
antes el padrón de `rentas` no revienta: **rechaza todas las fichas, una a una, y termina en
verde**, que es la forma en que una siembra a medias peor se lee. `ArchivosDeEjemploTest` lo cruza
contra el CSV del repositorio hermano por eso.

**Y la secuencia entera ya no la orquesta nadie.** `sembrar-demostracion.sh` nombraba los diez
pasos en un solo orden y vive en `infrastructure`. Con la siembra repartida en tres repositorios,
quien la coordina es esa infraestructura, no este directorio. **Está declarado como hueco en
`docs/00-gobierno/P5C-extraccion.md` y no está resuelto.**
