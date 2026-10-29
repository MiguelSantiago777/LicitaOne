# Fase 5 — Alertas e painel

**Branch:** `fase-5` (a partir de `fase-3`) · **Data:** 29/10/2026 · **Plano:** `docs/fases/fase-5-plano.md` · **Decisões:** ADR 0015

## O que ficou pronto
- **Três alertas diários** (7h, horário de Brasília), cada um vira tarefa de origem "Alerta":
  - **contrato sem pedido** há 30 dias ou mais, com **rascunho de e-mail ao órgão** ("Visto que participamos do certame…");
  - **ata vencendo** a 90, 60 e 30 dias (uma tarefa por faixa);
  - **licitação vencida sem contrato** há 30 dias: a tarefa já nasce atrasada (prazo a confirmar com o cliente).
- **Job com ShedLock**: roda uma vez só mesmo com várias instâncias. Só publica eventos no outbox; os consumidores no RabbitMQ criam a tarefa e mandam o e-mail.
- **Aviso interno por e-mail** (ADR 0006): vai para o responsável pela licitação (ou para os administradores), com o saldo a pedir e o link. No compose, chega no Mailpit (`http://localhost:18025`).
- **Rascunho na tarefa**:
  - "Copiar texto" e "Abrir no e-mail";
  - destinatários tirados dos contatos do órgão; sem contato, a tela avisa onde cadastrar;
  - o sistema nunca envia ao órgão.
- **Tarefa concluída sozinha**: o pedido registrado conclui "contrato sem pedido", e o contrato assinado conclui "licitação sem contrato". A tarefa mostra que foi o sistema que concluiu.
- **Página Alertas** (menu Comercial):
  - as regras com os prazos;
  - agenda e última verificação;
  - o que foi gerado, com a tarefa criada e para quem foi o e-mail, e filtro por regra na URL.
  - Para administradores: **Verificar agora** e **Mensagens com falha → Reprocessar** (DLQ).
- **Painel (5.7)**:
  - valor em carteira, saldo a pedir, tarefas atrasadas e atas vencendo;
  - faixa de contratos sem pedido (agora por contrato, com o valor a pedir);
  - carteira por situação, próximas sessões e maiores contratos;
  - **top órgãos por valor**, **alertas recentes** e **atas vencendo**.
  - Saiu o cartão "Chegando nas próximas fases".
- **Métricas de negócio** no Prometheus (contratos sem pedido, atas a vencer, tarefas atrasadas, importações com falha, mensagens em cada DLQ, alertas gerados e avisos mandados). O painel "LicitaOne · negócio" já vem no Grafana (`docker compose --profile observability up -d`, porta 13000).

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 124 testes, verde. Novos: `AlertRulesTest` (as três regras, faixas da ata, prorrogação, prazo, textos do rascunho e do aviso), `TaskTest` (rascunho, conclusão pelo sistema) e `AlertFlowTests` com o Mailpit no Testcontainers. Limites dos módulos verificados |
| Frontend lint / testes / build de produção | Verde |
| E2E (1440 e 390) contra o compose | 36 testes, verde |

`AlertFlowTests` cobre:
- verificar → tarefa com rascunho → e-mail no Mailpit → segunda verificação sem duplicata → tarefa de alerta não pode ser apagada → o pedido conclui a tarefa;
- licitação vencida sem contrato: tarefa atrasada, concluída quando o contrato é assinado;
- **SMTP fora** (container pausado): a tarefa sai, o aviso vai para a DLQ, o reprocessamento entrega e não há tarefa duplicada;
- só ADMIN verifica; todos veem as regras; a última verificação tem a hora certa;
- as métricas continuam com valor depois de uma coleta de lixo.

O E2E novo (`alerts.spec.ts`):
- cria uma licitação vencida há 60 dias e o contrato assinado há 40;
- clica em "Verificar agora" e espera a tarefa na licitação (sem sleep);
- confere o rascunho e o link `mailto:` e busca o aviso no Mailpit pela API;
- registra o pedido e vê a tarefa "Concluída sozinha".

Screenshots novos ou atualizados em `e2e/screenshots/{desktop,phone}/`: `01-painel`, `32-alertas` e `33-tarefa-rascunho`.

## Bugs encontrados e corrigidos
1. **Hora da última verificação com 3 horas a mais.** O ShedLock com a hora do banco grava em UTC sem fuso, e a coluna era `timestamptz`. Foi corrigido em uma migração nova (V15), porque a V14 já estava aplicada no compose. Um teste confere a hora.
2. **Métricas de negócio como NaN.** O Micrometer guarda a gauge por referência fraca, e o objeto sumia na coleta de lixo. As gauges agora usam referência forte, e um teste chama a coleta de lixo antes de conferir.
3. **Top órgãos estourava o cartão do painel** com nome longo: a coluna do grid crescia com o conteúdo. O nome agora corta com reticências e mostra o nome inteiro ao passar o mouse.
4. **Alertas no celular** rolavam de lado e cortavam o e-mail. No celular, regra e data vão para baixo do título, e o endereço quebra a linha.

## Checklist Web Design Guidelines (telas novas)
| Item | Situação |
|---|---|
| Rótulos e nomes acessíveis | ✅ abas com `role="tab"`/`aria-selected`; "Reprocessar" com o nome da fila; o rascunho é uma região com título; o texto do e-mail tem `aria-label` e é focável para rolar pelo teclado |
| Estados anunciados | ✅ resultado da verificação e "Texto copiado." em `role="status"`; erros em `role="alert"` |
| Ação arriscada | ✅ o sistema nunca envia e-mail ao órgão; "Abrir no e-mail" deixa a pessoa revisar; reprocessar é idempotente e diz isso na tela |
| Estados vazio, carregando e erro | ✅ "Nenhum alerta gerado" (com o porquê), "Carregando…", erro com "Tentar de novo"; órgão sem contato com e-mail explica onde cadastrar |
| Números e datas pt-BR | ✅ R$ sem centavos nos indicadores e com centavos nas listas, datas dd/MM/yyyy HH:mm |
| Responsivo (390 px) | ✅ cartões das regras em uma coluna; tabela de alertas sem rolagem lateral; painel sem rolagem horizontal |
| Cor não é a única pista | ✅ regras com rótulo de texto além da cor; "Prazo a confirmar" com ícone e texto |

## Pendências
- **Resposta do cliente**:
  - quem recebe o aviso (pergunta 5): está o responsável, ou os administradores;
  - o prazo X da licitação vencida sem contrato: está 30 dias, configurável.
- **Lembretes repetidos** (o contrato continua sem pedido 30 dias depois do primeiro alerta): hoje é um alerta por contrato. Fica como evolução, se o cliente pedir.
- **SMTP de produção** (provedor, remetente, SPF/DKIM): fica para a Fase 6, junto com o restante da operação.
- O banco do compose acumula as licitações de teste do E2E em Petrópolis (aparecem no top órgãos). Elas somem com `docker compose down -v` e o seed sobe de novo.
