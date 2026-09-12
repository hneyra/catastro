import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * El empaquetado de `catastro-web`.
 *
 * <h2>`base` es `/catastro/` y no `/`, y no es cosmetica</h2>
 *
 * ADR-0030 §2 pone el sistema delante de la ruta, y el MISMO Traefik sirve las cinco
 * interfaces del producto. Con `base: '/'` el paquete pediria `/assets/index-<huella>.js`,
 * que en el cluster no es de nadie —o peor, es de otro sistema— y el ingreso contestaria
 * 404 a los activos de una pagina que si carga. En el cluster el ingreso quita el prefijo
 * (`stripPrefix`), asi que nginx sirve en la raiz del contenedor y el navegador pide con el
 * prefijo puesto: las dos mitades tienen que decir lo mismo.
 *
 * **Y de aqui sale ademas `import.meta.env.BASE_URL`, que es el `redirect_uri` de la puerta
 * de identidad.** Esa es la segunda mitad, y la que costo el acceso a produccion en `rentas`
 * (`rentas`#71): alli la base estaba puesta y el `redirect_uri` seguia siendo la raiz del
 * SITIO, asi que quien se autenticaba volvia a `https://<dominio>/` y recibia un 404 con el
 * `code` correcto. Aqui `api/identidad.ts` compone el retorno con `BASE_URL` para que no
 * haya dos sitios que mantener, y `verificaciones/identidad.mjs` lo mide sobre el paquete
 * construido de verdad.
 *
 * <h2>El backend se sirve por el MISMO ORIGEN que la interfaz</h2>
 *
 * No es comodidad: `backend/` no tiene ni una linea de configuracion de CORS —cero
 * ocurrencias de `cors` y de `allowedOrigins` en todo el arbol—, asi que un React servido
 * desde otro origen se queda bloqueado por el navegador antes de que el backend conteste
 * nada. En desarrollo lo resuelve este reenvio; en el cluster lo resuelve el ingreso, que
 * parte `/catastro` en dos y manda `/catastro/api/v1` al backend (`infrastructure/src/descriptor.ts`).
 *
 * **El reenvio se acota a `/catastro/api/v1` y ya no a `/catastro` entero.** Tenia que
 * cambiar con la `base`: con el reenvio ancho, el servidor de desarrollo mandaria al backend
 * las peticiones de la PROPIA PAGINA —`/catastro/`, `/catastro/assets/…`, `/catastro/configuracion.js`—
 * y la interfaz no llegaria a cargarse. Lo que se pierde es lo que el comentario anterior
 * temia: una ruta que el backend anadiera por encima de `Api.RAIZ` no se reenviaria. Eso lo
 * dice `verificaciones/rutas.mjs`, que compara ruta a ruta contra los `@RequestMapping`.
 *
 * **No hay alias `@/*`, y es una decision.** El `tsconfig.json` del precedente declara
 * `paths: { "@/*": ["src/*"] }` y su `vite.config.ts` no declara el alias correspondiente:
 * eso compila con `tsc` y revienta en `vite build` el dia que alguien lo use. O se declara
 * en los dos sitios o en ninguno; aqui, en ninguno.
 */
export default defineConfig({
  base: '/catastro/',
  plugins: [react()],
  server: {
    port: 5190,
    strictPort: false,
    proxy: {
      '/catastro/api/v1': {
        target: process.env.VITE_CATASTRO_BACKEND ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: { target: 'es2022', chunkSizeWarningLimit: 900 },
});
