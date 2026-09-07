/**
 * Proxy de datos: la API de `catastro`, simulada en el navegador.
 *
 * Hereda enteras las cuatro decisiones de ADR-0010 de `sgtm`.
 *
 * <h2>1. Intercepta en la frontera del TRANSPORTE, no en la de la aplicacion</h2>
 *
 * Sustituye `fetch` y devuelve `Response` de verdad, con su codigo de estado y
 * su `application/problem+json`. **No es un adaptador que la aplicacion elija.**
 * La salida facil habria sido que cada pantalla leyera sus datos de una
 * constante importada; la trampa de esa salida es que el dia que el backend
 * exista habria que reescribir las pantallas para que pidan por HTTP. Asi no:
 * la pantalla llama a `solicitar()` con la ruta real —`GET
 * /catastro/api/v1/catastro/predios`— y todo el camino se ejerce; la URL se
 * compone, los parametros viajan, el token se adjunta, el error se convierte en
 * `ErrorDeApi`.
 *
 * <h2>2. Se apaga con una bandera, y la rama entera desaparece del paquete</h2>
 *
 * `VITE_CATASTRO_PROXY_DE_DATOS=false`. Se carga con `import()` dinamico desde
 * `main.tsx`, asi que apagado no viaja: Vite lo deja en un trozo aparte que
 * nadie pide. Con latencia simulada, para que los estados de carga se vean en
 * desarrollo.
 *
 * <h2>3. Se apaga tambien operacion por operacion</h2>
 *
 * `servidas.ts` exporta `YA_SERVIDAS`, y lo que este ahi pasa al backend. Nace
 * vacia y crece hasta cubrir las 64.
 *
 * <h2>4. No finge lo que no sabe</h2>
 *
 * **No filtra, no ordena, no pagina, no valida y no persiste.** Un proxy que
 * fingiera la semantica de `?uso=Comercio` estaria inventando un comportamiento
 * que el backend todavia no ha decidido, y la interfaz acabaria construida
 * contra esa invencion. Filtrar es del servidor: aqui la peticion se hace de
 * verdad y la respuesta es siempre el juego de datos completo. Lo mismo con las
 * escrituras: un `POST` responde con el recurso y no guarda nada.
 *
 * Lo unico que el sujeto decide es **cual de las respuestas que el backend YA
 * TIENE ESCRITAS toca** —la zona o el 422 por predio sin poligono—, que es
 * reproducir una bifurcacion existente y no inventar una tercera.
 *
 * <h2>Por que no MSW</h2>
 *
 * Hace lo mismo y mejor, con un *service worker*. Descartada por una dependencia
 * mas para el encaminamiento de este archivo, cuyo unico trabajo es desaparecer.
 */
import { RAIZ } from '../api/cliente';
import { YA_SERVIDAS, laSirveElBackend } from './servidas';
import type { OperacionServida } from './servidas';
import * as D from './datos';
import * as catalogo from './catalogo';
import * as ciclo from './ciclo';
import { ok, pagina, problema } from './respuestas';
import type { Respuesta } from './respuestas';

/** Latencia simulada, para que los estados de carga se vean en desarrollo. */
const LATENCIA_MINIMA_MS = 120;
const LATENCIA_MAXIMA_MS = 320;

type Parametros = Record<string, string>;
/**
 * `cuerpo` es lo que la peticion mando, y solo lo traen las escrituras.
 *
 * Hace falta desde #34: las cuatro altas de ficha se distinguen entre si por lo
 * que lleva el cuerpo, y sin el las tres respuestas que el backend YA TIENE
 * ESCRITAS —el 409 del codigo repetido, el 404 de la referencia que no existe y
 * el 201— serian indistinguibles.
 *
 * **Y desde #71 hay una excepcion, una sola y declarada**: el ciclo de
 * fiscalizacion (`ciclo.ts`) recuerda lo que se le escribe mientras dure la
 * pagina, porque simula una maquina de estados y sin memoria produciria un par
 * de respuestas que el backend no puede producir —un `200` que admite un
 * candidato y un `GET` siguiente que lo devuelve sin admitir—. Lo demas sigue
 * sin persistir nada.
 */
type Contexto = { parametros: Parametros; consulta: URLSearchParams; cuerpo: unknown };
type Manejador = (contexto: Contexto) => Respuesta;

const esperar = (ms: number) => new Promise((listo) => setTimeout(listo, ms));

function predioDe(contexto: Contexto, clave = 'predioId'): D.FilaDelPadron | undefined {
  const id = Number(contexto.parametros[clave] ?? contexto.consulta.get(clave) ?? '');
  return D.PADRON.find((p) => p.predioId === id);
}

