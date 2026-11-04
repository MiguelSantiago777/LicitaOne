# Fase 9 — Plano de trabalho (follow-up com contexto e resposta do órgão)

**Branch:** `fase-9` (a partir de `fase-8`) · **Plano de:** 04/11/2026 · **Origem:** [proposta 0001](../propostas/0001-ia-no-licitaone.md), item 3 · **Base técnica:** ADRs [0016](../adr/0016-leitura-com-ia.md) e [0017](../adr/0017-pedido-com-ia.md)

**Status:** aguarda validação antes de codar.

## O que existe hoje
- O alerta "contrato sem pedido há 30 dias" cria uma tarefa com um **rascunho de e-mail fixo** (`AlertRules`): o mesmo texto para todo órgão. Ele cita só a data e o objeto. O sistema nunca envia; a pessoa copia ou abre no e-mail.
- **Não existe registro de contato.** Quando o órgão responde ("sem previsão", "o empenho sai em abril"), isso não fica em lugar nenhum do sistema, só na cabeça de quem leu. O histórico da licitação guarda só criação, alteração e troca de situação.

## Escopo

### A. Registro de contato no histórico (base para o resto)
- Na aba Histórico da licitação, um botão **"Registrar contato"** com canal (e-mail, WhatsApp, telefone, reunião), data, resumo e, se houver, o contrato.
- O registro vira um item do histórico: "Contato por WhatsApp · Magé: pedem em março · por <usuário>".
- Sem IA. A IA usa esse histórico como contexto, e a pessoa também precisa dele sem a IA.

### B. Classificar a resposta do órgão
- Em "Registrar contato", a pessoa **cola a resposta** do órgão e clica em **"Entender com IA"**. A IA devolve:
  - **situação**: pedido previsto (com data), sem previsão, pedido a caminho (o texto já é o pedido), não vai pedir, pede documento ou informação, ou outro;
  - **resumo** de uma linha para o histórico;
  - **próxima ação sugerida**: uma tarefa com título e data. Exemplos: "Cobrar Magé sobre o pedido de março", com data em 1º de março; "Mandar certidão negativa", com data para amanhã.
- O formulário abre preenchido. A pessoa confere e salva o registro e, se quiser, a tarefa, pelos endpoints de sempre.
- Se a situação for "pedido a caminho", aparece **"Registrar pedido com IA"** com o mesmo texto (Fase 8).
- Datas relativas ("mês que vem", "depois do carnaval") são convertidas a partir da data do contato. Quando a data é vaga, a IA avisa e sugere uma data, sem afirmá-la.

### C. Follow-up com contexto
- Na tarefa do alerta e no contrato, o botão **"Escrever com IA"** gera o rascunho usando:
  - o contrato: número, assinatura e vigência;
  - o saldo: total e os itens com mais saldo;
  - o último pedido;
  - os **últimos contatos registrados**.
- A pessoa escolhe **e-mail** (formal, com assunto) ou **WhatsApp** (curto) e pode pedir "mais curto" ou "mais formal".
- O texto fica no rascunho da tarefa para editar. O sistema continua **sem enviar nada**: copiar, abrir no e-mail ou abrir no WhatsApp (`wa.me`, quando o contato tiver telefone).
- Regras do texto: não prometer prazo nem preço fora do contrato, não citar regra jurídica e assinar com o nome do responsável.

## Técnico
- **Síncrono, sem fila:** classificar uma resposta e escrever um rascunho são textos curtos, sem documento, e levam alguns segundos. A pessoa espera no diálogo, com indicador e tempo-limite de 60 s. A fila e o outbox continuam para a leitura de documentos. O porquê fica no ADR.
- Mesma porta `DocumentReader` → novo `TextAssistant` no módulo `extraction`, com as implementações de sempre: Claude, desligada, falsa (testes) e demonstração (E2E). A saída é estruturada nos dois casos.
- O registro de contato fica em `tenders`, como novo tipo de evento do histórico, com canal, resumo, contrato opcional e "sugerido pela IA".
- **Texto colado não é guardado.** Só o resumo que a pessoa salvou fica no histórico, como na Fase 8.
- Contexto enviado ao modelo: dados do contrato e os 5 últimos contatos. Nome e contato de servidor vão só se já estiverem no texto que a pessoa colou ou nos contatos do órgão, que são necessários para o rascunho.
- Métricas: `shema.extraction.*` com `kind=RESPOSTA` e `kind=RASCUNHO`.

## Etapas
1. Registro de contato (backend, histórico, diálogo), sem IA.
2. Classificação da resposta: esquema, conferência das datas no domínio, endpoint, preenchimento do diálogo, tarefa sugerida, atalho para o pedido.
3. Follow-up com contexto: esquema, endpoint, botão na tarefa e no contrato, e-mail e WhatsApp.
4. E2E (1440 e 390) com o leitor de demonstração, axe, ADR 0018 e relatório `docs/fases/fase-9.md`.

## Fora desta fase
- Enviar e-mail ou WhatsApp pelo sistema.
- Ler a caixa de entrada sozinho.
- Áudio.

## Dúvidas
1. **Registro de contato sem IA (etapa A)** entra? Ele é a base para a IA ter "a última conversa". Sem ele, o follow-up usa só contrato e pedidos. Recomendo que entre.
2. **WhatsApp:** o rascunho curto com botão "Abrir no WhatsApp" serve? Ele só abre a conversa com o texto, sem enviar.
3. **A tarefa sugerida** vai para o responsável pela licitação ou para quem registrou o contato?
