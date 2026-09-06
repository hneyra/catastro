package kamayuk.catastro.fiscalizacion.aplicacion;

import java.util.List;
import java.util.Optional;
import kamayuk.catastro.compartido.Pagina;
import kamayuk.catastro.compartido.Paginacion;
import kamayuk.catastro.fiscalizacion.dominio.Acta;
import kamayuk.catastro.fiscalizacion.dominio.AreasDelPadron;
import kamayuk.catastro.fiscalizacion.dominio.Evidencia;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import kamayuk.catastro.fiscalizacion.dominio.HallazgoDelPredio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que se hallo en una campania, con su evidencia y su acta.
 *
 * <h2>Esta clase es la que la regla vigila, y por eso conviene decir lo que NO tiene</h2>
 *
 * <p>No tiene un metodo {@code aplicar}. Es exactamente el camino corto que ADR-0035 punto 4
 * describe: la campania deja cuatro mil hallazgos con su delta de area calculado, alguien mira la
 * cifra y le parece obvio «aplicarlos» — y lo que produce es un padron corregido sin acto
 * administrativo detras: el contribuyente no recibe papel, no hay plazo que impugnar, y el
 * autovaluo de todo el distrito cambia sin que nadie lo haya decidido.
 *
 * <p>Que no lo tenga no depende de que nadie lo escriba: {@code NINGUN_HALLAZGO_CORRIGE_LA_FICHA}
 * mira las clases de {@code ..fiscalizacion..} que se llaman como el hallazgo —esta es una— y pone
 * el build rojo si dependen de un camino de escritura de la ficha. Se midio anadiendole aqui esa
 * dependencia.
 *
 * <p>Corregir el area es versionar la ficha con su observacion, y ese acto lo ejecuta una PERSONA
 * por el camino que ya existe.
 */
@Service
public class ConsultaDeHallazgos {

    private final FiscalizacionRepository repositorio;
    private final AreasDelPadron padron;

    public ConsultaDeHallazgos(FiscalizacionRepository repositorio, AreasDelPadron padron) {
        this.repositorio = repositorio;
        this.padron = padron;
    }

    @Transactional(readOnly = true)
    public Pagina<Hallazgo> deLaCampania(long campaniaId, Paginacion paginacion) {
        return repositorio.hallazgos(campaniaId, paginacion);
    }

    /**
     * Los hallazgos de UN predio, con su campania y su acta (#17, AC-1 y AC-3).
     *
     * <h2>Se pregunta primero por el predio, y no es una comprobacion de mas</h2>
     *
     * <p>Sin ella, «este predio no tiene hallazgos» y «ese identificador no existe aqui» se
     * contestarian igual —lista vacia—, y las dos se atienden de maneras opuestas: la primera
     * cierra una revision y la segunda se arregla tecleando bien. Es la misma distincion que {@code
     * ConsultaDeRiesgo} hace en {@code grd}, alli entre el predio que no esta y el que esta sin
     * plano.
     *
     * <p>Y va en <b>este</b> orden —predio primero, hallazgos despues— porque el orden contrario
     * daria lista vacia y luego preguntaria: sobre el predio inexistente se leeria la tabla para
     * nada, y sobre el ajeno la lista vacia ya seria la respuesta antes de saber que no es suyo.
     *
     * <p><b>Esta lectura tampoco corrige nada</b>, como el resto de esta clase: devuelve lo que se
     * hallo, quien lo hallo y con que acta. Corregir el area es versionar la ficha con su
     * observacion, y ese acto lo ejecuta una persona (ADR-0035 punto 4).
     *
     * @throws PredioFueraDelPadron si el predio no esta en el padron de esta municipalidad
     */
    @Transactional(readOnly = true)
    public List<HallazgoDelPredio> delPredio(long predioId) {
        if (!padron.estaEnElPadron(predioId)) {
            throw new PredioFueraDelPadron(predioId);
        }
        return repositorio.hallazgosDelPredio(predioId);
    }

    @Transactional(readOnly = true)
    public Optional<Hallazgo> porId(long hallazgoId) {
        return repositorio.hallazgoPorId(hallazgoId);
    }

    /**
     * Lo que sustenta el hallazgo. Vacia no es un error: un hallazgo puede no tener foto todavia.
     */
    @Transactional(readOnly = true)
    public List<Evidencia> evidenciasDe(long hallazgoId) {
        return repositorio.evidenciasDe(hallazgoId);
    }

    /** El acta, si ya se levanto. */
    @Transactional(readOnly = true)
    public Optional<Acta> actaDe(long hallazgoId) {
        return repositorio.actaDelHallazgo(hallazgoId);
    }

    /**
     * El predio por el que se pregunto no esta en el padron de esta municipalidad.
     *
     * <p>Es {@code 404} y no lista vacia (AC-3): un predio que no esta no es un predio limpio. Bajo
     * RLS el de la municipalidad vecina cae aqui tambien, y es lo correcto —no es «prohibido»: no
     * existe—.
     */
    public static final class PredioFueraDelPadron extends RuntimeException {
        @java.io.Serial private static final long serialVersionUID = 1L;

        private final long predioId;

        public PredioFueraDelPadron(long predioId) {
            super(
                    "El predio "
                            + predioId
                            + " no esta en el padron de esta municipalidad, asi que no tiene"
                            + " hallazgos que leer. Una lista vacia diria que esta limpio, que es"
                            + " otra cosa");
            this.predioId = predioId;
        }

        public long predioId() {
            return predioId;
        }
    }
}
