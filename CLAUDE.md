# `catastro` — Contexto para agentes

Predio, ficha versionada, construcciones, titularidad, geometría, catálogo vial y arancel de
terreno. **Calcula el valor del predio; no calcula el impuesto.**

Uno de los cinco repositorios de **Kamayuk**, el producto multi-municipal que reimplementa el
sistema documentado en el manual de usuario del SGTM de la Municipalidad Provincial de Sullana.
El reparto lo decide
[ADR-0029](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0029-cuatro-sistemas-separados.md);
qué tabla fue a qué repositorio y por qué, [GOB-05](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/docs/00-gobierno/inventario-del-corte.md).

## Qué hay hoy, medido y no supuesto

| Pieza | Estado |
|---|---|
| `infrastructure/` — el descriptor de despliegue | **Existe.** `yarn verificar` en verde, sin Pulumi, sin token y sin clúster |
| `backend/` — siete módulos | **Existe desde P5C.** `kamayuk-catastro-{dominio-compartido, esquema, plataforma, parametros, contribuyentes, catastro, aplicacion}` |
| **Código de negocio** | **Existe.** El contexto acotado `catastro` entero: predio, ficha versionada, construcciones, titularidad, inquilinos, catálogo vial, las cuatro clases de ficha, la geometría y el arancel |
| Su esquema | **Existe.** `V1__baseline.sql` (ADR-0032) más `V2` —la copia local de conjuntos sellados— y `V3` —la guarda del arancel reconstruida— |
| `./gradlew build` | **VERDE — 945 pruebas, 0 fallos**, contra PostgreSQL 16.15 real |
| `verificarArquitectura` / `verificarAislamiento` | **VERDE los dos** |
| Su frontend (`catastro-web`) | **NO existe** |
| Su imagen `ghcr.io/hneyra/kamayuk-catastro` | **NO existe.** El descriptor la nombra igual, y es correcto: aquí no se despliega nada todavía |
| **Carga cartográfica** | **NO hay ni un polígono** en ninguna instalación. `V61` trajo la columna; nada la llena todavía |
| **La resta en `rentas`** | **Hecha.** Su `V6` retiró las **20** claves foráneas —el enunciado decía tres— y las 15 tablas; su módulo `kamayuk-rentas-catastro` quedó como adaptador cliente: 26 clases, sin dominio y sin una sola consulta |
| **La corrida de valuación de ADR-0027** | **NO existe.** Y no es un olvido de la extracción: el sistema **no sabe valorizar un predio** —faltan el cuadro de valores unitarios y la depreciación (GOB-03 H-14/H-15), los aranceles de ordenanza (D-02b) y el `% actualización` (D-11)—. Está declarado en `docs/00-gobierno/P5C-extraccion.md` |
| **Las rutas de ADR-0030 para la frontera** | **NO se publican todavía.** `catastro` sirve la grilla de fichas, el listado de predios y las escrituras; **siete de los nueve puertos que `rentas` consume no tienen quien los conteste**, y lo dicen en vez de devolver vacío |

**Las barreras se construyeron primero, a propósito**, y el negocio entró después, por encima de
ellas. Lo que P5C midió al hacerlo está en
[`docs/00-gobierno/P5C-extraccion.md`](docs/00-gobierno/P5C-extraccion.md), con sus cuatro
criterios y sus huecos declarados.

## Lo que este repositorio NO hace

- **No calcula impuesto, no lee deuda y no sabe lo que es una deducción.** Lo dice
  [ADR-0024](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0024-la-frontera-del-calculo.md)
  con todas las letras, y es lo que permite abrir su API a desarrollo urbano sin abrir con ella el
  padrón tributario. **Si esa arista creciera, lo que hay que revisar es la frontera.**
- **No deriva el área del terreno del polígono.** La que vale es la que midió el técnico
  ([ADR-0021](docs/30-arquitectura/adr/ADR-0021-la-geometria-del-predio.md)): derivarla cambiaría
  el autovalúo de todo el padrón sin que nadie lo decidiera, y un área es indistinguible de otra
  al leerla.
