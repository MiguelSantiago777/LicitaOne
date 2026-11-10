# Runbook de operação do LicitaOne

Para quem instala e mantém o sistema. Outros documentos:
- roteiro de instalação para seguir com checklist (servidor com Apache): [`instalacao-passo-a-passo.md`](instalacao-passo-a-passo.md);
- mensageria e DLQ: [`mensageria.md`](mensageria.md);
- segurança: [`seguranca.md`](seguranca.md);
- performance: [`performance.md`](performance.md).

## 1. Visão geral
| Serviço | O que faz | Dados |
|---|---|---|
| `frontend` | nginx sem root: entrega o Angular e repassa `/api` ao backend (mesma origem) | nenhum |
| `backend` | Spring Boot (Java 21). API na 8080; Actuator só na 8081, interna | nenhum (sem estado) |
| `postgres` | PostgreSQL 18: todos os dados, o outbox de eventos e o lock dos jobs (ShedLock) | volume `postgres-data` |
| `rabbitmq` | filas de importação e de alertas, cada uma com DLQ | volume `rabbitmq-data` (mensagens em trânsito) |
| `s3` | SeaweedFS (S3): documentos, planilhas e relatórios de importação | volume `s3-data` |
| `prometheus`, `grafana` | métricas e o painel "LicitaOne · negócio" (perfil `observability`) | opcional |

O backend não guarda estado: dá para rodar mais de uma instância. O job diário de alertas roda em uma só, por causa do ShedLock.

## 2. Instalação em produção
Requisitos:
- um servidor Linux (Ubuntu 24.04 ou Debian 12, por exemplo) com Docker e Compose v2.24 ou mais novo;
- 2 vCPU e 4 GB de RAM bastam para o volume esperado, com 40 GB de disco;
- um domínio (ou subdomínio) com o DNS apontando para o servidor;
- uma conta SMTP para os avisos;
- portas 80 e 443 abertas (o resto fica fechado: banco, RabbitMQ e S3 não saem do Docker).

