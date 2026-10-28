# 0003 — Eventos entre módulos e outbox transacional

**Status:** Aceito · implementado na Fase 2, com o registro em JDBC em vez de JPA (ver [0012](0012-contratos-pedidos-e-importacao.md))

## Contexto
Alertas, importação e notificações precisam reagir a fatos de outros módulos (`ContratoAssinado`, `PedidoRegistrado`, ...) sem acoplar módulos e sem publicar um evento cuja transação depois falhou.

## Decisão
- Eventos de domínio publicados com o `ApplicationEventPublisher` e consumidos com `@ApplicationModuleListener`.
- **Outbox pelo Event Publication Registry do Spring Modulith** (JPA, tabela `event_publication`): o evento é gravado na mesma transação do dado e marcado como concluído só depois que o listener termina. Publicações incompletas são reenviadas na subida e por um job.
- **Externalização para o RabbitMQ** com `spring-modulith-events-amqp` (`@Externalized`) para os eventos que precisam sair do processo: importação, alertas, pós-processamento de documentos.
- Topologia declarada em código: exchanges `topic`, filas duráveis, DLQ por fila, nomes `shemacrm.<contexto>.<evento>`, envelope JSON versionado (`eventId`, `type`, `version`, `occurredAt`, `correlationId`).
- Consumidores idempotentes (tabela `processed_message` com `messageId` + consumidor), ack manual, prefetch configurado, retry com backoff exponencial e procedimento documentado para reprocessar a DLQ.

## Consequências
- Na Fase 0, as dependências de eventos JPA/AMQP do Modulith foram **retiradas** do `pom.xml`. O Hibernate valida a tabela `event_publication`, e ela só será criada (por migration Flyway) quando o outbox entrar, na Fase 2.
- O RabbitMQ já sobe no compose e aparece no health do backend desde a Fase 0.
