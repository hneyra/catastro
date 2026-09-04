# C-5 — Los siete puertos que nadie contestaba: cinco publicados, dos con su motivo

**Fecha:** 2026-09-04. **Repositorios tocados:** `catastro` (proveedor) y `rentas` (consumidor).
**`sgtm` no se tocó:** su `git status` queda limpio.

P5C dejó `kamayuk-rentas-catastro` como adaptador cliente y **siete de sus nueve puertos sin
ninguna ruta que los contestara** — lo que su propio entregable llamó «lo más caro que deja esta
etapa» (hueco 2). Estaba bloqueado por **D-22**, contestada el 2026-09-04.

Este trabajo publica **cinco lecturas** en `catastro` y las conecta en `rentas`, una por una, con su
prueba de contrato del lado del proveedor. **Las dos escrituras se quedan**, y no por falta de ruta:
por falta de transacción compartida. §4 lo dice con la medida que lo respalda.

---

## 1. Los cuatro criterios, con su medida

| # | Criterio | Estado | Medida |
|---|---|---|---|
| **1** | `SinRutaTodavia` vacío, o lo que quede con su motivo escrito | **Cumplido a medias, y dicho** | De **siete métodos en cinco puertos** quedan **dos escrituras**. `SinRutaTodavia` conserva una (`inscribirLoHallado`) y la otra vive en `TitularidadHttp`, porque su interfaz tiene además un método que sí se lee. Ningún método se borró sin conectarlo. §4 |
| **2** | Por cada ruta nueva, la mutación que demuestra que su prueba de contrato muerde **en el proveedor** | **Cumplido** | Seis mutaciones, una por ruta, todas rojas en `ContratoConRentasTest` de `catastro`. §3 |
| **3** | La fecha demostrada, con su mutación en rojo | **Cumplido** | Dos mutaciones —«la última» para la ficha, «la vigente hoy» para la titularidad— y una tercera que reproduce el desajuste de C-1 desde el otro lado. §2 |
| **4** | Las cifras no bajan | **Cumplido** | `rentas` 3 106 → **3 121** · `catastro` 962 → **974**. `caja`, `normativa` e `infrastructure` no se tocaron. §6 |
| **5** | Los tres verificadores bloqueantes en verde en los dos repositorios | **Cumplido** | §6 |

---

## 2. Las cinco lecturas publicadas, una a una

Todas cuelgan de `catastro/api/v1` y se sirven desde dos controladores nuevos. **Los puertos Java no
se tocaron**: la extracción los publica, no los inventa, y su javadoc —donde está escrito por qué
existen— se conserva entero.

| Ruta (clave de operación) | Puerto que contesta | Adaptador en `rentas` |
|---|---|---|
| `GET /catastro/predios/{predioId}` | `TitularesDelPredio.estaEnElPadron` | `TitularesDelPredioHttp` |
| `GET /catastro/predios/{predioId}/caracteristicas?fecha=` | `LectorDeCaracteristicas.de`, `LectorDeFichas.fichaVigenteEn`, `LectorDeFichasEconomicas.fichaEconomicaVigenteEn` | `CaracteristicasDelPredioHttp` |
| `GET /catastro/fichas/{fichaId}/area` | `LectorDeFichas.areaDeLaVersion` | `CaracteristicasDelPredioHttp` |
| `GET /catastro/titularidad?predio=…&fecha=` | `TitularesDelPredio.de` y `.deVarios` | `TitularesDelPredioHttp` |
| `GET /catastro/titularidad/cuota?predio=&contribuyente=&fecha=` | `GestorDeTitularidad.vigenteDe` | `TitularidadHttp` |
| `GET /catastro/titularidad/predios?contribuyente=&fecha=` | `PrediosDelContribuyente.de` | `PrediosDelContribuyenteHttp` |

Son **seis rutas para cinco puertos**: `LectorDeFichas` tiene dos métodos que preguntan cosas
distintas —la versión vigente de un predio a una fecha, y el área de *una* versión por su
identificador— y meterlas en la misma ruta habría obligado a inventarle una fecha a la segunda.

### 2.1 Las decisiones de forma, con su motivo

**Una petición contesta a tres puertos.** `…/caracteristicas` resuelve la ficha única, la económica,
el uso, el sector y el área en **una transacción** de `catastro` (`ConsultaDeCaracteristicas`, que
compone llamando a los tres puertos con propagación `REQUIRED`). Tres rutas habrían sido tres
peticiones y tres transacciones, con una versión nueva de ficha cabiendo entre la primera y la
tercera: es el defecto que #486 midió al revés —«entre la del predio y la de su ficha cabía una
versión nueva»— y aquí saldría más caro, porque el resultado se usa para determinar.

