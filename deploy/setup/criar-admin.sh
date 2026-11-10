#!/usr/bin/env bash
# Cria o usuário master (perfil Administrador) do LicitaOne, como o createsuperuser do Django.
#   deploy/setup/criar-admin.sh
# - E-mail novo: cria o administrador.
# - E-mail que já existe: vira administrador, ativo, com a senha nova; desbloqueia e derruba as sessões abertas.
#   Serve também para "esqueci a senha" do único administrador.
# Roda no servidor, da raiz do repositório ou de qualquer lugar, com o compose no ar (o backend já subiu uma vez e
# criou as tabelas). A senha não passa pela linha de comando nem fica no histórico.
set -euo pipefail

cd "$(dirname "$0")/../.."

[ -f .env ] || { echo "Não achei o .env. Rode antes deploy/setup/gerar-env-prod.sh." >&2; exit 1; }
set -a
# shellcheck disable=SC1091
. ./.env
set +a
DB_USER="${POSTGRES_USER:-shemacrm}"
DB_NAME="${POSTGRES_DB:-shemacrm}"

psql_db() { docker compose exec -T postgres psql -X -q -t -A -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" "$@"; }

if [ -z "$(docker compose ps -q --status running postgres)" ]; then
  echo "O banco não está no ar. Suba antes: docker compose up -d --build --wait" >&2
  exit 1
fi
if [ "$(psql_db -c "select to_regclass('public.users') is not null")" != "t" ]; then
  echo "As tabelas ainda não existem: o backend cria na primeira subida. Rode docker compose up -d --wait." >&2
  exit 1
fi

read -r -p "Nome [Administrador]: " NAME
NAME="${NAME:-Administrador}"
while :; do
  read -r -p "E-mail de login: " EMAIL
  EMAIL="$(printf '%s' "$EMAIL" | tr '[:upper:]' '[:lower:]' | xargs)"
  case "$EMAIL" in
    ?*@?*.?*) break ;;
    *) echo "E-mail inválido." >&2 ;;
  esac
done
while :; do
  read -r -s -p "Senha (mínimo 12 caracteres): " PASSWORD; echo
  read -r -s -p "Repita a senha: " PASSWORD2; echo
  LOCAL_PART="${EMAIL%%@*}"
  if [ "$PASSWORD" != "$PASSWORD2" ]; then
    echo "As senhas não conferem." >&2
  elif [ "${#PASSWORD}" -lt 12 ]; then
    echo "A senha precisa ter pelo menos 12 caracteres." >&2
  elif [ "${#LOCAL_PART}" -ge 4 ] && printf '%s' "$PASSWORD" | tr '[:upper:]' '[:lower:]' | grep -qF "$LOCAL_PART"; then
    echo "A senha não pode conter o seu e-mail." >&2
  else
    break
  fi
done

# Hash BCrypt no formato do Spring ({bcrypt}$2a$10$...). Usa o htpasswd do servidor (apache2-utils) ou, sem ele,
# o da imagem oficial do Apache. A senha vai pela entrada padrão.
if command -v htpasswd >/dev/null; then
  RAW="$(printf '%s\n' "$PASSWORD" | htpasswd -niBC 10 admin)"
else
  RAW="$(printf '%s\n' "$PASSWORD" | docker run --rm -i httpd:2.4-alpine htpasswd -niBC 10 admin)"
fi
RAW="$(printf '%s' "$RAW" | tr -d '\r\n')"
RAW="${RAW#admin:}"
HASH="{bcrypt}\$2a\$${RAW#\$2?\$}"
case "$HASH" in
  '{bcrypt}$2a$10$'*) ;;
  *) echo "Não consegui gerar o hash da senha." >&2; exit 1 ;;
esac
unset PASSWORD PASSWORD2

RESULT="$(psql_db -v name="$NAME" -v email="$EMAIL" -v hash="$HASH" <<'SQL'
with saved as (
  insert into users (id, name, email, password_hash, role, active, failed_login_attempts, version,
                     created_at, created_by, updated_at, updated_by)
  values (gen_random_uuid(), :'name', :'email', :'hash', 'ADMIN', true, 0, 0, now(), 'criar-admin', now(), 'criar-admin')
  on conflict (email) do update
    set password_hash = excluded.password_hash, role = 'ADMIN', active = true, failed_login_attempts = 0,
        locked_until = null, version = users.version + 1, updated_at = now(), updated_by = 'criar-admin'
  returning id, (xmax = 0) as created
), revoked as (
  update refresh_tokens set revoked_at = now()
  where user_id = (select id from saved) and revoked_at is null
)
select case when created then 'criado' else 'atualizado' end from saved;
SQL
)"

echo
if [ "$RESULT" = "criado" ]; then
  echo "Usuário master criado: $EMAIL"
else
  echo "Usuário $EMAIL já existia: agora é administrador, ativo, com a senha nova (o nome foi mantido)."
fi
echo "Entre em ${SHEMA_PUBLIC_URL:-http://localhost:14200} e cadastre os outros usuários em Administração → Usuários."
