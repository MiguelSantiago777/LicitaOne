# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# nginx sem root, ouvindo na 8080: serve o SPA e repassa /api para o backend (mesma origem = cookie SameSite=Strict)
FROM nginxinc/nginx-unprivileged:1.30-alpine
# Correções de segurança do Alpine publicadas depois da imagem base (Trivy no CI); depois volta ao usuário sem root
USER root
RUN apk upgrade --no-cache
USER 101
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.inc /etc/nginx/security-headers.inc
COPY --from=build /src/dist/frontend/browser /usr/share/nginx/html
EXPOSE 8080
