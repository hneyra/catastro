import { useCallback, useEffect, useMemo, useState } from 'react';
import { Shell } from './shell/Shell';
import type { Pestania } from './shell/Shell';
import { DESTINO_INICIAL, destinoDe } from './shell/modulos';
import { RUTA_VACIA, escribirRuta, leerRuta } from './shell/ruta';
import type { Ruta } from './shell/ruta';
import { PANTALLAS } from './modulos/pantallas';

/** Lo que recibe cada pantalla. Ninguna toca el hash directamente. */
export type PantallaProps = {
  ruta: Ruta;
  /** Fija el sujeto que la pantalla mira. **Conserva los filtros.** */
  onSujeto: (sujeto: string) => void;
  /** Cambia los filtros. Va por `replaceState`: no llena el historial. */
  onFiltros: (filtros: Record<string, string>) => void;
  /** El ejercicio de la barra global, que es global a la sesion. */
  ejercicio: number;
};

/**
 * La entidad.
 *
 * Es un dato de la instalacion y no cromo: el producto es multi-municipal y una
 * instalacion atiende a muchas. Hoy vale la municipalidad piloto; cuando exista
 * la puerta de sesion saldra del token, como el `municipalidad_id`.
 */
const ENTIDAD = 'Municipalidad Distrital de Catacaos';

