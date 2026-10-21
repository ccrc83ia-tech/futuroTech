#!/usr/bin/env bash
# ============================================================
# FuturoTech AI — Restauración de PostgreSQL desde un dump
# ADVERTENCIA: sobrescribe los datos actuales de la base.
# Requiere el stack levantado (docker compose up).
# Uso:  ./restore.sh backups/legal_engine-YYYYMMDD-HHmmss.dump
# ============================================================
set -euo pipefail
FILE="${1:?Uso: ./restore.sh <archivo.dump>}"
[ -f "${FILE}" ] || { echo "No existe el archivo: ${FILE}"; exit 1; }

DB_NAME="legal_engine"; DB_USER="legal"
if [ -f .env ]; then
  set -a; . ./.env 2>/dev/null || true; set +a
fi

echo "ADVERTENCIA: se sobrescribirán los datos de '${DB_NAME}'."
read -r -p "Escribe 'si' para continuar: " confirm
[ "${confirm}" = "si" ] || { echo "Cancelado."; exit 0; }

echo "Restaurando '${DB_NAME}' desde ${FILE}..."
docker compose exec -T db pg_restore -U "${DB_USER}" -d "${DB_NAME}" --clean --if-exists --no-owner < "${FILE}"
echo "Restauración completada."
