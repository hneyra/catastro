/**
 * Que ejercicios ofrece la barra global, y de donde salen.
 *
 * <h2>Que estaba mal, y cuando se veia</h2>
 *
 * Eran cuatro literales congelados en `Shell.tsx` —`['2026','2025','2024','2023']`—
 * y el ejercicio **no es cromo**: decide de que conjunto sellado se leen los tres
 * cuadros —valores unitarios, depreciacion y aranceles— y viaja como
 * `?ejercicio=` a las tres lecturas. El 1 de enero de 2027 nadie podia elegir
 * 2027: la interfaz se quedaba en 2026, ensenaba los cuadros de 2026 **y parecia
 * correcta**. Sin error, sin aviso, y la unica senal habria sido que alguien se
 * fijara en que el desplegable no ofrece el ano en curso. Es la misma forma de
 * defecto que `datos.mjs` existe para impedir en `src/datos/` —una cifra escrita
 * a mano se pinta igual que una leida del servidor— por un sitio que ese arnes no
 * mira: solo recorre `src/datos/`, y esto estaba en el armazon.
 *
 * <h2>Por que se admite un reloj aqui, y por que no contradice la regla 6</h2>
 *
 * La sexta regla de `CLAUDE.md` dice «las reglas tributarias son funciones puras:
 * sin base de datos, **sin reloj**, sin configuracion global», y su motivo esta
 * escrito al lado: *recalcular 2027 en 2037 debe dar el mismo centimo*. Esto no
 * es una regla tributaria y no calcula nada —es que anos ofrece un desplegable—,
 * y recalcular sigue dando lo mismo: el ano elegido viaja en `?ejercicio=` y el
 * backend contesta con el conjunto sellado de ESE ano, hoy y dentro de diez.
 *
 * Y aun asi el reloj no se lee aqui dentro. Las dos funciones **reciben la
 * fecha** y son puras, asi que una prueba puede fijarla; el unico reloj de todo
 * `src/` esta en `App.tsx`, se lee **una vez al montar** y las dos derivaciones
 * salen del mismo instante. Leerlo dos veces podria dar dos anos distintos a las
 * 23:59:59 del 31 de diciembre, y entonces el desplegable ofreceria una lista en
 * la que el valor elegido no esta —un `<select>` sin ninguna opcion que case no
 * ensena nada elegido y no avisa de nada—.
 *
 * Que la lista sale del reloj **de verdad**, y no de cuatro literales que hoy
 * coinciden con el, lo mide `verificaciones/ejercicios.mjs` moviendo el reloj del
 * navegador a un ano que no es este: con los literales puestos, cualquier guarda
 * que no mueva el reloj pasa en verde.
 *
 * <h2>La lista es una SUPOSICION, y lo sera hasta #51</h2>
 *
 * Que un ano salga en el desplegable **no dice que tenga conjunto sellado**:
 * nadie se lo ha preguntado al backend. La lectura que contestaria *cuales* hay
 * no existe —la que hay, `GET /seguridad/parametros/ejercicios/{ejercicio}`, es
 * **por ejercicio**: hay que saber cual preguntar, que es justo lo que falta— y
 * tiene issue propio, **#51**. Derivarla de ahi es la salida correcta y este
 * archivo desaparece el dia que aterrice.
 *
 * Mientras tanto la suposicion falla **ruidosa**, que es lo contrario del defecto
 * que esto cierra: elegir un ejercicio sin sellar hace que las tres lecturas de
 * cuadro contesten 404 con `parametroQueFalta` y sin `llave`, y la pantalla lo
 * dibuja como «falta sellar el conjunto de parametros del ejercicio», que dice
 * ademas que no lo arregla nadie desde la pantalla.
 */

/**
 * Cuantos ejercicios cerrados acompanan al que corre.
 *
 * Tres, que son los que habia escritos y los que el artboard dibuja. No sale de
 * ninguna norma —ningun plazo del TUO LTM son tres anos— y por eso no vive en
 * `src/datos/`: es cuanto pasado se alcanza de un vistazo, y nada mas.
 */
const CERRADOS_QUE_SE_OFRECEN = 3;

/**
 * El ejercicio en curso el dia `hoy`, que es el que la barra trae elegido.
 *
 * Del ano LOCAL y no del UTC: el ejercicio de una municipalidad empieza a su
 * medianoche, no a la de Greenwich, y en Peru las dos se llevan cinco horas.
 */
export function ejercicioEnCurso(hoy: Date): string {
  return String(hoy.getFullYear());
}

/**
 * Los ejercicios que la barra ofrece el dia `hoy`: el que corre delante y los
 * cerrados detras, del mas reciente al mas viejo.
 *
 * Siempre trae el de `ejercicioEnCurso(hoy)` en primer lugar, y de eso depende
 * que el valor elegido sea siempre una de las opciones.
 */
export function ejerciciosOfrecidos(hoy: Date): readonly string[] {
  const enCurso = hoy.getFullYear();
  return Array.from({ length: CERRADOS_QUE_SE_OFRECEN + 1 }, (_, cuantos) => String(enCurso - cuantos));
}
