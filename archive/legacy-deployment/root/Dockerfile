FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
COPY packages/shared-code/package.json packages/shared-code/package.json
COPY packages/web/package.json packages/web/package.json
COPY packages/mobile/package.json packages/mobile/package.json
COPY packages/desktop/package.json packages/desktop/package.json

RUN npm ci

COPY packages/shared-code packages/shared-code
COPY packages/web packages/web

ARG VITE_API_BASE_URL=https://kineticstore.online/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm -w @ebike/web run build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/packages/web/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
