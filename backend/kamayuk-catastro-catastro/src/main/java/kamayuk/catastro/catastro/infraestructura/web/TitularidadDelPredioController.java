package kamayuk.catastro.catastro.infraestructura.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.autorizacion.RequiereAcceso;
import kamayuk.catastro.catastro.GestorDeTitularidad;
import kamayuk.catastro.catastro.PrediosDelContribuyente;
import kamayuk.catastro.catastro.TitularDelPredio;
import kamayuk.catastro.catastro.TitularesDelPredio;
import kamayuk.catastro.web.Api;
import kamayuk.catastro.web.CodigoDeError;
import kamayuk.catastro.web.ProblemaDeNegocio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * De quien es un predio, y que predios son de alguien (C-5).
 *
 * <h2>Tres rutas, y una por pregunta</h2>
 *
 * <ul>
 *   <li>{@code GET /catastro/titularidad?predio=&#8230;&fecha=} — {@code TitularesDelPredio.de} y
 *       {@code .deVarios}. El parametro se repite: una pagina de veinte omisos cuesta <b>una</b>
 *       peticion, que es la forma que P5C conservo a proposito.
 *   <li>{@code GET /catastro/titularidad/cuota?predio=&contribuyente=&fecha=} — {@code
 *       GestorDeTitularidad.vigenteDe}. Es la unica que publica el {@code titularidadId}, que es el
 *       identificador con el que se <b>transfiere</b>: por eso va en su propia ruta, de un titular
 *       y un predio cada vez, y no dentro del listado.
 *   <li>{@code GET /catastro/titularidad/predios?contribuyente=&fecha=} — {@code
 *       PrediosDelContribuyente.de}. Es la lectura de la que sale la base del impuesto predial.
 * </ul>
 *
 * <h2>La fecha es obligatoria en las tres</h2>
 *
 * <p>De quien es un predio se pregunta a una fecha (regla 9). Un valor por omision del reloj
 * contestaria con el comprador de julio a quien pregunta por marzo, que es #24 y #366, y C-1 lo
 * encontro ya servido por HTTP. Sin valor por omision, olvidarla es un 422 y no una respuesta
 * equivocada.
 *
 * <h2>Ninguna ausencia es un 404</h2>
 *
 * <p>«Este predio no tiene titulares vigentes» y «esta persona no tiene cuota aqui» son datos, y
 * viajan como lista vacia o como {@code tieneCuota = false}. El {@code 404} de esta frontera queda
 * reservado para «esa ruta no existe», que es lo unico que un cliente no puede distinguir de otra
 * manera.
 *
 * <h2>El acceso, y lo que cuesta</h2>
 *
 * <p>{@code actualizacion_catastro} con {@code LECTURA}: la misma opcion que su hermana de
 * escritura, {@code POST /catastro/predios/&#123;predioId&#125;/titulares}. Y no {@code
 * consulta_fichas}, que es el acceso de la grilla: lo que estas rutas publican es el {@code
 * contribuyenteId}, o sea la <b>correlacion predio→persona</b>, y ADR-0015 §2.4 decidio
 * expresamente no ponerla al alcance de todo el que pueda listar fichas. La grilla sigue publicando
 * el nombre y no el identificador.
 *
 * <p>Lo que esto cuesta esta declarado en el entregable de C-5 y no resuelto aqui: el token llega
 * reenviado, no delegado (ADR-0028 §1), asi que quien determina en {@code rentas} tiene que tener
 * ademas esta opcion en {@code catastro}. Un token con audiencia propia (ADR-0028 §2) es lo que
 * permite contestar «quien puede pedir esto» sin colgarlo de una opcion de pantalla.
 */
@RestController
@RequestMapping(Api.RAIZ + "/catastro/titularidad")
@RequiereAcceso(acceso = "actualizacion_catastro", privilegio = Privilegio.LECTURA)
public class TitularidadDelPredioController {

    private final TitularesDelPredio titulares;
    private final GestorDeTitularidad titularidad;
    private final PrediosDelContribuyente predios;

    public TitularidadDelPredioController(
            TitularesDelPredio titulares,
            GestorDeTitularidad titularidad,
            PrediosDelContribuyente predios) {
        this.titulares = titulares;
        this.titularidad = titularidad;
        this.predios = predios;
    }

    /**
     * Las cuotas vigentes de uno o varios predios.
     *
     * <p><b>Sin tope de predios y a proposito</b>: el puerto no lo tenia, y esta frontera esta para
     * trasladar comportamiento y no para cambiarlo. Un tope inventado dejaria a una pagina
     * legitimamente mas larga contestando 422 por un numero que nadie decidio.
     *
     * <p>El orden de la respuesta es el de la peticion, sin repetidos. No sale del mapa que
     * devuelve el puerto: el orden de iteracion de un {@code Map.copyOf} no esta especificado, y
     * una respuesta que cambia de orden entre dos corridas no se puede afirmar en ninguna prueba
     * (#400).
     */
    @GetMapping
    public TitularesDeLosPrediosResource deLosPredios(
            @RequestParam List<Long> predio, @RequestParam String fecha) {

        LinkedHashSet<Long> pedidos = new LinkedHashSet<>(predio);
        if (pedidos.isEmpty()) {
            // Un parametro repetido cero veces llega igual que uno ausente, asi que «estos, y
            // ninguno» y «todos» serian la misma URL. Se rechaza en vez de contestar algo.
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "Hay que decir de que predios: «predio» se repite una vez por cada uno, y sin"
                            + " ninguno esta pregunta no tiene sujeto");
        }
        LocalDate cuando = DeclaracionDeFicha.fechaDe(fecha, "fecha");
        Map<Long, List<TitularDelPredio>> cuotas = titulares.deVarios(pedidos, cuando);

        List<TitularesDeUnPredioResource> filas = new ArrayList<>();
        for (Long predioId : pedidos) {
            List<TitularDelPredio> suyas = cuotas.get(predioId);
            if (suyas != null && !suyas.isEmpty()) {
                filas.add(TitularesDeUnPredioResource.de(predioId, suyas));
            }
        }
        return new TitularesDeLosPrediosResource(cuando, List.copyOf(filas));
    }

    /** La cuota vigente de un titular sobre un predio, con el identificador de su fila. */
    @GetMapping("/cuota")
    public CuotaDelTitularResource cuota(
            @RequestParam long predio,
            @RequestParam long contribuyente,
            @RequestParam String fecha) {

        LocalDate cuando = DeclaracionDeFicha.fechaDe(fecha, "fecha");
        return titularidad
                .vigenteDe(predio, contribuyente, cuando)
                .map(suya -> CuotaDelTitularResource.de(suya, cuando))
                .orElseGet(() -> CuotaDelTitularResource.sinCuota(predio, contribuyente, cuando));
    }

    /** Los predios de un contribuyente a una fecha, con su cuota y con lo que suma el predio. */
    @GetMapping("/predios")
    public PrediosDelTitularResource delContribuyente(
            @RequestParam long contribuyente, @RequestParam String fecha) {

        LocalDate cuando = DeclaracionDeFicha.fechaDe(fecha, "fecha");
        return new PrediosDelTitularResource(
                contribuyente,
                cuando,
                predios.de(contribuyente, cuando).stream()
                        .map(PredioDelTitularResource::de)
                        .toList());
    }
}
