// Contexto acotado `fiscalizacion` de `catastro` (ADR-0035, #6).
//
// Es la fiscalizacion CATASTRAL y no la tributaria. La tributaria —liquidacion,
// resolucion de determinacion, programa, muestra— vive entera en
// `kamayuk-rentas-fiscalizacion` y aqui no se duplica ni se contradice: la frontera
// la pone ADR-0024 y `TransferirARentas` es el puente que ya existe entre las dos.
//
// Lo que este modulo hace es completar la mitad de ADR-0021 que no estaba: «que las
// dos areas no coincidan es un hallazgo que se INFORMA, no una correccion que se
// aplica». Aqui esta el sitio donde se informa —candidato, hallazgo, evidencia y
// acta— y NO esta la correccion, que es versionar la ficha y la ejecuta una persona.
//
// NI UN IMPORTE, NI UNA ALICUOTA, NI UN TRIBUTO. Si alguna clase de este modulo
// acabara nombrando uno, lo que hay que revisar es la frontera y no la clase.

plugins {
    id("kamayuk.modulo")
    id("kamayuk.pruebas-postgres")
}

dependencies {
    // La UNICA arista hacia otro contexto acotado, y se importa solo su paquete raiz,
    // que es la API publica (ARQ-01 §4.1): `LectorDeFichas`.
    //
    // La usa `VerificarEnCampo` para dos cosas que ADR-0035 punto 2 exige y que este
    // modulo no puede saber por si mismo: QUE VERSION de ficha se contrasto
    // (`fichaVigenteEn`) y CUANTO decia esa version (`areaDeLaVersion`). Sin lo
    // primero, un hallazgo de marzo no se puede releer en julio; sin lo segundo, la
    // diferencia que el hallazgo afirma no queda congelada y manana dice otra cosa.
    //
    // `LectorDeFichas` devuelve identificador y area: NI UN METODO QUE ESCRIBA. Esta
    // declarado uno a uno en `tiposAjenosQueFiscalizacionSoloLee()` y lo vigila
    // SOLO_LA_TRANSFERENCIA_ESCRIBE_FUERA_DE_FISCALIZACION, que desde este issue
    // mira codigo real de `catastro` y no solo su muestra.
    implementation(project(":kamayuk-catastro-nucleo"))

    // La prueba del repositorio corre contra PostgreSQL de verdad: provisiona la base
    // como un ambiente real y se conecta como kamayuk_app, no como el superusuario
    // que entrega el motor (CAL-01 §3.2). Es lo unico que puede demostrar que RLS
    // acota las cinco tablas nuevas y que las dos compuertas no se pueden saltar.
    testImplementation(testFixtures(project(":kamayuk-catastro-esquema")))
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")

    // El caso de uso se prueba envuelto en un proxy transaccional de verdad, para que
    // lo que se verifique sea la anotacion y no un TransactionTemplate escrito por la
    // propia prueba.
    testImplementation("org.springframework:spring-aop")

    // MockMvc para el borde: forma del JSON, parametros y traduccion de errores, sin
    // base de datos.
    testImplementation("org.springframework:spring-test")
    testRuntimeOnly(libs.postgresql)
}
