package kamayuk.catastro.fiscalizacion.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abre una campania de deteccion y la cierra.
 *
 * <p>El umbral entra <b>al abrir</b> y se queda en la fila: es lo unico que hace comparable la tasa
 * de descarte de dos campanias. Uno guardado en configuracion global no sirve —cambiaria bajo los
 * pies de una campania ya corrida—, y uno no guardado deja la cifra sin denominador.
 *
 * <p>Ningun argumento es el identificador de municipalidad (regla 2), y las dos escrituras exigen
 * su {@link Observacion} (regla 10).
 */
@Service
public class AbrirCampania {

    private final FiscalizacionRepository repositorio;
    private final Auditoria auditoria;
    private final Clock reloj;

    public AbrirCampania(FiscalizacionRepository repositorio, Auditoria auditoria, Clock reloj) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Abre la campania.
     *
     * <p>Un codigo repetido es un conflicto y no un no-op: quien abre una campania esta afirmando
     * que no existe, y devolverle la de marzo con sus quince mil candidatos dentro seria peor que
     * un error.
     */
    @Transactional
    public Campania abrir(String codigo, String nombre, Score umbral, Observacion observacion) {
        if (repositorio.campaniaPorCodigo(codigo).isPresent()) {
            throw new CampaniaYaAbierta(codigo);
        }
        Campania guardada =
                repositorio.guardar(Campania.nueva(codigo, nombre, LocalDate.now(reloj), umbral));

        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj),
                                "campania",
                                String.valueOf(guardada.id()),
                                Operacion.ALTA,
                                observacion)
                        .con(null, descripcion(guardada)));
        return guardada;
    }

    /**
     * La cierra: deja de admitir candidatos.
     *
     * <p>Cerrarla no borra nada ni consolida ninguna cifra — lo unico que hace es que sus recuentos
     * dejen de moverse, que es lo que permite citarlos.
     */
    @Transactional
    public Campania cerrar(long campaniaId, Observacion observacion) {
        Campania anterior =
                repositorio
                        .campaniaPorId(campaniaId)
                        .orElseThrow(() -> new CampaniaInexistente(campaniaId));
        if (!anterior.admiteCandidatos()) {
            throw new CampaniaYaCerrada(campaniaId);
        }
        Campania cerrada =
                new Campania(
                        anterior.id(),
                        anterior.codigo(),
                        anterior.nombre(),
                        kamayuk.catastro.fiscalizacion.dominio.EstadoDeCampania.CERRADA,
                        anterior.inicio(),
                        LocalDate.now(reloj),
                        anterior.umbral());
        Campania guardada = repositorio.guardar(cerrada);

        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj),
                                "campania",
                                String.valueOf(guardada.id()),
                                Operacion.MODIFICACION,
                                observacion)
                        .con(descripcion(anterior), descripcion(guardada)));
        return guardada;
    }

    /**
     * Un JSON escrito a mano y no un serializador, por lo mismo que en {@code RegistrarSector}: son
     * cinco campos, y traer Jackson hasta la capa de aplicacion la ataria a la de presentacion.
     */
    private static String descripcion(Campania campania) {
        return "{\"codigo\":\""
                + escapar(campania.codigo())
                + "\",\"nombre\":\""
                + escapar(campania.nombre())
                + "\",\"estado\":\""
                + campania.estado()
                + "\",\"umbral\":"
                + campania.umbral()
                + ",\"fin\":"
                + (campania.fin() == null ? "null" : "\"" + campania.fin() + "\"")
                + "}";
    }

    private static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Ya hay una campania con ese codigo en esta municipalidad. */
    public static final class CampaniaYaAbierta extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        CampaniaYaAbierta(String codigo) {
            super("Ya hay una campania con el codigo '" + codigo + "' en esta municipalidad");
        }
    }

    /** No hay campania con ese identificador en esta municipalidad. */
    public static final class CampaniaInexistente extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        private final long campaniaId;

        public CampaniaInexistente(long campaniaId) {
            super("No hay ninguna campania " + campaniaId + " en esta municipalidad");
            this.campaniaId = campaniaId;
        }

        public long campaniaId() {
            return campaniaId;
        }
    }

    /** La campania ya estaba cerrada; cerrarla dos veces escribiria dos actos donde hubo uno. */
    public static final class CampaniaYaCerrada extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        CampaniaYaCerrada(long campaniaId) {
            super("La campania " + campaniaId + " ya estaba cerrada");
        }
    }
}
