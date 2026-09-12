# `frontend/` — `catastro-web`

React 19 sobre Vite, TypeScript. **El armazón del artwork `CatastroV6.dc.html`**, con los
**seis** módulos que este backend publica —no los doce del monolito que el artboard dibuja— y
una capa de datos que hoy **simula** y mañana **llama**, sin reescribir ninguna pantalla por el
camino.

Vive en `frontend/` y no en `catastro-web/` porque las dos guardas de este repositorio que
hablan de código de frontend —`RUTAS_DE_CODIGO` en `verificar-fila-del-registro.mjs` y el
`.dockerignore`— **ya nombran `frontend/`**. Con el otro nombre habría que cambiarlas, o un PR
que cerrara un issue pasaría en verde sin fila del registro.

```bash
yarn install
yarn dev        # http://localhost:5190/catastro/ (la `base` de ADR-0030 §2)
yarn build      # tsc + vite build
yarn verificar  # solo los tipos
yarn lint       # las prohibiciones de eslint.config.mjs
yarn reglas     # cada prohibición muerde sobre su muestra que la viola
yarn rutas      # lo que esta interfaz dice del backend sigue siendo verdad:
                # sus rutas, sus accesos, los campos por los que ofrece ordenar,
                # los ocho tramos del código catastral contra la
                # `ComposicionCatastral` del backend, los campos del cuerpo
                # del alta contra su `record`, y **cada lista de `src/api/`
                # contra el enumerado del que dice salir** —en los dos
                # sentidos, porque fallan distinto: lo que falta aquí es una
                # opción que no se puede elegir y lo que sobra es un 422 al
                # enviar—. Toda lista está pareada o declarada como no
                # derivada con su motivo: la que no diga de dónde sale es un
                # hallazgo
yarn datos      # en `src/datos/` no hay ni una cifra, solo rotulos y motivos
yarn motor      # `.nvmrc` y `engines` dicen lo mismo, vite lo admite, y ningún
                # guión se llama como un comando de yarn —se llamaba `node`, y
                # `yarn node` es el comando de yarn: el arnés no corría—
yarn mirar      # recorre los 16 destinos y sus 35 vistas en Chromium y guarda
                # una captura de cada uno en .capturas/; falla ante un error de
                # consola o si el <main> se queda en blanco —que es como falla
                # de verdad una pantalla a medio hacer: en silencio—
yarn errores    # que lo que `cliente.ts` distingue LLEGUE a la pantalla: rompe
                # una sola ruta con seis rechazos que comparten el mismo mensaje
                # —403, 500 con incidencia, 422 con detalles, 422 con y sin
                # llave, y la red caída— y exige que las seis salgan DISTINTAS,
                # que «Reintentar» aparezca sólo donde reintentar puede cambiar
                # algo, y que reintente SU lectura y no otra
yarn sin-red    # compila CON EL PROXY APAGADO, corta la red y comprueba que
                # ninguna pantalla enseña una cifra —y que todas siguen diciendo
                # QUÉ ruta no pudieron leer—
yarn impedimentos # ningún control apagado sin decir por qué, y ninguno CORTADO
                # por el borde a la anchura del artboard (1 440 px): uno apagado
                # no se puede pulsar y sí se puede ver; uno cortado no se puede
                # ni lo uno ni lo otro, así que no se concluye nada
yarn paleta     # la paleta de comandos se opera sólo con el teclado
yarn ejercicios # el desplegable de ejercicios sale del RELOJ, y se mide
                # MOVIÉNDOLO: fija el reloj del navegador en dos años que no son
                # éste y comprueba qué años ofrece, cuál enseña elegido y —lo que
                # de verdad manda— cuál VIAJA en `?ejercicio=` a los tres cuadros.
                # Sin mover el reloj no mide nada, y sale con 2 si detecta que no
                # se movió: los cuatro literales que #48 quitó coincidían hoy con
                # el reloj letra por letra
yarn ficha      # la ficha dibuja lo que la respuesta TRAE, y dice lo que no
                # existe: compara las filas del DOM con el JSON que la página
                # recibió en las cuatro clases de ficha, exige que ninguna cifra
                # de la sección de obras complementarias venga de otro sitio que
                # no sea el cuerpo —no hay valor que enseñar: el Anexo III no
                # está transcrito y `otra_instalacion` no tiene columna de
                # importe—, y mide el `?historico=` en las dos direcciones: que
                # viaje en la pestaña que lo pinta y NO en la que no
yarn transiciones # cada fila de la cola de fiscalización ofrece EXACTAMENTE lo
                # que su estado admite, y ni uno más. El dominio ya impide el
                # atajo —`Candidato.verificadoEnCampo()` exige venir de gabinete—,
                # así que ofrecerlo no rompe nada: contesta 409. Por eso no lo ve
                # ningún otro arnés, y por eso hace falta éste: un camino que no
                # existe manda a concluir que el sistema está roto. Ejecuta además
                # UN acto y comprueba que la fila cambia de estado y de lo que
                # ofrece, porque comparar una cola en reposo se cumple sola
yarn territorio # las cinco escrituras del catálogo territorial, conducidas de
                # verdad: rellena TODO control editable con una marca y exige que
                # cada marca aparezca en el cuerpo que salió por `fetch`, que ese
                # cuerpo NO traiga lo que el servidor descarta —el `activo` del
                # alta, el `codigo` de un `PUT`: están en el `record` y el
                # controlador los tira—, que ninguna cifra del panel de resultado
                # falte en el JSON —los conteos llegan NULOS al escribir y un cero
                # ahí se pinta igual que uno contado—, que lo que no se deshace no
                # escriba hasta confirmarse aparte, que la lista siguiente traiga
                # lo recién escrito, y que los cinco rechazos —dos «409» de verdad
                # y un 404, un 422 y un 403 inyectados— digan QUÉ HAY QUE HACER
yarn imagen     # los dos archivos que deciden CÓMO SE SIRVE: levanta `nginx.conf`
                # sobre la base del `Dockerfile` y pregunta POR HTTP si las tres
                # cabeceras de seguridad llegan en cada ruta —`add_header` no se
                # hereda, así que juntarlas «para no repetirlas» las apaga—, y
                # comprueba que nada que git no vea entra en el contexto de
                # construcción. Y desde #102, que el `USER` esté EN NÚMERO —el
                # kubelet no puede verificar uno nombrado contra `runAsNonRoot`—
                # y que este nginx **no reenvíe a ningún sitio**
yarn identidad  # la PUERTA, medida sobre el paquete que se publica: compila su
                # propia vista previa con el proxy apagado, abre un navegador y
                # lee la URL con la que la aplicación se va al emisor. Afirma que
                # el `redirect_uri` es la raíz de la APLICACIÓN —`/catastro/`— y
                # no la del sitio (`rentas`#71), el reto S256, el cliente, el
                # alcance, que el token NO toca el almacenamiento, y que las
                # señas servidas por `configuracion.js` mandan sobre las horneadas
```

