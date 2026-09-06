package kamayuk.catastro.grd.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.Operacion;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.grd.dominio.FajaMarginal;
import kamayuk.catastro.grd.dominio.GestionDeRiesgoRepository;
import kamayuk.catastro.grd.dominio.ZonaDeRiesgo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El alta de <b>un</b> poligono de la carta de restricciones (#5, AC-7).
 *
 * <p>Es un {@code @Service} aparte de {@link ImportarCartaDeRiesgo} y no un metodo suyo, y esa
 * separacion es la que hace posible el rechazo por fila: cada llamada abre <b>su</b> transaccion,
 * asi que un poligono mal formado se rechaza solo y los siguientes entran. Envolver el bucle haria
 * algo peor que perder la fila siguiente a la mala —la fila rechazada marca la transaccion como
 * <i>rollback-only</i> y la corrida entera revienta con {@code UnexpectedRollbackException}, sin
 * llegar a devolver el informe que la explicaba (#247 §2)—.
 *
 * <p>La observacion viaja en la firma (regla 10) y es la misma para toda la carga: es un acto —«se
 * cargo la carta de peligro tal»—, no uno por poligono.
 */
@Service
public class RegistrarCapaDeRiesgo {

    private final GestionDeRiesgoRepository repositorio;
    private final Auditoria auditoria;
    private final Clock reloj;

    public RegistrarCapaDeRiesgo(
            GestionDeRiesgoRepository repositorio, Auditoria auditoria, Clock reloj) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional
    public ZonaDeRiesgo registrar(ZonaDeRiesgo zona, String geometriaWkt, Observacion observacion) {
        ZonaDeRiesgo guardada = repositorio.guardar(zona, geometriaWkt);
        auditar("zona_riesgo", guardada.id(), observacion, descripcion(guardada));
        return guardada;
    }

    @Transactional
    public FajaMarginal registrar(FajaMarginal faja, String geometriaWkt, Observacion observacion) {
        FajaMarginal guardada = repositorio.guardar(faja, geometriaWkt);
        auditar("faja_marginal", guardada.id(), observacion, descripcion(guardada));
        return guardada;
    }

    private void auditar(
            String tabla,
            @org.jspecify.annotations.Nullable Long clave,
            Observacion observacion,
            String despues) {
        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj),
                                tabla,
                                String.valueOf(clave),
                                Operacion.ALTA,
                                observacion)
                        .con(null, despues));
    }

    private static String descripcion(ZonaDeRiesgo zona) {
        return "{\"codigo\":\""
                + zona.codigo()
                + "\",\"fenomeno\":\""
                + zona.fenomeno()
                + "\",\"nivel\":\""
                + zona.nivel()
                + "\",\"mitigable\":"
                + zona.mitigable()
                + "}";
    }

    private static String descripcion(FajaMarginal faja) {
        return "{\"codigo\":\""
                + faja.codigo()
                + "\",\"cuerpoDeAgua\":\""
                + faja.cuerpoDeAgua()
                + "\",\"anchoM\":\""
                + faja.ancho().magnitud().toPlainString()
                + "\"}";
    }
}
