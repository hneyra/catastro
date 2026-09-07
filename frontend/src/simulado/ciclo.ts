/**
 * El ciclo de fiscalizacion simulado: las nueve operaciones que #71 ofrece.
 *
 * <h2>Esto SI recuerda, y es la unica excepcion de ADR-0010 que hay aqui</h2>
 *
 * La cuarta decision de ADR-0010 dice que este proxy **no persiste**: «un POST
 * responde con el recurso y no guarda nada». Para el alta de una ficha eso es
 * correcto y esta escrito: la siguiente lectura del padron no la trae, y fingir
 * que si obligaria al proxy a tener memoria.
 *
 * Aqui no vale, y el motivo no es de comodidad. Este modulo simula **una
 * maquina de estados**, y su regla es que lo ofrecible sale del estado de la
 * fila (`TRANSICIONES_DEL_CANDIDATO`, AC-2 de #71). Sin memoria, el par de
 * respuestas que el proxy produce es uno que **el backend no puede producir**:
 *
 *   · `POST /candidatos/1/gabinete` contesta `200` con
 *     `estado: ADMITIDO_EN_GABINETE`, y
 *   · el `GET` siguiente contesta `DETECTADO` para ese mismo candidato.
 *
 * Y no es un detalle cosmetico: la pantalla vuelve a ofrecer «Admitir en
 * gabinete» sobre un candidato que el servidor acaba de admitir, o sea que **el
 * proxy fabrica el defecto que el AC-2 existe para impedir**. ADR-0010 dice que
 * lo unico que el sujeto decide es «cual de las respuestas que el backend YA
 * TIENE ESCRITAS toca»; una lectura que contradice a la escritura anterior no es
 * ninguna de ellas.
 *
 * Medido, con la memoria quitada: el arnes `transiciones.mjs` da «tras
 * «Admitir en gabinete», el candidato 1 sigue ofreciendo [admitirEnGabinete,
 * descartarEnGabinete] y su estado sigue diciendo DETECTADO». Con memoria, la
 * fila pasa a `ADMITIDO_EN_GABINETE` y ofrece las dos de campo.
 *
 * <h2>Lo que la memoria NO hace</h2>
 *
 * **Dura lo que la pagina.** Es estado de modulo, sembrado al importar desde
 * `datos.ts`: una recarga lo devuelve al principio. Asi cada captura de los
 * arneses se puede volver a producir, que es la propiedad que `datos.ts` cuida
 * al no darle un reloj a este proxy.
 *
 * **No filtra, no ordena, no pagina y no valida.** Sigue sin hacer las cuatro
 * cosas que ADR-0010 prohibe. Lo unico que cambia es que un acto que el backend
 * escribiria queda escrito tambien aqui, para que la lectura siguiente no mienta.
 *
 * **No inventa ninguna respuesta.** Los cuatro `409` que contesta son los cuatro
 * que el backend tiene escritos —`TransicionQueNoExiste`, `CampaniaYaAbierta`,
 * `CampaniaYaCerrada`, `HallazgoSinEfecto`, `HuellaRepetida`, `ActaRepetida`,
 * `SinLasDosCompuertas`— con su mensaje, y el unico `422` es el de la
 * observacion, que es una regla del sistema entero.
 */
import * as D from './datos';
import { creado, faltaLaObservacion, ok, pagina, problema } from './respuestas';
import type { Respuesta } from './respuestas';

type Fila = Record<string, unknown>;

/* ── El estado, sembrado de `datos.ts` y vivo mientras dure la pagina ────── */

const campanias: Fila[] = [{ ...D.CAMPANIA }];
const candidatos: Fila[] = D.CANDIDATOS.map((c) => ({ ...c }));
const hallazgos: Fila[] = D.HALLAZGOS.map((h) => ({ ...h }));
const evidencias: Fila[] = D.EVIDENCIAS.map((e) => ({ ...e }));
const actas: Fila[] = D.ACTAS.map((a) => ({ ...a }));

export function losCandidatos(): readonly Fila[] {
  return candidatos;
}

