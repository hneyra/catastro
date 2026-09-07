package kamayuk.catastro.nucleo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.compartido.MarcoGeografico;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.nucleo.dominio.DerivacionDeFrentes;
import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import kamayuk.catastro.nucleo.dominio.FrentePropuesto;
import kamayuk.catastro.nucleo.dominio.FrentesDelPredio;
import kamayuk.catastro.nucleo.dominio.MarcoDeLoLevantado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El recorrido del derivador (#7, AC 1 y AC 2), sin base de datos.
 *
 * <p>Lo que el corte encuentra tiene su prueba contra PostGIS ({@code
 * DerivacionDeFrentesJdbcTest}). Lo que se mide aqui es lo otro: que la corrida <b>deje constancia
 * pase lo que pase</b> y que lo que escriba nazca como propuesta.
 */
@DisplayName("#7 — El recorrido del derivador de frentes")
class DerivacionDeLosFrentesTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC);
    private static final Medida OCHO_METROS = Medida.enMetrosLineales("8.00");
    private static final Observacion PORQUE = Observacion.de("Derivacion de la prueba de #7");

    private final FrentesDeMentira frentes = new FrentesDeMentira();
    private final AuditoriaDeMentira auditoria = new AuditoriaDeMentira();

    private DerivacionDeLosFrentes derivacion() {
        return new DerivacionDeLosFrentes(
                frentes, new ProponerLosFrentesDeUnPredio(frentes, auditoria, RELOJ));
    }

    @Test
    @DisplayName("cada propuesta escrita deja su fila de auditoria, con su unidad y su estado")
    void cadaPropuestaEscritaDejaAuditoria() {
        frentes.predios(100L);
        frentes.corta(100L, propuesto(100L, 200L), propuesto(100L, 201L));

        DerivacionDeLosFrentes.Informe informe = derivacion().derivar(OCHO_METROS, 500, PORQUE);

        assertThat(informe.prediosRecorridos()).isEqualTo(1);
        assertThat(informe.prediosConFrenteNuevo()).isEqualTo(1);
        assertThat(informe.frentesPropuestos()).isEqualTo(2);
        assertThat(auditoria.descripciones)
                .as(
                        "la auditoria dice que es una PROPUESTA y de donde salio: sin eso, dentro"
                                + " de dos anios nadie puede contestar de donde salio la cifra con"
                                + " la que se cobro")
                .hasSize(2)
                // Campos y no prosa desde #20: la columna es `jsonb` y lo que se le pasaba era
                // «Frente PROPUESTO a la via 200: 18.50 ML. Derivado del corte…», que el
                // `cast(… AS jsonb)` rechazaba SIEMPRE. Aqui se afirma lo mismo por campo, que es
                // ademas como se puede consultar despues.
                .allMatch(descripcion -> descripcion.contains("\"estado\":\"PROPUESTA\""))
                .allMatch(descripcion -> descripcion.contains("\"longitud\":\"18.50 ML\""))
                .allMatch(
                        descripcion ->
                                descripcion.contains(
                                        "\"origen\":\"CORTE_CONTRA_EL_EJE_DE_CALZADA\""));
    }

    @Test
    @DisplayName("un predio sin ningun corte deja constancia IGUAL, y con su motivo")
    void unPredioSinCorteDejaConstanciaConMotivo() {
        frentes.predios(100L);

        DerivacionDeLosFrentes.Informe informe = derivacion().derivar(OCHO_METROS, 500, PORQUE);

        assertThat(informe.frentesPropuestos()).isZero();
        assertThat(frentes.anotadas).hasSize(1);
        DerivacionDeFrentes constancia = frentes.anotadas.get(0);
        assertThat(constancia.propuestos()).isZero();
        assertThat(constancia.motivo())
                .as(
                        "es la mitad que hace util al endpoint: sin el motivo, «no da a ninguna"
                                + " calle» y «no hay cartografia» son la misma lista vacia, y nadie"
                                + " revisa un cero (#6, AC 8)")
                .isNotNull()
                .contains("no tiene poligono");
    }

    @Test
    @DisplayName("y la constancia se anota tambien cuando la propuesta YA ESTABA")
    void laConstanciaSeAnotaTambienCuandoYaEstaba() {
        frentes.predios(100L);
        frentes.corta(100L, propuesto(100L, 200L));
        frentes.yaEstaTodo();

        DerivacionDeLosFrentes.Informe informe = derivacion().derivar(OCHO_METROS, 500, PORQUE);

        assertThat(informe.frentesPropuestos())
                .as("no se escribio nada: volver a derivar no pisa lo que hay")
                .isZero();
        assertThat(frentes.anotadas.get(0).propuestos())
                .as(
                        "pero el corte SI dio un tramo, y eso es lo que la constancia cuenta:"
                                + " «cuantos frentes tiene este predio segun el corte», que es estable"
                                + " entre corridas")
                .isEqualTo(1);
        assertThat(frentes.anotadas.get(0).motivo()).isNull();
    }

    @Test
    @DisplayName("en un padron del Peru el informe no trae ningun aviso de latitud")
    void enUnPadronDelPeruNoHayAvisoDeLatitud() {
        frentes.predios(100L);
        frentes.corta(100L, propuesto(100L, 200L));

        DerivacionDeLosFrentes.Informe informe = derivacion().derivar(OCHO_METROS, 500, PORQUE);

        assertThat(informe.avisoDeLatitud())
                .as(
                        "es el contraste, y sin el «lo dice» no significa nada: un aviso que sale"
                                + " siempre deja de leerse (#437)")
                .isNull();
    }

    @Test
    @DisplayName("y fuera de la banda de la constante el informe LO DICE (#29)")
    void fueraDeLaBandaElInformeLoDice() {
        // Hasta #29 el limite de `MargenDelMarco` era una frase de su javadoc que no consultaba
        // nadie: una instalacion fuera de la banda habria derivado frentes de menos sin ningun
        // sintoma —el marco descarta la via ANTES de que el ST_DWithin la vea—, y un frente que no
        // se propone es indistinguible de un predio que no da a la calle.
        frentes.predios(100L);
        frentes.corta(100L, propuesto(100L, 200L));
        frentes.padronEn(marco("-106.20", "28.55", "-106.00", "28.75"), 3);

        DerivacionDeLosFrentes.Informe informe = derivacion().derivar(OCHO_METROS, 500, PORQUE);

        assertThat(informe.frentesPropuestos())
                .as("y no se niega a derivar: lo que si sale sigue siendo correcto")
                .isEqualTo(1);
        assertThat(informe.avisoDeLatitud())
                .as("el aviso nombra la latitud que se salio y lo que va a pasar por ello")
                .isNotNull()
                .contains("28.75")
                .contains("descarta vias");
    }

    @Test
    @DisplayName("sin ni un poligono cargado no hay latitud que mirar, y eso no es un aviso")
    void sinCartografiaNoHayAvisoDeLatitud() {
        // El estado de hoy en toda instalacion. Que salga aviso aqui seria gritar en lo correcto:
        // lo que falta es la carga cartografica, y eso ya lo dice el informe con sus ceros y cada
        // predio con su motivo en `frente_derivacion`.
        frentes.predios(100L);
        frentes.padronEn(null, 0);

        DerivacionDeLosFrentes.Informe informe = derivacion().derivar(OCHO_METROS, 500, PORQUE);

        assertThat(informe.avisoDeLatitud()).isNull();
        assertThat(informe.frentesPropuestos()).isZero();
    }

    @Test
    @DisplayName("una corrida con lotes de cero predios no es una corrida")
    void unaCorridaConLoteVacioSeRechaza() {
        assertThatThrownBy(() -> derivacion().derivar(OCHO_METROS, 0, PORQUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no recorre ningun predio");
    }

    @Test
    @DisplayName("y sin observacion no se corre (regla 10)")
    void sinObservacionNoSeCorre() {
        assertThatThrownBy(() -> derivacion().derivar(OCHO_METROS, 500, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("observacion");
    }

    // ── #26: el recorrido POR LOTES, y su denominador ─────────────────

    /**
     * AC-1: la corrida recorre el padron entero aunque no quepa en un lote.
     *
     * <p>Se afirma <b>la lista exacta de predios recorridos</b> y no su tamano, y hacen falta las
     * dos cosas que eso mide a la vez: que no se pare en el primer lote —el defecto de #26 (a)— y
     * que no vuelva a pasar por los mismos. Un recuento solo no distingue «recorrio 1 200» de
     * «recorrio 500 tres veces», y con el cursor muerto eso es exactamente lo que pasaba.
     */
    @Test
    @DisplayName("#26 — la corrida recorre el padron ENTERO, aunque no quepa en un lote")
    void laCorridaRecorreElPadronEnteroPorLotes() {
        frentes.tantosPredios(1200);

        DerivacionDeLosFrentes.Informe informe = derivacion().derivar(OCHO_METROS, 500, PORQUE);

        assertThat(frentes.recorridos)
                .as(
                        "los 1 200, cada uno UNA vez. Hasta #26 el `desde` del puerto estaba muerto"
                                + " —`prediosPorDerivar(0L, tope)`, sin bucle— y los 700 de detras"
                                + " del primer lote no se derivaban nunca")
                .containsExactlyElementsOf(
                        java.util.stream.LongStream.rangeClosed(1, 1200).boxed().toList());
        assertThat(informe.prediosRecorridos()).isEqualTo(1200);
        assertThat(informe.lotes()).as("1 200 en lotes de 500 son tres consultas").isEqualTo(3);
    }

    /**
     * AC-2: el informe dice de cuantos.
     *
     * <p>«500 predio(s) recorrido(s)» es lo que hacia que una corrida que dejaba fuera al 96 % del
     * padron pareciera completa. El denominador es lo unico que lo delata sin ir a contar a mano.
     */
    @Test
    @DisplayName("#26 — el informe dice cuantos habia, y si se agoto el padron")
    void elInformeDiceDeCuantosYSiSeAgoto() {
        frentes.tantosPredios(1200);

        DerivacionDeLosFrentes.Informe informe = derivacion().derivar(OCHO_METROS, 500, PORQUE);

        assertThat(informe.prediosEnElPadron())
                .as("un recuento sin denominador no dice si la corrida termino")
                .isEqualTo(1200);
        assertThat(informe.agotoElPadron()).isTrue();
    }

    /**
     * EL CONTRASTE de AC-2: {@code agotoElPadron()} puede ser falso.
     *
     * <p>Sin este caso, la afirmacion de arriba la cumpliria tambien un metodo que devolviera
     * siempre {@code true} — que es la asercion que no puede fallar por el motivo que dice
     * comprobar. Aqui el informe se compone a mano con las cifras que dejaba la corrida rota.
     */
    @Test
    @DisplayName("#26 — EL CONTRASTE: un informe que no agoto el padron lo dice")
    void unInformeQueNoAgotoElPadronLoDice() {
        DerivacionDeLosFrentes.Informe corridaQueSeParoEnElPrimerLote =
                // El sexto componente es el aviso de latitud de #29: aqui va nulo porque lo que
                // este contraste mide es `agotoElPadron()`, y un padron del Peru no trae aviso.
                new DerivacionDeLosFrentes.Informe(1200, 500, 1, 0, 0, null);

        assertThat(corridaQueSeParoEnElPrimerLote.agotoElPadron())
                .as("es exactamente el estado de #26 (a): 500 de 1 200 y nada que lo dijera")
                .isFalse();
    }

    /**
     * Un adaptador que no respeta su {@code desde} para la corrida, en vez de dejarla en bucle.
     *
     * <p>Es la otra cara del cursor: mientras el {@code desde} no se usaba, ignorarlo era el
     * comportamiento normal y no rompia nada. Ahora el recorrido depende de el, y un adaptador que
     * devolviera siempre el primer lote no daria una corrida corta sino una que <b>no termina</b>
     * —proponiendo lo mismo para siempre, sin informe y sin nadie que sepa por que—.
     */
    @Test
    @DisplayName("#26 — un adaptador que ignora el cursor para la corrida y dice por que")
    void unAdaptadorQueIgnoraElCursorParaLaCorrida() {
        frentes.tantosPredios(1200);
        frentes.ignoraElCursor();

        assertThatThrownBy(() -> derivacion().derivar(OCHO_METROS, 500, PORQUE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("el cursor no avanza")
                .hasMessageContaining("prediosPorDerivar");
    }

    private static MarcoGeografico marco(String oeste, String sur, String este, String norte) {
        return new MarcoGeografico(
                new BigDecimal(oeste),
                new BigDecimal(sur),
                new BigDecimal(este),
                new BigDecimal(norte));
    }

    private static FrentePropuesto propuesto(long predioId, long viaId) {
        return new FrentePropuesto(
                predioId,
                viaId,
                "LINESTRING(-80.7 -4.9, -80.7002 -4.9)",
                Medida.enMetrosLineales("18.4999"));
    }

    /**
     * El repositorio, doblado.
     *
     * <h2>Y HONESTO con {@code desde} y con el tamano del lote, que es la mitad del hallazgo</h2>
     *
     * <p>Hasta #26 este doble contestaba {@code List.copyOf(predios)} <b>ignorando los dos
     * argumentos</b>. Con eso, la corrida que solo pedia el primer lote empezando de cero —el
     * defecto (a) de #26— recorria el padron entero en la prueba y salia en VERDE: el doble tapaba
     * exactamente lo que el caso de uso hacia mal. Un doble que no respeta el contrato del puerto
     * no dobla el puerto: dobla lo que a la prueba le conviene.
     */
    private static final class FrentesDeMentira implements FrentesDelPredio {

        private final List<Long> predios = new ArrayList<>();
        private final java.util.Map<Long, List<FrentePropuesto>> cortes = new java.util.HashMap<>();
        private final List<DerivacionDeFrentes> anotadas = new ArrayList<>();
        private final List<Long> recorridos = new ArrayList<>();
        private boolean todoYaEstaba;
        private boolean ignoraElCursor;

        /** Sullana, que es donde estan los datos de este proyecto: dentro de la banda. */
        private MarcoDeLoLevantado levantado =
                new MarcoDeLoLevantado(marco("-80.71", "-4.92", "-80.66", "-4.87"), 2);

        void padronEn(@org.jspecify.annotations.Nullable MarcoGeografico marco, long lotes) {
            levantado = new MarcoDeLoLevantado(marco, lotes);
        }

        void predios(long... ids) {
            for (long id : ids) {
                predios.add(id);
            }
        }

        void tantosPredios(int cuantos) {
            for (int i = 1; i <= cuantos; i++) {
                predios.add((long) i);
            }
        }

        /** Un adaptador roto: el que devuelve siempre el primer lote. */
        void ignoraElCursor() {
            ignoraElCursor = true;
        }

        void corta(long predioId, FrentePropuesto... propuestos) {
            cortes.put(predioId, List.of(propuestos));
        }

        void yaEstaTodo() {
            todoYaEstaba = true;
        }

        @Override
        public List<FrenteDelPredio> deUnPredio(long predioId) {
            return List.of();
        }

        @Override
        public Optional<DerivacionDeFrentes> ultimaDerivacion(long predioId) {
            return Optional.empty();
        }

        @Override
        public boolean existeElPredio(long predioId) {
            return predios.contains(predioId);
        }

        @Override
        public MarcoDeLoLevantado marcoDelPadron() {
            return levantado;
        }

        @Override
        public List<Long> prediosPorDerivar(long desde, int tamanoDelLote) {
            long cursor = ignoraElCursor ? 0L : desde;
            return predios.stream()
                    .sorted()
                    .filter(id -> id > cursor)
                    .limit(tamanoDelLote)
                    .toList();
        }

        @Override
        public int cuantosPrediosPorDerivar() {
            return predios.size();
        }

        @Override
        public Optional<FrenteDelPredio> unFrente(long frenteId) {
            return Optional.empty();
        }

        @Override
        public List<FrentePropuesto> cortarContraLasVias(long predioId, Medida tolerancia) {
            recorridos.add(predioId);
            return cortes.getOrDefault(predioId, List.of());
        }

        @Override
        public Optional<Medida> proponer(FrentePropuesto propuesto, Observacion observacion) {
            if (todoYaEstaba) {
                return Optional.empty();
            }
            // La longitud vuelve REDONDEADA A LA ESCALA DE LA COLUMNA, que es lo que el motor
            // hace de verdad: el corte midio 18,4999 y lo que queda escrito es 18,50.
            return Optional.of(Medida.enMetrosLineales("18.50"));
        }

        @Override
        public void anotarDerivacion(DerivacionDeFrentes derivacion) {
            anotadas.add(derivacion);
        }

        @Override
        public FrenteDelPredio confirmar(
                long frenteId, Medida longitud, Observacion observacion, Instant cuando) {
            throw new UnsupportedOperationException("Esta prueba no confirma nada");
        }
    }

    /** La auditoria, doblada: lo que interesa es QUE se anota, no donde. */
    private static final class AuditoriaDeMentira implements Auditoria {

        private final List<String> descripciones = new ArrayList<>();

        @Override
        public void registrar(RegistroDeAuditoria registro) {
            descripciones.add(String.valueOf(registro.datosNuevos()));
        }
    }
}
