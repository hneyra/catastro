package kamayuk.catastro.nucleo.aplicacion;

import kamayuk.catastro.nucleo.dominio.BuzonDeSalida;
import kamayuk.catastro.nucleo.dominio.HechoDeCatastro;
import kamayuk.catastro.nucleo.dominio.TerritorioParaPublicar;
import kamayuk.catastro.nucleo.infraestructura.ComponedorDeHechos;
import org.springframework.stereotype.Service;

/**
 * Publica el territorio al buzon de salida: manzanas, frentes y hallazgos firmes (#7).
 *
 * <h2>Este objeto NO abre transaccion, y ahi esta la decision</h2>
 *
 * <p>La lectura la abre {@link LecturaDelTerritorioParaPublicar} y cada escritura la abre {@link
 * PublicarUnHecho}. Es el mismo reparto que {@link PublicacionDelPadron} y por el mismo motivo:
 * envolver el recorrido en una sola transaccion hace que el hecho rechazado —un hallazgo firme que
 * volvio con otra area verificada— se lleve por delante todo lo ya publicado.
 *
 * <h2>Las tres publicaciones no se parecen, y por eso van separadas</h2>
 *
 * <ul>
 *   <li><b>Las manzanas y los frentes</b> son proyecciones: su identidad se deriva del contenido,
 *       asi que republicarlas cuesta —de los dos lados— exactamente lo que cambio. Se pueden correr
 *       todos los dias.
 *   <li><b>El hallazgo firme</b> es un hecho que una persona firmo: su identidad se deriva del
 *       identificador del hallazgo, asi que volver a publicarlo con otro contenido <b>se para</b>
 *       con {@code HechoSelladoReescrito} en vez de aplicarse encima. Es lo mismo que la valuacion.
 * </ul>
 *
 * <h2>Publicar un hallazgo no corrige ninguna ficha</h2>
 *
 * <p>Un hallazgo firme habilita el acto —versionar la ficha con su observacion— y no lo ejecuta
 * (ADR-0035 punto 4). Esta clase no depende de ningun camino de escritura de la ficha, y no es una
 * promesa del javadoc: no hay aqui un solo tipo que escriba una version.
 */
@Service
public class PublicacionDelTerritorio {

    private final LecturaDelTerritorioParaPublicar lectura;
    private final PublicarUnHecho publicador;
    private final ComponedorDeHechos componedor;

    public PublicacionDelTerritorio(
            LecturaDelTerritorioParaPublicar lectura,
            PublicarUnHecho publicador,
            ComponedorDeHechos componedor) {
        this.lectura = lectura;
        this.publicador = publicador;
        this.componedor = componedor;
    }

    /**
     * Publica lo que haya cambiado del territorio.
     *
     * @throws BuzonDeSalida.HechoSelladoReescrito si un hallazgo ya publicado saldria ahora con
     *     otro contenido. No se publica encima: se dice y se para
     */
    public Informe publicar() {
        LecturaDelTerritorioParaPublicar.Territorio territorio = lectura.leer();

        int manzanasNuevas = 0;
        for (TerritorioParaPublicar.ManzanaDelTerritorio manzana : territorio.manzanas()) {
            if (esNuevo(componedor.deLaManzana(manzana))) {
                manzanasNuevas++;
            }
        }

        int frentesNuevos = 0;
        for (TerritorioParaPublicar.FrentesDeUnPredio frentes : territorio.frentesPorPredio()) {
            if (esNuevo(componedor.deLosFrentes(frentes))) {
                frentesNuevos++;
            }
        }

        int hallazgosNuevos = 0;
        for (TerritorioParaPublicar.HallazgoFirme hallazgo : territorio.hallazgosFirmes()) {
            if (esNuevo(componedor.delHallazgoFirme(hallazgo))) {
                hallazgosNuevos++;
            }
        }

        return new Informe(
                territorio.manzanas().size(),
                manzanasNuevas,
                territorio.frentesPorPredio().size(),
                frentesNuevos,
                territorio.hallazgosFirmes().size(),
                hallazgosNuevos);
    }

    private boolean esNuevo(HechoDeCatastro hecho) {
        return publicador.publicar(hecho) == BuzonDeSalida.Publicacion.NUEVO;
    }

    /**
     * Lo que la corrida publico.
     *
     * <p>Se cuentan los leidos <b>y</b> los nuevos, y no solo estos: «cero nuevos» sobre cero
     * leidos y sobre catorce mil leidos son dos cosas distintas, y una sola cifra no las distingue.
     */
    public record Informe(
            int manzanasLeidas,
            int manzanasNuevas,
            int prediosConFrentes,
            int frentesNuevos,
            int hallazgosFirmes,
            int hallazgosNuevos) {}
}
