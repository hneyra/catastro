import { useState } from 'react';
import type { ReactNode } from 'react';
import { Aviso, Boton, Campo, Fallo, Rejilla, Seccion, Selector } from './componentes';
import { ErrorDeApi } from '../api/cliente';
import type { CodigoDeError } from '../api/cliente';
import { ACTO, ESCRIBIENDO, FALTA, IRREVERSIBLE, LA_OBSERVACION } from '../datos/actos';

/**
 * El formulario de un acto de escritura, con sus desenlaces separados.
 *
 * <h2>De donde sale, y por que se mudo aqui</h2>
 *
 * Lo escribio #71 dentro de `Fiscalizacion.tsx`, que era el unico modulo que
 * escribia. Con el catalogo territorial (#72) hay un segundo, y la alternativa
 * era que `catastro` importara un componente de `fiscalizacion` —dos contextos
 * acotados atados por una caja de formulario— o copiarlo. Vive en el sistema de
 * diseno porque es lo que es: la forma en que esta interfaz escribe.
 *
 * <h2>El primario nace apagado y dice que falta</h2>
 *
 * No se copia aqui ningun limite del dominio —el largo minimo de una
 * observacion, el rango de un umbral, la forma de un codigo—: lo que se exige es
 * que **todo campo obligatorio tenga algo**, y lo demas lo dice el servidor al
 * rechazar. Copiar los limites daria dos sitios con la misma verdad y la
 * pantalla acabaria afirmando el limite viejo.
 *
 * <h2>Los actos irreversibles se confirman aparte</h2>
 *
 * Con `advertencia`, el primario no envia: abre un panel con lo que va a pasar
 * escrito delante y dos botones. **Nunca un `confirm()` del navegador**: bloquea
 * el hilo, no se puede leer con un lector de pantalla y deja los arneses
 * colgados esperando a un dialogo que nadie va a cerrar.
 *
 * <h2>Y el fallo pasa por `Fallo`, entero, con lo que hay que HACER al lado</h2>
 *
 * `Fallo` dice lo que paso —el titulo del codigo, el mensaje del servidor, sus
 * detalles, la incidencia y la llave que falta—. Lo que no puede decir es lo que
 * quien lo lee tiene que hacer, porque eso depende de la operacion: un `409` al
 * dar de alta un sector se arregla con otro codigo; el mismo `409` en otra
 * pantalla significa otra cosa. Por eso `explicaciones` es un mapa por codigo y
 * lo pone quien abre el acto.
 *
 * **El caso que obliga a que exista es el `403`.** La baja logica de un sector o
 * de una via exige `ELIMINACION`, que es un privilegio distinto del de modificar
 * y que el guardia no puede comprobar por la ruta —la ruta es una sola para
 * editar y para retirar, y cual de las dos es depende del cuerpo—. Asi que a
 * quien puede editar y no dar de baja el servidor le contesta `403
 * SIN_PRIVILEGIO`, en una pantalla en la que acaba de guardar sin problema: sin
 * una frase que lo separe, ese 403 se lee como una averia del sistema.
 */

export type CampoDelActo = {
  k: string;
  rotulo: string;
  ayuda?: string;
  /** Cuando el campo sale de un enumerado del backend. */
  opciones?: readonly string[];
  opcional?: boolean;
  ancho?: number;
  /**
   * Un valor que se ensena y **no se puede editar**, con el motivo a la vista.
   *
   * Existe por lo que el backend descarta en silencio: el `codigo` del cuerpo de
   * un `PUT` se ignora —«cambiarlo desalinearia el codigo de todos los predios
   * del sector»—, asi que un campo editable con ese valor seria un control que
   * se rellena, viaja y no hace nada. Se ensena porque hace falta para saber que
   * se esta corrigiendo, y no se ofrece como si se pudiera cambiar.
   */
  fijo?: string;
};

