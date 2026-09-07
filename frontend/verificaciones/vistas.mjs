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
  /* Las OTRAS TRES clases de ficha, y no por completismo: los tres bloques de
     detalle —`economico`, `bienesComunes` y `rural`— son nulos salvo el que toca,
     y la ficha UNICA no tiene ninguno. Con el predio 1 a secas, los tres bloques
     que #46 dibuja no se dibujarian nunca y los arneses informarian en verde
     sobre codigo que ninguna vista alcanza. Los sujetos salen del padron de
     demostracion: 3 es ECONOMICA, 14 BIENES_COMUNES y 21 RURAL. */
  { modulo: 'catastro', hoja: 'predios', sujeto: '3', filtros: { ver: 'ficha' }, nombre: 'ficha economica' },
  { modulo: 'catastro', hoja: 'predios', sujeto: '14', filtros: { ver: 'ficha' }, nombre: 'ficha de bienes comunes' },
  { modulo: 'catastro', hoja: 'predios', sujeto: '21', filtros: { ver: 'ficha' }, nombre: 'ficha rural' },
  /* Y el predio sin detalle: el terreno sin construir, cuya ficha se queda con
     cero construcciones y cero obras. Es un caso de verdad —lo dice la cabecera
     de `detalle-de-fichas.csv`— y es donde se ve que una tabla vacia dice por que
     lo esta en vez de quedarse en blanco. */
  { modulo: 'catastro', hoja: 'predios', sujeto: '20', filtros: { ver: 'ficha' }, nombre: 'ficha sin construcciones' },
  { modulo: 'catastro', hoja: 'predios', sujeto: '1', filtros: { ver: 'movimientos' }, nombre: 'movimientos del predio' },
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
  /* Los hallazgos que fiscalizacion le encontro a un predio (#71, AC-4). El
     sujeto es 3 porque es el unico predio del padron de demostracion con un
     hallazgo firme y su acta: con otro se dibujaria el estado vacio, que tambien
     hay que ver pero no ensena ni la tabla ni el acta. */
  { modulo: 'catastro', hoja: 'predios', sujeto: '3', filtros: { ver: 'hallazgos' }, nombre: 'hallazgos del predio' },

  /* Fiscalizacion (#71): las nueve operaciones que #71 anade viven en ESTADOS de
     estas cuatro hojas —el formulario de un acto se abre desde la ruta, como el
     asistente de alta—, asi que con el destino a secas no se dibuja ni uno. Sin
     estas nueve entradas los tres arneses informarian en verde sobre el ciclo
     entero, que es justo lo que este issue existe para poder recorrer. */
  { modulo: 'fiscalizacion', hoja: 'campanias', sujeto: '1', nombre: 'embudo de la campania' },
  {
    modulo: 'fiscalizacion',
    hoja: 'campanias',
    filtros: { acto: 'abrirCampania' },
    nombre: 'abrir una campania',
  },
  {
    modulo: 'fiscalizacion',
    hoja: 'campanias',
    sujeto: '1',
    filtros: { acto: 'cerrarCampania' },
    nombre: 'cerrar la campania',
  },
  { modulo: 'fiscalizacion', hoja: 'candidatos', sujeto: '1', nombre: 'cola de candidatos' },
  {
    modulo: 'fiscalizacion',
    hoja: 'candidatos',
    sujeto: '1',
    filtros: { acto: 'admitirEnGabinete', candidato: '1' },
    nombre: 'compuerta de gabinete',
  },
  /* El candidato 2 y no el 1: la segunda compuerta solo se le puede ofrecer a
     uno que ya paso por gabinete, y el 1 esta DETECTADO. Abrir el acto sobre el
     que no lo admite dibujaria un formulario que la propia cola no ofrece. */
  {
    modulo: 'fiscalizacion',
    hoja: 'candidatos',
    sujeto: '1',
    filtros: { acto: 'verificarEnCampo', candidato: '2' },
    nombre: 'verificacion en campo',
  },
  { modulo: 'fiscalizacion', hoja: 'hallazgos', sujeto: '1', nombre: 'hallazgos de la campania' },
  {
    modulo: 'fiscalizacion',
    hoja: 'hallazgos',
    sujeto: '1',
    filtros: { acto: 'dejarSinEfecto', hallazgo: '1' },
    nombre: 'dejar un hallazgo sin efecto',
  },
  {
    modulo: 'fiscalizacion',
    hoja: 'actas',
    sujeto: '1',
    filtros: { acto: 'levantarActa' },
    nombre: 'levantar el acta',
  },
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
