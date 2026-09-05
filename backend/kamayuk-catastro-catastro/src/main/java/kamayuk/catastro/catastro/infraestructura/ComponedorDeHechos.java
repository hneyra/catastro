package kamayuk.catastro.catastro.infraestructura;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.catastro.dominio.CuotaDeTitular;
import kamayuk.catastro.catastro.dominio.HechoDeCatastro;
import kamayuk.catastro.catastro.dominio.HuellaDelHecho;
import kamayuk.catastro.catastro.dominio.IdentidadDelEvento;
import kamayuk.catastro.catastro.dominio.PadronParaPublicar;
import kamayuk.catastro.catastro.dominio.TipoDeEventoDeCatastro;
import kamayuk.catastro.catastro.dominio.ValuacionDelPredio;
import kamayuk.catastro.compartido.TenantContext;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Dinero;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Convierte un hecho del padron en la fila que va al buzon: su cuerpo y su huella (C-8).
 *
 * <h2>Las dos cosas se componen AQUI y en el mismo metodo, y es deliberado</h2>
 *
 * <p>El cuerpo y la huella describen lo mismo. Compuestos en dos sitios, un campo nuevo entra en
 * uno y no en el otro, y entonces la huella deja de cubrir parte del hecho <b>sin que nada
 * falle</b> —el receptor sigue copiandola, la comparacion sigue cuadrando y lo que cambio no lo ve
 * nadie—. Es el mismo reparto que #397 impuso al «Estado» de la infraccion administrativa: una sola
 * expresion, no dos copias.
 *
 * <h2>La huella va sobre los campos y no sobre el JSON</h2>
 *
 * <p>Ver {@link ContratoDeEventos}. Sobre el JSON, la huella de todo lo ya publicado cambiaria al
 * cambiar de version de la libreria de serializacion.
 */
@Component
public class ComponedorDeHechos {

    private final JsonMapper json;

    public ComponedorDeHechos(JsonMapper json) {
        this.json = json;
    }

    /** La proyeccion de un predio con las versiones de su ficha. */
    public HechoDeCatastro delPredio(
            PadronParaPublicar.LoteDelPadron lote,
            List<PadronParaPublicar.VersionDeFicha> versiones) {
        List<ContratoDeEventos.FichaProyectada> fichas = new ArrayList<>();
        List<@Nullable String> campos = new ArrayList<>();
        campos.add(String.valueOf(lote.predioId()));
        campos.add(lote.codigoRefCatastral());
        campos.add(lote.direccion());
        campos.add(lote.sectorCodigo());
        campos.add(lote.estado());
        for (PadronParaPublicar.VersionDeFicha version : versiones) {
            String hasta =
                    version.vigenciaHasta() == null ? null : version.vigenciaHasta().toString();
            fichas.add(
                    new ContratoDeEventos.FichaProyectada(
                            version.fichaId(),
                            version.tipo(),
                            version.version(),
                            version.vigenciaDesde().toString(),
                            hasta,
                            version.areaTerreno(),
                            version.uso()));
            campos.add(String.valueOf(version.fichaId()));
            campos.add(version.tipo());
            campos.add(String.valueOf(version.version()));
            campos.add(version.vigenciaDesde().toString());
            campos.add(hasta);
            campos.add(textoDelArea(version.areaTerreno()));
            campos.add(version.uso());
        }
        String huella = HuellaDelHecho.deLosCampos(campos);
        return new HechoDeCatastro(
                IdentidadDelEvento.deUnPredioProyectado(municipalidad(), lote.predioId(), huella),
                TipoDeEventoDeCatastro.PREDIO_PROYECTADO,
                lote.predioId(),
                null,
                escribir(
                        new ContratoDeEventos.PredioProyectado(
                                lote.predioId(),
                                lote.codigoRefCatastral(),
                                lote.direccion(),
                                lote.sectorCodigo(),
                                lote.estado(),
                                List.copyOf(fichas))),
                huella);
    }