/**
 * Las cuatro altas de ficha, y **que se simula de cada una**.
 *
 * <h2>No valida, no persiste, y aun asi contesta tres cosas distintas</h2>
 *
 * La cuarta decision de ADR-0010 dice que este proxy no finge lo que no sabe:
 * no filtra, no ordena, no pagina, no valida y no persiste. Lo unico que decide
 * es **cual de las respuestas que el backend YA TIENE ESCRITAS toca**, y el alta
 * tiene tres que se deciden con datos que este proxy ya tiene:
 *
 *   · **409** si el codigo de referencia catastral ya es de un predio del
 *     padron. Es `DuplicateKeyException` en el backend, y es el desenlace que la
 *     pantalla necesita poder ejercer: sin el, el aviso del codigo duplicado
 *     —que es la mitad del AC-2— no se dibujaria nunca.
 *   · **404** si el sector o la manzana que el codigo nombra no estan en el
 *     territorio simulado. Es `InscribirFicha.ReferenciaInexistente`.
 *   · **422** si falta la observacion. Es `DeclaracionDeFicha.observacionDe`, o
 *     sea RNF-052, y es la unica validacion que se reproduce porque es la unica
 *     que no depende de ningun dato: es una regla del sistema entero.
 *
 * Todo lo demas contesta **la ficha creada, sin guardar nada**. La siguiente
 * lectura del padron no la trae, y eso es correcto: fingir que si obligaria a
 * este archivo a tener memoria, que es exactamente lo que ADR-0010 le prohibe.
 */
function altaDeFicha(tipo: string): Manejador {
  return (c) => {
    const cuerpo = (c.cuerpo ?? {}) as Record<string, unknown>;
    const codigo = typeof cuerpo.codRefCatastral === 'string' ? cuerpo.codRefCatastral : '';
    const observacion = typeof cuerpo.observacion === 'string' ? cuerpo.observacion.trim() : '';
    const sector = typeof cuerpo.codigoDeSector === 'string' ? cuerpo.codigoDeSector : null;
    const manzana = typeof cuerpo.codigoDeManzana === 'string' ? cuerpo.codigoDeManzana : null;

    if (observacion === '') {
      return problema(
        'VALIDACION',
        422,
        'Toda modificacion exige la observacion del usuario: sin ella no se guarda',
      );
    }
    const yaEsta = D.PADRON.find((p) => p.codRefCatastral === codigo);
    if (yaEsta) {
      return problema(
        'CONFLICTO',
        409,
        `Ya existe un predio con el codigo de referencia catastral '${codigo}' en esta municipalidad`,
      );
    }
    if (sector !== null && !D.SECTORES.some((s) => s.codigo === sector)) {
      return problema('NO_ENCONTRADO', 404, `No existe el sector '${sector}'`);
    }
    if (manzana !== null && !D.MANZANAS.some((m) => m.codigo === manzana && m.sectorCodigo === sector)) {
      return problema('NO_ENCONTRADO', 404, `No existe la manzana '${manzana}' del sector '${sector ?? ''}'`);
    }

    /* La ficha creada, con la forma de `FichaResource` y **sin guardar nada**.
       El `predioId` es el que seguiria al ultimo del padron: es lo unico que se
       inventa, y se inventa porque el backend lo devuelve y la pantalla lo usa
       para abrir el predio recien creado. */
    return {
      estado: 201,
      cuerpo: {
        id: D.PADRON.length + 1,
        predioId: D.PADRON.length + 1,
        tipo,
        version: 1,
        areaTerreno: typeof cuerpo.areaTerreno === 'string' ? cuerpo.areaTerreno : null,
        uso: typeof cuerpo.uso === 'string' ? cuerpo.uso : null,
        frontis: null,
        condicionPropiedad: null,
        tipoEdificacion: null,
        vigenciaDesde: typeof cuerpo.vigenciaDesde === 'string' ? cuerpo.vigenciaDesde : D.HOY,
        vigenciaHasta: null,
        vigente: true,
        origen: typeof cuerpo.origen === 'string' ? cuerpo.origen : 'DECLARACION_JURADA',
        documentoOrigen: typeof cuerpo.documentoOrigen === 'string' ? cuerpo.documentoOrigen : null,
        observacion,
        denominacion: typeof cuerpo.denominacion === 'string' ? cuerpo.denominacion : null,
        construcciones: [],
        /* Las obras complementarias que el asistente acaba de teclear, DE VUELTA.
           `FichaResource` las construye siempre —no son anulables— y el alta las
           recibe en `PeticionDeAlta.instalaciones`, asi que devolverlas vacias
           dejaria sin ejercer el camino entero del que habla #46: se escriben, se
           guardan y se vuelven a leer. La `cantidad` sale con su unidad dentro,
           que es lo que hace `Medida.toString()`. */
        instalaciones: instalacionesDeclaradas(cuerpo.instalaciones),
        /* Los tres bloques de detalle salen nulos: el asistente no recoge
           ninguno —`rutas.mjs` lo dice en el sentido de ida— y el rural que si
           manda son los colindantes, que el backend guarda y esta simulacion no
           persiste. */
        economico: null,
        bienesComunes: null,
        rural: null,
        /* «No lo pediste»: un alta no lleva `?historico=`, y no puede tenerlo
           —acaba de nacer la version 1—. Nulo y no lista vacia. */
        historico: null,
      },
    };
  };
}

