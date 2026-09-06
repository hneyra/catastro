package kamayuk.catastro.nucleo.infraestructura.web;

import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.autorizacion.RequiereAcceso;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.nucleo.aplicacion.ConfirmarElFrente;
import kamayuk.catastro.nucleo.aplicacion.ConsultaDeFrentes;
import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import kamayuk.catastro.nucleo.dominio.FrentesDelPredio;
import kamayuk.catastro.web.Api;
import kamayuk.catastro.web.CodigoDeError;
import kamayuk.catastro.web.ProblemaDeNegocio;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los frentes de un predio: {@code GET /catastro/api/v1/catastro/predios/{predioId}/frentes} (#7).
 *
 * <h2>Que publica, y que NO</h2>
 *
 * <p>Publica el insumo de los arbitrios —a que vias da el predio, cuantos metros lineales, con que
 * numeracion y que retiro, y cual es el principal—. <b>No publica ni un importe</b>: el arbitrio lo
 * determina {@code rentas} (ADR-0024) y este sistema no sabe lo que es un servicio de limpieza. No
 * hay en este archivo, ni en los que devuelve, el nombre de ninguno.
 *
 * <h2>Un predio sin frentes contesta 200 con lista vacia, y dice desde cuando</h2>
 *
 * <p>Y no 404, porque el predio SI esta: lo que falta son sus frentes. La lista vacia viaja
 * acompanada de {@code derivadoEn} y {@code motivoDeLaDerivacion} —ver {@link
 * FrentesDelPredioResource}— porque «no da a ninguna calle» y «nadie lo ha calculado» son dos
 * problemas que se arreglan de maneras distintas, y hoy la respuesta va a ser siempre la segunda:
 * no hay ni un poligono cargado en ninguna instalacion.
 *
 * <p>El 404 se reserva para lo que de verdad no esta: un predio que no existe en el padron de esta
 * municipalidad. Decir «no tiene frentes» de un predio inexistente manda a quien atiende a buscar
 * un dato que falta en vez del numero que escribio mal.
 *
 * <h2>El acceso es el del padron, y hay que decir por que</h2>
 *
 * <p>{@code actualizacion_catastro}, el mismo que {@code PredioController}. La zonificacion (#4),
 * el riesgo (#5) y la fiscalizacion (#6) declararon opcion propia porque son <b>otros contextos
 * acotados</b> y otras oficinas; el frente no: cuelga del predio, vive en el mismo contexto y lo
 * mantiene la misma gente. Una opcion propia crearia un permiso que se puede dar sin dar el del
 * predio del que el frente cuelga, y entonces alguien podria leer los metros de un predio que no
 * tiene permiso para ver.
 *
 * <p><b>Ningun metodo recibe la municipalidad</b>, ni como parametro ni como encabezado ni en el
 * cuerpo: sale del token (ADR-0005, regla 2).
 */
@RestController
@RequestMapping(Api.RAIZ + "/catastro/predios/{predioId}/frentes")
@RequiereAcceso(acceso = "actualizacion_catastro", privilegio = Privilegio.LECTURA)
public class FrenteController {

    private final ConsultaDeFrentes consulta;
    private final ConfirmarElFrente confirmacion;

    public FrenteController(ConsultaDeFrentes consulta, ConfirmarElFrente confirmacion) {
        this.consulta = consulta;
        this.confirmacion = confirmacion;
    }

    @GetMapping
    @RequiereAcceso(acceso = "actualizacion_catastro", privilegio = Privilegio.LECTURA)
    public FrentesDelPredioResource consultar(@PathVariable long predioId) {
        try {
            return FrentesDelPredioResource.de(consulta.delPredio(predioId));
        } catch (ConsultaDeFrentes.PredioInexistente noEsta) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, mensajeDe(noEsta));
        }
    }

    /**
     * Confirma la longitud de un frente: el acto que la vuelve oficial (AC 2, ADR-0021).
     *
     * <p>{@code MODIFICACION} y no {@code LECTURA}: esto cambia una cifra de la que cuelga un
     * cobro, y quien puede consultar los frentes no tiene por que poder afirmarlos.
     */
    @PostMapping("/{frenteId}/confirmacion")
    @RequiereAcceso(acceso = "actualizacion_catastro", privilegio = Privilegio.MODIFICACION)
    public FrenteResource confirmar(
            @PathVariable long predioId,
            @PathVariable long frenteId,
            @RequestBody ConfirmacionDeFrente peticion) {
        try {
            return FrenteResource.de(
                    confirmacion.confirmar(
                            frenteId,
                            longitudDe(peticion.longitud()),
                            observacionDe(peticion.observacion())));
        } catch (FrentesDelPredio.FrenteInexistente noEsta) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, mensajeDe(noEsta));
        } catch (IllegalArgumentException malFormada) {
            throw new ProblemaDeNegocio(CodigoDeError.VALIDACION, mensajeDe(malFormada));
        }
    }

    /**
     * La longitud que se afirma, en metros lineales.
     *
     * <p>La unidad la pone el dominio y no el cliente: si el cuerpo pudiera traerla, alguien
     * mandaria {@code M2} y se confirmarian metros cuadrados como si fueran lineales.
     */
    private static Medida longitudDe(@Nullable String longitud) {
        if (longitud == null || longitud.isBlank()) {
            throw new IllegalArgumentException(
                    "Confirmar un frente es afirmar unos metros: falta «longitud»");
        }
        return Medida.de(longitud.strip(), FrenteDelPredio.UNIDAD);
    }

    private static Observacion observacionDe(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(
                    "Toda modificacion exige la observacion de quien la hace (regla 10, RNF-052):"
                            + " falta «observacion»");
        }
        return Observacion.de(texto);
    }

    private static String mensajeDe(RuntimeException excepcion) {
        String mensaje = excepcion.getMessage();
        return mensaje == null ? "No se pudo operar sobre los frentes del predio" : mensaje;
    }
}
