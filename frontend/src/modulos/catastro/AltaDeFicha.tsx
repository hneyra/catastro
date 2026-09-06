import { useMemo, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import * as api from '../../api/catastro';
import { ErrorDeApi } from '../../api/cliente';
import { useRebote, useRecurso } from '../../api/useRecurso';
import { Aviso, Insignia, Lectura } from '../../ds/componentes';
import {
  ALTA,
  MOTIVOS_DEL_ALTA,
  PASOS,
  ROTULO_DEL_CAMPO,
  ROTULO_DE_CONDICION_DEL_TITULAR,
  ROTULO_DE_ESTADO_DE_CONSERVACION,
  ROTULO_DE_MATERIAL,
  ROTULO_DE_ORIENTACION,
  ROTULO_DE_ORIGEN,
  ROTULO_DE_TIPO_DE_FICHA,
  ROTULO_DE_TIPO_DE_PREDIO,
} from '../../datos/alta';
import type { CampoDelPaso, PasoDelAlta } from '../../datos/alta';

/**
 * El alta de una ficha: el asistente de seis pasos del artboard (#34).
 *
 * Es **la unica escritura que `CatastroV6.dc.html` dibuja**, y la primera de
 * esta interfaz. Se abre desde la accion primaria de la lista de Predios y vive
 * dentro de ella —`#/catastro/predios/nuevo`—, como en el artboard: el asistente
 * ocupa el panel de detalle y la lista se queda al lado.
 *
 * <h2>Lo que el borrador NO hace</h2>
 *
 * No se guarda en ningun sitio hasta registrar. El artboard dice «el borrador se
 * guarda al avanzar» y aqui no hay donde: no existe ninguna ruta de borrador, y
 * un `localStorage` seria una ficha a medio hacer que sobrevive a la sesion y a
 * la municipalidad, en el navegador de quien la dejo. Se dice en el pie en vez
 * de fingirlo.
 *
 * <h2>Los tres desenlaces se tratan por separado, porque son tres trabajos</h2>
 *
 * **422** se arregla en esta pantalla —el servidor nombra el campo, y aqui se
 * senala y se dice en que paso esta—. **409** dice que el codigo ya esta
 * inscrito: no se arregla corrigiendo nada, se cambia el codigo o se actualiza
 * la ficha que ya existe, que es otra operacion. **404** dice que la via, el
 * sector o la manzana no existen todavia, y eso se arregla en Territorio, no
 * aqui. Ensenar los tres igual dejaria a quien atiende probando lo mismo otra
 * vez, que es lo unico que ninguno de los tres arregla.
 */

/* ── El borrador ────────────────────────────────────────────────────────── */

/** Un piso declarado, con todo en texto: se convierte al componer la peticion. */
type PisoDelBorrador = {
  piso: string;
  areaConstruida: string;
  anio: string;
  material: string;
  estadoConservacion: string;
  categorias: Record<string, string>;
};

const PISO_VACIO: PisoDelBorrador = {
  piso: '',
  areaConstruida: '',
  anio: '',
  material: '',
  estadoConservacion: '',
  categorias: {},
};

/** El estado del asistente. No sale de esta pantalla. */
type Borrador = {
  tramos: Record<string, string>;
  vals: Record<string, string>;
  pisos: PisoDelBorrador[];
  linderos: Record<string, string>;
  via: api.Via | null;
};

const BORRADOR_VACIO: Borrador = {
  tramos: {},
  vals: { tipoFicha: 'UNICA', tipoPredio: 'URBANO' },
  pisos: [],
  linderos: {},
  via: null,
};

/* ── Estilos del artboard ───────────────────────────────────────────────── */

const CAMPO: CSSProperties = {
  width: '100%',
  boxSizing: 'border-box',
  border: '1px solid var(--borde-campo)',
  borderRadius: 6,
  padding: '9px 10px',
  background: '#fff',
  fontSize: 14,
  fontFamily: 'inherit',
};

const CAMPO_MAL: CSSProperties = {
  ...CAMPO,
  border: '1px solid var(--bad-borde)',
  background: '#FFF9F8',
};

const ROTULO: CSSProperties = { fontSize: 12.5, fontWeight: 600, color: 'var(--tinta-2)' };

const AYUDA: CSSProperties = {
  display: 'block',
  fontSize: 12,
  lineHeight: 1.45,
  color: 'var(--tinta-3)',
  marginTop: 5,
  textWrap: 'pretty',
};

/* ── La composicion del codigo ──────────────────────────────────────────── */

/**
 * Los ocho tramos, con su `maxlength`, su `aria-label` y su marcador espaciado.
 *
 * `[data-tramo]::placeholder` —el punto medio repetido, con `letter-spacing`—
 * es del artboard y esta en `global.css`: es lo que hace que un tramo vacio se
 * lea como «faltan tres digitos» y no como un campo estrecho cualquiera.
 */
function CodigoPorTramos({
  tramos,
  onTramo,
  duplicado,
}: {
  tramos: Record<string, string>;
  onTramo: (k: string, v: string) => void;
  duplicado: boolean;
}) {
  const compuesto = api.componerCodigo(tramos);
  const tecleado = api.COMPOSICION_DEL_CODIGO.tramos.map((t) => tramos[t.k] ?? '').join('');
  const listos = api.COMPOSICION_DEL_CODIGO.tramos.filter((t) => (tramos[t.k] ?? '').length === t.digitos).length;
  const completo = listos === api.COMPOSICION_DEL_CODIGO.tramos.length;

  return (
    <div
      style={{
        flex: '0 0 auto',
        padding: '12px 18px',
        background: 'var(--sup)',
        borderBottom: '1px solid var(--linea)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap', marginBottom: 9 }}>
        <p style={{ margin: 0, fontSize: 12.5, fontWeight: 700 }}>{ALTA.codigo}</p>
        <p style={{ margin: 0, flex: 1, minWidth: 180, fontSize: 12.5, color: 'var(--tinta-3)', textWrap: 'pretty' }}>
          {ALTA.codigoNota}
        </p>
        <Insignia tono={duplicado ? 'bad' : completo ? 'ok' : 'warn'}>
          {duplicado
            ? ALTA.codigoUsado
            : completo
              ? ALTA.codigoCompleto
              : `${listos} de ${api.COMPOSICION_DEL_CODIGO.tramos.length} ${ALTA.codigoIncompleto}`}
        </Insignia>
      </div>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 7, flexWrap: 'wrap' }}>
        {api.COMPOSICION_DEL_CODIGO.tramos.map((t) => {
          const valor = tramos[t.k] ?? '';
          return (
            <label key={t.k} style={{ display: 'block' }}>
              <span
                style={{
                  display: 'block',
                  fontSize: 10.5,
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  letterSpacing: '.06em',
                  color: 'var(--tinta-3)',
                  marginBottom: 4,
                }}
              >
                {t.label}
              </span>
              <input
                data-tramo="1"
                value={valor}
                onChange={(e) => onTramo(t.k, e.target.value.replace(/[^0-9]/g, '').slice(0, t.digitos))}
                placeholder={'·'.repeat(t.digitos)}
                maxLength={t.digitos}
                aria-label={`${t.label}, ${t.digitos} digitos`}
                style={{
                  width: t.digitos * 13 + 22,
                  boxSizing: 'border-box',
                  border: `1px solid ${valor.length === t.digitos ? 'var(--borde-campo)' : 'var(--warn-borde)'}`,
                  borderRadius: 5,
                  padding: '8px 6px',
                  background: '#fff',
                  fontSize: 14.5,
                  textAlign: 'center',
                  letterSpacing: '.03em',
                  fontVariantNumeric: 'tabular-nums',
                }}
              />
            </label>
          );
        })}
        <span style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: 6, paddingBottom: 2 }}>
          <code
            style={{
              fontSize: 14.5,
              fontWeight: 700,
              letterSpacing: '.05em',
              color: 'var(--info-tinta)',
              background: 'var(--azul-suave)',
              borderRadius: 5,
              padding: '7px 11px',
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {tecleado === '' ? '—' : compuesto}
          </code>
          <span style={{ fontSize: 12, color: 'var(--tinta-3)' }}>
            {tecleado.length}/{api.LARGO_DEL_CODIGO}
          </span>
        </span>
      </div>
      {duplicado || !completo ? (
        <p
          style={{
            margin: '9px 0 0',
            padding: '9px 11px',
            borderLeft: `3px solid ${duplicado ? 'var(--bad-borde)' : 'var(--acento)'}`,
            borderRadius: '0 5px 5px 0',
            background: duplicado ? 'var(--bad-fondo)' : 'var(--azul-suave)',
            fontSize: 13,
            lineHeight: 1.5,
            color: duplicado ? 'var(--bad-tinta)' : 'var(--tinta-2)',
            textWrap: 'pretty',
          }}
        >
          {duplicado ? ALTA.codigoDuplicado : ALTA.codigoAyuda}
        </p>
      ) : null}
    </div>
  );
}

