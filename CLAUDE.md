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
| `backend/` — **once** módulos | **Existe desde P5C**, y desde #4, #5 y #6 con `kamayuk-catastro-{urbano, grd, fiscalizacion}`: `kamayuk-catastro-{dominio-compartido, esquema, plataforma, parametros, contribuyentes, nucleo, urbano, grd, fiscalizacion, seguridad, aplicacion}` |
| **Código de negocio** | **Existe.** El contexto acotado `catastro` entero: predio, ficha versionada, construcciones, titularidad, inquilinos, catálogo vial, las cuatro clases de ficha, la geometría y el arancel |
| Su esquema, hasta **`V14`** —el consumidor del buzón de `identidad` (ADR-0039 etapa 4, `identidad`#4)— | **Existe.** `V1__baseline.sql` (ADR-0032), `V2` —la copia local de conjuntos sellados—, `V3` —la guarda del arancel—, `V4`, `V5` —el buzón de salida—, **`V6`** —el CUC del SNCP y `frente_predio` (ADR-0036, que cierra D-10)— **`V7`** —la zonificación urbana: `zonificacion`, `parametro_urbanistico`, `seccion_via` y `habilitacion_urbana` (#4)— y **`V8`** —la gestión del riesgo: `zona_riesgo`, `faja_marginal` e `itse` (#5)—, **`V9`** —la fiscalización catastral: `campania`, `candidato`, `hallazgo`, `evidencia` y `acta` (ADR-0035, #6)— y **`V10`** —el frente lineal y el buzón del territorio: `via.eje`, `longitud_estado` en `frente_predio`, `frente_derivacion` y los tres tipos nuevos de `catastro_evento` (#7)—, `V11`..`V13` (#22, #23, #27) y **`V14`** —`identidad_evento_aplicado` e `identidad_evento_muerto`, la memoria del consumidor del buzón de `identidad`: RLS `ENABLE`+`FORCE`, el cuerpo en `text` y **sin `DELETE`** para `kamayuk_app` (etapa 4)— |
| `./gradlew build` | **VERDE**, contra PostgreSQL 16 real. Cifras vigentes en la última fila del registro |
| `verificarArquitectura` / `verificarAislamiento` | **VERDE los dos** |
| Su frontend (`catastro-web`) | **Existe desde #32 el armazón, y desde #33 el módulo Catastro entero.** Vive en `frontend/` —no en `catastro-web/`: es el nombre que ya usan `RUTAS_DE_CODIGO` y el `.dockerignore`—. React 19 sobre Vite 6, sin router, sin react-query y sin UI kit. Porta el marco de `CatastroV6.dc.html` con los **seis** módulos de este backend y sus **16** hojas, y **las cuatro hojas del módulo Catastro** que el artboard dibuja —Panel, Predios maestro-detalle, Territorio y Valores del ejercicio—. Todo sale por `src/api/cliente.ts`; los datos los pone un **proxy que sustituye `fetch`** (ADR-0010) y que se apaga con `VITE_CATASTRO_PROXY_DE_DATOS=false`; `servidas.ts` nace **vacía** y crece hasta las 64. **Ninguna cifra se escribe: `src/datos/` sólo tiene rótulos y lo comprueba `yarn datos`**. **Desde #40 su imagen se publica** —`kamayuk-catastro-web`, tercera entrada de la matriz, con el `sha` de este repositorio— y el `Dockerfile` la construye con `VITE_CATASTRO_PROXY_DE_DATOS=false`, así que **no lleva dentro el proxy de datos simulados**. Nadie la despliega todavía. **Y desde #47 son NUEVE arneses y no ocho**: `imagen` mira los dos archivos que deciden cómo se sirve —`nginx.conf` y `Dockerfile`—, y los mide **levantando nginx de verdad y preguntando por HTTP**, porque `add_header` no se hereda y una guarda de texto pasa en verde con el defecto puesto. **Desde #49 son diez** —`errores`— y **desde #48, ONCE**: `ejercicios` mide que el desplegable de la barra global sale del **reloj**, y lo mide **moviéndolo**, que es la única forma —los cuatro años que estaban escritos a mano coincidían con el reloj letra por letra, así que una guarda que preguntara «ofrece el año en curso» pasaba en verde con el defecto puesto: medido, verde—. **El ejercicio ya no es un literal** (#48): sale del año en curso y tres hacia atrás, el reloj se lee **una vez** en `App.tsx` y las dos funciones que derivan la lista son puras (`shell/ejercicios.ts`). Sigue siendo una **suposición** —nadie le pregunta al backend qué ejercicios tienen conjunto sellado, y esa lectura es **#51**— que falla ruidosa: elegir uno sin sellar da 404 con `parametroQueFalta`. **Y desde #46 son DOCE**: `ficha` compara lo que la pantalla **dibuja** con el JSON que la pagina recibio, en las cuatro clases de ficha. Con el, el tipo `Ficha` declara los **22** componentes que `FichaResource` publica —eran 17— y la pantalla los dibuja: lo construido, las **obras complementarias SIN IMPORTE y diciendo por que** (el Anexo III de la R.M. 277-2025-VIVIENDA no esta transcrito y `otra_instalacion` no tiene columna de importe), los tres bloques de detalle —cada uno por **su** ruta, porque `/urbana/{cod}` fija `TipoFicha.UNICA` y esa ficha no tiene ninguno— y la pestana **Movimientos**, que es la misma ruta con el `?historico=` que esta capa no ofrecia. |
| Sus imágenes en `ghcr.io/hneyra/` | **Son TRES desde #40, y dos de ellas ya existen.** Medido contra el registro el 2026-09-06 para el `sha` de `main`: `kamayuk-catastro` → **200**, `kamayuk-catastro-migrador` → **200**, y `kamayuk-catastro-web` → **403**, que es «no se puede concluir nada» y no «no existe» — nace en el primer `push` a `main` que lleve la matriz nueva. La fila anterior decía que la primera **no existía**, y era falso desde D. **Que exista la imagen no es que se despliegue**: `infrastructure` sigue desplegando `sgtm-interfaz`, la del monolito (#40, AC-3, sin cerrar) |
| **Carga cartográfica** | **NO hay ni un polígono** en ninguna instalación, y desde `V10` tampoco **ni un eje de calzada**. `V61` trajo la columna del lote; nada la llena todavía. Por eso el detector de subvaluadores de `V9` **dice que no puede** en vez de devolver cero (#6, AC 8), y el derivador de frentes de `V10` **deja constancia del motivo** por predio en vez de dejar una lista vacía sin explicar (#7, AC 3) |
| **El frente lineal** | **Existe desde #7**: se deriva cortando el lote contra el eje de la vía y nace **PROPUESTA**; confirmarla es un acto con su observación (ADR-0021). `catastro` **no determina ningún arbitrio con ella** —eso es `rentas`, ADR-0024— y no nombra ningún servicio: lo vigila `CatastroNoNombraUnArbitrioTest` sobre el árbol entero |
| **La resta en `rentas`** | **Hecha.** Su `V6` retiró las **20** claves foráneas —el enunciado decía tres— y las 15 tablas; su módulo `kamayuk-rentas-catastro` quedó como adaptador cliente: 26 clases, sin dominio y sin una sola consulta |
| **La corrida de valuación de ADR-0027** | **Produce cifras desde el 2026-09-06: 4 de 23 predios del padrón de demostración.** Lo que faltaba no era código sino una firma: `normativa` publica los dos cuadros nacionales (H-14, H-15) desde #8, y el `PORCENTAJE_DE_ACTUALIZACION` (D-11) esperaba la **segunda firma de ADR-0007**, que es un acto de una persona. Llegó, el ejercicio 2026 se selló y el padrón pasó de **0 de 23** a **4 de 23** — medido, no supuesto: el contrafactual que #8 dejó escrito acertó y se volvió a correr para comprobarlo. Los **19 restantes** ya no esperan a D-11 sino a **RT-004**, que es otra decisión y de otra clase: `depreciacion.md` §3 dice que traducir el uso de la ficha a una de las cuatro tablas del Anexo I «es criterio, no transcripción». Las **obras complementarias** siguen fuera: el Anexo III de la R.M. 277-2025-VIVIENDA no está transcrito y `otra_instalacion` no tiene columna de importe. Las tres cosas se **cuentan por llave** en `Informe.sinCifraPorLlave` |
| **Las rutas de ADR-0030 para la frontera** | **Publicadas las de LECTURA, y desde #17 y #18 sin hueco.** Medido sobre los **quince** puertos de `kamayuk-rentas-catastro`: los **trece de lectura** tienen quien los conteste —el contrato que `rentas` publica declara **15 operaciones**, y `ContratoConRentasTest` las comprueba desde aquí—, y los **dos de escritura** siguen lanzando `EscrituraSinTransaccionCompartida`, que **no se arregla publicando una ruta**: lo que falta es que dos bases confirmen juntas (C-5). El último que lanzaba por no tener ruta era `HallazgosDelPredio.de(predioId)`, y lo cierra #17 |
| **La copia local de la autorización** (`usuario`, `grupo`, `miembro`, `permiso`) | **Desde la etapa 4 de ADR-0039 la escribe el consumidor del buzón de `identidad`, y nadie más.** `kamayuk-catastro-seguridad` gana el aplicador (`AplicarUnEventoDeIdentidad`: una transacción `REQUIRES_NEW` por evento, con su `SET LOCAL`; los siete tipos sobre las cuatro tablas; «nunca» se aparta a `identidad_evento_muerto` con su motivo y se acusa, «ahora no» se pospone y no se acusa; un `PERMISO_FIJADO` de una opción de OTRO sistema se ignora con aviso y se acusa), el ingestor (`IngestarEventosDeIdentidad`: el acuse va **después** del commit, y la vuelta se para «sin progreso»), el runner del `CronJob` (`CorrerElConsumidorDeIdentidad`, perfil `batch`, 50 vueltas de 200) y el cliente HTTP con su token de servicio (`ClienteHttpDelBuzonDeIdentidad` + `TokenDeServicioDeKeycloak`, `client_credentials`). **Y desde la etapa 5 el consumidor es el ÚNICO que las escribe** (`identidad`#5): `SembradorDeLaCopiaLocal` pasó a llamarse `SembradorDelCatalogo` y se quedó con `modulo_sistema` y `acceso` —el catálogo, que es lo único que este sistema declara (RF-122)—; el grupo de administración, el primer administrador, su afiliación y sus permisos **llegan por el buzón**. `ImplantarMunicipalidad` da de alta la municipalidad, siembra el catálogo, **corre el consumidor una vez —y ya no es opcional—** y después **comprueba con el `ComprobadorDeAcceso` de producción que el administrador que el despliegue declara pueda leer alguna opción**: si no, falla nombrando lo que falta y el remedio («implantar `identidad` primero»), en vez de dejar el `Job` `Complete` sobre una copia con cero usuarios. **La regla 12 SÍ se vigila aquí**: `escritoresDeLaAutorizacionConMotivo()` declara **sólo** `AplicarUnEventoDeIdentidad`, sin fecha de fin. El descriptor despliega el `CronJob` `kamayuk-catastro-consumidor-de-identidad` (`*/5 * * * *`, `Forbid`, `backoffLimit: 1`), la clave `emisor: "keycloak"` y el egreso a `kamayuk-identidad-<amb>`; `docs/50-api/contratos-que-consume/identidad.json` declara las dos operaciones que se consumen. **Lo que NO está medido**: la ventana de inconsistencia (AC-5 de `identidad`#4) y la capacidad (AC-6), que son del carril de `infrastructure`; y nada de esto se ha levantado con Docker — no hay demonio en esta máquina |

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
- **No determina un arbitrio.** Desde #7 publica **el insumo**: cuántos metros lineales de frente
  tiene un predio y a qué vía dan. El importe lo pone `rentas` (ADR-0024), y aquí no aparece el
  nombre de ningún servicio ni ningún factor de barrido — lo comprueba una prueba sobre el árbol.
- **No decide si un giro es compatible con una zona.** Desde #4 publica **la zona** a la que cae un
  predio; quién es compatible con qué es dato de `rentas` (`ciiu.zonificacion_compatible`) y la
  licencia la emite `rentas`. Es la misma frontera de ADR-0024 que le impide calcular un tributo.
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
  kamayuk-catastro-urbano/      contexto acotado (#4): la zonificacion vigente. Publica LA ZONA
  kamayuk-catastro-grd/         contexto acotado (#5): zona de riesgo, faja marginal e ITSE
  kamayuk-catastro-fiscalizacion/  contexto acotado (#6): el hallazgo catastral (ADR-0035). NO es
                                la fiscalizacion TRIBUTARIA, que vive entera en `rentas`
  kamayuk-catastro-seguridad/   la copia local de usuarios, grupos y permisos (C-7, D-N5), y desde
                                la etapa 4 de ADR-0039 QUIEN la escribe: el consumidor del buzon de
                                `identidad` (aplicador, ingestor, runner del CronJob y cliente HTTP)
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
| ~~D-10~~ | **Contestada por [ADR-0036](docs/30-arquitectura/adr/ADR-0036-dos-codigos-y-no-uno.md)**: no era una longitud sino **dos identificadores distintos** —el de referencia catastral es municipal y de largo del tenant; el CUC del SNCP tiene 12 y entra en `V6` como columna propia— | ~~El baseline~~ |
| D-18 | La clave foránea que se pierde al separar `catastro` | El baseline |
| D-21 | Dónde se aplica el **`% de propiedad`**: aquí o en `rentas`. **Y con #8 tiene un hermano, medido**: el `% actualización` se pide como **precondición** de la valuación —para a los 23 predios— y se usa en **un solo sitio**, el incremento del autovalúo, que sólo se aplica si el porcentaje no es cero. Medido con tres corridas del padrón: con `p = 0` **ninguna de las cuatro cifras del hecho sellado depende de la llave** (el autovalúo es idéntico al terreno), y con `p ≠ 0` cambia **una sola** de las cuatro. O sea que lo que hoy bloquea la valuación entera no aporta un céntimo a ninguna de sus cifras — que es el argumento de ADR-0024 para ponerlo del lado de `rentas`, junto al `% propiedad`. **#8 lo mide y no lo mueve** | La frontera de ADR-0024 |
| ~~D-11~~ | **CERRADA PARA 2026, y sólo para 2026** (2026-09-06). [`predial-porcentaje-de-actualizacion.md` §1.6](https://github.com/hneyra/normativa/blob/main/docs/10-negocio/valores-normativos/predial-porcentaje-de-actualizacion.md) escribe el fundamento —no un valor por omisión sino el **hecho** de que el supuesto del art. 12 del TUO LTM no se cumple en 2026, porque se publicaron los aranceles y los precios unitarios—, una persona lo verificó, el archivo pasó a `VERIFICADO` y su fila viaja en el conjunto sellado. **Sigue abierta para cualquier otro ejercicio**: §1.6 lee el supuesto contra 2026 y ninguno más, y **no se hereda** | ~~La valorización entera~~. Ya no bloquea 2026: el padrón pasa de **0 de 23** a **4 de 23** |
| RT-004 | **Qué tabla del Anexo I del Reglamento Nacional de Tasaciones le toca a cada uso de ficha.** Lo destapó #8: `normativa` publica las cuatro y lo que falta es la traducción, que es criterio y no transcripción | El valor de **lo construido**: **19 de los 23 predios, y desde el 2026-09-06 se ven**. Hasta esa firma D-11 los paraba a los 23 antes y sólo se medían en un contrafactual; hoy son el único motivo que queda, repartidos `Casa habitacion 12 · Departamento 2 · Almacen de insumos 1 · Deposito y patio de maniobras 1 · Panaderia y pasteleria 1 · Taller de ceramica 1 · Tienda de artesania 1` |
| Anexo III | **Los valores unitarios a costo directo de obras complementarias**, que el corpus no transcribe —y la R.M. los da «de uso opcional… como una guía»—. `otra_instalacion` tampoco declara un importe | El valor de las **obras complementarias** |
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
## El monolito se llamaba `sgtm`, y en la prosa se sigue llamando asi

El producto es **Kamayuk**. El sistema del que sale —el monolito retirado— se llamaba `sgtm`, y
ese nombre **ya no esta en el codigo**: ni en un realm, ni en una imagen, ni en un identificador, ni
en un dato de configuracion.

**Pero sigue en los comentarios, en `docs/` y en el registro de «Verificar antes de afirmar», y eso
es deliberado.** No es limpieza pendiente:

- una fila del registro que dice «copiado de `sgtm@33f329a2`» **es la medicion que se hizo**;
  reescribirla la falsifica, y borrarla pierde con que rotura se demostro;
- un comentario que dice «hasta `E` la sonda apuntaba a `sgtm`» **es el motivo por el que el codigo
  de al lado es como es**; quitar el nombre lo deja sin sujeto y hay que volver a descubrirlo;
- y varias guardas explican en su docblock **de que defecto vienen**, que es lo que impide que
  alguien las «simplifique».

**Asi que NO se hace una pasada de limpieza sobre la prosa.** Si estas aqui por un `grep sgtm` que
devuelve cientos de lineas: casi todas son de este tipo y se quedan.

**Lo que si esta prohibido es que la cadena vuelva al codigo**, y lo vigila **una sola guarda para
los seis**: `sin-el-nombre-del-monolito.test.ts` de `infrastructure`, que barre este arbol y los
cinco clones hermanos. Barre **solo codigo de produccion** —ni `docs/`, ni `*.md`, ni pruebas— y
**omite comentarios**, por lo de arriba.

Esta en un sitio y no en `comun-verificaciones` porque, medido, **del lado Java no hay nada que
vigilar**: `backend/*/src/main` de los cinco solo nombra el monolito en comentarios y en dos
`COMMENT ON COLUMN`. Anadir una prohibicion a la libreria compartida exigiria su clase de muestra y
tocaria los seis builds para vigilar el conjunto vacio.

**Dos excepciones declaradas, y las dos con su motivo dentro de la guarda.** (1) Los buckets
`sgtm-{stg,prod}-respaldos` (`infra/Pulumi.{stg,prod}.yaml`): **son el nombre de cosas que
existen**, y renombrarlos en el codigo sin renombrar el bucket manda los respaldos a un sitio que no
existe — y eso no da error hasta el dia que hay que restaurar. (2) Dos `COMMENT ON COLUMN` dentro de
un `V1__baseline.sql` **ya aplicado**: Flyway valida la suma de comprobacion de cada migracion, asi
que editar una que ya corrio hace fallar el arranque de **toda base existente**. No es que no se
quiera cambiar: **no se puede** — se corregiria con una migracion nueva, si alguna vez importa.

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

**Las 42 filas que había aquí viven en [`docs/agent/HISTORY.md`](docs/agent/HISTORY.md)**, y ahí
es donde se escribe la siguiente. Se mudaron el 2026-09-12: eran el **92 %** de este archivo, que
se carga entero en cada sesión
([`infrastructure`#114](https://github.com/hneyra/infrastructure/issues/114)).

La tabla de arriba se deja **con su cabecera y vacía** a propósito: es la forma de la fila que hay
que escribir, y tenerla delante evita ir a buscarla.
