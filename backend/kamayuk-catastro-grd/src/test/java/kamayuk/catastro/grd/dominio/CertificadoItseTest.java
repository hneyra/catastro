package kamayuk.catastro.grd.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import kamayuk.catastro.dominio.Observacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La vigencia de un certificado, como funcion pura (#5, AC-4).
 *
 * <p>Sin Spring, sin base de datos y sin reloj (reglas 6 y 7): la fecha entra como argumento. Es la
 * otra mitad de lo que {@code GestionDeRiesgoFronteraTest} mide en el {@code WHERE} — <b>las dos
 * hacen falta</b>, porque el {@code WHERE} y este metodo son dos escrituras del mismo criterio y
 * nada las compara. Si divergieran, la lectura y el dominio dirian cosas distintas del mismo
 * certificado.
 */
@DisplayName("#5 — Un certificado ITSE esta vigente A UNA FECHA, o no lo esta")
class CertificadoItseTest {

    private static final Observacion OBSERVACION = Observacion.de("Inspeccion del 2026-01-02");

    @Test
    @DisplayName("los dos extremos entran, y el dia siguiente ya no")
    void losDosExtremosEntran() {
        CertificadoItse certificado = vigenteDel("2026-01-01", "2026-12-31");

        assertThat(certificado.vigenteA(LocalDate.parse("2026-01-01"))).isTrue();
        assertThat(certificado.vigenteA(LocalDate.parse("2026-12-31")))
                .as("«hasta el 31 de diciembre» incluye el 31 de diciembre")
                .isTrue();
        assertThat(certificado.vigenteA(LocalDate.parse("2025-12-31"))).isFalse();
        assertThat(certificado.vigenteA(LocalDate.parse("2027-01-01"))).isFalse();
    }

    @Test
    @DisplayName("una anulacion vale desde su fecha y NO hacia atras")
    void laAnulacionNoValeHaciaAtras() {
        CertificadoItse anulado =
                new CertificadoItse(
                        1L,
                        7L,
                        "ITSE-2026-009",
                        NivelDeRiesgo.ALTO,
                        ModalidadItse.PREVIA,
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-12-31"),
                        LocalDate.parse("2026-05-01"),
                        "Se detecto observacion no levantada",
                        OBSERVACION);

        assertThat(anulado.vigenteA(LocalDate.parse("2026-03-01")))
                .as(
                        "una licencia emitida en marzo se emitio con un certificado que en marzo"
                                + " estaba vigente; decir hoy que no lo estaba dejaria al sistema"
                                + " sin poder explicar sus propios actos (regla 9)")
                .isTrue();
        assertThat(anulado.vigenteA(LocalDate.parse("2026-05-01")))
                .as("el dia de la anulacion ya no")
                .isFalse();
        assertThat(anulado.vigenteA(LocalDate.parse("2026-06-15"))).isFalse();
    }

    @Test
    @DisplayName("un certificado sin fecha de vencimiento no se puede construir")
    void sinVencimientoNoSeConstruye() {
        assertThatThrownBy(
                        () ->
                                new CertificadoItse(
                                        null,
                                        7L,
                                        "ITSE-ETERNO",
                                        NivelDeRiesgo.BAJO,
                                        ModalidadItse.POSTERIOR,
                                        LocalDate.parse("2026-01-01"),
                                        null,
                                        null,
                                        null,
                                        OBSERVACION))
                .as("un certificado eterno no existe, y dejar la fecha vacia lo crearia")
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("siempre vence");
    }

    @Test
    @DisplayName("una anulacion sin motivo no se puede construir: no seria un acto")
    void laAnulacionSinMotivoNoSeConstruye() {
        assertThatThrownBy(
                        () ->
                                new CertificadoItse(
                                        null,
                                        7L,
                                        "ITSE-2026-010",
                                        NivelDeRiesgo.BAJO,
                                        ModalidadItse.POSTERIOR,
                                        LocalDate.parse("2026-01-01"),
                                        LocalDate.parse("2026-12-31"),
                                        LocalDate.parse("2026-05-01"),
                                        null,
                                        OBSERVACION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es un acto");
    }

    @Test
    @DisplayName("«vigente» a secas no se puede preguntar: la fecha es obligatoria")
    void vigenteASecasNoSePuedePreguntar() {
        assertThatThrownBy(() -> vigenteDel("2026-01-01", "2026-12-31").vigenteA(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("es «vigente a una fecha»");
    }

    @Test
    @DisplayName("el vocabulario de los cuatro niveles es el mismo que el de rentas")
    void elVocabularioEsElDeRentas() {
        // Los mismos cuatro nombres de `kamayuk.rentas.licencias.dominio.RiesgoItse` y de la
        // columna `ciiu.riesgo_itse`: alli se registra el nivel que un GIRO exige y aqui el que un
        // CERTIFICADO acredita. Con dos vocabularios habria que traducir, y una traduccion entre
        // dos listas de cuatro valores se escribe mal una vez y no la ve nadie.
        assertThat(NivelDeRiesgo.values())
                .extracting(Enum::name)
                .containsExactly("BAJO", "MEDIO", "ALTO", "MUY_ALTO");
        assertThat(NivelDeRiesgo.porNombre("muy alto"))
                .as("«MUY ALTO» con espacio es como lo escribe una carta de peligro")
                .isEqualTo(NivelDeRiesgo.MUY_ALTO);
        assertThat(NivelDeRiesgo.MUY_ALTO.esMasGraveQue(NivelDeRiesgo.MEDIO))
                .as(
                        "por orden y no por nombre: alfabeticamente «MEDIO» < «MUY_ALTO» pero"
                                + " «ALTO» < «BAJO», que es la respuesta equivocada")
                .isTrue();
        assertThat(NivelDeRiesgo.ALTO.esMasGraveQue(NivelDeRiesgo.BAJO)).isTrue();
    }

    private static CertificadoItse vigenteDel(String desde, String hasta) {
        return new CertificadoItse(
                1L,
                7L,
                "ITSE-2026-001",
                NivelDeRiesgo.ALTO,
                ModalidadItse.PREVIA,
                LocalDate.parse(desde),
                LocalDate.parse(hasta),
                null,
                null,
                OBSERVACION);
    }
}
