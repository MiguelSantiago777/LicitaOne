# Fase 4 — Funil de vendas e tarefas

**Branch:** `fase-4` (a partir de `fase-4-quadros`) · **Data:** 29/10/2026 · **Plano:** `docs/fases/fase-4-plano.md` · **Decisões:** ADR 0013

Os quadros livres (*Organização mensal*, *Licitações vencidas*) já tinham sido entregues antes: `docs/fases/fase-4-quadros.md`.

## O que ficou pronto (gravando no banco)
- **Negociações (funil de vendas)**, no lugar dos dados de exemplo:
  - kanban com as etapas da seção 5.5, contador e soma por etapa;
  - arrastar entre etapas; no celular e no teclado, o campo "Etapa" do diálogo move a negociação;
  - troca kanban/lista. Busca, "Minhas negociações" e status ficam na URL, com etiquetas removíveis;
  - card com status (Nova, Em andamento, Ganha, Perdida), prioridade em estrelas, órgão, licitação de origem, responsável e valor;
  - card com faixa rosa da tarefa atrasada mais antiga (data e título) ou o botão "Criar tarefa";
  - diálogo de criar e editar: órgão (com cadastro rápido), licitação de origem (sugere o valor), valor, prioridade, responsável, etapa e observações;
  - **Etapas** (só o administrador): renomear, reordenar, criar e excluir. Etapa com negociação não sai, e sempre sobra uma etapa de cada tipo;
  - as colunas têm a mesma altura, para dar para soltar em qualquer altura de uma etapa curta.
- **Tarefas**, no estilo do print 3.1:
  - abas Abertas, Atrasadas (com contadores), Concluídas e Todas. Busca, "Minhas tarefas", ordem pela data e página ficam na URL;
  - colunas Tarefa, Status (Atrasada / Aberta em dia / Concluída), Data e hora, Responsável, Negociação ou licitação (com link) e Valor total;
  - conclusão em massa (selecionar a página toda), tudo ou nada;
  - diálogo de criar e editar com negociação e licitação ligadas; concluir, reabrir e apagar (só as manuais).
- **Detalhe da licitação**: as abas *Tarefas* e *Negociação* agora funcionam. Dá para criar tarefa e negociação dali, já ligadas à licitação.
- **Seed**: 10 negociações (caronas das atas do seed em outros órgãos, incluindo uma ganha e uma perdida) e 13 tarefas, com datas relativas a hoje. Sempre há tarefa atrasada para mostrar.
- **Pronto para a Fase 5**: a tarefa tem `origem` (`MANUAL`/`ALERTA`) e chave de alerta única. Tarefa de alerta não pode ser apagada.

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 96 testes, verde. Novos: regras do funil e da negociação sem Spring (`PipelineTest`), regras da tarefa (`TaskTest`) e o fluxo pela API com Postgres real (`DealFlowTests`). O fluxo cobre a faixa de atraso no card, o filtro com soma por etapa, mover sem mudar a versão, o conflito de versão, a recusa de apagar com tarefa aberta, a conclusão em massa tudo ou nada e as permissões de COMERCIAL, LEITURA e ADMIN. Limites dos módulos verificados |
| Frontend lint / testes / build de produção | verde |
| E2E (1440 e 390) contra o compose | 24 testes, verde, duas rodadas seguidas |

Fluxo E2E novo (`deals.spec.ts`):
1. Cria a negociação a partir da ata da SEFAZ, e o valor vem preenchido.
2. "Criar tarefa" no card com data vencida faz a faixa de atraso aparecer.
3. A negociação muda de etapa: arrastando no desktop, pelo diálogo no celular.
4. A busca filtra a soma da etapa.
5. Na lista de tarefas, a tarefa aparece como atrasada, com a negociação e o valor. Ela é concluída em massa e aparece em Concluídas.
6. O teste apaga a negociação no fim.

Screenshots novos em `e2e/screenshots/{desktop,phone}/`: `06-negociacoes` e `07-tarefas` (agora com dados reais), `25-negociacoes-lista`, `26-negociacao`, `27-licitacao-negociacoes` e `28-licitacao-tarefas`.

## Bugs pegos nesta fase
1. **Foco perdido depois de salvar a negociação**: o foco ia para o card antes de o funil recarregado chegar. Agora espera os dados novos.
2. **Não dava para soltar numa etapa curta**: as colunas tinham a altura do conteúdo. Arrastando de uma coluna longa, a área de soltar da coluna curta ficava acima da tela. Agora todas as colunas têm a altura da maior.
3. **Faixa de atraso escondia a data**: com título longo, a data (o que importa) era cortada. A data agora vem primeiro.
4. **Tabela de tarefas cortada em 1440 px**: a coluna de valor ficava fora da tela. A data ficou mais curta e a coluna de vínculo, mais estreita.
5. **Screenshots tirados antes dos dados**: o teste de telas agora espera um texto que só aparece com os dados carregados.

## Checklist Web Design Guidelines (telas novas)
| Item | Situação |
|---|---|
| Rótulos de formulário | ✅ `label for` em todos os campos; busca e filtro de status com rótulo oculto |
| Foco e teclado | ✅ card do funil focável (Enter abre); painéis prendem o foco e fecham com Esc; o foco volta ao card depois de salvar |
| Alternativa ao arrastar | ✅ campo "Etapa" no diálogo; etapas reordenadas por botões com rótulo ("Mover … para a direita") |
| Estado dos botões | ✅ "Minhas negociações/tarefas" e Kanban/Lista com `aria-pressed`; ordenação com `aria-sort` |
| Erros e avisos | ✅ erros em `role="alert"`; "1 tarefa concluída." em `role="status"`; exclusões pedem confirmação na própria tela |
| Estados vazio, carregando e só leitura | ✅ "Nada com este filtro", "Nenhuma tarefa atrasada", "Carregando…"; quem não pode alterar vê o diálogo desabilitado e o aviso do motivo |
| Prioridade sem depender de cor | ✅ estrelas com `role="img"` e rótulo "Prioridade Alta" |
| Números tabulares e moeda pt-BR | ✅ `tabular-nums`; R$ 1.323.890,26; datas dd/MM/yyyy HH:mm no fuso de São Paulo |
| Responsivo (390 px) | ✅ funil com rolagem horizontal só dentro do quadro; tabela de tarefas com rolagem própria; sem rolagem horizontal da página |

## Pendências
- **Carona** (negociação ganha ligada a uma ata, limite de 4 órgãos e 200%) depende das atas da Fase 3.
- **Tarefas automáticas** (contrato sem pedido, ata vencendo, vencida sem contrato) entram na Fase 5. O modelo já está pronto.
- **Painel**: o contador de tarefas atrasadas entra com os indicadores da Fase 5.
- **Fase 3** (atas, documentos, S3) continua sendo a próxima.
