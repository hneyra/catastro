package kamayuk.catastro.nucleo.aplicacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kamayuk.catastro.dominio.Ejercicio;
import kamayuk.catastro.nucleo.dominio.CuotaDeTitular;
import kamayuk.catastro.nucleo.dominio.PadronParaPublicar;
import kamayuk.catastro.parametros.IdentificadorDeConjunto;
import kamayuk.catastro.parametros.LectorDeParametros;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lee de una vez todo lo que hace falta para publicar el padron (C-8).
 *
 * <h2>Una transaccion de LECTURA, y aparte de la de cada escritura</h2>
 *
 * <p>Las tres consultas van juntas para que describan <b>el mismo instante</b>: leidas en tres
 * transacciones, entre la de predios y la de fichas cabe una version nueva y la corrida publicaria
 * un predio con la ficha de otro momento. Es lo que #486 arreglo para las cuatro fichas, que hacian
 * tres consultas en tres transacciones distintas.
 *
 * <p>Y va aparte de las escrituras porque una lectura de 14 422 predios no puede compartir
 * transaccion con la publicacion de 14 422 hechos: seria la transaccion larga que el bucle existe
 * para no tener.
 *
 * <p><b>Sin transaccion no hay {@code SET LOCAL} y RLS no devuelve vacio: revienta</b> (#486). Por
 * eso la anotacion esta aqui y no es decorativa.
 */
@Service
public class LecturaDelPadronParaPublicar {

    private final PadronParaPublicar padron;
    private final LectorDeParametros parametros;

    public LecturaDelPadronParaPublicar(PadronParaPublicar padron, LectorDeParametros parametros) {
        this.padron = padron;
        this.parametros = parametros;
    }

    /**
     * El conjunto sellado que LA CORRIDA fija (ADR-0027 §2).
     *
     * <p>Lo fija la corrida y no lo resuelve cada sistema por su cuenta: si {@code catastro}
     * resolviera el suyo y {@code rentas} el suyo, un sellado publicado entre las dos resoluciones
     * produciria un padron calculado con dos conjuntos y <b>ningun error visible</b>.
     *
     * @throws LectorDeParametros.EjercicioSinSellar si el ejercicio no tiene conjunto sellado, que
     *     es el estado de HOY en toda municipalidad (D-02a). No hay valor por omision: sin conjunto
     *     no hay corrida
     */
    @Transactional(readOnly = true)
    public IdentificadorDeConjunto conjuntoDeLaCorrida(Ejercicio ejercicio) {
        return parametros.conjuntoVigenteEn(ejercicio);
    }

    /** El padron entero, en un solo instante. */
    @Transactional(readOnly = true)
    public Padron leer(LocalDate fechaDeCorte, long conjuntoId) {
        List<PadronParaPublicar.LoteDelPadron> lotes = padron.lotes();

        Map<Long, List<PadronParaPublicar.VersionDeFicha>> fichasPorPredio = new LinkedHashMap<>();
        for (PadronParaPublicar.VersionDeFicha version : padron.versionesDeFicha()) {
            fichasPorPredio
                    .computeIfAbsent(version.predioId(), predio -> new ArrayList<>())
                    .add(version);
        }

        Map<Long, Long> fichaVigente = new HashMap<>();
        for (PadronParaPublicar.FichaVigente vigente : padron.fichasVigentesA(fechaDeCorte)) {
            fichaVigente.put(vigente.predioId(), vigente.fichaId());
        }

        Map<Long, List<CuotaDeTitular>> titulares = new HashMap<>();
        for (PadronParaPublicar.TitularDelPredio titular : padron.titularesA(fechaDeCorte)) {
            titulares
                    .computeIfAbsent(titular.predioId(), predio -> new ArrayList<>())
                    .add(titular.cuota());
        }

        return new Padron(
                lotes,
                fichasPorPredio,
                fichaVigente,
                titulares,
                padron.viasConArancel(conjuntoId),
                padron.hayCuadroDeValoresUnitarios(conjuntoId),
                padron.hayCuadroDeDepreciacion(conjuntoId));
    }

    /** Cuantos predios hay, para poder decir «faltan tres de 14 422». */
    @Transactional(readOnly = true)
    public long cuantosPredios() {
        return padron.cuantosPredios();
    }

    /**
     * El padron leido en un instante.
     *
     * @param lotes en orden ascendente de {@code predioId}. <b>Ese orden decide la huella
     *     agregada</b> de la corrida, asi que no se reordena aqui ni despues
     */
    public record Padron(
            List<PadronParaPublicar.LoteDelPadron> lotes,
            Map<Long, List<PadronParaPublicar.VersionDeFicha>> versionesPorPredio,
            Map<Long, Long> fichaVigentePorPredio,
            Map<Long, List<CuotaDeTitular>> titularesPorPredio,
            Set<Long> viasConArancel,
            boolean hayCuadroDeValoresUnitarios,
            boolean hayCuadroDeDepreciacion) {

        public Padron {
            Objects.requireNonNull(lotes, "El padron leido, aunque este vacio");
        }

        /** Las versiones de ficha de un predio, o ninguna. */
        public List<PadronParaPublicar.VersionDeFicha> versionesDe(long predioId) {
            return versionesPorPredio.getOrDefault(predioId, List.of());
        }

        /** Los titulares de un predio a la fecha de corte, o ninguno. */
        public List<CuotaDeTitular> titularesDe(long predioId) {
            return titularesPorPredio.getOrDefault(predioId, List.of());
        }
    }
}