/* ── Los pendientes, paso a paso ────────────────────────────────────────── */

/**
 * Si un campo hay que rellenar antes de registrar.
 *
 * **Solo lo que viaja puede ser obligatorio.** Un campo que el cuerpo no admite
 * no puede impedir el alta: bloquearia el registro por un dato que nadie va a
 * recibir, y quien lo rellenara no conseguiria nada con ello. Los que quedan son
 * los que `DeclaracionDeFicha.exigir` reclama con un 422 nombrandolos.
 */
function obligatorio(campo: CampoDelPaso): boolean {
  return campo.viaja !== null && campo.opcional !== true;
}

function vacio(valor: string | undefined): boolean {
  return valor === undefined || valor.trim() === '';
}

export type PendienteDelPaso = { paso: PasoDelAlta; cuantos: number; que: string[] };

/**
 * Que le falta a cada paso, por su nombre.
 *
 * No vale un unico «faltan datos»: el asistente tiene seis pasos y solo se ve
 * uno, asi que un boton que diga «faltan tres» sin decir donde manda a mirarlos
 * uno a uno. Lo que se devuelve es la lista, y de ahi salen las tres cosas que
 * la pantalla necesita: el numerito de cada pestana, el `title` del boton
 * primario y el aviso del resumen.
 */
function faltan(borrador: Borrador): PendienteDelPaso[] {
  return PASOS.map((paso) => {
    const que: string[] = [];
    for (const campo of paso.campos) {
      if (!obligatorio(campo)) continue;
      if (vacio(borrador.vals[campo.k])) que.push(campo.label);
    }
    /* Los tres que no son campos de la rejilla y aun asi el servidor exige. */
    if (paso.id === 'ubic' && borrador.via === null) que.push(ALTA.viaElegida);
    if (paso.id === 'titular' && !vacio(borrador.vals.contrib)) {
      /* El bloque del titular es opcional entero; en cuanto viaja, el servidor
         exige sus tres campos con `exigir` y con un `null` explicito. */
      if (vacio(borrador.vals.partida)) que.push('Partida registral');
      if (vacio(borrador.vals.condTit)) que.push('Condicion del titular');
    }
    return { paso, cuantos: que.length, que };
  });
}

/* ── La peticion ────────────────────────────────────────────────────────── */

/**
 * El tipo de via como se escribe en una direccion: `CALLE` -> `Calle`.
 *
 * No se traduce ni se abrevia —el artboard escribe «CA Bolivar 539», con una
 * abreviatura que ningun catalogo publica—: se pone en caja de titulo el valor
 * que el catalogo trae, que es lo que hace que la direccion compuesta se lea
 * igual que las que el padron ya tiene («Calle Comercio 245»).
 */
function enCajaDeTitulo(texto: string): string {
  return texto.charAt(0) + texto.slice(1).toLowerCase();
}

