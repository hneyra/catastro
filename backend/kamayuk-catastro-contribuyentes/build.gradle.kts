// El PUERTO al padron de contribuyentes, y su unico transporte. No es el contexto acotado: el
// contexto vive en `rentas` (ADR-0029 §«Lo descartado»: sacarlo como quinto sistema multiplicaria
// por cuatro las llamadas de cada pantalla sin resolver ningun problema existente).
//
// Lo que hay aqui son TRES archivos —la interfaz, el resumen que devuelve y el cliente HTTP—, y
// esa pobreza es la decision: si este modulo tuviera un repositorio, `catastro` leeria la tabla
// `contribuyente`, que es de `rentas`, y el escaner de frontera lo diria.

plugins {
    id("sgtm.modulo")
}

dependencies {
    // El cliente habla HTTP con la JDK; de Spring solo entran el estereotipo, `@Value` y el
    // acceso a la peticion en curso (de donde sale el token que se reenvia), y de Jackson el
    // arbol JSON. Ni un cliente HTTP de framework: ver el javadoc de `ClienteHttpDeNormativa`.
    implementation("org.springframework:spring-web")
    implementation("tools.jackson.core:jackson-databind")
}
