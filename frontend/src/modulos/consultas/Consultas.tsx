import { useState } from 'react';
import type { PantallaProps } from '../../App';
import * as api from '../../api/consultas';
import { FORMATOS_DE_DOCUMENTO } from '../../api/cliente';
import type { FormatoDeDocumento } from '../../api/cliente';
import { ErrorDeApi } from '../../api/cliente';
import { useRecurso } from '../../api/useRecurso';
import {
  Aviso,
  Boton,
  Campo,
  Dato,
  Insignia,
  Lectura,
  Rejilla,
  Seccion,
  Selector,
  Servida,
  Tabla,
} from '../../ds/componentes';

/** Los predios de un contribuyente, o los de un codigo catastral. */
export function Resumen({ ruta, onFiltros }: PantallaProps) {
  const codCatastral = ruta.filtros.codCatastral ?? '';
  const codContribuyente = ruta.filtros.codContribuyente ?? '';
  const lista = useRecurso(
    (senal) => api.resumenPredial({ codCatastral, codContribuyente }, { tamano: 50 }, senal),
    ['resumen', codCatastral, codContribuyente],
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <Seccion titulo="Buscar">
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', padding: '14px 16px' }}>
          <Campo
            rotulo="Codigo catastral"
            valor={codCatastral}
            onCambio={(v) => onFiltros({ ...ruta.filtros, codCatastral: v })}
            marcador="Los primeros tramos bastan"
          />
          <Campo
            rotulo="Codigo de contribuyente"
            valor={codContribuyente}
            onCambio={(v) => onFiltros({ ...ruta.filtros, codContribuyente: v })}
            marcador="El del padron de rentas"
          />
        </div>
      </Seccion>

      <Seccion titulo="Resumen predial">
        <Lectura recurso={lista} espera="">
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Codigo catastral', pinta: (p: api.PredioDelResumen) => p.codCatastral },
                { label: 'Propietario', pinta: (p: api.PredioDelResumen) => p.nombreDelPropietario ?? '—' },
                { label: 'Direccion', pinta: (p: api.PredioDelResumen) => p.direccionDelPredio },
                { label: 'Uso', pinta: (p: api.PredioDelResumen) => p.uso },
                { label: 'Tipo', pinta: (p: api.PredioDelResumen) => <Insignia>{p.tipo}</Insignia> },
                { label: 'Version', numerica: true, pinta: (p: api.PredioDelResumen) => p.version },
                { label: 'Vigente desde', pinta: (p: api.PredioDelResumen) => p.vigenciaDesde },
              ]}
              filas={r.contenido}
              llave={(p) => p.fichaId}
              vacio="Ningun predio cumple lo pedido."
            />
          )}
        </Lectura>
      </Seccion>

      <Aviso tono="info" titulo="Aqui no hay ninguna deuda">
        Esta consulta se llama «resumen predial-arbitrios» en el catalogo del sistema y publica el lado
        CATASTRAL: que predios tiene una persona y con que ficha. Lo que se debe por ellos lo determina «rentas»,
        que es la frontera de ADR-0024.
      </Aviso>

      <Servida
        lee={[api.RUTAS.resumenPredial]}
        falta="La deuda de esos predios no la sirve ninguna ruta de este backend, y no es un hueco pendiente: la determina «rentas» (ADR-0024)."
      />
    </div>
  );
}

/**
 * La ficha del contribuyente, y su documento.
 *
 * La misma URI sirve las dos cosas y lo que las separa es que el parametro
 * `formato` EXISTA: sin el, JSON; con el, el archivo. Por eso son dos llamadas
 * distintas y no una con un parametro opcional.
 */
