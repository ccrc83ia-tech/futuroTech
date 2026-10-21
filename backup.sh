#!/usr/bin/env bash
# ============================================================
# FuturoTech AI — Backup de PostgreSQL (incluye datos pgvector)
# Dump comprimido en ./backups/ con marca de tiempo + retención.
# Requiere el stack levantado (docker compose up).
# Uso:  ./backup.sh [N_RETENCION]   (por defecto 14)
# ============================================================
set -euo pipefail
KEEP="${1:-14}"

DB_NAME="legal_engine"; DB_USER="legal"
if [ -f .env ]; then
  set -a; # shellcheck disable=SC1091
  . ./.env 2>/dev/null || true
  set +a
fi

mkdir -p backups
stamp="$(date +%Y%m%d-%H%M%S)"
file="backups/${DB_NAME}-${stamp}.dump"

echo "Respaldando base '${DB_NAME}' (usuario '${DB_USER}')..."
docker compose exec -T db pg_dump -U "${DB_USER}" -d "${DB_NAME}" -Fc > "${file}"
echo "Backup creado: ${file}"

# Retención: conservar solo los $KEEP más recientes.
ls -1t "backups/${DB_NAME}-"*.dump 2>/dev/null | tail -n +"$((KEEP + 1))" | while read -r old; do
  rm -f "${old}"
  echo "Backup antiguo eliminado: ${old}"
done
