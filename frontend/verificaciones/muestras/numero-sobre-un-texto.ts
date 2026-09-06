/* Viola: «Ningun Number() sobre un campo que el backend emite como texto».
   `AreaM2` viaja como "180.50"; pasarlo por Number para volver a formatearlo es
   como se pierde el decimal que RNF-055 conserva. */
export function area(ficha: { areaTerreno: string }): string {
  return Number(ficha.areaTerreno).toFixed(1);
}
