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
#   2. `/catastro/api/v1` lo reenvia nginx al backend por la red del despliegue.
#      Por eso la aplicacion pide a su propio origen y no hay CORS que configurar:
#      el navegador nunca habla con el backend directamente.

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

USER nginx
EXPOSE 8080
