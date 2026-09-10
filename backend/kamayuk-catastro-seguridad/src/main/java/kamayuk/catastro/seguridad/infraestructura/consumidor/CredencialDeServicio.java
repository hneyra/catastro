package kamayuk.catastro.seguridad.infraestructura.consumidor;

/**
 * De donde sale el {@code Authorization} con el que el consumidor llama a {@code identidad} (#21
 * AC-2, ADR-0028 §2).
 *
 * <p>Existe para que {@link ClienteHttpDelBuzonDeIdentidad} no sepa <b>como</b> se consigue. Lo que
 * se configura es la <b>clave con la que se pide el token</b> —la de un cliente confidencial de
 * este sistema y esta municipalidad—, y quien lo pide es {@link TokenDeServicioDeKeycloak}.
 *
 * <p><b>Devuelve la cabecera entera, con su esquema.</b> Devolver solo el token dejaria el {@code
 * "Bearer "} escrito en el cliente HTTP.
 *
 * <p>Puede lanzar {@link
 * kamayuk.catastro.seguridad.dominio.FuenteDeEventosDeIdentidad.IdentidadNoContesta}: no poder
 * pedir el token es un fallo de despliegue, y en este camino <b>todo fallo de transporte es
 * transitorio</b> a proposito —lo que no puede pasar es que mate un evento—.
 */
@FunctionalInterface
public interface CredencialDeServicio {

    /** La cabecera {@code Authorization}, o cadena vacia si este despliegue no tiene ninguna. */
    String cabecera();

    /** Una credencial fija, para las pruebas y para el compose sin identidad. */
    static CredencialDeServicio fija(String cabecera) {
        return () -> cabecera;
    }
}
