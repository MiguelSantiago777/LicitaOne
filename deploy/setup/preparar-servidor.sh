#!/usr/bin/env bash
# Prepara uma VM nova (Ubuntu 22.04/24.04 ou Debian 12) para o LicitaOne, tudo em Docker:
# - pacotes que os scripts do projeto usam: git, curl, openssl, apache2-utils (htpasswd do criar-admin.sh),
#   dnsutils (dig), ca-certificates;
# - Docker Engine + Compose v2 (script oficial) e o seu usuário no grupo docker;
# - fuso horário America/Sao_Paulo (os alertas e o cron do backup usam a hora local);
# - swap de 2 GB se a máquina tiver menos de 6 GB de RAM e nenhum swap (o build do Java e do Angular é pesado);
# - firewall (ufw): só SSH (22), HTTP (80) e HTTPS (443);
# - atualizações automáticas de segurança;
# - clona o repositório em /opt/licitaone (se ainda não estiver lá).
#
# Uso, com um usuário que tem sudo (ou como root):
#   bash preparar-servidor.sh
# Pode rodar de novo sem problema: o que já está feito é pulado.
# O repositório é privado? Copie este arquivo para a VM antes (scp deploy/setup/preparar-servidor.sh usuario@IP:)
# e tenha à mão um token do GitHub (só leitura) para o git clone.
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/MiguelSantiago777/LicitaOne.git}"
APP_DIR="${APP_DIR:-/opt/licitaone}"
TZ_NAME="${TZ_NAME:-America/Sao_Paulo}"

if [ "$(id -u)" -eq 0 ]; then SUDO=""; else SUDO="sudo"; fi
TARGET_USER="${SUDO_USER:-$(id -un)}"
step() { echo; echo "== $* =="; }

[ -r /etc/os-release ] && . /etc/os-release
case "${ID:-}" in
  ubuntu|debian) ;;
  *) echo "Feito para Ubuntu ou Debian (achei: ${PRETTY_NAME:-desconhecido})." >&2; exit 1 ;;
esac

step "Pacotes básicos"
export DEBIAN_FRONTEND=noninteractive
$SUDO apt-get update -q
$SUDO apt-get install -y -q git curl openssl ca-certificates apache2-utils dnsutils ufw unattended-upgrades nano

step "Docker"
if command -v docker >/dev/null && docker compose version >/dev/null 2>&1; then
  echo "Já instalado: $(docker --version)"
else
  curl -fsSL https://get.docker.com | $SUDO sh
fi
$SUDO systemctl enable --now docker
if [ "$TARGET_USER" != root ] && ! id -nG "$TARGET_USER" | grep -qw docker; then
  $SUDO usermod -aG docker "$TARGET_USER"
  RELOGIN=1
fi
docker_version="$($SUDO docker compose version --short 2>/dev/null || echo 0)"
echo "Compose $docker_version (precisa da 2.24 ou mais nova)"

step "Fuso horário"
$SUDO timedatectl set-timezone "$TZ_NAME" && timedatectl | grep -i "time zone" || true

step "Swap"
mem_mb=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
if [ "$(swapon --noheadings | wc -l)" -gt 0 ]; then
  echo "Já tem swap."
elif [ "$mem_mb" -lt 6000 ]; then
  $SUDO fallocate -l 2G /swapfile
  $SUDO chmod 600 /swapfile
  $SUDO mkswap /swapfile >/dev/null
  $SUDO swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | $SUDO tee -a /etc/fstab >/dev/null
  echo "Swap de 2 GB criado (RAM: ${mem_mb} MB)."
else
  echo "RAM de ${mem_mb} MB: swap não é necessário."
fi

step "Firewall (ufw): 22, 80 e 443"
# Libere as mesmas portas no firewall do painel da nuvem (security group); os dois precisam deixar passar.
$SUDO ufw allow OpenSSH >/dev/null
$SUDO ufw allow 80/tcp >/dev/null
$SUDO ufw allow 443/tcp >/dev/null
$SUDO ufw allow 443/udp >/dev/null
$SUDO ufw --force enable >/dev/null
$SUDO ufw status | sed -n '1,12p'

step "Atualizações automáticas de segurança"
echo 'APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";' | $SUDO tee /etc/apt/apt.conf.d/20auto-upgrades >/dev/null
echo "Ativadas."

step "Código em $APP_DIR"
if [ -d "$APP_DIR/.git" ]; then
  echo "Já clonado."
else
  $SUDO mkdir -p "$APP_DIR"
  $SUDO chown "$TARGET_USER" "$APP_DIR"
  echo "Clonando $REPO_URL (repositório privado: usuário do GitHub e, na senha, um token)"
  if [ "$(id -un)" = "$TARGET_USER" ]; then
    git clone "$REPO_URL" "$APP_DIR"
  else
    sudo -u "$TARGET_USER" git clone "$REPO_URL" "$APP_DIR"
  fi
fi
$SUDO mkdir -p /var/backups/licitaone
$SUDO chown "$TARGET_USER" /var/backups/licitaone

step "Pronto"
ip_publico="$(curl -fsS --max-time 5 https://api.ipify.org || echo '?')"
echo "IP público desta VM: $ip_publico"
echo "DNS de sistema.shemabrasil.com.br agora: $(dig +short sistema.shemabrasil.com.br | tail -1)"
echo
[ "${RELOGIN:-0}" = 1 ] && echo "IMPORTANTE: saia do ssh e entre de novo (para o grupo docker valer)."
echo "Depois:"
echo "  cd $APP_DIR"
echo "  deploy/setup/gerar-env-prod.sh       # https://sistema.shemabrasil.com.br, opção 1 (Caddy)"
echo "  docker compose up -d --build --wait"
echo "  deploy/setup/criar-admin.sh"
echo "Roteiro completo: docs/operacao/instalacao-nuvem.md"
