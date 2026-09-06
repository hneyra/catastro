package kamayuk.catastro.fiscalizacion.infraestructura.web;

import java.util.List;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.autorizacion.RequiereAcceso;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.aplicacion.AbrirCampania;
import kamayuk.catastro.fiscalizacion.aplicacion.ConsultaDeCandidatos;
import kamayuk.catastro.fiscalizacion.aplicacion.ConsultaDeHallazgos;
import kamayuk.catastro.fiscalizacion.aplicacion.DetectarSubvaluadores;
import kamayuk.catastro.fiscalizacion.aplicacion.LevantarActa;
import kamayuk.catastro.fiscalizacion.aplicacion.RegistrarEvidencia;
import kamayuk.catastro.fiscalizacion.aplicacion.VerificarEnCampo;
import kamayuk.catastro.fiscalizacion.aplicacion.VerificarEnGabinete;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.ClaseDeHallazgo;
import kamayuk.catastro.fiscalizacion.dominio.CriterioDeCandidatos;
import kamayuk.catastro.fiscalizacion.dominio.EstadoDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.HuellaDeEvidencia;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import kamayuk.catastro.fiscalizacion.dominio.TipoDeEvidencia;
import kamayuk.catastro.fiscalizacion.dominio.Tolerancia;
import kamayuk.catastro.web.Api;
import kamayuk.catastro.web.CodigoDeError;
import kamayuk.catastro.web.ParametrosDePaginacion;
import kamayuk.catastro.web.ProblemaDeNegocio;
import kamayuk.catastro.web.RespuestaPaginada;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * El borde de la fiscalizacion catastral: la campania, sus candidatos, las dos compuertas, la
 * evidencia y el acta (ADR-0035).
 *
 * <h2>Un controlador y no cinco</h2>
 *
 * <p>Porque es un solo recorrido —abrir, detectar, gabinete, campo, evidencia, acta— y partirlo por
 * entidad dejaria la secuencia repartida en cinco archivos que hay que leer en orden para
 * entenderla. Lo que si cambia por metodo es el privilegio.
 *
 * <h2>Ningun cuerpo trae geometria</h2>
 *
 * <p>ADR-0021: el poligono entra por la carga cartografica, con su plano y su acta. El de un
 * candidato lo copia el detector del predio que ya lo tenia, y el de un hallazgo llega —cuando
 * llega— por la misma via. Un {@code @RequestBody} con un poligono dentro dejaria que el area de un
 * predio la cambiara quien tenga el endpoint, sin brigada. Lo vigila {@code
 * TODA_GEOMETRIA_ENTRA_POR_BATCH}.
 *
 * <h2>Ninguna respuesta trae un importe</h2>
 *
 * <p>Un hallazgo dice dos superficies y su diferencia. Lo que se cobre —si se cobra— lo decide
 * {@code rentas} (ADR-0024), y el dia que uno de estos recursos gane un campo de dinero, lo que hay
 * que revisar es la frontera.
 */
@RestController
@RequestMapping(Api.RAIZ + "/fiscalizacion")
@RequiereAcceso(acceso = "fiscalizacion_catastral", privilegio = Privilegio.LECTURA)
public class FiscalizacionCatastralController {

    /**
     * La cola de gabinete se abre por lo mas sospechoso, no por lo mas antiguo.
     *
     * <p>Es lo unico que hace util un score: quien revisa tiene un dia y diez mil candidatos, y el
     * orden decide cuales de ellos se miran de verdad.
     */
    private static final String ORDEN_DE_CANDIDATOS = "score";

    /** Los hallazgos, por la fecha en que se verificaron. */
    private static final String ORDEN_DE_HALLAZGOS = "verificadoEn";

    private final AbrirCampania campanias;
    private final DetectarSubvaluadores detector;
    private final VerificarEnGabinete gabinete;
    private final VerificarEnCampo campo;
    private final RegistrarEvidencia evidencias;
    private final LevantarActa actas;
    private final ConsultaDeCandidatos consultaDeCandidatos;
    private final ConsultaDeHallazgos consultaDeHallazgos;

    public FiscalizacionCatastralController(
            AbrirCampania campanias,
            DetectarSubvaluadores detector,
            VerificarEnGabinete gabinete,
            VerificarEnCampo campo,
            RegistrarEvidencia evidencias,
            LevantarActa actas,
            ConsultaDeCandidatos consultaDeCandidatos,
            ConsultaDeHallazgos consultaDeHallazgos) {
        this.campanias = campanias;
        this.detector = detector;
        this.gabinete = gabinete;
        this.campo = campo;
        this.evidencias = evidencias;
        this.actas = actas;
        this.consultaDeCandidatos = consultaDeCandidatos;
        this.consultaDeHallazgos = consultaDeHallazgos;
    }

