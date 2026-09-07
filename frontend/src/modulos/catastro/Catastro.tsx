import { useEffect, useMemo, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import type { PantallaProps } from '../../App';
import { AltaDeFicha } from './AltaDeFicha';
import * as api from '../../api/catastro';
import * as fiscalizacion from '../../api/fiscalizacion';
import type { ErrorDeApi, RespuestaPaginada } from '../../api/cliente';
import { useRebote, useRecurso } from '../../api/useRecurso';
import { sinClaves } from '../../shell/ruta';
import type { Recurso } from '../../api/useRecurso';
import {
  Aviso,
  Boton,
  Campo,
  Dato,
  Fallo,
  Insignia,
  Lectura,
  motivoCorto,
  Rejilla,
  Seccion,
  Selector,
  Servida,
  Tabla,
  TD,
  TD1,
  TDN,
  TH,
  THN,
} from '../../ds/componentes';
import type { Tono } from '../../ds/componentes';
import { Acto } from '../../ds/Acto';
import { Icono } from '../../ds/Icono';
import { ICO } from '../../ds/iconos';
import { ALTA, PASOS } from '../../datos/alta';
import {
  COLUMNAS as COLUMNAS_DE_FISCALIZACION,
  MOTIVOS as MOTIVOS_DE_FISCALIZACION,
  PIES as PIES_DE_FISCALIZACION,
  TITULOS as TITULOS_DE_FISCALIZACION,
  VACIOS as VACIOS_DE_FISCALIZACION,
} from '../../datos/fiscalizacion';
import {
  ACTOS_DEL_TERRITORIO,
  CAMPOS_DEL_TERRITORIO,
  CHIPS_DE_PREDIOS,
  COLAS,
  CUADROS,
  FICHA,
  MOTIVOS,
  NOTAS_DE_LOS_ACTOS_DEL_TERRITORIO,
  PANEL,
  PREDIOS,
  QUE_HACER,
  RETIRADAS,
  ROTULO_DE_ORDEN,
  ROTULO_DE_PARTIDA,
  TERRITORIO,
  VALORES,
  VISTAS_DEL_PREDIO,
} from '../../datos/catastro';

/**
 * El modulo Catastro, portado de `CatastroV6.dc.html`.
 *
 * Las cuatro hojas que el artboard dibuja —Panel, Predios, Territorio y Valores
 * del ejercicio— con sus estilos en linea y sus textos, y dos mas que el
 * registro declara y el artboard no: Fichas y Plano catastral.
 *
 * <h2>Lo unico que no se copia son las cifras</h2>
 *
 * El artboard trae cinco predios, siete nodos de territorio y tres cuadros
 * enteros escritos a mano. Ninguno viaja: **toda cifra de estas pantallas sale
 * de una lectura**, y donde la lectura no puede hacerse se dice que falta. Es la
 * unica regla que gobierna esta interfaz y la mide `verificaciones/sin-red.mjs`
 * con el proxy apagado y la red cortada.
 *
 * <h2>Y hay tres sitios donde el artboard pide algo que este sistema no sabe</h2>
 *
 * El artboard dibuja el marco del monolito, donde catastro y predial son el
 * mismo sistema. Aqui no lo son. Los tres estan marcados con su motivo en
 * `src/datos/catastro.ts`: el autovaluo de cada fila, la cobertura medida en
 * «fichas conciliadas» y las vias colgando de un sector.
 */

/* ── Piezas del artboard que las cuatro hojas repiten ───────────────────── */

/** El panel maestro de un `data-split`: 376 px en Predios, 300 en Territorio. */
function ListaMaestra({ ancho, children }: { ancho: number; children: ReactNode }) {
  return (
    <div
      data-lista="1"
      style={{
        flex: `0 0 ${ancho}px`,
        width: ancho,
        display: 'flex',
        flexDirection: 'column',
        minHeight: 0,
        background: 'var(--blanco)',
        borderRight: '1px solid var(--linea)',
      }}
    >
      {children}
    </div>
  );
}

function Split({ children }: { children: ReactNode }) {
  return (
    <div data-split="1" style={{ flex: 1, display: 'flex', minHeight: 0, overflow: 'hidden', width: '100%' }}>
      {children}
    </div>
  );
}

/** El detalle de un `data-split`, a la derecha o debajo. */
function Detalle({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        minHeight: 0,
        overflow: 'hidden',
        background: 'var(--fondo)',
      }}
    >
      {children}
    </div>
  );
}

/** La tira de pestanas del artboard (lineas 651-660 y 816-822). */
function Tira({
  entradas,
  actual,
  onElegir,
}: {
  entradas: readonly { k: string; label: string }[];
  actual: string;
  onElegir: (k: string) => void;
}) {
  return (
    <div
      style={{
        flex: '0 0 auto',
        display: 'flex',
        alignItems: 'stretch',
        gap: 2,
        padding: '0 16px',
        background: 'var(--blanco)',
        borderBottom: '1px solid var(--linea)',
        overflowX: 'auto',
      }}
    >
      {entradas.map((e) => {
        const on = e.k === actual;
        return (
          <button
            key={e.k}
            type="button"
            onClick={() => onElegir(e.k)}
            aria-current={on ? 'true' : 'false'}
            style={{
              border: 0,
              borderBottom: `2px solid ${on ? 'var(--azul)' : 'transparent'}`,
              background: 'transparent',
              padding: '12px 14px 10px',
              cursor: 'pointer',
              fontSize: 14,
              whiteSpace: 'nowrap',
              color: on ? 'var(--tinta)' : 'var(--tinta-3)',
              fontWeight: on ? 700 : 400,
            }}
          >
            {e.label}
          </button>
        );
      })}
    </div>
  );
}

type Celda = { texto: ReactNode; numerica?: boolean };
type ColumnaAlVuelo = { label: string; numerica?: boolean };

/**
 * La tabla del artboard con cabecera fija, escrita literal.
 *
 * No usa `Tabla` de `componentes.tsx` a proposito: aqui la cabecera tiene que
 * quedarse pegada mientras el CUERPO se desplaza, y para eso el que desplaza
 * tiene que ser el contenedor de esta tabla y no un envoltorio interno. La
 * primera columna va en `TD1` —negrita— como en el artboard.
 */
function TablaFija({
  columnas,
  filas,
  vacio,
  pie,
}: {
  columnas: readonly ColumnaAlVuelo[];
  /**
   * `realzada` marca la fila sobre la que se esta actuando.
   *
   * Hace falta desde #72: los actos del catalogo vial se abren desde la fila y
   * el formulario aparece ARRIBA, asi que sin realzar la fila el formulario no
   * dice de quien es —y con quince vias en pantalla, «Corregir la via» sobre la
   * que no es se pinta exactamente igual que sobre la que si—.
   */
  filas: readonly { llave: string; celdas: readonly Celda[]; realzada?: boolean }[];
  vacio: ReactNode;
  /** La prosa del pie. Va en un `<p>`: solo texto. */
  pie?: ReactNode;
}) {
  return (
    <div style={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
      <table data-sticky="1" style={{ width: '100%', borderCollapse: 'collapse', minWidth: 620 }}>
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
            <tr
              key={f.llave}
              aria-current={f.realzada ? 'true' : undefined}
              style={{
                borderTop: '1px solid var(--linea-2)',
                background: f.realzada ? 'var(--azul-suave)' : 'var(--blanco)',
              }}
            >
              {f.celdas.map((celda, i) => (
                <td key={columnas[i]?.label ?? String(i)} style={i === 0 ? TD1 : celda.numerica ? TDN : TD}>
                  {celda.texto}
                </td>
              ))}
            </tr>
          ))}
          {filas.length === 0 ? (
            <tr>
              <td
                colSpan={columnas.length}
                style={{ ...TD, padding: '26px 16px', textAlign: 'center', background: 'var(--blanco)' }}
              >
                {vacio}
              </td>
            </tr>
          ) : null}
        </tbody>
      </table>
      {pie ? (
        <p
          style={{
            margin: 0,
            padding: '13px 18px',
            fontSize: 12.5,
            lineHeight: 1.55,
            color: 'var(--tinta-3)',
            textWrap: 'pretty',
          }}
        >
          {pie}
        </p>
      ) : null}
    </div>
  );
}

/**
 * El pie de una hoja a sangre: que ruta la sirve, y que le falta al backend.
 *
 * Va **fuera** de la lectura y no dentro de su tabla, y esto se corrigio
 * midiendo: con el `Servida` dentro del pie de la tabla, una lectura que falla
 * dibuja el aviso de error y el pie no llega a existir — o sea que justo cuando
 * la pantalla no puede ensenar nada, deja tambien de decir QUE no pudo leer. Lo
 * cazó `sin-red.mjs` en las tres hojas a sangre a la vez.
 */
function PieDeSangre({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        flex: '0 0 auto',
        padding: '10px 16px',
        background: 'var(--blanco)',
        borderTop: '1px solid var(--linea)',
      }}
    >
      {children}
    </div>
  );
}

/** Un valor que el servidor no trajo: «—», nunca un cero. */
function guion(valor: string | number | null | undefined): ReactNode {
  return valor === null || valor === undefined || valor === '' ? '—' : valor;
}

/** Si la pagina que trajo el servidor es el listado ENTERO. */
function cabeEntero<T>(r: RespuestaPaginada<T>): boolean {
  const total = r.totalElementos;
  return r.contenido.length >= total;
}

/**
 * Lo que se dice de una lectura que no llego: su motivo, sin ninguna cifra.
 *
 * Delega en `motivoCorto` del sistema de diseno y **no compone la frase aqui**:
 * los titulos de los doce codigos viven en un solo sitio, junto al `Fallo` que
 * los usa. Decia solo `error.mensaje`, y medido con el mismo mensaje un 500, un
 * 403 y un 422 con `parametroQueFalta` salian **byte a byte iguales** en las
 * siete superficies de esta pantalla que lo llaman.
 */
function motivoDelFallo(error: ErrorDeApi): string {
  return motivoCorto(error);
}

/* ══════════ Panel ══════════════════════════════════════════════════════ */

/**
 * Una de las cuatro tarjetas de cabecera (artboard 470-483).
 *
 * <h2>Cada tarjeta ofrece el reintento de SU lectura</h2>
 *
 * Y no un boton general arriba, que fue la otra opcion de #49. El motivo esta
 * medido: el Panel pide **cuatro** rutas y su unico «Reintentar» salia de las
 * dos `<Lectura>` de mas abajo, asi que con las cuatro en 500 aparecian dos
 * botones y cada uno re-pedia **una** —`/catastro/sectores` y
 * `/catastro/fichas`—. El padron y el plano quedaban muertos hasta recargar la
 * pagina entera, y su tarjeta no ofrecia nada porque su unica superficie es
 * esta nota. Un boton general los cubriria, pero reintentaria tambien las
 * lecturas que no fallaron; asi cada boton hace lo que su sitio promete.
 *
 * <h2>Y es una region con nombre</h2>
 *
 * `<section aria-label>` y no un `<div>`: la tarjeta pasa a ser una region
 * anunciable —quien navega con lector de pantalla oye de que tarjeta es el
 * boton— y ademas se vuelve **direccionable**, que es lo que permite a
 * `verificaciones/errores.mjs` medir el reintento de UNA tarjeta y no «hay un
 * boton en la pagina», que es la afirmacion que pasaba en verde con el defecto
 * puesto.
 */
