# Proposta 0001 — IA no LicitaOne

- **Status:** item 1 (leitura da ata) aprovado e feito na Fase 7 ([relatório](../fases/fase-7.md), [ADR 0016](../adr/0016-leitura-com-ia.md)); os outros seguem em discussão
- **Data:** 03/11/2026
- **Origem:** conversa com o time em 03/11/2026 sobre funcionalidades para diferenciar o produto

## Contexto

Os softwares de licitação no mercado se concentram **antes da vitória**: radar de editais e robôs de lance. O que o LicitaOne faz, acompanhar a licitação ganha até o encerramento e usar a ata como produto comercial, é pouco coberto. (Visão de mercado sem pesquisa formal; confirmar antes de usar em material de venda.)

A missão do produto ([especificação](../especificacao.md), item 1) é acompanhar cada licitação ganha **da vitória até o encerramento do contrato**, controlando pedidos, saldo, documentos e replicação da ata. A régua desta proposta é essa: a IA entra para **acelerar o que o CRM já faz**, não para abrir outro produto.

## Ideias, por aderência ao escopo

### Dentro da proposta (prioridade)

| Ideia | O que faz | Onde encaixa |
|---|---|---|
| Leitura de ata e contrato | Ao anexar o PDF, extrai número, vigência, itens e preços unitários e deixa o cadastro preenchido para conferência. Cada campo mostra a página de onde saiu. | Documentos (Fase 3); a especificação já prevê "extração de texto da ata" como pós-processamento assíncrono (seção 7) |
| Pedido a partir de qualquer canal | Órgão manda o pedido por PDF, foto do ofício, e-mail ou texto; a IA extrai itens e quantidades, relaciona com os itens do contrato, verifica o saldo e cria um pedido em rascunho. | Contratos e pedidos (Fase 2) |
| Áudio ou mensagem vira registro | Áudio transcrito ("falei com Magé, pedem em março") vira nota no histórico e tarefa com data. | Tarefas e histórico |
| Follow-up com contexto | O rascunho de e-mail/WhatsApp cita contrato, saldo, itens e a última conversa, em vez de um texto fixo. | Alertas (Fase 5), rascunho do contrato sem pedido |
| Classificar a resposta do órgão | A resposta colada ("sem previsão", "empenho sai em abril") vira status e tarefa com a data certa. | Tarefas |
| Ofício de carona sob medida | Para cada município candidato, redige o ofício de adesão com objeto, vigência e argumentos. | Atas e caronas (Fase 3) |

### Na fronteira (depois)

- **"Pergunte ao LicitaOne"**: perguntas em português sobre a carteira ("quais prefeituras têm mais de R$ 100 mil de saldo e não pedem há 60 dias?"), respondidas com lista clicável. Usa a API existente e respeita o perfil do usuário.
- **Resumo do dia**: 3 a 5 ações priorizadas por responsável, com o motivo de cada uma.
- **Importação assistida**: sugere o mapeamento de colunas e junta variações de nome de órgão e item.

### Fora da proposta (não fazer agora)

- **Leitor de edital** (resumo, riscos, decidir se participa): atua antes da vitória, terreno dos radares de editais. Rever quando o escopo for além do pós-venda, por exemplo com o ERP comercial previsto para etapa futura.

## Princípios, se for adiante

1. **Humano no controle:** a IA só gera rascunhos; nada é gravado sem confirmação. O histórico registra "sugerido por IA, aprovado por <usuário>".
2. **Rastreável:** todo campo extraído aponta o trecho e a página do documento de origem.
3. **Assíncrono:** o processamento de documentos roda como consumidor do RabbitMQ, com o mesmo padrão de outbox, idempotência e DLQ dos outros fluxos.
4. **Sem regra jurídica inventada:** limites de adesão e prazos de ata seguem configuráveis (especificação, seção 11). A IA aponta o ponto, mas não afirma validade legal.
5. **LGPD:** atas, contratos e editais são públicos; contatos de órgãos continuam com o mínimo de dados e ficam fora dos logs.
6. **Medir antes de ampliar:** a extração de ata é comparada com os cadastros que já existem, e o resultado decide se a ideia segue.