/**
 * Las obras complementarias declaradas, con la forma con que vuelven.
 *
 * `InstalacionDeclarada` trae `cantidad` y `unidad` por separado —es lo que el
 * formulario teclea— y `InstalacionResource` las publica **las dos**: la unidad
 * suelta, y otra vez dentro de la cantidad, que es lo que hace `Medida`. Sin
 * unidad no se compone ninguna medida, asi que la fila se devuelve tal cual el
 * dominio la rechazaria: el proxy no valida (ADR-0010), pero tampoco inventa una
 * unidad que nadie escribio.
 */
function instalacionesDeclaradas(declaradas: unknown): unknown[] {
  if (!Array.isArray(declaradas)) return [];
  return declaradas.map((cruda, i) => {
    const x = (cruda ?? {}) as Record<string, unknown>;
    const cantidad = typeof x.cantidad === 'string' ? x.cantidad : '';
    const unidad = typeof x.unidad === 'string' ? x.unidad : '';
    return {
      id: i + 1,
      descripcion: typeof x.descripcion === 'string' ? x.descripcion : '',
      unidad,
      cantidad: unidad === '' ? cantidad : `${cantidad} ${unidad}`,
      anioConstruccion: typeof x.anioConstruccion === 'number' ? x.anioConstruccion : null,
      estadoConservacion: typeof x.estadoConservacion === 'string' ? x.estadoConservacion : null,
    };
  });
}

/**
 * La ficha vigente de un predio, entera, con la forma de `FichaResource`.
 *
 * <h2>Las CUATRO lecturas, y por que no es una con un parametro</h2>
 *
 * `FichaController` declara una ruta por clase de ficha y cada una **fija su
 * `TipoFicha`**: `/urbana/{cod}` lee `TipoFicha.UNICA` y ninguna otra. Pedir la
 * ficha de un predio por la ruta que no le toca no devuelve un bloque vacio
 * —contesta **404** «El predio no tiene ficha urbana vigente al …»—, y ese es un
 * desenlace que la pantalla tiene que poder ejercer: es lo que decide que la
 * lectura se despache por el tipo que trae la grilla.
 *
 * <h2>Y el `?historico=`, que es una respuesta distinta y no un adorno</h2>
 *
 * Sin el parametro, `historico` viaja **nulo**: «no lo pediste». Con
 * `historico=true` viajan todas las versiones. Una lista vacia no puede pasar
 * —toda ficha tiene al menos la vigente—, asi que el nulo y la lista nunca
 * significan lo mismo. Este proxy reproduce las dos respuestas porque son las
 * dos que el backend ya tiene escritas.
 */
function lecturaDeFicha(tipo: string, llave: string, comoSeLlama: string): Manejador {
  return (c) => {
    const codigo = c.parametros[llave] ?? '';
    const p = D.PADRON.find((x) => x.codRefCatastral === codigo);
    if (!p) {
      return problema(
        'NO_ENCONTRADO',
        404,
        'No hay ningun predio con ese codigo de referencia catastral',
      );
    }
    if (p.tipoFicha !== tipo) {
      return problema(
        'NO_ENCONTRADO',
        404,
        `El predio no tiene ficha ${comoSeLlama} vigente al ${D.HOY}`,
      );
    }
    const detalle = D.DETALLE[p.codRefCatastral];
    const vigente = D.versionVigenteDe(p);
    return ok({
      id: vigente.id,
      predioId: p.predioId,
      tipo: p.tipoFicha,
      version: vigente.version,
      areaTerreno: p.areaTerreno,
      uso: p.uso,
      frontis: null,
      condicionPropiedad: p.condicion,
      tipoEdificacion: null,
      vigenciaDesde: vigente.vigenciaDesde,
      vigenciaHasta: vigente.vigenciaHasta,
      vigente: vigente.vigente,
      origen: vigente.origen,
      documentoOrigen: vigente.documentoOrigen,
      observacion: vigente.observacion,
      denominacion: p.denominacion,
      /* Un predio sin detalle no tiene construcciones ni obras complementarias, y
         eso es una LISTA VACIA de verdad: `FichaResource` construye las dos
         siempre y ninguna es anulable. */
      construcciones: detalle?.construcciones ?? [],
      instalaciones: detalle?.instalaciones ?? [],
      economico: detalle?.economico ?? null,
      bienesComunes: detalle?.bienesComunes ?? null,
      rural: detalle?.rural ?? null,
      historico: c.consulta.get('historico') === 'true' ? D.versionesDe(p) : null,
    });
  };
}

