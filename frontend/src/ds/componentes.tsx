import type { CSSProperties, ReactNode } from 'react';
import { Icono } from './Icono';
import { ICO } from './iconos';
import type { ErrorDeApi } from '../api/cliente';

/**
 * Los primitivos del artboard, con sus estilos EN LINEA y sus valores tal cual.
 *
 * Nada de clases de utilidad ni de una reescritura «mas limpia»: el objetivo es
 * que la pantalla se vea como el diseno, no que el codigo sea corto. Lo unico
 * que se movio a clases son los `hov-*`, porque React no tiene pseudoclases en
 * linea.
 */

/* Las cabeceras y celdas del artboard (lineas 924-928). */
export const TH: CSSProperties = {
  padding: '9px 16px',
  textAlign: 'left',
  fontSize: 11,
  fontWeight: 700,
  textTransform: 'uppercase',
  letterSpacing: '.07em',
  color: 'var(--tinta-3)',
  whiteSpace: 'nowrap',
  background: 'var(--sup)',
  borderBottom: '1px solid var(--linea)',
};
export const THN: CSSProperties = { ...TH, textAlign: 'right' };
export const TD: CSSProperties = { padding: '11px 16px', fontSize: 13.5, color: 'var(--tinta-2)' };
export const TDN: CSSProperties = {
  padding: '11px 16px',
  fontSize: 13.5,
  color: 'var(--tinta)',
  textAlign: 'right',
  whiteSpace: 'nowrap',
  fontVariantNumeric: 'tabular-nums',
};
export const TD1: CSSProperties = {
  padding: '11px 16px',
  fontSize: 13.5,
  fontWeight: 600,
  color: 'var(--tinta)',
  whiteSpace: 'nowrap',
};

export type Tono = 'ok' | 'warn' | 'bad' | 'info';

export function Insignia({ tono = 'info', children }: { tono?: Tono; children: ReactNode }) {
  return (
    <span
      style={{
        display: 'inline-block',
        fontSize: 11.5,
        fontWeight: 600,
        borderRadius: 4,
        padding: '2px 8px',
        background: `var(--${tono}-fondo)`,
        color: `var(--${tono}-tinta)`,
        whiteSpace: 'nowrap',
        flex: '0 0 auto',
      }}
    >
      {children}
    </span>
  );
}

export function Seccion({
  titulo,
  nota,
  derecha,
  children,
}: {
  titulo: string;
  nota?: string;
  derecha?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section
      style={{
        background: 'var(--blanco)',
        border: '1px solid var(--linea)',
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      <header
        style={{
          display: 'flex',
          alignItems: 'baseline',
          gap: 10,
          flexWrap: 'wrap',
          padding: '13px 16px',
          borderBottom: '1px solid var(--linea-2)',
        }}
      >
        <h2 style={{ margin: 0, flex: 1, minWidth: 0, fontSize: 15, fontWeight: 700 }}>{titulo}</h2>
        {nota ? <p style={{ margin: 0, fontSize: 12.5, color: 'var(--tinta-3)' }}>{nota}</p> : null}
        {derecha}
      </header>
      {children}
    </section>
  );
}

export function Boton({
  children,
  onClick,
  tipo = 'secundario',
  impedido,
  motivo,
}: {
  children: ReactNode;
  onClick?: () => void;
  tipo?: 'primario' | 'secundario';
  /** Si el boton no se puede pulsar. **Nunca sin `motivo`.** */
  impedido?: boolean;
  motivo?: string;
}) {
  const primario = tipo === 'primario';
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={impedido}
      title={impedido ? motivo : undefined}
      className={primario ? 'hov-azul' : 'hov-borde'}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 7,
        border: primario ? 0 : '1px solid var(--linea)',
        borderRadius: 6,
        padding: primario ? '9px 15px' : '8px 15px',
        background: primario ? 'var(--azul)' : 'var(--blanco)',
        color: primario ? '#fff' : 'var(--tinta)',
        fontSize: 13.5,
        fontWeight: primario ? 600 : 400,
        cursor: impedido ? 'not-allowed' : 'pointer',
        opacity: impedido ? 0.55 : 1,
        flex: '0 0 auto',
      }}
    >
      {children}
    </button>
  );
}

export function Campo({
  rotulo,
  valor,
  onCambio,
  marcador,
  ayuda,
  ancho = 220,
}: {
  rotulo: string;
  valor: string;
  onCambio: (v: string) => void;
  marcador?: string;
  ayuda?: string;
  ancho?: number;
}) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 5, width: ancho, maxWidth: '100%' }}>
      <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--tinta-2)' }}>{rotulo}</span>
      <input
        value={valor}
        onChange={(e) => onCambio(e.target.value)}
        placeholder={marcador}
        style={{
          width: '100%',
          boxSizing: 'border-box',
          border: '1px solid var(--borde-campo)',
          borderRadius: 6,
          padding: '9px 10px',
          background: '#fff',
          fontSize: 14,
        }}
      />
      {ayuda ? <span style={{ fontSize: 11.5, color: 'var(--tinta-3)' }}>{ayuda}</span> : null}
    </label>
  );
}