export default function App() {
  const [ruta, setRuta] = useState<Ruta>(() => resolver(leerRuta(window.location.hash)));
  const [pestanas, setPestanas] = useState<readonly Pestania[]>(() => {
    const inicial = resolver(leerRuta(window.location.hash));
    return [{ modulo: inicial.modulo, hoja: inicial.destino }];
  });
  const [ejercicio, setEjercicio] = useState('2026');

  /* La barra del navegador es la fuente: se escucha `hashchange` y no se guarda
     una copia de la ruta en dos sitios. Con dos copias, ir «atras» deja la
     pantalla mostrando lo de antes y la URL diciendo otra cosa. */
  useEffect(() => {
    const alCambiar = () => setRuta(resolver(leerRuta(window.location.hash)));
    window.addEventListener('hashchange', alCambiar);
    return () => window.removeEventListener('hashchange', alCambiar);
  }, []);

  /* Al entrar sin ruta, se escribe la inicial: asi la URL siempre dice donde se
     esta y se puede compartir tal cual. No depende del estado —vuelve a leer el
     hash— para que la lista de dependencias vacia sea cierta y no una excepcion
     escrita a mano. */
  useEffect(() => {
    if (window.location.hash === '') {
      window.history.replaceState(null, '', escribirRuta(resolver(leerRuta(''))));
    }
  }, []);

  /* La pestana la abre la RUTA y no el clic.
     Escrito solo en `ir()`, llegar por URL —pegar un enlace, recargar, el boton
     «atras»— dibujaba la pantalla pedida con la barra de pestanas senalando otra:
     el titulo decia «Predios», el panel realzaba «Predios» y la unica pestana
     decia «Panel». No es un fallo ruidoso —no hay error de consola y el `<main>`
     esta lleno, asi que `mirar` pasa en verde— y deja la interfaz contradiciendose
     a si misma. Al colgarlo de la ruta, los tres sitios dicen lo mismo venga de
     donde venga. */
  useEffect(() => {
    if (ruta.modulo === '' || ruta.destino === '') return;
    setPestanas((abiertas) =>
      abiertas.some((t) => t.modulo === ruta.modulo && t.hoja === ruta.destino)
        ? abiertas
        : [...abiertas, { modulo: ruta.modulo, hoja: ruta.destino }],
    );
  }, [ruta.modulo, ruta.destino]);

  const navegar = useCallback((siguiente: Ruta, reemplazar = false) => {
    const hash = escribirRuta(siguiente);
    if (reemplazar) {
      window.history.replaceState(null, '', hash);
      setRuta(siguiente);
      return;
    }
    /* `location.hash` dispara `hashchange` y de ahi sale el `setRuta`. Si el
       hash no cambia no dispara nada, y hay que fijarlo a mano. */
    if (`#${window.location.hash.replace(/^#/, '')}` === hash) setRuta(siguiente);
    else window.location.hash = hash;
  }, []);

  const ir = useCallback(
    (modulo: string, hoja: string) => {
      /* La pestana no se abre aqui: la abre el efecto que escucha la ruta, para
         que llegar por clic y llegar por URL hagan exactamente lo mismo. */
      /* Cambiar de destino BORRA EL SUJETO: el codigo de predio que se estaba
         mirando en Zonificacion no nombra nada en Territorio, y arrastrarlo
         haria que la pantalla nueva pidiera por un sujeto que nadie eligio. Los
         filtros se van con el, por lo mismo. */
      navegar({ modulo, destino: hoja, sujeto: '', filtros: {} });
    },
    [navegar],
  );

  const cerrar = useCallback(
    (modulo: string, hoja: string) => {
      setPestanas((abiertas) => {
        const i = abiertas.findIndex((t) => t.modulo === modulo && t.hoja === hoja);
        if (i < 0) return abiertas;
        const quedan = abiertas.filter((_, j) => j !== i);
        /* Cerrar la activa lleva a la vecina: la de la izquierda si hay, y si no
           la de la derecha. Cerrar la ultima deja el espacio vacio, que es
           honesto: no hay nada abierto. */
        if (ruta.modulo === modulo && ruta.destino === hoja) {
          const vecina = quedan[Math.max(i - 1, 0)];
          navegar(
            vecina ? { modulo: vecina.modulo, destino: vecina.hoja, sujeto: '', filtros: {} } : RUTA_VACIA,
            true,
          );
        }
        return quedan;
      });
    },
    [navegar, ruta.destino, ruta.modulo],
  );

  const onSujeto = useCallback(
    (sujeto: string) => navegar({ ...ruta, sujeto }),
    [navegar, ruta],
  );

  /* Los filtros van por `replaceState`: se teclean letra a letra, y con un
     `pushState` por pulsacion el boton «atras» tendria que pulsarse una vez por
     caracter para salir de la pantalla. */
  const onFiltros = useCallback(
    (filtros: Record<string, string>) => navegar({ ...ruta, filtros }, true),
    [navegar, ruta],
  );

  const destino = destinoDe(ruta.modulo, ruta.destino);
  const Pantalla = PANTALLAS[`${ruta.modulo}/${ruta.destino}`];

  const contenido = useMemo(() => {
    if (!Pantalla) return null;
    return <Pantalla ruta={ruta} onSujeto={onSujeto} onFiltros={onFiltros} ejercicio={Number(ejercicio)} />;
  }, [Pantalla, ruta, onSujeto, onFiltros, ejercicio]);

  return (
    <Shell
      ruta={ruta}
      entidad={ENTIDAD}
      ejercicio={ejercicio}
      onEjercicio={setEjercicio}
      pestanas={pestanas}
      onIr={ir}
      onCerrar={cerrar}
      titulo={destino ? destino.hoja.label : 'Destino desconocido'}
      subtitulo={destino ? `${destino.modulo.label} · ${destino.hoja.nota}` : ruta.destino}
    >
      {contenido ?? <Desconocido ruta={ruta} />}
    </Shell>
  );
}

/** Un hash que no nombra ningun destino cae en el inicial, no en una pantalla vacia. */
function resolver(ruta: Ruta): Ruta {
  if (destinoDe(ruta.modulo, ruta.destino)) return ruta;
  return { modulo: DESTINO_INICIAL.modulo, destino: DESTINO_INICIAL.hoja, sujeto: '', filtros: {} };
}

function Desconocido({ ruta }: { ruta: Ruta }) {
  return (
    <p style={{ margin: 0, fontSize: 13.5, lineHeight: 1.6, color: 'var(--tinta-3)' }}>
      «{ruta.modulo}/{ruta.destino}» no es ningun destino de este sistema. Elija uno en el menu de la izquierda.
    </p>
  );
}
