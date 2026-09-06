package kamayuk.catastro.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Dinero;
import kamayuk.catastro.dominio.ValorNormativo;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La funcion pura que decide la valuacion (#8, ADR-0027).
 *
 * <h2>Por que esta prueba no toca la base</h2>
 *
 * <p>Porque la regla 6 dice que la valuacion es una funcion pura —sin base, sin reloj, sin
 * configuracion— y una prueba que necesitara PostgreSQL para ejercitar sus ramas seria la evidencia
 * de que dejo de serlo. Lo que si mide contra PostgreSQL real es {@code
 * PublicacionDelPadronJdbcTest}, y mide otra cosa: que los insumos lleguen.
 *
 * <h2>Las cifras de aqui no son valores normativos</h2>
 *
 * <p>Son de relleno y estan escogidas para que la aritmetica se pueda leer a simple vista —un
 * arancel de 10 y un area de 100 dan 1 000—. Los valores de verdad los sella {@code normativa} y
 * este sistema no escribe ninguno (regla 5).
 */
@DisplayName("#8 — La valuacion del predio: o las cuatro cifras, o el motivo")
class ValorizacionDelPredioTest {

    private static final LocalDate CORTE = LocalDate.of(2026, 1, 1);
    private static final int EJERCICIO = 2026;
    private static final long CONJUNTO = 7L;

    /** Un arancel de relleno: diez por metro cuadrado, para que la cuenta se lea. */
    private static final ValorNormativo ARANCEL = ValorNormativo.de("10");

    /** El «% actualizacion» de 2026: cero, con el fundamento del art. 12 del TUO LTM. */
    private static final ValorNormativo SIN_ACTUALIZACION = ValorNormativo.de("0");

    /** Un cuadro con las tres partidas de la categoria C, y nada mas. */
    private static final ValorizacionDelPredio.CuadroDeValoresUnitarios CUADRO =
            new ValorizacionDelPredio.CuadroDeValoresUnitarios(
                    List.of(
                            celda(Partida.MUROS, 'C', "100"),
                            celda(Partida.TECHOS, 'C', "50"),
                            celda(Partida.PUERTAS, 'C', "25")));

    private static ValorUnitarioEdificacion celda(Partida partida, char categoria, String valor) {
        return new ValorUnitarioEdificacion(
                null, partida, categoria, 1990, null, ValorNormativo.de(valor), "cuadro de prueba");
    }

    // ------------------------------------------------------------------
    // Lo que si se valoriza
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un lote sin construir sale con las CUATRO cifras, y su construccion vale cero")
    void unLoteSinConstruirSaleConSusCuatroCifras() {
        ValuacionDelPredio valuacion = valorizar(ficha(List.of(), 0));

        assertThat(valuacion.seValorizo()).isTrue();
        assertThat(valuacion.valorTerreno()).isEqualTo(Dinero.de("1000"));
        // CERO porque no hay nada construido ni ninguna obra declarada, no porque falte una
        // cifra: lo que falta sale por su rama, con su llave. Es la distincion de #48 por su otra
        // cara — negarse a valorizar un terreno porque no tiene casa seria el defecto inverso.
        assertThat(valuacion.valorConstruccion()).isEqualTo(Dinero.CERO);
        assertThat(valuacion.valorObras()).isEqualTo(Dinero.CERO);
        assertThat(valuacion.valorDelPredio()).isEqualTo(Dinero.de("1000"));
        assertThat(valuacion.motivo()).isNull();
        assertThat(valuacion.llaveQueFalta()).isNull();
        assertThat(valuacion.reglasAplicadas()).isEqualTo("TERRENO");
    }

    @Test
    @DisplayName("el «% actualizacion» de 2026 vale cero y no mueve la cifra ni su escala")
    void elPorcentajeDeCeroNoMueveLaCifra() {
        ValuacionDelPredio valuacion = valorizar(ficha(List.of(), 0));

        // `x + x·0` y `x` son el mismo importe; lo que la rama evita es que multiplicar por uno
        // le anada decimales al resultado, que es la escala que D-03a deja abierta.
        assertThat(valuacion.valorDelPredio()).isEqualTo(valuacion.valorTerreno());
        assertThat(java.util.Objects.requireNonNull(valuacion.valorDelPredio()).valor().scale())
                .as("la escala es la del producto area x arancel, y nadie le anade decimales")
                .isEqualTo(
                        new AreaM2(new java.math.BigDecimal("100.00")).valor().scale()
                                + ARANCEL.valor().scale());
    }

    // ------------------------------------------------------------------
    // Lo que no se valoriza, en el orden de las ramas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sin ficha vigente no hay con que valorizar, y no se nombra ninguna llave")
    void sinFichaNoHayLlaveQueNombrar() {
        ValuacionDelPredio valuacion = valorizar(null);

        assertThat(valuacion.motivo()).contains("no tiene ficha catastral vigente al 2026-01-01");
        assertThat(valuacion.llaveQueFalta())
                .as("se arregla fichando el predio, no publicando una cifra")
                .isNull();
        assertThat(valuacion.fichaCatastralId()).isNull();
    }

    @Test
    @DisplayName("sin el cuadro de valores unitarios, la llave es la del cuadro y del ejercicio")
    void sinCuadroDeValoresUnitarios() {
        ValuacionDelPredio valuacion =
                valorizar(
                        ficha(List.of(), 0),
                        new ValorizacionDelPredio.CuadroDeValoresUnitarios(List.of()),
                        true,
                        ARANCEL,
                        SIN_ACTUALIZACION);

        assertThat(valuacion.llaveQueFalta()).isEqualTo("VALOR_UNITARIO:2026");
        assertThat(valuacion.motivo()).contains("GOB-03 H-14");
    }

    @Test
    @DisplayName("sin el cuadro de depreciacion, la llave es la suya y no la del valor unitario")
    void sinCuadroDeDepreciacion() {
        ValuacionDelPredio valuacion =
                valorizar(ficha(List.of(), 0), CUADRO, false, ARANCEL, SIN_ACTUALIZACION);

        assertThat(valuacion.llaveQueFalta()).isEqualTo("DEPRECIACION:2026");
    }

    @Test
    @DisplayName("sin arancel de la via no hay valor de terreno, y lo dice con D-02b")
    void sinArancelDeLaVia() {
        ValuacionDelPredio valuacion =
                valorizar(ficha(List.of(), 0), CUADRO, true, null, SIN_ACTUALIZACION);

        assertThat(valuacion.llaveQueFalta()).isEqualTo("ARANCEL:2026");
        assertThat(valuacion.motivo()).contains("D-02b");
    }

    @Test
    @DisplayName("sin el «% actualizacion» sellado, vuelve a ser la rama que paraba a todos")
    void sinPorcentajeDeActualizacion() {
        ValuacionDelPredio valuacion = valorizar(ficha(List.of(), 0), CUADRO, true, ARANCEL, null);

        assertThat(valuacion.llaveQueFalta())
                .isEqualTo(ValorizacionDelPredio.PORCENTAJE_DE_ACTUALIZACION);
        assertThat(valuacion.motivo())
                .as(
                        "y dice que 2026 es la excepcion y no la regla, para que nadie lea el cero"
                                + " como un valor por omision")
                .contains("SOLO para 2026")
                .contains("art. 12");
    }

    @Test
    @DisplayName("una construccion sin una de las tres categorias no se puede valorizar")
    void unaConstruccionSinUnaDeLasTresCategorias() {
        ValuacionDelPredio valuacion =
                valorizar(
                        ficha(
                                List.of(
                                        new ValorizacionDelPredio.Edificacion(
                                                "1", AreaM2.de("80.00"), 2010, 'C', null, 'C')),
                                0));

        assertThat(valuacion.motivo()).contains("no declara la categoria de «TECHOS»");
        assertThat(valuacion.llaveQueFalta())
                .as("no falta ninguna cifra normativa: falta describir la edificacion")
                .isNull();
    }

    @Test
    @DisplayName("una casilla que el cuadro NO publica no vale cero: se nombra")
    void unaCasillaQueElCuadroNoPublica() {
        // La categoria H de muros y columnas son puntos suspensivos en el Anexo I.2, y §1.1 del
        // corpus lo dice: «no son un dato que falte en esta transcripcion ni un cero». Con la
        // casilla ausente el predio NO se valoriza, que es lo contrario de valorizarlo al 0,00
        // (#48). Y es una llave distinta de «falta el cuadro»: el cuadro esta entero.
        ValuacionDelPredio valuacion =
                valorizar(
                        ficha(
                                List.of(
                                        new ValorizacionDelPredio.Edificacion(
                                                "1", AreaM2.de("80.00"), 2010, 'H', 'C', 'C')),
                                0));

        assertThat(valuacion.llaveQueFalta()).isEqualTo("VALOR_UNITARIO:MUROS:H");
        assertThat(valuacion.motivo()).contains("puntos suspensivos");
    }

    @Test
    @DisplayName("con las tres casillas puestas, lo que queda es RT-004: que tabla del Anexo I")
    void conLasTresCasillasPuestasLoQueQuedaEsRT004() {
        ValuacionDelPredio valuacion =
                valorizar(
                        ficha(
                                List.of(
                                        new ValorizacionDelPredio.Edificacion(
                                                "1", AreaM2.de("80.00"), 2010, 'C', 'C', 'C')),
                                0));

        // Es el hallazgo de #8 y el que el informe agrupa: `normativa` sella las CUATRO tablas
        // del Anexo I, y lo que no existe es la traduccion del uso de la ficha al numero de
        // tabla. `depreciacion.md` §3: «es criterio, no transcripcion … RT-004 sigue sin
        // escribirse». La llave lleva el uso para que se puedan contar por uso.
        assertThat(valuacion.llaveQueFalta())
                .isEqualTo(ValorizacionDelPredio.TABLA_DE_DEPRECIACION + ":CASA_HABITACION");
        assertThat(valuacion.motivo()).contains("RT-004");
    }

    @Test
    @DisplayName("un predio con obras complementarias tampoco se valoriza, y por otra llave")
    void conObrasComplementarias() {
        ValuacionDelPredio valuacion = valorizar(ficha(List.of(), 2));

        assertThat(valuacion.llaveQueFalta())
                .isEqualTo(ValorizacionDelPredio.VALOR_UNITARIO_OBRA_COMPLEMENTARIA + ":2026");
        assertThat(valuacion.motivo())
                .as("no hay ni cuadro ni declaracion de donde sacar la cifra")
                .contains("Anexo III")
                .contains("ninguna columna de importe");
    }

    // ------------------------------------------------------------------

    private static ValorizacionDelPredio.@Nullable FichaDeLaValuacion ficha(
            List<ValorizacionDelPredio.Edificacion> construcciones, int obras) {
        return new ValorizacionDelPredio.FichaDeLaValuacion(
                42L, AreaM2.de("100.00"), "CASA_HABITACION", construcciones, obras);
    }

    private static ValuacionDelPredio valorizar(
            ValorizacionDelPredio.@Nullable FichaDeLaValuacion ficha) {
        return valorizar(ficha, CUADRO, true, ARANCEL, SIN_ACTUALIZACION);
    }

    private static ValuacionDelPredio valorizar(
            ValorizacionDelPredio.@Nullable FichaDeLaValuacion ficha,
            ValorizacionDelPredio.CuadroDeValoresUnitarios cuadro,
            boolean depreciacion,
            @Nullable ValorNormativo arancel,
            @Nullable ValorNormativo actualizacion) {
        return ValorizacionDelPredio.valorizar(
                new ValorizacionDelPredio.Insumos(
                        1L,
                        EJERCICIO,
                        CORTE,
                        ficha,
                        CONJUNTO,
                        ValorizacionDelPredio.VERSION,
                        cuadro,
                        depreciacion,
                        arancel,
                        actualizacion,
                        List.of()));
    }
}
