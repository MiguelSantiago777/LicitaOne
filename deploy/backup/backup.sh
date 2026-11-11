#!/usr/bin/env bash
# Backup do LicitaOne: banco (pg_dump, formato custom) e arquivos (cópia do bucket S3), numa pasta com data e hora.
#   deploy/backup/backup.sh [pasta-de-destino]        (padrão: ./backups)
# Roda da raiz do repositório, com o compose no ar. Procedimento completo em docs/operacao/runbook.md.
set -euo pipefail

cd "$(dirname "$0")/../.."
set -a
# shellcheck disable=SC1091
[ -f .env ] && . ./.env
set +a

DEST_ROOT="${1:-backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
DEST="$DEST_ROOT/$STAMP"
PROJECT="${COMPOSE_PROJECT_NAME:-shemacrm}"
NETWORK="${BACKUP_NETWORK:-${PROJECT}_default}"
S3_ENDPOINT_URL="${BACKUP_S3_ENDPOINT:-http://s3:8333}"
BUCKET="${S3_BUCKET:-shemacrm-files}"
RCLONE_IMAGE="rclone/rclone:1.75.2"

mkdir -p "$DEST/files"
echo "Backup em $DEST"

# 1. Banco: dump consistente (uma transação só), sem dono nem permissões, para restaurar em outro servidor
docker compose exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges -f /tmp/licitaone.dump'
docker compose cp postgres:/tmp/licitaone.dump "$DEST/db.dump"
docker compose exec -T postgres rm -f /tmp/licitaone.dump

# 2. Arquivos: cópia do bucket (documentos, planilhas e relatórios de importação)
MSYS_NO_PATHCONV=1 docker run --rm --network "$NETWORK" \
  -v "$(cd "$DEST" && pwd -W 2>/dev/null || pwd)/files:/backup" \
  -e RCLONE_CONFIG_S3_TYPE=s3 -e RCLONE_CONFIG_S3_PROVIDER=SeaweedFS \
  -e RCLONE_CONFIG_S3_ENDPOINT="$S3_ENDPOINT_URL" \
  -e RCLONE_CONFIG_S3_ACCESS_KEY_ID="${S3_ACCESS_KEY:-shemacrm}" \
  -e RCLONE_CONFIG_S3_SECRET_ACCESS_KEY="${S3_SECRET_KEY:?defina S3_SECRET_KEY no .env}" \
  "$RCLONE_IMAGE" copy "s3:$BUCKET" /backup --quiet

# 3. Manifesto: o que tem no backup, para conferir na restauração
FILES=$(find "$DEST/files" -type f | wc -l | tr -d ' ')
{
  echo "criado_em=$STAMP"
  echo "bucket=$BUCKET"
  echo "arquivos=$FILES"
  echo "sha256_db=$(sha256sum "$DEST/db.dump" | cut -d' ' -f1)"
} > "$DEST/manifest.txt"

echo "Pronto: banco $(du -h "$DEST/db.dump" | cut -f1), $FILES arquivos."
echo "Guarde a pasta fora deste servidor (e cifrada): ela tem dados pessoais e documentos."
