# ADR-0027 — La valuación es un hecho sellado del ejercicio, no un estado del predio

| Campo | Valor |
|---|---|
| Estado | **Aceptado** |
| Fecha | 2026-09-03 |
| Decide | Dirección del proyecto |
| Depende de | [ADR-0024](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0024-la-frontera-del-calculo.md), [ADR-0029](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0029-cuatro-sistemas-separados.md) |
| Sigue la línea de | [ADR-0015](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0015-conciliacion-catastro-rentas.md) §1: un derivado con su ejercicio, no un estado que se guarda |


> Aceptado el 2026-09-04 por la direccion del proyecto, al contestar **D-22**: el producto lo opera un equipo central, no cada municipalidad. Ver [`D-22`](https://github.com/hneyra/infrastructure/blob/main/docs/00-gobierno/D-22-quien-opera-cuatro-despliegues.md) en `infrastructure`.

## Contexto

Con `catastro` y `rentas` en dos bases, algo tiene que viajar entre ellas para que la emisión sepa
cuánto vale cada predio. La forma obvia —que `rentas` consulte «el valor del predio 4821»— es la que
produce dos padrónes que se contradicen, y conviene ver por qué antes de descartarla:

- **«El valor del predio es S/ 82 400» es un estado y envejece.** Si la ficha se versiona en junio, la
  respuesta cambia, y una determinación emitida en febrero deja de poder explicarse con lo que la API
  contesta hoy.
- **No dice con que se calculo.** Dos cifras distintas del mismo predio se vuelven un misterio en vez
  de una diferencia de insumos.
- **Invita a leer «la ficha actual»**, que es exactamente lo que el reloj tributario prohibe: el
  predial se determina con la situación del predio al 31 de diciembre anterior, y un cambio de junio
  pertenece al ejercicio siguiente.

Este proyecto ya tiene la regla escrita para otras cifras —regla 9, RNF-075: «no existe *la deuda*,
existe `deudaActualizadaA(fecha)`»— y ADR-0015 ya la aplicó a la conciliación: «no existe
*conciliado*, existe `conciliadoA(ejercicio)`». La valuación es el mismo caso.

## Decisión

**Lo que `catastro` pública no es el valor de un predio: es la valuación de un predio en un
ejercicio, sellada, con la identidad de todos sus insumos.**

### 1. El hecho, entero

| Campo | Por qué esta |
|---|---|
| `predioId`, `ejercicio` | La identidad del hecho |
| `fechaDeCorte` | La situación a la que corresponde. Normalmente el 31-dic anterior |
| `valorTerreno`, `valorConstruccion`, `valorObras`, `valorDelPredio` | El desglose, no sólo el total: una diferencia se localiza en la partida |
| `fichaCatastralId` | **Qué versión de ficha** se valorizo |
| `conjuntoId` | Qué conjunto de parámetros sellado se uso ([ADR-0007](https://github.com/hneyra/normativa/blob/main/docs/30-arquitectura/adr/ADR-0007-parametros-versionados.md)) |
| `reglasVersion` | Qué catálogo de reglas ([ADR-0025](https://github.com/hneyra/normativa/blob/main/docs/30-arquitectura/adr/ADR-0025-normativa-servicio-y-libreria.md)) |
| `reglasAplicadas[]` | Los `RT-xxx` que corrieron, en orden de resolución del grafo |
| `titulares[]` | Cuotas vigentes **a la fecha de corte**: `contribuyenteId`, condición, porcentaje |
| `huella` | `sha256` del cuerpo canonico |

Es inmutable. Corregir una valuación es **publicar otra** con su motivo, igual que corregir una
edición de parámetros es publicar otra. Nunca un `UPDATE`: la tabla entra en `TABLAS_PROTEGIDAS`.

### 2. La corrida es la unidad, no el predio

Una `corrida_de_valuacion` se abre con un `ejercicio`, un `conjuntoId` y una `fechaDeCorte`, y se
cierra con un conteo y una huella agregada. **El `conjuntoId` lo fija la corrida, no cada sistema por
su cuenta**: si `catastro` resolviera el suyo y `rentas` el suyo, un sellado publicado entre las dos
resoluciones produciría un padrón calculado con dos conjuntos y ningún error visible.

`rentas` recibe `CorridaDeValuacionCerrada` y **verifica antes de emitir**: si el conteo o la huella
no cuadran, la corrida de emisión no arranca. Ese es el candado, y va antes de la emisión, no después:
un padrón emitido con el 3 % de las valuaciones sin llegar produce miles de recibos mal calculados y
se descubre en ventanilla.

### 3. En `rentas` es una proyección de sólo lectura

`valuacion_predio` no la escribe nadie salvo el ingestor de eventos, y eso **lo sostiene el motor**:
`sgtm_app` no tiene `UPDATE` sobre ella. Es la misma mecanica con la que `V54` protege el estado de
la declaración jurada — un privilegio de columna, no la disciplina del repositorio.

Cada fila lleva su procedencia: de que evento salió, con que secuencia y con que huella.

### 4. El reloj es parte del contrato

`rentas` **no lee nunca «la ficha vigente»** para determinar. Lee la valuación del ejercicio que esta
determinando. Una ficha versionada en junio de 2026 no cambia la determinación de 2026: entra a la
valuación de 2027.

La única via de vuelta al ejercicio en curso es la **determinación de oficio** de fiscalización, que
lleva acta, sustento, resolución notificable y cargo — y que ya tiene su camino: `TransferirARentas`
versiona la ficha por `catastro.TransferenciaDeFiscalizacion`, asienta la diferencia por
`cuentacorriente.GeneradorDeCargos` y emite la resolución, los tres en una transacción ([ARQ-01 §3.5](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/docs/30-arquitectura/contextos-acotados.md)).

## Consecuencias

- **Una diferencia entre los dos sistemas deja de ser un misterio.** Se comparan los insumos y se
  dice cual cambio: la ficha, el conjunto o las reglas.
- **La anti-entropia es barata.** Comparar predio a predio es caro; comparar una huella por sector es
  barato y localiza la diferencia en dos saltos. Sólo se piden en detalle los lotes que no cuadran.
- **El recalculo de 2027 en 2037 sigue dando el mismo céntimo**, que es lo que ADR-0007 exige, ahora
  también a través de una frontera.
- **Aparece una tabla más que mantener honesta** en `rentas`, y es el costo real de partir. Las ocho
  prácticas de la propuesta de arquitectura existen por esta tabla.
- **La pantalla tiene que rotular.** Cuando el funcionario ve la ficha de hoy junto a la deuda de
  2026, la única defensa contra que las compare mal es que cada cifra diga a que momento pertenece.

## Lo descartado, y por qué

- **Qué `rentas` consulte el valor cuando lo necesita.** Ata la emisión a la disponibilidad de
  catastro, no deja rastro de con que se calculo, y hace que reimprimir un valor de 2024 en 2030 lea
  la ficha actual — el mismo defecto que `declaracion_jurada.ficha_catastral_id` existe para evitar.
- **Replicar `predio` y `ficha_catastral` enteras en `rentas`.** Es copiar el modelo ajeno con todos
  sus invariantes, y el día que catastro versione distinto hay dos definiciones de «versión vigente a
  una fecha» envejeciendo aparte — que es lo que el javadoc de `FichasDelPadron` ya descarta por
  escrito para el caso de la grilla.
- **Publicar la valuación sin las cuotas de titularidad.** Obligaria a `rentas` a resolverlas por una
  segunda llamada, a otra fecha, y una titularidad resuelta a fecha distinta de la valuación es una
  base imponible mal repartida.
- **Emitir sin el candado**, confiando en que los eventos llegaron. Es la versión de este diseño que
  parece funcionar durante dos ejercicios.
