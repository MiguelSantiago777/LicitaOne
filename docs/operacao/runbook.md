# Runbook de operação do LicitaOne

Para quem instala e mantém o sistema. Outros documentos:
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
- um servidor Linux com Docker e Compose v2.24 ou mais novo;
- 2 vCPU e 4 GB de RAM bastam para o volume esperado;
- um domínio com HTTPS;
- uma conta SMTP.

1. Clone o repositório e crie o arquivo de ambiente:
   ```bash
   cp .env.prod.example .env.prod && chmod 600 .env.prod
   ```
2. Preencha o `.env.prod`:
   - senhas aleatórias: `openssl rand -base64 32`;
   - `SHEMA_PUBLIC_URL`;
   - SMTP;
   - a chave JWT: `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048`, colada entre aspas com as quebras de linha.
   - Sem a chave JWT, o backend **não sobe** em produção, de propósito.
3. Suba:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build --wait
   ```
   - O Flyway cria o banco na primeira subida, sem dados de exemplo.
   - O administrador do `.env.prod` é criado só se não houver nenhum usuário.
4. Configure o **TLS no proxy da frente**, encaminhando para `127.0.0.1:14200`. Exemplo com Caddy, que pega o certificado sozinho:
   ```
   licitaone.exemplo.com.br {
       reverse_proxy 127.0.0.1:14200
   }
   ```
5. Primeiro acesso:
   1. Entre com o administrador e **troque a senha** (ícone de engrenagem no rodapé do menu). O log avisa se a senha inicial não segue a política.
   2. Cadastre os usuários em Administração → Usuários.
6. Agende o backup (seção 6) e **teste uma restauração** antes de pôr dados de verdade.

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
| `SHEMA_EXTRACTION_DEMO` | leitor de demonstração, sem IA (só E2E); o backend não sobe com ele no perfil prod | `false` |

Os limites de carona ficam em `shema.caronas.*`: até 4 órgãos e 200% (ADR 0014).

## 4. Atualizar a versão
```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build --wait
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
| Leitura com IA termina com "A chave da IA foi recusada" | `ANTHROPIC_API_KEY` errada, revogada ou sem crédito | troque a chave e reinicie o backend; as leituras com falha podem ser pedidas de novo |
| Leitura com IA demora ou falha com "IA ocupada ou fora do ar" | limite de uso ou instabilidade da API (o SDK já tentou de novo) | pedir de novo depois; métricas `shema.extraction.failed` e `shema.extraction.tokens` (etiqueta `kind`: `DOCUMENTO` ou `PEDIDO`) no Prometheus. Filas: `shemacrm.extraction.extraction-requested` e `shemacrm.extraction.order-reading-requested`, cada uma com DLQ |
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
| Logs | `docker compose logs` | suporte | configure a rotação do Docker (ex.: `max-size: 50m`, `max-file: 5`) |
| Backups | fora do servidor | recuperação | 30 dias |

- **Pedido de um titular** (acesso ou exclusão do contato): o administrador consulta ou apaga o contato na tela do órgão.
- Os logs não guardam senha, token nem conteúdo de documento, só ids.
