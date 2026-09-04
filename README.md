# `catastro`

Predio, ficha versionada, construcciones, titularidad, geometria, catalogo vial y el
arancel de terreno. **Calcula el valor del predio; no calcula el impuesto.**

> **Todavia no hay una sola linea de codigo de negocio, y este README lo dice antes que nada.**
> Lo que hay es el **descriptor de infraestructura** —como se desplegaria este sistema el dia que
> exista— y las **dos barreras bloqueantes**, que se construyeron antes que el negocio a proposito.
> El negocio llega en la etapa 5 de [ADR-0029](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0029-cuatro-sistemas-separados.md).

## Que hay hoy, y que falta

| Pieza | Estado |
|---|---|
| `infrastructure/` — el descriptor (ADR-0031 §2) | **Existe y verifica**: `yarn verificar` en verde, sin Pulumi, sin token y sin cluster |
| `.github/workflows/` — su CI | **Existe**, con tres flujos: el descriptor, las **dos barreras bloqueantes** del backend y la guarda del registro |
| `docs/30-arquitectura/adr/` | **Existe**, con 3 ADR propio(s) y su indice ⚠ ver la nota de abajo |
| `backend/` — siete modulos con el negocio dentro | **Existe.** P3 puso las barreras y **P5C trajo el contexto acotado entero**: `./gradlew build` en verde con **945 pruebas**, y `verificarArquitectura` y `verificarAislamiento` tambien |
| `docs/40-datos/baselines/V1__baseline.sql` — su esquema | **NO esta aqui todavia.** Generado y verificado, vive en [`sgtm/docs/40-datos/baselines/catastro/`](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/docs/40-datos/baselines/catastro/V1__baseline.sql) hasta que la extraccion lo traiga |
| Su frontend (`catastro-web`, ADR-0030 §1) | **NO existe** |
| La imagen `ghcr.io/hneyra/kamayuk-catastro` | **NO existe.** El `Deployment` del descriptor la nombra igual: es correcto, y en esta etapa no se despliega nada |

## Por donde entrar

- **Montar el entorno y ejecutarlo**: [`docs/D0-desarrollo/README.md`](docs/D0-desarrollo/README.md).
- **Contexto para agentes**, con las diez reglas y lo que este repositorio no hace:
  [`CLAUDE.md`](CLAUDE.md).

## El descriptor

```bash
cd infrastructure
yarn install
yarn verificar          # lint, tipos y pruebas. Sin Pulumi, sin token y sin cluster
```

Declara **su base y sus roles**, **su Deployment**, **su Job de migracion**, **sus
rutas bajo su prefijo `catastro/`**, **su egreso**, sus alertas, su panel y su inventario de claves.
No declara la etiqueta de su imagen: la pone `infrastructure`, y es lo que hace que una
liberacion normal no sea un `pulumi up` (ADR-0011 §5).

**Su egreso, que es su grafo de dependencias:**

```
catastro  ──▶  normativa, rentas
```

Llama a `normativa` por el conjunto sellado con que valoriza, y a `rentas` **solo** para
resolver el nombre del titular de un predio.

No lee deuda, no lee determinaciones y no sabe lo que es una deduccion: `ADR-0024` lo dice con
todas las letras, y es lo que permite abrir su API a desarrollo urbano sin abrir con ella el
padron tributario. **Si esa arista creciera, lo que hay que revisar es la frontera.**

Su base es la unica con **PostGIS** (`V61`, ADR-0021) y `btree_gist` (`V72`).

## Lo que este repositorio NO decide

- **La etiqueta de su imagen.** La fija `infrastructure` al componer.
- **Su namespace ni sus `PriorityClass`.** Son de alcance de cluster.
- **Como se sella un valor normativo.** Eso es de `normativa`; aqui se consume un conjunto ya
  sellado.
- **Si su descriptor se aplica.** `infrastructure` lo audita con las mismas reglas que audita los
  suyos y **se niega** si incumple: una ruta fuera del prefijo, un `Deployment` sin limites, un
  `Secret` en claro o privilegios sobre la base de otro sistema.

## De donde viene

Extraido de [`sgtm`](https://github.com/hneyra/sgtm/tree/migracion-a-microservicios), que **no se borra**: es el archivo historico y la unica copia con
`git log`. El inventario del corte —que tabla va a que repositorio, y por que— esta en
[GOB-05](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/docs/00-gobierno/inventario-del-corte.md).
