package kamayuk.catastro.fiscalizacion.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.DatosDeAuditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.Score;
import org.springframework.dao.DuplicateKeyException;
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
    public Campania abrir(
            String codigo, String nombre, Score umbral, int tope, Observacion observacion) {
        // El atajo del caso corriente, y NADA MAS: la garantia es `campania_codigo_uq` y no este
        // `if`. Dos peticiones simultaneas leerian las dos «no esta» y las dos insertarian, y sin
        // la captura de abajo la segunda contestaria un 500 en vez de decir que ya existe.
        if (repositorio.campaniaPorCodigo(codigo).isPresent()) {
            throw new CampaniaYaAbierta(codigo);
        }
        Campania guardada;
        try {
            guardada =
                    repositorio.guardar(
                            Campania.nueva(codigo, nombre, LocalDate.now(reloj), umbral, tope),
                            observacion);
        } catch (DuplicateKeyException repetida) {
            throw new CampaniaYaAbierta(codigo);
        }

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
                        anterior.umbral(),
                        anterior.tope());
        Campania guardada = repositorio.guardar(cerrada, observacion);

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
     * La campania, para la bitacora.
     *
     * <p>Aqui vivia uno de los dos {@code escapar()} que #20 borro: cubria {@code \\} y {@code "} y
     * <b>no los caracteres de control</b>, asi que una campania cuyo nombre trajera un salto de
     * linea rompia el {@code cast(… AS jsonb)} igual que la que no escapaba nada. Un escape a mano
     * que hay que acordarse de llamar es el defecto, no el arreglo.
     *
     * <p>El umbral entra como su cifra y no como el objeto {@code Score}: un {@code Score} es un
     * envoltorio de un decimal y serializado como objeto saldria {@code {"valor":"0.20"}}, que dice
     * lo mismo y se lee peor. La cifra sale entrecomillada porque la bitacora escribe todo decimal
     * como texto — el motivo esta en {@code ObjetosDeValorEnJson.decimalesComoTexto()}.
     */
    private static DatosDeAuditoria descripcion(Campania campania) {
        return DatosDeAuditoria.campos()
                .mas("codigo", campania.codigo())
                .mas("nombre", campania.nombre())
                .mas("estado", campania.estado())
                .mas("umbral", campania.umbral().valor())
                .mas("tope", campania.tope())
                .mas("fin", campania.fin())
                .datos();
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
