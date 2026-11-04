# 0018 — Registro de contato, resposta do órgão e follow-up com IA (Fase 9)

**Status:** Aceito · 04/11/2026 · origem: [proposta 0001](../propostas/0001-ia-no-licitaone.md), item 3 · base: ADRs [0016](0016-leitura-com-ia.md) e [0017](0017-pedido-com-ia.md)

## Contexto
O follow-up de pedidos era um e-mail fixo gerado pelo alerta "contrato sem pedido" (ADR 0015). O texto era o mesmo para todo órgão e não citava saldo nem conversas anteriores. A resposta do órgão ("sem previsão", "o empenho sai em abril") não ficava registrada em lugar nenhum.

## Decisões

### Registro de contato, sem IA
- Tabela `tender_contacts` (V18) no módulo `tenders`: canal, data, resumo (até 1.000 caracteres), situação opcional, contrato opcional, "sugerido pela IA", autor.
- Aparece na linha do tempo da aba Histórico, junto com os eventos da licitação, dos contratos e da ata.
- Registra quem pode alterar a licitação. Apaga quem registrou, ou um administrador. Não há edição: um contato errado é apagado e registrado de novo.
- O contrato citado **não é conferido contra a licitação** no backend. Isso exigiria que `tenders` dependesse de `contracts`, que já depende de `tenders`. A tela só oferece os contratos da própria licitação, e o banco confere que o contrato existe. É o único dado afetado, e ele só serve de contexto.
- Nova API pública `TenderContacts.recent`, usada pelo follow-up.

### Resposta do órgão e follow-up: síncronos, sem fila
- `POST /tenders/{id}/contacts/interpretation` e `POST /tenders/{id}/follow-up-draft` chamam o modelo **na hora** e devolvem a sugestão. Não gravam nada.
- São textos curtos, sem documento, e a pessoa espera no diálogo com indicador. A fila e o outbox (ADR 0016) servem para documentos, que podem levar minutos e precisam de nova tentativa. Aqui, se a chamada falhar, a pessoa clica de novo.
- O serviço (`FollowUpAssistant`) **não abre transação**, para a chamada ao modelo não segurar conexão do banco. O contexto é lido pelas APIs públicas, cada uma com a sua transação curta.
- Falha da IA vira 422 `AI_FAILED` com a mensagem de sempre ("IA ocupada ou fora do ar").

### O que o modelo recebe
- **Resposta:** a resposta colada, como documento de texto separado das instruções e tratado como dado, não como ordem (como na Fase 8). Junto vão a data e o canal do contato e o contexto da licitação.
- **Follow-up:**
  - órgão e objeto;
  - contratos abertos (ou só o escolhido) com valor, pedido, saldo, vigência e último pedido;
  - os 5 itens de maior saldo em valor;
  - os 5 últimos contatos;
  - o canal (e-mail ou WhatsApp) e o tom (normal, mais curto ou mais formal);
  - a assinatura de quem está escrevendo.
- **Regras no prompt:** não inventar prazo, preço nem condição; não citar lei; continuar a conversa a partir do último contato. WhatsApp tem até 5 linhas.

### Conferência no domínio
- `ResponseReading`:
  - situação fora da lista vira `OUTRO`;
  - data prevista anterior ao contato é descartada, com aviso;
  - data vaga gera aviso;
  - "pedido previsto" sem data gera aviso;
  - a tarefa nunca fica para antes de hoje; sem data, ela cai na data prevista ou em 7 dias.
- `FollowUpDraft`:
  - o assunto só existe no e-mail;
  - o texto vai até 4.000 caracteres;
  - avisa quando o WhatsApp passa de 700 caracteres.

### A tarefa sugerida vai para o responsável pela licitação
- É o mesmo destino das tarefas de alerta. O campo aparece preenchido e pode ser trocado na hora (decisão de 04/11/2026, registrada no [plano](../fases/fase-9-plano.md)).

### O sistema continua sem enviar nada
- O rascunho aparece editável, com **Copiar**, **Abrir no e-mail** (`mailto:` com os e-mails dos contatos do órgão) e **Abrir no WhatsApp** (`wa.me`).
- O `wa.me` usa o primeiro telefone dos contatos, com 55 na frente quando o número vem sem o código do país, ou abre para a pessoa escolher a conversa.
- O rascunho não é guardado. Para guardar o que foi falado, a pessoa registra o contato.

## Consequências
- **A resposta colada não fica no banco nem nos logs.** Só o resumo que a pessoa salvou fica no histórico.
- **Mais dados vão para a Anthropic:** além de documentos, vão o contexto da licitação e os resumos dos contatos. Isso está coberto pela autorização de 04/11/2026.
- **Métricas:** `shema.extraction.finished` e `shema.extraction.tokens` com `kind=RESPOSTA` e `kind=RASCUNHO`.
- Uma chamada que passe de 60 s esbarra no tempo-limite do proxy (nginx). Com texto curto e esforço médio, isso não deve acontecer. Se acontecer, a pessoa vê o erro e tenta de novo.
