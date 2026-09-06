package kamayuk.catastro.nucleo.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Dinero;
import kamayuk.catastro.dominio.ValorNormativo;
import org.jspecify.annotations.Nullable;

/**
 * Decide la valuacion de un predio. <b>Funcion pura</b> (regla 6): sin base, sin reloj, sin
 * configuracion, y la fecha entra como argumento.
 *
 * <h2>Desde #8 SABE valorizar, y hoy no valoriza a nadie — las dos cosas a la vez</h2>
 *
 * <p>Hasta #8 esta clase tenia cinco ramas y <b>las cinco devolvian motivo</b> sin haber hecho
 * ninguna cuenta. Ahora la cuenta del terreno esta escrita y medida —area por arancel del metro
 * cuadrado de su via—, {@code normativa} publica los dos cuadros nacionales (H-14, H-15) y las
 * siete ramas dicen <b>de que depende cada predio</b> en vez de decir lo mismo de todos.
 *
 * <p><b>Y aun asi hoy no se valoriza ni un predio</b>, por una sola llave: el {@code %
 * actualizacion} (D-11). Su fundamento esta escrito en el corpus de {@code normativa} y <b>le falta
 * la segunda firma de ADR-0007</b>, que es un acto de una persona; hasta que llegue, ningun
 * conjunto sellado puede traerla y esta clase se para en la cuarta rama para todo el padron. Medido
 * sobre el padron de demostracion: <b>0 de 23</b> valorizados, los 23 por esa llave; con la firma
 * serian <b>4 de 23</b> — lo cuenta {@code ElPadronDeDemostracionSeValorizaTest}, que corre las dos
 * cuentas y las imprime.
 *
 * <p><b>Y hay una observacion de frontera medida, que este repositorio declara y no arregla</b> (es
 * D-21): esa llave se pide como <b>precondicion</b> y se usa <b>en un solo sitio</b>, el incremento
 * del autovaluo, que solo se aplica si el porcentaje no es cero. Con el valor que 2026 sellaria
 * —cero— <b>ninguna de las cuatro cifras del hecho sellado depende de ella</b>, y con un porcentaje
 * distinto de cero cambia <b>una sola</b>. O sea que lo que hoy para al padron entero no aportaria
 * un centimo a ninguna de las tres cifras que esta clase calcula, que es el argumento de ADR-0024
 * para situarlo del lado de {@code rentas}.
 *
 * <p>Lo que sale de aqui es el <b>valor del predio</b>, no la obligacion: ni tramos, ni alicuotas,
 * ni deducciones, ni minimo imponible. Eso es {@code rentas} (ADR-0024).
 *
 * <h2>El orden de las ramas, y por que ese</h2>
 *
 * <p>Va de lo que le falta al SISTEMA a lo que le falta al PREDIO, porque quien opera arregla cada
 * cosa en un sitio distinto y decirle la equivocada es peor que no decirle nada:
 *
 * <ol>
 *   <li><b>El predio no tiene ficha vigente.</b> Se arregla fichando el predio, no publicando una
 *       cifra;
 *   <li><b>el conjunto sellado no trae uno de los dos cuadros nacionales</b> (H-14, H-15). Se
 *       arregla publicandolo en {@code normativa} y volviendo a sellar;
 *   <li><b>la via del predio no tiene arancel</b> (D-02b). Es de ordenanza local;
 *   <li><b>el conjunto no trae el {@code % actualizacion} del ejercicio</b> (D-11). <b>Es la rama
 *       que HOY se alcanza siempre</b>: para 2026 el fundamento esta escrito y le falta la segunda
 *       firma de ADR-0007, y ningun otro ejercicio tiene ni siquiera eso;
 *   <li><b>al cuadro le falta la celda que esta construccion necesita</b>. Es distinto de que falte
 *       el cuadro: las tres celdas de puntos suspensivos del Anexo I.2 <b>no valen cero</b> (#48),
 *       y una construccion de categoria {@code H} en muros no se puede valorizar aunque el cuadro
 *       este entero;
 *   <li><b>no se sabe que tabla de depreciacion le toca a este uso</b> (RT-004). Es la decision que
 *       hoy deja sin valorizar a todo predio con construcciones, y se dice con su llave para que la
 *       direccion pueda contarlos;
 *   <li><b>el predio declara obras complementarias y no hay con que valorizarlas</b>. El Anexo III
 *       de la R.M. 277-2025-VIVIENDA no esta transcrito, y {@code otra_instalacion} no tiene
 *       columna de valor declarado: no hay ni cuadro ni declaracion de donde sacar la cifra.
 * </ol>
 *
 * <h2>Un cero que si es un cero</h2>
 *
 * <p>Un predio sin construcciones tiene {@code valorConstruccion} <b>cero</b>, y un predio sin
 * obras complementarias tiene {@code valorObras} cero. Eso <b>no</b> es el defecto de #48: alli el
 * cero era una cifra que faltaba disfrazada de cifra; aqui es un hecho sobre el predio —no hay nada
 * construido— y lo contrario, dejarlo sin valorizar, seria negarse a valorizar un terreno porque no
 * tiene casa. La diferencia se ve en que las dos situaciones se distinguen: lo que falta sale por
 * una de las ramas de arriba, con su llave; lo que no existe sale como cero.
 */
