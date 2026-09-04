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
| **1** | La corrida de valuación en `catastro` más la de emisión en `rentas` producen **el mismo padrón, céntimo a céntimo**, que la del monolito | **NO SE CUMPLE.** §2 | Lo que sí se midió: el padrón que el sistema **sí sabe calcular hoy** sale byte a byte idéntico antes y después, `sha256 225d0356…`. §5 |
| **2** | La página de omisos pagina y cuenta lo filtrado | **Cumplido** | Con el conteo dejando de filtrar —el defecto de #631 exacto— el sobre dice **7 donde debe decir 3** y **1 donde debe decir 0**. §6 |
| **3** | Con el ingestor detenido a mitad, `rentas` **se niega** a emitir y dice por qué | **Cumplido** | Cuatro roturas del candado y una quinta que destapó que existía sin estar **puesto**. §7 |
| **4** | Los tres verificadores bloqueantes en verde en los dos repositorios, y las excepciones nominadas de P3 que este prompt cierra, cerradas | **Cumplido** | Los seis en verde. `PENDIENTE-CRUCE-01` sale de la lista. §8 |

---

## 2. Criterio 1 — por qué no se cumple, y el paso exacto

**Se paró en la resta.** `rentas` **todavía contiene** `kamayuk-rentas-catastro` con sus 200 clases,
sus 15 tablas y sus 425 pruebas. La migración de baja `V3__baja_de_catastro.sql` **no se escribió**.

Lo que falta, enumerado para que quien siga no lo tenga que volver a medir:

1. **Vaciar `kamayuk-rentas-catastro` hasta dejar los nueve puertos** —`FichasDelPadron`,
   `LectorDeFichas`, `LectorDeCaracteristicas`, `TitularesDelPredio`, `GestorDeTitularidad`,
   `TransferenciaDeFiscalizacion`, `PrediosDelContribuyente`, `LectorDeFichasEconomicas`,
   `LectorDeValoresUnitarios`— e implementarlos con clientes HTTP. **Las 27 clases de `src/main`
   que los consumen no cambian**: importan el puerto, no la implementación. Eso ya está
   comprobado — es la misma forma con que `catastro` consume ahora el padrón de `rentas`.
2. **Retirar las claves foráneas.** El enunciado dice **tres**; medidas contra el baseline son
   **veinte**: quince contra `predio` (`acta_fiscalizacion`, `anuncio`, `beneficio`,
   `certificado`, `declaracion_jurada`, `determinacion`, `determinacion_arbitrio`,
   `determinacion_predio_detalle`, `licencia_edificacion`, `licencia_funcionamiento`,
   `liquidacion_detalle`, `notificacion_administrativa`, `programa_muestra`,
   `resolucion_determinacion`, `transferencia`) y cinco contra `ficha_catastral`. Y
   **`cuenta_corriente_asiento.predio_id` nunca tuvo ninguna** — `V2` no la declaró, cosa que #660
   ya había dejado escrita.
3. **Re-fixturizar 54 clases de prueba** de `rentas` que siembran `predio`, `ficha_catastral`,
   `titularidad`, `via`, `sector` o `manzana`. El mecanismo está probado en este mismo trabajo:
   tablas `_de_prueba` como las de `EscenarioDeNormativa` (P5B §11), que aquí ya se usó para el
   padrón (`EscenarioDelPadron`).

**Y aunque la resta estuviera hecha, el criterio 1 seguiría sin poder cumplirse como está escrito,
por un motivo que no es de esta etapa:** *no existe ninguna corrida de valuación con cifras, ni
aquí ni en el monolito*. Medido leyendo el código:

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
cero. Lo que ADR-0027 pide se puede construir como **mecanismo** —y §7 lo construye del lado de
`rentas`— pero sus cuatro cifras saldrían nulas con su motivo, no con un número.

---

## 3. El desglose de pruebas

Medido ejecutando con `cleanTest --no-build-cache --no-parallel` y contando los XML de
`*/build/test-results/test/TEST-*.xml`, no leyendo la salida de Gradle.

| | Antes (`rentas` solo) | `rentas` | `catastro` | Total |
|---|---:|---:|---:|---:|
| **Pruebas** | **3 667** | **3 681** | **945** | **4 626** |
| Fallos | 0 | 0 | 0 | 0 |

`rentas` **sube 14 y no baja**, y eso es exactamente lo que dice que la resta no está hecha: las
425 del contexto `catastro` siguen ahí. Las 14 nuevas son 5 de `ProyeccionDeSoloLecturaTest`, 5 de
`CandadoDeEmisionJdbcTest`, 2 del AC 2 en `DeteccionDeOmisosJdbcTest`, 1 del AC 3 en
`PredialControllerTest` y 1 de `PadronDelEjercicioTest`.

Módulo a módulo en `catastro`:

