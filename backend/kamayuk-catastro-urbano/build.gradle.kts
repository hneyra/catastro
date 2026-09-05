// Contexto acotado `urbano` (#4): la zonificacion, sus parametros urbanisticos, la seccion
// normativa de las vias y las habilitaciones urbanas.
//
// PUBLICA LA ZONA; NO DECIDE LA COMPATIBILIDAD. Quien es compatible con que es dato de
// `rentas` (`ciiu.zonificacion_compatible`), y quien emite la licencia tambien. Es la
// frontera de ADR-0024, la misma que impide que `catastro` calcule un tributo.
//
// NO DEPENDE DE `kamayuk-catastro-nucleo`, y no es un olvido. La pregunta que este modulo
// contesta —«a que zona cae ESTE predio»— necesita el poligono del predio, y ADR-0034
// regla 2 obliga a resolverla en UNA SOLA SENTENCIA: el marco delante y el operador
// espacial detras como refinado exacto. Traerse la geometria a Java para volver a
// mandarla serian dos viajes y dejaria el `ST_Contains` en una sentencia sin marco, que es
// exactamente lo que esa regla prohibe. Asi que el `JOIN` lo hace el motor, `predio` y
// `zonificacion` son las dos de `catastro` —ningun SQL cruza la frontera de SISTEMA, que
// es la regla 11— y este modulo no importa un solo tipo de `nucleo`.

plugins {
    id("kamayuk.modulo")
    id("kamayuk.pruebas-postgres")
}

dependencies {
    // La prueba de frontera provisiona la base como un ambiente real y se conecta como
    // kamayuk_app, no como el superusuario que entrega Testcontainers (CAL-01 §3.2): es la
    // unica forma de medir que la consulta del marco corre BAJO la politica.
    testImplementation(testFixtures(project(":kamayuk-catastro-esquema")))
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")

    // El caso de uso se prueba envuelto en un proxy transaccional de verdad, para que lo
    // que se verifique sea la anotacion y no un TransactionTemplate escrito por la prueba.
    testImplementation("org.springframework:spring-aop")

    // MockMvc para el endpoint: se prueba el transporte —forma del JSON, parametros,
    // traduccion de errores— sin base de datos.
    testImplementation("org.springframework:spring-test")
    testRuntimeOnly(libs.postgresql)
}
