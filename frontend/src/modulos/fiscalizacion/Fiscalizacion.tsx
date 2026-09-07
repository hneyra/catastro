import type { ReactNode } from 'react';
import type { PantallaProps } from '../../App';
import * as api from '../../api/fiscalizacion';
import { useRecurso } from '../../api/useRecurso';
import { sinClaves } from '../../shell/ruta';
import {
  Aviso,
  Boton,
  Campo,
  Dato,
  Insignia,
  Lectura,
  Rejilla,
  Seccion,
  Servida,
  Tabla,
} from '../../ds/componentes';
/* El formulario de un acto vive en el sistema de diseno desde #72, que trajo el
   segundo modulo que escribe. Lo que era suyo se queda: los rotulos y las notas
   de LOS ACTOS de fiscalizacion, que son los que se le pasan. */
import { Acto } from '../../ds/Acto';
import type { CampoDelActo } from '../../ds/Acto';
import {
  ACTOS,
  CAMPOS,
  COLUMNAS,
  ESPERAS,
  IRREVERSIBLES,
  MOTIVOS,
  NOTAS_DE_LOS_ACTOS,
  PIES,
  SUJETOS,
  TITULOS,
  VACIOS,
} from '../../datos/fiscalizacion';

/**
 * El modulo Fiscalizacion: el ciclo entero, y no solo su lectura (#71).
 *
 * <h2>Que se puede hacer aqui, y de donde sale</h2>
 *
 * `FiscalizacionCatastralController` publica trece operaciones. Hasta #71 esta
 * interfaz declaraba **cuatro** —las cuatro lecturas de campania— y ninguna
 * escritura: un inspector veia la cola de candidatos y no podia admitir ni
 * descartar ninguno; veia los hallazgos y no podia levantar el acta ni dejar
 * ninguno sin efecto. No era un error —la pantalla decia lo que habia— sino que
 * **no habia donde pulsar**, y el trabajo se hacia fuera del sistema.
 *
 * <h2>Lo ofrecible sale del ESTADO de la fila, y no de un menu fijo</h2>
 *
 * `TRANSICIONES_DEL_CANDIDATO` y `TRANSICIONES_DEL_HALLAZGO` viven en
 * `src/api/fiscalizacion.ts` y son `Record` completos sobre los enumerados del
 * backend. Un candidato `DETECTADO` **no ofrece** «Verificar en campo», porque
 * `Candidato.verificadoEnCampo()` exige venir de gabinete: ofrecerlo y recibir
 * un 409 es peor que no ofrecerlo, porque ensena un camino que no existe.
 * `verificaciones/transiciones.mjs` lo mide en el navegador, fila a fila.
 *
 * <h2>Cada escritura exige su observacion, y el primario dice por que espera</h2>
 *
 * RNF-052: sin observacion no se guarda. El boton primario de cada acto nace
 * apagado con su `title` diciendo que falta —nunca un `disabled` mudo, que es la
 * unica pieza de una interfaz que no puede explicarse a si misma— y lo mide
 * `verificaciones/impedimentos.mjs`.
 *
 * <h2>Y NO hay boton de «lanzar deteccion»</h2>
 *
 * Es la decision mas importante de esta pantalla y no se ve: la corrida de
 * deteccion **no es un endpoint** desde #30. Ponerle un boton reintroduciria por
 * la interfaz lo que aquel issue saco del backend, con el agravante de que hoy
 * no fallaria —no hay ni un poligono cargado— y empezaria a fallar el dia de la
 * primera carga cartografica. La pantalla dice donde corre, con el mismo
 * criterio con que dice que no hay listado de campanias.
 */

function numeroDe(texto: string): number | null {
  return /^\d+$/.test(texto) ? Number(texto) : null;
}

/* ── El sujeto que estas pantallas piden a mano ─────────────────────────── */

function CajaDeCampania({
  ruta,
  onSujeto,
  derecha,
}: Pick<PantallaProps, 'ruta' | 'onSujeto'> & { derecha?: ReactNode }) {
  return (
    <Seccion titulo={TITULOS.laCampania} derecha={derecha}>
      <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Campo
          rotulo={SUJETOS.campania.rotulo}
          valor={ruta.sujeto}
          onCambio={onSujeto}
          marcador={SUJETOS.campania.marcador}
          ayuda={SUJETOS.campania.ayuda}
        />
        <Aviso tono="warn" titulo="No hay listado de campanias, y por eso se pide a mano">
          {MOTIVOS.sinListadoDeCampanias}
        </Aviso>
      </div>
    </Seccion>
  );
}

