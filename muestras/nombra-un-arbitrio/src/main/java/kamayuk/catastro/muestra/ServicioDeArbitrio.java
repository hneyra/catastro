package kamayuk.catastro.muestra;

/**
 * LA MUESTRA de {@code CatastroNoNombraUnArbitrioTest}: el defecto que ese escaner existe para
 * atrapar, escrito como se escribiria de verdad (#29).
 *
 * <h2>Por que es un archivo y no una cadena dentro de la prueba</h2>
 *
 * <p>Hasta #29 el contraste de aquella clase comparaba tres literales contra otro literal escrito
 * dos lineas mas arriba: <b>no llamaba al recorrido</b>, no leia ningun archivo y no podia ponerse
 * roja por ningun cambio en el escaner — se podia romper {@code archivosDeProduccion()} entera y
 * seguia verde. Una guarda que no puede fallar no protege nada.
 *
 * <p>Ahora el contraste corre <b>el mismo recorrido</b> sobre este archivo, con el mismo filtro de
 * rutas y el mismo escaner que sobre {@code backend/}, y exige que lo encuentre nombrando su ruta.
 * Es lo que hacen las {@code muestras/} de {@code comun-verificaciones}: una clase que viola la
 * regla, no una cadena dentro del test.
 *
 * <h2>Por que vive en la RAIZ del repositorio y no bajo {@code backend/}</h2>
 *
 * <p>Porque el filtro que hay que ejercer es {@code /src/main/}, asi que la muestra tiene que estar
 * bajo un {@code src/main/} para que el recorrido la vea — y cualquier sitio bajo {@code backend/}
 * la meteria tambien en el recorrido de PRODUCCION y en el de los cinco escaneres de {@code
 * comun-verificaciones}, que recorren {@code backend/} buscando exactamente eso. La muestra pondria
 * roja la guarda que demuestra, que es la forma tonta de esta trampa.
 *
 * <p>No la compila nadie: no es un conjunto de fuentes de Gradle, asi que ni Checkstyle ni Spotless
 * la miran. Es texto, que es lo unico que este escaner lee.
 */
public enum ServicioDeArbitrio {

    /** Los tres del enumerado {@code Servicio} de {@code rentas}. */
    LIMPIEZA_PUBLICA,
    PARQUES_JARDINES,
    SERENAZGO;

    /** Y el vocabulario del calculo, que es como el defecto llega de verdad. */
    public static final String COLUMNA = "factor_de_barrido";

    /** El mismo, en la forma en que se escribe un campo de Java. */
    public static final String CAMPO = "factorDeBarrido";

    /** Y la tabla que vendria detras. */
    public static final String TABLA = "tarifa_de_arbitrio";
}
