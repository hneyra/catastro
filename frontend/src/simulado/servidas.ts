/**
 * Las operaciones que el backend **ya sirve de verdad en este entorno**.
 *
 * El proxy de datos las deja pasar: la peticion sale a `/catastro/api/v1`, Vite
 * la reenvia al Spring Boot local y la respuesta es la del backend. Todo lo
 * demas lo sigue contestando el proxy.
 *
 * <h2>Por que hace falta un modo intermedio</h2>
 *
 * El backend no va a conectarse de golpe: son **64 operaciones**. Sin esto, la
 * integracion seria un unico salto que nadie puede probar; con esto, cada issue
 * mueve sus rutas aqui y se comprueba una a una. **Esta lista crece hasta cubrir
 * las 64, y entonces desaparece**: con el backend sirviendolo todo, el proxy se
 * apaga —`VITE_CATASTRO_PROXY_DE_DATOS=false`— y este archivo se borra. El modo
 * intermedio es transitorio y su final es parte del trabajo, no un pendiente.
 *
 * <h2>Nace vacia, y es deliberado</h2>
 *
 * Una ruta aqui **es una afirmacion**: dice que el backend la contesta *donde
 * corre la aplicacion*. Encenderla sin tener los dos procesos levantados
 * convierte cada pantalla de ese modulo en un fallo de conexion. Y si el backend
 * contesta 404 a una ruta declarada aqui, el proxy **no la tapa**: devuelve un
 * 502 que lo dice en voz alta, porque caer al simulado en silencio esconderia
 * justo lo que se quiere ver — que la ruta de la lista y la del backend no
 * cuadran.
 */
export type OperacionServida = {
  readonly metodo: string;
  /** Camino del contrato, relativo a `/catastro/api/v1`, con sus parametros entre llaves. */
  readonly ruta: string;
};

export const YA_SERVIDAS: readonly OperacionServida[] = [];

/** `/catastro/predios/{predioId}` -> `^/catastro/api/v1/catastro/predios/[^/]+$`. */
function compilar(raiz: string, ruta: string): RegExp {
  const escapado = ruta
    .split(/(\{\w+\})/)
    .map((trozo) => (/^\{\w+\}$/.test(trozo) ? '[^/]+' : trozo.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
    .join('');
  return new RegExp(`^${raiz.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}${escapado}$`);
}

export function laSirveElBackend(
  servidas: readonly OperacionServida[],
  raiz: string,
  metodo: string,
  camino: string,
): boolean {
  const buscado = metodo.toUpperCase();
  return servidas.some(
    (operacion) => operacion.metodo.toUpperCase() === buscado && compilar(raiz, operacion.ruta).test(camino),
  );
}