public final class ValorizacionDelPredio {

    /**
     * La llave del factor que para a todos los predios, hoy incluido.
     *
     * <p>No es un valor tributario —es el <b>nombre</b> de uno— y por eso no lo caza el escaner de
     * la regla 5, que vigila literales numericos. Nombrarlo es lo contrario de inventarlo.
     *
     * <p><b>Ningun ejercicio tiene su fila sellada</b>: la de 2026 tiene su fundamento escrito en
     * {@code normativa} y espera la segunda firma de ADR-0007; los demas no tienen ni eso.
     */
    public static final String PORCENTAJE_DE_ACTUALIZACION = "PORCENTAJE_DE_ACTUALIZACION";

    /**
     * La llave de la decision que hoy deja sin valorizar a todo predio con construcciones.
     *
     * <p>El Anexo I del Reglamento Nacional de Tasaciones publica <b>cuatro</b> tablas —01
     * vivienda, 02 tiendas y depositos, 03 edificios y oficinas, 04 salud, cines, industria y
     * educacion— y {@code normativa} las trae las cuatro. Lo que no hay es la traduccion del <b>uso
     * que declara la ficha catastral</b> al numero de tabla: {@code depreciacion.md} §3 lo dice con
     * todas las letras —«es criterio, no transcripcion, y no vive en este dato: RT-004 sigue sin
     * escribirse»—, asi que inventarla aqui seria escribir una regla sin fuente y depreciar una
     * oficina con el porcentaje de una vivienda.
     */
    public static final String TABLA_DE_DEPRECIACION = "TABLA_DE_DEPRECIACION";

    /**
     * La llave que falta para valorizar una obra complementaria o instalacion fija.
     *
     * <p>Los valores unitarios a costo directo son el <b>Anexo III</b> de la R.M.
     * 277-2025-VIVIENDA, que el corpus no transcribe —y antes de transcribirlo hay que decidir que
     * significan, porque la propia resolucion los da como «de uso opcional… como una guia» mientras
     * su Anexo II manda el camino del analisis de costos con el factor de oficializacion—. Y
     * tampoco hay declaracion: {@code otra_instalacion} guarda descripcion, unidad y cantidad, y
     * <b>ninguna columna de valor</b>.
     */
    public static final String VALOR_UNITARIO_OBRA_COMPLEMENTARIA =
            "VALOR_UNITARIO_OBRA_COMPLEMENTARIA";

    /**
     * La version del procedimiento de valuacion de este repositorio, tal como viaja en cada hecho
     * sellado y en el cierre de la corrida ({@code reglas_version}, {@code varchar(40)}).
     *
     * <p>No es «la version del catalogo de reglas tributarias»: aqui no corre ninguna, y decir que
     * si seria mentir sobre lo que produjo la cifra. Es la version de <b>esto</b> —de la funcion
     * que decide—, y lo que compra es que {@code rentas} pueda distinguir dos corridas hechas con
     * procedimientos distintos sin tener que mirar sus cifras.
     *
     * <p><b>Cambia con #8</b>, porque el procedimiento cambio: donde antes no habia cuenta ahora la
     * hay. Las valuaciones ya publicadas <b>no se reescriben</b>: se publica otra corrida, y esta
     * cadena es lo unico que separa las dos (ADR-0027 §1).
     */
    public static final String VERSION = "valuacion-de-terreno-v1";

