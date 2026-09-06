package kamayuk.catastro.verificaciones;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kamayuk.comun.verificaciones.ConfiguracionDeLasVerificaciones;

/**
 * Lo que {@code sgtm} declara de si mismo a las barreras de {@code comun-verificaciones}.
 *
 * <p>La descubre {@link java.util.ServiceLoader}: el descriptor esta en {@code
 * src/test/resources/META-INF/services/}. Si se borra, las barreras no corren en silencio — fallan
 * nombrando lo que falta, que es lo que este mecanismo compra frente a pasar la configuracion por
 * constructor.
 *
 * <h2>Por que aqui el sistema depende del ARCHIVO y no del repositorio</h2>
 *
 * <p>{@code sgtm} es el monolito: los cuatro sistemas futuros conviven en el, y sus 132 tablas
 * estan en la misma base. Declarar «este repositorio es rentas» acusaria a {@code
 * kamayuk-catastro-nucleo} de leer sus propias tablas; declarar «es catastro» dejaria pasar todo lo
 * demas. Lo que hay es un reparto por modulo Gradle —GOB-05 §1— y eso es {@link
 * #sistemaDelArchivo(String)}.
 *
 * <p>Es lo que hace que {@code NINGUN_SQL_CRUZA_LA_FRONTERA_DE_SISTEMA} sirva <b>antes</b> del
 * corte: encuentra los cruces de GOB-05 §6 hoy, con todo junto y funcionando, que es la unica
 * ventana en la que arreglarlos cuesta barato.
 */
public final class ConfiguracionDeCatastro implements ConfiguracionDeLasVerificaciones {

    /**
     * El reparto por modulo Gradle (GOB-05 §1).
     *
     * <p>Los cinco que no son contextos acotados —{@code dominio-compartido}, {@code esquema},
     * {@code plataforma}, {@code seguridad} y {@code aplicacion}— van a {@link #SISTEMA_REPLICADO}:
     * no estan a ningun lado de la frontera, asi que no pueden cruzarla. {@code
     * kamayuk-catastro-esquema} entra ahi por el mismo motivo y por uno mas: sus migraciones crean
     * las tablas de los cuatro, y ADR-0032 §1 dice que no se reparten sino que se rehacen como un
     * baseline por sistema.
     */
    private static final Map<String, String> SISTEMA_DEL_MODULO =
            Map.ofEntries(
                    Map.entry("kamayuk-catastro-contribuyentes", "rentas"),
                    Map.entry("kamayuk-catastro-nucleo", "catastro"),
                    Map.entry("kamayuk-catastro-rentas", "rentas"),
                    Map.entry("kamayuk-catastro-parametros", "normativa"),
                    // CATASTRO desde #6, y hasta entonces era una entrada MUERTA heredada del
                    // monolito: no habia ningun modulo con ese nombre. Ahora lo hay, y es de este
                    // sistema —el hallazgo catastral de ADR-0035, que no liquida nada—, asi que
                    // dejarla en `rentas` habria acusado a sus repositorios de cruzar la frontera
                    // por leer `predio` y `ficha_catastral`, que son suyas.
                    Map.entry("kamayuk-catastro-fiscalizacion", "catastro"),
                    Map.entry("kamayuk-catastro-sanciones", "rentas"),
                    Map.entry("kamayuk-catastro-cuentacorriente", "rentas"),
                    // Se PARTE: 84 clases a caja y 33 a rentas (GOB-05 §1.3). El modulo se declara
                    // `caja` porque es la mayoria, y las 33 del convenio se nombran una a una en
                    // CLASES_QUE_NO_SIGUEN_A_SU_MODULO. Al reves seria peor: dejaria sin vigilar
                    // los ocho cruces de caja hacia rentas, que son los que ADR-0026 convierte en
                    // dos COMMIT.
                    Map.entry("kamayuk-catastro-tesoreria", "caja"),
                    Map.entry("kamayuk-catastro-valores", "rentas"),
                    Map.entry("kamayuk-catastro-coactiva", "rentas"),
                    Map.entry("kamayuk-catastro-licencias", "rentas"),
                    Map.entry("kamayuk-catastro-indicadores", "rentas"),
                    Map.entry("kamayuk-catastro-dominio-compartido", SISTEMA_REPLICADO),
                    Map.entry("kamayuk-catastro-esquema", SISTEMA_REPLICADO),
                    Map.entry("kamayuk-catastro-plataforma", SISTEMA_REPLICADO),
                    Map.entry("kamayuk-catastro-seguridad", SISTEMA_REPLICADO),
                    Map.entry("kamayuk-catastro-aplicacion", SISTEMA_REPLICADO));

