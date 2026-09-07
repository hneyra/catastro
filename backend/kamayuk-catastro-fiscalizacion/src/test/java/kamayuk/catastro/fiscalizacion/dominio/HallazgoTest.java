package kamayuk.catastro.fiscalizacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import kamayuk.catastro.dominio.AreaM2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0035 punto 2 y AC 4 de #6: lo que una persona verifico, contra QUE version. */
@DisplayName("ADR-0035 — El hallazgo lleva nombre, fecha y la version que contrasto")
class HallazgoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 5);

    @Test
    @DisplayName("un subvaluador SIN ficha_id no se puede escribir (AC 4)")
    void sinLaVersionNoHayHallazgoDeSubvaluador() {
        assertThatThrownBy(
                        () ->
                                new Hallazgo(
                                        null,
                                        1L,
                                        ClaseDeHallazgo.SUBVALUADOR,
                                        7L,
                                        null,
                                        AreaM2.de("120.00"),
                                        AreaM2.de("180.00"),
                                        "luis.campo",
                                        HOY,
                                        EstadoDelHallazgo.FIRME,
                                        null))
                .as(
                        "sin la version contrastada, un hallazgo de marzo no se puede releer en"
                                + " julio: la ficha se versiona")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no se puede releer en julio");
    }

    @Test
    @DisplayName("un omiso catastral CON predio tampoco: seria una contradiccion en una fila")
    void elOmisoNoPuedeTenerPredio() {
        assertThatThrownBy(
                        () ->
                                new Hallazgo(
                                        null,
                                        1L,
                                        ClaseDeHallazgo.OMISO_CATASTRAL,
                                        7L,
                                        null,
                                        null,
                                        AreaM2.de("90.00"),
                                        "luis.campo",
                                        HOY,
                                        EstadoDelHallazgo.FIRME,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("si lo tuviera no seria un omiso catastral");
    }

    @Test
    @DisplayName("el exceso verificado es una resta de superficies, y nunca un importe")
    void elExcesoEsUnaResta() {
        Hallazgo hallazgo =
                Hallazgo.deSubvaluador(
                        1L, 7L, 11L, AreaM2.de("120.00"), AreaM2.de("180.50"), "luis.campo", HOY);

        assertThat(hallazgo.excesoVerificado()).contains(AreaM2.de("60.50"));
        assertThat(hallazgo.fichaId()).as("y dice contra QUE version se comparo").isEqualTo(11L);
    }

    @Test
    @DisplayName("sin exceso el resultado es VACIO y no cero: cero significaria que coinciden")
    void sinExcesoNoHayCero() {
        Hallazgo iguales =
                Hallazgo.deSubvaluador(
                        1L, 7L, 11L, AreaM2.de("120.00"), AreaM2.de("120.00"), "luis", HOY);
        Hallazgo omiso = Hallazgo.deOmisoCatastral(1L, AreaM2.de("90.00"), "luis", HOY);

        assertThat(iguales.excesoVerificado()).isEmpty();
        assertThat(omiso.excesoVerificado())
                .as("un omiso no tiene area inscrita de la que diferir, porque no tiene ficha")
                .isEmpty();
    }

    @Test
    @DisplayName("dejarlo sin efecto no lo borra, guarda su ACTO, y no se puede hacer dos veces")
    void seDejaSinEfectoUnaVez() {
        Hallazgo firme =
                Hallazgo.deSubvaluador(
                        1L, 7L, 11L, AreaM2.de("120.00"), AreaM2.de("180.00"), "luis", HOY);
        java.time.Instant cuando = java.time.Instant.parse("2026-09-07T10:00:00Z");

        Hallazgo retirado =
                firme.dejadoSinEfecto("el area medida era la del vecino", "ana.jefa", cuando);

        assertThat(retirado.estado()).isEqualTo(EstadoDelHallazgo.DEJADO_SIN_EFECTO);
        assertThat(retirado.anulacion())
                .as(
                        "las tres cosas y no una: sin motivo no explica nada, sin nombre no"
                                + " contesta quien lo decidio —que es lo unico que un estado no"
                                + " dice— y sin fecha no se ordena contra el acta que ya produjo")
                .isEqualTo(
                        new Hallazgo.Anulacion(
                                "el area medida era la del vecino", "ana.jefa", cuando));
        assertThat(retirado.areaVerificada())
                .as("lo que el inspector verifico no se reescribe: se retira el hallazgo")
                .isEqualTo(firme.areaVerificada());
        assertThatThrownBy(() -> retirado.dejadoSinEfecto("otra vez", "ana.jefa", cuando))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("y el estado y el acto van atados en los DOS sentidos (#23)")
    void elEstadoYElActoVanAtados() {
        assertThatThrownBy(
                        () ->
                                new Hallazgo(
                                        1L,
                                        1L,
                                        ClaseDeHallazgo.OMISO_CATASTRAL,
                                        null,
                                        null,
                                        null,
                                        AreaM2.de("90.00"),
                                        "luis",
                                        HOY,
                                        EstadoDelHallazgo.DEJADO_SIN_EFECTO,
                                        null))
                .as("un DEJADO_SIN_EFECTO mudo no explica nada")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lleva su motivo, quien lo decidio y cuando");

        assertThatThrownBy(
                        () ->
                                new Hallazgo(
                                        1L,
                                        1L,
                                        ClaseDeHallazgo.OMISO_CATASTRAL,
                                        null,
                                        null,
                                        null,
                                        AreaM2.de("90.00"),
                                        "luis",
                                        HOY,
                                        EstadoDelHallazgo.FIRME,
                                        new Hallazgo.Anulacion(
                                                "motivo",
                                                "ana",
                                                java.time.Instant.parse("2026-09-07T10:00:00Z"))))
                .as("y un FIRME con motivo de anulacion es una contradiccion en una fila")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un hallazgo sin inspector no se puede escribir: la pregunta es «quien dijo esto»")
    void elHallazgoLlevaNombre() {
        assertThatThrownBy(() -> Hallazgo.deOmisoCatastral(1L, AreaM2.de("90.00"), "  ", HOY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inspector");
    }
}
