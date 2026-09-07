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

    /** El tope que la campania de esta prueba declara. Ya no lo pone el borde (#25). */
    private static final int TOPE = 500;

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
                                Score.de("0.20"),
                                TOPE),
                        OBSERVACION);
        campaniaId = campania.id();
    }

    /** El padron sin ningun poligono: la situacion REAL de hoy en todas las instalaciones. */
    private static final AreasDelPadron SIN_CARTOGRAFIA =
            new AreasDelPadron() {
                @Override
                public CruceDelPadron contrastar(Score umbral, int tope) {
                    throw new AreasDelPadron.SinCartografia();
                }

                @Override
                public boolean estaEnElPadron(long predioId) {
                    // Sin cartografia el padron sigue teniendo predios: lo que falta son planos.
                    return true;
                }
            };

    /**
     * Un padron que <b>aplica el umbral que le pidan</b>, y anota con que se lo pidieron.
     *
     * <p>Desde #25 el umbral se aplica en UN solo sitio —el {@code WHERE} del cruce— y el caso de
     * uso no vuelve a filtrar. De modo que un doble que devolviera todo lo que le den mediria un
     * filtro que ya no existe, y las pruebas de esta clase dirian que el umbral acota cuando quien
     * acota es la base. Aqui el doble imita lo que el SQL hace —{@code >=}, que es {@link
     * Score#alcanza}— y guarda lo que recibio, que es lo que esta capa SI puede afirmar: que el
     * detector pide con las dos cifras de la campania y no con otras.
     */
    private static final class PadronQueAnotaLoQuePidieron implements AreasDelPadron {

        private final List<ContrasteDeAreas> todos;
        private Score umbralPedido;
        private int topePedido;

        PadronQueAnotaLoQuePidieron(ContrasteDeAreas... contrastes) {
            this.todos = List.of(contrastes);
        }

        @Override
        public CruceDelPadron contrastar(Score umbral, int tope) {
            this.umbralPedido = umbral;
            this.topePedido = tope;
            List<ContrasteDeAreas> alcanzan =
                    todos.stream()
                            .filter(uno -> uno.diferenciaRelativa().alcanza(umbral))
                            .limit(tope)
                            .toList();
            return new CruceDelPadron(
                    alcanzan,
                    new Cobertura(
                            todos.size(), 0, 0, todos.size(), alcanzan.size(), alcanzan.size()));
        }

        @Override
        public boolean estaEnElPadron(long predioId) {
            return true;
        }
    }

    private static PadronQueAnotaLoQuePidieron con(ContrasteDeAreas... contrastes) {
        return new PadronQueAnotaLoQuePidieron(contrastes);
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

        assertThatThrownBy(() -> detector.detectar(campaniaId, OBSERVACION))
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

        List<Candidato> detectados = detector.detectar(campaniaId, OBSERVACION).candidatos();

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
                // Entrecomilladas desde #20: las dos areas van tipadas como `AreaM2` y las
                // escribe el serializador —la cifra sola, sin la unidad—, que es donde #607 dice
                // que tienen que escribirse. Antes se componian a mano con `.valor()`.
                .contains("\"areaDeLaFicha\":\"120.00\"")
                .contains("\"areaDelPoligono\":\"180.00\"");
        assertThat(repositorio.hallazgos(campaniaId, unaPagina()).contenido())
                .as("y NINGUN hallazgo: eso lo produce una persona en la segunda compuerta")
                .isEmpty();
    }

    @Test
    @DisplayName("pide el cruce con el umbral y el tope DE LA CAMPANIA, y con ningun otro (#25)")
    void pideConElCriterioDeLaCampania() {
        PadronQueAnotaLoQuePidieron padron =
                con(
                        contraste(7L, 11L, "120.00", "180.00", "0.5000"),
                        contraste(8L, 12L, "120.00", "134.00", "0.1167"));
        DetectarSubvaluadores detector = detectorCon(padron);

        List<Candidato> detectados = detector.detectar(campaniaId, OBSERVACION).candidatos();

        assertThat(padron.umbralPedido)
                .as(
                        "la cifra con la que se filtra es la que la campania guarda. Antes de #25"
                                + " venia una `tolerancia` en el cuerpo de la peticion, y con"
                                + " `tolerancia > umbral` esta columna decia un criterio que no fue"
                                + " el que corrio")
                .isEqualTo(Score.de("0.20"));
        assertThat(padron.topePedido)
                .as("y el tope tambien sale de la campania: antes eran 500 escritos en el borde")
                .isEqualTo(TOPE);
        assertThat(detectados)
                .as("el umbral de esta campania es 0,20 y el segundo contraste da 0,1167")
                .hasSize(1);
        assertThat(detectados.get(0).predioId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("y el censo viaja con el resultado: «0 candidatos» dice de cuantos predios sale")
    void elCensoViajaConElResultado() {
        DetectarSubvaluadores detector =
                detectorCon(con(contraste(7L, 11L, "120.00", "180.00", "0.5000")));

        DetectarSubvaluadores.Deteccion deteccion = detector.detectar(campaniaId, OBSERVACION);

        assertThat(deteccion.cobertura().contrastados())
                .as(
                        "sin esta cifra, «0 candidatos» y «0 candidatos entre las fichas que miro»"
                                + " se leen igual, y la segunda cierra una campania afirmando que"
                                + " el padron esta bien")
                .isEqualTo(1);
        assertThat(deteccion.cobertura().superanElUmbral()).isEqualTo(1);
        assertThat(deteccion.cobertura().truncadosPorElTope()).isZero();
    }

    @Test
    @DisplayName("una corrida entera deja UNA fila de auditoria, con sus cifras")
    void unaCorridaEsUnActo() {
        DetectarSubvaluadores detector =
                detectorCon(
                        con(
                                contraste(7L, 11L, "120.00", "180.00", "0.5000"),
                                contraste(8L, 12L, "100.00", "160.00", "0.6000")));

        detector.detectar(campaniaId, OBSERVACION);

        assertThat(bitacora)
                .as("es UN acto con una observacion, no N filas identicas salvo la clave")
                .hasSize(1);
        assertThat(String.valueOf(bitacora.get(0).datosNuevos()))
                .contains("\"contrastados\":2")
                .contains("\"detectados\":2")
                .as("el criterio que se asienta es el de la campania, y son las DOS cifras (#25)")
                // Entrecomillada desde #20: la bitacora escribe todo decimal como texto, porque
                // se republica verbatim y ahi ningun esquema declara el tipo de nada. El tope es
                // un entero y sigue sin comillas.
                .contains("\"umbral\":\"0.20\"")
                .contains("\"tope\":500");
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
                        abierta.umbral(),
                        abierta.tope()),
                OBSERVACION);

        DetectarSubvaluadores detector =
                detectorCon(con(contraste(7L, 11L, "120.00", "180.00", "0.5000")));

        assertThatThrownBy(() -> detector.detectar(campaniaId, OBSERVACION))
                .isInstanceOf(DetectarSubvaluadores.CampaniaCerradaParaDetectar.class)
                .hasMessageContaining("tasa de descarte que alguien ya pudo citar");
    }

    private static kamayuk.catastro.compartido.Paginacion unaPagina() {
        return kamayuk.catastro.compartido.Paginacion.de(0, 20, "id");
    }
}