const LECTURAS_DE_FICHA: readonly { metodo: string; ruta: string; responder: Manejador }[] = [
  { metodo: 'GET', ruta: '/catastro/fichas/urbana/{codRefCatastral}', responder: lecturaDeFicha('UNICA', 'codRefCatastral', 'urbana') },
  {
    metodo: 'GET',
    ruta: '/catastro/fichas/economica/{codRefCatastral}',
    responder: lecturaDeFicha('ECONOMICA', 'codRefCatastral', 'economica'),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/fichas/bienes-comunes/{codEdificacion}',
    responder: lecturaDeFicha('BIENES_COMUNES', 'codEdificacion', 'de bienes comunes'),
  },
  { metodo: 'GET', ruta: '/catastro/fichas/rural/{codUnidad}', responder: lecturaDeFicha('RURAL', 'codUnidad', 'rural') },
];

const ALTAS_DE_FICHA: readonly { metodo: string; ruta: string; responder: Manejador }[] = [
  { metodo: 'POST', ruta: '/catastro/fichas/urbana', responder: altaDeFicha('UNICA') },
  { metodo: 'POST', ruta: '/catastro/fichas/economica', responder: altaDeFicha('ECONOMICA') },
  { metodo: 'POST', ruta: '/catastro/fichas/bienes-comunes', responder: altaDeFicha('BIENES_COMUNES') },
  { metodo: 'POST', ruta: '/catastro/fichas/rural', responder: altaDeFicha('RURAL') },
];

/**
 * La tabla de operaciones.
 *
 * Son las que el armazon lee. Las 64 del contrato no estan todas: lo que falta
 * no se simula «por si acaso», porque una respuesta simulada que nadie ha
 * mirado es indistinguible de una que nadie ha escrito.
 */
