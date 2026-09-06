package kamayuk.catastro.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.ValorNormativo;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>El entregable de #8, AC-5: el censo del padron de demostracion por llave que falta.</b>
 *
 * <p>Corre la funcion pura de valuacion sobre <b>el padron de demostracion de verdad</b> —los 23
 * predios de {@code infra/carga-de-datos/ejemplos/fichas.csv} y las 51 lineas de detalle de {@code
 * detalle-de-fichas.csv}— con <b>el cuadro de valores unitarios de verdad</b>, el derivado del
 * Anexo I.2 que {@code normativa} publica y firma por su sha256. Lo que sale es una cifra que la
 * direccion puede mirar: cuantos predios se valorizan, cuantos no, y <b>por que llave</b>.
 *
 * <h2>Por que se puede medir sin base de datos, y por que eso lo hace mas util</h2>
 *
 * <p>Porque {@link ValorizacionDelPredio} es una funcion pura (regla 6): la rama que le toca a un
 * predio depende solo de sus insumos. Montar la siembra entera —{@code pasos.tsv}, {@code
 * cargar-predios.sh}, un conjunto sellado en {@code normativa} y un despliegue de los dos— daria
 * exactamente el mismo reparto y ademas dejaria el censo dependiendo de que ese montaje corra, que
 * es lo contrario de una guarda.
 *
 * <h2>Se cuenta TRES VECES, y la diferencia entre las tres es el entregable</h2>
 *
 * <ol>
 *   <li><b>Hoy</b>, con el {@code % actualizacion} <b>ausente</b>. Es el estado real del sistema:
 *       su archivo del corpus esta en {@code TRANSCRITO} —le falta la segunda firma de ADR-0007, y
 *       ninguna maquina puede ponerla— asi que ningun conjunto sellado puede traer esa llave.
 *   <li><b>El dia que una persona firme §1.6</b>, con el {@code % actualizacion} en el valor que
 *       ese fundamento sella: <b>cero</b>. Es un contrafactual y se dice que lo es.
 *   <li><b>El mismo contrafactual con un porcentaje distinto de cero</b>, que no es ningun
 *       ejercicio real: existe para medir <b>que cifra de las cuatro depende de la llave</b>.
 * </ol>
 *
 * <p>La diferencia entre (1) y (2) es <b>lo que cuesta esa firma</b>, y se lee sin interpretar
 * nada. La diferencia entre (2) y (3) es la <b>observacion de frontera</b> que este censo declara y
 * no arregla: ver abajo.
 *
 * <h2>La observacion de frontera, medida y NO arreglada (es D-21 y no es de este issue)</h2>
 *
 * <p>La llave {@code PORCENTAJE_DE_ACTUALIZACION} se pide en {@code queImpideValorizar} como
 * <b>precondicion</b> —la quinta rama, antes de tocar una sola cifra— y se usa <b>en un solo
 * sitio</b>: el incremento del autovaluo, que solo se aplica si el porcentaje no es cero. De modo
 * que con el valor que 2026 sella, <b>ninguna de las cuatro cifras del hecho sellado depende de
 * ella</b>: el terreno, la construccion y las obras no la tocan nunca, y el valor del predio sale
 * identico al del terreno. Esta prueba lo mide comparando (2) contra (3): con {@code p != 0} cambia
 * <b>una sola</b> de las cuatro.
 *
 * <p>Eso es exactamente lo que ADR-0024 pone del lado de {@code rentas}: un incremento sobre el
 * autovaluo que no cambia el valor del predio es de la base imponible, no de la valuacion, y vive
 * junto al {@code % propiedad} de D-21. <b>No se mueve aqui</b> —decidirlo no es de este issue—,
 * pero queda medido, porque significa que la firma de §1.6 desbloquea el padron sin aportar una
 * cifra a ninguna de las cuatro, y eso cambia donde hay que mirar cuando se decida D-21.
 *
 * <h2>La premisa que queda, dicha porque se puede desmentir</h2>
 *
 * <p><b>El arancel de cada via se supone publicado</b>, con un valor cualquiera. El padron de
 * demostracion NO trae ninguno —D-02b sigue abierta y el arancel es de ordenanza local—, y este
 * censo no mide importes: mide en que RAMA cae cada predio, y esa rama no depende del valor del
 * arancel sino de que exista. Sin la premisa, los 23 predios saldrian por {@code ARANCEL:2026} y el
 * censo no diria nada de lo demas.
 *
 * <p>Lo que el censo NO supone es nada del cuadro: las casillas son las que el corpus firma, y una
 * combinacion que el Anexo no publique sale por su llave.
 */
