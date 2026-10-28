# 0012 — Contratos, pedidos, saldo, outbox e importação (Fase 2)

**Status:** Aceito · 28/10/2026 · detalha o [0003](0003-eventos-e-outbox.md) e o [0005](0005-modelo-de-dominio.md)

## Decisões

### Contratos e saldo
- **Módulo `contracts`.** O agregado `Contract` reúne itens (`ContractItem`) e pedidos (`ContractOrder` + `OrderLine`).
  - Uma licitação pode ter vários contratos, empenhos ou dispensas (ADR 0005).
  - O pedido fica **dentro** do agregado, porque a regra de saldo precisa ver todos os pedidos.
- **Saldo sempre calculado**, nunca digitado:
  - por item: quantidade contratada − pedida;
  - por contrato: valor − soma dos pedidos.
  - Pedido acima do saldo de um item, ou do valor do contrato, é recusado (422 `BALANCE_EXCEEDED`).
  - Contrato sem itens recebe pedido só por valor.
- **Números:** quantidade com até 3 casas, valor unitário com até 4 (preço de pregão), total da linha arredondado para centavos (meio para cima, como as planilhas).
- **Concorrência:** itens e pedidos usam `PESSIMISTIC_FORCE_INCREMENT` na versão do contrato.
  - A versão sobe na hora, então a resposta já sai com ela.
  - A linha fica travada até o commit: dois pedidos simultâneos não passam pela mesma checagem de saldo (o segundo recebe 409).
- **Idempotency-Key** no POST de pedido e na confirmação da importação, guardada em `idempotency_keys` (escopo, chave e usuário).
- Pedido lançado por engano pode ser apagado; o saldo volta e o histórico registra.

### Licitação ↔ contratos
- O módulo de contratos avisa a licitação pela API pública `tenders.TenderLifecycle`, na mesma transação.
  - Primeiro contrato → *Contrato assinado*.
  - Primeiro pedido → *Em andamento*.
  - Último contrato encerrado → *Encerrado*.
- `contracts` depende de `tenders` e nunca o contrário: não há ciclo (o `ModularityTests` verifica).
- A transição manual para esses três estados passa a dar 422 `MANAGED_BY_CONTRACTS`. Os campos `signedAt`, `ordersStartedAt` e `closedAt` da licitação viram "primeira assinatura", "primeiro pedido" e "último encerramento".
- `TenderLifecycle` recebe quem fez a ação (`CurrentUser`), porque a importação roda no consumidor da fila, sem requisição nem JWT.

### Outbox e mensageria (implementação do ADR 0003)
- **Registro de eventos em JDBC**, e não em JPA como previa o ADR 0003. O jar do Spring Modulith traz o esquema oficial em SQL, copiado em `V6__outbox.sql`, e a criação automática fica desligada.
- Eventos marcados com `@IntegrationEvent` saem para o RabbitMQ depois do commit, com publisher confirms e reenvio na subida.
  - Formato: envelope `{eventId, type, version, occurredAt, correlationId, payload}`.
  - O `eventId` nasce na publicação e é gravado com o evento, para que um reenvio seja reconhecido pelo consumidor.
- **Consumo** (`ReliableConsumer`):
  - ack manual, prefetch 10;
  - 4 tentativas com backoff exponencial (1, 2 e 4 s);
  - marca em `processed_messages` na mesma transação do efeito;
  - mensagem ilegível ou esgotada vai para a DLQ.
  - Procedimento de reprocessamento em `docs/operacao/mensageria.md` e pela API `/api/v1/admin/dead-letters`.
- O conversor JSON vale só para a publicação. Os consumidores leem a mensagem crua, para que um envelope inválido chegue ao `ReliableConsumer` e seja logado.
- **Problema conhecido:** o Modulith 2.1.2 descarta os headers configurados na externalização AMQP. `type` e `correlationId` vão no envelope.

### Importação
- **Escopo desta fase:** itens e pedidos de um contrato, a partir da aba do órgão na planilha de controle.
  - A aba GERAL não tem UASG, número do pregão nem datas, que a especificação exige para cadastrar uma licitação. Virou pergunta ao cliente (`docs/decisoes.md`).
- **Fluxo:** upload → abas com cabeçalho e colunas reconhecidos pelo título → prévia linha a linha → confirmação (202) → consumidor da fila → status por consulta → relatório CSV.
- **Mesclagem** (`ItemImportPlan`, regra pura do domínio, usada pela prévia e pela gravação):
  - Uma linha casa com o item de mesmo lote, descrição (sem acento e sem caixa) e valor unitário.
  - Linhas repetidas casam na ordem em que aparecem.
  - O pedido lançado é a diferença entre o "pedido" da planilha e o já registrado.
  - Nada é apagado, e reimportar não muda nada.
- O consumidor grava o contrato numa transação própria. Se a regra do contrato recusar, nada muda e o job fica com falha e o motivo, sem novas tentativas.
- **`FileStorage`** em `shared`: disco local (volume no compose). O S3 entra na Fase 3, com os documentos.

### Contatos do órgão
- Tabela e endpoints próprios, fora das listas de órgãos (LGPD: só o mínimo).
- E-mail validado e telefone guardado com DDD.
- A substituição da lista usa a versão do órgão.

## Consequências
- O frontend mostra o saldo nas listas com uma consulta em lote (`/contracts/totals`), sem carregar os agregados.
- Alertas e notificações (Fase 5) consomem os eventos de `shemacrm.contracts` sem mudar o módulo de contratos.
