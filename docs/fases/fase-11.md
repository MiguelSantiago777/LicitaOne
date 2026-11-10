# Fase 11 — "Pergunte ao LicitaOne"

**Branch:** `fase-11` (a partir de `fase-10`) · **Data:** 04/11/2026 · **Plano:** [fase-11-plano.md](fase-11-plano.md) · **Decisão:** [ADR 0020](../adr/0020-assistente-de-perguntas.md)

## O que ficou pronto

### Na tela
- Botão **"Perguntar"** no topo de todas as telas. Ele abre o painel "Pergunte ao LicitaOne", que ocupa a tela no celular, e só aparece com a IA configurada.
- Painel vazio: explica o que dá para perguntar e mostra 4 exemplos clicáveis.
- Enquanto pensa, o painel mostra o passo atual ("Consultando contratos e saldos…").
- A resposta tem três partes:
  - texto curto;
  - **lista clicável** (tipo, nome e um dado-chave, como saldo, vencimento ou etapa), que abre a licitação na aba certa, o órgão, as tarefas ou o funil;
  - **"Ver as consultas feitas"**, com os filtros usados e quantas linhas cada consulta trouxe.
- **Perguntas de seguimento** ("e quais atas aceitam carona?") usam as respostas anteriores da conversa.
- "Nova conversa" recomeça. **Fechar o painel ou abrir um item apaga a conversa.**
- No rodapé: "X de 50 perguntas hoje". Enter envia; Shift+Enter quebra a linha.

### No backend
- Módulo `assistant`:
  - `AssistantService`, com o laço, as rodadas, a conferência dos ids, os limites e as métricas;
  - `AssistantTools`, com 10 consultas de leitura e `responder`;
  - `ClaudeAssistantModel`, `DemoAssistantModel` e o modelo desligado;
  - `AssistantUsageRepository`.
- Endpoints:
  - `GET /api/v1/assistant`: ligado, limite, uso do dia e exemplos;
  - `POST /api/v1/assistant/questions`: 202 com o id;
  - `GET /api/v1/assistant/questions/{id}`: passos e resposta, só para quem perguntou.
- V19 `assistant_usage`: só a contagem diária por pessoa.
- Consultas públicas novas nos módulos:
  - `TenderSearch`, `AgencyDirectory.search`, `ContractOrderContext.allOpen`;
  - `PriceRecordSearch`, `TaskSearch`, `DealSearch`;
  - `UserDirectory.searchActive`.
- `SHEMA_ASSISTANT_DAILY_LIMIT` (padrão 50), no compose e no `.env.example`.

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 181 testes, verde. Novos: `AssistantAnswerTest` (só ids das consultas viram link, ordem, repetidos, teto de itens e de texto), `ClaudeAssistantModelTest` (ferramentas, raciocínio, histórico e volta dos resultados) e `AssistantFlowTests`. O `AssistantFlowTests` cobre: pergunta pela API com modelo roteirizado, perfil Leitura perguntando, contexto com hoje, quem pergunta e a conversa anterior, id inventado fora da lista com aviso, consulta registrada com os filtros, outra pessoa sem acesso à pergunta, IA fora do ar, limite do dia sem contar a recusada, cada consulta contra o seed e parâmetros inválidos voltando como erro |
| Frontend lint, formatação, testes, build | Verde (22 testes) |
| E2E (1440 e 390), compose com o modo de demonstração | 52 testes, verde. Novo: `assistant.spec` (exemplo, resposta com lista, "ver as consultas", pergunta de seguimento, axe, abrir um item fecha o painel e apaga a conversa) |

## Checklist Web Design Guidelines (telas novas)
- [x] **Estados:**
  - IA desligada (o botão some);
  - painel vazio com exemplos;
  - pensando (passo anunciado em `status`, botão `aria-busy`);
  - resposta sem itens;
  - avisos;
  - falha (`alert`);
  - limite do dia (mensagem do servidor).
- [x] **Acessibilidade:**
  - painel é `dialog` com título;
  - conversa em lista ordenada;
  - "Você:" para leitor de tela;
  - itens são botões com tipo, nome e detalhe;
  - "Ver as consultas" com `aria-expanded`;
  - axe sem violações em 1440 e 390.
- [x] **Celular:** o painel ocupa a tela, o botão do topo vira só ícone, e os itens quebram linha.
- [x] **Texto:** o subtítulo diz que o assistente só consulta.

## Diferenças em relação ao plano
- **A tela consulta o andamento a cada segundo, em vez de SSE**, sem rota especial no nginx. É mais robusto: não depende do proxy repassar a conexão aberta e usa o mesmo cliente HTTP do resto. O porquê está no ADR.
- **Até 2 perguntas ao mesmo tempo por pessoa:** com 1, duas abas abertas se bloqueavam.
- **Ajuda sobre o uso do sistema e perguntas sobre os PDFs** continuam fora, como no plano.

## Pendências
- **Medir com a chave e perguntas reais:** as respostas do modelo de verdade ainda não foram vistas, só as do modo de demonstração. Sugestão: um roteiro de 20 perguntas da equipe, conferidas contra as telas.
- Próximo item da [proposta 0001](../propostas/0001-ia-no-licitaone.md): o **resumo do dia**, que pode reaproveitar as consultas do assistente.
