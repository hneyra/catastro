package kamayuk.catastro.urbano.infraestructura.web;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.autorizacion.RequiereAcceso;
import kamayuk.catastro.urbano.ZonificacionDelPredio;
import kamayuk.catastro.web.Api;
import kamayuk.catastro.web.CodigoDeError;
import kamayuk.catastro.web.ProblemaDeNegocio;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La zona de un predio: {@code GET
 * /catastro/api/v1/urbano/zonificacion?predioId={id}&aLaFecha={fecha}} (#4).
 *
 * <h2>Las tres respuestas que no son 200, y por que no son la misma</h2>
 *
 * <ul>
 *   <li><b>404</b> — ese predio no esta en el padron de esta municipalidad. Lo arregla dar de alta
 *       el predio.
 *   <li><b>422</b> — el predio esta y <b>no tiene poligono</b>. Lo arregla cargar el plano
 *       catastral (ADR-0021).
 *   <li><b>404</b> — el predio tiene poligono y ningun plan vigente a esa fecha lo cubre. Lo
 *       arregla aprobar la zonificacion de esa area.
 * </ul>
 *
 * <p><b>El 422 del predio sin poligono es el punto de este endpoint</b>, no un borde. Hoy no hay ni
 * un poligono cargado en ninguna instalacion, asi que es el camino que se va a recorrer siempre al
 * principio, y un {@code 200} con {@code "zona": null} seria <b>indistinguible</b> de «este predio
 * esta en zona nula». La diferencia no es academica: quien evalua una licencia de funcionamiento
 * lee esa respuesta, y una zona nula no admite ningun giro. Un dato que falta acabaria negando una
 * licencia que la ordenanza permite, y nadie podria explicar por que.
 *
 * <h2>La fecha es un parametro y tiene valor por omision (regla 9)</h2>
 *
 * <p>No existe «la zona»: existe la zona vigente a una fecha, y la respuesta lleva la suya dentro.
 * Sin {@code aLaFecha} se toma hoy, del reloj <b>inyectado</b> y no de {@code LocalDate.now()}: es
 * lo que permite fijar la fecha en una prueba sin depender del dia en que corra.
 *
 * <p><b>Declara su acceso</b>: {@code zonificacion} es el id de esta opcion en el catalogo de
 * pantallas, el mismo que la implantacion siembra en la tabla {@code acceso} y que {@code
 * CatalogoDelSistemaTest} compara contra {@code CatalogoDelSistema}. Una regla de ArchUnit rompe el
 * build si un endpoint no lo declara.
 *
 * <p><b>Ningun metodo recibe la municipalidad</b>, ni como parametro ni como encabezado ni en el
 * cuerpo: sale del token (ADR-0005, regla 2).
 */
@RestController
@RequestMapping(Api.RAIZ + "/urbano/zonificacion")
@RequiereAcceso(acceso = "zonificacion", privilegio = Privilegio.LECTURA)
public class ZonificacionController {

    private final ZonificacionDelPredio zonificacion;
    private final Clock reloj;

    public ZonificacionController(ZonificacionDelPredio zonificacion, Clock reloj) {
        this.zonificacion = zonificacion;
        this.reloj = reloj;
    }

    @GetMapping
    public ZonaResource consultar(
            @RequestParam long predioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    @Nullable LocalDate aLaFecha) {
        LocalDate fecha = aLaFecha == null ? LocalDate.now(reloj) : aLaFecha;
        try {
            return ZonaResource.de(zonificacion.zonaDe(predioId, fecha), fecha);
        } catch (ZonificacionDelPredio.PredioInexistente | ZonificacionDelPredio.SinZonaVigente e) {
            throw new ProblemaDeNegocio(CodigoDeError.NO_ENCONTRADO, mensajeDe(e));
        } catch (ZonificacionDelPredio.PredioSinGeometria e) {
            // 422 y no 200 con la zona nula: ver el javadoc de la clase. Y no 404, porque el
            // predio SI esta: lo que falta es su poligono, y decir «no encontrado» mandaria a
            // quien atiende a buscar un predio que existe.
            throw new ProblemaDeNegocio(CodigoDeError.VALIDACION, mensajeDe(e));
        }
    }

    private static String mensajeDe(RuntimeException excepcion) {
        String mensaje = excepcion.getMessage();
        return mensaje == null ? "No se pudo determinar la zona del predio" : mensaje;
    }
}
