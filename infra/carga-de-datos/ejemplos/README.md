# Archivos de carga de ejemplo de `catastro`

Cinco CSV que dejan el catastro **poblado** en una instalación de demostración. Los cinco
restantes de la siembra viven donde vive su sistema —cuatro en `rentas` y uno en `caja`—, y
**cada uno está en un solo repositorio** desde C-6: dos copias del mismo archivo divergen, y la
que se lea decidiría contra qué se cruzan las demás.

La municipalidad de referencia es **Catacaos** (ubigeo `200104`), la piloto de D-01 —no Sullana,
de cuyo manual sale la especificación funcional—.

| Archivo | Qué contiene | Naturaleza | Se carga con | Paso |
|---|---|---|---|---|
| `vias.csv` | 15 vías de Catacaos | **Estructura**: nombres de vía de dominio público | `cargar-catalogo-vial.sh` | 1 |
| `sectores.csv` | 4 sectores | **Estructura** | `cargar-sectores.sh` | 2 |
| `manzanas.csv` | 15 manzanas | **Estructura** | `cargar-manzanas.sh` | 3 |
| `fichas.csv` | 23 predios con su primera ficha y su titular | **Ficticio**: predios inventados | `cargar-fichas-demo.sh` | 6 |
| `detalle-de-fichas.csv` | lo que hay **dentro** de 22 de esas fichas, que quedan versionadas | **Ficticio** | `cargar-detalle-fichas-demo.sh` | 7 |

Y uno más que **no es de la siembra**, con #5:

| Archivo | Qué contiene | Naturaleza | Se carga con | Paso |
|---|---|---|---|---|
| `riesgo.csv` | 4 zonas de peligro y 2 fajas marginales, en dos capas | **Ficticio**, y por un motivo que se puede medir: hoy no hay ni un polígono de predio cargado, así que no había con qué cruzarlas | `cargar-riesgo.sh` | ninguno |

`riesgo.csv` no tiene número de paso porque **no lo tiene**: `pasos.tsv` no lo nombra y
`sembrar-demostracion.sh` no lo corre. Su cargador (`CargarRiesgo`) no pregunta por
`es_demostracion` —una carta de peligro es un acto de CENEPRED, no un dato inventado— y por eso
está aquí sólo como **muestra del formato**, no como parte de la demostración.

Los tres primeros son datos de estructura y valen para una municipalidad real: sus cargadores
(`CargarCatalogoVial`, `CargarSectores`, `CargarManzanas`) no preguntan nada sobre el régimen de
la instalación.

Los dos últimos **solo corren contra una instalación de demostración**. Antes de leer una fila
preguntan por `municipalidad.es_demostracion` —la misma fila que decide si un documento sale
marcado— y si la respuesta es «no», no escriben nada: `SoloEnDemostracion` lo impide. Y aquí no se
borra nada (RNF-051): deshacerlo sería dar de baja fila a fila.

## El orden, y por qué no está aquí

Dentro de `catastro` el orden es `vias → sectores → manzanas → fichas → detalle-de-fichas`, y cada
archivo nombra por código algo que otro tuvo que escribir antes. Pero **`fichas.csv` nombra
además a sus titulares por su código de contribuyente, y ese padrón vive en `rentas`**: el paso 6
depende del 5, que está en otra base. Por eso el orden de los diez pasos, con su dueño, está
escrito **una sola vez** y no aquí:
[`infrastructure/infra/carga-de-datos/siembra/pasos.tsv`](https://github.com/hneyra/infrastructure/blob/main/infra/carga-de-datos/siembra/pasos.tsv).

## Dos cosas de `fichas.csv` y `detalle-de-fichas.csv`

**`fichas.csv` no tiene una columna con el código predial entero.** Sus primeras columnas son los
tramos del código de referencia catastral, en el orden que declara `ComposicionCatastral.DEL_MANUAL`;
el código lo compone `CodigoReferenciaCatastral.componer`, rellenando cada tramo con ceros a la
izquierda. **D-10 sigue abierta** —el manual da 23 posiciones y el prototipo de interfaz 21—; si se
cierra en 21, cambia el número de columnas de tramo de este archivo y nada más.

**`detalle-de-fichas.csv` versiona, no sobrescribe.** Sus 51 filas se agrupan por predio —la unidad
de carga es el predio, no la fila, porque `siguienteVersion` copia de la anterior lo que no se le
mande y media versión es una ficha que miente— y dejan **22 versiones nuevas**. Sumadas a las 23 que
inscribe `fichas.csv` son las **45 versiones** que el juego de datos anuncia; esa cifra no está
escrita en ninguna parte, se deriva de los dos archivos.

`Jirón Cusco 900` se queda fuera a propósito: es un **terreno sin construir**, y su ficha conserva
cero construcciones. La pantalla tiene que saber dibujar eso.

## Ninguna cifra normativa

Ni aranceles, ni valores unitarios, ni tablas de depreciación. El área de terreno es una **medida**
del predio, no un valor. `ArchivosDeEjemploTest` recorre los cinco archivos y rechaza cualquier línea
que nombre un arancel, un valor unitario, una depreciación, la UIT o una alícuota.

## Estos archivos pasan por el analizador de verdad

`ArchivosDeEjemploTest` los carga fila a fila con los importadores de producción, en su orden y
sobre el mismo catastro en memoria, y exige que entren enteros. Para cruzar los titulares lee
`contribuyentes.csv` **del clon hermano de `rentas`**; sin él, falla nombrando el `git clone`.
