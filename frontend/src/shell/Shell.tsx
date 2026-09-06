import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { Icono } from '../ds/Icono';
import { ICO } from '../ds/iconos';
import { DESTINOS, MODULOS, destinoDe } from './modulos';
import type { Modulo } from './modulos';
import type { Ruta } from './ruta';

/**
 * El armazon: barra global, panel de modulos, pestanas y barra de titulo.
 *
 * Portado de `CatastroV6.dc.html` con sus estilos en linea y sus textos letra
 * por letra. **El conmutador de tres variantes del artboard no viaja**: su
 * propio comentario dice «no es parte del producto», y el panel es la variante
 * A, el acordeon con chevron.
 */

const EJERCICIOS = ['2026', '2025', '2024', '2023'] as const;

export type Pestania = { modulo: string; hoja: string };

export type ShellProps = {
  ruta: Ruta;
  entidad: string;
  ejercicio: string;
  onEjercicio: (v: string) => void;
  pestanas: readonly Pestania[];
  onIr: (modulo: string, hoja: string) => void;
  onCerrar: (modulo: string, hoja: string) => void;
  titulo: string;
  subtitulo: string;
  /**
   * La pantalla se dibuja a sangre: sin el margen del `<main>` y sin su
   * desplazamiento, porque lleva el suyo.
   *
   * Lo piden los dos maestro-detalle del artboard y la hoja de cuadros, que
   * ocupan el alto entero. Es aditivo: sin la bandera, el `<main>` sigue siendo
   * exactamente el que era.
   */
  aSangre?: boolean;
  children: ReactNode;
};

