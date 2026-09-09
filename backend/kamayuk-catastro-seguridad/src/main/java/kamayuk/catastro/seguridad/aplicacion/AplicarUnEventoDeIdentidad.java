package kamayuk.catastro.seguridad.aplicacion;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import kamayuk.catastro.seguridad.dominio.AplicadorDeEventosDeIdentidad;
import kamayuk.catastro.seguridad.dominio.EventoRecibido;
import kamayuk.catastro.seguridad.dominio.TipoDeEventoDeIdentidad;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lleva UN evento de {@code identidad} a la copia local de la autorizacion, en su propia
 * transaccion (ADR-0039, etapa 4; identidad#4 AC-1).
 *
 * <h2>Es el UNICO escritor de `usuario`, `grupo`, `miembro` y `permiso` fuera de la implantacion
 * </h2>
 *
 * <p>Y es una afirmacion medida, no una intencion: {@code ConfiguracionDeCatastro} declara desde la
 * etapa 4 {@code escritoresDeLaAutorizacionConMotivo()} con esta clase y con {@code
 * SembradorDeLaCopiaLocal}, y con eso el escaner de ADR-0039 pone rojo cualquier otro {@code
 * INSERT}, {@code UPDATE} o {@code DELETE} sobre las cuatro tablas en {@code src/main}. Por eso el
 * SQL vive AQUI y no en un repositorio aparte: cada clase que escribe la autorizacion es una
 * entrada mas en esa lista, y una lista corta es la que se puede leer.
 *
 * <h2>Una transaccion por evento, y {@code REQUIRES_NEW} no es una preferencia</h2>
 *
 * <p>El consumidor recorre un lote en un solo hilo y fija el contexto de tenant antes de la vuelta.
 * {@code REQUIRES_NEW} hace que cada evento abra SU transaccion, y es al abrirla cuando el gestor
 * de tenant emite el {@code SET LOCAL} con la municipalidad del contexto: dos eventos de dos
 * municipalidades en la misma transaccion escribirian los dos con el {@code app.municipalidad_id}
 * del primero, o sea el permiso de una municipalidad concedido en otra. Y el commit de un evento no
 * puede depender del siguiente: lo aplicado se acusa, y lo que se acusa tiene que estar confirmado.
 *
 * <h2>Se enlaza por clave natural, nunca por el identificador del emisor</h2>
 *
 * <p>El cuerpo trae {@code usuarioId}, {@code grupoId} y {@code sujetoId} de la base de {@code
 * identidad}, y aqui esos numeros no significan nada: cada base asigna los suyos. Lo que une las
 * dos copias es lo que las une desde C-7 —la {@code cuenta} del usuario, que es el {@code
 * preferred_username} del token, y el {@code nombre} del grupo—, y el permiso se cuelga del {@code
 * codigo} del acceso, que es lo que {@code @RequiereAcceso} escribe.
 *
 * <h2>Lo que pasa cuando lo que se nombra no esta</h2>
 *
 * <p>Un {@code MIEMBRO_AFILIADO} o un {@code PERMISO_FIJADO} que nombra un grupo o una cuenta que
 * esta copia no conoce <b>no se aplica ahora</b> y no se acusa: el emisor publica en orden y el
 * alta del sujeto va delante, asi que lo que falta esta en camino o se aparto antes con su motivo.
 * No se da de alta desde aqui, y es deliberado: el cuerpo de una afiliacion trae la cuenta y no el
 * nombre de la persona, y {@code usuario.nombre} es {@code NOT NULL} — inventarle uno seria
 * escribir en la copia un dato que nadie declaro. Es la leccion de {@code identidad}#8 con su
 * {@code AplicadorDeReferencia}: quien aplica no puede descartar en silencio lo que no sabe
 * colocar.
 *
 * <p>Un {@code USUARIO_MODIFICADO} o un {@code GRUPO_MODIFICADO} de alguien que aqui no existe
 * <b>si</b> lo da de alta: su cuerpo es la fila entera tal como quedo (etapa 2), asi que no falta
 * nada, y una copia que rechazara el estado final de una fila por no haber visto su alta se
 * quedaria atras sin ganar nada.
 *
 * <h2>Una baja es una baja</h2>
 *
 * <p>Un usuario deshabilitado, un miembro desafiliado y un permiso con los siete privilegios en
 * falso se escriben <b>como tales</b>: aqui no se borra nada (regla 4). La constancia de quien tuvo
 * que permiso es lo que la auditoria necesita el dia que alguien pregunte.
 */
@org.springframework.stereotype.Service
public class AplicarUnEventoDeIdentidad extends RepositorioJdbc
        implements AplicadorDeEventosDeIdentidad {

    /** El sistema del que este consumidor es dueno: solo aplica los permisos sobre SUS opciones. */
    public static final String SISTEMA = "catastro";

    private final JsonMapper json;

    public AplicarUnEventoDeIdentidad(JdbcClient jdbc, JsonMapper json) {
        super(jdbc);
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Resultado aplicar(EventoRecibido evento, Instant cuando) {
        TipoDeEventoDeIdentidad tipo = evento.tipo();
        if (tipo == null) {
            throw new NoSePuedeAplicar(
                    "`identidad` publica un evento de tipo «"
                            + evento.tipoPublicado()
                            + "», que este sistema no sabe aplicar. Que tipos sabe aplicar esta en"
                            + " TipoDeEventoDeIdentidad; cuales publica el emisor, en su buzon");
        }
        JsonNode cuerpo = leer(evento);

        // Primero la memoria, en la MISMA transaccion que la escritura: si el commit no confirma,
        // se va con ella, y el evento se vuelve a servir y a aplicar. Si ya estaba, la fila lo dice
        // y se compara la huella: un reintento del emisor no hace nada, un emisor que reescribe un
        // evento con otro contenido no se aplica nunca.
        Optional<String> yaAplicadoCon = anotarComoAplicado(evento, cuando);
        if (yaAplicadoCon.isPresent()) {
            if (!yaAplicadoCon.get().strip().equals(evento.huella())) {
                throw new NoSePuedeAplicar(
                        "El evento "
                                + evento.eventoId()
                                + " ya se aplico aqui con la huella "
                                + yaAplicadoCon.get().strip()
                                + " y ahora llega con "
                                + evento.huella()
                                + ": el emisor esta reescribiendo un evento ya aplicado, y eso no"
                                + " es un reintento");
            }
            return Resultado.YA_APLICADO;
        }

        return switch (tipo) {
            case USUARIO_DADO_DE_ALTA, USUARIO_MODIFICADO -> {
                escribirUsuario(cuerpo);
                yield Resultado.APLICADO;
            }
            case GRUPO_DADO_DE_ALTA, GRUPO_MODIFICADO -> {
                escribirGrupo(cuerpo);
                yield Resultado.APLICADO;
            }
            case MIEMBRO_AFILIADO, MIEMBRO_DESAFILIADO -> {
                escribirMiembro(evento, cuerpo);
                yield Resultado.APLICADO;
            }
            case PERMISO_FIJADO -> escribirPermiso(evento, cuerpo);
        };
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void matar(EventoRecibido evento, String motivo, Instant cuando) {
        // El motivo se recorta a lo que cabe en la columna: un diagnostico cortado es peor que uno
        // entero, y mucho mejor que un INSERT que revienta y deja el evento sin apartar, que es
        // lo que lo volveria a servir para siempre. La columna mide 400.
        String recortado = motivo.length() > 400 ? motivo.substring(0, 400) : motivo;
        jdbc().sql(
                        "INSERT INTO identidad_evento_muerto (municipalidad_id, evento_id,"
                                + " secuencia, tipo, sujeto_id, cuerpo, huella, motivo,"
                                + " recibido_en) VALUES ("
                                + MUNICIPALIDAD_ACTUAL
                                + ", :evento, :secuencia, :tipo, :sujeto, :cuerpo, :huella,"
                                + " :motivo, :cuando)"
                                + " ON CONFLICT (municipalidad_id, evento_id) DO NOTHING")
                .param("evento", evento.eventoId())
                .param("secuencia", evento.secuencia())
                .param("tipo", recortar(evento.tipoPublicado(), 40))
                .param("sujeto", evento.sujetoId())
                .param("cuerpo", evento.cuerpo())
                .param("huella", evento.huella())
                .param("motivo", recortado)
                .param("cuando", Timestamp.from(cuando))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public long muertosSinExplicar() {
        Long cuantos =
                jdbc().sql("SELECT count(*) FROM identidad_evento_muerto WHERE explicacion IS NULL")
                        .query(Long.class)
                        .single();
        return cuantos == null ? 0L : cuantos;
    }

    // ------------------------------------------------------------------
    // Las escrituras, una por tabla
    // ------------------------------------------------------------------

    private void escribirUsuario(JsonNode cuerpo) {
        jdbc().sql(
                        "INSERT INTO usuario (municipalidad_id, cuenta, nombre, correo, habilitado,"
                                + " vigencia_desde, vigencia_hasta) VALUES ("
                                + MUNICIPALIDAD_ACTUAL
                                + ", :cuenta, :nombre, :correo, :habilitado, :desde, :hasta)"
                                + " ON CONFLICT (municipalidad_id, cuenta) DO UPDATE SET"
                                + " nombre = EXCLUDED.nombre, correo = EXCLUDED.correo,"
                                + " habilitado = EXCLUDED.habilitado,"
                                + " vigencia_desde = EXCLUDED.vigencia_desde,"
                                + " vigencia_hasta = EXCLUDED.vigencia_hasta")
                .param("cuenta", texto(cuerpo, "cuenta"))
                .param("nombre", texto(cuerpo, "nombre"))
                .param("correo", textoOpcional(cuerpo, "correo"))
                .param("habilitado", booleano(cuerpo, "habilitado"))
                .param("desde", fecha(cuerpo, "vigenciaDesde"))
                .param("hasta", fecha(cuerpo, "vigenciaHasta"))
                .update();
    }

    private void escribirGrupo(JsonNode cuerpo) {
        jdbc().sql(
                        "INSERT INTO grupo (municipalidad_id, nombre, descripcion, habilitado,"
                                + " vigencia_desde, vigencia_hasta) VALUES ("
                                + MUNICIPALIDAD_ACTUAL
                                + ", :nombre, :descripcion, :habilitado, :desde, :hasta)"
                                + " ON CONFLICT (municipalidad_id, nombre) DO UPDATE SET"
                                + " descripcion = EXCLUDED.descripcion,"
                                + " habilitado = EXCLUDED.habilitado,"
                                + " vigencia_desde = EXCLUDED.vigencia_desde,"
                                + " vigencia_hasta = EXCLUDED.vigencia_hasta")
                .param("nombre", texto(cuerpo, "nombre"))
                .param("descripcion", textoOpcional(cuerpo, "descripcion"))
                .param("habilitado", booleano(cuerpo, "habilitado"))
                .param("desde", fecha(cuerpo, "vigenciaDesde"))
                .param("hasta", fecha(cuerpo, "vigenciaHasta"))
                .update();
    }

    /**
     * La afiliacion, por el nombre del grupo y la cuenta del usuario.
     *
     * <p>Es un {@code INSERT ... SELECT}: si el grupo o la cuenta no estan aqui, escribe CERO filas
     * y no falla. Ese cero no se traga: es «ahora no», porque lo que falta esta en camino.
     */
    private void escribirMiembro(EventoRecibido evento, JsonNode cuerpo) {
        boolean activo = booleano(cuerpo, "activo");
        String grupo = texto(cuerpo, "grupoNombre");
        String cuenta = texto(cuerpo, "usuarioCuenta");
        int escritas =
                jdbc().sql(
                                "INSERT INTO miembro (municipalidad_id, grupo_id, usuario_id,"
                                        + " usuario_alta, activo, fecha_baja, usuario_baja)"
                                        + " SELECT "
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", g.id, u.id, :usuarioAlta, CAST(:activo AS boolean),"
                                        + " CAST(:fechaBaja AS timestamptz), :usuarioBaja"
                                        + " FROM grupo g, usuario u"
                                        + " WHERE g.nombre = :grupo AND u.cuenta = :cuenta"
                                        + " ON CONFLICT (municipalidad_id, grupo_id, usuario_id)"
                                        + " DO UPDATE SET activo = EXCLUDED.activo,"
                                        + " fecha_baja = EXCLUDED.fecha_baja,"
                                        + " usuario_baja = EXCLUDED.usuario_baja")
                        .param("grupo", grupo)
                        .param("cuenta", cuenta)
                        // `usuario_alta` es NOT NULL y una desafiliacion no trae quien afilio:
                        // se escribe quien desafilio, que es lo que el evento sabe. En un UPDATE
                        // la columna no se toca, asi que el alta original se conserva.
                        .param("usuarioAlta", quien(cuerpo, activo ? "usuarioAlta" : "usuarioBaja"))
                        .param("activo", activo)
                        .param("fechaBaja", activo ? null : Timestamp.from(evento.creadoEn()))
                        .param("usuarioBaja", activo ? null : quien(cuerpo, "usuarioBaja"))
                        .update();
        if (escritas != 1) {
            throw new NoSePuedeAplicarAhora(
                    "El evento "
                            + evento.eventoId()
                            + " nombra el grupo «"
                            + grupo
                            + "» y la cuenta «"
                            + cuenta
                            + "», y esta copia no conoce a uno de los dos: la sentencia escribio "
                            + escritas
                            + " filas. Un evento que llega antes que aquel del que depende no se"
                            + " puede aplicar todavia; se vuelve a intentar en la vuelta siguiente");
        }
    }

    /**
     * El permiso, por el codigo del acceso y el nombre del sujeto.
     *
     * <p><b>Solo los de este sistema.</b> El buzon de {@code identidad} lleva los permisos de los
     * cinco catalogos y esta base no tiene columna {@code sistema} en {@code acceso}: dos sistemas
     * pueden llamar igual a dos opciones distintas —{@code permisos} es una opcion de {@code
     * identidad} y de {@code rentas}—, asi que aplicar por codigo un permiso ajeno seria conceder
     * aqui una opcion que nadie concedio. Se ignora, con aviso, y se acusa.
     */
    private Resultado escribirPermiso(EventoRecibido evento, JsonNode cuerpo) {
        String sistema = texto(cuerpo, "sistema");
        if (!SISTEMA.equals(sistema)) {
            return Resultado.IGNORADO_AJENO;
        }
        String sujeto = texto(cuerpo, "sujeto");
        boolean deGrupo =
                switch (sujeto) {
                    case "GRUPO" -> true;
                    case "USUARIO" -> false;
                    default ->
                            throw new NoSePuedeAplicar(
                                    "El permiso del evento "
                                            + evento.eventoId()
                                            + " dice que su sujeto es «"
                                            + sujeto
                                            + "», y un permiso es de un GRUPO o de un USUARIO");
                };
        JsonNode privilegios = cuerpo.path("privilegios");
        StringBuilder columnas = new StringBuilder();
        StringBuilder valores = new StringBuilder();
        StringBuilder actualizaciones = new StringBuilder();
        for (Privilegio privilegio : Privilegio.values()) {
            String columna = privilegio.columna();
            columnas.append(", ").append(columna);
            valores.append(", ").append(booleano(privilegios, columna));
            actualizaciones.append(", ").append(columna).append(" = EXCLUDED.").append(columna);
        }
        String codigo = texto(cuerpo, "codigo");
        String sujetoNombre = texto(cuerpo, "sujetoNombre");
        String tabla = deGrupo ? "grupo" : "usuario";
        String clave = deGrupo ? "nombre" : "cuenta";
        String columnaDelSujeto = deGrupo ? "grupo_id" : "usuario_id";
        int escritas =
                jdbc().sql(
                                "INSERT INTO permiso (municipalidad_id, acceso_id, "
                                        + columnaDelSujeto
                                        + ", usuario_registro"
                                        + columnas
                                        + ") SELECT "
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", a.id, s.id, :quien"
                                        + valores
                                        + " FROM acceso a, "
                                        + tabla
                                        + " s WHERE a.codigo = :codigo AND s."
                                        + clave
                                        + " = :sujeto"
                                        + " ON CONFLICT (municipalidad_id, acceso_id, "
                                        + columnaDelSujeto
                                        + ") WHERE "
                                        + columnaDelSujeto
                                        + " IS NOT NULL DO UPDATE SET"
                                        + " usuario_registro = EXCLUDED.usuario_registro"
                                        + actualizaciones)
                        .param("quien", quien(cuerpo, "usuarioRegistro"))
                        .param("codigo", codigo)
                        .param("sujeto", sujetoNombre)
                        .update();
        if (escritas != 1) {
            throw new NoSePuedeAplicarAhora(
                    "El evento "
                            + evento.eventoId()
                            + " fija un permiso sobre la opcion «"
                            + codigo
                            + "» de "
                            + SISTEMA
                            + " para el "
                            + tabla
                            + " «"
                            + sujetoNombre
                            + "», y esta copia no conoce a uno de los dos: la sentencia escribio "
                            + escritas
                            + " filas. Si es la opcion, la siembra el despliegue; si es el sujeto,"
                            + " su alta esta en camino. Se vuelve a intentar en la vuelta"
                            + " siguiente");
        }
        return Resultado.APLICADO;
    }

    // ------------------------------------------------------------------
    // La memoria
    // ------------------------------------------------------------------

    /**
     * Anota el evento como aplicado, y si ya lo estaba devuelve la huella con que se aplico.
     *
     * <p>{@code ON CONFLICT DO NOTHING RETURNING} devuelve la fila solo cuando la escribio: si no
     * devuelve nada es que ya estaba, y entonces se lee la huella guardada para compararla.
     */
    private Optional<String> anotarComoAplicado(EventoRecibido evento, Instant cuando) {
        Optional<String> escrita =
                jdbc().sql(
                                "INSERT INTO identidad_evento_aplicado (municipalidad_id,"
                                        + " evento_id, secuencia, tipo, sujeto_id, huella,"
                                        + " aplicado_en) VALUES ("
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", :evento, :secuencia, :tipo, :sujeto, :huella, :cuando)"
                                        + " ON CONFLICT (municipalidad_id, evento_id) DO NOTHING"
                                        + " RETURNING huella")
                        .param("evento", evento.eventoId())
                        .param("secuencia", evento.secuencia())
                        .param("tipo", recortar(evento.tipoPublicado(), 40))
                        .param("sujeto", evento.sujetoId())
                        .param("huella", evento.huella())
                        .param("cuando", Timestamp.from(cuando))
                        .query(String.class)
                        .optional();
        if (escrita.isPresent()) {
            return Optional.empty();
        }
        return jdbc().sql("SELECT huella FROM identidad_evento_aplicado WHERE evento_id = :evento")
                .param("evento", evento.eventoId())
                .query(String.class)
                .optional();
    }

    // ------------------------------------------------------------------
    // Lectura del cuerpo
    // ------------------------------------------------------------------

    private JsonNode leer(EventoRecibido evento) {
        JsonNode cuerpo;
        try {
            cuerpo = json.readTree(evento.cuerpo());
        } catch (JacksonException ilegible) {
            throw new NoSePuedeAplicar(
                    "El cuerpo del evento "
                            + evento.eventoId()
                            + " no es JSON: "
                            + ilegible.getOriginalMessage(),
                    ilegible);
        }
        if (cuerpo == null || !cuerpo.isObject()) {
            throw new NoSePuedeAplicar(
                    "El cuerpo del evento " + evento.eventoId() + " no es un objeto JSON");
        }
        return cuerpo;
    }

    private static String texto(JsonNode cuerpo, String campo) {
        JsonNode valor = cuerpo.path(campo);
        if (valor.isMissingNode() || valor.isNull() || !valor.isString()) {
            throw new NoSePuedeAplicar(
                    "El cuerpo del evento no trae «" + campo + "» como texto, y hace falta");
        }
        String texto = valor.asString();
        if (texto.isBlank()) {
            throw new NoSePuedeAplicar("El cuerpo del evento trae «" + campo + "» en blanco");
        }
        return texto;
    }

    private static @Nullable String textoOpcional(JsonNode cuerpo, String campo) {
        JsonNode valor = cuerpo.path(campo);
        if (valor.isMissingNode() || valor.isNull()) {
            return null;
        }
        if (!valor.isString()) {
            throw new NoSePuedeAplicar("El cuerpo del evento trae «" + campo + "» y no es texto");
        }
        return valor.asString();
    }

    private static boolean booleano(JsonNode cuerpo, String campo) {
        JsonNode valor = cuerpo.path(campo);
        if (!valor.isBoolean()) {
            throw new NoSePuedeAplicar(
                    "El cuerpo del evento no trae «"
                            + campo
                            + "» como booleano, y hace falta: un permiso o una vigencia supuestos"
                            + " serian un permiso concedido que nadie concedio");
        }
        return valor.asBoolean();
    }

    private static @Nullable LocalDate fecha(JsonNode cuerpo, String campo) {
        String texto = textoOpcional(cuerpo, campo);
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException ilegible) {
            throw new NoSePuedeAplicar(
                    "El cuerpo del evento trae «"
                            + campo
                            + "» = «"
                            + texto
                            + "», que no es una"
                            + " fecha ISO",
                    ilegible);
        }
    }

    /** Quien hizo el acto en el emisor, recortado a lo que cabe en las columnas `usuario_*`. */
    private static String quien(JsonNode cuerpo, String campo) {
        String valor = textoOpcional(cuerpo, campo);
        return recortar(valor == null || valor.isBlank() ? "identidad" : valor, 60);
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() > maximo ? texto.substring(0, maximo) : texto;
    }
}
