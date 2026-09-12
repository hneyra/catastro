import { describe, expect, it } from "vitest";
import type { Contenedor, EntornoDelDescriptor, Manifiesto } from "@kamayuk/infra-contrato";
import { catastro } from "../src/descriptor";

/**
 * El descriptor de `catastro`, verificado sobre lo que devuelve.
 *
 * Esto es lo que corre en la maquina de quien lo escribe y en el CI de este repositorio: **sin
 * Pulumi, sin token y sin cluster**. La auditoria completa —las convenciones de `INF-01` §4 y las
 * cinco prohibiciones— la hace `infrastructure` al componer; aqui se comprueba lo que este
 * repositorio decide y solo el.
 */

const ENTORNO: EntornoDelDescriptor = {
  ambiente: "stg",
  namespace: "kamayuk-catastro-stg",
  dominio: "stg.kamayuk.example",
  etiquetas: { "app.kubernetes.io/part-of": "kamayuk", ambiente: "stg" },
  imagenDe: (c) => `ghcr.io/hneyra/kamayuk-${c}:0eee58e43e04b1c2d3f4a5b6c7d8e9f0a1b2c3d4`,
  secretoDe: (c) => `kamayuk-catastro-stg-${c}`,
  prioridadDe: (clase) => `kamayuk-stg-prioridad-${clase}`,
  // Del AMBIENTE, no de este sistema (C-7): quien recibe el aviso cuando algo
  // se rompe aqui. `checkInvariants` de `infrastructure` rechaza el relleno.
  operacion: { responsable: "Guardia de plataforma", canal: "guardia@example.pe" },
  // La municipalidad que el AMBIENTE implanta (C-14, punto 4). Los cuatro sistemas implantan la
  // misma, cada uno en su base.
  implantacion: {
    ubigeo: "200105",
    nombre: "Municipalidad Distrital de Catacaos",
    tipo: "DISTRITAL",
    administrador: "administrador",
    nombreDelAdministrador: "Administrador del sistema",
    esDemostracion: true,
    // El `id` de la fila que crea el Job de implantacion. En una base recien creada vale 1.
    municipalidadId: 1,
  },
  namespaceDe: (otro) => `kamayuk-${otro}-stg`,
  // El nombre de un `Job` lleva la version: un `Job` de Kubernetes es INMUTABLE.
  nombreConVersion: (base) => `${base}-0eee58e43e04`,
  plataforma: {
    namespace: "kamayuk-stg",
    // El anfitrion del motor, ya cruzando el namespace (C-17, punto 1). Los cuatro descriptores
    // escribian `postgres:5432` a mano, que es el nombre del `compose.yaml` local: en Kubernetes
    // no existe ningun `Service` que se llame asi.
    motor: "kamayuk-stg-postgres.kamayuk-stg:5432",
    emisor: "https://stg.kamayuk.example/keycloak/realms/kamayuk",
    jwks: "http://kamayuk-stg-identidad.kamayuk-stg:8080/keycloak/realms/kamayuk/protocol/openid-connect/certs",
    token: "http://kamayuk-stg-identidad.kamayuk-stg:8080/keycloak/realms/kamayuk/protocol/openid-connect/token",
  },
};

