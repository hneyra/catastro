package kamayuk.catastro.esquema;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * <b>FIXTURE DE PRUEBA</b>: el padron de contribuyentes de {@code rentas}, recreado aqui con nombre
 * propio para poder sembrar el escenario de una prueba (P5C).
 *
 * <h2>Por que existe</h2>
 *
 * <p>Ocho clases de prueba de este modulo necesitan la misma premisa —«este predio tiene un
 * titular, y se llama asi»— y la tabla donde la escribian se fue a {@code rentas} con P5C. {@code
 * titularidad.contribuyente_id} <b>si</b> se queda aqui: es de catastro, y desde P5C es un
 * identificador que apunta a otro sistema (su clave foranea la retiro el propio generador del
 * baseline, comentada en `V1` como {@code [CRUZA LA FRONTERA]}). Lo que hace falta es el <b>otro
 * extremo</b>, para que la prueba pueda decir a quien pertenece esa cuota.
 *
 * <h2>Por que se llama `_de_prueba`, igual que las de normativa</h2>
 *
 * <p>Por la misma razon exacta que {@link EscenarioDeNormativa}: conservarle el nombre habria
 * dejado en el arbol ocho clases con SQL contra {@code contribuyente}, y quien lo leyera concluiria
 * que el padron sigue estando aqui. El escaner de frontera de sistema solo recorre {@code
 * src/main}, asi que no lo cazaria — y esa es justamente la razon de que el nombre tenga que
 * decirlo solo.
 *
 * <p>No lleva RLS ni privilegios acotados, y su {@code municipalidad_id} es <b>anulable</b>: si
 * fuera {@code NOT NULL}, {@code AislamientoMultiTenantTest} le exigiria RLS sola —esa es
 * exactamente su regla— y estariamos manteniendo la politica de una tabla que no es de nadie. Lo
 * que si tiene RLS es lo de catastro, que es lo que el codigo de produccion lee.
 *
 * <p><b>Y no es un doble del padron:</b> el codigo de produccion nunca lee esta tabla. Lee {@link
 * kamayuk.catastro.contribuyentes.DirectorioDeContribuyentes}, que en produccion implementa un
 * cliente HTTP contra {@code rentas}. Las pruebas que la siembran acompanan la siembra con una
 * implementacion en memoria de ese puerto.
 */
public final class EscenarioDelPadron {

    private EscenarioDelPadron() {}

    /**
     * Crea la tabla si no esta. Idempotente: cada clase de prueba provisiona su propia base, y
     * dentro de una hay varias que llaman aqui.
     */
    public static void crear(Connection conexion) throws SQLException {
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.execute(
                    """
                    CREATE TABLE IF NOT EXISTS contribuyente_de_prueba (
                        municipalidad_id    bigint,
                        id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        codigo_contribuyente varchar(20)  NOT NULL,
                        tipo_documento      varchar(10)  NOT NULL,
                        numero_documento    varchar(20)  NOT NULL,
                        tipo_persona        varchar(20)  NOT NULL,
                        nombre_razon_social varchar(240) NOT NULL,
                        condicion_especial  varchar(30),
                        fecha_nacimiento    date,
                        estado_civil        varchar(20),
                        conyuge_id          bigint,
                        activo              boolean NOT NULL DEFAULT true,
                        fecha_registro      timestamptz NOT NULL DEFAULT now(),
                        usuario_registro    varchar(60) NOT NULL
                    )
                    """);
            // Sin DELETE: `AislamientoMultiTenantTest` exige que `kamayuk_app` no lo tenga en
            // ninguna tabla de esta base (RNF-051, regla 4), y la comprobacion mira el catalogo
            // entero, no una lista.
            sentencia.execute("GRANT SELECT, INSERT, UPDATE ON contribuyente_de_prueba TO PUBLIC");
        }
        // La conexion llega con autoCommit en false y se cierra al salir del try de quien
        // llama: sin este commit la tabla se crea y se pierde, y el sintoma es exactamente el
        // mismo que si no se hubiera creado.
        conexion.commit();
    }
}
