# P5C — `catastro` extraído: el sistema más caro, y lo que no se pudo cerrar

**Fecha:** 2026-09-04. **Origen:** `rentas@24c9ed0` (que viene de `sgtm@0d33ad7b` por P5A y P5B).
**Repositorios tocados:** `catastro` (destino) y `rentas` (origen de la resta).
**`sgtm` no se tocó:** `git status` queda limpio, sin un solo archivo modificado.

Es la segunda extracción de verdad y la que el propio enunciado llama «el sistema más caro». Se
cerraron dos de los cuatro criterios enteros, uno a medias con su medida publicada, y **el
primero —el que manda— no se cumple**. §2 dice el paso exacto en el que se paró.

---

## 1. Los cuatro criterios, con su medida

| # | Criterio | Estado | Medida |
|---|---|---|---|
| **1** | La corrida de valuación en `catastro` más la de emisión en `rentas` producen **el mismo padrón, céntimo a céntimo**, que la del monolito | **NO SE CUMPLE**, y el motivo es de **datos y no de código**. §2 | El padrón que el sistema **sí sabe calcular hoy** sale byte a byte idéntico antes y después, `sha256 225d0356…`. §5 |
| **2** | La página de omisos pagina y cuenta lo filtrado | **Cumplido** | Con el conteo dejando de filtrar —el defecto de #631 exacto— el sobre dice **7 donde debe decir 3** y **1 donde debe decir 0**. §6 |
| **3** | Con el ingestor detenido a mitad, `rentas` **se niega** a emitir y dice por qué | **Cumplido** | Cuatro roturas del candado y una quinta que destapó que existía sin estar **puesto**. §7 |
| **4** | Los tres verificadores bloqueantes en verde en los dos repositorios, y las excepciones nominadas de P3 que este prompt cierra, cerradas | **Cumplido** | Los seis en verde. **`PENDIENTE-CRUCE-01`, `-04` y `-05` salen de la lista**; queda una entrada, la de `caja`. §8 |

---

## 2. Criterio 1 — por qué no se cumple

**No es la resta: la resta está hecha.** `V6__baja_de_catastro.sql` retiró las veinte claves
foráneas y las quince tablas, el módulo quedó reducido a los puertos y su transporte, y `rentas`
está en verde con sus tres verificadores. Lo que impide el criterio 1 es que **no existe ninguna
corrida de valuación con cifras**, ni en `catastro` ni en el monolito, y eso está medido leyendo el
código:

- `DeterminarPredial` recibe el autovalúo **declarado** en la petición, y su javadoc lo dice con
  todas las letras: «el sistema no sabe valorizar un predio todavía». Le faltan el cuadro de
  valores unitarios y la depreciación (GOB-03, H-14 y H-15), los aranceles de la ordenanza (D-02b)
  y el `% actualización`, que sigue sin fuente (D-11).
- `RT001ValorDeTerreno` —la única rama del grafo de NEG-05 §1 que se puede escribir entera— **no la
  llama nadie en producción**: su único uso es el corpus de casos.
- El único valorizador real del sistema es `ValorizacionDeObra`, de `licencias`, y su propio
  javadoc enumera lo que **no** hace: ni el +5 %, ni la depreciación, que son justo el resto de la
  fórmula predial.

De modo que «la corrida de valuación en `catastro`» no tiene hoy dos versiones que comparar: tiene
cero. Lo que ADR-0027 pide se construyó como **mecanismo** —§7, del lado receptor— y sus cuatro
cifras saldrían nulas con su motivo, no con un número. **El día que esas cuatro decisiones cierren,
lo que falta es el emisor; el receptor ya está probado.**

Lo que sí se pudo comparar —y es la línea base para ese día— está en §5.

### Y una corrección al enunciado, medida

El enunciado de esta etapa hablaba de **tres** claves foráneas que dejan de existir:
`declaracion_jurada`, `determinacion` y `cuenta_corriente_asiento`. Medidas contra el baseline son
**veinte**:

| Contra | Cuántas | Cuáles |
|---|---:|---|
| `predio` | **15** | `acta_fiscalizacion`, `anuncio`, `beneficio`, `certificado`, `declaracion_jurada`, `determinacion`, `determinacion_arbitrio`, `determinacion_predio_detalle`, `licencia_edificacion`, `licencia_funcionamiento`, `liquidacion_detalle`, `notificacion_administrativa`, `programa_muestra`, `resolucion_determinacion`, `transferencia` |
| `ficha_catastral` | **5** | `acta_fiscalizacion`, `declaracion_jurada`, `licencia_funcionamiento`, y **dos** de `resolucion_determinacion` (la ficha anterior y la nueva) |

Y de las tres que el enunciado nombraba, una **no existe**: `cuenta_corriente_asiento.predio_id`
nunca tuvo clave foránea. `V2` del monolito no la declaró, y #660 ya lo había dejado escrito al
medir por qué un asiento puede quedar apuntando a un predio que ya no está. `V6` no inventa un
`DROP` de algo que no hay, y lo dice en su cabecera.

## 3. El desglose de pruebas, y a dónde se fue cada una

Medido ejecutando con `cleanTest --no-build-cache --no-parallel` y contando los XML de
`*/build/test-results/test/TEST-*.xml`, no leyendo la salida de Gradle.

