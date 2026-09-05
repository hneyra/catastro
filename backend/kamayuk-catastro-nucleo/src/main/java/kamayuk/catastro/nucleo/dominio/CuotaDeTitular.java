package kamayuk.catastro.nucleo.dominio;

import java.util.Objects;
import kamayuk.catastro.dominio.Porcentaje;

/**
 * Un titular del predio con su cuota, <b>vigente a la fecha de corte de la corrida</b> (C-8).
 *
 * <p>Viaja dentro de la valuacion y no se resuelve despues, y eso es la regla 9 aplicada a la
 * titularidad: quien pregunte en marzo de 2030 por la valuacion de 2026 tiene que recibir los
 * titulares que la corrida uso, no los de hoy. Resolverlos al leer es el defecto que #24 midio con
 * los domicilios y #366 con los titulares de un predio — «la ultima» en vez de «la vigente a la
 * fecha».
 *
 * <p>El {@code %} pondera la base imponible de cada predio (NEG-05 §1), asi que una cuota resuelta
 * a otra fecha no cambia un dato accesorio: cambia el impuesto.
 */
public record CuotaDeTitular(long contribuyenteId, String condicion, Porcentaje cuota) {

    public CuotaDeTitular {
        Objects.requireNonNull(
                condicion, "Una cuota de titularidad dice en que condicion se tiene");
        Objects.requireNonNull(cuota, "Una cuota de titularidad lleva su porcentaje");
        if (contribuyenteId < 1) {
            throw new IllegalArgumentException(
                    "El titular es un contribuyente del padron, y «"
                            + contribuyenteId
                            + "» no lo es");
        }
    }
}
