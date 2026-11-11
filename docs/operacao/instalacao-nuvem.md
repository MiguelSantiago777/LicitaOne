# Instalação numa VM na nuvem (tudo em Docker)

Roteiro para subir o LicitaOne em `https://sistema.shemabrasil.com.br` numa VM só dele, com tudo em Docker, inclusive o HTTPS (Caddy). Servidor que já tem Apache nas portas 80 e 443? Use o [passo a passo com Apache](instalacao-passo-a-passo.md). Detalhes e manutenção ficam no [runbook](runbook.md).

## Como fica
```
navegador ──HTTPS──> Caddy (Docker, portas 80/443, certificado Let's Encrypt automático)
                       └──> frontend (nginx) ──> backend Java
                                                   └── Postgres, RabbitMQ e S3: só na rede interna do Docker
```
Só as portas 80 e 443 ficam abertas para a internet. Banco, RabbitMQ e S3 não saem do Docker.

## Antes de começar
- [ ] **VM**: Ubuntu 24.04 (ou Debian 12), 2 vCPU, 4 GB de RAM e 40 GB de disco. Pegue um **IP fixo** (Elastic IP na AWS, IP reservado na Oracle/GCP, IP primário na Hetzner/DigitalOcean...).
- [ ] **Firewall da nuvem** (security group / regras de entrada): liberar **22** (SSH, de preferência só do seu IP), **80** e **443**. Nada mais.
- [ ] **DNS**: no painel do domínio `shemabrasil.com.br`, crie um registro **A** `sistema` → IP da VM. Confira de qualquer máquina:
  ```bash
  nslookup sistema.shemabrasil.com.br      # tem que devolver o IP da VM
  ```
  ⚠ O Caddy só consegue o certificado depois que o DNS já aponta para a VM. Se tiver Cloudflare, deixe a nuvem **cinza** (só DNS) pelo menos na primeira subida.
