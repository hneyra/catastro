/**
 * La ruta vive en el hash: `#/<modulo>/<destino>/<sujeto>?<filtros>`.
 *
 * Asi una pantalla concreta se comparte por su URL y sobrevive a una recarga sin
 * que haga falta un servidor que la sirva —lo cual importa aqui, porque la
 * imagen es nginx sirviendo archivos estaticos—.
 *
 * <h2>Se parte solo por los DOS primeros separadores</h2>
 *
 * Un sujeto puede llevar barras. El codigo de referencia catastral no las lleva,
 * pero el `codRefCatastral` de una ficha de bienes comunes y cualquier
 * identificador que venga de un documento si pueden, y `split('/')` a secas los
 * cortaria por la mitad **sin error**: la pantalla pediria otro predio y
 * ensenaria una ficha que no es la que se compartio. Por eso el sujeto es «todo
 * lo que queda», y por eso se codifica al escribirlo.
 */
export type Ruta = {
  modulo: string;
  destino: string;
  /** Lo que la pantalla mira: un codigo, un identificador. Vacio = ninguno. */
  sujeto: string;
  filtros: Record<string, string>;
};

export const RUTA_VACIA: Ruta = { modulo: '', destino: '', sujeto: '', filtros: {} };

/**
 * Descodifica un tramo del hash, y **no revienta si no se puede**.
 *
 * `decodeURIComponent` lanza `URIError` ante un porcentaje suelto o una secuencia
 * incompleta —`%`, `%E0%A4%A`—, y eso llega a la URL sola: se pega un enlace
 * truncado por un chat, se copia media direccion, se teclea un `%` en un codigo.
 * Sin esta guarda, medido en Chromium sobre la vista previa de produccion:
 *
 *   · **En frio** —`#/catastro/predios/%E0%A4%A` abierto de nuevo— el `useState`
 *     de `App` lanza al construir el estado inicial, React no monta nada y la
 *     pagina se queda **EN BLANCO**: `#raiz` con 0 caracteres y un
 *     «PAGEERROR: URI malformed» en la consola. Es el desenlace peor de los tres
 *     —ni datos ni error— que toda esta interfaz esta escrita para evitar.
 *   · **En caliente** —cambiar el hash con la aplicacion abierta, que es lo que
 *     hacen pegar una direccion y el boton «atras»— lanza dentro del oyente de
 *     `hashchange`, el estado no se actualiza, y la pantalla **sigue ensenando
 *     la ruta anterior mientras la barra dice otra**. Se comparte esa URL y quien
 *     la abre ve algo distinto de quien la mando.
 *
 * Lo que no se puede descodificar se devuelve **tal cual**: un sujeto que no
 * casa con ningun predio acaba en «no se encontro», que es una respuesta; una
 * pagina en blanco no lo es.
 */
function descodificar(tramo: string): string {
  try {
    return decodeURIComponent(tramo);
  } catch {
    return tramo;
  }
}

export function leerRuta(hash: string): Ruta {
  const crudo = hash.startsWith('#') ? hash.slice(1) : hash;
  const sinBarra = crudo.startsWith('/') ? crudo.slice(1) : crudo;
  const corte = sinBarra.indexOf('?');
  const camino = corte >= 0 ? sinBarra.slice(0, corte) : sinBarra;
  const consulta = corte >= 0 ? sinBarra.slice(corte + 1) : '';

  /* Los DOS primeros separadores, y el resto se queda entero. */
  const primera = camino.indexOf('/');
  if (primera < 0) return { ...RUTA_VACIA, modulo: descodificar(camino) };
  const segunda = camino.indexOf('/', primera + 1);
  const modulo = camino.slice(0, primera);
  const destino = segunda < 0 ? camino.slice(primera + 1) : camino.slice(primera + 1, segunda);
  const sujeto = segunda < 0 ? '' : camino.slice(segunda + 1);

  const filtros: Record<string, string> = {};
  for (const [clave, valor] of new URLSearchParams(consulta)) {
    if (valor !== '') filtros[clave] = valor;
  }
  return {
    modulo: descodificar(modulo),
    destino: descodificar(destino),
    sujeto: descodificar(sujeto),
    filtros,
  };
}

export function escribirRuta(ruta: Ruta): string {
  const partes = [encodeURIComponent(ruta.modulo), encodeURIComponent(ruta.destino)];
  /* El sujeto se codifica ENTERO, barras incluidas: es lo que hace que volver a
     leerlo devuelva lo mismo que se escribio. */
  if (ruta.sujeto !== '') partes.push(encodeURIComponent(ruta.sujeto));
  const consulta = new URLSearchParams();
  for (const [clave, valor] of Object.entries(ruta.filtros)) {
    if (valor !== '') consulta.set(clave, valor);
  }
  const cola = consulta.toString();
  return `#/${partes.join('/')}${cola ? `?${cola}` : ''}`;
}