    /** Las reglas que corrieron, anotadas en el hecho. Vacio cuando no corrio ninguna. */
    private static final String REGLAS_DEL_TERRENO = "TERRENO";

    private ValorizacionDelPredio() {}

    /**
     * La ficha vigente del predio, con lo que hay que mirar para valorizarlo.
     *
     * @param uso el uso que declara la ficha. Es lo que RT-004 tendria que traducir a una de las
     *     cuatro tablas del Anexo I
     * @param obrasComplementarias cuantas instalaciones fijas y permanentes declara la ficha. Se
     *     cuenta y no se lista porque hoy ninguna se puede valorizar: lo que decide es si hay
     *     alguna
     */
    public record FichaDeLaValuacion(
            long fichaId,
            AreaM2 areaTerreno,
            String uso,
            List<Edificacion> construcciones,
            int obrasComplementarias) {

        public FichaDeLaValuacion {
            Objects.requireNonNull(areaTerreno, "Toda ficha lleva su area de terreno");
            Objects.requireNonNull(uso, "Toda ficha lleva su uso");
            construcciones =
                    List.copyOf(Objects.requireNonNull(construcciones, "La lista, o vacia"));
            if (obrasComplementarias < 0) {
                throw new IllegalArgumentException(
                        "Una ficha no puede declarar un numero negativo de obras complementarias");
            }
        }
    }

    /**
     * Una construccion de la ficha, con lo que el cuadro necesita para ponerle precio.
     *
     * <p>Las tres categorias son las de las <b>tres partidas de apreciacion exterior</b> del Cuadro
     * de Valores Unitarios (V59), y no las siete columnas {@code categoria_*} de {@code
     * construccion}: esas describen la edificacion y no le ponen precio. Nulo es «no declarada», y
     * <b>no</b> es la categoria de la casilla sin techo ni sin puertas —esas son la {@code H} y la
     * {@code I} del propio cuadro, con su cifra—.
     */
    public record Edificacion(
            String piso,
            AreaM2 areaConstruida,
            @Nullable Integer anioConstruccion,
            @Nullable Character categoriaMuros,
            @Nullable Character categoriaTechos,
            @Nullable Character categoriaPuertas) {

        public Edificacion {
            Objects.requireNonNull(piso, "Toda construccion dice en que piso esta");
            Objects.requireNonNull(areaConstruida, "Toda construccion lleva su area construida");
        }

        /** La categoria declarada para esa partida, si la hay. */
        @Nullable Character categoriaDe(Partida partida) {
            return switch (partida) {
                case MUROS -> categoriaMuros;
                case TECHOS -> categoriaTechos;
                case PUERTAS -> categoriaPuertas;
            };
        }
    }

    /**
     * El cuadro sellado, tal como se consulta.
     *
     * <p>Es el cuadro ENTERO del conjunto y no una celda: lo trae la corrida una vez, no una vez
     * por predio, que es la propiedad de ADR-0025 §1 aplicada al camino caliente.
     */
    public record CuadroDeValoresUnitarios(List<ValorUnitarioEdificacion> celdas) {

        public CuadroDeValoresUnitarios {
            celdas = List.copyOf(Objects.requireNonNull(celdas, "El cuadro, o vacio"));
        }

        public boolean estaVacio() {
            return celdas.isEmpty();
        }

        /**
         * El valor por metro cuadrado de una casilla, si el cuadro la publica.
         *
         * <p>Vacio significa <b>que la norma no publica esa casilla</b> —las tres de puntos
         * suspensivos del Anexo I.2— o que ninguna cubre ese ano de construccion. Las dos se tratan
         * igual y ninguna vale cero (#48).
         */
        Optional<ValorNormativo> valorDe(Partida partida, char categoria, int anioConstruccion) {
            return celdas.stream()
                    .filter(celda -> celda.partida() == partida)
                    .filter(celda -> celda.categoria() == categoria)
                    .filter(celda -> celda.anioConstruccionDesde() <= anioConstruccion)
                    .filter(
                            celda ->
                                    celda.anioConstruccionHasta() == null
                                            || celda.anioConstruccionHasta() >= anioConstruccion)
                    .map(ValorUnitarioEdificacion::valorM2)
                    .findFirst();
        }
    }

