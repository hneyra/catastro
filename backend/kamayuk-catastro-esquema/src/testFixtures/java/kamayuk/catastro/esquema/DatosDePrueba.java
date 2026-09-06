package kamayuk.catastro.esquema;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Siembra una fila en <b>cada</b> tabla de tenant, para las dos municipalidades de la prueba.
 *
 * <p>La cobertura completa no es adorno: la verificacion "con contexto de A no se ve ninguna fila
 * de B" es vacia si en la tabla no hay filas de B. Una tabla sin datos sembrados pasaria en verde
 * sin probar nada, que es justamente el modo de fallo contra el que existe esta prueba. Por eso la
 * prueba exige ademas que cada tabla de tenant tenga al menos una fila propia.
 *
 * <p><b>Al agregar una tabla de tenant hay que sembrarla aqui.</b> Si no, el build se pone rojo con
 * el mensaje de que la municipalidad A no ve filas suyas en esa tabla.
 *
 * <p>Los importes son BigDecimal (regla 1 de ARQ-04 §2) y no representan ninguna regla tributaria:
 * son datos de relleno. Ninguna cifra de aqui debe leerse como un parametro del predial, que sigue
 * bloqueado por D-02.
 */
public final class DatosDePrueba {

    private static final LocalDate VIGENCIA = LocalDate.of(2026, 1, 1);
    private static final short EJERCICIO = 2026;
    private static final BigDecimal CIEN = new BigDecimal("100.00");

    /**
     * El titular que se siembra en `titularidad`, y que NO esta en esta base.
     *
     * <p>Desde P5C el padron vive en `rentas`. Las dos municipalidades siembran el MISMO numero a
     * proposito: lo que la prueba de aislamiento tiene que seguir demostrando es que B no ve la
     * fila de A, y con identificadores distintos eso se podria confundir con que simplemente no
     * coinciden.
     */
    private static final long TITULAR_DE_OTRO_SISTEMA = 900_001L;

    private static final BigDecimal MIL = new BigDecimal("1000.00");

    /**
     * El modelo minimo que {@code documento_emitido.datos} admite: un {@code ModeloDeDocumento}.
     */
    private static final String MODELO_DE_DOCUMENTO =
            "{\"titulo\":\"Documento de prueba\",\"subtitulo\":null,\"aLaFecha\":\"2026-01-01\","
                    + "\"cabecera\":[],\"tablas\":[],\"pie\":[],\"duplicado\":null}";

    private DatosDePrueba() {}

