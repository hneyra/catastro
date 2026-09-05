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
 * <h2>Los siete desajustes que ya estaban, cerrados en C-1</h2>
 *
 * <p>Esta prueba nacio roja, y no por un cambio: las dos fronteras ya estaban rotas y no habia nada
 * que pudiera verlo. {@link #desajustesVivos()} los registraba uno a uno; C-1 los cerro y la lista
 * quedo <b>vacia</b>, que es donde tiene que estar: con la lista a cero, un desajuste nuevo no
 * tiene donde esconderse. La lista sigue con las dos direcciones cerradas —uno nuevo pone el build
 * rojo, y una entrada que ya no ocurre tambien—.
 *
 * <p>Cual de los dos lados pago la traduccion se decidio uno a uno, y esta escrito donde se hizo el
 * cambio. De este lado se pagaron cuatro:
 *
 * <ul>
 *   <li><b>{@code id} pasa a {@code fichaId}</b> en {@link
 *       kamayuk.catastro.nucleo.infraestructura.web.FichaEncontradaResource}: la fila lleva dos
 *       identificadores y {@code id} al lado de {@code predioId} no dice cual. El sintoma era MUDO
 *       —{@code asLong()} sobre un nodo que falta devuelve 0—.
 *   <li><b>{@code soloPredio} y {@code exceptoPredio} se leen</b> en {@code ConsultaController}:
 *       dejarlos caer devolvia la grilla del padron entero a una conciliacion que pedia unos lotes,
 *       o sea #631 deshecho por la separacion en repositorios.
 *   <li><b>{@code ?anio=} pasa a {@code ?ejercicio=}</b> en las tres lecturas de cuadro: lo que
 *       acota es el ejercicio del conjunto sellado, y en la misma respuesta viaja un {@code
 *       anioConstruccionDesde} que si es un ano.
 * </ul>
 *
 * <p>Los otros tres los pago {@code rentas}, y el motivo esta en su adaptador: {@code
 * vigenciaDesde} es texto en el JSON venga de un {@code String} o de un {@code LocalDate}; el
 * cuadro sellado se lee entero y por eso sale como array y no como sobre paginado; y {@code fecha}
 * es como esta capa web nombra la fecha de corte en <b>siete</b> endpoints, asi que renombrar uno
 * dejaria dos nombres para el mismo criterio dentro del proveedor.
 *
 * <p><b>Y una premisa del registro de P6 resulto falsa al medirla</b>: decia que {@code
 * ConsultaController} «declara el parametro {@code fecha} y lo ignora — la ficha vigente la
 * resuelve con {@code LocalDate.now(reloj)}». No lo ignora: lo pasa a {@code
 * ConsultaDeFichas.buscar} y de ahi al {@code WHERE f.vigencia_desde <= :fecha} del repositorio. El
 * efecto que P6 describe —pedir marzo y recibir la ficha de hoy— era real, y su causa era el
 * nombre: como {@code aLaFecha} no llegaba, se tomaba el valor por omision del reloj. Se cerraba
 * renombrando, y se renombro.
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
     * <b>Vacia, y esa es la afirmacion.</b> Lo que {@code rentas} espera de este backend, este
     * backend lo cumple entero: cada campo que lee, cada parametro que manda.
     *
     * <p>Se deja declarada en vez de borrar el metodo a proposito, por lo mismo que #429 dejo
     * declarada la lista de hojas pendientes con la lista vacia: lo que permite es una excepcion
     * <b>temporal y con nombre</b>, y con la lista vacia un desajuste nuevo no tiene donde
     * esconderse. Anadir una linea aqui vuelve a ser una decision que se ve en el diff.
     *
     * <p>El texto de una entrada, si alguna vez vuelve a haberla, es exacto porque tiene que serlo:
     * una lista que aceptara «algo parecido» dejaria entrar un desajuste nuevo del mismo campo.
     */
    @Override
    protected Set<String> desajustesVivos() {
        return Set.of();
    }
}
