package kamayuk.catastro.nucleo.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.esquema.BaseDeDatosDePrueba;
import kamayuk.catastro.esquema.ContextoDeTenant;
import kamayuk.catastro.esquema.DatosDePrueba;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #27 (AC-2) — el indice que {@code V13} retira, y por que la lectura no pierde nada.
 *
 * <h2>Lo que estaba de mas</h2>
 *
 * <p>{@code V6} creo {@code frente_predio_ix (municipalidad_id, predio_id)} y {@code V10} creo
 * {@code frente_predio_via_uq (municipalidad_id, predio_id, via_id)}, del que el primero es
 * <b>prefijo estricto</b>: toda busqueda que use uno usa el otro. Medido antes de retirarlo, con
 * los dos puestos, el planificador ya elegia el segundo y {@code pg_stat_user_indexes} daba
 * <b>0</b> recorridos del primero. Lo que quedaba era su precio en cada escritura, que es lo que
 * {@code V7} escribio al retirar {@code zonificacion_vigencia_ix}.
 *
 * <h2>Lo que esta prueba fija, y no es la palabra «Index»</h2>
 *
 * <p>Que la consulta de produccion —{@link FrentesDelPredioJdbc#FRENTES_DEL_PREDIO}, la unica que
 * filtra por {@code predio_id} a secas— sigue llegando a un indice <b>nombrado</b>, con {@code
 * predio_id} y la condicion de la politica JUNTOS en el {@code Index Cond}. Exigir «Index» no
 * serviria: el plan del quinto hallazgo de RLS tambien la decia, apoyado solo en la politica y
 * leyendo el padron entero del inquilino.
 *
 * <h2>Y el nombre del indice TAMPOCO basta: hubo que medir los bloques</h2>
 *
 * <p>La primera version de esta prueba exigia el nombre del indice y las dos columnas en el {@code
 * Index Cond}, y <b>paso en VERDE sobre el defecto exacto que existe para atrapar</b>: con {@code
 * frente_predio_via_uq} reordenado a {@code (municipalidad_id, via_id, predio_id)} —misma unicidad,
 * mismo derivador idempotente— el plan sale <b>byte a byte igual</b>:
 *
 * <pre>
 * Index Scan using frente_predio_via_uq on frente_predio f
 *   Index Cond: ((municipalidad_id = current_setting('app.municipalidad_id')::bigint)
 *                AND (predio_id = 1500))
 * </pre>
 *
 * <p>Con {@code predio_id} detras de {@code via_id} deja de ser condicion de BUSQUEDA y pasa a ser
 * un filtro <i>dentro</i> del indice: PostgreSQL lo sigue imprimiendo en el {@code Index Cond}, y
 * el recorrido se traga el rango entero de la municipalidad. Es el quinto hallazgo de RLS por un
 * tercer eje —el plan dice «Index», nombra el indice bueno <b>y</b> nombra la columna, y aun asi
 * lee los 9 000 frentes del inquilino para devolver 3—. Lo unico que los separa son los bloques:
 * <b>6 con el orden bueno y 47 con el roto</b>, medido con {@code EXPLAIN (ANALYZE, BUFFERS)}. Por
 * eso esta prueba los cuenta.
 *
 * <p><b>Sin {@code enable_seqscan = off} a proposito</b>: forzarlo mediria si el indice es
 * ALCANZABLE, y lo que hay que medir es que el planificador lo ELIGE. Y la conexion es la de {@code
 * kamayuk_app}: como superusuario —que omite RLS— el plan seria otro, y la prueba daria por bueno
 * uno que la aplicacion nunca obtiene.
 */
@DisplayName("#27 — Los frentes de un predio llegan al indice sin frente_predio_ix (V13)")
class FrentesEnElIndiceTest {

    /**
     * Suficientes para que el planificador prefiera un indice.
     *
     * <p>El mismo motivo que {@code PlanoEnElIndiceTest}: con unas pocas filas PostgreSQL recorre
     * la tabla <b>y hace bien</b>, asi que una prueba de plan sobre dos filas no mide el plan, mide
     * el tamano.
     */
    private static final int PREDIOS = 3_000;

    private static final int VIAS = 400;

    private static BaseDeDatosDePrueba base;
    private static long municipalidad;
    private static long predioConFrentes;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();
        municipalidad = DatosDePrueba.crearMunicipalidad(base, "200610", "Municipalidad de #27");
        long vecina = DatosDePrueba.crearMunicipalidad(base, "200611", "Municipalidad vecina, #27");
        // Las DOS se siembran, y no es un adorno: con una sola dueña de toda la tabla la condicion
        // de la politica selecciona el 100 % de las filas y no acota nada, asi que el plan medido
        // seria otro (la leccion de PlanoEnElIndiceTest).
        for (long cual : new long[] {municipalidad, vecina}) {
            sembrar(cual);
        }
        predioConFrentes = unPredioConFrentes();
        analizar();
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    @DisplayName("la consulta de produccion llega a frente_predio_via_uq, no a un Seq Scan")
    void losFrentesLleganAlIndiceCompuesto() throws SQLException {
        // Se le pide el plan a la CONSTANTE del repositorio y no a una copia escrita aqui: una
        // copia seguiria verde el dia que alguien cambiara la de produccion (#17).
        String sql =
                FrentesDelPredioJdbc.FRENTES_DEL_PREDIO.replace(
                        ":predio", Long.toString(predioConFrentes));
        String plan = explicar(sql);

        assertThat(plan)
                .as(
                        "lo que se exige NUNCA es la palabra «Index» —la del quinto hallazgo de RLS"
                                + " tambien la decia—: es que el indice NOMBRADO sea el del filtro."
                                + " Plan medido:%n%s",
                        plan)
                .contains("frente_predio_via_uq");

        String condicion = String.join(" ", condicionesDeIndice(plan));
        assertThat(condicion)
                .as(
                        "el predio y la condicion de la politica, JUNTOS: si la municipalidad"
                                + " cayera al Filter, la consulta leeria los frentes de todas las"
                                + " municipalidades para descartarlos despues. Plan medido:%n%s",
                        plan)
                .contains("predio_id")
                .contains("municipalidad_id");

        // Y LA MITAD QUE EL NOMBRE NO DA. Ver la cabecera: con predio_id detras de via_id el plan
        // sale identico —mismo indice, mismo Index Cond con las dos columnas— y el recorrido lee
        // el rango entero de la municipalidad. Lo unico que los distingue son los bloques.
        int bloques = bloquesDelNodo(plan, "frente_predio_via_uq");
        assertThat(bloques)
                .as(
                        "un descenso al predio toca unas pocas paginas; un recorrido del rango de"
                                + " la municipalidad se traga los %d frentes del inquilino para"
                                + " devolver 3. Medido: 6 bloques con (municipalidad_id, predio_id,"
                                + " via_id) y 47 con (municipalidad_id, via_id, predio_id), que es"
                                + " la MISMA salida de EXPLAIN sin BUFFERS. Plan medido:%n%s",
                        PREDIOS * 3, plan)
                .isLessThan(20);
    }

    @Test
    @DisplayName("y frente_predio_ix ya no existe: era prefijo estricto del anterior")
    void elIndiceRedundanteSeFue() throws SQLException {
        assertThat(existeElIndice("frente_predio_ix"))
                .as(
                        "V13 lo retira. El planificador ya prefería el compuesto teniendo los dos,"
                                + " asi que lo unico que quedaba era su precio: medido en WAL, 3 000"
                                + " altas de frente cuestan 21 230 registros con el y 18 220 sin el")
                .isFalse();
        assertThat(existeElIndice("frente_predio_via_uq"))
                .as("el que hace idempotente al derivador NO se toca (V10)")
                .isTrue();
        assertThat(existeElIndice("frente_principal_uq"))
                .as(
                        "ni el del frente principal, que es PARCIAL (WHERE es_principal) y por eso"
                                + " no lo cubre ningun otro")
                .isTrue();
    }

    // ------------------------------------------------------------------

    private static String explicar(String sql) throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, municipalidad);
            List<String> lineas = new ArrayList<>();
            // Con ANALYZE y BUFFERS: sin los bloques, las dos ordenaciones del indice compuesto
            // dan la MISMA salida y esta prueba pasaria sobre el defecto (ver la cabecera).
            try (PreparedStatement explicar =
                            app.prepareStatement("EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) " + sql);
                    ResultSet filas = explicar.executeQuery()) {
                while (filas.next()) {
                    lineas.add(filas.getString(1));
                }
            }
            return String.join("\n", lineas);
        }
    }

    /**
     * Los bloques que toca el nodo que usa ese indice: la linea {@code Buffers:} que sigue a su
     * {@code Index Scan}, antes de que empiece otro nodo.
     */
    private static int bloquesDelNodo(String plan, String indice) {
        boolean dentro = false;
        for (String linea : plan.lines().map(String::strip).toList()) {
            if (linea.startsWith("->")
                    || linea.startsWith("Index Scan")
                    || linea.startsWith("Seq")) {
                dentro = linea.contains(indice);
            }
            if (dentro && linea.startsWith("Buffers:")) {
                int total = 0;
                for (String pieza : linea.substring("Buffers:".length()).split("[ ,]+")) {
                    int igual = pieza.indexOf('=');
                    if (igual > 0
                            && (pieza.startsWith("shared")
                                    || pieza.contains("hit")
                                    || pieza.contains("read"))) {
                        total += Integer.parseInt(pieza.substring(igual + 1));
                    }
                }
                return total;
            }
        }
        throw new IllegalStateException(
                "no se encontro el nodo de «"
                        + indice
                        + "» con sus bloques: sin eso esta prueba no midio nada.\n"
                        + plan);
    }

    private static List<String> condicionesDeIndice(String plan) {
        return plan.lines()
                .map(String::strip)
                .filter(linea -> linea.startsWith("Index Cond:"))
                .toList();
    }

    private static boolean existeElIndice(String nombre) throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER);
                PreparedStatement consulta =
                        owner.prepareStatement(
                                "SELECT count(*) FROM pg_class"
                                        + " WHERE relkind = 'i' AND relname = ?")) {
            consulta.setString(1, nombre);
            try (ResultSet fila = consulta.executeQuery()) {
                fila.next();
                return fila.getInt(1) > 0;
            }
        }
    }

    // ── Fixtures ───────────────────────────────────────────────────────

    private static void sembrar(long municipalidadId) throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER)) {
            ContextoDeTenant.fijar(owner, municipalidadId);
            ejecutar(
                    owner,
                    "INSERT INTO via (municipalidad_id, codigo, tipo_via, nombre)"
                            + " SELECT "
                            + municipalidadId
                            + ", 'V' || lpad(i::text, 6, '0'), 'CALLE', 'Calle ' || i"
                            + " FROM generate_series(1, "
                            + VIAS
                            + ") i");
            ejecutar(
                    owner,
                    "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo, direccion)"
                            + " SELECT "
                            + municipalidadId
                            + ", lpad(("
                            + municipalidadId
                            + " * 1000000 + i)::text, 23, '0'), 'URBANO', 'Direccion ' || i"
                            + " FROM generate_series(1, "
                            + PREDIOS
                            + ") i");
            // Tres frentes por predio y sobre vias distintas: es lo que frente_predio_via_uq
            // admite, y lo que hace que el indice compuesto tenga algo que discriminar.
            ejecutar(
                    owner,
                    "INSERT INTO frente_predio (municipalidad_id, predio_id, via_id, geometria,"
                            + " longitud_m, observacion, usuario_registro)"
                            + " SELECT "
                            + municipalidadId
                            + ", p.id, v.id,"
                            + " ST_MakeLine(ST_MakePoint(-80.0 + p.id / 10000.0, -4.9),"
                            + "             ST_MakePoint(-80.0 + p.id / 10000.0 + 0.0001, -4.9))"
                            + "   ::geography,"
                            + " 10.00 + k, 'siembra de la prueba de #27', 'pruebas'"
                            + " FROM (SELECT id, row_number() OVER (ORDER BY id) rn FROM predio"
                            + "        WHERE municipalidad_id = "
                            + municipalidadId
                            + ") p"
                            + " CROSS JOIN generate_series(0, 2) k"
                            + " JOIN LATERAL (SELECT id FROM via WHERE municipalidad_id = "
                            + municipalidadId
                            + "               ORDER BY id OFFSET ((p.rn + k) % "
                            + VIAS
                            + ") LIMIT 1) v ON true");
            owner.commit();
        }
    }

    private static long unPredioConFrentes() throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER)) {
            ContextoDeTenant.fijar(owner, municipalidad);
            try (PreparedStatement consulta =
                            owner.prepareStatement(
                                    "SELECT predio_id FROM frente_predio"
                                            + " ORDER BY predio_id OFFSET 1500 LIMIT 1");
                    ResultSet fila = consulta.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }

    /** Sin estadisticas el planificador adivina, y la prueba mediria su adivinanza. */
    private static void analizar() throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER)) {
            ejecutar(owner, "ANALYZE frente_predio");
            ejecutar(owner, "ANALYZE via");
            ejecutar(owner, "ANALYZE predio");
            owner.commit();
        }
    }

    private static void ejecutar(Connection conexion, String sql) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.execute();
        }
    }
}
