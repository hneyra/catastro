package kamayuk.catastro.urbano.aplicacion;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import kamayuk.catastro.carga.InformeDeImportacion;
import kamayuk.catastro.carga.InformeDeImportacion.FilaRechazada;
import kamayuk.catastro.carga.LectorDeFilasCsv;
import kamayuk.catastro.carga.LectorDeFilasCsv.FilaCsv;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.urbano.dominio.ParametroUrbanistico;
import kamayuk.catastro.urbano.dominio.Zona;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Carga un plan de zonificacion desde archivo: una fila es <b>una zona y sus parametros</b> (#4).
 *
 * <h2>Rechazo por fila, no por archivo</h2>
 *
 * <p>{@code importar} <b>no</b> lleva {@code @Transactional}: cada fila abre la suya al llamar a
 * {@link RegistrarZonificacion#registrar}, que si la lleva y es un {@code @Service} distinto. Una
 * zona con un poligono mal escrito se rechaza sola y las siguientes entran. Envolver el bucle haria
 * algo peor que perder la fila que sigue a la mala: la fila rechazada marca la transaccion como
 * <i>rollback-only</i> y la corrida entera revienta con {@code UnexpectedRollbackException}, sin
 * llegar a devolver el informe que la explicaba (#247 §2).
 *
 * <p><b>Y aqui hay un rechazo que solo el motor puede dar</b>, que es el que este archivo existe
 * para no perder: la restriccion {@code zonificacion_planes_no_se_pisan} rechaza la zona que se
 * pisa con otro plan vigente. Sale como {@link DataAccessException} —diferida al {@code COMMIT} de
 * SU transaccion, que es la de la fila— y se traduce a un motivo en castellano que nombra el plan y
 * la zona, sin filtrar tabla ni restriccion (ARQ-04 §5).
 *
 * <h2>Los parametros van en la misma fila, en pares</h2>
 *
 * <p>Detras de las siete columnas fijas van pares {@code clave=valor} o tercias {@code
 * clave=valor:unidad}, tantos como la ordenanza declare para esa zona. Un archivo aparte, con una
 * fila por parametro, obligaria a cargar dos y a que el segundo nombrara zonas del primero: la
 * mitad de las filas se rechazarian por orden, que es el defecto que C-6 midio para la siembra.
 */
@Service
public class ImportarZonificacion {

    /** plan, ordenanza, codigo, nombre, vigenciaDesde, vigenciaHasta, geometria. */
    private static final int COLUMNAS_FIJAS = 7;

    private final RegistrarZonificacion registrar;

    public ImportarZonificacion(RegistrarZonificacion registrar) {
        this.registrar = registrar;
    }

    public InformeDeImportacion importar(Reader archivo, Observacion observacion) {
        List<FilaCsv> filas = leer(archivo);
        List<FilaRechazada> rechazadas = new ArrayList<>();
        int nuevas = 0;

        for (FilaCsv fila : filas) {
            Zona zona;
            List<ParametroUrbanistico> parametros;
            try {
                zona = zonaDe(fila.campos());
                parametros = parametrosDe(fila.campos());
            } catch (IllegalArgumentException e) {
                rechazadas.add(FilaRechazada.de(fila.numeroDeLinea(), e));
                continue;
            }
            try {
                if (registrar.registrar(zona, parametros, observacion)) {
                    nuevas++;
                }
            } catch (IllegalArgumentException e) {
                rechazadas.add(FilaRechazada.de(fila.numeroDeLinea(), e));
            } catch (DataAccessException e) {
                // Ni tabla, ni restriccion, ni SQL. Lo que puede fallar aqui son dos cosas y las
                // dos se arreglan en el archivo: un poligono que el motor no reconoce, y una zona
                // que se pisa con la de un plan que ya rige esa fecha sobre ese suelo.
                rechazadas.add(
                        new FilaRechazada(
                                fila.numeroDeLinea(),
                                "El motor rechazo la zona '"
                                        + zona.codigo()
                                        + "' del plan '"
                                        + zona.plan()
                                        + "': revise que la geometria sea un MULTIPOLYGON valido y"
                                        + " que ningun otro plan vigente cubra ya ese suelo"));
            }
        }
        return new InformeDeImportacion(filas.size(), nuevas, rechazadas);
    }

    private static List<FilaCsv> leer(Reader archivo) {
        try {
            return LectorDeFilasCsv.leer(archivo);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo de zonificacion", e);
        }
    }

    private static Zona zonaDe(List<String> campos) {
        if (campos.size() < COLUMNAS_FIJAS) {
            throw new IllegalArgumentException(
                    "La fila trae "
                            + campos.size()
                            + " columna(s) y hacen falta al menos "
                            + COLUMNAS_FIJAS
                            + ": plan, ordenanza, codigo, nombre, vigenciaDesde, vigenciaHasta y"
                            + " geometria");
        }
        return new Zona(
                null,
                campos.get(0),
                campos.get(1),
                campos.get(2),
                campos.get(3),
                campos.get(6),
                fechaDe(campos.get(4), "vigenciaDesde"),
                fechaOpcionalDe(campos.get(5)));
    }

    private static List<ParametroUrbanistico> parametrosDe(List<String> campos) {
        List<ParametroUrbanistico> parametros = new ArrayList<>();
        for (int i = COLUMNAS_FIJAS; i < campos.size(); i++) {
            String celda = campos.get(i).strip();
            if (celda.isEmpty()) {
                continue;
            }
            int igual = celda.indexOf('=');
            if (igual <= 0 || igual == celda.length() - 1) {
                throw new IllegalArgumentException(
                        "El parametro urbanistico '"
                                + celda
                                + "' no tiene la forma «clave=valor» ni «clave=valor:unidad»");
            }
            String clave = celda.substring(0, igual);
            String resto = celda.substring(igual + 1);
            int dosPuntos = resto.lastIndexOf(':');
            String valor = dosPuntos < 0 ? resto : resto.substring(0, dosPuntos);
            String unidad = dosPuntos < 0 ? null : resto.substring(dosPuntos + 1);
            parametros.add(new ParametroUrbanistico(clave, valor, unidad));
        }
        return parametros;
    }

    private static LocalDate fechaDe(String texto, String columna) {
        try {
            return LocalDate.parse(texto.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "La columna " + columna + " no es una fecha AAAA-MM-DD: '" + texto + "'");
        }
    }

    private static @Nullable LocalDate fechaOpcionalDe(String texto) {
        return texto.strip().isEmpty() ? null : fechaDe(texto, "vigenciaHasta");
    }
}
