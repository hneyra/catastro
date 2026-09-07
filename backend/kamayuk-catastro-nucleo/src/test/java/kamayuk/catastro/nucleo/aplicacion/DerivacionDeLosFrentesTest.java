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
    @DisplayName("una corrida con tope cero no es una corrida")
    void unaCorridaConTopeCeroSeRechaza() {
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

    /** El repositorio, doblado. */
    private static final class FrentesDeMentira implements FrentesDelPredio {

        private final List<Long> predios = new ArrayList<>();
        private final java.util.Map<Long, List<FrentePropuesto>> cortes = new java.util.HashMap<>();
        private final List<DerivacionDeFrentes> anotadas = new ArrayList<>();
        private boolean todoYaEstaba;

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
        public List<Long> prediosPorDerivar(long desde, int tope) {
            return List.copyOf(predios);
        }

        @Override
        public List<FrentePropuesto> cortarContraLasVias(long predioId, Medida tolerancia) {
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
