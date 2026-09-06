package kamayuk.catastro.grd.aplicacion;

import java.time.LocalDate;
import java.util.List;
import kamayuk.catastro.grd.dominio.CertificadoItse;
import kamayuk.catastro.grd.dominio.GestionDeRiesgoRepository;
import kamayuk.catastro.grd.dominio.PredioDesconocido;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El ITSE de un predio <b>vigente a una fecha</b> (#5, AC-4).
 *
 * <h2>Un certificado vencido no se devuelve como vigente</h2>
 *
 * <p>Es la regla 9 aplicada a un certificado: «tiene ITSE» no es una afirmacion que se pueda hacer
 * sin fecha. Un certificado que vencio el 31 de diciembre estaba vigente el 30 y no lo esta el 1 de
 * enero, y las dos respuestas son correctas — la que no lo es es la que no dice de que dia habla.
 *
 * <p>El filtro lo hace la base y no un bucle de Java: {@link
 * GestionDeRiesgoRepository#itseVigenteA} lleva los tres extremos en el {@code WHERE}. Traer todos
 * y descartar aqui dejaria el vencido a un refactor de distancia de volver a salir, y el sintoma
 * seria una licencia emitida contra un certificado caducado.
 *
 * <p><b>Aqui no hay 422 por falta de geometria</b>, al reves que en {@link ConsultaDeRiesgo}: un
 * certificado cuelga del predio y no de su poligono, asi que un predio sin plano levantado puede
 * contestar perfectamente «ninguno vigente» y esa respuesta es verdadera.
 */
@Service
public class ConsultaDeItse {

    private final GestionDeRiesgoRepository repositorio;

    public ConsultaDeItse(GestionDeRiesgoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public List<CertificadoItse> vigenteA(long predioId, LocalDate aLaFecha) {
        if (!repositorio.estadoDelLote(predioId).existe()) {
            throw new PredioDesconocido(predioId);
        }
        return repositorio.itseVigenteA(predioId, aLaFecha);
    }
}
