package kamayuk.catastro.grd.aplicacion;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kamayuk.catastro.carga.InformeDeImportacion;
import kamayuk.catastro.carga.InformeDeImportacion.FilaRechazada;
import kamayuk.catastro.carga.LectorDeFilasCsv;
import kamayuk.catastro.carga.LectorDeFilasCsv.FilaCsv;
import kamayuk.catastro.dominio.Medida;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.grd.dominio.FajaMarginal;
import kamayuk.catastro.grd.dominio.NivelDeRiesgo;
import kamayuk.catastro.grd.dominio.ZonaDeRiesgo;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * La carta de restricciones de una municipalidad, fila a fila (#5, AC-7).
 *
 * <h2>Un archivo con dos capas, y por que no son dos archivos</h2>
 *
 * <p>La primera columna es la <b>capa</b>: {@code PELIGRO} para un poligono de la carta de CENEPRED
 * y {@code FAJA_MARGINAL} para uno de la ANA. Es como llega el dato —una exportacion del sistema de
 * informacion geografica de la municipalidad, con una columna que dice de que capa es cada
 * geometria— y es lo que permite que haya <b>un</b> proceso, <b>una</b> propiedad y <b>un</b> guion
 * en vez de tres juegos que hay que acordarse de correr en orden.
 *
 * <p>Las dos capas no comparten columnas y eso no es un defecto del formato: una zona de peligro
 * tiene {@code fenomeno}, {@code nivel} y {@code mitigable}; una faja marginal tiene {@code
 * cuerpoDeAgua} y {@code anchoM}. Cada fila rellena las suyas y deja las otras vacias, y
 * <b>rellenar las que no le tocan es un rechazo</b>: una faja con nivel MUY_ALTO es una fila que
 * alguien escribio a mano creyendo que el nivel decidia algo, y guardarla en silencio dejaria el
 * dato inventado dentro.
 *
 * <h2>Rechazo por fila, no por archivo</h2>
 *
 * <p>{@code importar} <b>no</b> lleva {@code @Transactional}: cada fila abre la suya al llamar a
 * {@link RegistrarCapaDeRiesgo}, que si la lleva y es un {@code @Service} distinto. Un poligono mal
 * formado o un codigo repetido se rechaza solo y los siguientes entran (#247 §2).
 */
@Service
public class ImportarCartaDeRiesgo {

    /**
     * Capa, codigo, fenomeno, nivel, mitigable, cuerpoDeAgua, anchoM, fuente, documento, 2 fechas,
     * geometria.
     */
    private static final int COLUMNAS = 12;

    private static final String CAPA_PELIGRO = "PELIGRO";
    private static final String CAPA_FAJA = "FAJA_MARGINAL";

    private final RegistrarCapaDeRiesgo registrar;

    public ImportarCartaDeRiesgo(RegistrarCapaDeRiesgo registrar) {
        this.registrar = registrar;
    }

    public InformeDeImportacion importar(Reader archivo, Observacion observacion) {
        List<FilaCsv> filas = leer(archivo);
        List<FilaRechazada> rechazadas = new ArrayList<>();
        int nuevas = 0;

        for (FilaCsv fila : filas) {
            try {
                registrarUna(fila.campos(), observacion);
                nuevas++;
            } catch (IllegalArgumentException malFormada) {
                rechazadas.add(FilaRechazada.de(fila.numeroDeLinea(), malFormada));
            } catch (DataAccessException laBaseLaRechaza) {
                // Un codigo repetido -el archivo ya se cargo- o un WKT que PostGIS no admite.
                // El mensaje del motor NO se publica: nombraria tabla y restriccion (ARQ-04 §5).
                rechazadas.add(
                        new FilaRechazada(
                                fila.numeroDeLinea(),
                                "La base rechazo el poligono: o su codigo ya esta cargado en esta"
                                        + " municipalidad, o la geometria no es un MULTIPOLYGON"
                                        + " valido en WGS84"));
            }
        }
        return new InformeDeImportacion(filas.size(), nuevas, rechazadas);
    }

    private void registrarUna(List<String> campos, Observacion observacion) {
        if (campos.size() < COLUMNAS) {
            throw new IllegalArgumentException(
                    "La fila tiene "
                            + campos.size()
                            + " columnas y hacen falta "
                            + COLUMNAS
                            + ": capa, codigo, fenomeno, nivel, mitigable, cuerpoDeAgua, anchoM,"
                            + " fuente, documentoOrigen, vigenciaDesde, vigenciaHasta, geometria");
        }
        String capa = campos.get(0).strip().toUpperCase(Locale.ROOT);
        String geometria = exigir(campos.get(11), "geometria");

        switch (capa) {
            case CAPA_PELIGRO -> registrar.registrar(zonaDe(campos), geometria, observacion);
            case CAPA_FAJA -> registrar.registrar(fajaDe(campos), geometria, observacion);
            default ->
                    throw new IllegalArgumentException(
                            "La capa va entre "
                                    + CAPA_PELIGRO
                                    + " y "
                                    + CAPA_FAJA
                                    + ": llego '"
                                    + campos.get(0)
                                    + "'");
        }
    }

    private static ZonaDeRiesgo zonaDe(List<String> campos) {
        exigirVacias(campos, "una zona de peligro", 5, "cuerpoDeAgua", 6, "anchoM");
        return new ZonaDeRiesgo(
                null,
                exigir(campos.get(1), "codigo"),
                exigir(campos.get(2), "fenomeno"),
                NivelDeRiesgo.porNombre(exigir(campos.get(3), "nivel")),
                booleano(campos.get(4)),
                exigir(campos.get(7), "fuente"),
                exigir(campos.get(8), "documentoOrigen"),
                fecha(exigir(campos.get(9), "vigenciaDesde")),
                fechaOpcional(campos.get(10)),
                Observacion.de("Carga de la carta de peligro (CENEPRED)"));
    }

    private static FajaMarginal fajaDe(List<String> campos) {
        exigirVacias(campos, "una faja marginal", 2, "fenomeno", 3, "nivel");
        if (!campos.get(4).isBlank()) {
            throw new IllegalArgumentException(
                    "Una faja marginal no lleva «mitigable»: la ANA no declara un nivel que se"
                            + " pueda mitigar, declara una restriccion de dominio publico"
                            + " hidraulico");
        }
        return new FajaMarginal(
                null,
                exigir(campos.get(1), "codigo"),
                exigir(campos.get(5), "cuerpoDeAgua"),
                Medida.enMetrosLineales(exigir(campos.get(6), "anchoM")),
                exigir(campos.get(7), "fuente"),
                exigir(campos.get(8), "documentoOrigen"),
                fecha(exigir(campos.get(9), "vigenciaDesde")),
                fechaOpcional(campos.get(10)),
                Observacion.de("Carga de la faja marginal (ANA)"));
    }

    /** Las columnas de la OTRA capa tienen que venir vacias: rellenarlas es inventar el dato. */
    private static void exigirVacias(
            List<String> campos,
            String queEs,
            int primera,
            String nombre1,
            int segunda,
            String nombre2) {
        if (!campos.get(primera).isBlank() || !campos.get(segunda).isBlank()) {
            throw new IllegalArgumentException(
                    "La fila declara "
                            + queEs
                            + " y trae «"
                            + nombre1
                            + "» o «"
                            + nombre2
                            + "», que son de la otra capa. Guardarla dejaria dentro un dato que"
                            + " ningun acto le dio");
        }
    }

    private static boolean booleano(String valor) {
        String limpio = valor.strip().toUpperCase(Locale.ROOT);
        return switch (limpio) {
            case "SI", "S", "TRUE", "1" -> true;
            case "NO", "N", "FALSE", "0" -> false;
            default ->
                    throw new IllegalArgumentException(
                            "«mitigable» va SI o NO, y no admite quedarse vacio: es el dato que"
                                    + " decide, y un valor por omision autorizaria o negaria por"
                                    + " descuido. Llego '"
                                    + valor
                                    + "'");
        };
    }

    private static LocalDate fecha(String valor) {
        try {
            return LocalDate.parse(valor.strip());
        } catch (java.time.format.DateTimeParseException malFormada) {
            throw new IllegalArgumentException(
                    "La fecha va en formato AAAA-MM-DD: '" + valor + "'");
        }
    }

    private static @Nullable LocalDate fechaOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : fecha(valor);
    }

    private static String exigir(String valor, String columna) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta la columna '" + columna + "'");
        }
        return valor.strip();
    }

    private static List<FilaCsv> leer(Reader archivo) {
        try {
            return LectorDeFilasCsv.leer(archivo);
        } catch (IOException noSePudoLeer) {
            throw new IllegalStateException(
                    "No se pudo leer la carta de restricciones", noSePudoLeer);
        }
    }
}
