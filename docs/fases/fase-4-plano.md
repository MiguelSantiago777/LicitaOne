# Fase 4 — Plano de trabalho (funil de vendas e tarefas)

**Branch:** `fase-4` (a partir de `fase-4-quadros`) · **Início:** 29/10/2026

## Escopo (seções 5.5 e 5.6 da especificação)
Os quadros livres já foram entregues (`docs/fases/fase-4-quadros.md`). Falta:
- **Funil de vendas (Negociações)**: kanban com etapas configuráveis, soma e contador por coluna, arrastar, troca kanban/lista, filtros "Minhas negociações" e status, card com status, valor, responsável, prioridade, faixa de tarefa atrasada e "Criar tarefa".
- **Tarefas**: lista no estilo do print 3.1 (tarefa, status, data e hora, responsável, negociação/licitação, valor), criação manual, conclusão em massa, filtros e ordenação por data. A criação automática por alerta é da Fase 5; o modelo já nasce com `origem` e chave de idempotência.

## Decisões de modelo (vão para o ADR 0013)
- **Módulo `deals`**, separado dos quadros livres:
  - `DealStage` (etapa): nome, posição e tipo (`ABERTA`, `GANHA`, `PERDIDA`). Seed na migration com as etapas da seção 5.5: Sem contato, Contato feito, Identificação do interesse, Apresentação, Proposta enviada, Fechado ganho, Fechado perdido.
  - `Deal` (negociação): título, órgão (opcional), licitação de origem (opcional), etapa, posição, valor, prioridade (1 a 3), responsável, observações.
  - O status vem do tipo da etapa: *Em andamento*, *Ganha* ou *Perdida*. "Nova" é só etiqueta visual, para negociação criada há menos de 7 dias.
  - Os quadros livres não servem para o funil. A negociação tem valor, prioridade e status próprios, e o status depende da etapa.
- **Etapas**: só ADMIN cria, renomeia, reordena ou exclui (e só exclui etapa vazia). Tem que existir pelo menos uma etapa de cada tipo.
- **Permissão nas negociações**: ADMIN altera tudo; COMERCIAL altera as suas e as sem responsável (a mesma regra das licitações); LEITURA só consulta.
- **Módulo `tasks`**:
  - `Task`: título, vencimento (data e hora), responsável, negociação (opcional), licitação (opcional), observações, `origem` (`MANUAL`, `ALERTA`), chave de idempotência (única, para os alertas da Fase 5), conclusão (quando e por quem).
  - "Atrasada" é calculado (aberta e vencida); não fica gravado.
  - **Conclusão em massa** é tudo ou nada: se o usuário não puder alterar uma das tarefas, nenhuma é concluída.
- **Sem ciclo entre módulos**:
  - `deals` lê as tarefas pela API pública `tasks.TaskOverview` (tarefas abertas e atrasadas por negociação).
  - `tasks` mostra o título e o valor da negociação por uma interface que ele mesmo declara (`tasks.TaskSubjects`), implementada por `deals`. Assim `tasks` não depende de `deals`.
- **Órgão** lido pela API pública nova `tenders.AgencyDirectory`.
- **Carona** (negociação ganha ligada a uma ata) fica para a Fase 3, que traz as atas.

## Etapas
1. Backend `deals`: migration, domínio e testes sem Spring, API, seed.
2. Backend `tasks`: migration, domínio e testes, API (lista com filtros, criação, edição, conclusão em massa, reabrir), seed.
3. Integração: faixa de tarefa atrasada no card, valor e título da negociação na lista de tarefas, teste de fluxo com Postgres real.
4. Frontend: Negociações reais (kanban e lista, filtros na URL, arrastar, diálogo, criar tarefa, etapas para o admin), Tarefas reais (lista, filtros, ordenação, conclusão em massa, diálogo) e abas *Tarefas* e *Negociação* no detalhe da licitação.
5. E2E (1440 e 390), screenshots e checklist da Web Design Guidelines.
6. ADR 0013 e relatório `docs/fases/fase-4.md`.
