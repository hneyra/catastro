#!/usr/bin/env bash
# Carga el plan de zonificacion de una municipalidad contra un ambiente real (#4),
# corriendo el proceso batch CargarZonificacion (kamayuk-catastro-urbano) como un Job de un
# solo uso.
#
# Es lo que permite contestar «a que zona cae este predio», que es la mitad del territorio
# de la licencia de funcionamiento. La otra mitad -si ese giro es compatible con esa zona-
# es dato de `rentas` (ciiu.zonificacion_compatible) y este guion no la toca: es la
# frontera de ADR-0024.
#
# Y por eso este guion NO exige --es-demostracion ni nada parecido, igual que
# cargar-predios.sh: el plan de desarrollo urbano de una municipalidad no es un dato
# inventado, es su norma, aprobada por ordenanza.
#
# DOS PLANES VIGENTES NO PUEDEN CUBRIR EL MISMO SUELO, y lo rechaza el motor
# (zonificacion_planes_no_se_pisan, V7), no este guion. Asi que cargar un plan nuevo SIN
# haber cerrado el anterior no revienta la corrida: rechaza fila a fila las zonas que se
# pisan, y lo dice en el registro. Leer ese registro es parte del procedimiento -si sale
# «N rechazada(s)», lo que falta es cerrar el plan anterior con su vigencia_hasta la
# vispera, no volver a lanzar esto-.
#
# El CSV lleva una fila por zona: plan, ordenanza, codigo, nombre, las dos fechas de
# vigencia, el poligono en WKT y detras tantos parametros urbanisticos como la ordenanza
# declare. `infra/carga-de-datos/ejemplos/zonificacion.csv` ensena la forma; sus poligonos
# son inventados y sus ordenanzas dicen DEMO, a proposito.
#
#
# ESTE GUION VIVE EN EL REPOSITORIO DE SU PROCESO, y no es una preferencia (C-6): un guion
# lanzado contra la imagen de otro sistema arranca la aplicacion, NO CARGA NADA y sale con
# codigo 0 -medido, sin un solo aviso-.
#   uso: cargar-zonificacion.sh --ambiente stg|prod --municipalidad-id N --archivo zonificacion.csv
#        [--namespace kamayuk-stg] [--observacion "..."]
#
# Requiere: kubectl con el tunel al API del ambiente ya abierto (ver infra/README.md),
# y el mismo kubeconfig que usa pulumi up.
set -euo pipefail

AMBIENTE=""
MUNICIPALIDAD_ID=""
ARCHIVO=""
NAMESPACE=""
OBSERVACION="Carga del plan de zonificacion aprobado por ordenanza (#4)"
while [ $# -gt 0 ]; do
    case "$1" in
        --ambiente) AMBIENTE=${2:?falta el valor de --ambiente}; shift 2 ;;
        --municipalidad-id) MUNICIPALIDAD_ID=${2:?falta el valor de --municipalidad-id}; shift 2 ;;
        --archivo) ARCHIVO=${2:?falta el valor de --archivo}; shift 2 ;;
        --namespace) NAMESPACE=${2:?falta el valor de --namespace}; shift 2 ;;
        --observacion) OBSERVACION=${2:?falta el valor de --observacion}; shift 2 ;;
        *) echo "Opcion desconocida: $1" >&2; exit 2 ;;
    esac
done
[ -n "$AMBIENTE" ] || { echo "Falta --ambiente (stg o prod)." >&2; exit 2; }
[ -n "$MUNICIPALIDAD_ID" ] || { echo "Falta --municipalidad-id." >&2; exit 2; }
[ -n "$ARCHIVO" ] || { echo "Falta --archivo (el CSV con las zonas del plan)." >&2; exit 2; }
[ -f "$ARCHIVO" ] || { echo "No existe el archivo: $ARCHIVO" >&2; exit 2; }
NAMESPACE=${NAMESPACE:-kamayuk-$AMBIENTE}

SUFIJO=$(date +%s)
RECURSO="kamayuk-${AMBIENTE}-carga-zonificacion-${SUFIJO}"

IMAGEN=$(kubectl -n "$NAMESPACE" get deployment "kamayuk-${AMBIENTE}-aplicacion" \
    -o jsonpath='{.spec.template.spec.containers[0].image}')