/** Lo que se va a mandar, o `null` si algo esencial falta todavia. */
function componerPeticion(borrador: Borrador): api.PeticionDeAlta {
  const v = borrador.vals;
  const via = borrador.via;
  const numero = (v.numMun ?? '').trim();
  const linderos = api.ORIENTACIONES.filter((o) => !vacio(borrador.linderos[o])).map((o) => ({
    orientacion: o,
    descripcion: (borrador.linderos[o] ?? '').trim(),
  }));
  const esRural = v.tipoFicha === 'RURAL';

  const construcciones: api.ConstruccionDeclarada[] = borrador.pisos.map((p) => {
    const anio = p.anio.trim();
    return {
      piso: p.piso.trim(),
      areaConstruida: p.areaConstruida.trim(),
      anioConstruccion: anio === '' ? undefined : Number(anio),
      material: p.material === '' ? undefined : p.material,
      estadoConservacion: p.estadoConservacion === '' ? undefined : p.estadoConservacion,
      ...Object.fromEntries(
        api.CATEGORIAS_CONSTRUCTIVAS.filter((c) => (p.categorias[c.k] ?? '') !== '').map((c) => [
          c.k,
          p.categorias[c.k],
        ]),
      ),
    };
  });

  const titular: api.TitularDeclarado | undefined = vacio(v.contrib)
    ? undefined
    : {
        codigoContribuyente: v.contrib!.trim(),
        condicion: v.condTit === '' ? undefined : v.condTit,
        porcentaje: vacio(v.porcProp) ? undefined : v.porcProp!.trim(),
        documentoOrigen: vacio(v.partida) ? undefined : v.partida!.trim(),
      };

  /* Con ningun tramo tecleado, el codigo NO viaja: componerlo daria una tira de
     ceros, que es un codigo valido de otro predio y no «todavia nada». El
     resumen lo ensenaria como si fuera lo que se va a mandar. */
  const tecleado = api.COMPOSICION_DEL_CODIGO.tramos.map((t) => borrador.tramos[t.k] ?? '').join('');

  return {
    observacion: vacio(v.observacion) ? undefined : v.observacion!.trim(),
    codRefCatastral: tecleado === '' ? undefined : api.componerCodigo(borrador.tramos),
    tipoPredio: v.tipoPredio,
    /* La compone la via del catalogo y el numero municipal, igual que el
       artboard compone el titulo de la ficha. El servidor la EXIGE y el issue no
       la lista: `predioDeclarado` la lee con `exigir(peticion.direccion(),
       "direccion")`, asi que sin ella el alta es un 422 en el primer campo que
       el servidor mira. */
    direccion: via === null ? undefined : `${enCajaDeTitulo(via.tipo)} ${via.nombre}${numero === '' ? '' : ` ${numero}`}`,
    codigoDeVia: via?.codigo,
    numeroMunicipal: numero === '' ? undefined : numero,
    /* Los tramos que el usuario NO teclea no viajan. Mandar «00» porque el
       relleno de ceros lo produce seria afirmar que el predio esta en el sector
       cero, y el servidor contestaria 404 buscandolo. */
    codigoDeSector: vacio(borrador.tramos.sector) ? undefined : borrador.tramos.sector,
    codigoDeManzana: vacio(borrador.tramos.manzana) ? undefined : borrador.tramos.manzana,
    lote: vacio(borrador.tramos.lote) ? undefined : borrador.tramos.lote,
    ubigeo: vacio(borrador.tramos.ubigeo) ? undefined : borrador.tramos.ubigeo,
    areaTerreno: vacio(v.areaTerreno) ? undefined : v.areaTerreno!.trim(),
    uso: vacio(v.uso) ? undefined : v.uso,
    denominacion: vacio(v.denominacion) ? undefined : v.denominacion!.trim(),
    vigenciaDesde: vacio(v.fecha) ? undefined : v.fecha,
    origen: vacio(v.fuente) ? undefined : v.fuente,
    documentoOrigen: vacio(v.documentoOrigen) ? undefined : v.documentoOrigen!.trim(),
    construcciones,
    /* El artboard no dibuja ningun paso de obras complementarias, asi que van
       vacias y se dice. En un alta una lista ausente y una vacia son lo mismo
       —no hay version anterior de la que copiar—, asi que se manda la vacia para
       que el resumen pueda ensenar que no va ninguna. */
    instalaciones: [],
    rural: esRural && linderos.length > 0 ? { colindantes: linderos } : undefined,
    titular,
  };
}

/** Como se lee en el resumen lo que un campo de la peticion lleva dentro. */
function comoSeLee(campo: string, valor: unknown): string {
  if (Array.isArray(valor)) return String(valor.length);
  if (valor !== null && typeof valor === 'object') {
    if (campo === 'titular') {
      const t = valor as api.TitularDeclarado;
      return [t.codigoContribuyente, t.condicion ? ROTULO_DE_CONDICION_DEL_TITULAR[t.condicion] : null, t.porcentaje]
        .filter((x) => x)
        .join(' · ');
    }
    const r = valor as api.RuralDeclarado;
    return String(r.colindantes?.length ?? 0);
  }
  const texto = String(valor);
  if (campo === 'origen') return ROTULO_DE_ORIGEN[texto] ?? texto;
  if (campo === 'tipoPredio') return ROTULO_DE_TIPO_DE_PREDIO[texto] ?? texto;
  return texto;
}

/* ── Los desenlaces del envio ───────────────────────────────────────────── */

/**
 * Los campos que el servidor senala en un 422.
 *
 * `ManejadorDeErrores` publica `detalles` **solo cuando el rechazo trae
 * cifras**, asi que la ausencia no es un cero: es «este rechazo no publica
 * nada». Los `ProblemaDeNegocio` del alta nombran el campo **dentro del
 * mensaje** —`Falta el campo 'documentoOrigen'`—, asi que se miran los dos: los
 * detalles primero, y el mensaje despues.
 */
function camposSenalados(error: ErrorDeApi): string[] {
  const textos = [...(error.detalles ?? []), error.mensaje];
  const nombres = new Set<string>();
  for (const texto of textos) {
    for (const casa of texto.matchAll(/'([A-Za-z][A-Za-z.]*)'/g)) nombres.add(casa[1]!);
  }
  return [...nombres].filter((n) => CAMPO_POR_NOMBRE.has(n));
}

/** De `documentoOrigen` al campo del asistente que lo llena, y a su paso. */
const CAMPO_POR_NOMBRE: ReadonlyMap<string, { campo: CampoDelPaso; paso: PasoDelAlta }> = new Map(
  PASOS.flatMap((paso) =>
    paso.campos.filter((c) => c.viaja !== null).map((campo) => [campo.viaja!, { campo, paso }] as const),
  ),
);

/* ── El asistente ───────────────────────────────────────────────────────── */