- **No sella un valor normativo.** Eso es `normativa`; aquí se **consume** un conjunto sellado.
- **No decide la etiqueta de su imagen, ni su namespace, ni sus `PriorityClass`.** Las pone `infrastructure`.
- **No tiene `git log` de su historia.** La tiene `sgtm`, que no se borra.

## Estructura

```
backend/                Gradle. Java 25, Spring Boot 4 cuando llegue el negocio
  kamayuk-catastro-dominio-compartido/  objetos de valor. Sin Spring, sin contexto acotado
  kamayuk-catastro-esquema/     V1 baseline, V2 cache de normativa, V3 guarda del arancel
  kamayuk-catastro-plataforma/  el contexto de tenant hasta la transaccion (ARQ-03 §2)
  kamayuk-catastro-parametros/  el CLIENTE de normativa y su copia local sellada
  kamayuk-catastro-contribuyentes/  SOLO el puerto al padron de `rentas`, y su cliente HTTP
  kamayuk-catastro-nucleo/      el contexto acotado. 200 clases. Se llamaba `catastro` (R-N)
  kamayuk-catastro-aplicacion/  ensambla el artefacto y aloja las barreras
infrastructure/         el descriptor de despliegue en TypeScript, con yarn
docs/                   ADR propios, hallazgos de RLS y esta guía de desarrollo
```

El backend **no compila sin `infrastructure` clonado al lado**: las barreras se consumen como
*composite build* desde `../../infrastructure/librerias-backend`. `settings.gradle.kts` lo
comprueba antes y falla diciendo qué `git clone` falta, en vez de dejar reventar a Gradle sobre un
directorio que no está.

Los paquetes son `kamayuk.catastro.*`; los módulos, `kamayuk-*`. Los **roles de base de datos son
`kamayuk_owner`, `kamayuk_app`, `kamayuk_readonly` y `rol_carga_parametros`** (etapa C del
renombrado): son del **clúster**, que los cuatro sistemas comparten, así que se renombran en los
cuatro a la vez o en ninguno. Su base es la única con
**PostGIS** y `btree_gist`.

## Antes de escribir código, leer

| Si vas a tocar… | Lee |
|---|---|
| Cualquier cosa | [ADR-0002 — Estrategia multi-tenant](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0002-estrategia-multi-tenant.md) — es el riesgo número uno |
| Base de datos | [Los cinco hallazgos de RLS](docs/40-datos/hallazgos-de-rls.md) **primero**, y `../srtm/docs/40-datos/ddl/esquema-verificado.sql` para tipos y longitudes |
| Geometría | [ADR-0021](docs/30-arquitectura/adr/ADR-0021-la-geometria-del-predio.md) y [ADR-0022](docs/30-arquitectura/adr/ADR-0022-el-visor-del-plano-catastral.md). **Bajo RLS el operador espacial no llega al índice, y el plan sigue diciendo «Index»** |
| Valuación | [ADR-0027](docs/30-arquitectura/adr/ADR-0027-la-valuacion-es-un-hecho-sellado.md) |
| La frontera con rentas | [ADR-0024](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0024-la-frontera-del-calculo.md) |
| Backend | [ARQ-04 — Estándares de código](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/estandares-de-codigo-backend.md) |
| Montar el entorno | [D0 — Desarrollo](docs/D0-desarrollo/README.md) |

Índice de decisiones: [`docs/30-arquitectura/adr/README.md`](docs/30-arquitectura/adr/README.md).

## Decisiones abiertas que bloquean

Registro completo en [GOB-02](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/docs/00-gobierno/decisiones-abiertas.md).

| # | Decisión | Bloquea |
|---|---|---|
| D-10 | **Longitud exacta del código de referencia catastral**: la plantilla del manual da 23 posiciones y los ejemplos del prototipo, otra cosa | El baseline y toda búsqueda por prefijo |
| D-18 | La clave foránea que se pierde al separar `catastro` | El baseline |
| D-21 | Dónde se aplica el **`% de propiedad`**: aquí o en `rentas` | La frontera de ADR-0024 |
| D-11 | Los factores sin fuente del cuadro de valuación | La valorización |
## Reglas que no se negocian

