# ==============================================================================
# LifeInbox V2 Frontend - Production Dockerfile
# Multi-stage build: Node.js 20 build stage -> Nginx Alpine static server
# ==============================================================================

# Stage 1: Build production static distribution
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Replace default Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy compiled frontend assets from builder
COPY --from=builder /app/dist .

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