/**
 * Los actos que la fila ofrece, agrupados y **con el estado en el nombre**.
 *
 * Es un `role="group"` con `aria-label` y no un monton de botones sueltos: un
 * grupo de acciones de una fila necesita nombre para poder decirse en voz alta,
 * y de paso es lo que hace medible la regla del AC-2 —`transiciones.mjs` lee
 * cada grupo, saca de su nombre el estado de la fila y compara los botones con
 * `TRANSICIONES_DEL_*`—.
 */
function ActosDeLaFila({
  que,
  id,
  estado,
  actos,
  rotulos,
  onActo,
}: {
  que: string;
  id: number;
  estado: string;
  actos: readonly string[];
  rotulos: Readonly<Record<string, string>>;
  onActo: (acto: string) => void;
}) {
  return (
    <div
      role="group"
      aria-label={`${que} ${id} · ${estado}`}
      style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}
    >
      {actos.length === 0 ? (
        <span style={{ fontSize: 12.5, color: 'var(--tinta-3)' }}>Sin actos</span>
      ) : (
        actos.map((a) => (
          <Boton key={a} onClick={() => onActo(a)}>
            {rotulos[a]}
          </Boton>
        ))
      )}
    </div>
  );
}

/** Los actos que admite un estado, o ninguno si el estado no es del enumerado. */
function actosDe<A extends string>(
  tabla: Readonly<Record<string, readonly A[]>>,
  estado: string,
): readonly A[] {
  return tabla[estado] ?? [];
}

/* ── Campanias: abrirla, mirar su embudo y cerrarla ─────────────────────── */

/** El embudo de una campania: seis cifras y ningun porcentaje. */
export function Campanias({ ruta, onSujeto, onFiltros }: PantallaProps) {
  const campaniaId = numeroDe(ruta.sujeto);
  const tasa = useRecurso(
    (senal) => api.tasaDeDescarte(campaniaId!, senal),
    ['tasa', campaniaId],
    campaniaId !== null,
  );

  const acto = ruta.filtros.acto ?? '';
  const abrir = (k: string) => onFiltros({ ...ruta.filtros, acto: k });
  const cerrarElActo = () => onFiltros(sinClaves(ruta.filtros, ['acto']));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1000 }}>
      <CajaDeCampania
        ruta={ruta}
        onSujeto={onSujeto}
        derecha={
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Boton tipo="primario" onClick={() => abrir('abrirCampania')}>
              {ACTOS.abrirCampania}
            </Boton>
            <Boton
              impedido={campaniaId === null}
              motivo="Falta el identificador de la campania que se va a cerrar."
              onClick={() => abrir('cerrarCampania')}
            >
              {ACTOS.cerrarCampania}
            </Boton>
          </div>
        }
      />

      {acto === 'abrirCampania' ? (
        <Acto
          key="abrirCampania"
          titulo={ACTOS.abrirCampania}
          nota={NOTAS_DE_LOS_ACTOS.abrirCampania}
          campos={[
            { k: 'codigo', rotulo: CAMPOS.codigo.rotulo, ayuda: CAMPOS.codigo.ayuda },
            { k: 'nombre', rotulo: CAMPOS.nombre.rotulo, ayuda: CAMPOS.nombre.ayuda, ancho: 320 },
            { k: 'umbral', rotulo: CAMPOS.umbral.rotulo, ayuda: CAMPOS.umbral.ayuda },
            { k: 'tope', rotulo: CAMPOS.tope.rotulo, ayuda: CAMPOS.tope.ayuda, ancho: 320 },
          ]}
          enviar={(v) =>
            api.abrirCampania({
              codigo: v.codigo!,
              nombre: v.nombre!,
              umbral: v.umbral!,
              tope: Number(v.tope),
              observacion: v.observacion!,
            })
          }
          pinta={(c) => <LaCampania campania={c} />}
          onCerrar={cerrarElActo}
        />
      ) : null}

      {acto === 'cerrarCampania' && campaniaId !== null ? (
        <Acto
          key="cerrarCampania"
          titulo={ACTOS.cerrarCampania}
          nota={NOTAS_DE_LOS_ACTOS.cerrarCampania}
          campos={[]}
          advertencia={IRREVERSIBLES.cierre}
          enviar={(v) => api.cerrarCampania(campaniaId, { observacion: v.observacion! })}
          pinta={(c) => <LaCampania campania={c} />}
          onCerrar={cerrarElActo}
        />
      ) : null}

      <Seccion titulo={TITULOS.elEmbudo} nota={TITULOS.notaDelEmbudo}>
        <Lectura recurso={tasa} espera={ESPERAS.embudo}>
          {(t) => (
            <>
              <Rejilla>
                <Dato rotulo="Detectados">{t.detectados}</Dato>
                <Dato rotulo="Descartados en gabinete">{t.descartadosEnGabinete}</Dato>
                <Dato rotulo="Pasaron gabinete">{t.loQuePasoGabinete}</Dato>
                <Dato rotulo="Descartados en campo">{t.descartadosEnCampo}</Dato>
                <Dato rotulo="Verificados">{t.verificados}</Dato>
                <Dato rotulo="En curso">{t.enCurso}</Dato>
              </Rejilla>
              <p style={PIE}>
                Son cifras y no una tasa, a proposito. Un porcentaje esconde el denominador —la mitad de veinte y
                la mitad de veinte mil no dicen lo mismo— y ademas calcularlo aqui exigiria decidir un modo de
                redondeo que nadie ha decidido (D-03b).
              </p>
            </>
          )}
        </Lectura>
      </Seccion>

      <Aviso tono="info" titulo="La corrida de deteccion no se lanza desde aqui">
        {MOTIVOS.laDeteccionCorreEnBatch}
      </Aviso>

      <Servida
        lee={[api.RUTAS.tasaDeDescarte]}
        escribe={[{ metodo: 'POST', ruta: api.RUTAS.campanias }, { metodo: 'POST', ruta: api.RUTAS.cierre }]}
        falta={MOTIVOS.sinListadoDeCampanias}
      />
    </div>
  );
}

