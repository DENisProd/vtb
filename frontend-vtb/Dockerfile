FROM node:20-alpine AS build

# Включаем pnpm через corepack и закрепляем совместимую версию
RUN corepack enable pnpm && corepack prepare pnpm@9.12.3 --activate

WORKDIR /app

# Копируем зависимости и lock-файл
COPY package.json pnpm-lock.yaml ./

# Устанавливаем зависимости (с devDeps)
RUN pnpm install --frozen-lockfile --prod=false

# Копируем исходники
COPY . .

# Собираем статику
RUN [ -f .env.prod ] && cp .env.prod .env.production || true && pnpm run build