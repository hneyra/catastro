/* Viola: «Ninguna aritmetica sobre un importe».
   La cuenta la hace el backend. Una segunda formula aqui puede divergir de la
   suya, y las dos cifras son indistinguibles al leerlas. */
export function total(v: { valorTerreno: number; valorConstruccion: number }): number {
  return v.valorTerreno + v.valorConstruccion;
}
