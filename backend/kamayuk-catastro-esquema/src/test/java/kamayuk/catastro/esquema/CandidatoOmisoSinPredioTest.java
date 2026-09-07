package kamayuk.catastro.esquema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #27 (AC-5) — un candidato {@code OMISO_CATASTRAL} no puede apuntar a un predio.
 *
 * <h2>Que faltaba, y por que no se veia</h2>
 *
 * <p>{@code V9} ato UNA direccion: {@code candidato_predio_de_la_clase_check} dice «un {@code
 * SUBVALUADOR} exige predio» y calla la otra mitad. Su gemelo {@code hallazgo_contraste_check} ata
 * las dos, y la cabecera de {@code V9} escribio por que:
 *
 * <blockquote>
 * «Dejarlo "opcional" permitiria escribir las dos a la vez y nadie sabria cual de las dos
 * afirmaciones es la del inspector.»
 * </blockquote>
 *
 * <p>El mismo argumento vale para {@code candidato} y no se aplico. Un omiso catastral es, por
 * definicion, techo en la ortofoto <b>sin</b> fila de {@code predio}: si apunta a uno, o no es
 * omiso, o el predio que nombra es de otro. Medido contra el esquema de {@code V11} antes de
 * escribir {@code V13}: la fila entraba y devolvia {@code 4001 | OMISO_CATASTRAL | 1}.
 *
 * <h2>La mitad que importa son los contrastes</h2>
 *
 * <p>Una restriccion que rechaza es facil; la que ademas deja pasar lo legitimo es la que hay que
 * probar. Aqui son dos, y sin ellas un {@code CHECK} que rechazara <i>todo</i> {@code candidato}
 * pasaria la primera prueba: el omiso SIN predio —que es el caso normal, y la razon de que {@code
 * predio_id} sea nulable— y el subvaluador CON predio, que la otra mitad exige.
 */
@DisplayName("#27 — Un OMISO_CATASTRAL con predio es una contradiccion escrita en una fila (V13)")
class CandidatoOmisoSinPredioTest {

