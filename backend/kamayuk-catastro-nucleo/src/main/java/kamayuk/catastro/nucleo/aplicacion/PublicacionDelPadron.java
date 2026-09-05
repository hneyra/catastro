package kamayuk.catastro.nucleo.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kamayuk.catastro.dominio.Ejercicio;
import kamayuk.catastro.nucleo.dominio.BuzonDeSalida;
import kamayuk.catastro.nucleo.dominio.HechoDeCatastro;
import kamayuk.catastro.nucleo.dominio.HuellaDelHecho;
import kamayuk.catastro.nucleo.dominio.PadronParaPublicar;
import kamayuk.catastro.nucleo.dominio.ValorizacionDelPredio;
import kamayuk.catastro.nucleo.dominio.ValuacionDelPredio;
import kamayuk.catastro.nucleo.infraestructura.ComponedorDeHechos;
import kamayuk.catastro.parametros.IdentificadorDeConjunto;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Publica el padron y su valuacion al buzon de salida (C-8, ADR-0027).
 *
 * <h2>Este objeto NO abre transaccion, y ahi esta la decision</h2>
 *
 * <p>La lectura la abre {@link LecturaDelPadronParaPublicar} y cada escritura la abre {@link
 * PublicarUnHecho}. Envolver el recorrido en una sola es el defecto que #328, #54, #430 y #247 §2
 * midieron cuatro veces: la fila rechazada marca la transaccion como <i>rollback-only</i> y se
 * lleva por delante el informe y todo lo ya publicado.
 *
 * <h2>Las dos publicaciones son distintas y van separadas</h2>
 *
 * <ul>
 *   <li><b>Proyectar el padron</b> es barato de repetir: la identidad de cada hecho se deriva de su
 *       contenido, asi que reproyectar cuesta —del lado del receptor— exactamente los predios que
 *       cambiaron. Se puede correr todos los dias.
 *   <li><b>Correr la valuacion</b> es un acto de un ejercicio, fija su conjunto sellado y termina
 *       con un cierre. Su cierre es lo unico que abre el candado de ADR-0027 §2.
 * </ul>
 */
@Service
public class PublicacionDelPadron {

    private final LecturaDelPadronParaPublicar lectura;
    private final PublicarUnHecho publicador;
    private final ComponedorDeHechos componedor;
    private final BuzonDeSalida buzon;
    private final JdbcClient jdbc;
    private final Clock reloj;