function LaCampania({ campania }: { campania: api.Campania }) {
  return (
    <Rejilla>
      <Dato rotulo="Identificador">{campania.id}</Dato>
      <Dato rotulo="Codigo">{campania.codigo}</Dato>
      <Dato rotulo="Nombre">{campania.nombre}</Dato>
      <Dato rotulo="Estado">
        <Insignia tono={campania.estado === 'ABIERTA' ? 'ok' : 'info'}>{campania.estado}</Insignia>
      </Dato>
      <Dato rotulo="Inicio">{campania.inicio}</Dato>
      <Dato rotulo="Fin">{campania.fin}</Dato>
      <Dato rotulo="Umbral">{campania.umbral}</Dato>
      <Dato rotulo="Tope">{campania.tope}</Dato>
    </Rejilla>
  );
}

const PIE = {
  margin: 0,
  padding: '10px 16px',
  borderTop: '1px solid var(--linea-2)',
  background: 'var(--sup)',
  fontSize: 12.5,
  lineHeight: 1.55,
  color: 'var(--tinta-3)',
  textWrap: 'pretty',
} as const;

/* ── Candidatos: las dos compuertas ─────────────────────────────────────── */

/** Lo detectado, con lo que su estado admite y nada mas. */
export function Candidatos({ ruta, onSujeto, onFiltros }: PantallaProps) {
  const campaniaId = numeroDe(ruta.sujeto);
  const lista = useRecurso(
    (senal) => api.candidatos(campaniaId!, {}, { tamano: 50 }, senal),
    ['candidatos', campaniaId],
    campaniaId !== null,
  );

  const acto = ruta.filtros.acto ?? '';
  const candidatoId = numeroDe(ruta.filtros.candidato ?? '');
  const abrir = (k: string, id: number) => onFiltros({ ...ruta.filtros, acto: k, candidato: String(id) });
  const cerrarElActo = () => onFiltros(sinClaves(ruta.filtros, ['acto', 'candidato']));
  /* Tras un acto, la lista se vuelve a pedir: el estado de la fila lo dice el
     servidor, y quedarse con el de antes ofreceria otra vez lo que se acaba de
     hacer. */
  const hecho = () => lista.reintentar();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <CajaDeCampania ruta={ruta} onSujeto={onSujeto} />

      {candidatoId !== null && esActoDelCandidato(acto) ? (
        <ActoDelCandidato
          acto={acto}
          candidatoId={candidatoId}
          onCerrar={cerrarElActo}
          onHecho={hecho}
        />
      ) : null}

      <Seccion titulo={TITULOS.candidatos}>
        <Lectura recurso={lista} espera={ESPERAS.candidatos}>
          {(r) => (
            <Tabla
              columnas={[
                { label: COLUMNAS.predio, numerica: true, pinta: (c: api.Candidato) => c.predioId ?? '—' },
                { label: COLUMNAS.clase, pinta: (c: api.Candidato) => <Insignia>{c.clase}</Insignia> },
                { label: COLUMNAS.origen, pinta: (c: api.Candidato) => c.origen },
                { label: COLUMNAS.score, numerica: true, pinta: (c: api.Candidato) => c.score },
                {
                  label: COLUMNAS.estado,
                  pinta: (c: api.Candidato) => (
                    <Insignia tono={tonoDelCandidato(c.estado)}>{c.estado}</Insignia>
                  ),
                },
                { label: COLUMNAS.etapaDelDescarte, pinta: (c: api.Candidato) => c.etapaDeDescarte ?? '—' },
                { label: COLUMNAS.motivo, pinta: (c: api.Candidato) => c.motivoDeDescarte ?? '—' },
                {
                  label: COLUMNAS.acciones,
                  pinta: (c: api.Candidato) => (
                    <ActosDeLaFila
                      que="Candidato"
                      id={c.id}
                      estado={c.estado}
                      actos={actosDe(api.TRANSICIONES_DEL_CANDIDATO, c.estado)}
                      rotulos={ACTOS}
                      onActo={(a) => abrir(a, c.id)}
                    />
                  ),
                },
              ]}
              filas={r.contenido}
              llave={(c) => c.id}
              vacio={VACIOS.candidatos}
              pie={`${PIES.candidatos} ${MOTIVOS.sinAtajo} ${MOTIVOS.terminal}`}
            />
          )}
        </Lectura>
      </Seccion>

      <Servida
        lee={[api.RUTAS.candidatos]}
        escribe={[{ metodo: 'POST', ruta: api.RUTAS.gabinete }, { metodo: 'POST', ruta: api.RUTAS.campo }, { metodo: 'POST', ruta: api.RUTAS.descarteEnCampo }]}
        falta="Los candidatos cuelgan de una campania y no hay lectura por predio ni por municipalidad, ni lectura de UNO suelto: sin el identificador de la campania no hay a quien preguntar."
      />
    </div>
  );
}

