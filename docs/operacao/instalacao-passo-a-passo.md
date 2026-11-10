# Instalação no servidor: passo a passo

Roteiro para instalar o LicitaOne num servidor Linux que **já tem outros apps e o Apache nas portas 80 e 443**. Siga na ordem e marque cada item. Detalhes e manutenção ficam no [runbook](runbook.md).

## Antes de começar: tenha em mãos
- [ ] **Domínio** do LicitaOne (ex.: `licitaone.suaempresa.com.br`) com o DNS (registro A) apontando para o IP do servidor. Confira com `dig +short licitaone.suaempresa.com.br`.
- [ ] **SMTP** para os avisos: servidor, porta (587), usuário e senha. No Gmail, crie uma **senha de app** (Conta Google → Segurança → Senhas de app); a senha normal não funciona.
- [ ] **Chave do Gemini**: aistudio.google.com/apikey. ⚠ No plano gratuito o Google pode usar o conteúdo enviado: confirme com o cliente ou ative o faturamento.
- [ ] **Dados da empresa** para as cartas do kit de carona: razão social, CNPJ, endereço, cidade, representante legal e cargo, e-mail e telefone.
- [ ] **E-mail e senha do usuário master**: senha com pelo menos 12 caracteres, sem conter o e-mail.
- [ ] Um **cofre de senhas** (Bitwarden, 1Password...) para guardar o `.env.prod`.

## Como fica no servidor
```
navegador ──HTTPS──> Apache (80/443, já existe)
                       └──> 127.0.0.1:14200  LicitaOne (Docker)
                               ├── frontend (nginx)
                               ├── backend Java (Spring Boot, Tomcat embutido)
                               └── Postgres, RabbitMQ e S3: só na rede interna do Docker
```
- O LicitaOne ocupa só a porta `127.0.0.1:14200`, que está livre.
- O Postgres dele fica dentro do Docker e não disputa a porta 5432 com o Postgres que já roda no servidor.
- Os outros apps (gunicorn nas portas 8000 e 8010) continuam como estão.

## 1. Docker
Se `docker compose version` já responder com a v2.24 ou mais nova, pule este passo.
```bash
curl -fsSL https://get.docker.com | sudo sh
docker compose version
```
- [ ] Docker instalado

(Se não estiver logado como root: `sudo usermod -aG docker $USER`, depois saia e entre de novo.)

## 2. Baixar o código
```bash
sudo mkdir -p /opt/licitaone && sudo chown $USER /opt/licitaone
git clone https://github.com/MiguelSantiago777/LicitaOne.git /opt/licitaone
cd /opt/licitaone
```
- [ ] Código em `/opt/licitaone`

## 3. Gerar o `.env.prod`
```bash
deploy/setup/gerar-env-prod.sh
```
O script pergunta:
- o endereço público, com `https://`;
- o SMTP;
- a IA: digite **1** para Gemini e cole a chave (ela não aparece na tela).

**O que ele gera sozinho**, sem mostrar na tela:

| Variável | O que é |
|---|---|
| `POSTGRES_PASSWORD` | senha do banco (48 caracteres aleatórios) |
| `RABBITMQ_PASSWORD` | senha da fila de mensagens |
| `S3_SECRET_KEY` | senha do armazenamento de arquivos |
| `SHEMA_JWT_PRIVATE_KEY` | chave dos tokens de login (RSA 2048), o equivalente ao `SECRET_KEY` do Django |
| `SHEMA_JWT_KEY_ID` | identificador da chave |

Ele também grava o arquivo com permissão 600 e cria o link `.env -> .env.prod`. Com isso, o `docker compose` funciona sem flags.

Depois, preencha os dados da empresa:
```bash
nano .env.prod      # bloco SHEMA_COMPANY_*; valores com espaço entre aspas
```
Por fim, guarde o arquivo inteiro no cofre de senhas:
```bash
cat .env.prod
```
- [ ] `.env.prod` gerado
- [ ] Dados da empresa preenchidos
- [ ] Cópia do `.env.prod` no cofre de senhas

## 4. Subir
```bash
docker compose up -d --build --wait
docker compose ps
```
- A primeira vez demora alguns minutos, porque compila o Java e o Angular.
- No `ps`, todos os serviços devem aparecer `running` e o backend `healthy`.
- Para conferir, rode `curl -I http://127.0.0.1:14200`: deve responder `200`.

