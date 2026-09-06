package kamayuk.catastro.fiscalizacion.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;
import kamayuk.catastro.fiscalizacion.dominio.ContrasteDeAreas;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import kamayuk.catastro.fiscalizacion.dominio.Tolerancia;
import kamayuk.catastro.plataforma.tenant.TenantTransactionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AC 8 de #6: el cruce de areas, <b>contra PostGIS de verdad</b>.
 *
 * <h2>Por que esta prueba existe, y no bastaban las otras</h2>
 *
 * <p>Se midio: con {@code AreasDelPadronJdbc.contrastar} devolviendo una lista vacia en vez de
 * lanzar {@link AreasDelPadron.SinCartografia} —o sea con el defecto que AC 8 existe para impedir,
 * aplicado sobre {@code src/main}—, <b>las 41 pruebas del modulo pasaban en VERDE</b>. Y no era un
 * descuido de una prueba: era que las dos que hablan de la cartografia usan un <b>doble</b> que
 * lanza, asi que lo que median era que el caso de uso y el borde propagan la excepcion —cierto y
 * util— y no <b>quien decide lanzarla</b>, que es esta clase.
 *
 * <p>Es el modo de fallo que este proyecto lleva doscientos issues evitando, y aqui es el peor
 * posible: su sintoma es la AUSENCIA de sintoma. La campania se cierra con cero hallazgos, la
 * conclusion es que el padron esta bien, y nadie revisa un cero.
 *
 * <h2>El contraste es la mitad que hace util la prueba</h2>
 *
 * <p>«No hay poligonos» y «los hay y ninguno difiere» son dos respuestas distintas que se arreglan
 * de maneras distintas —una cargando la cartografia y la otra no arreglandose—, asi que las dos
 * estan medidas: la primera lanza y la segunda devuelve vacio. Una prueba que solo midiera la
 * primera pasaria en verde con un metodo que lanzara SIEMPRE.
 *
 * <p>{@code ST_Area} calcula sobre el elipsoide en metros cuadrados, que es la unidad de {@code
 * area_terreno} (ADR-0021). El poligono que la prueba planta esta dimensionado para que su area sea
 * verificable a ojo contra la de la ficha.
 */