| | Antes (`rentas` solo) | `rentas` | `catastro` | Total |
|---|---:|---:|---:|---:|
| **Pruebas** | **3 667** | **3 246** | **945** | **4 191** |
| Fallos | 0 | 0 | 0 | 0 |

**`rentas` baja 421, y cuadra al número:**

```
3 667 (base) − 425 (el contexto `catastro`, que ahora corre en su repositorio)
            −  10 (los dos disparadores de catastro: titularidad y vigencias)
            −   4 (TitularPrincipalRepositoryJdbcTest, sustituido por 5)
            −   1 (el solape de fichas, que V72 impide y V6 se llevó)
            +  19 (nuevas: 5 proyección + 5 candado + 5 titular por el puerto
                   + 2 del AC 2 + 1 del AC 3 + 1 del padrón)
                                                                    = 3 246
```

**Ninguna prueba se perdió sin estar en otro sitio**, y conviene decir dónde:

| Se fue de `rentas` | Cuántas | Dónde está ahora |
|---|---:|---|
| El contexto acotado `catastro` entero | 425 | `catastro`, las mismas 425 |
| `TitularidadNoExcede100Test` y `VigenciasQueNoSePisanTest` | 10 | `catastro`, contra los disparadores de verdad —`V6` los retiró de esta base, así que aquí ya no podían medir nada— |
| `TitularPrincipalRepositoryJdbcTest` | 4 | Sustituida por `TitularPrincipalPorElPuertoTest`, **5 pruebas**: mide el mismo criterio por el puerto, y una más para el desempate que cambió |
| «el solape de fichas ya no se puede escribir», en `ConteoDeLaDeteccionTest` | 1 | `catastro` (`VigenciasQueNoSePisanTest`). Medía la restricción de exclusión de `V72`, que se fue con su tabla |
| La mitad de `AreaEnLaMismaFormaEntreModulosTest` que comparaba `FichaEncontradaResource` | 0 pruebas | La guarda estructural equivalente corre en `catastro`; aquí el par que queda son las dos puertas de `rentas`, que es donde #607 encontró las dos convenciones |

Módulo a módulo:

| Módulo | Antes | Ahora | Δ |
|---|---:|---:|---:|
| aplicacion | 130 | 130 | 0 |
| **catastro** | **425** | **0** | **−425** |
| coactiva | 197 | 197 | 0 |
| contribuyentes | 80 | 80 | 0 |
| cuentacorriente | 273 | 273 | 0 |
| dominio-compartido | 154 | 154 | 0 |
| esquema | 46 | 41 | −5 |
| fiscalizacion | 297 | 298 | +1 |
| indicadores | 57 | 57 | 0 |
| licencias | 285 | 285 | 0 |
| parametros | 54 | 54 | 0 |
| plataforma | 177 | 177 | 0 |
| rentas | 586 | 595 | +9 |
| sanciones | 240 | 240 | 0 |
| seguridad | 180 | 180 | 0 |
| tesoreria | 304 | 304 | 0 |
| valores | 181 | 181 | 0 |
| **TOTAL** | **3 667** | **3 246** | **−421** |

`kamayuk-rentas-catastro` queda con **cero pruebas y eso es correcto**: ya no tiene qué probar. De
sus 200 clases quedan **26 de producción** —los 19 puertos y las 7 del transporte— y 7 de fixtures.

## 4. El renombrado, y la única decisión que se aparta del enunciado

El enunciado pide `kamayuk.rentas.catastro.*` → **`kamayuk.catastro.*`**. **No se hizo así, y
conviene decir por qué**: colapsar el contexto sobre la raíz hace chocar dos `package-info.java`
—el del contexto acotado y el del dominio compartido, que declaran cosas distintas a Spring
Modulith— y funde `kamayuk.rentas.dominio` con `kamayuk.rentas.catastro.dominio`, que es
literalmente el límite que ARQ-01 §4 regla 6 pone entre ellos.

Lo que se hizo es la **regla mecánica de P5B**, `kamayuk.rentas.*` → `kamayuk.catastro.*` sobre
todo el árbol, que produce:

| Qué | De | A |
|---|---|---|
| Paquetes | `kamayuk.rentas.*` | `kamayuk.catastro.*` |
| El contexto acotado | `kamayuk.rentas.catastro` | `kamayuk.catastro.catastro` |
| Módulos Gradle | `kamayuk-rentas-<X>` | `kamayuk-catastro-<X>` |
| Raíz de la API | `/rentas/api/v1` | `/catastro/api/v1` (ADR-0030) |

`kamayuk.catastro.catastro` es redundante y es lo que la consistencia produce: en `normativa` el
contexto es `kamayuk.normativa.parametros`, y aquí el sistema y su único contexto se llaman igual.

**La comprobación es un `git grep` que no encuentra nada:**

```
$ grep -rn "kamayuk\.rentas\|kamayuk/rentas\|kamayuk-rentas" . --exclude-dir=build
(vacío)
```

Dos renombrados más, y los dos con precedente en P5B: `kamayuk-esquema` →
`kamayuk-catastro-esquema`, y `kamayuk-verificaciones` disuelto dentro de
`kamayuk-catastro-aplicacion` con su `ConfiguracionDelSgtm` pasando a llamarse
`ConfiguracionDeCatastro`.

---

## 5. Criterio 1, la parte que sí se pudo medir: el padrón comparado como archivo

