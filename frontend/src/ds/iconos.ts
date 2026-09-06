import type { Trazos } from './Icono';

/**
 * Los trazos del artboard `CatastroV6.dc.html`, copiados literales de sus
 * tablas `MODULOS` (lineas 946-959) e `ICO_SEC` (lineas 961-966).
 *
 * Los seis modulos de ESTE sistema no son los doce del monolito, asi que solo
 * viajan los que tienen a quien representar aqui, y los que faltan —riesgo,
 * urbano— toman el trazo del modulo del monolito que el artboard les dibuja mas
 * cerca. Se dice de donde sale cada uno.
 */
export const ICO = {
  /** `MODULOS['Catastro']`. */
  catastro: [
    'M3.5 6.6 9 4.2l6 2.4 5.5-2.4v13.2L15 19.8l-6-2.4-5.5 2.4z',
    'M9 4.2v13.2',
    'M15 6.6v13.2',
  ],
  /** `MODULOS['Autorizaciones y licencias']`: lo urbano del monolito. */
  urbano: ['M4.4 9.6V20h15.2V9.6', 'M3.2 9.6 5.2 4.6h13.6l2 5z', 'M9.6 20v-5.4h4.8V20'],
  /** `MODULOS['Infracciones administrativas']`: el triangulo de peligro. */
  riesgo: ['M12 4.2 20.8 19.6H3.2z', 'M12 9.8v4.4', 'M12 17.1h.02'],
  /** `MODULOS['Fiscalizacion']`. */
  fiscalizacion: [
    'M9.5 4.5H8A1.5 1.5 0 0 0 6.5 6v13A1.5 1.5 0 0 0 8 20.5h8a1.5 1.5 0 0 0 1.5-1.5V6A1.5 1.5 0 0 0 16 4.5h-1.5',
    'M9.5 3.2h5v2.8h-5z',
    'M9.6 13.2l2 2 3.4-4',
  ],
  /** `MODULOS['Consultas']`. */
  consultas: ['M17.4 11a6.4 6.4 0 1 1-12.8 0 6.4 6.4 0 0 1 12.8 0', 'M15.8 15.8 20.6 20.6'],
  /** `MODULOS['Valores']`. */
  parametros: [
    'M6.5 3.5h7.5l4 4v13h-11.5z',
    'M14 3.5v4h4',
    'M9.5 11.5h5',
    'M15.6 16.4a2.3 2.3 0 1 1-4.6 0 2.3 2.3 0 0 1 4.6 0',
  ],

  /* `ICO_SEC`: los iconos de los submodulos. */
  panel: ['M4 19.5h16', 'M6.5 19.5V9', 'M11 19.5V5.5', 'M15.5 19.5v-7', 'M20 19.5v-11'],
  predios: ['M3.5 6.6 9 4.2l6 2.4 5.5-2.4v13.2L15 19.8l-6-2.4-5.5 2.4z', 'M9 4.2v13.2'],
  territorio: ['M4.5 4.5h6v6h-6z', 'M13.5 4.5h6v6h-6z', 'M4.5 13.5h6v6h-6z', 'M13.5 13.5h6v6h-6z'],
  valores: ['M6.5 3.5h7.5l4 4v13h-11.5z', 'M14 3.5v4h4', 'M9.5 12.5h5'],

  /* Del surtido comun del artboard: la lupa, la cruz, el chevron, la campana. */
  lupa: ['M17.4 11a6.4 6.4 0 1 1-12.8 0 6.4 6.4 0 0 1 12.8 0', 'M15.8 15.8 20.6 20.6'],
  cruz: ['M6 6l12 12M18 6L6 18'],
  /* El mas de la accion primaria: registrar un predio. */
  mas: ['M12 5v14M5 12h14'],
  chevron: ['M6 9.5l6 6 6-6'],
  barras: ['M4 7h16M4 12h16M4 17h16'],
  campana: ['M18 15.6V10.5a6 6 0 0 0-12 0v5.1L4.4 18h15.2z', 'M9.8 18a2.2 2.2 0 0 0 4.4 0'],
  mapa: ['M3.5 6.6 9 4.2l6 2.4 5.5-2.4v13.2L15 19.8l-6-2.4-5.5 2.4z'],
  documento: ['M6.5 3.5h7.5l4 4v13h-11.5z', 'M14 3.5v4h4', 'M9.5 12.5h5'],
  persona: [
    'M12 7.4a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
    'M3.6 20c0-3 2.4-4.6 5.4-4.6s5.4 1.6 5.4 4.6',
  ],
  candado: ['M7 11V8a5 5 0 0 1 10 0v3', 'M5.5 11h13v9.5h-13z'],
  salir: [
    'M9.5 20H6A1.5 1.5 0 0 1 4.5 18.5v-13A1.5 1.5 0 0 1 6 4h3.5',
    'M14 8l4 4-4 4',
    'M18 12H9',
  ],
  aviso: ['M12 4.2 20.8 19.6H3.2z', 'M12 9.8v4.4', 'M12 17.1h.02'],
  vacio: ['M6.5 3.5h7.5l4 4v13h-11.5z', 'M14 3.5v4h4', 'M9.5 12.5h5'],
} satisfies Record<string, Trazos>;
