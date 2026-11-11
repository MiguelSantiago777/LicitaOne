#!/usr/bin/env bash
# Gera o .env.prod do LicitaOne a partir do .env.prod.example:
# - pergunta o endereço público, quem faz o HTTPS (Caddy no Docker ou Apache do servidor), o SMTP e a IA (Gemini ou Claude, com a chave);
# - sorteia as senhas do banco, do RabbitMQ e do S3, e gera a chave JWT (RSA 2048);
# - grava com permissão só para o dono (600) e cria o link .env -> .env.prod, para o `docker compose` e os scripts
#   de backup lerem o arquivo sem flags.
#   deploy/setup/gerar-env-prod.sh
# Roda no servidor, da raiz do repositório ou de qualquer lugar. Não sobrescreve um .env.prod que já exista.
# O usuário master é criado depois de subir, com deploy/setup/criar-admin.sh.
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

echo "== LicitaOne: gerar $TARGET =="
echo

PUBLIC_URL="$(ask "Endereço público, com https (ex.: https://licitaone.suaempresa.com.br)")"
case "$PUBLIC_URL" in
  https://*) ;;
  *) echo "Aviso: o endereço não começa com https://. O cookie de sessão é Secure e só funciona em HTTPS." >&2 ;;
esac
PUBLIC_URL="${PUBLIC_URL%/}"

echo
echo "-- HTTPS (quem atende nas portas 80 e 443) --"
echo "  1) Caddy no Docker: servidor só do LicitaOne (VM na nuvem). Tira e renova o certificado sozinho."
echo "  2) Apache que já existe no servidor (modelo em deploy/apache/licitaone.conf, certificado pelo certbot)"
PROXY_CHOICE="$(ask "Escolha" "1")"
case "$PROXY_CHOICE" in
  1) COMPOSE_FILES=docker-compose.yml:docker-compose.prod.yml:docker-compose.caddy.yml ;;
  2) COMPOSE_FILES=docker-compose.yml:docker-compose.prod.yml ;;
  *) echo "Opção inválida: $PROXY_CHOICE" >&2; exit 1 ;;
esac

echo
echo "-- SMTP dos avisos por e-mail (porta 587 com STARTTLS, ou 465 com SSL) --"
MAIL_HOST="$(ask "Servidor SMTP (ex.: smtp.gmail.com)")"
MAIL_PORT="$(ask "Porta" "587")"
case "$MAIL_PORT" in
  465) MAIL_SSL=true; MAIL_STARTTLS=false ;;
  *) MAIL_SSL=false; MAIL_STARTTLS=true ;;
esac
MAIL_USER="$(ask "Usuário SMTP (em geral, o e-mail)")"
MAIL_PASSWORD="$(ask_secret "Senha SMTP (no Gmail, uma senha de app)")"
MAIL_FROM="$(ask "Remetente dos avisos" "LicitaOne <$MAIL_USER>")"

echo
echo "-- IA: leitura de documentos e o assistente \"Pergunte ao LicitaOne\" --"
echo "  1) Gemini (chave do Google AI Studio: aistudio.google.com/apikey)"
echo "  2) Claude (chave da Anthropic: console.anthropic.com)"
echo "  3) Sem IA por enquanto (dá para pôr a chave depois no $TARGET e reiniciar o backend)"
AI_CHOICE="$(ask "Escolha" "1")"
AI_PROVIDER=claude
GEMINI_KEY=""
ANTHROPIC_KEY=""
case "$AI_CHOICE" in
  1)
    AI_PROVIDER=gemini
    GEMINI_KEY="$(ask_secret "Chave do Gemini")"
    echo "  Aviso: no plano gratuito do Gemini o Google pode usar o conteúdo enviado. Confirme com o cliente." >&2
    ;;
  2) ANTHROPIC_KEY="$(ask_secret "Chave da Anthropic")" ;;
  3) ;;
  *) echo "Opção inválida: $AI_CHOICE" >&2; exit 1 ;;
esac

for value in "$PUBLIC_URL" "$MAIL_HOST" "$MAIL_USER" "$MAIL_PASSWORD" "$MAIL_FROM" "$ANTHROPIC_KEY" "$GEMINI_KEY"; do
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

export V_COMPOSE_FILE="$COMPOSE_FILES"
export V_SHEMA_PUBLIC_URL="$(q "$PUBLIC_URL")"
export V_POSTGRES_PASSWORD="$(random_hex 24)"
export V_RABBITMQ_PASSWORD="$(random_hex 24)"
export V_S3_SECRET_KEY="$(random_hex 24)"
export V_SHEMA_JWT_PRIVATE_KEY="\"$JWT_KEY\""
export V_SHEMA_JWT_KEY_ID="prod-$(date +%Y-%m)"
export V_SHEMA_AI_PROVIDER="$AI_PROVIDER"
export V_ANTHROPIC_API_KEY="$(q "$ANTHROPIC_KEY")"
export V_GEMINI_API_KEY="$(q "$GEMINI_KEY")"
export V_MAIL_HOST="$(q "$MAIL_HOST")"
export V_MAIL_PORT="$MAIL_PORT"
export V_MAIL_SSL="$MAIL_SSL"
export V_MAIL_STARTTLS="$MAIL_STARTTLS"
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
echo "Próximos passos:"
echo "  1. Preencha os dados da empresa (SHEMA_COMPANY_*) no $TARGET, para as cartas do kit de carona."
echo "  2. Suba:  docker compose up -d --build --wait"
echo "  3. Crie o usuário master:  deploy/setup/criar-admin.sh"
if [ "$PROXY_CHOICE" = 1 ]; then
  echo "  4. HTTPS: o Caddy sobe junto no passo 2 e tira o certificado sozinho. Antes, o DNS de $PUBLIC_URL tem que"
  echo "     apontar para este servidor e as portas 80 e 443 têm que estar liberadas no firewall da nuvem."
else
  echo "  4. Configure o site no Apache apontando para 127.0.0.1:14200 (modelo em deploy/apache/licitaone.conf)"
  echo "     e tire o certificado com o certbot (runbook, seção 2)."
fi
echo "  5. Guarde uma cópia do $TARGET num cofre de senhas. As senhas do banco e do RabbitMQ ficam gravadas nos volumes"
echo "     na primeira subida: depois disso, trocar no $TARGET não troca lá, e o sistema para de conectar."
