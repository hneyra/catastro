package kamayuk.catastro.nucleo.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import kamayuk.catastro.dominio.AreaM2;
import kamayuk.catastro.dominio.Medida;
import org.jspecify.annotations.Nullable;

/**
 * Lo que hay que leer para publicar el TERRITORIO al buzon de salida (#7).
 *
 * <p>Tres cosas y no una: la manzana, los frentes de cada predio y el hallazgo que quedo firme. Es
 * el mismo mecanismo que {@link PadronParaPublicar} —una lectura completa, en un solo instante, sin
 * paginar— y por el mismo motivo: lo que se publica es el territorio entero y un cursor no
 * sobrevive a que alguien inscriba una manzana en medio.
 *
 * <h2>Por que el hallazgo se lee AQUI y no lo publica {@code fiscalizacion}</h2>
 *
 * <p>Porque el buzon de salida es de {@code nucleo} —{@code BuzonDeSalida}, {@code PublicarUnHecho}
 * y {@code ComponedorDeHechos} viven ahi— y Spring Modulith trata como interno todo lo que esta en
 * un subpaquete: {@code fiscalizacion} no puede importarlos. Sacarlos a un puerto publico de {@code
 * nucleo} para que los usara tampoco vale, y no por Modulith sino por la regla que ya existe:
 * {@code SOLO_LA_TRANSFERENCIA_ESCRIBE_FUERA_DE_FISCALIZACION} clasifica cada tipo de {@code
 * nucleo} que {@code fiscalizacion} toca en «solo lectura» o «puerto de escritura», y un puerto que
 * escribe en el buzon es lo segundo — con lo cual solo {@code TransferirARentas} podria usarlo, que
 * es exactamente lo contrario de lo que hace falta.
 *
 * <p>Asi que el cruce se resuelve donde este proyecto ya lo resuelve: <b>en una sola sentencia
 * SQL</b>, leyendo {@code hallazgo} desde el repositorio de {@code nucleo}. Es lo mismo que hacen
 * {@code grd} y {@code urbano} con {@code predio} —lo dice {@code ModulosTest} con todas las
 * letras— y no cruza ninguna frontera de sistema: {@code hallazgo} es una tabla de {@code
 * catastro}, y el escaner de la regla 11 lo comprueba.
 *
 * <p><b>Y publicar no es corregir.</b> Un hallazgo firme habilita el acto —versionar la ficha con
 * su observacion— y no lo ejecuta (ADR-0035 punto 4). Aqui se lee y se manda; el area del padron no
 * se toca, y ninguna clase de este camino depende de un camino de escritura de la ficha.
 */
public interface TerritorioParaPublicar {

    /** Las manzanas con su sector, en orden ascendente de identificador. */
    List<ManzanaDelTerritorio> manzanas();

    /**
     * Los frentes agrupados por predio, en orden de predio y de via.
     *
     * <p>Agrupados y no sueltos porque el hecho es «a que da este predio»: ver {@code
     * IdentidadDelEvento.deLosFrentesDeUnPredio}.
     */
    List<FrentesDeUnPredio> frentesPorPredio();

    /** Los hallazgos que estan FIRMES, en orden ascendente de identificador. */
    List<HallazgoFirme> hallazgosFirmes();

    /** Una manzana con el sector al que pertenece. */
    record ManzanaDelTerritorio(
            long manzanaId, String codigo, String sectorCodigo, String sectorNombre) {

        public ManzanaDelTerritorio {
            Objects.requireNonNull(codigo, "Toda manzana tiene codigo");
            Objects.requireNonNull(sectorCodigo, "Toda manzana esta en un sector");
            Objects.requireNonNull(sectorNombre, "Todo sector tiene nombre");
        }
    }

    /** Los frentes de un predio, con el codigo con el que {@code rentas} lo conoce. */
    record FrentesDeUnPredio(
            long predioId, String codigoRefCatastral, List<FrenteDelTerritorio> frentes) {

        public FrentesDeUnPredio {
            Objects.requireNonNull(codigoRefCatastral, "Todo predio tiene codigo catastral");
            frentes = List.copyOf(frentes);
            if (frentes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Un predio sin frentes no se publica: el hecho es «a que vias da este"
                                + " predio», y una lista vacia no dice si es que no da a ninguna o"
                                + " si es que nadie lo ha derivado (ver DerivacionDeFrentes)");
            }
        }
    }

    /**
     * Un frente, tal como viaja.
     *
     * <p>Lleva el estado de su longitud, y ese campo es la mitad del hecho: quien determine un
     * arbitrio sobre metros que nadie confirmo tiene que poder saberlo (ADR-0021).
     */
    record FrenteDelTerritorio(
            long frenteId,
            long viaId,
            String viaCodigo,
            String viaNombre,
            Medida longitud,
            EstadoDeLaLongitud estado,
            boolean esPrincipal,
            @Nullable String numeracion,
            @Nullable Medida retiro) {

        public FrenteDelTerritorio {
            Objects.requireNonNull(viaCodigo, "Un frente da a una via, y la via tiene codigo");
            Objects.requireNonNull(viaNombre, "Un frente da a una via, y la via tiene nombre");
            Objects.requireNonNull(longitud, "Un frente tiene su longitud");
            Objects.requireNonNull(estado, "Un frente dice de donde sale su longitud");
        }
    }

    /**
     * Un hallazgo firme, tal como viaja.
     *
     * <p>Ni un importe: lo hallado son dos superficies y su contraste, y cuanto se cobra por la
     * diferencia lo decide {@code rentas} (ADR-0024). Y ninguna de las cinco tablas de {@code V9}
     * tiene columna de importe, asi que no hay ninguno que copiar.
     *
     * @param predioId nulo en un {@code OMISO_CATASTRAL}, que es —por definicion— lo que no tiene
     *     predio
     * @param areaDeLaFicha nula por lo mismo: no hay ficha de la que diferir
     */
    record HallazgoFirme(
            long hallazgoId,
            long candidatoId,
            String clase,
            @Nullable Long predioId,
            @Nullable Long fichaId,
            @Nullable AreaM2 areaDeLaFicha,
            AreaM2 areaVerificada,
            String inspector,
            LocalDate verificadoEn) {

        public HallazgoFirme {
            Objects.requireNonNull(clase, "Un hallazgo tiene su clase");
            Objects.requireNonNull(areaVerificada, "Un hallazgo trae el area que se verifico");
            Objects.requireNonNull(inspector, "Un hallazgo lleva NOMBRE: quien lo verifico");
            Objects.requireNonNull(verificadoEn, "Un hallazgo trae la fecha en que se verifico");
        }
    }
}