- [ ] Tudo `healthy`

## 5. Criar o usuário master
```bash
deploy/setup/criar-admin.sh
```
O script pergunta o nome, o e-mail e a senha (duas vezes, sem mostrar na tela).
- [ ] Usuário master criado

## 6. Apache e HTTPS
```bash
nano deploy/apache/licitaone.conf      # troque licitaone.exemplo.com.br pelo seu domínio
sudo cp deploy/apache/licitaone.conf /etc/apache2/sites-available/
sudo a2enmod proxy proxy_http headers
sudo a2ensite licitaone
sudo apache2ctl configtest             # tem que dizer "Syntax OK"
sudo systemctl reload apache2
```
Depois, o certificado:
```bash
sudo apt install -y certbot python3-certbot-apache    # se ainda não tiver
sudo certbot --apache -d licitaone.suaempresa.com.br
```
O certbot cria o site com HTTPS e passa a redirecionar o HTTP para ele. O domínio tem que ser o mesmo do `SHEMA_PUBLIC_URL`.
- [ ] Site no Apache
- [ ] HTTPS funcionando

## 7. Primeiro acesso
- [ ] Abrir `https://licitaone.suaempresa.com.br` e entrar com o usuário master
- [ ] Cadastrar os outros usuários em Administração → Usuários
- [ ] Testar a IA: botão "Perguntar" no topo, ou "Preencher com IA" numa ata
- [ ] Conferir o e-mail: os avisos só saem quando há alerta (contrato sem pedido, ata vencendo...); com o banco vazio, nada chega. Se o SMTP estiver errado, a mensagem cai em Alertas → Mensagens com falha.

## 8. Backup diário
```bash
sudo mkdir -p /var/backups/licitaone && sudo chown $USER /var/backups/licitaone
crontab -e
```
Adicione a linha:
```
0 2 * * * cd /opt/licitaone && deploy/backup/backup.sh /var/backups/licitaone >> /var/log/licitaone-backup.log 2>&1
```
Teste uma vez na mão:
```bash
deploy/backup/backup.sh /var/backups/licitaone
```
Depois, copie os backups para fora do servidor, cifrados (runbook, seção 6).
- [ ] Cron agendado
- [ ] Backup manual funcionou

## Cuidados com o `.env.prod`
- **Não rode o gerador de novo numa instalação que já subiu.** As senhas do banco e do RabbitMQ ficam gravadas nos volumes na primeira subida. Se mudar no arquivo, o sistema para de conectar. O script se recusa a sobrescrever um `.env.prod` existente justamente por isso.
- **Perder o arquivo não perde dados.** Um `.env.prod` novo num servidor limpo restaura o backup normalmente. Com a chave JWT nova, todo mundo só precisa fazer login de novo.
- **O arquivo não vai para o git**: está no `.gitignore`.
- **A chave da IA ou o SMTP mudaram?** Edite o `.env.prod` e rode `docker compose up -d backend`.

## Dia a dia
| Para | Comando (em `/opt/licitaone`) |
|---|---|
| Ver se está tudo no ar | `docker compose ps` |
| Ver os logs | `docker compose logs -f backend` |
| Atualizar a versão (faça um backup antes) | `git pull && docker compose up -d --build --wait` |
| Esqueceu a senha do master | `deploy/setup/criar-admin.sh` com o mesmo e-mail |
| Reiniciar | `docker compose restart backend` |

## Se der errado
| Sintoma | O que fazer |
|---|---|
| Backend reiniciando sem parar | `docker compose logs backend`: a primeira linha de erro diz o que falta (em geral, SMTP ou uma variável em branco) |
| Erro `502` no navegador | o LicitaOne não está no ar (`docker compose ps`) ou a porta do site no Apache está errada |
| Login não mantém a sessão | o site precisa abrir em `https://`, e o domínio tem que ser igual ao `SHEMA_PUBLIC_URL` |
| `criar-admin.sh` diz que as tabelas não existem | o backend ainda não subiu nenhuma vez: rode o passo 4 |
| "A cota gratuita da IA acabou por hoje" | é o limite diário do Gemini gratuito: espere o dia virar ou ative o faturamento |

Se travar em algum passo, copie a saída do comando e mande para análise.
