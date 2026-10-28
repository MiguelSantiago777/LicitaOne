# Fase 2 — Contratos e pedidos

**Branch:** `fase-2` (a partir de `fase-1`) · **Data:** 28/10/2026 · **Decisões:** [ADR 0012](../adr/0012-contratos-pedidos-e-importacao.md)

## O que ficou pronto (funcionando de verdade, gravando no banco)
- **Contratos** (e empenhos, e dispensas):
  - uma licitação pode ter vários;
  - número, assinatura, vigência, valor e campo livre "o que foi pedido / o que falta pedir".
  - Registrar o primeiro contrato assina a licitação, o primeiro pedido a põe em andamento e encerrar o último contrato encerra a licitação.
- **Itens, pedidos e saldo**:
  - saldo por quantidade de cada item e por valor, sempre calculado;
  - pedido acima do saldo é recusado com mensagem clara;
  - pedido lançado por engano pode ser apagado;
  - dois pedidos simultâneos não estouram o saldo.
- **Importação de planilha**:
  - Funciona com a aba de cada órgão da planilha de controle do cliente, como ela é hoje: cabeçalho na linha 5, título e lotes mesclados, fórmulas, totais soltos. Também lê `.csv` com `;` e vírgula decimal.
  - Fluxo: colunas reconhecidas pelo título → prévia linha a linha (novo, alterado, sem mudança, aviso, erro) → processamento em segundo plano (RabbitMQ, resposta 202) → relatório CSV que abre no Excel.
  - Reimportar a mesma planilha não duplica nada.
- **Outbox transacional + RabbitMQ**:
  - eventos de contrato e de importação saem só depois do commit, num envelope versionado com correlationId;
  - consumidor com ack manual, tentativas com backoff, idempotência e DLQ;
  - reprocessamento da DLQ por API de admin e procedimento em [`docs/operacao/mensageria.md`](../operacao/mensageria.md).
- **Telas**:
  - aba "Itens e pedidos" no detalhe da licitação;
  - "Registrar contrato" no cabeçalho da licitação vencida;
  - histórico juntando licitação e contratos;
  - "pedido / falta pedir" nas listas de contratos;
  - saldo a pedir no painel;
  - página Importação real.
- **Contatos do órgão** (pendência da Fase 1): nome, cargo, e-mail e telefone, no painel de edição do órgão.
- **Seed** com contratos para as licitações assinadas. A SEFAZ tem itens e pedidos como na planilha: R$ 237.000,00, com R$ 94.800,00 pedidos.

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 81 testes, verde. Inclui: regras de contrato, saldo e importação sem Spring; API com Postgres e RabbitMQ reais (Testcontainers); outbox de ponta a ponta; DLQ e reprocessamento; limites dos módulos; cobertura do domínio acima de 80% |
| Frontend `npm test` / `npm run lint` / build de produção | 13 testes verdes, sem erros de lint, dentro dos budgets |
| E2E (desktop 1440 e celular 390), contra o build de produção no compose | 20 testes, verde, duas rodadas seguidas |

Fluxos E2E novos:
- licitação vencida → registrar contrato → registrar pedido → falta pedir atualizado → histórico;
- importar planilha com linhas válidas e inválidas → prévia com erro na linha → resultado;
- registrar contrato sem data mostra a regra.

Screenshots novos em `e2e/screenshots/{desktop,phone}/`: `22-itens-e-pedidos`, `23-registrar-pedido`, `24-importar-planilha`, além das telas da Fase 1.

## Bugs pegos pelos testes nesta fase
1. O BOM do relatório CSV saía depois do cabeçalho, porque o `CSVPrinter` escreve o cabeçalho no construtor. O Excel abriria os acentos errados.
2. O formatador do Java trocava escapes Unicode (`﻿`, ` `) por caracteres invisíveis no código. Viraram constantes numéricas.
3. O conversor JSON global fazia o listener falhar antes do consumidor ler a mensagem, e uma mensagem ilegível não ia para a DLQ com log. O conversor agora vale só na publicação.
4. A versão do contrato só subia no commit (`OPTIMISTIC_FORCE_INCREMENT`). A resposta voltaria com a versão antiga, e a próxima edição daria 409.
5. O Spring Modulith 2.1.2 descarta os headers da externalização AMQP. Documentado; `type` e `correlationId` vão no envelope.

## Checklist Web Design Guidelines (telas novas)
| Item | Situação |
|---|---|
| Rótulos de formulário | ✅ `label for` em todos os campos; nos editores de linhas (itens, contatos, quantidades do pedido), `aria-label` com o número da linha |
| Foco e teclado | ✅ painéis prendem o foco e fecham com Esc; apagar pedido pede confirmação na própria linha, sem diálogo do navegador |
| Erros anunciados | ✅ erros em `role="alert"`; total do pedido e resultado da importação em `aria-live` |
| Quantidade acima do saldo | ✅ campo marcado com `aria-invalid` enquanto digita; o backend recusa com a mesma mensagem |
| Barra de progresso | ✅ `role="progressbar"` com valor e rótulo ("40% pedido") |
| Estados vazio, erro e carregando | ✅ sem contrato (com ação "Registrar contrato"), em disputa, perdida; erro com "Tentar de novo"; botões "Salvando…", "Lendo planilha…", "Importando…" |
| Números tabulares e moeda pt-BR | ✅ `tabular-nums`; R$ com 2 casas; valor unitário com até 4; quantidades "1.200" |
| Responsivo (390 px) | ✅ itens viram lista de duas linhas; pedidos quebram a linha; mapeamento de colunas em uma coluna; sem rolagem horizontal da página |
| `prefers-reduced-motion` | ✅ o indicador de "Importando…" desacelera |
| Arquivo | ✅ seletor de arquivo em português; o input nativo fica oculto, mas acessível dentro do rótulo |

## Pendências
- **Perguntas novas ao cliente** (`docs/decisoes.md`, itens 6 e 7):
  - colunas para importar a aba GERAL;
  - aditivo de contrato acima do saldo.
- **Nulabilidade no OpenAPI** (ADR 0011) continua pendente.
- **Arquivos ficam em disco local** (volume do compose). O S3 entra na Fase 3, junto com os documentos.
- **Os eventos de contrato já saem para o RabbitMQ**, mas ainda não há consumidor. Os alertas e notificações da Fase 5 vão consumir.
- **Job que ficar na DLQ** (erro inesperado depois das tentativas) continua "na fila" até o reprocessamento. O procedimento está documentado.
- **Ainda não há repositório remoto**: sem PR nem CI no GitHub, mas os mesmos comandos foram rodados localmente.
