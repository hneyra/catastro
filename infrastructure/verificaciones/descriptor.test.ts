import { describe, expect, it } from "vitest";
import type { Contenedor, EntornoDelDescriptor, Manifiesto } from "@sgtm/infra-contrato";
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
    namespace: "sgtm-stg",
    emisor: "https://stg.kamayuk.example/keycloak/realms/sgtm",
    jwks: "http://sgtm-stg-identidad.sgtm-stg:8080/keycloak/realms/sgtm/protocol/openid-connect/certs",
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

  it("su egreso es normativa y rentas, y nada mas", () => {
    expect(destinosDeEgreso()).toEqual(["normativa", "rentas"]);
  });
});

/** Los SISTEMAS a los que este descriptor declara egreso. El motor y la identidad no cuentan. */
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
   * Hasta C-14 corria la misma que el `Deployment` con `SGTM_DB_USUARIO=sgtm_owner` y sin perfil:
   * arrancaba el proceso web con las credenciales del unico rol con DDL, y la aplicacion tiene
   * `spring.flyway.enabled: false` a proposito (ARQ-03 §4). O sea que ese Job **no migraba**.
   */
  it("el Job de migracion corre el migrador, con las variables que el migrador lee", () => {
    const contenedores = contenedoresDe(catastro.migracion(ENTORNO));
    expect(contenedores).toHaveLength(1);
    const c = contenedores[0]!;
    expect(c.image).toBe(ENTORNO.imagenDe(`${"catastro"}-migrador`));
    expect(valorDe(c, "SGTM_DB_OWNER_USUARIO")).toBe("sgtm_owner");
    expect(declara(c, "SGTM_DB_OWNER_CLAVE")).toBe(true);
    // La de la APLICACION. El migrador no la lee, y ponerla es lo que hacia que este Job
    // pareciera correcto sin migrar nada.
    expect(declara(c, "SGTM_DB_USUARIO")).toBe(false);
  });

  it("y las dos imagenes son los dos objetivos del Dockerfile", () => {
    expect(catastro.imagenes).toEqual(["catastro", `${"catastro"}-migrador`]);
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
    expect(crones).toHaveLength(1);
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
