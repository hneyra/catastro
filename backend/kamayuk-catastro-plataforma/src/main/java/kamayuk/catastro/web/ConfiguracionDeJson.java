package kamayuk.catastro.web;

import kamayuk.catastro.json.ObjetosDeValorEnJson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;

/**
 * Como se serializan los objetos de valor del dominio <b>en el transporte</b>.
 *
 * <p><b>Todo decimal sale como cadena, nunca como numero JSON.</b> El {@code number} de JavaScript
 * es un binario de doble precision: {@code 0.1 + 0.2} no es {@code 0.3}, y un importe con muchos
 * digitos se redondea al leerlo en el navegador. Es exactamente el defecto que la regla 1 prohibe
 * en Java, y no tendria sentido protegerlo en el servidor y perderlo en el transporte (RNF-055). El
 * contrato lo dice igual: su esquema {@code Importe} es {@code type: string}.
 *
 * <p>Se resuelve aqui, en un modulo de Jackson, y no anotando cada DTO: una anotacion que hay que
 * acordarse de poner en 134 pantallas es una anotacion que faltara en alguna.
 *
 * <p><b>La definicion de esos serializadores se mudo a {@link ObjetosDeValorEnJson} con #20</b>, y
 * esta clase se quedo con lo unico que es suyo: declararlos como bean de Spring. El motivo es que
 * tienen un segundo consumidor que no transporta nada —la bitacora, que compone {@code
 * auditoria.datos_nuevos} y antes lo hacia concatenando cadenas—, y con la definicion aqui dentro
 * ese consumidor tendria que depender de la capa de presentacion para escribir una columna.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionDeJson {

    @Bean
    public SimpleModule moduloDeObjetosDeValor() {
        return ObjetosDeValorEnJson.modulo();
    }
}
