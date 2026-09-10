// La copia local de usuarios, grupos y permisos de `catastro` (D-N5, que contesta D-19).
//
// Lo que hay aqui son DOS cosas y no un contexto acotado entero: quien LEE la copia para autorizar
// —`ComprobadorDeAccesoJdbc`, la implementacion del puerto que `kamayuk-catastro-plataforma`
// declara— y quien la TRAE, que desde la etapa 4 de ADR-0039 es el consumidor del buzon de
// `identidad`. Las once escrituras de administracion de seguridad viven en `identidad`, que es el
// dueno de la autorizacion, asi que aqui no hay ni controlador ni pantalla. Y desde la etapa 5
// (identidad#5) la implantacion no escribe ninguna de las cuatro tablas: siembra `modulo_sistema`
// y `acceso` —el catalogo de este sistema, que es lo unico que este sistema declara— y lo demas
// llega por el buzon.
//
// El nombre del modulo no se elige: `ConfiguracionDeCatastro` ya lo reparte a
// SISTEMA_REPLICADO desde P5C, porque las cinco tablas de seguridad estan replicadas en los cuatro
// baselines (ADR-0032). Este modulo es el que las usa.

plugins {
    id("kamayuk.modulo")
    id("kamayuk.pruebas-postgres")
}

dependencies {
    testImplementation(testFixtures(project(":kamayuk-catastro-esquema")))
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.springframework:spring-aop")
    testRuntimeOnly(libs.postgresql)
}
