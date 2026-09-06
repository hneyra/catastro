import type { PantallaProps } from '../../App';
import * as api from '../../api/fiscalizacion';
import { useRecurso } from '../../api/useRecurso';
import { Aviso, Campo, Dato, Insignia, Lectura, Rejilla, Seccion, Tabla } from '../../ds/componentes';

function campaniaDe(sujeto: string): number | null {
  return /^\d+$/.test(sujeto) ? Number(sujeto) : null;
}

function CajaDeCampania({ ruta, onSujeto }: Pick<PantallaProps, 'ruta' | 'onSujeto'>) {
  return (
    <Seccion titulo="La campania">
      <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Campo
          rotulo="Identificador de la campania"
          valor={ruta.sujeto}
          onCambio={onSujeto}
          marcador="El numero que devolvio el alta"
          ayuda="Hay que escribirlo: el backend no publica el listado"
        />
        <Aviso tono="warn" titulo="No hay listado de campanias, y por eso se pide a mano">
          El controlador publica once operaciones y ninguna enumera las campanias: se crean con un POST y se leen
          por identificador. Dibujar aqui una tabla exigiria inventar la operacion que las lista, y esta interfaz
          no inventa endpoints.
        </Aviso>
      </div>
    </Seccion>
  );
}

/** El embudo de una campania: seis cifras y ningun porcentaje. */
export function Campanias({ ruta, onSujeto }: PantallaProps) {
  const campaniaId = campaniaDe(ruta.sujeto);
  const tasa = useRecurso(
    (senal) => api.tasaDeDescarte(campaniaId!, senal),
    ['tasa', campaniaId],
    campaniaId !== null,
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1000 }}>
      <CajaDeCampania ruta={ruta} onSujeto={onSujeto} />

      <Seccion titulo="El embudo" nota="Detectado, descartado y verificado">
        <Lectura
          recurso={tasa}
          espera="Escriba el identificador de una campania y aqui saldra su embudo: cuanto se detecto, cuanto se descarto en cada compuerta y cuanto llego a hallazgo."
        >
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
                Son cifras y no una tasa, a proposito. Un porcentaje esconde el denominador —la mitad de veinte y
                la mitad de veinte mil no dicen lo mismo— y ademas calcularlo aqui exigiria decidir un modo de
                redondeo que nadie ha decidido (D-03b).
              </p>
            </>
          )}
        </Lectura>
      </Seccion>
    </div>
  );
}

/** Lo detectado, antes de verificarlo en campo. */
export function Candidatos({ ruta, onSujeto }: PantallaProps) {
  const campaniaId = campaniaDe(ruta.sujeto);
  const lista = useRecurso(
    (senal) => api.candidatos(campaniaId!, {}, { tamano: 50 }, senal),
    ['candidatos', campaniaId],
    campaniaId !== null,
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <CajaDeCampania ruta={ruta} onSujeto={onSujeto} />

      <Seccion titulo="Candidatos de la campania">
        <Lectura
          recurso={lista}
          espera="Escriba el identificador de una campania y aqui saldran sus candidatos, con la compuerta en la que esta cada uno."
        >
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Predio', numerica: true, pinta: (c: api.Candidato) => c.predioId ?? '—' },
                { label: 'Clase', pinta: (c: api.Candidato) => <Insignia>{c.clase}</Insignia> },
                { label: 'Origen', pinta: (c: api.Candidato) => c.origen },
                { label: 'Score', numerica: true, pinta: (c: api.Candidato) => c.score },
                {
                  label: 'Estado',
                  pinta: (c: api.Candidato) => (
                    <Insignia tono={c.estado === 'DESCARTADO' ? 'bad' : 'warn'}>{c.estado}</Insignia>
                  ),
                },
                { label: 'Etapa del descarte', pinta: (c: api.Candidato) => c.etapaDeDescarte ?? '—' },
                { label: 'Motivo', pinta: (c: api.Candidato) => c.motivoDeDescarte ?? '—' },
              ]}
              filas={r.contenido}
              llave={(c) => c.id}
              vacio="Esta campania no tiene ningun candidato."
              pie="Un candidato no puede saltarse el gabinete: verificarlo en campo exige que haya pasado esa compuerta antes, y el motivo del descarte se guarda con quien lo firmo."
            />
          )}
        </Lectura>
      </Seccion>
    </div>
  );
}