const TABLA: readonly { metodo: string; ruta: string; responder: Manejador }[] = [
  /* ── Padron y fichas ────────────────────────────────────────────────── */
  { metodo: 'GET', ruta: '/catastro/predios', responder: () => pagina(D.PADRON) },
  {
    metodo: 'GET',
    ruta: '/catastro/predios/{predioId}',
    responder: (c) => {
      const p = predioDe(c);
      return p
        ? ok({ predioId: p.predioId, enElPadron: true })
        : problema('NO_ENCONTRADO', 404, 'No hay ningun predio con ese identificador');
    },
  },
  {
    metodo: 'GET',
    ruta: '/catastro/predios/{predioId}/frentes',
    responder: (c) => {
      const p = predioDe(c);
      if (!p) return problema('NO_ENCONTRADO', 404, 'No hay ningun predio con ese identificador');
      /* Los dos desenlaces de #7: el predio con lote levantado propone frentes;
         el resto —o sea, todos los de una instalacion de verdad— salen con el
         MOTIVO por el que no se propuso ninguno. */
      const tiene = p.predioId === D.PREDIO_CON_POLIGONO;
      return ok({
        predioId: p.predioId,
        frentes: tiene ? D.FRENTES : [],
        derivadoEn: '2026-09-05T03:14:00Z',
        frentesDerivados: tiene ? D.FRENTES.length : 0,
        motivoDeLaDerivacion: tiene ? null : D.SIN_FRENTES,
      });
    },
  },
  {
    metodo: 'GET',
    ruta: '/catastro/predios/plano',
    responder: () =>
      /* Ni un lote con geometria, que es el estado real de toda instalacion. La
         pantalla ensena `sinGeometria` en vez de un lienzo vacio. */
      ok({ lotes: [], sinGeometria: D.PADRON.length }),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/predios/plano/marco',
    responder: () =>
      ok({
        marco: null,
        lotes: 0,
        notaDelMarco: 'Ningun predio de este ambito tiene poligono levantado, asi que no hay marco que componer.',
      }),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/fichas',
    responder: () =>
      pagina(
        D.PADRON.map((p) => {
          /* La grilla trae la ficha VIGENTE, que es la segunda en los 22 predios
             que `detalle-de-fichas.csv` versiona. Su `fichaId` y su `version` son
             los de esa version y no los de la primera: con «1» fijo, el detalle
             que se abre desde aqui hablaria de otra fila que la que la grilla
             dice. */
          const vigente = D.versionVigenteDe(p);
          return {
            fichaId: vigente.id,
            predioId: p.predioId,
            codRefCatastral: p.codRefCatastral,
            direccion: p.direccion,
            manzana: p.codigoDeManzana,
            lote: p.lote,
            tipo: p.tipoFicha,
            version: vigente.version,
            areaTerreno: p.areaTerreno,
            areaConstruida: null,
            uso: p.uso,
            vigenciaDesde: vigente.vigenciaDesde,
            titular: D.contribuyenteDe(p.contribuyente).nombre,
          };
        }),
      ),
  },
  ...LECTURAS_DE_FICHA,

  /* ── El alta de una ficha (#34): las cuatro rutas ───────────────────── */
  ...ALTAS_DE_FICHA,

  /* ── Territorio: las tres lecturas y las cinco escrituras de #72 ────── */
  { metodo: 'GET', ruta: '/catastro/sectores', responder: () => catalogo.listarSectores() },
  { metodo: 'POST', ruta: '/catastro/sectores', responder: (c) => catalogo.registrarSector(c.cuerpo) },
  {
    metodo: 'PUT',
    ruta: '/catastro/sectores/{codigo}',
    responder: (c) => catalogo.modificarSector(c.parametros.codigo!, c.cuerpo),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/sectores/{codigo}/manzanas',
    responder: (c) => catalogo.listarManzanas(c.parametros.codigo!),
  },
  {
    metodo: 'POST',
    ruta: '/catastro/sectores/{codigo}/manzanas',
    responder: (c) => catalogo.registrarManzana(c.parametros.codigo!, c.cuerpo),
  },
  { metodo: 'GET', ruta: '/catastro/vias', responder: () => catalogo.listarVias() },
  { metodo: 'POST', ruta: '/catastro/vias', responder: (c) => catalogo.registrarVia(c.cuerpo) },
  {
    metodo: 'PUT',
    ruta: '/catastro/vias/{codigo}',
    responder: (c) => catalogo.modificarVia(c.parametros.codigo!, c.cuerpo),
  },

  /* ── Cuadros del ejercicio ──────────────────────────────────────────── */
  {
    metodo: 'GET',
    ruta: '/catastro/tablas/aranceles',
    responder: (c) => cuadroDe(c, D.ARANCELES),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/tablas/valores-unitarios',
    responder: (c) => cuadroDe(c, D.VALORES_UNITARIOS),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/tablas/depreciacion',
    responder: (c) => cuadroDe(c, D.DEPRECIACION),
  },
  {
    metodo: 'GET',
    ruta: '/seguridad/parametros/ejercicios/{ejercicio}',
    responder: (c) => {
      const anio = Number(c.parametros.ejercicio);
      /* Un ejercicio sin sellar contesta **200 con `sellado: false`**, no 404:
         medido en `EjercicioParametrizadoController`. Es una respuesta, no un
         fallo, y la pantalla la dibuja como tal. */
      return anio === D.EJERCICIO
        ? ok(D.EJERCICIO_SELLADO)
        : ok({ ejercicio: anio, sellado: false, conjuntoId: null, version: null });
    },
  },

  /* ── Urbano y GRD: los dos caminos ──────────────────────────────────── */
  {
    metodo: 'GET',
    ruta: '/urbano/zonificacion',
    responder: (c) => sinPoligono(c) ?? ok({ ...D.ZONA }),
  },
  {
    metodo: 'GET',
    ruta: '/grd/riesgo',
    responder: (c) => {
      const p = predioDe(c);
      return sinPoligono(c) ?? ok({ predioId: p!.predioId, ...D.RIESGO });
    },
  },
  {
    metodo: 'GET',
    ruta: '/grd/itse',
    responder: (c) => {
      const p = predioDe(c);
      if (!p) return problema('NO_ENCONTRADO', 404, 'No hay ningun predio con ese identificador');
      return ok({ predioId: p.predioId, ...D.ITSE });
    },
  },

  /* ── Fiscalizacion: las cuatro lecturas y las nueve operaciones de #71 ── */
  { metodo: 'POST', ruta: '/fiscalizacion/campanias', responder: (c) => ciclo.abrirCampania(c.cuerpo) },
  {
    metodo: 'POST',
    ruta: '/fiscalizacion/campanias/{campaniaId}/cierre',
    responder: (c) => ciclo.cerrarCampania(Number(c.parametros.campaniaId), c.cuerpo),
  },
  {
    metodo: 'GET',
    ruta: '/fiscalizacion/campanias/{campaniaId}/candidatos',
    responder: (c) => ciclo.enLaCampania(Number(c.parametros.campaniaId), ciclo.losCandidatos()),
  },
  {
    metodo: 'GET',
    ruta: '/fiscalizacion/campanias/{campaniaId}/tasa-de-descarte',
    responder: (c) => ciclo.tasaDeLaCampania(Number(c.parametros.campaniaId)),
  },
  {
    metodo: 'POST',
    ruta: '/fiscalizacion/candidatos/{candidatoId}/gabinete',
    responder: (c) => ciclo.enGabinete(Number(c.parametros.candidatoId), c.cuerpo),
  },
  {
    metodo: 'POST',
    ruta: '/fiscalizacion/candidatos/{candidatoId}/campo',
    responder: (c) => ciclo.enCampo(Number(c.parametros.candidatoId), c.cuerpo),
  },
  {
    metodo: 'POST',
    ruta: '/fiscalizacion/candidatos/{candidatoId}/campo/descarte',
    responder: (c) => ciclo.descartarEnCampo(Number(c.parametros.candidatoId), c.cuerpo),
  },
  {
    metodo: 'GET',
    ruta: '/fiscalizacion/campanias/{campaniaId}/hallazgos',
    responder: (c) => ciclo.enLaCampania(Number(c.parametros.campaniaId), ciclo.losHallazgos()),
  },
  {
    metodo: 'GET',
    ruta: '/fiscalizacion/predios/{predioId}/hallazgos',
    responder: (c) => ciclo.hallazgosDelPredio(Number(c.parametros.predioId)),
  },
  {
    metodo: 'GET',
    ruta: '/fiscalizacion/hallazgos/{hallazgoId}/evidencias',
    responder: (c) => ciclo.evidenciasDe(Number(c.parametros.hallazgoId)),
  },
  {
    metodo: 'POST',
    ruta: '/fiscalizacion/hallazgos/{hallazgoId}/evidencias',
    responder: (c) => ciclo.adjuntarEvidencia(Number(c.parametros.hallazgoId), c.cuerpo),
  },
  {
    metodo: 'POST',
    ruta: '/fiscalizacion/hallazgos/{hallazgoId}/anulacion',
    responder: (c) => ciclo.dejarSinEfecto(Number(c.parametros.hallazgoId), c.cuerpo),
  },
  {
    metodo: 'POST',
    ruta: '/fiscalizacion/hallazgos/{hallazgoId}/acta',
    responder: (c) => ciclo.levantarActa(Number(c.parametros.hallazgoId), c.cuerpo),
  },

  /* ── Ventanilla ─────────────────────────────────────────────────────── */
  {
    metodo: 'GET',
    ruta: '/consultas/resumen-predial',
    responder: () =>
      pagina(
        D.PADRON.map((p) => {
          const vigente = D.versionVigenteDe(p);
          return {
            fichaId: vigente.id,
            predioId: p.predioId,
            codCatastral: p.codRefCatastral,
            codPropietario: p.contribuyente,
            nombreDelPropietario: D.contribuyenteDe(p.contribuyente).nombre,
            direccionDelPredio: p.direccion,
            uso: p.uso,
            tipo: p.tipoFicha,
            version: vigente.version,
            vigenciaDesde: vigente.vigenciaDesde,
          };
        }),
      ),
  },
  {
    metodo: 'GET',
    ruta: '/catastro/contribuyentes/{codigo}/ficha.pdf',
    responder: (c) => {
      const codigo = c.parametros.codigo;
      const suyos = D.PADRON.filter((p) => p.contribuyente === codigo);
      if (suyos.length === 0) return problema('NO_ENCONTRADO', 404, 'No hay ningun contribuyente con ese codigo');
      const quien = D.contribuyenteDe(codigo);
      return ok({
        aLaFecha: '2026-09-06',
        codigo,
        nombre: quien.nombre,
        documento: quien.documento,
        domicilioFiscal: suyos[0]!.direccion,
        unidades: suyos.map((p) => ({
          codRefCatastral: p.codRefCatastral,
          direccion: p.direccion,
          condicion: p.condicion,
          porcentaje: '100.0000',
          areaTerreno: p.areaTerreno,
          uso: p.uso,
          version: D.versionVigenteDe(p).version,
        })),
      });
    },
  },
];

