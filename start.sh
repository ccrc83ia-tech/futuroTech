#!/usr/bin/env bash
# ============================================================
# FuturoTech AI — Arranque de un comando (Linux / macOS)
# Levanta todo el stack: PostgreSQL+pgvector, Ollama (+ descarga de
# modelos), backend Spring Boot y frontend React tras Nginx.
# ============================================================
set -euo pipefail

if [ ! -f .env ]; then
  echo "No existe .env. Creando desde .env.example..."
  cp .env.example .env
  secret="$(openssl rand -base64 48 2>/dev/null || head -c 36 /dev/urandom | base64)"
  # Reemplaza la línea JWT_SECRET= por una con clave generada.
  tmp="$(mktemp)"
  sed "s|^JWT_SECRET=.*$|JWT_SECRET=${secret}|" .env > "$tmp" && mv "$tmp" .env
  echo "Se generó un JWT_SECRET aleatorio en .env. Revisa DB_PASSWORD antes de producción."
fi

echo "Construyendo y levantando el stack..."
docker compose up -d --build

cat <<'EOF'

Listo. La primera vez, Ollama descarga los modelos (puede tardar varios minutos).
  Frontend:  http://localhost
  Backend:   http://localhost:8080
  Health:    http://localhost:8080/health

Las credenciales iniciales aparecen una sola vez en el log del backend:
  docker compose logs backend | grep Credenciales
EOF
