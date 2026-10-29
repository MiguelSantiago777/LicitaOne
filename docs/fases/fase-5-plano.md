# Fase 5 — Plano de trabalho (alertas e painel)

**Branch:** `fase-5` (a partir de `fase-3`) · **Início:** 29/10/2026

## Escopo (seções 5.4, 5.7, 7 e 12 da especificação; ADR 0006)
- **Três alertas diários** (seção 5.4), com prazos configuráveis:
  - contrato assinado há mais de 30 dias sem pedido → tarefa + rascunho de e-mail ao órgão;
  - ata vencendo em 90, 60 e 30 dias → tarefa;
  - licitação vencida há X dias sem contrato assinado → tarefa já atrasada.
- **Job agendado com ShedLock** (lock no PostgreSQL), que só publica eventos. **Consumidores idempotentes** no RabbitMQ criam a tarefa e mandam o aviso interno por e-mail.
- **DLQ e reprocessamento**: uma mensagem que falha vai para a DLQ e volta para a fila depois que a causa é corrigida, sem tarefa duplicada.
- **Rascunho de e-mail ao órgão**, com "copiar" e "abrir no cliente de e-mail". Nunca é enviado pelo sistema.
- **Painel (5.7)**:
  - totais por estado, valor em carteira e saldo a pedir;
  - contratos sem pedido há mais de 30 dias, atas vencendo, tarefas atrasadas;
  - top órgãos por valor.
- **Métricas de negócio** no Prometheus (contratos sem pedido, atas a vencer, tarefas atrasadas, importações com falha, mensagens na DLQ) e um painel no Grafana.

## Decisões de modelo (vão para o ADR 0015)
- **Módulo `notifications`** ("Alertas"):
  - as regras dos alertas e o job;
  - a tabela `alerts`: um registro por chave, o que já foi avisado;
  - os dois consumidores.
  - Lê os candidatos pelas APIs públicas de `contracts`, `tenders` e `pricerecords`, e cria tarefas pela API pública de `tasks`.
- **Chave de idempotência** = regra + assunto + período:
  - `CONTRATO_SEM_PEDIDO:<contrato>`: um alerta por contrato;
  - `LICITACAO_SEM_CONTRATO:<licitação>`: um alerta por licitação;
  - `ATA_VENCENDO:<ata>:<vigência>:<90|60|30>`: um por faixa. Se a vigência mudar (prorrogação), as faixas recomeçam. Se o job só enxergar a ata já com 20 dias, avisa só a faixa de 30.
  - O job grava a chave em `alerts` (`on conflict do nothing`) e só publica se ela for nova. A tarefa também tem `alert_key` único, e o consumidor usa `processed_messages`. Três barreiras contra duplicata.
- **Um evento só**, `AlertRaised` (exchange `shemacrm.alerts`, tipo `alert.raised`), com a regra no campo `rule`. Os três eventos da especificação (ContratoSemPedido30Dias, AtaVencendo, LicitacaoVencidaSemContrato) são os valores de `rule`.
  - O evento carrega tudo o que os consumidores precisam (título, vencimento, responsável, rascunho, saldo), para eles não consultarem outros módulos.
- **Duas filas, cada uma com DLQ**:
  - `shemacrm.alerts.create-task`: cria a tarefa de origem ALERTA, com o rascunho;
  - `shemacrm.alerts.notify-team`: e-mail interno (ADR 0006) para o responsável pela licitação, com o saldo a pedir. Sem responsável, vai para os administradores (resposta 5 assumida).
  - Uma falha no e-mail não impede a tarefa, e o contrário também vale.
- **Rascunho de e-mail na tarefa** (para, assunto e texto). O "para" vem dos contatos do órgão com e-mail. Sem contato, fica vazio e a tela avisa.
- **Tarefa concluída sozinha**:
  - quando o fato que gerou o alerta deixa de valer: o pedido registrado conclui "contrato sem pedido", e o contrato assinado conclui "licitação sem contrato";
  - escuta os eventos de contrato que já existem (Fase 2), sem consultar nada.
- **ShedLock** (JDBC, tabela `shedlock`):
  - cron diário às 7h (horário de Brasília), configurável;
  - "Verificar agora" para administradores, com o mesmo lock (409 se já estiver rodando).
- **Prazos configuráveis** em `shema.alerts.*` e mostrados na tela. ⚠ "X dias" da licitação sem contrato começa em 30 e precisa de validação do cliente. Os outros prazos vêm da especificação.
- **Métricas**: cada módulo registra as próprias (gauges com cache de 1 min); a DLQ fica em `shared.messaging`. Assim `notifications` não precisa conhecer `imports`.

## Decisões tomadas durante a execução (entraram no ADR 0015)
- A tarefa de "licitação sem contrato" nasce atrasada: vence no dia em que o prazo passou.
- O ShedLock usa colunas `timestamp` sem fuso (V15), como pede a documentação para a hora do banco.
- As gauges de negócio usam referência forte (o Micrometer guarda por referência fraca).

## Etapas
1. ✅ Backend: ShedLock, `alerts`, regras no domínio (sem Spring) e job publicando no outbox.
2. ✅ Backend: consumidores (tarefa com rascunho, e-mail interno), conclusão automática, "Verificar agora" e API de alertas.
3. ✅ Backend: indicadores do painel (contratos sem pedido, top órgãos), métricas de negócio e o painel no Grafana.
4. ✅ Testes: domínio das regras e fluxo de ponta a ponta, com Mailpit no Testcontainers, falha de SMTP → DLQ → reprocessar e sem duplicata.
5. ✅ Frontend:
   - página *Alertas* (lista, prazos, "Verificar agora", mensagens com falha e reprocessar);
   - rascunho na tarefa;
   - Painel completo.
6. ✅ E2E (1440 e 390): contrato assinado há mais de 30 dias → verificar → tarefa com rascunho → e-mail no Mailpit. Mais screenshots e o checklist da Web Design Guidelines.
7. ✅ ADR 0015 e relatório `docs/fases/fase-5.md`.