1. Instale o Docker (script oficial) e libere seu usuário:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER    # saia e entre de novo na sessão
   ```
2. Clone o repositório em `/opt/licitaone`:
   ```bash
   sudo mkdir -p /opt/licitaone && sudo chown $USER /opt/licitaone
   git clone https://github.com/MiguelSantiago777/LicitaOne.git /opt/licitaone && cd /opt/licitaone
   ```
3. Gere o `.env.prod`:
   ```bash
   deploy/setup/gerar-env-prod.sh
   ```
   - Ele pergunta o endereço público, o SMTP e a IA: **Gemini** (chave do Google AI Studio), Claude (chave da Anthropic) ou nenhuma por enquanto.
   - Sorteia as senhas do banco, do RabbitMQ e do S3 e gera a chave JWT.
   - Grava com permissão 600 e cria o link `.env -> .env.prod`. Com ele, o `docker compose` já usa os dois arquivos de compose (`COMPOSE_FILE`), sem flags, e os scripts de backup acham as senhas.
   - Depois, preencha à mão os dados da empresa (`SHEMA_COMPANY_*`).
   - **Guarde uma cópia do `.env.prod` num cofre de senhas.**
     - As senhas do banco e do RabbitMQ ficam gravadas nos volumes na primeira subida. Depois disso, mudar no `.env.prod` não muda lá, e o sistema para de conectar. Para trocar, altere primeiro dentro do serviço (`alter user` no Postgres, `rabbitmqctl change_password`) e depois no arquivo.
     - Perder o arquivo não perde dados: um `.env.prod` novo num servidor limpo restaura o backup normalmente. Só a chave JWT nova faz todo mundo entrar de novo.
   - Prefere fazer à mão? Copie o `.env.prod.example` e siga os comentários dele; sem a chave JWT o backend **não sobe** em produção, de propósito.
4. Suba:
   ```bash
   docker compose up -d --build --wait
   ```
   - A primeira subida compila as imagens (alguns minutos).
   - O Flyway cria o banco, sem dados de exemplo.
5. Crie o **usuário master** (como o `createsuperuser` do Django):
   ```bash
   deploy/setup/criar-admin.sh
   ```
   Pergunta nome, e-mail e senha (duas vezes, sem mostrar; mínimo de 12 caracteres). A senha não passa pela linha de comando nem fica no histórico. O hash BCrypt sai do `htpasswd` do servidor ou, sem ele, da imagem `httpd:2.4-alpine`.
   - Alternativa: preencher `SHEMA_ADMIN_*` no `.env.prod` antes de subir. O backend cria esse administrador só se ainda não houver nenhum; depois, as variáveis são ignoradas.
6. Configure o **TLS no proxy da frente**, encaminhando para `127.0.0.1:14200`. Com Caddy, que pega o certificado sozinho:
   ```bash
   sudo apt install -y caddy
   ```
   Em `/etc/caddy/Caddyfile`:
   ```
   licitaone.exemplo.com.br {
       reverse_proxy 127.0.0.1:14200
   }
   ```
   ```bash
   sudo systemctl reload caddy
   ```
   **O servidor já tem Apache nas portas 80 e 443** (outros sites)? Não instale o Caddy: use o modelo `deploy/apache/licitaone.conf` (instruções no próprio arquivo) e tire o certificado com `sudo certbot --apache -d <domínio>`. O LicitaOne só ocupa `127.0.0.1:14200`; para trocar, mude `FRONTEND_PORT` no `.env.prod` e a porta no site do Apache. O Postgres do LicitaOne fica dentro do Docker e não disputa a porta 5432 com um Postgres instalado no servidor.
7. Primeiro acesso: entre com o usuário master e cadastre os outros em Administração → Usuários.
8. Agende o backup (seção 6) e **teste uma restauração** antes de pôr dados de verdade.

**Esqueceu a senha do único administrador?** Outro administrador define uma nova em Usuários → Definir senha. Se não houver outro, rode `deploy/setup/criar-admin.sh` com o mesmo e-mail: o usuário continua o mesmo (nome e histórico), volta a ser administrador e ativo, ganha a senha nova, é desbloqueado e tem as sessões abertas encerradas.

## 3. Configuração (variáveis de ambiente)
| Variável | Para quê | Padrão |
|---|---|---|
| `SHEMA_PUBLIC_URL` | origem do CORS e link nos e-mails | — (obrigatória em prod) |
| `SHEMA_JWT_PRIVATE_KEY` / `SHEMA_JWT_KEY_ID` | assinatura dos tokens | — (obrigatória em prod) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_STARTTLS` | SMTP dos avisos internos | porta 587 com STARTTLS |
| `SHEMA_ALERTS_MAIL_FROM` | remetente dos avisos | — |
| `SHEMA_ALERTS_MAIL_ENABLED` | `false` desliga o e-mail; as tarefas continuam | `true` |
| `SHEMA_ALERTS_CRON` | horário do job de alertas (horário de Brasília); `-` desliga | `0 0 7 * * *` |
| `SHEMA_ALERTS_CONTRACT_WITHOUT_ORDER_DAYS` | contrato sem pedido | 30 |
| `SHEMA_ALERTS_PRICE_RECORD_EXPIRING_DAYS` | faixas da ata vencendo | `90,60,30` |
| `SHEMA_ALERTS_TENDER_WITHOUT_CONTRACT_DAYS` | licitação vencida sem contrato (⚠ a confirmar com o cliente) | 30 |
| `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` | armazenamento de arquivos | SeaweedFS local |
| `SHEMA_LOGIN_ATTEMPTS_PER_MINUTE` | limite de tentativas de login por IP | 10 |
| `ANTHROPIC_API_KEY` | leitura com IA de atas, contratos e pedidos (ADRs 0016 e 0017); em branco, a funcionalidade fica desligada. O envio de documentos e mensagens à Anthropic foi autorizado em 04/11/2026 | — |
| `SHEMA_EXTRACTION_MODEL` | modelo da leitura com IA | `claude-opus-5-5` |
| `SHEMA_AI_PROVIDER` | provedor da IA, para leitura e assistente (ADR 0021): `claude` ou `gemini` | `claude` |
| `GEMINI_API_KEY` | chave do Google AI Studio, usada com `SHEMA_AI_PROVIDER=gemini`; em branco, a IA fica desligada. ⚠ No plano gratuito o Google pode usar o conteúdo enviado: confirme com o cliente antes de ligar em produção | — |
| `SHEMA_EXTRACTION_GEMINI_MODEL`, `SHEMA_ASSISTANT_GEMINI_MODEL` | modelos do Gemini | `gemini-3.8-flash` |
| `SHEMA_EXTRACTION_DEMO` | leitor de demonstração, sem IA (só E2E); o backend não sobe com ele no perfil prod | `false` |

Os limites de carona ficam em `shema.caronas.*`: até 4 órgãos, 50% de cada item por órgão e 200% no total (ADRs 0014 e 0019).

Os dados da empresa no kit de carona (razão social, CNPJ, endereço, cidade, representante e cargo, e-mail e telefone) ficam em `SHEMA_COMPANY_*` (ADR 0019). O `.env.example` traz exemplos: troque antes do primeiro uso. Campo em branco aparece entre colchetes nas cartas.

