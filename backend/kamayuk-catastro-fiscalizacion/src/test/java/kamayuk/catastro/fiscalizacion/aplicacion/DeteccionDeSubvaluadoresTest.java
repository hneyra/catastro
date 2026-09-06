package kamayuk.catastro.fiscalizacion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.ClaseDeHallazgo;
import kamayuk.catastro.fiscalizacion.dominio.ContrasteDeAreas;
import kamayuk.catastro.fiscalizacion.dominio.EstadoDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import kamayuk.catastro.fiscalizacion.dominio.Tolerancia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC 8 de #6: el detector produce <b>candidatos</b>, nunca correcciones, y sin poligonos dice que
 * no puede.
 */
@DisplayName("AC 8 — El detector de subvaluadores")
class DeteccionDeSubvaluadoresTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);
    private static final Observacion OBSERVACION =
            Observacion.de("corrida de deteccion de la campania de prueba");
    private static final Tolerancia DIEZ_POR_CIENTO = Tolerancia.de("0.10");

    private FiscalizacionEnMemoria repositorio;
    private List<RegistroDeAuditoria> bitacora;
    private long campaniaId;

    @BeforeEach
    void abrirLaCampania() {
        repositorio = new FiscalizacionEnMemoria();
        bitacora = new ArrayList<>();
        Campania campania =
                repositorio.guardar(
                        Campania.nueva(
                                "CAM-2026",
                                "Barrido de ortofoto 2026",
                                java.time.LocalDate.now(RELOJ),
                                Score.de("0.20")));
        campaniaId = campania.id();
    }

    /** El padron sin ningun poligono: la situacion REAL de hoy en todas las instalaciones. */
    private static final AreasDelPadron SIN_CARTOGRAFIA =
            (tolerancia, tope) -> {
                throw new AreasDelPadron.SinCartografia();
            };

    private static AreasDelPadron con(ContrasteDeAreas... contrastes) {
        return (tolerancia, tope) -> List.of(contrastes);
    }

    private static ContrasteDeAreas contraste(
            long predioId, long fichaId, String inscrita, String medida, String diferencia) {
        return new ContrasteDeAreas(
                predioId,
                fichaId,
                "2401010001000100010000" + predioId,
                AreaM2.de(inscrita),
                AreaM2.de(medida),
                Score.de(diferencia),
                "MULTIPOLYGON(((-80.0 -4.9, -80.0 -4.9002, -80.01 -4.9002, -80.01 -4.9, -80.0 -4.9)))");
    }

    private DetectarSubvaluadores detectorCon(AreasDelPadron areas) {
        Auditoria auditoria = bitacora::add;
        return new DetectarSubvaluadores(repositorio, areas, auditoria, RELOJ);
    }

    @Test
    @DisplayName("SIN POLIGONOS dice que no puede, y no devuelve cero subvaluadores")
    void sinPoligonosDiceQueNoPuede() {
        DetectarSubvaluadores detector = detectorCon(SIN_CARTOGRAFIA);

        assertThatThrownBy(() -> detector.detectar(campaniaId, DIEZ_POR_CIENTO, 500, OBSERVACION))
                .as(
                        "una lista vacia se leeria como «no hay subvaluadores», que es"
                                + " indistinguible de «no pude mirar» y que nadie va a revisar: la"
                                + " campania se cerraria con cero hallazgos y la conclusion seria"
                                + " que el padron esta bien")
                .isInstanceOf(AreasDelPadron.SinCartografia.class)
                .hasMessageContaining("no tiene ni un predio con geometria");

        assertThat(bitacora).as("y no deja ninguna traza de una corrida que no ocurrio").isEmpty();
    }

    @Test
    @DisplayName("produce CANDIDATOS, con su predio, su origen y sus insumos")
    void produceCandidatosYNoCorrecciones() {
        DetectarSubvaluadores detector =
                detectorCon(con(contraste(7L, 11L, "120.00", "180.00", "0.5000")));

        List<Candidato> detectados =
                detector.detectar(campaniaId, DIEZ_POR_CIENTO, 500, OBSERVACION);

        assertThat(detectados).hasSize(1);
        Candidato candidato = detectados.get(0);
        assertThat(candidato.estado())
                .as("lo que el detector puede producir es una sospecha, y nada mas")
                .isEqualTo(EstadoDelCandidato.DETECTADO);
        assertThat(candidato.clase()).isEqualTo(ClaseDeHallazgo.SUBVALUADOR);
        assertThat(candidato.predioId()).isEqualTo(7L);
        assertThat(candidato.score()).isEqualTo(Score.de("0.5000"));
        assertThat(candidato.insumos())
                .as(
                        "los insumos guardan las dos areas TAL COMO ESTABAN al contrastar: dentro"
                                + " de un ano la ficha estara versionada y el area de entonces no"
                                + " existira en ninguna parte")
                .contains("\"fichaId\":11")
                .contains("\"areaDeLaFicha\":120.00")
                .contains("\"areaDelPoligono\":180.00");
        assertThat(repositorio.hallazgos(campaniaId, unaPagina()).contenido())
                .as("y NINGUN hallazgo: eso lo produce una persona en la segunda compuerta")
                .isEmpty();
    }

    @Test
    @DisplayName("el umbral de la campania acota: lo que no lo alcanza no se escribe")
    void elUmbralAcota() {
        DetectarSubvaluadores detector =
                detectorCon(
                        con(
                                contraste(7L, 11L, "120.00", "180.00", "0.5000"),
                                contraste(8L, 12L, "120.00", "134.00", "0.1167")));

        List<Candidato> detectados =
                detector.detectar(campaniaId, DIEZ_POR_CIENTO, 500, OBSERVACION);

        assertThat(detectados)
                .as("el umbral de esta campania es 0,20 y el segundo contraste da 0,1167")
                .hasSize(1);
        assertThat(detectados.get(0).predioId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("una corrida entera deja UNA fila de auditoria, con sus cifras")
    void unaCorridaEsUnActo() {
        DetectarSubvaluadores detector =
                detectorCon(
                        con(
                                contraste(7L, 11L, "120.00", "180.00", "0.5000"),
                                contraste(8L, 12L, "100.00", "160.00", "0.6000")));

        detector.detectar(campaniaId, DIEZ_POR_CIENTO, 500, OBSERVACION);

        assertThat(bitacora)
                .as("es UN acto con una observacion, no N filas identicas salvo la clave")
                .hasSize(1);
        assertThat(bitacora.get(0).datosNuevos())
                .contains("\"contrastados\":2")
                .contains("\"detectados\":2")
                .contains("\"tolerancia\":0.10");
        assertThat(bitacora.get(0).observacion()).isEqualTo(OBSERVACION);
    }

    @Test
    @DisplayName("una campania CERRADA no admite candidatos nuevos")
    void laCampaniaCerradaNoAdmiteCandidatos() {
        Campania abierta = repositorio.campaniaPorId(campaniaId).orElseThrow();
        repositorio.guardar(
                new Campania(
                        abierta.id(),
                        abierta.codigo(),
                        abierta.nombre(),
                        kamayuk.catastro.fiscalizacion.dominio.EstadoDeCampania.CERRADA,
                        abierta.inicio(),
                        abierta.inicio(),
                        abierta.umbral()));

        DetectarSubvaluadores detector =
                detectorCon(con(contraste(7L, 11L, "120.00", "180.00", "0.5000")));

        assertThatThrownBy(() -> detector.detectar(campaniaId, DIEZ_POR_CIENTO, 500, OBSERVACION))
                .isInstanceOf(DetectarSubvaluadores.CampaniaCerradaParaDetectar.class)
                .hasMessageContaining("tasa de descarte que alguien ya pudo citar");
    }

    private static kamayuk.catastro.compartido.Paginacion unaPagina() {
        return kamayuk.catastro.compartido.Paginacion.de(0, 20, "id");
    }
}
