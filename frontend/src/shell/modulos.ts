import { ICO } from '../ds/iconos';
import type { Trazos } from '../ds/Icono';
import { SESION_PROPIA } from '../api/parametros';

/**
 * El registro de los modulos de `catastro-web`.
 *
 * <h2>Seis, y no los doce del artboard</h2>
 *
 * `CatastroV6.dc.html` dibuja el arbol de los **doce modulos del monolito
 * SGTM** —Transito, Tesoreria, Coactiva, Seguridad…— porque dibuja el marco de
 * un sistema entero. Este repositorio no es ese sistema: ADR-0029 reparte el
 * monolito en cuatro, y lo que aqui hay son los **cinco modulos de backend**
 * (`nucleo`, `urbano`, `grd`, `fiscalizacion`, `parametros`) mas la ventanilla,
 * que cuelga de `nucleo` pero de otro `acceso`. Portar los doce dibujaria diez
 * modulos que ningun endpoint de este backend sirve.
 *
 * <h2>Ni `pastilla` ni `tono`, a proposito</h2>
 *
 * Un recuento solo lo puede poner quien lo ha contado, y eso es la pantalla. Si
 * el catalogo pudiera declararlos, la cifra del prototipo volveria a entrar por
 * aqui sin que nadie la contara — y `yarn sin-red` la cazaria, pero solo despues
 * de que alguien la hubiera escrito.
 *
 * <h2>El `acceso` es dato, y hay quien lo contrasta</h2>
 *
 * Cada hoja declara el `acceso` de la `@RequiereAcceso` que protege lo que lee.
 * `verificaciones/rutas.mjs` compara esta lista con `backend/` y nombra los que
 * ningun `CatalogoDelSistema` declara: una lista escrita a mano que nadie
 * contrasta es el segundo sitio donde el catalogo puede estar mal.
 */
export type Hoja = {
  k: string;
  label: string;
  nota: string;
  /** El `acceso` de la `@RequiereAcceso` que protege la lectura principal. */
  acceso: string;
  /** Los demas, cuando la pantalla lee de mas de un recurso. */
  tambien?: readonly string[];
};

export type Modulo = {
  k: string;
  label: string;
  nota: string;
  icono: Trazos;
  hojas: readonly Hoja[];
};

export const MODULOS: readonly Modulo[] = [
  {
    k: 'catastro',
    label: 'Catastro',
    nota: 'Predios y valuacion',
    icono: ICO.catastro,
    hojas: [
      {
        k: 'panel',
        label: 'Panel',
        nota: 'Lo que el padron dice hoy',
        acceso: 'consulta_fichas',
        tambien: ['actualizacion_catastro'],
      },
      {
        k: 'predios',
        label: 'Predios',
        nota: 'El padron, y el frente de cada lote',
        acceso: 'actualizacion_catastro',
      },
      { k: 'fichas', label: 'Fichas', nota: 'La grilla de fichas versionadas', acceso: 'consulta_fichas' },
      {
        k: 'territorio',
        label: 'Territorio',
        nota: 'Sectores, manzanas y catalogo vial',
        acceso: 'sectores',
        tambien: ['calles'],
      },
      {
        k: 'plano',
        label: 'Plano catastral',
        nota: 'El marco de lo levantado',
        acceso: 'consulta_fichas',
      },
      {
        k: 'valores',
        label: 'Valores del ejercicio',
        nota: 'Aranceles, valores unitarios y depreciacion',
        acceso: 'aranceles',
        tambien: ['valores_unitarios', 'depreciacion'],
      },
    ],
  },
  {
    k: 'urbano',
    label: 'Urbano',
    nota: 'Zonificacion vigente',
    icono: ICO.urbano,
    hojas: [
      {
        k: 'zonificacion',
        label: 'Zonificacion',
        nota: 'A que zona cae un predio',
        acceso: 'zonificacion',
      },
    ],
  },
  {
    k: 'riesgo',
    label: 'Riesgo (GRD)',
    nota: 'Peligro, fajas e ITSE',
    icono: ICO.riesgo,
    hojas: [
      {
        k: 'predio',
        label: 'Riesgo del predio',
        nota: 'Zonas de peligro y fajas marginales',
        acceso: 'gestion_del_riesgo',
      },
      {
        k: 'itse',
        label: 'Certificados ITSE',
        nota: 'Los vigentes a una fecha',
        acceso: 'gestion_del_riesgo',
      },
    ],
  },
  {
    k: 'fiscalizacion',
    label: 'Fiscalizacion',
    nota: 'Hallazgo catastral',
    icono: ICO.fiscalizacion,
    hojas: [
      {
        k: 'campanias',
        label: 'Campanias',
        nota: 'El embudo de una campania',
        acceso: 'fiscalizacion_catastral',
      },
      {
        k: 'candidatos',
        label: 'Candidatos',
        nota: 'Lo detectado, antes de verificarlo',
        acceso: 'fiscalizacion_catastral',
      },
      {
        k: 'hallazgos',
        label: 'Hallazgos',
        nota: 'Lo verificado en campo',
        acceso: 'fiscalizacion_catastral',
      },
      {
        k: 'actas',
        label: 'Actas',
        nota: 'El acto, y la evidencia que lo sostiene',
        acceso: 'fiscalizacion_catastral',
      },
    ],
  },
  {
    k: 'consultas',
    label: 'Consultas',
    nota: 'Ventanilla',
    icono: ICO.consultas,
    hojas: [
      {
        k: 'resumen',
        label: 'Resumen predial',
        nota: 'Los predios de un contribuyente',
        acceso: 'consulta_resumen_predial',
      },
      {
        k: 'ficha-contribuyente',
        label: 'Ficha del contribuyente',
        nota: 'La hoja, y su documento',
        acceso: 'ficha_contribuyente_reporte',
      },
    ],
  },
  {
    k: 'parametros',
    label: 'Parametros',
    nota: 'El ejercicio sellado',
    icono: ICO.parametros,
    hojas: [
      {
        k: 'ejercicio',
        label: 'Ejercicio',
        nota: 'Si el conjunto esta sellado, y cual',
        /* El centinela de `RequiereAcceso`, no una opcion del catalogo: esta
           lectura solo exige tener sesion. Escribirla como si fuera una opcion
           inventaria un permiso que nadie puede conceder. */
        acceso: SESION_PROPIA,
      },
    ],
  },
];

/** Todos los accesos que esta interfaz dice necesitar, sin repetir. */
export const ACCESOS: readonly string[] = [
  ...new Set(MODULOS.flatMap((m) => m.hojas.flatMap((h) => [h.acceso, ...(h.tambien ?? [])]))),
].sort();

/** Un destino, resuelto: `catastro/predios` -> su modulo y su hoja. */
export type Destino = { modulo: Modulo; hoja: Hoja };

export function destinoDe(modulo: string, hoja: string): Destino | null {
  const m = MODULOS.find((x) => x.k === modulo);
  if (!m) return null;
  const h = m.hojas.find((x) => x.k === hoja);
  return h ? { modulo: m, hoja: h } : null;
}

/** Todos los destinos, en el orden del panel. Es lo que recorren los arneses. */
export const DESTINOS: readonly { modulo: string; hoja: string; label: string }[] = MODULOS.flatMap((m) =>
  m.hojas.map((h) => ({ modulo: m.k, hoja: h.k, label: `${m.label} · ${h.label}` })),
);

/** El primero, que es lo que se abre al entrar sin ruta. */
export const DESTINO_INICIAL = { modulo: 'catastro', hoja: 'panel' } as const;
