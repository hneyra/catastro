package kamayuk.catastro.urbano.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.io.StringReader;
import java.time.LocalDate;
import kamayuk.catastro.carga.InformeDeImportacion;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.urbano.dominio.Zona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #4 (AC-6) — el importador del plan: una fila es una zona y sus parametros, y rechaza por fila.
 */
@DisplayName("#4 — El importador del plan de zonificacion")
class ImportarZonificacionTest {

    private static final String ENCABEZADO =
            "plan,ordenanza,codigo,nombre,vigenciaDesde,vigenciaHasta,geometria,parametros\n";

    private static final String POLIGONO =
            "\"MULTIPOLYGON(((-80.69 -5.27,-80.67 -5.27,-80.67 -5.25,-80.69 -5.25,"
                    + "-80.69 -5.27)))\"";

    private UrbanoEnMemoria urbano;
    private ImportarZonificacion importar;
    private Observacion observacion;

    @BeforeEach
    void preparar() {
        urbano = new UrbanoEnMemoria();
        importar = new ImportarZonificacion(new RegistrarZonificacion(urbano));
        observacion = Observacion.de("Carga del plan de la prueba de #4");
    }

    @Test
    @DisplayName("una fila entera se convierte en zona con sus parametros")
    void unaFilaEntera() {
        InformeDeImportacion informe =
                importar.importar(
                        leer(
                                "PDU-2026,ORD-004,RDM,Residencial de densidad media,"
                                        + "2026-01-01,,"
                                        + POLIGONO
                                        + ",altura_maxima=5:pisos,retiro_frontal=segun frente\n"),
                        observacion);

        assertThat(informe.rechazadas()).isEmpty();
        assertThat(informe.nuevas()).isEqualTo(1);

        Zona zona = urbano.zonasGuardadas().get(0);
        assertThat(zona.codigo()).isEqualTo("RDM");
        assertThat(zona.vigenciaDesde()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(zona.vigenciaHasta()).isNull();
        assertThat(urbano.parametrosDe(1L))
                .extracting("clave", "valor", "unidad")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("altura_maxima", "5", "pisos"),
                        org.assertj.core.groups.Tuple.tuple(
                                "retiro_frontal", "segun frente", null));
    }

    @Test
    @DisplayName("la zona y sus parametros llevan LA MISMA observacion: es un acto, no dos")
    void laMismaObservacion() {
        importar.importar(
                leer("PDU-2026,ORD-004,RDM,Residencial,2026-01-01,," + POLIGONO + ",a=1\n"),
                observacion);

        assertThat(urbano.observaciones())
                .as("regla 10 aplicada a un acto compuesto, como InscribirFicha con predio+ficha")
                .containsExactly(observacion, observacion);
    }

    @Test
    @DisplayName("una fila mala se rechaza SOLA y las siguientes entran (#247 §2)")
    void rechazoPorFila() {
        InformeDeImportacion informe =
                importar.importar(
                        leer(
                                "PDU-2026,ORD-004,RDM,Residencial,NO-ES-FECHA,,"
                                        + POLIGONO
                                        + ",a=1\n"
                                        + "PDU-2026,ORD-004,CZ,Comercio zonal,2026-01-01,,"
                                        + POLIGONO
                                        + ",b=2\n"),
                        observacion);

        assertThat(informe.totalFilas()).isEqualTo(2);
        assertThat(informe.nuevas())
                .as("la fila que sigue a la mala ENTRA: el bucle no lleva @Transactional")
                .isEqualTo(1);
        assertThat(informe.rechazadas()).hasSize(1);
        assertThat(informe.rechazadas().get(0).motivo()).contains("vigenciaDesde");
        assertThat(informe.rechazadas().get(0).fila())
                .as("el numero de linea real del archivo, con el encabezado contado")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("un parametro sin la forma «clave=valor» rechaza la fila y lo dice")
    void parametroMalFormado() {
        InformeDeImportacion informe =
                importar.importar(
                        leer(
                                "PDU-2026,ORD-004,RDM,Residencial,2026-01-01,,"
                                        + POLIGONO
                                        + ",altura maxima 5\n"),
                        observacion);

        assertThat(informe.rechazadas()).hasSize(1);
        assertThat(informe.rechazadas().get(0).motivo()).contains("altura maxima 5");
        assertThat(urbano.zonasGuardadas()).isEmpty();
    }

    @Test
    @DisplayName(
            "repetir la carga no duplica el plan: la zona que ya esta se devuelve, no se escribe")
    void repetirNoDuplica() {
        String fila = "PDU-2026,ORD-004,RDM,Residencial,2026-01-01,," + POLIGONO + ",a=1\n";

        importar.importar(leer(fila), observacion);
        InformeDeImportacion segunda = importar.importar(leer(fila), observacion);

        assertThat(segunda.nuevas()).isZero();
        assertThat(segunda.rechazadas())
                .as("no es un rechazo: la fila ya esta cargada y volver a correr es lo corriente")
                .isEmpty();
        assertThat(urbano.zonasGuardadas()).hasSize(1);
    }

    @Test
    @DisplayName("una fila corta dice cuantas columnas faltan y cuales son")
    void filaCorta() {
        InformeDeImportacion informe =
                importar.importar(leer("PDU-2026,ORD-004,RDM\n"), observacion);

        assertThat(informe.rechazadas()).hasSize(1);
        assertThat(informe.rechazadas().get(0).motivo())
                .contains("3 columna(s)")
                .contains("vigenciaDesde")
                .contains("geometria");
    }

    @Test
    @DisplayName("una zona sin poligono se rechaza: no cubre ningun suelo y no decide nada")
    void sinPoligono() {
        InformeDeImportacion informe =
                importar.importar(
                        leer("PDU-2026,ORD-004,RDM,Residencial,2026-01-01,,,a=1\n"), observacion);

        assertThat(informe.rechazadas()).hasSize(1);
        assertThat(informe.rechazadas().get(0).motivo()).contains("no cubre ningun suelo");
    }

    private static Reader leer(String filas) {
        return new StringReader(ENCABEZADO + filas);
    }
}
