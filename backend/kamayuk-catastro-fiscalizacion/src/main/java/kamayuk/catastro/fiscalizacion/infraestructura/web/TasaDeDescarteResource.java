package kamayuk.catastro.fiscalizacion.infraestructura.web;

import kamayuk.catastro.fiscalizacion.dominio.TasaDeDescarte;

/**
 * Cuantos candidatos cayo cada compuerta (AC 7 de #6, ADR-0035 punto 5).
 *
 * <p><b>Cifras, y ningun porcentaje.</b> Un 50 % sobre veinte candidatos y el mismo 50 % sobre
 * veinte mil no significan lo mismo, y publicar solo el porcentaje esconde el denominador — que es
 * justo lo que hace falta para decidir si el umbral se baja. Con los cinco recuentos, cualquier
 * cociente se puede hacer sabiendo sobre que.
 *
 * <p>Y {@code loQuePasoGabinete} sale por eso mismo: es el denominador de los descartes de campo, y
 * sin el las dos cifras de descarte no se pueden comparar entre si —campo solo ve lo que gabinete
 * admitio—.
 */
public record TasaDeDescarteResource(
        long detectados,
        long descartadosEnGabinete,
        long loQuePasoGabinete,
        long descartadosEnCampo,
        long verificados,
        long enCurso) {

    public static TasaDeDescarteResource de(TasaDeDescarte tasa) {
        return new TasaDeDescarteResource(
                tasa.detectados(),
                tasa.descartadosEnGabinete(),
                tasa.loQuePasoGabinete(),
                tasa.descartadosEnCampo(),
                tasa.verificados(),
                tasa.enCurso());
    }
}
