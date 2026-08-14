# syntax=docker/dockerfile:1
# Day 11 — frontend: Node build stage -> Nginx runtime serving the static SPA.
FROM node:20-alpine AS build
WORKDIR /app
# Install dependencies first for layer caching.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Day 11 decision: build with an EMPTY VITE_API_BASE_URL so the bundle issues
# relative /api calls, which the Nginx container reverse-proxies to the
# api-gateway service. No gateway URL is baked into the bundle — the image
# works on any host/port. (The app code treats an empty value as "relative".)
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
# Day 16: Web Push — the VAPID public key baked into the bundle. Empty
# (default) → the push toggle is hidden in the notification bell.
ARG VITE_VAPID_PUBLIC_KEY=""
ENV VITE_VAPID_PUBLIC_KEY=$VITE_VAPID_PUBLIC_KEY
RUN npm run build

FROM nginx:1.27-alpine
# SPA fallback + /api reverse-proxy to the api-gateway container.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -qO- http://127.0.0.1/ > /dev/null 2>&1 || exit 1