export function FichaDelContribuyente({ ruta, onSujeto }: PantallaProps) {
  const codigo = ruta.sujeto;
  const [formato, setFormato] = useState<FormatoDeDocumento>('PDF');
  const [entrega, setEntrega] = useState<string | null>(null);
  const [falloAlBajar, setFalloAlBajar] = useState<string | null>(null);

  const ficha = useRecurso(
    (senal) => api.fichaDelContribuyente(codigo, undefined, senal),
    ['ficha-contribuyente', codigo],
    codigo !== '',
  );

  const bajar = () => {
    setEntrega(null);
    setFalloAlBajar(null);
    api
      .documentoDeLaFicha(codigo, formato)
      .then((d) => setEntrega(d.nombre))
      .catch((e: unknown) =>
        setFalloAlBajar(e instanceof ErrorDeApi ? e.mensaje : 'No se pudo entregar el documento'),
      );
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1000 }}>
      <Seccion titulo="El contribuyente">
        <div style={{ padding: '14px 16px' }}>
          <Campo
            rotulo="Codigo de contribuyente"
            valor={codigo}
            onCambio={onSujeto}
            marcador="El del padron de rentas"
            ayuda="No es el documento de identidad ni el codigo catastral"
          />
        </div>
      </Seccion>

      <Seccion titulo="La hoja">
        <Lectura
          recurso={ficha}
          espera="Escriba el codigo de un contribuyente y aqui saldra su hoja: quien es y que unidades tiene."
        >
          {(r) => (
            <>
              <Rejilla>
                <Dato rotulo="Codigo">{r.codigo}</Dato>
                <Dato rotulo="Nombre">{r.nombre}</Dato>
                <Dato rotulo="Documento">{r.documento}</Dato>
                <Dato rotulo="Domicilio fiscal">{r.domicilioFiscal}</Dato>
                <Dato rotulo="A la fecha">{r.aLaFecha}</Dato>
              </Rejilla>
              <Tabla
                columnas={[
                  { label: 'Codigo catastral', pinta: (u: api.UnidadDelReporte) => u.codRefCatastral },
                  { label: 'Direccion', pinta: (u: api.UnidadDelReporte) => u.direccion },
                  { label: 'Condicion', pinta: (u: api.UnidadDelReporte) => u.condicion },
                  { label: 'Porcentaje', numerica: true, pinta: (u: api.UnidadDelReporte) => u.porcentaje },
                  { label: 'Area terreno', numerica: true, pinta: (u: api.UnidadDelReporte) => u.areaTerreno ?? '—' },
                  { label: 'Uso', pinta: (u: api.UnidadDelReporte) => u.uso ?? '—' },
                ]}
                filas={r.unidades}
                llave={(u) => u.codRefCatastral}
                vacio="Este contribuyente no tiene ninguna unidad en el catastro."
                pie="El porcentaje y el area llegan como texto con decimal plano, y se pintan tal cual."
              />
            </>
          )}
        </Lectura>
      </Seccion>

      <Seccion titulo="El documento">
        <div style={{ display: 'flex', gap: 14, alignItems: 'flex-end', flexWrap: 'wrap', padding: '14px 16px' }}>
          <Selector
            rotulo="Formato"
            valor={formato}
            onCambio={(v) => setFormato(v as FormatoDeDocumento)}
            opciones={FORMATOS_DE_DOCUMENTO.map((f) => ({ valor: f, label: f }))}
            ayuda="Los tres que el servidor admite"
            ancho={160}
          />
          <Boton
            tipo="primario"
            onClick={bajar}
            impedido={codigo === ''}
            motivo="Escriba antes el codigo del contribuyente: sin el no hay ficha que emitir"
          >
            Descargar la ficha
          </Boton>
        </div>
        {entrega ? (
          <div style={{ padding: '0 16px 14px' }}>
            <Aviso tono="ok" titulo="Entregado">
              Se descargo «{entrega}».
            </Aviso>
          </div>
        ) : null}
        {falloAlBajar ? (
          <div style={{ padding: '0 16px 14px' }}>
            <Aviso tono="bad" titulo="No se pudo entregar">
              {falloAlBajar}
            </Aviso>
          </div>
        ) : null}
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
          Es la unica ruta del sistema con esta forma: la misma URI devuelve datos o un archivo segun{' '}
          <strong>exista</strong> el parametro «formato». Y no vale un enlace: el token viaja en una cabecera, asi
          que un &lt;a href&gt; a esa ruta se baja el rechazo con nombre de PDF.
        </p>
      </Seccion>

      <Servida
        lee={[api.RUTAS.fichaDelContribuyente]}
        falta="No hay ninguna busqueda de contribuyentes en este backend: el padron de personas vive en «rentas» y aqui solo hay una copia por codigo, asi que el codigo se teclea."
      />
    </div>
  );
}
