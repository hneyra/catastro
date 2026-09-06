package kamayuk.catastro.nucleo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.nucleo.dominio.DerivacionDeFrentes;
import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import kamayuk.catastro.nucleo.dominio.FrentePropuesto;
import kamayuk.catastro.nucleo.dominio.FrentesDelPredio;
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
                .allMatch(descripcion -> descripcion.contains("PROPUESTO"))
                .allMatch(descripcion -> descripcion.contains("ML"))
                .allMatch(descripcion -> descripcion.contains("no confirmado"));
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