    /**
     * Lo que se sabe de un predio en el momento de valorizarlo.
     *
     * @param ficha la ficha VIGENTE A LA FECHA DE CORTE, o nulo si no tiene ninguna
     * @param arancelM2 el arancel de la via del predio en el conjunto sellado, o nulo si esa via no
     *     lo tiene publicado (D-02b). Es el VALOR y no un booleano: es lo que valoriza el terreno
     * @param porcentajeDeActualizacion el {@code % actualizacion} del ejercicio, tal como lo sello
     *     {@code normativa}, o <b>nulo</b> si el conjunto no trae esa llave. Nulo NO es cero: cero
     *     es una cifra sellada con su fundamento y nulo es que nadie la publico (D-11)
     * @param cuadroDeDepreciacion si el conjunto trae la tabla de depreciacion (H-15). Es un
     *     booleano y no el cuadro porque hoy no se llega a consultarlo: RT-004 para antes
     */
    public record Insumos(
            long predioId,
            int ejercicio,
            LocalDate fechaDeCorte,
            @Nullable FichaDeLaValuacion ficha,
            long conjuntoId,
            String reglasVersion,
            CuadroDeValoresUnitarios cuadroDeValoresUnitarios,
            boolean cuadroDeDepreciacion,
            @Nullable ValorNormativo arancelM2,
            @Nullable ValorNormativo porcentajeDeActualizacion,
            List<CuotaDeTitular> titulares) {

        public Insumos {
            Objects.requireNonNull(fechaDeCorte, "La fecha entra como argumento (regla 6)");
            Objects.requireNonNull(reglasVersion, "La corrida dice que catalogo de reglas usa");
            Objects.requireNonNull(cuadroDeValoresUnitarios, "El cuadro, aunque este vacio");
            titulares = List.copyOf(Objects.requireNonNull(titulares, "La lista, o vacia"));
        }
    }

    /** La valuacion que corresponde a esos insumos. */
    public static ValuacionDelPredio valorizar(Insumos insumos) {
        Objects.requireNonNull(insumos, "No se valoriza sin insumos");
        SinValorizar sinValorizar = queImpideValorizar(insumos);
        if (sinValorizar != null) {
            return conMotivo(insumos, sinValorizar);
        }

        FichaDeLaValuacion ficha = Objects.requireNonNull(insumos.ficha());
        ValorNormativo arancel = Objects.requireNonNull(insumos.arancelM2());
        ValorNormativo actualizacion = Objects.requireNonNull(insumos.porcentajeDeActualizacion());

        // El valor del terreno: area por arancel del metro cuadrado de su via. No se redondea aqui
        // —D-03a y D-03b siguen abiertas— y el producto viaja exacto: quien redondee lo hara con
        // una PoliticaDeRedondeo, que es donde esa decision se puede leer.
        Dinero terreno = new Dinero(ficha.areaTerreno().valor().multiply(arancel.valor()));
        // Cero porque no hay nada construido y nada declarado, no porque falte una cifra: lo que
        // falta sale por `queImpideValorizar` con su llave. Ver el javadoc de la clase.
        Dinero construccion = Dinero.CERO;
        Dinero obras = Dinero.CERO;

        // El «% actualizacion» INCREMENTA el autovaluo; no lo multiplica. Su valor neutro es CERO
        // y no uno, medido contra una determinacion real del SRTM (#437, y
        // `predial-porcentaje-de-actualizacion.md` §1.3). Para 2026 valdria cero con su fundamento
        // —el supuesto del art. 12 del TUO LTM no se cumple— asi que no moveria la cifra; el dia
        // que un ejercicio lo active, hay que decidir antes DONDE se aplica, porque la captura
        // del SRTM lo situa entre el autovaluo y la base imponible, o sea del lado de `rentas`
        // (ADR-0024, junto al `% propiedad` de D-21).
        //
        // Y ESTA LINEA ES LA UNICA QUE LO USA, que es la observacion de frontera de #8: las tres
        // cifras de arriba —terreno, construccion y obras— no lo tocan nunca, y `valorDelPredio`
        // solo cuando el porcentaje no es cero. Medido en `ElPadronDeDemostracionSeValorizaTest`
        // con tres corridas del padron de demostracion.
        //
        // Un incremento de CERO no se aplica, y no es un atajo: `x + x·0` y `x` son el mismo
        // importe, pero multiplicar por uno le anade a la escala tantos decimales como traiga el
        // porcentaje, y elegir la escala del resultado es exactamente la decision que D-03a deja
        // abierta. Aqui no se redondea nada —quien emita un documento lo hara con una
        // `PoliticaDeRedondeo`— asi que tampoco se ensucia.
        Dinero autovaluo = terreno.mas(construccion).mas(obras);
        if (actualizacion.valor().signum() != 0) {
            autovaluo = autovaluo.mas(autovaluo.por(porCiento(actualizacion)));
        }

        return new ValuacionDelPredio(
                insumos.predioId(),
                insumos.ejercicio(),
                insumos.fechaDeCorte(),
                terreno,
                construccion,
                obras,
                autovaluo,
                null,
                null,
                ficha.fichaId(),
                insumos.conjuntoId(),
                insumos.reglasVersion(),
                REGLAS_DEL_TERRENO,
                insumos.titulares());
    }