export function losHallazgos(): readonly Fila[] {
  return hallazgos;
}

/** El identificador que seguiria al ultimo. Lo unico que se inventa. */
function siguienteId(filas: readonly Fila[]): number {
  return filas.reduce((mayor, f) => Math.max(mayor, Number(f.id)), 0) + 1;
}

function texto(cuerpo: unknown, campo: string): string {
  const campos = (cuerpo ?? {}) as Record<string, unknown>;
  const valor = campos[campo];
  return typeof valor === 'string' ? valor.trim() : '';
}

/**
 * Quien firma los actos del recorrido, y cuando.
 *
 * En el backend salen del token y del reloj del servidor; aqui no hay ni uno ni
 * otro —este proxy declara por escrito que no tiene reloj—, asi que son
 * constantes derivadas del dia con el que contestan todas las lecturas «a la
 * fecha». Un `new Date()` haria que la misma captura no se pudiera repetir.
 */
const QUIEN = 'demo.catastro';
const AHORA = `${D.HOY}T09:15:00Z`;

/* ── La campania ────────────────────────────────────────────────────────── */

export function abrirCampania(cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const codigo = texto(cuerpo, 'codigo');
  if (campanias.some((c) => c.codigo === codigo)) {
    return problema(
      'CONFLICTO',
      409,
      `Ya hay una campania con el codigo '${codigo}' en esta municipalidad`,
    );
  }

  const campos = (cuerpo ?? {}) as Record<string, unknown>;
  const nueva: Fila = {
    id: siguienteId(campanias),
    codigo,
    nombre: texto(cuerpo, 'nombre'),
    estado: 'ABIERTA',
    inicio: D.HOY,
    fin: null,
    umbral: texto(cuerpo, 'umbral'),
    /* Tal como llego. El borde lo exige y no le pone valor por omision (#25), y
       este proxy no valida: si no llega, llega lo que llegue. */
    tope: typeof campos.tope === 'number' ? campos.tope : null,
  };
  campanias.push(nueva);
  return creado(nueva);
}

export function cerrarCampania(campaniaId: number, cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const campania = campanias.find((c) => c.id === campaniaId);
  if (!campania) return campaniaInexistente(campaniaId);
  if (campania.estado === 'CERRADA') {
    return problema('CONFLICTO', 409, `La campania ${campaniaId} ya estaba cerrada`);
  }
  campania.estado = 'CERRADA';
  campania.fin = D.HOY;
  return ok({ ...campania });
}

function campaniaInexistente(campaniaId: number): Respuesta {
  return problema('NO_ENCONTRADO', 404, `No hay ninguna campania ${campaniaId} en esta municipalidad`);
}

/**
 * Lo de una campania, o el 404 si no es ninguna de las que hay.
 *
 * **No filtra**, y eso es ADR-0010 decision 4: aqui hay una sola campania
 * sembrada, asi que filtrar seria escribir una semantica que no se puede
 * comprobar. Lo que si decide es cual de las dos respuestas escritas toca —la
 * lista o el 404—, que es lo unico que el sujeto decide.
 */
export function enLaCampania(campaniaId: number, filas: readonly Fila[]): Respuesta {
  if (!campanias.some((c) => c.id === campaniaId)) return campaniaInexistente(campaniaId);
  return pagina(filas);
}

export function tasaDeLaCampania(campaniaId: number): Respuesta {
  if (!campanias.some((c) => c.id === campaniaId)) return campaniaInexistente(campaniaId);
  return ok(D.TASA_DE_DESCARTE);
}

/* ── Las dos compuertas ─────────────────────────────────────────────────── */

function candidatoInexistente(candidatoId: number): Respuesta {
  return problema('NO_ENCONTRADO', 404, `No hay ningun candidato ${candidatoId} en esta municipalidad`);
}

/**
 * El `409` que el AC-2 existe para no tener que ver, con el mensaje del dominio.
 *
 * Es `Candidato.TransicionQueNoExiste`. La pantalla no ofrece el acto que el
 * estado no admite, asi que este rechazo solo se alcanza pidiendo la ruta a
 * mano —o desde otra sesion que ya lo movio—, que es exactamente cuando hace
 * falta que se entienda.
 */