@DisplayName("AC 8 — El cruce de areas, contra PostGIS")
class ContrasteDeAreasJdbcTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);

    /** Un uno por ciento. Lo bastante fino para que un lote que difiere el 50 % salte. */
    private static final Tolerancia UNO_POR_CIENTO = Tolerancia.de("0.01");

    private static BaseDeDatosDePrueba base;
    private static long sinPlanos;
    private static long conPlanos;
    private static AreasDelPadronJdbc areas;
    private static TransactionTemplate enUnaTransaccion;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        sinPlanos = DatosDePrueba.crearMunicipalidad(base, "240401", "La que no tiene cartografia");
        conPlanos = DatosDePrueba.crearMunicipalidad(base, "240402", "La que si la tiene");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, sinPlanos, parametroId, "SP");
        DatosDePrueba.sembrarTenant(base, conPlanos, parametroId, "CP");

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        areas = new AreasDelPadronJdbc(JdbcClient.create(pool), RELOJ);
        enUnaTransaccion = new TransactionTemplate(new TenantTransactionManager(pool));
    }

    @AfterAll
    static void cerrar() {
        if (base != null) {
            base.close();
        }
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
    }

    @BeforeEach
    void sinPoligonosDePartida() throws SQLException {
        // La siembra deja los predios SIN geometria, que es el estado real de hoy en todas las
        // instalaciones: `V61` trajo la columna y nada la llena todavia.
        ejecutar(conPlanos, "UPDATE predio SET geometria = NULL");
        ejecutar(sinPlanos, "UPDATE predio SET geometria = NULL");
    }

    @Test
    @DisplayName("SIN un solo poligono LANZA, y no devuelve una lista vacia")
    void sinCartografiaLanza() {
        TenantContext.fijar(new MunicipalidadId(sinPlanos));

        assertThatThrownBy(
                        () ->
                                enUnaTransaccion.execute(
                                        estado -> areas.contrastar(UNO_POR_CIENTO, 500)))
                .as(
                        "cero subvaluadores es una AFIRMACION —«no los hay»— y aqui lo cierto es"
                                + " «no puedo mirar». Las dos se arreglan distinto y nadie revisa"
                                + " un cero")
                .isInstanceOf(AreasDelPadron.SinCartografia.class);
    }

    @Test
    @DisplayName("EL CONTRASTE: con poligonos y sin discrepancia devuelve VACIO, y no lanza")
    void conPoligonosYSinDiscrepanciaDevuelveVacio() throws SQLException {
        java.math.BigDecimal medida = plantarPoligonoEn(conPlanos);
        areaDeLaFicha(conPlanos, medida);
        TenantContext.fijar(new MunicipalidadId(conPlanos));

        List<ContrasteDeAreas> hallados =
                enUnaTransaccion.execute(estado -> areas.contrastar(UNO_POR_CIENTO, 500));

        assertThat(hallados)
                .as(
                        "esta es la otra respuesta, y es la que distingue «no puedo mirar» de «mire"
                                + " y no hay nada»: sin este caso, un metodo que lanzara SIEMPRE"
                                + " pasaria la prueba de arriba")
                .isEmpty();
    }

    @Test
    @DisplayName("con discrepancia devuelve el contraste, con su ficha y su diferencia relativa")
    void conDiscrepanciaDevuelveElContraste() throws SQLException {
        // La ficha dice el DOBLE de lo que el plano mide: la diferencia relativa es exactamente
        // 0,5000, y sale exacta porque 2 x (una cifra de dos decimales) sigue teniendo dos.
        java.math.BigDecimal medida = plantarPoligonoEn(conPlanos);
        java.math.BigDecimal inscritaElDoble = medida.multiply(new java.math.BigDecimal("2"));
        areaDeLaFicha(conPlanos, inscritaElDoble);
        TenantContext.fijar(new MunicipalidadId(conPlanos));

        List<ContrasteDeAreas> hallados =
                enUnaTransaccion.execute(estado -> areas.contrastar(UNO_POR_CIENTO, 500));

        assertThat(hallados).hasSize(1);
        ContrasteDeAreas contraste = hallados.get(0);
        assertThat(contraste.areaDeLaFicha()).isEqualTo(new AreaM2(inscritaElDoble));
        assertThat(contraste.areaDelPoligono())
                .as("ST_Area sobre `geography` da metros cuadrados, que es la unidad de la ficha")
                .isEqualTo(new AreaM2(medida));
        assertThat(contraste.diferenciaRelativa())
                .as(
                        "la calcula el CRUCE y no una segunda formula en Java: alli ya es el filtro"
                                + " y el orden")
                .isEqualTo(Score.de("0.5000"));
        assertThat(contraste.fichaId())
                .as("y trae la version desde el cruce, que es donde AC 4 tiene que empezar")
                .isPositive();
        assertThat(contraste.elPoligonoSupera())
                .as(
                        "aqui el plano mide MENOS que lo inscrito, que es lo contrario de un"
                                + " subvaluador y suele ser un error de digitacion. Salta igual"
                                + " porque la diferencia del cruce es ABSOLUTA: quedarse solo con"
                                + " el exceso dejaria esa mitad sin detectar")
                .isFalse();
        assertThat(contraste.geometria())
                .as("con el poligono, para copiarlo al candidato sin volver a pedirlo")
                .contains("POLYGON");
    }

    @Test
    @DisplayName("la tolerancia acota: el mismo predio deja de saltar si se admite mas diferencia")
    void laToleranciaAcota() throws SQLException {
        java.math.BigDecimal medida = plantarPoligonoEn(conPlanos);
        areaDeLaFicha(conPlanos, medida.multiply(new java.math.BigDecimal("2")));
        TenantContext.fijar(new MunicipalidadId(conPlanos));

        List<ContrasteDeAreas> conMuchaTolerancia =
                enUnaTransaccion.execute(estado -> areas.contrastar(Tolerancia.de("0.60"), 500));

        assertThat(conMuchaTolerancia)
                .as("difieren el 50 %, y con el 60 % admitido eso deja de ser una sospecha")
                .isEmpty();
    }

    @Test
    @DisplayName("y no ve los poligonos de la municipalidad vecina: lo acota la politica")
    void elAislamientoSeSostiene() throws SQLException {
        plantarPoligonoEn(conPlanos);
        TenantContext.fijar(new MunicipalidadId(sinPlanos));

        assertThatThrownBy(
                        () ->
                                enUnaTransaccion.execute(
                                        estado -> areas.contrastar(UNO_POR_CIENTO, 500)))
                .as(
                        "la vecina SI tiene un poligono, y aun asi esta municipalidad dice que no"
                                + " puede mirar: el aislamiento no lo pone ningun WHERE")
                .isInstanceOf(AreasDelPadron.SinCartografia.class);
    }

    // ── Fixtures ───────────────────────────────────────────────────────

    /**
     * Le planta al predio de esa municipalidad un poligono, y devuelve el area que PostGIS le mide.
     *
     * <p>Entra por SQL y no por ninguna operacion del contrato, y eso no es un atajo de la prueba:
     * es como entra de verdad. ADR-0021 lo dice —la geometria entra por la carga cartografica, que
     * es un proceso {@code batch}— y {@code TODA_GEOMETRIA_ENTRA_POR_BATCH} lo vigila.
     *
     * <p><b>El area de la ficha se DERIVA de lo que el motor mide, y no al reves</b>, y esa es la
     * unica forma de que esta prueba no haya que ajustarla a mano: un rectangulo dibujado en UTM
     * mide sobre el ELIPSOIDE algo parecido y no identico —el factor de escala de la proyeccion—,
     * asi que fijar «180,00 m2» en la prueba y esperar que {@code ST_Area} lo devuelva es escribir
     * una cifra que depende de la zona, del datum y de la version de PostGIS.
     */
    private java.math.BigDecimal plantarPoligonoEn(long municipalidadId) throws SQLException {
        ejecutar(
                municipalidadId,
                "UPDATE predio SET geometria = ST_Multi(ST_Transform("
                        + "  ST_SetSRID(ST_MakeBox2D(ST_Point(534000, 9458000),"
                        + "                          ST_Point(534200, 9458200)), 32717),"
                        + "  4326))::geography");
        return unaCifraDe(
                municipalidadId,
                "SELECT ROUND(ST_Area(geometria)::numeric, 2) FROM predio"
                        + " WHERE geometria IS NOT NULL ORDER BY id LIMIT 1");
    }

    /** Le pone a la ficha {@code UNICA} vigente el area que se le diga. */
    private void areaDeLaFicha(long municipalidadId, java.math.BigDecimal area)
            throws SQLException {
        ejecutar(
                municipalidadId,
                "UPDATE ficha_catastral SET area_terreno = "
                        + area.toPlainString()
                        + " WHERE tipo = 'UNICA' AND vigencia_hasta IS NULL");
    }

    private java.math.BigDecimal unaCifraDe(long municipalidadId, String consulta)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (Statement sentencia = app.createStatement();
                    ResultSet fila = sentencia.executeQuery(consulta)) {
                if (!fila.next()) {
                    throw new IllegalStateException("La fixture no dejo lo que la prueba necesita");
                }
                return fila.getBigDecimal(1);
            }
        }
    }

    private void ejecutar(long municipalidadId, String sql) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidadId);
            try (Statement sentencia = app.createStatement()) {
                sentencia.executeUpdate(sql);
            }
            app.commit();
        }
    }

    /** Deja constancia de que el motor tiene PostGIS: sin el, todo lo de arriba seria otra cosa. */
    @Test
    @DisplayName("el motor de la prueba tiene PostGIS, que es la premisa de todo lo anterior")
    void elMotorTienePostgis() throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP);
                Statement sentencia = app.createStatement();
                ResultSet fila = sentencia.executeQuery("SELECT postgis_version()")) {
            assertThat(fila.next()).isTrue();
            assertThat(fila.getString(1)).isNotBlank();
        }
    }
}