    /** El porcentaje como fraccion. {@code 0 %} da cero, que es el valor neutro del incremento. */
    private static BigDecimal porCiento(ValorNormativo porcentaje) {
        return porcentaje.valor().movePointLeft(2);
    }

    /**
     * El primer insumo que falta, en el orden declarado, o nulo si no falta ninguno.
     *
     * <p>El orden importa y esta razonado en el javadoc de la clase: quien opera arregla cada cosa
     * en un sitio distinto.
     */
    private static @Nullable SinValorizar queImpideValorizar(Insumos insumos) {
        FichaDeLaValuacion ficha = insumos.ficha();
        if (ficha == null) {
            // No es «falta publicar»: es que este predio no tiene con que valorizarse. Se
            // distingue de las demas a proposito, porque se arregla fichando el predio y no
            // publicando una cifra — y mandar a quien opera a buscar una ordenanza que no le
            // falta es peor que no decirle nada.
            return new SinValorizar(
                    "El predio no tiene ficha catastral vigente al "
                            + insumos.fechaDeCorte()
                            + ": no hay area, ni uso, ni construcciones con que valorizarlo",
                    null);
        }
        if (insumos.cuadroDeValoresUnitarios().estaVacio()) {
            return new SinValorizar(
                    "El conjunto sellado del ejercicio no trae el cuadro de valores unitarios de"
                            + " edificacion (GOB-03 H-14): sin el no hay valor de construccion",
                    "VALOR_UNITARIO:" + insumos.ejercicio());
        }
        if (!insumos.cuadroDeDepreciacion()) {
            return new SinValorizar(
                    "El conjunto sellado del ejercicio no trae el cuadro de depreciacion"
                            + " (GOB-03 H-15): sin el, la construccion se valorizaria sin depreciar",
                    "DEPRECIACION:" + insumos.ejercicio());
        }
        if (insumos.arancelM2() == null) {
            return new SinValorizar(
                    "La via del predio no tiene arancel publicado para el ejercicio (D-02b, de"
                            + " ordenanza local con su ratificacion provincial): sin el no hay"
                            + " valor de terreno",
                    "ARANCEL:" + insumos.ejercicio());
        }
        if (insumos.porcentajeDeActualizacion() == null) {
            return new SinValorizar(
                    "El conjunto sellado del ejercicio "
                            + insumos.ejercicio()
                            + " no trae el «% actualizacion» (D-11). Su fundamento —el supuesto"
                            + " del art. 12 del TUO LTM no se cumple— esta escrito y le falta la"
                            + " segunda firma de ADR-0007, que es un acto de una persona, y no"
                            + " hay valor por omision",
                    PORCENTAJE_DE_ACTUALIZACION);
        }
        return loQueImpideValorizarLoConstruido(insumos, ficha);
    }