## 4. Atualizar a versão
```bash
git pull
docker compose up -d --build --wait
```
- As migrações do banco rodam sozinhas na subida.
- **Antes de atualizar, faça um backup** (seção 6). Uma migração não tem volta automática: para voltar a versão, restaure o backup feito antes e suba a versão anterior (`git checkout <tag>`).
- Eventos que estavam no outbox e não chegaram ao RabbitMQ são reenviados na subida, então não se perde nada no meio da atualização.

## 5. Monitorar
- **Saúde:**
  ```bash
  docker compose ps
  ```
  O backend fica `healthy` quando banco, RabbitMQ e S3 respondem: o health check usa `/actuator/health/readiness`, que só existe na rede interna.
- **Logs:**
  ```bash
  docker compose logs -f backend
  ```
  - Os logs são JSON (ECS), e cada linha tem o `correlationId`.
  - O mesmo id volta para o navegador no cabeçalho `X-Correlation-Id`: peça esse número a quem reportar um erro e procure por ele.
- **Métricas:**
  - `docker compose --profile observability up -d` sobe o Prometheus e o Grafana (painel "LicitaOne · negócio").
  - Em produção, publique o Grafana só atrás do proxy, com login.

O que merece alerta:

| Sinal | Onde | Ação |
|---|---|---|
| Backend `unhealthy` | `docker compose ps` | ver os logs; em geral o banco ou o RabbitMQ caíram |
| `shema_messaging_dlq_messages > 0` | Grafana, tela **Alertas** | ver [`mensageria.md`](mensageria.md) e reprocessar |
| Última verificação de alertas com mais de 1 dia | tela **Alertas** | job parado: ver o log e procurar `Alertas de` |
| `shema_imports_failed` subindo | Grafana | abrir a importação na tela e ler o motivo |
| Disco acima de 80% | servidor | volumes `postgres-data` e `s3-data`; backups antigos |

## 6. Backup e restauração
**O que entra:**
- o banco inteiro (dados, histórico, outbox, Flyway);
- todos os arquivos do bucket.

O RabbitMQ não precisa: o que está pendente fica no outbox do banco e é reenviado.

```bash
deploy/backup/backup.sh /caminho/dos/backups      # cria /caminho/dos/backups/AAAAMMDD-HHMMSS
deploy/backup/restore.sh /caminho/dos/backups/20261029-150000
```

**O que o backup grava e como a restauração confere:**
- O backup grava `db.dump` (pg_dump `--format=custom`), a pasta `files/` e um `manifest.txt` com o SHA-256 do dump.
- A restauração confere o manifesto antes de começar.
- Ela também para o backend durante o processo, recria o banco e devolve os arquivos ao bucket.

**Rotina recomendada:**
- Backup diário às 2h, pelo cron do servidor:
  ```
  0 2 * * * cd /opt/licitaone && deploy/backup/backup.sh /var/backups/licitaone >> /var/log/licitaone-backup.log 2>&1
  ```
- Guarde 30 dias e copie para **fora do servidor**, cifrado, porque tem dados pessoais. Por exemplo, `age` ou `gpg` antes de enviar a um bucket de outra conta.
- **Teste a restauração todo mês** num ambiente de ensaio:
  ```bash
  deploy/backup/test-restore.sh
  ```
  Ele faz o backup, apaga o ambiente, restaura e confere as contagens e o SHA-256 de cada documento. ⚠ Apaga os volumes do compose em que roda: **nunca rode no servidor de produção.**
  - Último teste: 29/10/2026, OK (134 licitações, 125 contratos, 29 arquivos).
- Com S3 externo: defina `BACKUP_S3_ENDPOINT`. A cópia de arquivos também pode usar a replicação ou o versionamento do próprio provedor.