    /** El alta de una municipalidad es una operacion de implantacion: la hace el owner. */
    public static long crearMunicipalidad(BaseDeDatosDePrueba base, String ubigeo, String nombre)
            throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER)) {
            long id =
                    insertar(
                            owner,
                            "INSERT INTO municipalidad (ubigeo, nombre, tipo)"
                                    + " VALUES (?, ?, 'DISTRITAL') RETURNING id",
                            ubigeo,
                            nombre);
            owner.commit();
            return id;
        }
    }

    /**
     * Catalogo nacional: lo carga su propio rol, no la aplicacion.
     *
     * <p>Desde D-13 (ADR-0017, V55) aqui entran tambien las tres tablas de valuacion —el cuadro de
     * valores unitarios, la depreciacion y los valores referenciales del MEF—, que dejaron de ser
     * municipales. Se siembran <b>una sola vez para las dos municipalidades</b>, que es exactamente
     * lo que la decision afirma: una copia nacional no puede divergir de si misma. Y se siembran
     * como {@code rol_carga_parametros}, porque {@code kamayuk_app} ya no tiene {@code INSERT}
     * sobre ellas.
     *
     * @return el identificador del parametro de relleno que las tablas de tenant componen
     */
    public static long crearParametroNacional(BaseDeDatosDePrueba base) throws SQLException {
        try (Connection carga = base.conexion(BaseDeDatosDePrueba.CARGA_PARAMETROS)) {
            long id =
                    insertar(
                            carga,
                            "INSERT INTO parametro_tributario_de_prueba"
                                    + " (municipalidad_id, tipo, clave, valor_numerico, vigencia_desde,"
                                    + "  documento_fuente, usuario_carga)"
                                    + " VALUES (NULL, 'PRUEBA', 'valor-de-relleno', 1.000000, ?,"
                                    + "         'fixture de la prueba de aislamiento', 'prueba')"
                                    + " RETURNING id",
                            VIGENCIA);
            sembrarValuacionNacional(carga);
            carga.commit();
            return id;
        }
    }

    /**
     * La cabecera de una edicion nacional y una fila de cada uno de los tres cuadros. La cabecera
     * es un {@code parametro_tributario} mas: es lo que un conjunto municipal compone por {@code
     * conjunto_parametro_detalle} para congelar que edicion uso.
     */
    private static void sembrarValuacionNacional(Connection carga) throws SQLException {
        long edicion =
                insertar(
                        carga,
                        "INSERT INTO parametro_tributario_de_prueba"
                                + " (municipalidad_id, tipo, clave, valor_texto, vigencia_desde,"
                                + "  documento_fuente, usuario_carga, usuario_aprueba)"
                                + " VALUES (NULL, 'PRUEBA_EDICION', 'valuacion', 'edicion de"
                                + " prueba', ?, 'fixture de la prueba de aislamiento', 'prueba',"
                                + " 'otra persona')"
                                + " RETURNING id",
                        VIGENCIA);
        ejecutar(
                carga,
                "INSERT INTO valor_unitario_de_prueba (publicacion_id, partida, categoria,"
                        + " anio_construccion_desde, valor_m2, documento_fuente)"
                        + " VALUES (?, 'MUROS', 'C', 2000, 1.000000, 'fixture de la prueba')",
                edicion);
        ejecutar(
                carga,
                "INSERT INTO depreciacion_de_prueba (publicacion_id, uso, material, estado_conservacion,"
                        + " antiguedad_hasta, porcentaje, documento_fuente)"
                        + " VALUES (?, '01', 'CONCRETO', 'BUENO', 10, 1.0000, 'fixture de la"
                        + " prueba')",
                edicion);
        ejecutar(
                carga,
                "INSERT INTO valor_referencial_de_prueba (publicacion_id, ejercicio, categoria,"
                        + " marca, modelo, anio_fabricacion, valor, documento_fuente)"
                        + " VALUES (?, ?, 'A1', 'MARCA', 'MODELO', 2020, 1000.00,"
                        + "         'fixture de la prueba')",
                edicion,
                EJERCICIO);
    }

    /**
     * Siembra todas las tablas de tenant como {@code kamayuk_app} y con el contexto de la
     * municipalidad fijado. Sembrar con el rol de la aplicacion, y no con el owner, verifica de
     * paso que la clausula {@code WITH CHECK} deja pasar lo que debe dejar pasar.
     */
    public static void sembrarTenant(
            BaseDeDatosDePrueba base, long muni, long parametroId, String sufijo)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, muni);

            long conjuntoId = sembrarParametros(app, muni, parametroId);

            // El PADRON NO ESTA EN ESTA BASE (P5C): vive en `rentas`. La titularidad se queda
            // aqui y su `contribuyente_id` es, desde la separacion, un identificador que apunta
            // a otro sistema —su clave foranea la retiro el propio generador del baseline,
            // comentada en `V1` como `[CRUZA LA FRONTERA]`—. Asi que se siembra un numero, y eso
            // es exactamente lo que el aislamiento tiene que seguir protegiendo: que la
            // municipalidad B no vea la titularidad de A, aunque el numero del titular sea el
            // mismo. De ahi que las dos siembren `TITULAR_DE_OTRO_SISTEMA`.
            sembrarDosTitularesDeMentira(app, muni, sufijo);
            long predioId = sembrarCatastro(app, muni, sufijo, TITULAR_DE_OTRO_SISTEMA, conjuntoId);

            // Y AHORA la copia local del conjunto sellado, no antes.
            //
            // El orden no es una comodidad de la prueba: es el de produccion. Un arancel se carga
            // contra un conjunto que en `normativa` todavia esta ABIERTO; cuando se sella,
            // `normativa` empieza a servirlo y este sistema lo descarga. La guarda de `V3`
            // (P5C, hueco 3 de P5B) rechaza escribir un arancel cuyo conjunto ya este en la
            // cache, y sembrar al reves la disparaba — lo cual, dicho de paso, es la primera
            // demostracion de que muerde: la fixture cayo con «El conjunto de parametros 1 esta
            // sellado» antes de que ninguna prueba la mirara.
            sembrarCacheDeNormativa(app, muni, conjuntoId);
            sembrarSeguridad(app, muni, sufijo);

            // Constancia de que los identificadores encadenados se usaron.
            if (predioId <= 0) {
                throw new IllegalStateException("No se sembro ningun predio");
            }

            // El trigger diferido de titularidad se evalua aqui.
            app.commit();
        }
    }

    // ------------------------------------------------------------------
    // Parametros
    // ------------------------------------------------------------------

    private static long sembrarParametros(Connection app, long muni, long parametroId)
            throws SQLException {
        long conjuntoId =
                insertar(
                        app,
                        "INSERT INTO conjunto_parametros_de_prueba (municipalidad_id, ejercicio, version)"
                                + " VALUES (?, ?, 1) RETURNING id",
                        muni,
                        EJERCICIO);
        ejecutar(
                app,
                "INSERT INTO conjunto_parametro_detalle_de_prueba (municipalidad_id, conjunto_id,"
                        + " parametro_id) VALUES (?, ?, ?)",
                muni,
                conjuntoId,
                parametroId);
        // La cache NO se siembra aqui: la siembra `sembrarTenant` DESPUES del arancel. Ver el
        // comentario de esa llamada — el orden es el real y lo impone la guarda de `V3`.
        return conjuntoId;
    }

    /**
     * La cache local del conjunto sellado (`V3`, P5B).
     *
     * <p>Son cinco tablas de tenant, asi que {@code AislamientoMultiTenantTest} exige que la
     * municipalidad A vea filas suyas en las cinco: una tabla vacia haria que «no se ve nada de B»
     * fuera cierto sin probar nada, que es el modo de fallo contra el que existe esa prueba.
     *
     * <p>Las cifras son de relleno y estan marcadas como tales en su documento fuente. Ninguna se
     * puede leer como un valor normativo: los de verdad los publica {@code normativa}.
     */
    private static void sembrarCacheDeNormativa(Connection app, long muni, long conjuntoId)
            throws SQLException {
        ejecutar(
                app,
                "INSERT INTO normativa_conjunto (municipalidad_id, conjunto_id, ejercicio, version,"
                        + " ambito, sha256, filas, origen, descargado_en)"
                        + " VALUES (?, ?, ?, 1, 'OBLIGACION', repeat('0', 64), 4,"
                        + "         'fixture de la prueba de aislamiento', now())",
                muni,
                conjuntoId,
                EJERCICIO);
        ejecutar(
                app,
                // La CLAVE no puede ser `valor-de-relleno`, y eso lo destapo catastro#8 al leer
                // por primera vez esta tabla con `LectorDeParametrosCacheados`. Al sellar el
                // conjunto, el disparador de `EscenarioDeNormativa` copia aqui todo lo que
                // `conjunto_parametro_detalle_de_prueba` compone —y compone justamente el
                // `PRUEBA:valor-de-relleno` nacional—, de modo que la fila quedaba DOS VECES con
                // la misma llave y la misma vigencia. Nadie lo veia porque nadie leia los
                // parametros de este conjunto; en cuanto la corrida los lee, la guarda de #659
                // salta con «tiene dos filas de PRUEBA:valor-de-relleno vigentes en 2026 … y
                // nadie eligio cual rige», que es exactamente lo que esa guarda existe para
                // decir. La fila manual se queda —`AislamientoMultiTenantTest` exige que esta
                // tabla tenga filas propias de cada municipalidad, y el disparador solo escribe
                // al sellar— pero con llave propia.
                "INSERT INTO normativa_parametro (municipalidad_id, conjunto_id, tipo, clave,"
                        + " valor_numerico, vigencia_desde, documento_fuente)"
                        + " VALUES (?, ?, 'PRUEBA', 'solo-de-la-cache-local', 1.000000, ?,"
                        + "         'fixture de la prueba de aislamiento')",
                muni,
                conjuntoId,
                VIGENCIA);
        // EL «% ACTUALIZACION» NO SE SIEMBRA AQUI, Y ES LA PARTE QUE HAY QUE LEER.
        //
        // Esta fixture es la copia local de un conjunto SELLADO, y `normativa` hoy no puede sellar
        // ninguno que traiga esa llave: su archivo del corpus
        // —`predial-porcentaje-de-actualizacion.md`— esta en `TRANSCRITO`, le falta la segunda
        // firma de ADR-0007, y `verificar-publicacion.mjs` no publica desde ese estado. Sembrarla
        // aqui seria describir un estado del mundo que no existe, que es la clase de mentira que
        // una fixture no puede contar: las cifras de relleno son relleno declarado, pero una
        // llave que no se puede sellar no es relleno, es una premisa falsa.
        //
        // catastro#8 llego a sembrarla apoyandose en una firma que nadie puso; la direccion lo
        // rechazo, y esto es la consecuencia medida. Quien quiera ver la corrida produciendo sus
        // cuatro cifras la siembra en SU municipalidad y lo dice —lo hace
        // `PublicacionDelPadronJdbcTest.conLaLlaveSelladaLaCorridaProduceCifras`—, en vez de
        // dejarla puesta para todas y que el padron parezca valorizable.
        ejecutar(
                app,
                "INSERT INTO normativa_valor_unitario (municipalidad_id, conjunto_id, partida,"
                        + " categoria, anio_construccion_desde, valor_m2, documento_fuente)"
                        + " VALUES (?, ?, 'MUROS', 'C', 2000, 1.000000,"
                        + "         'fixture de la prueba de aislamiento')",
                muni,
                conjuntoId);
        ejecutar(
                app,
                "INSERT INTO normativa_depreciacion (municipalidad_id, conjunto_id, uso, material,"
                        + " estado_conservacion, antiguedad_hasta, porcentaje, documento_fuente)"
                        + " VALUES (?, ?, '01', 'CONCRETO', 'BUENO', 10, 1.0000,"
                        + "         'fixture de la prueba de aislamiento')",
                muni,
                conjuntoId);
        // El anexo vehicular NO se siembra: `catastro` no cachea `normativa_valor_referencial`
        // —es la mitad del snapshot que consume `rentas`— y `V2` de este repositorio no crea esa
        // tabla. Sembrarla aqui seria pedirle a la prueba de aislamiento que censara una tabla
        // que este sistema no tiene.
    }

    // ------------------------------------------------------------------
    // Catastro
    // ------------------------------------------------------------------

    /**
     * Dos filas en {@code contribuyente_de_prueba}, para las pruebas de los disparadores de
     * titularidad (P5C).
     *
     * <p>No es el padron: es lo minimo para que una copropiedad se pueda escribir con dos
     * identificadores distintos. El padron de verdad vive en `rentas` y este sistema le pregunta
     * por HTTP; ver el javadoc de {@link EscenarioDelPadron}.
     */
    private static void sembrarDosTitularesDeMentira(Connection app, long muni, String sufijo)
            throws SQLException {
        for (String orden : new String[] {"1", "2"}) {
            ejecutar(
                    app,
                    "INSERT INTO contribuyente_de_prueba (municipalidad_id,"
                            + " codigo_contribuyente, tipo_documento, numero_documento,"
                            + " tipo_persona, nombre_razon_social, usuario_registro)"
                            + " VALUES (?, ?, 'DNI', ?, 'NATURAL', ?, 'siembra')",
                    muni,
                    "C-" + sufijo + "-" + orden,
                    "0000000" + orden,
                    "DEMO TITULAR " + sufijo + " " + orden);
        }
    }

    private static long sembrarCatastro(
            Connection app, long muni, String sufijo, long titular, long conjuntoId)
            throws SQLException {
        long viaId =
                insertar(
                        app,
                        "INSERT INTO via (municipalidad_id, codigo, tipo_via, nombre)"
                                + " VALUES (?, ?, 'AVENIDA', ?) RETURNING id",
                        muni,
                        "V-" + sufijo,
                        "Avenida Grau " + sufijo);
        long sectorId =
                insertar(
                        app,
                        "INSERT INTO sector (municipalidad_id, codigo, nombre)"
                                + " VALUES (?, ?, ?) RETURNING id",
                        muni,
                        "S-" + sufijo,
                        "Sector " + sufijo);
        long manzanaId =
                insertar(
                        app,
                        "INSERT INTO manzana (municipalidad_id, sector_id, codigo)"
                                + " VALUES (?, ?, ?) RETURNING id",
                        muni,
                        sectorId,
                        "M-" + sufijo);
        long predioId =
                insertar(
                        app,
                        "INSERT INTO predio (municipalidad_id, codigo_ref_catastral, tipo, via_id,"
                                + " direccion, sector_id, manzana_id, lote)"
                                + " VALUES (?, ?, 'URBANO', ?, ?, ?, ?, '01') RETURNING id",
                        muni,
                        codigoCatastral(sufijo),
                        viaId,
                        "Jr. Union " + sufijo,
                        sectorId,
                        manzanaId);

        long fichaId =
                insertar(
                        app,
                        "INSERT INTO ficha_catastral (municipalidad_id, predio_id, tipo, version,"
                                + " area_terreno, uso, vigencia_desde, origen, documento_origen,"
                                + " observacion, usuario_registro)"
                                + " VALUES (?, ?, 'UNICA', 1, 120.00, 'CASA_HABITACION', ?,"
                                + "         'DECLARACION_JURADA', 'DJ-001', 'ficha inicial de prueba',"
                                + "         'prueba') RETURNING id",
                        muni,
                        predioId,
                        VIGENCIA);
        ejecutar(
                app,
                "INSERT INTO construccion (municipalidad_id, ficha_id, piso, area_construida,"
                        + " anio_construccion, material_estructural, estado_conservacion,"
                        + " categoria_muros)"
                        + " VALUES (?, ?, '1', 80.00, 2010, 'CONCRETO', 'BUENO', 'C')",
                muni,
                fichaId);
        ejecutar(
                app,
                "INSERT INTO otra_instalacion (municipalidad_id, ficha_id, descripcion,"
                        + " unidad_medida, cantidad)"
                        + " VALUES (?, ?, 'Cerco perimetrico', 'ML', 25.00)",
                muni,
                fichaId);

        // El frente del predio (V6). Es la PRIMERA geometria que siembran estas fixtures, y por
        // eso lleva coordenadas de verdad: la prueba del marco de ADR-0034 filtra por un
        // rectangulo, y con la geometria nula las cuatro columnas generadas salen nulas y el
        // filtro no devolveria nada — o sea que pasaria en verde sin haber comprobado el
        // aislamiento del camino nuevo.
        //
        // Cada municipalidad cae en un GRADO distinto de longitud (`desplazamiento`), asi que sus
        // frentes no se solapan ni por casualidad: si el filtro por marco tuviera una fuga, la
        // prueba veria filas de la otra y no un empate ambiguo.
        ejecutar(
                app,
                "INSERT INTO frente_predio (municipalidad_id, predio_id, via_id, geometria,"
                        + " longitud_m, es_principal, numeracion, observacion, usuario_registro)"
                        + " VALUES (?, ?, ?,"
                        + "         ST_GeogFromText('SRID=4326;LINESTRING(' || ? || ' -4.90, '"
                        + "                          || ? || ' -4.9002)'),"
                        + "         18.50, true, '101', 'frente inicial de prueba', 'prueba')",
                muni,
                predioId,
                viaId,
                desplazamientoDe(sufijo) + " ",
                desplazamientoDe(sufijo) + " ");

        sembrarUrbano(app, muni, sufijo, viaId, predioId);
        // La gestion del riesgo (#5): una zona de peligro, una faja marginal y un ITSE.
        //
        // Se siembran aqui —y no en la prueba que las usa— por lo mismo que el frente: la prueba
        // de aislamiento recorre TODAS las tablas de tenant y exige que la municipalidad A vea
        // filas suyas. Sobre una tabla vacia, «no se ve nada de B» es cierto y no prueba nada, y
        // esa es la forma exacta en que una tabla nueva entra sin que nadie compruebe su politica.
        //
        // Los dos poligonos van sobre el MISMO desplazamiento que el frente, asi que cada
        // municipalidad tiene los suyos en un grado distinto de longitud: si el filtro por marco
        // tuviera una fuga, la prueba veria filas de la otra y no un empate ambiguo.
        ejecutar(
                app,
                "INSERT INTO zona_riesgo (municipalidad_id, codigo, fenomeno, nivel, mitigable,"
                        + " fuente, documento_origen, vigencia_desde, geometria, observacion,"
                        + " usuario_registro)"
                        + " VALUES (?, ?, 'INUNDACION', 'MUY_ALTO', false, 'CENEPRED',"
                        + "         'CARTA-DE-PRUEBA', DATE '2025-01-01',"
                        + "         ST_GeogFromText('SRID=4326;MULTIPOLYGON(((' || ? || ' -4.90,'"
                        + "                          || ? || ' -4.91, ' || ? || ' -4.91, '"
                        + "                          || ? || ' -4.90, ' || ? || ' -4.90)))'),"
                        + "         'zona de riesgo de prueba', 'prueba')",
                muni,
                "ZR-" + sufijo,
                desplazamientoDe(sufijo) + " ",
                desplazamientoDe(sufijo) + " ",
                desplazamientoDe(sufijo) + "1 ",
                desplazamientoDe(sufijo) + "1 ",
                desplazamientoDe(sufijo) + " ");
        ejecutar(
                app,
                "INSERT INTO faja_marginal (municipalidad_id, codigo, cuerpo_agua, ancho_m,"
                        + " fuente, documento_origen, vigencia_desde, geometria, observacion,"
                        + " usuario_registro)"
                        + " VALUES (?, ?, 'Rio de prueba', 25.00, 'ANA', 'RD-DE-PRUEBA',"
                        + "         DATE '2023-01-01',"
                        + "         ST_GeogFromText('SRID=4326;MULTIPOLYGON(((' || ? || ' -4.92,'"
                        + "                          || ? || ' -4.93, ' || ? || ' -4.93, '"
                        + "                          || ? || ' -4.92, ' || ? || ' -4.92)))'),"
                        + "         'faja marginal de prueba', 'prueba')",
                muni,
                "FM-" + sufijo,
                desplazamientoDe(sufijo) + " ",
                desplazamientoDe(sufijo) + " ",
                desplazamientoDe(sufijo) + "1 ",
                desplazamientoDe(sufijo) + "1 ",
                desplazamientoDe(sufijo) + " ");
        // Y el certificado, que NO tiene geometria: cuelga del predio.
        ejecutar(
                app,
                "INSERT INTO itse (municipalidad_id, predio_id, numero, nivel_riesgo, modalidad,"
                        + " vigencia_desde, vigencia_hasta, observacion, usuario_registro)"
                        + " VALUES (?, ?, ?, 'ALTO', 'PREVIA', DATE '2026-01-01',"
                        + "         DATE '2026-12-31', 'certificado de prueba', 'prueba')",
                muni,
                predioId,
                "ITSE-" + sufijo);

        // Los otros tres tipos de ficha (#19). Van sobre el mismo predio a proposito: el indice
        // parcial admite una vigente de cada tipo, y sembrarlas juntas lo comprueba de paso.
        long economica =
                insertar(
                        app,
                        "INSERT INTO ficha_catastral (municipalidad_id, predio_id, tipo, version,"
                                + " area_terreno, uso, informacion_complementaria, vigencia_desde,"
                                + " origen, documento_origen, observacion, usuario_registro)"
                                + " VALUES (?, ?, 'ECONOMICA', 1, 120.00, 'COMERCIO',"
                                + "         'ficha economica de prueba', ?, 'FISCALIZACION',"
                                + "         'ACTA-001', 'ficha economica de prueba', 'prueba')"
                                + " RETURNING id",
                        muni,
                        predioId,
                        VIGENCIA);
        ejecutar(
                app,
                "INSERT INTO actividad_economica (municipalidad_id, ficha_id, conductor,"
                        + " nombre_comercial, ciiu, licencia_numero, licencia_fecha)"
                        + " VALUES (?, ?, 'Conductor de prueba', 'Bodega de prueba', '4711',"
                        + "         'LIC-001', ?)",
                muni,
                economica,
                VIGENCIA);

        long comunes =
                insertar(
                        app,
                        "INSERT INTO ficha_catastral (municipalidad_id, predio_id, tipo, version,"
                                + " area_terreno, uso, denominacion, vigencia_desde, origen,"
                                + " documento_origen, observacion, usuario_registro)"
                                + " VALUES (?, ?, 'BIENES_COMUNES', 1, 120.00, 'MULTIFAMILIAR',"
                                + "         'Edificio de prueba', ?, 'DECLARACION_JURADA',"
                                + "         'DJ-002', 'ficha de bienes comunes de prueba', 'prueba')"
                                + " RETURNING id",
                        muni,
                        predioId,
                        VIGENCIA);
        ejecutar(
                app,
                "INSERT INTO bien_comun (municipalidad_id, ficha_id, descripcion, area,"
                        + " material_estructural, estado_conservacion)"
                        + " VALUES (?, ?, 'Escalera comun', 30.00, 'CONCRETO', 'BUENO')",
                muni,
                comunes);
        ejecutar(
                app,
                "INSERT INTO participacion_comun (municipalidad_id, ficha_id, predio_id,"
                        + " porcentaje) VALUES (?, ?, ?, 100)",
                muni,
                comunes,
                predioId);

        long rural =
                insertar(
                        app,
                        "INSERT INTO ficha_catastral (municipalidad_id, predio_id, tipo, version,"
                                + " area_terreno, uso, denominacion, vigencia_desde, origen,"
                                + " documento_origen, observacion, usuario_registro)"
                                + " VALUES (?, ?, 'RURAL', 1, 120.00, 'AGRICOLA',"
                                + "         'Fundo de prueba', ?, 'DECLARACION_JURADA', 'DJ-003',"
                                + "         'ficha rural de prueba', 'prueba') RETURNING id",
                        muni,
                        predioId,
                        VIGENCIA);
        ejecutar(
                app,
                "INSERT INTO tierra_rural (municipalidad_id, ficha_id, clasificacion, riego,"
                        + " cantidad_hectareas) VALUES (?, ?, 'CULTIVO_TRANSITORIO', 'SECANO',"
                        + "         2.5000)",
                muni,
                rural);
        ejecutar(
                app,
                "INSERT INTO colindante_rural (municipalidad_id, ficha_id, orientacion,"
                        + " descripcion) VALUES (?, ?, 'NORTE', 'Predio de prueba colindante')",
                muni,
                rural);

        ejecutar(
                app,
                "INSERT INTO documento_emitido (municipalidad_id, tipo, numero, ejercicio,"
                        + " referencia, datos, formato, resumen, fecha_emision, usuario_emision,"
                        + " observacion)"
                        + " VALUES (?, 'FICHA_CONTRIBUYENTE', 'FICHA_CONTRIBUYENTE-2026-000001',"
                        + "         2026, 'C-0001', CAST(? AS jsonb), 'PDF', repeat('a', 64),"
                        + "         ?, 'siembra', 'documento de prueba')",
                muni,
                "{\"titulo\":\"Documento de prueba\",\"subtitulo\":null,\"aLaFecha\":\"2026-01-01\","
                        + "\"cabecera\":[],\"tablas\":[],\"pie\":[],\"duplicado\":null}",
                VIGENCIA);
        ejecutar(
                app,
                "INSERT INTO titularidad (municipalidad_id, predio_id, contribuyente_id, condicion,"
                        + " porcentaje, vigencia_desde, documento_origen)"
                        + " VALUES (?, ?, ?, 'PROPIETARIO_UNICO', 100, ?, 'MINUTA-001')",
                muni,
                predioId,
                titular,
                VIGENCIA);
        ejecutar(
                app,
                "INSERT INTO inquilino (municipalidad_id, predio_id, contribuyente_id,"
                        + " vigencia_desde, documento_origen)"
                        + " VALUES (?, ?, ?, ?, 'CONTRATO-001')",
                muni,
                predioId,
                titular,
                VIGENCIA);

        // Tablas de valuacion. Valores de relleno: los normativos siguen en D-02. Cuelgan del
        // conjunto de parametros sembrado por sembrarParametros, no de un ejercicio suelto (#17).
        ejecutar(
                app,
                "INSERT INTO arancel (municipalidad_id, conjunto_id, via_id, valor_m2,"
                        + " documento_fuente)"
                        + " VALUES (?, ?, ?, 1.000000, 'fixture de la prueba')",
                muni,
                conjuntoId,
                viaId);
        // Y un SEGUNDO arancel de la misma via, este CON tramo y con otra cifra. No es adorno:
        // sin el, quitarle a `arancelSinTramoPorVia` su `AND tramo IS NULL` pasaba en VERDE
        // —medido en catastro#8— porque no habia ninguna fila con tramo que se pudiera colar. Con
        // esta fila, la misma rotura hace que la consulta devuelva DOS aranceles para la via y la
        // corrida revienta nombrando la ambiguedad, en vez de valorizar el terreno con la cifra
        // que el planificador devolviera primero.
        ejecutar(
                app,
                "INSERT INTO arancel (municipalidad_id, conjunto_id, via_id, tramo, valor_m2,"
                        + " documento_fuente)"
                        + " VALUES (?, ?, ?, 'cuadra 1', 9.000000, 'fixture de la prueba')",
                muni,
                conjuntoId,
                viaId);
        // valor_unitario_edificacion y depreciacion ya no se siembran aqui: desde D-13 (V55) son
        // nacionales, las carga rol_carga_parametros y viven en crearParametroNacional. El arancel
        // si se queda: se carga y se corrige por municipalidad.

        sembrarFiscalizacion(app, muni, sufijo, predioId, fichaId);
        return predioId;
    }

    /**
     * Las cuatro tablas de {@code V7} (#4): zonificacion, sus parametros, la seccion de via y la
     * habilitacion urbana.
     *
     * <p>Son de tenant, asi que {@code AislamientoMultiTenantTest} exige que la municipalidad A vea
     * filas suyas en las cuatro: una tabla vacia haria que «no se ve nada de B» fuera cierto sin
     * probar nada, que es el modo de fallo contra el que existe esa prueba.
     *
     * <p><b>La zona se dibuja alrededor del frente del predio</b>, sobre el mismo grado de longitud
     * que {@code desplazamientoDe} le dio a esta municipalidad. Dos motivos, y los dos se miden:
     * asi el predio de A cae DENTRO de la zona de A —que es lo que hace util la consulta de
     * contencion en la prueba de frontera— y las zonas de las dos municipalidades no se solapan ni
     * por casualidad, de modo que una fuga del filtro por marco se veria como filas de la otra y no
     * como un empate ambiguo.
     */
    private static void sembrarUrbano(
            Connection app, long muni, String sufijo, long viaId, long predioId)
            throws SQLException {
        String x = desplazamientoDe(sufijo);
        long zonaId =
                insertar(
                        app,
                        "INSERT INTO zonificacion (municipalidad_id, plan, ordenanza, codigo,"
                                + " nombre, geometria, vigencia_desde, observacion,"
                                + " usuario_registro)"
                                + " VALUES (?, ?, ?, 'RDM', 'Residencial de densidad media',"
                                + "  ST_GeogFromText('SRID=4326;MULTIPOLYGON(((' || ? || ' -4.91,'"
                                + "   || ? || ' -4.91,' || ? || ' -4.89,' || ? || ' -4.89,'"
                                + "   || ? || ' -4.91)))'),"
                                + "  ?, 'zonificacion inicial de prueba', 'prueba') RETURNING id",
                        muni,
                        "PDU-" + sufijo,
                        "ORD-001-" + sufijo,
                        aLaIzquierda(x),
                        aLaDerecha(x),
                        aLaDerecha(x),
                        aLaIzquierda(x),
                        aLaIzquierda(x),
                        VIGENCIA);
        ejecutar(
                app,
                "INSERT INTO parametro_urbanistico (municipalidad_id, zonificacion_id, clave,"
                        + " valor, unidad, observacion, usuario_registro)"
                        + " VALUES (?, ?, 'altura_maxima', '5', 'pisos',"
                        + "         'parametro inicial de prueba', 'prueba')",
                muni,
                zonaId);
        ejecutar(
                app,
                "INSERT INTO seccion_via (municipalidad_id, via_id, tramo, plan, ordenanza,"
                        + " clasificacion, ancho_via_m, ancho_calzada_m, ancho_vereda_m, retiro_m,"
                        + " vigencia_desde, observacion, usuario_registro)"
                        + " VALUES (?, ?, ?, ?, ?, 'COLECTORA', 20.00, 12.00, 3.00, 3.00, ?,"
                        + "         'seccion inicial de prueba', 'prueba')",
                muni,
                viaId,
                "cuadra 1 " + sufijo,
                "PDU-" + sufijo,
                "ORD-001-" + sufijo,
                VIGENCIA);
        ejecutar(
                app,
                "INSERT INTO habilitacion_urbana (municipalidad_id, codigo, denominacion,"
                        + " resolucion, fecha_resolucion, estado, lotes_aprobados, area_bruta_m2,"
                        + " geometria, observacion, usuario_registro)"
                        + " VALUES (?, ?, ?, ?, ?, 'RECEPCIONADA', 40, 12000.00,"
                        + "  ST_GeogFromText('SRID=4326;MULTIPOLYGON(((' || ? || ' -4.91,'"
                        + "   || ? || ' -4.91,' || ? || ' -4.89,' || ? || ' -4.89,'"
                        + "   || ? || ' -4.91)))'),"
                        + "  'habilitacion inicial de prueba', 'prueba')",
                muni,
                "HU-" + sufijo,
                "Urbanizacion de prueba " + sufijo,
                "RES-010-" + sufijo,
                VIGENCIA,
                aLaIzquierda(x),
                aLaDerecha(x),
                aLaDerecha(x),
                aLaIzquierda(x),
                aLaIzquierda(x));
        // Constancia de que el identificador encadenado se uso.
        if (zonaId <= 0) {
            throw new IllegalStateException("No se sembro ninguna zona");
        }
    }

    /** Un grado a la izquierda del desplazamiento de esta municipalidad. */
    private static String aLaIzquierda(String desplazamiento) {
        return Double.toString(Double.parseDouble(desplazamiento) - 0.5) + " ";
    }

    /** Y medio grado a la derecha: la zona envuelve al frente del predio y no llega a la vecina. */
    private static String aLaDerecha(String desplazamiento) {
        return Double.toString(Double.parseDouble(desplazamiento) + 0.5) + " ";
    }

    /**
     * La longitud en que caen los frentes de este tenant, para que dos municipalidades no compartan
     * rectangulo.
     *
     * <p>Un grado de longitud por sufijo: separa los marcos lo bastante como para que una fuga se
     * vea como filas de la otra municipalidad y no como un empate que hay que interpretar. El
     * entorno es Sullana, asi que se parte de -80.
     */
    /**
     * Las cinco tablas del hallazgo catastral (V9, ADR-0035, #6).
     *
     * <p>Son de tenant, asi que {@code AislamientoMultiTenantTest} exige que la municipalidad A vea
     * filas suyas en las cinco: una tabla vacia haria que «no se ve nada de B» fuera cierto sin
     * probar nada, que es el modo de fallo contra el que esa prueba existe. Sin esto la migracion
     * salia en verde y el aislamiento de las cinco no lo comprobaba nadie — medido: cinco rojos,
     * uno por tabla, con «la municipalidad A debe ver sus propias filas».
     *
     * <p>El candidato y el hallazgo llevan <b>geometria de verdad</b>, y por el mismo motivo que el
     * frente de predio: con la geometria nula las cuatro columnas del marco (ADR-0034) salen nulas
     * y un filtro por marco no devolveria nada, o sea que pasaria en verde sin comprobar el camino
     * que ADR-0034 obliga a usar. Cada municipalidad cae en un grado distinto de longitud.
     *
     * <p>El candidato se siembra <b>ya verificado en campo</b> porque el hallazgo cuelga de el: es
     * la unica forma de que las dos filas existan a la vez, y de paso deja escrito en la fixture
     * que un hallazgo sin candidato verificado no se puede escribir.
     */
    private static void sembrarFiscalizacion(
            Connection app, long muni, String sufijo, long predioId, long fichaId)
            throws SQLException {
        long campaniaId =
                insertar(
                        app,
                        "INSERT INTO campania (municipalidad_id, codigo, nombre, inicio, umbral,"
                                + " observacion, usuario_registro)"
                                + " VALUES (?, ?, ?, ?, 0.7000, 'campania de prueba', 'prueba')"
                                + " RETURNING id",
                        muni,
                        "CAM-" + sufijo,
                        "Campania de prueba " + sufijo,
                        VIGENCIA);

        long candidatoId =
                insertar(
                        app,
                        "INSERT INTO candidato (municipalidad_id, campania_id, predio_id, clase,"
                                + " origen, score, insumos, geometria, estado, observacion,"
                                + " usuario_registro)"
                                + " VALUES (?, ?, ?, 'SUBVALUADOR', 'ORTOFOTO', 0.9100,"
                                + "         '{\"fuente\":\"fixture\"}'::jsonb,"
                                + "         ST_GeogFromText('SRID=4326;MULTIPOLYGON(((' || ? || ' -4.90,'"
                                + "                          || ? || ' -4.9002,' || ? || ' -4.9002,'"
                                + "                          || ? || ' -4.90,' || ? || ' -4.90)))'),"
                                + "         'VERIFICADO_EN_CAMPO', 'candidato de prueba', 'prueba')"
                                + " RETURNING id",
                        muni,
                        campaniaId,
                        predioId,
                        desplazamientoDe(sufijo) + " ",
                        desplazamientoDe(sufijo) + " ",
                        desplazamientoDe(sufijo) + "01 ",
                        desplazamientoDe(sufijo) + "01 ",
                        desplazamientoDe(sufijo) + " ");

        long hallazgoId =
                insertar(
                        app,
                        "INSERT INTO hallazgo (municipalidad_id, candidato_id, clase, predio_id,"
                                + " ficha_id, area_de_la_ficha, area_verificada, inspector,"
                                + " verificado_en, geometria, observacion, usuario_registro)"
                                + " VALUES (?, ?, 'SUBVALUADOR', ?, ?, 120.00, 180.00,"
                                + "         'inspector.prueba', ?,"
                                + "         ST_GeogFromText('SRID=4326;MULTIPOLYGON(((' || ? || ' -4.90,'"
                                + "                          || ? || ' -4.9002,' || ? || ' -4.9002,'"
                                + "                          || ? || ' -4.90,' || ? || ' -4.90)))'),"
                                + "         'hallazgo de prueba', 'prueba')"
                                + " RETURNING id",
                        muni,
                        candidatoId,
                        predioId,
                        fichaId,
                        VIGENCIA,
                        desplazamientoDe(sufijo) + " ",
                        desplazamientoDe(sufijo) + " ",
                        desplazamientoDe(sufijo) + "01 ",
                        desplazamientoDe(sufijo) + "01 ",
                        desplazamientoDe(sufijo) + " ");

        // La huella lleva el sufijo dentro: `evidencia_sha256_uq` es POR municipalidad, asi que dos
        // huellas iguales en dos municipalidades no chocarian — y justamente por eso se siembran
        // distintas, para que la prueba de aislamiento no pueda confundir «no lo veo» con «choco».
        ejecutar(
                app,
                "INSERT INTO evidencia (municipalidad_id, hallazgo_id, tipo, sha256, ruta,"
                        + " capturado_en, recibido_en, dispositivo, observacion, usuario_registro)"
                        + " VALUES (?, ?, 'FOTO', ?, ?, now() - interval '2 hours', now(),"
                        + "         'tableta-01', 'evidencia de prueba', 'prueba')",
                muni,
                hallazgoId,
                huellaDePrueba(sufijo),
                "s3://evidencias/" + sufijo + "/foto-01.jpg");

        ejecutar(
                app,
                "INSERT INTO acta (municipalidad_id, numero, hallazgo_id, fecha, inspector,"
                        + " detalle, observacion, usuario_registro)"
                        + " VALUES (?, ?, ?, ?, 'inspector.prueba', 'acta de prueba',"
                        + "         'acta de prueba', 'prueba')",
                muni,
                "ACT-" + sufijo + "-001",
                hallazgoId,
                VIGENCIA);
    }

    /** Sesenta y cuatro hexadigitos distintos por municipalidad. No es un sha256 de nada. */
    private static String huellaDePrueba(String sufijo) {
        String semilla = Integer.toHexString(Math.floorMod(sufijo.hashCode(), 16));
        return semilla.repeat(64);
    }

    private static String desplazamientoDe(String sufijo) {
        return "-8" + (Math.floorMod(sufijo.hashCode(), 9) + 1) + ".0";
    }

    /**
     * Codigo de referencia catastral de relleno.
     *
     * <p>Su longitud ya no es una ambiguedad: ADR-0036 declara este codigo <b>municipal y de largo
     * configurable por municipalidad</b>, y saca de el la identidad del SNCP, que es la que tiene
     * doce posiciones fijas (`predio.cuc`, V6).
     */
    private static String codigoCatastral(String sufijo) {
        String digitos = Integer.toString(Math.abs(sufijo.hashCode() % 100) + 10);
        return ("2006010101500101010" + digitos + "000000").substring(0, 21);
    }

    // ------------------------------------------------------------------
    // Rentas y cuenta corriente
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Tesoreria
    // ------------------------------------------------------------------

    /**
     * La serie de la caja sembrada, {@code varchar(5)} y unica por municipalidad (V29).
     *
     * <p>Se deriva del sufijo del tenant en lugar de ser un {@code '001'} fijo: la unicidad es por
     * municipalidad, asi que un literal serviria igual, pero sembrar dos cajas en un mismo tenant
     * -que es lo que hara la prueba de la caja- chocaria, y el fallo apareceria como un choque de
     * clave unica en el fixture en lugar de como lo que es.
     */

    // ------------------------------------------------------------------
    // Valores y coactiva
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Sanciones y fiscalizacion
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Licencias
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Seguridad y auditoria
    // ------------------------------------------------------------------

    private static void sembrarSeguridad(Connection app, long muni, String sufijo)
            throws SQLException {
        long moduloId =
                insertar(
                        app,
                        "INSERT INTO modulo_sistema (municipalidad_id, codigo, nombre)"
                                + " VALUES (?, ?, 'Rentas') RETURNING id",
                        muni,
                        "MOD-" + sufijo);
        long accesoId =
                insertar(
                        app,
                        "INSERT INTO acceso (municipalidad_id, modulo_id, tipo, codigo, nombre)"
                                + " VALUES (?, ?, 'OPCION_MENU', ?, 'Contribuyentes') RETURNING id",
                        muni,
                        moduloId,
                        "contribuyentes-" + sufijo);
        long grupoId =
                insertar(
                        app,
                        "INSERT INTO grupo (municipalidad_id, nombre, descripcion)"
                                + " VALUES (?, ?, 'Grupo de prueba') RETURNING id",
                        muni,
                        "Cajeros " + sufijo);
        long usuarioId =
                insertar(
                        app,
                        "INSERT INTO usuario (municipalidad_id, cuenta, nombre)"
                                + " VALUES (?, ?, 'Usuario de prueba') RETURNING id",
                        muni,
                        "usuario-" + sufijo);
        ejecutar(
                app,
                "INSERT INTO miembro (municipalidad_id, grupo_id, usuario_id, usuario_alta)"
                        + " VALUES (?, ?, ?, 'prueba')",
                muni,
                grupoId,
                usuarioId);
        ejecutar(
                app,
                "INSERT INTO permiso (municipalidad_id, acceso_id, grupo_id, lectura, registro,"
                        + " usuario_registro) VALUES (?, ?, ?, true, true, 'prueba')",
                muni,
                accesoId,
                grupoId);
        ejecutar(
                app,
                "INSERT INTO sesion (municipalidad_id, usuario_id, origen_equipo, origen_ip,"
                        + " ejercicio_trabajo)"
                        + " VALUES (?, ?, 'PC-PRUEBA', CAST(? AS inet), ?)",
                muni,
                usuarioId,
                "10.0.0.1",
                EJERCICIO);
        ejecutar(
                app,
                "INSERT INTO auditoria (municipalidad_id, ejercicio, tabla, clave, operacion,"
                        + " usuario_id, origen_equipo, origen_ip, observacion)"
                        + " VALUES (?, ?, 'via', '1', 'ALTA', 'prueba', 'PC-PRUEBA',"
                        + "         CAST(? AS inet), 'alta inicial de la prueba de aislamiento')",
                muni,
                EJERCICIO,
                "10.0.0.1");
        sembrarBuzonDeSalida(app, muni, sufijo);
    }

    /**
     * Un hecho en el buzon de salida de C-8, para que el aislamiento de {@code catastro_evento} se
     * pueda medir.
     *
     * <p>La prueba de aislamiento no comprueba solo que A no vea filas de B: comprueba
     * <b>ademas</b> que A vea las suyas, y sin una fila sembrada las dos cosas darian cero y se
     * leerian igual.
     *
     * <p>El {@code evento_id} lleva el sufijo de la municipalidad dentro: dos municipalidades con
     * el mismo uuid seria una fuga que la clave unica —que es POR municipalidad— dejaria pasar sin
     * ruido.
     *
     * <p>Nace ENTREGADO y no PENDIENTE, y eso tambien lo decidio ejecutar: con la fila pendiente,
     * toda prueba que lea el buzon de salida —el publicador, el controlador del feed— se encuentra
     * dentro un hecho de mentira que ella no produjo. Entregado, la fila sigue estando para que el
     * aislamiento se pueda medir y no aparece en ninguna lectura de lo pendiente.
     *
     * <p>Es un cierre de corrida y no una proyeccion de predio, y eso lo decidio el propio motor:
     * con {@code PREDIO_PROYECTADO} y {@code predio_id} nulo, {@code catastro_evento_predio_ck}
     * rechazo la siembra en las cuatro clases que la usan — la primera vez que ese CHECK vio una
     * fila. Un cierre no habla de ningun predio, asi que aqui no hay que inventar uno.
     */
    private static void sembrarBuzonDeSalida(Connection app, long muni, String sufijo)
            throws SQLException {
        // La huella es de mentira y no dice nada de nadie: lo que esta fila mide es la politica
        // RLS de la tabla, no que el contenido signifique algo.
        String huella = "0".repeat(64);
        ejecutar(
                app,
                "INSERT INTO catastro_evento (municipalidad_id, evento_id, tipo, predio_id,"
                        + " ejercicio, cuerpo, huella, estado, creado_en, entregado_en)"
                        + " VALUES (?, CAST(? AS uuid), 'CORRIDA_CERRADA', NULL, ?,"
                        + "         CAST(? AS jsonb), ?, 'ENTREGADO', now(), now())",
                muni,
                uuidDeterminista(sufijo),
                EJERCICIO,
                "{\"prueba\": \"aislamiento\"}",
                huella);
    }

    /**
     * Un uuid estable derivado del sufijo de la municipalidad, para no depender del azar en una
     * siembra que se compara entre dos.
     */
    private static String uuidDeterminista(String sufijo) {
        return java.util
                .UUID
                .nameUUIDFromBytes(
                        ("catastro-evento-" + sufijo)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }

    /**
     * Identificador de la via sembrada en una municipalidad.
     *
     * <p>Sustituye a {@code contribuyenteDe}, que era lo que la prueba de aislamiento usaba en el
     * monolito: el padron se fue a `rentas` con P5C, y lo que hace falta aqui es una fila de tenant
     * cualquiera de ESTE sistema con la que probar el UPDATE ajeno.
     */
    public static long viaDe(BaseDeDatosDePrueba base, long municipalidadId) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                PreparedStatement sentencia =
                        admin.prepareStatement(
                                "SELECT id FROM via WHERE municipalidad_id = ?"
                                        + " ORDER BY id LIMIT 1")) {
            sentencia.setLong(1, municipalidadId);
            return unicoLong(sentencia);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static long insertar(Connection conexion, String sql, Object... valores)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            fijar(sentencia, valores);
            return unicoLong(sentencia);
        }
    }

    private static void ejecutar(Connection conexion, String sql, Object... valores)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            fijar(sentencia, valores);
            sentencia.executeUpdate();
        }
    }

    private static void fijar(PreparedStatement sentencia, Object... valores) throws SQLException {
        for (int i = 0; i < valores.length; i++) {
            sentencia.setObject(i + 1, valores[i]);
        }
    }

    private static long unicoLong(PreparedStatement sentencia) throws SQLException {
        try (ResultSet resultado = sentencia.executeQuery()) {
            if (!resultado.next()) {
                throw new IllegalStateException("La sentencia no devolvio ninguna fila");
            }
            return resultado.getLong(1);
        }
    }
}