/**
 * Un cuadro del ejercicio: solo el sellado lo tiene; los demas, 404.
 *
 * **Con su `parametroQueFalta`, y sin `llave`.** Es lo que el backend emite,
 * medido: los tres controladores solo atrapan
 * `LectorDeParametros.EjercicioSinSellar`, cuyo `llave()` es `Optional.empty()`,
 * asi que estas tres rutas nunca nombran una fila del corpus — lo que falta es
 * el conjunto entero del ano. Omitir el miembro aqui dejaria a la pantalla sin
 * el unico discriminador que separa «falta un campo de la peticion», que quien
 * atiende arregla, de «falta publicar», que no arregla nadie desde la pantalla.
 */
function cuadroDe(contexto: Contexto, filas: readonly unknown[]): Respuesta {
  const ejercicio = Number(contexto.consulta.get('ejercicio') ?? '');
  if (ejercicio !== D.EJERCICIO) {
    return problema(
      'NO_ENCONTRADO',
      404,
      `El ejercicio ${ejercicio} no tiene un conjunto de parametros sellado. Calcular con uno abierto` +
        ' produciria una cifra que manana puede ser otra, y el contribuyente ya tendria el recibo (ADR-0007)',
      { ejercicio },
    );
  }
  return ok(filas);
}


/**
 * El 422 por predio sin poligono, que es el desenlace que ocurre de verdad.
 *
 * Devuelve `null` cuando el predio SI tiene lote levantado y hay que seguir. Y
 * distingue los dos rechazos: un predio que no existe es 404, y un predio que
 * existe sin poligono es **422 y no 404**, a proposito — el backend lo escribe
 * asi porque las dos cosas se arreglan de manera distinta.
 */