**La fecha es obligatoria en las cuatro rutas que la llevan.** Las otras siete lecturas de esa capa
web admiten `fecha` ausente y resuelven con el reloj; éstas no. Los puertos que sirven reciben
*siempre* una fecha (regla 9), y un cliente que la olvidara recibiría la ficha de hoy con 200
delante — que es exactamente el defecto que C-1 encontró **ya servido por HTTP**. Sin valor por
omisión, un olvido es un 422 ruidoso.

**Ninguna ausencia es un 404, y ésa es la regla de esta frontera.** «Ese predio no está en el
padrón», «esa versión de ficha no existe» y «esta persona no tiene cuota aquí» viajan como campo
(`enElPadron`, `existe`, `tieneCuota`) con 200 delante. Si fueran 404, un cliente que pidiera una
ruta mal escrita —o un despliegue que se quedara atrás— leería «el predio no está», que es plausible
y falso; y las dos cosas se arreglan de maneras opuestas: una es un dato del padrón, la otra es un
despliegue. El **404 de esta frontera queda reservado para «esa ruta no existe»**, que es lo único
que un cliente no puede distinguir de otra manera.

**«Lanzar es mejor que devolver vacío» sigue en pie, y lo que cambia es cuándo.** Mientras no había
ruta, una lista vacía de predios se leía como «este contribuyente no tiene ninguno» y dejaba la base
del predial en cero. Ahora la ruta existe y una lista vacía vuelve a ser un dato — **pero sólo si la
respuesta es de quien se preguntó y de la fecha que se preguntó**. Las dos vuelven en el cuerpo y
las dos se comprueban antes de leer una fila: es el guardia de #298, donde el portal enseñaba a
quien tecleaba su DNI la deuda de la primera persona del padrón. Aquí el equivalente sería determinar
el predial de una persona con los predios de otra.

**Una petición para una página entera.** `GET /catastro/titularidad` repite el parámetro `predio`,
que es la forma que `TitularesDelPredio.deVarios` conservó a propósito desde P5C: una página de
veinte omisos cuesta una petición y no veinte. Sin ningún `predio` el servidor contesta **422**, y el
cliente **corta antes de salir a la red**: un parámetro repetido cero veces llega igual que uno
ausente, así que «estos, y ninguno» y «no acotes» serían la misma URL. Es el mismo corto-circuito
que `FichasDelPadronHttp` lleva desde C-1, y por el mismo motivo.

**Sin tope de predios, y a propósito.** El puerto no lo tenía. Un tope inventado dejaría a una página
legítimamente más larga contestando 422 por un número que nadie decidió; esta frontera está para
trasladar comportamiento, no para cambiarlo. Por lo mismo, la respuesta **sólo trae los predios que
tienen alguna cuota vigente**: devolver una entrada vacía sería más informativo y cambiaría lo que el
mapa contesta —`null` pasaría a ser lista vacía— para los cuatro sitios que ya lo consumen.