/**
 * El acto que retiro el hallazgo, con sus tres campos juntos.
 *
 * Los tres o ninguno: `motivoAnulacion`, `anuladoPor` y `anuladoEn` viajan
 * nulos mientras el hallazgo siga firme, y por separado no dicen nada —«quien»
 * sin «por que» es un nombre suelto—.
 */
function LaAnulacion({ hallazgo }: { hallazgo: api.Hallazgo }) {
  if (hallazgo.motivoAnulacion === null && hallazgo.anuladoPor === null) return null;
  return (
    <>
      {COLUMNAS.anulacion}: {hallazgo.motivoAnulacion} · {COLUMNAS.anuladoPor} {hallazgo.anuladoPor} ·{' '}
      {COLUMNAS.anuladoEn} {hallazgo.anuladoEn}
    </>
  );
}

function tonoDelCandidato(estado: string) {
  if (estado === 'DESCARTADO') return 'bad' as const;
  if (estado === 'VERIFICADO_EN_CAMPO') return 'ok' as const;
  return 'warn' as const;
}

function esActoDelCandidato(k: string): k is api.ActoDelCandidato {
  return (api.ACTOS_DEL_CANDIDATO as readonly string[]).includes(k);
}

/**
 * Los cuatro actos de un candidato, cada uno con lo que su caso de uso pide.
 *
 * Los dos descartes mandan el mismo `record` —`PeticionDeCompuerta`— y se
 * distinguen por la ruta y por `admite`: el de gabinete va con `admite: false`
 * a la primera compuerta y el de campo tiene ruta propia, porque el dominio
 * exige haber pasado gabinete antes.
 */
function ActoDelCandidato({
  acto,
  candidatoId,
  onCerrar,
  onHecho,
}: {
  acto: api.ActoDelCandidato;
  candidatoId: number;
  onCerrar: () => void;
  onHecho: () => void;
}) {
  const elMotivo: CampoDelActo = {
    k: 'motivo',
    rotulo: CAMPOS.motivoDelDescarte.rotulo,
    ayuda: CAMPOS.motivoDelDescarte.ayuda,
    ancho: 460,
  };

  if (acto === 'admitirEnGabinete') {
    return (
      <Acto
        key={`${acto}-${candidatoId}`}
        titulo={ACTOS[acto]}
        nota={NOTAS_DE_LOS_ACTOS[acto]}
        campos={[]}
        enviar={(v) => api.enGabinete(candidatoId, { admite: true, observacion: v.observacion! })}
        pinta={(c) => <ElCandidato candidato={c} onHecho={onHecho} />}
        onCerrar={onCerrar}
      />
    );
  }
  if (acto === 'descartarEnGabinete') {
    return (
      <Acto
        key={`${acto}-${candidatoId}`}
        titulo={ACTOS[acto]}
        nota={NOTAS_DE_LOS_ACTOS[acto]}
        campos={[elMotivo]}
        enviar={(v) =>
          api.enGabinete(candidatoId, { admite: false, motivo: v.motivo!, observacion: v.observacion! })
        }
        pinta={(c) => <ElCandidato candidato={c} onHecho={onHecho} />}
        onCerrar={onCerrar}
      />
    );
  }
  if (acto === 'descartarEnCampo') {
    return (
      <Acto
        key={`${acto}-${candidatoId}`}
        titulo={ACTOS[acto]}
        nota={NOTAS_DE_LOS_ACTOS[acto]}
        campos={[elMotivo]}
        enviar={(v) =>
          api.descartarEnCampo(candidatoId, { admite: false, motivo: v.motivo!, observacion: v.observacion! })
        }
        pinta={(c) => <ElCandidato candidato={c} onHecho={onHecho} />}
        onCerrar={onCerrar}
      />
    );
  }
  return (
    <Acto
      key={`${acto}-${candidatoId}`}
      titulo={ACTOS[acto]}
      nota={NOTAS_DE_LOS_ACTOS[acto]}
      campos={[
        { k: 'areaVerificada', rotulo: CAMPOS.areaVerificada.rotulo, ayuda: CAMPOS.areaVerificada.ayuda },
        { k: 'inspector', rotulo: CAMPOS.inspector.rotulo, ayuda: CAMPOS.inspector.ayuda },
      ]}
      enviar={(v) =>
        api.enCampo(candidatoId, {
          areaVerificada: v.areaVerificada!,
          inspector: v.inspector!,
          observacion: v.observacion!,
        })
      }
      pinta={(h) => <ElHallazgo hallazgo={h} onHecho={onHecho} />}
      onCerrar={onCerrar}
    />
  );
}

