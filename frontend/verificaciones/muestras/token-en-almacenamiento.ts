/* Viola: «El token vive en memoria, nunca en localStorage ni sessionStorage».
   Es lo que hacia `src/api/cliente.ts` hasta la puerta de identidad, y el motivo
   por el que ya no: en una PC de ventanilla que tres turnos comparten, esto deja
   la credencial del turno de la manana funcionando por la tarde. */
export function recordarLaSesion(valor: string): string | null {
  localStorage.setItem('catastro.token', valor);
  return localStorage.getItem('catastro.token');
}