function transicionQueNoExiste(desde: unknown, acto: string): Respuesta {
  return problema(
    'CONFLICTO',
    409,
    `Un candidato ${String(desde)} no admite ${acto}: las dos compuertas —gabinete y campo— se pasan` +
      ' en orden, y saltarse una deja un acta sin nadie que la sostenga (ADR-0035)',
  );
}

const TERMINALES = ['DESCARTADO', 'VERIFICADO_EN_CAMPO'];

export function enGabinete(candidatoId: number, cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const candidato = candidatos.find((c) => c.id === candidatoId);
  if (!candidato) return candidatoInexistente(candidatoId);

  const campos = (cuerpo ?? {}) as Record<string, unknown>;
  if (campos.admite === true) {
    if (candidato.estado !== 'DETECTADO') {
      return transicionQueNoExiste(candidato.estado, 'admitirlo en gabinete');
    }
    candidato.estado = 'ADMITIDO_EN_GABINETE';
    return ok({ ...candidato });
  }

  if (TERMINALES.includes(String(candidato.estado))) {
    return transicionQueNoExiste(candidato.estado, 'descartarlo');
  }
  candidato.estado = 'DESCARTADO';
  candidato.etapaDeDescarte = 'GABINETE';
  candidato.motivoDeDescarte = texto(cuerpo, 'motivo');
  candidato.descartadoPor = QUIEN;
  return ok({ ...candidato });
}

export function descartarEnCampo(candidatoId: number, cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const candidato = candidatos.find((c) => c.id === candidatoId);
  if (!candidato) return candidatoInexistente(candidatoId);
  if (candidato.estado !== 'ADMITIDO_EN_GABINETE') {
    return transicionQueNoExiste(candidato.estado, 'descartarlo en campo');
  }
  candidato.estado = 'DESCARTADO';
  candidato.etapaDeDescarte = 'CAMPO';
  candidato.motivoDeDescarte = texto(cuerpo, 'motivo');
  candidato.descartadoPor = QUIEN;
  return ok({ ...candidato });
}

/**
 * La segunda compuerta, que es la unica que produce un hallazgo.
 *
 * <h2>El exceso viaja NULO, y es una decision con su motivo</h2>
 *
 * Lo calcula el servidor. Aqui no se calcula por dos razones que apuntan al
 * mismo sitio: una segunda resta podria divergir de la suya sin que nada lo
 * dijera —es lo que el pie de la tabla de hallazgos dice con todas las letras—,
 * y las areas viajan como TEXTO, de modo que restarlas exigiria pasarlas por
 * `Number`, que es donde se pierde el decimal que RNF-055 conserva y lo prohibe
 * la configuracion de ESLint. El nulo no es un hueco: es un valor que el propio
 * `HallazgoResource` emite —«nulo cuando no hay con que comparar o cuando lo
 * verificado no supera lo inscrito»—.
 */
export function enCampo(candidatoId: number, cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const candidato = candidatos.find((c) => c.id === candidatoId);
  if (!candidato) return candidatoInexistente(candidatoId);
  if (candidato.estado !== 'ADMITIDO_EN_GABINETE') {
    return transicionQueNoExiste(candidato.estado, 'verificarlo en campo');
  }

  const predioId = candidato.predioId === null ? null : Number(candidato.predioId);
  const predio = predioId === null ? undefined : D.PADRON.find((p) => p.predioId === predioId);
  if (predio !== undefined && !predio.fichado) {
    return problema(
      'CONFLICTO',
      409,
      `El predio ${predioId} no tiene ficha vigente al ${D.HOY}: no hay version que contrastar, y un` +
        ' hallazgo sin ella no se puede releer despues',
    );
  }

  candidato.estado = 'VERIFICADO_EN_CAMPO';
  const hallazgo: Fila = {
    id: siguienteId(hallazgos),
    candidatoId,
    clase: candidato.clase,
    predioId,
    fichaId: predio === undefined ? null : D.versionVigenteDe(predio).id,
    areaDeLaFicha: predio === undefined ? null : predio.areaTerreno,
    areaVerificada: texto(cuerpo, 'areaVerificada'),
    excesoVerificado: null,
    inspector: texto(cuerpo, 'inspector'),
    verificadoEn: D.HOY,
    estado: 'FIRME',
    motivoAnulacion: null,
    anuladoPor: null,
    anuladoEn: null,
  };
  hallazgos.push(hallazgo);
  return creado(hallazgo);
}

