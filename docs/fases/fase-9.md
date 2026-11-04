# Fase 9 — Registro de contato, resposta do órgão e follow-up com IA

**Branch:** `fase-9` (a partir de `fase-8`) · **Data:** 04/11/2026 · **Plano:** [fase-9-plano.md](fase-9-plano.md) · **Decisão:** [ADR 0018](../adr/0018-follow-up-com-ia.md)

## O que ficou pronto

### Registro de contato (sem IA)
- **"Registrar contato"** na aba Histórico: canal (e-mail, WhatsApp, telefone, reunião, outro), data, situação, contrato e o que foi conversado.
- O contato aparece na linha do tempo, com a situação em destaque. Quem registrou, ou um administrador, pode apagar, com confirmação na própria linha.
- Com um contrato aberto só, ele já vem escolhido.

### Entender a resposta do órgão com IA
- No mesmo diálogo, a pessoa cola a resposta e clica em **"Entender com IA"**. A IA preenche:
  - a **situação**: pedido previsto, sem previsão, pedido a caminho, não vai pedir, pede documento ou outro;
  - o **resumo** do que foi conversado;
  - e **sugere a tarefa** (título e data), para o responsável pela licitação, trocável.
- Datas relativas viram datas. Data vaga, data anterior ao contato e "pedido previsto sem data" geram aviso.
- Se a resposta já é o pedido, o botão **"Registrar pedido com IA"** leva para "Itens e pedidos" e abre o pedido em "Mensagem colada" com o texto (Fase 8).
- A resposta colada não é guardada. Só o resumo salvo fica no histórico, com "sugeridos pela IA e conferidos por <usuário>".

### Follow-up com contexto
- **"Escrever com IA"** em dois lugares: na tarefa (com licitação, enquanto aberta) e no botão **Follow-up** de cada contrato.
- O texto cita o contrato, o saldo, os itens com mais saldo, o último pedido e os últimos contatos registrados.
- **E-mail** (com assunto) ou **WhatsApp** (curto). Os botões **"Mais curto"** e **"Mais formal"** reescrevem o texto.
- O texto fica editável, com **Copiar**, **Abrir no e-mail** e **Abrir no WhatsApp** (`wa.me`, com o telefone do órgão quando houver). O sistema não envia nada.

### No backend
- `tender_contacts` (V18) e endpoints `GET/POST /tenders/{id}/contacts` e `DELETE /tenders/{id}/contacts/{contactId}`.
- `POST /tenders/{id}/contacts/interpretation` e `POST /tenders/{id}/follow-up-draft`: chamadas diretas ao modelo, sem fila e sem transação aberta durante a chamada.
- Conferência no domínio: `ResponseReading` (datas, situação, tarefa nunca no passado) e `FollowUpDraft` (assunto só no e-mail, teto do texto, aviso de WhatsApp longo).
- APIs públicas novas e ampliadas:
  - `TenderContacts.recent`;
  - `AgencyDirectory.contactPhones`;
  - `ContractOrderContext.openForTender`, agora com vigência, valor, pedido, saldo e último pedido.
- Esquemas da API com nomes próprios (`TenderContactRequest`, `AgencyResponseRequest`, `FollowUpDraftRequest`). Sem isso, o contato da licitação colidia com o contato do órgão no OpenAPI e o cliente gerado usava o tipo errado.
- Leitor de demonstração: resposta "pedem no mês que vem" e rascunhos fixos de e-mail e WhatsApp.

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 169 testes, verde. Novos: `ResponseReadingTest` (datas, tarefa, avisos, rascunho), `ClaudeDocumentReaderTest` (resposta como documento de texto; canal, tom e assinatura no follow-up; esquemas aceitos pelo SDK) e `FollowUpFlowTests` (contato registrado e listado; contexto com saldo, último pedido, itens e contatos; resposta entendida sem gravar nada; falha da IA em 422; permissões; data no futuro; apagar) |
| Frontend lint, formatação, testes, build | Verde (19 testes; novo `follow-up-writer.spec` do número de WhatsApp) |
| E2E (1440 e 390), compose com o leitor de demonstração | 48 testes, verde. Novo: `follow-up.spec` (resposta → situação, resumo e tarefa; contato no histórico; tarefa na aba Tarefas; follow-up por WhatsApp e por e-mail; axe nos dois diálogos) |

## Checklist Web Design Guidelines (telas novas)
- [x] **Estados:**
  - IA desligada (as caixas somem e o registro manual continua);
  - lendo e escrevendo (status anunciado);
  - avisos;
  - falha (mensagem);
  - contato salvo e tarefa com falha (avisa sem duplicar o contato);
  - órgão sem e-mail ou sem telefone (explica o que fazer).
- [x] **Acessibilidade:**
  - axe sem violações nos diálogos de contato e de follow-up preenchidos, em 1440 e 390;
  - a escolha de canal é `radiogroup`;
  - os campos têm rótulo;
  - os botões ficam `aria-busy`;
  - apagar contato pede confirmação na própria linha, sem diálogo do navegador.
- [x] **Celular:** as grades do diálogo de contato viram uma coluna abaixo de 480 px; os botões quebram linha.
- [x] **Texto:** a tela diz que a resposta não fica guardada e que o LicitaOne não envia por você.

## Diferenças em relação ao plano
- O rascunho do follow-up **não é guardado na tarefa**: aparece para editar e mandar. Para registrar o que foi falado, a pessoa registra o contato. Assim, o histórico tem uma origem só.
- O caminho "pedido a caminho" → "Registrar pedido com IA" não está no E2E, porque o leitor de demonstração sempre responde "pedido previsto". Ele foi conferido pelo build e pela revisão do fluxo.

## Pendências
- **Medir com respostas e pedidos reais**, quando houver exemplos e a chave de API.
- Próximos itens da [proposta 0001](../propostas/0001-ia-no-licitaone.md): ofício de carona sob medida e os itens da fronteira ("Pergunte ao LicitaOne", resumo do dia).
