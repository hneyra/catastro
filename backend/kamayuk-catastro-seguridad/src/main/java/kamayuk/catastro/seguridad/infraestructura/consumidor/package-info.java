/**
 * Lo que habla con {@code identidad} y con Keycloak por el consumidor del buzon (ADR-0039 etapa 4):
 * el cliente HTTP del buzon, el token de servicio, la alerta al responsable y el cableado del
 * perfil {@code batch}.
 *
 * <p>Las cuatro piezas de transporte estan copiadas de {@code kamayuk.rentas.nucleo.infraestructura
 * .ingestor} con sus motivos —{@code TokenDeServicioDeKeycloak}, {@code CredencialDeServicio}, el
 * cliente y la alerta—, porque es el mismo camino leido desde el otro extremo: alli {@code rentas}
 * consume el buzon de {@code catastro}; aqui {@code catastro} consume el de {@code identidad}. Que
 * esten copiadas y no compartidas es el hueco de las cuatro copias que ADR-0038 le asigna a {@code
 * kamayuk-lib}, y se dice aqui en vez de descubrirse.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.seguridad.infraestructura.consumidor;
