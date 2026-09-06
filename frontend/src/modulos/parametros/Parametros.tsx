import type { PantallaProps } from '../../App';
import * as api from '../../api/parametros';
import { useRecurso } from '../../api/useRecurso';
import { Aviso, Dato, Insignia, Lectura, Rejilla, Seccion, Servida } from '../../ds/componentes';

/** Si el ejercicio de la barra global tiene conjunto sellado, y cual. */
export function Ejercicio({ ejercicio }: PantallaProps) {
  const conjunto = useRecurso((senal) => api.ejercicio(ejercicio, senal), ['ejercicio', ejercicio]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, maxWidth: 900 }}>
      <Seccion titulo="El conjunto del ejercicio" nota={`Ejercicio ${ejercicio}`}>
        <Lectura recurso={conjunto} espera="">
          {(c) => (
            <>
              <Rejilla>
                <Dato rotulo="Ejercicio">{c.ejercicio}</Dato>
                <Dato rotulo="Sellado">
                  {c.sellado ? <Insignia tono="ok">Si</Insignia> : <Insignia tono="warn">Todavia no</Insignia>}
                </Dato>
                <Dato rotulo="Conjunto">{c.conjuntoId}</Dato>
                <Dato rotulo="Version">{c.version}</Dato>
              </Rejilla>
              {!c.sellado ? (
                <div style={{ padding: '0 16px 14px' }}>
                  <Aviso tono="warn" titulo="Este ejercicio no tiene conjunto sellado">
                    No es un fallo: es la respuesta. El servidor contesta correctamente y dice que todavia no hay
                    nada sellado para {c.ejercicio}, asi que los cuadros de ese ejercicio no se pueden leer. Se
                    resuelve sellando el conjunto en «normativa», no desde esta pantalla.
                  </Aviso>
                </div>
              ) : null}
            </>
          )}
        </Lectura>
      </Seccion>

      <Aviso tono="info" titulo="Aqui no se sella nada">
        «catastro» no sella ningun valor normativo: eso es «normativa». Lo que hay en esta base es la copia local
        de un conjunto ya sellado, y esta pantalla dice si existe y con que version se esta trabajando.
      </Aviso>

      <Aviso tono="warn" titulo="El unico permiso de esta interfaz que no es una opcion del catalogo">
        La lectura que alimenta esta pantalla no exige ninguna opcion: exige el centinela «__sesion_propia__», que
        el guardia trata aparte y significa «basta con tener sesion». Se declara asi en la lista de modulos —y no
        como una opcion inventada— porque una opcion que el catalogo no tiene es un permiso que nadie puede
        conceder.
      </Aviso>

      <Servida
        lee={[api.RUTAS.ejercicio]}
        falta="Sellar el conjunto no lo sirve ninguna ruta de este backend: se sella en «normativa», y aqui solo se lee la copia local."
      />
    </div>
  );
}
