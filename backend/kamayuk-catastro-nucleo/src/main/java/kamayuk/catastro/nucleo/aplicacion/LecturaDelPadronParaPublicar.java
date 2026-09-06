package kamayuk.catastro.nucleo.aplicacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kamayuk.catastro.dominio.Ejercicio;
import kamayuk.catastro.dominio.ValorNormativo;
import kamayuk.catastro.nucleo.dominio.CuotaDeTitular;
import kamayuk.catastro.nucleo.dominio.PadronParaPublicar;
import kamayuk.catastro.nucleo.dominio.ValorizacionDelPredio;
import kamayuk.catastro.parametros.IdentificadorDeConjunto;
import kamayuk.catastro.parametros.LectorDeParametros;
import org.jspecify.annotations.Nullable;
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

    /**
     * El padron entero, en un solo instante, <b>sin nada normativo</b>.
     *
     * <p>Es lo que necesita la proyeccion, que no valoriza: no lee cuadros, ni aranceles, ni
     * parametros sellados. <b>Y por eso es un metodo propio y no un {@code conjuntoId = 0}</b>, que
     * es como estaba: ese cero se colaba hasta {@code IdentificadorDeConjunto.de(0)} —que lo
     * rechaza, «el identificador de un conjunto es el que le dio la base: 0»— en cuanto alguien
     * leyera algo mas del conjunto. Lo dijo la primera ejecucion de #8, y la salida no era aceptar
     * el cero sino dejar de pasarlo.
     */
    @Transactional(readOnly = true)
    public Padron leerParaProyectar(LocalDate aLaFecha) {
        return componer(aLaFecha, null);
    }

    /** El padron entero, en un solo instante, con lo que el conjunto sellado trae. */
    @Transactional(readOnly = true)
    public Padron leer(LocalDate fechaDeCorte, long conjuntoId) {
        return componer(fechaDeCorte, IdentificadorDeConjunto.de(conjuntoId));
    }

    private Padron componer(LocalDate fechaDeCorte, @Nullable IdentificadorDeConjunto conjunto) {
        List<PadronParaPublicar.LoteDelPadron> lotes = padron.lotes();

        Map<Long, List<PadronParaPublicar.VersionDeFicha>> fichasPorPredio = new LinkedHashMap<>();
        for (PadronParaPublicar.VersionDeFicha version : padron.versionesDeFicha()) {
            fichasPorPredio
                    .computeIfAbsent(version.predioId(), predio -> new ArrayList<>())
                    .add(version);
        }

        Map<Long, Long> fichaVigente = new HashMap<>();
        Map<Long, PadronParaPublicar.VersionDeFicha> fichaDeLaCorrida = new HashMap<>();
        for (PadronParaPublicar.FichaVigente vigente : padron.fichasVigentesA(fechaDeCorte)) {
            fichaVigente.put(vigente.predioId(), vigente.fichaId());
            // La VERSION concreta que rige a la fecha, no «la ultima»: es de donde salen el area
            // de terreno y el uso con que se valoriza. Resolverla con el reloj contestaria una
            // reclamacion de 2024 con lo que rige hoy (#24, #366).
            for (PadronParaPublicar.VersionDeFicha version :
                    fichasPorPredio.getOrDefault(vigente.predioId(), List.of())) {
                if (version.fichaId() == vigente.fichaId()) {
                    fichaDeLaCorrida.put(vigente.predioId(), version);
                }
            }
        }

        Map<Long, List<CuotaDeTitular>> titulares = new HashMap<>();
        for (PadronParaPublicar.TitularDelPredio titular : padron.titularesA(fechaDeCorte)) {
            titulares
                    .computeIfAbsent(titular.predioId(), predio -> new ArrayList<>())
                    .add(titular.cuota());
        }

        Map<Long, List<PadronParaPublicar.ConstruccionDeLaFicha>> construcciones =
                new LinkedHashMap<>();
        for (PadronParaPublicar.ConstruccionDeLaFicha construccion : padron.construcciones()) {
            construcciones
                    .computeIfAbsent(construccion.fichaId(), ficha -> new ArrayList<>())
                    .add(construccion);
        }

        Map<Long, Integer> obras = new HashMap<>();
        for (PadronParaPublicar.ObrasDeLaFicha obra : padron.obrasComplementarias()) {
            obras.put(obra.fichaId(), obra.cuantas());
        }

        if (conjunto == null) {
            return new Padron(
                    lotes,
                    fichasPorPredio,
                    fichaVigente,
                    fichaDeLaCorrida,
                    titulares,
                    construcciones,
                    obras,
                    Map.of(),
                    new ValorizacionDelPredio.CuadroDeValoresUnitarios(List.of()),
                    false,
                    null);
        }
        long conjuntoId = conjunto.valor();
        return new Padron(
                lotes,
                fichasPorPredio,
                fichaVigente,
                fichaDeLaCorrida,
                titulares,
                construcciones,
                obras,
                padron.arancelSinTramoPorVia(conjuntoId),
                new ValorizacionDelPredio.CuadroDeValoresUnitarios(
                        padron.cuadroDeValoresUnitarios(conjuntoId)),
                padron.hayCuadroDeDepreciacion(conjuntoId),
                // El «% actualizacion» del conjunto SELLADO, o nulo si no lo trae. Nulo y cero no
                // son lo mismo: cero seria una cifra sellada con su fundamento y nulo es que nadie
                // la publico. HOY es nulo siempre: el archivo del corpus que lo respalda esta en
                // TRANSCRITO y le falta la segunda firma de ADR-0007 (D-11).
                parametros
                        .porConjunto(conjunto)
                        .numero(ValorizacionDelPredio.PORCENTAJE_DE_ACTUALIZACION, null)
                        .orElse(null));
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
            Map<Long, PadronParaPublicar.VersionDeFicha> fichaDeLaCorridaPorPredio,
            Map<Long, List<CuotaDeTitular>> titularesPorPredio,
            Map<Long, List<PadronParaPublicar.ConstruccionDeLaFicha>> construccionesPorFicha,
            Map<Long, Integer> obrasPorFicha,
            Map<Long, ValorNormativo> arancelPorVia,
            ValorizacionDelPredio.CuadroDeValoresUnitarios cuadroDeValoresUnitarios,
            boolean hayCuadroDeDepreciacion,
            @Nullable ValorNormativo porcentajeDeActualizacion) {

        public Padron {
            Objects.requireNonNull(lotes, "El padron leido, aunque este vacio");
            Objects.requireNonNull(cuadroDeValoresUnitarios, "El cuadro, aunque este vacio");
        }

        /** Las versiones de ficha de un predio, o ninguna. */
        public List<PadronParaPublicar.VersionDeFicha> versionesDe(long predioId) {
            return versionesPorPredio.getOrDefault(predioId, List.of());
        }

        /** Los titulares de un predio a la fecha de corte, o ninguno. */
        public List<CuotaDeTitular> titularesDe(long predioId) {
            return titularesPorPredio.getOrDefault(predioId, List.of());
        }

        /**
         * La ficha con que se valoriza un predio, o nulo si no tiene ninguna vigente.
         *
         * <p>Se compone aqui —y no en el bucle de la corrida— porque las construcciones y las obras
         * se leen <b>de una vez para todo el padron</b>, en la misma transaccion que el resto: una
         * consulta por predio dentro del bucle es el defecto que #486 arreglo y que ADR-0025 §1
         * evita para los parametros.
         */
        public ValorizacionDelPredio.@Nullable FichaDeLaValuacion fichaDeLaValuacion(
                long predioId) {
            PadronParaPublicar.VersionDeFicha ficha = fichaDeLaCorridaPorPredio.get(predioId);
            if (ficha == null) {
                return null;
            }
            return new ValorizacionDelPredio.FichaDeLaValuacion(
                    ficha.fichaId(),
                    ficha.areaTerreno(),
                    ficha.uso(),
                    construccionesPorFicha.getOrDefault(ficha.fichaId(), List.of()).stream()
                            .map(
                                    c ->
                                            new ValorizacionDelPredio.Edificacion(
                                                    c.piso(),
                                                    c.areaConstruida(),
                                                    c.anioConstruccion(),
                                                    c.categoriaMuros(),
                                                    c.categoriaTechos(),
                                                    c.categoriaPuertas()))
                            .toList(),
                    obrasPorFicha.getOrDefault(ficha.fichaId(), 0));
        }

        /** El arancel de la via del predio, o nulo si el predio no tiene via o ella no lo tiene. */
        public @Nullable ValorNormativo arancelDe(@Nullable Long viaId) {
            return viaId == null ? null : arancelPorVia.get(viaId);
        }
    }
}