`mirar`, `impedimentos`, `paleta`, `errores`, `ejercicios`, `ficha`, `transiciones` y `territorio` necesitan una vista previa levantada; si no está en el 5190, se le dice con
`CATASTRO_BASE=http://localhost:5210 yarn mirar`. **`CATASTRO_BASE` es el ORIGEN y nada más**:
la ruta de la aplicación —`/catastro`— la pone `verificaciones/base.mjs` leyéndola de
`vite.config.ts`, que es quien la decide; escribirla en cada arnés sería el noveno literal que
se queda viejo el día que cambie. `sin-red` e `identidad` **levantan la suya**, y hace falta:
la bandera del proxy la resuelve Vite al compilar, así que correrlos contra otra vista previa
mediría el paquete equivocado. `imagen` necesita **Docker**, y sin Docker **sale con 2, no se
omite**: una comprobación bloqueante que se salta a sí misma deja el flujo en verde sin haber
verificado nada. No construye la imagen del frontend: copia `nginx.conf` sobre `nginx:…` y siembra
una raíz de mentira, así que no depende de que `yarn build` haya corrido.

## Cómo está armado

```
src/
  api/            La ÚNICA puerta por la que salen las peticiones
    cliente.ts    solicitar() · descargar() · ErrorDeApi · RespuestaPaginada
    catastro.ts · urbano.ts · grd.ts · fiscalizacion.ts · consultas.ts ·
    parametros.ts   los tipos, campo por campo, de los `record` del backend
    useRecurso.ts   una lectura con sus cuatro estados
  datos/          Los RÓTULOS: columnas, motivos y enumerados. Ni una cifra
    catastro.ts   los del módulo · alta.ts   los seis pasos del asistente
    fiscalizacion.ts  los del ciclo: actos, campos y lo que el backend no publica
    actos.ts      los que comparte CUALQUIER acto de escritura: la observación
                  obligatoria, lo que dice el primario apagado y la confirmación
                  aparte de lo que no se deshace
  simulado/       La pieza que desaparece (ADR-0010)
    proxy.ts      sustituye `fetch` y devuelve `Response` de verdad
    respuestas.ts la forma de un rechazo, de un listado y de lo recién creado
    ciclo.ts      las nueve operaciones de fiscalización. Recuerda lo escrito
                  mientras dure la página, porque sin memoria la lectura
                  siguiente contradiría a la escritura anterior — un par que el
                  backend no puede producir
    catalogo.ts   las cinco escrituras del catálogo territorial (#72). Recuerda
                  por otro motivo, y se volvió a decidir en vez de heredarlo: la
                  hoja Territorio es un maestro-detalle donde el maestro ES la
                  lista, así que sin memoria el alta contesta `201` y el árbol
                  vuelve igual que antes — que es como se ve un alta que no se
                  guardó. Medido: `territorio.mjs` da «se dio de alta X y
                  «[data-lista]» NO lo trae»
    servidas.ts   lo que el backend YA sirve. Nace vacía y crece hasta las 64
    padron.ts     GENERADO de `infra/carga-de-datos/ejemplos/`: el padrón y el
                  detalle de las 23 fichas, con sus dos versiones
    datos.ts      lo que no tiene archivo de ejemplo
  shell/
    Shell.tsx     barra global · panel · pestañas · barra de título · paleta
    modulos.ts    el registro de los seis módulos y sus dieciséis hojas
    ruta.ts       `#/<modulo>/<destino>/<sujeto>?<filtros>`
    ejercicios.ts qué años ofrece la barra global, derivados del reloj (#48)
  ds/             El sistema de diseño del artboard
    Acto.tsx      el formulario de una escritura: observación obligatoria,
                  primario apagado con su motivo, confirmación aparte de lo
                  irreversible y QUÉ HAY QUE HACER con cada rechazo (#71, #72)
    tokens/       colores, tipografía y medidas, con sus valores literales
    fuentes/      Source Sans 3, auto-hospedada
  modulos/<k>/    Un módulo por carpeta
    catastro/AltaDeFicha.tsx   el asistente de seis pasos del alta
    fiscalizacion/Fiscalizacion.tsx  el ciclo entero: las dos compuertas, la
                  evidencia, el acta, la anulación y el cierre (#71)
verificaciones/   Los quince arneses, sus vistas y las muestras que violan cada regla
                  Trece miran `src/`; `imagen.mjs` mira los dos archivos que deciden
                  cómo se sirve: `nginx.conf` y `Dockerfile`; `identidad.mjs` mira la
                  PUERTA, sobre el paquete construido y sin emisor de mentira delante
  base.mjs        dónde vive la aplicación dentro del sitio, leído de `vite.config.ts`
  emisor.mjs      el emisor OIDC de mentira que los nueve arneses de pantalla ponen al
                  otro lado de la puerta para poder llegar a las pantallas
  registro.mjs    compila `src/` al vuelo, para que lo comparado salga del fuente
  vistas.mjs      los ESTADOS de una pantalla que su destino a secas no dibuja
  reposo.mjs      esperar a que la pantalla se asiente, con el plazo fijo como TOPE
  presupuesto.mjs el reloj del job de navegador, contra su propio `timeout-minutes`
```

## Las decisiones que explican el resto

**Seis módulos, no doce.** El artboard dibuja el árbol del monolito SGTM entero. Este
repositorio no es ese sistema: ADR-0029 lo reparte en cuatro, y lo que hay aquí son los cinco
módulos del backend (`nucleo`, `urbano`, `grd`, `fiscalizacion`, `parametros`) más la
ventanilla. Portar los doce dibujaría diez módulos que ningún endpoint de este backend sirve.

**El proxy intercepta en la frontera del transporte.** No es un adaptador que la aplicación
elija: sustituye `fetch`. La salida fácil habría sido que cada pantalla leyera sus datos de una
constante importada, y la trampa de esa salida es que el día que el backend exista habría que
reescribir las pantallas para que pidan por HTTP. Así no: la pantalla llama a `solicitar()` con
la ruta real y todo el camino se ejerce.

**Y se apaga de tres maneras.** Con la bandera —y entonces la rama entera **no viaja en el
paquete**, porque se carga con `import()` dinámico—; operación por operación, moviendo entradas
a `servidas.ts`; y del todo, borrando el directorio, que es su final previsto.

**No finge lo que no sabe.** No filtra, no ordena, no pagina y no valida. Un proxy que fingiera
la semántica de `?uso=Comercio` estaría inventando un comportamiento que el backend todavía no
ha decidido, y la interfaz acabaría construida contra esa invención.

**Y no persiste, salvo donde no persistir MIENTE.** Son dos sitios y los dos están escritos con
su motivo: `ciclo.ts` porque simula una máquina de estados, y `catalogo.ts` porque la hoja
Territorio es un maestro-detalle donde el maestro es la propia lista. La regla que queda no es
«no guardar» sino la de siempre: **el proxy no puede producir un par de respuestas que el backend
no pueda producir**, y una lectura que contradice a la escritura anterior es exactamente eso.
Lo demás sigue igual: la memoria dura lo que la página y se olvida al recargarla, así que toda
captura de los arneses se puede volver a producir.

**El mismo origen, o nada.** `backend/` no tiene ni una línea de CORS —cero ocurrencias de
`cors` y de `allowedOrigins` en todo el árbol—, así que un React servido desde otro origen se
bloquea antes de que el backend conteste. En desarrollo lo reenvía Vite; **en el clúster lo parte
el ingreso**, que manda `/catastro/api/v1` al backend y `/catastro` a esta interfaz dentro del
mismo `Host`. En `nginx.conf` **ya no hay ningún `proxy_pass`**, y por eso: el que había apuntaba
a `catastro:8080` —el nombre del servicio del `compose.yaml`—, que en Kubernetes no resuelve, y
nginx resuelve el anfitrión de un `proxy_pass` **al arrancar**, así que el pod no arrancaba.

**El token vive en MEMORIA, y en ningún otro sitio.** `api/identidad.ts` hace el código de
autorización con PKCE S256 contra Keycloak —escrito a mano, sin `keycloak-js`— y guarda lo que
canjea en una variable de módulo, que se muere con la pestaña. Nada de `localStorage`: esta
interfaz corre en PCs de ventanilla que varios turnos comparten, y un token persistido sobrevive
al cierre del navegador (ADR-0030 §3). Lo vigila la prohibición `token-en-almacenamiento`. Lo
único que sobrevive al rebote es el verificador PKCE, en `sessionStorage`, porque sin él no hay
canje al volver — y no es una credencial.

**El `redirect_uri` es la raíz de la APLICACIÓN.** `origin + import.meta.env.BASE_URL`, o sea
`…/catastro/` y no `…/`. Confundirlas costó el acceso a producción en `rentas` (#71), donde el
defecto llegó **con su prueba unitaria en verde**: el entorno de pruebas no declaraba la misma
`base`, así que la prueba afirmaba el valor equivocado siendo coherente. Por eso aquí lo mide
`yarn identidad` sobre el paquete construido y servido de verdad.

**El emisor OIDC NO se hornea en la imagen.** Vite resuelve `import.meta.env.VITE_*` al compilar,
así que un emisor escrito ahí convertiría la imagen en la imagen **de un ambiente**. Sale de
`public/configuracion.js`, que viaja **vacío** a propósito y sobre el que el `ConfigMap` del
clúster se monta al desplegar (`src/api/configuracion.ts`, tres escalones).

**El `municipalidadId` no se envía nunca.** Sale del claim `municipalidad_id` del token, así que
un defecto de esta interfaz no puede filtrar entre municipalidades: no tiene por dónde.

**Los importes son texto.** `Dinero`, `Alicuota`, `Porcentaje` y `AreaM2` viajan como cadena
JSON con decimal plano. Se declaran `string` y se pintan como texto; pasarlos por `Number` para
volver a formatearlos es como se pierde un decimal, y lo prohíbe ESLint.

**Hay UN reloj, y está en `App.tsx`.** Los años del desplegable de la barra global eran cuatro
literales congelados, y el 1 de enero de 2027 la interfaz se habría quedado sin poder elegir el
año en curso —sin error, sin aviso, enseñando los cuadros del año anterior—. Se derivan del
reloj (#48). La sexta regla de la casa dice «sin reloj» para las **reglas tributarias**, porque
recalcular 2027 en 2037 debe dar el mismo céntimo; esto no calcula nada y no es una de ellas.
El reloj se lee **una vez al montar** y las dos derivaciones —qué años se ofrecen y cuál viene
elegido— salen del mismo instante; las funciones que las hacen (`shell/ejercicios.ts`) reciben
la fecha y son puras, que es lo que permite que `yarn ejercicios` **mueva el reloj**. Y la lista
sigue siendo una **suposición**: nadie le ha preguntado al backend qué ejercicios tienen conjunto
sellado —esa lectura no existe y es **#51**—, así que elegir uno sin sellar da 404 con
`parametroQueFalta`, que al menos se ve.

## Lo que esta interfaz NO hace, y lo dice en pantalla

- **No pinta ningún mapa.** No por falta de datos: elegir la librería es una decisión propia
  (ADR-0022, ADR-0037) y no la toma este trabajo. `Plano catastral` enseña lo que el backend
  publica —el marco y cuántos lotes se quedan fuera por no tener polígono— y dice por qué.
- **No cablea OIDC.** ADR-0030 §3 pone la sesión y los permisos en `rentas`, y aquí no hay
  ningún endpoint de «quién soy». El menú de sesión lo dice en vez de fingir tres opciones.
- **No decide si un giro es compatible con una zona**, ni calcula ningún tributo, ni determina
  ningún arbitrio con el frente lineal. Es la frontera de ADR-0024.

## El alta de una ficha, que es la única escritura

Se abre desde la acción primaria de la lista de Predios y vive dentro de ella
—`#/catastro/predios/nuevo?paso=terreno`—, como en el artboard. Tres decisiones
que hay que saber antes de tocarla:

**El código se compone en un solo sitio.** `COMPOSICION_DEL_CODIGO`, en
`src/api/catastro.ts`, declara los ocho tramos con su largo, y de ahí salen los
campos, sus `maxlength`, sus `aria-label` y la concatenación con relleno de
ceros. **No hay ningún `23` por la pantalla**: ADR-0036 dice que el largo lo
decide el tenant. Los ocho tramos de aquí y los **diez** de
`ComposicionCatastral.DEL_MANUAL` son la misma composición con distinto grano
—el artboard teclea el ubigeo entero en un campo de seis y el backend lo reparte
en tres de dos, que es lo que devuelve `CodigoReferenciaCatastral.ubigeo()`—, y
`yarn rutas` comprueba que el reparto cuadra tramo a tramo.

**Los seis pasos se portan enteros y no todos sus campos viajan.** El artboard
dibuja **cuarenta y ocho** campos y `FichaController.PeticionDeAlta` admite menos
de la mitad. Y el modo de fallo no es un error: el cuerpo es una **lista blanca**,
así que un campo que el `record` no tenga **se descarta en silencio** y el
servidor contesta que la ficha se creó. Por eso cada campo declara `viaja` con el
nombre del campo del contrato que llena, o `null` con su motivo medido; la
pantalla lo marca, el resumen de «lo que se va a registrar» se compone
**recorriendo la petición ya armada** —no una lista escrita al lado—, y ningún
campo que no viaja puede bloquear el alta.

**Los tres desenlaces se tratan por separado, porque son tres trabajos.** **422**
se arregla en el asistente y el servidor nombra el campo, que aquí se señala
diciendo en qué paso está. **409** dice que el código ya está inscrito: mientras
el código tecleado siga siendo el que el servidor rechazó, el primario no está
disponible y su `title` lo dice. **404** dice que la vía, el sector o la manzana
no existen, y eso se arregla en Territorio. El artboard sabe el duplicado antes
de preguntar —lo compara con una constante suya— y aquí no se puede: el padrón es
del servidor, y comprobarlo desde la pantalla sería una lectura que no vale para
el instante siguiente.

## Los estados de una pantalla, y no solo sus destinos

`DESTINOS` sale del registro y abre cada hoja **vacía**: sin sujeto y sin
filtros. Eso basta mientras una hoja sea una tabla y deja de bastar en cuanto es
un maestro-detalle: con `#/catastro/predios` a secas, el panel de detalle no se
dibuja nunca y el arnés informa en verde sobre media pantalla.

`verificaciones/vistas.mjs` declara esos estados —el predio abierto por cada una
de sus tres pestañas, el catálogo vial, las dos matrices de cuadros— y los
recorren `mirar`, `sin-red` e `impedimentos`. Es una lista escrita a mano, así
que trae **su propia guarda**: toda vista tiene que nombrar un destino que el
registro declare. Sin ella, una hoja renombrada dejaría las vistas apuntando a
un destino que el armazón resuelve al inicial, la captura saldría llena y no lo
vería nadie.

## Dos cosas medidas que hay que saber antes de tocar

**El panel mide 252 px y el issue decía 246.** El artboard escribe `flex:0 0 252px` y «246» no
aparece en él como medida: los 246 son del otro frontend, `sgtm/frontend`, cuya paleta el AC-3
manda expresamente no copiar. Se toma la del artboard, que es la especificación.

**No hay alias `@/*`, y es a propósito.** El `tsconfig.json` del precedente declara
`paths: { "@/*": ["src/*"] }` y su `vite.config.ts` no declara el alias: eso compila con `tsc` y
revienta en `vite build` el día que alguien lo use. O se declara en los dos sitios o en ninguno.

## Cuatro sitios donde el artboard pide algo que este sistema no sabe

El artboard dibuja el marco del monolito SGTM, donde catastro y predial son el
mismo sistema. Aquí no lo son (ADR-0029), y la frontera de ADR-0024 le prohíbe a
`catastro` saber lo que es una deuda. Los cuatro están escritos en pantalla con su
motivo, en `src/datos/catastro.ts`:

- **El autovalúo de cada fila del padrón.** No hay ninguna lectura de valuación
  en este backend: ninguna ruta publica el hecho sellado de ADR-0027. La corrida
  **sí produce cifras desde el 2026-09-06** —4 de los 23 predios del padrón de
  demostración, en cuanto se firmó D-11—, y aun así no hay por dónde pedirlas.
  Donde el artboard pone la cifra, la lista pone la manzana y el lote.
- **La cobertura medida en «fichas conciliadas».** Lo dice el propio backend:
  `ConsultaController` declara `conciliadaConRentas` y **redirige** la petición
  que lo trae a `/catastro/fichas/conciliacion`, porque componer las dos mitades
  es de `rentas` desde #344 (ARQ-01 §4, ADR-0015 §2). La barra mide `fichado`
  sobre los predios ACTIVOS del sector —lo que `SectorConConteos` cuenta— y el
  pie lo dice.
- **Las vías colgando de un sector.** `GET /catastro/vias` **rechaza** el filtro
  `sector` con un 422 explícito —la tabla no guarda el sector—, así que el árbol
  tiene un solo nodo «Catálogo vial» en vez de uno por sector.
- **Las cuatro insignias con sus etiquetas literales.** «Conciliada», «Sin
  conciliar», «En verificación» y «Con licencia de obra» son estados de `rentas`
  y de licencias, y ninguno viaja en un `record` de este backend. Los cuatro
  tonos sí se usan, con las etiquetas que este sistema publica:
  `ACTIVO`/`DADO_DE_BAJA`, `Fichado`/`Sin ficha`, `PROPUESTA`/`CONFIRMADA` y el
  tipo de ficha.

## Un hueco del backend que se nombra y no se rellena

> **`sectores` y `calles` ya no son el segundo.** Estuvieron fuera de `CatalogoDelSistema`
> —ocho endpoints contestando 403 a todo el mundo, con la guarda del backend en verde porque
> buscaba literales de cadena y esos dos accesos se pasan como constante—, y **#43** los metió:
> hoy el catálogo declara **dieciséis** opciones y `yarn rutas` no nombra ningún huérfano.

- **Fiscalización no publica ni el listado de campañas ni ninguna lectura de actas.** De sus
  **trece** operaciones, **cinco** son lecturas —el censo decía «once» y «cuatro», y las dos
  cifras estaban viejas: #17 añadió los hallazgos por predio y #23 el cierre y la anulación—.
  Ninguna enumera campañas y ninguna lee un acta por campaña o por hallazgo, así que las
  pantallas piden el identificador a mano y lo dicen, en vez de dibujar una tabla contra una
  operación que no existe. La única forma de LEER un acta es por el predio del hallazgo, donde
  viaja dentro de la respuesta.
- **Tampoco publica la lectura de UN candidato ni de UN hallazgo sueltos**, así que la hoja de
  Actas ofrece los dos actos que cuelgan de un hallazgo y deja que el servidor conteste si ese
  hallazgo está dejado sin efecto: es una respuesta con su motivo, no un camino inventado.
- **Y la corrida de detección NO es un endpoint** desde #30: corre en el perfil `batch`. Por eso
  la pantalla de campañas **no lleva botón de «lanzar detección»** y dice dónde corre. Ponerlo
  reintroduciría por la interfaz lo que aquel issue sacó del backend, y hoy no fallaría —no hay
  ni un polígono cargado— sino el día de la primera carga cartográfica.

## Un error se dice ENTERO, y las tres superficies que no lo hacían

`src/api/cliente.ts` distingue doce códigos, `faltaUnaCifraNormativa`,
`reintentable`, `detalles` e `incidencia`. De las 29 lecturas de `src/modulos`,
**26 pasan por `<Lectura>` → `Fallo`** y ahí las cuatro llegan intactas. Tres no
pasaban (#49), y las tres eran invisibles de la peor manera: la primera enseñaba
datos plausibles, la segunda un botón que parecía funcionar, la tercera un
mensaje que parecía completo.

- **El catálogo vial en 403 dejaba el cuadro de aranceles entero.** `calles` es
  un `@RequiereAcceso` distinto de `aranceles`, así que hay usuarios con uno y
  sin el otro. Nadie leía `vias.error`, `viaPorId` caía a un `Map` vacío y la
  tabla pintaba `` `Via ${a.viaId}` `` y `'—'`: seis filas de «Via 1 — Sin tramo
  388.00» sin un solo aviso. **`Via 1` no se distingue del nombre de una vía.**
  Ahora el fallo del catálogo sale con su `Fallo` encima de la tabla y la celda
  dice cuál de los tres estados es —no se pudo leer, se está pidiendo, o se leyó
  y esa vía no está en él—, porque piden trabajos distintos.
- **El «Reintentar» del Panel reintentaba dos de las cuatro lecturas.** El Panel
  pide `predios`, `sectores`, `predios/plano` y `fichas`; con las cuatro en 500
  salían **2** botones —de las dos `<Lectura>` de más abajo— y cada uno re-pedía
  **una**. El padrón y el plano quedaban muertos hasta recargar la página.
  **Cada tarjeta ofrece ahora el reintento de SU lectura** —medido: 6 botones, y
  los cuatro de las tarjetas re-piden su ruta y sólo la suya—. La otra opción era
  un botón general arriba, y se descartó porque reintentaría lecturas que no
  fallaron. Las tarjetas son `<section aria-label>`: una región con nombre, que
  además es lo que hace medible «el reintento de esta tarjeta».
- **`Consultas.bajar()` destruía el `ErrorDeApi` en el `catch`.** Se quedaba con
  `e.mensaje`, así que el código, `detalles`, `incidencia`, `parametroQueFalta` y
  `reintentable` morían ahí. Medido con el mismo mensaje en cuatro escenarios
  —500 con incidencia, 422 con `{ejercicio}`, 422 con `{ejercicio, llave}` y
  403—, la pantalla decía **byte a byte lo mismo**. Ahora el estado guarda el
  error entero y lo pinta el mismo `Fallo` que las otras 26.

Lo mide `yarn errores`, y **la decisión que hace que mida algo es que los seis
escenarios comparten el mismo `mensaje`**: con mensajes distintos, «las pantallas
salen distintas» se cumpliría pintando `error.mensaje`, que es exactamente el
tercer defecto. Lo esperado —los títulos de los doce códigos, la regla de
`reintentable`, la frase de una línea— se deriva compilando `cliente.ts` y
`componentes.tsx` al vuelo, y no se copia en el arnés.

## Dos defectos que se encontraron midiendo, y su arreglo

**`parametroQueFalta` es un objeto y se leía como cadena.** `cliente.ts` hacía
`typeof cuerpo.parametroQueFalta === 'string'`, y el backend lo emite con
`ParametroQueFalta.comoMiembro()`, que compone `{ejercicio, llave?}`. O sea que
`faltaUnaCifraNormativa` valía `false` **siempre**: el único discriminador que
separa «falta un campo de la petición», que quien atiende arregla, de «falta
publicar», que no arregla nadie desde la pantalla, no llegaba nunca. No lo
delataba nada, porque el camino de la ausencia y el de «no lo entendí» son el
mismo `undefined`. Y de paso: **las tres lecturas de cuadro nunca nombran una
llave** —sus controladores sólo atrapan `EjercicioSinSellar`, cuyo `llave()` es
`Optional.empty()`—, así que la pantalla dice «falta sellar el conjunto del
ejercicio N» y no inventa ninguna.

**El proxy encaminaba `/catastro/predios/plano` a `/catastro/predios/{predioId}`.**
Recorría la tabla en el orden en que está escrita, y el patrón con parámetro va
antes. `Number('plano')` daba `NaN` y la lectura del plano catastral contestaba
**404 «No hay ningún predio con ese identificador»** desde #32, sin que nadie
tuviera cómo notarlo: en esa pantalla un 404 se lee como «aquí no hay lotes»,
que es justo lo que se espera. Se ordena de lo literal a lo parametrizado y una
guarda lo comprueba al importar el módulo — un arreglo por reordenación se
deshace solo en cuanto alguien añade una entrada al final.

## El reloj del recorrido: se espera a una condición, no a un plazo

Cuatro arneses recorren las 51 pantallas —`mirar`, `impedimentos`, `sin-red` y
`errores`— y los cuatro esperaban con `page.waitForTimeout(N)`: un plazo fijo,
elegido para el peor caso, que se paga entero en todos los demás. Instrumentados
uno a uno antes de tocar nada:

| arnés | total | navegar | esperas fijas | trabajo de verdad |
|---|---|---|---|---|
| `errores` | 122,7 s | 1,4 s | **117,8 s** | 3,5 s |
| `sin-red` | 58,7 s | 0,08 s | **56,2 s** | 2,4 s |
| `mirar` | 39,9 s | 0,6 s | **35,8 s** | 3,5 s (capturas) |
| `impedimentos` | 31,6 s | 0,6 s | **30,7 s** | 0,2 s |

**El 95 % del reloj era dormir**, y las 222 navegaciones —lo que se propuso
ahorrar reutilizando cargas de página— cuestan **2,7 s: el 1,1 %**.

`reposo.mjs` cambia «duerme N» por «espera **hasta** N a que la pantalla deje de
cambiar». Dos propiedades, y las dos importan:

- **El tope es exactamente el plazo que sustituye**, así que el peor caso es el
  de antes y ningún runner lento empeora.
- **La condición no es la afirmación**: se espera a que el texto de `body` no
  cambie y a que no quede ninguna petición en vuelo, nunca a que salga el título,
  la cifra o el control que después se comprueba. Un arnés que espera a lo que va
  a afirmar no puede ponerse rojo.

**`networkidle` no sirve aquí, y está medido por qué**: con el proxy de datos
encendido no hay ni una petición de red —el proxy *sustituye* `fetch` y contesta
desde la propia página, con 120-320 ms de latencia simulada—, así que Chromium
declara la red en reposo mientras las lecturas siguen en curso. Y las hojas
maestro-detalle encadenan: la lista contesta, se dibuja, y **eso** dispara la
lectura del detalle. Por eso la huella incluye cuántas peticiones se han hecho y
cuántas siguen en vuelo, contadas envolviendo `fetch` con el mismo `get`/`set`
que `errores.mjs` ya usaba para inyectar sus rechazos.

**Y lo que impide que este cambio pierda una afirmación son dos guardas, no la
buena voluntad.** En `errores` y en `sin-red` la lectura temprana va al rojo por
sí sola —falta el título, falta el mensaje, el `<main>` se queda mudo o anónimo—.
En `impedimentos` no: ahí sólo baja un número, y **el número no es estable**.
Medido con la huella rota para que ignore las peticiones, el mismo defecto da
857, 866, 879, 896, 897, 902, 905, 911, 920 o 956 controles según la corrida y la
máquina, siempre con `exit 0`, donde el árbol sano dice **965**. Quien revise no
tiene con qué compararlo, y escribir 965 en el arnés sería un segundo sitio con
la misma verdad que además habría que tocar cada vez que entra una pantalla
legítima — o sea que se tocaría sin pensar.

La primera guarda mira **el mecanismo**: una espera que vuelve antes de poder
haber observado un solo intervalo de quietud saca el arnés con **2**. Hace falta
porque el detector degradado —devolver `true` en la primera lectura— dejaba
`impedimentos` en 965 y `errores` en 60 renders verdes, sostenidos por la puerta
de las peticiones.

La segunda mira **el resultado**, y es la que cierra el agujero: en cada pantalla,
después de contar, se vigila el DOM 150 ms con un `MutationObserver`. Si nada se
movió, la espera acertó y no se paga nada más; si algo se movió es que volvió
pronto, y entonces se esperan 1 500 ms —un plazo fijo, que **no** usa el detector
que se está juzgando— y se vuelven a contar los mismos dos conjuntos. No se
compara contra ningún número escrito: **se compara la espera consigo misma con
más tiempo**, igual que `ejercicios.mjs` mueve el reloj y `territorio.mjs` compara
con el JSON que la página recibió.

Y se vigilan **las 51 y no una muestra**, porque *cuál* pantalla pierde depende de
cómo caiga la carrera: una muestra de cuatro escritas a mano no cazó el defecto
ni una vez, y una de ocho derivada de la corrida lo cazó **2 de 3 veces**. Con las
51 son **6 de 6**. Sale casi gratis porque en el árbol sano **no se mueve
ninguna** —0 de 51 tienen una sola mutación en esos 150 ms—, así que el margen no
se paga nunca: **7,6 s en total, 0,15 s por pantalla nueva**.

Y el observador se comprueba a sí mismo: «no se movió nada» y «no estaba mirando»
se ven igual, así que al instalarlo se hace **una mutación a propósito** y se
exige verla. Sin eso, un observador roto dejaría la calibración cumpliéndose sola
en las 51 — medido: quitarle el `observe` da «En 51 de 51 pantalla(s) el
observador no vio ni la mutación que este arnés hace a propósito».
