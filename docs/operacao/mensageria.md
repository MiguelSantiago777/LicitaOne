# Mensageria: outbox, filas e DLQ

## Como um evento sai do sistema
1. O caso de uso publica o evento de domínio (`ApplicationEventPublisher`) dentro da transação.
2. O Spring Modulith grava o evento na tabela `event_publication`, na **mesma transação** do dado. Se a transação falhar, o evento não existe.
3. Depois do commit, os eventos marcados com `@IntegrationEvent` são enviados ao RabbitMQ dentro de um envelope:
   ```json
   {"eventId": "…", "type": "contract.signed", "version": 1, "occurredAt": "…", "correlationId": "…", "payload": {…}}
   ```
   O `eventId` é gravado com o evento. Por isso, um reenvio sai com o mesmo id.
4. O evento é marcado como concluído (`completion_date`) só depois que o RabbitMQ confirma o recebimento (publisher confirms).
5. Eventos pendentes, por exemplo de uma queda antes da confirmação, são reenviados quando a aplicação sobe (`republish-outstanding-events-on-restart`).

Para ver eventos pendentes:
```sql
select event_type, publication_date, completion_attempts
from event_publication
where completion_date is null
order by publication_date;
```

## Exchanges e filas
| Exchange | Routing keys | Filas (consumidor) |
|---|---|---|
| `shemacrm.contracts` | `contract.signed`, `contract.order-registered`, `contract.closed` | `shemacrm.alerts.contract-facts` (`contract.signed` e `contract.order-registered`: conclui sozinha a tarefa do alerta resolvido) |
| `shemacrm.imports` | `import.requested` | `shemacrm.imports.import-requested` (processa a planilha) |
| `shemacrm.alerts` | `alert.raised` | `shemacrm.alerts.create-task` (cria a tarefa com o rascunho) e `shemacrm.alerts.notify-team` (e-mail interno) |
| `shemacrm.dlx` | nome da fila de origem | `<fila>.dlq` de cada fila |

A topologia é declarada no código (`Topology.queue(...)`): exchanges topic duráveis, filas duráveis, e cada fila com a sua DLQ.

## Consumo
- Ack manual e prefetch 10 (`spring.rabbitmq.listener.simple`).
- Cada mensagem é tentada até **4 vezes**, com espera de 1 s, 2 s e 4 s entre as tentativas (`MessagingConfig.consumerRetry`).
- O efeito e a marca em `processed_messages` (`eventId` + consumidor) são gravados na mesma transação. Assim, uma mensagem entregue duas vezes só tem efeito uma vez.
- Esgotadas as tentativas, ou se o envelope for ilegível, a mensagem é rejeitada sem voltar para a fila e o RabbitMQ a move para `<fila>.dlq`. O erro fica no log, com o `correlationId`.

## Alertas (Fase 5, ADR 0015)
- O job diário (7h, `shema.alerts.cron`) roda sob o lock `alert-scan` do ShedLock (tabela `shedlock`): com várias instâncias, só uma roda. "Verificar agora" (tela Alertas, administrador, ou `POST /api/v1/alerts/run`) usa o mesmo lock e responde 409 se já estiver rodando.
- O job grava cada alerta novo em `alerts` (chave única) e publica `alert.raised` pelo outbox. Rodar de novo não repete nada.
- Para ver o que já foi avisado:
  ```sql
  select rule, title, raised_at, task_id is not null as tarefa, notified_to
  from alerts order by raised_at desc limit 20;
  ```
- Falha no SMTP: o aviso vai para `shemacrm.alerts.notify-team.dlq` depois das tentativas, e a tarefa sai mesmo assim (fila separada). Com o SMTP de volta, reprocesse.

## Reprocessar a DLQ
0. Pela tela: **Alertas → Mensagens com falha → Reprocessar** (só administradores; o quadro aparece quando há mensagem parada). A métrica `shema_messaging_dlq_messages{queue}` e o painel "LicitaOne · negócio" do Grafana mostram o mesmo número.
1. Ache a causa no log: procure por `enviada para a DLQ` e pelo `correlationId` da mensagem.
2. Corrija a causa (dado, configuração ou deploy da correção).
3. Veja quantas mensagens estão paradas (perfil ADMIN):
   ```
   GET /api/v1/admin/dead-letters
   ```
4. Devolva as mensagens à fila de origem:
   ```
   POST /api/v1/admin/dead-letters/shemacrm.imports.import-requested/replay?max=100
   ```
   As mensagens voltam para o fim da fila e passam de novo pelo consumidor. Se alguma já tinha sido processada, a marca em `processed_messages` impede que o efeito se repita.
5. Para inspecionar uma mensagem antes de reprocessar, use a interface do RabbitMQ (`http://localhost:15683`, porta `RABBITMQ_UI_PORT` do `.env`): *Queues* → `<fila>.dlq` → *Get messages*, com *Ack mode* em "Nack message requeue true", para a mensagem continuar na fila.

## Problema conhecido
O Spring Modulith 2.1.2 descarta os headers configurados em `EventExternalizationConfiguration.headers(...)` no transporte AMQP: o mapa é calculado, mas não é passado ao `convertAndSend`. Por isso o `type` e o `correlationId` vão só no envelope, que é o que os consumidores leem.
