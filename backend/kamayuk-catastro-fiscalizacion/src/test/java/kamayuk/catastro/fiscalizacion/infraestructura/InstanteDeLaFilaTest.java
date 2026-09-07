package kamayuk.catastro.fiscalizacion.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-4 de #24: <b>un instante que falta se dice, no se inventa</b>.
 *
 * <h2>Por que hace falta una prueba aparte, y por que va contra PostgreSQL</h2>
 *
 * <p>Las cinco columnas de instante de {@code V9} son {@code NOT NULL}, asi que <b>ninguna consulta
 * de {@code FiscalizacionRepositoryJdbc} puede traer el nulo</b>: por el camino normal esta
 * decision no se puede ejercer, y por eso hasta #24 nadie la vio devolver {@link Instant#EPOCH}. Un
 * {@code capturado_en} de 1970-01-01 en una evidencia es <b>plausible y falso</b>, y una evidencia
 * existe para poder auditarse.
 *
 * <p>La fila con el nulo la produce el motor —{@code SELECT NULL::timestamptz}— y no un doble: lo
 * que se mide es como se comporta el mapeador ante un {@code ResultSet} de verdad. Un {@code
 * ResultSet} de mentira mediria como se comporta ante la imitacion.
 *
 * <p><b>Y las dos direcciones</b>: sin el caso que devuelve el instante, un metodo que lanzara
 * SIEMPRE pasaria esta prueba igual.
 */
@DisplayName("#24 AC-4 — un instante nulo se dice, y no se convierte en 1970")
class InstanteDeLaFilaTest {

    private static BaseDeDatosDePrueba base;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
    }

    @AfterAll
    static void cerrar() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    @DisplayName("la columna nula LANZA, y el mensaje la nombra")
    void laColumnaNulaLanza() throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP);
                Statement sentencia = app.createStatement();
                ResultSet fila =
                        sentencia.executeQuery("SELECT NULL::timestamptz AS capturado_en")) {
            assertThat(fila.next()).isTrue();
            assertThatThrownBy(() -> FiscalizacionRepositoryJdbc.instanteDe(fila, "capturado_en"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("capturado_en")
                    .hasMessageContaining("plausible y falsa");
        }
    }

    @Test
    @DisplayName("y el contraste: con valor devuelve EL VALOR, no cualquier cosa")
    void conValorDevuelveElValor() throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP);
                Statement sentencia = app.createStatement();
                ResultSet fila =
                        sentencia.executeQuery(
                                "SELECT TIMESTAMPTZ '2026-09-07 10:00:00+00' AS capturado_en")) {
            assertThat(fila.next()).isTrue();
            assertThat(FiscalizacionRepositoryJdbc.instanteDe(fila, "capturado_en"))
                    .isEqualTo(Instant.parse("2026-09-07T10:00:00Z"));
        }
    }
}