/* ── El hallazgo, su evidencia y su acta ────────────────────────────────── */

function hallazgoInexistente(hallazgoId: number): Respuesta {
  return problema('NO_ENCONTRADO', 404, `No hay ningun hallazgo ${hallazgoId} en esta municipalidad`);
}

function hallazgoSinEfecto(hallazgoId: number): Respuesta {
  return problema(
    'CONFLICTO',
    409,
    `El hallazgo ${hallazgoId} esta dejado sin efecto: ya no habilita ningun acto, asi que` +
      ' sustentarlo con mas evidencia no cambia nada',
  );
}

export function evidenciasDe(hallazgoId: number): Respuesta {
  if (!hallazgos.some((h) => h.id === hallazgoId)) return hallazgoInexistente(hallazgoId);
  return ok(evidencias.filter((e) => e.hallazgoId === hallazgoId));
}

export function adjuntarEvidencia(hallazgoId: number, cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const hallazgo = hallazgos.find((h) => h.id === hallazgoId);
  if (!hallazgo) return hallazgoInexistente(hallazgoId);
  if (hallazgo.estado === 'DEJADO_SIN_EFECTO') return hallazgoSinEfecto(hallazgoId);

  const huella = texto(cuerpo, 'sha256').toLowerCase();
  if (evidencias.some((e) => e.sha256 === huella)) {
    return problema(
      'CONFLICTO',
      409,
      `El archivo con huella ${huella} ya sustenta otro hallazgo en esta municipalidad: una foto no` +
        ' sustenta dos actas (ADR-0035 punto 3)',
    );
  }

  const capturadoEn = texto(cuerpo, 'capturadoEn');
  const dispositivo = texto(cuerpo, 'dispositivo');
  const evidencia: Fila = {
    id: siguienteId(evidencias),
    hallazgoId,
    tipo: texto(cuerpo, 'tipo').toUpperCase(),
    sha256: huella,
    ruta: texto(cuerpo, 'ruta'),
    capturadoEn,
    /* Los dos relojes salen los dos, que es el punto de ADR-0035 punto 3: el del
       aparato llega en la peticion y el del servidor lo pone quien recibe. */
    recibidoEn: AHORA,
    desfaseEnSegundos: desfase(capturadoEn, AHORA),
    dispositivo: dispositivo === '' ? null : dispositivo,
  };
  evidencias.push(evidencia);
  return creado(evidencia);
}

/**
 * `recibidoEn - capturadoEn`, en segundos, y **puede ser negativo**.
 *
 * Un negativo significa que el reloj del aparato va adelantado, y tambien es un
 * dato: por eso no se acota a cero. Si el instante que llego no se puede leer,
 * sale cero — el backend habria contestado 422 antes de llegar aqui, y este
 * proxy no valida.
 */
function desfase(capturado: string, recibido: string): number {
  const desde = Date.parse(capturado);
  const hasta = Date.parse(recibido);
  if (Number.isNaN(desde) || Number.isNaN(hasta)) return 0;
  return Math.round((hasta - desde) / 1000);
}

export function dejarSinEfecto(hallazgoId: number, cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const hallazgo = hallazgos.find((h) => h.id === hallazgoId);
  if (!hallazgo) return hallazgoInexistente(hallazgoId);
  if (hallazgo.estado === 'DEJADO_SIN_EFECTO') return hallazgoSinEfecto(hallazgoId);

  hallazgo.estado = 'DEJADO_SIN_EFECTO';
  hallazgo.motivoAnulacion = texto(cuerpo, 'motivo');
  hallazgo.anuladoPor = QUIEN;
  hallazgo.anuladoEn = AHORA;
  return ok({ ...hallazgo });
}

