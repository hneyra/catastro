/* Viola: «Sin tildes ni enie en identificadores».
   El texto con tildes va en las CADENAS, que es donde se lee; en el nombre de
   una variable solo produce dos formas de escribir la misma cosa. */
export function calcular(valuacion: { alicuota: string }): string {
  const alícuota = valuacion.alicuota;
  return alícuota;
}