    /**
     * Las clases de {@code kamayuk-catastro-tesoreria} que se van a {@code rentas} con el convenio.
     *
     * <p>GOB-05 §1.3 las lista todas; aqui solo hacen falta las que escriben SQL, que son sus dos
     * repositorios. Sin esta lista, {@code ConvenioRepositoryJdbc} saldria acusado de leer {@code
     * contribuyente} —y GOB-05 §6.9 ya midio que eso NO es un cruce: el convenio y el padron van
     * los dos a {@code rentas}—.
     */
    private static final Map<String, String> CLASES_QUE_NO_SIGUEN_A_SU_MODULO =
            Map.of(
                    "ConvenioRepositoryJdbc", "rentas",
                    "MovimientoDeConvenioRepositoryJdbc", "rentas");

    private static final Set<String> DE_RENTAS =
            Set.of(
                    "acta_fiscalizacion",
                    "acto_coactivo",
                    "anuncio",
                    "anuncio_correlativo",
                    "anuncio_movimiento",
                    "beneficio",
                    "certificado",
                    "certificado_correlativo",
                    "ciiu",
                    "codigo_infraccion",
                    "constancia_libre",
                    "contacto",
                    "contribuyente",
                    "convenio",
                    "convenio_correlativo",
                    "convenio_cuota",
                    "convenio_deuda",
                    "convenio_movimiento",
                    "corrida_predial",
                    "corrida_predial_observado",
                    "costa_obligacion",
                    "costa_procesal",
                    "cuenta_corriente_asiento",
                    "cuenta_corriente_asiento_2026",
                    "cuenta_corriente_asiento_2027",
                    "declaracion_jurada",
                    "descargo",
                    "determinacion",
                    "determinacion_2026",
                    "determinacion_2027",
                    "determinacion_arbitrio",
                    "determinacion_arbitrio_2026",
                    "determinacion_arbitrio_2027",
                    "determinacion_predio_detalle",
                    "determinacion_predio_detalle_2026",
                    "determinacion_predio_detalle_2027",
                    "dj_correlativo",
                    "domicilio",
                    "edificacion_correlativo",
                    "edificacion_estructura",
                    "edificacion_movimiento",
                    "edificacion_profesional",
                    "edificacion_proyecto",
                    "edificacion_requisito",
                    "edificacion_terreno",
                    "edificacion_vigencia",
                    "espectaculo",
                    "expediente_coactivo",
                    "expediente_correlativo",
                    "expediente_movimiento",
                    "expediente_valor",
                    "internamiento",
                    "internamiento_movimiento",
                    "licencia_correlativo",
                    "licencia_duplicado",
                    "licencia_edificacion",
                    "licencia_funcionamiento",
                    "licencia_giro",
                    "licencia_movimiento",
                    "liquidacion_correlativo",
                    "liquidacion_costas",
                    "liquidacion_costas_correlativo",
                    "liquidacion_detalle",
                    "liquidacion_fiscalizacion",
                    "liquidacion_movimiento",
                    "notificacion",
                    "notificacion_administrativa",
                    "papeleta",
                    "papeleta_cambio_numero",
                    "papeleta_masivo",
                    "papeleta_masivo_item",
                    "prescripcion",
                    "prescripcion_ejercicio",
                    "prescripcion_hecho",
                    "programa_fiscalizacion",
                    "programa_muestra",
                    "resolucion_determinacion",
                    "resolucion_gerencia",
                    "responsable_solidario",
                    "saldo_proyectado",
                    "transferencia",
                    "valor",
                    "valor_correlativo",
                    "valor_detalle",
                    "valor_masivo",
                    "valor_masivo_item",
                    "valor_movimiento",
                    "vehiculo");