describe("el descriptor de catastro", () => {
  it("declara su base, y SOLO la suya", () => {
    const base = catastro.baseDeDatos(ENTORNO);
    expect(base.nombre).toBe("catastro");
    for (const rol of base.roles) {
      expect(rol.sobre).toEqual(["catastro"]);
      // Un superusuario OMITE RLS aunque haya FORCE (DAT-01 §0, hallazgo 1).
      expect(rol.superusuario).toBe(false);
    }
  });

  it("no fija la etiqueta de ninguna imagen: la pide", () => {
    // La prohibicion (b) de `infrastructure`, comprobada aqui tambien porque es la que sostiene
    // que una liberacion normal NO sea un `pulumi up` (ADR-0011 §5).
    const admisibles = catastro.imagenes.map((n) => ENTORNO.imagenDe(n));
    const imagenes = [...catastro.despliegue(ENTORNO), ...catastro.migracion(ENTORNO)]
      .flatMap((m) =>
        m.kind === "Deployment"
          ? m.spec.template.spec.containers
          : m.kind === "Job"
            ? m.spec.template.spec.containers
            : [],
      )
      .map((c) => c.image);
    expect(imagenes.length).toBeGreaterThan(0);
    for (const i of imagenes) expect(admisibles).toContain(i);
  });

  it("todas sus rutas van bajo su prefijo", () => {
    for (const m of catastro.ingreso(ENTORNO)) {
      if (m.kind !== "IngressRoute") continue;
      for (const r of m.spec.routes) {
        for (const encaje of r.match.matchAll(/PathPrefix\(`([^`]*)`\)/g)) {
          expect(encaje[1]).toMatch(/^\/catastro(\/|$)/);
        }
      }
    }
  });

  it("no emite ningun Secret, y su inventario no trae valores", () => {
    const todos = [
      ...catastro.despliegue(ENTORNO),
      ...catastro.migracion(ENTORNO),
      ...catastro.ingreso(ENTORNO),
    ];
    expect(todos.some((m) => (m as { kind: string }).kind === "Secret")).toBe(false);
    for (const c of catastro.claves(ENTORNO)) {
      for (const campo of ["valor", "value", "data", "stringData", "password"]) {
        expect((c as unknown as Record<string, unknown>)[campo]).toBeUndefined();
      }
    }
  });

  it("todo contenedor declara limites de recursos", () => {
    const contenedores = [...catastro.despliegue(ENTORNO), ...catastro.migracion(ENTORNO)].flatMap((m) =>
      m.kind === "Deployment"
        ? m.spec.template.spec.containers
        : m.kind === "Job"
          ? m.spec.template.spec.containers
          : [],
    );
    for (const c of contenedores) {
      expect(c.resources.requests.cpu).toBeTruthy();
      expect(c.resources.limits.memory).toBeTruthy();
    }
  });

  it("su egreso es identidad (el sistema), normativa y rentas, y nada mas", () => {
    // `identidad-sistema` es el SISTEMA `identidad` (ADR-0039), no Keycloak: el filtro de abajo
    // quita `identidad` a secas, que es la etiqueta de Keycloak en la plataforma.
    expect(destinosDeEgreso()).toEqual(["identidad-sistema", "normativa", "rentas"]);
  });
});

/** Los SISTEMAS a los que este descriptor declara egreso. El motor y Keycloak no cuentan. */
function destinosDeEgreso(): string[] {
  const infra = ["postgres", "identidad"];
  return catastro
    .egreso(ENTORNO)
    .flatMap((p) => p.spec.egress ?? [])
    .flatMap((r) => r.to ?? [])
    .map((s) => s.podSelector?.matchLabels?.["componente"])
    .filter((c): c is string => c !== undefined && !infra.includes(c))
    .sort();
}

describe("C-14 — que esto se pueda desplegar", () => {
  /**
   * El Job de migracion corre la imagen del MIGRADOR, no la de la aplicacion.
   *
   * Hasta C-14 corria la misma que el `Deployment` con `KAMAYUK_DB_USUARIO=kamayuk_owner` y sin perfil:
   * arrancaba el proceso web con las credenciales del unico rol con DDL, y la aplicacion tiene
   * `spring.flyway.enabled: false` a proposito (ARQ-03 §4). O sea que ese Job **no migraba**.
   */
  it("el Job de migracion corre el migrador, con las variables que el migrador lee", () => {
    const contenedores = contenedoresDe(catastro.migracion(ENTORNO));
    expect(contenedores).toHaveLength(1);
    const c = contenedores[0]!;
    expect(c.image).toBe(ENTORNO.imagenDe(`${"catastro"}-migrador`));
    expect(valorDe(c, "KAMAYUK_DB_OWNER_USUARIO")).toBe("kamayuk_owner");
    expect(declara(c, "KAMAYUK_DB_OWNER_CLAVE")).toBe(true);
    // La de la APLICACION. El migrador no la lee, y ponerla es lo que hacia que este Job
    // pareciera correcto sin migrar nada.
    expect(declara(c, "KAMAYUK_DB_USUARIO")).toBe(false);
  });

  /**
   * Y las TRES imagenes: dos objetivos de `backend/Dockerfile` y una de `frontend/Dockerfile`.
   *
   * La tercera se llama `catastro-interfaz` y no `catastro-web`, que es como se publicaba hasta
   * `catastro`#102: `kamayuk-catastro-web` ya es el `Deployment` y el `Service` del BACKEND con
   * el perfil `web`, y con los dos manifiestos en el mismo namespace esa frase pasa a tener dos
   * respuestas. `rentas` y `caja` llaman `kamayuk-<sistema>-interfaz` a lo suyo.
   */
  it("y las tres imagenes son los dos objetivos del backend y la de la interfaz", () => {
    expect(catastro.imagenes).toEqual([
      "catastro",
      `${"catastro"}-migrador`,
      `${"catastro"}-interfaz`,
    ]);
  });

  /**
   * El Job de implantacion (C-7 §2.3): la fila de `municipalidad` en SU base.
   *
   * Con el migrador de contenedor de inicializacion: un `Deployment` no sabe esperar a un `Job`,
   * y la salida del monolito —un contenedor con `psql`— no vale aqui, porque un descriptor solo
   * puede nombrar SUS imagenes (prohibicion (b)).
   */
  it("implanta la municipalidad del ambiente, detras del esquema", () => {
    const jobs = catastro.implantacion(ENTORNO).filter((m) => m.kind === "Job");
    expect(jobs).toHaveLength(1);
    const job = jobs[0]!;
    expect(job.metadata.name).toContain("0eee58e43e04");
    const pod = job.spec.template.spec;
    expect((pod.initContainers ?? []).map((c) => c.image)).toEqual([
      ENTORNO.imagenDe(`${"catastro"}-migrador`),
    ]);
    const c = pod.containers[0]!;
    expect(c.image).toBe(ENTORNO.imagenDe("catastro"));
    expect(valorDe(c, "SPRING_PROFILES_ACTIVE")).toBe("batch");
    expect(valorDe(c, "KAMAYUK_IMPLANTACION_UBIGEO")).toBe("200105");
    expect(valorDe(c, "KAMAYUK_IMPLANTACION_ESDEMOSTRACION")).toBe("true");
  });

  /**
   * Un `podSelector` sin `namespaceSelector` selecciona pods **del mismo namespace**, y desde
   * ADR-0031 cada sistema tiene el suyo. Una regla escrita asi no abre nada: el sintoma es
   * trafico denegado con una politica que dice permitirlo.
   */
  it("toda regla de egreso nombra el namespace de su destino", () => {
    const destinos = catastro.egreso(ENTORNO)
      .flatMap((p) => p.spec.egress ?? [])
      .flatMap((r) => r.to ?? []);
    expect(destinos.length).toBeGreaterThan(0);
    for (const destino of destinos) {
      expect(destino.namespaceSelector, JSON.stringify(destino)).toBeDefined();
    }
  });
});

/** Los contenedores de una lista de manifiestos, los de inicializacion aparte. */
function contenedoresDe(manifiestos: readonly Manifiesto[]) {
  return manifiestos.flatMap((m) =>
    m.kind === "Deployment"
      ? m.spec.template.spec.containers
      : m.kind === "Job"
        ? m.spec.template.spec.containers
        : m.kind === "CronJob"
          ? m.spec.jobTemplate.spec.template.spec.containers
          : [],
  );
}

function valorDe(c: Contenedor, nombre: string): string | undefined {
  return (c.env ?? []).find((e) => e.name === nombre)?.value;
}

function declara(c: Contenedor, nombre: string): boolean {
  return (c.env ?? []).some((e) => e.name === nombre);
}

describe("C-14 §3 — el publicador del padron, desplegado", () => {
  /**
   * La mitad emisora del camino que C-8 midio de extremo a extremo. Escribe su propio buzon y
   * **no entrega nada**: la entrega la hace el consumidor viniendo a buscarla. Por eso corre
   * activo, y no suspendido: no llama a nadie y no depende de ninguna identidad de servicio.
   */
  it("es un CronJob activo, con la municipalidad que fija el contexto", () => {
    const crones = catastro.lotes(ENTORNO).filter((m) => m.kind === "CronJob");
    expect(crones.map((c) => c.metadata.name)).toEqual([
      "kamayuk-catastro-publicador",
      "kamayuk-catastro-consumidor-de-identidad",
    ]);
    const cron = crones[0]!;
    expect(cron.spec.suspend).toBeUndefined();
    expect(cron.spec.concurrencyPolicy).toBe("Forbid");
    const c = cron.spec.jobTemplate.spec.template.spec.containers[0]!;
    expect(c.image).toBe(ENTORNO.imagenDe("catastro"));
    expect(valorDe(c, "SPRING_PROFILES_ACTIVE")).toBe("batch");
    // `@ConditionalOnProperty("kamayuk.catastro.publicacion.municipalidad")`: sin ella el runner
    // NO se registra y el CronJob arranca un proceso que no hace nada.
    expect(valorDe(c, "KAMAYUK_CATASTRO_PUBLICACION_MUNICIPALIDAD")).toBe("1");
    // Y el ejercicio NO: con el, el publicador corre ademas la valuacion, que es un acto de un
    // ejercicio y no se dispara desde una tarea programada que nadie pidio.
    expect(declara(c, "KAMAYUK_CATASTRO_PUBLICACION_EJERCICIO")).toBe(false);
  });
});

describe("C-17 — que el despliegue pase de verdad", () => {
  /**
   * El anfitrion del motor **se pide**, y este descriptor no escribe ninguno.
   *
   * Es la mutacion que este criterio existe para cazar: hasta C-17 la constante decia
   * `jdbc:postgresql://postgres:5432/...`, y en Kubernetes no hay ningun `Service` llamado
   * `postgres` —ese nombre viene del `compose.yaml` local—. Medido en el clúster:
   * `UnknownHostException` en los ocho Jobs de los cuatro sistemas y en sus `Deployment`.
   */
  it("toda URL de base sale del anfitrion que entrega el entorno", () => {
    const urls = contenedoresDe([
      ...catastro.despliegue(ENTORNO),
      ...catastro.migracion(ENTORNO),
      ...catastro.implantacion(ENTORNO),
      ...catastro.lotes(ENTORNO),
    ]).flatMap((c) => (c.env ?? []).map((v) => v.value ?? ""))
      .filter((v) => v.startsWith("jdbc:"));

    expect(urls.length, "ninguna variable lleva una URL de base: ¿se dejo de leer?").toBeGreaterThan(0);
    for (const url of urls) {
      expect(url).toBe(`jdbc:postgresql://${ENTORNO.plataforma.motor}/catastro`);
    }
  });

  /**
   * DNS, sin el cual las demas reglas de egreso no sirven de nada.
   *
   * Una politica de egreso convierte a los pods que selecciona en «solo lo declarado», y todo lo
   * que estas reglas nombran —el motor, la identidad, los sistemas hermanos— se alcanza por el
   * nombre de un `Service`. Resolverlo es una consulta a CoreDNS, en `kube-system`. Con la regla
   * anadida a mano sobre el clúster, las ocho tareas de los cuatro sistemas pasaron de `Failed` a
   * `Complete` (C-17, punto 3).
   */
  it("abre DNS hacia kube-system, en UDP y en TCP, en CADA politica de egreso", () => {
    /* Por politica y no en total: desde `catastro`#102 hay dos —la del backend y la de la
       interfaz— y una cuenta global de «hay una regla de DNS» se cumpliria con que UNA de las dos
       la tuviera, dejando a la otra sin resolver ningun nombre. Cada `podSelector` restringe a
       sus pods por separado: lo que abre una politica no lo hereda la otra. */
    const politicas = catastro.egreso(ENTORNO).filter((p) => p.spec.policyTypes.includes("Egress"));
    expect(politicas.length, "sin politicas de egreso esto se cumpliria solo").toBeGreaterThan(1);

    for (const politica of politicas) {
      const dns = (politica.spec.egress ?? []).filter((r) =>
        (r.to ?? []).some(
          (d) => d.namespaceSelector?.matchLabels?.["kubernetes.io/metadata.name"] === "kube-system",
        ),
      );
      expect(
        dns,
        `«${politica.metadata.name}»: sin DNS ninguna de sus reglas de egreso puede resolver un nombre`,
      ).toHaveLength(1);
      expect(
        (dns[0]?.ports ?? []).map((p) => `${p.protocol}/${p.port}`).sort(),
        "TCP tambien: una respuesta que no cabe en un datagrama se reintenta por TCP",
      ).toEqual(["TCP/53", "UDP/53"]);
    }
  });
});

describe("ADR-0039 etapa 4 — el consumidor del buzon de identidad, desplegado (identidad#4 AC-2)", () => {
  const cron = () =>
    catastro
      .lotes(ENTORNO)
      .filter((m) => m.kind === "CronJob")
      .find((c) => c.metadata.name === "kamayuk-catastro-consumidor-de-identidad")!;

  /**
   * Las tres mitades de «el consumidor CORRE» que `infrastructure` comprueba (AC-3), vistas
   * desde aqui: existe, no nace suspendido, y tiene con que autenticarse. Cualquiera sola lo apaga
   * en silencio — es lo que #21 midio con el ingestor de `catastro`, que nacio `suspend: true`
   * «hasta que exista la identidad de servicio» y esa linea se convirtio en un requisito.
   */
  it("existe, cada cinco minutos, nunca dos a la vez y con un solo intento", () => {
    const c = cron();
    expect(c).toBeDefined();
    expect(c.spec.schedule).toBe("*/5 * * * *");
    expect(c.spec.concurrencyPolicy).toBe("Forbid");
    expect(c.spec.jobTemplate.spec.backoffLimit).toBe(1);
    expect(c.spec.jobTemplate.spec.template.spec.priorityClassName).toBe(ENTORNO.prioridadDe("lote"));
  });

  it("y NO nace suspendido: lo que lo sostiene es la cuenta declarada, no un interruptor", () => {
    expect(cron().spec.suspend, "identidad#4 AC-3: un `suspend: true` lo apaga en silencio").toBeUndefined();
  });

  it("lleva las seis variables del consumidor, la URL compuesta con el namespace de identidad", () => {
    const c = cron().spec.jobTemplate.spec.template.spec.containers[0]!;
    expect(c.image).toBe(ENTORNO.imagenDe("catastro"));
    expect(valorDe(c, "SPRING_PROFILES_ACTIVE")).toBe("batch");
    // `kamayuk-identidad-web` en SU namespace: es como se llama el Service del backend de
    // `identidad`, y `namespaceDe` es lo que impide escribir el namespace de aqui.
    expect(valorDe(c, "KAMAYUK_IDENTIDAD_URL")).toBe("http://kamayuk-identidad-web.kamayuk-identidad-stg");
    expect(valorDe(c, "KAMAYUK_IDENTIDAD_TOKEN")).toBe(ENTORNO.plataforma.token);
    expect(valorDe(c, "KAMAYUK_IDENTIDAD_CLIENTE")).toBe("kamayuk-catastro-servicio-200105");
    expect(
      (c.env ?? []).find((v) => v.name === "KAMAYUK_IDENTIDAD_CREDENCIAL")?.valueFrom?.secretKeyRef,
    ).toEqual({ name: "kamayuk-catastro-stg-identidad", key: "clave" });
    expect(valorDe(c, "KAMAYUK_IDENTIDAD_RESPONSABLE")).toBe(ENTORNO.operacion.responsable);
    expect(valorDe(c, "KAMAYUK_IDENTIDAD_CANAL")).toBe(ENTORNO.operacion.canal);
    // `@ConditionalOnProperty("kamayuk.identidad.consumidor.municipalidad")`: sin ella el runner
    // NO se registra y el CronJob arranca un proceso que no hace nada (C-18 §5).
    expect(valorDe(c, "KAMAYUK_IDENTIDAD_CONSUMIDOR_MUNICIPALIDAD")).toBe("1");
  });

  it("y la implantacion lleva las mismas, menos la municipalidad, porque termina con una pasada", () => {
    const c = catastro.implantacion(ENTORNO).filter((m) => m.kind === "Job")[0]!.spec.template.spec
      .containers[0]!;
    for (const v of [
      "KAMAYUK_IDENTIDAD_URL",
      "KAMAYUK_IDENTIDAD_TOKEN",
      "KAMAYUK_IDENTIDAD_CLIENTE",
      "KAMAYUK_IDENTIDAD_CREDENCIAL",
      "KAMAYUK_IDENTIDAD_RESPONSABLE",
      "KAMAYUK_IDENTIDAD_CANAL",
    ]) {
      expect(declara(c, v), v).toBe(true);
    }
    // La municipalidad la CREA la implantacion; con la propiedad puesta correrian dos pasadas en
    // el mismo proceso, la del runner y la de la implantacion.
    expect(declara(c, "KAMAYUK_IDENTIDAD_CONSUMIDOR_MUNICIPALIDAD")).toBe(false);
  });

  it("declara la credencial del emisor, con `emisor: keycloak` y el nombre del que cuelga el secretKeyRef", () => {
    const clave = catastro.claves(ENTORNO).find((c) => c.nombre === "kamayuk-catastro-stg-identidad");
    expect(clave).toBeDefined();
    // Sin `emisor`, `bootstrap-secretos.sh` generaria un valor aleatorio que ningun emisor firmo:
    // el pod arranca y `identidad` contesta 401 en la primera llamada (#21).
    expect(clave?.emisor).toBe("keycloak");
    expect(clave?.clave).toBe("clave");
    // Y ninguna otra clave lleva emisor: las de la base las genera la plataforma.
    expect(catastro.claves(ENTORNO).filter((c) => c.emisor !== undefined)).toHaveLength(1);
  });

  it("abre egreso hacia el namespace del SISTEMA identidad, y no hacia Keycloak por el", () => {
    const reglas = catastro
      .egreso(ENTORNO)
      .flatMap((p) => p.spec.egress ?? [])
      .filter((r) =>
        (r.to ?? []).some(
          (d) => d.namespaceSelector?.matchLabels?.["kubernetes.io/metadata.name"] === "kamayuk-identidad-stg",
        ),
      );
    expect(reglas).toHaveLength(1);
    const destino = reglas[0]!.to![0]!;
    // `identidad-sistema`: en la plataforma `componente: identidad` es Keycloak, y un
    // `podSelector` con ese nombre en el namespace del sistema no seleccionaria nada.
    expect(destino.podSelector?.matchLabels?.["componente"]).toBe("identidad-sistema");
    expect((reglas[0]!.ports ?? []).map((p) => `${p.protocol}/${p.port}`)).toEqual(["TCP/8080"]);
  });
});

/**
 * La interfaz, desplegada por fin (`catastro`#102).
 *
 * Hasta este trabajo `kamayuk-catastro-web` —la imagen de la interfaz— se publicaba en cada merge
 * y **este descriptor no la mencionaba ni una vez**: un artefacto que nadie arrancaba. Lo contaba
 * `infrastructure` como censo, «lo que se publica y nadie despliega».
 */
describe("la interfaz, y el reparto de la ruta que la hace alcanzable", () => {
  const manifiestos = () => catastro.despliegue(ENTORNO);
  const de = (kind: string, name: string) =>
    manifiestos().find((m) => m.kind === kind && m.metadata.name === name);

  it("tiene su ConfigMap, su Deployment y su Service", () => {
    for (const kind of ["ConfigMap", "Deployment", "Service"]) {
      const nombre = kind === "ConfigMap" ? "kamayuk-catastro-interfaz-configuracion" : "kamayuk-catastro-interfaz";
      expect(
        de(kind, nombre),
        `falta el ${kind} «${nombre}»: la imagen se publica en cada merge y sin esto no la arranca nadie`,
      ).toBeDefined();
    }
  });

  /**
   * Su `componente` es SUYO, y esa es la etiqueta que le niega la salida a la base.
   *
   * `egreso()` abre el motor, Keycloak y los tres sistemas vecinos a los pods
   * `componente: catastro`. Con esa misma etiqueta, un nginx de archivos estaticos heredaria las
   * cinco aristas — incluida la de PostgreSQL, o sea la base del padron catastral.
   */
  it("no lleva la etiqueta «componente» del backend, que es lo que le abriria el motor", () => {
    const interfaz = de("Deployment", "kamayuk-catastro-interfaz");
    const backend = de("Deployment", "kamayuk-catastro-web");
    const componenteDe = (m: Manifiesto | undefined) =>
      (m?.metadata.labels ?? {})["componente"];
    expect(componenteDe(backend)).toBe("catastro");
    expect(componenteDe(interfaz)).toBe("catastro-interfaz");
  });

  /** Un `Secret` montado en un nginx de archivos estaticos es una credencial regalada. */
  it("no recibe ni una variable de entorno ni un solo secreto", () => {
    const d = de("Deployment", "kamayuk-catastro-interfaz");
    const contenedores: Contenedor[] =
      d?.kind === "Deployment" ? d.spec.template.spec.containers : [];
    expect(contenedores).toHaveLength(1);
    expect(contenedores[0]!.env ?? []).toEqual([]);
    expect(JSON.stringify(d)).not.toContain("secretKeyRef");
  });

  /**
   * El `ConfigMap` trae el emisor PUBLICO, el cliente y el alcance.
   *
   * Es lo unico que hace que una sola imagen sirva para todas las municipalidades: Vite resuelve
   * `import.meta.env.VITE_*` al construir, asi que un emisor horneado convertiria la imagen en la
   * imagen de un ambiente. Se monta sobre `public/configuracion.js`, que viaja vacio.
   */
  it("sirve las senias del ambiente, con el emisor PUBLICO y no el JWKS interno", () => {
    const cm = de("ConfigMap", "kamayuk-catastro-interfaz-configuracion");
    const guion = (cm as unknown as { data: Record<string, string> }).data["configuracion.js"] ?? "";
    expect(guion).toContain("window.__KAMAYUK_CATASTRO__");
    // El mismo nombre global que declara `frontend/src/api/configuracion.ts`: si uno cambia y el
    // otro no, la cadena cae al escalon de abajo y el ambiente no entra nunca — en silencio.
    const senas = JSON.parse(guion.replace(/^[^=]*=\s*/, "").replace(/;\s*$/, "")) as Record<string, string>;
    expect(senas["oidcRealm"]).toBe(ENTORNO.plataforma.emisor);
    expect(
      senas["oidcRealm"],
      "el JWKS es una direccion de la red interna del cluster: el navegador no la alcanza",
    ).not.toBe(ENTORNO.plataforma.jwks);
    expect(senas["oidcCliente"]).toBe("kamayuk-backoffice");
    expect(
      senas["oidcAlcance"],
      "sin `offline_access`: el token vive en memoria y muere con la pestana (ADR-0030 §3)",
    ).toBe("openid profile");
  });

  /**
   * **Las prioridades del ingreso, explicitas.**
   *
   * Traefik v3 ordena por la longitud del `match` cuando nadie declara `priority`, asi que hoy
   * saldria bien por accidente. El fallo no grita: con la precedencia al reves, una ruta de la
   * API la atenderia el nginx de la interfaz, cuyo `try_files` devuelve el `index.html` con un
   * **200**. La pantalla pide JSON y recibe HTML con codigo de exito.
   */
  it("manda /catastro/api/v1 al backend y /catastro a la interfaz, y la API gana", () => {
    const ingreso = catastro.ingreso(ENTORNO);
    const ruta = ingreso.find((m) => m.kind === "IngressRoute");
    const rutas = ruta?.kind === "IngressRoute" ? ruta.spec.routes : [];
    expect(rutas).toHaveLength(2);

    const api = rutas.find((r) => r.match.includes("/catastro/api/v1"))!;
    const interfaz = rutas.find((r) => !r.match.includes("/catastro/api/v1"))!;
    expect(api.services[0]!.name).toBe("kamayuk-catastro-web");
    expect(interfaz.services[0]!.name).toBe("kamayuk-catastro-interfaz");
    expect(
      api.priority,
      "sin prioridades explicitas el reparto depende de como Traefik ordene dos reglas",
    ).toBeGreaterThan(interfaz.priority!);

    // Y el prefijo se quita SOLO en la de la interfaz: `Api.RAIZ` del backend ES la ruta entera,
    // asi que quitarselo dejaria a Spring buscando `/api/v1/...` y contestando 404 a todo.
    expect(api.middlewares ?? []).toEqual([]);
    expect((interfaz.middlewares ?? []).map((m) => m.name)).toEqual(["kamayuk-catastro-quitar-prefijo"]);
    const middleware = ingreso.find((m) => m.kind === "Middleware");
    expect(
      (middleware as unknown as { spec: { stripPrefix: { prefixes: string[] } } }).spec.stripPrefix.prefixes,
    ).toEqual(["/catastro"]);
  });

  /**
   * Su egreso es DNS y NADA MAS — y sobre todo, **no el backend**.
   *
   * No es una promesa: `frontend/nginx.conf` no tiene un solo `proxy_pass` desde este trabajo, y
   * no lo tiene porque el mismo origen se consigue en el ingreso. El dia que alguien vuelva a
   * escribir uno, no funcionara en el cluster aunque funcione en una vista previa.
   */
  it("solo puede salir a DNS: ni a la base, ni al backend, ni a Keycloak", () => {
    const egreso = catastro
      .egreso(ENTORNO)
      .find((p) => p.metadata.name === "kamayuk-catastro-interfaz-egreso");
    expect(egreso, "sin esta politica la interfaz hereda la denegacion del namespace y nada mas").toBeDefined();
    expect(egreso!.spec.podSelector.matchLabels).toEqual({ componente: "catastro-interfaz" });
    const reglas = egreso!.spec.egress ?? [];
    expect(reglas).toHaveLength(1);
    expect((reglas[0]!.ports ?? []).map((p) => `${p.protocol}/${p.port}`).sort()).toEqual([
      "TCP/53",
      "UDP/53",
    ]);
  });

  /** Entrada: el ingreso y nadie mas, y por el puerto del POD y no el del `Service`. */
  it("solo admite entrada desde el ingreso, por el 8080 del contenedor", () => {
    const ingreso = catastro
      .egreso(ENTORNO)
      .find((p) => p.metadata.name === "kamayuk-catastro-interfaz-ingreso");
    expect(ingreso).toBeDefined();
    const reglas = ingreso!.spec.ingress ?? [];
    expect(reglas).toHaveLength(1);
    // 8080 y no 80: una `NetworkPolicy` filtra sobre el puerto del POD, y el mapeo 80 -> 8080 lo
    // deshace el `Service` antes de que la politica mire nada. Con 80 no admitiria nada, y el
    // sintoma seria el navegador esperando delante de un pod sano.
    expect((reglas[0]!.ports ?? []).map((p) => `${p.protocol}/${p.port}`)).toEqual(["TCP/8080"]);
  });

  /**
   * **La mitad de `runAsNonRoot` que este paquete SI puede afirmar.**
   *
   * `SEGURIDAD` fija `runAsNonRoot: true` y **no** fija `runAsUser`, y eso solo es correcto
   * porque la imagen declara su uid EN NUMERO: el kubelet no puede comprobar que un `USER`
   * nombrado no sea root —tendria que leer `/etc/passwd` de una imagen que aun no ha arrancado—
   * asi que se niega y el pod queda en `CreateContainerConfigError`, un fallo que solo aparece
   * AL DESPLEGAR sobre una imagen que localmente funciona.
   *
   * La otra mitad —que `frontend/Dockerfile` diga `USER 101` y no `USER nginx`, y que su
   * `nginx.conf` no reenvie a ningun sitio— la comprueba `frontend/verificaciones/imagen.mjs`,
   * que es donde se puede leer un archivo: **este paquete no declara `@types/node` a proposito**,
   * porque un descriptor es una funcion pura que no lee ni el disco ni el entorno (ADR-0031 §2) y
   * la forma mas barata de que siga siendolo es que ni siquiera pueda.
   */
  it("no fija runAsUser, porque quien declara el uid es la imagen", () => {
    const d = de("Deployment", "kamayuk-catastro-interfaz");
    const contenedor = d?.kind === "Deployment" ? d.spec.template.spec.containers[0] : undefined;
    expect(contenedor?.securityContext?.runAsNonRoot).toBe(true);
    expect(JSON.stringify(contenedor?.securityContext)).not.toContain("runAsUser");
  });
});