function ElCandidato({ candidato, onHecho }: { candidato: api.Candidato; onHecho: () => void }) {
  return (
    <>
      <Rejilla>
        <Dato rotulo="Candidato">{candidato.id}</Dato>
        <Dato rotulo={COLUMNAS.predio}>{candidato.predioId}</Dato>
        <Dato rotulo={COLUMNAS.estado}>
          <Insignia tono={tonoDelCandidato(candidato.estado)}>{candidato.estado}</Insignia>
        </Dato>
        <Dato rotulo={COLUMNAS.etapaDelDescarte}>{candidato.etapaDeDescarte}</Dato>
        <Dato rotulo={COLUMNAS.motivo}>{candidato.motivoDeDescarte}</Dato>
        <Dato rotulo="Descartado por">{candidato.descartadoPor}</Dato>
      </Rejilla>
      <VolverALeer onHecho={onHecho} />
    </>
  );
}

function ElHallazgo({ hallazgo, onHecho }: { hallazgo: api.Hallazgo; onHecho: () => void }) {
  return (
    <>
      <Rejilla>
        <Dato rotulo="Hallazgo">{hallazgo.id}</Dato>
        <Dato rotulo={COLUMNAS.clase}>{hallazgo.clase}</Dato>
        <Dato rotulo={COLUMNAS.predio}>{hallazgo.predioId}</Dato>
        <Dato rotulo={COLUMNAS.areaDeLaFicha}>{hallazgo.areaDeLaFicha}</Dato>
        <Dato rotulo={COLUMNAS.areaVerificada}>{hallazgo.areaVerificada}</Dato>
        <Dato rotulo={COLUMNAS.exceso}>{hallazgo.excesoVerificado}</Dato>
        <Dato rotulo={COLUMNAS.inspector}>{hallazgo.inspector}</Dato>
        <Dato rotulo={COLUMNAS.estado}>
          <Insignia tono={hallazgo.estado === 'FIRME' ? 'ok' : 'bad'}>{hallazgo.estado}</Insignia>
        </Dato>
        <Dato rotulo={COLUMNAS.anuladoPor}>{hallazgo.anuladoPor}</Dato>
        <Dato rotulo={COLUMNAS.anuladoEn}>{hallazgo.anuladoEn}</Dato>
        <Dato rotulo="Motivo de la anulacion">{hallazgo.motivoAnulacion}</Dato>
      </Rejilla>
      <VolverALeer onHecho={onHecho} />
    </>
  );
}

/**
 * Volver a leer la lista, y **por que es un boton y no algo automatico**.
 *
 * El acto ya se hizo: lo que la pantalla ensena arriba es lo que el servidor
 * contesto, que es la respuesta autorizada. La lista de al lado sale de OTRA
 * lectura, y refrescarla sola dejaria a quien mira sin saber cual de las dos
 * cosas esta viendo. Se ofrece, se dice, y quien lo pulse ve la fila con su
 * estado nuevo.
 */
function VolverALeer({ onHecho }: { onHecho: () => void }) {
  return (
    <div style={{ padding: '0 16px 4px' }}>
      <Boton onClick={onHecho}>Volver a leer la lista</Boton>
    </div>
  );
}

/* ── Hallazgos: lo verificado, y el acto que lo retira ──────────────────── */

