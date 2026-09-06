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

// LA UNICA SALIDA, Y SOLO PARA CONSTRUIR EL ARTEFACTO (C-7, punto 5).
//
// El `Dockerfile` construye con el contexto en la raiz de ESTE repositorio, y
// `infrastructure/librerias-backend` vive en un clon hermano: fuera del contexto, y sin forma de
// meterlo dentro —un `.dockerignore` no puede describir un contexto que es el directorio padre—.
// Asi que la imagen se paraba en el `require` de aqui. Estaba escrito como hueco desde P3 y no lo
// medía nadie, porque ninguno de los cuatro repositorios construye su imagen en CI todavia.
//
// Lo que se midio antes de decidir: `comun-verificaciones` es `testImplementation` y **solo** de
// `kamayuk-catastro-aplicacion`. La imagen construye `bootJar` e `installDist` y no corre ni una
// prueba, asi que no necesita la libreria para nada — lo unico que la necesitaba era este
// `require`.
//
// De ahi la propiedad: con ella el build se queda SIN las verificaciones, y para que eso no pueda
// convertirse en «verificar sin verificar» el `build.gradle.kts` de la raiz **hace fallar toda
// tarea de prueba** mientras este puesta. O sea: o esta la libreria, o no hay verificacion; nunca
// una verificacion que pasa en verde sin la libreria, que es el modo de fallo que el composite
// build existe para impedir (#192).
val soloElArtefacto = providers.gradleProperty("kamayuk.sinLibreriasComunes").isPresent

// LO QUE CUESTA, dicho aqui y no descubierto mas tarde: este backend NO COMPILA sus pruebas sin
// tener `infrastructure` clonado al lado.
require(libreriasComunes.isDirectory || soloElArtefacto) {
    "No esta ${libreriasComunes.canonicalPath}. El backend consume comun-verificaciones como" +
        " composite build, asi que `infrastructure` tiene que estar clonado al lado de" +
        " `catastro`: git clone https://github.com/hneyra/infrastructure ../../infrastructure"
}
if (!soloElArtefacto) {
    includeBuild(libreriasComunes)
}

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

// El contexto acotado: `catastro` es las dos cosas, el repositorio y el unico contexto que
// contiene, igual que `kamayuk-normativa-parametros` es el unico de `normativa`. Por eso se
// llamaba `kamayuk-catastro-catastro`, y por eso se llama `nucleo` desde R-N (2026-09-05): la
// direccion pidio quitar la repeticion. El patron `kamayuk-<sistema>-<contexto>` queda intacto;
// lo que cambia es el nombre del contexto.
include("kamayuk-catastro-nucleo")

// El contexto acotado del urbanismo (#4): la zonificacion vigente, sus parametros
// urbanisticos, la seccion normativa de las vias y las habilitaciones urbanas. Publica LA
// ZONA a la que cae un predio; quien es compatible con que es dato de `rentas`
// (`ciiu.zonificacion_compatible`), que es la frontera de ADR-0024.
include("kamayuk-catastro-urbano")

// La gestion del riesgo de desastres del predio (#5). Zona de riesgo, faja marginal y
// certificado ITSE. Publica el hecho; quien emite la licencia decide (ADR-0024).
include("kamayuk-catastro-grd")

// El hallazgo catastral: campania, candidato, hallazgo, evidencia y acta (ADR-0035, #6).
// Es fiscalizacion CATASTRAL y no tributaria: no liquida, no determina y no emite un valor —eso
// es `rentas` (ADR-0024)—. Lo que hace es completar la mitad de ADR-0021 que faltaba: que las dos
// areas no coincidan es un hallazgo que se INFORMA, y hasta ahora no habia donde informarlo.
include("kamayuk-catastro-fiscalizacion")

// La copia local de usuarios, grupos y permisos, y su siembra (D-N5). No es un contexto
// acotado: es el lector que autoriza y el sembrador que implanta. Las pantallas de
// administracion de seguridad viven en `rentas` (ADR-0030 §3).
include("kamayuk-catastro-seguridad")

// Ensambla el artefacto y aloja las barreras: es el unico modulo que ve a todos los demas.
include("kamayuk-catastro-aplicacion")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}
