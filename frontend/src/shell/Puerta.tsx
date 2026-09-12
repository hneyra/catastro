import { Aviso, Boton } from '../ds/componentes';

/**
 * Lo que se ve cuando la puerta de identidad **no llego a abrirse**.
 *
 * <h2>Por que ocupa la pantalla entera y no un rincon</h2>
 *
 * Porque sin token no hay nada que hacer aqui. Toda ruta de este backend exige token y el
 * inquilino sale del token (`TenantContextFilter`), asi que sin el ninguna pantalla puede pedir
 * nada y el armazon dibujaria un padron vacio, una cola de trabajo vacia y un panel de cifras en
 * blanco — tres pantallas que se ven exactamente igual que «no hay predios», «no hay nada parado»
 * y «no se valorizo nada». Es mas honesto no dibujar el armazon que dibujarlo lleno de ceros que
 * no son de nadie.
 *
 * <h2>Cuando SE VE, que son los cuatro casos en que no se rebota</h2>
 *
 * El camino normal no pasa por aqui: sin token el arranque se va al emisor y vuelve. Esta pantalla
 * es lo que queda cuando eso no se puede hacer o no se debe:
 *
 *   1. el emisor contesto un `?error=` o el canje se cayo —`canjearSiVuelve` devuelve el motivo—;
 *   2. se agoto el tope de tres idas, o sea que algo rebota sin fin;
 *   3. se acaba de cerrar sesion, y volver a entrar solo seria entrar OTRA VEZ con la misma cuenta;
 *   4. el navegador no expone `crypto.subtle`, asi que no se puede calcular el reto S256.
 *
 * <h2>El boton se ofrece solo cuando sirve, y por eso es un `prop` y no una constante</h2>
 *
 * «Volver a identificarse» arregla 2 y 3, y **no arregla** 4: un navegador sin `crypto.subtle`
 * —o una pagina servida por HTTP plano, que es lo mismo desde el navegador— dara el mismo
 * resultado las veces que se pulse, y a la tercera quien atiende deja de creerse los botones.
 */
export interface PuertaProps {
  /** Que paso, en una linea. Es lo que se lee primero. */
  readonly motivo: string;
  /** El detalle, que es lo que distingue dos motivos que se parecen. */
  readonly detalle: string;
  /** Que hacer. Se escribe aparte del detalle para que no se lea como parte del diagnostico. */
  readonly remedio: string;
  /** Si volver a la puerta puede cambiar algo. Ver la cabecera. */
  readonly ofreceVolver: boolean;
  readonly alVolverAIdentificarse: () => void;
}

export function Puerta({
  motivo,
  detalle,
  remedio,
  ofreceVolver,
  alVolverAIdentificarse,
}: PuertaProps) {
  return (
    <main
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
        background: 'var(--fondo)',
      }}
    >
      <div style={{ width: 'min(560px, 100%)' }}>
        <p style={{ margin: '0 0 14px', fontSize: 18, fontWeight: 700, color: 'var(--tinta)' }}>
          Catastro
          <span
            style={{
              display: 'block',
              fontSize: 13,
              fontWeight: 400,
              color: 'var(--tinta-3)',
              marginTop: 2,
            }}
          >
            Kamayuk — gestión catastral municipal
          </span>
        </p>

        <Aviso tono="bad" titulo={motivo}>
          <p style={{ margin: 0 }}>{detalle}</p>
          <p style={{ margin: '10px 0 0' }}>{remedio}</p>
          {ofreceVolver ? (
            <div style={{ marginTop: 14 }}>
              <Boton tipo="primario" onClick={alVolverAIdentificarse}>
                Volver a identificarse
              </Boton>
            </div>
          ) : null}
        </Aviso>
      </div>
    </main>
  );
}