    // ── La campania ────────────────────────────────────────────────────

    @PostMapping("/campanias")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereAcceso(acceso = "fiscalizacion_catastral", privilegio = Privilegio.REGISTRO)
    public CampaniaResource abrir(@RequestBody PeticionDeCampania peticion) {
        try {
            return CampaniaResource.de(
                    campanias.abrir(
                            exigir(peticion.codigo(), "codigo"),
                            exigir(peticion.nombre(), "nombre"),
                            scoreDe(peticion.umbral()),
                            observacionDe(peticion.observacion())));
        } catch (AbrirCampania.CampaniaYaAbierta yaEsta) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(yaEsta));
        }
    }

    /**
     * Lanza la deteccion de subvaluadores sobre la campania.
     *
     * <p><b>El 409 sin cartografia es la respuesta correcta, y no un 200 con lista vacia.</b> Hoy
     * no hay ni un poligono en ninguna instalacion: un {@code 200 []} se leeria como «no hay
     * subvaluadores» y nadie va a revisar un cero.
     */
    @PostMapping("/campanias/{campaniaId}/deteccion")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereAcceso(acceso = "fiscalizacion_catastral", privilegio = Privilegio.EJECUCION)
    public List<CandidatoResource> detectar(
            @PathVariable long campaniaId, @RequestBody PeticionDeDeteccion peticion) {
        try {
            return detector
                    .detectar(
                            campaniaId,
                            toleranciaDe(peticion.tolerancia()),
                            peticion.tope() == null ? 500 : peticion.tope(),
                            observacionDe(peticion.observacion()))
                    .stream()
                    .map(CandidatoResource::de)
                    .toList();
        } catch (AreasDelPadron.SinCartografia sinPlanos) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(sinPlanos));
        } catch (AbrirCampania.CampaniaInexistente noEsta) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, mensajeDe(noEsta));
        } catch (DetectarSubvaluadores.CampaniaCerradaParaDetectar cerrada) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(cerrada));
        }
    }

    // ── La cola y la tasa ──────────────────────────────────────────────

    @GetMapping("/campanias/{campaniaId}/candidatos")
    public RespuestaPaginada<CandidatoResource> candidatos(
            @PathVariable long campaniaId,
            @RequestParam(required = false) @Nullable String estado,
            @RequestParam(required = false) @Nullable String clase,
            ParametrosDePaginacion paginacion) {

        CriterioDeCandidatos criterio =
                CriterioDeCandidatos.deLaCampania(campaniaId)
                        .con(
                                estado == null
                                        ? null
                                        : valorDe(EstadoDelCandidato.class, estado, "estado"))
                        .de(clase == null ? null : valorDe(ClaseDeHallazgo.class, clase, "clase"));

        return RespuestaPaginada.de(
                consultaDeCandidatos.buscar(criterio, paginacion.aPaginacion(ORDEN_DE_CANDIDATOS)),
                CandidatoResource::de);
    }

    /**
     * La tasa de descarte por etapa (AC 7).
     *
     * <p>Es la unica cifra de este contrato que no describe una fila, y esta aqui porque es lo que
     * dice si el umbral de la campania sirve: sin ella, el descarte se conserva y nadie lo mira.
     */
    @GetMapping("/campanias/{campaniaId}/tasa-de-descarte")
    public TasaDeDescarteResource tasaDeDescarte(@PathVariable long campaniaId) {
        try {
            return TasaDeDescarteResource.de(consultaDeCandidatos.tasaDeDescarte(campaniaId));
        } catch (AbrirCampania.CampaniaInexistente noEsta) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, mensajeDe(noEsta));
        }
    }

    // ── Las dos compuertas ─────────────────────────────────────────────

    @PostMapping("/candidatos/{candidatoId}/gabinete")
    @RequiereAcceso(acceso = "fiscalizacion_catastral", privilegio = Privilegio.MODIFICACION)
    public CandidatoResource enGabinete(
            @PathVariable long candidatoId, @RequestBody PeticionDeCompuerta peticion) {
        Observacion observacion = observacionDe(peticion.observacion());
        try {
            if (peticion.admite()) {
                return CandidatoResource.de(gabinete.admitir(candidatoId, observacion));
            }
            return CandidatoResource.de(
                    gabinete.descartar(
                            candidatoId, exigir(peticion.motivo(), "motivo"), observacion));
        } catch (VerificarEnGabinete.CandidatoInexistente noEsta) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, mensajeDe(noEsta));
        } catch (Candidato.TransicionQueNoExiste noSePuede) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(noSePuede));
        }
    }

    @PostMapping("/candidatos/{candidatoId}/campo")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereAcceso(acceso = "fiscalizacion_catastral", privilegio = Privilegio.MODIFICACION)
    public HallazgoResource enCampo(
            @PathVariable long candidatoId, @RequestBody PeticionDeCampo peticion) {
        try {
            return HallazgoResource.de(
                    campo.confirmar(
                            candidatoId,
                            areaDe(peticion.areaVerificada()),
                            exigir(peticion.inspector(), "inspector"),
                            null,
                            observacionDe(peticion.observacion())));
        } catch (VerificarEnGabinete.CandidatoInexistente noEsta) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, mensajeDe(noEsta));
        } catch (Candidato.TransicionQueNoExiste noSePuede) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(noSePuede));
        } catch (VerificarEnCampo.PredioSinFichaQueContrastar sinFicha) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(sinFicha));
        }
    }

    @PostMapping("/candidatos/{candidatoId}/campo/descarte")
    @RequiereAcceso(acceso = "fiscalizacion_catastral", privilegio = Privilegio.MODIFICACION)
    public CandidatoResource descartarEnCampo(
            @PathVariable long candidatoId, @RequestBody PeticionDeCompuerta peticion) {
        try {
            return CandidatoResource.de(
                    campo.descartar(
                            candidatoId,
                            exigir(peticion.motivo(), "motivo"),
                            observacionDe(peticion.observacion())));
        } catch (VerificarEnGabinete.CandidatoInexistente noEsta) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, mensajeDe(noEsta));
        } catch (Candidato.TransicionQueNoExiste noSePuede) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(noSePuede));
        }
    }

    // ── El hallazgo, su evidencia y su acta ────────────────────────────

    @GetMapping("/campanias/{campaniaId}/hallazgos")
    public RespuestaPaginada<HallazgoResource> hallazgos(
            @PathVariable long campaniaId, ParametrosDePaginacion paginacion) {
        return RespuestaPaginada.de(
                consultaDeHallazgos.deLaCampania(
                        campaniaId, paginacion.aPaginacion(ORDEN_DE_HALLAZGOS)),
                HallazgoResource::de);
    }

    @PostMapping("/hallazgos/{hallazgoId}/evidencias")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereAcceso(acceso = "fiscalizacion_catastral", privilegio = Privilegio.REGISTRO)
    public EvidenciaResource adjuntar(
            @PathVariable long hallazgoId, @RequestBody PeticionDeEvidencia peticion) {
        try {
            return EvidenciaResource.de(
                    evidencias.adjuntar(
                            hallazgoId,
                            valorDe(TipoDeEvidencia.class, exigir(peticion.tipo(), "tipo"), "tipo"),
                            HuellaDeEvidencia.de(exigir(peticion.sha256(), "sha256")),
                            exigir(peticion.ruta(), "ruta"),
                            instanteDe(peticion.capturadoEn()),
                            peticion.dispositivo(),
                            observacionDe(peticion.observacion())));
        } catch (RegistrarEvidencia.HallazgoInexistente noEsta) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, mensajeDe(noEsta));
        } catch (RegistrarEvidencia.HallazgoSinEfecto sinEfecto) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(sinEfecto));
        } catch (RegistrarEvidencia.HuellaRepetida repetida) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(repetida));
        }
    }

    @GetMapping("/hallazgos/{hallazgoId}/evidencias")
    public List<EvidenciaResource> evidencias(@PathVariable long hallazgoId) {
        return consultaDeHallazgos.evidenciasDe(hallazgoId).stream()
                .map(EvidenciaResource::de)
                .toList();
    }

    @PostMapping("/hallazgos/{hallazgoId}/acta")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereAcceso(acceso = "fiscalizacion_catastral", privilegio = Privilegio.REGISTRO)
    public ActaResource levantarActa(
            @PathVariable long hallazgoId, @RequestBody PeticionDeActa peticion) {
        try {
            return ActaResource.de(
                    actas.levantar(
                            hallazgoId,
                            exigir(peticion.numero(), "numero"),
                            exigir(peticion.inspector(), "inspector"),
                            exigir(peticion.detalle(), "detalle"),
                            observacionDe(peticion.observacion())));
        } catch (RegistrarEvidencia.HallazgoInexistente noEsta) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, mensajeDe(noEsta));
        } catch (RegistrarEvidencia.HallazgoSinEfecto sinEfecto) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(sinEfecto));
        } catch (LevantarActa.SinLasDosCompuertas sinCompuertas) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(sinCompuertas));
        } catch (LevantarActa.ActaRepetida repetida) {
            throw new ProblemaDeNegocio(CodigoDeError.CONFLICTO, mensajeDe(repetida));
        }
    }

    // ── Traduccion del borde ───────────────────────────────────────────

    private static <T extends Enum<T>> T valorDe(Class<T> tipo, String texto, String campo) {
        try {
            return Enum.valueOf(tipo, texto.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException desconocido) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "El campo '" + campo + "' no admite el valor '" + texto + "'");
        }
    }

    private static Observacion observacionDe(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "Falta la observacion: toda escritura la exige, y sin ella no se guarda"
                            + " (regla 10, ADR-0008)");
        }
        try {
            return Observacion.de(texto);
        } catch (IllegalArgumentException corta) {
            throw new ProblemaDeNegocio(CodigoDeError.VALIDACION, mensajeDe(corta));
        }
    }

    private static Score scoreDe(@Nullable String texto) {
        try {
            return Score.de(exigir(texto, "umbral"));
        } catch (IllegalArgumentException malFormado) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION, "El umbral va de 0 a 1: '" + texto + "'");
        }
    }

    /** La tolerancia, como FRACCION de 1 y nunca como porcentaje. Ver {@link Tolerancia}. */
    private static Tolerancia toleranciaDe(@Nullable String texto) {
        try {
            return Tolerancia.de(exigir(texto, "tolerancia"));
        } catch (IllegalArgumentException malFormada) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "La tolerancia va de 0 a 1, como fraccion y no como porcentaje: '"
                            + texto
                            + "'");
        }
    }

    private static AreaM2 areaDe(@Nullable String texto) {
        try {
            return AreaM2.de(exigir(texto, "areaVerificada"));
        } catch (IllegalArgumentException malFormada) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "El area verificada va en metros cuadrados, no negativa: '" + texto + "'");
        }
    }

    /**
     * El reloj del APARATO, tal como llego.
     *
     * <p>Se exige y no se sustituye por el del servidor cuando falta: si se sustituyera, la
     * diferencia entre los dos relojes —que es lo que ADR-0035 punto 3 manda conservar— saldria
     * siempre cero y la captura seguiria sin poder auditarse, esta vez en silencio.
     */
    private static java.time.Instant instanteDe(@Nullable String texto) {
        try {
            return java.time.Instant.parse(exigir(texto, "capturadoEn"));
        } catch (java.time.format.DateTimeParseException malFormado) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "El instante de captura va en ISO-8601 con zona (2026-09-05T14:30:00Z): '"
                            + texto
                            + "'");
        }
    }

    private static String exigir(@Nullable String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION, "Falta el campo obligatorio '" + campo + "'");
        }
        return valor.strip();
    }

    /** El mensaje de una excepcion es anulable para el verificador; aqui nunca lo es. */
    private static String mensajeDe(RuntimeException excepcion) {
        String mensaje = excepcion.getMessage();
        return mensaje == null ? "El valor recibido no es valido" : mensaje;
    }

    // ── Los cuerpos: LISTA BLANCA ──────────────────────────────────────

    /**
     * Abrir una campania. Lo que no esta aqui no entra, aunque llegue en el JSON.
     *
     * <p>El umbral viaja como texto y se convierte a {@code BigDecimal} aqui: un {@code double} en
     * el {@code record} lo prohibe la regla 1, y ademas Jackson lo leeria con la imprecision que
     * {@code Score} existe para evitar.
     */
    public record PeticionDeCampania(
            @Nullable String codigo,
            @Nullable String nombre,
            @Nullable String umbral,
            @Nullable String observacion) {}

    /** Lanzar la deteccion. La tolerancia entra por peticion: la sabe quien lanza la campania. */
    public record PeticionDeDeteccion(
            @Nullable String tolerancia, @Nullable Integer tope, @Nullable String observacion) {}

    /**
     * Una compuerta: admitir o descartar.
     *
     * <p>{@code admite} y {@code motivo} van juntos a proposito — un descarte sin motivo no se
     * puede escribir, y lo rechaza el dominio antes de llegar a la base (ADR-0035 punto 5).
     */
    public record PeticionDeCompuerta(
            boolean admite, @Nullable String motivo, @Nullable String observacion) {}

    /**
     * La verificacion en campo.
     *
     * <p><b>No trae geometria</b>, y no es un olvido: un poligono que entra por HTTP cambia el
     * padron sin brigada, sin plano y sin acto (ADR-0021).
     */
    public record PeticionDeCampo(
            @Nullable String areaVerificada,
            @Nullable String inspector,
            @Nullable String observacion) {}

    /**
     * La evidencia. {@code capturadoEn} es el reloj del APARATO; el del servidor lo pone el caso de
     * uso.
     */
    public record PeticionDeEvidencia(
            @Nullable String tipo,
            @Nullable String sha256,
            @Nullable String ruta,
            @Nullable String capturadoEn,
            @Nullable String dispositivo,
            @Nullable String observacion) {}

    /** El acta. Sin importe: lo que se cobre lo decide `rentas` (ADR-0024). */
    public record PeticionDeActa(
            @Nullable String numero,
            @Nullable String inspector,
            @Nullable String detalle,
            @Nullable String observacion) {}
}