    private static final Set<String> DE_CATASTRO =
            Set.of(
                    "actividad_economica",
                    // V9 (#6, ADR-0035): las cinco del hallazgo catastral. `acta` es la de
                    // CATASTRO y no `acta_fiscalizacion`, que es de `rentas` y sigue siendo suya.
                    "acta",
                    "arancel",
                    "campania",
                    "candidato",
                    "bien_comun",
                    "colindante_rural",
                    "construccion",
                    "evidencia",
                    "ficha_catastral",
                    // V6: el frente del predio. Se nombra aunque este sistema no la tenga —y por
                    // eso
                    // mismo—: sin la entrada, el reparto la da por «replicada» y el escaner de la
                    // regla 11 DEJA DE MIRAR un cruce contra ella, en verde (la leccion de R-N).
                    "frente_predio",
                    "hallazgo",
                    "inquilino",
                    "manzana",
                    "otra_instalacion",
                    "participacion_comun",
                    "predio",
                    "sector",
                    "tierra_rural",
                    "titularidad",
                    "via");

    private static final Set<String> DE_NORMATIVA =
            Set.of(
                    "conjunto_parametro_detalle",
                    "conjunto_parametros",
                    "depreciacion",
                    "parametro_tributario",
                    "valor_referencial_vehiculo",
                    "valor_unitario_edificacion");

    private static final Set<String> DE_CAJA =
            Set.of(
                    "area",
                    "caja",
                    "cierre_caja",
                    "cierre_turno",
                    "cierre_turno_detalle",
                    "recibo",
                    "recibo_correlativo",
                    "recibo_detalle",
                    "recibo_movimiento",
                    "tasa");

    /** Transversales (§2.5) y las siete de seguridad (§2.6): se replican en los cuatro. */
    private static final Set<String> REPLICADAS =
            Set.of(
                    "acceso",
                    "auditoria",
                    "auditoria_2026",
                    "auditoria_2027",
                    "documento_emitido",
                    "grupo",
                    "miembro",
                    "modulo_sistema",
                    "municipalidad",
                    "permiso",
                    "respaldo",
                    "sesion",
                    "usuario");

    @Override
    public String paqueteRaiz() {
        return "kamayuk.catastro";
    }

    /**
     * No hay uno solo: {@code sgtm} es el monolito.
     *
     * <p>Se devuelve {@code rentas} porque es el que se lleva 88 de las 132 tablas, pero lo que de
     * verdad decide es {@link #sistemaDelArchivo(String)}. Este valor solo se usa si alguien lo
     * pregunta sin dar un archivo, y ahi la respuesta menos equivocada es la mayoritaria.
     */
    @Override
    public String sistema() {
        return "catastro";
    }

    /**
     * La raiz de la API de este sistema tras el corte (ADR-0030): {@code /catastro/api/v1}.
     *
     * <p>Es {@link kamayuk.catastro.web.Api#RAIZ}, y se deriva de ahi en vez de repetirse: el
     * camino base vive en un solo sitio, y la regla del centinela del ciudadano compara contra el
     * mismo que publican los controladores.
     */
    @Override
    public String raizDeLaApi() {
        return kamayuk.catastro.web.Api.RAIZ;
    }

    @Override
    public String sistemaDelArchivo(String rutaRelativa) {
        String normalizada = rutaRelativa.replace('\\', '/');
        String clase = claseDe(normalizada);
        String porClase = CLASES_QUE_NO_SIGUEN_A_SU_MODULO.get(clase);
        if (porClase != null) {
            return porClase;
        }
        int barra = normalizada.indexOf('/');
        String modulo = barra < 0 ? normalizada : normalizada.substring(0, barra);
        return SISTEMA_DEL_MODULO.getOrDefault(modulo, SISTEMA_REPLICADO);
    }

    private static String claseDe(String ruta) {
        int barra = ruta.lastIndexOf('/');
        String nombre = barra < 0 ? ruta : ruta.substring(barra + 1);
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? nombre : nombre.substring(0, punto);
    }

