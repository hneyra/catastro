/**
 * Los ROTULOS que comparte cualquier acto de escritura, sea del modulo que sea.
 *
 * <h2>Por que existe este archivo</h2>
 *
 * #71 escribio el formulario de un acto —observacion obligatoria, primario
 * apagado con su `title`, confirmacion aparte para lo que no se deshace— dentro
 * de `Fiscalizacion.tsx`, con sus textos en `datos/fiscalizacion.ts`. Nada de
 * eso es de fiscalizacion: «Falta rellenar: » es la regla 10 dicha en voz alta,
 * y «Esto no se deshace» es lo que se dice antes de cualquier acto irreversible.
 *
 * Con el catalogo territorial (#72) hay un segundo modulo que escribe, y las dos
 * salidas malas estaban a la vista: copiar las frases —dos sitios con la misma
 * verdad, que es la forma de defecto que este repositorio lleva encontrada cinco
 * veces— o que `catastro` importara de `datos/fiscalizacion`, que ataria dos
 * contextos acotados por un rotulo. Se sacan aqui y los dos modulos las usan.
 *
 * **Aqui tampoco hay ni una cifra**, y lo comprueba `verificaciones/datos.mjs`
 * igual que en los otros dos archivos de este directorio.
 */

/** Lo que dice el boton primario cuando esta apagado, delante de lo que falta. */
export const FALTA = 'Falta rellenar: ';

/** Y mientras la peticion esta en vuelo, que tampoco es «se puede pulsar». */
export const ESCRIBIENDO = 'Escribiendo…';

export const ACTO = {
  cerrar: 'Cerrar',
  loQueSeAcabaDeHacer: 'Lo que el servidor contesto',
  /**
   * El titulo del bloque que dice **que hay que hacer** con el rechazo.
   *
   * No es lo mismo que el titulo del codigo, que dice lo que PASO. Un 409 sobre
   * un codigo de sector se arregla con otro codigo; un 404 se arregla en otra
   * pantalla; y un 403 sobre la baja logica no se arregla en ninguna, porque el
   * privilegio lo concede otra persona. Los tres se leen igual si solo se dice
   * «El estado actual no admite la operacion».
   */
  queHayQueHacer: 'Que hay que hacer',
  observacion:
    'Toda escritura exige la observacion de quien la hace, y sin ella no se guarda (RNF-052, ADR-0008). El servidor la rechaza si no explica el cambio.',
  elProxyNoPersiste:
    'Contra el proxy de datos del navegador, lo escrito dura lo que dure la pagina y se olvida al recargarla: no hay ningun servidor detras. Contra el backend, lo que se escribe se queda.',
} as const;

/**
 * Lo que se dice antes de un acto irreversible, y **se confirma aparte**.
 *
 * Dos pulsaciones y no una, y ninguna de las dos es un dialogo del navegador:
 * `confirm()` bloquea el hilo, no se puede leer con un lector de pantalla y deja
 * los arneses colgados. La segunda confirmacion es un panel de la propia
 * pantalla, con lo que se va a hacer escrito delante.
 */
export const IRREVERSIBLE = {
  titulo: 'Esto no se deshace',
  confirmar: 'Si, confirmar',
  cancelar: 'Cancelar',
} as const;

/** La observacion va la ULTIMA en todos los actos, y en todos es obligatoria. */
export const LA_OBSERVACION = {
  rotulo: 'Observacion',
  ayuda: 'Por que se hace esta escritura. Es obligatoria en todas y sin ella no se guarda nada (RNF-052).',
} as const;