/** Lo verificado en campo. Un hallazgo se INFORMA; no corrige la ficha. */
export function Hallazgos({ ruta, onSujeto, onFiltros, onIr }: PantallaProps) {
  const campaniaId = numeroDe(ruta.sujeto);
  const lista = useRecurso(
    (senal) => api.hallazgos(campaniaId!, { tamano: 50 }, senal),
    ['hallazgos', campaniaId],
    campaniaId !== null,
  );

  const acto = ruta.filtros.acto ?? '';
  const hallazgoId = numeroDe(ruta.filtros.hallazgo ?? '');
  const cerrarElActo = () => onFiltros(sinClaves(ruta.filtros, ['acto', 'hallazgo']));

  /* Los otros dos actos del hallazgo viven en la hoja de Actas, que es donde se
     lee su evidencia: se va alli con el hallazgo puesto de sujeto. */
  const irAlActo = (k: string, id: number) => {
    if (k === 'dejarSinEfecto') {
      onFiltros({ ...ruta.filtros, acto: k, hallazgo: String(id) });
      return;
    }
    onIr('fiscalizacion', 'actas', { acto: k }, String(id));
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <CajaDeCampania ruta={ruta} onSujeto={onSujeto} />

      {acto === 'dejarSinEfecto' && hallazgoId !== null ? (
        <Acto
          key={`anulacion-${hallazgoId}`}
          titulo={ACTOS.dejarSinEfecto}
          nota={NOTAS_DE_LOS_ACTOS.dejarSinEfecto}
          campos={[{ k: 'motivo', rotulo: CAMPOS.motivo.rotulo, ayuda: CAMPOS.motivo.ayuda, ancho: 460 }]}
          advertencia={IRREVERSIBLES.anulacion}
          enviar={(v) => api.dejarSinEfecto(hallazgoId, { motivo: v.motivo!, observacion: v.observacion! })}
          pinta={(h) => <ElHallazgo hallazgo={h} onHecho={() => lista.reintentar()} />}
          onCerrar={cerrarElActo}
        />
      ) : null}

      <Seccion titulo={TITULOS.hallazgos}>
        <Lectura recurso={lista} espera={ESPERAS.hallazgos}>
          {(r) => (
            <Tabla
              columnas={[
                { label: COLUMNAS.predio, numerica: true, pinta: (h: api.Hallazgo) => h.predioId ?? '—' },
                { label: COLUMNAS.clase, pinta: (h: api.Hallazgo) => <Insignia>{h.clase}</Insignia> },
                {
                  label: COLUMNAS.areaDeLaFicha,
                  numerica: true,
                  pinta: (h: api.Hallazgo) => h.areaDeLaFicha ?? '—',
                },
                { label: COLUMNAS.areaVerificada, numerica: true, pinta: (h: api.Hallazgo) => h.areaVerificada },
                { label: COLUMNAS.exceso, numerica: true, pinta: (h: api.Hallazgo) => h.excesoVerificado ?? '—' },
                { label: COLUMNAS.inspector, pinta: (h: api.Hallazgo) => h.inspector },
                { label: COLUMNAS.verificado, pinta: (h: api.Hallazgo) => h.verificadoEn },
                {
                  label: COLUMNAS.estado,
                  pinta: (h: api.Hallazgo) => (
                    <Insignia tono={h.estado === 'FIRME' ? 'ok' : 'bad'}>{h.estado}</Insignia>
                  ),
                },
                {
                  label: COLUMNAS.acciones,
                  pinta: (h: api.Hallazgo) => (
                    <ActosDeLaFila
                      que="Hallazgo"
                      id={h.id}
                      estado={h.estado}
                      actos={actosDe(api.TRANSICIONES_DEL_HALLAZGO, h.estado)}
                      rotulos={ACTOS}
                      onActo={(a) => irAlActo(a, h.id)}
                    />
                  ),
                },
              ]}
              filas={r.contenido}
              llave={(h) => h.id}
              /* El acto de la anulacion (#23) va DEBAJO de su fila y no en una
                 columna, y no es cosmetica: sus tres campos —motivo, quien y
                 cuando— son nulos en todo hallazgo firme, o sea en la mayoria,
                 y solo dicen algo juntos. Como columna quedaba vacia casi
                 siempre y estrujada en el resto, y con ella la tabla no cabia
                 en los 1 440 px del artboard: el ultimo control —«Adjuntar
                 evidencia»— quedaba cortado por el borde, que es un boton que
                 no se puede pulsar Y no se puede ver. */
              detalle={(h) => <LaAnulacion hallazgo={h} />}
              vacio={VACIOS.hallazgos}
              pie={PIES.hallazgos}
            />
          )}
        </Lectura>
      </Seccion>

      <Aviso tono="info" titulo="Un hallazgo se informa: no corrige la ficha">
        {MOTIVOS.noCorrigeLaFicha} {MOTIVOS.sinImporte}
      </Aviso>

      <Servida
        lee={[api.RUTAS.hallazgos]}
        escribe={[{ metodo: 'POST', ruta: api.RUTAS.anulacion }]}
        falta="Ninguna ruta corrige la ficha desde un hallazgo, y no falta: corregirla es versionar la ficha con su observacion, y ese acto lo ejecuta una persona (ADR-0021, ADR-0035)."
      />
    </div>
  );
}

