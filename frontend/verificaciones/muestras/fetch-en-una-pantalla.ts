/* Viola: «Ningun fetch suelto fuera de src/api/cliente.ts».
   Una pantalla que pide por su cuenta se salta el token, la raiz de la API y la
   traduccion del RFC 9457, y eso deja de verse en cuanto compila. */
export async function predios(): Promise<unknown> {
  const respuesta = await fetch('/catastro/api/v1/catastro/predios');
  return respuesta.json();
}
