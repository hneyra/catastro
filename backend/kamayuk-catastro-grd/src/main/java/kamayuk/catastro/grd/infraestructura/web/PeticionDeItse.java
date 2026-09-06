package kamayuk.catastro.grd.infraestructura.web;

import java.time.LocalDate;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.grd.dominio.CertificadoItse;
import kamayuk.catastro.grd.dominio.ModalidadItse;
import kamayuk.catastro.grd.dominio.NivelDeRiesgo;
import kamayuk.catastro.web.CodigoDeError;
import kamayuk.catastro.web.ProblemaDeNegocio;
import org.jspecify.annotations.Nullable;

/**
 * El alta de un certificado ITSE, tal y como llega por HTTP (#5).
 *
 * <p><b>Lista blanca</b>: solo estos campos entran. Un {@code record} con los componentes escritos
 * es lo que impide que el dia de manana alguien mande {@code fechaAnulacion} en el alta y anule un
 * certificado en el mismo acto que lo emite.
 *
 * <p>No hay ninguna geometria aqui, y no es casualidad: un certificado no ocupa un poligono. La
 * regla {@code TODA_GEOMETRIA_ENTRA_POR_BATCH} lo vigila de todos modos.
 *
 * @param observacion el «por que» (regla 10). Sin ella la peticion es 422 y no se guarda nada
 */
public record PeticionDeItse(
        @Nullable Long predioId,
        @Nullable String numero,
        @Nullable String nivelRiesgo,
        @Nullable String modalidad,
        @Nullable String vigenciaDesde,
        @Nullable String vigenciaHasta,
        @Nullable String observacion) {

    /**
     * El certificado que la peticion declara.
     *
     * <p>Todo lo que puede venir mal se traduce a {@code 422} <b>aqui</b>, con el mensaje del
     * dominio: es el unico sitio que sabe a la vez lo que llego y lo que se esperaba.
     */
    CertificadoItse aCertificado() {
        return new CertificadoItse(
                null,
                exigirPredio(),
                exigir(numero, "numero"),
                NivelDeRiesgo.porNombre(exigir(nivelRiesgo, "nivelRiesgo")),
                ModalidadItse.porNombre(exigir(modalidad, "modalidad")),
                fecha(exigir(vigenciaDesde, "vigenciaDesde"), "vigenciaDesde"),
                fecha(exigir(vigenciaHasta, "vigenciaHasta"), "vigenciaHasta"),
                null,
                null,
                observacionDeclarada());
    }

    /**
     * La observacion, o {@code 422} nombrando el campo.
     *
     * <p>{@code Observacion.de} ya rechaza la vacia y su {@code IllegalArgumentException} tambien
     * acaba en 422 —{@code ManejadorDeErrores} la traduce—, pero diciendo «la observacion no puede
     * estar vacia» sin decir de que peticion. Lo que esto aporta es el nombre del campo, que es lo
     * que quien atiende necesita para corregirla.
     */
    Observacion observacionDeclarada() {
        return Observacion.de(exigir(observacion, "observacion"));
    }

    private long exigirPredio() {
        if (predioId == null || predioId < 1) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "Falta 'predioId': un certificado ITSE cuelga de un predio");
        }
        return predioId;
    }

    private static String exigir(@Nullable String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "Falta '" + campo + "', que no tiene valor por omision");
        }
        return valor.strip();
    }

    private static LocalDate fecha(String valor, String campo) {
        try {
            return LocalDate.parse(valor);
        } catch (java.time.format.DateTimeParseException malFormada) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "El campo '" + campo + "' va en formato AAAA-MM-DD: '" + valor + "'");
        }
    }
}
