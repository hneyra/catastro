import type { PantallaProps } from '../../App';
import * as api from '../../api/grd';
import { useRecurso } from '../../api/useRecurso';
import { Aviso, Campo, Dato, Insignia, Lectura, Rejilla, Seccion, Servida, Tabla } from '../../ds/componentes';

function predioDe(sujeto: string): number | null {
  return /^\d+$/.test(sujeto) ? Number(sujeto) : null;
}

function CajaDePredio({ ruta, onSujeto }: Pick<PantallaProps, 'ruta' | 'onSujeto'>) {
  return (
    <Seccion titulo="El predio">
      <div style={{ padding: '14px 16px' }}>
        <Campo
          rotulo="Identificador del predio"
          valor={ruta.sujeto}
          onCambio={onSujeto}
          marcador="El numero interno del predio"
          ayuda="El que devuelve el padron, no el codigo de referencia catastral"
        />
      </div>
    </Seccion>
  );
}

/** Zonas de peligro y fajas marginales. Mismo 422 por predio sin poligono. */
export function RiesgoDelPredio({ ruta, onSujeto }: PantallaProps) {
  const predioId = predioDe(ruta.sujeto);
  const riesgo = useRecurso(
    (senal) => api.riesgo(predioId!, senal),
    ['riesgo', predioId],
    predioId !== null,
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1000 }}>
      <CajaDePredio ruta={ruta} onSujeto={onSujeto} />

      <Seccion titulo="Peligro sobre el predio">
        <Lectura
          recurso={riesgo}
          espera="Escriba el identificador de un predio y aqui saldran las zonas de peligro y las fajas marginales que lo alcanzan."
        >
          {(r) => (
            <>
              <div style={{ padding: '14px 16px 0' }}>
                {r.hayRiesgoNoMitigable ? (
                  <Aviso tono="bad" titulo="Hay riesgo NO mitigable">
                    Alguna de las zonas que alcanzan a este predio no es mitigable. Es el dato del que cuelga la
                    decision, y por eso el servidor lo publica derivado arriba y no solo zona a zona: el nivel
                    solo no decide nada.
                  </Aviso>
                ) : (
                  <Aviso tono="ok" titulo="Ninguna zona no mitigable">
                    Todas las zonas que alcanzan a este predio son mitigables.
                  </Aviso>
                )}
              </div>
              <Tabla
                columnas={[
                  { label: 'Codigo', pinta: (z: api.ZonaDeRiesgo) => z.codigo },
                  { label: 'Fenomeno', pinta: (z: api.ZonaDeRiesgo) => z.fenomeno },
                  { label: 'Nivel', pinta: (z: api.ZonaDeRiesgo) => <Insignia tono="warn">{z.nivel}</Insignia> },
                  {
                    label: 'Mitigable',
                    pinta: (z: api.ZonaDeRiesgo) =>
                      z.mitigable ? <Insignia tono="ok">Si</Insignia> : <Insignia tono="bad">No</Insignia>,
                  },
                  { label: 'Fuente', pinta: (z: api.ZonaDeRiesgo) => z.fuente },
                  { label: 'Documento', pinta: (z: api.ZonaDeRiesgo) => z.documentoOrigen },
                ]}
                filas={r.zonas}
                llave={(z) => z.id}
                vacio="Ninguna zona de peligro alcanza a este predio."
              />
              <Tabla
                columnas={[
                  { label: 'Faja', pinta: (f: api.FajaMarginal) => f.codigo },
                  { label: 'Cuerpo de agua', pinta: (f: api.FajaMarginal) => f.cuerpoDeAgua },
                  { label: 'Ancho (m)', numerica: true, pinta: (f: api.FajaMarginal) => f.anchoM },
                  { label: 'Documento', pinta: (f: api.FajaMarginal) => f.documentoOrigen },
                ]}
                filas={r.fajasMarginales}
                llave={(f) => f.id}
                vacio="Ninguna faja marginal alcanza a este predio."
                pie="La faja marginal es tabla aparte de la zona de riesgo, y no por comodidad: meterla dentro obligaria a inventarle un nivel y un «mitigable» que ninguna resolucion de la ANA le dio — y ese «mitigable» inventado es justo el campo del que cuelga la decision."
              />
            </>
          )}
        </Lectura>
      </Seccion>

      <Aviso tono="warn" titulo="Por que casi siempre sale un rechazo">
        Como la zonificacion, esta lectura cruza el poligono del lote contra las cartas de peligro, y{' '}
        <strong>no hay ni un poligono cargado</strong>. El servidor contesta que el predio existe y le falta la
        geometria, en vez de decir que no existe.
      </Aviso>

      <Servida
        lee={[api.RUTAS.riesgo]}
        falta="Ninguna ruta de este backend publica la carta de peligro entera ni permite cargarla: las zonas entran por el cargador batch «cargar-riesgo», que vive en «infra/carga-de-datos»."
      />
    </div>
  );
}

/** Los certificados ITSE vigentes. **Esta si contesta sin poligono.** */
export function Itse({ ruta, onSujeto }: PantallaProps) {
  const predioId = predioDe(ruta.sujeto);
  const itse = useRecurso((senal) => api.itse(predioId!, undefined, senal), ['itse', predioId], predioId !== null);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1000 }}>
      <CajaDePredio ruta={ruta} onSujeto={onSujeto} />

      <Seccion titulo="Certificados vigentes">
        <Lectura
          recurso={itse}
          espera="Escriba el identificador de un predio y aqui saldran sus certificados ITSE vigentes."
        >
          {(r) => (
            <>
              <Rejilla>
                <Dato rotulo="A la fecha">{r.aLaFecha}</Dato>
                <Dato rotulo="Certificados vigentes">{r.vigentes.length}</Dato>
              </Rejilla>
              <Tabla
                columnas={[
                  { label: 'Numero', pinta: (c: api.CertificadoItse) => c.numero },
                  { label: 'Nivel de riesgo', pinta: (c: api.CertificadoItse) => c.nivelRiesgo },
                  { label: 'Modalidad', pinta: (c: api.CertificadoItse) => c.modalidad },
                  { label: 'Desde', pinta: (c: api.CertificadoItse) => c.vigenciaDesde },
                  { label: 'Hasta', pinta: (c: api.CertificadoItse) => c.vigenciaHasta },
                  {
                    label: 'Anulado',
                    pinta: (c: api.CertificadoItse) =>
                      c.fechaAnulacion ? <Insignia tono="bad">{c.fechaAnulacion}</Insignia> : '—',
                  },
                ]}
                filas={r.vigentes}
                llave={(c) => c.id}
                vacio="Este predio no tiene ningun certificado ITSE vigente a la fecha."
                pie="«Vigente» incluye el ultimo dia: el servidor compara con «vigencia_hasta >= la fecha». Dejar el extremo fuera devolveria certificados caducados dentro de la lista de vigentes, y con uno de esos se emite una licencia contra un certificado vencido."
              />
            </>
          )}
        </Lectura>
      </Seccion>

      <Aviso tono="info" titulo="La unica lectura de este modulo que hoy contesta con datos">
        El ITSE cuelga del predio y no de su geometria, asi que no le afecta que no haya cartografia cargada. Su
        vecina, el riesgo, si.
      </Aviso>

      <Servida
        lee={[api.RUTAS.itse]}
        falta="No hay ninguna lectura que liste TODOS los certificados de la municipalidad: se piden por predio, asi que esta pantalla pide el predio."
      />
    </div>
  );
}
