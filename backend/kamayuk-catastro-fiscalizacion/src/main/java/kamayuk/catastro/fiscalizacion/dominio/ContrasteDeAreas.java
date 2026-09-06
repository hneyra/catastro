package kamayuk.catastro.fiscalizacion.dominio;

import java.util.Objects;
import kamayuk.catastro.dominio.AreaM2;

/**
 * Lo que el cruce devuelve por predio: la version de ficha, las dos areas y cuanto difieren.
 *
 * <p>Trae {@code fichaId} <b>desde el cruce</b> y no despues, y eso es AC 4 empezando donde tiene
 * que empezar: la version que se contrasto es la que estaba vigente <b>cuando se miro</b>, y
 * resolverla mas tarde daria otra. Si el hallazgo se levanta tres dias despues y entretanto alguien
 * versiono la ficha, lo que el candidato sospecho no es lo que el hallazgo diria.
 *
 * <h2>La diferencia relativa viene del cruce y no se recalcula aqui</h2>
 *
 * <p>La consulta ya la calcula: es su condicion de filtro y su criterio de orden. Recalcularla en
 * Java seria una <b>segunda implementacion de la misma formula</b>, y el dia que una de las dos
 * cambie —o que redondeen distinto— el candidato saldria ordenado por una cifra y explicado por
 * otra, las dos plausibles, sin que nada fallara. Es el reparto que este proyecto ya se ha comido
 * varias veces (#397, #481).
 *
 * <p>Y de paso resuelve lo otro: dividir aqui exigiria escribir una escala y un modo de redondeo, y
 * D-03a/D-03b siguen abiertas — el escaner de fuentes lo rechaza, y tiene razon.
 *
 * <p>{@code areaDelPoligono} se calcula en la base con {@code ST_Area} y <b>no se guarda en ninguna
 * columna</b>: es un insumo de la sospecha, no un dato del predio. Escribirla seria derivar el area
 * del terreno del poligono, que es exactamente lo que ADR-0021 se niega a hacer.
 *
 * @param diferenciaRelativa cuanto difieren, como fraccion de lo inscrito y acotada a 1
 * @param geometria el poligono del predio en WKT, para copiarlo al candidato
 */
public record ContrasteDeAreas(
        long predioId,
        long fichaId,
        String codigoReferenciaCatastral,
        AreaM2 areaDeLaFicha,
        AreaM2 areaDelPoligono,
        Score diferenciaRelativa,
        String geometria) {

    public ContrasteDeAreas {
        Objects.requireNonNull(codigoReferenciaCatastral, "El contraste nombra el predio");
        Objects.requireNonNull(areaDeLaFicha, "El contraste lleva el area de la ficha");
        Objects.requireNonNull(areaDelPoligono, "El contraste lleva el area del poligono");
        Objects.requireNonNull(diferenciaRelativa, "El contraste lleva cuanto difieren");
        Objects.requireNonNull(geometria, "El contraste lleva el poligono que midio");
    }

    /** Si lo verificado por el plano supera lo inscrito, que es la forma tipica del subvaluador. */
    public boolean elPoligonoSupera() {
        return areaDelPoligono.compareTo(areaDeLaFicha) > 0;
    }
}