    /** El hecho sellado de una valuacion. */
    public HechoDeCatastro deLaValuacion(ValuacionDelPredio valuacion) {
        List<ContratoDeEventos.TitularConCuota> titulares = new ArrayList<>();
        List<@Nullable String> campos = new ArrayList<>();
        campos.add(String.valueOf(valuacion.predioId()));
        campos.add(String.valueOf(valuacion.ejercicio()));
        campos.add(valuacion.fechaDeCorte().toString());
        campos.add(importe(valuacion.valorTerreno()));
        campos.add(importe(valuacion.valorConstruccion()));
        campos.add(importe(valuacion.valorObras()));
        campos.add(importe(valuacion.valorDelPredio()));
        campos.add(valuacion.motivo());
        campos.add(valuacion.llaveQueFalta());
        campos.add(
                valuacion.fichaCatastralId() == null
                        ? null
                        : String.valueOf(valuacion.fichaCatastralId()));
        campos.add(String.valueOf(valuacion.conjuntoId()));
        campos.add(valuacion.reglasVersion());
        campos.add(valuacion.reglasAplicadas());
        for (CuotaDeTitular titular : valuacion.titulares()) {
            titulares.add(
                    new ContratoDeEventos.TitularConCuota(
                            titular.contribuyenteId(), titular.condicion(), titular.cuota()));
            campos.add(String.valueOf(titular.contribuyenteId()));
            campos.add(titular.condicion());
            campos.add(titular.cuota().valor().toPlainString());
        }
        String huella = HuellaDelHecho.deLosCampos(campos);
        return new HechoDeCatastro(
                IdentidadDelEvento.deUnaValuacion(
                        municipalidad(), valuacion.ejercicio(), valuacion.predioId()),
                TipoDeEventoDeCatastro.VALUACION_PUBLICADA,
                valuacion.predioId(),
                valuacion.ejercicio(),
                escribir(
                        new ContratoDeEventos.ValuacionPublicada(
                                valuacion.predioId(),
                                valuacion.ejercicio(),
                                valuacion.fechaDeCorte().toString(),
                                valuacion.valorTerreno(),
                                valuacion.valorConstruccion(),
                                valuacion.valorObras(),
                                valuacion.valorDelPredio(),
                                valuacion.motivo(),
                                valuacion.llaveQueFalta(),
                                valuacion.fichaCatastralId(),
                                valuacion.conjuntoId(),
                                valuacion.reglasVersion(),
                                valuacion.reglasAplicadas(),
                                List.copyOf(titulares))),
                huella);
    }

    /** El cierre de la corrida, con su conteo y su huella agregada. */
    public HechoDeCatastro delCierre(
            long corridaId,
            PadronParaPublicar.Corrida corrida,
            long conjuntoId,
            String reglasVersion,
            int conteo,
            String huellaAgregada,
            Instant cerradaEn) {
        String huella =
                HuellaDelHecho.deLosCampos(
                        List.of(
                                String.valueOf(corridaId),
                                String.valueOf(corrida.ejercicio().valor()),
                                corrida.fechaDeCorte().toString(),
                                String.valueOf(conjuntoId),
                                reglasVersion,
                                String.valueOf(conteo),
                                huellaAgregada));
        return new HechoDeCatastro(
                IdentidadDelEvento.deUnCierreDeCorrida(
                        municipalidad(), corrida.ejercicio().valor(), corridaId),
                TipoDeEventoDeCatastro.CORRIDA_CERRADA,
                null,
                corrida.ejercicio().valor(),
                escribir(
                        new ContratoDeEventos.CorridaCerrada(
                                corridaId,
                                corrida.ejercicio().valor(),
                                corrida.fechaDeCorte().toString(),
                                conjuntoId,
                                reglasVersion,
                                conteo,
                                huellaAgregada,
                                cerradaEn.toString())),
                huella);
    }

    private static @Nullable String importe(@Nullable Dinero dinero) {
        return dinero == null ? null : dinero.valor().toPlainString();
    }

    /**
     * El area, como texto, PARA LA HUELLA y para nada mas.
     *
     * <p>Esta clase esta en {@code componenElAreaAManoConMotivo()} por esta linea, y el motivo es
     * el mismo que el de las otras dos entradas de esa lista: <b>esto no es una proyeccion
     * HTTP</b>, es un campo de un resumen criptografico que no pasa por ningun serializador. El
     * JSON del evento si lo escribe {@code ConfiguracionDeJson}, con el {@code AreaM2} tipado, y
     * ahi sale la cifra sola como en todo el sistema.
     *
     * <p>Y escribe la <b>cifra sola</b>, sin unidad: si escribiera «360.00 m2», la huella dejaria
     * de poder compararse contra ninguna otra cosa que hable de la misma area.
     */
    private static String textoDelArea(AreaM2 area) {
        // El parametro se llama `area` A PROPOSITO: asi el escaner de #607 CAZA esta linea y esta
        // clase tiene que estar nombrada en `componenElAreaAManoConMotivo()`. Llamarlo «medida» la
        // dejaria pasar sin que nadie hubiera decidido nada — y una exencion que no se ve en el
        // diff es peor que no tener la regla.
        return area.valor().toPlainString();
    }

    /**
     * La municipalidad sale del contexto, nunca de un parametro (regla 2).
     *
     * <p>Entra en la identidad del evento —y no en su cuerpo— porque el receptor ya sabe de quien
     * es: el token con el que se le entrega lo dice, y su politica RLS lo aplica. Lo que la
     * identidad necesita es no chocar entre municipalidades.
     */
    private static long municipalidad() {
        return TenantContext.actual().valor();
    }

    private String escribir(Object cuerpo) {
        try {
            return json.writeValueAsString(cuerpo);
        } catch (JacksonException noSePuede) {
            // No puede pasar con records de campos simples. Si pasara, el hecho NO se publica: un
            // evento sin cuerpo no se puede entregar ni explicar.
            throw new IllegalStateException(
                    "No se pudo componer el cuerpo del hecho a publicar", noSePuede);
        }
    }
}
