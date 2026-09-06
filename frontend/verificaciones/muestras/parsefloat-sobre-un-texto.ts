/* Viola: «Nada de parseFloat».
   Es la prohibicion HERMANA de la de `Number()` sobre un campo del backend, y
   hace falta aparte: aquella se ancla al NOMBRE del campo —`areaTerreno`,
   `valorTerreno`— y esta no se ancla a nada, porque lo unico decimal que este
   backend publica viaja como texto. Un `parseFloat` sobre una variable
   intermedia se escapa de la primera y pierde el decimal igual. */
export function aNumero(texto: string): number {
  return parseFloat(texto);
}
