import { useCallback } from 'react';
import type { PantallaProps } from '../../App';
import * as api from '../../api/catastro';
import { useRecurso } from '../../api/useRecurso';
import {
  Aviso,
  Campo,
  Dato,
  Insignia,
  Lectura,
  Rejilla,
  Seccion,
  Selector,
  Tabla,
} from '../../ds/componentes';

/** Un identificador de predio que el usuario escribio. Vacio = ninguno. */
function sujetoNumerico(sujeto: string): number | null {
  if (!/^\d+$/.test(sujeto)) return null;
  return Number(sujeto);
}

/* ── Panel ──────────────────────────────────────────────────────────────── */

export function Panel({ ejercicio }: PantallaProps) {
  const padron = useRecurso((senal) => api.predios({}, { tamano: 1 }, senal), ['panel-predios']);
  const fichas = useRecurso((senal) => api.fichas({}, { tamano: 1 }, senal), ['panel-fichas']);
  const sectores = useRecurso((senal) => api.sectores({ tamano: 100 }, senal), ['panel-sectores']);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <Seccion titulo="Lo que el padron dice hoy" nota={`Ejercicio ${ejercicio}`}>
        <Rejilla>
          <Lectura recurso={padron} espera="">
            {(r) => <Dato rotulo="Predios en el padron">{r.totalElementos}</Dato>}
          </Lectura>
          <Lectura recurso={fichas} espera="">
            {(r) => <Dato rotulo="Fichas versionadas">{r.totalElementos}</Dato>}
          </Lectura>
          <Lectura recurso={sectores} espera="">
            {(r) => <Dato rotulo="Sectores">{r.totalElementos}</Dato>}
          </Lectura>
        </Rejilla>
      </Seccion>

      <Seccion titulo="Los sectores, con lo que cuelga de cada uno">
        <Lectura recurso={sectores} espera="">
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Codigo', pinta: (s: api.Sector) => s.codigo },
                { label: 'Nombre', pinta: (s: api.Sector) => s.nombre },
                { label: 'Zona', pinta: (s: api.Sector) => s.zona ?? '—' },
                { label: 'Manzanas', numerica: true, pinta: (s: api.Sector) => s.manzanas ?? '—' },
                { label: 'Predios', numerica: true, pinta: (s: api.Sector) => s.predios ?? '—' },
              ]}
              filas={r.contenido}
              llave={(s) => s.id}
              vacio="El backend no devolvio ningun sector."
              pie="Los tres conteos los cuenta el servidor con su «SectorConConteos»; aqui no se suma nada."
            />
          )}
        </Lectura>
      </Seccion>

      <Aviso tono="info" titulo="Lo que este panel no dice">
        No hay ninguna cifra de valuacion. El backend sabe valorizar el terreno y hoy no produce ni un importe:
        le falta una llave del conjunto sellado, «PORCENTAJE_DE_ACTUALIZACION», que espera una firma humana
        (D-11). Poner aqui un total seria inventarlo.
      </Aviso>
    </div>
  );
}

/* ── Predios ────────────────────────────────────────────────────────────── */

