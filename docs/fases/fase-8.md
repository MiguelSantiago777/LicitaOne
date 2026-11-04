# Fase 8 — Contrato e pedido com IA

**Branch:** `fase-8` (a partir de `fase-7`) · **Data:** 04/11/2026 · **Plano:** [fase-8-plano.md](fase-8-plano.md) · **Decisão:** [ADR 0017](../adr/0017-pedido-com-ia.md)

## O que ficou pronto

### Contrato (fecha o item 1 da proposta)
- **"Preencher com IA" em Registrar contrato / Editar contrato**, com o mesmo componente da ata.
  - Número, assinatura, vigência e valor chegam preenchidos, com a etiqueta **"IA · p. N"**.
  - Os avisos da conferência aparecem no topo. Novo: **órgão do PDF diferente do órgão da licitação**.
- **Itens:** depois de salvar, os itens lidos abrem no editor de itens. Itens que já têm pedido ficam; os demais dão lugar aos lidos.
- **Histórico:** "Contrato … · dados sugeridos pela IA e conferidos por <usuário>", e o mesmo nos itens e nas alterações.

### Pedido a partir de PDF, foto ou mensagem (item 2)
- **"Preencher com IA" em Registrar pedido**, com duas opções:
  - **Documento ou foto:** ofício, AF, empenho ou foto já enviado em Documentos (tipos Empenho ou Outros; PDF, JPEG ou PNG). O mais recente vem escolhido.
  - **Mensagem colada:** e-mail ou WhatsApp. A tela avisa que a mensagem vai para a IA e não fica guardada.
- A IA relaciona cada linha com um item do contrato. O diálogo abre com data, referência (nº da AF/empenho), entrega nas observações e **a quantidade de cada item**, com a etiqueta "IA · p. N".
- **Avisos por item:** quantidade acima do saldo e preço do pedido diferente do contrato (o pedido usa o preço do contrato).
- **Avisos do pedido:** órgão, contrato citado ou data que parecem de outro contrato, e o que a IA disse que ficou ilegível.
- **Linhas que não são do contrato** aparecem em "Ficaram de fora", com descrição e quantidade, e não entram no pedido.
- **Documento de origem:** o pedido guarda o documento lido. A aba mostra o link para baixar, e o histórico registra "documento "…" · dados sugeridos pela IA e conferidos por <usuário>".

### No backend
- **Leitura de pedido** no módulo `extraction`: tabela `order_readings` (V17), fila `shemacrm.extraction.order-reading-requested` com DLQ, endpoints `POST /contracts/{id}/order-readings` (202) e `GET /order-readings/{id}`.
- **Conferência no domínio** (`OrderReadingResult`, sem Spring): item fora da lista, saldo, preço, item repetido (soma), data, órgão e contrato citado.
- **Fotos reduzidas** para 2.576 px no lado maior antes do envio (`ImageShrinker`). Testado também dentro da imagem Alpine do backend.
- **Texto colado apagado** ao terminar a leitura, com sucesso ou falha. Ele nunca vai para os logs.
- **Nova API pública** `ContractOrderContext` (itens, saldo, órgão, assinatura). `contracts` passa a usar `DocumentFiles` para conferir o documento de origem.
- **Métricas** com a etiqueta `kind` (`DOCUMENTO`/`PEDIDO`).
- **Leitor de demonstração:** também devolve um contrato (datas relativas a hoje) e um pedido com os dois primeiros itens do contrato mais uma linha que não é dele.

### Correção de um bug que já existia
- Depois de clicar em **Registrar contrato** no cabeçalho, sair da aba "Itens e pedidos" e voltar reabria o diálogo sozinho. O pedido do cabeçalho agora zera quando a pessoa sai da aba.

### Ajuste depois do teste do usuário (04/11/2026)
- Na Fase 7, a caixa "Preencher com IA" sumia quando a licitação ainda não tinha o PDF, e a pessoa não via botão nenhum. Agora ela aparece sempre que a IA está ligada, com **"Enviar PDF e ler com IA"**: o arquivo vai para Documentos e a leitura começa em seguida.
- O mesmo vale para a ata, o contrato e o pedido. No pedido, o arquivo enviado pela caixa fica em Documentos como "Outros", com o título "Pedido · <nome do arquivo>".

### Diferenças em relação ao plano
- **Medição com pedidos reais não foi feita:** não há exemplos (resposta de 04/11/2026). Ficou como pendência, junto com a das atas.
- O histórico mostra o **nome** do documento de origem. O link para baixar fica na lista de pedidos, e não no histórico.

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 159 testes, verde (JaCoCo do domínio, Spotless e `ModularityTests`). Novos: `OrderReadingResultTest` (correspondência, saldo, preço, soma, órgão, contrato citado, datas), `ImageShrinkerTest`, `ClaudeDocumentReaderTest` (mensagem como documento de texto, foto como imagem, esquema aceito pelo SDK) e `OrderReadingFlowTests` (202 → fila → resultado, texto apagado, tipos recusados, permissão, documento de outra licitação recusado, histórico) |
| Frontend lint, formatação, testes, build | Verde (16 testes; novo `agency-match.spec`) |
| E2E (1440 e 390), compose com o leitor de demonstração | 46 testes, verde. Novo: `order-ai.spec` (contrato pelo PDF com itens, pedido pela AF, pedido por mensagem, documento de origem, histórico, axe nos dois diálogos preenchidos) |

## Checklist Web Design Guidelines (telas novas)
- [x] **Estados:**
  - sem documento de pedido (orienta a enviar em Documentos ou colar a mensagem);
  - lendo (status anunciado);
  - concluída (resumo, avisos e "ficaram de fora");
  - falha (motivo);
  - IA desligada (caixa oculta);
  - contrato sem itens (caixa oculta, porque a IA precisa dos itens).
- [x] **Acessibilidade:**
  - axe sem violações nos diálogos de contrato e de pedido preenchidos, em 1440 e 390;
  - a escolha "Documento ou foto / Mensagem colada" é um `radiogroup` com `aria-checked`;
  - a caixa de texto tem rótulo, e o botão fica `aria-busy`.
- [x] **Celular:** os botões de origem quebram linha, e seletor e botão empilham abaixo de 480 px (mesmo estilo da ata).
- [x] **Texto:** a origem de cada valor aparece; a tela diz que o pedido usa o preço do contrato e que a mensagem não fica guardada.

## Pendências
- **Medir a precisão** com pedidos, atas e contratos reais (script de avaliação), quando houver exemplos e a chave de API.
- **Chave de API** em produção (`ANTHROPIC_API_KEY`). O envio já foi autorizado pelo usuário em 04/11/2026.
- Próximos itens da [proposta 0001](../propostas/0001-ia-no-licitaone.md): follow-up com contexto e classificação da resposta do órgão.
