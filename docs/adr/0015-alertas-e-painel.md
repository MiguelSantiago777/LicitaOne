# 0015 — Alertas e painel (Fase 5)

**Status:** Aceito · 29/10/2026 · detalha o [0003](0003-eventos-e-outbox.md) e o [0006](0006-alertas-por-email.md)

## Decisões

### Onde fica cada coisa
- **Módulo `notifications`** ("Alertas"):
  - as regras (domínio, sem Spring);
  - o job;
  - a tabela `alerts`;
  - os consumidores;
  - a API `/api/v1/alerts`.
- Os candidatos vêm pelas APIs públicas dos donos dos dados:
  - `contracts.ContractFollowUp` (contratos abertos sem pedido);
  - `tenders.TenderLifecycle.awaitingContractWonOnOrBefore` e `AgencyDirectory.contactEmails`;
  - `pricerecords.PriceRecordCalendar` (vigências).
- A tarefa é criada pela API pública `tasks.AlertTasks`. O teste de módulos continua sem violações.

### As três regras (seção 5.4)
| Regra | Quando | Chave de idempotência | Tarefa |
|---|---|---|---|
| `CONTRATO_SEM_PEDIDO` | contrato aberto, assinado há 30 dias ou mais, sem nenhum pedido | `CONTRATO_SEM_PEDIDO:<contrato>` | vence no dia; **rascunho de e-mail ao órgão** |
| `ATA_VENCENDO` | ata a 90, 60 ou 30 dias do vencimento | `ATA_VENCENDO:<ata>:<vigência>:<faixa>` | uma por faixa; só a faixa mais próxima sai |
| `LICITACAO_SEM_CONTRATO` | vencida (ou aguardando assinatura) há X dias sem contrato | `LICITACAO_SEM_CONTRATO:<licitação>` | **nasce atrasada**: vence no dia em que o prazo passou |

Detalhes das regras:
- **Faixas da ata:** se o job enxerga uma ata pela primeira vez já com 20 dias, não gera três tarefas de uma vez. A vigência entra na chave: se a ata for prorrogada, as faixas recomeçam.
- **Prazos configuráveis** (`shema.alerts.*`) e mostrados na tela Alertas.
  - O "X dias" da licitação sem contrato não vem da especificação. Começa em 30 e aparece marcado **"Prazo a confirmar"**.
  - O texto da ata manda conferir a prorrogação no edital. O sistema não calcula prazo legal (seção 11).

### Job, eventos e filas
- **Job diário** às 7h (horário de Brasília, `shema.alerts.cron`) com **ShedLock** (JDBC, hora do banco):
  - com várias instâncias, só uma roda;
  - o agendado segura o lock por 5 minutos, contra relógios desencontrados.
- **"Verificar agora"** (só ADMIN) usa o mesmo lock. Se já estiver rodando, responde 409.
- **O job só publica.** Numa transação, grava cada alerta em `alerts` (`on conflict do nothing` na chave) e publica `alert.raised` pelo outbox apenas para as chaves novas.
- **Um evento só**, `AlertRaised` (exchange `shemacrm.alerts`, tipo `alert.raised`):
  - os três eventos da especificação são os valores do campo `rule`;
  - o evento leva tudo o que os consumidores precisam (título, vencimento, responsável, rascunho, aviso).
- **Duas filas para o mesmo evento, cada uma com DLQ:**
  - `create-task`: cria a tarefa;
  - `notify-team`: e-mail interno.
  - Um SMTP fora do ar não segura a tarefa: o aviso vai para a DLQ, a tarefa sai, e o aviso é reprocessado depois.
- **Três barreiras contra duplicata:**
  1. a chave nova no job;
  2. `processed_messages` no consumidor;
  3. `alert_key` único na tarefa.
- **Conclusão automática:** a fila `contract-facts` escuta os eventos de contrato que já existiam desde a Fase 2.
  - O pedido registrado conclui "contrato sem pedido".
  - O contrato assinado conclui "licitação sem contrato".
  - A tarefa fica "concluída sozinha", sem autor.

### E-mails
- **Rascunho ao órgão** na própria tarefa (`email_to`, `email_subject`, `email_body`). O sistema nunca envia: a tela tem "Copiar texto" e "Abrir no e-mail" (`mailto:`).
  - Destinatários: os contatos do órgão com e-mail. Sem contato, o campo fica vazio e a tela avisa.
- **Aviso interno** (ADR 0006), por SMTP (Mailpit no compose):
  - vai para o responsável pela licitação; sem responsável ativo, para os administradores (resposta 5 assumida);
  - traz o saldo a pedir e o link da licitação;
  - `shema.alerts.mail-enabled=false` desliga o aviso, e a tarefa continua saindo.
- O aviso sai dentro da transação que marca a mensagem como processada. Ele só se repete se o banco falhar depois de o SMTP aceitar.

### Painel e métricas
- **Painel (5.7)**:
  - valor em carteira, saldo a pedir, tarefas atrasadas e atas vencendo;
  - contratos sem pedido, contados por contrato e com o mesmo prazo do alerta (`/contracts/without-orders`);
  - carteira por situação;
  - top órgãos pelo valor das licitações vencidas (`/tenders/top-agencies`);
  - alertas recentes e atas vencendo.
- **Métricas de negócio** (seção 7), cada uma registrada pelo módulo dono:
  - `shema_contracts_without_order`, `shema_price_records_expiring`, `shema_tasks_overdue` (`notifications`);
  - `shema_imports_failed` (`imports`);
  - `shema_messaging_dlq_messages{queue}` (`shared.messaging`);
  - os contadores `shema_alerts_raised_total{rule}` e `shema_alerts_notices_total{result}`.
  - As gauges consultam o banco no máximo uma vez por minuto (`CachedGauge`).
- **Grafana**: o painel "LicitaOne · negócio" é provisionado no perfil `observability` do compose.

## Consequências
- Cumpre os itens da definição de pronto:
  - "contratos assinados há mais de 30 dias sem pedido geram tarefa e rascunho de e-mail";
  - "uma mensagem com falha vai para a DLQ e dá para reprocessar; nenhum alerta gera tarefa duplicada".
  - Os dois têm teste de integração e E2E.
- A tabela `alerts` é o histórico do que foi avisado. Apagar uma linha faz o alerta voltar na próxima verificação: é assim que se força um reaviso.
- Um contrato sem pedido gera um alerta só, mesmo que continue sem pedido. Lembretes periódicos (a cada 30 dias) ficam como evolução, se o cliente pedir.