function Tarjeta<T>({
  etiqueta,
  recurso,
  valor,
  nota,
}: {
  etiqueta: string;
  recurso: Recurso<T>;
  valor: (datos: T) => ReactNode;
  nota: (datos: T) => ReactNode;
}) {
  const hay = recurso.datos !== null && recurso.error === null;
  return (
    <section
      aria-label={etiqueta}
      style={{
        background: 'var(--blanco)',
        border: '1px solid var(--linea)',
        borderRadius: 8,
        padding: '14px 15px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <p
          style={{
            margin: 0,
            flex: 1,
            fontSize: 11,
            fontWeight: 600,
            textTransform: 'uppercase',
            letterSpacing: '.09em',
            color: 'var(--tinta-3)',
          }}
        >
          {etiqueta}
        </p>
      </div>
      <p
        style={{
          margin: '9px 0 0',
          fontSize: 29,
          fontWeight: 700,
          letterSpacing: '-.025em',
          lineHeight: 1,
          color: hay ? 'var(--tinta)' : 'var(--tinta-3)',
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {hay ? valor(recurso.datos!) : recurso.cargando ? '…' : '—'}
      </p>
      <p style={{ margin: '8px 0 0', fontSize: 12.5, lineHeight: 1.45, color: 'var(--tinta-3)', textWrap: 'pretty' }}>
        {hay ? nota(recurso.datos!) : recurso.error ? motivoDelFallo(recurso.error) : 'Pidiendo al servidor…'}
      </p>
      {/* Solo donde reintentar puede cambiar algo, que es la misma regla que
          `Fallo` aplica: un privilegio que falta sale igual las veces que se
          pulse. `reintentable` lo decide `ErrorDeApi` y no esta pantalla. */}
      {recurso.error?.reintentable ? (
        <p style={{ margin: '10px 0 0' }}>
          <Boton onClick={recurso.reintentar}>Reintentar</Boton>
        </p>
      ) : null}
    </section>
  );
}

function SeccionDelPanel({ titulo, derecha, children }: { titulo: string; derecha: ReactNode; children: ReactNode }) {
  return (
    <section
      style={{
        background: 'var(--blanco)',
        border: '1px solid var(--linea)',
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '12px 15px',
          borderBottom: '1px solid var(--linea-2)',
        }}
      >
        <h2 style={{ margin: 0, flex: 1, fontSize: 14.5, fontWeight: 700 }}>{titulo}</h2>
        {derecha}
      </div>
      {children}
    </section>
  );
}

const PIE_DE_SECCION: CSSProperties = {
  margin: 0,
  padding: '11px 15px',
  background: 'var(--sup)',
  fontSize: 12.5,
  lineHeight: 1.5,
  color: 'var(--tinta-3)',
  textWrap: 'pretty',
};

const FILA_PULSABLE: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 12,
  width: '100%',
  textAlign: 'left',
  border: 0,
  borderBottom: '1px solid var(--linea-2)',
  background: 'transparent',
  cursor: 'pointer',
};

export function Panel({ ejercicio, onIr }: PantallaProps) {
  const padron = useRecurso((senal) => api.predios({}, { tamano: api.TAMANO_MAXIMO }, senal), ['panel-padron']);
  const sectores = useRecurso((senal) => api.sectores({ tamano: 100 }, senal), ['panel-sectores']);
  const plano = useRecurso((senal) => api.plano({}, senal), ['panel-plano']);
  const recientes = useRecurso(
    (senal) => api.fichas({}, { tamano: 5, ordenarPor: 'vigenciaDesde', direccion: 'DESCENDENTE' }, senal),
    ['panel-recientes'],
  );

  /* Las colas se cuentan sobre el padron leido, y solo si cabe entero. Contar
     sobre la primera pagina y llamarlo total es la cifra que parece correcta
     siempre: sale plausible, nadie la contrasta y nadie la corrige. */
  const completo = padron.datos !== null && cabeEntero(padron.datos);
  const filasDelPadron = padron.datos?.contenido ?? [];

  const conteoDeLaCola = (k: string): { n: number | null; motivo: string | null } => {
    if (k === 'sin-poligono') {
      if (plano.error) return { n: null, motivo: motivoDelFallo(plano.error) };
      if (plano.datos === null) return { n: null, motivo: null };
      return { n: plano.datos.sinGeometria, motivo: null };
    }
    if (padron.error) return { n: null, motivo: motivoDelFallo(padron.error) };
    if (padron.datos === null) return { n: null, motivo: null };
    if (!completo) return { n: null, motivo: MOTIVOS.padronNoCabe };
    if (k === 'sin-ficha') return { n: filasDelPadron.filter((p) => !p.fichado).length, motivo: null };
    return { n: filasDelPadron.filter((p) => p.estado !== 'ACTIVO').length, motivo: null };
  };

  return (
    <div style={{ maxWidth: 1180, display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(212px,1fr))', gap: 12 }}>
        <Tarjeta
          etiqueta="Predios en el padron"
          recurso={padron}
          valor={(r) => r.totalElementos}
          nota={(r) => `${r.contenido.filter((p) => p.fichado).length} de los leidos tienen ficha catastral.`}
        />
        <Tarjeta
          etiqueta="Sectores"
          recurso={sectores}
          valor={(r) => r.totalElementos}
          nota={() => 'Cada uno con sus manzanas, sus predios activos y sus lotes, contados por el servidor.'}
        />
        <Tarjeta
          etiqueta="Fichas versionadas"
          recurso={recientes}
          valor={(r) => r.totalElementos}
          nota={() => 'Vigentes a la fecha de hoy: la grilla de fichas las lista con su version.'}
        />
        <Tarjeta
          etiqueta="Lotes con poligono"
          recurso={plano}
          valor={(r) => r.lotes.length}
          nota={(r) => `Sin geometria levantada: ${r.sinGeometria}. Sin lote no hay zona, ni riesgo, ni frente.`}
        />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(340px,1fr))', gap: 14 }}>
        <SeccionDelPanel
          titulo={PANEL.cola}
          derecha={
            <span style={{ fontSize: 12, color: 'var(--tinta-3)' }}>
              {padron.datos ? `sobre ${padron.datos.totalElementos} predios` : '—'}
            </span>
          }
        >
          {COLAS.map((cola) => {
            const { n, motivo } = conteoDeLaCola(cola.k);
            return (
              <button
                key={cola.k}
                type="button"
                onClick={() => onIr('catastro', cola.destino, { ...cola.filtros })}
                className="hov-suave"
                style={{ ...FILA_PULSABLE, padding: '12px 15px' }}
              >
                <Insignia tono={cola.tono as Tono}>{cola.etiqueta}</Insignia>
                <span style={{ flex: 1, minWidth: 0 }}>
                  <span style={{ display: 'block', fontSize: 14, fontWeight: 600 }}>{cola.titulo}</span>
                  <span
                    style={{
                      display: 'block',
                      fontSize: 12.5,
                      lineHeight: 1.45,
                      color: 'var(--tinta-3)',
                      marginTop: 2,
                      textWrap: 'pretty',
                    }}
                  >
                    {motivo ?? cola.detalle}
                  </span>
                </span>
                <span style={{ fontSize: 17, fontWeight: 700, flex: '0 0 auto', fontVariantNumeric: 'tabular-nums' }}>
                  {guion(n)}
                </span>
              </button>
            );
          })}
          <p style={PIE_DE_SECCION}>{MOTIVOS.colaNoSeSuma}</p>
        </SeccionDelPanel>

        <SeccionDelPanel
          titulo={PANEL.cobertura}
          derecha={<span style={{ fontSize: 12, color: 'var(--tinta-3)' }}>{PANEL.medidaDeLaCobertura}</span>}
        >
          <Lectura recurso={sectores} espera="">
            {(r) => (
              <>
                {r.contenido.map((s) => {
                  const de = s.predios;
                  const conFicha = completo
                    ? filasDelPadron.filter((p) => p.codigoDeSector === s.codigo && p.fichado).length
                    : null;
                  const pct = de === null || de === 0 || conFicha === null ? null : (conFicha * 100) / de;
                  /* Los tres cortes de color son los del artboard (linea 1758):
                     por debajo de 90 rojo, por debajo de 97 ambar, y verde. */
                  const tinta =
                    pct === null
                      ? 'var(--tinta-3)'
                      : pct < 90
                        ? 'var(--bad-tinta)'
                        : pct < 97
                          ? 'var(--warn-tinta)'
                          : 'var(--ok-tinta)';
                  const relleno =
                    pct === null ? 'var(--linea)' : pct < 90 ? 'var(--contador)' : pct < 97 ? '#C08A00' : 'var(--azul)';
                  return (
                    <button
                      key={s.id}
                      type="button"
                      onClick={() => onIr('catastro', 'territorio', {}, s.codigo)}
                      className="hov-suave"
                      style={{ ...FILA_PULSABLE, padding: '11px 15px' }}
                    >
                      <span
                        style={{
                          flex: '0 0 130px',
                          minWidth: 0,
                          fontSize: 13.5,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {s.codigo} — {s.nombre}
                      </span>
                      <span
                        style={{
                          flex: 1,
                          minWidth: 40,
                          height: 8,
                          borderRadius: 999,
                          background: 'var(--azul-suave)',
                          overflow: 'hidden',
                          position: 'relative',
                        }}
                      >
                        <span
                          style={{
                            position: 'absolute',
                            inset: '0 auto 0 0',
                            width: pct === null ? 0 : `${pct.toFixed(1)}%`,
                            borderRadius: 999,
                            background: relleno,
                          }}
                        />
                      </span>
                      <span
                        style={{
                          flex: '0 0 52px',
                          textAlign: 'right',
                          fontSize: 13,
                          fontWeight: 600,
                          color: tinta,
                          fontVariantNumeric: 'tabular-nums',
                        }}
                      >
                        {pct === null ? '—' : `${pct.toFixed(0)} %`}
                      </span>
                      <span
                        data-sm-hide="1"
                        style={{
                          flex: '0 0 84px',
                          textAlign: 'right',
                          fontSize: 12.5,
                          color: 'var(--tinta-3)',
                          fontVariantNumeric: 'tabular-nums',
                        }}
                      >
                        {conFicha === null || de === null ? '—' : `${conFicha} de ${de}`}
                      </span>
                    </button>
                  );
                })}
                <p style={PIE_DE_SECCION}>{MOTIVOS.coberturaNoEsConciliacion}</p>
              </>
            )}
          </Lectura>
        </SeccionDelPanel>
      </div>

      <SeccionDelPanel
        titulo={PANEL.actividad}
        derecha={
          <button
            type="button"
            onClick={() => onIr('catastro', 'predios')}
            className="hov-borde"
            style={{
              border: '1px solid var(--linea)',
              borderRadius: 5,
              padding: '5px 11px',
              background: 'var(--blanco)',
              fontSize: 12.5,
              cursor: 'pointer',
            }}
          >
            {PANEL.verTodos}
          </button>
        }
      >
        <Lectura recurso={recientes} espera="">
          {(r) => {
            const filas = r.contenido.slice(0, 5);
            const ordenadas = filas.every((f, i) => i === 0 || (filas[i - 1]?.vigenciaDesde ?? '') >= f.vigenciaDesde);
            const dioDeMas = r.contenido.length > filas.length;
            return (
              <>
                {filas.map((f) => (
                  <button
                    key={f.fichaId}
                    type="button"
                    onClick={() => onIr('catastro', 'predios', {}, String(f.predioId))}
                    className="hov-suave"
                    style={{ ...FILA_PULSABLE, padding: '10px 15px' }}
                  >
                    <Insignia>{f.tipo}</Insignia>
                    <span
                      style={{
                        flex: '0 0 auto',
                        fontSize: 13,
                        fontWeight: 600,
                        color: 'var(--azul)',
                        fontVariantNumeric: 'tabular-nums',
                      }}
                    >
                      {f.codRefCatastral}
                    </span>
                    <span
                      style={{
                        flex: 1,
                        minWidth: 0,
                        fontSize: 13,
                        color: 'var(--tinta-2)',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {f.direccion} · {f.uso}
                    </span>
                    <span style={{ flex: '0 0 auto', fontSize: 12, color: 'var(--tinta-3)' }}>{f.vigenciaDesde}</span>
                  </button>
                ))}
                {filas.length === 0 ? (
                  <p style={{ ...PIE_DE_SECCION, background: 'var(--blanco)' }}>
                    El servidor no devolvio ninguna ficha.
                  </p>
                ) : null}
                <p style={PIE_DE_SECCION}>
                  {MOTIVOS.actividadSonFichas}
                  {ordenadas
                    ? ''
                    : ' El servidor no las devolvio en el orden pedido, asi que estas no son las mas recientes.'}
                  {dioDeMas ? ' Y devolvio mas filas de las pedidas: aqui salen las primeras.' : ''}
                </p>
              </>
            );
          }}
        </Lectura>
      </SeccionDelPanel>

      <Servida
        lee={[api.RUTAS.predios, api.RUTAS.sectores, api.RUTAS.fichas, api.RUTAS.plano]}
        falta={`Ninguna cifra de valuacion sale en este panel, y no es un olvido: ${MOTIVOS.sinAutovaluo} El ejercicio ${ejercicio} de la barra global manda en la hoja de Valores, no aqui.`}
      />
    </div>
  );
}

/* ══════════ Predios ════════════════════════════════════════════════════ */

const TAMANO_DE_PAGINA = 25;

/** El rebote del buscador: se pide cuando quien teclea para, no en cada letra. */
/**
 * El sujeto que abre el asistente de alta.
 *
 * Es una palabra y no un identificador porque no hay ninguno todavia: el predio
 * no existe hasta que el servidor lo crea. `elegido` solo admite digitos, asi
 * que este valor no puede confundirse con un predio del padron.
 */
const SUJETO_DEL_ALTA = 'nuevo';

function chipActivo(filtros: Record<string, string>): string {
  const encontrado = CHIPS_DE_PREDIOS.find(
    (c) => Object.keys(c.filtros).length > 0 && Object.entries(c.filtros).every(([k, v]) => filtros[k] === v),
  );
  return encontrado?.k ?? 'todos';
}

export function Predios({ ruta, onSujeto, onFiltros, onIr }: PantallaProps) {
  const [texto, setTexto] = useState(ruta.filtros.codRefCatastral ?? '');
  const buscado = useRebote(texto);
  const chip = chipActivo(ruta.filtros);
  const orden = ruta.filtros.ordenarPor ?? api.ORDENES.predios.campos[0];
  const sentido = ruta.filtros.direccion === 'DESCENDENTE' ? 'DESCENDENTE' : 'ASCENDENTE';
  const pagina = /^\d+$/.test(ruta.filtros.pagina ?? '') ? Number(ruta.filtros.pagina) : 0;
  const vista = ruta.filtros.ver ?? VISTAS_DEL_PREDIO[0].k;
  const elegido = /^\d+$/.test(ruta.sujeto) ? Number(ruta.sujeto) : null;
  /* El alta es un ESTADO de esta pantalla, como en el artboard: el asistente
     ocupa el panel de detalle y la lista se queda al lado. Vive en la ruta
     —`#/catastro/predios/nuevo?paso=terreno`— para que se pueda enlazar y para
     que una recarga no devuelva al primer paso. */
  const esNuevo = ruta.sujeto === SUJETO_DEL_ALTA;
  const pasoDelAlta = ruta.filtros.paso ?? PASOS[0].id;

  /* El texto reposado se lleva a la ruta: asi la busqueda es enlazable y volver
     «atras» no obliga a pulsar una vez por caracter (va por `replaceState`). */
  useEffect(() => {
    const enLaRuta = ruta.filtros.codRefCatastral ?? '';
    if (buscado === enLaRuta) return;
    const siguientes: Record<string, string> = { ...ruta.filtros, pagina: '0' };
    if (buscado === '') delete siguientes.codRefCatastral;
    else siguientes.codRefCatastral = buscado;
    onFiltros(siguientes);
    /* La dependencia es SOLO el texto reposado, y es deliberado: con la ruta
       dentro, el efecto se volveria a disparar por su propio cambio y cada
       pulsacion de un chip reescribiria la busqueda. Lo que lee de la ruta lo
       lee del render en que se disparo, que es el que acaba de pintar. */
  }, [buscado]);

  const lista = useRecurso(
    (senal) =>
      api.predios(
        {
          codRefCatastral: ruta.filtros.codRefCatastral,
          estado: ruta.filtros.estado,
          fichado: ruta.filtros.fichado === undefined ? undefined : ruta.filtros.fichado === 'true',
          titularidad: ruta.filtros.titularidad,
        },
        { pagina, tamano: TAMANO_DE_PAGINA, ordenarPor: orden, direccion: sentido },
        senal,
      ),
    [
      'predios',
      ruta.filtros.codRefCatastral,
      ruta.filtros.estado,
      ruta.filtros.fichado,
      ruta.filtros.titularidad,
      orden,
      sentido,
      pagina,
    ],
  );

  const predio = lista.datos?.contenido.find((p) => p.predioId === elegido) ?? null;

  const fijar = (cambios: Record<string, string | undefined>) => {
    const siguientes = { ...ruta.filtros };
    for (const [k, v] of Object.entries(cambios)) {
      if (v === undefined) delete siguientes[k];
      else siguientes[k] = v;
    }
    onFiltros(siguientes);
  };

  const elegirChip = (k: string) => {
    const chipElegido = CHIPS_DE_PREDIOS.find((c) => c.k === k);
    if (!chipElegido) return;
    const siguientes: Record<string, string | undefined> = {
      fichado: undefined,
      estado: undefined,
      titularidad: undefined,
      pagina: '0',
    };
    for (const [clave, valor] of Object.entries(chipElegido.filtros)) siguientes[clave] = valor;
    fijar(siguientes);
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, width: '100%' }}>
      <Split>
        <ListaMaestra ancho={376}>
          <div style={{ flex: '0 0 auto', padding: '11px 12px 10px', borderBottom: '1px solid var(--linea-2)' }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                border: '1px solid var(--linea)',
                borderRadius: 6,
                padding: '7px 10px',
                background: 'var(--sup)',
              }}
            >
              <span style={{ color: 'var(--tinta-3)', flex: '0 0 auto', display: 'flex' }}>
                <Icono d={ICO.lupa} tam={15} grosor={1.8} />
              </span>
              <input
                value={texto}
                onChange={(e) => setTexto(e.target.value)}
                placeholder={PREDIOS.marcador}
                aria-label={PREDIOS.marcador}
                style={{ flex: 1, minWidth: 0, border: 0, background: 'transparent', fontSize: 14, outline: 'none' }}
              />
              {texto !== '' ? (
                <button
                  type="button"
                  onClick={() => setTexto('')}
                  aria-label="Limpiar la busqueda"
                  style={{
                    border: 0,
                    background: 'transparent',
                    padding: 0,
                    cursor: 'pointer',
                    color: 'var(--tinta-4)',
                    flex: '0 0 auto',
                    display: 'flex',
                  }}
                >
                  <Icono d={ICO.cruz} tam={14} grosor={2.2} />
                </button>
              ) : null}
            </div>
            <button
              type="button"
              onClick={() => onSujeto(SUJETO_DEL_ALTA)}
              className="hov-azul"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 7,
                width: '100%',
                marginTop: 9,
                border: 0,
                borderRadius: 6,
                padding: '9px 15px',
                background: 'var(--azul)',
                color: '#fff',
                fontSize: 13.5,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              <Icono d={ICO.mas} tam={15} grosor={2.2} />
              {PREDIOS.registrar}
            </button>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginTop: 9 }}>
              {CHIPS_DE_PREDIOS.map((c) => {
                const on = c.k === chip;
                return (
                  <button
                    key={c.k}
                    type="button"
                    onClick={() => elegirChip(c.k)}
                    aria-pressed={on}
                    style={{
                      border: `1px solid ${on ? 'var(--azul)' : 'var(--linea)'}`,
                      borderRadius: 999,
                      padding: '3px 10px',
                      cursor: 'pointer',
                      fontSize: 12,
                      background: on ? 'var(--azul-suave)' : 'var(--blanco)',
                      color: on ? 'var(--info-tinta)' : 'var(--tinta-3)',
                      fontWeight: on ? 600 : 400,
                    }}
                  >
                    {c.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div
            style={{
              flex: '0 0 auto',
              display: 'flex',
              alignItems: 'center',
              gap: 9,
              padding: '8px 12px',
              borderBottom: '1px solid var(--linea-2)',
              background: 'var(--sup)',
            }}
          >
            <span style={{ flex: 1, fontSize: 12, fontWeight: 600, color: 'var(--tinta-3)' }}>
              {lista.datos ? `${lista.datos.contenido.length} de ${lista.datos.totalElementos}` : '—'}
            </span>
            <select
              value={orden}
              onChange={(e) => fijar({ ordenarPor: e.target.value, pagina: '0' })}
              aria-label="Ordenar la lista"
              style={{
                border: '1px solid var(--linea)',
                borderRadius: 5,
                padding: '3px 7px',
                background: 'var(--blanco)',
                fontSize: 12,
                cursor: 'pointer',
              }}
            >
              {api.ORDENES.predios.campos.map((campo) => (
                <option key={campo} value={campo}>
                  {ROTULO_DE_ORDEN[campo] ?? campo}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => fijar({ direccion: sentido === 'ASCENDENTE' ? 'DESCENDENTE' : 'ASCENDENTE', pagina: '0' })}
              title={sentido === 'ASCENDENTE' ? 'Ordenar de mayor a menor' : 'Ordenar de menor a mayor'}
              style={{
                border: '1px solid var(--linea)',
                borderRadius: 5,
                padding: '3px 7px',
                background: 'var(--blanco)',
                fontSize: 12,
                cursor: 'pointer',
              }}
            >
              {sentido === 'ASCENDENTE' ? '↑' : '↓'}
            </button>
          </div>

          <div style={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
            <Lectura recurso={lista} espera="">
              {(r) => (
                <>
                  {r.contenido.length === 0 ? (
                    <div style={{ padding: '32px 20px', textAlign: 'center' }}>
                      <p style={{ margin: 0, fontSize: 14.5, fontWeight: 600 }}>
                        {r.totalElementos === 0 && Object.keys(ruta.filtros).length === 0
                          ? PREDIOS.padronVacio
                          : PREDIOS.sinCoincidencias}
                      </p>
                      <p
                        style={{
                          margin: '6px 0 0',
                          fontSize: 13,
                          lineHeight: 1.5,
                          color: 'var(--tinta-3)',
                          textWrap: 'pretty',
                        }}
                      >
                        {r.totalElementos === 0 && Object.keys(ruta.filtros).length === 0
                          ? PREDIOS.padronVacioDetalle
                          : PREDIOS.sinCoincidenciasDetalle}
                      </p>
                    </div>
                  ) : null}
                  {r.contenido.map((p) => {
                    const on = p.predioId === elegido;
                    return (
                      <button
                        key={p.predioId}
                        type="button"
                        onClick={() => onSujeto(String(p.predioId))}
                        aria-current={on ? 'true' : 'false'}
                        className="hov-suave"
                        style={{
                          display: 'block',
                          width: '100%',
                          textAlign: 'left',
                          border: 0,
                          borderBottom: '1px solid var(--linea-2)',
                          borderLeft: `3px solid ${on ? 'var(--azul)' : 'transparent'}`,
                          background: on ? 'var(--azul-suave)' : 'transparent',
                          padding: '11px 13px',
                          cursor: 'pointer',
                        }}
                      >
                        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span
                            style={{
                              flex: 1,
                              minWidth: 0,
                              fontSize: 14,
                              fontWeight: 600,
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap',
                            }}
                          >
                            {p.direccion}
                          </span>
                          <Insignia tono={p.estado === 'ACTIVO' ? 'ok' : 'bad'}>{p.estado}</Insignia>
                        </span>
                        <span
                          style={{
                            display: 'block',
                            fontSize: 12.5,
                            color: 'var(--tinta-3)',
                            marginTop: 3,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {p.tipo} · {p.via ?? 'sin via del catalogo'} · {p.fichado ? 'con ficha' : 'sin ficha'}
                        </span>
                        <span style={{ display: 'flex', alignItems: 'baseline', gap: 9, marginTop: 6 }}>
                          <span style={{ fontSize: 12, color: 'var(--tinta-3)', fontVariantNumeric: 'tabular-nums' }}>
                            {p.codRefCatastral}
                          </span>
                          <span style={{ flex: 1 }} />
                          <span style={{ fontSize: 13, fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                            Mz. {guion(p.codigoDeManzana)} · Lt. {guion(p.lote)}
                          </span>
                        </span>
                      </button>
                    );
                  })}
                </>
              )}
            </Lectura>
          </div>

          <div
            style={{
              flex: '0 0 auto',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 12px',
              borderTop: '1px solid var(--linea-2)',
              background: 'var(--sup)',
            }}
          >
            <button
              type="button"
              onClick={() => fijar({ pagina: String(pagina - 1) })}
              disabled={pagina === 0}
              title={pagina === 0 ? 'Esta es la primera pagina: no hay ninguna anterior' : undefined}
              style={BOTON_DE_PAGINA(pagina === 0)}
            >
              Anterior
            </button>
            <span style={{ flex: 1, textAlign: 'center', fontSize: 12, color: 'var(--tinta-3)' }}>
              Pagina {pagina + 1}
              {lista.datos ? ` de ${lista.datos.totalPaginas}` : ''}
            </span>
            <button
              type="button"
              onClick={() => fijar({ pagina: String(pagina + 1) })}
              disabled={!lista.datos?.hayMas}
              title={lista.datos?.hayMas ? undefined : 'El servidor dice que no hay mas paginas despues de esta'}
              style={BOTON_DE_PAGINA(!lista.datos?.hayMas)}
            >
              Siguiente
            </button>
          </div>
        </ListaMaestra>

        <Detalle>
          {esNuevo ? (
            <AltaDeFicha
              paso={pasoDelAlta}
              onPaso={(id) => fijar({ paso: id })}
              onDescartar={() => onSujeto('')}
              onRegistrada={(ficha) => {
                /* Se abre el predio recien creado, que es lo que el artboard
                   hace al registrar. La lista NO se refresca sola aqui: la
                   recarga la trae el cambio de sujeto, y el listado vuelve a
                   pedirse porque su llave incluye los filtros de la ruta. */
                onIr('catastro', 'predios', { ver: VISTAS_DEL_PREDIO[1].k }, String(ficha.predioId));
              }}
            />
          ) : predio === null ? (
            <div style={{ flex: 1, display: 'grid', placeItems: 'center', padding: 32 }}>
              <div style={{ maxWidth: '46ch', textAlign: 'center' }}>
                <p style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>
                  {elegido !== null && lista.datos !== null ? PREDIOS.fueraDeLaPagina : PREDIOS.sinSeleccion}
                </p>
                <p
                  style={{
                    margin: '7px 0 0',
                    fontSize: 13.5,
                    lineHeight: 1.55,
                    color: 'var(--tinta-3)',
                    textWrap: 'pretty',
                  }}
                >
                  {elegido !== null && lista.datos !== null
                    ? PREDIOS.fueraDeLaPaginaDetalle
                    : `${PREDIOS.sinSeleccionDetalle} ${MOTIVOS.buscadorSoloCodigo}`}
                </p>
              </div>
            </div>
          ) : (
            <DetalleDelPredio predio={predio} vista={vista} onVista={(k) => fijar({ ver: k })} onIr={onIr} />
          )}
        </Detalle>
      </Split>
      <PieDeSangre>
        <Servida
          lee={
            esNuevo
              ? [api.RUTAS.vias]
              : [
                  api.RUTAS.predios,
                  api.RUTAS.fichas,
                  /* Las CUATRO lecturas de ficha, porque la de un predio se pide
                     a la de SU clase. Nombrarlas todas es lo que hace que, con el
                     servidor caido, esta pantalla siga diciendo QUE no pudo leer. */
                  ...api.TIPOS_DE_FICHA.map((t) => api.RUTA_DE_LA_FICHA[t].ruta),
                  api.RUTAS.frentes,
                  /* Y la unica de este detalle que no es de `catastro`: los
                     hallazgos que fiscalizacion le encontro a este predio (#17). */
                  fiscalizacion.RUTAS.hallazgosDelPredio,
                ]
          }
          /* Las CUATRO, porque el alta va a una u otra segun la clase de ficha
             que se elija en el primer paso, y cada una exige su propio permiso. */
          escribe={
            esNuevo
              ? api.TIPOS_DE_FICHA.map((t) => ({ metodo: 'POST', ruta: api.RUTA_DEL_ALTA[t] }))
              : undefined
          }
          falta={
            esNuevo
              ? ALTA.noViajanNota
              : `${MOTIVOS.ordenAcotado} ${MOTIVOS.filtrosLosAplicaElServidor} ${MOTIVOS.sinAutovaluo}`
          }
        />
      </PieDeSangre>
    </div>
  );
}

function BOTON_DE_PAGINA(impedido: boolean): CSSProperties {
  return {
    border: '1px solid var(--linea)',
    borderRadius: 5,
    padding: '4px 11px',
    background: 'var(--blanco)',
    fontSize: 12.5,
    cursor: impedido ? 'not-allowed' : 'pointer',
    opacity: impedido ? 0.55 : 1,
  };
}

function DetalleDelPredio({
  predio,
  vista,
  onVista,
  onIr,
}: {
  predio: api.PredioDelCatastro;
  vista: string;
  onVista: (k: string) => void;
  onIr: PantallaProps['onIr'];
}) {
  /* La grilla primero, y no por gusto: es la que dice de QUE CLASE es la ficha, y
     la ficha entera se pide a la ruta de su clase. Sin este paso habria que
     suponerla, y suponer «urbana» contesta 404 en todo predio cuya ficha no sea
     la UNICA: esa ruta fija «TipoFicha.UNICA» y no devuelve ninguna otra. */
  const enLaGrilla = useRecurso(
    (senal) => api.fichas({ codRefCatastral: predio.codRefCatastral }, { tamano: 50 }, senal),
    ['ficha-del-predio', predio.codRefCatastral],
    vista === 'ficha' || vista === 'movimientos',
  );
  const suya = enLaGrilla.datos?.contenido.find((f) => f.codRefCatastral === predio.codRefCatastral) ?? null;
  const clase = suya !== null && api.esTipoDeFicha(suya.tipo) ? suya.tipo : null;

  /* Y el historico viaja SOLO en la pestana que lo pinta. Las dos lecturas son la
     misma ruta y contestan cosas distintas, asi que la llave lleva el parametro:
     sin el, volver de «Movimientos» a «Ficha vigente» reusaria la respuesta con
     el historico dentro y `?historico=` no decidiria nada. */
  const conHistorico = vista === 'movimientos';
  const completa = useRecurso(
    (senal) => api.ficha(clase!, predio.codRefCatastral, { historico: conHistorico }, senal),
    ['ficha-completa', predio.codRefCatastral, clase, conHistorico],
    clase !== null && (vista === 'ficha' || vista === 'movimientos'),
  );

  const frentes = useRecurso(
    (senal) => api.frentes(predio.predioId, senal),
    ['frentes-del-predio', predio.predioId],
    vista === 'frentes',
  );

  /* Los hallazgos de fiscalizacion, por el PREDIO (#17, y #71 AC-4). Es la unica
     lectura de este detalle que no es del modulo `catastro`, y por eso la hoja
     declara tambien su acceso: quien tenga `actualizacion_catastro` y no
     `fiscalizacion_catastral` vera aqui un 403, dibujado como el rechazo que es. */
  const hallazgos = useRecurso(
    (senal) => fiscalizacion.hallazgosDelPredio(predio.predioId, senal),
    ['hallazgos-del-predio', predio.predioId],
    vista === 'hallazgos',
  );

  /* Los bloques de detalle salen del MISMO dato que la cabecera, asi que se
     dibujan solo cuando esa lectura esta resuelta: con la respuesta anterior
     todavia en la mano, la cabecera ensenaria el esqueleto y los bloques de
     debajo el contenido de la ficha que ya no se esta mirando. */
  const conDatos = completa.cargando || completa.error !== null ? null : completa.datos;

  const contexto = [
    predio.tipo,
    predio.codigoDeSector ? `sector ${predio.codigoDeSector}` : null,
    predio.codigoDeManzana ? `manzana ${predio.codigoDeManzana}` : null,
    predio.lote ? `lote ${predio.lote}` : null,
    predio.via,
    predio.fichado ? 'con ficha catastral' : 'sin ficha catastral',
  ]
    .filter((x) => x !== null && x !== '')
    .join(' · ');

  return (
    <>
      <div
        style={{
          flex: '0 0 auto',
          padding: '12px 18px',
          background: 'var(--blanco)',
          borderBottom: '1px solid var(--linea)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--azul)', fontVariantNumeric: 'tabular-nums' }}>
            {predio.codRefCatastral}
          </span>
          <Insignia tono={predio.estado === 'ACTIVO' ? 'ok' : 'bad'}>{predio.estado}</Insignia>
          <Insignia tono={predio.fichado ? 'ok' : 'warn'}>{predio.fichado ? 'Fichado' : 'Sin ficha'}</Insignia>
          <span style={{ flex: 1, minWidth: 20 }} />
          <button
            type="button"
            onClick={() => onIr('urbano', 'zonificacion', {}, String(predio.predioId))}
            className="hov-borde"
            style={ACCION_SECUNDARIA}
          >
            Zonificacion
          </button>
          <button
            type="button"
            onClick={() => onIr('riesgo', 'predio', {}, String(predio.predioId))}
            className="hov-borde"
            style={ACCION_SECUNDARIA}
          >
            Riesgo
          </button>
          <button
            type="button"
            onClick={() => onIr('catastro', 'plano', {}, predio.codigoDeSector ?? '')}
            className="hov-borde"
            style={ACCION_SECUNDARIA}
          >
            Plano del sector
          </button>
        </div>
        <p style={{ margin: '8px 0 0', fontSize: 17, fontWeight: 700, letterSpacing: '-.015em', textWrap: 'pretty' }}>
          {predio.direccion}
        </p>
        <p style={{ margin: '4px 0 0', fontSize: 13.5, lineHeight: 1.5, color: 'var(--tinta-3)', textWrap: 'pretty' }}>
          {contexto}
        </p>
      </div>

      <Tira entradas={VISTAS_DEL_PREDIO} actual={vista} onElegir={onVista} />

      <div style={{ flex: 1, overflow: 'auto', minHeight: 0, padding: '16px 18px 24px' }}>
        <div style={{ maxWidth: 920, display: 'flex', flexDirection: 'column', gap: 14 }}>
          {vista === 'identificacion' ? (
            <>
              <Seccion titulo="Lo que el padron dice de este predio">
                <Rejilla>
                  <Dato rotulo="Codigo de referencia catastral">{predio.codRefCatastral}</Dato>
                  <Dato rotulo="Identificador">{predio.predioId}</Dato>
                  <Dato rotulo="Tipo">{predio.tipo}</Dato>
                  <Dato rotulo="Direccion">{predio.direccion}</Dato>
                  <Dato rotulo="Numero municipal">{predio.numeroMunicipal}</Dato>
                  <Dato rotulo="Via del catalogo">
                    {predio.codigoDeVia ? `${predio.codigoDeVia} — ${predio.via ?? ''}` : null}
                  </Dato>
                  <Dato rotulo="Sector">{predio.codigoDeSector}</Dato>
                  <Dato rotulo="Manzana">{predio.codigoDeManzana}</Dato>
                  <Dato rotulo="Lote">{predio.lote}</Dato>
                  <Dato rotulo="Ubigeo">{predio.ubigeo}</Dato>
                  <Dato rotulo="Estado">{predio.estado}</Dato>
                </Rejilla>
              </Seccion>
              <Aviso tono="info" titulo="Lo que este listado no publica">
                {MOTIVOS.sinTitularEnLaLista} {MOTIVOS.sinAutovaluo}
              </Aviso>
            </>
          ) : null}

          {vista === 'ficha' || vista === 'movimientos' ? (
            <Seccion
              titulo={vista === 'ficha' ? FICHA.cabecera : FICHA.movimientos}
              nota={`Predio ${predio.predioId}`}
            >
              <Lectura recurso={enLaGrilla} espera="">
                {() =>
                  suya === null ? (
                    <div style={{ padding: '14px 16px' }}>
                      <Aviso tono="warn" titulo="Este predio no tiene ficha vigente hoy">
                        La grilla de fichas contesta y no trae ninguna con este codigo a la fecha de hoy. Un predio
                        inscrito sin ficha no tiene area, ni uso, ni construcciones que valorizar.
                      </Aviso>
                    </div>
                  ) : clase === null ? (
                    <div style={{ padding: '14px 16px' }}>
                      <Aviso tono="bad" titulo={`Clase de ficha desconocida: «${suya.tipo}»`}>
                        {MOTIVOS.tipoDeFichaDesconocido}
                      </Aviso>
                    </div>
                  ) : (
                    <Lectura recurso={completa} espera="">
                      {(f) =>
                        vista === 'ficha' ? (
                          <Rejilla>
                            <Dato rotulo="Ficha">{f.id}</Dato>
                            <Dato rotulo="Tipo de ficha">{f.tipo}</Dato>
                            <Dato rotulo="Version">{f.version}</Dato>
                            <Dato rotulo="Area de terreno">{f.areaTerreno}</Dato>
                            <Dato rotulo="Uso">{f.uso}</Dato>
                            <Dato rotulo="Frontis">{f.frontis}</Dato>
                            <Dato rotulo="Condicion de propiedad">{f.condicionPropiedad}</Dato>
                            <Dato rotulo="Tipo de edificacion">{f.tipoEdificacion}</Dato>
                            <Dato rotulo="Denominacion">{f.denominacion}</Dato>
                            <Dato rotulo="Vigente desde">{f.vigenciaDesde}</Dato>
                            <Dato rotulo="Vigente hasta">{f.vigenciaHasta}</Dato>
                            <Dato rotulo="Origen">{f.origen}</Dato>
                            <Dato rotulo="Documento de origen">{f.documentoOrigen}</Dato>
                            <Dato rotulo="Observacion">{f.observacion}</Dato>
                            <Dato rotulo="Titular">{suya.titular}</Dato>
                          </Rejilla>
                        ) : (
                          <Movimientos ficha={f} />
                        )
                      }
                    </Lectura>
                  )
                }
              </Lectura>
            </Seccion>
          ) : null}

          {vista === 'ficha' && conDatos !== null ? <BloquesDeLaFicha ficha={conDatos} /> : null}

          {vista === 'frentes' ? (
            <Seccion titulo="Frentes del predio" nota={`Predio ${predio.predioId}`}>
              <Lectura recurso={frentes} espera="">
                {(r) => (
                  <>
                    {r.motivoDeLaDerivacion ? (
                      <div style={{ padding: '14px 16px' }}>
                        <Aviso tono="warn" titulo="La derivacion no propuso ningun frente">
                          {r.motivoDeLaDerivacion}
                        </Aviso>
                      </div>
                    ) : null}
                    <Tabla
                      columnas={[
                        { label: 'Via', pinta: (f: api.Frente) => `${f.viaCodigo} — ${f.viaNombre}` },
                        { label: 'Longitud (m)', numerica: true, pinta: (f: api.Frente) => f.longitud },
                        {
                          label: 'Estado',
                          pinta: (f: api.Frente) => (
                            <Insignia tono={f.longitudEstado === 'CONFIRMADA' ? 'ok' : 'warn'}>
                              {f.longitudEstado}
                            </Insignia>
                          ),
                        },
                        { label: 'Numeracion', pinta: (f: api.Frente) => guion(f.numeracion) },
                        { label: 'Confirmado por', pinta: (f: api.Frente) => guion(f.confirmadoPor) },
                      ]}
                      filas={r.frentes}
                      llave={(f) => f.id}
                      vacio="No hay ningun frente derivado para este predio."
                      pie="Un frente nace PROPUESTA porque lo derivo una maquina cortando el lote contra el eje de la via; confirmarlo es un acto de una persona, con su observacion (ADR-0021). «catastro» publica los metros lineales y no determina ningun arbitrio con ellos: el importe lo pone «rentas» (ADR-0024)."
                    />
                  </>
                )}
              </Lectura>
            </Seccion>
          ) : null}

          {vista === 'hallazgos' ? (
            <>
              <Seccion titulo={TITULOS_DE_FISCALIZACION.hallazgosDelPredio} nota={`Predio ${predio.predioId}`}>
                <Lectura recurso={hallazgos} espera="">
                  {(r) => (
                    <Tabla
                      columnas={[
                        {
                          label: COLUMNAS_DE_FISCALIZACION.campania,
                          pinta: (h: fiscalizacion.HallazgoDelPredio) => `${h.campaniaCodigo}`,
                        },
                        {
                          label: COLUMNAS_DE_FISCALIZACION.clase,
                          pinta: (h: fiscalizacion.HallazgoDelPredio) => <Insignia>{h.clase}</Insignia>,
                        },
                        {
                          label: COLUMNAS_DE_FISCALIZACION.areaDeLaFicha,
                          numerica: true,
                          pinta: (h: fiscalizacion.HallazgoDelPredio) => guion(h.areaDeLaFicha),
                        },
                        {
                          label: COLUMNAS_DE_FISCALIZACION.areaVerificada,
                          numerica: true,
                          pinta: (h: fiscalizacion.HallazgoDelPredio) => h.areaVerificada,
                        },
                        {
                          label: COLUMNAS_DE_FISCALIZACION.exceso,
                          numerica: true,
                          pinta: (h: fiscalizacion.HallazgoDelPredio) => guion(h.excesoVerificado),
                        },
                        {
                          label: COLUMNAS_DE_FISCALIZACION.estado,
                          pinta: (h: fiscalizacion.HallazgoDelPredio) => (
                            <Insignia tono={h.estado === 'FIRME' ? 'ok' : 'bad'}>{h.estado}</Insignia>
                          ),
                        },
                      ]}
                      filas={r.hallazgos}
                      llave={(h) => h.id}
                      /* Quien verifico, cuando, y **el acta** van debajo de su
                         fila y no en tres columnas mas. El panel de detalle de
                         un predio mide 774 px y con nueve columnas la tabla se
                         iba a 878: la del acta —que es lo unico que este
                         `record` anade, y lo unico que esta interfaz puede LEER
                         de un acta— quedaba fuera del borde de la pagina.
                         Abajo caben enteras y se leen como una frase. */
                      detalle={(h) => (
                        <>
                          {COLUMNAS_DE_FISCALIZACION.verificado} {h.verificadoEn} ·{' '}
                          {COLUMNAS_DE_FISCALIZACION.inspector} {h.inspector} ·{' '}
                          {COLUMNAS_DE_FISCALIZACION.acta}{' '}
                          {h.acta === null ? 'Sin acta' : `${h.acta.numero} del ${h.acta.fecha}`}
                        </>
                      )}
                      vacio={VACIOS_DE_FISCALIZACION.hallazgosDelPredio}
                      pie={PIES_DE_FISCALIZACION.hallazgosDelPredio}
                    />
                  )}
                </Lectura>
              </Seccion>
              <Aviso tono="info" titulo="Esta lectura no puede traer un omiso catastral">
                {MOTIVOS_DE_FISCALIZACION.sinOmisos}{' '}
                {MOTIVOS_DE_FISCALIZACION.laListaVaciaNoEsUnCuatrocientosCuatro}
              </Aviso>
            </>
          ) : null}
        </div>
      </div>
    </>
  );
}

/**
 * Lo que cuelga de la ficha: lo construido, las obras y el bloque de su clase.
 *
 * <h2>Los tres bloques de detalle se dibujan por separado a proposito</h2>
 *
 * `economico`, `bienesComunes` y `rural` son **nulos salvo el que toca**, y un
 * nulo aqui significa «esta ficha no es de las que lo declaran», no «este predio
 * no declara nada». Por eso cada bloque existe o no existe entero, en vez de
 * salir vacio: una ficha rural con una tabla de actividades sin filas se leeria
 * como un local sin licencia.
 *
 * <h2>Y las obras complementarias salen SIN IMPORTE, diciendo por que</h2>
 *
 * No es que la lectura lo recorte: **no hay cifra que ensenar**. El Anexo III de
 * la R.M. 277-2025-VIVIENDA no esta transcrito en el corpus y `otra_instalacion`
 * no tiene columna de importe, asi que no hay ni cuadro del que leerlo ni dato
 * declarado donde estuviera. La tabla publica lo que el tecnico midio —que es,
 * cuanto y en que unidad— y el aviso dice que falta y de que depende. Poner un
 * cero seria inventar una base imponible, que es lo que este repositorio
 * prohibe; dejar la columna en blanco seria peor, porque se leeria como un dato
 * que no llego.
 */
function BloquesDeLaFicha({ ficha }: { ficha: api.Ficha }) {
  return (
    <>
      <Seccion titulo={FICHA.construcciones} nota={`${ficha.construcciones.length}`}>
        <Tabla
          columnas={[
            { label: 'Piso', pinta: (c: api.Construccion) => c.piso },
            { label: 'Area construida', numerica: true, pinta: (c: api.Construccion) => c.areaConstruida },
            { label: 'Ano', numerica: true, pinta: (c: api.Construccion) => guion(c.anioConstruccion) },
            { label: 'Material', pinta: (c: api.Construccion) => guion(c.material) },
            { label: 'Estado', pinta: (c: api.Construccion) => guion(c.estadoConservacion) },
            { label: 'Categorias', pinta: (c: api.Construccion) => c.categorias },
            {
              label: 'Construido',
              numerica: true,
              pinta: (c: api.Construccion) => guion(c.porcentajeConstruido),
            },
          ]}
          filas={ficha.construcciones}
          llave={(c) => c.id}
          vacio={FICHA.sinConstrucciones}
          pie={FICHA.notaDeConstrucciones}
        />
      </Seccion>

      <Seccion titulo={FICHA.instalaciones} nota={`${ficha.instalaciones.length}`}>
        {ficha.instalaciones.length > 0 ? (
          <div style={{ padding: '14px 16px 0' }}>
            <Aviso tono="warn" titulo="Que falta para que estas obras tengan un valor">
              {MOTIVOS.obraSinImporte}
            </Aviso>
          </div>
        ) : null}
        <Tabla
          columnas={[
            { label: 'Descripcion', pinta: (o: api.Instalacion) => o.descripcion },
            { label: 'Cantidad', numerica: true, pinta: (o: api.Instalacion) => o.cantidad },
            { label: 'Unidad', pinta: (o: api.Instalacion) => o.unidad },
            { label: 'Ano', numerica: true, pinta: (o: api.Instalacion) => guion(o.anioConstruccion) },
            { label: 'Estado', pinta: (o: api.Instalacion) => guion(o.estadoConservacion) },
          ]}
          filas={ficha.instalaciones}
          llave={(o) => o.id}
          vacio={FICHA.sinInstalaciones}
        />
      </Seccion>

      {ficha.economico !== null ? (
        <Seccion titulo={FICHA.economico} nota={`${ficha.economico.actividades.length}`}>
          <Rejilla>
            <Dato rotulo="Actividades sin licencia">{ficha.economico.sinLicencia}</Dato>
            <Dato rotulo="Informacion complementaria">{ficha.economico.informacionComplementaria}</Dato>
          </Rejilla>
          <Tabla
            columnas={[
              { label: 'Conductor', pinta: (a: api.Actividad) => a.conductor },
              { label: 'Nombre comercial', pinta: (a: api.Actividad) => guion(a.nombreComercial) },
              { label: 'CIIU', pinta: (a: api.Actividad) => guion(a.ciiu) },
              { label: 'Area ocupada', numerica: true, pinta: (a: api.Actividad) => guion(a.areaOcupada) },
              {
                label: 'Licencia',
                pinta: (a: api.Actividad) =>
                  a.licenciaNumero === null ? (
                    <Insignia tono="warn">Sin licencia</Insignia>
                  ) : (
                    `${a.licenciaNumero}${a.licenciaFecha === null ? '' : ` · ${a.licenciaFecha}`}`
                  ),
              },
              { label: 'Anuncio', pinta: (a: api.Actividad) => guion(a.anuncioNumero) },
              { label: 'Declarada desde', pinta: (a: api.Actividad) => guion(a.vigenciaDesde) },
            ]}
            filas={ficha.economico.actividades}
            llave={(a) => a.id}
            vacio={FICHA.sinActividades}
            pie={MOTIVOS.actividadSinLicencia}
          />
        </Seccion>
      ) : null}

      {ficha.bienesComunes !== null ? (
        <Seccion titulo={FICHA.bienesComunes} nota={`${ficha.bienesComunes.bienes.length}`}>
          <Rejilla>
            <Dato rotulo="Area comun total">{ficha.bienesComunes.areaComunTotal}</Dato>
          </Rejilla>
          <Tabla
            columnas={[
              { label: 'Descripcion', pinta: (b: api.BienComun) => b.descripcion },
              { label: 'Area', numerica: true, pinta: (b: api.BienComun) => b.area },
              { label: 'Material', pinta: (b: api.BienComun) => guion(b.material) },
              { label: 'Estado', pinta: (b: api.BienComun) => guion(b.estadoConservacion) },
              { label: 'Ano', numerica: true, pinta: (b: api.BienComun) => guion(b.anioConstruccion) },
            ]}
            filas={ficha.bienesComunes.bienes}
            llave={(b) => b.id}
            vacio={FICHA.sinBienes}
          />
          <Tabla
            columnas={[
              { label: 'Predio participe', pinta: (p: api.Participacion) => p.predioId },
              { label: 'Participacion', numerica: true, pinta: (p: api.Participacion) => p.porcentaje },
            ]}
            filas={ficha.bienesComunes.participaciones}
            llave={(p) => p.predioId}
            vacio={FICHA.sinParticipaciones}
            pie={MOTIVOS.bienesSinValor}
          />
        </Seccion>
      ) : null}

      {ficha.rural !== null ? (
        <Seccion titulo={FICHA.rural} nota={`${ficha.rural.tierras.length}`}>
          <Rejilla>
            <Dato rotulo="Superficie total">{ficha.rural.hectareasTotales}</Dato>
          </Rejilla>
          <Tabla
            columnas={[
              { label: 'Clasificacion', pinta: (t: api.Tierra) => t.clasificacion },
              { label: 'Calidad agrologica', pinta: (t: api.Tierra) => guion(t.calidadAgrologica) },
              { label: 'Riego', pinta: (t: api.Tierra) => t.riego },
              { label: 'Superficie', numerica: true, pinta: (t: api.Tierra) => t.hectareas },
              { label: 'De area comun', numerica: true, pinta: (t: api.Tierra) => guion(t.hectareasComunes) },
            ]}
            filas={ficha.rural.tierras}
            llave={(t) => t.id}
            vacio={FICHA.sinTierras}
          />
          <Tabla
            columnas={[
              { label: 'Orientacion', pinta: (c: api.Colindante) => c.orientacion },
              { label: 'Colinda con', pinta: (c: api.Colindante) => c.descripcion },
            ]}
            filas={ficha.rural.colindantes}
            llave={(c) => c.orientacion}
            vacio={FICHA.sinColindantes}
            pie={MOTIVOS.ruralEnHectareas}
          />
        </Seccion>
      ) : null}

      <Aviso tono="info" titulo="Lo que esta ficha publica y lo que no">
        {MOTIVOS.fichaPorSuClase} {FICHA.soloSuBloque} {MOTIVOS.historicoNoSePide}
      </Aviso>
    </>
  );
}

/**
 * «Movimientos del Predio»: todas las versiones de la ficha.
 *
 * Viene de la MISMA ruta que la ficha vigente, con `?historico=true`, que es
 * como `ResumenPredialController` documenta esta pestana. Y `historico` **nulo
 * no es una lista vacia**: nulo es «no se pidio», y una lista vacia no puede
 * pasar —toda ficha tiene al menos la version vigente—. Por eso el nulo se
 * dibuja como un aviso y no como «no hay movimientos», que seria falso.
 */
function Movimientos({ ficha }: { ficha: api.Ficha }) {
  if (ficha.historico === null) {
    return (
      <div style={{ padding: '14px 16px' }}>
        <Aviso tono="warn" titulo="Esta respuesta no trae ninguna version">
          {MOTIVOS.historicoNoPedido}
        </Aviso>
      </div>
    );
  }
  return (
    <Tabla
      columnas={[
        { label: 'Version', numerica: true, pinta: (v: api.VersionDeLaFicha) => v.version },
        { label: 'Desde', pinta: (v: api.VersionDeLaFicha) => v.vigenciaDesde },
        { label: 'Hasta', pinta: (v: api.VersionDeLaFicha) => guion(v.vigenciaHasta) },
        {
          label: 'Estado',
          pinta: (v: api.VersionDeLaFicha) => (
            <Insignia tono={v.vigente ? 'ok' : 'info'}>{v.vigente ? 'Vigente' : 'Cerrada'}</Insignia>
          ),
        },
        { label: 'Area de terreno', numerica: true, pinta: (v: api.VersionDeLaFicha) => v.areaTerreno },
        { label: 'Uso', pinta: (v: api.VersionDeLaFicha) => v.uso },
        /* La observacion va ANTES del origen y del documento, y no al final: es
           la mitad util de la fila —un diff dice que el area paso de 120 a 180 y
           solo ella dice si fue una fiscalizacion o un error de tecleo— y la
           tabla se desplaza a lo ancho, asi que la ultima columna es la que
           nadie ve. */
        { label: 'Observacion', pinta: (v: api.VersionDeLaFicha) => v.observacion },
        { label: 'Usuario', pinta: (v: api.VersionDeLaFicha) => v.usuario },
        { label: 'Origen', pinta: (v: api.VersionDeLaFicha) => v.origen },
        { label: 'Documento', pinta: (v: api.VersionDeLaFicha) => v.documentoOrigen },
      ]}
      filas={ficha.historico}
      llave={(v) => v.id}
      vacio={FICHA.sinMovimientos}
      pie={MOTIVOS.movimientosSonVersiones}
    />
  );
}

const ACCION_SECUNDARIA: CSSProperties = {
  border: '1px solid var(--linea)',
  borderRadius: 6,
  padding: '7px 13px',
  background: 'var(--blanco)',
  fontSize: 13,
  cursor: 'pointer',
};

/* ══════════ Territorio ═════════════════════════════════════════════════ */

const NODO_DE_VIAS = 'vias';

/**
 * Las claves que un acto de esta hoja pone en la ruta.
 *
 * El formulario se abre desde la ruta —`?acto=altaDeSector`— y no desde un
 * estado interno, por lo mismo que el asistente de alta de ficha: asi la
 * pantalla abierta se comparte por su URL, sobrevive a una recarga, y los
 * arneses pueden llegar a ella sin pulsar nada. Cerrarlo quita estas dos y
 * conserva las demas.
 */
const CLAVES_DEL_ACTO = ['acto', 'via'];

type ActoDelTerritorio = keyof typeof ACTOS_DEL_TERRITORIO;

function esActoDelTerritorio(k: string): k is ActoDelTerritorio {
  return Object.hasOwn(ACTOS_DEL_TERRITORIO, k);
}

/**
 * El catalogo territorial: sectores, sus manzanas y el catalogo vial, **y su
 * mantenimiento** (#72).
 *
 * <h2>Cinco escrituras que hasta aqui no ofrecia ninguna pantalla</h2>
 *
 * `SectorController` y `ViaController` publican el alta de un sector, su
 * correccion, el alta de una manzana, el alta de una via y su correccion. Esta
 * hoja las leia las tres y no escribia ninguna: el catalogo se podia mirar y no
 * mantener, y eso bloquea algo mas que si mismo —**el alta de un predio obliga a
 * elegir la via del catalogo**, asi que una calle que no este cargada no se
 * puede inscribir desde ningun sitio de esta interfaz—.
 *
 * <h2>La baja logica es OTRO acto, porque es otro privilegio</h2>
 *
 * En el backend la correccion y la baja son la **misma ruta**: un `PUT` cuyo
 * cuerpo decide cual de las dos es. El guardia comprueba `MODIFICACION`, que es
 * lo que la anotacion declara, y el controlador comprueba `ELIMINACION` **a
 * mano** cuando el cuerpo trae el estado en falso. O sea que quien puede
 * corregir y no retirar recibe un `403 SIN_PRIVILEGIO` en una pantalla en la que
 * acaba de guardar sin problema.
 *
 * Aqui son dos botones y dos formularios, y no una casilla dentro de «guardar»:
 * con la casilla, quien corrige un nombre y de paso la desmarca sin darse cuenta
 * pierde las dos cosas de un golpe, y el rechazo habla de un privilegio que no
 * tiene nada que ver con lo que creia estar haciendo.
 *
 * **Y no se esconde el boton a quien no lo tenga**, porque no se puede saber:
 * ADR-0030 §3 pone la sesion y los permisos en `rentas`, y este backend no
 * publica ninguna lectura de «que privilegios tengo» —medido sobre sus
 * `@RequestMapping`—. Adivinarlo escondiendo el acto seria peor que el 403: un
 * boton que no esta no se puede preguntar. Se ofrece, y el rechazo dice de que
 * privilegio se trata y quien lo concede (`QUE_HACER.sinPrivilegioDeRetirar`).
 *
 * <h2>Y no se ofrece ningun control que el servidor no lea</h2>
 *
 * El alta de sector **no lleva casilla de estado** —un sector nace activo y el
 * `activo` del cuerpo se ignora— y la correccion **ensena el codigo sin dejar
 * cambiarlo** —el `codigo` del cuerpo se ignora, porque es un tramo del codigo
 * de referencia catastral de todos sus predios—. Las dos cosas estan declaradas
 * con su motivo en `LO_QUE_EL_SERVIDOR_DESCARTA`, y `verificaciones/territorio.mjs`
 * mide el cuerpo que de verdad viaja: un control que se rellena y se descarta en
 * silencio es peor que uno que falta, porque el servidor contesta que se guardo.
 */
export function Territorio({ ruta, onSujeto, onFiltros }: PantallaProps) {
  const sectores = useRecurso((senal) => api.sectores({ tamano: 100 }, senal), ['territorio-sectores']);
  const vias = useRecurso((senal) => api.vias({}, { tamano: 500 }, senal), ['territorio-vias']);
  const nodo = ruta.sujeto === '' ? (sectores.datos?.contenido[0]?.codigo ?? '') : ruta.sujeto;
  const esVias = nodo === NODO_DE_VIAS;
  const manzanas = useRecurso(
    (senal) => api.manzanas(nodo, { tamano: 500 }, senal),
    ['territorio-manzanas', nodo],
    !esVias && nodo !== '',
  );

  const ubigeos = [...new Set((vias.datos?.contenido ?? []).map((v) => v.ubigeo).filter((u) => u !== null))];
  const sector = sectores.datos?.contenido.find((s) => s.codigo === nodo) ?? null;

  const acto = ruta.filtros.acto ?? '';
  const laVia = vias.datos?.contenido.find((v) => v.codigo === (ruta.filtros.via ?? '')) ?? null;
  const abrir = (k: ActoDelTerritorio, via?: string) =>
    onFiltros({ ...ruta.filtros, acto: k, ...(via === undefined ? {} : { via }) });
  const cerrarElActo = () => onFiltros(sinClaves(ruta.filtros, CLAVES_DEL_ACTO));

  /* Tras una escritura, el catalogo se vuelve a pedir: lo que tiene lo dice el
     servidor, y quedarse con lo de antes ofreceria otra vez lo que se acaba de
     hacer —«Retirar» sobre una via que se acaba de retirar—. Es un boton y no
     algo automatico por lo mismo que en fiscalizacion: lo que el acto ensena
     arriba es lo que el servidor contesto, y refrescar la lista sola dejaria a
     quien mira sin saber cual de las dos cosas esta viendo. */
  const volverALeerElCatalogo = () => {
    sectores.reintentar();
    vias.reintentar();
    manzanas.reintentar();
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, width: '100%' }}>
      <Split>
        <ListaMaestra ancho={300}>
          <p
            style={{
              margin: 0,
              flex: '0 0 auto',
              padding: '11px 14px',
              borderBottom: '1px solid var(--linea-2)',
              fontSize: 12.5,
              fontWeight: 700,
            }}
          >
            {ubigeos.length === 1 ? `Distrito ${ubigeos[0]}` : TERRITORIO.sectores}
          </p>
          <div style={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
            <Lectura recurso={sectores} espera="">
              {(r) => (
                <>
                  {r.contenido.map((s) => (
                    <NodoDelArbol
                      key={s.id}
                      label={`Sector ${s.codigo} — ${s.nombre}`}
                      /* Un sector retirado lo dice el ARBOL, que es donde se
                         elige. Hasta #72 daba igual —nadie podia retirar nada
                         desde aqui—, y en cuanto se puede, un retirado que se
                         pinta igual que uno vigente es un dato degradado que no
                         se distingue de uno bueno: se elegiria para trabajar
                         sobre el sin saberlo. Sustituye al conteo y no se pone al
                         lado a proposito: en un sector fuera del catalogo, lo que
                         importa es eso y no cuantos lotes tenia. */
                      conteo={
                        s.activo ? (
                          s.lotes === null ? (
                            '—'
                          ) : (
                            `${s.lotes} lotes`
                          )
                        ) : (
                          <Insignia tono="bad">{TERRITORIO.laRetirada}</Insignia>
                        )
                      }
                      on={s.codigo === nodo}
                      onElegir={() => onSujeto(s.codigo)}
                    />
                  ))}
                  <NodoDelArbol
                    label={TERRITORIO.catalogoVial}
                    conteo={vias.datos ? `${vias.datos.totalElementos} vias` : '—'}
                    on={esVias}
                    onElegir={() => onSujeto(NODO_DE_VIAS)}
                  />
                </>
              )}
            </Lectura>
          </div>
        </ListaMaestra>

        <Detalle>
          <div
            style={{
              flex: '0 0 auto',
              display: 'flex',
              alignItems: 'flex-start',
              gap: 14,
              flexWrap: 'wrap',
              padding: '13px 18px',
              background: 'var(--blanco)',
              borderBottom: '1px solid var(--linea)',
            }}
          >
            <div style={{ flex: 1, minWidth: 260 }}>
              <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>
                {esVias ? TERRITORIO.catalogoVial : sector ? `Sector ${sector.codigo} — ${sector.nombre}` : 'Sectores'}
              </h2>
              <p
                style={{
                  margin: '5px 0 0',
                  fontSize: 13.5,
                  lineHeight: 1.55,
                  color: 'var(--tinta-3)',
                  maxWidth: '78ch',
                  textWrap: 'pretty',
                }}
              >
                {esVias ? TERRITORIO.notaDeVias : TERRITORIO.notaDeManzanas}
              </p>
            </div>
            <div
              role="group"
              aria-label={TERRITORIO.acciones}
              style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}
            >
              {esVias ? (
                <Boton tipo="primario" onClick={() => abrir('altaDeVia')}>
                  {ACTOS_DEL_TERRITORIO.altaDeVia}
                </Boton>
              ) : (
                <>
                  <Boton tipo="primario" onClick={() => abrir('altaDeSector')}>
                    {ACTOS_DEL_TERRITORIO.altaDeSector}
                  </Boton>
                  <Boton
                    impedido={sector === null}
                    motivo="Falta elegir un sector en la lista de la izquierda."
                    onClick={() => abrir('corregirSector')}
                  >
                    {ACTOS_DEL_TERRITORIO.corregirSector}
                  </Boton>
                  <Boton
                    impedido={sector === null}
                    motivo="Falta elegir un sector en la lista de la izquierda."
                    onClick={() => abrir(sector?.activo === false ? 'reactivarSector' : 'bajaDeSector')}
                  >
                    {sector?.activo === false
                      ? ACTOS_DEL_TERRITORIO.reactivarSector
                      : ACTOS_DEL_TERRITORIO.bajaDeSector}
                  </Boton>
                  <Boton
                    impedido={sector === null}
                    motivo="Falta elegir un sector: una manzana se da de alta dentro del suyo."
                    onClick={() => abrir('altaDeManzana')}
                  >
                    {ACTOS_DEL_TERRITORIO.altaDeManzana}
                  </Boton>
                </>
              )}
            </div>
          </div>

          {esActoDelTerritorio(acto) ? (
            <div style={{ flex: '0 1 auto', maxHeight: '62%', overflow: 'auto', padding: '14px 18px 0' }}>
              <ActoDelCatalogo
                acto={acto}
                sector={sector}
                via={laVia}
                onCerrar={cerrarElActo}
                onHecho={volverALeerElCatalogo}
              />
            </div>
          ) : null}

          {esVias ? (
            <CatalogoVial vias={vias} elegido={laVia} onActo={abrir} />
          ) : (
            <Lectura recurso={manzanas} espera="Elija un sector en la lista de la izquierda.">
              {(r) => (
                <TablaFija
                  columnas={[
                    { label: 'Manzana' },
                    { label: 'Sector' },
                    { label: 'Predios activos', numerica: true },
                    { label: 'Lotes distintos', numerica: true },
                  ]}
                  filas={r.contenido.map((m) => ({
                    llave: String(m.id),
                    celdas: [
                      { texto: m.codigo },
                      { texto: m.sectorCodigo },
                      { texto: guion(m.predios), numerica: true },
                      { texto: guion(m.lotes), numerica: true },
                    ],
                  }))}
                  vacio="Este sector no tiene ninguna manzana registrada."
                  pie={MOTIVOS.conteosDelSector}
                />
              )}
            </Lectura>
          )}
        </Detalle>
      </Split>
      <PieDeSangre>
        <Servida
          lee={[api.RUTAS.sectores, api.RUTAS.manzanas, api.RUTAS.vias]}
          escribe={[
            { metodo: 'POST', ruta: api.RUTAS.sectores },
            { metodo: 'PUT', ruta: api.RUTAS.sector },
            { metodo: 'POST', ruta: api.RUTAS.manzanas },
            { metodo: 'POST', ruta: api.RUTAS.vias },
            { metodo: 'PUT', ruta: api.RUTAS.via },
          ]}
          falta={`${MOTIVOS.viasNoCuelganDelSector} El arancel de cada via no sale aqui sino en Valores del ejercicio: cuelga del conjunto sellado de un anio, y esta lectura no lo tiene.`}
        />
      </PieDeSangre>
    </div>
  );
}

function NodoDelArbol({
  label,
  conteo,
  on,
  onElegir,
}: {
  label: string;
  conteo: ReactNode;
  on: boolean;
  onElegir: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onElegir}
      aria-current={on ? 'true' : 'false'}
      className="hov-suave"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        width: '100%',
        border: 0,
        borderBottom: '1px solid var(--linea-2)',
        borderLeft: `3px solid ${on ? 'var(--azul)' : 'transparent'}`,
        background: on ? 'var(--azul-suave)' : 'transparent',
        padding: '10px 13px',
        cursor: 'pointer',
        color: 'var(--tinta)',
        fontWeight: on ? 700 : 400,
      }}
    >
      <span style={{ flex: 1, minWidth: 0, textAlign: 'left', fontSize: 13.5 }}>{label}</span>
      <span style={{ fontSize: 12, color: 'var(--tinta-3)', flex: '0 0 auto' }}>{conteo}</span>
    </button>
  );
}

/**
 * El catalogo vial, con lo que se le puede hacer a cada via.
 *
 * Los dos actos van **en la fila** y no en la cabecera, porque cuelgan de una
 * via concreta: una cabecera con «Corregir» obligaria a elegir antes la via en
 * otro sitio, y elegir en un sitio para actuar en otro es como se acaba
 * corrigiendo la calle de al lado. La fila elegida se realza, para que el
 * formulario de abajo se sepa de quien es.
 *
 * El arancel de cada via NO sale aqui: cuelga del conjunto sellado de un anio y
 * lo ensena «Valores del ejercicio», que es la lectura que lo tiene.
 */
function CatalogoVial({
  vias,
  elegido,
  onActo,
}: {
  vias: Recurso<RespuestaPaginada<api.Via>>;
  elegido: api.Via | null;
  onActo: (acto: ActoDelTerritorio, via: string) => void;
}) {
  return (
    <Lectura recurso={vias} espera="">
      {(r) => (
        <TablaFija
          columnas={[
            { label: 'Codigo' },
            { label: 'Via' },
            { label: 'Tipo' },
            { label: 'Ubigeo' },
            { label: TERRITORIO.activa },
            { label: TERRITORIO.acciones },
          ]}
          filas={r.contenido.map((v) => ({
            llave: String(v.id),
            realzada: elegido?.codigo === v.codigo,
            celdas: [
              { texto: v.codigo },
              { texto: v.nombre },
              { texto: v.tipo },
              { texto: guion(v.ubigeo) },
              {
                texto: v.activa ? (
                  <Insignia tono="ok">{TERRITORIO.laVigente}</Insignia>
                ) : (
                  <Insignia tono="bad">{TERRITORIO.laRetirada}</Insignia>
                ),
              },
              {
                texto: (
                  <div
                    role="group"
                    aria-label={`Via ${v.codigo} · ${v.activa ? TERRITORIO.laVigente : TERRITORIO.laRetirada}`}
                    style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}
                  >
                    <Boton onClick={() => onActo('corregirVia', v.codigo)}>{TERRITORIO.corregir}</Boton>
                    <Boton onClick={() => onActo(v.activa ? 'bajaDeVia' : 'reactivarVia', v.codigo)}>
                      {v.activa ? TERRITORIO.retirar : TERRITORIO.devolver}
                    </Boton>
                  </div>
                ),
              },
            ],
          }))}
          vacio="El catalogo vial esta vacio."
          pie="La via sale del catalogo y no se escribe libre: dos formas de escribir la misma calle producen dos direcciones que nadie cruza, y ninguna de las dos se puede corregir sin tocar los predios de las dos."
        />
      )}
    </Lectura>
  );
}

/* ── Los nueve actos del catalogo, cada uno con lo que su ruta pide ──────── */

/**
 * Que formulario abre cada acto, y sobre que.
 *
 * Los cuatro campos de sector y de via se declaran aqui y no dentro de cada
 * `Acto` porque son los mismos en el alta y en la correccion: lo que cambia es
 * si son obligatorios —en el alta lo son, y el servidor los exige— y si el
 * codigo se puede escribir o solo se ensena.
 */
function ActoDelCatalogo({
  acto,
  sector,
  via,
  onCerrar,
  onHecho,
}: {
  acto: ActoDelTerritorio;
  sector: api.Sector | null;
  via: api.Via | null;
  onCerrar: () => void;
  onHecho: () => void;
}) {
  const titulo = ACTOS_DEL_TERRITORIO[acto];
  const nota = NOTAS_DE_LOS_ACTOS_DEL_TERRITORIO[acto];
  /* Lo que el usuario no rellena NO viaja: en un `PUT` la ausencia conserva el
     valor y la cadena vacia lo BORRA, asi que mandar `''` por un campo que nadie
     toco borraria la zona o el ubigeo sin que nadie lo pidiera. */
  const oNada = (v: string | undefined) => (v === undefined || v === '' ? undefined : v);

  if (acto === 'altaDeSector') {
    return (
      <Acto
        key="altaDeSector"
        titulo={titulo}
        nota={nota}
        campos={[
          { k: 'codigo', ...CAMPOS_DEL_TERRITORIO.codigoDeSector },
          { k: 'nombre', ...CAMPOS_DEL_TERRITORIO.nombreDelSector, ancho: 320 },
          { k: 'zona', ...CAMPOS_DEL_TERRITORIO.zona, opcional: true },
        ]}
        explicaciones={{
          CONFLICTO: QUE_HACER.codigoDeSectorRepetido,
          VALIDACION: QUE_HACER.campoRechazado,
          SIN_PRIVILEGIO: QUE_HACER.sinPrivilegioDeEscribir,
        }}
        enviar={(v) =>
          api.registrarSector({
            codigo: v.codigo!,
            nombre: v.nombre!,
            zona: oNada(v.zona),
            observacion: v.observacion!,
          })
        }
        pinta={(s) => <ElSector sector={s} onHecho={onHecho} />}
        onCerrar={onCerrar}
      />
    );
  }

  if (acto === 'altaDeVia') {
    return (
      <Acto
        key="altaDeVia"
        titulo={titulo}
        nota={nota}
        campos={[
          { k: 'codigo', ...CAMPOS_DEL_TERRITORIO.codigoDeVia },
          { k: 'tipo', ...CAMPOS_DEL_TERRITORIO.tipoDeVia, opciones: api.TIPOS_DE_VIA },
          { k: 'nombre', ...CAMPOS_DEL_TERRITORIO.nombreDeLaVia, ancho: 320 },
          { k: 'ubigeo', ...CAMPOS_DEL_TERRITORIO.ubigeo, opcional: true },
        ]}
        explicaciones={{
          CONFLICTO: QUE_HACER.codigoDeViaRepetido,
          VALIDACION: QUE_HACER.campoRechazado,
          SIN_PRIVILEGIO: QUE_HACER.sinPrivilegioDeEscribir,
        }}
        enviar={(v) =>
          api.registrarVia({
            codigo: v.codigo!,
            tipo: v.tipo!,
            nombre: v.nombre!,
            ubigeo: oNada(v.ubigeo),
            observacion: v.observacion!,
          })
        }
        pinta={(x) => <LaVia via={x} onHecho={onHecho} />}
        onCerrar={onCerrar}
      />
    );
  }

  if (acto === 'altaDeManzana' || acto === 'corregirSector' || acto === 'bajaDeSector' || acto === 'reactivarSector') {
    if (sector === null) return <SinSujeto que="sector" onCerrar={onCerrar} />;
    if (acto === 'altaDeManzana') {
      return (
        <Acto
          key={`altaDeManzana-${sector.codigo}`}
          titulo={titulo}
          nota={nota}
          campos={[
            {
              k: 'sector',
              rotulo: CAMPOS_DEL_TERRITORIO.codigoDeSector.rotulo,
              fijo: sector.codigo,
              ayuda: 'El de la lista de la izquierda: la manzana se da de alta dentro de el.',
            },
            { k: 'codigo', ...CAMPOS_DEL_TERRITORIO.codigoDeManzana },
          ]}
          explicaciones={{
            CONFLICTO: QUE_HACER.codigoDeManzanaRepetido,
            NO_ENCONTRADO: QUE_HACER.sectorQueNoEsta,
            VALIDACION: QUE_HACER.campoRechazado,
            SIN_PRIVILEGIO: QUE_HACER.sinPrivilegioDeEscribir,
          }}
          enviar={(v) => api.registrarManzana(sector.codigo, { codigo: v.codigo!, observacion: v.observacion! })}
          pinta={(m) => <LaManzana manzana={m} onHecho={onHecho} />}
          onCerrar={onCerrar}
        />
      );
    }
    if (acto === 'corregirSector') {
      return (
        <Acto
          key={`corregirSector-${sector.codigo}`}
          titulo={titulo}
          nota={nota}
          campos={[
            {
              k: 'codigo',
              rotulo: CAMPOS_DEL_TERRITORIO.codigoDeSector.rotulo,
              fijo: sector.codigo,
              ayuda: LO_QUE_NO_SE_EDITA.sector,
            },
            { k: 'nombre', ...CAMPOS_DEL_TERRITORIO.nombreDelSector, opcional: true, ancho: 320 },
            { k: 'zona', ...CAMPOS_DEL_TERRITORIO.zona, opcional: true },
          ]}
          explicaciones={{
            NO_ENCONTRADO: QUE_HACER.sectorQueNoEsta,
            VALIDACION: QUE_HACER.campoRechazado,
            SIN_PRIVILEGIO: QUE_HACER.sinPrivilegioDeEscribir,
          }}
          enviar={(v) =>
            api.modificarSector(sector.codigo, {
              nombre: oNada(v.nombre),
              zona: oNada(v.zona),
              observacion: v.observacion!,
            })
          }
          pinta={(s) => <ElSector sector={s} onHecho={onHecho} />}
          onCerrar={onCerrar}
        />
      );
    }
    const retirando = acto === 'bajaDeSector';
    return (
      <Acto
        key={`${acto}-${sector.codigo}`}
        titulo={titulo}
        nota={nota}
        campos={[
          {
            k: 'codigo',
            rotulo: CAMPOS_DEL_TERRITORIO.codigoDeSector.rotulo,
            fijo: `${sector.codigo} — ${sector.nombre}`,
            ayuda: 'El sector elegido en la lista de la izquierda.',
          },
        ]}
        advertencia={retirando ? RETIRADAS.sector : undefined}
        explicaciones={{
          NO_ENCONTRADO: QUE_HACER.sectorQueNoEsta,
          VALIDACION: QUE_HACER.campoRechazado,
          SIN_PRIVILEGIO: retirando ? QUE_HACER.sinPrivilegioDeRetirar : QUE_HACER.sinPrivilegioDeEscribir,
        }}
        enviar={(v) => api.modificarSector(sector.codigo, { activo: !retirando, observacion: v.observacion! })}
        pinta={(s) => <ElSector sector={s} onHecho={onHecho} />}
        onCerrar={onCerrar}
      />
    );
  }

  if (via === null) return <SinSujeto que="via" onCerrar={onCerrar} />;
  if (acto === 'corregirVia') {
    return (
      <Acto
        key={`corregirVia-${via.codigo}`}
        titulo={titulo}
        nota={nota}
        campos={[
          {
            k: 'codigo',
            rotulo: CAMPOS_DEL_TERRITORIO.codigoDeVia.rotulo,
            fijo: via.codigo,
            ayuda: LO_QUE_NO_SE_EDITA.via,
          },
          { k: 'tipo', ...CAMPOS_DEL_TERRITORIO.tipoDeVia, opciones: api.TIPOS_DE_VIA, opcional: true },
          { k: 'nombre', ...CAMPOS_DEL_TERRITORIO.nombreDeLaVia, opcional: true, ancho: 320 },
          { k: 'ubigeo', ...CAMPOS_DEL_TERRITORIO.ubigeo, opcional: true },
        ]}
        explicaciones={{
          NO_ENCONTRADO: QUE_HACER.viaQueNoEsta,
          VALIDACION: QUE_HACER.campoRechazado,
          SIN_PRIVILEGIO: QUE_HACER.sinPrivilegioDeEscribir,
        }}
        enviar={(v) =>
          api.modificarVia(via.codigo, {
            tipo: oNada(v.tipo),
            nombre: oNada(v.nombre),
            ubigeo: oNada(v.ubigeo),
            observacion: v.observacion!,
          })
        }
        pinta={(x) => <LaVia via={x} onHecho={onHecho} />}
        onCerrar={onCerrar}
      />
    );
  }
  const retirando = acto === 'bajaDeVia';
  return (
    <Acto
      key={`${acto}-${via.codigo}`}
      titulo={titulo}
      nota={nota}
      campos={[
        {
          k: 'codigo',
          rotulo: CAMPOS_DEL_TERRITORIO.codigoDeVia.rotulo,
          fijo: `${via.codigo} — ${via.tipo} ${via.nombre}`,
          ayuda: 'La via elegida en la tabla.',
        },
      ]}
      advertencia={retirando ? RETIRADAS.via : undefined}
      explicaciones={{
        NO_ENCONTRADO: QUE_HACER.viaQueNoEsta,
        VALIDACION: QUE_HACER.campoRechazado,
        SIN_PRIVILEGIO: retirando ? QUE_HACER.sinPrivilegioDeRetirar : QUE_HACER.sinPrivilegioDeEscribir,
      }}
      enviar={(v) => api.modificarVia(via.codigo, { activa: !retirando, observacion: v.observacion! })}
      pinta={(x) => <LaVia via={x} onHecho={onHecho} />}
      onCerrar={onCerrar}
    />
  );
}

/** Por que un campo se ensena y no se puede escribir. Es el motivo del backend. */
const LO_QUE_NO_SE_EDITA = {
  sector:
    'Se ensena y no se edita: es uno de los tramos del codigo de referencia catastral de todos sus predios, y cambiarlo los desalinearia. El servidor descarta el que llegue en el cuerpo.',
  via: 'Se ensena y no se edita: es lo que la direccion de cada predio cita. El servidor descarta el que llegue en el cuerpo.',
} as const;

/** Un acto que necesita un sujeto que no esta elegido. */
function SinSujeto({ que, onCerrar }: { que: string; onCerrar: () => void }) {
  return (
    <Seccion titulo="Falta elegir" derecha={<Boton onClick={onCerrar}>Cerrar</Boton>}>
      <div style={{ padding: '14px 16px' }}>
        <Aviso tono="warn" titulo="Este acto cuelga de algo que no esta elegido">
          {`Esta direccion abre un acto sobre un ${que} que no esta en el catalogo leido, asi que no hay sobre que actuar. Se elige en la lista y se vuelve a abrir: actuar sobre lo que la pantalla no pudo leer seria escribir a ciegas.`}
        </Aviso>
      </div>
    </Seccion>
  );
}

/**
 * El sector que el servidor acaba de devolver.
 *
 * **Los tres conteos salen «—» y no cero**, y eso no es cosmetica: la respuesta
 * de una escritura los trae NULOS —`SectorResource.de(Sector)`, frente al
 * `SectorResource.de(SectorConConteos)` del listado— porque quien escribe un
 * sector no pidio contar nada. Un `0` diria «no tiene ninguna manzana», que en
 * la correccion de un sector con cuarenta seria sencillamente falso, y se
 * pintaria exactamente igual que una cifra leida. Lo hace `Dato`, que ya
 * distingue el nulo del cero, y lo mide `verificaciones/territorio.mjs`
 * comparando toda cifra del panel contra el JSON que la pagina recibio.
 */
function ElSector({ sector, onHecho }: { sector: api.Sector; onHecho: () => void }) {
  return (
    <>
      <Rejilla>
        <Dato rotulo="Identificador">{sector.id}</Dato>
        <Dato rotulo={CAMPOS_DEL_TERRITORIO.codigoDeSector.rotulo}>{sector.codigo}</Dato>
        <Dato rotulo={CAMPOS_DEL_TERRITORIO.nombreDelSector.rotulo}>{sector.nombre}</Dato>
        <Dato rotulo={CAMPOS_DEL_TERRITORIO.zona.rotulo}>{sector.zona}</Dato>
        <Dato rotulo="Estado">
          <Insignia tono={sector.activo ? 'ok' : 'bad'}>
            {sector.activo ? TERRITORIO.laVigente : TERRITORIO.laRetirada}
          </Insignia>
        </Dato>
        <Dato rotulo="Manzanas">{sector.manzanas}</Dato>
        <Dato rotulo="Predios activos">{sector.predios}</Dato>
        <Dato rotulo="Lotes distintos">{sector.lotes}</Dato>
      </Rejilla>
      <p style={{ margin: 0, padding: '0 16px', fontSize: 12.5, lineHeight: 1.5, color: 'var(--tinta-3)' }}>
        {MOTIVOS.conteosDeLaEscritura}
      </p>
      <VolverALeerElCatalogo onHecho={onHecho} />
    </>
  );
}

function LaManzana({ manzana, onHecho }: { manzana: api.Manzana; onHecho: () => void }) {
  return (
    <>
      <Rejilla>
        <Dato rotulo="Identificador">{manzana.id}</Dato>
        <Dato rotulo={CAMPOS_DEL_TERRITORIO.codigoDeManzana.rotulo}>{manzana.codigo}</Dato>
        <Dato rotulo={CAMPOS_DEL_TERRITORIO.codigoDeSector.rotulo}>{manzana.sectorCodigo}</Dato>
        <Dato rotulo="Predios activos">{manzana.predios}</Dato>
        <Dato rotulo="Lotes distintos">{manzana.lotes}</Dato>
      </Rejilla>
      <p style={{ margin: 0, padding: '0 16px', fontSize: 12.5, lineHeight: 1.5, color: 'var(--tinta-3)' }}>
        {MOTIVOS.conteosDeLaEscritura}
      </p>
      <VolverALeerElCatalogo onHecho={onHecho} />
    </>
  );
}

function LaVia({ via, onHecho }: { via: api.Via; onHecho: () => void }) {
  return (
    <>
      <Rejilla>
        <Dato rotulo="Identificador">{via.id}</Dato>
        <Dato rotulo={CAMPOS_DEL_TERRITORIO.codigoDeVia.rotulo}>{via.codigo}</Dato>
        <Dato rotulo={CAMPOS_DEL_TERRITORIO.tipoDeVia.rotulo}>{via.tipo}</Dato>
        <Dato rotulo={CAMPOS_DEL_TERRITORIO.nombreDeLaVia.rotulo}>{via.nombre}</Dato>
        <Dato rotulo={CAMPOS_DEL_TERRITORIO.ubigeo.rotulo}>{via.ubigeo}</Dato>
        <Dato rotulo="Estado">
          <Insignia tono={via.activa ? 'ok' : 'bad'}>
            {via.activa ? TERRITORIO.laVigente : TERRITORIO.laRetirada}
          </Insignia>
        </Dato>
      </Rejilla>
      <VolverALeerElCatalogo onHecho={onHecho} />
    </>
  );
}

/**
 * Volver a leer el catalogo, y **por que es un boton y no algo automatico**.
 *
 * El acto ya se hizo: lo que se ensena arriba es lo que el servidor contesto,
 * que es la respuesta autorizada. El arbol y la tabla salen de OTRAS lecturas, y
 * refrescarlas solas dejaria a quien mira sin saber cual de las dos cosas esta
 * viendo. Se ofrece, se dice, y quien lo pulse ve el catalogo con lo que acaba
 * de escribir dentro.
 */
function VolverALeerElCatalogo({ onHecho }: { onHecho: () => void }) {
  return (
    <div style={{ padding: '0 16px 4px' }}>
      <Boton onClick={onHecho}>Volver a leer el catalogo</Boton>
    </div>
  );
}

/* ══════════ Valores del ejercicio ══════════════════════════════════════ */

/** Una casilla de una matriz: la cifra sola, «—», o cuantas filas hay. */
function casilla(valores: readonly string[]): Celda {
  if (valores.length === 0) return { texto: '—', numerica: true };
  if (valores.length === 1) return { texto: valores[0], numerica: true };
  return { texto: `${valores.length} filas`, numerica: true };
}

/**
 * Que dice la celda de la via cuando el catalogo no la tiene, y **por que no un
 * identificador crudo**.
 *
 * `Via 1` no se distingue del nombre de una via: con el catalogo en 403 la tabla
 * salia entera con «Via 1 — Sin tramo 388.00» y quien la lee no tiene como saber
 * que le falta media columna. Son tres estados distintos y se dicen los tres,
 * porque piden trabajos distintos: el catalogo **no se pudo leer** —hay que
 * mirar el aviso de arriba—, **se esta pidiendo**, o se leyo y esa via **no
 * esta** en el, que es una inconsistencia del dato y no un fallo de red.
 */
function motivoDeLaViaQueFalta(vias: Recurso<RespuestaPaginada<api.Via>>): string {
  if (vias.error) return MOTIVOS.viaSinCatalogo;
  if (vias.datos === null) return '…';
  return MOTIVOS.viaQueNoEstaEnElCatalogo;
}

export function Valores({ ejercicio, ruta, onFiltros }: PantallaProps) {
  const cuadro = CUADROS.find((c) => c.k === ruta.filtros.cuadro) ?? CUADROS[0];
  const aranceles = useRecurso((senal) => api.aranceles(ejercicio, senal), ['aranceles', ejercicio]);
  const unitarios = useRecurso((senal) => api.valoresUnitarios(ejercicio, senal), ['unitarios', ejercicio]);
  const deprec = useRecurso((senal) => api.depreciacion(ejercicio, senal), ['depreciacion', ejercicio]);
  const vias = useRecurso((senal) => api.vias({}, { tamano: 500 }, senal), ['valores-vias']);

  const viaPorId = useMemo(() => {
    const mapa = new Map<number, api.Via>();
    for (const v of vias.datos?.contenido ?? []) mapa.set(v.id, v);
    return mapa;
  }, [vias.datos]);
  const sinVia = motivoDeLaViaQueFalta(vias);

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, overflow: 'hidden', width: '100%' }}>
      <Tira entradas={CUADROS} actual={cuadro.k} onElegir={(k) => onFiltros({ ...ruta.filtros, cuadro: k })} />
      <div
        style={{
          flex: '0 0 auto',
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
          padding: '12px 18px',
          background: 'var(--blanco)',
          borderBottom: '1px solid var(--linea)',
        }}
      >
        <p
          style={{
            margin: 0,
            flex: 1,
            minWidth: 220,
            fontSize: 13.5,
            lineHeight: 1.55,
            color: 'var(--tinta-2)',
            maxWidth: '80ch',
            textWrap: 'pretty',
          }}
        >
          {cuadro.nota}
        </p>
        <span
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 7,
            border: '1px solid var(--linea)',
            borderRadius: 999,
            padding: '4px 12px',
            background: 'var(--sup)',
            fontSize: 12.5,
            color: 'var(--tinta-3)',
            flex: '0 0 auto',
          }}
        >
          <Icono d={ICO.candado} tam={13} grosor={2} />
          {VALORES.soloLectura}
        </span>
        <span
          style={{
            fontSize: 12.5,
            color: 'var(--tinta-3)',
            flex: '0 0 auto',
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          Ejercicio {ejercicio}
        </span>
      </div>

      {cuadro.k === 'aranceles' ? (
        <Lectura recurso={aranceles} espera="">
          {(filas) => (
            <>
              {/* El cuadro llego y el CATALOGO no. Es una lectura aparte y con
                  su propio acceso, asi que su fallo se dice aqui —con las cuatro
                  distinciones de `ErrorDeApi` intactas— en vez de esconderse en
                  dos columnas degradadas. Sin esto, la tabla salia entera y sin
                  un solo aviso (#49). */}
              {vias.error ? <Fallo error={vias.error} reintentar={vias.reintentar} /> : null}
              <TablaFija
                columnas={[
                  { label: 'Via' },
                  { label: 'Tipo' },
                  { label: 'Tramo' },
                  { label: 'Arancel S/ m²', numerica: true },
                  { label: 'Documento fuente' },
                ]}
                filas={filas.map((a) => {
                  const via = viaPorId.get(a.viaId);
                  return {
                    llave: String(a.id),
                    celdas: [
                      { texto: via ? `${via.codigo} — ${via.nombre}` : `${sinVia} · id ${a.viaId}` },
                      { texto: via ? via.tipo : sinVia },
                      { texto: a.tramo ?? 'Sin tramo' },
                      { texto: a.valorM2, numerica: true },
                      { texto: a.documentoFuente },
                    ],
                  };
                })}
                vacio="El conjunto sellado de este ejercicio no trae ningun arancel de terreno."
                pie={`${cuadro.pie} ${MOTIVOS.arancelSinZona} ${VALORES.noSeSellaAqui}`}
              />
            </>
          )}
        </Lectura>
      ) : null}

      {cuadro.k === 'unitarios' ? (
        <Lectura recurso={unitarios} espera="">
          {(filas) => {
            const partidas = [...new Set(filas.map((v) => v.partida))];
            const categorias = [...new Set(filas.map((v) => v.categoria))].sort();
            let hayCasillaAmbigua = false;
            const cuerpo = categorias.map((categoria) => ({
              llave: categoria,
              celdas: [
                { texto: categoria } as Celda,
                ...partidas.map((partida) => {
                  const suyas = filas
                    .filter((v) => v.categoria === categoria && v.partida === partida)
                    .map((v) => v.valorM2);
                  if (suyas.length > 1) hayCasillaAmbigua = true;
                  return casilla(suyas);
                }),
              ],
            }));
            return (
              <TablaFija
                columnas={[
                  { label: 'Categoria' },
                  ...partidas.map((p) => ({ label: ROTULO_DE_PARTIDA[p] ?? p, numerica: true })),
                ]}
                filas={cuerpo}
                vacio="El conjunto sellado de este ejercicio no trae el cuadro de valores unitarios."
                pie={`${cuadro.pie} ${MOTIVOS.sietePartidas}${
                  hayCasillaAmbigua ? ` ${MOTIVOS.casillaConVariasFilas}` : ''
                } ${VALORES.noSeSellaAqui}`}
              />
            );
          }}
        </Lectura>
      ) : null}

      {cuadro.k === 'depreciacion' ? (
        <Lectura recurso={deprec} espera="">
          {(filas) => {
            const tramos = [...new Set(filas.map((d) => d.antiguedadHasta))].sort((a, b) => {
              if (a === null) return 1;
              if (b === null) return -1;
              return a - b;
            });
            const claves = [...new Set(filas.map((d) => `${d.uso}|${d.material}|${d.estadoConservacion}`))];
            let hayCasillaAmbigua = false;
            const cuerpo = claves.map((clave) => {
              const [uso, material, conservacion] = clave.split('|');
              const celdas: Celda[] = [{ texto: uso }, { texto: material }, { texto: conservacion }];
              for (const tramo of tramos) {
                const suyas = filas
                  .filter(
                    (d) =>
                      d.uso === uso &&
                      d.material === material &&
                      d.estadoConservacion === conservacion &&
                      d.antiguedadHasta === tramo,
                  )
                  .map((d) => d.porcentaje);
                if (suyas.length > 1) hayCasillaAmbigua = true;
                celdas.push(casilla(suyas));
              }
              return { llave: clave, celdas };
            });
            return (
              <TablaFija
                columnas={[
                  { label: 'Uso' },
                  { label: 'Material' },
                  { label: 'Estado' },
                  ...tramos.map((t) => ({
                    label: t === null ? 'Sin limite declarado' : `Hasta ${t} anios`,
                    numerica: true,
                  })),
                ]}
                filas={cuerpo}
                vacio="El conjunto sellado de este ejercicio no trae el cuadro de depreciacion."
                pie={`${cuadro.pie}${
                  hayCasillaAmbigua ? ` ${MOTIVOS.casillaConVariasFilas}` : ''
                } ${VALORES.noSeSellaAqui}`}
              />
            );
          }}
        </Lectura>
      ) : null}

      <PieDeSangre>
        <Servida
          lee={[api.RUTAS.aranceles, api.RUTAS.valoresUnitarios, api.RUTAS.depreciacion, api.RUTAS.vias]}
          falta={VALORES.noSeSellaAqui}
        />
      </PieDeSangre>
    </div>
  );
}

/* ══════════ Fichas ═════════════════════════════════════════════════════ */

export function Fichas({ ruta, onFiltros }: PantallaProps) {
  const codigo = ruta.filtros.codRefCatastral ?? '';
  const tipo = ruta.filtros.tipo ?? '';
  const orden = ruta.filtros.ordenarPor ?? api.ORDENES.fichas.campos[0];
  const lista = useRecurso(
    (senal) =>
      api.fichas(
        { codRefCatastral: codigo, tipo: (tipo || undefined) as api.TipoDeFicha | undefined },
        { tamano: 50, ordenarPor: orden },
        senal,
      ),
    ['fichas', codigo, tipo, orden],
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <Seccion titulo="Buscar fichas">
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', padding: '14px 16px' }}>
          <Campo
            rotulo="Codigo de referencia catastral"
            valor={codigo}
            onCambio={(v) => onFiltros({ ...ruta.filtros, codRefCatastral: v })}
            marcador="Los primeros tramos bastan"
          />
          <Selector
            rotulo="Tipo de ficha"
            valor={tipo}
            onCambio={(v) => onFiltros({ ...ruta.filtros, tipo: v })}
            opciones={[{ valor: '', label: 'Todos' }, ...api.TIPOS_DE_FICHA.map((t) => ({ valor: t, label: t }))]}
            ayuda="Los cuatro del enumerado del backend, letra por letra"
          />
          <Selector
            rotulo="Ordenar por"
            valor={orden}
            onCambio={(v) => onFiltros({ ...ruta.filtros, ordenarPor: v })}
            opciones={api.ORDENES.fichas.campos.map((c) => ({ valor: c, label: ROTULO_DE_ORDEN[c] ?? c }))}
            ayuda="Solo los campos que el servidor admite"
          />
        </div>
      </Seccion>

      <Seccion titulo="La grilla de fichas">
        <Lectura recurso={lista} espera="">
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Codigo', pinta: (f: api.FichaEncontrada) => f.codRefCatastral },
                { label: 'Direccion', pinta: (f: api.FichaEncontrada) => f.direccion },
                { label: 'Tipo', pinta: (f: api.FichaEncontrada) => <Insignia>{f.tipo}</Insignia> },
                { label: 'Version', numerica: true, pinta: (f: api.FichaEncontrada) => f.version },
                { label: 'Area terreno', numerica: true, pinta: (f: api.FichaEncontrada) => f.areaTerreno },
                {
                  label: 'Area construida',
                  numerica: true,
                  pinta: (f: api.FichaEncontrada) => guion(f.areaConstruida),
                },
                { label: 'Uso', pinta: (f: api.FichaEncontrada) => f.uso },
                { label: 'Titular', pinta: (f: api.FichaEncontrada) => guion(f.titular) },
                { label: 'Vigente desde', pinta: (f: api.FichaEncontrada) => f.vigenciaDesde },
              ]}
              filas={r.contenido}
              llave={(f) => f.fichaId}
              vacio="Ninguna ficha cumple lo pedido."
              pie="Las areas llegan como texto y se pintan como texto: «AreaM2» se serializa con decimal plano, y pasarla por Number para volver a formatearla es como se pierde un decimal (RNF-055)."
            />
          )}
        </Lectura>
      </Seccion>

      <Servida lee={[api.RUTAS.fichas]} falta={MOTIVOS.ordenAcotado} />
    </div>
  );
}

/* ══════════ Plano catastral ════════════════════════════════════════════ */

export function Plano({ ruta, onSujeto }: PantallaProps) {
  const sector = ruta.sujeto;
  const marco = useRecurso(
    (senal) => api.marcoDelPlano({ codigoDeSector: sector || undefined }, senal),
    ['marco', sector],
  );
  const lotes = useRecurso((senal) => api.plano({ codigoDeSector: sector || undefined }, senal), ['plano', sector]);
  const sectores = useRecurso((senal) => api.sectores({ tamano: 100 }, senal), ['plano-sectores']);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <Seccion titulo="Ambito">
        <div style={{ padding: '14px 16px' }}>
          <Lectura recurso={sectores} espera="">
            {(r) => (
              <Selector
                rotulo="Sector"
                valor={sector}
                onCambio={onSujeto}
                opciones={[
                  { valor: '', label: 'Toda la municipalidad' },
                  ...r.contenido.map((s) => ({ valor: s.codigo, label: `${s.codigo} — ${s.nombre}` })),
                ]}
              />
            )}
          </Lectura>
        </div>
      </Seccion>

      <Seccion titulo="El marco de lo levantado">
        <Lectura recurso={marco} espera="">
          {(r) => (
            <>
              <Rejilla>
                <Dato rotulo="Lotes con poligono">{r.lotes}</Dato>
                <Dato rotulo="Oeste">{r.marco?.oeste}</Dato>
                <Dato rotulo="Sur">{r.marco?.sur}</Dato>
                <Dato rotulo="Este">{r.marco?.este}</Dato>
                <Dato rotulo="Norte">{r.marco?.norte}</Dato>
              </Rejilla>
              {r.notaDelMarco ? (
                <div style={{ padding: '0 16px 14px' }}>
                  <Aviso tono="warn" titulo="No hay marco que componer">
                    {r.notaDelMarco}
                  </Aviso>
                </div>
              ) : null}
            </>
          )}
        </Lectura>
      </Seccion>

      <Seccion titulo="Lotes del ambito">
        <Lectura recurso={lotes} espera="">
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Codigo', pinta: (l: api.LoteDelPlano) => l.codRefCatastral },
                { label: 'Direccion', pinta: (l: api.LoteDelPlano) => l.direccion },
                { label: 'Manzana', pinta: (l: api.LoteDelPlano) => guion(l.codigoDeManzana) },
                { label: 'Lote', pinta: (l: api.LoteDelPlano) => guion(l.lote) },
              ]}
              filas={r.lotes}
              llave={(l) => l.predioId}
              vacio={`Ningun lote de este ambito tiene poligono. Sin geometria: ${r.sinGeometria}.`}
              pie="Aqui no se dibuja ningun mapa, y no por falta de datos: elegir la libreria es una decision propia (ADR-0022 y ADR-0037) y no la toma este trabajo. Lo que se ensena es lo que el backend publica: el marco, y cuantos lotes se quedan fuera por no tener poligono."
            />
          )}
        </Lectura>
      </Seccion>

      <Servida lee={[api.RUTAS.plano, api.RUTAS.marcoDelPlano, api.RUTAS.sectores]} />
    </div>
  );
}
