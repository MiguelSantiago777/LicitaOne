# Fase 7 — Leitura da ata com IA

**Branch:** `fase-7` (a partir de `fase-6`) · **Data:** 03/11/2026 · **Plano:** [proposta 0001](../propostas/0001-ia-no-licitaone.md) · **Decisão:** [ADR 0016](../adr/0016-leitura-com-ia.md)

## O que ficou pronto

### Na tela
- **"Preencher com IA" no formulário da ata** (Registrar ata / Editar ata). A pessoa escolhe o PDF da ata, já enviado em Documentos, e clica em **Ler com IA**.
  - A leitura roda em segundo plano, e o formulário continua utilizável enquanto isso. O status é anunciado para leitor de tela.
  - Ao terminar, número, assinatura, vigência, "permite adesão" e o arquivo da ata chegam preenchidos.
  - Cada campo preenchido ganha a etiqueta **"IA · p. N"**, com a página do PDF de onde saiu.
  - **Avisos** aparecem no topo: o que a IA disse que ficou ilegível e o que a conferência encontrou, como vigência invertida, soma dos itens diferente do total ou fornecedor que não é a Shema.
- **Itens:** depois de salvar a ata, os itens lidos abrem no diálogo de itens para conferir. O que a IA não conseguiu ler fica em branco para a pessoa completar.
- **Releitura evitada:** se a versão atual do PDF já foi lida, a caixa oferece "Usar essa leitura", sem mandar o PDF de novo nem gastar outra vez.
- **Histórico:** "Ata … registrada … · dados sugeridos pela IA e conferidos por <usuário>", e o mesmo nos itens.
- **Sem chave configurada, a caixa não aparece.** O resto do sistema segue igual.

### No backend
- **Módulo `extraction`:**
  - `POST /api/v1/documents/{id}/extractions` responde 202 e a leitura sai pela fila `shemacrm.extraction.extraction-requested`, com DLQ.
  - O consumidor não segura conexão do banco durante a chamada ao modelo (`ReliableConsumer.consumeOutsideTransaction`).
- **Claude pelo SDK oficial** (`anthropic-java` 3.4.0):
  - PDF em base64 e saída estruturada (`RawReading`);
  - modelo `claude-opus-5-5` com esforço `medium`, os dois configuráveis;
  - fallback do servidor ligado, para quando o modelo recusa o documento por engano.
- **Conferência no domínio** (`ReadingResult`, sem Spring):
  - datas e números convertidos, aceitando formato brasileiro;
  - dinheiro nunca passa por double;
  - avisos de inconsistência.
- **Só PDF de Ata ou Contrato, até 20 MB, e só quem pode alterar a licitação pede a leitura.**
- **Falhas que a pessoa entende** encerram a leitura com o motivo, sem nova tentativa (documento recusado, PDF protegido, IA fora do ar). Só erro do sistema vai para tentativas e DLQ.
- **Custo:** tokens gravados por leitura e métricas no Prometheus (`shema.extraction.tokens`, `shema.extraction.finished`, `shema.extraction.failed`).
- **Leitor de demonstração** (`SHEMA_EXTRACTION_DEMO=true`): devolve sempre a mesma ata, sem IA, para o E2E rodar no CI sem chave e sem custo. O backend se recusa a subir com ele no perfil `prod`.

### Diferenças em relação ao plano
- O botão ficou **no formulário da ata**, e não na lista de documentos. É ali que a pessoa está quando precisa dos dados, e o preenchimento acontece no mesmo lugar.
- **Contrato:** o backend já lê contratos (tipo Contrato), mas o preenchimento do formulário de contrato ainda não foi feito na tela. Fica para a próxima etapa, junto com o pedido por foto ou PDF.

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 145 testes, verde (com o código final; JaCoCo do domínio, Spotless e `ModularityTests`). Novos: `ReadingResultTest` (conversão e avisos), `ClaudeDocumentReaderTest` (o SDK aceita o esquema; fallback e esforço na requisição, sem rede) e `ExtractionFlowTests` (202 → fila → resultado, falha com motivo, tipos recusados, permissão, histórico "sugerido pela IA") |
| Frontend lint, formatação, testes, build | Verde |
| E2E (1440 e 390), compose com o leitor de demonstração | 44 testes, verde. Novo: `ai-fill.spec` (lê, preenche, completa o item que faltou, histórico, axe no formulário preenchido) |

Observações sobre as rodadas:
- **A primeira rodada completa do E2E teve 2 falhas que não se repetiram** (`atas.spec` e `deals.spec`, no desktop). O `deals.spec` passou ao rodar de novo. A causa da falha do `atas.spec` não foi identificada: a ata que ele deixou para trás encobriu o erro original. Depois de limpar a sobra, ele passou sozinho e na suíte completa, que deu 44 de 44.
- **O axe pegou um problema de contraste** nos avisos da IA: texto âmbar sobre o fundo lilás dava 4,22:1. O texto agora fica na cor normal, com o âmbar só no ícone.

## Checklist Web Design Guidelines (telas novas)
- [x] **Estados:** leitura em andamento (status anunciado), concluída (resumo e avisos), falha (mensagem com o motivo) e funcionalidade desligada (caixa oculta).
- [x] **Acessibilidade:** axe sem violações no formulário preenchido, em 1440 e 390. O seletor do PDF tem rótulo, o botão fica `aria-busy` durante a leitura e o resumo usa `role="status"`.
- [x] **Celular:** seletor e botão empilham abaixo de 480 px.
- [x] **Movimento:** o indicador de leitura respeita "reduzir movimento" (regra global).
- [x] **Texto:** a origem de cada valor aparece na tela. A interface nunca diz que a IA "garante" algo, só que sugere e que a pessoa confere.

## Pendências
- **Autorização do cliente** para enviar atas e contratos à API da Anthropic, antes de ligar `ANTHROPIC_API_KEY` em produção.
- **Chave de API e 2 ou 3 atas reais em PDF** para medir a precisão campo a campo (script de avaliação) e ajustar o prompt.
- **Preenchimento do contrato na tela** e, depois, **pedido por foto ou PDF** (proposta 0001, item 2).