**El orden de la respuesta es el de la petición.** No sale del `Map` que devuelve el puerto: el orden
de iteración de un `Map.copyOf` no está especificado, y una respuesta que cambia de orden entre dos
corridas no se puede afirmar en ninguna prueba (#400).

**`PredioEnElPadronResource` publica dos campos, y lo que NO publica es la decisión.** No lleva el
`estado`. Un predio **dado de baja sigue estando en el padrón**, y de eso depende que su deuda se
pueda seguir moviendo: #660 lo midió al revés y #680 lo dejó escrito. Publicar aquí el estado
invitaría a que quien lee lo filtrara, y eso es reintroducir ese defecto desde el otro lado de la
frontera. Por lo mismo, «estar en el padrón» lo contesta `TitularesDelPredio.estaEnElPadron` y no un
`predio(id).isPresent()` escrito en el controlador: esa frase ya tiene **una** definición en este
sistema, y escribir la segunda serían dos verdades sobre lo mismo.

**El listado de titulares no publica `titularidadId`; la ruta de la cuota sí.** Es el identificador
con el que se **transfiere** una cuota, y ponerlo en el listado lo dejaría al alcance de una lectura
de página. Quien lo necesita lo pide de un titular y un predio cada vez.

### 2.2 La fecha, demostrada (criterio 3)

`LecturasDeLaFronteraFronteraTest` siembra un predio con **dos versiones de ficha** —la de 2024
cerrada el día antes de que abra la de 2026— y **dos dueños** con el mismo corte, y pregunta por las
dos fechas. Va de HTTP a PostgreSQL real como `sgtm_app`, porque lo que hay que demostrar vive en el
`WHERE` de dos consultas y un doble del repositorio devolvería lo que se le pidiera.

| Rotura sobre `src/main` | Resultado |
|---|---|
| `LectorDeFichasCatastro.fichaVigenteEn` resuelve **«la última»** (`ultimaVersion`) en vez de `vigenteA(fecha)` | **2 en rojo.** Preguntando por 2024 la respuesta sale con `"fichaId":2` —la versión de 2026— y el uso y el área de 2024 al lado: una respuesta internamente inconsistente que ninguna cifra delata. Es #24 y #366 servidos por HTTP |
| `VIGENTE_A_LA_FECHA` del repositorio pasa a ser **«la abierta»** (`vigencia_desde <= :fecha AND vigencia_hasta IS NULL`) | **2 en rojo.** Preguntar por 2024 devuelve `{"aLaFecha":"2024-06-30","predios":[]}` y `{"contribuyenteId":1,…,"predios":[]}`: el dueño de entonces desaparece, y con él el predio sobre el que se le determinó |
| El controlador lee `@RequestParam("aLaFecha")` en vez de `fecha` — el desajuste 3 de C-1 reintroducido | **4 en rojo en el proveedor** *y* **1 en `ContratoConRentasTest`**, «el consumidor manda «fecha» y este endpoint no lo lee (lee [aLaFecha, predioId])» |

Y del lado del consumidor la fecha se comprueba **de vuelta**: la respuesta trae `aLaFecha` —la fecha
con la que `catastro` resolvió, no la que llegó en la URL— y el adaptador la compara con la que
pidió. Es lo único que caza ese defecto desde este lado, porque el único que sabe qué fecha se pidió
es quien la pidió.

| Rotura | Resultado |
|---|---|
| `exigirQueContesteALaFecha` deja de comparar | 1 en rojo en `LecturaDeCatastroTest`: «Expecting code to raise a throwable» |
| Sólo el adaptador vuelve a mandar `?aLaFecha=` | **El CI del proveedor no se entera** —sus entradas no cambian— y lo caza este lado, con el mensaje entero: «se pidió al 2026-03-15 y la respuesta dice estar resuelta al «»» |

---

## 3. Criterio 2 — la mutación de cada ruta, en el proveedor

Cada una se aplicó **sola** sobre `src/main`, se ejecutó, y se restauró **por copia comparada con
`cmp`**. Todas ponen roja `ContratoConRentasTest` de **`catastro`**, que es la mitad que P6 puso del
lado del proveedor a propósito: si se rompe la respuesta, el rojo le llega a quien la rompió, en el
PR que la rompe.

| Ruta | Rotura | Lo que dice el rojo |
|---|---|---|
| `GET /catastro/predios/{predioId}` | `enElPadron` → `esta` | «falta el campo «enElPadron», que el consumidor lee. Este endpoint declara [esta, predioId]» |
| `…/{predioId}/caracteristicas` | `@RequestParam("aLaFecha")` | «el consumidor manda «fecha» y este endpoint no lo lee (lee [aLaFecha, predioId]). Viaja en la URL y se descarta en silencio» |
| `GET /catastro/fichas/{fichaId}/area` | se le quita el `@GetMapping` | «este backend no publica esa operacion. Publica [… GET /catastro/predios/{predioId}/caracteristicas, …]» |
| `GET /catastro/titularidad` | `cuotas` → `titulares` | «falta el campo «predios[].cuotas» … Este endpoint declara [predioId, titulares]» |
| `GET /catastro/titularidad/cuota` | se le quita `titularidadId` | «falta el campo «titularidadId» … declara [aLaFecha, contribuyenteId, porcentaje, predioId, tieneCuota]» |
| `GET /catastro/titularidad/predios` | `porcentajeRegistradoDelPredio` → `porcentajeDelPredio` | «falta el campo «predios[].porcentajeRegistradoDelPredio» …» |

La última es la que más cuesta ver sin la guarda: los **dos** porcentajes son dos cosas distintas
—uno pondera la base imponible (#395) y el otro dice si el saneamiento de la titularidad está
completo (#690)— y no se deriva uno del otro. Quedarse con el primero deja de avisar de que el predio
está a medias, y en una copropiedad bien saneada los dos valen cosas distintas.

**`desajustesVivos()` sigue vacío** en los dos archivos donde C-1 lo dejó a cero, y ésa es la
afirmación: lo que `rentas` espera de `catastro` lo cumple entero, campo a campo y parámetro a
parámetro. El contrato pasa de **3 operaciones a 9**.

---

## 4. Las dos escrituras que se quedan, y por qué no es falta de ruta

`GestorDeTitularidad.transferir` y `TransferenciaDeFiscalizacion.inscribirLoHallado` **no se
conectan**. No lanzan ya `SinRutaEnCatastro` —eso sería mentir, porque publicar la ruta no lo
arregla— sino un tipo nuevo, `ClienteHttpDeCatastro.EscrituraSinTransaccionCompartida`, que dice
exactamente qué falta. Se distingue de las otras dos excepciones por lo mismo que ellas se distinguen
entre sí: **se arregla de otra manera**, y decir la equivocada manda a quien opera a mirar una cola o
un despliegue.

### 4.1 La medida

Las dos ocurren **dentro de una `@Transactional` de `rentas` que confirma otras escrituras después
de ellas**:

- `RegistrarTransferencia.transferirPredio` (`kamayuk-rentas-rentas`) hace, en este orden: leer la
  cuota vigente, **transferirla en `catastro`**, insertar la fila de `transferencia` y auditar.
- `TransferirARentas.transferir` (`kamayuk-rentas-fiscalizacion`) hace, en este orden: **(1)**
  inscribir lo hallado en el padrón, **(2)** emitir la resolución de determinación *con la versión
  que acaba de quedar inscrita*, **(3)** asentar los cargos de la diferencia y **(4)** registrar la
  fila que ata las dos versiones con el documento y la liquidación. El comentario de ese paso 1 dice
  por qué va primero: «para que el papel imprima lo que quedó inscrito de verdad y no lo que se
  esperaba inscribir».

Servidas por HTTP, `catastro` confirmaría su escritura por su cuenta y el resto ocurriría después, en
otra base y en otra transacción. Un fallo entre medias deja **el padrón cambiado sin el acto que lo
justifica** — sin resolución y sin cargo que cobrar. **Eso no es una hipótesis:** es la mutación que
#52 midió cuando se le dio `REQUIRES_NEW` a la versión de la ficha, y salieron **12 fichas donde debe
haber 11**.

### 4.2 Y una guarda que dejaría de poder fallar

`TransferenciaDeFiscalizacionCatastro.inscribirLoHallado` declara
`Propagation.MANDATORY` **precisamente** para que eso no se pueda escribir suelto. Llamada desde un
controlador de `catastro`, esa guarda **se cumpliría** —hay una transacción, la del borde— mientras
el invariante que protege ya no existiría. Una regla que no puede fallar donde antes mordía es peor
que ninguna, y por eso la ruta no se publica «para que exista».

### 4.3 Qué lo desbloquea

Una de dos, y ninguna cabe en este trabajo:

1. **Que la escritura remota sea la última y reversible por compensación.** Para
   `RegistrarTransferencia` es reordenar tres pasos; para `TransferirARentas` **no se puede sin
   decidir qué imprime el papel**, porque hoy imprime lo que `catastro` devuelve.
2. **El buzón de eventos de ADR-0027**, que P5C dejó declarado como hueco 3 —«no hay cola, no hay
   suscripción, no hay reintento»—. Y el buzón solo no basta para `transferir`: quien llama necesita
   el `titularidadId` nuevo **en la misma petición**, así que una escritura asíncrona tampoco lo
   contesta.

---

## 5. Lo que se decidió **no** hacer

- **No se publicó `POST /catastro/predios/{id}/titularidad` ni `…/transferencia-fiscal`**, que son
  las dos rutas que ADR-0030 nombra para esta frontera. §4.
- **Y la primera no se podría construir con ese nombre aunque se quisiera**, medido: el puerto
  `GestorDeTitularidad.transferir` recibe `titularidadAnteriorId` y **no recibe ningún predio**, así
  que el cliente no tiene con qué rellenar el `{id}` de la ruta. La ruta honesta habría sido
  `POST /catastro/titularidades/{titularidadId}/transferencia`. Es una corrección al enunciado de
  P5C, que la nombraba por el predio.
- **No se envolvió la respuesta de `…/titularidad` en un sobre paginado.** Se pide por identificador
  y se contesta por identificador; un `totalElementos` que nunca significa nada es peor que una
  lista.
- **No se le puso tope al número de predios**, ni se devolvieron entradas vacías para los que no
  tienen titular: las dos cosas cambiarían el comportamiento que el puerto tenía dentro del proceso.
- **No se tocó ningún puerto Java, ni su javadoc.** Son el contrato desde ARQ-01 §4, y por eso las
  veintisiete clases que los consumen siguen sin cambiar una línea.

---

## 6. Las cifras y las barreras

Contra **PostgreSQL 16.15 real** en `127.0.0.1:55444`, no por Testcontainers (§7, hueco 2).

| Repositorio | Antes | Después | Diferencia |
|---|---:|---:|---|
| `rentas` | 3 106 | **3 121** | +15: 7 de `PeticionesACatastroTest` y 8 de `LecturaDeCatastroTest` |
| `catastro` | 962 | **974** | +12, las de `LecturasDeLaFronteraFronteraTest` |
| `caja` | 673 | 673 | sin tocar |
| `normativa` | 606 | 606 | sin tocar |
| `infrastructure` | 389/389 | 389/389 | sin tocar |

| Tarea | `catastro` | `rentas` |
|---|---|---|
| `./gradlew build` (Spotless, Checkstyle, NullAway) | **VERDE** | **VERDE** |
| `./gradlew verificarArquitectura` | **VERDE** | **VERDE** |
| `./gradlew verificarAislamiento` | **VERDE** | **VERDE** |

---

## 7. Huecos declarados

1. **Las dos escrituras siguen sin conectar**, con su motivo en §4 y escrito donde se ejecuta:
   `SinRutaTodavia` y `TitularidadHttp`. `SinRutaTodavia` encogió de siete métodos a uno y **sigue
   siendo la lista de trabajo pendiente de esta frontera**.

2. **El motor de pruebas no es el de CI.** Testcontainers no sirve desde esta máquina —el demonio de
   Docker es un túnel a un VPS y el puerto publicado del contenedor se queda allí—, así que las
   pruebas de persistencia corrieron contra un PostgreSQL 16.15 externo con RLS, `FORCE ROW LEVEL
   SECURITY` y los cinco roles reales. Es el mismo hueco que declararon P3, P4, P5A, P5B, P5C y C-1.

3. **El acceso de estas rutas cuelga de una opción de pantalla, y el token llega reenviado.** Las
   tres lecturas del predio exigen `consulta_fichas` y las tres de titularidad
   `actualizacion_catastro` —la misma opción que su hermana de escritura, y no `consulta_fichas`,
   porque lo que publican es el `contribuyenteId`, o sea la correlación predio→persona que ADR-0015
   §2.4 decidió expresamente no poner al alcance de todo el que pueda listar fichas—. Pero el token
   que llega es **el del funcionario, reenviado tal cual** (ADR-0028 §1, hueco 6 de P5C): quien
   determina el predial en `rentas` tendría que tener además esas opciones en `catastro`, y en una
   corrida sin usuario delante no hay token y la llamada se rechaza. **Un token con audiencia propia
   (ADR-0028 §2) es lo que permite contestar «quién puede pedir esto» sin colgarlo de una opción de
   pantalla**, y hasta que exista este reparto es lo mejor que se puede afirmar.

4. **Cada método de `CaracteristicasDelPredioHttp` es una petición.** Medido antes de decidirlo: de
   las once clases de `src/main` que consumen esos tres puertos, **ninguna pide dos de las tres cosas
   sobre el mismo predio y la misma fecha**, así que hoy nadie paga más de una. El día que alguna lo
   haga, lo que hay que añadir es un método al puerto que pida las tres, no una caché en el
   adaptador.

5. **`catastro` sigue sin contrato de API derivado** (hueco 7 de P5C). Estas seis rutas no están en
   ningún OpenAPI: lo único que las sujeta es el contrato del consumidor y su prueba en el proveedor,
   que es exactamente lo que ADR-0030 §4 pide y no sustituye a un contrato publicado.

6. **La ruta de las características no publica el `estado` del predio ni el `tipo` de la ficha**, y
   ninguno de los dos hace falta hoy. Se dice aquí para que añadirlos sea una decisión y no un
   descuido: el contrato es de contención, así que un campo de más no rompe a nadie, pero tampoco lo
   comprueba nadie.
