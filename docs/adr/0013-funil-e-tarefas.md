# 0013 — Funil de vendas (negociações) e tarefas (Fase 4)

**Status:** Aceito · 29/10/2026 · detalha o [0005](0005-modelo-de-dominio.md)

## Decisões

### Funil separado dos quadros livres
- **Módulo `deals`**, com modelo próprio. Ele não reaproveita os quadros do módulo `boards`.
  - A negociação tem valor, prioridade e status, e o status depende da etapa. Os cards dos quadros são livres e não têm nada disso.
  - O padrão é o mesmo dos quadros: agregado com a estrutura (`Pipeline` com as `DealStage`) e itens fora dele (`Deal`), para arrastar não disputar a versão do funil.
- **Etapas configuráveis.** O seed traz as da seção 5.5: Sem contato, Contato feito, Identificação do interesse, Apresentação, Proposta enviada, Fechado ganho e Fechado perdido.
  - Cada etapa tem um tipo: `ABERTA`, `GANHA` ou `PERDIDA`. O tipo não muda depois de criada.
  - Só ADMIN cria, renomeia, reordena e exclui etapas: o funil é da equipe toda.
  - Etapa com negociação não sai, e o funil sempre mantém pelo menos uma etapa de cada tipo.
- **Status = tipo da etapa** (*Em andamento*, *Ganha*, *Perdida*). Fica gravado na negociação só para filtrar sem juntar tabelas.
  - Entrar numa etapa fechada registra `closedAt`. Passar de ganha para perdida mantém a data, e voltar para uma etapa aberta limpa.
  - "Nova" é só etiqueta do card: negociação em andamento criada há menos de 7 dias.
- **Órgão ou licitação de origem, pelo menos um** (checado no domínio e no banco). O caso típico é a carona: órgão novo e a licitação cuja ata será replicada.
  - Ao escolher a licitação, o front sugere o valor dela.
  - Registrar a carona de fato (limite de 4 órgãos e 200%) fica para a Fase 3, que traz as atas.
- **Permissões:** ADMIN altera tudo; COMERCIAL altera as negociações suas e as sem responsável (a regra das licitações, ADR 0011), inclusive para mover; LEITURA só consulta.
- **Concorrência:** a versão da negociação protege o conteúdo, e mover não mexe nela (`@OptimisticLock(excluded)` em etapa, posição, status e data de fechamento). Mover trava o funil (`PESSIMISTIC_WRITE`) só durante a renumeração.
- **Filtro em memória.** `GET /deals/pipeline` traz todas as negociações do funil e filtra no serviço (busca, status, "minhas", órgão, licitação).
  - Um funil tem centenas de negociações, não milhares.
  - A busca alcança o nome do órgão, que mora em outro módulo.
  - Contador e soma de cada etapa seguem o filtro.
  - A visão de lista usa a mesma resposta. Se o funil crescer muito, troca por consulta paginada no banco.
- **Apagar** só negociação sem tarefa aberta (422 `DEAL_HAS_OPEN_TASKS`). As tarefas concluídas ficam, sem o vínculo (`on delete set null`).

### Tarefas
- **Módulo `tasks`.** A `Task` tem título, vencimento com data e hora, responsável, negociação e licitação opcionais, observações e conclusão (quando e por quem).
- **"Atrasada" é calculada** (aberta e vencida pela hora atual), nunca gravada. A lista filtra por situação: abertas (inclui atrasadas, como no print 3.1), atrasadas, concluídas ou todas.
- **Origem `MANUAL` ou `ALERTA`, com `alert_key` única**, já pronta para a Fase 5: o mesmo alerta (contrato + regra + período) nunca gera duas tarefas. Tarefa de alerta não é apagada, só concluída: se fosse apagada, a chave sumiria e o alerta criaria a tarefa de novo.
- **Conclusão em massa tudo ou nada.** Se uma das tarefas for de outra pessoa, nenhuma é concluída (403), e a mensagem diz qual tarefa barrou. Concluir de novo não muda quem nem quando concluiu, e a resposta informa quantas foram concluídas agora.
- **Permissões:** ADMIN e COMERCIAL criam, inclusive para outra pessoa (delegar é normal). Alterar, concluir, reabrir e apagar seguem a regra das licitações.
- **Valor na lista:** o da negociação ligada; sem negociação, o da licitação.

### Limites entre módulos
- `deals` depende de `tasks` (API pública `TaskOverview`: tarefas abertas e a atrasada mais antiga por negociação, para a faixa do card).
- `tasks` precisa mostrar o título, o órgão e o valor da negociação. Para não criar ciclo, `tasks` **declara** a interface `TaskSubjects` e `deals` a **implementa** (`DealTaskSubjects`). A dependência fica num sentido só, e o `ModularityTests` confere.
- Órgãos são lidos pela API pública nova `tenders.AgencyDirectory`.
- As chaves estrangeiras entre tabelas de módulos diferentes (como `tasks.deal_id`) são integridade de banco. Elas não criam dependência de código.

## Consequências
- O print 3.1 (lista de tarefas) e o 3.2 (kanban de negociações) saíram dos dados de exemplo e passaram a gravar no banco.
- As abas *Tarefas* e *Negociação* do detalhe da licitação passaram a funcionar.
- A Fase 5 só precisa publicar os eventos de alerta e criar as tarefas com `Task.fromAlert`. O modelo e o índice único já existem.