`PadronDelEjercicioTest` construye el conjunto sellado, determina **doce contribuyentes con quince
predios** y escribe un CSV con la base afecta, la base imponible, el impuesto, el derecho de
emisión, la base ponderada de cada predio y las cuatro cuotas con su vencimiento. La cuenta la hace
la regla de producción; escribirla en la prueba habría medido la prueba.

Los doce casos cubren los tres tramos del art. 13, **sus dos fronteras exactas con un céntimo a
cada lado** (15 UIT = 82 500 y 60 UIT = 330 000), el mínimo imponible, el céntimo que no cabe en el
reparto de cuotas, y —lo que más importa aquí— **la ponderación por porcentaje de propiedad**, que
es el dato que sale de `titularidad`, o sea de `catastro`.

La misma clase corrió en un **worktree de `rentas@24c9ed0`** —el árbol anterior a P5C— sin una sola
adaptación. **No sustituye a `PadronRecalculadoTest`**, que es el de P5B y compara otra cosa: allí
lo que se movió fue de dónde salen los **parámetros**; aquí, de dónde sale el **predio**. Los dos
miden el mismo invariante por lados distintos y conviven.

```
$ diff /tmp/padron-ANTES.csv /tmp/padron-DESPUES.csv
$ shasum -a 256 /tmp/padron-ANTES.csv /tmp/padron-DESPUES.csv
225d0356656ec62d740254e6e9fd5ce2240f5127d8e637a4bbdc840b210c801d  /tmp/padron-ANTES.csv
225d0356656ec62d740254e6e9fd5ce2240f5127d8e637a4bbdc840b210c801d  /tmp/padron-DESPUES.csv
```

### Que la comparación muerde

| Rotura en el árbol de después | Resultado |
|---|---|
| Quitar la ponderación por `%` de propiedad (`afecto.por(cuota…)` → `afecto`) | **30 líneas de diff.** La copropiedad al 50 % pasa de base **100 000 a 200 000** y su impuesto de **270,00 a 870,00**; el contribuyente con tres cuotas parciales, de 386,11 a 1 576,67 |

**Lo que este archivo NO cubre, dicho aquí:** ninguna cifra que salga de una valuación, por lo que
§2 explica. Mide todo lo que el sistema **sí** calcula hoy — la ponderación, los tramos, el mínimo,
el redondeo y el cronograma — y por eso vale como línea base para quien termine la resta: si al
sustituir `PrediosDelContribuyente` por un cliente HTTP cambiara un céntimo, este diff lo diría.

---

## 6. Criterio 2 — la página y el conteo del mismo conjunto