export function AltaDeFicha({
  paso,
  onPaso,
  onDescartar,
  onRegistrada,
}: {
  /** El identificador del paso abierto. Viaja en la ruta: el asistente es enlazable. */
  paso: string;
  onPaso: (id: string) => void;
  onDescartar: () => void;
  /** Lo que se hace con la ficha recien creada: abrir su predio en la lista. */
  onRegistrada: (ficha: api.Ficha) => void;
}) {
  const [borrador, setBorrador] = useState<Borrador>(BORRADOR_VACIO);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<ErrorDeApi | null>(null);
  /**
   * El codigo que el servidor rechazo con un 409, y **por que se recuerda**.
   *
   * El artboard compara con una constante suya —`CODIGO_EN_USO`— y sabe el
   * duplicado antes de preguntar. Aqui no se puede: el padron es del servidor y
   * comprobarlo desde la pantalla seria una segunda lectura que no vale para el
   * instante siguiente. Lo que si se puede es **no volver a mandar lo que ya se
   * rechazo**: mientras el codigo tecleado siga siendo ese, el boton primario no
   * esta disponible y dice por que.
   */
  const [codigoRechazado, setCodigoRechazado] = useState<string | null>(null);

  const indice = Math.max(
    0,
    PASOS.findIndex((p) => p.id === paso),
  );
  const actual = PASOS[indice]!;
  const ultimo = indice === PASOS.length - 1;

  const pendientes = useMemo(() => faltan(borrador), [borrador]);
  const peticion = useMemo(() => componerPeticion(borrador), [borrador]);

  const tecleado = api.COMPOSICION_DEL_CODIGO.tramos.map((t) => borrador.tramos[t.k] ?? '').join('');
  const codigoListo = api.COMPOSICION_DEL_CODIGO.tramos.every(
    (t) => (borrador.tramos[t.k] ?? '').length === t.digitos,
  );
  const codigo = api.componerCodigo(borrador.tramos);
  const duplicado = codigoRechazado !== null && codigoRechazado === codigo;
  const totalPendiente = pendientes.reduce((a, p) => a + p.cuantos, 0);

  /**
   * Por que el boton primario no esta disponible, con el paso de cada cosa.
   *
   * Nunca vacio cuando el boton esta apagado: un `disabled` mudo es la unica
   * pieza de una interfaz que no puede explicarse a si misma, y lo comprueba
   * `yarn impedimentos` sobre toda la pantalla.
   */
  const motivo = duplicado
    ? ALTA.codigoDuplicado
    : !codigoListo
      ? `Falta completar los ${api.COMPOSICION_DEL_CODIGO.tramos.length} tramos del codigo de referencia catastral.`
      : totalPendiente > 0
        ? pendientes
            .filter((p) => p.cuantos > 0)
            .map((p) => `${p.paso.label}: ${p.que.join(', ')}`)
            .join(' · ')
        : '';
  const puede = motivo === '';

  const senalados = error !== null && error.codigo === 'VALIDACION' ? camposSenalados(error) : [];
  const senaladosPorK = new Set(senalados.map((n) => CAMPO_POR_NOMBRE.get(n)!.campo.k));

  const fijar = (k: string, v: string) =>
    setBorrador((b) => ({ ...b, vals: { ...b.vals, [k]: v } }));

  const registrar = () => {
    if (!puede || enviando) return;
    setEnviando(true);
    setError(null);
    api
      .inscribirFicha(borrador.vals.tipoFicha as api.TipoDeFicha, peticion)
      .then((ficha) => {
        setEnviando(false);
        onRegistrada(ficha);
      })
      .catch((fallo: unknown) => {
        setEnviando(false);
        const real =
          fallo instanceof ErrorDeApi ? fallo : new ErrorDeApi('SIN_RESPUESTA', 'No se pudo registrar la ficha', 0);
        setError(real);
        /* El 409 es el unico que deja el boton apagado despues: los otros dos se
           arreglan cambiando algo, y volver a intentar tiene sentido. */
        if (real.codigo === 'CONFLICTO') setCodigoRechazado(codigo);
      });
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, overflow: 'hidden' }}>
      <Cabecera
        codigo={tecleado === '' ? 'Sin codigo' : codigo}
        titular={borrador.vals.contrib ?? ''}
        onDescartar={onDescartar}
      />

      <CodigoPorTramos
        tramos={borrador.tramos}
        duplicado={duplicado}
        onTramo={(k, v) => {
          setBorrador((b) => ({ ...b, tramos: { ...b.tramos, [k]: v } }));
        }}
      />

      <div
        style={{
          flex: '0 0 auto',
          display: 'flex',
          alignItems: 'stretch',
          gap: 2,
          padding: '0 14px',
          background: 'var(--blanco)',
          borderBottom: '1px solid var(--linea)',
          overflowX: 'auto',
        }}
      >
        {PASOS.map((p, i) => {
          const on = i === indice;
          const cuantos = pendientes[i]!.cuantos;
          return (
            <button
              key={p.id}
              type="button"
              onClick={() => onPaso(p.id)}
              aria-current={on ? 'true' : 'false'}
              title={cuantos > 0 ? `${p.label}: falta ${pendientes[i]!.que.join(', ')}` : undefined}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 7,
                border: 0,
                borderBottom: `2px solid ${on ? 'var(--azul)' : 'transparent'}`,
                background: 'transparent',
                padding: '11px 12px 9px',
                cursor: 'pointer',
                fontSize: 13.5,
                whiteSpace: 'nowrap',
                color: on ? 'var(--tinta)' : 'var(--tinta-3)',
                fontWeight: on ? 700 : 400,
              }}
            >
              <span>{p.label}</span>
              {cuantos > 0 ? (
                <span
                  style={{
                    fontSize: 10.5,
                    fontWeight: 700,
                    borderRadius: 999,
                    padding: '1px 6px',
                    background: 'var(--warn-fondo)',
                    color: 'var(--warn-tinta)',
                  }}
                >
                  {cuantos}
                </span>
              ) : null}
            </button>
          );
        })}
      </div>

      <div style={{ flex: 1, overflow: 'auto', minHeight: 0, padding: '16px 18px 24px' }}>
        <div style={{ maxWidth: 920 }}>
          <p
            style={{
              margin: '0 0 14px',
              fontSize: 13.5,
              lineHeight: 1.6,
              color: 'var(--tinta-2)',
              maxWidth: '74ch',
              textWrap: 'pretty',
            }}
          >
            {actual.nota}
          </p>

          {error !== null ? <ElFallo error={error} senalados={senalados} /> : null}

          <Rejilla>
            {actual.campos.map((campo) => (
              <UnCampo
                key={campo.k}
                campo={campo}
                valor={borrador.vals[campo.k] ?? ''}
                derivado={derivadoDelCodigo(campo.k, borrador)}
                senalado={senaladosPorK.has(campo.k)}
                onCambio={(v) => fijar(campo.k, v)}
              />
            ))}
          </Rejilla>

          {actual.id === 'ubic' ? (
            <LaVia
              elegida={borrador.via}
              onElegir={(via) => setBorrador((b) => ({ ...b, via }))}
            />
          ) : null}

          {actual.id === 'terreno' ? (
            <LosLinderos
              linderos={borrador.linderos}
              rural={borrador.vals.tipoFicha === 'RURAL'}
              onCambio={(o, v) => setBorrador((b) => ({ ...b, linderos: { ...b.linderos, [o]: v } }))}
            />
          ) : null}

          {actual.id === 'const' ? (
            <LosPisos
              pisos={borrador.pisos}
              onCambio={(pisos) => setBorrador((b) => ({ ...b, pisos }))}
            />
          ) : null}

          {ultimo ? <ElResumen peticion={peticion} puede={puede} motivo={motivo} /> : null}

          <LoQueNoViaja paso={actual} />
        </div>
      </div>

      <div
        style={{
          flex: '0 0 auto',
          display: 'flex',
          alignItems: 'center',
          gap: 11,
          flexWrap: 'wrap',
          padding: '11px 18px',
          background: 'var(--blanco)',
          borderTop: '1px solid var(--linea)',
        }}
      >
        <button
          type="button"
          onClick={() => onPaso(PASOS[Math.max(indice - 1, 0)]!.id)}
          disabled={indice === 0}
          title={indice === 0 ? ALTA.primerPaso : undefined}
          style={{
            border: `1px solid ${indice === 0 ? 'var(--linea)' : 'var(--azul)'}`,
            borderRadius: 6,
            padding: '9px 18px',
            background: '#fff',
            fontSize: 13.5,
            fontWeight: 600,
            cursor: indice === 0 ? 'not-allowed' : 'pointer',
            color: indice === 0 ? 'var(--tinta-4)' : 'var(--azul)',
          }}
        >
          {ALTA.anterior}
        </button>
        <p
          style={{
            margin: 0,
            flex: 1,
            minWidth: 170,
            fontSize: 12.5,
            lineHeight: 1.45,
            color: 'var(--tinta-3)',
            textWrap: 'pretty',
          }}
        >
          {ultimo ? (puede ? ALTA.resumenNota : motivo) : ALTA.borradorAlAvanzar}
        </p>
        <button
          type="button"
          onClick={() => (ultimo ? registrar() : onPaso(PASOS[indice + 1]!.id))}
          disabled={ultimo && (!puede || enviando)}
          title={ultimo ? (enviando ? 'Registrando…' : puede ? undefined : motivo) : undefined}
          className="hov-azul"
          style={{
            border: 0,
            borderRadius: 6,
            padding: '10px 22px',
            background: 'var(--azul)',
            color: '#fff',
            fontSize: 13.5,
            fontWeight: 600,
            cursor: ultimo && !puede ? 'not-allowed' : 'pointer',
            opacity: ultimo && (!puede || enviando) ? 0.55 : 1,
          }}
        >
          {ultimo ? ALTA.registrar : ALTA.continuar}
        </button>
      </div>
    </div>
  );
}

