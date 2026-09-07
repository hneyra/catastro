# `muestras/` — el codigo que las guardas de este repositorio existen para atrapar

Aqui no hay codigo de produccion y no lo compila nadie. Lo que hay son **archivos que violan a
proposito una prohibicion**, para que la prueba que la vigila pueda demostrarse sobre un archivo de
verdad y no sobre una cadena escrita dentro del propio test.

Es lo mismo que hacen las [`muestras/` de
`comun-verificaciones`](https://github.com/hneyra/infrastructure/tree/main/librerias-backend/comun-verificaciones),
y por el mismo motivo, que este proyecto lleva escrito desde el primer dia: **una regla que no
puede fallar no protege nada**.

## Por que estan en la raiz del repositorio y no bajo `backend/`

Porque el filtro que hay que ejercer es `/src/main/`, asi que cada muestra vive bajo un `src/main/`
suyo — y **cualquier sitio bajo `backend/` la meteria tambien en el recorrido de produccion**: el
de la guarda que la usa, y el de los cinco escaneres de `comun-verificaciones`, que recorren
`backend/` buscando exactamente `/src/main/`. La muestra pondria roja la guarda que existe para
demostrar, que es la forma tonta de esta trampa.

Y por estar fuera de todo conjunto de fuentes de Gradle, ni Checkstyle ni Spotless las miran: son
texto, que es lo unico que estos escaneres leen.

## Lo que hay

| Muestra | La viola | La guarda |
|---|---|---|
| `nombra-un-arbitrio/` | ADR-0024: nombra los tres servicios de arbitrio de `rentas` y el vocabulario del calculo | `CatastroNoNombraUnArbitrioTest` (#7 AC 6, #29) |

## Si añades una

Añade con ella la prueba que la encuentra, y comprueba **las dos direcciones**: que sin la muestra
la guarda dice que no midio nada, y que con ella la encuentra nombrando su ruta. Una guarda que
solo puede pasar es la misma trampa por el otro lado.
