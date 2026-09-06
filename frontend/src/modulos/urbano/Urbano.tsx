import type { PantallaProps } from '../../App';
import * as api from '../../api/urbano';
import { useRecurso } from '../../api/useRecurso';
import { Aviso, Campo, Dato, Lectura, Rejilla, Seccion, Servida, Tabla } from '../../ds/componentes';

/**
 * La zonificacion vigente: a que zona cae un predio.
 *
 * **Hoy contesta 422 para todo predio real**, y esta pantalla lo distingue del
 * «no existe»: sin poligono levantado no hay con que contestar, y eso lo
 * arregla la carga cartografica, no quien atiende.
 */
export function Zonificacion({ ruta, onSujeto }: PantallaProps) {
  const predioId = /^\d+$/.test(ruta.sujeto) ? Number(ruta.sujeto) : null;
  const zona = useRecurso(
    (senal) => api.zonificacion(predioId!, undefined, senal),
    ['zonificacion', predioId],
    predioId !== null,
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 900 }}>
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

      <Seccion titulo="Zona a la que cae">
        <Lectura
          recurso={zona}
          espera="Escriba el identificador de un predio y aqui saldra la zona a la que cae, con sus parametros urbanisticos."
        >
          {(z) => (
            <>
              <Rejilla>
                <Dato rotulo="Codigo">{z.codigo}</Dato>
                <Dato rotulo="Zona">{z.nombre}</Dato>
                <Dato rotulo="Plan">{z.plan}</Dato>
                <Dato rotulo="Ordenanza">{z.ordenanza}</Dato>
                <Dato rotulo="Vigente desde">{z.vigenciaDesde}</Dato>
                <Dato rotulo="Vigente hasta">{z.vigenciaHasta}</Dato>
              </Rejilla>
              <Tabla
                columnas={[
                  { label: 'Parametro', pinta: (p: api.ParametroUrbanistico) => p.clave },
                  { label: 'Valor', numerica: true, pinta: (p: api.ParametroUrbanistico) => p.valor },
                  { label: 'Unidad', pinta: (p: api.ParametroUrbanistico) => p.unidad ?? '—' },
                ]}
                filas={z.parametros}
                llave={(p) => p.clave}
                vacio="Esta zona no publica ningun parametro urbanistico."
              />
            </>
          )}
        </Lectura>
      </Seccion>

      <Aviso tono="info" titulo="Lo que esta pantalla no puede contestar">
        Si un giro es compatible con esta zona <strong>no se decide aqui</strong>. «catastro» publica la zona;
        quien es compatible con que es dato de «rentas» y la licencia la emite «rentas». Es la misma frontera de
        ADR-0024 que le impide calcular un tributo, y es lo que permite abrir esta API a desarrollo urbano sin
        abrir con ella el padron tributario.
      </Aviso>

      <Servida
        lee={[api.RUTAS.zonificacion]}
        falta="La compatibilidad de un giro con esta zona no la sirve nadie de este backend, y no es un hueco: es dato de «rentas» (ciiu.zonificacion_compatible) y la licencia la emite «rentas»."
      />

      <Aviso tono="warn" titulo="Por que casi siempre sale un rechazo">
        No hay <strong>ni un poligono cargado</strong> en ninguna instalacion. Sin geometria del lote no se puede
        cruzar contra las zonas, asi que el servidor contesta que el predio existe y le falta el poligono —y no
        que no existe—, porque las dos cosas se arreglan de manera distinta.
      </Aviso>
    </div>
  );
}
