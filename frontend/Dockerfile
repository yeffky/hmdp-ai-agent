FROM node:20-alpine AS build

WORKDIR /workspace
COPY frontend/package*.json ./frontend/
RUN cd frontend && npm ci

COPY frontend ./frontend
ARG VITE_AMAP_JS_KEY=your-amap-js-key
ENV VITE_AMAP_JS_KEY=${VITE_AMAP_JS_KEY}
RUN mkdir -p /workspace/nginx-1.18.0/html/hmdp-app
RUN cd frontend && npm run build

FROM nginx:1.27-alpine
COPY deploy/nginx.docker.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/nginx-1.18.0/html/hmdp-app /usr/share/nginx/html

EXPOSE 8080