export function Shell(props: ShellProps) {
  const [panelAbierto, setPanelAbierto] = useState(true);
  const [filtro, setFiltro] = useState('');
  const [desplegado, setDesplegado] = useState<string | null>(props.ruta.modulo || MODULOS[0]!.k);
  const [paleta, setPaleta] = useState(false);
  const [lanzador, setLanzador] = useState(false);
  const [sesion, setSesion] = useState(false);
  const [aviso, setAviso] = useState(true);

  /* Al navegar, el modulo de destino se despliega: si no, se abre una pestana
     cuyo submodulo no se ve en el arbol y el panel parece no haber reaccionado. */
  useEffect(() => {
    if (props.ruta.modulo) setDesplegado(props.ruta.modulo);
  }, [props.ruta.modulo]);

  const cerrarTodo = useCallback(() => {
    setPaleta(false);
    setLanzador(false);
    setSesion(false);
  }, []);

  useEffect(() => {
    const alTeclear = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setLanzador(false);
        setSesion(false);
        setPaleta((x) => !x);
      } else if (e.key === 'Escape') {
        cerrarTodo();
      }
    };
    window.addEventListener('keydown', alTeclear);
    return () => window.removeEventListener('keydown', alTeclear);
  }, [cerrarTodo]);

  const ir = useCallback(
    (modulo: string, hoja: string) => {
      cerrarTodo();
      props.onIr(modulo, hoja);
    },
    [cerrarTodo, props],
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden', background: 'var(--fondo)' }}>
      {lanzador ? <Lanzador alCerrar={() => setLanzador(false)} actual={props.ruta.modulo} ir={ir} /> : null}
      {paleta ? <Paleta alCerrar={() => setPaleta(false)} ir={ir} /> : null}

      <BarraGlobal
        entidad={props.entidad}
        panelAbierto={panelAbierto}
        alternarPanel={() => {
          setPanelAbierto((x) => !x);
          cerrarTodo();
        }}
        ejercicio={props.ejercicio}
        onEjercicio={props.onEjercicio}
        avisoAbierto={aviso}
        verAviso={() => setAviso((x) => !x)}
        abrirPaleta={() => {
          cerrarTodo();
          setPaleta(true);
        }}
        lanzador={lanzador}
        abrirLanzador={() => {
          setPaleta(false);
          setSesion(false);
          setLanzador((x) => !x);
        }}
        sesion={sesion}
        alternarSesion={() => {
          setPaleta(false);
          setLanzador(false);
          setSesion((x) => !x);
        }}
        cerrarSesion={() => setSesion(false)}
      />

      <div style={{ flex: 1, display: 'flex', minHeight: 0, overflow: 'hidden' }}>
        {panelAbierto ? (
          <Panel
            filtro={filtro}
            setFiltro={setFiltro}
            desplegado={desplegado}
            setDesplegado={setDesplegado}
            ruta={props.ruta}
            abiertas={props.pestanas}
            ir={ir}
          />
        ) : null}

        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          <Pestanas
            pestanas={props.pestanas}
            activa={props.ruta}
            ir={ir}
            cerrar={(m, h) => {
              cerrarTodo();
              props.onCerrar(m, h);
            }}
          />

          {props.pestanas.length > 0 ? (
            <div
              style={{
                flex: '0 0 auto',
                display: 'flex',
                alignItems: 'center',
                gap: 14,
                flexWrap: 'wrap',
                padding: '0 16px',
                minHeight: 'var(--barra-titulo)',
                background: 'var(--blanco)',
                borderBottom: '1px solid var(--linea)',
              }}
            >
              <span style={{ flex: 1, minWidth: 0, display: 'flex', alignItems: 'baseline', gap: 9 }}>
                <h1
                  style={{
                    margin: 0,
                    fontSize: 16.5,
                    fontWeight: 700,
                    letterSpacing: '-.01em',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {props.titulo}
                </h1>
                <span
                  data-sm-hide="1"
                  style={{
                    fontSize: 13,
                    color: 'var(--tinta-3)',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {props.subtitulo}
                </span>
              </span>
            </div>
          ) : null}

          {aviso ? (
            <div
              role="status"
              style={{
                flex: '0 0 auto',
                display: 'flex',
                alignItems: 'flex-start',
                gap: 12,
                padding: '11px 16px',
                background: 'var(--warn-fondo)',
                borderBottom: '1px solid #E8C86A',
              }}
            >
              <span style={{ color: '#8A5B00', marginTop: 1 }}>
                <Icono d={ICO.aviso} tam={18} grosor={2} />
              </span>
              <p style={{ margin: 0, flex: 1, fontSize: 13.5, lineHeight: 1.5, color: '#4A3200', textWrap: 'pretty' }}>
                Esta interfaz lee de un <strong>proxy de datos</strong> y no del backend: los predios que ensena
                son los de demostracion de Catacaos y ninguno existe. Se apaga con{' '}
                <code>VITE_CATASTRO_PROXY_DE_DATOS=false</code>, y entonces ninguna pantalla ensena una cifra
                hasta que su ruta pase a «servidas.ts».
              </p>
              <button
                type="button"
                onClick={() => setAviso(false)}
                aria-label="Descartar el aviso"
                style={{ border: 0, background: 'transparent', padding: 2, cursor: 'pointer', color: '#8A5B00', flex: '0 0 auto' }}
              >
                <Icono d={ICO.cruz} tam={16} grosor={2.1} />
              </button>
            </div>
          ) : null}

          <main
            style={
              props.aSangre
                ? {
                    flex: 1,
                    minHeight: 0,
                    display: 'flex',
                    overflow: 'hidden',
                    animation: 'fadeIn .22s ease',
                  }
                : { flex: 1, overflow: 'auto', padding: 18, animation: 'fadeIn .22s ease' }
            }
          >
            {props.pestanas.length === 0 ? <SinPestanas /> : props.children}
          </main>
        </div>
      </div>
    </div>
  );
}

/* ── La barra global ────────────────────────────────────────────────────── */

const BOTON_DE_BARRA: CSSProperties = {
  display: 'grid',
  placeItems: 'center',
  width: 32,
  height: 32,
  border: '1px solid rgba(255,255,255,.2)',
  borderRadius: 6,
  background: 'rgba(255,255,255,.09)',
  color: '#fff',
  cursor: 'pointer',
  flex: '0 0 auto',
};

function BarraGlobal(p: {
  entidad: string;
  panelAbierto: boolean;
  alternarPanel: () => void;
  ejercicio: string;
  onEjercicio: (v: string) => void;
  avisoAbierto: boolean;
  verAviso: () => void;
  abrirPaleta: () => void;
  lanzador: boolean;
  abrirLanzador: () => void;
  sesion: boolean;
  alternarSesion: () => void;
  cerrarSesion: () => void;
}) {
  return (
    <header
      style={{
        flex: '0 0 auto',
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        height: 'var(--barra-global)',
        padding: '0 12px 0 8px',
        background: 'var(--azul-osc)',
        zIndex: 50,
      }}
    >
      {/* Muestra u oculta el arbol de modulos, que EMPUJA el contenido en lugar
          de taparlo: el panel es un hermano en la fila, no una capa encima. */}
      <button
        type="button"
        onClick={p.alternarPanel}
        aria-label="Mostrar u ocultar los modulos"
        aria-expanded={p.panelAbierto}
        title={p.panelAbierto ? 'Ocultar los modulos' : 'Mostrar los modulos'}
        className="hov-claro"
        style={{
          display: 'grid',
          placeItems: 'center',
          width: 38,
          height: 38,
          border: 0,
          borderRadius: 8,
          background: p.panelAbierto ? 'rgba(255,255,255,.16)' : 'transparent',
          color: '#fff',
          cursor: 'pointer',
          flex: '0 0 auto',
        }}
      >
        <Icono d={ICO.barras} tam={19} grosor={1.9} />
      </button>

      <span style={{ display: 'flex', alignItems: 'center', gap: 10, flex: '1 1 auto', minWidth: 0 }}>
        <Escudo />
        <span style={{ flex: '1 1 auto', minWidth: 0, lineHeight: 1.15 }}>
          <span
            style={{
              display: 'block',
              fontSize: 14.5,
              fontWeight: 700,
              color: '#fff',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {p.entidad}
          </span>
          <span data-sm-hide="1" style={{ display: 'block', fontSize: 10.5, color: 'var(--sobre-azul)' }}>
            Catastro — predio, ficha y territorio
          </span>
        </span>
      </span>

      <button
        type="button"
        onClick={p.verAviso}
        aria-label="1 aviso del sistema"
        title="1 aviso del sistema"
        className="hov-claro"
        style={{ ...BOTON_DE_BARRA, position: 'relative' }}
      >
        <Icono d={ICO.campana} tam={17} />
        <span
          style={{
            position: 'absolute',
            top: -5,
            right: -5,
            minWidth: 17,
            height: 17,
            padding: '0 4px',
            borderRadius: 999,
            display: 'grid',
            placeItems: 'center',
            fontSize: 10.5,
            fontWeight: 700,
            background: 'var(--contador)',
            color: '#fff',
            border: '1.5px solid var(--azul-osc)',
          }}
        >
          1
        </span>
      </button>

      {/* El ejercicio es global a la sesion: lo declara asi el artboard, y aqui
          ademas decide de que conjunto sellado se leen los cuadros. */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 7,
          border: '1px solid rgba(255,255,255,.2)',
          borderRadius: 6,
          padding: '3px 4px 3px 10px',
          background: 'rgba(255,255,255,.09)',
          flex: '0 0 auto',
        }}
      >
        <span
          data-sm-hide="1"
          style={{
            fontSize: 11,
            fontWeight: 600,
            textTransform: 'uppercase',
            letterSpacing: '.08em',
            color: 'var(--sobre-azul)',
          }}
        >
          Ejercicio
        </span>
        <select
          value={p.ejercicio}
          onChange={(e) => p.onEjercicio(e.target.value)}
          aria-label="Ejercicio de trabajo"
          style={{
            border: 0,
            background: 'var(--azul-suave)',
            color: 'var(--info-tinta)',
            borderRadius: 4,
            padding: '3px 7px',
            fontSize: 13,
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          {EJERCICIOS.map((a) => (
            <option key={a} value={a}>
              {a}
            </option>
          ))}
        </select>
      </div>

      <button
        type="button"
        onClick={p.abrirPaleta}
        aria-label="Buscar"
        title="Buscar — Ctrl K"
        className="hov-claro"
        style={BOTON_DE_BARRA}
      >
        <Icono d={ICO.lupa} tam={16} />
      </button>

      <button
        type="button"
        onClick={p.abrirLanzador}
        aria-label="Ver todos los modulos"
        aria-expanded={p.lanzador}
        title="Todos los modulos"
        style={{
          display: 'grid',
          placeItems: 'center',
          width: 40,
          height: 38,
          border: 0,
          borderRadius: 8,
          cursor: 'pointer',
          color: '#fff',
          flex: '0 0 auto',
          background: p.lanzador ? 'rgba(255,255,255,.2)' : 'transparent',
        }}
      >
        <svg width="19" height="19" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          {[0, 1, 2].flatMap((fila) =>
            [0, 1, 2].map((columna) => (
              <circle key={`${fila}-${columna}`} cx={6 + columna * 6} cy={6 + fila * 6} r={1.9} />
            )),
          )}
        </svg>
      </button>

      <MenuDeSesion abierto={p.sesion} alternar={p.alternarSesion} cerrar={p.cerrarSesion} />
    </header>
  );
}

/**
 * El escudo de la entidad.
 *
 * Es un **marcador de posicion dibujado**, y el propio artboard lo declara asi:
 * «El escudo es un marcador de posicion: si me envian el archivo del logo, lo
 * cambio por la imagen real». Se dibuja en vez de referenciar el
 * `escudo-catacaos.png` del artboard porque ese archivo no existe en este
 * repositorio, y un `<img>` roto es peor que una forma neutra: parece que falta
 * un despliegue.
 */
function Escudo() {
  return (
    <svg
      width="30"
      height="36"
      viewBox="0 0 30 36"
      role="img"
      aria-label="Escudo de la municipalidad (marcador de posicion)"
      style={{ display: 'block', flex: '0 0 auto' }}
    >
      <path
        d="M15 1.5 28 5.4v13.1C28 26.2 22.4 31.6 15 34.5 7.6 31.6 2 26.2 2 18.5V5.4z"
        fill="rgba(255,255,255,.12)"
        stroke="var(--sobre-azul)"
        strokeWidth="1.6"
      />
      <path d="M9 15h12M9 20h12M15 10.5v14" stroke="var(--sobre-azul)" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

function MenuDeSesion(p: { abierto: boolean; alternar: () => void; cerrar: () => void }) {
  const opciones = [
    { label: 'Mi perfil', icono: ICO.persona, salida: false },
    { label: 'Cambiar contrasena', icono: ICO.candado, salida: false },
    { label: 'Cerrar sesion', icono: ICO.salir, salida: true },
  ];
  return (
    <div style={{ position: 'relative', borderLeft: '1px solid rgba(255,255,255,.18)', paddingLeft: 12, flex: '0 0 auto' }}>
      <button
        type="button"
        onClick={p.alternar}
        aria-expanded={p.abierto}
        aria-label="Sesion de V. Reto Santos"
        className="hov-claro"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          border: 0,
          borderRadius: 7,
          padding: '3px 6px 3px 3px',
          cursor: 'pointer',
          background: p.abierto ? 'rgba(255,255,255,.16)' : 'transparent',
        }}
      >
        <span
          style={{
            width: 27,
            height: 27,
            borderRadius: '50%',
            background: 'var(--azul-suave)',
            color: 'var(--info-tinta)',
            display: 'grid',
            placeItems: 'center',
            fontSize: 11.5,
            fontWeight: 700,
            flex: '0 0 auto',
          }}
        >
          VR
        </span>
        <span data-sm-hide="1" style={{ lineHeight: 1.2, textAlign: 'left' }}>
          <span style={{ display: 'block', fontSize: 12.5, fontWeight: 600, color: '#fff' }}>V. Reto Santos</span>
          <span style={{ display: 'block', fontSize: 10.5, color: 'var(--sobre-azul)' }}>Tecnico catastral</span>
        </span>
        <span
          style={{
            display: 'grid',
            placeItems: 'center',
            width: 15,
            height: 15,
            flex: '0 0 auto',
            color: 'var(--sobre-azul)',
            transform: `rotate(${p.abierto ? 180 : 0}deg)`,
            transition: 'transform .15s ease',
          }}
        >
          <Icono d={ICO.chevron} tam={13} grosor={2.1} />
        </span>
      </button>

      {p.abierto ? (
        <>
          <div onClick={p.cerrar} style={{ position: 'fixed', inset: 0, zIndex: 84 }} />
          <div
            role="menu"
            aria-label="Sesion"
            style={{
              position: 'absolute',
              zIndex: 85,
              top: 44,
              right: 0,
              width: 'min(258px,calc(100vw - 24px))',
              background: '#fff',
              border: '1px solid var(--linea)',
              borderRadius: 9,
              boxShadow: '0 16px 42px rgba(0,54,90,.22)',
              overflow: 'hidden',
              animation: 'pop .13s ease',
            }}
          >
            <div style={{ padding: '13px 14px', borderBottom: '1px solid var(--linea-2)' }}>
              <p style={{ margin: 0, fontSize: 13.5, fontWeight: 700 }}>V. Reto Santos</p>
              <p style={{ margin: 0, fontSize: 11.5, color: 'var(--tinta-3)' }}>Tecnico catastral</p>
            </div>
            <div style={{ padding: 5 }}>
              {opciones.map((o) => (
                <button
                  key={o.label}
                  type="button"
                  role="menuitem"
                  onClick={p.cerrar}
                  className="hov-suave"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    width: '100%',
                    border: 0,
                    borderRadius: 6,
                    padding: 9,
                    cursor: 'pointer',
                    background: 'transparent',
                    color: o.salida ? 'var(--bad-tinta)' : 'var(--tinta-2)',
                    fontWeight: o.salida ? 600 : 400,
                  }}
                >
                  <Icono d={o.icono} tam={15} />
                  <span style={{ flex: 1, minWidth: 0, textAlign: 'left', fontSize: 13.5 }}>{o.label}</span>
                </button>
              ))}
            </div>
            {/* La sesion no la sirve este backend: ADR-0030 §3 la pone en
                `rentas`, y aqui no hay ningun endpoint de «quien soy». Decirlo
                es mejor que dibujar tres opciones que no hacen nada. */}
            <p
              style={{
                margin: 0,
                padding: '10px 14px',
                borderTop: '1px solid var(--linea-2)',
                background: 'var(--warn-fondo)',
                fontSize: 12,
                lineHeight: 1.5,
                color: 'var(--warn-tinta)',
                textWrap: 'pretty',
              }}
            >
              La sesion y los permisos viven en «rentas» (ADR-0030 §3). Este sistema no publica ningun endpoint
              de «quien soy», asi que estas tres opciones todavia no llevan a ninguna parte.
            </p>
          </div>
        </>
      ) : null}
    </div>
  );
}

/* ── El panel: variante A, acordeon con chevron ─────────────────────────── */

function Panel(p: {
  filtro: string;
  setFiltro: (v: string) => void;
  desplegado: string | null;
  setDesplegado: (v: string | null) => void;
  ruta: Ruta;
  abiertas: readonly Pestania[];
  ir: (modulo: string, hoja: string) => void;
}) {
  const q = p.filtro.trim().toLowerCase();
  const casaModulo = (m: Modulo) => m.label.toLowerCase().includes(q);
  const hojasQueCasan = (m: Modulo) =>
    q === '' || casaModulo(m) ? m.hojas : m.hojas.filter((h) => h.label.toLowerCase().includes(q));
  const visibles = MODULOS.filter((m) => q === '' || casaModulo(m) || hojasQueCasan(m).length > 0);

  /* Se cuenta en cada dibujo y no se memoriza: son seis modulos y dieciseis
     hojas, y un `useMemo` cuyas dependencias hay que silenciar cuesta mas de lo
     que ahorra. */
  const conteo = (() => {
    if (q === '') return '';
    const mods = visibles.length;
    const hojas = visibles.reduce((n, m) => n + hojasQueCasan(m).length, 0);
    if (mods === 0) return 'Sin coincidencias';
    return `${mods} ${mods === 1 ? 'modulo' : 'modulos'} · ${hojas} ${hojas === 1 ? 'submodulo' : 'submodulos'}`;
  })();

  return (
    <aside
      aria-label="Modulos y submodulos"
      style={{
        flex: '0 0 var(--panel)',
        width: 'var(--panel)',
        background: 'var(--blanco)',
        borderRight: '1px solid var(--linea)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      <div style={{ flex: '0 0 auto', padding: '10px 11px', borderBottom: '1px solid var(--linea-2)' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 7,
            border: '1px solid var(--linea)',
            borderRadius: 6,
            padding: '6px 9px',
            background: 'var(--sup)',
          }}
        >
          <span style={{ color: 'var(--tinta-3)', display: 'grid' }}>
            <Icono d={ICO.lupa} tam={14} />
          </span>
          <input
            value={p.filtro}
            onChange={(e) => p.setFiltro(e.target.value)}
            placeholder="Filtrar modulos y submodulos"
            aria-label="Filtrar modulos y submodulos"
            style={{ flex: 1, minWidth: 0, border: 0, background: 'transparent', fontSize: 12.5, outline: 'none' }}
          />
          {p.filtro !== '' ? (
            <button
              type="button"
              onClick={() => p.setFiltro('')}
              aria-label="Quitar el filtro"
              style={{ border: 0, background: 'transparent', padding: 0, cursor: 'pointer', color: 'var(--tinta-4)', flex: '0 0 auto' }}
            >
              <Icono d={ICO.cruz} tam={13} grosor={2.2} />
            </button>
          ) : null}
        </div>
        {conteo ? <p style={{ margin: '7px 2px 0', fontSize: 11, color: 'var(--tinta-3)' }}>{conteo}</p> : null}
      </div>

      {visibles.length === 0 ? (
        <div style={{ flex: 1, display: 'grid', placeItems: 'center', padding: '24px 18px' }}>
          <p
            style={{
              margin: 0,
              fontSize: 13,
              lineHeight: 1.55,
              color: 'var(--tinta-3)',
              textAlign: 'center',
              textWrap: 'pretty',
            }}
          >
            Ningun modulo ni submodulo se llama asi. Pruebe con «predio», «ficha», «hallazgo» o «zonificacion».
          </p>
        </div>
      ) : (
        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', overflow: 'auto' }}>
          <div style={{ padding: '7px 7px 10px', display: 'flex', flexDirection: 'column', gap: 1 }}>
            {visibles.map((m) => {
              const abierto = q !== '' ? true : p.desplegado === m.k;
              const nAbiertas = p.abiertas.filter((t) => t.modulo === m.k).length;
              return (
                <div key={m.k}>
                  <button
                    type="button"
                    onClick={() => p.setDesplegado(p.desplegado === m.k ? null : m.k)}
                    aria-expanded={abierto}
                    className="hov-suave"
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      width: '100%',
                      border: 0,
                      borderRadius: 7,
                      padding: '7px 8px',
                      cursor: 'pointer',
                      background: abierto ? 'var(--azul-suave)' : 'transparent',
                      color: abierto ? 'var(--info-tinta)' : 'var(--tinta)',
                    }}
                  >
                    <span
                      style={{
                        display: 'grid',
                        placeItems: 'center',
                        width: 24,
                        height: 24,
                        borderRadius: 6,
                        flex: '0 0 auto',
                        background: abierto ? 'var(--azul)' : 'var(--fondo)',
                        color: abierto ? '#fff' : 'var(--tinta-3)',
                      }}
                    >
                      <Icono d={m.icono} tam={14} />
                    </span>
                    <span
                      style={{
                        flex: 1,
                        minWidth: 0,
                        textAlign: 'left',
                        fontSize: 13.5,
                        fontWeight: 600,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {m.label}
                    </span>
                    {nAbiertas > 0 ? (
                      <span
                        style={{
                          fontSize: 9.5,
                          fontWeight: 700,
                          borderRadius: 999,
                          padding: '1px 6px',
                          flex: '0 0 auto',
                          background: abierto ? '#fff' : 'var(--azul-suave)',
                          color: 'var(--info-tinta)',
                        }}
                      >
                        {nAbiertas}
                      </span>
                    ) : null}
                    <span
                      style={{
                        display: 'grid',
                        placeItems: 'center',
                        width: 16,
                        height: 16,
                        flex: '0 0 auto',
                        color: 'var(--tinta-4)',
                        transform: `rotate(${abierto ? 180 : 0}deg)`,
                        transition: 'transform .16s ease',
                      }}
                    >
                      <Icono d={ICO.chevron} tam={13} grosor={2.1} />
                    </span>
                  </button>

                  {abierto ? (
                    <div
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 1,
                        padding: '2px 0 7px 15px',
                        marginLeft: 16,
                        borderLeft: '1px solid var(--linea-2)',
                      }}
                    >
                      {hojasQueCasan(m).map((h) => {
                        const activa = p.ruta.modulo === m.k && p.ruta.destino === h.k;
                        const yaAbierta = p.abiertas.some((t) => t.modulo === m.k && t.hoja === h.k);
                        return (
                          <button
                            key={h.k}
                            type="button"
                            onClick={() => p.ir(m.k, h.k)}
                            aria-current={activa ? 'page' : undefined}
                            className="hov-suave"
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 8,
                              width: '100%',
                              border: 0,
                              borderRadius: 6,
                              padding: '7px 9px',
                              cursor: 'pointer',
                              background: activa ? 'var(--azul-suave)' : 'transparent',
                              color: activa ? 'var(--info-tinta)' : 'var(--tinta-2)',
                              fontWeight: activa ? 700 : 400,
                            }}
                          >
                            <span style={{ flex: 1, minWidth: 0, textAlign: 'left', fontSize: 13 }}>{h.label}</span>
                            {yaAbierta && !activa ? (
                              <span
                                style={{
                                  fontSize: 9.5,
                                  fontWeight: 700,
                                  textTransform: 'uppercase',
                                  letterSpacing: '.06em',
                                  color: 'var(--tinta-3)',
                                  flex: '0 0 auto',
                                }}
                              >
                                abierta
                              </span>
                            ) : null}
                          </button>
                        );
                      })}
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </aside>
  );
}

/* ── Las pestanas ───────────────────────────────────────────────────────── */

function Pestanas(p: {
  pestanas: readonly Pestania[];
  activa: Ruta;
  ir: (modulo: string, hoja: string) => void;
  cerrar: (modulo: string, hoja: string) => void;
}) {
  return (
    <div
      style={{
        flex: '0 0 auto',
        display: 'flex',
        alignItems: 'stretch',
        padding: '0 12px 0 0',
        minHeight: 'var(--pestanas)',
        background: 'var(--fondo)',
        borderBottom: '1px solid var(--linea)',
        zIndex: 40,
        overflowX: 'auto',
      }}
    >
      {p.pestanas.map((t) => {
        const destino = destinoDe(t.modulo, t.hoja);
        if (!destino) return null;
        const activa = p.activa.modulo === t.modulo && p.activa.destino === t.hoja;
        return (
          <span
            key={`${t.modulo}/${t.hoja}`}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 2,
              flex: '0 0 auto',
              padding: '0 6px 0 0',
              borderRight: '1px solid var(--linea-2)',
              background: activa ? 'var(--blanco)' : 'transparent',
              borderTop: `2px solid ${activa ? 'var(--azul)' : 'transparent'}`,
            }}
          >
            <button
              type="button"
              onClick={() => p.ir(t.modulo, t.hoja)}
              aria-current={activa ? 'page' : undefined}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                border: 0,
                background: 'transparent',
                padding: '11px 8px 11px 13px',
                cursor: 'pointer',
                fontSize: 13.5,
                color: activa ? 'var(--tinta)' : 'var(--tinta-3)',
                fontWeight: activa ? 700 : 400,
              }}
            >
              <span style={{ display: 'grid', placeItems: 'center', flex: '0 0 auto', color: activa ? 'var(--azul)' : 'var(--tinta-3)' }}>
                <Icono d={destino.modulo.icono} tam={13} />
              </span>
              <span style={{ whiteSpace: 'nowrap' }}>{destino.hoja.label}</span>
            </button>
            <button
              type="button"
              onClick={() => p.cerrar(t.modulo, t.hoja)}
              aria-label={`Cerrar ${destino.hoja.label}`}
              title={`Cerrar ${destino.hoja.label}`}
              className="hov-linea"
              style={{
                display: 'grid',
                placeItems: 'center',
                width: 22,
                height: 22,
                border: 0,
                borderRadius: 5,
                background: 'transparent',
                cursor: 'pointer',
                color: activa ? 'var(--tinta-3)' : 'var(--tinta-4)',
                flex: '0 0 auto',
              }}
            >
              <Icono d={ICO.cruz} tam={13} grosor={2.2} />
            </button>
          </span>
        );
      })}
      <span style={{ flex: 1, minWidth: 8 }} />
    </div>
  );
}

function SinPestanas() {
  return (
    <div style={{ flex: 1, display: 'grid', placeItems: 'center', padding: 32 }}>
      <div style={{ maxWidth: '40ch', textAlign: 'center' }}>
        <span style={{ color: 'var(--tinta-4)', display: 'inline-grid' }}>
          <Icono d={ICO.vacio} tam={30} grosor={1.5} />
        </span>
        <p style={{ margin: '12px 0 0', fontSize: 16, fontWeight: 700 }}>No hay ningun submodulo abierto</p>
        <p style={{ margin: '7px 0 0', fontSize: 13.5, lineHeight: 1.55, color: 'var(--tinta-3)', textWrap: 'pretty' }}>
          Elija uno en el menu de la izquierda y se abrira como pestana. Puede tener varios abiertos y moverse
          entre ellos.
        </p>
      </div>
    </div>
  );
}

/* ── El lanzador de modulos ─────────────────────────────────────────────── */

function Lanzador(p: { alCerrar: () => void; actual: string; ir: (m: string, h: string) => void }) {
  return (
    <>
      <div onClick={p.alCerrar} style={{ position: 'fixed', inset: 0, zIndex: 80 }} />
      <div
        role="dialog"
        aria-label="Modulos del sistema"
        style={{
          position: 'fixed',
          zIndex: 81,
          top: 50,
          left: 10,
          width: 'min(560px,calc(100vw - 20px))',
          background: '#fff',
          border: '1px solid var(--linea)',
          borderRadius: 10,
          boxShadow: '0 18px 48px rgba(0,54,90,.2)',
          overflow: 'hidden',
          animation: 'pop .14s ease',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'baseline',
            gap: 10,
            padding: '14px 16px 12px',
            borderBottom: '1px solid var(--linea-2)',
          }}
        >
          <p style={{ margin: 0, flex: 1, fontSize: 15, fontWeight: 700 }}>Modulos</p>
          <p style={{ margin: 0, fontSize: 12.5, color: 'var(--tinta-3)' }}>Los seis de este sistema</p>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(168px,1fr))', padding: 6 }}>
          {MODULOS.map((m) => {
            const actual = m.k === p.actual;
            return (
              <button
                key={m.k}
                type="button"
                onClick={() => p.ir(m.k, m.hojas[0]!.k)}
                aria-current={actual ? 'true' : undefined}
                className="hov-suave"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  width: '100%',
                  border: 0,
                  borderRadius: 8,
                  padding: 10,
                  cursor: 'pointer',
                  background: actual ? 'var(--azul-suave)' : 'transparent',
                  color: 'var(--tinta)',
                  fontWeight: actual ? 700 : 400,
                }}
              >
                <span
                  style={{
                    display: 'grid',
                    placeItems: 'center',
                    width: 32,
                    height: 32,
                    borderRadius: 8,
                    flex: '0 0 auto',
                    background: actual ? 'var(--azul)' : 'var(--azul-suave)',
                    color: actual ? '#fff' : 'var(--info-tinta)',
                  }}
                >
                  <Icono d={m.icono} tam={17} />
                </span>
                <span style={{ flex: 1, minWidth: 0, textAlign: 'left', fontSize: 13.5 }}>{m.label}</span>
              </button>
            );
          })}
        </div>
      </div>
    </>
  );
}

/* ── La paleta de comandos ──────────────────────────────────────────────── */

/**
 * Se opera **solo con el teclado**, y hay quien lo comprueba (`yarn paleta`).
 *
 * Esto ya se rompio una vez en el precedente: la paleta se abria con Ctrl-K, se
 * tecleaba para filtrar, y sin flechas ni Intro no habia forma de elegir nada —el
 * atajo llevaba a un callejon—. Quien navega con teclado se quedaba fuera.
 */
function Paleta(p: { alCerrar: () => void; ir: (m: string, h: string) => void }) {
  const [q, setQ] = useState('');
  const [activo, setActivo] = useState(0);
  const caja = useRef<HTMLInputElement>(null);

  useEffect(() => caja.current?.focus(), []);

  const resultados = useMemo(() => {
    const busca = q.trim().toLowerCase();
    return DESTINOS.filter((d) => busca === '' || d.label.toLowerCase().includes(busca));
  }, [q]);

  /* Al filtrar, el foco vuelve al primero: si no, se queda en una fila que
     nadie eligio y Intro abre algo que ya no se esta mirando. */
  useEffect(() => setActivo(0), [q]);

  const alTeclear = (e: React.KeyboardEvent) => {
    if (resultados.length === 0) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActivo((i) => Math.min(i + 1, resultados.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActivo((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Home') {
      e.preventDefault();
      setActivo(0);
    } else if (e.key === 'End') {
      e.preventDefault();
      setActivo(resultados.length - 1);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const elegido = resultados[activo];
      if (elegido) p.ir(elegido.modulo, elegido.hoja);
    }
  };

  return (
    <>
      <div onClick={p.alCerrar} style={{ position: 'fixed', inset: 0, zIndex: 82, background: 'rgba(22,35,44,.38)' }} />
      <div
        role="dialog"
        aria-label="Buscar"
        style={{
          position: 'fixed',
          zIndex: 83,
          top: '12vh',
          left: '50%',
          transform: 'translateX(-50%)',
          width: 'min(620px,92vw)',
          background: '#fff',
          border: '1px solid var(--linea)',
          borderRadius: 10,
          boxShadow: '0 22px 56px rgba(0,54,90,.26)',
          overflow: 'hidden',
          animation: 'pop .14s ease',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '14px 16px', borderBottom: '1px solid var(--linea-2)' }}>
          <span style={{ color: 'var(--tinta-3)', display: 'grid' }}>
            <Icono d={ICO.lupa} tam={18} />
          </span>
          <input
            ref={caja}
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={alTeclear}
            placeholder="Un modulo, un submodulo, una pantalla…"
            aria-label="Buscar un destino"
            aria-controls="paleta-resultados"
            aria-activedescendant={resultados[activo] ? `paleta-${activo}` : undefined}
            style={{ flex: 1, border: 0, background: 'transparent', padding: '2px 0', fontSize: 16, outline: 'none' }}
          />
          <kbd style={{ fontSize: 11, color: 'var(--tinta-4)', border: '1px solid var(--linea)', borderRadius: 4, padding: '2px 6px' }}>
            Esc
          </kbd>
        </div>
        <div id="paleta-resultados" role="listbox" aria-label="Destinos" style={{ maxHeight: '54vh', overflow: 'auto' }}>
          {resultados.map((r, i) => (
            <button
              key={`${r.modulo}/${r.hoja}`}
              id={`paleta-${i}`}
              type="button"
              role="option"
              aria-selected={i === activo}
              onClick={() => p.ir(r.modulo, r.hoja)}
              onMouseEnter={() => setActivo(i)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                width: '100%',
                textAlign: 'left',
                border: 0,
                borderBottom: '1px solid var(--linea-2)',
                background: i === activo ? '#EFF7FC' : 'transparent',
                padding: '11px 16px',
                cursor: 'pointer',
              }}
            >
              <span
                style={{
                  fontSize: 10.5,
                  fontWeight: 700,
                  textTransform: 'uppercase',
                  letterSpacing: '.07em',
                  color: 'var(--tinta-3)',
                  background: 'var(--sup)',
                  border: '1px solid var(--linea)',
                  borderRadius: 4,
                  padding: '2px 7px',
                  flex: '0 0 auto',
                }}
              >
                Pantalla
              </span>
              <span
                style={{
                  flex: 1,
                  minWidth: 0,
                  fontSize: 14.5,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {r.label}
              </span>
            </button>
          ))}
          {resultados.length === 0 ? (
            <p style={{ margin: 0, padding: '18px 16px', fontSize: 13, color: 'var(--tinta-3)', textAlign: 'center' }}>
              Nada se llama asi. Pruebe con «predio», «ficha», «hallazgo» o «zonificacion».
            </p>
          ) : null}
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            gap: 10,
            padding: '9px 16px',
            background: 'var(--sup)',
            fontSize: 12,
            color: 'var(--tinta-3)',
          }}
        >
          <span>
            {resultados.length} {resultados.length === 1 ? 'resultado' : 'resultados'}
          </span>
          <span>Ctrl K</span>
        </div>
      </div>
    </>
  );
}