function sinPoligono(contexto: Contexto): Respuesta | null {
  const p = predioDe(contexto);
  if (!p) return problema('NO_ENCONTRADO', 404, 'No hay ningun predio con ese identificador');
  if (p.predioId === D.PREDIO_CON_POLIGONO) return null;
  return problema(
    'VALIDACION',
    422,
    'El predio existe y no tiene poligono levantado: sin geometria no se puede decir a que zona cae',
  );
}

function compilar(ruta: string): { patron: RegExp; nombres: string[] } {
  const nombres: string[] = [];
  const escapado = ruta
    .split(/(\{\w+\})/)
    .map((trozo) => {
      const llave = trozo.match(/^\{(\w+)\}$/);
      if (llave) {
        nombres.push(llave[1]!);
        return '([^/]+)';
      }
      return trozo.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    })
    .join('');
  return { patron: new RegExp(`^${RAIZ.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}${escapado}$`), nombres };
}

/** Cuantos `{parametro}` tiene una ruta del contrato. */
function cuantosParametros(ruta: string): number {
  return (ruta.match(/\{\w+\}/g) ?? []).length;
}

/**
 * La tabla, ordenada de lo LITERAL a lo parametrizado.
 *
 * <h2>Un defecto que se encontro midiendo, y cuyo sintoma era una respuesta
 * plausible</h2>
 *
 * El encaminamiento recorria la tabla en el orden en que esta escrita, y
 * `/catastro/predios/{predioId}` esta ANTES que `/catastro/predios/plano`. Asi
 * que `GET /catastro/predios/plano` casaba con el primero, `Number('plano')`
 * daba `NaN`, y el proxy contestaba **404 «No hay ningun predio con ese
 * identificador»** a la lectura del plano catastral.
 *
 * Lo caro no es el 404: es que en ESA pantalla un 404 se lee como una respuesta
 * correcta —«aqui no hay lotes» y «no existe» se parecen mucho cuando lo que se
 * espera es un plano vacio—, asi que la pantalla llevaba desde #32 ensenando el
 * error de otra ruta y nadie tenia como notarlo. `/catastro/predios/plano/marco`
 * se salvaba de milagro: tiene un segmento mas y ningun patron lo tapa.
 *
 * Se ordena por numero de parametros y **se comprueba que el orden funciona**:
 * toda ruta sin parametros tiene que casar consigo misma y no con otra. Un
 * arreglo por reordenacion se deshace solo en cuanto alguien anade una entrada
 * al final, y esta comprobacion lo dice al importar el modulo, no en produccion.
 */
const COMPILADAS = TABLA.map((entrada) => ({ ...entrada, ...compilar(entrada.ruta) })).sort(
  (a, b) => cuantosParametros(a.ruta) - cuantosParametros(b.ruta),
);

for (const entrada of COMPILADAS) {
  if (cuantosParametros(entrada.ruta) > 0) continue;
  const camino = RAIZ + entrada.ruta;
  const primera = COMPILADAS.find((otra) => otra.metodo === entrada.metodo && otra.patron.test(camino));
  if (primera !== entrada) {
    throw new Error(
      `El proxy encamina «${entrada.metodo} ${entrada.ruta}» a «${primera?.ruta}»: una ruta literal la` +
        ' esta tapando un patron con parametro. Ordenar por numero de parametros no basta para este par,' +
        ' y contestar la respuesta de otra ruta es un fallo que la pantalla lee como si fuera suyo.',
    );
  }
}

/** Cuantas operaciones responde el proxy. */
export const OPERACIONES_SIMULADAS = COMPILADAS.length;

/** Las rutas que simula, para quien quiera contrastarlas con el backend. */
export const RUTAS_SIMULADAS: readonly { metodo: string; ruta: string }[] = TABLA.map((t) => ({
  metodo: t.metodo,
  ruta: t.ruta,
}));

