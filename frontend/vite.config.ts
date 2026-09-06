import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * El backend se sirve por el MISMO ORIGEN que la interfaz.
 *
 * No es comodidad: `backend/` no tiene ni una linea de configuracion de CORS
 * —cero ocurrencias de `cors` y de `allowedOrigins` en todo el arbol—, asi que
 * un React servido desde otro origen se queda bloqueado por el navegador antes
 * de que el backend conteste nada. En desarrollo lo resuelve este reenvio; en la
 * imagen, el `proxy_pass` de `nginx.conf`.
 *
 * Se reenvia `/catastro` entero y no `/catastro/api/v1`: la raiz de la API es
 * `Api.RAIZ = "/catastro/api/v1"`, y acotar el reenvio a la raiz completa
 * dejaria fuera cualquier ruta que el backend anada por encima sin que nada lo
 * dijera.
 *
 * **No hay alias `@/*`, y es una decision.** El `tsconfig.json` del precedente
 * declara `paths: { "@/*": ["src/*"] }` y su `vite.config.ts` no declara el
 * alias correspondiente: eso compila con `tsc` y revienta en `vite build` el dia
 * que alguien lo use. O se declara en los dos sitios o en ninguno; aqui, en
 * ninguno.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5190,
    strictPort: false,
    proxy: {
      '/catastro': {
        target: process.env.VITE_CATASTRO_BACKEND ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: { target: 'es2022', chunkSizeWarningLimit: 900 },
});