[ -n "$IMAGEN" ] || {
    echo "No se pudo leer la imagen de kamayuk-${AMBIENTE}-aplicacion en $NAMESPACE" >&2
    exit 1
}
echo "Imagen desplegada: $IMAGEN"

echo "Creando ConfigMap $RECURSO con $ARCHIVO..."
kubectl -n "$NAMESPACE" create configmap "$RECURSO" --from-file=zonificacion.csv="$ARCHIVO"

cleanup() {
    kubectl -n "$NAMESPACE" delete configmap "$RECURSO" --ignore-not-found >/dev/null
}
trap cleanup EXIT

cat <<EOF | kubectl -n "$NAMESPACE" apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: $RECURSO
  labels:
    proyecto: sgtm
    ambiente: $AMBIENTE
    componente: carga-zonificacion
spec:
  backoffLimit: 0
  template:
    metadata:
      labels:
        proyecto: sgtm
        ambiente: $AMBIENTE
        componente: carga-zonificacion
        # NetworkPolicy "permitir-ingreso-postgres" (infra/componentes/Red.ts) solo deja
        # pasar al puerto 5432 a pods con app en {aplicacion, identidad, migracion,
        # implantacion, lote, respaldo}: "lote" es la etiqueta generica para un Job de
        # un solo uso que necesita hablar con la base. Con "carga-zonificacion" el pod arranca
        # pero la conexion cae con "Connection refused" -denegacion por omision
        # funcionando como se disenio, no un error de credenciales.
        app: lote
    spec:
      restartPolicy: Never
      priorityClassName: kamayuk-${AMBIENTE}-prioridad-lote
      containers:
        - name: carga-zonificacion
          image: $IMAGEN
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: batch
            - name: KAMAYUK_DB_URL
              value: jdbc:postgresql://kamayuk-${AMBIENTE}-postgres:5432/sgtm
            - name: KAMAYUK_DB_USUARIO
              value: kamayuk_app
            - name: KAMAYUK_DB_CLAVE
              valueFrom:
                secretKeyRef:
                  name: kamayuk-${AMBIENTE}-postgres-app
                  key: clave-app
            - name: KAMAYUK_CARGAZONIFICACION_MUNICIPALIDADID
              value: "$MUNICIPALIDAD_ID"
            - name: KAMAYUK_CARGAZONIFICACION_ARCHIVO
              value: /datos/zonificacion.csv
            - name: KAMAYUK_CARGAZONIFICACION_USUARIODELPROCESO
              value: carga-zonificacion
            - name: KAMAYUK_CARGAZONIFICACION_OBSERVACION
              value: "$OBSERVACION"
          volumeMounts:
            - name: datos
              mountPath: /datos
              readOnly: true
          securityContext:
            allowPrivilegeEscalation: false
            capabilities:
              drop: ["ALL"]
            runAsNonRoot: true
          resources:
            requests: { cpu: "250m", memory: "256Mi" }
            limits: { cpu: "1", memory: "512Mi" }
      volumes:
        - name: datos
          configMap:
            name: $RECURSO
EOF

echo "Esperando a que $RECURSO termine..."
LIMITE=$((SECONDS + 300))
while true; do
    completo=$(kubectl -n "$NAMESPACE" get job "$RECURSO" \
        -o jsonpath='{.status.conditions[?(@.type=="Complete")].status}')
    fallido=$(kubectl -n "$NAMESPACE" get job "$RECURSO" \
        -o jsonpath='{.status.conditions[?(@.type=="Failed")].status}')
    if [ "$completo" = "True" ]; then
        echo "Completado."
        break
    fi
    if [ "$fallido" = "True" ]; then
        echo "El Job fallo. Registro:" >&2
        kubectl -n "$NAMESPACE" logs "job/$RECURSO" --tail=500 >&2
        exit 1
    fi
    [ "$SECONDS" -lt "$LIMITE" ] || {
        echo "Se agoto el tiempo de espera (300s)." >&2
        exit 1
    }
    sleep 3
done

kubectl -n "$NAMESPACE" logs "job/$RECURSO" --tail=500
