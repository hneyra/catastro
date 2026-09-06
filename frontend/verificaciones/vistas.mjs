/**
 * Los ESTADOS de una pantalla que hay que mirar, y no solo su destino.
 *
 * `DESTINOS` sale del registro y abre cada hoja **vacia**: sin sujeto y sin
 * filtros. Eso basta mientras una hoja sea una tabla, y deja de bastar en cuanto
 * es un maestro-detalle o una hoja con pestanas: con `#/catastro/predios` a
 * secas, el panel de detalle nunca se dibuja, la matriz de valores unitarios
 * nunca se pivota y **el arnes informa en verde sobre la mitad de la pantalla**.
 *
 * Por eso esto es una lista escrita a mano, y por eso lleva su propia guarda:
 * `comprobarVistas` exige que cada entrada nombre un destino que el registro
 * declare. Una lista a mano que nadie contrasta se queda vieja sin ruido, que es
 * justo lo que `registro.mjs` existe para evitar en la otra mitad.
 *
 * El sujeto de los predios es `1` porque es el unico del padron de demostracion
 * con poligono levantado (`PREDIO_CON_POLIGONO`), o sea el unico que recorre el
 * camino feliz de zonificacion y de riesgo. Los demas caminos —el 422 por lote
 * sin geometria— ya salen con el destino a secas.
 */

/** @type {readonly {modulo: string, hoja: string, sujeto?: string, filtros?: Record<string,string>, nombre: string}[]} */
export const VISTAS = [
  { modulo: 'catastro', hoja: 'predios', sujeto: '1', nombre: 'predio elegido' },
  { modulo: 'catastro', hoja: 'predios', sujeto: '1', filtros: { ver: 'ficha' }, nombre: 'ficha del predio' },
  { modulo: 'catastro', hoja: 'predios', sujeto: '1', filtros: { ver: 'frentes' }, nombre: 'frentes del predio' },
  { modulo: 'catastro', hoja: 'predios', filtros: { fichado: 'false' }, nombre: 'cola de predios sin ficha' },
  /* El alta (#34): es un ESTADO de Predios y no un destino, asi que sin estas
     tres entradas los arneses informan en verde sobre la unica escritura de
     esta interfaz. Van los tres pasos que dibujan cosas distintas: el primero
     —donde el boton «Anterior» nace apagado—, el de la via del catalogo, y el
     ULTIMO, que es el unico donde el primario aparece y donde vive el resumen.
     Con el destino a secas no se dibuja ninguno. */
  { modulo: 'catastro', hoja: 'predios', sujeto: 'nuevo', nombre: 'alta de ficha' },
  {
    modulo: 'catastro',
    hoja: 'predios',
    sujeto: 'nuevo',
    filtros: { paso: 'ubic' },
    nombre: 'alta de ficha en ubicacion',
  },
  {
    modulo: 'catastro',
    hoja: 'predios',
    sujeto: 'nuevo',
    filtros: { paso: 'verif' },
    nombre: 'alta de ficha en verificacion',
  },
  { modulo: 'catastro', hoja: 'territorio', sujeto: '02', nombre: 'manzanas de otro sector' },
  { modulo: 'catastro', hoja: 'territorio', sujeto: 'vias', nombre: 'catalogo vial' },
  { modulo: 'catastro', hoja: 'valores', filtros: { cuadro: 'unitarios' }, nombre: 'matriz de valores unitarios' },
  { modulo: 'catastro', hoja: 'valores', filtros: { cuadro: 'depreciacion' }, nombre: 'matriz de depreciacion' },
  { modulo: 'urbano', hoja: 'zonificacion', sujeto: '1', nombre: 'zona de un predio con poligono' },
  { modulo: 'riesgo', hoja: 'itse', sujeto: '1', nombre: 'certificados de un predio' },
];

/** `#/catastro/predios/1?ver=ficha` */
export function hashDe(vista) {
  const camino = ['#', vista.modulo, vista.hoja];
  if (vista.sujeto) camino.push(encodeURIComponent(vista.sujeto));
  const consulta = new URLSearchParams(vista.filtros ?? {}).toString();
  return camino.join('/') + (consulta ? `?${consulta}` : '');
}

/**
 * Que toda vista nombre un destino del registro.
 *
 * Devuelve la lista de problemas; vacia si todo cuadra. Se comprueba en cada
 * arnes que use las vistas, y no una vez: un destino que se renombra deja las
 * dos mitades desincronizadas y la unica senal seria una captura de la pantalla
 * de «destino desconocido», que **no esta vacia** y por tanto pasa en verde.
 */
export function comprobarVistas(destinos) {
  const conocidos = new Set(destinos.map((d) => `${d.modulo}/${d.hoja}`));
  return VISTAS.filter((v) => !conocidos.has(`${v.modulo}/${v.hoja}`)).map(
    (v) =>
      `la vista «${v.nombre}» apunta a «${v.modulo}/${v.hoja}», que no es ningun destino del registro. ` +
      'El armazon lo resolveria al destino inicial y la captura saldria llena, asi que esto no se ve mirando.',
  );
}