export function Acto<T>({
  titulo,
  nota,
  campos,
  advertencia,
  explicaciones,
  enviar,
  pinta,
  onCerrar,
}: {
  /** Como se llama el acto. Es el titulo de la seccion y el rotulo del primario. */
  titulo: string;
  /** Que hace, en una frase. */
  nota: string;
  campos: readonly CampoDelActo[];
  /** Si esta, el acto no se deshace y se confirma aparte. */
  advertencia?: string;
  /** Que hay que HACER con cada rechazo. Lo pone quien abre el acto. */
  explicaciones?: Partial<Record<CodigoDeError, ReactNode>>;
  enviar: (valores: Readonly<Record<string, string>>) => Promise<T>;
  pinta: (hecho: T) => ReactNode;
  onCerrar: () => void;
}) {
  const laObservacion: CampoDelActo = {
    k: 'observacion',
    rotulo: LA_OBSERVACION.rotulo,
    ayuda: LA_OBSERVACION.ayuda,
    ancho: 460,
  };
  const todos = [...campos, laObservacion];
  const [valores, setValores] = useState<Record<string, string>>({});
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<ErrorDeApi | null>(null);
  const [hecho, setHecho] = useState<T | null>(null);
  const [confirmando, setConfirmando] = useState(false);

  /* Un campo fijo no se rellena: ya tiene valor y no se puede escribir. */
  const faltan = todos.filter(
    (c) => c.opcional !== true && c.fijo === undefined && (valores[c.k] ?? '').trim() === '',
  );
  const motivo = enviando ? ESCRIBIENDO : faltan.length ? FALTA + faltan.map((c) => c.rotulo).join(', ') : '';
  const puede = motivo === '';

  const mandar = () => {
    if (!puede) return;
    setEnviando(true);
    setError(null);
    setConfirmando(false);
    enviar(valores)
      .then((r) => {
        setEnviando(false);
        setHecho(r);
      })
      .catch((fallo: unknown) => {
        setEnviando(false);
        setHecho(null);
        setError(
          fallo instanceof ErrorDeApi
            ? fallo
            : new ErrorDeApi('SIN_RESPUESTA', 'No se pudo completar la operacion', 0),
        );
      });
  };

  const queHacer = error === null ? undefined : explicaciones?.[error.codigo];

  return (
    <Seccion
      titulo={titulo}
      nota={hecho === null ? undefined : ACTO.loQueSeAcabaDeHacer}
      derecha={<Boton onClick={onCerrar}>{ACTO.cerrar}</Boton>}
    >
      <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <p style={{ margin: 0, fontSize: 13, lineHeight: 1.55, color: 'var(--tinta-3)', textWrap: 'pretty' }}>
          {nota}
        </p>

        {error !== null ? <Fallo error={error} reintentar={mandar} /> : null}
        {queHacer ? (
          <Aviso tono="info" titulo={ACTO.queHayQueHacer}>
            {queHacer}
          </Aviso>
        ) : null}

        {hecho !== null ? (
          <>
            {pinta(hecho)}
            <p style={{ margin: 0, fontSize: 12.5, lineHeight: 1.5, color: 'var(--tinta-3)' }}>
              {ACTO.elProxyNoPersiste}
            </p>
          </>
        ) : (
          <>
            <Rejilla>
              {todos.map((c) =>
                c.fijo !== undefined ? (
                  <ValorFijo key={c.k} rotulo={c.rotulo} valor={c.fijo} ayuda={c.ayuda} />
                ) : c.opciones ? (
                  <Selector
                    key={c.k}
                    rotulo={c.rotulo}
                    valor={valores[c.k] ?? ''}
                    onCambio={(v) => setValores((x) => ({ ...x, [c.k]: v }))}
                    opciones={[
                      { valor: '', label: 'Elija uno' },
                      ...c.opciones.map((o) => ({ valor: o, label: o })),
                    ]}
                    ayuda={c.ayuda}
                    ancho={c.ancho}
                  />
                ) : (
                  <Campo
                    key={c.k}
                    rotulo={c.rotulo}
                    valor={valores[c.k] ?? ''}
                    onCambio={(v) => setValores((x) => ({ ...x, [c.k]: v }))}
                    ayuda={c.ayuda}
                    ancho={c.ancho}
                  />
                ),
              )}
            </Rejilla>

            {confirmando ? (
              <Aviso tono="bad" titulo={IRREVERSIBLE.titulo}>
                <p style={{ margin: 0 }}>{advertencia}</p>
                <div style={{ display: 'flex', gap: 10, marginTop: 10 }}>
                  <Boton tipo="primario" onClick={mandar}>
                    {IRREVERSIBLE.confirmar}
                  </Boton>
                  <Boton onClick={() => setConfirmando(false)}>{IRREVERSIBLE.cancelar}</Boton>
                </div>
              </Aviso>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                <Boton
                  tipo="primario"
                  impedido={!puede}
                  motivo={motivo}
                  onClick={() => (advertencia === undefined ? mandar() : setConfirmando(true))}
                >
                  {titulo}
                </Boton>
                <p style={{ margin: 0, flex: 1, minWidth: 180, fontSize: 12.5, color: 'var(--tinta-3)' }}>
                  {puede ? ACTO.observacion : motivo}
                </p>
              </div>
            )}
          </>
        )}
      </div>
    </Seccion>
  );
}

/**
 * Un valor que el acto ENSENA y no deja cambiar, con su motivo debajo.
 *
 * No es un `<input disabled>`, y la diferencia importa: un campo apagado dice
 * «esto se podria rellenar y ahora no», que es falso —este valor no se puede
 * cambiar nunca por esta operacion—, y ademas seria un control impedido mas que
 * `impedimentos.mjs` obligaria a explicar en un `title` que nadie puede leer sin
 * pulsarlo. Se dibuja como dato, que es lo que es, y el motivo va a la vista.
 */
function ValorFijo({ rotulo, valor, ayuda }: { rotulo: string; valor: string; ayuda?: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 5, width: 220, maxWidth: '100%' }}>
      <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--tinta-2)' }}>{rotulo}</span>
      <span
        style={{
          padding: '9px 10px',
          border: '1px dashed var(--linea)',
          borderRadius: 6,
          background: 'var(--sup)',
          fontSize: 14,
          color: 'var(--tinta)',
        }}
      >
        {valor}
      </span>
      {ayuda ? <span style={{ fontSize: 11.5, color: 'var(--tinta-3)' }}>{ayuda}</span> : null}
    </div>
  );
}
