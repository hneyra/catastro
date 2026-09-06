/**
 * El hallazgo catastral: campania, candidato, hallazgo, evidencia y acta (ADR-0035).
 *
 * <p>Sin Spring y sin JPA (regla 7). Ninguna cifra tributaria: ni un importe, ni una alicuota, ni
 * un tributo. La diferencia de areas que un hallazgo afirma es un HECHO, y lo que se cobre por ella
 * —si es que se cobra algo— lo decide {@code rentas} (ADR-0024).
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.fiscalizacion.dominio;
