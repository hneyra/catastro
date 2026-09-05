// Contexto acotado `grd`: la gestion del riesgo de desastres del PREDIO (#5).
//
// Es el segundo contexto acotado de este sistema, y el primero que no es `nucleo`. Lo que
// contiene es un HECHO sobre el lote —en que zona de riesgo cae, si tiene ITSE vigente— y ninguna
// consecuencia: no emite licencias, no las niega y no sabe lo que es un giro (ADR-0024).
//
// NO depende de `kamayuk-catastro-nucleo`, y conviene saber por que: lo unico que necesita del
// predio es su POLIGONO, y ese cruce se resuelve dentro de una sola sentencia SQL —marco primero,
// operador espacial detras como refinado exacto (ADR-0034 regla 2)—. Sacar el poligono a Java para
// volver a meterlo como parametro seria mover cientos de vertices por la red para preguntar lo que
// el motor contesta en el sitio, y ademas dejaria el refinado sin la condicion de marco delante,
// que es justo lo que la regla existe para impedir.

plugins {
    id("kamayuk.modulo")
    id("kamayuk.pruebas-postgres")
}

dependencies {
    // La lectura del riesgo y la del ITSE se prueban contra PostgreSQL de verdad: lo que hay que
    // demostrar —que el marco filtra, que el operador espacial refina y que un certificado vencido
    // NO sale— lo sostiene el motor, no un doble.
    testImplementation(testFixtures(project(":kamayuk-catastro-esquema")))
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")

    // El caso de uso se prueba envuelto en un proxy transaccional de verdad, para que lo que se
    // verifique sea la anotacion y no un TransactionTemplate escrito por la propia prueba.
    testImplementation("org.springframework:spring-aop")

    // MockMvc para el borde HTTP: forma del JSON, parametros y traduccion de errores, sin base.
    testImplementation("org.springframework:spring-test")
    testRuntimeOnly(libs.postgresql)
}
