#!/usr/bin/env bash
# Gera o .env.prod do LicitaOne a partir do .env.prod.example:
# - pergunta o endereço público, o administrador inicial e o SMTP;
# - sorteia as senhas do banco, do RabbitMQ, do S3 e do administrador, e gera a chave JWT (RSA 2048);
# - grava com permissão só para o dono (600) e cria o link .env -> .env.prod, para o `docker compose` e os scripts
#   de backup lerem o arquivo sem flags.
#   deploy/setup/gerar-env-prod.sh
# Roda no servidor, da raiz do repositório ou de qualquer lugar. Não sobrescreve um .env.prod que já exista.
# Procedimento completo em docs/operacao/runbook.md (seção 2).
set -euo pipefail

cd "$(dirname "$0")/../.."
umask 077

TARGET=.env.prod
EXAMPLE=.env.prod.example

command -v openssl >/dev/null || { echo "Precisa do openssl (apt install openssl)." >&2; exit 1; }
[ -f "$EXAMPLE" ] || { echo "Não achei $EXAMPLE: rode dentro do repositório." >&2; exit 1; }
if [ -e "$TARGET" ]; then
  echo "$TARGET já existe. Para gerar de novo, mova ou apague o atual (as senhas do banco já criado ficam nele)." >&2
  exit 1
fi
if [ -e .env ] && [ ! -L .env ]; then
  echo "Já existe um .env comum (não é link). Mova ou apague antes: em produção ele deve apontar para o $TARGET." >&2
  exit 1
fi

ask() { # ask "Pergunta" [padrão] -> resposta
  local prompt="$1" default="${2:-}" answer
  if [ -n "$default" ]; then
    read -r -p "$prompt [$default]: " answer
    echo "${answer:-$default}"
  else
    while :; do
      read -r -p "$prompt: " answer
      [ -n "$answer" ] && { echo "$answer"; return; }
    done
  fi
}

ask_secret() { # ask_secret "Pergunta" -> resposta (pode ficar em branco)
  local answer
  read -r -s -p "$1: " answer
  echo >&2
  echo "$answer"
}

random_hex() { openssl rand -hex "$1"; }

# Senha do admin: 20 caracteres de letras e números (passa na política de 12+ caracteres)
random_password() { openssl rand -base64 30 | tr -dc 'A-Za-z0-9' | head -c 20; }

echo "== LicitaOne: gerar $TARGET =="
echo

PUBLIC_URL="$(ask "Endereço público, com https (ex.: https://licitaone.suaempresa.com.br)")"
case "$PUBLIC_URL" in
  https://*) ;;
  *) echo "Aviso: o endereço não começa com https://. O cookie de sessão é Secure e só funciona em HTTPS." >&2 ;;
esac
PUBLIC_URL="${PUBLIC_URL%/}"

echo
echo "-- Administrador inicial (o \"superusuário\") --"
ADMIN_NAME="$(ask "Nome" "Administrador")"
ADMIN_EMAIL="$(ask "E-mail de login")"
ADMIN_PASSWORD="$(ask_secret "Senha (Enter para gerar uma aleatória)")"
GENERATED_ADMIN_PASSWORD=false
if [ -z "$ADMIN_PASSWORD" ]; then
  ADMIN_PASSWORD="$(random_password)"
  GENERATED_ADMIN_PASSWORD=true
elif [ "${#ADMIN_PASSWORD}" -lt 12 ]; then
  echo "A senha precisa ter pelo menos 12 caracteres." >&2
  exit 1
fi

echo
echo "-- SMTP dos avisos por e-mail (porta 587 com STARTTLS) --"
MAIL_HOST="$(ask "Servidor SMTP (ex.: smtp.gmail.com)")"
MAIL_PORT="$(ask "Porta" "587")"
MAIL_USER="$(ask "Usuário SMTP" "$ADMIN_EMAIL")"
MAIL_PASSWORD="$(ask_secret "Senha SMTP")"
MAIL_FROM="$(ask "Remetente dos avisos" "LicitaOne <$MAIL_USER>")"

