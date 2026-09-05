package kamayuk.catastro.nucleo.infraestructura.web;

import java.util.List;
import kamayuk.catastro.autorizacion.Privilegio;
import kamayuk.catastro.autorizacion.RequiereAcceso;
import kamayuk.catastro.nucleo.aplicacion.ConsultaDeHuellas;
import kamayuk.catastro.web.Api;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las huellas del padron, para que {@code rentas} compare su proyeccion (P6, punto 4, ADR-0030).
 *
 * <p>Dos formas de la misma ruta, y la escalera es el diseño: <b>sin</b> {@code sector} devuelve
 * una cifra por sector —decenas de filas, que es lo que se compara a diario—, y <b>con</b> {@code
 * sector} devuelve los lotes de ese sector con su huella, que es lo que se pide solo del que no
 * cuadro. Pedir siempre el detalle seria leer el catastro entero cada dia; pedir solo el resumen no
 * diria nunca cual lote difiere.
 *
 * <p><b>No publica ni un dato del predio</b> —ni direccion, ni codigo, ni titular—: solo
 * identificadores y huellas. Una anti-entropia no necesita ver el dato para saber que dos lados no
 * cuadran, y publicarlo convertiria esta ruta en una segunda forma de leer el padron entero, con
 * otro permiso y sin paginar.
 */
@RestController
@RequestMapping(Api.RAIZ + "/catastro/predios/huellas")
public class HuellasController {

    /**
     * El mismo acceso que la consulta de fichas.
     *
     * <p>No estrena opcion del catalogo a proposito: esto no es una pantalla, es la lectura que un
     * proceso de otro sistema hace de este padron, y darle acceso propio obligaria a inventar una
     * opcion que ningun manual dibuja.
     */
    private static final String ACCESO = "consulta_fichas";

    private final ConsultaDeHuellas consulta;

    public HuellasController(ConsultaDeHuellas consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    @RequiereAcceso(acceso = ACCESO, privilegio = Privilegio.LECTURA)
    public RespuestaDeHuellas huellas(
            @RequestParam(required = false) @Nullable String sector,
            @RequestParam(required = false, defaultValue = "false") boolean detalle) {

        if (!detalle) {
            return new RespuestaDeHuellas(
                    consulta.porSector().stream()
                            .map(
                                    fila ->
                                            new SectorResource(
                                                    fila.sector(), fila.lotes(), fila.huella()))
                            .toList(),
                    List.of());
        }
        return new RespuestaDeHuellas(
                List.of(),
                consulta.deUnSector(sector).stream()
                        .map(fila -> new LoteResource(fila.predioId(), fila.huella()))
                        .toList());
    }

    /**
     * Las dos formas en una, y las dos declaradas siempre.
     *
     * <p>La que no se pidio viaja vacia en vez de ausente: un campo que a veces no esta obliga a
     * quien lo lee a distinguir «no lo pedi» de «no hay ninguno», y esas dos cosas se leen igual.
     */
    public record RespuestaDeHuellas(List<SectorResource> sectores, List<LoteResource> lotes) {}

    /** La huella de un sector, con cuantos lotes la componen. */
    public record SectorResource(@Nullable String sector, int lotes, String huella) {}

    /** La huella de un lote. Solo el identificador: ningun dato del predio sale por aqui. */
    public record LoteResource(long predioId, String huella) {}
}
