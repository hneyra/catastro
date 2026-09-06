import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/**
 * Las prohibiciones de `catastro-web`, escritas como verificacion.
 *
 * Mismo criterio que las once reglas del backend: **toda prohibicion que pueda
 * expresarse como verificacion automatica se expresa asi.** Una prohibicion que
 * solo vive en un documento se incumple en seis meses, y las tres de aqui
 * llevaban desde siempre escritas en prosa en el `CLAUDE.md` de este
 * repositorio, donde ninguna maquina las leia.
 *
 * Cada una tiene su muestra que la viola en `verificaciones/muestras/`, y
 * `verificaciones/reglas.mjs` exige que muerda sobre ella: **una regla que no
 * puede fallar no protege nada**. Es lo mismo que
 * `ReglasDeArquitecturaMuerdenTest` impone en el backend.
 */

/** Tildes, dieresis y enie: prohibidas en identificadores (idioma del repositorio). */
const LETRAS_ACENTUADAS = 'áéíóúÁÉÍÓÚñÑüÜ';

/**
 * Los dos unicos archivos que pueden nombrar `fetch`.
 *
 * `src/api/cliente.ts` es la puerta (AC-8). `src/simulado/proxy.ts` es la otra
 * cara de la misma moneda: el proxy de datos intercepta **en la frontera del
 * transporte** —sustituye `fetch` y devuelve `Response` de verdad—, que es la
 * primera de las cuatro decisiones de ADR-0010 y lo que hace que apagarlo no
 * obligue a reescribir ninguna pantalla. Un proxy que no pudiera nombrar `fetch`
 * tendria que ser un adaptador que la aplicacion elige, que es exactamente lo
 * que ADR-0010 descarta.
 */
const PUERTA = ['src/api/cliente.ts'];
const PROXY = ['src/simulado/proxy.ts'];

/**
 * Los nombres de campo que el backend emite como TEXTO.
 *
 * `ConfiguracionDeJson` serializa `Dinero`, `Alicuota`, `Porcentaje` y `AreaM2`
 * como cadena JSON con decimal plano —`"180.50"`, `"100.0000"`—, de modo que un
 * `Number(ficha.areaTerreno)` no es una conversion inocente: es donde se pierde
 * el decimal que RNF-055 existe para conservar. Se pintan como texto.
 */
const CAMPOS_DE_TEXTO =
  '^(area|arancel|alicuota|autovaluo|deuda|importe|monto|porcentaje|saldo|total|valor|longitud|score|umbral|exceso|ancho)';

const PROHIBICIONES = [
  {
    selector: `Identifier[name=/[${LETRAS_ACENTUADAS}]/]`,
    message: 'Sin tildes ni enie en identificadores. El texto con tildes va en las cadenas.',
  },
  /* Se prohibe sobre el CAMPO y no la llamada entera, que es lo que dice la
     regla: `Number(ruta.sujeto)` convierte un identificador que venia de la URL
     —donde todo es texto— y es legitimo; `Number(ficha.areaTerreno)` es donde se
     pierde el decimal. Prohibir `Number` a secas ponia trece sitios en rojo, y
     doce eran identificadores: una regla que grita en lo correcto se acaba
     apagando, y entonces no protege del caso que existe para atrapar. */
  {
    selector: `CallExpression[callee.name=/^(Number|parseFloat|parseInt)$/] > MemberExpression[property.name=/${CAMPOS_DE_TEXTO}/i]`,
    message:
      'Dinero, Alicuota, Porcentaje y AreaM2 viajan como texto JSON con decimal plano. Pasarlos por Number() es como se pierde un decimal; se pintan como texto.',
  },
  /* `parseFloat` no tiene ningun uso legitimo aqui: lo unico decimal que este
     sistema publica son los cuatro tipos que viajan como texto —el unico numero
     JSON de verdad es el marco geografico, que ya llega numero—. */
  {
    selector: `CallExpression[callee.name='parseFloat']`,
    message:
      'Nada de parseFloat: lo unico decimal que publica este backend viaja como texto y se pinta como texto.',
  },
  {
    selector: `BinaryExpression[operator=/^[-+*/%]$/] > MemberExpression[property.name=/${CAMPOS_DE_TEXTO}/i]`,
    message:
      'Ninguna aritmetica sobre un importe. La cifra llega como texto del backend, y una cuenta aqui es una segunda formula que puede divergir de la del servidor.',
  },
];

/** Nombrar `fetch`: prohibido fuera de la puerta. */
const NOMBRAR_FETCH = [
  {
    selector: `CallExpression[callee.name='fetch']`,
    message:
      'Ningun fetch suelto: todo sale por solicitar() o descargar() de src/api/cliente.ts, que es lo que permite cambiar el origen, el token y el trato de los errores en un sitio.',
  },
];

const TOCAR_FETCH = [
  {
    selector: `MemberExpression[property.name='fetch']`,
    message:
      'Solo la puerta (src/api/cliente.ts) y el proxy de datos (src/simulado/proxy.ts) nombran fetch.',
  },
];

export default tseslint.config(
  {
    ignores: [
      '**/node_modules/**',
      /* Todos los `dist*`, no solo `dist`: `sin-red.mjs` compila su propia vista
         previa en `dist-sin-red/` y el contraste en `dist-con-proxy/`, y con el
         patron acotado a `dist` ESLint entraba a lintar el paquete MINIFICADO
         —miles de «'document' is not defined» sobre una sola linea de 40 000
         caracteres—. `yarn lint` salia verde en un arbol limpio y rojo despues
         de correr `yarn sin-red`, que es un orden de ejecucion que CI tiene. */
      'dist*/**',
      '.capturas/**',
      '**/*.config.mjs',
      '**/*.config.ts',
      // Violan las reglas a proposito. Se lintan desde `reglas.mjs`, no desde `yarn lint`.
      'verificaciones/muestras/**',
      'verificaciones/**/*.mjs',
    ],
  },

  js.configs.recommended,
  ...tseslint.configs.recommended,

  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: { ...globals.browser, ...globals.es2022 },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    rules: {
      // `any` prohibido: el tipado es la mitad del motivo de elegir TypeScript.
      '@typescript-eslint/no-explicit-any': 'error',
      'no-restricted-syntax': ['error', ...PROHIBICIONES, ...NOMBRAR_FETCH, ...TOCAR_FETCH],
    },
  },

  {
    files: PUERTA,
    rules: {
      'no-restricted-syntax': ['error', ...PROHIBICIONES],
    },
  },

  {
    files: PROXY,
    rules: {
      // El proxy puede SUSTITUIR `fetch` —`globalThis.fetch = …`— y no puede
      // llamarlo suelto: lo que delega, lo delega en la funcion que guardo.
      'no-restricted-syntax': ['error', ...PROHIBICIONES, ...NOMBRAR_FETCH],
    },
  },
);