/* ── Las piezas ─────────────────────────────────────────────────────────── */

function Cabecera({
  codigo,
  titular,
  onDescartar,
}: {
  codigo: string;
  titular: string;
  onDescartar: () => void;
}) {
  return (
    <div
      style={{
        flex: '0 0 auto',
        padding: '12px 18px',
        background: 'var(--blanco)',
        borderBottom: '1px solid var(--linea)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <span
          style={{
            fontSize: 14,
            fontWeight: 700,
            color: 'var(--azul)',
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          {codigo}
        </span>
        <Insignia tono="warn">{ALTA.estado}</Insignia>
        <span style={{ flex: 1, minWidth: 20 }} />
        <button
          type="button"
          onClick={onDescartar}
          className="hov-borde"
          style={{
            border: '1px solid var(--linea)',
            borderRadius: 6,
            padding: '7px 13px',
            background: '#fff',
            fontSize: 13,
            cursor: 'pointer',
          }}
        >
          {ALTA.descartar}
        </button>
      </div>
      <p style={{ margin: '8px 0 0', fontSize: 17, fontWeight: 700, letterSpacing: '-.015em' }}>{ALTA.titulo}</p>
      <p style={{ margin: '4px 0 0', fontSize: 13.5, lineHeight: 1.5, color: 'var(--tinta-3)', textWrap: 'pretty' }}>
        {titular.trim() === '' ? ALTA.sinTitular : `${titular} · ${ALTA.conTitular}`}
      </p>
    </div>
  );
}

function Rejilla({ children }: { children: ReactNode }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(212px,1fr))', gap: '14px 16px' }}>
      {children}
    </div>
  );
}

/**
 * Los tres campos de solo lectura que salen del codigo y no de un dato.
 *
 * El artboard los rellena con «Piura», «Sullana» y «Sullana», que son datos de
 * su propio prototipo. Aqui no hay ningun catalogo de ubigeo que consultar, asi
 * que se ensenan **los digitos del codigo que el usuario acaba de teclear**:
 * es lo mismo que devuelve `CodigoReferenciaCatastral.ubigeo()`, y no afirma
 * ningun nombre que nadie haya publicado.
 */
function derivadoDelCodigo(k: string, borrador: Borrador): string | null {
  const ubigeo = borrador.tramos.ubigeo ?? '';
  if (k === 'dep') return ubigeo.slice(0, 2);
  if (k === 'prov') return ubigeo.slice(2, 4);
  if (k === 'dist') return ubigeo.slice(4, 6);
  if (k === 'numPisos') return borrador.pisos.length === 0 ? '' : String(borrador.pisos.length);
  return null;
}

function opcionesDelContrato(campo: CampoDelPaso): readonly { valor: string; label: string }[] {
  if (campo.delContrato === 'tipoFicha') {
    return api.TIPOS_DE_FICHA.map((v) => ({ valor: v, label: ROTULO_DE_TIPO_DE_FICHA[v] ?? v }));
  }
  if (campo.delContrato === 'tipoPredio') {
    return api.TIPOS_DE_PREDIO.map((v) => ({ valor: v, label: ROTULO_DE_TIPO_DE_PREDIO[v] ?? v }));
  }
  if (campo.delContrato === 'origen') {
    return api.ORIGENES_DE_FICHA.map((v) => ({ valor: v, label: ROTULO_DE_ORIGEN[v] ?? v }));
  }
  if (campo.delContrato === 'condicionDelTitular') {
    return api.CONDICIONES_DE_TITULARIDAD.map((v) => ({
      valor: v,
      label: ROTULO_DE_CONDICION_DEL_TITULAR[v] ?? v,
    }));
  }
  return (campo.opciones ?? []).map((o) => ({ valor: o, label: o }));
}

function UnCampo({
  campo,
  valor,
  derivado,
  senalado,
  onCambio,
}: {
  campo: CampoDelPaso;
  valor: string;
  derivado: string | null;
  senalado: boolean;
  onCambio: (v: string) => void;
}) {
  const estilo = senalado ? CAMPO_MAL : CAMPO;
  const tipo = campo.tipo ?? 'texto';
  const conVacia = campo.opcional === true || vacio(valor);

  return (
    <label
      style={{
        display: 'block',
        minWidth: 0,
        gridColumn: campo.ancho === true ? '1 / -1' : undefined,
      }}
    >
      <span style={{ display: 'flex', alignItems: 'baseline', gap: 7, marginBottom: 5, flexWrap: 'wrap' }}>
        <span style={ROTULO}>{campo.label}</span>
        {campo.opcional === true ? (
          <span style={{ fontSize: 11.5, color: 'var(--tinta-4)' }}>opcional</span>
        ) : null}
        {campo.viaja === null ? (
          <span
            title={campo.motivo}
            style={{
              fontSize: 10.5,
              fontWeight: 700,
              borderRadius: 999,
              padding: '1px 6px',
              background: 'var(--sup)',
              border: '1px solid var(--linea)',
              color: 'var(--tinta-3)',
            }}
          >
            {ALTA.noViaja}
          </span>
        ) : null}
      </span>

      {tipo === 'ro' ? (
        <span
          style={{
            display: 'block',
            border: '1px dashed var(--borde-campo)',
            borderRadius: 6,
            padding: '9px 10px',
            background: 'var(--sup)',
            fontSize: 13.5,
            color: 'var(--tinta-2)',
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          {derivado === null || derivado === '' ? '—' : derivado}
        </span>
      ) : tipo === 'sel' ? (
        <select value={valor} onChange={(e) => onCambio(e.target.value)} style={estilo}>
          {conVacia ? <option value="" /> : null}
          {opcionesDelContrato(campo).map((o) => (
            <option key={o.valor} value={o.valor}>
              {o.label}
            </option>
          ))}
        </select>
      ) : tipo === 'area' ? (
        <textarea
          value={valor}
          onChange={(e) => onCambio(e.target.value)}
          rows={3}
          placeholder={campo.marcador}
          style={{ ...estilo, resize: 'vertical' }}
        />
      ) : tipo === 'chk' ? (
        <span
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 9,
            border: '1px solid var(--borde-campo)',
            borderRadius: 6,
            padding: '9px 10px',
            background: '#fff',
          }}
        >
          <input
            type="checkbox"
            checked={valor === 'si'}
            onChange={(e) => onCambio(e.target.checked ? 'si' : '')}
            style={{ accentColor: 'var(--azul)', width: 17, height: 17, flex: '0 0 auto', marginTop: 1 }}
          />
          <span style={{ fontSize: 13.5, lineHeight: 1.4, color: 'var(--tinta-2)' }}>{campo.marcador}</span>
        </span>
      ) : (
        <input
          type={tipo === 'fecha' ? 'date' : 'text'}
          value={valor}
          onChange={(e) => onCambio(e.target.value)}
          placeholder={campo.marcador}
          style={estilo}
        />
      )}

      {campo.ayuda ? <span style={AYUDA}>{campo.ayuda}</span> : null}
    </label>
  );
}

/**
 * Lo que este paso recoge y el cuerpo del alta no admite.
 *
 * Va al pie del paso y no al lado de cada campo porque el motivo se repite: con
 * una insignia por campo y su `title` basta para saber cual, y aqui se lee por
 * que. Un formulario que acepta datos que no llegan a ninguna parte es peor que
 * uno que no los pide, y esto es lo que impide que se lea como si llegaran.
 */
function LoQueNoViaja({ paso }: { paso: PasoDelAlta }) {
  const sinSitio = paso.campos.filter((c) => c.viaja === null && c.motivo);
  if (sinSitio.length === 0) return null;
  return (
    <section
      style={{
        marginTop: 18,
        background: 'var(--blanco)',
        border: '1px solid var(--linea)',
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      <div style={{ padding: '11px 14px', borderBottom: '1px solid var(--linea-2)' }}>
        <h3 style={{ margin: 0, fontSize: 13.5, fontWeight: 700 }}>
          {ALTA.noViajanTitulo} · {sinSitio.length}
        </h3>
        <p
          style={{
            margin: '5px 0 0',
            fontSize: 12.5,
            lineHeight: 1.5,
            color: 'var(--tinta-3)',
            maxWidth: '78ch',
            textWrap: 'pretty',
          }}
        >
          {ALTA.noViajanNota}
        </p>
      </div>
      {sinSitio.map((c) => (
        <div
          key={c.k}
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 12,
            padding: '10px 14px',
            borderTop: '1px solid var(--linea-2)',
          }}
        >
          <span style={{ flex: '0 0 210px', fontSize: 13, fontWeight: 600, color: 'var(--tinta-2)' }}>{c.label}</span>
          <span style={{ flex: 1, minWidth: 0, fontSize: 12.5, lineHeight: 1.5, color: 'var(--tinta-3)', textWrap: 'pretty' }}>
            {c.motivo}
          </span>
        </div>
      ))}
    </section>
  );
}

/**
 * La via, del catalogo y no escrita libre.
 *
 * Es lo que pide la nota del paso en el artboard, y no es una preferencia: dos
 * formas de escribir la misma calle producen dos direcciones que nadie cruza. De
 * aqui salen `codigoDeVia` y la mitad de la `direccion` que el servidor exige.
 */
function LaVia({ elegida, onElegir }: { elegida: api.Via | null; onElegir: (via: api.Via | null) => void }) {
  const [texto, setTexto] = useState('');
  const buscado = useRebote(texto);

  const lista = useRecurso(
    (senal) => api.vias({ nombreDeCalle: buscado, activa: true }, { tamano: 20 }, senal),
    ['vias-del-alta', buscado],
    buscado.trim().length >= 2 && elegida === null,
  );

  if (elegida !== null) {
    return (
      <section style={{ marginTop: 18 }}>
        <Aviso tono="ok" titulo={ALTA.viaElegida}>
          <span style={{ fontVariantNumeric: 'tabular-nums' }}>{elegida.codigo}</span> · {elegida.tipo}{' '}
          {elegida.nombre}{' '}
          <button
            type="button"
            onClick={() => onElegir(null)}
            style={{
              border: 0,
              background: 'transparent',
              padding: 0,
              color: 'var(--azul)',
              fontSize: 13,
              textDecoration: 'underline',
              cursor: 'pointer',
            }}
          >
            {ALTA.viaQuitar}
          </button>
        </Aviso>
      </section>
    );
  }

  return (
    <section
      style={{
        marginTop: 18,
        background: 'var(--blanco)',
        border: '1px solid var(--linea)',
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      <div style={{ padding: '11px 14px', borderBottom: '1px solid var(--linea-2)' }}>
        <h3 style={{ margin: '0 0 7px', fontSize: 13.5, fontWeight: 700 }}>{ALTA.viaBuscar}</h3>
        <input
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          placeholder={ALTA.viaMarcador}
          aria-label={ALTA.viaBuscar}
          style={{ ...CAMPO, maxWidth: 380 }}
        />
      </div>
      {buscado.trim().length < 2 ? (
        <p style={{ margin: 0, padding: '18px 14px', fontSize: 13, color: 'var(--tinta-3)', textWrap: 'pretty' }}>
          {ALTA.viaSinBuscar}
        </p>
      ) : (
        <Lectura recurso={lista} espera={ALTA.viaSinBuscar}>
          {(r) =>
            r.contenido.length === 0 ? (
              <p style={{ margin: 0, padding: '18px 14px', fontSize: 13, color: 'var(--tinta-3)' }}>
                {ALTA.viaSinResultados}
              </p>
            ) : (
              <div style={{ maxHeight: 240, overflow: 'auto' }}>
                {r.contenido.map((via) => (
                  <button
                    key={via.id}
                    type="button"
                    onClick={() => onElegir(via)}
                    className="hov-suave"
                    style={{
                      display: 'flex',
                      alignItems: 'baseline',
                      gap: 10,
                      width: '100%',
                      textAlign: 'left',
                      border: 0,
                      borderTop: '1px solid var(--linea-2)',
                      background: 'transparent',
                      padding: '9px 14px',
                      cursor: 'pointer',
                      fontSize: 13.5,
                    }}
                  >
                    <span style={{ fontVariantNumeric: 'tabular-nums', color: 'var(--tinta-3)', fontSize: 12.5 }}>
                      {via.codigo}
                    </span>
                    <span style={{ flex: 1, minWidth: 0 }}>
                      {via.tipo} {via.nombre}
                    </span>
                  </button>
                ))}
              </div>
            )
          }
        </Lectura>
      )}
    </section>
  );
}

/**
 * Los cuatro linderos, que solo viajan en una ficha rural.
 *
 * `ColindanteDeclarado` vive dentro del bloque `rural`, y `exigirDelTipo`
 * rechaza ese bloque en las otras tres clases de ficha con un 422 explicito: no
 * lo ignora, para que declarar linderos en una ficha urbana no acabe en un
 * «registrado» con los linderos en ninguna parte.
 */
function LosLinderos({
  linderos,
  rural,
  onCambio,
}: {
  linderos: Record<string, string>;
  rural: boolean;
  onCambio: (orientacion: string, valor: string) => void;
}) {
  return (
    <section style={{ marginTop: 18 }}>
      <Rejilla>
        {api.ORIENTACIONES.map((o) => (
          <label key={o} style={{ display: 'block', minWidth: 0 }}>
            <span style={{ display: 'flex', alignItems: 'baseline', gap: 7, marginBottom: 5, flexWrap: 'wrap' }}>
              <span style={ROTULO}>{ROTULO_DE_ORIENTACION[o]}</span>
              <span style={{ fontSize: 11.5, color: 'var(--tinta-4)' }}>opcional</span>
              {rural ? null : (
                <span
                  title={MOTIVOS_DEL_ALTA.colindantesSoloRural}
                  style={{
                    fontSize: 10.5,
                    fontWeight: 700,
                    borderRadius: 999,
                    padding: '1px 6px',
                    background: 'var(--sup)',
                    border: '1px solid var(--linea)',
                    color: 'var(--tinta-3)',
                  }}
                >
                  {ALTA.noViaja}
                </span>
              )}
            </span>
            <input
              value={linderos[o] ?? ''}
              onChange={(e) => onCambio(o, e.target.value)}
              style={CAMPO}
              aria-label={ROTULO_DE_ORIENTACION[o]}
            />
          </label>
        ))}
      </Rejilla>
      <p style={{ ...AYUDA, marginTop: 9, maxWidth: '78ch' }}>{MOTIVOS_DEL_ALTA.colindantesSoloRural}</p>
    </section>
  );
}

/** La tabla «Pisos declarados» del artboard, que es lo que viaja como `construcciones`. */
function LosPisos({
  pisos,
  onCambio,
}: {
  pisos: PisoDelBorrador[];
  onCambio: (pisos: PisoDelBorrador[]) => void;
}) {
  const cambiar = (i: number, cambio: Partial<PisoDelBorrador>) =>
    onCambio(pisos.map((p, j) => (i === j ? { ...p, ...cambio } : p)));

  return (
    <section
      style={{
        marginTop: 18,
        background: 'var(--blanco)',
        border: '1px solid var(--linea)',
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '11px 14px', borderBottom: '1px solid var(--linea-2)' }}>
        <h3 style={{ margin: 0, flex: 1, fontSize: 13.5, fontWeight: 700 }}>{ALTA.pisos}</h3>
        <button
          type="button"
          onClick={() => onCambio([...pisos, { ...PISO_VACIO, categorias: {} }])}
          className="hov-borde"
          style={{
            border: '1px solid var(--linea)',
            borderRadius: 5,
            padding: '5px 11px',
            background: '#fff',
            fontSize: 12.5,
            cursor: 'pointer',
          }}
        >
          {ALTA.anadirPiso}
        </button>
      </div>

      {pisos.length === 0 ? (
        <p style={{ margin: 0, padding: '22px 14px', textAlign: 'center', fontSize: 13, color: 'var(--tinta-3)' }}>
          {ALTA.sinPisos}
        </p>
      ) : (
        pisos.map((p, i) => (
          <div key={i} style={{ padding: '12px 14px', borderTop: '1px solid var(--linea-2)' }}>
            <Rejilla>
              <label style={{ display: 'block' }}>
                <span style={{ ...ROTULO, display: 'block', marginBottom: 5 }}>Piso</span>
                <input value={p.piso} onChange={(e) => cambiar(i, { piso: e.target.value })} style={CAMPO} />
              </label>
              <label style={{ display: 'block' }}>
                <span style={{ ...ROTULO, display: 'block', marginBottom: 5 }}>Area construida (m²)</span>
                <input
                  value={p.areaConstruida}
                  onChange={(e) => cambiar(i, { areaConstruida: e.target.value })}
                  style={CAMPO}
                />
              </label>
              <label style={{ display: 'block' }}>
                <span style={{ ...ROTULO, display: 'block', marginBottom: 5 }}>Ano de construccion</span>
                <input
                  value={p.anio}
                  onChange={(e) => cambiar(i, { anio: e.target.value.replace(/[^0-9]/g, '') })}
                  style={CAMPO}
                />
                <span style={AYUDA}>{ALTA.notaDelAnio}</span>
              </label>
              <label style={{ display: 'block' }}>
                <span style={{ ...ROTULO, display: 'block', marginBottom: 5 }}>Material predominante</span>
                <select value={p.material} onChange={(e) => cambiar(i, { material: e.target.value })} style={CAMPO}>
                  <option value="" />
                  {api.MATERIALES.map((m) => (
                    <option key={m} value={m}>
                      {ROTULO_DE_MATERIAL[m] ?? m}
                    </option>
                  ))}
                </select>
              </label>
              <label style={{ display: 'block' }}>
                <span style={{ ...ROTULO, display: 'block', marginBottom: 5 }}>Estado de conservacion</span>
                <select
                  value={p.estadoConservacion}
                  onChange={(e) => cambiar(i, { estadoConservacion: e.target.value })}
                  style={CAMPO}
                >
                  <option value="" />
                  {api.ESTADOS_DE_CONSERVACION.map((e) => (
                    <option key={e} value={e}>
                      {ROTULO_DE_ESTADO_DE_CONSERVACION[e] ?? e}
                    </option>
                  ))}
                </select>
              </label>
            </Rejilla>

            <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, flexWrap: 'wrap', marginTop: 12 }}>
              {api.CATEGORIAS_CONSTRUCTIVAS.map((c) => (
                <label key={c.k} style={{ display: 'block' }}>
                  <span
                    style={{
                      display: 'block',
                      fontSize: 10.5,
                      fontWeight: 600,
                      textTransform: 'uppercase',
                      letterSpacing: '.06em',
                      color: 'var(--tinta-3)',
                      marginBottom: 4,
                    }}
                  >
                    {c.label}
                  </span>
                  <input
                    value={p.categorias[c.k] ?? ''}
                    onChange={(e) =>
                      cambiar(i, {
                        categorias: {
                          ...p.categorias,
                          [c.k]: e.target.value.replace(/[^A-Za-z]/g, '').slice(0, 1).toUpperCase(),
                        },
                      })
                    }
                    maxLength={1}
                    aria-label={`${c.label}, una letra`}
                    style={{
                      width: 46,
                      boxSizing: 'border-box',
                      border: '1px solid var(--borde-campo)',
                      borderRadius: 5,
                      padding: '8px 6px',
                      background: '#fff',
                      fontSize: 14.5,
                      textAlign: 'center',
                    }}
                  />
                </label>
              ))}
              <span style={{ flex: 1, minWidth: 20 }} />
              <button
                type="button"
                onClick={() => onCambio(pisos.filter((_, j) => j !== i))}
                className="hov-borde"
                style={{
                  border: '1px solid var(--linea)',
                  borderRadius: 5,
                  padding: '6px 11px',
                  background: '#fff',
                  fontSize: 12.5,
                  cursor: 'pointer',
                }}
              >
                {ALTA.quitarPiso}
              </button>
            </div>
          </div>
        ))
      )}
      <p
        style={{
          margin: 0,
          padding: '10px 14px',
          borderTop: '1px solid var(--linea-2)',
          background: 'var(--sup)',
          fontSize: 12.5,
          lineHeight: 1.5,
          color: 'var(--tinta-3)',
          textWrap: 'pretty',
        }}
      >
        {ALTA.notaDeLasCategorias}
      </p>
    </section>
  );
}

/**
 * «Lo que se va a registrar»: la peticion ya armada, campo a campo.
 *
 * Se recorre **el objeto que se va a enviar** y no una lista escrita al lado:
 * eso es lo que hace que el resumen no pueda decir una cosa y el cuerpo llevar
 * otra, que es el unico modo de fallo que un resumen tiene.
 */
function ElResumen({
  peticion,
  puede,
  motivo,
}: {
  peticion: api.PeticionDeAlta;
  puede: boolean;
  motivo: string;
}) {
  const entradas = api.CAMPOS_DEL_ALTA.map((campo) => ({
    campo,
    valor: (peticion as Record<string, unknown>)[campo],
  })).filter((e) => e.valor !== undefined);

  return (
    <section
      style={{
        marginTop: 18,
        background: 'var(--blanco)',
        border: '1px solid var(--linea)',
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      <div style={{ padding: '13px 15px', borderBottom: '1px solid var(--linea-2)' }}>
        <h3 style={{ margin: 0, fontSize: 14.5, fontWeight: 700 }}>{ALTA.resumen}</h3>
        <p style={{ margin: '5px 0 0', fontSize: 13, lineHeight: 1.55, color: 'var(--tinta-3)', maxWidth: '72ch', textWrap: 'pretty' }}>
          {ALTA.resumenNota}
        </p>
      </div>
      {entradas.map((e) => (
        <div
          key={e.campo}
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 12,
            padding: '9px 15px',
            borderTop: '1px solid var(--linea-2)',
          }}
        >
          <span style={{ flex: '0 0 220px', fontSize: 13, color: 'var(--tinta-2)' }}>
            {ROTULO_DEL_CAMPO[e.campo] ?? e.campo}
          </span>
          <span
            style={{
              flex: 1,
              minWidth: 0,
              fontSize: 13,
              fontWeight: 600,
              wordBreak: 'break-word',
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {comoSeLee(e.campo, e.valor)}
          </span>
          <code style={{ flex: '0 0 auto', fontSize: 11.5, color: 'var(--tinta-4)' }}>{e.campo}</code>
        </div>
      ))}
      <p
        style={{
          margin: 0,
          padding: '12px 15px',
          borderLeft: `4px solid ${puede ? 'var(--ok-tinta)' : 'var(--bad-borde)'}`,
          background: puede ? 'var(--ok-fondo)' : 'var(--bad-fondo)',
          fontSize: 13,
          lineHeight: 1.55,
          color: puede ? 'var(--ok-tinta)' : 'var(--bad-tinta)',
          textWrap: 'pretty',
        }}
      >
        {puede ? ALTA.resumenNota : `No se puede registrar todavia. ${motivo}`}
      </p>
    </section>
  );
}

/** Los tres desenlaces del envio, cada uno con lo que hay que hacer. */
function ElFallo({ error, senalados }: { error: ErrorDeApi; senalados: string[] }) {
  const donde = senalados
    .map((n) => CAMPO_POR_NOMBRE.get(n)!)
    .map((x) => `«${x.campo.label}», en ${x.paso.label}`);

  const titulo =
    error.codigo === 'CONFLICTO'
      ? ALTA.falloConflicto
      : error.codigo === 'NO_ENCONTRADO'
        ? ALTA.falloNoEncontrado
        : error.codigo === 'VALIDACION'
          ? ALTA.falloValidacion
          : 'No se pudo registrar la ficha';
  const queHacer =
    error.codigo === 'CONFLICTO'
      ? ALTA.falloConflictoQueHacer
      : error.codigo === 'NO_ENCONTRADO'
        ? ALTA.falloNoEncontradoQueHacer
        : error.codigo === 'VALIDACION'
          ? ALTA.falloValidacionQueHacer
          : null;

  return (
    <div style={{ marginBottom: 16 }}>
      <Aviso tono={error.codigo === 'VALIDACION' ? 'warn' : 'bad'} titulo={titulo}>
        <p style={{ margin: 0 }}>{error.mensaje}</p>
        {donde.length > 0 ? <p style={{ margin: '6px 0 0' }}>Hay que corregir {donde.join(' · ')}.</p> : null}
        {error.detalles && error.detalles.length > 0 ? (
          <ul style={{ margin: '6px 0 0', paddingLeft: 18 }}>
            {error.detalles.map((d) => (
              <li key={d}>{d}</li>
            ))}
          </ul>
        ) : null}
        {queHacer ? <p style={{ margin: '6px 0 0' }}>{queHacer}</p> : null}
        {error.incidencia ? <p style={{ margin: '6px 0 0' }}>Incidencia {error.incidencia}</p> : null}
      </Aviso>
    </div>
  );
}
