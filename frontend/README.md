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
                # `ComposicionCatastral` del backend, y los campos del cuerpo
                # del alta contra su `record`
yarn datos      # en `src/datos/` no hay ni una cifra, solo rotulos y motivos
yarn node       # `.nvmrc` y `engines` dicen lo mismo, y vite lo admite
yarn mirar      # recorre los 16 destinos y sus 13 vistas en Chromium y guarda
                # una captura de cada uno en .capturas/; falla ante un error de
                # consola o si el <main> se queda en blanco —que es como falla
                # de verdad una pantalla a medio hacer: en silencio—
yarn sin-red    # compila CON EL PROXY APAGADO, corta la red y comprueba que
                # ninguna pantalla enseña una cifra —y que todas siguen diciendo
                # QUÉ ruta no pudieron leer—
yarn impedimentos # ningún control apagado sin decir por qué
yarn paleta     # la paleta de comandos se opera sólo con el teclado
```

`mirar`, `impedimentos` y `paleta` necesitan una vista previa levantada; si no está en el 5190, se le dice con
`CATASTRO_BASE=http://localhost:5210 yarn mirar`. `sin-red` **levanta la suya**, y hace falta:
la bandera del proxy la resuelve Vite al compilar, así que correrlo contra otra vista previa
mediría el paquete equivocado.

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
    padron.ts     GENERADO de `infra/carga-de-datos/ejemplos/`
    datos.ts      lo que no tiene archivo de ejemplo
  shell/
    Shell.tsx     barra global · panel · pestañas · barra de título · paleta
    modulos.ts    el registro de los seis módulos y sus dieciséis hojas
    ruta.ts       `#/<modulo>/<destino>/<sujeto>?<filtros>`
  ds/             El sistema de diseño del artboard
    tokens/       colores, tipografía y medidas, con sus valores literales
    fuentes/      Source Sans 3, auto-hospedada
  modulos/<k>/    Un módulo por carpeta
    catastro/AltaDeFicha.tsx   el asistente de seis pasos: la ÚNICA escritura
verificaciones/   Los ocho arneses, sus vistas y las muestras que violan cada regla
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

## Dos huecos del backend que se nombran y no se rellenan

- **`sectores` y `calles` no están en `CatalogoDelSistema`**, que declara catorce opciones. Sus
  controladores pasan el acceso como **constante** (`SectorController.ACCESO`) y la guarda que
  compara los dos conjuntos busca literales de cadena, así que no los ve y sigue en verde.
  Mientras no se siembren, `Catastro · Territorio` contestará 403. `yarn rutas` lo nombra.
- **Fiscalización no publica ni el listado de campañas ni ninguna lectura de actas.** De sus
  once operaciones, cuatro son lecturas. Las dos pantallas lo dicen y piden el identificador a
  mano, en vez de dibujar una tabla contra una operación que no existe.

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
