package kamayuk.catastro.verificaciones;

import java.util.Set;
import kamayuk.comun.verificaciones.contrato.ContratoConElConsumidorTestBase;
import org.junit.jupiter.api.DisplayName;

/**
 * {@code catastro} sigue cumpliendo lo que {@code rentas} espera de el (ADR-0030 §4).
 *
 * <p>El contrato lo publica {@code rentas} —{@code
 * rentas/docs/50-api/contratos-que-consume/catastro.json}— derivado de lo que sus adaptadores piden
 * y leen de verdad. Esta prueba lo lee y compara contra los controladores de aqui.
 *
 * <p><b>Esta prueba vive en este repositorio a proposito</b>, y es la mitad que P5E §6.3 no pudo
 * escribir: «nada impide hoy que catastro renombre una de sus dos rutas y que ClienteHttpDeCatastro
 * siga pidiendo la vieja; eso solo aparece al integrar». Desde aqui, quitarle un campo a {@code
 * FichaEncontradaResource} pone rojo <b>este</b> build, en el PR que lo quita.
 *
 * <h2>Los desajustes que ya estaban</h2>
 *
 * <p>Esta prueba nacio roja, y no por un cambio: las dos fronteras ya estaban rotas y no habia nada
 * que pudiera verlo. {@link #desajustesVivos()} los registra uno a uno, y la lista tiene las dos
 * direcciones cerradas — uno nuevo pone el build rojo, y uno que ya no ocurre tambien—.
 *
 * <p>Ninguno se arregla aqui, y hay que decir por que: el que se arregle decide de que lado se paga
 * la traduccion, y esa es una decision de las dos partes. El de {@code fichaId} contra {@code id}
 * puede arreglarse en cualquiera de los dos; el de {@code aLaFecha} contra {@code fecha} <b>no</b>,
 * porque no es un nombre: {@code ConsultaController} declara el parametro y lo ignora — la ficha
 * vigente la resuelve con {@code LocalDate.now(reloj)}, asi que preguntar por marzo devuelve la de
 * hoy, que es el defecto de #24 y #366 servido por HTTP.
 */
@DisplayName("Contrato con rentas (catastro es el proveedor)")
class ContratoConRentasTest extends ContratoConElConsumidorTestBase {

    @Override
    protected String consumidor() {
        return "rentas";
    }

    @Override
    protected String proveedor() {
        return "catastro";
    }

    /**
     * Lo que hoy no cuadra, medido y no supuesto. Cada linea es deuda con nombre.
     *
     * <p>El texto es exacto porque tiene que serlo: una lista que aceptara «algo parecido» dejaria
     * entrar un desajuste nuevo del mismo campo, que es justo lo que no puede pasar.
     */
    @Override
    protected Set<String> desajustesVivos() {
        return Set.of(
                // (1) `FichaEncontradaResource` publica `id` y el adaptador lee `fichaId`.
                // El sintoma es MUDO: `asLong()` sobre un nodo que falta devuelve 0, asi que
                // toda ficha llega a `rentas` con `fichaId = 0` y ninguna cifra parece mal.
                "GET /catastro/fichas: falta el campo «contenido[].fichaId», que el consumidor lee."
                        + " Este endpoint declara [areaConstruida, areaTerreno, codRefCatastral,"
                        + " direccion, id, lote, manzana, predioId, tipo, titular, uso, version,"
                        + " vigenciaDesde].",
                // (2) `vigenciaDesde` es `String` aqui y el adaptador lo lee con
                // `LocalDate.parse`. Coinciden por casualidad —el `String` lleva un ISO—, y
                // por eso entra en la lista en vez de arreglarse a ciegas: cambiarlo a
                // `LocalDate` cambia lo que Jackson emite para TODOS sus consumidores.
                "GET /catastro/fichas: el campo «contenido[].vigenciaDesde» es «texto» y el"
                        + " consumidor lo lee como «fecha».",
                // (3) El criterio de fecha viaja y se descarta. No es un nombre: el
                // controlador declara `fecha`, no lo usa, y resuelve con el reloj.
                "GET /catastro/fichas: el consumidor manda «aLaFecha» y este endpoint no lo lee"
                        + " (lee [codRefCatastral, conciliadaConRentas, contribuyente, direccion,"
                        + " fecha, lote, manzana, ordenarPor, pagina, tamano, tipo]). Viaja en la URL y"
                        + " se descarta en silencio.",
                // (4) y (5) La acotacion por predio de #631 no llega: la grilla se pide para
                // unos lotes concretos y vuelve la del padron entero.
                "GET /catastro/fichas: el consumidor manda «exceptoPredio» y este endpoint no lo"
                        + " lee (lee [codRefCatastral, conciliadaConRentas, contribuyente, direccion,"
                        + " fecha, lote, manzana, ordenarPor, pagina, tamano, tipo]). Viaja en la URL y"
                        + " se descarta en silencio.",
                "GET /catastro/fichas: el consumidor manda «soloPredio» y este endpoint no lo lee"
                        + " (lee [codRefCatastral, conciliadaConRentas, contribuyente, direccion,"
                        + " fecha, lote, manzana, ordenarPor, pagina, tamano, tipo]). Viaja en la URL y"
                        + " se descarta en silencio.",
                // (6) El cuadro de valores unitarios devuelve un ARRAY plano y el adaptador
                // itera `contenido`: la lista sale vacia con un 200 delante, que se lee como
                // «este ejercicio no tiene cuadro publicado».
                "GET /catastro/tablas/valores-unitarios: en «(la respuesta)» el consumidor espera"
                        + " un objeto y este endpoint publica «[{id=entero, partida=texto,"
                        + " categoria=texto, anioConstruccionDesde=entero,"
                        + " anioConstruccionHasta=entero, valorM2=texto, documentoFuente=texto}]».",
                // (7) `?ejercicio=` contra `@RequestParam int anio`, que ademas es
                // obligatorio: la peticion no llega a 200, sale 400 y el cliente la traduce a
                // `CatastroInalcanzable` — «catastro no responde» donde lo que pasa es que el
                // parametro se llama de otra manera.
                "GET /catastro/tablas/valores-unitarios: el consumidor manda «ejercicio» y este"
                        + " endpoint no lo lee (lee [anio]). Viaja en la URL y se descarta en"
                        + " silencio.");
    }
}
