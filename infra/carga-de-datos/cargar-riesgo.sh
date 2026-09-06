#!/usr/bin/env bash
# Carga la carta de peligro (CENEPRED) y la faja marginal (ANA) de una municipalidad
# contra un ambiente real (#5), corriendo el proceso batch CargarRiesgo
# (kamayuk-catastro-grd) como un Job de un solo uso.
#
# UN ARCHIVO CON DOS CAPAS. La primera columna del CSV dice si la fila es PELIGRO o
# FAJA_MARGINAL, asi que este guion carga las dos tablas de una vez. Es como llega el
# dato -una exportacion del SIG de la municipalidad- y por eso hay UN proceso, UNA
# propiedad y UN guion; con tres juegos habria que acordarse del orden.
#
# ESTE GUION NO EXIGE --es-demostracion, y es la misma decision que tomo cargar-predios.sh:
# la carta de peligro y la faja marginal NO SON DATOS INVENTADOS, son actos de dos
# organismos del Estado sobre el territorio de esa municipalidad. Exigir es_demostracion
# dejaria a una instalacion de verdad sin forma de cargarlos, que es el hueco que #430
# encontro para area y caja.
#
# LO QUE ESTE GUION NO PUEDE COMPROBAR, dicho antes de que alguien lo descubra: que los
# poligonos caigan sobre el distrito. El CSV se carga entero aunque este en otro lado del
# mundo; lo unico que la base rechaza es un WKT que no sea un MULTIPOLYGON valido en
# WGS84. Conviene mirar el rectangulo que envuelve la capa ANTES de correr esto -si no es
# el del distrito, la carta no es la que se creia-, igual que cargar-predios.sh manda leer
# el resumen.txt del plano.
#
# UNA FILA RECHAZADA NO ABORTA LA CORRIDA: sale en el log con su numero de linea y su
# motivo, y las demas entran. Al final se imprimen las tres cifras -leidas, nuevas,
# rechazadas-. Un Job que sale con codigo 0 habiendo cargado cero poligonos es el modo de
# fallo que C-6 midio, asi que hay que LEER esa linea y no solo el estado del Job.
#
# ESTE GUION VIVE EN EL REPOSITORIO DE SU PROCESO, y no es una preferencia (C-6): un guion
# lanzado contra la imagen de otro sistema arranca la aplicacion, NO CARGA NADA y sale con
# codigo 0 -medido, sin un solo aviso-.
#   uso: cargar-riesgo.sh --ambiente stg|prod --municipalidad-id N --archivo riesgo.csv
#        [--namespace kamayuk-stg] [--observacion "..."]
#
# Requiere: kubectl con el tunel al API del ambiente ya abierto (ver infra/README.md),
# y el mismo kubeconfig que usa pulumi up.
set -euo pipefail

AMBIENTE=""
MUNICIPALIDAD_ID=""
ARCHIVO=""
NAMESPACE=""
OBSERVACION="Carga de la carta de riesgo y faja marginal (#5)"
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
[ -n "$ARCHIVO" ] || { echo "Falta --archivo (el CSV de dos capas: PELIGRO y FAJA_MARGINAL)." >&2; exit 2; }
[ -f "$ARCHIVO" ] || { echo "No existe el archivo: $ARCHIVO" >&2; exit 2; }
NAMESPACE=${NAMESPACE:-kamayuk-$AMBIENTE}

SUFIJO=$(date +%s)
RECURSO="kamayuk-${AMBIENTE}-carga-riesgo-${SUFIJO}"

IMAGEN=$(kubectl -n "$NAMESPACE" get deployment "kamayuk-${AMBIENTE}-aplicacion" \
    -o jsonpath='{.spec.template.spec.containers[0].image}')
[ -n "$IMAGEN" ] || {
    echo "No se pudo leer la imagen de kamayuk-${AMBIENTE}-aplicacion en $NAMESPACE" >&2
    exit 1
}
echo "Imagen desplegada: $IMAGEN"

echo "Creando ConfigMap $RECURSO con $ARCHIVO..."
kubectl -n "$NAMESPACE" create configmap "$RECURSO" --from-file=riesgo.csv="$ARCHIVO"

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
    componente: carga-riesgo
spec:
  backoffLimit: 0
  template:
    metadata:
      labels:
        proyecto: sgtm
        ambiente: $AMBIENTE
        componente: carga-riesgo
        # NetworkPolicy "permitir-ingreso-postgres" (infra/componentes/Red.ts) solo deja
        # pasar al puerto 5432 a pods con app en {aplicacion, identidad, migracion,
        # implantacion, lote, respaldo}: "lote" es la etiqueta generica para un Job de
        # un solo uso que necesita hablar con la base. Con "carga-riesgo" el pod arranca
        # pero la conexion cae con "Connection refused" -denegacion por omision
        # funcionando como se disenio, no un error de credenciales.
        app: lote
    spec:
      restartPolicy: Never
      priorityClassName: kamayuk-${AMBIENTE}-prioridad-lote
      containers:
        - name: carga-riesgo
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
            - name: KAMAYUK_CARGARIESGO_MUNICIPALIDADID
              value: "$MUNICIPALIDAD_ID"
            - name: KAMAYUK_CARGARIESGO_ARCHIVO
              value: /datos/riesgo.csv
            - name: KAMAYUK_CARGARIESGO_USUARIODELPROCESO
              value: carga-riesgo
            - name: KAMAYUK_CARGARIESGO_OBSERVACION
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
