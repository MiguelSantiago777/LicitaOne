# Fase 2 — Plano de trabalho (contratos, itens, pedidos, saldo e importação)

**Branch:** `fase-2` (a partir de `fase-1`) · **Início:** 28/10/2026

## Escopo (seção 10 da especificação)
Itens, pedidos, saldo, datas de assinatura e encerramento, importação de planilha assíncrona (RabbitMQ + job + relatório de erros) e outbox implementado. Contatos do órgão também entram, porque ficaram pendentes da Fase 1.

## Decisões de modelo (vão para o ADR 0012)
- **Módulo `contracts`**:
  - `Contract`: licitação, tipo (`CONTRATO`, `EMPENHO`, `DISPENSA`), número, assinatura, vigência, encerramento, valor e observações livres ("o que foi pedido / o que falta pedir");
  - `ContractItem`: lote, descrição, unidade, quantidade contratada e valor unitário;
  - `Order` e `OrderLine`: data, itens e quantidades, com o valor unitário copiado do item.
- **Saldo** calculado a partir dos pedidos, nunca digitado. Vale por item (quantidade) e por contrato (valor).
  - Pedido acima do saldo é recusado (422 `BALANCE_EXCEEDED`).
  - Contrato sem itens aceita pedido só por valor.
- **Licitação ↔ contratos**:
  - Registrar o primeiro contrato leva a licitação para *Contrato assinado*, e o primeiro pedido, para *Em andamento*.
  - Encerrar o último contrato aberto encerra a licitação.
  - Quem chama é o módulo `contracts`, pela API pública `tenders.TenderLifecycle`, na mesma transação. Assim não há ciclo entre módulos.
  - A licitação continua com a máquina de estados e o histórico.
  - O endpoint de transição deixa de aceitar esses três estados.
- **Outbox**: Event Publication Registry do Spring Modulith (JPA) com externalização para o RabbitMQ.
  - Eventos: `ContractSigned`, `OrderRegistered`, `ContractClosed` e `ImportRequested`.
  - Envelope versionado, DLQ por fila e consumidores idempotentes (`processed_message`).
- **Idempotency-Key** nos POSTs de pedido e de importação.
- **Arquivos**: interface `FileStorage` em `shared`, com disco local por padrão e S3 (SeaweedFS no compose) por configuração. A Fase 3 reaproveita.
- **Importação** (módulo `imports`):
  - Fluxo: upload → detecção da linha de cabeçalho → mapeamento de colunas → pré-visualização com erros por linha → confirmação (202 + jobId) → consumidor RabbitMQ → status por polling → relatório CSV baixável.
  - Dois tipos: *licitações e contratos* (aba GERAL) e *itens e pedidos de um contrato* (aba do órgão).
  - Reimportar o mesmo arquivo não duplica, por causa da chave natural de cada linha.

## Etapas
1. ✅ Backend `contracts`: migration, domínio + testes, API, integração com o ciclo da licitação, seed.
2. ✅ Outbox + RabbitMQ: `event_publication` (registro JDBC do Modulith, esquema v2 copiado no Flyway), topologia, DLQ, `processed_messages`, API de reprocessamento.
3. ✅ `FileStorage` local + importação assíncrona + relatório CSV.
4. ✅ Contatos do órgão.
5. ✅ Frontend: aba "Itens e pedidos" no detalhe, registrar contrato/pedido, saldo nas listas, tela de importação real, painel com saldo a pedir.
6. ✅ E2E: assinatura → pedido → saldo; importação com linhas válidas e inválidas. Screenshots 1440/390, checklist da Web Design Guidelines.
7. ✅ ADR 0012, relatório `docs/fases/fase-2.md`.

## Decisões tomadas durante a execução (entram no ADR 0012)
- **Importação desta fase = itens e pedidos de um contrato**, a partir da aba do órgão.
  - A aba GERAL não tem UASG, número do pregão nem datas, e a especificação exige UASG ou PE em toda licitação. Fica como pergunta ao cliente.
  - Reimportar é idempotente: o pedido criado é a diferença entre o "pedido qtd" da planilha e o já registrado. Nada é apagado.
- **Registro de eventos em JDBC**, não JPA como dizia o ADR 0003: o jar traz o esquema oficial em SQL, copiado para `V6__outbox.sql`.
- **Headers AMQP**: o Modulith 2.1.2 descarta os headers configurados na externalização. `type` e `correlationId` vão no envelope (docs/operacao/mensageria.md).
- **Conversor JSON só na publicação**: os consumidores recebem a mensagem crua, e o `ReliableConsumer` lê o envelope. Mensagem ilegível vai para a DLQ com log.
- **`TenderLifecycle` recebe quem fez a ação**, porque o consumidor não tem requisição nem JWT.
- **Pedido dentro do agregado `Contract`**, com `PESSIMISTIC_FORCE_INCREMENT`: a versão sobe na hora (a resposta já sai com ela) e dois pedidos simultâneos não estouram o saldo.