## Ordem sugerida

1. Leitura de ata e contrato (fácil de medir contra os dados atuais).
2. Pedido a partir de foto, PDF ou mensagem (mesmo leitor de documentos).
3. Follow-up com contexto e classificação da resposta do órgão.
4. Ofício de carona.
5. Itens da fronteira, conforme o uso.

## Fase 7: plano da leitura de ata e contrato

Escolhas de 03/11/2026: começar pela **leitura de ata e contrato**, com o **Claude (Anthropic)** como fornecedor. Este plano aguarda validação antes de codar.

**Fluxo**
1. Na aba Documentos, um documento do tipo Ata ou Contrato em PDF ganha o botão **"Ler com IA"**. A leitura não é automática no envio, para controlar custo e deixar claro o que sai do sistema.
2. `POST /api/v1/documents/{id}/extractions` responde **202** com o id da leitura, no mesmo padrão da importação de planilha.
3. Pelo outbox sai o evento `shemacrm.extraction.requested`. Um consumidor idempotente, com DLQ e retry, lê o arquivo do `FileStorage` e chama a API do Claude com o PDF.
4. O resultado vai para a tabela `document_extractions`: status, dados extraídos, modelo, tokens usados e erro, se houver. O front acompanha por polling.
5. O formulário da ata (ou do contrato) abre preenchido com o que a IA leu: número, assinatura, vigência, se permite adesão e itens (lote, descrição, unidade, quantidade, preço unitário). Cada campo mostra "sugerido pela IA · p. N".
6. A pessoa confere e salva pelos endpoints que já existem, então todas as validações do domínio continuam valendo. O histórico registra "dados sugeridos por IA, aprovados por <usuário>".

**Técnico**
- Novo módulo `extraction`, com API pública e eventos e sem acessar repositório de outro módulo, conferido pelo teste de arquitetura.
- Interface `DocumentReader`, com implementação Claude (SDK oficial `com.anthropic:anthropic-java`) e uma implementação falsa para testes e CI, que rodam sem rede e sem chave.
- Saída estruturada: um esquema JSON do que a ata e o contrato têm. Citações nativas da API não combinam com saída estruturada, por isso a página de cada campo é informada pelo modelo no próprio esquema.
- Modelo configurável por propriedade, com padrão `claude-opus-5-5` e o fallback do servidor ligado para o caso de recusa.
- Sem `ANTHROPIC_API_KEY` configurada, a funcionalidade fica desligada e o botão some. A chave vai só no `.env`, nunca no repositório.
- Limite de tamanho: a requisição aceita até 32 MB e o PDF vai em base64 (cerca de 33% maior). A leitura aceita PDFs de até cerca de 20 MB; acima disso, avisa.
- Custo: o uso de tokens de cada leitura fica gravado e vira métrica de negócio no Prometheus.

**Medição**
Um script de avaliação compara o que a IA leu com o cadastro feito à mão em 2 ou 3 atas reais, campo a campo. O resultado entra no relatório da fase.

**Dúvidas que bloqueiam**
1. O cliente autoriza enviar atas e contratos para a API da Anthropic? Os documentos são públicos, mas a decisão é dele. Isso bloqueia a produção, não o desenvolvimento.
2. Conseguir 2 ou 3 atas e contratos reais em PDF.
3. Uma chave de API para desenvolvimento.

**Fora desta fase:** imagens e fotos (entram com o pedido por foto), extração de empenho e leitura de edital.

## Pendências para decidir

- [ ] Cliente valida se a direção faz sentido para o time comercial.
- [ ] Conseguir 2 ou 3 atas e pedidos reais em PDF ou foto para um protótipo de extração.
- [ ] Escolher fornecedor e modelo de IA, com custo por documento (vira ADR quando aprovado).
- [ ] Confirmar a política de envio de documentos a um serviço externo de IA.
