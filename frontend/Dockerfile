# syntax=docker/dockerfile:1
#
# `catastro-web`, compilada y servida como archivos estaticos.
#
# Dos cosas que conviene saber antes de tocarla:
#
#   1. Vite resuelve las variables VITE_* AL COMPILAR. No son configuracion de
#      ejecucion: cambiarlas exige reconstruir la imagen. Por eso
#      `VITE_CATASTRO_API` es una RUTA del mismo origen —`/catastro/api/v1`— y
#      nunca una URL absoluta; un dominio ahi queda horneado en el paquete
#      estatico y, como la misma imagen se despliega en varias municipalidades,
#      las dos mitades del despliegue acaban apuntando a sitios distintos, en
#      verde y sin un solo sintoma.
#   2. `/catastro/api/v1` **ya no lo reenvia este nginx**: lo parte el ingreso, que manda
#      esa ruta al backend y el resto de `/catastro` aqui, dentro del mismo `Host`. La
#      aplicacion sigue pidiendo a su propio origen —que es lo que quita el CORS— y este
#      contenedor no necesita alcanzar al backend por la red. Ver `nginx.conf`.
#   3. El paquete se sirve bajo `/catastro/` (`vite.config.ts`, `base`) y el ingreso quita
#      ese prefijo antes de reenviar, asi que aqui dentro las rutas llegan sin el.

# ---------------------------------------------------------------------------
FROM node:22-alpine AS construccion

WORKDIR /origen

# Primero el manifiesto: asi la capa de dependencias se reutiliza mientras no
# cambien, que es lo que hace que reconstruir tras tocar una pantalla sea rapido.
COPY package.json yarn.lock ./
RUN yarn install --frozen-lockfile

COPY . .

ARG VITE_CATASTRO_API=/catastro/api/v1
# El proxy de datos se apaga en la imagen: lo que se despliega habla con el
# backend o no habla con nadie. Encendido meteria 23 predios INVENTADOS dentro de
# un artefacto que se publica, y con la bandera apagada el trozo entero ni
# siquiera viaja —lo mide `verificaciones/sin-red.mjs` sobre los activos—.
ARG VITE_CATASTRO_PROXY_DE_DATOS=false

ENV VITE_CATASTRO_API=$VITE_CATASTRO_API \
    VITE_CATASTRO_PROXY_DE_DATOS=$VITE_CATASTRO_PROXY_DE_DATOS

# NINGUN token se hornea en la imagen. `VITE_CATASTRO_TOKEN` existe para una vista
# previa local contra el backend, y meterlo aqui dejaria una credencial dentro de
# un artefacto que se publica y se comparte.
RUN yarn build

# ---------------------------------------------------------------------------
# Version fija y no una etiqueta movil: con `alpine` a secas, cada reconstruccion
# se lleva una version distinta sin que nadie lo decida, y un escaneo de imagenes
# no puede decir de que version habla. Actualizarla es la correccion cuando el
# escaneo encuentre algo, no ignorar el hallazgo. Medido contra el registro el
# 2026-09-06: 1.31.5-alpine es la mas nueva publicada.
FROM nginx:1.31.5-alpine AS interfaz

COPY --from=construccion /origen/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Sin root. La imagen base ya trae el usuario "nginx" —lo usan sus procesos
# hijos— pero el master arranca como root por dos motivos que hay que resolver
# los dos: escribe su pid en /var/run, y el entrypoint de la propia imagen
# reescribe este `default.conf` al arrancar para anadir IPv6 si el nodo lo tiene.
# Se redirige el pid a /tmp —escribible por cualquiera— y se le da a "nginx" el
# mismo permiso que root sobre `/var/cache/nginx` y `/etc/nginx`, POR GRUPO y no
# por usuario: asi sigue funcionando si el orquestador decide correrlo con otro
# UID, que es lo que hace un `runAsUser` aleatorio.
RUN sed -i \
        -e '/^user  *nginx;/d' \
        -e 's#^pid .*#pid /tmp/nginx.pid;#' \
        /etc/nginx/nginx.conf \
    && chown -R nginx:0 /var/cache/nginx /etc/nginx \
    && chmod -R g=u /var/cache/nginx /etc/nginx

# EL UID EN NUMERO, no `USER nginx`, y esto no es estilo.
#
# `infrastructure/src/descriptor.ts` le pone a este contenedor `runAsNonRoot: true` —el
# endurecimiento que no admite excepcion— y el kubelet **no puede comprobar** que un usuario
# nombrado no sea root: para eso tendria que leer `/etc/passwd` dentro de una imagen que aun no
# ha arrancado. Se niega, y el pod queda en `CreateContainerConfigError` — un fallo que solo
# aparece AL DESPLEGAR, sobre una imagen que localmente funciona perfectamente.
#
# 101 es el uid del usuario `nginx` en la imagen base, medido sobre `nginx:1.31.5-alpine`. Es el
# mismo numero que usan las interfaces de `rentas` y de `caja`, y por el mismo motivo; las dos
# imagenes del backend de este repositorio ya usaban `USER 10001` y `USER 10002`.
USER 101
EXPOSE 8080
