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
                "INSERT INTO normativa_parametro (municipalidad_id, conjunto_id, tipo, clave,"
                        + " valor_numerico, vigencia_desde, documento_fuente)"
                        + " VALUES (?, ?, 'PRUEBA', 'valor-de-relleno', 1.000000, ?,"
                        + "         'fixture de la prueba de aislamiento')",
                muni,
                conjuntoId,
                VIGENCIA);
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
        // valor_unitario_edificacion y depreciacion ya no se siembran aqui: desde D-13 (V55) son
        // nacionales, las carga rol_carga_parametros y viven en crearParametroNacional. El arancel
        // si se queda: se carga y se corrige por municipalidad.
        return predioId;
    }

    /** Codigo de referencia catastral de relleno; la longitud exacta es D-10. */
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
