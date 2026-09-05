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
 * Tabla de depreciacion: {@code GET /api/v1/catastro/tablas/depreciacion?ejercicio=2026} (RF-009).
 *
 * <p>Igual que {@link ArancelController}: devuelve el conjunto sellado vigente del ejercicio, y
 * {@code NO_ENCONTRADO} si el ejercicio no tiene ninguno —con el discriminador dentro desde #723,
 * porque «no hay conjunto sellado» y «la tabla de depreciacion no existe» son dos cosas distintas y
 * solo una la arregla quien publica—. El motivo de que el codigo sea 404 y no 422 esta escrito una
 * sola vez, en {@link ArancelController}.
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
@RequestMapping(Api.RAIZ + "/catastro/tablas/depreciacion")
@RequiereAcceso(acceso = "depreciacion", privilegio = Privilegio.LECTURA)
public class DepreciacionController {

    private final TablasDeValuacion tablas;

    public DepreciacionController(TablasDeValuacion tablas) {
        this.tablas = tablas;
    }

    @GetMapping
    public List<DepreciacionResource> listar(@RequestParam int ejercicio) {
        try {
            return tablas.depreciaciones(new Ejercicio(ejercicio)).stream()
                    .map(DepreciacionResource::de)
                    .toList();
        } catch (LectorDeParametros.EjercicioSinSellar excepcion) {
            throw FaltaPublicar.noEncontrado(excepcion);
        }
    }
}
