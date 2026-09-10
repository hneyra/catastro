package kamayuk.catastro.seguridad.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.DatosDeAuditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import kamayuk.catastro.seguridad.dominio.CatalogoDelSistema;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra <b>el catalogo de este sistema</b>: sus modulos y sus opciones (RF-122). Y nada mas.
 *
 * <h2>Que dejo de hacer, y por que (ADR-0039 etapa 5, identidad#5 AC-1)</h2>
 *
 * <p>Hasta la etapa 4 esta clase se llamaba {@code SembradorDeLaCopiaLocal} y escribia ademas el
 * grupo de administracion, el primer administrador, su afiliacion y sus permisos: cuatro {@code
 * INSERT} sobre {@code grupo}, {@code usuario}, {@code miembro} y {@code permiso}. Desde la etapa 4
 * esas cuatro tablas <b>tambien</b> las escribe el consumidor del buzon de {@code identidad}, o sea
 * que habia <b>dos origenes para la misma tabla</b> — y el segundo solo agrega, con {@code ON
 * CONFLICT … DO NOTHING}. Eso no es una duplicacion inofensiva: es exactamente lo que hace que
 * <b>un permiso revocado no se propague</b>. {@code identidad} revoca, el evento llega y lo aplica
 * el consumidor; el despliegue siguiente vuelve a correr la implantacion y el sembrador vuelve a
 * otorgar los siete privilegios que alguien retiro, sin que nada lo diga.
 *
 * <p>Lo que queda es lo unico que este sistema es dueno de decir: <b>que opciones tiene</b>. El
 * catalogo no viaja por el buzon —{@code identidad} guarda a quien se le concede cada opcion, no
 * quien las declara (ADR-0039)— y sin las filas de {@code acceso} un {@code PERMISO_FIJADO} que
 * llegue del buzon no tiene sobre que colgarse: el consumidor lo pospone diciendolo. Por eso el
 * orden de la implantacion es catalogo primero, consumidor despues.
 *
 * <h2>Por que aqui hay SQL y no un contexto acotado</h2>
 *
 * <p>Porque no hay ninguna escritura de administracion en este sistema y no la va a haber: la
 * administracion vive en {@code identidad} desde la etapa 4 de ADR-0039. Lo que este sistema
 * necesita son exactamente dos cosas —leer para autorizar, y sembrar su catalogo al implantar—, asi
 * que lo que hay es un lector ({@code ComprobadorDeAccesoJdbc}) y este sembrador.
 *
 * <p><b>Idempotente y solo agrega.</b> Se puede ejecutar en cada despliegue: lo que ya existe se
 * queda como esta y lo que falta se crea. Lo que <b>no</b> hace es borrar: los permisos que cuelgan
 * de un acceso retirado son constancia de quien pudo hacer que, y eso no se borra (RNF-051, regla
 * 4).
 */
@Service
public class SembradorDelCatalogo extends RepositorioJdbc {

    private final Auditoria auditoria;
    private final Clock reloj;

    public SembradorDelCatalogo(JdbcClient jdbc, Auditoria auditoria, Clock reloj) {
        super(jdbc);
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Deja el catalogo de este sistema sembrado para la municipalidad del contexto.
     *
     * <p><b>Una sola transaccion para todo</b>, y por dos motivos distintos. El primero es de
     * negocio: un catalogo sembrado a medias deja pantallas a las que nadie puede dar permiso, y
     * parece completo. El segundo es tecnico y se paga en cuanto se olvida: las dos tablas llevan
     * RLS con {@code FORCE} y sus politicas leen {@code app.municipalidad_id}, que el gestor de
     * transacciones fija con {@code SET LOCAL} <b>al abrir la transaccion</b>; leerlas fuera de una
     * no devuelve vacio, revienta (DAT-01 §0, #486).
     *
     * @return cuantos accesos se crearon; 0 en un despliegue donde no cambio el catalogo
     */
    @Transactional
    public int sembrar(Observacion porQue) {
        List<CatalogoDelSistema.Opcion> opciones = CatalogoDelSistema.opciones();
        if (opciones.isEmpty()) {
            throw new IllegalStateException(
                    "El catalogo de este sistema vino vacio. Sembrar cero accesos dejaria el"
                            + " sistema sin ninguna opcion configurable, y en silencio");
        }

        int creados = 0;
        for (CatalogoDelSistema.Opcion opcion : opciones) {
            creados += crearAccesoSiFalta(opcion, moduloId(opcion));
        }

        // Solo si se creo algo. Un despliegue que no cambia el catalogo no tiene nada que
        // asentar, y una fila de auditoria por despliegue convierte la bitacora en un registro de
        // reinicios — que es lo contrario de lo que ADR-0008 quiere que se pueda leer ahi.
        if (creados == 0) {
            return 0;
        }
        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj), "acceso", "catalogo", Operacion.ALTA, porQue)
                        .con(
                                null,
                                DatosDeAuditoria.campos()
                                        .mas("accesosCreados", creados)
                                        .mas("opcionesDelSistema", opciones.size())
                                        .datos()));
        return creados;
    }

    /** Crea el modulo si falta y devuelve su identificador. */
    private long moduloId(CatalogoDelSistema.Opcion opcion) {
        jdbc().sql(
                        "INSERT INTO modulo_sistema (municipalidad_id, codigo, nombre)"
                                + " VALUES ("
                                + MUNICIPALIDAD_ACTUAL
                                + ", :codigo, :nombre)"
                                + " ON CONFLICT (municipalidad_id, codigo) DO NOTHING")
                .param("codigo", opcion.moduloCodigo())
                .param("nombre", opcion.moduloNombre())
                .update();

        return jdbc().sql("SELECT id FROM modulo_sistema WHERE codigo = :codigo")
                .param("codigo", opcion.moduloCodigo())
                .query(Long.class)
                .single();
    }

    private int crearAccesoSiFalta(CatalogoDelSistema.Opcion opcion, long moduloId) {
        return jdbc().sql(
                        "INSERT INTO acceso (municipalidad_id, modulo_id, tipo, codigo, nombre)"
                                + " VALUES ("
                                + MUNICIPALIDAD_ACTUAL
                                + ", :modulo, 'OPCION_MENU', :codigo, :nombre)"
                                + " ON CONFLICT (municipalidad_id, codigo) DO NOTHING")
                .param("modulo", moduloId)
                .param("codigo", opcion.codigo())
                .param("nombre", opcion.nombre())
                .update();
    }
}
