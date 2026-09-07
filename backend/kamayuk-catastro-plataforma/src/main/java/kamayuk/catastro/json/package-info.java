/**
 * El unico sitio del sistema donde se escribe JSON con un serializador.
 *
 * <p>Nace con #20, y su motivo es un defecto medido: {@code auditoria.datos_anteriores} y {@code
 * datos_nuevos} son {@code jsonb}, catorce casos de uso componian ese JSON concatenando cadenas y
 * dos le pasaban prosa. Un JSON compuesto a mano necesita que alguien se acuerde de escapar lo que
 * interpola, y los dos {@code escapar()} que existian —duplicados, y los dos incompletos— son la
 * demostracion de que eso no se sostiene.
 *
 * <p>Tiene paquete propio y no vive dentro de {@code kamayuk.catastro.web} porque sus dos
 * consumidores no son de la misma capa: el {@code ObjectMapper} de Spring —lo que sale por HTTP y
 * el cuerpo de los eventos del buzon— y la bitacora, que escribe una columna de la base y no
 * transporta nada. Con la definicion dentro de {@code web}, escribir esa columna obligaria a
 * depender de la capa de presentacion.
 *
 * <p>Depende de {@code kamayuk.catastro.dominio} y nadie del dominio depende de el: Jackson no
 * entra en la capa de dominio (regla 7), y este paquete es la frontera donde se queda.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.catastro.json;
