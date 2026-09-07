package kamayuk.catastro.urbano.dominio;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Una zona <b>tal como se lee</b>: la que rige sobre un suelo, sin su poligono (#30).
 *
 * <h2>Por que es un registro propio y no {@link Zona}</h2>
 *
 * <p>Porque {@link Zona} es lo que se <b>ESCRIBE</b> —lo que el cargador compone desde el CSV del
 * plan— y esto es lo que se <b>LEE</b>. La diferencia no es de comodidad: {@link Zona} exige su
 * poligono y se niega a existir sin el («una zona sin poligono no cubre ningun suelo y no puede
 * decidir nada»), y esa exigencia es cierta al cargar y ociosa al leer, porque al leer <b>el
 * poligono ya decidio</b>: quien lo uso fue el {@code ST_Covers} de la propia sentencia, detras del
 * marco (ADR-0034 regla 2). Traerlo de vuelta a Java es serializar a texto un dato que ya hizo su
 * trabajo dentro del motor.
 *
 * <p>Es la misma asimetria, con el mismo motivo, que {@code ZonaDeRiesgo} en {@code grd} —«el
 * poligono no viaja en este registro: se queda en la base»— y que {@code HallazgoDelPredio} en
 * {@code fiscalizacion}.
 *
 * <p><b>Y lo que cuesta el atajo esta medido</b> (#30, AC-4): con un poligono de PDU de 5 001
 * vertices, la respuesta de la consulta de contencion pesaba <b>188 636 bytes</b>, de los que 188
 * 595 eran el poligono; ninguna de las siete componentes que {@code ZonaVigente} publica lo lee, y
 * {@code ZonaResource} tampoco lo saca. Desde #22 la consulta pide <b>dos</b> zonas para poder
 * decir que hay mas de una, asi que eran dos poligonos por lectura y no uno.
 *
 * <h2>Lo que este registro NO es</h2>
 *
 * <p>No es la respuesta de la API: esa es {@code ZonaVigente}, que ademas trae los parametros
 * urbanisticos y vive en el paquete raiz del modulo. Esto es la fila de {@code zonificacion} sin la
 * columna que nadie lee.
 *
 * @param id el identificador de la fila; una zona leida de la base siempre lo tiene
 * @param vigenciaHasta el ultimo dia que rige —inclusivo—; nulo mientras el plan siga vigente
 */
public record ZonaQueRige(
        long id,
        String plan,
        String ordenanza,
        String codigo,
        String nombre,
        LocalDate vigenciaDesde,
        @Nullable LocalDate vigenciaHasta) {

    public ZonaQueRige {
        Objects.requireNonNull(plan, "La zona leida trae su plan");
        Objects.requireNonNull(ordenanza, "La zona leida trae su ordenanza");
        Objects.requireNonNull(codigo, "La zona leida trae su codigo");
        Objects.requireNonNull(nombre, "La zona leida trae su nombre");
        Objects.requireNonNull(vigenciaDesde, "Toda zona rige desde una fecha (regla 9)");
    }
}