    private static @Nullable SinValorizar loQueImpideValorizarLoConstruido(
            Insumos insumos, FichaDeLaValuacion ficha) {
        for (Edificacion construccion : ficha.construcciones()) {
            SinValorizar celda = celdaQueFalta(insumos, construccion);
            if (celda != null) {
                return celda;
            }
        }
        if (!ficha.construcciones().isEmpty()) {
            // RT-004: que tabla del Anexo I le toca a este uso. Es criterio y no transcripcion, y
            // `normativa` no lo puede publicar porque ninguna norma lo fija
            // (`depreciacion.md` §3). Se nombra con el USO para que la direccion pueda contar los
            // predios por uso y ver que decide cada linea.
            return new SinValorizar(
                    "No esta decidido que tabla de depreciacion del Anexo I del RNT le"
                            + " corresponde al uso «"
                            + ficha.uso()
                            + "» (RT-004). Las cuatro estan selladas; traducir el uso al numero de"
                            + " tabla es criterio y no transcripcion, y depreciar de mas no lo"
                            + " delata ninguna consulta",
                    TABLA_DE_DEPRECIACION + ":" + ficha.uso());
        }
        if (ficha.obrasComplementarias() > 0) {
            return new SinValorizar(
                    "El predio declara "
                            + ficha.obrasComplementarias()
                            + " obra(s) complementaria(s) y no hay con que valorizarlas: el"
                            + " Anexo III de la R.M. 277-2025-VIVIENDA no esta transcrito, y"
                            + " «otra_instalacion» no tiene columna de importe",
                    VALOR_UNITARIO_OBRA_COMPLEMENTARIA + ":" + insumos.ejercicio());
        }
        return null;
    }

    /**
     * La casilla del cuadro que esta construccion necesita y no esta.
     *
     * <p><b>Es distinto de que falte el cuadro</b>, y por eso es una rama propia: el Anexo I.2
     * publica 27 casillas y tres de ellas son puntos suspensivos —muros en {@code H} e {@code I},
     * techos en {@code I}—, que «no son un dato que falte en la transcripcion ni un cero». Una
     * construccion que caiga en una de esas no se puede valorizar aunque el cuadro este entero, y
     * decir «falta el cuadro» mandaria a publicar algo que ya esta publicado.
     */
    private static @Nullable SinValorizar celdaQueFalta(Insumos insumos, Edificacion construccion) {
        Integer anio = construccion.anioConstruccion();
        if (anio == null) {
            return new SinValorizar(
                    "La construccion del piso "
                            + construccion.piso()
                            + " no declara ano de construccion, y sin el no se puede elegir la"
                            + " casilla del cuadro ni la antiguedad con que se deprecia",
                    null);
        }
        for (Partida partida : Partida.values()) {
            Character categoria = construccion.categoriaDe(partida);
            if (categoria == null) {
                return new SinValorizar(
                        "La construccion del piso "
                                + construccion.piso()
                                + " no declara la categoria de «"
                                + partida
                                + "», que es una de las tres partidas que el cuadro suma. Una"
                                + " categoria sin declarar no es «sin techo» ni «sin puertas»:"
                                + " esas son casillas del cuadro, con su cifra",
                        null);
            }
            if (insumos.cuadroDeValoresUnitarios().valorDe(partida, categoria, anio).isEmpty()) {
                return new SinValorizar(
                        "El cuadro de valores unitarios no publica la casilla «"
                                + partida
                                + "» de la categoria "
                                + categoria
                                + " para una construccion de "
                                + anio
                                + ". En el Anexo I.2 esa casilla son puntos suspensivos, que no"
                                + " son un dato que falte en la transcripcion ni un cero (#48)",
                        "VALOR_UNITARIO:" + partida + ":" + categoria);
            }
        }
        return null;
    }

    private static ValuacionDelPredio conMotivo(Insumos insumos, SinValorizar sinValorizar) {
        FichaDeLaValuacion ficha = insumos.ficha();
        return new ValuacionDelPredio(
                insumos.predioId(),
                insumos.ejercicio(),
                insumos.fechaDeCorte(),
                null,
                null,
                null,
                null,
                sinValorizar.motivo(),
                sinValorizar.llave(),
                ficha == null ? null : ficha.fichaId(),
                insumos.conjuntoId(),
                insumos.reglasVersion(),
                // Ninguna. Y se dice vacio en vez de omitirlo: «no corrio ninguna regla» y «no se
                // anoto cual corrio» son cosas distintas, y la segunda no se puede auditar.
                "",
                insumos.titulares());
    }

    /** Por que no se pudo valorizar, y con que llave se agrupa. */
    private record SinValorizar(String motivo, @Nullable String llave) {}
}