Son las mismas en los cinco repositorios, y las verifica **el mismo artefacto**:
[`comun-verificaciones`](https://github.com/hneyra/infrastructure/tree/main/librerias-backend/comun-verificaciones),
que vive en `infrastructure` y se consume como *composite build*.

| # | Regla | Motivo |
|---|---|---|
| 1 | **Importes en `BigDecimal`/`NUMERIC`.** Prohibidos `double` y `float` | Precisión monetaria (RNF-055) |
| 2 | **Ningún método de dominio recibe `municipalidadId`.** Sale del token, se fija una vez con `SET LOCAL` | Si el desarrollador no lo maneja, no puede olvidarlo |
| 3 | **`SET LOCAL`, jamás `SET SESSION`** | `SET SESSION` sobrevive al retorno de la conexión al pool y contamina la petición de otra municipalidad |
| 4 | **Sin `DELETE`** en deuda, pagos, recibos, valores, valuaciones, asientos ni auditoría. Se anula, se da de baja o se reversa | RNF-051, y el manual §Auditoría |
| 5 | **Ningún literal numérico tributario en el código.** UIT, tramos, alícuotas, valores unitarios, aranceles y tablas de depreciación viven en datos versionados | Reproducibilidad y cambio sin despliegue (RNF-053) |
| 6 | **Las reglas tributarias son funciones puras.** Sin base de datos, sin reloj, sin configuración global; la fecha entra como argumento | Recalcular 2027 en 2037 debe dar el mismo céntimo |
| 7 | **Nada de Spring ni JPA en la capa `dominio`** | Las reglas deben probarse sin levantar el contexto |
| 8 | **`alicuota`, nunca `tasa`**, para un porcentaje | `tasa` es un tipo de tributo |
| 9 | **No existe «la deuda»:** es `deudaActualizadaA(fecha)`, y toda cifra mostrada indica su fecha | RNF-075 |
| 10 | **Toda modificación de datos exige observación del usuario.** Sin observación no se guarda | Manual §Auditoría; RNF-052 |

Las reglas 1, 2, 6, 7 y las fechas están escritas como pruebas de ArchUnit; `SET SESSION` y
`DELETE` sobre tabla protegida, como escáner del código fuente. Se añade una **undécima**, que
sólo existe desde que hay cinco repositorios: **ningún SQL cruza la frontera de sistema** —un
`JOIN` contra una tabla de otro sistema no deja huella en el bytecode, así que la vigila un
escáner de texto y no ArchUnit—.

**Si agregas una regla, agrega también la clase de muestra que la viola**, en las `muestras/` de
`comun-verificaciones`: una regla que no puede fallar no protege nada. Y lo exige por
construcción `ReglasDeArquitecturaMuerdenTest`, un `@TestFactory` sobre todas las reglas: una
regla sin muestra sale roja sola.

Lista completa con su justificación:
[ARQ-04 — Estándares de código del backend](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/estandares-de-codigo-backend.md).

## Idioma

Español en el dominio, inglés en lo técnico. **Sin tildes en identificadores**: Checkstyle lo
revisa en el backend, ESLint en el descriptor.

```java
public final class Papeleta { … }                  // dominio: español
public interface PapeletaRepository { … }          // patrón: inglés
autovaluo.calcularTotal();                         // comportamiento: español
repository.findById(id);                           // infraestructura: inglés
```

Tablas y columnas en español `snake_case`. Campos de la API JSON en español `camelCase`.
Comentarios, pruebas y mensajes de commit en español.
## Comandos

```bash
cd backend
./gradlew verificarArquitectura   # ArchUnit, escaner de fuentes, aserciones y frontera de sistema
./gradlew verificarArranque       # el artefacto levanta en los dos perfiles (C-7). Requiere PostgreSQL 16
./gradlew verificarAislamiento    # aislamiento multi-tenant. BLOQUEANTE. Requiere PostgreSQL 16
./gradlew build                   # lo anterior mas Spotless
./gradlew spotlessApply           # arregla el formato en vez de solo reprocharlo

cd ../infrastructure
yarn install && yarn verificar    # el descriptor: lint, tipos y pruebas. Sin Pulumi ni cluster

# La plataforma: PostgreSQL con las cuatro bases, Keycloak con sus dos realms, Traefik y el buzon
cd ../../infrastructure
docker compose -f despliegue/plataforma.compose.yaml up -d --wait

# La guarda del registro (#711) y su autoprueba
node docs/00-gobierno/verificar-fila-del-registro.mjs
node docs/00-gobierno/verificar-las-muestras-del-registro.mjs
```

**`verificarAislamiento` no se omite sin Docker: falla.** Una prueba bloqueante que se salta a sí
misma deja el build en verde sin haber verificado nada. La salida documentada es apuntar a un
PostgreSQL 16 que ya exista, y **ninguna que omita la prueba**:

```bash
./gradlew verificarAislamiento \
  -Dkamayuk.pruebas.postgres.url=jdbc:postgresql://localhost:5432/postgres \
  -Dkamayuk.pruebas.postgres.usuario=postgres \
  -Dkamayuk.pruebas.postgres.clave=…
```

Tiene que ser **PostgreSQL 16** —el esquema no corre en 18 (`V11` falla con «text search
dictionary "unaccent" does not exist»)— y superusuario, porque la prueba crea los cuatro roles.
Cómo montarlo desde cero: [D0 — Desarrollo](docs/D0-desarrollo/README.md).
## Verificar antes de afirmar

**Ejecutar la prueba vale más que razonar sobre ella.** Y no basta con que la verificación esté
escrita: **tiene que demostrarse que puede fallar** — se rompe a propósito el código que protege,
se ejecuta, y se anota el rojo exacto que sale.

Cada issue deja aquí una fila con qué se implementó, **con qué rotura se demostró que la
verificación muerde** y qué rojo produjo. Es lo que impide volver a descubrir el mismo hallazgo
por tercera vez.

> **La tabla nace vacía, y es correcto que se vea así.** El registro anterior —288 filas, issue a
> issue— es historia de `sgtm` y **no viaja**: en un repositorio sin ese `git log` sería el
> registro de un trabajo que aquí no se hizo. Vive en
> [`sgtm/CLAUDE.md`](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/CLAUDE.md),
> que no se borra. Se consulta; no se copia.

Que la fila **exista** lo comprueba `docs/00-gobierno/verificar-fila-del-registro.mjs` en cada PR
que cierre un issue y toque código de producción. Lo que la fila **diga** —que la mutación sea
real y las cifras cuadren— no lo puede leer una máquina: eso lo lee la revisión.

| Verificación | Cómo se demostró que puede fallar | Resultado |
|---|---|---|
| **C-1 — los siete desajustes de frontera que `rentas` no podía ver, cerrados** (`fichaId`, `soloPredio`/`exceptoPredio`, `?ejercicio=` en las tres lecturas de cuadro; 7 pruebas nuevas de capa web y el registro `desajustesVivos()` a cero) | Cuatro roturas, cada una aplicada **sola** sobre `src/main` y restaurada **por copia comparada con `cmp`**: devolver `fichaId` a `id` en `FichaEncontradaResource`; quitarle al controlador los dos parámetros de acotación; devolver `?ejercicio=` a `?anio=` en `ValorUnitarioController`; y, del lado del consumidor, devolver el contrato comprometido a lo que declaraba antes de C-1 (`aLaFecha`, `vigenciaDesde` como «fecha», el sobre `contenido` del cuadro) | **1, 2, 1 y 3 en rojo** en `ContratoConRentasTest`, cada una nombrando el campo o el parámetro: «falta el campo «contenido[].fichaId», que el consumidor lee», «el consumidor manda «soloPredio» y este endpoint no lo lee … Viaja en la URL y se descarta en silencio», «el consumidor manda «ejercicio» y este endpoint no lo lee (lee [anio])». La segunda pone además **3 de 17** en rojo en `ConsultaControllerTest`, que es lo que mide que los parámetros no sólo se declaren sino que **acoten**. **Y una premisa del registro de P6 resultó falsa al medirla**: decía que `ConsultaController` «declara el parámetro `fecha` y lo ignora». No lo ignora —lo pasa a `ConsultaDeFichas.buscar` y de ahí al `WHERE f.vigencia_desde <= :fecha`—; el efecto que P6 describe (pedir marzo y recibir la ficha de hoy) era real y su causa era **el nombre**, porque `aLaFecha` no llegaba y se tomaba el valor por omisión del reloj. **Y el hallazgo del método**: las tres roturas del lado del consumidor pasaron en VERDE la primera vez —`test` quedaba UP-TO-DATE porque el contrato del consumidor vive en OTRO CLON y no era entrada declarada de Gradle (la lección de #192 punto 2, ahora en la frontera entre repositorios)—; declarado como entrada, la misma rotura vuelve a morder sin `cleanTest` |
| **C-6 — el guion de carga que estaba aqui y su proceso vive en `rentas`** (`ArchivosDeEjemploTest` pasa de seis archivos a cinco) | Devolver `cargar-transferencias-demo.sh` a este repositorio | 2 en rojo en `siembra-de-la-demostracion.test.ts` de `infrastructure`: «catastro/cargar-transferencias-demo.sh: manda `KAMAYUK_CARGATRANSFERENCIASDEMO_ARCHIVO`, y ningun cargador de «catastro» la atiende». **Su sintoma era la ausencia de sintoma**: lanzaba un Job con la imagen de `catastro` y una propiedad que solo `rentas` atiende, asi que la aplicacion arrancaba, **no cargaba ni una fila** y salia con codigo 0 —medido: cero lineas de carga, ni un aviso, ni una fila rechazada—. Y el paso 7 sin el 6 imprime «51 fila(s) leidas, 0 ficha(s) versionada(s), 22 predio(s) rechazado(s)» con exit 0, que es el hueco 8 de P5C reproducido; ahora la comprobacion de `infrastructure` lo pone en rojo con «`0 de 45`: FALTAN 45». Las 974 pruebas siguen en 974, 0 fallos, con `--rerun-tasks` contra PostgreSQL 16.15 real. **Y ejecutar destapo que esta aplicacion no arranca**: no tiene ninguna implementacion de `ComprobadorDeAcceso` —su javadoc dice «vive en `seguridad`», que se quedo en `rentas`— y ademas le falta el `ObjectMapper` de Jackson 2 que inyecta `DirectorioHttpDeRentas`; declarado, no arreglado, en [C-6](https://github.com/hneyra/infrastructure/blob/main/docs/00-gobierno/C-6-la-siembra-orquestada.md) §6 |
| **C-7 — `catastro` arranca, y su imagen se construyó de verdad** ([C-7](https://github.com/hneyra/infrastructure/blob/main/docs/00-gobierno/C-7-que-arranquen.md): el módulo `kamayuk-catastro-seguridad`, la prueba de arranque y `verificarArranque`) | Cinco roturas, cada una sola y restaurada por copia comparada con `cmp`: quitarle el `@Component` a `ComprobadorDeAccesoJdbc`; devolver `DirectorioHttpDeRentas` a Jackson 2; quitar del catálogo una opción que un endpoint declara; volver la precedencia una **unión** —que la excepción del usuario deje de sustituir al grupo—; y conectar el pool del comprobador como **superusuario del clúster** | **4 de 4** las dos primeras, con los dos mensajes que C-6 midió: «required a bean of type `kamayuk.catastro.autorizacion.ComprobadorDeAcceso`» y «… `com.fasterxml.jackson.databind.ObjectMapper`». 1, 2 y 2 las otras tres. **La cuarta es la decisión de D-N5 hecha prueba**: una excepción **que niega** tiene que poder expresarse, y con una unión pura el único modo de quitarle un permiso a alguien sería sacarlo del grupo y repetirle los demás a mano. **La quinta enseña lo de siempre**: sin RLS la misma cuenta contesta lo mismo en las dos municipalidades, porque el aislamiento aquí no lo pone ningún `WHERE`. Y el `Dockerfile` se cerró **construyendo la imagen**: `Successfully built`, y el contenedor arranca —«Started SgtmAplicacion in 6.994 seconds»—; construir encontró de paso que este `Dockerfile` copiaba `docs/10-negocio/catalogo-de-opciones.md`, **que en `catastro` no existe**, así que fallaba antes de compilar una sola clase. La implantación se ejecutó contra una base creada de cero: «Municipalidad 200105 lista en catastro (DEMOSTRACION): id 1, 11 accesos nuevos», y la segunda corrida dice «0 accesos nuevos» sin mover una fila |
