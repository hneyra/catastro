import { useCallback, useEffect, useRef, useState } from 'react';
import { ErrorDeApi, alCambiarLaSesion, sesionActual } from './cliente';

/**
 * Una lectura con sus cuatro estados: pidiendo, con datos, con error, y sin
 * pedir todavia.
 *
 * Cancela la anterior al cambiar las llaves y **descarta la que vuelve tarde**:
 * sin eso, dos busquedas seguidas pueden pintar la primera respuesta encima de
 * la segunda y quien mira ve el resultado de lo que ya no pregunto.
 *
 * `activo` existe para las pantallas que necesitan un sujeto: mientras nadie
 * haya escrito un codigo de predio no se pide nada, y el estado no es «cargando»
 * ni «error» sino **«falta el sujeto»**, que es lo que la pantalla dice.
 */
export type Recurso<T> = {
  datos: T | null;
  cargando: boolean;
  error: ErrorDeApi | null;
  /** Si aun no se ha pedido nada porque falta el sujeto. */
  enEspera: boolean;
  reintentar: () => void;
};

export function useRecurso<T>(
  pedir: (senal: AbortSignal) => Promise<T>,
  llaves: ReadonlyArray<string | number | boolean | null | undefined>,
  activo = true,
): Recurso<T> {
  const [datos, setDatos] = useState<T | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<ErrorDeApi | null>(null);
  const [intento, setIntento] = useState(0);
  const [deLaSesion, setDeLaSesion] = useState(sesionActual());

  /* La funcion cambia de identidad en cada render; lo que decide si hay que
     volver a pedir son las LLAVES, no ella. Guardarla en una referencia es lo
     que evita el bucle infinito que produce ponerla en las dependencias. */
  const pedirRef = useRef(pedir);
  pedirRef.current = pedir;

  useEffect(() => alCambiarLaSesion(() => setDeLaSesion(sesionActual())), []);

  const firma = JSON.stringify(llaves);

  useEffect(() => {
    if (!activo) {
      setDatos(null);
      setError(null);
      setCargando(false);
      return;
    }
    const control = new AbortController();
    let vigente = true;
    setCargando(true);
    setError(null);
    pedirRef
      .current(control.signal)
      .then((r) => {
        if (!vigente) return;
        setDatos(r);
        setCargando(false);
      })
      .catch((fallo: unknown) => {
        if (!vigente) return;
        /* Cancelar no es fallar: si la pantalla cambio, no hay nada que decir. */
        if (fallo instanceof DOMException && fallo.name === 'AbortError') return;
        setError(
          fallo instanceof ErrorDeApi
            ? fallo
            : new ErrorDeApi('SIN_RESPUESTA', 'No se pudo completar la lectura', 0),
        );
        setDatos(null);
        setCargando(false);
      });
    return () => {
      vigente = false;
      control.abort();
    };
  }, [firma, activo, intento, deLaSesion]);

  const reintentar = useCallback(() => setIntento((n) => n + 1), []);

  return { datos, cargando, error, enEspera: !activo, reintentar };
}