export function Predios({ ruta, onSujeto, onFiltros }: PantallaProps) {
  const codigo = ruta.filtros.codRefCatastral ?? '';
  const sector = ruta.filtros.codigoDeSector ?? '';
  const predioId = sujetoNumerico(ruta.sujeto);

  const lista = useRecurso(
    (senal) => api.predios({ codRefCatastral: codigo, codigoDeSector: sector }, { tamano: 50 }, senal),
    ['predios', codigo, sector],
  );
  const sectores = useRecurso((senal) => api.sectores({ tamano: 100 }, senal), ['sectores-de-predios']);
  const frentes = useRecurso(
    (senal) => api.frentes(predioId!, senal),
    ['frentes', predioId],
    predioId !== null,
  );

  const fijarFiltro = useCallback(
    (clave: string, valor: string) => onFiltros({ ...ruta.filtros, [clave]: valor }),
    [onFiltros, ruta.filtros],
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <Seccion titulo="Buscar en el padron">
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', padding: '14px 16px' }}>
          <Campo
            rotulo="Codigo de referencia catastral"
            valor={codigo}
            onCambio={(v) => fijarFiltro('codRefCatastral', v)}
            marcador="Los primeros tramos bastan"
            ayuda="El servidor acota por prefijo"
          />
          <Lectura recurso={sectores} espera="">
            {(r) => (
              <Selector
                rotulo="Sector"
                valor={sector}
                onCambio={(v) => fijarFiltro('codigoDeSector', v)}
                opciones={[
                  { valor: '', label: 'Todos' },
                  ...r.contenido.map((s) => ({ valor: s.codigo, label: `${s.codigo} — ${s.nombre}` })),
                ]}
                ayuda="Del catalogo del servidor, no de una lista escrita aqui"
              />
            )}
          </Lectura>
        </div>
      </Seccion>

      <Seccion titulo="El padron">
        <Lectura recurso={lista} espera="">
          {(r) => (
            <Tabla
              columnas={[
                {
                  label: 'Codigo',
                  pinta: (p: api.PredioDelCatastro) => (
                    <button
                      type="button"
                      onClick={() => onSujeto(String(p.predioId))}
                      style={{
                        border: 0,
                        background: 'transparent',
                        padding: 0,
                        color: 'var(--azul)',
                        cursor: 'pointer',
                        fontVariantNumeric: 'tabular-nums',
                      }}
                    >
                      {p.codRefCatastral}
                    </button>
                  ),
                },
                { label: 'Direccion', pinta: (p: api.PredioDelCatastro) => p.direccion },
                { label: 'Via', pinta: (p: api.PredioDelCatastro) => p.via ?? '—' },
                { label: 'Sector', pinta: (p: api.PredioDelCatastro) => p.codigoDeSector ?? '—' },
                {
                  label: 'Estado',
                  pinta: (p: api.PredioDelCatastro) => (
                    <Insignia tono={p.estado === 'ACTIVO' ? 'ok' : 'warn'}>{p.estado}</Insignia>
                  ),
                },
                {
                  label: 'Ficha',
                  pinta: (p: api.PredioDelCatastro) =>
                    p.fichado ? <Insignia tono="ok">Fichado</Insignia> : <Insignia tono="warn">Sin ficha</Insignia>,
                },
              ]}
              filas={r.contenido}
              llave={(p) => p.predioId}
              vacio="Ningun predio del padron cumple lo pedido."
              pie="No hay columna de titular, y no es un olvido: publicarla convertiria «quien puede listar predios» en «quien puede cosechar la correlacion predio-persona de toda la municipalidad». Se resuelve al abrir el predio, de uno en uno."
            />
          )}
        </Lectura>
      </Seccion>

      <Seccion
        titulo="Frentes del predio"
        nota={predioId === null ? undefined : `Predio ${predioId}`}
      >
        <Lectura
          recurso={frentes}
          espera="Elija un predio en la tabla de arriba para ver que frentes se le derivaron y a que via dan."
        >
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
                      <Insignia tono={f.longitudEstado === 'CONFIRMADA' ? 'ok' : 'warn'}>{f.longitudEstado}</Insignia>
                    ),
                  },
                  { label: 'Numeracion', pinta: (f: api.Frente) => f.numeracion ?? '—' },
                  { label: 'Confirmado por', pinta: (f: api.Frente) => f.confirmadoPor ?? '—' },
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
    </div>
  );
}

/* ── Fichas ─────────────────────────────────────────────────────────────── */

export function Fichas({ ruta, onFiltros }: PantallaProps) {
  const codigo = ruta.filtros.codRefCatastral ?? '';
  const tipo = ruta.filtros.tipo ?? '';
  const lista = useRecurso(
    (senal) =>
      api.fichas(
        { codRefCatastral: codigo, tipo: (tipo || undefined) as api.TipoDeFicha | undefined },
        { tamano: 50 },
        senal,
      ),
    ['fichas', codigo, tipo],
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
            opciones={[
              { valor: '', label: 'Todos' },
              ...api.TIPOS_DE_FICHA.map((t) => ({ valor: t, label: t })),
            ]}
            ayuda="Los cuatro del enumerado del backend, letra por letra"
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
                  pinta: (f: api.FichaEncontrada) => f.areaConstruida ?? '—',
                },
                { label: 'Uso', pinta: (f: api.FichaEncontrada) => f.uso },
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
    </div>
  );
}

/* ── Territorio ─────────────────────────────────────────────────────────── */