    public PublicacionDelPadron(
            LecturaDelPadronParaPublicar lectura,
            PublicarUnHecho publicador,
            ComponedorDeHechos componedor,
            BuzonDeSalida buzon,
            JdbcClient jdbc,
            Clock reloj) {
        this.lectura = lectura;
        this.publicador = publicador;
        this.componedor = componedor;
        this.buzon = buzon;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /**
     * Proyecta cada predio con las versiones de su ficha.
     *
     * <p>No necesita conjunto sellado —no valoriza nada— y por eso <b>si se puede correr hoy</b>,
     * con D-02a abierta. Es lo que alimenta {@code predio_ref} y {@code ficha_ref} de `rentas`, sin
     * las que la deteccion de omisos no cabe en un solo {@code WHERE} (#631).
     */
    public Informe proyectarElPadron(LocalDate aLaFecha) {
        Objects.requireNonNull(aLaFecha, "La fecha entra como argumento (regla 6, regla 9)");
        // Se lee con un conjunto que no se usa: la proyeccion no mira ninguna tabla de valuacion.
        // Se pasa 0 y no se resuelve ninguno a proposito — resolverlo aqui exigiria el ejercicio
        // sellado para una publicacion que no lo necesita, y dejaria la proyeccion del padron
        // bloqueada por D-02a sin ningun motivo.
        LecturaDelPadronParaPublicar.Padron padron = lectura.leer(aLaFecha, 0L);
        int nuevos = 0;
        int yaEstaban = 0;
        for (PadronParaPublicar.LoteDelPadron lote : padron.lotes()) {
            HechoDeCatastro hecho = componedor.delPredio(lote, padron.versionesDe(lote.predioId()));
            if (publicador.publicar(hecho) == BuzonDeSalida.Publicacion.NUEVO) {
                nuevos++;
            } else {
                yaEstaban++;
            }
        }
        return new Informe(padron.lotes().size(), nuevos, yaEstaban, null);
    }

    /**
     * Corre la valuacion de un ejercicio y la cierra.
     *
     * <p>El cierre va <b>al final y solo si todo lo demas se publico</b>: es lo unico que abre el
     * candado de `rentas`, y publicarlo antes seria decirle que estan todas cuando no lo estan.
     *
     * @throws kamayuk.catastro.parametros.LectorDeParametros.EjercicioSinSellar si el ejercicio no
     *     tiene conjunto sellado. Es el estado de HOY (D-02a) y por eso la corrida se niega
     *     nombrandolo en vez de fijar un conjunto inventado
     * @throws BuzonDeSalida.HechoSelladoReescrito si una valuacion ya publicada de ese ejercicio
     *     saldria ahora con otro contenido
     */
    public Informe correrLaValuacion(Ejercicio ejercicio, LocalDate fechaDeCorte) {
        Objects.requireNonNull(ejercicio, "Una corrida es de un ejercicio");
        Objects.requireNonNull(fechaDeCorte, "Una corrida tiene su fecha de corte (regla 9)");
        IdentificadorDeConjunto conjunto = lectura.conjuntoDeLaCorrida(ejercicio);
        LecturaDelPadronParaPublicar.Padron padron = lectura.leer(fechaDeCorte, conjunto.valor());
        PadronParaPublicar.Corrida corrida =
                new PadronParaPublicar.Corrida(ejercicio, fechaDeCorte);

        int nuevos = 0;
        int yaEstaban = 0;
        // EN EL ORDEN EN QUE `lotes()` los devuelve, que es `predio_id` ascendente. Ese orden es
        // el que decide la huella agregada, y es el mismo que el `ORDER BY h.predio_id` con que
        // `rentas` calcula la suya. Reordenar aqui —o agrupar con un `HashMap`— romperia el
        // candado sin que faltara ni una valuacion.
        List<String> huellas = new ArrayList<>();
        for (PadronParaPublicar.LoteDelPadron lote : padron.lotes()) {
            ValuacionDelPredio valuacion =
                    ValorizacionDelPredio.valorizar(
                            new ValorizacionDelPredio.Insumos(
                                    lote.predioId(),
                                    ejercicio.valor(),
                                    fechaDeCorte,
                                    padron.fichaVigentePorPredio().get(lote.predioId()),
                                    conjunto.valor(),
                                    ValorizacionDelPredio.VERSION,
                                    padron.hayCuadroDeValoresUnitarios(),
                                    padron.hayCuadroDeDepreciacion(),
                                    lote.viaId() != null
                                            && padron.viasConArancel().contains(lote.viaId()),
                                    padron.titularesDe(lote.predioId())));
            HechoDeCatastro hecho = componedor.deLaValuacion(valuacion);
            huellas.add(hecho.huella());
            if (publicador.publicar(hecho) == BuzonDeSalida.Publicacion.NUEVO) {
                nuevos++;
            } else {
                yaEstaban++;
            }
        }

        String huellaAgregada = HuellaDelHecho.deUnaCorrida(huellas);
        long corridaId = siguienteCorrida();
        HechoDeCatastro cierre =
                componedor.delCierre(
                        corridaId,
                        corrida,
                        conjunto.valor(),
                        ValorizacionDelPredio.VERSION,
                        huellas.size(),
                        huellaAgregada,
                        reloj.instant());
        publicador.publicar(cierre);
        return new Informe(padron.lotes().size(), nuevos, yaEstaban, corridaId);
    }

    /** Cuantos hechos quedan sin entregar. */
    public long pendientesDeEntregar() {
        return buzon.pendientesQueQuedan();
    }

    /**
     * El identificador de esta corrida, de la secuencia del motor.
     *
     * <p>Y no de un {@code max(...) + 1}: dos corridas simultaneas leerian el mismo maximo y se
     * darian el mismo numero, que es el hueco que #44 midio con {@code siguienteCorrelativo}.
     */
    private long siguienteCorrida() {
        Long siguiente =
                jdbc.sql("SELECT nextval('catastro_corrida_seq')").query(Long.class).single();
        return Objects.requireNonNull(siguiente, "nextval siempre devuelve un valor");
    }

    /**
     * Lo que hizo una publicacion.
     *
     * @param yaEstaban los que no hubo que escribir porque su contenido no cambio. No es un fallo:
     *     es el caso corriente de una reproyeccion, y contarlo aparte es lo que permite ver de un
     *     vistazo cuanto cambio el padron
     * @param corridaId el identificador de la corrida, o nulo si esto no fue una corrida
     */
    public record Informe(int leidos, int nuevos, int yaEstaban, @Nullable Long corridaId) {

        @Override
        public String toString() {
            return leidos
                    + " predio(s) leidos, "
                    + nuevos
                    + " hecho(s) publicados, "
                    + yaEstaban
                    + " sin cambios"
                    + (corridaId == null ? "" : ", corrida " + corridaId);
        }
    }
}
