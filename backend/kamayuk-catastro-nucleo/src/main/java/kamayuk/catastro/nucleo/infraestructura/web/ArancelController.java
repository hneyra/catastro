package kamayuk.catastro.nucleo.infraestructura.web;

import java.util.List;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.autorizacion.RequiereAcceso;
import kamayuk.catastro.dominio.Ejercicio;
import kamayuk.catastro.nucleo.aplicacion.TablasDeValuacion;
import kamayuk.catastro.parametros.FaltaPublicar;
import kamayuk.catastro.parametros.LectorDeParametros;
import kamayuk.catastro.web.Api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aranceles de terreno: {@code GET /api/v1/catastro/tablas/aranceles?ejercicio=2026} (RF-009).
 *
 * <p>Devuelve el conjunto <b>sellado</b> vigente del ejercicio, nunca el ultimo cargado: si el
 * ejercicio no tiene ninguno, es {@code NO_ENCONTRADO} y no una lista vacia —una lista vacia diria
 * que la municipalidad no tiene aranceles, cuando lo que pasa es que todavia nadie cerro la carga
 * del ejercicio—.
 *
 * <h2>Por que sigue siendo 404, y por que el cuerpo lleva el discriminador (#723)</h2>
 *
 * <p>Esta ruta y sus dos hermanas —{@link DepreciacionController}, {@link ValorUnitarioController}—
 * eran las <b>tres unicas</b> del backend que traducian una excepcion de la familia «falta
 * publicar» a algo que no fuera un 422, y por eso el censo de #691 no las toco. #723 pregunta si el
 * codigo es el correcto. Lo es, por tres cosas medidas:
 *
 * <ol>
 *   <li><b>Lo que se pide es un documento, no un acto.</b> La ruta nombra un recurso —la tabla de
 *       aranceles sellada de ese ano— y cuando no hay ninguna, «no esta» es literalmente lo que
 *       pasa. Los 422 de esta familia estan todos detras de una peticion que el servidor intento
 *       <b>ejecutar</b>. Es la distincion que #540 y #547 ya dejaron escrita.
 *   <li><b>En esta misma ruta el 422 ya significa otra cosa.</b> {@code ?ejercicio=1800} construye
 *       un {@link Ejercicio} fuera de rango y sale como {@code 422 VALIDACION} sin discriminador:
 *       un error que quien atiende corrige tecleando un ano de verdad. Unificar pondria las dos
 *       cosas —una que se arregla en la pantalla y otra que no— bajo el mismo codigo justo donde
 *       conviven.
 *   <li><b>El codigo no es lo que le dice a un programa que hacer.</b> Eso lo dice el miembro
 *       {@code parametroQueFalta} (#604), que el contrato ya declara en el esquema compartido
 *       {@code Error} y que hasta #723 estas tres no llevaban. Con el, un 404 de aqui se distingue
 *       de los otros ciento trece del backend —«ese contribuyente no esta en el padron»— sin leer
 *       el texto.
 * </ol>
 *
 * <p>Lo que <b>no</b> se hizo, y conviene decirlo: no se cambio el estado. Cambiarlo habria movido
 * lo que sale por el cable sin cambiar lo que nadie puede hacer con ello —ningun cliente lee hoy
 * este cuerpo por programa—, mientras que el miembro cambia lo que se puede hacer sin mover el
 * cable.
 *
 * <h2>El parametro se llama {@code ejercicio} y no {@code anio} (C-1, desajuste 7)</h2>
 *
 * <p>Lo que acota es el <b>ejercicio</b> del conjunto sellado, que es un tipo del dominio ({@link
 * Ejercicio}, 1990..2100) y no un ano cualquiera. En esta misma familia de respuestas viaja un
 * {@code anioConstruccionDesde}, que si es un ano: llamar «anio» a los dos conflaba exactamente los
 * dos numeros que #723 tuvo que separar en {@code ValorUnitarioSinParametrizar}.
 *
 * <p>Se renombra en las <b>tres</b> lecturas de cuadro a la vez —aranceles, depreciacion y valores
 * unitarios—, y no solo en la que tenia el desajuste: son la misma ruta con otro nombre y sus
 * javadoc se citan entre si, asi que dejar dos vocabularios entre tres hermanas es el defecto que
 * #397 y #481 midieron. Nadie mas las llamaba: {@code catastro} no publica OpenAPI y el unico
 * cliente —{@code ValoresUnitariosHttp} de {@code rentas}— ya mandaba {@code ejercicio}.
 */
@RestController
@RequestMapping(Api.RAIZ + "/catastro/tablas/aranceles")
@RequiereAcceso(acceso = "aranceles", privilegio = Privilegio.LECTURA)
public class ArancelController {

    private final TablasDeValuacion tablas;

    public ArancelController(TablasDeValuacion tablas) {
        this.tablas = tablas;
    }

    @GetMapping
    public List<ArancelResource> listar(@RequestParam int ejercicio) {
        try {
            return tablas.aranceles(new Ejercicio(ejercicio)).stream()
                    .map(ArancelResource::de)
                    .toList();
        } catch (LectorDeParametros.EjercicioSinSellar excepcion) {
            throw FaltaPublicar.noEncontrado(excepcion);
        }
    }
}
