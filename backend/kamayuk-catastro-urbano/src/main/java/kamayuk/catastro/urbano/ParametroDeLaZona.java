package kamayuk.catastro.urbano;

import org.jspecify.annotations.Nullable;

/**
 * Un parametro urbanistico de una zona: altura maxima, area de lote minima, retiro, coeficiente de
 * edificacion, densidad, estacionamientos (#4).
 *
 * <p><b>El valor es texto y no un numero, a proposito.</b> La mitad de los parametros de una
 * ordenanza de zonificacion no son cifras —«segun frente», «1/2 por vivienda», «3 pisos + azotea»—,
 * y convertirlos a numero seria inventar lo que la ordenanza no dice. Aqui no se calcula nada con
 * ellos: se publican para que quien evalue el expediente los lea, que es lo que la frontera de
 * ADR-0024 deja de este lado.
 *
 * <p>Y por lo mismo <b>no es un importe</b>: la regla 1 no aplica. Un parametro urbanistico no es
 * dinero, y no hay ninguna aritmetica en este modulo que lo toque.
 *
 * @param unidad «m», «m2», «pisos», «hab/ha»…; nula donde la ordenanza no la da
 */
public record ParametroDeLaZona(String clave, String valor, @Nullable String unidad) {}