export function Territorio({ ruta, onSujeto }: PantallaProps) {
  const sectores = useRecurso((senal) => api.sectores({ tamano: 100 }, senal), ['territorio-sectores']);
  const vias = useRecurso((senal) => api.vias({}, { tamano: 100 }, senal), ['territorio-vias']);
  const sector = ruta.sujeto;
  const manzanas = useRecurso(
    (senal) => api.manzanas(sector, { tamano: 100 }, senal),
    ['manzanas', sector],
    sector !== '',
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <Seccion titulo="Sectores">
        <Lectura recurso={sectores} espera="">
          {(r) => (
            <Tabla
              columnas={[
                {
                  label: 'Codigo',
                  pinta: (s: api.Sector) => (
                    <button
                      type="button"
                      onClick={() => onSujeto(s.codigo)}
                      style={{ border: 0, background: 'transparent', padding: 0, color: 'var(--azul)', cursor: 'pointer' }}
                    >
                      {s.codigo}
                    </button>
                  ),
                },
                { label: 'Nombre', pinta: (s: api.Sector) => s.nombre },
                { label: 'Zona', pinta: (s: api.Sector) => s.zona ?? '—' },
                { label: 'Manzanas', numerica: true, pinta: (s: api.Sector) => s.manzanas ?? '—' },
                { label: 'Predios', numerica: true, pinta: (s: api.Sector) => s.predios ?? '—' },
              ]}
              filas={r.contenido}
              llave={(s) => s.id}
              vacio="El backend no devolvio ningun sector."
            />
          )}
        </Lectura>
      </Seccion>

      <Seccion titulo="Manzanas del sector" nota={sector === '' ? undefined : `Sector ${sector}`}>
        <Lectura recurso={manzanas} espera="Elija un sector arriba y aqui saldran sus manzanas.">
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Manzana', pinta: (m: api.Manzana) => m.codigo },
                { label: 'Sector', pinta: (m: api.Manzana) => m.sectorCodigo },
                { label: 'Predios', numerica: true, pinta: (m: api.Manzana) => m.predios ?? '—' },
              ]}
              filas={r.contenido}
              llave={(m) => m.id}
              vacio="Este sector no tiene ninguna manzana registrada."
            />
          )}
        </Lectura>
      </Seccion>

      <Seccion titulo="Catalogo vial">
        <Lectura recurso={vias} espera="">
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Codigo', pinta: (v: api.Via) => v.codigo },
                { label: 'Tipo', pinta: (v: api.Via) => v.tipo },
                { label: 'Nombre', pinta: (v: api.Via) => v.nombre },
                { label: 'Ubigeo', pinta: (v: api.Via) => v.ubigeo ?? '—' },
                {
                  label: 'Activa',
                  pinta: (v: api.Via) => (v.activa ? <Insignia tono="ok">Si</Insignia> : <Insignia tono="bad">No</Insignia>),
                },
              ]}
              filas={r.contenido}
              llave={(v) => v.id}
              vacio="El catalogo vial esta vacio."
              pie="La via sale del catalogo y no se escribe libre: dos formas de escribir la misma calle producen dos direcciones que nadie cruza."
            />
          )}
        </Lectura>
      </Seccion>

      <Aviso tono="warn" titulo="Dos permisos que ningun catalogo declara">
        Esta pantalla exige «sectores» y «calles». Los dos existen como «@RequiereAcceso» en el backend y{' '}
        <strong>ninguno de los dos esta en «CatalogoDelSistema.opciones()»</strong>, que declara catorce. La
        causa esta medida: sus controladores pasan el acceso como CONSTANTE —«SectorController.ACCESO»— y la
        guarda que compara los dos conjuntos busca literales de cadena, asi que no los ve y sigue en verde. Hasta
        que se siembren, nadie puede recibir estos dos permisos y esta pantalla contestara 403.
      </Aviso>
    </div>
  );
}

/* ── Plano catastral ────────────────────────────────────────────────────── */

