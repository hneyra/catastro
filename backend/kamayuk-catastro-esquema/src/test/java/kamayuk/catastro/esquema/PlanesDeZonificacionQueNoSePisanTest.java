package kamayuk.catastro.esquema;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #4 (AC-2) y #22 — dos zonas vigentes no pueden cubrir el mismo suelo, ni de dos planes distintos
 * (V7) ni del mismo (V11).
 *
 * <h2>Son DOS restricciones y hacen falta las dos</h2>
 *
 * <p>{@code zonificacion_planes_no_se_pisan} lleva {@code plan WITH <>}, y una restriccion de
 * exclusion conflictua cuando <b>todos</b> sus operadores se satisfacen: dos filas del MISMO plan
 * no chocan nunca con ella, se solapen como se solapen. Eso era lo correcto para lo que #4 tenia
 * que impedir y dejaba abierto el hueco de dentro de un plan, que cierra {@code
 * zonas_del_plan_no_se_pisan} (V11) — y no como exclusion, porque «los interiores se cortan» no es
 * un operador indexable por GiST y {@code &&} rechazaria ademas las zonas adyacentes. El porque,
 * con sus tres medidas, en la cabecera de V11.
 *
 * <h2>Lo rechaza el motor, no el codigo</h2>
 *
 * <p>{@code zonificacion_planes_no_se_pisan} es una restriccion de exclusion, y por eso vale: una
 * comprobacion escrita en Java se salta con el {@code INSERT} de un cargador, y este dato entra
 * justamente por un cargador. Si dos planes se pisaran, «la zona de este predio» tendria dos
 * respuestas y la licencia se concederia o se negaria segun cual leyera la consulta.
 *
 * <h2>La mitad que importa son los contrastes</h2>
 *
 * <p>Una restriccion que rechaza el solape es facil; la que ademas deja pasar lo legitimo es la que
 * hay que probar. Aqui son tres, y el primero es el que decidio la forma de la restriccion:
 *
 * <ul>
 *   <li><b>Dos zonas ADYACENTES del mismo plan</b> —que es como se dibuja un plan: el distrito
 *       entero, partido—. Sus cajas envolventes se tocan, y {@code &&} sobre {@code geography}
 *       compara cajas: sin {@code plan WITH <>} en la llave, la segunda zona de la primera manzana
 *       ya se rechazaria y <b>no se podria cargar ningun plan</b>. Medido antes de escribir {@code
 *       V7}, con la forma ingenua: «conflicting key value violates exclusion constraint».
 *   <li><b>El plan que SUCEDE al anterior</b>, cerrado la vispera.
 *   <li><b>El relevo dentro de una transaccion</b>, que atraviesa un estado intermedio solapado a
 *       proposito: sin {@code DEFERRABLE} la primera de las dos sentencias fallaria y sustituir un
 *       plan por otro seria imposible.
 * </ul>
 */
@DisplayName("#4 — Dos planes de zonificacion vigentes no se pisan (V7)")
class PlanesDeZonificacionQueNoSePisanTest {

    private static final Date DESDE_2016 = Date.valueOf("2016-03-01");
    private static final Date DESDE_2026 = Date.valueOf("2026-01-01");
    private static final Date LA_VISPERA = Date.valueOf("2025-12-31");

