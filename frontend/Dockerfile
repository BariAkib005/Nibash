# syntax=docker/dockerfile:1
#
# Nibash web — builds the React app, then serves it from Nginx, which also reverse-proxies /api,
# /media and the /ws chat socket to the backend (plan §Week 6).

# ---------------------------------------------------------------- build
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---------------------------------------------------------------- runtime
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