export function Selector({
  rotulo,
  valor,
  onCambio,
  opciones,
  ayuda,
  ancho = 220,
}: {
  rotulo: string;
  valor: string;
  onCambio: (v: string) => void;
  opciones: readonly { valor: string; label: string }[];
  ayuda?: string;
  ancho?: number;
}) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 5, width: ancho, maxWidth: '100%' }}>
      <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--tinta-2)' }}>{rotulo}</span>
      <select
        value={valor}
        onChange={(e) => onCambio(e.target.value)}
        style={{
          width: '100%',
          boxSizing: 'border-box',
          border: '1px solid var(--borde-campo)',
          borderRadius: 6,
          padding: '9px 10px',
          background: '#fff',
          fontSize: 14,
        }}
      >
        {opciones.map((o) => (
          <option key={o.valor} value={o.valor}>
            {o.label}
          </option>
        ))}
      </select>
      {ayuda ? <span style={{ fontSize: 11.5, color: 'var(--tinta-3)' }}>{ayuda}</span> : null}
    </label>
  );
}

export type Columna<T> = {
  label: string;
  numerica?: boolean;
  pinta: (fila: T) => ReactNode;
};

export function Tabla<T>({
  columnas,
  filas,
  llave,
  pie,
  vacio,
}: {
  columnas: readonly Columna<T>[];
  filas: readonly T[];
  llave: (fila: T) => string | number;
  pie?: ReactNode;
  vacio: string;
}) {
  return (
    <>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }} data-sticky="1">
          <thead>
            <tr>
              {columnas.map((c) => (
                <th key={c.label} style={c.numerica ? THN : TH}>
                  {c.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filas.map((f) => (
              <tr key={llave(f)} style={{ borderBottom: '1px solid var(--linea-2)' }}>
                {columnas.map((c) => (
                  <td key={c.label} style={c.numerica ? TDN : TD}>
                    {c.pinta(f)}
                  </td>
                ))}
              </tr>
            ))}
            {filas.length === 0 ? (
              <tr>
                <td colSpan={columnas.length} style={{ ...TD, padding: '22px 16px', textAlign: 'center' }}>
                  {vacio}
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
      {pie ? (
        <p
          style={{
            margin: 0,
            padding: '10px 16px',
            borderTop: '1px solid var(--linea-2)',
            background: 'var(--sup)',
            fontSize: 12.5,
            lineHeight: 1.55,
            color: 'var(--tinta-3)',
            textWrap: 'pretty',
          }}
        >
          {pie}
        </p>
      ) : null}
    </>
  );
}

/** Un dato con su rotulo. Sin valor sale «—», nunca un cero ni un blanco. */
export function Dato({ rotulo, children }: { rotulo: string; children?: ReactNode }) {
  const hay = children !== undefined && children !== null && children !== '';
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 140 }}>
      <span
        style={{
          fontSize: 10.5,
          fontWeight: 600,
          textTransform: 'uppercase',
          letterSpacing: '.08em',
          color: 'var(--tinta-3)',
        }}
      >
        {rotulo}
      </span>
      <span style={{ fontSize: 14, color: hay ? 'var(--tinta)' : 'var(--tinta-3)' }}>
        {hay ? children : '—'}
      </span>
    </div>
  );
}

export function Rejilla({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))',
        gap: 14,
        padding: '14px 16px',
      }}
    >
      {children}
    </div>
  );
}

export function Aviso({
  tono = 'info',
  titulo,
  children,
  accion,
}: {
  tono?: Tono;
  titulo?: string;
  children: ReactNode;
  accion?: ReactNode;
}) {
  return (
    <div
      role="status"
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 12,
        padding: '12px 16px',
        background: `var(--${tono}-fondo)`,
        border: '1px solid var(--linea)',
        borderRadius: 8,
      }}
    >
      <span style={{ color: `var(--${tono}-tinta)`, marginTop: 1 }}>
        <Icono d={ICO.aviso} tam={17} grosor={2} />
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        {titulo ? (
          <p style={{ margin: '0 0 4px', fontSize: 13.5, fontWeight: 700, color: `var(--${tono}-tinta)` }}>
            {titulo}
          </p>
        ) : null}
        <div
          style={{
            fontSize: 13,
            lineHeight: 1.55,
            color: `var(--${tono}-tinta)`,
            textWrap: 'pretty',
          }}
        >
          {children}
        </div>
      </div>
      {accion}
    </div>
  );
}

