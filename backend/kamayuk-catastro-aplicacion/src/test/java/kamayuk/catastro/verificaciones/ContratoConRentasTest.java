package kamayuk.catastro.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import kamayuk.comun.verificaciones.contrato.ContratoConElConsumidorTestBase;
import kamayuk.comun.verificaciones.contrato.ContratoDelConsumidor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    /** La operacion por la que cruza la unica cifra de este sistema de la que cuelga un cobro. */
    private static final String LOS_FRENTES = "GET /catastro/predios/{predioId}/frentes";

    /**
     * AC-3 de #28 — el consumidor tiene que LEER el estado de la longitud del frente.
     *
     * <h2>Por que esto no lo cubre la comprobacion de arriba, medido</h2>
     *
     * <p>{@link #cumpleLoQueSuConsumidorEspera()} compara por <b>contencion</b> y en una sola
     * direccion: el proveedor no puede publicar MENOS de lo que el consumidor lee. Que el
     * consumidor deje de leer un campo no es un desajuste para esa regla —publicar de mas nunca
     * rompio a nadie— y por eso quitar {@code longitudEstado} del contrato comprometido deja el
     * build entero en VERDE. Medido, con la rotura aplicada sola sobre el archivo de `rentas`.
     *
     * <h2>Y por que la guarda va aqui, en el PROVEEDOR</h2>
     *
     * <p>Porque el que publica una cifra que nadie firmo es este lado. {@code
     * TerritorioParaPublicarJdbc.frentesPorPredio()} publica <b>todos</b> los frentes con su
     * estado, y publicar la propuesta es lo correcto: el consumidor necesita saber que falta por
     * confirmar, y recortarla dejaria a `rentas` sin poder distinguir «no hay frente» de «hay uno
     * sin confirmar». Lo que faltaba no era el recorte sino la guarda.
     *
     * <p>Y no puede vivir del lado de `rentas`: alli mediria dos archivos del mismo repositorio —lo
     * que P5E §6.3 se nego a escribir— y sobre todo el rojo le llegaria a quien no rompio nada.
     * Aqui, quien deje de leer el campo se lleva el rojo en el CI del proveedor, que es exactamente
     * el reparto de ADR-0030 §4 y el mismo mecanismo con el que C-1 cazo los siete desajustes que
     * `rentas` no podia ver.
     *
     * <h2>Lo que esta en juego, y no es una formalidad</h2>
     *
     * <p>{@code PROPUESTA} la corto una maquina contra el eje de la via; {@code CONFIRMADA} la
     * firmo una persona (ADR-0021). Las dos cifras se leen igual. `V10` lo escribe con todas las
     * letras: «de esta cifra cuelga un cobro […] dejarla como oficial cambiaria la base de los
     * arbitrios de todo el padron sin que nadie lo decidiera». {@code catastro} publica el insumo y
     * {@code rentas} determina el importe (ADR-0024); esta prueba no dice como se determina —no le
     * toca—, dice que el dato que permite decidirlo <b>llega y se lee</b>.
     *
     * <p>Se exigen {@code longitud} y {@code longitudEstado} juntos a proposito: leer la primera
     * sin la segunda es justo el defecto, y una guarda que solo mirara la segunda pasaria en verde
     * sobre un consumidor que no lee ninguna de las dos.
     */
    @Test
    @DisplayName("AC-3 — el consumidor lee el ESTADO de la longitud del frente (#28)")
    void elConsumidorLeeElEstadoDeLaLongitudDelFrente() {
        ContratoDelConsumidor contrato = ContratoDelConsumidor.leer(archivoDelConsumidor());

        ContratoDelConsumidor.OperacionEsperada esperada = contrato.operaciones().get(LOS_FRENTES);
        assertThat(esperada)
                .as(
                        "«%s» no esta en el contrato de «%s». Sin esa operacion no hay frente que"
                                + " leer, asi que esta comprobacion no mediria nada: son los"
                                + " metros lineales de los que cuelga el arbitrio que determina el"
                                + " consumidor (ADR-0024)",
                        LOS_FRENTES, consumidor())
                .isNotNull();

        assertThat(camposDelFrente(esperada.respuesta()))
                .as(
                        "«%s» publica cada frente con el estado de su longitud y «%s» no lo lee."
                                + " Una PROPUESTA la corto una maquina contra el eje de la via y una"
                                + " CONFIRMADA la firmo una persona (ADR-0021): sin ese campo las dos"
                                + " llegan iguales, y quien determine un arbitrio sobre metros que"
                                + " nadie confirmo no tiene como saberlo. El campo NO se deja de"
                                + " publicar —el consumidor necesita las propuestas para saber que"
                                + " falta por confirmar—: lo que se exige es que se lea.",
                        proveedor(), consumidor())
                .contains("longitud", "longitudEstado");
    }

    /** Los campos de un frente dentro de la respuesta que el consumidor declara leer. */
    private static Set<String> camposDelFrente(Object respuesta) {
        assertThat(respuesta)
                .as("la respuesta declarada para «%s» no es un objeto", LOS_FRENTES)
                .isInstanceOf(Map.class);
        Object frentes = ((Map<?, ?>) respuesta).get("frentes");
        assertThat(frentes)
                .as(
                        "«%s» no declara leer ninguna lista «frentes», asi que no hay campos de"
                                + " frente que comprobar y esta guarda se estaria cumpliendo sola",
                        LOS_FRENTES)
                .isInstanceOf(List.class);
        List<?> lista = (List<?>) frentes;
        assertThat(lista)
                .as(
                        "la lista «frentes» viene vacia: una lista sin forma dentro no declara"
                                + " ningun campo, y comparar contra ella pasaria siempre")
                .isNotEmpty();
        assertThat(lista.get(0)).isInstanceOf(Map.class);
        Set<String> campos = new java.util.TreeSet<>();
        ((Map<?, ?>) lista.get(0)).keySet().forEach(campo -> campos.add(String.valueOf(campo)));
        return campos;
    }
}
