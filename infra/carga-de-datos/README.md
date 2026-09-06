# Carga de datos de `catastro`

Los **cinco** archivos de `ejemplos/` son la parte de `catastro` de la siembra de la municipalidad de
demostración; los otros cinco se quedaron donde vive su sistema: `contribuyentes.csv`,
`vehiculos.csv`, `transferencias.csv` y `deuda.csv` en `rentas`, y `cajas.csv` en `caja`.

**Cada archivo está en un solo repositorio.** Hasta C-6 había hasta tres copias byte a byte de cada
uno —`infrastructure`, `rentas` y `catastro`— y nada impedía que divergieran: la copia que alguien
edita no tiene por qué ser la que el cargador lee. `ArchivosDeEjemploTest` lee `contribuyentes.csv`
del clon hermano de `rentas` por eso, y `ArchivosDeEjemploDeRentasTest` lee `fichas.csv` de aquí por
lo mismo.

## El orden, y dónde está escrito

Cada archivo nombra por código algo que otro tuvo que escribir antes, y **la fila que nombre algo
inexistente se rechaza sola** — no revienta la carga. Dentro de `catastro` el orden es:

| # global | Guion | Archivo | Necesita antes |
|---|---|---|---|
| 1 | `cargar-catalogo-vial.sh` | `ejemplos/vias.csv` | — |
| 2 | `cargar-sectores.sh` | `ejemplos/sectores.csv` | — |
| 3 | `cargar-manzanas.sh` | `ejemplos/manzanas.csv` | los sectores |
| 6 | `cargar-fichas-demo.sh` | `ejemplos/fichas.csv` | vías, sectores, manzanas **y el padrón de `rentas`** |
| 7 | `cargar-detalle-fichas-demo.sh` | `ejemplos/detalle-de-fichas.csv` | las fichas |
| — | `cargar-zonificacion.sh` | `ejemplos/zonificacion.csv` | nada: no es un paso de la siembra |

`cargar-predios.sh`, `cargar-arancel-vial.sh`, `cargar-zonificacion.sh` y `cargar-riesgo.sh` **no
siembran datos de demostración**: el primero importa un catastro real desde un GeoPackage
(ADR-0021), el segundo el arancel del MEF, el tercero el plan de zonificación aprobado por ordenanza
(#4) y el cuarto la carta de peligro de CENEPRED y la faja marginal de la ANA (#5). Los cuatro
cargan **actos de terceros sobre el territorio de esa municipalidad**, no datos inventados, así que
ninguno lleva la guarda `SoloEnDemostracion` — exigirla dejaría a una instalación de verdad sin
forma de poblarlos, que es el hueco que #430 encontró para `area` y `caja`.

**Y por eso ni `zonificacion.csv` ni `riesgo.csv` están en `pasos.tsv`.** Un paso declara una
comprobación —«esta tabla queda con N filas»—, así que meterlos ahí afirmaría que la municipalidad
de demostración tiene un plan de zonificación aprobado y una carta de peligro. No los tiene. Los dos
son ejemplos **para que el analizador se ejerza contra filas de verdad** y para que quien monte una
instalación vea la forma exacta que su SIG tiene que producir; `sembrar-demostracion.sh` no los
corre. Los polígonos de `riesgo.csv` sí son inventados, y conviene saberlo: hoy no hay ni un
polígono de predio cargado en ninguna instalación y no había con qué cruzarlos.

Que un CSV de `ejemplos/` **no** sea siembra es una premisa que `infrastructure` no tenía: su guarda
`ejemplosHuerfanos()` daba por sentado que todo archivo de ese directorio lo carga un paso, y con
estos dos puestos decía `expected [ 'catastro/riesgo.csv' ] to deeply equal []`. Se cierra allí, con
`EJEMPLOS_QUE_NO_SIEMBRAN` —la lista declarada con su motivo, como ya existía `GUIONES_QUE_NO_SIEMBRAN`
por el otro eje— y su contraste. `cargar-predios.sh` y `cargar-arancel-vial.sh` esquivaban esa guarda
sólo porque **no tienen** archivo de ejemplo.

**Los números 4, 5, 8, 9 y 10 no están aquí, y ése es el punto.** El orden completo —los diez pasos,
con su dueño— vive en **un solo sitio**, y no es este repositorio:
[`infrastructure/infra/carga-de-datos/siembra/pasos.tsv`](https://github.com/hneyra/infrastructure/blob/main/infra/carga-de-datos/siembra/pasos.tsv).
Está allí porque el orden es un hecho *entre* sistemas: escrito aquí, su dueño no podría ver a los
otros dos (ADR-0031, y el mismo argumento con que C-2 puso la guarda de extensiones allí).

Para sembrar entero:

```bash
../../../infrastructure/infra/carga-de-datos/siembra/sembrar-demostracion.sh \
    --ambiente stg --municipalidad-id 4 \
    --url-catastro postgresql://… --url-rentas postgresql://… --url-caja postgresql://…
```

## El paso 6 cruza la frontera, y hasta C-6 lo hacía en silencio

`fichas.csv` nombra a sus titulares por su código de contribuyente, y ese padrón vive en `rentas`
desde P5C. Sembrar `catastro` sin haber sembrado antes ese padrón **no revienta: rechaza todas las
fichas, una a una, y termina en verde**, que es la forma en que una siembra a medias peor se lee. Lo
mismo le pasa al paso 7 sin el 6 — medido contra PostgreSQL 16.15 el 2026-09-05:

```
$ cargar-detalle-fichas-demo.sh …
… 51 fila(s) leidas, 0 ficha(s) versionada(s), 22 predio(s) rechazado(s)
$ echo $?
0
```

Eso lo cierra `comprobar-siembra.sh` de `infrastructure`, que después de cada paso cuenta lo que la
tabla **tiene** y se para en rojo diciendo cuántas faltan.

## Lo que este repositorio tenía y no era suyo

`cargar-transferencias-demo.sh` estaba aquí y **el cargador que lo atiende vive en `rentas`**
(`CargarTransferenciasDeDemostracion`, encendido por `kamayuk.carga-transferencias-demo.archivo`).
Lanzado desde aquí arrancaba la aplicación de `catastro`, **no ejecutaba ni una línea de carga** y
salía con código 0 — sin un aviso, sin una fila rechazada, sin nada. Medido, y por eso se fue a
`rentas` con su CSV. Que no vuelva a pasar lo comprueba `siembra-de-la-demostracion.test.ts` de
`infrastructure`, que cruza cada guion con los `@ConditionalOnProperty` de su propio repositorio.