## 7. Incidentes comuns
| Sintoma | Causa provável | O que fazer |
|---|---|---|
| Ninguém entra; o backend reinicia em loop | variável obrigatória faltando (chave JWT, SMTP) ou banco fora | `docker compose logs backend`: a primeira linha de erro diz qual |
| E-mails de aviso não chegam, tarefas sim | SMTP fora ou credencial errada | as mensagens vão para a DLQ `notify-team`. Corrija o SMTP e clique em **Alertas → Mensagens com falha → Reprocessar** |
| "Verificar agora" responde que já está rodando | outra verificação em andamento (lock do ShedLock) | espere um minuto. Se o processo morreu, o lock expira sozinho em até 15 min |
| Upload de documento falha | S3 fora ou disco cheio | `docker compose ps s3` e espaço em disco |
| Pessoa bloqueada após errar a senha | 5 erros: bloqueio de 15 min | esperar, ou o administrador define uma senha nova (Usuários → Definir senha) |
| Leitura com IA termina com "A cota gratuita da IA acabou por hoje" | Gemini no plano gratuito: a cota diária acabou (volta à meia-noite do horário do Pacífico, 4h ou 5h em Brasília) | espere a cota voltar, ou ative o faturamento no projeto do Google AI Studio |
| Leitura com IA termina com "A chave da IA foi recusada" | `ANTHROPIC_API_KEY` errada, revogada ou sem crédito | troque a chave e reinicie o backend; as leituras com falha podem ser pedidas de novo |
| Leitura com IA demora ou falha com "IA ocupada ou fora do ar" | limite de uso ou instabilidade da API (o SDK já tentou de novo) | pedir de novo depois; métricas `shema.extraction.failed` e `shema.extraction.tokens` (etiqueta `kind`: `DOCUMENTO`, `PEDIDO`, `RESPOSTA`, `RASCUNHO` ou `CARONA`). O assistente "Pergunte ao LicitaOne" tem `shema.assistant.questions` (`outcome`), `shema.assistant.tool.calls` (`tool`) e `shema.assistant.tokens` (ADR 0020) no Prometheus. Filas: `shemacrm.extraction.extraction-requested` e `shemacrm.extraction.order-reading-requested`, cada uma com DLQ |
| "Não deu para abrir a imagem" no pedido por foto | arquivo corrompido ou formato que a IA não lê | pedir a foto em JPEG ou PNG, ou o PDF; o texto colado é outra opção |
| Sessões caem a cada reinício | backend sem `SHEMA_JWT_PRIVATE_KEY` (só acontece fora do perfil prod) | defina a chave |

## 8. Rotinas de segurança
- **Trocar a chave JWT** (por exemplo, uma vez por ano ou se vazar):
  - gere uma chave nova e troque `SHEMA_JWT_PRIVATE_KEY` e `SHEMA_JWT_KEY_ID`;
  - suba de novo; todo mundo entra de novo.
- **Pessoa saiu da empresa:** desmarque "Ativo" no usuário (não apague). As sessões dela caem na hora, e o histórico continua com o nome de quem fez cada coisa.
- **Atualizações de segurança:**
  - o CI roda o Trivy nas dependências e nas imagens, e quebra o build com vulnerabilidade crítica;
  - reconstrua as imagens todo mês (`up -d --build`), para pegar as correções do Alpine.

## 9. LGPD: dados pessoais e retenção
| Dado | Onde | Por que | Retenção |
|---|---|---|---|
| Usuários (nome, e-mail, perfil) | `users` | acesso e auditoria | enquanto houver histórico; desativar em vez de apagar |
| Contatos de órgãos (nome, cargo, e-mail, telefone) | `agency_contacts` | follow-up de pedidos | enquanto o órgão for cliente; apagar o contato a pedido da pessoa |
| Histórico (quem fez o quê) | `*_events` | auditoria (seção 5.8) | prazo dos contratos + 5 anos |
| Documentos (atas, contratos, empenhos) | bucket S3 | prova documental | apagar é lógico; os arquivos ficam pelo mesmo prazo |
| Logs | `docker compose logs` | suporte | o compose de produção roda 5 arquivos de 50 MB por serviço |
| Backups | fora do servidor | recuperação | 30 dias |

- **Pedido de um titular** (acesso ou exclusão do contato): o administrador consulta ou apaga o contato na tela do órgão.
- Os logs não guardam senha, token nem conteúdo de documento, só ids.

## Assistente "Pergunte ao LicitaOne" (ADR 0020)
- Liga com o mesmo provedor e a mesma chave da leitura (`ANTHROPIC_API_KEY`, ou `GEMINI_API_KEY` com `SHEMA_AI_PROVIDER=gemini`). Sem a chave, o botão "Perguntar" não aparece.
- Limite de perguntas por pessoa por dia: `SHEMA_ASSISTANT_DAILY_LIMIT` (padrão 50). A contagem fica em `assistant_usage`; para liberar alguém no mesmo dia, apague a linha da pessoa nessa tabela.
- O texto das perguntas não é guardado nem vai para os logs. As perguntas em andamento ficam na memória do backend por 10 minutos: reiniciar o backend perde só as que estavam rodando.
- E2E: rode o compose com `SHEMA_EXTRACTION_DEMO=true` e `SHEMA_ASSISTANT_DAILY_LIMIT=1000`, porque o teste pergunta várias vezes como o mesmo usuário.
