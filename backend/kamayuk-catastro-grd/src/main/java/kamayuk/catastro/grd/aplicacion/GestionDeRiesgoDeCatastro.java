package kamayuk.catastro.grd.aplicacion;

import java.time.LocalDate;
import kamayuk.catastro.grd.LectorDeGestionDeRiesgo;
import kamayuk.catastro.grd.SituacionDelPredio;
import kamayuk.catastro.grd.dominio.RiesgoDelPredio;
import org.springframework.stereotype.Service;

/**
 * La implementacion del unico puerto que este contexto publica (#5).
 *
 * <p>Compone las dos lecturas y devuelve el resumen: dos booleanos, una cuenta y la fecha. Quien
 * pregunta esto —hoy nadie, manana quien emita una licencia— no necesita el poligono de la zona ni
 * el numero del certificado, y darselos ataria su codigo a la forma interna de este contexto.
 *
 * <p><b>No decide nada</b> (ADR-0024). {@code enRiesgoNoMitigable} es un hecho sobre el lote, no un
 * «no se puede dar la licencia»: eso depende ademas del giro, de la zonificacion y del riesgo que
 * la actividad exige, y ninguna de las tres cosas vive aqui.
 *
 * <p>Deja pasar {@code PredioSinGeometria} tal cual, y a proposito: quien vaya a decidir sobre este
 * lote tiene que enterarse de que el plano no esta cargado en vez de recibir «no hay riesgo».
 */
@Service
public class GestionDeRiesgoDeCatastro implements LectorDeGestionDeRiesgo {

    private final ConsultaDeRiesgo riesgo;
    private final ConsultaDeItse itse;

    public GestionDeRiesgoDeCatastro(ConsultaDeRiesgo riesgo, ConsultaDeItse itse) {
        this.riesgo = riesgo;
        this.itse = itse;
    }

    @Override
    public SituacionDelPredio situacionDe(long predioId, LocalDate aLaFecha) {
        RiesgoDelPredio delLote = riesgo.delPredio(predioId, aLaFecha);
        return new SituacionDelPredio(
                predioId,
                aLaFecha,
                delLote.zonas().size(),
                delLote.hayRiesgoNoMitigable(),
                !delLote.fajas().isEmpty(),
                !itse.vigenteA(predioId, aLaFecha).isEmpty());
    }
}