/* ── Actas y evidencia: los dos actos que cuelgan de un hallazgo ────────── */

/**
 * El acto y la evidencia que lo sostiene.
 *
 * **El backend no publica ninguna lectura de actas**, asi que lo que esta
 * pantalla puede LEER es la evidencia de un hallazgo. Lo que puede ESCRIBIR son
 * los dos actos que cuelgan de el: adjuntar una evidencia y levantar el acta.
 * El estado del hallazgo no se puede leer desde aqui —no hay lectura de uno
 * suelto—, asi que los dos se ofrecen y es el servidor quien dice que no si el
 * hallazgo esta dejado sin efecto: es una respuesta con su motivo, no un camino
 * inventado.
 */
export function Actas({ ruta, onSujeto, onFiltros }: PantallaProps) {
  const hallazgoId = numeroDe(ruta.sujeto);
  const lista = useRecurso(
    (senal) => api.evidencias(hallazgoId!, senal),
    ['evidencias', hallazgoId],
    hallazgoId !== null,
  );

  const acto = ruta.filtros.acto ?? '';
  const abrir = (k: string) => onFiltros({ ...ruta.filtros, acto: k });
  const cerrarElActo = () => onFiltros(sinClaves(ruta.filtros, ['acto']));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <Seccion
        titulo={TITULOS.elHallazgo}
        derecha={
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Boton
              impedido={hallazgoId === null}
              motivo="Falta el identificador del hallazgo al que se le adjunta la evidencia."
              onClick={() => abrir('adjuntarEvidencia')}
            >
              {ACTOS.adjuntarEvidencia}
            </Boton>
            <Boton
              tipo="primario"
              impedido={hallazgoId === null}
              motivo="Falta el identificador del hallazgo sobre el que se levanta el acta."
              onClick={() => abrir('levantarActa')}
            >
              {ACTOS.levantarActa}
            </Boton>
          </div>
        }
      >
        <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
          <Campo
            rotulo={SUJETOS.hallazgo.rotulo}
            valor={ruta.sujeto}
            onCambio={onSujeto}
            marcador={SUJETOS.hallazgo.marcador}
            ayuda={SUJETOS.hallazgo.ayuda}
          />
          <Aviso tono="warn" titulo="De las actas no se puede leer nada por esta ruta">
            {MOTIVOS.sinLecturaDeActas} {MOTIVOS.sinLecturaDeUnHallazgo}
          </Aviso>
        </div>
      </Seccion>

      {acto === 'adjuntarEvidencia' && hallazgoId !== null ? (
        <Acto
          key={`evidencia-${hallazgoId}`}
          titulo={ACTOS.adjuntarEvidencia}
          nota={NOTAS_DE_LOS_ACTOS.adjuntarEvidencia}
          campos={[
            {
              k: 'tipo',
              rotulo: CAMPOS.tipoDeEvidencia.rotulo,
              ayuda: CAMPOS.tipoDeEvidencia.ayuda,
              opciones: api.TIPOS_DE_EVIDENCIA,
            },
            { k: 'sha256', rotulo: CAMPOS.huella.rotulo, ayuda: CAMPOS.huella.ayuda, ancho: 460 },
            { k: 'ruta', rotulo: CAMPOS.rutaDelArchivo.rotulo, ayuda: CAMPOS.rutaDelArchivo.ayuda, ancho: 460 },
            { k: 'capturadoEn', rotulo: CAMPOS.capturadoEn.rotulo, ayuda: CAMPOS.capturadoEn.ayuda, ancho: 320 },
            {
              k: 'dispositivo',
              rotulo: CAMPOS.dispositivo.rotulo,
              ayuda: CAMPOS.dispositivo.ayuda,
              opcional: true,
            },
          ]}
          enviar={(v) =>
            api.adjuntarEvidencia(hallazgoId, {
              tipo: v.tipo!,
              sha256: v.sha256!,
              ruta: v.ruta!,
              capturadoEn: v.capturadoEn!,
              dispositivo: v.dispositivo,
              observacion: v.observacion!,
            })
          }
          pinta={(e) => <LaEvidencia evidencia={e} onHecho={() => lista.reintentar()} />}
          onCerrar={cerrarElActo}
        />
      ) : null}

      {acto === 'levantarActa' && hallazgoId !== null ? (
        <Acto
          key={`acta-${hallazgoId}`}
          titulo={ACTOS.levantarActa}
          nota={NOTAS_DE_LOS_ACTOS.levantarActa}
          campos={[
            { k: 'numero', rotulo: CAMPOS.numeroDelActa.rotulo, ayuda: CAMPOS.numeroDelActa.ayuda },
            { k: 'inspector', rotulo: CAMPOS.inspector.rotulo, ayuda: CAMPOS.inspector.ayuda },
            { k: 'detalle', rotulo: CAMPOS.detalle.rotulo, ayuda: CAMPOS.detalle.ayuda, ancho: 460 },
          ]}
          enviar={(v) =>
            api.levantarActa(hallazgoId, {
              numero: v.numero!,
              inspector: v.inspector!,
              detalle: v.detalle!,
              observacion: v.observacion!,
            })
          }
          pinta={(a) => <ElActa acta={a} />}
          onCerrar={cerrarElActo}
        />
      ) : null}

      <Seccion titulo={TITULOS.evidencia}>
        <Lectura recurso={lista} espera={ESPERAS.evidencia}>
          {(r) => (
            <Tabla
              columnas={[
                { label: COLUMNAS.tipo, pinta: (e: api.Evidencia) => <Insignia>{e.tipo}</Insignia> },
                { label: COLUMNAS.ruta, pinta: (e: api.Evidencia) => e.ruta },
                {
                  label: COLUMNAS.huella,
                  pinta: (e: api.Evidencia) => (
                    <code style={{ fontSize: 12 }}>{e.sha256.slice(0, 12)}…</code>
                  ),
                },
                { label: COLUMNAS.capturado, pinta: (e: api.Evidencia) => e.capturadoEn },
                { label: COLUMNAS.recibido, pinta: (e: api.Evidencia) => e.recibidoEn },
                { label: COLUMNAS.desfase, numerica: true, pinta: (e: api.Evidencia) => e.desfaseEnSegundos },
                { label: COLUMNAS.dispositivo, pinta: (e: api.Evidencia) => e.dispositivo ?? '—' },
              ]}
              filas={r}
              llave={(e) => e.id}
              vacio={VACIOS.evidencias}
              pie={PIES.evidencias}
            />
          )}
        </Lectura>
      </Seccion>

      <Servida
        lee={[api.RUTAS.evidencias]}
        escribe={[{ metodo: 'POST', ruta: api.RUTAS.evidencias }, { metodo: 'POST', ruta: api.RUTAS.acta }]}
        falta={MOTIVOS.sinLecturaDeActas}
      />
    </div>
  );
}

