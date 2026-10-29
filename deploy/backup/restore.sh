#!/usr/bin/env bash
# Restaura um backup feito por backup.sh: substitui o banco e devolve os arquivos ao bucket.
#   deploy/backup/restore.sh backups/20261029-150000
# O backend fica parado durante a restauração (ninguém grava no meio). Procedimento em docs/operacao/runbook.md.
set -euo pipefail

cd "$(dirname "$0")/../.."
set -a
# shellcheck disable=SC1091
[ -f .env ] && . ./.env
set +a

SRC="${1:?informe a pasta do backup, ex.: backups/20261029-150000}"
[ -f "$SRC/db.dump" ] && [ -f "$SRC/manifest.txt" ] || { echo "Backup incompleto em $SRC" >&2; exit 1; }

PROJECT="${COMPOSE_PROJECT_NAME:-shemacrm}"
NETWORK="${BACKUP_NETWORK:-${PROJECT}_default}"
S3_ENDPOINT_URL="${BACKUP_S3_ENDPOINT:-http://s3:8333}"
BUCKET="${S3_BUCKET:-shemacrm-files}"
RCLONE_IMAGE="rclone/rclone:1.75.2"

expected=$(grep '^sha256_db=' "$SRC/manifest.txt" | cut -d= -f2)
actual=$(sha256sum "$SRC/db.dump" | cut -d' ' -f1)
[ "$expected" = "$actual" ] || { echo "O dump não confere com o manifesto (arquivo corrompido?)" >&2; exit 1; }

echo "Parando o backend e o frontend"
docker compose stop frontend backend

echo "Restaurando o banco"
docker compose up -d postgres
docker compose cp "$SRC/db.dump" postgres:/tmp/licitaone.dump
# Recria o banco do zero: o dump traz as tabelas, os dados e o histórico do Flyway
docker compose exec -T postgres sh -c '
  set -e
  psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
    -c "drop database if exists \"$POSTGRES_DB\" with (force)" \
    -c "create database \"$POSTGRES_DB\" owner \"$POSTGRES_USER\""
  pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges --exit-on-error /tmp/licitaone.dump
  rm -f /tmp/licitaone.dump'

echo "Devolvendo os arquivos ao bucket"
docker compose up -d s3
MSYS_NO_PATHCONV=1 docker run --rm --network "$NETWORK" \
  -v "$(cd "$SRC" && pwd -W 2>/dev/null || pwd)/files:/backup:ro" \
  -e RCLONE_CONFIG_S3_TYPE=s3 -e RCLONE_CONFIG_S3_PROVIDER=SeaweedFS \
  -e RCLONE_CONFIG_S3_ENDPOINT="$S3_ENDPOINT_URL" \
  -e RCLONE_CONFIG_S3_ACCESS_KEY_ID="${S3_ACCESS_KEY:-shemacrm}" \
  -e RCLONE_CONFIG_S3_SECRET_ACCESS_KEY="${S3_SECRET_KEY:?defina S3_SECRET_KEY no .env}" \
  "$RCLONE_IMAGE" sync /backup "s3:$BUCKET" --quiet

echo "Subindo o sistema"
docker compose up -d --wait backend frontend
echo "Restaurado de $SRC ($(grep '^arquivos=' "$SRC/manifest.txt" | cut -d= -f2) arquivos)."