    @Override
    public Set<String> modulosDelReparto() {
        return SISTEMA_DEL_MODULO.keySet();
    }

    @Override
    public Map<String, String> sistemaDeCadaTabla() {
        Map<String, String> reparto = new HashMap<>();
        DE_RENTAS.forEach(t -> reparto.put(t, "rentas"));
        DE_CATASTRO.forEach(t -> reparto.put(t, "catastro"));
        DE_NORMATIVA.forEach(t -> reparto.put(t, "normativa"));
        DE_CAJA.forEach(t -> reparto.put(t, "caja"));
        REPLICADAS.forEach(t -> reparto.put(t, SISTEMA_REPLICADO));
        return Map.copyOf(reparto);
    }

    @Override
    public List<CruceConsentido> crucesConsentidos() {
        return CrucesConsentidosDelSgtm.LISTA;
    }

    @Override
    public Set<String> tablasProtegidas() {
        return TablasDelSgtm.PROTEGIDAS;
    }

    @Override
    public Set<String> tablasInmutables() {
        return TablasDelSgtm.INMUTABLES;
    }

    /**
     * Los dos envoltorios de decimal que anade este sistema (#6).
     *
     * <p>La regla existe para que ninguna regla de negocio maneje {@code BigDecimal} suelto, no
     * para impedir que el tipo que lo guarda pueda devolverlo — su propio javadoc lo dice—. {@link
     * kamayuk.catastro.fiscalizacion.dominio.Score} y {@link
     * kamayuk.catastro.fiscalizacion.dominio.Tolerancia} son exactamente eso: dos fracciones de 0 a
     * 1 con su rango comprobado en el constructor, y la alternativa —pasarlas como {@code
     * BigDecimal}— es justo lo que la regla quiere impedir, porque a la vista 0,10 y 10 son la
     * misma tolerancia escrita de dos maneras.
     *
     * <p><b>Ninguna de las dos es una cifra tributaria</b> (regla 5): no entran en nada que se
     * cobre. Lo unico que deciden es a quien mira primero una persona.
     */
    @Override
    public Set<String> envoltoriosDeDecimal() {
        Set<String> heredados = ConfiguracionDeLasVerificaciones.super.envoltoriosDeDecimal();
        Set<String> propios =
                Set.of(".fiscalizacion.dominio.Score", ".fiscalizacion.dominio.Tolerancia");
        return java.util.stream.Stream.concat(heredados.stream(), propios.stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<String> componenElAreaAManoConMotivo() {
        return Set.of(
                // El modelo del papel de la ficha del contribuyente: la unidad va en el rotulo
                // de la fila, no dentro de la celda. Los otros tres modelos que esta lista tenia
                // en el monolito —FUE, licencia y resolucion de determinacion— son de `rentas` y
                // `licencias`, y aqui declararlos seria una entrada muerta: la lista es la de
                // este sistema.
                "ModeloDeLaFichaDelContribuyente",
                // La descripcion que va a la columna JSON de la auditoria. El motivo no es «no
                // llega al cliente» —la bitacora la publica verbatim— sino que ahi el area no es
                // un campo tipado sino una instantanea de texto libre, y se escribe SIN la unidad
                // para que diga lo mismo que el resto (#607).
                "ActualizarFichaCatastral",
                // El componedor de hechos del buzon de salida (C-8). Misma forma que las dos de
                // arriba y el mismo motivo que la lista declara: el area se compone a mano SOLO
                // para la huella del hecho, que es un resumen criptografico y no pasa por ningun
                // serializador. El JSON del evento SI lo escribe `ConfiguracionDeJson`, con el
                // `AreaM2` tipado. Y escribe la cifra sola: con la unidad dentro, la huella
                // dejaria de poder compararse contra nada que hable de la misma area.
                "ComponedorDeHechos",
                // Los dos JSON escritos a mano de la fiscalizacion catastral (#6), y por el mismo
                // motivo que `ActualizarFichaCatastral`: ahi el area no es un campo tipado sino una
                // INSTANTANEA DE TEXTO LIBRE, y se escribe sin la unidad para que diga lo mismo que
                // el resto.
                //
                // `DetectarSubvaluadores` compone los `insumos` del candidato: es el registro de
                // por
                // que se sospecho, y tiene que poder explicarse solo dentro de un ano —cuando la
                // ficha ya este versionada tres veces y el area de entonces no exista en ninguna
                // parte—. `VerificarEnCampo` compone el «antes/despues» de la bitacora, que es el
                // mismo caso.
                //
                // Lo que SI va tipado es `HallazgoResource`: sus tres areas son `AreaM2` y las
                // escribe el serializador de `ConfiguracionDeJson`, que es donde #607 dice que
                // tienen que escribirse.
                "DetectarSubvaluadores",
                "VerificarEnCampo");
    }

    /**
     * Los ambitos de ADR-0024 sin una sola clase en este sistema (P5C).
     *
     * <p>`catastro` tiene el ambito de la VALUACION y ninguno de los otros: no fiscaliza, no cobra,
     * no sanciona y no lleva libro. Declararlos aqui no es callarlos: la regla acotada de
     * ArquitecturaTest exige que <b>o</b> haya clases <b>o</b> el ambito este en esta lista, y una
     * regla que no revisa nada y pasa en verde es lo que este censo existe para impedir.
     */
    @Override
    public Set<String> ambitosAusentes() {
        return Set.of(
                // `fiscalizacion` SALE de esta lista con #6. Estuvo aqui mientras este sistema no
                // tenia contexto de fiscalizacion catastral, y mientras estuvo,
                // NINGUN_HALLAZGO_CORRIGE_LA_FICHA y
                // SOLO_LA_TRANSFERENCIA_ESCRIBE_FUERA_DE_FISCALIZACION vivian aqui SOLO de su clase
                // de muestra. Desde que el modulo existe, las dos miran codigo real — y el censo de
                // `ArquitecturaTestBase` no deja quitarlo a medias: declarar ausente un ambito que
                // SI tiene clases sale rojo, y declararlo presente sin ellas tambien.
                "indicadores",
                "cuentacorriente",
                "tesoreria",
                "sanciones",
                "coactiva",
                "valores",
                "licencias",
                "seguridad",
                "rentas");
    }

    @Override
    public Set<String> paquetesQueTienenQueExistir() {
        return Set.of(
                "kamayuk.catastro.compartido",
                "kamayuk.catastro.plataforma.tenant",
                "kamayuk.catastro.dominio",
                // El contexto acotado principal, anadido en R-N: sin el, renombrar el paquete del
                // modulo mas grande del sistema no ponia roja esta guarda —se conformaba con que
                // estuvieran los tres de infraestructura—. `caja` ya lo declaraba desde P5D.
                "kamayuk.catastro.nucleo.dominio",
                // El segundo contexto acotado (#6). Se nombra por el mismo motivo que el primero:
                // sin la entrada, renombrar o vaciar su paquete de dominio no pondria roja ninguna
                // guarda.
                "kamayuk.catastro.fiscalizacion.dominio");
    }

    @Override
    public Set<String> tiposAjenosQueFiscalizacionSoloLee() {
        return Set.of(
                // La ficha que sustenta un acta y la que sustenta una declaracion (#45, #49).
                // Devuelven identificador y area: ni un metodo que escriba.
                ".nucleo.LectorDeFichas",
                // El uso y las caracteristicas del predio a una fecha (#49).
                ".nucleo.LectorDeCaracteristicas",
                ".nucleo.CaracteristicasDelPredio",
                // Quien es titular de un predio a una fecha, por lote, para poner el nombre en la
                // fila de omisos (#545).
                ".nucleo.TitularesDelPredio",
                ".nucleo.TitularDelPredio",
                // Lo que la transferencia DEVOLVIO. Es un registro de resultado, no una puerta: no
                // tiene un metodo que escriba, y lo lee tambien quien dibuja el papel.
                ".nucleo.VersionTransferida",
                // Y su excepcion: atraparla no es escribir. La captura la capa web, que traduce a
                // 422 «el predio no tiene ficha vigente».
                ".nucleo.TransferenciaDeFiscalizacion$SinFichaQueVersionar",
                // Si un predio declaro en un ejercicio, por lote (RF-055).
                ".rentas.DeclaracionesDelEjercicio",
                ".rentas.DeclaracionDelEjercicio",
                // Cuanto se debe a una fecha, para el estado de cuenta de fiscalizacion (RF-056).
                // Arista al reves de las otras: la excepcion de ARQ-01 §4 regla 2.
                ".cuentacorriente.ConsultaDeDeudaPublica",
                ".cuentacorriente.ObligacionPublica",
                // Como se llaman los tributos del libro (#553). Es un enumerado: lo que aporta es
                // que fiscalizacion no declare su propio literal.
                ".cuentacorriente.TributoDelLibro");
    }

    @Override
    public Set<String> escriturasSinUsuarioQueObserve() {
        return Set.of(
                // Reconstruye saldo_proyectado desde el libro (#23). Es un cache derivado: no
                // modifica ningun dato, lo recalcula. El libro no se toca.
                ".cuentacorriente.aplicacion.ReconstruirSaldo.deContribuyente(long)",
                // La lista de predios SIN declaracion jurada (ADR-0015 §2.3, #344). Es una
                // CONSULTA.
                // Lo unico que escribe es su propia fila de ACCESO, y esa observacion no la puede
                // dar el usuario porque nadie escribe un motivo para mirar una grilla.
                ".rentas.aplicacion.ConsultaDeConciliacion.noConciliadas("
                        + "kamayuk.catastro.nucleo.BusquedaDeFichas, kamayuk.catastro.dominio.Ejercicio,"
                        + " java.time.LocalDate, kamayuk.catastro.compartido.Paginacion)",
                // El titular de un predio, resuelto al clic (ADR-0015 §2.4, #366). Misma forma.
                ".rentas.aplicacion.ConsultaDeTitulares.resolver(long, java.time.LocalDate)",
                // La rama del portal del contribuyente (ADR-0020, #57). Misma forma y un motivo
                // mas fuerte: aqui el usuario ni siquiera es un funcionario.
                ".rentas.aplicacion.RamaDelCiudadano.leer(java.time.LocalDate)",
                // La descarga del conjunto sellado de `normativa` (P5B, ADR-0025 §1). Es el caso
                // mas claro de la lista: lo que escribe es una COPIA de un dato que este sistema no
                // produjo, ya sellado en el otro y verificado por su sha256. No hay ningun usuario
                // que la pida —la dispara la primera lectura que necesita el conjunto— y no hay
                // ningun «por que» que dar: la copia es identica a la fuente por construccion, y si
                // no lo fuera no se guardaria. Exigir una observacion aqui produciria la cadena
                // fija que el javadoc de la regla advierte.
                ".parametros.aplicacion.DescargaDeNormativa.asegurarDescargado("
                        + "long, java.lang.String)",
                // El buzon de salida (C-8, ADR-0026 §3). Las dos escrituras son de la MISMA
                // clase: escribir un hecho ya ocurrido y anotar que se entrego.
                //
                // Publicar un hecho NO modifica ningun dato del padron: copia al buzon lo que la
                // ficha, el predio y la titularidad ya dicen —y en el caso de la valuacion,
                // produce un hecho derivado de ellos—. La fuente queda intacta, y quien la
                // modifico dio su observacion en el acto que la modifico. Ademas lo dispara un
                // proceso por lotes: no hay ningun usuario delante que pueda dar un «por que».
                //
                // Y marcar entregado es un ACUSE DE RECIBO de otro sistema. Exigir aqui una
                // observacion produciria exactamente lo que el javadoc de la regla advierte: una
                // cadena fija que satisface la comprobacion y vacia de sentido la auditoria.
                ".nucleo.aplicacion.PublicarUnHecho.publicar("
                        + "kamayuk.catastro.nucleo.dominio.HechoDeCatastro)",
                ".nucleo.aplicacion.EntregaDeEventos.marcarEntregados("
                        + "java.util.List, java.time.Instant)");
    }
}