- [ ] **SMTP** (servidor, porta 587, usuário e senha; no Gmail, uma senha de app).
- [ ] **Chave do Gemini** (veja [abaixo](#chave-do-gemini)).
- [ ] **Dados da empresa** para as cartas do kit de carona (razão social, CNPJ, endereço, cidade, representante e cargo, e-mail, telefone).
- [ ] **E-mail e senha do usuário master** (12+ caracteres).

## 1 e 2. Preparar a VM e baixar o código
Um script instala e configura tudo o que o projeto precisa:
- git, curl, openssl, apache2-utils (o `htpasswd` do `criar-admin.sh`) e dnsutils;
- Docker e Compose, com o seu usuário no grupo `docker`;
- fuso America/Sao_Paulo;
- swap de 2 GB, se a VM tiver pouca RAM;
- firewall (ufw) só com 22, 80 e 443;
- atualizações automáticas de segurança;
- clona o código em `/opt/licitaone` e cria `/var/backups/licitaone`.

Do seu computador, copie o script para a VM e rode lá:
```bash
scp deploy/setup/preparar-servidor.sh usuario@IP_DA_VM:
ssh usuario@IP_DA_VM
bash preparar-servidor.sh
exit                                # saia e entre de novo, para o grupo docker valer
ssh usuario@IP_DA_VM
cd /opt/licitaone
```
O repositório é privado? Na hora do `git clone`, ele pede o usuário do GitHub e, no lugar da senha, um **token**: GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token, só o repositório LicitaOne, permissão *Contents: Read-only*.
- [ ] VM preparada e código em `/opt/licitaone`

## 3. `.env.prod`
```bash
deploy/setup/gerar-env-prod.sh
```
Responda:
- Endereço público: `https://sistema.shemabrasil.com.br`
- HTTPS: **1** (Caddy no Docker)
- SMTP e IA: os seus dados

Depois preencha os dados da empresa (`nano .env.prod`, bloco `SHEMA_COMPANY_*`, valores com espaço entre aspas) e **guarde uma cópia do `.env.prod` num cofre de senhas**.
- [ ] `.env.prod` gerado, dados da empresa preenchidos, cópia no cofre

## 4. Subir
```bash
docker compose up -d --build --wait
docker compose ps
docker compose logs caddy | grep -i certificate
```
- A primeira subida compila o Java e o Angular: alguns minutos. Com 4 GB de RAM dá; com menos, crie um swap de 2 GB antes.
- No `ps`, todos `running` e o backend `healthy`.
- No log do Caddy deve aparecer `certificate obtained successfully`.
- [ ] Tudo no ar e certificado emitido

## 5. Usuário master
```bash
deploy/setup/criar-admin.sh
```
- [ ] Usuário master criado

## 6. Primeiro acesso
- [ ] Abrir `https://sistema.shemabrasil.com.br` (cadeado no navegador) e entrar com o master
- [ ] Cadastrar os outros usuários em Administração → Usuários
- [ ] Testar a IA (botão "Perguntar") e o SMTP (falhas aparecem em Alertas → Mensagens com falha)

## 7. Backup diário
```bash
sudo mkdir -p /var/backups/licitaone && sudo chown $USER /var/backups/licitaone
deploy/backup/backup.sh /var/backups/licitaone        # teste na mão
crontab -e
```
```
0 2 * * * cd /opt/licitaone && deploy/backup/backup.sh /var/backups/licitaone >> /var/log/licitaone-backup.log 2>&1
```
Na nuvem, o disco da VM não é backup: copie os arquivos para fora (outro bucket/provedor, cifrado; runbook, seção 6) e, se o provedor oferecer, ative também o snapshot automático do disco.
- [ ] Backup agendado e copiado para fora da VM

## Chave do Gemini
1. Entre em **https://aistudio.google.com/apikey** com uma conta Google. De preferência, use uma conta da empresa, não a pessoal.
2. Aceite os termos na primeira vez.
3. Clique em **Create API key** (Criar chave de API). Escolha um projeto do Google Cloud ou deixe ele criar um.
4. Copie a chave (começa com `AIza...`). Ela é uma senha: não mande por WhatsApp nem e-mail, guarde no cofre.
5. No `gerar-env-prod.sh`, escolha a IA **1 (Gemini)** e cole a chave. Ela não aparece na tela.

Já subiu sem a chave, ou quer trocar? Edite a linha `GEMINI_API_KEY='...'` e deixe `SHEMA_AI_PROVIDER=gemini` no `.env.prod`. Depois rode `docker compose up -d backend`.

**Plano gratuito × pago**
- **Gratuito**: tem limite de pedidos por minuto e por dia. Quando acaba, o sistema avisa "A cota gratuita da IA acabou por hoje". Além disso, **o Google pode usar o conteúdo enviado** (atas, contratos) para melhorar os produtos dele.
- **Pago**: no AI Studio, abra a chave e clique em **Set up billing** (Configurar faturamento) para ligar um cartão ao projeto. Os limites sobem e o Google deixa de usar os dados. Para documentos de clientes, é o recomendado.
- Para não ter surpresa na fatura, crie um alerta de orçamento em console.cloud.google.com → Faturamento → Orçamentos e alertas.

## Dia a dia
| Para | Comando (em `/opt/licitaone`) |
|---|---|
| Ver se está tudo no ar | `docker compose ps` |
| Logs | `docker compose logs -f backend` (ou `caddy`) |
| Atualizar (faça backup antes) | `git pull && docker compose up -d --build --wait` |
| Esqueceu a senha do master | `deploy/setup/criar-admin.sh` com o mesmo e-mail |

## Se der errado
| Sintoma | O que fazer |
|---|---|
| Caddy não tira o certificado (`challenge failed`, `timeout`) | DNS ainda não aponta para a VM, ou as portas 80/443 estão fechadas no firewall da nuvem. Corrija e rode `docker compose restart caddy`. Não fique reiniciando em loop: o Let's Encrypt bloqueia por uma hora depois de várias falhas. |
| Erro `502` | o frontend ou o backend não está no ar: `docker compose ps` e `docker compose logs backend` |
| Login não mantém a sessão | o endereço aberto tem que ser exatamente o `SHEMA_PUBLIC_URL`, com `https://` |
| Build morre sem mensagem clara | falta de memória: crie swap (`sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`) |