export function Plano({ ruta, onSujeto }: PantallaProps) {
  const sector = ruta.sujeto;
  const marco = useRecurso(
    (senal) => api.marcoDelPlano({ codigoDeSector: sector || undefined }, senal),
    ['marco', sector],
  );
  const lotes = useRecurso(
    (senal) => api.plano({ codigoDeSector: sector || undefined }, senal),
    ['plano', sector],
  );
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
                { label: 'Manzana', pinta: (l: api.LoteDelPlano) => l.codigoDeManzana ?? '—' },
                { label: 'Lote', pinta: (l: api.LoteDelPlano) => l.lote ?? '—' },
              ]}
              filas={r.lotes}
              llave={(l) => l.predioId}
              vacio={`Ningun lote de este ambito tiene poligono. Sin geometria: ${r.sinGeometria}.`}
              pie="Aqui no se dibuja ningun mapa, y no por falta de datos: elegir la libreria es una decision propia (ADR-0022 y ADR-0037) y no la toma este trabajo. Lo que se ensena es lo que el backend publica: el marco, y cuantos lotes se quedan fuera por no tener poligono."
            />
          )}
        </Lectura>
      </Seccion>
    </div>
  );
}

/* ── Valores del ejercicio ──────────────────────────────────────────────── */

export function Valores({ ejercicio }: PantallaProps) {
  const aranceles = useRecurso((senal) => api.aranceles(ejercicio, senal), ['aranceles', ejercicio]);
  const unitarios = useRecurso((senal) => api.valoresUnitarios(ejercicio, senal), ['unitarios', ejercicio]);
  const deprec = useRecurso((senal) => api.depreciacion(ejercicio, senal), ['depreciacion', ejercicio]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 1180 }}>
      <Aviso tono="info" titulo="Estos cuadros no se sellan aqui">
        «catastro» no sella ningun valor normativo: eso es «normativa». Lo que se lee aqui es la copia local del
        conjunto sellado del ejercicio {ejercicio}, y si ese ejercicio no tiene conjunto, las tres lecturas
        contestan que no hay de donde leer.
      </Aviso>

      <Seccion titulo="Aranceles de terreno" nota={`Ejercicio ${ejercicio}`}>
        <Lectura recurso={aranceles} espera="">
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Via', numerica: true, pinta: (a: api.Arancel) => a.viaId },
                { label: 'Tramo', pinta: (a: api.Arancel) => a.tramo ?? 'Sin tramo' },
                { label: 'Valor por m2', numerica: true, pinta: (a: api.Arancel) => a.valorM2 },
                { label: 'Fuente', pinta: (a: api.Arancel) => a.documentoFuente },
              ]}
              filas={r}
              llave={(a) => a.id}
              vacio="El conjunto sellado no trae ningun arancel."
            />
          )}
        </Lectura>
      </Seccion>

      <Seccion titulo="Valores unitarios de edificacion" nota={`Ejercicio ${ejercicio}`}>
        <Lectura recurso={unitarios} espera="">
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Partida', pinta: (v: api.ValorUnitario) => v.partida },
                { label: 'Categoria', pinta: (v: api.ValorUnitario) => v.categoria },
                { label: 'Valor por m2', numerica: true, pinta: (v: api.ValorUnitario) => v.valorM2 },
                { label: 'Fuente', pinta: (v: api.ValorUnitario) => v.documentoFuente },
              ]}
              filas={r}
              llave={(v) => v.id}
              vacio="El conjunto sellado no trae el cuadro de valores unitarios."
            />
          )}
        </Lectura>
      </Seccion>

      <Seccion titulo="Tabla de depreciacion" nota={`Ejercicio ${ejercicio}`}>
        <Lectura recurso={deprec} espera="">
          {(r) => (
            <Tabla
              columnas={[
                { label: 'Uso', pinta: (d: api.Depreciacion) => d.uso },
                { label: 'Material', pinta: (d: api.Depreciacion) => d.material },
                { label: 'Conservacion', pinta: (d: api.Depreciacion) => d.estadoConservacion },
                {
                  label: 'Antiguedad hasta',
                  numerica: true,
                  pinta: (d: api.Depreciacion) => d.antiguedadHasta ?? '—',
                },
                { label: 'Porcentaje', numerica: true, pinta: (d: api.Depreciacion) => d.porcentaje },
              ]}
              filas={r}
              llave={(d) => d.id}
              vacio="El conjunto sellado no trae el cuadro de depreciacion."
              pie="Que tabla del Anexo I le toca a cada uso de ficha sigue sin decidirse (RT-004): «depreciacion.md» §3 dice que traducirlo es criterio y no transcripcion, y por eso el backend no lo inventa."
            />
          )}
        </Lectura>
      </Seccion>
    </div>
  );
}
