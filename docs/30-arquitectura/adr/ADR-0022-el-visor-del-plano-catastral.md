# ADR-0022 — El visor del plano catastral

| Campo | Valor |
|---|---|
| Estado | Aceptado |
| Fecha | 2026-08-31 |
| Decide | Dirección del proyecto |
| Amplía | [ADR-0021](ADR-0021-la-geometria-del-predio.md), que se cerró diciendo «tampoco hace un visor de mapas» |
| Implementa | issue [#500](https://github.com/hneyra/sgtm/issues/500) |

## Contexto

`Catastro.dc.html` promueve el **mapa catastral a forma principal de encontrar un predio** —«por
manzana y lote, que es como la gente lo piensa»— y lo pone como uno de los cinco destinos del
módulo, junto al panel, «Predios», «Territorio» y «Valores del ejercicio».

ADR-0021 dejó la geometría en la base y el visor **fuera de su alcance, con todas las letras**.
Abrirlo es una decisión de arquitectura y no un detalle de interfaz, porque decide qué sale por
HTTP, con qué tamaño, y qué se puede afirmar dibujándolo. Un plano no se lee como una maqueta: se
lee como un levantamiento.

## Decisión

### 1. Se publica el polígono entero, en WGS84, y no una teselación

`GET /api/v1/catastro/predios/plano` devuelve, por lote, **la geometría tal como está en la
columna**: `geography(MultiPolygon, 4326)` serializada a **GeoJSON** con `ST_AsGeoJSON`. Ni se
reproyecta, ni se simplifica, ni se teselan.

- **No se reproyecta** porque la columna ya está en 4326 por la razón de ADR-0021 —una instalación
  atiende municipalidades de las zonas UTM 17S, 18S y 19S y ninguna proyección sirve para todas—, y
  porque 4326 es lo que toda biblioteca de mapas consume. La proyección de **pantalla** (Web
  Mercator) la aplica el cliente al dibujar, que es donde no compromete el dato.
- **No se simplifica.** `ST_Simplify` mueve vértices, y un vértice movido es un lindero movido. La
  tentación es real —el peso de la respuesta baja mucho— y la salida es la misma que ADR-0021 tomó
  con el área: **la geometría no se retoca; lo que se acota es cuántas filas se piden**. Un lindero
  aproximado es indistinguible de uno exacto al mirarlo, que es la forma de error que este proyecto
  rechaza en todas partes.
- **No se teselan.** Servir teselas vectoriales (`ST_AsMVT`) es la respuesta correcta para un padrón
  entero en pantalla, y **no es la pregunta que esta superficie hace**: aquí se busca un lote
  concreto por manzana y lote, con el sector ya elegido. Las teselas traen un formato binario, una
  caché por nivel de zoom y una invalidación al corregir un polígono; se retoman cuando la medida
  diga que hacen falta, y no antes.

### 2. La respuesta se acota por marco, y **se niega** antes que recortarse

La lectura acota por **marco** (`bbox`, en grados WGS84: oeste, sur, este, norte), y admite además
los dos filtros con los que se busca en ventanilla: `codigoDeSector` y `codigoDeManzana`.

Un sector de Sullana son miles de lotes. Lo que decide esta entrada no es el tamaño de la respuesta
sino **qué hace cuando no cabe**: si el marco contiene más lotes de los que se sirven, la operación
responde **422 diciendo cuántos hay y cuál es el tope**, y no una página con los primeros.

> **Un mapa truncado en silencio es un mapa que miente.** Una tabla recortada se ve recortada —tiene
> paginación, y un total encima—; un plano al que le faltan lotes se lee como un plano donde ahí no
> hay nada. Es el mismo error que #322 evitó con el «—» de la conciliación, y aquí de peor clase:
> quien mira un hueco en el plano concluye que ese terreno no está en el padrón.

Por eso tampoco se pagina: la paginación de un plano no significa nada —no hay un orden que haga
que «la página 2» sea una porción del territorio— y las dos formas de terminar son legítimas: cabe,
o acércate.

#### 2.1 De dónde sale el **primer** marco (#612)

Exigir `bbox` deja una pregunta abierta que nadie contestaba: **dónde está la municipalidad**.
Medido, ninguna otra operación del contrato publicaba un rectángulo, un centroide ni un ubigeo
resoluble a coordenadas, así que la pantalla abría sobre un marco **declarado** —el Perú
continental— y encuadraba después sobre los polígonos que volvían. Hoy eso pasa inadvertido porque
no hay ni un lote digitalizado; el día que se cargue el primer plano, ese marco contiene más lotes
que el tope y la respuesta pasa a ser el 422 de arriba: **correcta, y imposible de obedecer**,
porque desde la pantalla no se sabe hacia dónde acercarse.

Lo cierra una segunda lectura, `GET /catastro/predios/plano/marco`, que publica el rectángulo que
envuelve **la geometría ya cargada** —agregando las cuatro columnas de `V65`, no una constante— con
los **mismos** filtros de sector y manzana. Que sean los mismos no es una comodidad: un marco
calculado sobre otro conjunto de predios encuadraría sobre algo que después no se dibuja, y eso, sin
base cartográfica debajo, **no se ve**.

Publica un rectángulo y una cuenta, y nada más: ni un `predioId`, ni un código, ni una dirección
—añadir el del lote más al norte la convertiría en una forma de recorrer el padrón sin pedir el
padrón—. Y **puede no haber marco**, en dos situaciones que se dicen por separado porque se arreglan
distinto: con cero lotes levantados —el estado de hoy— lo que falta es la carga cartográfica; con
lotes y sin rectángulo, todo lo levantado cae sobre la misma línea y su envolvente no encuadra nada.
Nunca `0,0,0,0`: ese punto está en el golfo de Guinea, y nada delataría un visor abierto ahí.

### 3. Los predios sin geometría se **cuentan**, no se esconden

ADR-0021 dice que son «todos los de hoy, y muchos no la tendrán nunca». La lectura devuelve, junto a
los lotes, **cuántos predios del padrón, con los mismos filtros de sector y de manzana, no tienen
polígono — sin acotar por el marco**, y la pantalla lo dice siempre, incluso cuando son cero.

Que el marco **no** la acote es la decisión, no un descuido: un predio sin polígono no tiene sitio en
el marco, así que compararlo contra `bbox` daría cero **siempre** —sus cuatro columnas `marco_*` de
`V65` son nulas y ninguna desigualdad se cumple—, y daría cero justo cuando la cifra más hace falta.
El único dato que podría situarlo, el perímetro de su manzana, no existe en el esquema, y derivarlo
de la unión de los lotes ya levantados es lo que §5 prohíbe.

Sin esa cifra, el visor afirma algo que no sabe. Con doscientos lotes dibujados y ochocientos sin
polígono, un plano mudo dice «este sector tiene doscientos lotes», y lo que pasa es que tiene mil y
ochocientos no están levantados. Es la misma frontera de ADR-0015: **el sistema dice que falta y
quién lo resuelve es una persona**, aquí con una carga cartográfica.

Y el estado de **hoy** es ése llevado al extremo: ninguna municipalidad tiene un solo polígono
cargado, así que el visor abre vacío y su vacío nombra la causa y la salida —`cargar-predios.sh`,
ADR-0021—. No es un estado de error ni un adorno: es el primer estado que la pantalla tiene que
saber dibujar.

### 4. La biblioteca: Leaflet con teselas de OpenStreetMap, en su propio trozo y **prescindibles**

- **Leaflet**, y no el SVG a mano del prototipo. El plano del artboard son doce rectángulos en una
  rejilla calculada; una geometría real proyectada necesita reproyección, encuadre, zoom continuo y
  aciertos de puntería sobre polígonos irregulares, que es exactamente lo que una biblioteca de
  mapas ya resuelve. Escribirlo a mano sería reescribirla peor.
- **En su propio trozo perezoso**, con `import()`, y por eso no entra en el presupuesto de arranque:
  quien entra a mirar un recibo no descarga un motor de mapas. Medido, `leaflet-src` son **42,3 KB
  comprimidos** y su hoja de estilos 6,3, más 4,5 de la pantalla y 0,9 de su CSS; el arranque pasa de
  146,9 a **147,5** y lo que sube son la entrada del menú, la rama de la barra lateral y la ruta, que
  son del arranque por diseño. Es la regla que #433 dejó escrita: lo que no se ve al entrar viaja con
  el trozo de su módulo. **Su hoja de estilos tampoco entra**: vive en `pantallas/catastro/mapa.css`
  y no en la global, porque escrita allí dejaba el arranque a una décima de su tope.
- **Las teselas son la referencia, no el dato.** El plano son los polígonos, que llegan por HTTP
  desde el mismo servidor que todo lo demás; las teselas de OpenStreetMap sólo dicen qué hay
  alrededor. Si no cargan —una municipalidad sin salida a internet, que es lo corriente—, **el plano
  se dibuja igual** y la pantalla lo dice. El origen de las teselas es configurable
  (`VITE_KAMAYUK_TESELAS`) para que una instalación pueda apuntar a su propio servidor, y la atribución
  de OpenStreetMap se muestra siempre, que es su licencia.

### 5. Qué capa es cada capa, y **cuál no se pinta**

El artboard pide cinco capas conmutables. Medido contra el esquema, **una tiene geometría propia**:

| Capa del artboard | Qué la sostiene hoy | Qué hace el visor |
|---|---|---|
| Predios (lotes) | `predio.geometria` (V61) | **Se dibuja.** Es el plano |
| Manzanas | nada: `manzana` no tiene columna de geometría | **Colorea y rotula los lotes por su manzana.** No dibuja su perímetro |
| Sectores | nada: `sector` no tiene columna de geometría | Igual: colorea los lotes por su sector |
| Vías y calles | nada: `via` no tiene columna de geometría | **No se dibuja**, y la capa lo dice |
| Aranceles por zona | no es resoluble por lote (abajo) | **No se pinta**, y la capa dice por qué |

**Manzanas y sectores no son un perímetro.** El contorno de una manzana no es la unión de los lotes
que alguien haya digitalizado: es la manzana, y hasta que no haya una capa que la lleve, dibujar esa
unión sería publicar un lindero que nadie levantó. Lo que sí es cierto y sí sirve es que **cada lote
sabe de qué manzana y de qué sector es**, y agruparlo por color es decir eso y nada más. La leyenda
lo escribe.

**El arancel no se pinta, y el motivo no es la prudencia: es que no se puede resolver.** `arancel`
está llaveado por `(conjunto, via_id, tramo)` —`arancel_uq` de V18, con `arancel_sin_tramo_uq` de
V25 para el tramo nulo—, y `predio` tiene `via_id` y `numero_municipal` pero **no tiene tramo**. Una
vía con más de un tramo tiene más de un arancel, y nada en el sistema dice cuál le toca a un lote.
Colorear el lote exigiría elegir uno, y elegir mal no se ve: el color sale igual de plausible.

Y aunque la vía tuviera un solo arancel, **un color es un rango y una cifra normativa no lo es**. El
arancel se lee donde ya se lee —`GET /catastro/aranceles`, la superficie de valores del ejercicio—,
con su ejercicio y su documento fuente al lado. Cuando el tramo del lote se modele, la capa vuelve a
la mesa con una leyenda de valores exactos, no con una rampa.

### 6. El panel del lote: nueve filas, y lo que no se sabe dice «—»

Al seleccionar un lote se abre su panel. **Sale de tres sitios y ninguno se inventa.** La lectura del
plano trae la identidad y la ubicación, y nada más.

El **nombre** del titular, el uso y las dos áreas salen de la consulta de fichas del mismo predio
—`GET /catastro/fichas?codRefCatastral=…`, que ya publica los cuatro y que este perfil puede hacer,
porque es el permiso que el visor exige—. Un predio **sin ficha** —que es justo lo que una carga
cartográfica produce— los muestra en «—».

El **código** del titular es otra cosa, y por eso va aparte: lo resuelve
`/catastro/predios/{predioId}/titulares`, que exige lectura sobre `contribuyentes` —el permiso del
padrón— y deja fila de ACCESO en la bitácora por cada resolución (ADR-0015 §2.4). Sólo hace falta
para la salida «ver deuda», porque la deuda es de una persona y no de un predio; quien no tenga ese
permiso no ve un enlace que le llevaría a un 403: ve dicho qué le falta.

Las dos salidas del artboard —**abrir el predio** y **ver su deuda**— son enlaces a pantallas que ya
existen, y se dibujan sólo si el perfil puede verlas (REQ-03 §5). El visor no gana ninguna escritura:
**corregir un polígono a mano es dibujar**, y para eso hace falta un editor que ADR-0021 ya dijo que
no existe.

### 7. Ni una escritura, y ni un área derivada

Lo que ADR-0021 prohibió sigue prohibido, y el visor es donde más tienta: **el área del polígono no
se muestra en ningún sitio donde pueda confundirse con la de la ficha**. `area_terreno` es la que
midió el técnico. Que las dos no coincidan es un hallazgo que se informa —y no lo informa un mapa:
lo informa quien compare las dos columnas, con su acto y su observación.

## Consecuencias

- **Una operación de lectura más en el contrato**, y de una forma que ninguna otra tiene: se niega
  en vez de paginar. Nació declarada y **sin controlador que la sirviera** —el censo de #400 la
  contaba, igual que `GET /portal/deuda`— y #536 la sirvió. **Son dos desde #612**: la del plano y
  la de su marco (§2.1), que cuelga de la misma ruta y hereda su acceso porque es el encuadre del
  mismo mapa.
- **El visor no es una opción número 135.** Las 134 siguen siendo 134: `mapa` es una ruta del módulo,
  como la portada, sin id en el catálogo y sin permiso propio (ADR-0014 §5). El permiso que exige es
  el de **encontrar un predio** —`consulta_fichas`, con `LECTURA`—, porque el mapa es esa misma
  búsqueda por otro camino; pedir el de actualizar el catastro dejaría sin mapa a quien sólo mira.
- **Primera dependencia de terceros del frontend con peso.** Leaflet entra por `import()` y su trozo
  no pasa por el presupuesto de arranque, que es lo que permite aceptarla. Si un día se dibujan
  teselas vectoriales, la decisión de biblioteca se revisa entera.
- **La atribución de OpenStreetMap es obligatoria** y se dibuja siempre que sus teselas se usen. No
  es adorno: es la licencia ODbL.
- Las tres capas que hoy no tienen geometría —vías, manzanas, sectores— tienen su issue. Ninguna se
  dibuja «mientras tanto» con una aproximación.

## Alternativas descartadas

**Teselas vectoriales desde el principio (`ST_AsMVT`).** Es lo que hace falta el día que alguien
quiera ver el distrito entero, y hoy no es la pregunta: la superficie busca un lote por manzana y
lote. Traería un formato binario en el contrato, una caché por nivel de zoom y una invalidación que
hoy no tiene a quién invalidar —no hay un solo polígono cargado—. Se retoma con una medida delante.

**Simplificar el polígono al servirlo.** Baja mucho el peso y mueve linderos. Un lindero movido no
se ve. Descartada por lo mismo que ADR-0021 descartó derivar el área.

**Paginar el plano.** Es lo que hace toda otra lectura del sistema, y aquí no significa nada: no hay
un orden que convierta «la página 2» en una porción del territorio, y un plano al que le faltan lotes
se lee como un plano donde no hay lotes. La negativa con su cifra es más pequeña y más honesta.

**Dibujar la manzana como la unión de sus lotes.** Da un contorno plausible al instante, y publica un
lindero que nadie levantó: allí donde falten lotes por digitalizar —que es en todas partes—, la
manzana saldría mordida. Se colorean los lotes y se rotula la manzana, que es exactamente lo que se
sabe.

**Reproyectar a UTM en el servidor.** Daría metros y una escala exacta sin trabajo del cliente, y
obliga a elegir zona: el mismo problema que ADR-0021 resolvió eligiendo `geography`. La escala
gráfica del visor se calcula sobre el elipsoide, en el meridiano del centro de la vista, que es lo
que una escala gráfica significa.
