package kamayuk.catastro.nucleo.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Como se agrupan en el informe los predios que no dependen de ninguna llave.
     *
     * <p>Son los que NO se arreglan publicando nada: el predio sin ficha vigente y la construccion
     * cuya ficha no la describe entera. Se cuentan aparte a proposito, porque son la unica parte
     * del recuento que se cierra fichando y no decidiendo.
     */
    public static final String SIN_LLAVE = "SIN_LLAVE_(ES_DEL_CATASTRO)";

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
        // Se lee SIN conjunto: la proyeccion no mira ninguna tabla de valuacion. No se resuelve
        // ninguno a proposito —resolverlo aqui exigiria el ejercicio sellado para una publicacion
        // que no lo necesita, y dejaria la proyeccion del padron bloqueada por D-02a sin ningun
        // motivo—, y desde #8 se dice con un metodo propio y no con un `conjuntoId = 0`, que era
        // un centinela que se colaba hasta `IdentificadorDeConjunto.de(0)`.
        LecturaDelPadronParaPublicar.Padron padron = lectura.leerParaProyectar(aLaFecha);
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
        return new Informe(padron.lotes().size(), nuevos, yaEstaban, null, 0, Map.of());
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
        int valorizados = 0;
        // Cuantos predios se quedan sin cifra POR CADA LLAVE que falta. Es el entregable de #8:
        // convierte una decision abierta en algo que se puede contar y mirar, en vez de en una
        // frase. En orden de aparicion, que es el orden de las ramas de `ValorizacionDelPredio`.
        Map<String, Integer> sinCifraPorLlave = new LinkedHashMap<>();
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
                                    padron.fichaDeLaValuacion(lote.predioId()),
                                    conjunto.valor(),
                                    ValorizacionDelPredio.VERSION,
                                    padron.cuadroDeValoresUnitarios(),
                                    padron.hayCuadroDeDepreciacion(),
                                    padron.arancelDe(lote.viaId()),
                                    padron.porcentajeDeActualizacion(),
                                    padron.titularesDe(lote.predioId())));
            if (valuacion.seValorizo()) {
                valorizados++;
            } else {
                // Sin llave —el predio no tiene ficha, o su ficha no describe la edificacion— se
                // agrupa aparte y con nombre: son los que NO se arreglan publicando nada, y
                // meterlos en el mismo saco que los demas escondería la unica parte del recuento
                // que depende del catastro y no de una decision normativa.
                String llave =
                        valuacion.llaveQueFalta() == null ? SIN_LLAVE : valuacion.llaveQueFalta();
                sinCifraPorLlave.merge(llave, 1, Integer::sum);
            }
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
        return new Informe(
                padron.lotes().size(),
                nuevos,
                yaEstaban,
                corridaId,
                valorizados,
                Map.copyOf(sinCifraPorLlave));
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
     * @param valorizados cuantos predios salieron con sus cuatro cifras. Cero en una proyeccion,
     *     que no valoriza nada
     * @param sinCifraPorLlave cuantos se quedaron sin cifra por cada llave que falta. <b>Es el
     *     entregable de #8</b>: agrupa por lo que hay que decidir o publicar, de modo que «el
     *     sistema no valoriza» se convierte en «faltan estas tres cosas, y esta afecta a N
     *     predios». Los que no dependen de ninguna llave —el predio sin ficha, la construccion sin
     *     describir— se agrupan bajo {@link #SIN_LLAVE}
     */
    public record Informe(
            int leidos,
            int nuevos,
            int yaEstaban,
            @Nullable Long corridaId,
            int valorizados,
            Map<String, Integer> sinCifraPorLlave) {

        public Informe {
            sinCifraPorLlave =
                    Map.copyOf(Objects.requireNonNull(sinCifraPorLlave, "El recuento, o vacio"));
        }

        /** Cuantos predios quedaron con motivo en vez de con cifras. */
        public int sinValorizar() {
            return sinCifraPorLlave.values().stream().mapToInt(Integer::intValue).sum();
        }

        @Override
        public String toString() {
            StringBuilder texto = new StringBuilder();
            texto.append(leidos)
                    .append(" predio(s) leidos, ")
                    .append(nuevos)
                    .append(" hecho(s) publicados, ")
                    .append(yaEstaban)
                    .append(" sin cambios");
            if (corridaId != null) {
                texto.append(", corrida ").append(corridaId);
                texto.append("; ")
                        .append(valorizados)
                        .append(" valorizado(s), ")
                        .append(sinValorizar())
                        .append(" con motivo");
                // Ordenado por llave para que dos corridas del mismo padron impriman lo mismo: un
                // recuento cuyo orden dependa del `HashMap` no se puede comparar entre dias.
                for (Map.Entry<String, Integer> porLlave :
                        new java.util.TreeMap<>(sinCifraPorLlave).entrySet()) {
                    texto.append(" [")
                            .append(porLlave.getKey())
                            .append(": ")
                            .append(porLlave.getValue())
                            .append("]");
                }
            }
            return texto.toString();
        }
    }
}
