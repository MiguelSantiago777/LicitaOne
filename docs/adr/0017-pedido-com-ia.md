# 0017 — Pedido a partir de PDF, foto ou mensagem com IA (Fase 8)

**Status:** Aceito · 04/11/2026 · origem: [proposta 0001](../propostas/0001-ia-no-licitaone.md), item 2 · base: [ADR 0016](0016-leitura-com-ia.md)

## Contexto
O órgão manda o pedido de vários jeitos: ofício, autorização ou ordem de fornecimento (AF), nota de empenho, foto do papel, e-mail ou WhatsApp. Hoje a pessoa lê e digita a quantidade de cada item no diálogo de pedido. A Fase 7 já lê atas e contratos com o mesmo modelo de "a IA sugere, a pessoa confere".

## Decisões

### Uma leitura serve um contrato
- A pessoa parte do contrato aberto ("Registrar pedido" → "Preencher com IA"). Pela pesquisa de 04/11/2026, o empenho e a AF saem de uma ata ou contrato e vão para um fornecedor só (registrado em [plano da Fase 8](../fases/fase-8-plano.md)).
- O modelo recebe os **itens do contrato numerados** (lote, descrição, unidade, preço e saldo) e informa, por linha, o número do item que corresponde, ou `""` se nenhum corresponde com segurança. O prompt manda não forçar a correspondência.
- A linha sem item vira aviso ("fica fora do pedido"). Órgão, número de contrato e data que parecem de outro contrato também viram avisos. A IA nunca troca de contrato sozinha.

### A IA só sugere, e o saldo continua no domínio
- O resultado vai para `order_readings` e é conferido em `OrderReadingResult` (domínio, sem Spring):
  - linha sem item;
  - quantidade acima do saldo;
  - preço do pedido diferente do preço do contrato (o pedido usa sempre o do contrato);
  - o mesmo item em duas linhas (as quantidades são somadas);
  - data no futuro ou antes da assinatura;
  - órgão diferente, comparando palavras significativas sem acento ("Magé" casa com "Município de Magé"; "Prefeitura" e "Municipal" não contam);
  - número de contrato citado diferente do contrato aberto.
- O pedido é registrado pelo `POST /contracts/{id}/orders` de sempre, com `Idempotency-Key`. Saldo, data e versão continuam validados por `Contract.placeOrder`.

### Fontes: documento ou texto colado
- **Documento** já enviado em Documentos, dos tipos Empenho ou Outros, em PDF, JPEG ou PNG, até 20 MB.
- **Fotos** são reduzidas no backend (`ImageShrinker`) para 2.576 px no lado maior, que é o máximo que o modelo usa, e regravadas em JPEG quando passam de 7 MB. A API aceita até 10 MB por imagem em base64. Isso economiza envio e tokens sem perder leitura.
- **Texto colado** (até 20 mil caracteres) vai como documento de texto, separado das instruções. O prompt diz que o conteúdo é dado e não ordem, e pede aviso se o texto tentar dar instruções à IA.
  - O texto pode ter nome e telefone de servidor. Por isso ele **fica no banco só até a leitura terminar**: `finish` e `fail` apagam `source_text`. Ele nunca vai para os logs.

### Documento de origem no pedido
- `contract_orders.source_document_id` guarda o documento lido. O contrato confere que ele é da mesma licitação.
- A aba mostra o link para baixar o documento, e o histórico registra: "Pedido de R$ … · AF 77/2026 · documento "…" · dados sugeridos pela IA e conferidos por …".
- Com isso, `contracts` passa a depender da API pública de `pricerecords` (`DocumentFiles`). `extraction` depende da nova API pública `ContractOrderContext`, com itens, saldo, órgão e assinatura. Não há ciclo, e o teste de modularidade confere.

### Mesmo assíncrono da Fase 7
- `POST /api/v1/contracts/{id}/order-readings` responde 202 e a leitura sai pela fila `shemacrm.extraction.order-reading-requested`, com DLQ.
- O consumidor chama o modelo fora de transação (`consumeOutsideTransaction`) e lê os itens e o saldo **na hora da leitura**.
- As falhas que a pessoa entende encerram a leitura com o motivo, sem nova tentativa.
- Métricas: `shema.extraction.finished` e `shema.extraction.tokens` ganham a etiqueta `kind` (`DOCUMENTO` ou `PEDIDO`).

### Contrato na tela (fecha a Fase 7)
- "Preencher com IA" no formulário do contrato usa o mesmo componente da ata (`DocumentAiFill`, com `kind`).
- Os campos preenchidos são número, assinatura, vigência e valor. Há um aviso quando o órgão lido não é o da licitação.
- Os itens lidos abrem no editor de itens depois de salvar. Contratos, itens e pedidos aceitam `suggestedByAi`, que vai para o histórico.

## Consequências
- **Dados saem do sistema:** documentos e mensagens vão para a Anthropic. O usuário autorizou em 04/11/2026 ([decisões](../decisoes.md)).
- **Precisão não medida com pedidos da Shema:** não há exemplos reais. A medição fica para quando houver pedidos, junto com a de atas e contratos.
- **Foto torta ou de lado:** a redução não corrige a orientação EXIF. O modelo costuma ler assim mesmo; se virar problema, girar a foto pelo EXIF antes do envio.
- **Pedido que mistura contratos:** só a parte do contrato aberto entra. O resto aparece como "fica fora do pedido", e a pessoa registra no outro contrato.
