package kamayuk.catastro.nucleo.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import kamayuk.catastro.auditoria.Origen;
import kamayuk.catastro.auditoria.OrigenContext;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.MunicipalidadId;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import kamayuk.catastro.nucleo.dominio.DerivacionDeFrentes;
import kamayuk.catastro.nucleo.dominio.EstadoDeLaLongitud;
import kamayuk.catastro.nucleo.dominio.FrenteDelPredio;
import kamayuk.catastro.nucleo.dominio.FrentePropuesto;
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
 * El corte del lote contra el eje de calzada, <b>contra PostGIS de verdad</b> (#7, AC 1 y AC 8).
 *
 * <h2>Por que esta prueba no se puede sustituir por un doble</h2>
 *
 * <p>Lo que decide si un frente sale o no sale es <b>SQL</b>: el marco ensanchado, el {@code
 * ST_DWithin} metrico, el {@code ST_Intersection} contra la franja de la calzada y el {@code
 * ST_LineMerge} que junta los tramos. Un doble mediria que el caso de uso llama al repositorio
 * —cierto y poco util— y no lo unico que puede fallar en silencio: que la consulta descarte una via
 * que si da al lote.
 *
 * <h2>La geometria esta puesta para que el marco DECIDA</h2>
 *
 * <p>Las dos vias del escenario estan a 3 m del borde del lote, y por lados distintos:
 *
 * <ul>
 *   <li><b>Grau</b>, al norte, con su rectangulo envolvente <b>fuera</b> del rectangulo del lote —3
 *       m por encima de su borde norte—. Solo aparece si el marco se ensancha. Es el caso que
 *       {@code MargenDelMarco} existe para cubrir, y romper ese ensanchado la hace desaparecer sin
 *       ningun error.
 *   <li><b>Sur</b>, apoyada <b>sobre</b> el borde sur, con su rectangulo tocando el del lote. Sale
 *       con margen y sin el.
 * </ul>
 *
 * <p>Con las dos, el contraste es exacto: con el ensanchado se proponen <b>dos</b> frentes y sin el
 * <b>uno</b>. Una prueba con una sola via no distinguiria «no encontro» de «no habia».
 */
@DisplayName("#7 — El corte del lote contra el eje de la via, contra PostGIS")
class DerivacionDeFrentesJdbcTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC);

    /** Ocho metros: media calzada de una via local, mas la holgura de un levantamiento. */
    private static final Medida OCHO_METROS = Medida.enMetrosLineales("8.00");

    private static final Observacion PORQUE =
            Observacion.de("Derivacion de frentes de la prueba de #7");

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static FrentesDelPredioJdbc frentes;
    private static TransactionTemplate enUnaTransaccion;
    private static long predioId;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "240501", "La del corte");
        long parametroId = DatosDePrueba.crearParametroNacional(base);
        DatosDePrueba.sembrarTenant(base, municipalidad, parametroId, "CO");

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        frentes = new FrentesDelPredioJdbc(JdbcClient.create(pool));
        enUnaTransaccion = new TransactionTemplate(new TenantTransactionManager(pool));
        predioId = unaCifraDe("SELECT id FROM predio ORDER BY id LIMIT 1").longValue();
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
        OrigenContext.limpiar();
    }

    /**
     * Deja el escenario: el lote con su poligono, las dos vias con su eje y ningun frente.
     *
     * <p>La geometria entra por SQL y no por ninguna operacion del contrato, y eso no es un atajo:
     * es como entra de verdad (ADR-0021, {@code TODA_GEOMETRIA_ENTRA_POR_BATCH}).
     */
    @BeforeEach
    void elEscenario() throws SQLException {
        // La siembra deja un frente ya inscrito sobre la via sembrada; se retira para que lo que
        // se mida sea lo que el corte propone y no lo que ya estaba.
        //
        // Va por la conexion de ADMINISTRACION y no por la de la aplicacion, y no es comodidad:
        // `kamayuk_app` NO tiene DELETE sobre `frente_predio` —la primera corrida de esta prueba
        // lo dijo, «permission denied for table frente_predio»— porque de esa tabla cuelga un
        // cobro y aqui no se borra nada (regla 4, RNF-051). Que la fixture tenga que salirse del
        // rol de la aplicacion para limpiar es la prueba de que el privilegio esta bien puesto.
        limpiarComoAdmin("DELETE FROM frente_predio");
        limpiarComoAdmin("DELETE FROM frente_derivacion");
        ejecutar("UPDATE via SET eje = NULL");

        // El lote: 200 m x 200 m en UTM 17S, transformado a WGS84.
        ejecutar(
                "UPDATE predio SET geometria = ST_Multi(ST_Transform("
                        + "  ST_SetSRID(ST_MakeBox2D(ST_Point(534000, 9458000),"
                        + "                          ST_Point(534200, 9458200)), 32717),"
                        + "  4326))::geography WHERE id = "
                        + predioId);

        // Grau, al NORTE y 3 m por encima del borde: su rectangulo NO toca el del lote.
        ejeDeLaVia("V-GRAU", "LINESTRING(533900 9458203, 534300 9458203)");
        // Sur, apoyada sobre el borde sur: su rectangulo SI toca el del lote.
        ejeDeLaVia("V-SUR", "LINESTRING(533900 9458000, 534300 9458000)");
    }

    @Test
    @DisplayName("el corte propone un frente por via, con su tramo y sus metros")
    void elCorteProponeUnFrentePorVia() {
        TenantContext.fijar(new MunicipalidadId(municipalidad));

        List<FrentePropuesto> propuestos =
                enUnaTransaccion.execute(
                        estado -> frentes.cortarContraLasVias(predioId, OCHO_METROS));

        assertThat(propuestos)
                .as(
                        "dos vias bordean el lote y las dos estan a 3 m: un frente por cada una."
                                + " Con UNA sola via esta prueba no distinguiria «no encontro» de «no"
                                + " habia»")
                .hasSize(2);
        assertThat(propuestos)
                .allSatisfy(
                        propuesto -> {
                            assertThat(propuesto.geometriaWkt())
                                    .as("el tramo cortado, para poder dibujarlo")
                                    .contains("LINESTRING");
                            assertThat(propuesto.longitud().magnitud())
                                    .as(
                                            "el lado del lote mide 200 m; la franja de la calzada"
                                                    + " coge ademas unos metros de las dos esquinas,"
                                                    + " asi que el tramo es algo mas largo. Lo que NO"
                                                    + " puede ser es un numero suelto: en grados"
                                                    + " valdria 0,0018")
                                    .isGreaterThan(new BigDecimal("195"))
                                    .isLessThan(new BigDecimal("240"));
                        });
    }

    @Test
    @DisplayName("EL CONTRASTE: sin ensanchar el marco, la via de enfrente desaparece")
    void sinEnsancharElMarcoLaViaDeEnfrenteDesaparece() {
        // Lo que aqui se mide es el defecto que `MargenDelMarco` existe para impedir, escrito como
        // se escribe de verdad: una tolerancia tan pequena que el ensanchado no llega. Grau esta a
        // 3 m del borde y su rectangulo queda fuera del rectangulo del lote, asi que el marco la
        // descarta ANTES de que el ST_DWithin la vea — sin error, sin traza, y el predio de
        // esquina sale con un frente en vez de dos.
        TenantContext.fijar(new MunicipalidadId(municipalidad));

        List<FrentePropuesto> conMargenMinusculo =
                enUnaTransaccion.execute(
                        estado ->
                                frentes.cortarContraLasVias(
                                        predioId, Medida.enMetrosLineales("0.01")));

        assertThat(conMargenMinusculo)
                .as("solo queda la via apoyada sobre el borde, cuyo rectangulo si toca el del lote")
                .hasSize(1);
    }

    @Test
    @DisplayName("lo propuesto se guarda como PROPUESTA, nunca como confirmado (ADR-0021)")
    void loPropuestoSeGuardaComoPropuesta() {
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        OrigenContext.fijar(Origen.deProceso("derivacion-de-frentes"));

        List<FrenteDelPredio> guardados = derivarYLeer();

        assertThat(guardados).hasSize(2);
        assertThat(guardados)
                .as(
                        "de esta cifra cuelga un cobro y la corto una maquina: nadie puede"
                                + " determinar sobre ella sin saber que lo hace")
                .allMatch(frente -> frente.estado() == EstadoDeLaLongitud.PROPUESTA)
                .allMatch(frente -> frente.confirmadoPor() == null)
                .noneMatch(FrenteDelPredio::estaConfirmada);
        assertThat(guardados)
                .allSatisfy(
                        frente ->
                                assertThat(frente.longitud().unidad())
                                        .as("metros LINEALES, y la unidad viaja con la cifra")
                                        .isEqualTo(FrenteDelPredio.UNIDAD));
    }

    @Test
    @DisplayName("volver a derivar no escribe una segunda propuesta del mismo tramo")
    void volverADerivarNoEscribeDosVeces() {
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        OrigenContext.fijar(Origen.deProceso("derivacion-de-frentes"));

        derivarYLeer();
        long escritosEnLaSegunda =
                enUnaTransaccion.execute(
                        estado ->
                                frentes.cortarContraLasVias(predioId, OCHO_METROS).stream()
                                        .filter(p -> frentes.proponer(p, PORQUE).isPresent())
                                        .count());

        assertThat(escritosEnLaSegunda)
                .as(
                        "la garantia es del MOTOR —`frente_predio_via_uq` con ON CONFLICT DO"
                                + " NOTHING— y no de un `if`: dos corridas simultaneas leerian las dos"
                                + " «no esta» y las dos insertarian")
                .isZero();
        assertThat(leerFrentes()).hasSize(2);
    }

    @Test
    @DisplayName("y no pisa una longitud CONFIRMADA: no hay ninguna rama que actualice")
    void noPisaUnaLongitudConfirmada() throws SQLException {
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        OrigenContext.fijar(Origen.deProceso("derivacion-de-frentes"));
        derivarYLeer();

        long frenteId = leerFrentes().get(0).id();
        enUnaTransaccion.execute(
                estado ->
                        frentes.confirmar(
                                frenteId,
                                Medida.enMetrosLineales("111.00"),
                                Observacion.de("Medido en campo con cinta, 2026-09-06"),
                                RELOJ.instant()));

        enUnaTransaccion.execute(
                estado -> {
                    frentes.cortarContraLasVias(predioId, OCHO_METROS)
                            .forEach(propuesto -> frentes.proponer(propuesto, PORQUE));
                    return null;
                });

        FrenteDelPredio confirmado =
                leerFrentes().stream()
                        .filter(frente -> frente.id() == frenteId)
                        .findFirst()
                        .orElseThrow();
        assertThat(confirmado.longitud().magnitud())
                .as(
                        "lo unico que puede cambiar una longitud es el acto de confirmarla; una"
                                + " corrida del derivador que la pisara devolveria a PROPUESTA una"
                                + " cifra que alguien firmo")
                .isEqualByComparingTo(new BigDecimal("111.00"));
        assertThat(confirmado.estaConfirmada()).isTrue();
        assertThat(confirmado.confirmadoPor()).isEqualTo("derivacion-de-frentes");
    }

    @Test
    @DisplayName("un predio sin poligono no propone nada, y la constancia dice por que")
    void unPredioSinPoligonoNoProponeNadaYDicePorQue() throws SQLException {
        // El estado real de HOY en toda instalacion: `V61` trajo la columna y nada la llena.
        ejecutar("UPDATE predio SET geometria = NULL WHERE id = " + predioId);
        TenantContext.fijar(new MunicipalidadId(municipalidad));

        List<FrentePropuesto> propuestos =
                enUnaTransaccion.execute(
                        estado -> frentes.cortarContraLasVias(predioId, OCHO_METROS));
        enUnaTransaccion.execute(
                estado -> {
                    frentes.anotarDerivacion(
                            new DerivacionDeFrentes(
                                    predioId, RELOJ.instant(), 0, "El lote no tiene poligono"));
                    return null;
                });

        assertThat(propuestos).isEmpty();
        Optional<DerivacionDeFrentes> constancia =
                enUnaTransaccion.execute(estado -> frentes.ultimaDerivacion(predioId));
        assertThat(constancia)
                .as(
                        "«no da a ninguna calle» y «nadie lo ha calculado» son la misma lista vacia"
                                + " y dos problemas distintos: sin esta fila, la respuesta del endpoint"
                                + " no los distingue")
                .isPresent();
        assertThat(constancia.orElseThrow().motivo()).contains("no tiene poligono");
    }

    @Test
    @DisplayName("una via sin eje levantado no propone nada, y es el otro estado de hoy")
    void unaViaSinEjeNoProponeNada() throws SQLException {
        ejecutar("UPDATE via SET eje = NULL");
        TenantContext.fijar(new MunicipalidadId(municipalidad));

        List<FrentePropuesto> propuestos =
                enUnaTransaccion.execute(
                        estado -> frentes.cortarContraLasVias(predioId, OCHO_METROS));

        assertThat(propuestos)
                .as("sin eje no hay contra que cortar, y no es un error: es que falta el dato")
                .isEmpty();
    }

    @Test
    @DisplayName("y no ve el lote de la municipalidad vecina: lo acota la politica, no un WHERE")
    void elAislamientoSeSostiene() throws SQLException, IOException {
        long vecina = DatosDePrueba.crearMunicipalidad(base, "240502", "La vecina");
        DatosDePrueba.sembrarTenant(base, vecina, DatosDePrueba.crearParametroNacional(base), "VE");
        TenantContext.fijar(new MunicipalidadId(vecina));

        List<FrentePropuesto> propuestos =
                enUnaTransaccion.execute(
                        estado -> frentes.cortarContraLasVias(predioId, OCHO_METROS));

        assertThat(propuestos)
                .as("el predio del escenario existe, y desde la vecina no hay nada que cortar")
                .isEmpty();
    }

    // ── Fixtures ───────────────────────────────────────────────────────

    private List<FrenteDelPredio> derivarYLeer() {
        enUnaTransaccion.execute(
                estado -> {
                    frentes.cortarContraLasVias(predioId, OCHO_METROS)
                            .forEach(propuesto -> frentes.proponer(propuesto, PORQUE));
                    return null;
                });
        return leerFrentes();
    }

    private List<FrenteDelPredio> leerFrentes() {
        return enUnaTransaccion.execute(estado -> frentes.deUnPredio(predioId));
    }

    private static void ejeDeLaVia(String codigo, String wktEnUtm) throws SQLException {
        ejecutar(
                "UPDATE via SET eje = ST_Transform(ST_GeomFromText('"
                        + wktEnUtm
                        + "', 32717), 4326)::geography WHERE codigo = '"
                        + codigo
                        + "'");
        // La via se crea si no estaba: la siembra trae una sola y el escenario necesita dos.
        ejecutar(
                "INSERT INTO via (municipalidad_id, codigo, tipo_via, nombre, eje)"
                        + " SELECT current_setting('app.municipalidad_id')::bigint, '"
                        + codigo
                        + "', 'AVENIDA', 'Via "
                        + codigo
                        + "', ST_Transform(ST_GeomFromText('"
                        + wktEnUtm
                        + "', 32717), 4326)::geography"
                        + " WHERE NOT EXISTS (SELECT 1 FROM via WHERE codigo = '"
                        + codigo
                        + "')");
    }

    /** Lo que la aplicacion no puede hacer y la fixture si: borrar para dejar el escenario. */
    private static void limpiarComoAdmin(String sql) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                Statement sentencia = admin.createStatement()) {
            sentencia.executeUpdate(sql);
        }
    }

    private static void ejecutar(String sql) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement()) {
                sentencia.executeUpdate(sql);
            }
            app.commit();
        }
    }

    private static BigDecimal unaCifraDe(String consulta) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            try (Statement sentencia = app.createStatement();
                    ResultSet fila = sentencia.executeQuery(consulta)) {
                if (!fila.next()) {
                    throw new IllegalStateException("La fixture no dejo lo que la prueba necesita");
                }
                return fila.getBigDecimal(1);
            }
        }
    }
}