function LaEvidencia({ evidencia, onHecho }: { evidencia: api.Evidencia; onHecho: () => void }) {
  return (
    <>
      <Rejilla>
        <Dato rotulo="Evidencia">{evidencia.id}</Dato>
        <Dato rotulo={COLUMNAS.tipo}>{evidencia.tipo}</Dato>
        <Dato rotulo={COLUMNAS.ruta}>{evidencia.ruta}</Dato>
        <Dato rotulo={COLUMNAS.capturado}>{evidencia.capturadoEn}</Dato>
        <Dato rotulo={COLUMNAS.recibido}>{evidencia.recibidoEn}</Dato>
        <Dato rotulo={COLUMNAS.desfase}>{evidencia.desfaseEnSegundos}</Dato>
        <Dato rotulo={COLUMNAS.dispositivo}>{evidencia.dispositivo}</Dato>
      </Rejilla>
      <VolverALeer onHecho={onHecho} />
    </>
  );
}

/**
 * El acta recien levantada, que es **la unica forma de verla por esta ruta**.
 *
 * No hay ningun `GET` de actas: el servidor devuelve la que acaba de crear y
 * despues solo se puede volver a leer por el predio del hallazgo, donde viaja
 * dentro.
 */
function ElActa({ acta }: { acta: api.Acta }) {
  return (
    <Rejilla>
      <Dato rotulo="Acta">{acta.id}</Dato>
      <Dato rotulo="Numero">{acta.numero}</Dato>
      <Dato rotulo="Hallazgo">{acta.hallazgoId}</Dato>
      <Dato rotulo="Fecha">{acta.fecha}</Dato>
      <Dato rotulo={COLUMNAS.inspector}>{acta.inspector}</Dato>
      <Dato rotulo="Detalle">{acta.detalle}</Dato>
    </Rejilla>
  );
}
