/* Viola: «Solo la puerta y el proxy nombran fetch».
   Sustituir el transporte desde una pantalla es peor que pedir por tu cuenta:
   se lo cambia a todo el mundo. */
export function instalar(): void {
  globalThis.fetch = async () => new Response('{}');
}
