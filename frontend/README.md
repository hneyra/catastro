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
yarn dev        # http://localhost:5190
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
yarn mirar      # recorre los 16 destinos y sus 18 vistas en Chromium y guarda
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
yarn impedimentos # ningún control apagado sin decir por qué
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
yarn imagen     # los dos archivos que deciden CÓMO SE SIRVE: levanta `nginx.conf`
                # sobre la base del `Dockerfile` y pregunta POR HTTP si las tres
                # cabeceras de seguridad llegan en cada ruta —`add_header` no se
                # hereda, así que juntarlas «para no repetirlas» las apaga—, y
                # comprueba que nada que git no vea entra en el contexto de
                # construcción
```

`mirar`, `impedimentos`, `paleta`, `errores`, `ejercicios` y `ficha` necesitan una vista previa levantada; si no está en el 5190, se le dice con
`CATASTRO_BASE=http://localhost:5210 yarn mirar`. `sin-red` **levanta la suya**, y hace falta:
la bandera del proxy la resuelve Vite al compilar, así que correrlo contra otra vista previa
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
  simulado/       La pieza que desaparece (ADR-0010)
    proxy.ts      sustituye `fetch` y devuelve `Response` de verdad
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
    tokens/       colores, tipografía y medidas, con sus valores literales
    fuentes/      Source Sans 3, auto-hospedada
  modulos/<k>/    Un módulo por carpeta
    catastro/AltaDeFicha.tsx   el asistente de seis pasos: la ÚNICA escritura
verificaciones/   Los once arneses, sus vistas y las muestras que violan cada regla
                  Diez miran `src/`; `imagen.mjs` mira los dos archivos que deciden
                  cómo se sirve: `nginx.conf` y `Dockerfile`
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

**No finge lo que no sabe.** No filtra, no ordena, no pagina, no valida y no persiste. Un proxy
que fingiera la semántica de `?uso=Comercio` estaría inventando un comportamiento que el
backend todavía no ha decidido, y la interfaz acabaría construida contra esa invención.

**El mismo origen, o nada.** `backend/` no tiene ni una línea de CORS —cero ocurrencias de
`cors` y de `allowedOrigins` en todo el árbol—, así que un React servido desde otro origen se
bloquea antes de que el backend conteste. En desarrollo lo reenvía Vite; en la imagen, nginx.

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
  once operaciones, cuatro son lecturas. Las dos pantallas lo dicen y piden el identificador a
  mano, en vez de dibujar una tabla contra una operación que no existe.

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