| Módulo | Pruebas |
|---|---:|
| `kamayuk-catastro-aplicacion` (las barreras) | 84 |
| `kamayuk-catastro-catastro` (el contexto) | **425** |
| `kamayuk-catastro-dominio-compartido` | 154 |
| `kamayuk-catastro-esquema` | 51 |
| `kamayuk-catastro-parametros` | 54 |
| `kamayuk-catastro-plataforma` | 177 |
| **TOTAL** | **945** |

Las 425 del contexto son **las mismas 425** que `rentas` tenía tras P5B: ni una prueba de negocio
se perdió al mover. Las 51 del esquema son las 46 de antes más las 5 de la guarda del arancel.

---

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

## 8. Criterio 4 — las barreras, y el cruce que se cierra

Contra PostgreSQL **16.15 real** en `127.0.0.1:55444`, con `cleanTest`, `--no-build-cache` y
`--no-parallel`.

| Tarea | `catastro` | `rentas` |
|---|---|---|
| `./gradlew build` | **VERDE** — 945 pruebas | **VERDE** — 3 681 pruebas |
| `./gradlew verificarArquitectura` | **VERDE** | **VERDE** |
| `./gradlew verificarAislamiento` | **VERDE** | **VERDE** |

> **Hueco heredado, medido otra vez:** las pruebas de persistencia corrieron contra un **motor de
> verdad**, con RLS, `FORCE ROW LEVEL SECURITY` y los cinco roles reales, pero **no por el camino
> de Testcontainers**, que es el que corre en CI. Testcontainers no sirve desde esta máquina y está
> medido, no supuesto: el demonio es un túnel a un VPS, el contenedor arranca allí y su puerto se
> publica allí. Es el mismo hueco que declararon P3, P4, P5A y P5B.

### `PENDIENTE-CRUCE-01`, cerrado

