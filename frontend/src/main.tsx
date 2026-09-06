import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './ds/global.css';

/**
 * El proxy de datos se enciende ANTES de montar React.
 *
 * Y se carga con `import()` dinamico a proposito: apagado, la rama entera
 * —el proxy, sus fixtures y el padron de demostracion— **no viaja en el
 * paquete**. Con un `import` estatico viajaria siempre, y una interfaz de
 * produccion llevaria dentro 23 predios inventados esperando a que alguien
 * cambiara una bandera.
 *
 * Encendido por omision porque hoy no hay ningun backend al que pedir: el
 * armazon es lo que este issue construye, y la conexion se hace ruta a ruta
 * moviendo entradas a `src/simulado/servidas.ts`.
 */
const PROXY = import.meta.env.VITE_CATASTRO_PROXY_DE_DATOS !== 'false';

async function arrancar() {
  if (PROXY) {
    const { instalarProxyDeDatos } = await import('./simulado/proxy');
    instalarProxyDeDatos();
  }
  const raiz = document.getElementById('raiz');
  if (!raiz) throw new Error('Falta el nodo #raiz en index.html');
  createRoot(raiz).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}

void arrancar();
