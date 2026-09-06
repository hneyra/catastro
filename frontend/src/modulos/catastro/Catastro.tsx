import { useEffect, useMemo, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import type { PantallaProps } from '../../App';
import { AltaDeFicha } from './AltaDeFicha';
import * as api from '../../api/catastro';
import type { ErrorDeApi, RespuestaPaginada } from '../../api/cliente';
import { useRebote, useRecurso } from '../../api/useRecurso';
import type { Recurso } from '../../api/useRecurso';
import {
  Aviso,
  Campo,
  Dato,
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
import { Icono } from '../../ds/Icono';
import { ICO } from '../../ds/iconos';
import { ALTA, PASOS } from '../../datos/alta';
import {
  CHIPS_DE_PREDIOS,
  COLAS,
  CUADROS,
  MOTIVOS,
  PANEL,
  PREDIOS,
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
  filas: readonly { llave: string; celdas: readonly Celda[] }[];
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
            <tr key={f.llave} style={{ borderTop: '1px solid var(--linea-2)', background: 'var(--blanco)' }}>
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

/** Una de las cuatro tarjetas de cabecera (artboard 470-483). */
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
    <div
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
    </div>
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
          lee={esNuevo ? [api.RUTAS.vias] : [api.RUTAS.predios, api.RUTAS.fichas, api.RUTAS.frentes]}
          /* Las CUATRO, porque el alta va a una u otra segun la clase de ficha
             que se elija en el primer paso, y cada una exige su propio permiso. */
          escribe={esNuevo ? api.TIPOS_DE_FICHA.map((t) => api.RUTA_DEL_ALTA[t]) : undefined}
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
  const ficha = useRecurso(
    (senal) => api.fichas({ codRefCatastral: predio.codRefCatastral }, { tamano: 50 }, senal),
    ['ficha-del-predio', predio.codRefCatastral],
    vista === 'ficha',
  );
  const frentes = useRecurso(
    (senal) => api.frentes(predio.predioId, senal),
    ['frentes-del-predio', predio.predioId],
    vista === 'frentes',
  );

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

          {vista === 'ficha' ? (
            <Seccion titulo="Ficha vigente" nota={`Predio ${predio.predioId}`}>
              <Lectura recurso={ficha} espera="">
                {(r) => {
                  const suya = r.contenido.find((f) => f.codRefCatastral === predio.codRefCatastral);
                  if (!suya) {
                    return (
                      <div style={{ padding: '14px 16px' }}>
                        <Aviso tono="warn" titulo="Este predio no tiene ficha vigente hoy">
                          La grilla de fichas contesta y no trae ninguna con este codigo a la fecha de hoy. Un predio
                          inscrito sin ficha no tiene area, ni uso, ni construcciones que valorizar.
                        </Aviso>
                      </div>
                    );
                  }
                  return (
                    <Rejilla>
                      <Dato rotulo="Ficha">{suya.fichaId}</Dato>
                      <Dato rotulo="Tipo de ficha">{suya.tipo}</Dato>
                      <Dato rotulo="Version">{suya.version}</Dato>
                      <Dato rotulo="Area de terreno">{suya.areaTerreno}</Dato>
                      <Dato rotulo="Area construida">{suya.areaConstruida}</Dato>
                      <Dato rotulo="Uso">{suya.uso}</Dato>
                      <Dato rotulo="Vigente desde">{suya.vigenciaDesde}</Dato>
                      <Dato rotulo="Titular">{suya.titular}</Dato>
                    </Rejilla>
                  );
                }}
              </Lectura>
            </Seccion>
          ) : null}

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
        </div>
      </div>
    </>
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

export function Territorio({ ruta, onSujeto }: PantallaProps) {
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
            {ubigeos.length === 1 ? `Distrito ${ubigeos[0]}` : 'Sectores y catalogo vial'}
          </p>
          <div style={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
            <Lectura recurso={sectores} espera="">
              {(r) => (
                <>
                  {r.contenido.map((s) => (
                    <NodoDelArbol
                      key={s.id}
                      label={`Sector ${s.codigo} — ${s.nombre}`}
                      conteo={s.lotes === null ? '—' : `${s.lotes} lotes`}
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
              padding: '13px 18px',
              background: 'var(--blanco)',
              borderBottom: '1px solid var(--linea)',
            }}
          >
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

          {esVias ? (
            <CatalogoVial vias={vias} />
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
  conteo: string;
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
 * El catalogo vial, con el arancel del ejercicio de cada via.
 *
 * El arancel sale de OTRA lectura y se cruza por `viaId`. Cuando una via tiene
 * mas de un arancel —el cuadro publica tramos— **no se elige ninguno**: se dice
 * cuantos hay. Elegir uno seria inventar el tramo en el que esta el predio, que
 * es el defecto que #8 midio en el backend.
 */
function CatalogoVial({ vias }: { vias: Recurso<RespuestaPaginada<api.Via>> }) {
  return (
    <Lectura recurso={vias} espera="">
      {(r) => (
        <TablaFija
          columnas={[
            { label: 'Codigo' },
            { label: 'Via' },
            { label: 'Tipo' },
            { label: 'Ubigeo' },
            { label: 'Activa' },
          ]}
          filas={r.contenido.map((v) => ({
            llave: String(v.id),
            celdas: [
              { texto: v.codigo },
              { texto: v.nombre },
              { texto: v.tipo },
              { texto: guion(v.ubigeo) },
              {
                texto: v.activa ? <Insignia tono="ok">Si</Insignia> : <Insignia tono="bad">No</Insignia>,
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

/* ══════════ Valores del ejercicio ══════════════════════════════════════ */

/** Una casilla de una matriz: la cifra sola, «—», o cuantas filas hay. */
function casilla(valores: readonly string[]): Celda {
  if (valores.length === 0) return { texto: '—', numerica: true };
  if (valores.length === 1) return { texto: valores[0], numerica: true };
  return { texto: `${valores.length} filas`, numerica: true };
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
                    { texto: via ? `${via.codigo} — ${via.nombre}` : `Via ${a.viaId}` },
                    { texto: via ? via.tipo : '—' },
                    { texto: a.tramo ?? 'Sin tramo' },
                    { texto: a.valorM2, numerica: true },
                    { texto: a.documentoFuente },
                  ],
                };
              })}
              vacio="El conjunto sellado de este ejercicio no trae ningun arancel de terreno."
              pie={`${cuadro.pie} ${MOTIVOS.arancelSinZona} ${VALORES.noSeSellaAqui}`}
            />
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