/** Lo que se dibuja mientras se pide. Nunca una cifra: barras grises. */
export function Esqueleto({ filas = 3 }: { filas?: number }) {
  return (
    <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 9 }} aria-busy="true">
      {Array.from({ length: filas }, (_, i) => (
        <span
          key={i}
          style={{
            display: 'block',
            height: 13,
            width: `${92 - i * 11}%`,
            borderRadius: 4,
            background: 'var(--linea-2)',
          }}
        />
      ))}
      <span style={{ fontSize: 12.5, color: 'var(--tinta-3)' }}>Pidiendo al servidor…</span>
    </div>
  );
}

/**
 * Lo que la pantalla dice cuando la lectura fallo.
 *
 * **«Reintentar» solo sale donde reintentar puede cambiar algo.** Un privilegio
 * que falta sale igual las veces que se pulse; un verbo que la ruta no admite no
 * puede funcionar nunca. Ofrecer el boton ahi manda a insistir sobre algo que ya
 * se sabe imposible.
 */
export function Fallo({ error, reintentar }: { error: ErrorDeApi; reintentar: () => void }) {
  const tono: Tono = error.codigo === 'VALIDACION' ? 'warn' : 'bad';
  return (
    <div style={{ padding: '14px 16px' }}>
      <Aviso
        tono={tono}
        titulo={titulosDeError[error.codigo] ?? 'No se pudo leer'}
        accion={
          error.reintentable ? (
            <Boton onClick={reintentar}>Reintentar</Boton>
          ) : undefined
        }
      >
        <p style={{ margin: 0 }}>{error.mensaje}</p>
        {error.detalles?.length ? (
          <ul style={{ margin: '6px 0 0', paddingLeft: 18 }}>
            {error.detalles.map((d) => (
              <li key={d}>{d}</li>
            ))}
          </ul>
        ) : null}
        {error.faltaUnaCifraNormativa ? (
          <p style={{ margin: '6px 0 0' }}>
            Falta publicar «{error.parametroQueFalta}». No se arregla desde esta pantalla: hay que sellar el
            conjunto o publicar la cifra.
          </p>
        ) : null}
        {error.incidencia ? (
          <p style={{ margin: '6px 0 0', fontVariantNumeric: 'tabular-nums' }}>
            Incidencia {error.incidencia}
          </p>
        ) : null}
      </Aviso>
    </div>
  );
}

const titulosDeError: Partial<Record<string, string>> = {
  NO_AUTENTICADO: 'La sesion no vale',
  SIN_MUNICIPALIDAD: 'El token no dice de que municipalidad es',
  SIN_DOCUMENTO: 'El token no trae documento de identidad',
  SIN_PRIVILEGIO: 'No tiene el permiso que esta pantalla necesita',
  VALIDACION: 'El servidor no admite lo que se le pidio',
  ORDEN_NO_ADMITIDO: 'No se puede ordenar por ese campo',
  MARCO_CON_DEMASIADOS_LOTES: 'El marco pedido tiene demasiados lotes',
  NO_ENCONTRADO: 'No existe',
  METODO_NO_ADMITIDO: 'Esa ruta no admite este verbo',
  CONFLICTO: 'El estado actual no admite la operacion',
  ERROR_INTERNO: 'Fallo el servidor',
  SIN_RESPUESTA: 'No hubo respuesta del servidor',
};

/** Lo que la pantalla dice mientras espera a que alguien escriba el sujeto. */
export function EnEspera({ children }: { children: ReactNode }) {
  return (
    <div style={{ padding: '26px 16px', display: 'grid', placeItems: 'center' }}>
      <p
        style={{
          margin: 0,
          maxWidth: '52ch',
          textAlign: 'center',
          fontSize: 13.5,
          lineHeight: 1.6,
          color: 'var(--tinta-3)',
          textWrap: 'pretty',
        }}
      >
        {children}
      </p>
    </div>
  );
}

/**
 * El envoltorio de una lectura: sus cuatro estados en un sitio.
 *
 * Que esten los cuatro y en un solo sitio es lo que impide el desenlace peor —la
 * pantalla en blanco, sin datos y sin error—, que no lo delata ningun error de
 * consola y por eso `mirar.mjs` lo busca a mano.
 */
export function Lectura<T>({
  recurso,
  espera,
  children,
}: {
  recurso: { datos: T | null; cargando: boolean; error: ErrorDeApi | null; enEspera: boolean; reintentar: () => void };
  espera: ReactNode;
  children: (datos: T) => ReactNode;
}) {
  if (recurso.enEspera) return <EnEspera>{espera}</EnEspera>;
  if (recurso.error) return <Fallo error={recurso.error} reintentar={recurso.reintentar} />;
  if (recurso.cargando || recurso.datos === null) return <Esqueleto />;
  return <>{children(recurso.datos)}</>;
}