`DeteccionRepositoryJdbc` leía `predio`, `sector` y `ficha_catastral` en la misma consulta que
pagina y cuenta. Con dos bases eso desaparece, y **componerlo en memoria ya se probó y falló**: la
conciliación contestaba «722 páginas, 14 422 elementos» y cero filas en todas (#631).

La salida es `V4` de `rentas`: `predio_ref` y `ficha_ref`, alimentadas por evento. Son **dos tablas
y no una** porque la pregunta del padrón no es «cuál es la ficha» sino «cuál regía el 31 de
diciembre»: una proyección con «la vigente» estaría fechada el día que se proyectó y contestaría con
ella una reclamación de 2024. Con las versiones y su rango, la resolución por fecha **se queda en el
`WHERE`**, que es lo único que hace que la página y el `count(*)` no puedan separarse.

`sector` deja de leerse de ninguna forma: la proyección lleva el **código** del sector, que es lo
que los filtros teclean.

### Cómo se demostró que muerde

| Rotura | Resultado |
|---|---|
| Que `CONTEO_CON_CONDICION` deje de aplicar el filtro de condición —el defecto de #631, exacto— | **7 en rojo de 21.** El sobre dice **7 donde debe decir 3** y **1 donde debe decir 0**, y recorrer las páginas que anuncia devuelve filas que no cumplen el filtro |

Se conservan las dos pruebas nuevas —una que recorre **todas** las páginas que el sobre anuncia y
comprueba que salen exactamente las que dice, y otra que exige que una condición sin filas conteste
**cero y no el padrón**—, porque son las dos mitades del defecto: #631 anunciaba páginas que no
existían *y* contaba lo que no devolvía.

### Y que la proyección no es más cara, medido

`ConteoDeLaDeteccionTest` mide páginas tocadas con `EXPLAIN (ANALYZE, BUFFERS)` sobre 14 422
predios en tres municipalidades. Sigue en verde sobre la proyección — con un hallazgo por el
camino: **el `ANALYZE` tiene que incluir `predio_ref` y `ficha_ref`**, y hacerlo antes de proyectar
dejaba el conteo del padrón pequeño en **78 páginas para veinticinco predios**, porque el
planificador prefiere recorrer una tabla de la que no sabe el tamaño.

---

## 7. Criterio 3 — el candado antes de emitir

`V5` de `rentas` estrena `valuacion_corrida` y `valuacion_predio`, y `CandadoDeEmision` las
comprueba **antes** de que `DeterminarPredialMasivo` recorra un solo contribuyente.

Comprueba **tres cosas y no una**, porque se arreglan de tres maneras distintas y decir la
equivocada manda a quien opera a buscar donde no es:

| Situación | Se arregla | Lo que dice |
|---|---|---|
| No hay cierre de corrida | corriendo la valuación, o mirando la cola | «`catastro` no ha cerrado su corrida de valuación» |
| El cierre llegó y faltan | esperando o reprocesando | «cerró con 5 y aquí han llegado 2. **Faltan 3**» |
| Están todas y no son las mismas | volviendo a valorizar | «NO son las que emitió… **esto no se arregla esperando**» |

**El conteo y la huella vienen CON el cierre y no se derivan aquí**: si `rentas` los calculara de lo
que recibió, comprobaría que lo que tiene es igual a lo que tiene.

Sale por HTTP como **409 y no 422**: un 422 dice «corrige lo que mandaste», y aquí no hay nada en la
petición que corregir — el sistema no está en estado de emitir.

### Cómo se demostró que muerde

| Rotura | Resultado |
|---|---|
| El candado deja de comprobar el **conteo** | 1 en rojo de 5, la suya |
| El candado deja de comprobar la **huella** | 1 en rojo de 5, la suya |
| El candado se niega **siempre** (el contraste) | **4 en rojo de 5.** Sin él, un candado que rechazara todo pasaría las tres pruebas de arriba y dejaría la emisión inalcanzable para siempre |
| **Quitar la llamada al candado** de `DeterminarPredialMasivo` | **VERDE en las 3 680 pruebas del backend.** El candado existía **sin estar puesto** |

La cuarta es el hallazgo. Se cierra con una prueba de capa web que monta la corrida masiva con la
valuación sin cerrar y exige 409; con ella, la misma rotura la nombra.

`valuacion_predio` lleva además un `CHECK` que obliga a elegir: **o las cuatro cifras, o el motivo
por el que no se pudo valorizar**. Hoy todas las filas llevan motivo, que es el estado real
(D-02a, D-11, GOB-03). Un cero ahí es indistinguible de un predio que no vale nada — el defecto que
#48 midió con la licencia que salía con «valor de obra 0,00».

### De sólo lectura, y lo sostiene el motor

`sgtm_app` no escribe ninguna de las cinco tablas nuevas: quien lo hace es
**`rol_ingestor_catastro`**, que se crea en `crear-roles.sql` y no atiende peticiones.
`ProyeccionDeSoloLecturaTest` lo mide contra el catálogo **además** de contra el intento, por lo
que #435 midió: RLS y `GRANT` son dos guardas independientes y las dos dan `42501`, así que el
síntoma no distingue cuál actuó.

Y una asimetría deliberada: el ingestor **sí** puede actualizar `valuacion_corrida` —una corrida
nueva reemplaza el cierre— y **no** `valuacion_predio`, porque un hecho sellado no se sustituye
(ADR-0027 §1). Tampoco puede borrar ninguna (regla 4), ni leer `contribuyente`,
`declaracion_jurada` o `cuenta_corriente_asiento`: si pudiera, la tentación sería componer la
proyección con un `JOIN` y volveríamos al cruce que esta etapa deshace.

---

## 8. Criterio 4 — las barreras, y los tres cruces que se cierran

Contra PostgreSQL **16.15 real** en `127.0.0.1:55444`, con `cleanTest`, `--no-build-cache` y
`--no-parallel`.

| Tarea | `catastro` | `rentas` |
|---|---|---|
| `./gradlew build` | **VERDE** — 945 pruebas | **VERDE** — 3 246 pruebas |
| `./gradlew verificarArquitectura` | **VERDE** | **VERDE** |
| `./gradlew verificarAislamiento` | **VERDE** | **VERDE** |

> **Hueco heredado, medido otra vez:** las pruebas de persistencia corrieron contra un **motor de
> verdad**, con RLS, `FORCE ROW LEVEL SECURITY` y los cinco roles reales, pero **no por el camino
> de Testcontainers**, que es el que corre en CI. Testcontainers no sirve desde esta máquina y está
> medido, no supuesto: el demonio es un túnel a un VPS, el contenedor arranca allí y su puerto se
> publica allí. Es el mismo hueco que declararon P3, P4, P5A y P5B.

### Tres de los cuatro cruces, cerrados

`V6` retiró de la base de `rentas` las quince tablas de `catastro`, así que **ninguna de las cuatro
clases que las leían puede seguir haciéndolo**. Cada una se cerró por el camino que su propia nota
de P3 anticipaba:

| Id | Clase | Cómo se cerró |
|---|---|---|
| `-01` | `DeteccionRepositoryJdbc` y `ConciliacionRepositoryJdbc` | Leen `predio_ref` y `ficha_ref`, la proyección local de `V4`. Iban juntas a propósito —es el mismo padrón, paginado en un caso y contado en el otro (#564)— y por eso se cierran juntas |
| `-04` | `TitularPrincipalRepositoryJdbc` | **Desaparece.** Lo sustituye `TitularPrincipalPorElPuerto`, que pregunta por `TitularesDelPredio`. Su nota avisaba del desempate, y ese matiz quedó escrito en la clase nueva |
| `-05` | `CuotaDeArbitrioRepositoryJdbc` | Traduce el código predial contra `predio_ref`. Su nota proponía un puerto HTTP; la proyección lo resuelve sin salir de la base, que es **mejor**: el filtro entra en el MISMO `WHERE` que el conteo |

**Queda una entrada**: `ReciboRepositoryJdbc` → `contribuyente`, `PENDIENTE-CRUCE-06`, que es de
`caja` y tiene D-17 abierta. `-02` y `-03` los había cerrado P5B.

Retirarlas no es un trámite: `ningunCruceConsentidoSobra` vuelve a escanear **sin** la lista y exige
que cada entrada siga eximiendo un cruce de verdad, así que dejar cualquiera de las siete la habría
puesto en rojo.

**Y `NINGUN_SQL_CRUZA_LA_FRONTERA_DE_SISTEMA` está en verde después de la baja**, con la lista
reducida a una: se comprobó ejecutando `verificarArquitectura` sobre el árbol ya sin las tablas.

En `catastro` la lista está **vacía y tiene que estarlo**, y las tres cosas que en el monolito
habrían entrado no entran por tres motivos distintos, escritos en la propia clase.

### El desajuste que la baja destapó en el contrato

Cuarenta operaciones de `/catastro/` estaban en `IMPLEMENTADAS` y ya no las publica este backend.
**Se quedan en el contrato y salen de esa lista**, que es lo mismo que P5B hizo con `GET
/seguridad/parametros` y lo que ya se hacía con `GET /portal/deuda`: el contrato describe lo que la
interfaz pide, y la interfaz las sigue pidiendo; lo que dejó de ser cierto es que las sirva
`rentas`.

**Tres NO salen, y conviene decir por qué**: `GET /catastro/fichas/conciliacion`, su `/resumen` y
`GET /catastro/predios/{predioId}/titulares` los publica `rentas` y los seguirá publicando — la
conciliación es un derivado de `declaracion_jurada`, que `catastro` no puede mirar sin depender de
`rentas` (ADR-0015), y los titulares los sirve `rentas` porque `contribuyentes` es la base del grafo
(#366, ADR-0015 §2.4).

## 9. La resta: qué se fue de `rentas`, y en qué quedó el módulo

### `V6__baja_de_catastro.sql`, en cuatro bloques y en este orden

**Primero las claves foráneas, después las tablas.** Al revés, PostgreSQL exigiría `CASCADE` sobre
cada `DROP TABLE`, y eso se llevaría por delante lo que apunte a ella **sin que se vea en el diff**.
Es la misma decisión que `V2` tomó en P5B y por el mismo motivo: aquí cada línea dice exactamente
qué garantía se retira.

1. **Las quince** contra `predio`, una a una y por nombre.
2. **Las cinco** contra `ficha_catastral`.
3. **Los tres disparadores** —el del arancel, que ya estaba roto desde P5B, y los dos invariantes
   de titularidad y participación—.
4. **Las quince tablas**, en orden de dependencia y **sin `CASCADE`**: si quedara algo apuntando a
   una de ellas, el `DROP` tiene que fallar y decirlo.
5. **Las dos funciones** que solo ellas usaban.

**Las columnas se quedan.** `declaracion_jurada.predio_id`, `determinacion.predio_id` y las demás
son la referencia al hecho de catastro que sustenta cada acto, y perderlas sería perder por qué se
determinó lo que se determinó. Lo único que se retira es la garantía del motor — literalmente el
costo que ADR-0029 nombra: «se paga una clave foránea por una invariante».

### Y una función NO se fue, y lo dijo el motor antes que ninguna revisión

`nombre_normalizado(text)` iba a irse con las otras dos. El `DROP` falló:

```
ERROR: cannot drop function nombre_normalizado(text) because other objects depend on it
Detail: index contribuyente_nombre_trgm_ix depends on function nombre_normalizado(text)
```

No es de catastro: la comparten `via.nombre_busqueda` (`V66`) y el índice de búsqueda por
aproximación del **padrón de contribuyentes** (`V11`, RF-014), que se queda aquí. Que los dos
sistemas la tengan cada uno en su esquema es lo correcto —es una función de texto, no una regla de
negocio— y `catastro` la conserva en su baseline por su lado. Queda escrito en `V6`.

### El módulo se queda, como ADAPTADOR CLIENTE

Es la opción (a) del encargo, y es lo que P5B hizo con `kamayuk-rentas-parametros`. De sus **200
clases** quedan **26 de producción**:

- **Los 19 puertos** del paquete raíz. **No se tocó ni uno**: ya eran el contrato, y por eso las
  **27 clases de `src/main`** que los consumen no cambiaron una línea. Ésa es la propiedad que
  ARQ-01 §4 compró y que aquí se cobró.
- **7 de transporte**: el cliente HTTP y los adaptadores.

Sin dominio, sin repositorio y **sin una sola consulta**. Si volviera a tener una, `rentas` leería
tablas de `catastro` y el escáner de frontera lo diría.

### SIETE de los nueve puertos no tienen hoy quien los conteste

Y hay que decirlo porque es la consecuencia visible de la resta. `catastro` publica hoy la grilla de
fichas, el listado de predios, el resumen predial y las escrituras de titularidad e inquilinos — y
**no** las rutas que ADR-0030 fija para esta frontera (`GET /predios/{id}/titulares`,
`/caracteristicas`, `POST /predios/{id}/titularidad`, `/transferencia-fiscal`).

Así que el cliente hace dos cosas distintas:

- **Dos puertos salen por HTTP** contra la ruta que `catastro` publica de verdad: la grilla de
  fichas y el cuadro de valores unitarios.
- **Los otros siete lanzan `SinRutaEnCatastro`**, que nombra la operación de ADR-0030 que los
  serviría. **No devuelven vacío**, y ahí está toda la decisión: una lista vacía de predios se lee
  como «este contribuyente no tiene ninguno» y un `Optional.empty()` como «este predio no tiene
  ficha». Las dos son plausibles y falsas — la determinación predial saldría con la base a cero y
  ninguna cifra parecería mal. Es el criterio de #48 con la licencia que salía con «valor de obra
  0,00», y el que `LectorDeValoresUnitarios` ya llevaba escrito.

**Esto no es una regresión que introduzca P5C: es la que P5C hace visible.** Mientras las tablas
seguían aquí, `rentas` era dueño de un catastro que ya vivía en otro repositorio y la frontera era
mentira. La clase `SinRutaTodavia` **es** la lista de trabajo pendiente de esta frontera, escrita
donde se ejecuta, y encoge cada vez que `catastro` publique una ruta.

## 10. La guarda del arancel, reconstruida (hueco 3 de P5B)

P5B retiró de `rentas` el disparador `arancel_de_conjunto_sellado_inmutable` **y su función**,
porque consultaba `conjunto_parametros`, que se fue a `normativa`: una función que consulta una
tabla inexistente no protege nada, revienta en el primer `INSERT`. Y lo dejó anotado: «hoy nada
impide cargar un arancel contra un conjunto ya sellado».

`V3` de `catastro` la reconstruye **contra `normativa_conjunto`**, la copia local que crea `V2`. La
equivalencia no es una suposición sino una propiedad del contrato de ADR-0025 §1: **`normativa` no
sirve un conjunto abierto**, así que una fila en la caché *significa* «este conjunto está sellado».
Por eso `normativa_conjunto` no tiene columna `estado`.

### Cómo se demostró que muerde

| Rotura | Resultado |
|---|---|
| Quitar el disparador (el estado exacto en que P5B dejó `arancel`) | **2 en rojo:** el `INSERT` y el `UPDATE` entran sin ruido |
| Mirar también el `ambito` en la condición | **2 en rojo.** El conjunto se descarga en dos mitades y basta una: mirarlo dejaría entrar un arancel mientras sólo estuviera bajada la otra |
| Quitar `AND c.municipalidad_id = v_muni` | **0 — VERDE**, y ése es el hallazgo (abajo) |

**Y una cuarta no hubo que provocarla.** Al reconstruir la guarda, `DatosDePrueba` cayó con «El
conjunto de parametros 1 esta sellado»: sembraba la caché **antes** del arancel. Tenía razón, y el
arreglo fue poner el orden real —el arancel contra un conjunto abierto; la descarga después de
sellar—, que es el de producción.

**El hallazgo de la tercera rotura**, escrito en la cabecera de `V3` para que nadie lo descubra dos
veces: esa cláusula es **redundante por el camino normal**. `normativa_conjunto` lleva RLS con
`FORCE` y el disparador **no** es `SECURITY DEFINER`, así que corre con el rol y el contexto de
quien escribe: la fila de la vecina no la ve. Quien sostiene la propiedad —«sellar en una
municipalidad no congela el arancel de la otra»— es RLS, no la cláusula. Se conserva porque cambia
algo para una conexión que omita RLS, y **se dice que ninguna prueba puede hacerla fallar** en vez
de dejar creer que protege algo.

Lo que esta guarda ve **menos** que la de `V18` también está escrito, y son tres cosas: un conjunto
sellado en `normativa` que esta base no haya descargado no se detecta; un `conjunto_id` que no
existe en ninguna parte ya no lo rechaza nadie —la clave foránea la retiró el propio generador del
baseline— y eso es literalmente el costo que ADR-0029 nombra; y sigue sin comprobar que la vía sea
del mismo conjunto.

---

## 11. El baseline: lo que le sobraba, con su diff

El generador de ADR-0032 restringe el esquema de `sgtm` a las tablas de este sistema pero arrastra
**toda** función del esquema original. **Se comprobó con el mismo método que P5B**, y sobraban
**cinco** — una más que en `normativa`:

| Función retirada | De quién es | Por qué sobra |
|---|---|---|
| `conjunto_sellado_es_inmutable()` | `normativa` | Consulta `conjunto_parametros`. Sin disparador aquí |
| `detalle_de_conjunto_sellado_es_inmutable()` | `normativa` | Íd. |
| `valuacion_de_publicacion_sellada_es_inmutable()` | `normativa` | Consulta `parametro_tributario` |
| `declaracion_jurada_estado_es_terminal()` | `rentas` | Sin disparador aquí |
| `valuacion_de_conjunto_sellado_es_inmutable()` | **catastro** | **Sí es de este sistema** —la usaba el disparador del arancel— pero su cuerpo consulta `conjunto_parametros`. Se va con su disparador, y `V3` la reconstruye |

El archivo pasa de **1 056 a 940 líneas**, más 34 de una nota en la cabecera que explica cada
retirada: **974**. `sgtm/docs/40-datos/baselines/catastro/V1__baseline.sql` **no se tocó**.

La quinta es la que enseña algo que P5B no vio: una función puede ser **del sistema correcto** y
estar rota igual. Lo que la delata no es de quién es, sino qué consulta.

---

## 12. Los hallazgos que salieron de mover, y no de razonar

**Una prueba dependía del ORDEN DE EJECUCIÓN, y sólo el cambio de paquete lo destapó.**
`SinNormativaFronteraTest` afirmaba que el repliegue a la caché devuelve el conjunto 7070; otra
prueba de la misma clase deja en la caché el 9090 del mismo ejercicio, y `conjuntoCacheadoDe`
resuelve con `ORDER BY version DESC, conjunto_id DESC`. Con las dos en versión 1 gana el
identificador más alto. JUnit ordena por un hash que incluye el **nombre completo** de la clase,
así que al pasar de `kamayuk.rentas` a `kamayuk.catastro` cambió el orden y la prueba se puso roja
—«expected: 7070 but was: 9090»— sin que nadie hubiera tocado ni el código ni la prueba. **La
dependencia de orden estaba desde que existe**, y en `rentas` sigue pasando en verde por
casualidad.

**El escenario de prueba necesita escribir a mano el filtro que en producción pone RLS.** Es el
primero de los dos defectos que P5B §11 documenta, y en esta etapa se cobró **cuatro veces**: en el
`JOIN` contra `contribuyente_de_prueba` —dos titulares al 100 % sobre el mismo predio—; en la
subconsulta del sector de `ConteoDeLaDeteccionTest` —«more than one row returned by a subquery»—;
en los cuatro fixtures que leen el escenario, que ahora resuelven su municipalidad por
`TenantContext`; y, el más caro, **en la guarda «ya está sembrado»** de la conciliación: `SELECT
count(*) FROM predio_de_prueba` sin filtro contaba los predios de las cuatro municipalidades del
archivo, de modo que ninguna volvía a sembrarse y siete pruebas medían un padrón vacío.

**Y una función que parecía de catastro no lo era, y lo dijo el motor.** `nombre_normalizado(text)`
iba a irse con `V6`; el `DROP` falló nombrando `contribuyente_nombre_trgm_ix`. Es de los dos
sistemas —la vía y el padrón— y ninguna revisión lo habría visto. §9.

---

## 13. Lo que se movió, archivo a archivo

| Qué | De | A |
|---|---|---|
| `kamayuk-rentas-catastro/` (200 clases) | `rentas` | `kamayuk-catastro-catastro` |
| `kamayuk-rentas-{dominio-compartido, plataforma, parametros}` | `rentas` | `kamayuk-catastro-*` (copias; ver hueco 4) |
| `DirectorioDeContribuyentes` + `ResumenDeContribuyente` | `rentas` | `kamayuk-catastro-contribuyentes`, **sólo el puerto**, con su cliente HTTP |
| `cargar-{predios, fichas-demo, detalle-fichas-demo, sectores, manzanas, catalogo-vial, transferencias-demo, arancel-vial}.sh` | `infrastructure` | `catastro/infra/carga-de-datos/` |
| `ejemplos/{vias, sectores, manzanas, fichas, detalle-de-fichas, transferencias}.csv` | `rentas` | `catastro/infra/carga-de-datos/ejemplos/` |
| `V1__baseline.sql` corregido | `sgtm/docs/40-datos/baselines/catastro/` | `kamayuk-catastro-esquema/…/db/migration/` |
| Las 15 tablas, sus 20 claves foráneas y sus 3 disparadores | `rentas` (`V6` los retira) | `catastro` (`V1` baseline) |
| `TitularidadNoExcede100Test` y `VigenciasQueNoSePisanTest` | `rentas` | `catastro`, contra los disparadores de verdad |

**Lo que NO se movió, y por qué:** `contribuyentes.csv` se queda en `rentas` y esta prueba lo lee
del repositorio hermano. Dos copias del mismo padrón de demostración divergen, y la que se leyera
decidiría contra qué se cruzan las fichas — que es el defecto que la siembra de #290 existe para no
tener. Es el mismo mecanismo con que `CorpusDeNormativa` lee el corpus tras P5B, y tiene su mismo
costo, declarado abajo.

---

## 14. Huecos declarados

1. **No hay corrida de valuación con cifras**, ni aquí ni en el monolito, y por eso el criterio 1 no
   se cumple. **Es de datos y no de código**: faltan el cuadro de valores unitarios y la
   depreciación (GOB-03, H-14 y H-15), los aranceles de ordenanza (D-02b), el `% actualización`
   (D-11) y que algún ejercicio esté sellado (D-02a). Lo que sí está construido es **la mitad
   receptora** —las tablas, el candado y su forma, §7— y la línea base con la que comparar el día
   que el emisor exista (§5).

2. **Siete de los nueve puertos no tienen ruta que los conteste** (§9). `catastro` publica hoy la
   grilla de fichas, el listado de predios, el resumen predial y las escrituras de titularidad; las
   de ADR-0030 —titulares, características, titularidad, transferencia fiscal, valuación— no. Los
   siete **lanzan nombrando la operación que los serviría**, y `SinRutaTodavia` es esa lista.
   **Es lo más caro que deja esta etapa**, y es lo que la resta hizo visible.

3. **El ingestor de eventos no existe.** Lo que hay es el esquema —el buzón deduplicado por
   `evento_id`, la secuencia, los privilegios— y cinco fixtures que hacen su papel en las pruebas.
   No hay cola, no hay suscripción, no hay reintento. Su forma está fijada por `V4` y por el rol;
   su transporte, no.

4. **Tres copias de la plataforma y del dominio compartido.** `rentas`, `normativa` y ahora
   `catastro` llevan cada uno los suyos (331 pruebas duplicadas por repositorio). Es el hueco 1 de
   P5B agravado en un tercio: **nada impide que diverjan**, y el riesgo es el que ADR-0024 §3
   nombra. Sacar `dominio-compartido` a librería compartida son 938 archivos de renombrado sólo en
   `rentas`.

5. **`catastro` no compila sus pruebas sin `rentas` clonado al lado**, por `contribuyentes.csv`
   (§13). Es el hueco 2 de P5B con los papeles cambiados, y **el CI de `catastro` tendría que hacer
   checkout de tres repositorios**; no se ha escrito en su `backend.yml`, porque los workflows no
   se empujan desde esta sesión.

6. **El cliente reenvía el token, no lo intercambia.** ADR-0028 §1 pide un token **delegado** por
   RFC 8693 con la audiencia del destino. `ClienteHttpDeCatastro` y `DirectorioHttpDeRentas`
   reenvían el del funcionario tal cual: conservan el sujeto y el claim `municipalidad_id` —o sea
   son correctos en lo que importa, el aislamiento— y pierden que la bitácora del destino distinga
   «lo pidió catastro en nombre de fulano» de «lo pidió fulano». Y **en una corrida sin usuario
   delante no hay token**: la llamada sale sin credencial y el destino la rechaza, que es
   deliberado. ADR-0028 §2 dice cómo se cierra.

7. **`catastro` no tiene contrato de API derivado.** Es el hueco 5 de P5B con el mismo argumento: el
   generador de `rentas` deriva del prototipo del manual (#312) y aquí no hay prototipo del que
   derivar. Las tres pruebas que lo sujetan en `rentas` —`ContratoDeApiTest`, `FormasDeLaApiTest`,
   `RespuestasDeLaApiTest`— no se copiaron. **Y con la baja, dos promesas de `ParametrosDeLaConsulta`
   se quedaron sin dueño**: las del plano catastral (#536, #612), que este backend ya no publica; la
   promesa tiene que recogerla `catastro`, y hasta que tenga contrato no puede.

8. **La secuencia de siembra de la municipalidad de demostración ya no la orquesta nadie.**
   `sembrar-demostracion.sh` nombraba diez pasos en un solo orden y vive en `infrastructure`; ahora
   los archivos están repartidos en tres repositorios y **el paso 4 cruza la frontera** —
   `fichas.csv` nombra titulares del padrón de `rentas`—. Sembrar `catastro` sin haber sembrado
   antes ese padrón **no revienta: rechaza todas las fichas y termina en verde**. Está escrito en
   `infra/carga-de-datos/README.md` y no está resuelto.

9. **`rol_ingestor_catastro` se crea sin `LOGIN` y nadie le asigna clave.** Igual que los otros
   cuatro, y por lo mismo, pero **no está en el inventario de secretos de INF-06**
   (`infra/componentes/secretos.ts`, donde sí está `rol_carga_parametros`) ni en
   `asignar-claves.sh`. Hasta que lo esté, el ingestor no se puede conectar en un ambiente
   desplegado.

10. **`P3-safeguards.md` sigue describiendo seis identificadores abiertos.** Hoy es **uno**: P5B
    cerró `-02` y `-03`, y P5C cierra `-01`, `-04` y `-05`. El documento es el registro de su etapa
    y no se editó; la lista viva es `CrucesConsentidosDelSgtm`, que es la que se pone roja.

11. **La grilla de fichas y la titularidad, en las pruebas de `rentas`, se miden contra un fixture
    y no contra la consulta de producción.** Es inevitable —esa consulta vive en `catastro`— y lo
    que se conserva es lo que sigue siendo de `rentas`: que **componga** bien la acotación por
    predio (#631) y que la fecha **viaje**. Lo que ya no se mide aquí es que la página y el
    `count(*)` de catastro salgan del mismo `WHERE`; eso lo miden sus 425 pruebas. **El recuento de
    la conciliación sí se sigue midiendo contra PostgreSQL**, porque lee `predio_ref`.

12. **El desempate del titular principal cambió** (`PENDIENTE-CRUCE-04`). El SQL desempataba por el
    `id` de la fila de titularidad; el puerto no lo publica, así que ahora se desempata por
    `contribuyenteId`. Lo que cambia es a cuál de dos copropietarios **empatados** se le cobra el
    arbitrio; lo que no cambia —y lo mide una prueba— es que la elección sea la misma en dos
    corridas.

## 15. Cómo sembrar el escenario en una prueba, ahora que el catastro no está

Tres piezas, y conviene entenderlas juntas porque son el equivalente de P5B §11:

1. **`EscenarioDelPadron`** (fixtures del esquema de `catastro`) crea `contribuyente_de_prueba`,
   réplica de la tabla que se fue. El sufijo **no es cosmético**: conservarle el nombre habría
   dejado ocho clases con SQL contra `contribuyente`, y quien lo leyera concluiría que el padrón
   sigue aquí. Su `municipalidad_id` es **anulable a propósito** — si fuera `NOT NULL`,
   `AislamientoMultiTenantTest` le exigiría RLS sola, y estaríamos manteniendo la política de una
   tabla que no es de nadie.
2. **`ProyeccionDeCatastro`** (fixtures del esquema de `rentas`) hace correr al ingestor: **lee con
   `sgtm_app` y escribe con `rol_ingestor_catastro`**, en dos conexiones. El rodeo es fiel — el
   ingestor no tiene privilegio sobre `predio` y no debe tenerlo, porque el de producción recibe
   los datos dentro del evento.
3. **La llamada va justo antes de preguntar**, no al final de cada siembra. Así el desfase de la
   proyección es **visible** en el código de la prueba; una siembra que proyectara sola dejaría
   creer que la proyección se actualiza en la misma transacción, que es exactamente lo que no pasa.