    private static final AtomicInteger CORRELATIVO = new AtomicInteger();

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "200603", "Municipalidad de #4");
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    @DisplayName("un segundo plan vigente sobre el mismo suelo se rechaza")
    void unSegundoPlanSobreElMismoSueloSeRechaza() throws SQLException {
        try (Connection app = conexionConContexto()) {
            Escenario e = escenario();
            double x = e.oeste();
            insertar(app, "PDU-2016" + e.sufijo(), "RDB", x, x + 0.02, DESDE_2016, null);
            insertar(app, "PDU-2026" + e.sufijo(), "RDA", x + 0.005, x + 0.015, DESDE_2026, null);

            assertThatThrownBy(app::commit)
                    .as(
                            "con los dos vivos, «la zona de este predio» tiene dos respuestas y la"
                                    + " licencia se concede o se niega segun cual lea la consulta")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("zonificacion_planes_no_se_pisan");
        }
    }

    @Test
    @DisplayName("EL CONTRASTE que decidio la forma: dos zonas ADYACENTES del mismo plan entran")
    void dosZonasAdyacentesDelMismoPlanEntran() throws SQLException {
        try (Connection app = conexionConContexto()) {
            Escenario e = escenario();
            double x = e.oeste();
            insertar(app, "PDU-2026" + e.sufijo(), "RDM", x, x + 0.01, DESDE_2026, null);
            insertar(app, "PDU-2026" + e.sufijo(), "CZ", x + 0.01, x + 0.02, DESDE_2026, null);

            assertThatCode(app::commit)
                    .as(
                            "solo comparten la arista, y aun asi sus CAJAS se tocan: sin «plan WITH"
                                    + " <>» esto se rechazaria y no se podria cargar ningun plan")
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("EL CONTRASTE: el plan que sucede al anterior, cerrado la vispera, entra")
    void elPlanQueSucedeAlAnteriorEntra() throws SQLException {
        try (Connection app = conexionConContexto()) {
            Escenario e = escenario();
            double x = e.oeste();
            insertar(app, "PDU-2016" + e.sufijo(), "RDB", x, x + 0.02, DESDE_2016, LA_VISPERA);
            insertar(app, "PDU-2026" + e.sufijo(), "RDA", x, x + 0.02, DESDE_2026, null);

            assertThatCode(app::commit)
                    .as("es el relevo normal de un plan por otro")
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("y el relevo dentro de una transaccion atraviesa un estado solapado (DEFERRABLE)")
    void elRelevoAtraviesaUnEstadoSolapado() throws SQLException {
        try (Connection app = conexionConContexto()) {
            Escenario e = escenario();
            double x = e.oeste();
            insertar(app, "PDU-2016" + e.sufijo(), "RDB", x, x + 0.02, DESDE_2016, null);
            app.commit();

            // El contexto se fija con SET LOCAL y muere con la transaccion (regla 3).
            ContextoDeTenant.fijar(app, municipalidad);
            // Se abre el plan nuevo ANTES de cerrar el viejo: entre las dos sentencias los dos
            // cubren 2026. Sin el diferimiento, la primera ya habria fallado y relevar un plan
            // seria imposible — la misma leccion que `ficha_vigencias_no_se_pisan`.
            insertar(app, "PDU-2026" + e.sufijo(), "RDA", x, x + 0.02, DESDE_2026, null);
            try (PreparedStatement cierre =
                    app.prepareStatement(
                            "UPDATE zonificacion SET vigencia_hasta = ?"
                                    + " WHERE municipalidad_id = ? AND plan = ?"
                                    + "   AND vigencia_desde = ?")) {
                cierre.setDate(1, LA_VISPERA);
                cierre.setLong(2, municipalidad);
                cierre.setString(3, "PDU-2016" + e.sufijo());
                cierre.setDate(4, DESDE_2016);
                cierre.executeUpdate();
            }

            assertThatCode(app::commit).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("#22 — dos zonas del MISMO plan que se pisan de verdad se rechazan (V11)")
    void dosZonasDelMismoPlanSolapadasSeRechazan() throws SQLException {
        try (Connection app = conexionConContexto()) {
            Escenario e = escenario();
            double x = e.oeste();
            insertar(app, "PDU-2026" + e.sufijo(), "RDM", x, x + 0.02, DESDE_2026, null);
            // Dentro de la anterior, no pegada a ella: los INTERIORES se cortan.
            insertar(app, "PDU-2026" + e.sufijo(), "CZ", x + 0.005, x + 0.015, DESDE_2026, null);

            assertThatThrownBy(app::commit)
                    .as(
                            "es el hueco que «plan WITH <>» deja abierto a proposito: impide que"
                                    + " dos PLANES se pisen y no mira lo que pasa DENTRO de uno. Hasta"
                                    + " V11 estas dos entraban, y «la zona de este predio» pasaba a"
                                    + " tener dos respuestas")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("zonas_del_plan_no_se_pisan");
        }
    }

    @Test
    @DisplayName("#22 — y una zona CONTENIDA del todo en otra del mismo plan, tambien")
    void unaZonaContenidaEnOtraDelMismoPlanSeRechaza() throws SQLException {
        // El contraste por arriba del anterior: `ST_Relate(...,'T********')` es cierto tanto si
        // los poligonos se cruzan como si uno esta dentro del otro, y las dos son el mismo
        // defecto —suelo cubierto dos veces—. Con un predicado de «se cruzan» estricto
        // (ST_Overlaps) la contenida se colaria, que es el caso mas facil de dibujar a mano.
        try (Connection app = conexionConContexto()) {
            Escenario e = escenario();
            double x = e.oeste();
            insertar(app, "PDU-2026" + e.sufijo(), "RDM", x, x + 0.04, DESDE_2026, null);
            insertar(app, "PDU-2026" + e.sufijo(), "CZ", x + 0.01, x + 0.02, DESDE_2026, null);

            assertThatThrownBy(app::commit)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("zonas_del_plan_no_se_pisan");
        }
    }

    @Test
    @DisplayName("#22 — el mensaje nombra LAS DOS zonas y su plan, no «una restriccion»")
    void elRechazoNombraLasDosZonas() throws SQLException {
        // Quien carga un plan recibe esto sobre un archivo de cientos de filas: sin los dos
        // codigos dentro no hay forma de saber que dos lineas corregir.
        try (Connection app = conexionConContexto()) {
            Escenario e = escenario();
            double x = e.oeste();
            insertar(app, "PDU-2026" + e.sufijo(), "RDM", x, x + 0.02, DESDE_2026, null);
            insertar(app, "PDU-2026" + e.sufijo(), "CZ", x + 0.005, x + 0.015, DESDE_2026, null);

            assertThatThrownBy(app::commit)
                    .hasMessageContaining("RDM")
                    .hasMessageContaining("CZ")
                    .hasMessageContaining("PDU-2026" + e.sufijo());
        }
    }

    @Test
    @DisplayName(
            "la misma zona del mismo plan dos veces choca con su indice unico, no con la exclusion")
    void laMismaZonaDosVeces() throws SQLException {
        try (Connection app = conexionConContexto()) {
            Escenario e = escenario();
            double x = e.oeste();
            insertar(app, "PDU-2026" + e.sufijo(), "RDM", x, x + 0.02, DESDE_2026, null);

            // EN EL INSERT, no en el commit, y esa es la diferencia que esta prueba fija: el
            // indice unico NO es diferible y la restriccion de exclusion SI. Que el relevo de un
            // plan pueda atravesar un estado solapado (la prueba de arriba) y que una zona
            // repetida se pare en el acto son la misma decision vista por sus dos caras.
            assertThatThrownBy(
                            () ->
                                    insertar(
                                            app,
                                            "PDU-2026" + e.sufijo(),
                                            "RDM",
                                            x,
                                            x + 0.02,
                                            DESDE_2026,
                                            null))
                    .as(
                            "reimportar el archivo no puede duplicar la zona; el mensaje nombra el"
                                    + " indice y no la exclusion, que es lo que distingue «esta"
                                    + " repetida» de «se pisa con otro plan»")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("zonificacion_codigo_uq");
        }
    }

    // ------------------------------------------------------------------

    private static Connection conexionConContexto() throws SQLException {
        Connection app = base.conexion(BaseDeDatosDePrueba.APP);
        ContextoDeTenant.fijar(app, municipalidad);
        return app;
    }

    /**
     * Un escenario propio para cada prueba: su grado de longitud y su sufijo de plan.
     *
     * <p><b>Las dos cosas, y lo enseno ejecutar.</b> Las pruebas comparten la base y la restriccion
     * es sobre el SUELO, asi que dibujar todas sobre el mismo rectangulo las hace estorbarse entre
     * si. Pero con solo el suelo propio, tres de las cinco cayeron con {@code duplicate key value
     * violates unique constraint "zonificacion_codigo_uq"}: la unicidad es {@code
     * (municipalidad_id, plan, codigo, vigencia_desde)} y no mira la geometria, asi que dos pruebas
     * que usen «PDU-2016 / RDB / 2016-03-01» chocan aunque dibujen a mil kilometros. El rojo
     * hablaba de la prueba anterior, que es justo lo que un escenario propio evita.
     */
    private static Escenario escenario() {
        int n = CORRELATIVO.incrementAndGet();
        return new Escenario(-70.0 - n, "-" + n);
    }

    /** El suelo sobre el que dibuja una prueba, y el sufijo con que nombra sus planes. */
    private record Escenario(double oeste, String sufijo) {}

    private static void insertar(
            Connection app,
            String plan,
            String codigo,
            double oeste,
            double este,
            Date desde,
            Date hasta)
            throws SQLException {
        try (PreparedStatement sentencia =
                app.prepareStatement(
                        "INSERT INTO zonificacion (municipalidad_id, plan, ordenanza, codigo,"
                                + " nombre, geometria, vigencia_desde, vigencia_hasta,"
                                + " observacion, usuario_registro)"
                                + " VALUES (?, ?, ?, ?, ?,"
                                + "  ST_GeogFromText('SRID=4326;MULTIPOLYGON(((' || ? || ' -4.91,'"
                                + "   || ? || ' -4.91,' || ? || ' -4.89,' || ? || ' -4.89,'"
                                + "   || ? || ' -4.91)))'),"
                                + "  ?, ?, 'zona de la prueba de #4', 'prueba')")) {
            sentencia.setLong(1, municipalidad);
            sentencia.setString(2, plan);
            sentencia.setString(3, "ORD-" + plan);
            sentencia.setString(4, codigo);
            sentencia.setString(5, "Zona " + codigo);
            sentencia.setString(6, oeste + " ");
            sentencia.setString(7, este + " ");
            sentencia.setString(8, este + " ");
            sentencia.setString(9, oeste + " ");
            sentencia.setString(10, oeste + " ");
            sentencia.setDate(11, desde);
            sentencia.setDate(12, hasta);
            sentencia.executeUpdate();
        }
    }
}
