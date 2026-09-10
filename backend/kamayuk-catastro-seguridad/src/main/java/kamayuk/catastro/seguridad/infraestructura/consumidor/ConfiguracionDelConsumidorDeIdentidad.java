package kamayuk.catastro.seguridad.infraestructura.consumidor;

import java.time.Clock;
import kamayuk.catastro.seguridad.aplicacion.AlertaDeEventosSinAplicar;
import kamayuk.catastro.seguridad.aplicacion.AplicarUnEventoDeIdentidad;
import kamayuk.catastro.seguridad.aplicacion.IngestarEventosDeIdentidad;
import kamayuk.catastro.seguridad.dominio.FuenteDeEventosDeIdentidad;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cablea el consumidor del buzon de {@code identidad} (ADR-0039 etapa 4, identidad#4).
 *
 * <h2>Solo en el perfil {@code batch}, y solo si se dice donde esta `identidad`</h2>
 *
 * <p>{@code @Profile("batch")} porque el proceso que atiende las pantallas <b>no llama a nadie</b>
 * para autorizar: su guardia lee su copia local, y eso es lo que hace cierto que un permiso se
 * compruebe con {@code identidad} apagado (ADR-0039 §«No toca»). Y {@code @ConditionalOnProperty}
 * sobre la URL porque el perfil {@code batch} corre muchas cosas mas —el publicador del padron, el
 * derivador de frentes, las cargas— y ninguna necesita una credencial de servicio hacia {@code
 * identidad}; sin la propiedad, este cableado no existe y la implantacion lo dice.
 *
 * <p>Con la URL puesta, el responsable y su canal <b>son obligatorios</b>: un consumidor que aparta
 * eventos sin que nadie se entere es exactamente lo que ADR-0026 §4 prohibe.
 *
 * <h2>El runner es aparte, y tiene su propia condicion</h2>
 *
 * <p>{@code CorrerElConsumidorDeIdentidad} es un {@code @Component} con su propio
 * {@code @Profile("batch")} y su propia condicion —las DOS propiedades, la URL y {@code
 * kamayuk.identidad.consumidor.municipalidad}, que pone el {@code CronJob}—, y no un {@code @Bean}
 * de aqui: la regla de ArchUnit que exige el perfil a todo {@code ApplicationRunner} lee la CLASE,
 * y un runner que solo estuviera condicionado por esta configuracion la pasaria por delante
 * (medido: «corre al arrancar y no declara @Profile: correria tambien en el proceso web»). La
 * implantacion tiene la URL y no la municipalidad —la crea ella—, asi que en su proceso corre la
 * pasada final desde {@code ImplantarMunicipalidad} y no este runner: dos pasadas en el mismo
 * proceso serian inofensivas y confusas.
 *
 * <p>No hace falta un segundo pool: basta {@code kamayuk_app}, que ya tiene {@code INSERT, SELECT,
 * UPDATE} sobre las cuatro tablas de la autorizacion (identidad#4).
 */
@Configuration(proxyBeanMethods = false)
@Profile("batch")
@ConditionalOnProperty("kamayuk.identidad.url")
public class ConfiguracionDelConsumidorDeIdentidad {

    @Bean
    ResponsableDelConsumidor responsableDelConsumidor(
            @Value("${kamayuk.identidad.responsable:}") String nombre,
            @Value("${kamayuk.identidad.canal:}") String canal) {
        return new ResponsableDelConsumidor(nombre, canal);
    }

    @Bean
    AlertaDeEventosSinAplicar alertaDeEventosSinAplicar(
            JsonMapper json, ResponsableDelConsumidor responsable) {
        return new AlertaAlCanalDelResponsable(json, responsable);
    }

    /**
     * De donde sale el {@code Authorization} del consumidor (#21 AC-2). Un {@code @Bean} y no un
     * {@code @Component}: un componente descubierto por barrido se construiria tambien en el
     * proceso web, que no llama a nadie.
     */
    @Bean
    CredencialDeServicio credencialDeServicioDeIdentidad(
            JsonMapper json,
            Clock reloj,
            @Value("${kamayuk.identidad.token:}") String punto,
            @Value("${kamayuk.identidad.cliente:}") String cliente,
            @Value("${kamayuk.identidad.credencial:}") String clave) {
        return new TokenDeServicioDeKeycloak(json, reloj, punto, cliente, clave);
    }

    @Bean
    FuenteDeEventosDeIdentidad fuenteDeEventosDeIdentidad(
            JsonMapper json,
            @Value("${kamayuk.identidad.url}") String raiz,
            CredencialDeServicio credencialDeServicioDeIdentidad) {
        return new ClienteHttpDelBuzonDeIdentidad(json, raiz, credencialDeServicioDeIdentidad);
    }

    @Bean
    IngestarEventosDeIdentidad ingestarEventosDeIdentidad(
            FuenteDeEventosDeIdentidad fuente,
            AplicarUnEventoDeIdentidad aplicador,
            AlertaDeEventosSinAplicar alerta,
            Clock reloj) {
        return new IngestarEventosDeIdentidad(fuente, aplicador, alerta, reloj);
    }
}
