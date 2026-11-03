# 0016 — Leitura de atas e contratos com IA (Fase 7)

**Status:** Aceito · 03/11/2026 · origem: [proposta 0001](../propostas/0001-ia-no-licitaone.md)

## Contexto
Cadastrar uma ata ou um contrato é digitação: número, datas, "permite adesão" e dezenas de itens com quantidade e preço unitário, copiados do PDF. O cliente escolheu começar a IA por aqui, com o Claude (Anthropic) como fornecedor (03/11/2026).

## Decisões

### A IA só sugere
- O resultado de uma leitura é uma **sugestão** guardada em `document_extractions`. Nada da ata ou do contrato muda nesse momento.
- O formulário de sempre abre preenchido. A pessoa confere e salva pelos endpoints que já existem, então todas as regras do domínio continuam valendo (vigência, itens com carona, limites).
- O histórico registra que os dados foram sugeridos pela IA e conferidos por quem salvou.
- **Cada valor traz a página do PDF** de onde saiu. As citações nativas da API não combinam com saída estruturada (400), por isso a página é um campo do próprio esquema, informada pelo modelo.

### Leitura sob pedido, nunca no envio
- O PDF só sai do sistema quando alguém clica em **"Preencher com IA"**. Isso controla o custo e deixa claro o que vai para fora.
- Só PDF dos tipos Ata e Contrato, até 20 MB. A requisição da API aceita até 32 MB, e o PDF vai em base64 (cerca de 33% maior).
- Imagens e fotos ficam para o "pedido por foto".

### Módulo `extraction`
- Depende só da API pública nova de `pricerecords`, `DocumentFiles` (metadados e conteúdo da versão atual de um documento). O teste de arquitetura confere.
- `DocumentReader` é a porta, com três implementações:
  - **Claude**, com o SDK oficial `com.anthropic:anthropic-java`;
  - **desligada**, quando não há chave: a API responde `AI_DISABLED` e o front esconde o botão;
  - **falsa**, nos testes: CI e `verify` rodam sem rede e sem chave.
- **Esquema:** `RawReading` (tudo texto, `""` para "não encontrado") é convertido e conferido em `ReadingResult`, no domínio e sem Spring:
  - dinheiro nunca passa por double;
  - aceita formato brasileiro copiado do documento;
  - avisa sobre vigência invertida, soma dos itens diferente do total e fornecedor diferente da Shema.

### Assíncrono, sem segurar conexão
- `POST /api/v1/documents/{id}/extractions` responde 202. O evento sai pelo outbox para a fila `shemacrm.extraction.extraction-requested`, com DLQ.
- A chamada ao modelo pode passar de um minuto. Por isso o consumidor usa `ReliableConsumer.consumeOutsideTransaction`: uma transação curta marca o início, a chamada roda fora do banco e outra transação curta grava o resultado. A idempotência vem do estado da leitura (concluída não roda de novo).
- **Falhas que a pessoa entende** encerram a leitura com a mensagem, sem nova tentativa:
  - IA recusou o documento;
  - PDF protegido ou corrompido;
  - documento grande demais;
  - IA fora do ar depois das tentativas do SDK.
  
  Só erro do nosso lado (banco, armazenamento) segue o caminho de tentativas e DLQ.

### Modelo e custo
- Padrão `claude-opus-5-5`, com esforço `medium`. Os dois são configuráveis (`SHEMA_EXTRACTION_MODEL`, `SHEMA_EXTRACTION_EFFORT`).
- **Fallback do servidor ligado** (`fallbacks: "default"`, beta `server-side-fallback-2026-07-01`): se o modelo recusar o documento por política de segurança (falso positivo), a própria API tenta outro modelo. A leitura grava o modelo que respondeu. Para desligar: `SHEMA_EXTRACTION_FALLBACKS=false`.
- Tokens de entrada e saída gravados por leitura e expostos no Prometheus (`shema.extraction.tokens`, `shema.extraction.finished`, `shema.extraction.failed`).
- Chave só em `ANTHROPIC_API_KEY` (`.env`), nunca no repositório.

## Consequências
- **Dados saem do sistema.** Atas e contratos são documentos públicos, mas o envio à Anthropic precisa de autorização do cliente antes de ligar em produção (pendência da proposta 0001).
- A precisão ainda não foi medida. Falta o script de avaliação com atas reais, comparando campo a campo com o cadastro feito à mão.
- Atas muito longas (centenas de itens) podem passar do teto de resposta. Nesse caso a leitura termina pedindo o cadastro pela planilha.
