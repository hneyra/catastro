package kamayuk.catastro.catastro.aplicacion;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import kamayuk.catastro.catastro.CaracteristicasDelPredio;
import kamayuk.catastro.catastro.LectorDeCaracteristicas;
import kamayuk.catastro.catastro.LectorDeFichas;
import kamayuk.catastro.catastro.LectorDeFichasEconomicas;
import kamayuk.catastro.dominio.AreaM2;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que otro sistema necesita saber de un predio <b>a una fecha</b>, en una sola lectura (C-5).
 *
 * <h2>Por que compone en vez de dejar que el borde pregunte tres veces</h2>
 *
 * <p>Los tres puertos que esto sirve —{@link LectorDeCaracteristicas}, {@link LectorDeFichas} y
 * {@link LectorDeFichasEconomicas}— vivian dentro del mismo proceso que quien preguntaba, asi que
 * tres llamadas eran tres consultas de la misma transaccion. Desde P5C quien pregunta esta en otro
 * repositorio: si el borde publicara tres rutas, serian <b>tres peticiones y tres
 * transacciones</b>, y entre la primera y la tercera cabe una version nueva de ficha. Es
 * literalmente el defecto que #486 midio al reves —«las cuatro fichas dejan de hacer TRES consultas
 * en TRES transacciones distintas: entre la del predio y la de su ficha cabia una version nueva»—,
 * y aqui saldria mas caro porque el resultado se usa para determinar.
 *
 * <p>Los tres colaboradores son los <b>puertos</b> y no los repositorios, a proposito: cada
 * implementacion declara su {@code @Transactional(readOnly = true)} y la propagacion por omision es
 * {@code REQUIRED}, asi que las tres se unen a la que abre este metodo. Una sola transaccion, un
 * solo {@code SET LOCAL} (ARQ-03 §3.1).
 *
 * <h2>«No esta en el padron» no es «no tiene ficha»</h2>
 *
 * <p>{@link CaracteristicasEnUnaFecha#enElPadron()} es {@code false} solo cuando el predio no
 * existe. Un predio que existe y no tiene ninguna version de ficha vigente a esa fecha sale con
 * {@code enElPadron = true} y los identificadores nulos: son dos situaciones que se arreglan de
 * maneras distintas —una es un identificador equivocado y la otra es un predio sin fichar— y
 * confundirlas manda a mirar donde no es.
 */
@Service
public class ConsultaDeCaracteristicas {

    private final LectorDeCaracteristicas caracteristicas;
    private final LectorDeFichas fichas;
    private final LectorDeFichasEconomicas economicas;

    public ConsultaDeCaracteristicas(
            LectorDeCaracteristicas caracteristicas,
            LectorDeFichas fichas,
            LectorDeFichasEconomicas economicas) {
        this.caracteristicas = caracteristicas;
        this.fichas = fichas;
        this.economicas = economicas;
    }

    /**
     * Lo inscrito de un predio a esa fecha. Nunca {@code null}: el predio que no esta responde con
     * {@code enElPadron = false}, que es un dato y no una ausencia.
     */
    @Transactional(readOnly = true)
    public CaracteristicasEnUnaFecha de(long predioId, LocalDate fecha) {
        Objects.requireNonNull(fecha, "Lo inscrito de un predio se pregunta a una fecha (regla 9)");

        Optional<CaracteristicasDelPredio> delPredio = caracteristicas.de(predioId, fecha);
        if (delPredio.isEmpty()) {
            return CaracteristicasEnUnaFecha.fueraDelPadron(predioId, fecha);
        }
        return new CaracteristicasEnUnaFecha(
                predioId,
                true,
                fichas.fichaVigenteEn(predioId, fecha).orElse(null),
                economicas.fichaEconomicaVigenteEn(predioId, fecha).orElse(null),
                delPredio.get().uso(),
                delPredio.get().sectorCodigo(),
                delPredio.get().areaTerreno(),
                fecha);
    }

    /**
     * Lo que de un predio se puede decir a una fecha.
     *
     * @param aLaFecha la fecha con la que se resolvio, no la que se pidio. Viaja en la respuesta
     *     porque es lo unico con lo que quien la lee puede comprobar que su criterio llego: hasta
     *     C-1 el nombre del parametro no coincidia, se descartaba en silencio y la ficha se
     *     resolvia con el reloj del servidor (#24, #366)
     */
    public record CaracteristicasEnUnaFecha(
            long predioId,
            boolean enElPadron,
            @Nullable Long fichaId,
            @Nullable Long fichaEconomicaId,
            @Nullable String uso,
            @Nullable String sectorCodigo,
            @Nullable AreaM2 areaTerreno,
            LocalDate aLaFecha) {

        static CaracteristicasEnUnaFecha fueraDelPadron(long predioId, LocalDate fecha) {
            return new CaracteristicasEnUnaFecha(
                    predioId, false, null, null, null, null, null, fecha);
        }
    }
}
