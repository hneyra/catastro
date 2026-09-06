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
yarn rutas      # lo que esta interfaz dice del backend sigue siendo verdad
yarn node       # `.nvmrc` y `engines` dicen lo mismo, y vite lo admite
yarn mirar      # recorre los 16 destinos en Chromium y guarda una captura de
                # cada uno en .capturas/; falla ante un error de consola o si
                # el <main> se queda en blanco —que es como falla de verdad una
                # pantalla a medio hacer: en silencio—
yarn sin-red    # compila CON EL PROXY APAGADO, corta la red y comprueba que
                # ninguna pantalla enseña una cifra
yarn paleta     # la paleta de comandos se opera sólo con el teclado
```

`mirar` y `paleta` necesitan una vista previa levantada; si no está en el 5190, se le dice con
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
verificaciones/   Los cinco arneses, y las muestras que violan cada regla
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

## Dos cosas medidas que hay que saber antes de tocar

**El panel mide 252 px y el issue decía 246.** El artboard escribe `flex:0 0 252px` y «246» no
aparece en él como medida: los 246 son del otro frontend, `sgtm/frontend`, cuya paleta el AC-3
manda expresamente no copiar. Se toma la del artboard, que es la especificación.

**No hay alias `@/*`, y es a propósito.** El `tsconfig.json` del precedente declara
`paths: { "@/*": ["src/*"] }` y su `vite.config.ts` no declara el alias: eso compila con `tsc` y
revienta en `vite build` el día que alguien lo use. O se declara en los dos sitios o en ninguno.

## Dos huecos del backend que se nombran y no se rellenan

- **`sectores` y `calles` no están en `CatalogoDelSistema`**, que declara catorce opciones. Sus
  controladores pasan el acceso como **constante** (`SectorController.ACCESO`) y la guarda que
  compara los dos conjuntos busca literales de cadena, así que no los ve y sigue en verde.
  Mientras no se siembren, `Catastro · Territorio` contestará 403. `yarn rutas` lo nombra.
- **Fiscalización no publica ni el listado de campañas ni ninguna lectura de actas.** De sus
  once operaciones, cuatro son lecturas. Las dos pantallas lo dicen y piden el identificador a
  mano, en vez de dibujar una tabla contra una operación que no existe.
