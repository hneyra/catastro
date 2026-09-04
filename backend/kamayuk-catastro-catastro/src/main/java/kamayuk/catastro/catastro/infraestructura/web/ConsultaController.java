package kamayuk.catastro.catastro.infraestructura.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.autorizacion.RequiereAcceso;
import kamayuk.catastro.catastro.AcotacionPorPredio;
import kamayuk.catastro.catastro.aplicacion.ConsultaDeFichas;
import kamayuk.catastro.catastro.dominio.FiltroDeFichas;
import kamayuk.catastro.catastro.dominio.TipoFicha;
import kamayuk.catastro.web.Api;
import kamayuk.catastro.web.CodigoDeError;
import kamayuk.catastro.web.ParametrosDePaginacion;
import kamayuk.catastro.web.ProblemaDeNegocio;
import kamayuk.catastro.web.RespuestaPaginada;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Consulta transversal de fichas: {@code GET /api/v1/catastro/fichas} (RF-006).
 *
 * <p>Los filtros son los que declara el contrato, que salio de la pantalla del prototipo. Uno de
 * ellos, {@code conciliadaConRentas}, <b>lo sirve otra ruta</b>: el estado de conciliacion se
 * deriva de {@code declaracion_jurada}, que es de {@code rentas}, y este contexto no puede mirarla
 * —dependeria de rentas y {@code verificarArquitectura} rechaza el ciclo (ADR-0015 §2)—.
 *
 * <p>Hasta #344 eso se contestaba con un <b>422 deliberado</b>, porque la lectura compuesta no
 * existia. Ahora existe, en {@code kamayuk-catastro-rentas}, y este endpoint <b>redirige</b> a ella
 * con 307 conservando la peticion entera. No se ignora el filtro y no se responde sin el: devolver
 * el listado completo daria un resultado plausible y equivocado —quien lo mira creeria estar viendo
 * solo las conciliadas— y esa es la razon por la que el 422 se puso; el redirigir la conserva y
 * ademas contesta.
 *
 * <h2>La acotacion por predio, que la sirve esta misma ruta (#631, C-1 desajustes 4 y 5)</h2>
 *
 * <p>{@code ?soloPredio=} y {@code ?exceptoPredio=} acotan la grilla a un conjunto de lotes, o a su
 * complemento. No los teclea nadie: los pone el contexto que compone la consulta —hoy {@code
 * rentas}, para servir la conciliacion paginando y contando <b>lo filtrado</b>—.
 *
 * <p><b>Se leen aqui y no se descartan, y ese es todo el motivo por el que existen.</b> Hasta C-1
 * viajaban en la URL y este endpoint no los miraba: la conciliacion volvia a componerse en memoria
 * —catastro devolvia la pagina del padron y rentas descartaba las filas que no cumplian—, de modo
 * que {@code totalElementos} seguia siendo el del padron entero. Medido sobre Catacaos en #631:
 * «722 paginas, 14 422 elementos» y <b>cero filas en todas</b>.
 *
 * <p>Los dos a la vez se rechazan con 422: {@link AcotacionPorPredio} no puede expresar «solo estos
 * y ademas todos menos estos», y quedarse con uno de los dos en silencio es exactamente lo que este
 * endpoint acaba de dejar de hacer.
 *
 * <p><b>Lo que no se puede distinguir, dicho</b>: «solo estos, y ninguno» —que no devuelve ni una
 * fila— llega por la URL igual que «no acotes», porque un parametro repetido cero veces es un
 * parametro ausente. Por eso el corto-circuito vive en el cliente ({@code FichasDelPadronHttp}) y
 * no aqui: quitarlo de alli mandaria una peticion para no traer nada, y este endpoint la
 * contestaria con el padron entero.
 *
 * <p>La ruta de destino es un subcamino de este mismo recurso, {@code
 * /catastro/fichas/conciliacion}, asi que aqui no aparece ninguna ruta de otro modulo: quien la
 * sirve —rentas— es un detalle de donde vive el codigo, no del contrato (mismo criterio que {@code
 * ConsultaPrediosController}, que sirve {@code consulta_predios} desde rentas).
 */
@RestController
@RequestMapping(Api.RAIZ + "/catastro/fichas")
@RequiereAcceso(acceso = "consulta_fichas", privilegio = Privilegio.LECTURA)
public class ConsultaController {

    /** Por codigo de referencia catastral, que es como se recorre un sector. */
    private static final String ORDEN_POR_OMISION = "codRefCatastral";

    /**
     * La misma grilla, con la columna «Conciliada» (ADR-0015 §2, #344). La sirve {@code rentas}.
     */
    static final String RUTA_DE_LA_CONCILIACION = Api.RAIZ + "/catastro/fichas/conciliacion";

    private final ConsultaDeFichas consulta;
    private final Clock reloj;

    public ConsultaController(ConsultaDeFichas consulta, Clock reloj) {
        this.consulta = consulta;
        this.reloj = reloj;
    }

    @GetMapping
    public ResponseEntity<RespuestaPaginada<FichaEncontradaResource>> consultar(
            @RequestParam(required = false) @Nullable String codRefCatastral,
            @RequestParam(required = false) @Nullable String contribuyente,
            @RequestParam(required = false) @Nullable String manzana,
            @RequestParam(required = false) @Nullable String lote,
            @RequestParam(required = false) @Nullable String tipo,
            @RequestParam(required = false) @Nullable String conciliadaConRentas,
            @RequestParam(required = false) @Nullable String fecha,
            @RequestParam(required = false) @Nullable List<Long> soloPredio,
            @RequestParam(required = false) @Nullable List<Long> exceptoPredio,
            ParametrosDePaginacion paginacion,
            HttpServletRequest peticion) {

        if (conciliadaConRentas != null && !conciliadaConRentas.isBlank()) {
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                    .location(destinoDeLaConciliacion(peticion.getParameterMap()))
                    .build();
        }

        FiltroDeFichas filtro =
                new FiltroDeFichas(codRefCatastral, contribuyente, manzana, lote, tipoDe(tipo))
                        .acotadoA(acotacionDe(soloPredio, exceptoPredio));
        LocalDate cuando = fecha == null || fecha.isBlank() ? LocalDate.now(reloj) : parsear(fecha);

        return ResponseEntity.ok(
                RespuestaPaginada.de(
                        consulta.buscar(filtro, cuando, paginacion.aPaginacion(ORDEN_POR_OMISION)),
                        FichaEncontradaResource::de));
    }

    /**
     * El destino del 307, con la peticion entera.
     *
     * <p>Se reenvian <b>todos</b> los parametros recibidos y no solo los que este metodo declara:
     * cualquier filtro que el contrato anada despues a la otra ruta se perderia por el camino sin
     * que nada avise, que es la clase de defecto que este endpoint existe para no cometer.
     *
     * <p>Se reconstruyen en vez de reenviar la cadena de consulta tal cual, y por dos motivos:
     * {@code UriComponentsBuilder} vuelve a codificar cada valor —asi nada que venga del cliente
     * entra crudo en una cabecera {@code Location}— y el resultado es el mismo con un contenedor de
     * verdad y con el de las pruebas, que no rellena la cadena original.
     */
    private static URI destinoDeLaConciliacion(Map<String, String[]> parametros) {
        UriComponentsBuilder destino = UriComponentsBuilder.fromPath(RUTA_DE_LA_CONCILIACION);
        for (Map.Entry<String, String[]> parametro : parametros.entrySet()) {
            destino.queryParam(parametro.getKey(), (Object[]) parametro.getValue());
        }
        return URI.create(destino.build().encode().toUriString());
    }

    /**
     * La acotacion por predio que la peticion trae, o ninguna.
     *
     * <p>Los dos parametros juntos son 422 y no una eleccion silenciosa: el rechazo dice cual es el
     * problema, y quedarse con uno devolveria una grilla plausible que acota por otra cosa.
     */
    private static AcotacionPorPredio acotacionDe(
            @Nullable List<Long> soloPredio, @Nullable List<Long> exceptoPredio) {

        List<Long> incluidos = soloPredio == null ? List.of() : soloPredio;
        List<Long> excluidos = exceptoPredio == null ? List.of() : exceptoPredio;
        if (!incluidos.isEmpty() && !excluidos.isEmpty()) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "«soloPredio» y «exceptoPredio» no se pueden pedir a la vez: uno acota a un"
                            + " conjunto de lotes y el otro a su complemento, y no hay ninguna"
                            + " grilla que sea las dos cosas");
        }
        if (!incluidos.isEmpty()) {
            return AcotacionPorPredio.soloEstos(incluidos);
        }
        return AcotacionPorPredio.todosMenosEstos(excluidos);
    }

    private static @Nullable TipoFicha tipoDe(@Nullable String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return null;
        }
        try {
            return TipoFicha.valueOf(tipo.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException noExiste) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "El tipo de ficha va entre UNICA, ECONOMICA, BIENES_COMUNES y RURAL: '"
                            + tipo
                            + "'");
        }
    }

    private static LocalDate parsear(String fecha) {
        try {
            return LocalDate.parse(fecha);
        } catch (java.time.format.DateTimeParseException malFormada) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION, "La fecha va en formato AAAA-MM-DD: '" + fecha + "'");
        }
    }
}
