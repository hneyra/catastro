package kamayuk.catastro.catastro.aplicacion;

import kamayuk.catastro.catastro.dominio.BuzonDeSalida;
import kamayuk.catastro.catastro.dominio.HechoDeCatastro;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe UN hecho en el buzon, en SU PROPIA transaccion (C-8).
 *
 * <h2>Por que esto es una clase aparte y no un metodo del que recorre</h2>
 *
 * <p>Por dos motivos que se han medido cuatro veces en este proyecto y siguen siendo el mismo:
 *
 * <ol>
 *   <li><b>Una transaccion por hecho, no una para la corrida.</b> Envolver el bucle es el defecto
 *       que #328, #54, #430 y #247 §2 midieron: la fila que se rechaza marca la transaccion como
 *       <i>rollback-only</i> y se lleva por delante <b>el informe y todo lo que ya iba bien</b> —y
 *       aqui «lo que ya iba bien» son hechos publicados que habria que volver a publicar—.
 *   <li><b>La anotacion no se aplica por auto-invocacion.</b> Si este metodo viviera en la misma
 *       clase que el bucle, {@code @Transactional} no lo interceptaria y la separacion seria una
 *       promesa del javadoc: es exactamente lo que #430 midio con {@code ImportarCajas} y #536 con
 *       el bucle de la carga cartografica.
 * </ol>
 */
@Service
public class PublicarUnHecho {

    private final BuzonDeSalida buzon;

    public PublicarUnHecho(BuzonDeSalida buzon) {
        this.buzon = buzon;
    }

    /** Publica el hecho y dice si era nuevo. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BuzonDeSalida.Publicacion publicar(HechoDeCatastro hecho) {
        return buzon.publicar(hecho);
    }
}
