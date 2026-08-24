# Angular frontend — built and served via nginx, reverse-proxying the API to the backend
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npx ng build

FROM nginx:1.27-alpine
COPY --from=build /app/dist/rag-ui/browser /usr/share/nginx/html
# nginx's entrypoint runs envsubst on /etc/nginx/templates/*.template,
# so BACKEND_URL can be overridden at runtime (default fits the Helm chart)
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
ENV BACKEND_URL=http://rag-tutorial-backend:8080
EXPOSE 80
