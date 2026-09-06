package kamayuk.catastro.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;

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
 * <h2>Las dos premisas, dichas porque se pueden desmentir</h2>
 *
 * <ol>
 *   <li><b>El arancel de cada via se supone publicado</b>, con un valor cualquiera. El padron de
 *       demostracion NO trae ninguno —D-02b sigue abierta y el arancel es de ordenanza local—, y
 *       este censo no mide importes: mide en que RAMA cae cada predio, y esa rama no depende del
 *       valor del arancel sino de que exista. Sin la premisa, los 23 predios saldrian por {@code
 *       ARANCEL:2026} y el censo no diria nada de lo demas.
 *   <li><b>El {@code % actualizacion} se supone sellado</b>, que es lo que {@code normativa} hace
 *       para 2026 desde este mismo issue.
 * </ol>
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

    private static final ValorNormativo SIN_ACTUALIZACION = ValorNormativo.de("0");

    @Test
    @DisplayName("23 predios: el reparto por llave, contado y no supuesto")
    void elCensoDelPadronDeDemostracion() throws IOException {
        List<Predio> padron = leerElPadron();
        ValorizacionDelPredio.CuadroDeValoresUnitarios cuadro = leerElCuadroDeNormativa();

        int valorizados = 0;
        Map<String, Integer> porLlave = new TreeMap<>();
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
                                    SIN_ACTUALIZACION,
                                    List.of()));
            if (valuacion.seValorizo()) {
                valorizados++;
            } else {
                porLlave.merge(
                        valuacion.llaveQueFalta() == null ? "SIN_LLAVE" : valuacion.llaveQueFalta(),
                        1,
                        Integer::sum);
            }
        }

        // La cifra del entregable, escrita para que se lea de un vistazo en el registro.
        System.out.println(
                "CENSO DEL PADRON DE DEMOSTRACION ("
                        + padron.size()
                        + " predios): "
                        + valorizados
                        + " valorizado(s), "
                        + (padron.size() - valorizados)
                        + " con motivo "
                        + porLlave);

        assertThat(padron)
                .as("el padron de demostracion que este repositorio versiona")
                .hasSize(23);
        assertThat(cuadro.celdas())
                .as("las 24 casillas con cifra del Anexo I.2, tal como `normativa` las firma")
                .hasSize(24);

        // 4 predios sin ninguna construccion declarada: SI se valorizan, con su terreno y con
        // construccion y obras en cero. Antes de #8 eran CERO, y por una llave que no era ninguna
        // de estas: el `% actualizacion` paraba a los 23.
        assertThat(valorizados).isEqualTo(4);
        // Y los 19 restantes salen TODOS por la misma llave, que es la cifra que este censo existe
        // para poner delante: **RT-004**, que tabla del Anexo I del Reglamento Nacional de
        // Tasaciones le toca a cada uso de ficha. `normativa` sella las cuatro tablas; lo que
        // falta es la traduccion, y `depreciacion.md` §3 dice que es criterio y no transcripcion.
        // Una sola decision desbloquea 19 de 23 predios.
        //
        // Se cuenta POR USO y no en bloque porque el uso es lo que esa decision tiene que
        // traducir: la lista de abajo es, literalmente, el trabajo que RT-004 tiene por delante.
        assertThat(porLlave)
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
        assertThat(porLlave)
                .doesNotContainKey(
                        ValorizacionDelPredio.VALOR_UNITARIO_OBRA_COMPLEMENTARIA + ":2026");
        assertThat(porLlave.values().stream().mapToInt(Integer::intValue).sum())
                .as("y suma exactamente lo que no se valorizo: ningun predio se queda sin contar")
                .isEqualTo(padron.size() - valorizados);
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
