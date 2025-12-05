FROM node:20-slim

WORKDIR /app

# Install build dependencies for better-sqlite3
RUN apt-get update && apt-get install -y python3 make g++ && rm -rf /var/lib/apt/lists/*

COPY package*.json ./
# Install all deps (including dev) so TypeScript is available for build
RUN npm ci

COPY tsconfig.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev

# Create data directory for SQLite
RUN mkdir -p /data
ENV DATABASE_PATH=/data/bizur.db

EXPOSE 8787
CMD ["node", "dist/index.js"]
