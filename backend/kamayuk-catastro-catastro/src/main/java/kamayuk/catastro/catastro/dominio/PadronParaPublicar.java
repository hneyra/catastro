package kamayuk.catastro.catastro.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Ejercicio;
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

    /** Las vias que tienen arancel publicado en el conjunto sellado de ese ejercicio (D-02b). */
    Set<Long> viasConArancel(long conjuntoId);

    /** Si el conjunto trae el cuadro de valores unitarios de edificacion (GOB-03 H-14). */
    boolean hayCuadroDeValoresUnitarios(long conjuntoId);

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
