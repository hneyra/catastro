package kamayuk.catastro.nucleo.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.nucleo.aplicacion.ConfirmarElFrente;
import kamayuk.catastro.nucleo.aplicacion.ConsultaDeFrentes;
import kamayuk.catastro.nucleo.dominio.DerivacionDeFrentes;
import kamayuk.catastro.nucleo.dominio.EstadoDeLaLongitud;
import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import kamayuk.catastro.nucleo.dominio.FrentesDelPredio;
import kamayuk.catastro.web.ConfiguracionDeJson;
import kamayuk.catastro.web.ManejadorDeErrores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * Capa web de los frentes (#7, AC 3): {@code GET /…/catastro/predios/{predioId}/frentes}.
 *
 * <p>Sin base de datos: lo que se verifica es el <b>transporte</b> —forma del JSON, rutas y
 * traduccion de errores—. Lo que solo la base puede verificar —que el corte encuentre la via, que
 * el marco vaya delante y que la politica acote— tiene su prueba contra PostGIS de verdad en {@code
 * DerivacionDeFrentesJdbcTest}.
 *
 * <p><b>El caso que da nombre a esta clase es la lista vacia</b>, no la lista con frentes. Hoy no
 * hay ni un poligono cargado en ninguna instalacion, asi que el predio sin frentes derivados es el
 * camino que se recorre siempre al principio — y un {@code 200 []} a secas seria indistinguible de
 * «este predio no da a ninguna calle», que se arregla de otra manera.
 */
@DisplayName("#7 — Capa web: los frentes de un predio, y la lista vacia que dice desde cuando")
class FrenteControllerTest {

    private static final String RUTA = "/catastro/api/v1/catastro/predios/7/frentes";

    private final ConsultaDeMentira consulta = new ConsultaDeMentira();
    private final ConfirmacionDeMentira confirmacion = new ConfirmacionDeMentira();

    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new FrenteController(consulta, confirmacion))
                    .setControllerAdvice(new ManejadorDeErrores())
                    .setMessageConverters(
                            new JacksonJsonHttpMessageConverter(
                                    JsonMapper.builder()
                                            .addModule(
                                                    new ConfiguracionDeJson()
                                                            .moduloDeObjetosDeValor())
                                            .build()))
                    .build();

    @Test
    @DisplayName("un predio SIN frentes derivados contesta 200, lista vacia y NUNCA derivado")
    void sinFrentesYSinDerivarDiceQueNoSeHaDerivado() throws Exception {
        consulta.responde(
                new ConsultaDeFrentes.FrentesConsultados(7L, List.of(), Optional.empty()));

        MvcResult respuesta = mvc.perform(get(RUTA)).andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
        assertThat(respuesta.getResponse().getContentAsString())
                .as(
                        "«no da a ninguna calle» y «nadie lo ha calculado» se arreglan de maneras"
                                + " distintas —midiendo en campo la primera, cargando la cartografia la"
                                + " segunda—, asi que la respuesta tiene que distinguirlas")
                .contains("\"frentes\":[]")
                .contains("\"derivadoEn\":null")
                .contains("\"frentesDerivados\":null");
    }

    @Test
    @DisplayName("EL CONTRASTE: derivado y sin frentes dice CUANDO y POR QUE")
    void derivadoYSinFrentesDiceCuandoYPorQue() throws Exception {
        consulta.responde(
                new ConsultaDeFrentes.FrentesConsultados(
                        7L,
                        List.of(),
                        Optional.of(
                                new DerivacionDeFrentes(
                                        7L,
                                        Instant.parse("2026-09-06T12:00:00Z"),
                                        0,
                                        "El lote no tiene poligono"))));

        MvcResult respuesta = mvc.perform(get(RUTA)).andReturn();

        assertThat(respuesta.getResponse().getContentAsString())
                .as(
                        "sin este caso, la prueba de arriba pasaria con una respuesta que no"
                                + " llevara nunca la constancia — y las dos listas vacias volverian"
                                + " a ser la misma")
                .contains("\"derivadoEn\":\"2026-09-06T12:00:00Z\"")
                .contains("\"frentesDerivados\":0")
                .contains("El lote no tiene poligono");
    }

    @Test
    @DisplayName("con frentes, cada uno lleva su via, su longitud con unidad y su estado")
    void conFrentesLlevaLaUnidadYElEstado() throws Exception {
        consulta.responde(
                new ConsultaDeFrentes.FrentesConsultados(
                        7L,
                        List.of(unFrente(EstadoDeLaLongitud.PROPUESTA, null, null)),
                        Optional.of(
                                new DerivacionDeFrentes(
                                        7L, Instant.parse("2026-09-06T12:00:00Z"), 1, null))));

        MvcResult respuesta = mvc.perform(get(RUTA)).andReturn();

        String cuerpo = respuesta.getResponse().getContentAsString();
        assertThat(cuerpo)
                .as(
                        "la unidad va DENTRO de la cifra, como el frontis de la ficha: el barrido"
                                + " se determina sobre metros LINEALES y el recojo sobre CUADRADOS, y"
                                + " leer unos por otros no falla — cobra otra cosa")
                .contains("\"longitud\":\"18.50 ML\"")
                .contains("\"longitudEstado\":\"PROPUESTA\"")
                .contains("\"viaCodigo\":\"V-01\"")
                .contains("\"confirmadoPor\":null");
        assertThat(cuerpo)
                .as("y ni un importe: el arbitrio lo determina `rentas` (ADR-0024)")
                .doesNotContain("importe")
                .doesNotContain("tarifa")
                .doesNotContain("S/");
    }

    @Test
    @DisplayName("un predio que NO existe contesta 404, y no una lista vacia")
    void unPredioInexistenteEs404() throws Exception {
        consulta.lanza(new ConsultaDeFrentes.PredioInexistente(7L));

        MvcResult respuesta = mvc.perform(get(RUTA)).andReturn();

        assertThat(respuesta.getResponse().getStatus())
                .as(
                        "decir «no tiene frentes» de un predio que no existe manda a quien atiende"
                                + " a buscar un dato que falta en vez del numero que escribio mal")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("confirmar la longitud devuelve el frente CONFIRMADO, con quien y cuando")
    void confirmarDevuelveElFrenteConfirmado() throws Exception {
        confirmacion.responde(
                unFrente(
                        EstadoDeLaLongitud.CONFIRMADA,
                        "tecnico.catastro",
                        Instant.parse("2026-09-06T12:00:00Z")));

        MvcResult respuesta =
                mvc.perform(
                                post(RUTA + "/9/confirmacion")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"longitud\":\"18.50\",\"observacion\":\"Medido"
                                                        + " en campo con cinta metrica\"}"))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
        assertThat(respuesta.getResponse().getContentAsString())
                .contains("\"longitudEstado\":\"CONFIRMADA\"")
                .contains("\"confirmadoPor\":\"tecnico.catastro\"");
    }

    @Test
    @DisplayName("confirmar SIN observacion se rechaza: sin ella no se guarda (regla 10)")
    void confirmarSinObservacionSeRechaza() throws Exception {
        MvcResult respuesta =
                mvc.perform(
                                post(RUTA + "/9/confirmacion")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"longitud\":\"18.50\"}"))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
        assertThat(respuesta.getResponse().getContentAsString()).contains("observacion");
    }

    @Test
    @DisplayName("y confirmar sin longitud tampoco: confirmar es afirmar unos metros")
    void confirmarSinLongitudSeRechaza() throws Exception {
        MvcResult respuesta =
                mvc.perform(
                                post(RUTA + "/9/confirmacion")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"observacion\":\"Medido en campo\"}"))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
        assertThat(respuesta.getResponse().getContentAsString()).contains("longitud");
    }

    @Test
    @DisplayName("confirmar un frente que no existe contesta 404")
    void confirmarUnFrenteInexistenteEs404() throws Exception {
        confirmacion.lanza(new FrentesDelPredio.FrenteInexistente(9L));

        MvcResult respuesta =
                mvc.perform(
                                post(RUTA + "/9/confirmacion")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"longitud\":\"18.50\",\"observacion\":\"Medido"
                                                        + " en campo con cinta metrica\"}"))
                        .andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(404);
    }

    private static FrenteDelPredio unFrente(
            EstadoDeLaLongitud estado, String quien, Instant cuando) {
        return new FrenteDelPredio(
                9L,
                7L,
                200L,
                "V-01",
                "Avenida Grau",
                "LINESTRING(-80.7 -4.9, -80.7002 -4.9)",
                Medida.enMetrosLineales("18.50"),
                estado,
                true,
                "101",
                null,
                quien,
                cuando);
    }

    /** El caso de uso, doblado: esta prueba es del transporte y no del cruce espacial. */
    private static final class ConsultaDeMentira extends ConsultaDeFrentes {

        private ConsultaDeFrentes.FrentesConsultados respuesta;
        private RuntimeException fallo;

        ConsultaDeMentira() {
            super(null);
        }

        void responde(ConsultaDeFrentes.FrentesConsultados consultado) {
            this.respuesta = consultado;
            this.fallo = null;
        }

        void lanza(RuntimeException excepcion) {
            this.fallo = excepcion;
            this.respuesta = null;
        }

        @Override
        public FrentesConsultados delPredio(long predioId) {
            if (fallo != null) {
                throw fallo;
            }
            return respuesta;
        }
    }

    /** El acto de confirmar, doblado por lo mismo. */
    private static final class ConfirmacionDeMentira extends ConfirmarElFrente {

        private FrenteDelPredio respuesta;
        private RuntimeException fallo;

        ConfirmacionDeMentira() {
            super(null, null, null);
        }

        void responde(FrenteDelPredio frente) {
            this.respuesta = frente;
            this.fallo = null;
        }

        void lanza(RuntimeException excepcion) {
            this.fallo = excepcion;
            this.respuesta = null;
        }

        @Override
        public FrenteDelPredio confirmar(long frenteId, Medida longitud, Observacion observacion) {
            if (fallo != null) {
                throw fallo;
            }
            return respuesta;
        }
    }
}