export function levantarActa(hallazgoId: number, cuerpo: unknown): Respuesta {
  const sinObservacion = faltaLaObservacion(cuerpo);
  if (sinObservacion) return sinObservacion;

  const hallazgo = hallazgos.find((h) => h.id === hallazgoId);
  if (!hallazgo) return hallazgoInexistente(hallazgoId);
  if (hallazgo.estado === 'DEJADO_SIN_EFECTO') return hallazgoSinEfecto(hallazgoId);

  const candidato = candidatos.find((c) => c.id === hallazgo.candidatoId);
  if (candidato !== undefined && candidato.estado !== 'VERIFICADO_EN_CAMPO') {
    return problema(
      'CONFLICTO',
      409,
      `Su candidato esta ${String(candidato.estado)} y no VERIFICADO_EN_CAMPO: un acta se levanta sobre` +
        ' lo que gabinete admitio y campo confirmo. Una ortofoto detecta techos, no predios (ADR-0035)',
    );
  }

  const numero = texto(cuerpo, 'numero');
  if (actas.some((a) => a.numero === numero || a.hallazgoId === hallazgoId)) {
    return problema(
      'CONFLICTO',
      409,
      `El acta '${numero}' ya existe en esta municipalidad, o el hallazgo ${hallazgoId} ya tiene la` +
        ' suya: dos actas del mismo hallazgo serian dos papeles que dicen lo mismo y dos plazos para' +
        ' el administrado',
    );
  }

  const acta: Fila = {
    id: siguienteId(actas),
    numero,
    hallazgoId,
    fecha: D.HOY,
    inspector: texto(cuerpo, 'inspector'),
    detalle: texto(cuerpo, 'detalle'),
  };
  actas.push(acta);
  return creado(acta);
}

/* ── Los hallazgos de un predio ─────────────────────────────────────────── */

/**
 * `GET /fiscalizacion/predios/{predioId}/hallazgos`, con sus dos desenlaces.
 *
 * El predio que no esta en el padron es **404**, y el que esta y no tiene
 * ninguno es **200 con la lista vacia**: no son la misma respuesta y no se
 * arreglan igual. Y la lista **no puede traer un omiso catastral**, no por un
 * filtro sino por construccion: un omiso no tiene predio al que apuntar.
 */
export function hallazgosDelPredio(predioId: number): Respuesta {
  const predio = D.PADRON.find((p) => p.predioId === predioId);
  if (!predio) {
    return problema(
      'NO_ENCONTRADO',
      404,
      `El predio ${predioId} no esta en el padron de esta municipalidad`,
    );
  }
  const suyos = hallazgos
    .filter((h) => h.predioId === predioId)
    .map((h) => {
      /* La campania sale del CANDIDATO y no del hallazgo: `HallazgoResource` no
         publica `campaniaId` —en la pagina de una campania seria el mismo en las
         cuatro mil filas—, y es justo lo que este otro `record` anade. */
      const candidato = candidatos.find((c) => c.id === h.candidatoId);
      const campania = campanias.find((c) => c.id === Number(candidato?.campaniaId ?? D.CAMPANIA.id));
      const acta = actas.find((a) => a.hallazgoId === h.id);
      return {
        id: h.id,
        candidatoId: h.candidatoId,
        campaniaId: campania?.id ?? D.CAMPANIA.id,
        campaniaCodigo: campania?.codigo ?? D.CAMPANIA.codigo,
        clase: h.clase,
        predioId,
        fichaId: h.fichaId,
        areaDeLaFicha: h.areaDeLaFicha,
        areaVerificada: h.areaVerificada,
        excesoVerificado: h.excesoVerificado,
        inspector: h.inspector,
        verificadoEn: h.verificadoEn,
        estado: h.estado,
        acta: acta === undefined ? null : { ...acta },
      };
    });
  return ok({ predioId, hallazgos: suyos });
}