/** Lo que la peticion mando, si mando algo y es JSON. */
function leerCuerpo(cuerpo: BodyInit | null | undefined): unknown {
  if (typeof cuerpo !== 'string') return null;
  try {
    return JSON.parse(cuerpo) as unknown;
  } catch {
    return null;
  }
}

function json(cuerpo: unknown, estado: number): Response {
  return new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: {
      'content-type': estado >= 400 ? 'application/problem+json' : 'application/json',
    },
  });
}

function noLaSirve(metodo: string, camino: string, estado: number): Response {
  return json(
    {
      type: 'https://sgtm.gob.pe/errores/operacion-declarada-y-no-servida',
      title: 'La operacion esta declarada como servida y el backend no la sirve',
      status: 502,
      detail: `«${metodo} ${camino}» esta en «src/simulado/servidas.ts» y el backend respondio ${estado}. Quita la ruta de esa lista o implementa la operacion: caer al proxy en silencio esconderia el desajuste.`,
      codigo: 'ERROR_INTERNO',
      mensaje: `«${metodo} ${camino}» esta declarada como servida y el backend respondio ${estado}.`,
    },
    502,
  );
}

function noSimulada(metodo: string, camino: string): Response {
  return json(
    {
      type: 'https://sgtm.gob.pe/errores/operacion-no-simulada',
      title: 'La operacion no existe en el proxy de datos',
      status: 404,
      detail: `El proxy de datos no conoce «${metodo} ${camino}». El backend publica 64 operaciones y este proxy simula ${OPERACIONES_SIMULADAS}: las que el armazon lee. Anadela a «src/simulado/proxy.ts» o enciendela en «servidas.ts».`,
      codigo: 'NO_ENCONTRADO',
      mensaje: `El proxy de datos no conoce «${metodo} ${camino}».`,
    },
    404,
  );
}

let original: typeof fetch | null = null;

export type OpcionesDelProxy = {
  /** Latencia simulada. Encendida en desarrollo; se apaga para medir. */
  readonly latencia?: boolean;
  readonly yaServidas?: readonly OperacionServida[];
};

/**
 * Sustituye `fetch` por el proxy. Devuelve la funcion que lo desinstala.
 *
 * Solo intercepta lo que cuelga de la raiz de la API; cualquier otra peticion
 * —una fuente tipografica, un recurso— sigue su camino sin tocarse.
 */
export function instalarProxyDeDatos({
  latencia = true,
  yaServidas = YA_SERVIDAS,
}: OpcionesDelProxy = {}): () => void {
  if (original) return desinstalarProxyDeDatos;
  original = globalThis.fetch;
  /* Para delegar hace falta ligarlo; para restaurar, no: devolver el envoltorio
     ligado en vez de la funcion original dejaria una capa pegada en cada ciclo. */
  const anterior = original.bind(globalThis);

  globalThis.fetch = async (entrada: RequestInfo | URL, opciones?: RequestInit): Promise<Response> => {
    const href =
      typeof entrada === 'string' ? entrada : entrada instanceof URL ? entrada.href : entrada.url;
    const url = new URL(href, globalThis.location?.origin ?? 'http://localhost');
    if (!url.pathname.startsWith(RAIZ)) return anterior(entrada, opciones);

    const metodo = (
      opciones?.method ??
      (typeof entrada === 'object' && 'method' in entrada ? entrada.method : 'GET')
    ).toUpperCase();

    if (laSirveElBackend(yaServidas, RAIZ, metodo, url.pathname)) {
      const respuesta = await anterior(entrada, opciones);
      return respuesta.status === 404 || respuesta.status === 501
        ? noLaSirve(metodo, url.pathname, respuesta.status)
        : respuesta;
    }

    if (latencia) {
      await esperar(LATENCIA_MINIMA_MS + Math.random() * (LATENCIA_MAXIMA_MS - LATENCIA_MINIMA_MS));
    }

    for (const entradaDeLaTabla of COMPILADAS) {
      if (entradaDeLaTabla.metodo !== metodo) continue;
      const casa = entradaDeLaTabla.patron.exec(url.pathname);
      if (!casa) continue;
      const parametros: Parametros = {};
      entradaDeLaTabla.nombres.forEach((nombre, i) => {
        parametros[nombre] = decodeURIComponent(casa[i + 1]!);
      });
      const { estado, cuerpo } = entradaDeLaTabla.responder({
        parametros,
        consulta: url.searchParams,
        cuerpo: leerCuerpo(opciones?.body),
      });
      return json(cuerpo, estado);
    }

    return noSimulada(metodo, url.pathname);
  };

  return desinstalarProxyDeDatos;
}

export function desinstalarProxyDeDatos(): void {
  if (!original) return;
  globalThis.fetch = original;
  original = null;
}

export function proxyDeDatosInstalado(): boolean {
  return original !== null;
}