echo
echo "-- IA (opcional: Enter deixa desligada; dá para pôr depois no $TARGET) --"
ANTHROPIC_KEY="$(ask_secret "Chave da API da Anthropic")"

for value in "$PUBLIC_URL" "$ADMIN_NAME" "$ADMIN_EMAIL" "$ADMIN_PASSWORD" "$MAIL_HOST" "$MAIL_USER" "$MAIL_PASSWORD" \
             "$MAIL_FROM" "$ANTHROPIC_KEY"; do
  case "$value" in
    *\'*) echo "Os valores não podem ter aspas simples ('). Tente de novo." >&2; exit 1 ;;
  esac
done

echo
echo "Gerando senhas e a chave JWT..."
JWT_KEY="$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null)"

# Valores entre aspas simples: o compose e o bash leem literalmente (sem expandir $). A chave JWT vai entre aspas
# duplas, porque tem várias linhas (o PEM não tem $ nem aspas).
q() { printf "'%s'" "$1"; }

export V_SHEMA_PUBLIC_URL="$(q "$PUBLIC_URL")"
export V_POSTGRES_PASSWORD="$(random_hex 24)"
export V_RABBITMQ_PASSWORD="$(random_hex 24)"
export V_S3_SECRET_KEY="$(random_hex 24)"
export V_SHEMA_ADMIN_NAME="$(q "$ADMIN_NAME")"
export V_SHEMA_ADMIN_EMAIL="$(q "$ADMIN_EMAIL")"
export V_SHEMA_ADMIN_PASSWORD="$(q "$ADMIN_PASSWORD")"
export V_SHEMA_JWT_PRIVATE_KEY="\"$JWT_KEY\""
export V_SHEMA_JWT_KEY_ID="prod-$(date +%Y-%m)"
export V_ANTHROPIC_API_KEY="$(q "$ANTHROPIC_KEY")"
export V_MAIL_HOST="$(q "$MAIL_HOST")"
export V_MAIL_PORT="$MAIL_PORT"
export V_MAIL_USER="$(q "$MAIL_USER")"
export V_MAIL_PASSWORD="$(q "$MAIL_PASSWORD")"
export V_SHEMA_ALERTS_MAIL_FROM="$(q "$MAIL_FROM")"

# Troca só as linhas KEY= que têm valor gerado; o resto (comentários, dados da empresa) vem do exemplo.
awk '
  match($0, /^[A-Z_0-9]+=/) {
    key = substr($0, 1, RLENGTH - 1)
    if (("V_" key) in ENVIRON) { print key "=" ENVIRON["V_" key]; next }
  }
  { print }
' "$EXAMPLE" > "$TARGET.tmp"
mv "$TARGET.tmp" "$TARGET"
chmod 600 "$TARGET"
ln -sfn "$TARGET" .env

# Confere que o bash lê o arquivo (o backup.sh faz isso)
( set -a; . "./$TARGET" ) || { echo "O $TARGET gerado não abre no bash: confira as aspas." >&2; exit 1; }

echo
echo "Pronto: $TARGET criado (permissão 600) e .env -> $TARGET."
echo
echo "  Administrador: $ADMIN_EMAIL"
if [ "$GENERATED_ADMIN_PASSWORD" = true ]; then
  echo "  Senha inicial: $ADMIN_PASSWORD"
  echo "  (anote agora; troque no primeiro acesso, pelo ícone de engrenagem no rodapé do menu)"
fi
echo
echo "Próximos passos:"
echo "  1. Preencha os dados da empresa (SHEMA_COMPANY_*) no $TARGET, para as cartas do kit de carona."
echo "  2. Suba:  docker compose up -d --build --wait"
echo "  3. Configure o HTTPS no proxy (Caddy) apontando para 127.0.0.1:14200 (runbook, seção 2)."
echo "  4. Guarde uma cópia do $TARGET num cofre de senhas: sem ele, não dá para restaurar o banco nem os tokens."
