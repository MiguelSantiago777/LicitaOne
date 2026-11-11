#!/usr/bin/env bash
# Teste de ponta a ponta do backup (Fase 6): faz o backup, APAGA o ambiente (docker compose down -v), sobe vazio,
# restaura e confere que o banco e os arquivos voltaram iguais (contagens e SHA-256 de cada documento).
# ATENÇÃO: apaga os volumes do compose. Use só em dev ou num ambiente de ensaio.
#   deploy/backup/test-restore.sh
set -euo pipefail

cd "$(dirname "$0")/../.."
set -a
# shellcheck disable=SC1091
[ -f .env ] && . ./.env
set +a
PROJECT="${COMPOSE_PROJECT_NAME:-shemacrm}"
BUCKET="${S3_BUCKET:-shemacrm-files}"

sql() {
  docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1' <<<"$1"
}

# Retrato do que precisa voltar: linhas das tabelas principais e o hash de cada versão de documento
snapshot() {
  sql "select 'licitacoes=' || count(*) from tenders
       union all select 'contratos=' || count(*) from contracts
       union all select 'pedidos=' || count(*) from contract_orders
       union all select 'atas=' || count(*) from price_records
       union all select 'tarefas=' || count(*) from tasks
       union all select 'alertas=' || count(*) from alerts
       union all select 'usuarios=' || count(*) from users
       union all select 'versoes=' || count(*) from document_versions
       union all select 'migracoes=' || count(*) from flyway_schema_history;"
}

# SHA-256 de cada arquivo no bucket, comparado com o que o banco guardou no envio
check_files() {
  local bad=0
  while IFS='|' read -r key sha; do
    [ -z "$key" ] && continue
    got=$(MSYS_NO_PATHCONV=1 docker run --rm --network "${PROJECT}_default" \
      -e RCLONE_CONFIG_S3_TYPE=s3 -e RCLONE_CONFIG_S3_PROVIDER=SeaweedFS \
      -e RCLONE_CONFIG_S3_ENDPOINT=http://s3:8333 \
      -e RCLONE_CONFIG_S3_ACCESS_KEY_ID="${S3_ACCESS_KEY:-shemacrm}" \
      -e RCLONE_CONFIG_S3_SECRET_ACCESS_KEY="$S3_SECRET_KEY" \
      rclone/rclone:1.75.2 cat --quiet "s3:$BUCKET/$key" | sha256sum | cut -d' ' -f1)
    if [ "$got" != "$sha" ]; then
      echo "  arquivo diferente: $key" >&2
      bad=$((bad + 1))
    fi
  done < <(sql "select storage_key || '|' || sha256 from document_versions order by storage_key;")
  return $bad
}

echo "1/5 Retrato antes do backup"
before=$(snapshot)
echo "$before" | tr '\n' ' '; echo

echo "2/5 Backup"
out=$(deploy/backup/backup.sh backups)
echo "$out"
dir=$(echo "$out" | sed -n 's/^Backup em //p')

echo "3/5 Apagando tudo (down -v) e subindo vazio"
docker compose down -v
docker compose up -d --wait

echo "4/5 Restaurando $dir"
deploy/backup/restore.sh "$dir"

echo "5/5 Conferindo"
after=$(snapshot)
if [ "$before" != "$after" ]; then
  echo "FALHOU: o banco restaurado é diferente" >&2
  diff <(echo "$before") <(echo "$after") >&2 || true
  exit 1
fi
check_files || { echo "FALHOU: arquivos diferentes no bucket" >&2; exit 1; }
versions=$(echo "$after" | grep '^versoes=' | cut -d= -f2)
echo "OK: banco igual ($(echo "$after" | tr '\n' ' ')) e $versions arquivos de documento com o SHA-256 original."
