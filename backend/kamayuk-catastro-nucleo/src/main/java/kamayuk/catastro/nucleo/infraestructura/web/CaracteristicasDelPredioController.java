package kamayuk.catastro.nucleo.infraestructura.web;

import java.time.LocalDate;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.autorizacion.RequiereAcceso;
import kamayuk.catastro.nucleo.LectorDeFichas;
import kamayuk.catastro.nucleo.TitularesDelPredio;
import kamayuk.catastro.nucleo.aplicacion.ConsultaDeCaracteristicas;
import kamayuk.catastro.web.Api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que otro sistema necesita saber de un predio: si esta, que tiene inscrito a una fecha, y el
 * area de una version concreta de su ficha (C-5).
 *
 * <h2>Que rutas son y a que puertos contestan</h2>
 *
 * <p>Estas tres cerraban tres de los siete puertos que P5C dejo sin nadie que los contestara. Los
 * puertos no se tocaron —{@code LectorDeCaracteristicas}, {@code LectorDeFichas} y {@code
 * LectorDeFichasEconomicas} son los mismos de siempre—: lo que faltaba era la ruta.
 *
 * <ul>
 *   <li>{@code GET /catastro/predios/&#123;predioId&#125;} — {@code
 *       TitularesDelPredio.estaEnElPadron}
 *   <li>{@code GET /catastro/predios/&#123;predioId&#125;/caracteristicas?fecha=} — los tres
 *       lectores, en <b>una</b> peticion y una transaccion
 *   <li>{@code GET /catastro/fichas/&#123;fichaId&#125;/area} — {@code
 *       LectorDeFichas.areaDeLaVersion}
 * </ul>
 *
 * <h2>La fecha es obligatoria, y eso es deliberado</h2>
 *
 * <p>Las otras siete lecturas de esta capa web admiten {@code fecha} ausente y resuelven con el
 * reloj. Aqui no: los puertos que estas rutas sirven reciben <b>siempre</b> una fecha (regla 9), y
 * un cliente que la olvidara recibiria la ficha de hoy con 200 delante. Es exactamente el defecto
 * que C-1 encontro servido por HTTP —el nombre del parametro no coincidia, se descartaba en
 * silencio, y preguntar por marzo devolvia la ficha de hoy (#24, #366)—. Sin valor por omision, un
 * olvido es un 422 ruidoso y no una respuesta equivocada.
 *
 * <h2>Un 404 aqui significa «esa ruta no existe», y nada mas</h2>
 *
 * <p>«Ese predio no esta en el padron» y «esa version de ficha no existe» viajan como <b>campo</b>,
 * no como codigo de estado. Si fueran 404, un cliente que pidiera una ruta mal escrita —o un
 * despliegue que se quedara atras— leeria «el predio no esta», que es plausible y falso. Las dos
 * situaciones se arreglan de maneras opuestas: una es un dato del padron, la otra es un despliegue.
 *
 * <h2>El acceso</h2>
 *
 * <p>{@code consulta_fichas}, la opcion del catalogo (NEG-03) desde la que se consulta lo que el
 * catastro tiene inscrito, con {@code LECTURA}. Es la misma que ya cubre la grilla que {@code
 * rentas} consume, y estas tres publican menos que ella: ni el nombre del titular, ni el codigo del
 * padron. Lo que esto <b>no</b> resuelve esta declarado en el entregable de C-5: el token que llega
 * es el del funcionario, reenviado tal cual (ADR-0028 §1), asi que quien puede determinar en {@code
 * rentas} tiene que tener ademas esta opcion aqui.
 */
@RestController
@RequestMapping(Api.RAIZ + "/catastro")
@RequiereAcceso(acceso = "consulta_fichas", privilegio = Privilegio.LECTURA)
public class CaracteristicasDelPredioController {

    private final ConsultaDeCaracteristicas caracteristicas;
    private final LectorDeFichas fichas;
    private final TitularesDelPredio padron;

    public CaracteristicasDelPredioController(
            ConsultaDeCaracteristicas caracteristicas,
            LectorDeFichas fichas,
            TitularesDelPredio padron) {
        this.caracteristicas = caracteristicas;
        this.fichas = fichas;
        this.padron = padron;
    }

    /**
     * Si el predio esta inscrito en esta municipalidad.
     *
     * <p>Lo contesta {@code TitularesDelPredio.estaEnElPadron} y no un {@code
     * predio(id).isPresent()} escrito aqui, aunque la ruta no hable de titulares: «estar en el
     * padron» ya tiene <b>una</b> definicion en este sistema y es esa —la que a proposito <b>no
     * mira el estado</b>, porque un predio dado de baja sigue estando (#660, #680)—. Escribir la
     * segunda seria dos verdades sobre lo mismo, y la que se leyera decidiria si una deuda se puede
     * corregir.
     */
    @GetMapping("/predios/{predioId}")
    public PredioEnElPadronResource enElPadron(@PathVariable long predioId) {
        return new PredioEnElPadronResource(predioId, padron.estaEnElPadron(predioId));
    }

    /**
     * Lo inscrito de un predio a una fecha: su ficha unica, su ficha economica, su uso y su area.
     */
    @GetMapping("/predios/{predioId}/caracteristicas")
    public CaracteristicasDelPredioResource caracteristicas(
            @PathVariable long predioId, @RequestParam String fecha) {
        LocalDate cuando = DeclaracionDeFicha.fechaDe(fecha, "fecha");
        return CaracteristicasDelPredioResource.de(caracteristicas.de(predioId, cuando));
    }

    /**
     * El area de terreno de esa version de ficha.
     *
     * <p>Sin fecha, y no es un olvido: se pregunta por el identificador de <b>una version</b>, que
     * ya lleva su vigencia dentro. Anadirle una fecha sugeriria que la version se resuelve, y
     * resolverla es justo lo que quien pregunta no quiere: guarda ese identificador precisamente
     * para que su declaracion jurada de 2024 siga enlazada a la ficha de 2024.
     */
    @GetMapping("/fichas/{fichaId}/area")
    public AreaDeLaVersionResource areaDeLaVersion(@PathVariable long fichaId) {
        return fichas.areaDeLaVersion(fichaId)
                .map(area -> new AreaDeLaVersionResource(fichaId, true, area))
                .orElseGet(() -> new AreaDeLaVersionResource(fichaId, false, null));
    }
}
