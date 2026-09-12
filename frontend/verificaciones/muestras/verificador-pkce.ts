/* NO viola nada, y esa es su razon de ser: es la EXCEPCION de
   «token-en-almacenamiento», medida.

   El verificador PKCE tiene que sobrevivir al rebote al emisor —el navegador se va
   y vuelve, y sin el no hay canje— asi que va en `sessionStorage`. No es una
   credencial: es un secreto de un solo uso que demuestra que quien canja es quien
   pidio. Su llave no lleva ninguna de las palabras que la prohibicion vigila.

   Si alguien ensanchara el selector para prohibir `sessionStorage` a secas, la
   puerta de identidad dejaria de poder funcionar y esta muestra lo diria. */
export function guardarElVerificador(valor: string): string | null {
  sessionStorage.setItem('catastro.pkce.verificador', valor);
  return sessionStorage.getItem('catastro.pkce.verificador');
}