/** Lo verificado en campo. Un hallazgo se INFORMA; no corrige la ficha. */
export function Hallazgos({ ruta, onSujeto }: PantallaProps) {
  const campaniaId = campaniaDe(ruta.sujeto);
  const lista = useRecurso(
    (senal) => api.hallazgos(campaniaId!, { tamano: 50 }, senal),
    ['hallazgos', campaniaId],
    campaniaId !== null,
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <CajaDeCampania ruta={ruta} onSujeto={onSujeto} />

      <Seccion titulo="Hallazgos de la campania">
        <Lectura
          recurso={lista}
          espera="Escriba el identificador de una campania y aqui saldran sus hallazgos, con el area de la ficha frente a la verificada."
        >
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Predio', numerica: true, pinta: (h: api.Hallazgo) => h.predioId ?? '—' },
                { label: 'Clase', pinta: (h: api.Hallazgo) => <Insignia>{h.clase}</Insignia> },
                { label: 'Area de la ficha', numerica: true, pinta: (h: api.Hallazgo) => h.areaDeLaFicha ?? '—' },
                { label: 'Area verificada', numerica: true, pinta: (h: api.Hallazgo) => h.areaVerificada },
                { label: 'Exceso', numerica: true, pinta: (h: api.Hallazgo) => h.excesoVerificado ?? '—' },
                { label: 'Inspector', pinta: (h: api.Hallazgo) => h.inspector },
                { label: 'Verificado', pinta: (h: api.Hallazgo) => h.verificadoEn },
                {
                  label: 'Estado',
                  pinta: (h: api.Hallazgo) => (
                    <Insignia tono={h.estado === 'FIRME' ? 'ok' : 'warn'}>{h.estado}</Insignia>
                  ),
                },
              ]}
              filas={r.contenido}
              llave={(h) => h.id}
              vacio="Esta campania no tiene ningun hallazgo."
              pie="Las tres areas llegan como texto y se pintan como texto: el exceso lo calcula el servidor, y una segunda resta aqui podria divergir de la suya sin que nada lo dijera."
            />
          )}
        </Lectura>
      </Seccion>

      <Aviso tono="info" titulo="Un hallazgo se informa: no corrige la ficha">
        Ninguna operacion de este modulo escribe en el area del predio. Corregirla es versionar la ficha con su
        observacion, y ese acto lo ejecuta una persona (ADR-0021, ADR-0035). Y aqui no se liquida nada: ninguna de
        las cinco tablas de fiscalizacion tiene columna de importe, y ninguna respuesta del contrato la trae.
      </Aviso>
    </div>
  );
}

/**
 * El acto y la evidencia que lo sostiene.
 *
 * **El backend no publica ninguna lectura de actas**, asi que lo que esta
 * pantalla puede ensenar es la evidencia de un hallazgo, que es lo que un acta
 * incorpora. Se dice; no se dibuja una tabla de actas vacia.
 */
export function Actas({ ruta, onSujeto }: PantallaProps) {
  const hallazgoId = campaniaDe(ruta.sujeto);
  const lista = useRecurso(
    (senal) => api.evidencias(hallazgoId!, senal),
    ['evidencias', hallazgoId],
    hallazgoId !== null,
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <Seccion titulo="El hallazgo">
        <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
          <Campo
            rotulo="Identificador del hallazgo"
            valor={ruta.sujeto}
            onCambio={onSujeto}
            marcador="El numero del hallazgo"
            ayuda="Sale de la pantalla de hallazgos"
          />
          <Aviso tono="warn" titulo="De las actas no se puede leer nada todavia">
            Un acta se levanta con un POST sobre su hallazgo y el servidor devuelve la que acaba de crear. No hay
            ninguna lectura de actas —ni por campania ni por hallazgo—, asi que esta pantalla no puede listarlas.
            Lo que si se lee es la evidencia del hallazgo, que es lo que un acta incorpora.
          </Aviso>
        </div>
      </Seccion>

      <Seccion titulo="Evidencia del hallazgo">
        <Lectura
          recurso={lista}
          espera="Escriba el identificador de un hallazgo y aqui saldra su evidencia, con la huella de cada pieza y el desfase entre capturarla y recibirla."
        >
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Tipo', pinta: (e: api.Evidencia) => <Insignia>{e.tipo}</Insignia> },
                { label: 'Ruta', pinta: (e: api.Evidencia) => e.ruta },
                {
                  label: 'Huella',
                  pinta: (e: api.Evidencia) => (
                    <code style={{ fontSize: 12 }}>{e.sha256.slice(0, 12)}…</code>
                  ),
                },
                { label: 'Capturado', pinta: (e: api.Evidencia) => e.capturadoEn },
                { label: 'Recibido', pinta: (e: api.Evidencia) => e.recibidoEn },
                { label: 'Desfase (s)', numerica: true, pinta: (e: api.Evidencia) => e.desfaseEnSegundos },
                { label: 'Dispositivo', pinta: (e: api.Evidencia) => e.dispositivo ?? '—' },
              ]}
              filas={r}
              llave={(e) => e.id}
              vacio="Este hallazgo no tiene ninguna evidencia registrada."
              pie="El desfase entre capturar y recibir lo calcula el servidor y viaja como dato: es lo que separa una fotografia tomada en el predio de una anadida despues. La evidencia no se corrige en el sitio; si algo cambia, se agrega otro registro (ADR-0006, ADR-0008)."
            />
          )}
        </Lectura>
      </Seccion>
    </div>
  );
}
