package kamayuk.catastro.grd.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import kamayuk.catastro.auditoria.Auditoria;
import kamayuk.catastro.auditoria.RegistroDeAuditoria;
import kamayuk.catastro.carga.InformeDeImportacion;
import kamayuk.catastro.dominio.Observacion;
import kamayuk.catastro.grd.dominio.NivelDeRiesgo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El analizador de la carta de restricciones, fila a fila (#5, AC-7).
 *
 * <p>Sin base de datos: lo que se verifica aqui es que el archivo se lee, que la capa decide a que
 * tabla va cada fila y que <b>una fila mala se rechaza sola</b>. Como persiste PostgreSQL lo prueba
 * {@code GestionDeRiesgoFronteraTest} contra el motor real.
 *
 * <p>Y la ultima prueba corre {@code infra/carga-de-datos/ejemplos/riesgo.csv} <b>por el analizador
 * de verdad</b>, como {@code ArchivosDeEjemploTest} hace con los cinco de la siembra: un archivo
 * versionado se copia y se carga tal cual, asi que si le falta una columna o trae un nivel mal
 * escrito, el sintoma aparece contra un ambiente real —o delante de alguien— en vez de en el build.
 */
@DisplayName("#5 — La carta de riesgo se analiza fila a fila")
class ImportarCartaDeRiesgoTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("America/Lima"));

    private static final String ENCABEZADO =
            "capa,codigo,fenomeno,nivel,mitigable,cuerpoDeAgua,anchoM,fuente,documentoOrigen,"
                    + "vigenciaDesde,vigenciaHasta,geometria\n";

    private static final String CUADRADO =
            "\"MULTIPOLYGON(((-80.69 -5.27,-80.68 -5.27,-80.68 -5.26,-80.69 -5.26,-80.69 -5.27)))\"";

    private GestionDeRiesgoEnMemoria repositorio;
    private ImportarCartaDeRiesgo importar;
    private Observacion observacion;

    @BeforeEach
    void preparar() {
        repositorio = new GestionDeRiesgoEnMemoria();
        Auditoria auditoria = (RegistroDeAuditoria registro) -> {};
        importar =
                new ImportarCartaDeRiesgo(new RegistrarCapaDeRiesgo(repositorio, auditoria, RELOJ));
        observacion = Observacion.de("Carga de la carta de riesgo (#5)");
    }

    @Test
    @DisplayName("una fila PELIGRO y una FAJA_MARGINAL van cada una a su tabla")
    void cadaCapaVaASuTabla() {
        InformeDeImportacion informe =
                importar.importar(
                        leer(
                                ENCABEZADO
                                        + "PELIGRO,ZR-1,INUNDACION,MUY_ALTO,NO,,,CENEPRED,CARTA-25,"
                                        + "2025-01-01,,"
                                        + CUADRADO
                                        + "\n"
                                        + "FAJA_MARGINAL,FM-1,,,,Rio Piura,25.00,ANA,RD-2023,"
                                        + "2023-08-15,,"
                                        + CUADRADO
                                        + "\n"),
                        observacion);

        assertThat(informe.totalFilas()).isEqualTo(2);
        assertThat(informe.nuevas()).isEqualTo(2);
        assertThat(informe.rechazadas()).isEmpty();
        assertThat(repositorio.zonas())
                .singleElement()
                .satisfies(
                        zona -> {
                            assertThat(zona.codigo()).isEqualTo("ZR-1");
                            assertThat(zona.nivel()).isEqualTo(NivelDeRiesgo.MUY_ALTO);
                            assertThat(zona.mitigable()).isFalse();
                            assertThat(zona.impide())
                                    .as("no mitigable es lo que de verdad impide")
                                    .isTrue();
                        });
        assertThat(repositorio.fajas())
                .singleElement()
                .satisfies(
                        faja -> {
                            assertThat(faja.cuerpoDeAgua()).isEqualTo("Rio Piura");
                            assertThat(faja.ancho().magnitud().toPlainString()).isEqualTo("25.00");
                            assertThat(faja.ancho().unidad())
                                    .as(
                                            "un ancho de 25 se lee igual en metros que en pies si no lleva su unidad")
                                    .isEqualTo("ML");
                        });
    }

    @Test
    @DisplayName("«mitigable» no admite quedarse vacio: es el dato que decide")
    void mitigableNoAdmiteVacio() {
        InformeDeImportacion informe =
                importar.importar(
                        leer(
                                ENCABEZADO
                                        + "PELIGRO,ZR-1,INUNDACION,MUY_ALTO,,,,CENEPRED,CARTA-25,"
                                        + "2025-01-01,,"
                                        + CUADRADO
                                        + "\n"),
                        observacion);

        assertThat(repositorio.zonas())
                .as(
                        "un valor por omision autorizaria o negaria por descuido: las dos formas de"
                                + " equivocarse")
                .isEmpty();
        assertThat(informe.rechazadas())
                .singleElement()
                .satisfies(rechazada -> assertThat(rechazada.motivo()).contains("mitigable"));
    }

    @Test
    @DisplayName("una faja con nivel o fenomeno se rechaza: son columnas de la otra capa")
    void laFajaConColumnasDeLaOtraCapaSeRechaza() {
        InformeDeImportacion informe =
                importar.importar(
                        leer(
                                ENCABEZADO
                                        + "FAJA_MARGINAL,FM-1,INUNDACION,MUY_ALTO,,Rio Piura,25.00,"
                                        + "ANA,RD-2023,2023-08-15,,"
                                        + CUADRADO
                                        + "\n"),
                        observacion);

        assertThat(repositorio.fajas())
                .as("la ANA no declara un nivel; guardarla dejaria dentro un dato inventado")
                .isEmpty();
        assertThat(informe.rechazadas())
                .singleElement()
                .satisfies(rechazada -> assertThat(rechazada.motivo()).contains("otra capa"));
    }

    @Test
    @DisplayName("una fila mala no arrastra a las que vienen detras")
    void unaFilaMalaNoArrastraALasDemas() {
        InformeDeImportacion informe =
                importar.importar(
                        leer(
                                ENCABEZADO
                                        + "PELIGRO,ZR-1,INUNDACION,ALTISIMO,NO,,,CENEPRED,CARTA-25,"
                                        + "2025-01-01,,"
                                        + CUADRADO
                                        + "\n"
                                        + "PELIGRO,ZR-2,SISMO,MEDIO,SI,,,CENEPRED,CARTA-25,"
                                        + "2025-01-01,,"
                                        + CUADRADO
                                        + "\n"),
                        observacion);

        assertThat(informe.rechazadas())
                .singleElement()
                .satisfies(
                        rechazada -> {
                            assertThat(rechazada.fila())
                                    .as("el numero de LINEA del archivo, encabezado incluido")
                                    .isEqualTo(2);
                            assertThat(rechazada.motivo()).contains("BAJO, MEDIO, ALTO y MUY_ALTO");
                        });
        assertThat(repositorio.zonas())
                .as("la segunda entra: el rechazo es por fila y no por archivo (#247 §2)")
                .singleElement()
                .satisfies(zona -> assertThat(zona.codigo()).isEqualTo("ZR-2"));
    }

    @Test
    @DisplayName("una capa que no existe se rechaza nombrando las dos que si")
    void laCapaDesconocidaSeRechaza() {
        InformeDeImportacion informe =
                importar.importar(
                        leer(
                                ENCABEZADO
                                        + "ZONIFICACION,Z-1,,,,,,MUNICIPALIDAD,ORD-01,2025-01-01,,"
                                        + CUADRADO
                                        + "\n"),
                        observacion);

        assertThat(informe.rechazadas())
                .singleElement()
                .satisfies(
                        rechazada ->
                                assertThat(rechazada.motivo())
                                        .contains("PELIGRO")
                                        .contains("FAJA_MARGINAL"));
    }

    @Test
    @DisplayName("el poligono llega al repositorio tal cual: aqui no se toca un vertice")
    void elPoligonoNoSeToca() {
        importar.importar(
                leer(
                        ENCABEZADO
                                + "PELIGRO,ZR-1,INUNDACION,BAJO,SI,,,CENEPRED,CARTA-25,2025-01-01,,"
                                + CUADRADO
                                + "\n"),
                observacion);

        assertThat(repositorio.geometrias())
                .singleElement()
                .as("ni ST_Transform ni ST_Simplify: un vertice movido es un lindero movido")
                .isEqualTo(CUADRADO.substring(1, CUADRADO.length() - 1));
    }

    // ── El archivo de ejemplo versionado, por el analizador de verdad ──

    @Test
    @DisplayName("riesgo.csv se carga entero, sin una sola fila rechazada")
    void elArchivoDeEjemploSeCargaEntero() throws IOException {
        InformeDeImportacion informe;
        try (Reader archivo = Files.newBufferedReader(ejemplo(), StandardCharsets.UTF_8)) {
            informe = importar.importar(archivo, observacion);
        }

        assertThat(informe.rechazadas()).isEmpty();
        assertThat(informe.totalFilas()).isEqualTo(6);
        assertThat(repositorio.zonas()).hasSize(4);
        assertThat(repositorio.fajas()).hasSize(2);
        assertThat(repositorio.zonas())
                .as("el archivo tiene que ensenar las dos caras de «mitigable», o no ensena nada")
                .anySatisfy(zona -> assertThat(zona.mitigable()).isTrue())
                .anySatisfy(zona -> assertThat(zona.mitigable()).isFalse());
    }

    @Test
    @DisplayName("y no nombra ninguna cifra normativa (regla 5)")
    void elArchivoDeEjemploNoTraeCifrasNormativas() throws IOException {
        List<String> prohibidas =
                List.of("arancel", "valor unitario", "valorm2", "depreciacion", "uit", "alicuota");

        for (String linea : Files.readAllLines(ejemplo(), StandardCharsets.UTF_8)) {
            if (linea.stripLeading().startsWith("#")) {
                continue; // los comentarios explican justamente que no hay ninguna
            }
            String enMinusculas = linea.toLowerCase(Locale.ROOT);
            for (String palabra : prohibidas) {
                assertThat(enMinusculas)
                        .as("el ancho de una faja marginal es una MEDIDA, no un valor tributario")
                        .doesNotContain(palabra);
            }
        }
    }

    private static Path ejemplo() {
        Path candidato = Path.of("").toAbsolutePath();
        while (candidato != null
                && !Files.isDirectory(candidato.resolve("infra/carga-de-datos/ejemplos"))) {
            candidato = candidato.getParent();
        }
        if (candidato == null) {
            throw new IllegalStateException(
                    "No se encontro infra/carga-de-datos/ejemplos subiendo desde "
                            + Path.of("").toAbsolutePath()
                            + ". Sin el, esta prueba no analiza ningun archivo y «no se pudo"
                            + " comprobar» no es «esta bien»");
        }
        return candidato.resolve("infra/carga-de-datos/ejemplos/riesgo.csv");
    }

    private static Reader leer(String contenido) {
        return new StringReader(contenido);
    }
}
