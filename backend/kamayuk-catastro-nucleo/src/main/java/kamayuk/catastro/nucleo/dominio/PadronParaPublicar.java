package kamayuk.catastro.nucleo.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Ejercicio;
import kamayuk.catastro.dominio.ValorNormativo;
import org.jspecify.annotations.Nullable;

/**
 * Lo que hay que leer del padron para publicarlo (C-8).
 *
 * <h2>Por que devuelve el padron entero y no una pagina</h2>
 *
 * <p>Porque lo que se publica es el padron entero: una corrida de valuacion recorre todos los
 * predios de la municipalidad y una proyeccion tambien. Paginar aqui obligaria a mantener un cursor
 * estable mientras alguien ficha un predio, y el orden por {@code predio_id} —que es el que decide
 * la huella agregada— no sobrevive a una insercion en medio de una pagina.
 *
 * <p>Es lo mismo que hace {@code HuellasDelPadronJdbc} para la anti-entropia, y por lo mismo. En
 * Catacaos son 14 422 predios: una consulta que devuelve cinco columnas de cada uno.
 */
public interface PadronParaPublicar {

    /** Los predios, en orden ascendente de identificador. */
    List<LoteDelPadron> lotes();

    /** Las versiones de ficha de todos ellos, en orden de predio y version. */
    List<VersionDeFicha> versionesDeFicha();

    /**
     * La ficha vigente a la fecha, por predio.
     *
     * <p>Se pregunta por la fecha y no por «la ultima»: es el defecto que #24 midio con los
     * domicilios y #366 con los titulares — resolver con el reloj contesta una reclamacion de 2024
     * con lo que rige hoy.
     */
    List<FichaVigente> fichasVigentesA(LocalDate fecha);

    /** Los titulares con su cuota, vigentes a la fecha, de todos los predios. */
    List<TitularDelPredio> titularesA(LocalDate fecha);

    /** Las construcciones de todas las fichas, en orden de ficha y piso. */
    List<ConstruccionDeLaFicha> construcciones();

    /** Cuantas obras complementarias o instalaciones fijas declara cada ficha. */
    List<ObrasDeLaFicha> obrasComplementarias();

    /**
     * El arancel <b>sin tramo</b> de cada via, en el conjunto sellado (D-02b).
     *
     * <p>Devuelve el VALOR y no un conjunto de identificadores, porque el arancel es lo que
     * valoriza el terreno y una corrida que solo supiera «esta via tiene arancel» tendria que
     * volver a preguntar por cada predio.
     *
     * <p><b>Sin tramo</b>, y esa es la unica forma que hoy se puede usar: {@code arancel} admite
     * varios tramos por via ({@code arancel_uq} es (municipalidad, conjunto, via, tramo)) y {@code
     * predio} no dice en que tramo de su via esta, asi que una via que solo publique tramos deja a
     * sus predios sin arancel aplicable. El indice {@code arancel_sin_tramo_uq} garantiza que el
     * que no lleva tramo sea uno solo.
     */
    Map<Long, ValorNormativo> arancelSinTramoPorVia(long conjuntoId);

    /**
     * El cuadro de valores unitarios de edificacion del conjunto, entero (GOB-03 H-14).
     *
     * <p>Entero y de una vez, no una celda por predio: es la propiedad de ADR-0025 §1 aplicada al
     * camino caliente. Vacio significa que el conjunto no lo compuso.
     */
    List<ValorUnitarioEdificacion> cuadroDeValoresUnitarios(long conjuntoId);

    /** Si el conjunto trae el cuadro de depreciacion (GOB-03 H-15). */
    boolean hayCuadroDeDepreciacion(long conjuntoId);

    /** Cuantos predios tiene el padron. Para poder decir «faltan tres de 14 422». */
    long cuantosPredios();

    /**
     * Un predio, con las cinco columnas que la proyeccion de {@code rentas} copia mas la via.
     *
     * <p>Las cinco primeras son EXACTAMENTE las que {@link HuellaDelLote} resume, y no por
     * casualidad: la anti-entropia compara esa huella contra la que {@code rentas} calcula sobre
     * {@code predio_ref}, asi que lo que se proyecta y lo que se compara tienen que ser lo mismo.
     * La via no viaja en la proyeccion —{@code predio_ref} no la tiene— y se lee aqui porque el
     * arancel cuelga de ella.
     */
    record LoteDelPadron(
            long predioId,
            String codigoRefCatastral,
            String direccion,
            @Nullable String sectorCodigo,
            String estado,
            @Nullable Long viaId) {

        public LoteDelPadron {
            Objects.requireNonNull(codigoRefCatastral, "Todo predio tiene codigo catastral");
            Objects.requireNonNull(direccion, "Todo predio tiene direccion");
            Objects.requireNonNull(estado, "Todo predio tiene estado");
        }
    }

    /** Una version de ficha, con su rango de vigencia. */
    record VersionDeFicha(
            long fichaId,
            long predioId,
            String tipo,
            int version,
            LocalDate vigenciaDesde,
            @Nullable LocalDate vigenciaHasta,
            AreaM2 areaTerreno,
            String uso) {

        public VersionDeFicha {
            Objects.requireNonNull(tipo, "Toda ficha dice de que tipo es");
            Objects.requireNonNull(vigenciaDesde, "Toda version de ficha rige desde una fecha");
            Objects.requireNonNull(areaTerreno, "Toda ficha lleva su area de terreno");
            Objects.requireNonNull(uso, "Toda ficha lleva su uso");
        }
    }

    /** Que ficha regia un predio a la fecha de corte. */
    record FichaVigente(long predioId, long fichaId) {}

    /**
     * Una construccion de una ficha, con lo que el cuadro de valores unitarios necesita.
     *
     * <p>Las tres categorias son las de las <b>tres partidas de apreciacion exterior</b> que el
     * cuadro publica (V59), y no las siete columnas {@code categoria_*} de {@code construccion}:
     * las otras cuatro describen la edificacion y no le ponen precio, porque ninguna region del
     * Anexo I las publica.
     */
    record ConstruccionDeLaFicha(
            long fichaId,
            String piso,
            AreaM2 areaConstruida,
            @Nullable Integer anioConstruccion,
            @Nullable Character categoriaMuros,
            @Nullable Character categoriaTechos,
            @Nullable Character categoriaPuertas) {

        public ConstruccionDeLaFicha {
            Objects.requireNonNull(piso, "Toda construccion dice en que piso esta");
            Objects.requireNonNull(areaConstruida, "Toda construccion lleva su area construida");
        }
    }

    /** Cuantas obras complementarias declara una ficha. */
    record ObrasDeLaFicha(long fichaId, int cuantas) {}

    /** Un titular de un predio, con su cuota a la fecha. */
    record TitularDelPredio(long predioId, CuotaDeTitular cuota) {

        public TitularDelPredio {
            Objects.requireNonNull(cuota, "Un titular llega con su cuota");
        }
    }

    /** Que ejercicio se esta valorizando. Existe para no pasar un {@code int} suelto. */
    record Corrida(Ejercicio ejercicio, LocalDate fechaDeCorte) {

        public Corrida {
            Objects.requireNonNull(ejercicio, "Una corrida es de un ejercicio");
            Objects.requireNonNull(fechaDeCorte, "Una corrida tiene su fecha de corte (regla 9)");
        }
    }
}
