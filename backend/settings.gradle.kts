// Backend de `catastro`. El sistema del predio: su ficha versionada, su titularidad, su geometria
// y —desde P5C— la valuacion sellada del ejercicio (ADR-0027).
//
// Las barreras —ArchUnit, el escaner de fuentes, el de aserciones y la frontera de sistema— viven
// en `infrastructure/librerias-backend` y las comparten los cinco repositorios. Se consumen como
// *composite build* y no como artefacto publicado, y el motivo es el modo de fallo: un jar
// publicado a mano se queda viejo sin que nada se ponga rojo, y una verificacion vieja que pasa en
// verde es lo que este proyecto lleva doscientos issues evitando. Con `includeBuild`, Gradle la
// recompila desde el fuente en cada build: no puede quedarse vieja.
//
// LO QUE CUESTA, dicho aqui y no descubierto mas tarde: este backend NO COMPILA sin tener
// `infrastructure` clonado al lado.
val libreriasComunes = file("../../infrastructure/librerias-backend")
require(libreriasComunes.isDirectory) {
    "No esta ${libreriasComunes.canonicalPath}. El backend consume comun-verificaciones como" +
        " composite build, asi que `infrastructure` tiene que estar clonado al lado de" +
        " `catastro`: git clone https://github.com/hneyra/infrastructure ../../infrastructure"
}
includeBuild(libreriasComunes)

rootProject.name = "kamayuk-catastro-backend"

// Compartido: objetos de valor y contexto de tenant. No depende de ningun contexto acotado.
include("kamayuk-catastro-dominio-compartido")

// Esquema: el baseline de ADR-0032, la cache local de normativa y la prueba de aislamiento.
include("kamayuk-catastro-esquema")

// Plataforma: lleva el contexto de tenant hasta la transaccion (ARQ-03 §2).
include("kamayuk-catastro-plataforma")

// Normativa cacheada. NO es el servicio: es el cliente y su copia local sellada (ADR-0025 §1).
// Aqui la leen la valuacion —valores unitarios y depreciacion— y la guarda del arancel.
include("kamayuk-catastro-parametros")

// El PUERTO al padron de contribuyentes y su cliente HTTP, y nada mas: ni tabla, ni JDBC, ni
// pantalla. El padron vive en `rentas` (ADR-0029 lo dejo alli a proposito) y aqui solo se le
// pregunta el nombre de un titular.
include("kamayuk-catastro-contribuyentes")

// El contexto acotado. Se llama igual que el sistema porque `catastro` es las dos cosas: el
// repositorio y el unico contexto que contiene, igual que `kamayuk-normativa-parametros` es el
// unico de `normativa`.
include("kamayuk-catastro-catastro")

// Ensambla el artefacto y aloja las barreras: es el unico modulo que ve a todos los demas.
include("kamayuk-catastro-aplicacion")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}