    private static final AtomicInteger CORRELATIVO = new AtomicInteger();

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static long campania;
    private static long predio;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "200604", "Municipalidad de #27");
        try (Connection app = conexionConContexto()) {
            campania = abrirCampania(app);
            predio = crearPredio(app);
            app.commit();
        }
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    @DisplayName("un OMISO_CATASTRAL con predio se rechaza, y el mensaje nombra la restriccion")
    void unOmisoConPredioSeRechaza() throws SQLException {
        try (Connection app = conexionConContexto()) {
            assertThatThrownBy(() -> insertar(app, "OMISO_CATASTRAL", predio))
                    .as(
                            "hasta V13 entraba, y entonces la fila afirmaba a la vez que no hay"
                                    + " predio y cual es")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("candidato_omiso_sin_predio_ck");
        }
    }

    @Test
    @DisplayName("EL CONTRASTE: un OMISO_CATASTRAL SIN predio entra, que es el caso normal")
    void unOmisoSinPredioEntra() throws SQLException {
        try (Connection app = conexionConContexto()) {
            assertThatCode(
                            () -> {
                                insertar(app, "OMISO_CATASTRAL", null);
                                app.commit();
                            })
                    .as(
                            "es la razon de que predio_id sea nulable: no hay a que apuntar. Sin"
                                    + " este caso, un CHECK que rechazara todo candidato pasaria la"
                                    + " prueba de arriba")
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("EL CONTRASTE por el otro lado: un SUBVALUADOR CON predio sigue entrando")
    void unSubvaluadorConPredioEntra() throws SQLException {
        try (Connection app = conexionConContexto()) {
            assertThatCode(
                            () -> {
                                insertar(app, "SUBVALUADOR", predio);
                                app.commit();
                            })
                    .as(
                            "la otra mitad, la de V9, no se toca: sin predio no hay ficha que"
                                    + " contrastar")
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("la restriccion de V9 sigue viva: un SUBVALUADOR sin predio se rechaza")
    void unSubvaluadorSinPredioSeRechaza() throws SQLException {
        try (Connection app = conexionConContexto()) {
            assertThatThrownBy(() -> insertar(app, "SUBVALUADOR", null))
                    .as("V13 anade una direccion; no sustituye la que V9 ya ataba")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("candidato_predio_de_la_clase_check");
        }
    }

    @Test
    @DisplayName("y la restriccion quedo VALIDA, no NOT VALID: el motor ya recorrio las filas")
    void laRestriccionQuedoValidada() throws SQLException {
        // El cuarto hallazgo de RLS es de las FORANEAS —validar una foranea ejecuta una CONSULTA
        // contra la tabla referenciada y el migrador corre sin `app.municipalidad_id`, asi que la
        // politica la deja sin ver ni una fila—. Validar un CHECK es un recorrido del monton que
        // hace el motor, no una consulta sujeta a la politica, y por eso este puede ir VALIDO.
        //
        // Que salga `t` no es lo mismo que decir que el recorrido vio algo: eso se midio aparte,
        // sembrando 4 000 candidatos en dos municipalidades y una fila que lo viola, y el mismo
        // ALTER TABLE —sin contexto de tenant— murio con «is violated by some row». La cifra esta
        // en la cabecera de V13; aqui se fija lo que un NOT VALID por descuido cambiaria.
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER);
                PreparedStatement consulta =
                        owner.prepareStatement(
                                "SELECT convalidated FROM pg_constraint"
                                        + " WHERE conname = 'candidato_omiso_sin_predio_ck'")) {
            try (ResultSet fila = consulta.executeQuery()) {
                assertThat(fila.next()).as("la restriccion de V13 tiene que existir").isTrue();
                assertThat(fila.getBoolean("convalidated"))
                        .as(
                                "NOT VALID dejaria pasar las filas que ya estuvieran, y esta"
                                        + " restriccion existe justamente porque hasta V13 podian"
                                        + " estar")
                        .isTrue();
            }
        }
    }

    // ------------------------------------------------------------------

    private static Connection conexionConContexto() throws SQLException {
        Connection app = base.conexion(BaseDeDatosDePrueba.APP);
        ContextoDeTenant.fijar(app, municipalidad);
        return app;
    }

    private static void insertar(Connection app, String clase, Long predioId) throws SQLException {
        try (PreparedStatement alta =
                app.prepareStatement(
                        "INSERT INTO candidato (municipalidad_id, campania_id, predio_id, clase,"
                                + " origen, score, insumos, observacion, usuario_registro)"
                                + " VALUES (?, ?, ?, ?, 'ORTOFOTO', 0.9000, '{}'::jsonb,"
                                + "         'candidato de la prueba de #27', 'pruebas')")) {
            alta.setLong(1, municipalidad);
            alta.setLong(2, campania);
            if (predioId == null) {
                alta.setNull(3, java.sql.Types.BIGINT);
            } else {
                alta.setLong(3, predioId);
            }
            alta.setString(4, clase);
            alta.executeUpdate();
        }
    }

    private static long abrirCampania(Connection app) throws SQLException {
        try (PreparedStatement alta =
                app.prepareStatement(
                        // El `tope` es NOT NULL desde la `V12` de #25: quien abre una campania
                        // tiene que DECIRLO, porque un valor por omision recorta el universo del
                        // cruce sin que nadie lo haya elegido. Esta prueba no mide el tope, asi
                        // que lo declara y sigue a lo suyo.
                        "INSERT INTO campania (municipalidad_id, codigo, nombre, inicio, umbral,"
                                + " tope, observacion, usuario_registro)"
                                + " VALUES (?, ?, 'Campania de #27', ?, 0.7000, 500,"
                                + "         'campania de la prueba', 'pruebas') RETURNING id")) {
            alta.setLong(1, municipalidad);
            alta.setString(2, "C27-" + CORRELATIVO.incrementAndGet());
            alta.setDate(3, Date.valueOf("2026-01-01"));
            try (ResultSet fila = alta.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }

    private static long crearPredio(Connection app) throws SQLException {
        try (PreparedStatement alta =
                app.prepareStatement(
                        "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo,"
                                + " direccion) VALUES (?, ?, 'URBANO',"
                                + " 'Calle de la prueba de #27') RETURNING id")) {
            alta.setLong(1, municipalidad);
            alta.setString(2, "27" + "0".repeat(21));
            try (ResultSet fila = alta.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }
}
