package kamayuk.catastro.grd.infraestructura.web;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.autorizacion.RequiereAcceso;
import kamayuk.catastro.grd.aplicacion.ConsultaDeItse;
import kamayuk.catastro.grd.aplicacion.ConsultaDeRiesgo;
import kamayuk.catastro.grd.aplicacion.RegistrarCertificadoItse;
import kamayuk.catastro.grd.dominio.PredioDesconocido;
import kamayuk.catastro.grd.dominio.PredioSinGeometria;
import kamayuk.catastro.web.Api;
import kamayuk.catastro.web.CodigoDeError;
import kamayuk.catastro.web.ProblemaDeNegocio;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * El riesgo y el ITSE de un predio: {@code GET /catastro/api/v1/grd/...} (#5).
 *
 * <p><b>Publica hechos y ninguna consecuencia</b> (ADR-0024). Dice que zonas de riesgo cruzan el
 * lote, si alguna es no mitigable y si hay ITSE vigente a una fecha. <b>No</b> dice si se puede dar
 * una licencia de funcionamiento: eso depende ademas del giro y del riesgo que la actividad exige,
 * que viven en {@code rentas} ({@code ciiu.riesgo_itse}). El dia que este endpoint devolviera un
 * «procede: si/no», la frontera de ADR-0024 estaria movida y habria que revisarla, no ampliarlo.
 *
 * <h2>Dos endpoints, y solo uno se niega sin geometria</h2>
 *
 * <p>{@code /riesgo} contesta {@code 422} sobre un predio sin poligono, y {@code /itse} contesta
 * {@code 200} con lista vacia. No es una inconsistencia: son dos preguntas distintas. El riesgo se
 * responde <b>cruzando geometrias</b>, asi que sin poligono la respuesta correcta no existe —«cero
 * zonas» se leeria como «no cae en ninguna» y acabaria autorizando lo que no debe—. Un certificado
 * cuelga del predio y no de su plano, asi que «ninguno vigente» es verdad aunque el lote no este
 * levantado.
 *
 * <p><b>Hoy no hay ni un poligono cargado</b> en ninguna instalacion, asi que el {@code 422} es el
 * camino habitual y no el raro. Es exactamente lo que se busca: una respuesta que se puede obedecer
 * —cargar el plano— en vez de una cifra tranquilizadora.
 *
 * <h2>La fecha</h2>
 *
 * <p><b>Las dos la reciben en {@code ?aLaFecha=}</b>; sin ella, hoy segun el {@link Clock}
 * inyectado y nunca {@code LocalDate.now()}. Las dos la devuelven dentro, que es la regla 9: una
 * respuesta sin fecha es una que dentro de un mes es otra sin que nadie pueda decir cual se dio.
 *
 * <p><b>{@code /riesgo} no la admitia hasta #18</b>, y no era simetria lo que faltaba: quien evalua
 * hoy una licencia denegada en 2024 necesita saber que decia la carta de peligro <b>entonces</b>, y
 * con la ruta anterior esa pregunta no se podia hacer. Las zonas y las fajas ya tenian sus dos
 * fechas en {@code V8} y el repositorio ya filtraba por ellas; lo que faltaba era <b>dejar
 * pedirla</b>. El nombre es {@code aLaFecha} y no {@code fecha} porque es el que ya usan {@code
 * /itse} y {@code /urbano/zonificacion}, y el que el consumidor manda: dos nombres para el mismo
 * criterio es como uno de los dos acaba descartandose en silencio (C-1).
 */
@RestController
@RequestMapping(Api.RAIZ + "/grd")
@RequiereAcceso(acceso = "gestion_del_riesgo", privilegio = Privilegio.LECTURA)
public class GrdController {

    private final ConsultaDeRiesgo riesgo;
    private final ConsultaDeItse itse;
    private final RegistrarCertificadoItse registrar;
    private final Clock reloj;

    public GrdController(
            ConsultaDeRiesgo riesgo,
            ConsultaDeItse itse,
            RegistrarCertificadoItse registrar,
            Clock reloj) {
        this.riesgo = riesgo;
        this.itse = itse;
        this.registrar = registrar;
        this.reloj = reloj;
    }

    /**
     * Las zonas de riesgo y las fajas marginales que intersectaban el lote <b>a una fecha</b> (#5
     * AC-3, #18 AC-1).
     *
     * <p>Sin {@code aLaFecha}, hoy segun el reloj inyectado. Con ella, lo que estaba vigente ese
     * dia: una zona cerrada antes no sale y una que abre despues tampoco, en las dos tablas — y con
     * ellas cambia {@code hayRiesgoNoMitigable}, que es el dato del que cuelga la decision y que
     * por eso se deriva de <b>estas</b> zonas y no de las de hoy (#18, AC-4).
     */
    @GetMapping("/riesgo")
    public RiesgoDelPredioResource riesgo(
            @RequestParam long predioId,
            @RequestParam(required = false) @Nullable String aLaFecha) {
        try {
            return RiesgoDelPredioResource.de(riesgo.delPredio(predioId, fechaDe(aLaFecha)));
        } catch (PredioSinGeometria sinPlano) {
            // 422 y no 200 con lista vacia: ver el javadoc de la clase y el de PredioSinGeometria.
            throw new ProblemaDeNegocio(CodigoDeError.VALIDACION, sinPlano.mensaje());
        } catch (PredioDesconocido noEsDeAqui) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, noEsDeAqui.mensaje());
        }
    }

    /** El ITSE vigente a una fecha (AC-4). Un certificado vencido no sale. */
    @GetMapping("/itse")
    public ItseDelPredioResource itse(
            @RequestParam long predioId,
            @RequestParam(required = false) @Nullable String aLaFecha) {

        LocalDate cuando = fechaDe(aLaFecha);
        try {
            return ItseDelPredioResource.de(predioId, cuando, itse.vigenteA(predioId, cuando));
        } catch (PredioDesconocido noEsDeAqui) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, noEsDeAqui.mensaje());
        }
    }

    /**
     * El alta de un certificado.
     *
     * <p>Exige {@code REGISTRO} y no la {@code LECTURA} de la clase: escribe. Y entra por HTTP y no
     * por carga masiva —al reves que la carta de peligro— porque un ITSE lo emite la propia
     * municipalidad de uno en uno, no un organismo de fuera en un archivo de miles de poligonos.
     */
    @PostMapping("/itse")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereAcceso(acceso = "gestion_del_riesgo", privilegio = Privilegio.REGISTRO)
    public ItseDelPredioResource.CertificadoItseResource registrar(
            @RequestBody PeticionDeItse peticion) {
        try {
            return ItseDelPredioResource.CertificadoItseResource.de(
                    registrar.registrar(peticion.aCertificado(), peticion.observacionDeclarada()));
        } catch (PredioDesconocido noEsDeAqui) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, noEsDeAqui.mensaje());
        } catch (DuplicateKeyException repetido) {
            // `itse_numero_uq`. No se nombra la restriccion (ARQ-04 §5): se dice lo unico que
            // quien atiende puede corregir, que es el numero que tecleo.
            throw new ProblemaDeNegocio(
                    CodigoDeError.CONFLICTO,
                    "Ya hay un certificado ITSE con el numero '"
                            + peticion.numero()
                            + "' en esta municipalidad");
        }
    }

    /**
     * La fecha pedida, o hoy.
     *
     * <p>Una fecha ilegible es {@code 422} y no «hoy»: quien pidio el 30 de febrero recibiria la
     * respuesta de hoy creyendo que es la de febrero, y eso es peor que un error.
     */
    private LocalDate fechaDe(@Nullable String aLaFecha) {
        if (aLaFecha == null || aLaFecha.isBlank()) {
            return LocalDate.now(reloj);
        }
        try {
            return LocalDate.parse(aLaFecha.strip());
        } catch (java.time.format.DateTimeParseException malFormada) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "El parametro 'aLaFecha' va en formato AAAA-MM-DD: '" + aLaFecha + "'");
        }
    }
}
