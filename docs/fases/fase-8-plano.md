# Fase 8 — Plano de trabalho (contrato e pedido com IA)

**Branch:** `fase-8` (a partir de `fase-7`) · **Plano de:** 04/11/2026 · **Origem:** [proposta 0001](../propostas/0001-ia-no-licitaone.md), itens 1 (parte que faltou) e 2 · **Base técnica:** [ADR 0016](../adr/0016-leitura-com-ia.md)

**Status:** validado e concluído em 04/11/2026 ([relatório](fase-8.md), [ADR 0017](../adr/0017-pedido-com-ia.md)).

## Decisões de 04/11/2026
1. **Mais de um contrato por pedido:** o usuário não sabia; pesquisamos. A nota de empenho e a ordem/autorização de fornecimento são emitidas a partir de **uma** ata ou contrato e para **um** fornecedor (minutas da Lei 14.133, como a da [PGM de Nova Iguaçu](https://pgm.novaiguacu.rj.gov.br/Documentos/MinutasPadraoLei141332021/minuta%20padr%C3%A3o%20-%20ata%20de%20registro%20de%20pre%C3%A7os%20-%20aquisi%C3%A7%C3%A3o.pdf), e atas que citam uma nota por solicitação). Ofício ou e-mail informal pode misturar atas, mas é exceção. **Decisão:** uma leitura serve um contrato. Uma linha que não é dele vira aviso ("não está neste contrato", com o nº de ata/contrato citado, se houver) e fica fora do pedido.
2. **Guardar o documento de origem:** sim. O pedido guarda o id do documento lido e o histórico mostra o link.
3. **Exemplos reais:** não há. A medição fica com exemplos públicos (ordens de fornecimento e empenhos publicados por prefeituras) e com o leitor de demonstração; a medição com pedidos da Shema fica pendente.
4. **Autorização:** o usuário autorizou o envio de atas, contratos, pedidos e mensagens à Anthropic (04/11/2026).

## Escopo

### A. "Preencher com IA" no formulário do contrato (fecha o item 1)
O backend já lê PDFs do tipo Contrato desde a Fase 7. Falta a tela, no mesmo modelo da ata:
- caixa "Preencher com IA" em **Registrar contrato** / **Assinar contrato**, escolhendo o PDF já enviado em Documentos;
- número, assinatura, vigência, valor e itens chegam preenchidos, cada um com a etiqueta **"IA · p. N"**;
- avisos da conferência no topo (vigência invertida, soma dos itens diferente do total, fornecedor que não é a Shema e, novo, **órgão do PDF diferente do órgão do contrato**);
- itens lidos abrem no editor de itens para conferir;
- histórico: "dados sugeridos pela IA e conferidos por <usuário>".

### B. Pedido a partir de PDF, foto ou mensagem (item 2)
O órgão manda o pedido de vários jeitos: ofício, autorização de fornecimento, nota de empenho, foto do papel, e-mail ou WhatsApp.

**Fluxo**
1. No diálogo **Registrar pedido** do contrato, uma caixa "Preencher com IA" com duas opções:
   - **documento:** um PDF ou imagem (PNG/JPEG) já enviado em Documentos (tipos Empenho ou Outros);
   - **texto colado:** e-mail ou mensagem, até cerca de 20 mil caracteres.
2. `POST /api/v1/contracts/{id}/order-readings` responde **202**. A leitura usa a mesma fila, consumidor e tabela da Fase 7 (`document_extractions`, novo tipo `PEDIDO`).
3. O prompt leva os **itens do contrato** (número do lote/item, descrição, unidade e preço) para a IA relacionar cada linha pedida com um item, informando a página e o trecho de onde tirou.
4. A conferência no domínio (sem Spring, como `ReadingResult`) aponta:
   - linha que não bate com nenhum item do contrato (fica para a pessoa escolher);
   - quantidade acima do saldo do item;
   - preço no documento diferente do preço do contrato (o pedido usa sempre o preço do contrato);
   - órgão ou número de contrato no documento diferente do contrato aberto;
   - data do pedido no futuro ou antes da assinatura.
5. O diálogo abre preenchido (data, referência como nº do ofício/empenho, observações e linhas com quantidade). A pessoa confere e salva pelo `POST /contracts/{id}/orders` de sempre, com `Idempotency-Key`. Saldo e versão continuam validados pelo domínio.
6. Histórico do contrato: "Pedido … registrado · sugerido pela IA a partir de <ofício.pdf | mensagem colada> e conferido por <usuário>".

**Técnico**
- `DocumentReader` ganha a leitura de pedido, com o mesmo padrão: Claude (SDK oficial), desligada sem chave, falsa nos testes e demonstração no E2E.
- **Imagens:** foto de celular costuma ser grande; o backend reduz a imagem antes do envio para caber no limite da API (o limite exato é conferido na implementação). PDF segue até 20 MB.
- **Texto colado não fica guardado** depois da leitura: a tabela grava só o resultado, e o texto nunca vai para os logs (pode ter nome e telefone de servidor).
- `extraction` passa a depender da API pública de `contracts` só para ler os itens e o saldo; o teste de arquitetura confere.
- Métricas: `shema.extraction.*` ganham a etiqueta `kind=PEDIDO`.

**Medição:** o script de avaliação (pendente da Fase 7) passa a cobrir pedidos: itens relacionados certo/errado e quantidades, em 3 a 5 pedidos reais.

## Etapas
1. ✅ Contrato na tela: caixa no formulário, etiquetas, avisos (incluindo órgão), itens no editor, E2E.
2. ✅ Pedido por documento (PDF e imagem): esquema, prompt com os itens do contrato, conferência no domínio, testes de fluxo.
3. ✅ Pedido por texto colado: endpoint, descarte do texto, testes.
4. ✅ Diálogo de pedido preenchido, histórico, E2E com o leitor de demonstração (1440 e 390) e axe.
5. ⏳ Script de avaliação: sem exemplos reais (decisão 3), fica pendente. ✅ Relatório `docs/fases/fase-8.md`.

## Fora desta fase
- Áudio e transcrição (item "áudio vira registro").
- Descobrir sozinho **de qual contrato** é o pedido quando ele chega solto. Nesta fase a pessoa parte do contrato; a IA só avisa se parecer ser de outro.
- Ler e-mail direto da caixa de entrada ou do WhatsApp (integração).

## Dúvidas
1. **Autorização do cliente** para enviar atas, contratos, pedidos e mensagens à Anthropic (segue pendente da Fase 7; bloqueia só a produção). Mensagens podem ter dado pessoal de servidor, o que pesa mais que documento público.
2. **Exemplos reais:** 3 a 5 pedidos como chegam hoje (ofício, AF, empenho, foto, e-mail), mais 2 ou 3 atas e contratos.
3. **Um pedido pode cobrir mais de um contrato?** Por exemplo, um ofício que pede itens de dois lotes com contratos diferentes. Se for comum, a etapa 2 muda.
4. **Guardar o arquivo de origem junto do pedido?** Proposta: o pedido guarda o id do documento usado na leitura e mostra o link no histórico.
5. **Chave de API** para desenvolvimento (a mesma da Fase 7).