`DeteccionRepositoryJdbc` y `ConciliacionRepositoryJdbc` —las dos juntas a propósito, porque es el
mismo padrón paginado en un caso y contado en el otro (#564)— dejan de leer tablas de `catastro`.
Sus **cinco entradas** salen de `CrucesConsentidosDelSgtm`, y salen porque
`ningunCruceConsentidoSobra` vuelve a escanear **sin** la lista y las habría puesto en rojo.

Quedan tres: `-04`, `-05` y `-06`.

En `catastro` la lista está **vacía y tiene que estarlo**, y las tres cosas que en el monolito
habrían entrado no entran por tres motivos distintos, escritos en la propia clase.

---

## 9. La guarda del arancel, reconstruida (hueco 3 de P5B)

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

## 10. El baseline: lo que le sobraba, con su diff

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

## 11. Los dos hallazgos que salieron de mover, y no de razonar

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
primero de los dos defectos que P5B §11 documenta, repetido aquí exactamente: sin
`AND c.municipalidad_id = ?` en el `JOIN` contra `contribuyente_de_prueba`, la segunda municipalidad
casa también con los contribuyentes de la primera y el predio sale con **dos titulares al 100 %** —
lo caza el disparador de titularidad, que tiene razón.

---

## 12. Lo que se movió, archivo a archivo

| Qué | De | A |
|---|---|---|
| `kamayuk-rentas-catastro/` (200 clases) | `rentas` | `kamayuk-catastro-catastro` |
| `kamayuk-rentas-{dominio-compartido, plataforma, parametros}` | `rentas` | `kamayuk-catastro-*` (copias; ver hueco 4) |
| `DirectorioDeContribuyentes` + `ResumenDeContribuyente` | `rentas` | `kamayuk-catastro-contribuyentes`, **sólo el puerto**, con su cliente HTTP |
| `cargar-{predios, fichas-demo, detalle-fichas-demo, sectores, manzanas, catalogo-vial, transferencias-demo, arancel-vial}.sh` | `infrastructure` | `catastro/infra/carga-de-datos/` |
| `ejemplos/{vias, sectores, manzanas, fichas, detalle-de-fichas, transferencias}.csv` | `rentas` | `catastro/infra/carga-de-datos/ejemplos/` |
| `V1__baseline.sql` corregido | `sgtm/docs/40-datos/baselines/catastro/` | `kamayuk-catastro-esquema/…/db/migration/` |

**Lo que NO se movió, y por qué:** `contribuyentes.csv` se queda en `rentas` y esta prueba lo lee
del repositorio hermano. Dos copias del mismo padrón de demostración divergen, y la que se leyera
decidiría contra qué se cruzan las fichas — que es el defecto que la siembra de #290 existe para no
tener. Es el mismo mecanismo con que `CorpusDeNormativa` lee el corpus tras P5B, y tiene su mismo
costo, declarado abajo.

---

## 13. Huecos declarados

1. **LA RESTA NO ESTÁ HECHA.** `rentas` conserva el contexto `catastro` entero, sus 15 tablas y sus
   20 claves foráneas. §2 enumera los tres pasos que faltan. **Mientras eso siga así, no hay dos
   sistemas: hay uno y una copia**, y la proyección de `V4` convive con las tablas que proyecta.

2. **No existe la corrida de valuación de ADR-0027 en `catastro`.** Ni sus rutas
   (`/valuaciones/{ejercicio}`, `/huellas`, `POST /corridas-de-valuacion`), ni
   `ValuacionDePredioPublicada`, ni `CorridaDeValuacionCerrada` del lado emisor. Lo que sí está es
   **la mitad receptora**, en `rentas`: las tablas, el candado y su forma. El motivo no es de
   tiempo sino de datos: §2 lo mide. **El día que D-02a, D-02b, D-11 y GOB-03 cierren, lo que hay
   que construir es el emisor, y el receptor ya está probado.**

3. **El ingestor de eventos no existe.** Lo que hay es el esquema —el buzón deduplicado por
   `evento_id`, la secuencia, los privilegios— y un fixture, `ProyeccionDeCatastro`, que hace su
   papel en las pruebas leyendo de las tablas que todavía conviven. No hay cola, no hay
   suscripción, no hay reintento. Su forma está fijada por `V4` y por el rol; su transporte, no.

4. **Tres copias de la plataforma y del dominio compartido.** `rentas`, `normativa` y ahora
   `catastro` llevan cada uno los suyos (331 pruebas duplicadas por repositorio). Es el hueco 1 de
   P5B agravado en un tercio: **nada impide que diverjan**, y el riesgo que eso trae es el que
   ADR-0024 §3 nombra. Sacar `dominio-compartido` a librería compartida son 938 archivos de
   renombrado sólo en `rentas`.

5. **`catastro` no compila sus pruebas sin `rentas` clonado al lado**, por `contribuyentes.csv`
   (§12). Es el hueco 2 de P5B con los papeles cambiados, y **el CI de `catastro` tendría que hacer
   checkout de tres repositorios**; no se ha escrito en su `backend.yml`, porque los workflows no
   se empujan desde esta sesión.

6. **El cliente del padrón reenvía el token, no lo intercambia.** ADR-0028 §1 pide un token
   **delegado** por RFC 8693 con la audiencia de `rentas`. `DirectorioHttpDeRentas` reenvía el del
   funcionario tal cual: conserva el sujeto y el claim `municipalidad_id` —o sea es correcto en lo
   que importa, el aislamiento— y pierde que la bitácora de `rentas` distinga «lo pidió catastro en
   nombre de fulano» de «lo pidió fulano». Y **en una corrida sin usuario delante no hay token**:
   la llamada sale sin credencial y `rentas` la rechaza, que es deliberado — preferimos que falle a
   que una corrida nocturna se invente una identidad. ADR-0028 §2 dice cómo se cierra.

7. **`catastro` no tiene contrato de API derivado.** Es el hueco 5 de P5B con el mismo argumento: el
   generador de `rentas` deriva del prototipo del manual (#312) y aquí no hay prototipo del que
   derivar. Las tres pruebas que lo sujetan en `rentas` —`ContratoDeApiTest`, `FormasDeLaApiTest`,
   `RespuestasDeLaApiTest`— **no se copiaron**, y sus entradas se retiraron de `tasks.test` con su
   motivo escrito.

8. **La secuencia de siembra de la municipalidad de demostración ya no la orquesta nadie.**
   `sembrar-demostracion.sh` nombraba diez pasos en un solo orden y vive en `infrastructure`; ahora
   los archivos están repartidos en tres repositorios y **el paso 4 cruza la frontera** —
   `fichas.csv` nombra titulares del padrón de `rentas`—. Sembrar `catastro` sin haber sembrado
   antes ese padrón **no revienta: rechaza todas las fichas y termina en verde**. Está escrito en
   `infra/carga-de-datos/README.md` y no está resuelto.

9. **`rol_ingestor_catastro` se crea sin `LOGIN` y nadie le asigna clave.** Igual que los otros
   cuatro, y por lo mismo (`crear-roles.sql` no lleva claves), pero **no está en el inventario de
   secretos de INF-06** ni en `asignar-claves.sh`. Hasta que lo esté, el ingestor no se puede
   conectar en un ambiente desplegado.

10. **`P3-safeguards.md` sigue describiendo seis identificadores abiertos.** Hoy son tres: P5B
    cerró `-02` y `-03`, y P5C cierra `-01`. El documento es el registro de su etapa y no se editó;
    la lista viva es `CrucesConsentidosDelSgtm`, que es la que se pone roja.

---

## 14. Cómo sembrar el escenario en una prueba, ahora que el padrón no está

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