@DisplayName("#8 AC-5 — El padron de demostracion, valorizado y contado por llave")
class ElPadronDeDemostracionSeValorizaTest {

    private static final LocalDate CORTE = LocalDate.of(2026, 6, 30);
    private static final int EJERCICIO = 2026;

    /** Ver el javadoc: el valor no decide ninguna rama, solo que la via tenga arancel. */
    private static final ValorNormativo ARANCEL_SUPUESTO = ValorNormativo.de("1");

    /** El valor que §1.6 sella cuando alguien la firme. Contrafactual, y se dice que lo es. */
    private static final ValorNormativo SI_ALGUIEN_FIRMA = ValorNormativo.de("0");

    /**
     * Ningun ejercicio real. Existe para medir que cifra de las cuatro depende de la llave.
     *
     * <p>No es un literal tributario prohibido por la regla 5: no es la cifra de ninguna norma, y
     * la prueba no afirma nada sobre su valor — solo sobre <b>cual de las cuatro cifras se
     * mueve</b> cuando el porcentaje deja de ser cero.
     */
    private static final ValorNormativo UN_PORCENTAJE_CUALQUIERA = ValorNormativo.de("10");

    @Test
    @DisplayName("23 predios: el reparto por llave, contado y no supuesto")
    void elCensoDelPadronDeDemostracion() throws IOException {
        List<Predio> padron = leerElPadron();
        ValorizacionDelPredio.CuadroDeValoresUnitarios cuadro = leerElCuadroDeNormativa();

        assertThat(padron)
                .as("el padron de demostracion que este repositorio versiona")
                .hasSize(23);
        assertThat(cuadro.celdas())
                .as("las 24 casillas con cifra del Anexo I.2, tal como `normativa` las firma")
                .hasSize(24);

        Censo hoy = censar(padron, cuadro, null);
        Censo siAlguienFirma = censar(padron, cuadro, SI_ALGUIEN_FIRMA);
        Censo conUnPorcentaje = censar(padron, cuadro, UN_PORCENTAJE_CUALQUIERA);

        // Las cifras del entregable, escritas para que se lean de un vistazo en el registro.
        System.out.println(
                "CENSO DEL PADRON DE DEMOSTRACION ("
                        + padron.size()
                        + " predios)\n  HOY, con el «% actualizacion» AUSENTE:   "
                        + hoy
                        + "\n  SI ALGUIEN FIRMA §1.6 (contrafactual): "
                        + siAlguienFirma);

        // ------------------------------------------------------------------
        // (1) HOY: el estado real del sistema
        // ------------------------------------------------------------------
        // `predial-porcentaje-de-actualizacion.md` esta en TRANSCRITO: tiene su fundamento escrito
        // y le falta la segunda firma de ADR-0007, que es un acto de una PERSONA. Sin ella, ningun
        // conjunto sellado trae la llave, y la quinta rama para a los 23 predios antes de calcular
        // nada. Es la rotura R9 de este issue convertida en el estado permanente del sistema.
        assertThat(hoy.valorizados()).isZero();
        assertThat(hoy.porLlave())
                .as("los 23, por la misma llave, y no repartidos entre varias")
                .containsExactly(entry(ValorizacionDelPredio.PORCENTAJE_DE_ACTUALIZACION, 23));

        // ------------------------------------------------------------------
        // (2) EL DIA QUE ALGUIEN FIRME: lo que esa firma desbloquea
        // ------------------------------------------------------------------
        // 4 predios sin ninguna construccion declarada: se valorizan con su terreno, y con
        // construccion y obras en cero — cero porque no hay nada declarado, no porque falte una
        // cifra, que es la distincion que #48 existe para sostener.
        assertThat(siAlguienFirma.valorizados()).isEqualTo(4);
        // Y los 19 restantes salen TODOS por la misma llave, que es la cifra que este censo existe
        // para poner delante: **RT-004**, que tabla del Anexo I del Reglamento Nacional de
        // Tasaciones le toca a cada uso de ficha. `normativa` sella las cuatro tablas; lo que
        // falta es la traduccion, y `depreciacion.md` §3 dice que es criterio y no transcripcion.
        // Una sola decision desbloquea 19 de 23 predios — el dia que la primera este firmada.
        //
        // Se cuenta POR USO y no en bloque porque el uso es lo que esa decision tiene que
        // traducir: la lista de abajo es, literalmente, el trabajo que RT-004 tiene por delante.
        assertThat(siAlguienFirma.porLlave())
                .as("el reparto entero, sin agrupar nada bajo «otros»")
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                ValorizacionDelPredio.TABLA_DE_DEPRECIACION + ":Casa habitacion",
                                12,
                                ValorizacionDelPredio.TABLA_DE_DEPRECIACION + ":Departamento",
                                2,
                                ValorizacionDelPredio.TABLA_DE_DEPRECIACION + ":Almacen de insumos",
                                1,
                                ValorizacionDelPredio.TABLA_DE_DEPRECIACION
                                        + ":Deposito y patio de maniobras",
                                1,
                                ValorizacionDelPredio.TABLA_DE_DEPRECIACION
                                        + ":Panaderia y pasteleria",
                                1,
                                ValorizacionDelPredio.TABLA_DE_DEPRECIACION + ":Taller de ceramica",
                                1,
                                ValorizacionDelPredio.TABLA_DE_DEPRECIACION
                                        + ":Tienda de artesania",
                                1));
        // Y NINGUNO sale por las obras complementarias, aunque el padron declare instalaciones:
        // los predios que las tienen tienen tambien construcciones, y la rama de RT-004 va antes.
        // Se dice porque la ausencia de esa llave no significa que el Anexo III sobre — significa
        // que hoy no llega a estorbar.
        assertThat(siAlguienFirma.porLlave())
                .doesNotContainKey(
                        ValorizacionDelPredio.VALOR_UNITARIO_OBRA_COMPLEMENTARIA + ":2026");
        assertThat(siAlguienFirma.porLlave().values().stream().mapToInt(Integer::intValue).sum())
                .as("y suma exactamente lo que no se valorizo: ningun predio se queda sin contar")
                .isEqualTo(padron.size() - siAlguienFirma.valorizados());

        // ------------------------------------------------------------------
        // (3) LA OBSERVACION DE FRONTERA: que cifra de las cuatro depende de la llave
        // ------------------------------------------------------------------
        // Con el porcentaje en el valor que §1.6 sella —cero—, el autovaluo es IDENTICO al valor
        // del terreno: la llave que para a los 23 predios no aporta un centimo a ninguna de las
        // cuatro cifras. Es una precondicion, no un insumo del calculo.
        for (ValuacionDelPredio valuacion : siAlguienFirma.cifras().values()) {
            assertThat(valuacion.valorDelPredio())
                    .as("predio %d: con p = 0 el autovaluo es el terreno", valuacion.predioId())
                    .isEqualTo(valuacion.valorTerreno());
        }
        // Y con un porcentaje distinto de cero se mueve UNA SOLA de las cuatro. Se mide, en vez de
        // razonarlo, porque es lo que decide de que lado de ADR-0024 vive el «% actualizacion»: un
        // incremento que no cambia el valor del predio es de la BASE IMPONIBLE y no de la
        // valuacion, o sea de `rentas`, junto al «% propiedad» de D-21. Aqui NO se mueve.
        assertThat(conUnPorcentaje.cifras().keySet())
                .as("el porcentaje no cambia QUIEN se valoriza, solo cuanto")
                .isEqualTo(siAlguienFirma.cifras().keySet());
        for (Map.Entry<Long, ValuacionDelPredio> caso : siAlguienFirma.cifras().entrySet()) {
            ValuacionDelPredio conCero = caso.getValue();
            ValuacionDelPredio conPorcentaje = conUnPorcentaje.cifras().get(caso.getKey());
            assertThat(conPorcentaje.valorTerreno()).isEqualTo(conCero.valorTerreno());
            assertThat(conPorcentaje.valorConstruccion()).isEqualTo(conCero.valorConstruccion());
            assertThat(conPorcentaje.valorObras()).isEqualTo(conCero.valorObras());
            assertThat(conPorcentaje.valorDelPredio())
                    .as(
                            "predio %d: la UNICA de las cuatro que depende de la llave, y solo"
                                    + " cuando el porcentaje no es cero",
                            caso.getKey())
                    .isNotEqualTo(conCero.valorDelPredio());
        }
    }

    // ------------------------------------------------------------------
    // El censo, contado con un valor cualquiera del «% actualizacion»
    // ------------------------------------------------------------------

    /**
     * Lo que sale de una corrida: cuantos con cifras, cuantos por llave, y las cifras de los que se
     * valorizaron.
     */
    private record Censo(
            int valorizados, Map<String, Integer> porLlave, Map<Long, ValuacionDelPredio> cifras) {

        @Override
        public String toString() {
            return valorizados
                    + " valorizado(s), "
                    + porLlave.values().stream().mapToInt(Integer::intValue).sum()
                    + " con motivo "
                    + porLlave;
        }
    }

    private static Censo censar(
            List<Predio> padron,
            ValorizacionDelPredio.CuadroDeValoresUnitarios cuadro,
            @Nullable ValorNormativo porcentajeDeActualizacion) {
        Map<String, Integer> porLlave = new TreeMap<>();
        Map<Long, ValuacionDelPredio> cifras = new LinkedHashMap<>();
        for (Predio predio : padron) {
            ValuacionDelPredio valuacion =
                    ValorizacionDelPredio.valorizar(
                            new ValorizacionDelPredio.Insumos(
                                    predio.numero(),
                                    EJERCICIO,
                                    CORTE,
                                    predio.ficha(),
                                    1L,
                                    ValorizacionDelPredio.VERSION,
                                    cuadro,
                                    true,
                                    ARANCEL_SUPUESTO,
                                    porcentajeDeActualizacion,
                                    List.of()));
            if (valuacion.seValorizo()) {
                cifras.put(predio.numero(), valuacion);
            } else {
                porLlave.merge(
                        valuacion.llaveQueFalta() == null ? "SIN_LLAVE" : valuacion.llaveQueFalta(),
                        1,
                        Integer::sum);
            }
        }
        return new Censo(cifras.size(), porLlave, cifras);
    }

    // ------------------------------------------------------------------
    // La lectura de los archivos reales
    // ------------------------------------------------------------------

    private record Predio(long numero, ValorizacionDelPredio.FichaDeLaValuacion ficha) {}

    /** La raiz del clon, subiendo hasta encontrar {@code .git}. */
    private static Path raizDelRepositorio() {
        Path candidato = Path.of("").toAbsolutePath();
        // `Files.exists` y no `Files.isDirectory`: en un `git worktree` el `.git` de la raiz es un
        // ARCHIVO con una linea `gitdir:` dentro (la leccion de #4 y #5).
        while (candidato != null && !Files.exists(candidato.resolve(".git"))) {
            candidato = candidato.getParent();
        }
        if (candidato == null) {
            throw new IllegalStateException(
                    "No se encontro la raiz del repositorio subiendo desde "
                            + Path.of("").toAbsolutePath());
        }
        return candidato;
    }

    /**
     * El cuadro que {@code normativa} publica, leido de su clon hermano.
     *
     * <p>Se lee del clon y no se copia aqui, por lo mismo que {@code catastro} lee {@code
     * contribuyentes.csv} del clon de {@code rentas} (C-6): una copia es un segundo sitio donde una
     * cifra normativa puede estar mal, y quien la edite no tiene por que enterarse de la otra. Si
     * el clon no esta, esto <b>falla diciendo que `git clone` falta</b> en vez de saltarse.
     */
    private static ValorizacionDelPredio.CuadroDeValoresUnitarios leerElCuadroDeNormativa()
            throws IOException {
        Path derivado =
                raizDelRepositorio()
                        .resolveSibling("normativa")
                        .resolve(
                                "docs/10-negocio/valores-normativos/fuentes/valores-unitarios-2026/"
                                        + "valores-unitarios-costa-2026.csv");
        if (!Files.exists(derivado)) {
            throw new IllegalStateException(
                    "Falta el clon hermano de `normativa`, y sin el este censo no puede leer el"
                            + " cuadro de valores unitarios que ese repositorio firma. No se copia aqui"
                            + " a proposito: una copia de una cifra normativa es un segundo sitio donde"
                            + " puede estar mal. Remedio: git clone https://github.com/hneyra/normativa"
                            + " "
                            + derivado);
        }
        List<ValorUnitarioEdificacion> celdas = new ArrayList<>();
        for (String linea : datos(derivado)) {
            String[] campos = linea.split(",", -1);
            celdas.add(
                    new ValorUnitarioEdificacion(
                            null,
                            Partida.valueOf(campos[0]),
                            campos[1].charAt(0),
                            Integer.parseInt(campos[2]),
                            campos[3].isBlank() ? null : Integer.parseInt(campos[3]),
                            ValorNormativo.de(campos[4]),
                            "Anexo I.2 de la R.M. 277-2025-VIVIENDA"));
        }
        return new ValorizacionDelPredio.CuadroDeValoresUnitarios(celdas);
    }

    /** El padron de demostracion: sus fichas y el detalle de cada una. */
    private static List<Predio> leerElPadron() throws IOException {
        Path ejemplos = raizDelRepositorio().resolve("infra/carga-de-datos/ejemplos");
        Map<String, List<ValorizacionDelPredio.Edificacion>> construcciones = new LinkedHashMap<>();
        Map<String, Integer> instalaciones = new LinkedHashMap<>();
        for (String linea : datos(ejemplos.resolve("detalle-de-fichas.csv"))) {
            String[] campos = linea.split(",", -1);
            String codigoPredial = campos[0];
            String seccion = campos[5];
            if ("CONSTRUCCION".equals(seccion)) {
                // c1 piso, c2 area, c3 anio, c4 material, c5 estado, c6 las SIETE categorias del
                // formulario de la ficha, c7 porcentaje construido. De las siete, la valuacion usa
                // las TRES partidas de apreciacion exterior (V59): muros (1.a), techos (2.a) y
                // puertas y ventanas (4.a).
                String categorias = campos[11];
                construcciones
                        .computeIfAbsent(codigoPredial, predio -> new ArrayList<>())
                        .add(
                                new ValorizacionDelPredio.Edificacion(
                                        campos[6],
                                        AreaM2.de(campos[7]),
                                        Integer.valueOf(campos[8]),
                                        categorias.charAt(0),
                                        categorias.charAt(1),
                                        categorias.charAt(3)));
            } else if ("INSTALACION".equals(seccion)) {
                instalaciones.merge(codigoPredial, 1, Integer::sum);
            }
        }

        List<Predio> padron = new ArrayList<>();
        long numero = 0;
        for (String linea : datos(ejemplos.resolve("fichas.csv"))) {
            String[] campos = linea.split(",", -1);
            String codigoPredial = codigoPredialDe(campos);
            numero++;
            padron.add(
                    new Predio(
                            numero,
                            new ValorizacionDelPredio.FichaDeLaValuacion(
                                    numero,
                                    AreaM2.de(campos[15]),
                                    campos[16],
                                    construcciones.getOrDefault(codigoPredial, List.of()),
                                    instalaciones.getOrDefault(codigoPredial, 0))));
        }
        return List.copyOf(padron);
    }

    /**
     * El codigo predial que {@code CargarFichasDeDemostracion} compone con los diez tramos.
     *
     * <p>Se compone igual aqui porque es la llave con que {@code detalle-de-fichas.csv} apunta a su
     * ficha; no es un dato del archivo, es la concatenacion de sus diez primeras columnas.
     */
    private static String codigoPredialDe(String[] campos) {
        StringBuilder codigo = new StringBuilder();
        for (int tramo = 0; tramo < 10; tramo++) {
            codigo.append(campos[tramo]);
        }
        return codigo.toString();
    }

    /** Las lineas con datos: sin comentarios, sin la cabecera y sin las vacias. */
    private static List<String> datos(Path archivo) throws IOException {
        List<String> lineas = new ArrayList<>();
        boolean cabeceraVista = false;
        for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
            if (linea.isBlank() || linea.startsWith("#")) {
                continue;
            }
            if (!cabeceraVista) {
                cabeceraVista = true;
                continue;
            }
            lineas.add(linea);
        }
        return lineas;
    }
}
