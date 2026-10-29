# Fase 3 — Plano de trabalho (atas, documentos e caronas)

**Branch:** `fase-3` (a partir de `fase-4`) · **Início:** 29/10/2026

A Fase 4 foi feita antes, a pedido do usuário (`docs/decisoes.md`). Esta branch parte dela para o funil já poder apontar para atas.

## Escopo (seção 5.3 da especificação e respostas do cliente)
- **Documentos** por licitação: tipo (Ata, Contrato, Empenho, Atestado, Edital, Outros), versão e data, guardados no S3 (SeaweedFS no compose, ADR 0007).
- **Ata** (uma por licitação, ADR 0005): número, vigência, "permite adesão", observações e os itens registrados. Independe da data de encerramento do contrato.
- **Destaque** em licitação com ata vigente que permite adesão: "Ata válida até 2028 — oportunidade de replicar".
- **Lista "Atas vigentes / oportunidades"**, ordenada pelo vencimento, e a lista de **municípios sem ata** da Shema (prospecção).
- **Carona** (adesão de outro órgão à ata, resposta 4 do cliente): órgão, data, quantidades por item, valor e negociação de origem. Os limites são configuráveis e sinalizados para validação humana.

## Decisões de modelo (vão para o ADR 0014)
- **Módulo `pricerecords`** ("Atas e documentos").
- **Armazenamento**: `S3FileStorage` (AWS SDK v2) ao lado do `LocalFileStorage`, escolhido por `shema.storage.type`. O compose usa S3; os testes usam disco e um teste próprio sobe o SeaweedFS no Testcontainers.
- **Documento com versões**: o documento (tipo, título, data) tem uma ou mais versões (arquivo, tamanho, tipo detectado, SHA-256, quem enviou e quando). Uma versão nova não apaga a anterior.
  - Apagar é lógico (`deleted_at`): documento jurídico não some (seção 7 da especificação). O arquivo fica no S3.
  - **Validação do arquivo**: até 25 MB; PDF, imagens (PNG, JPG), Word, Excel e ODF. O tipo vem do conteúdo (assinatura do arquivo), não do nome nem do navegador. Antivírus fica como evolução, como diz a especificação.
- **Itens da ata são da ata**: quantitativo registrado e preço unitário. A tela oferece copiar os itens de um contrato da licitação como ponto de partida. Assim o módulo não depende de `contracts`.
- **Limites da carona**, configuráveis (`shema.caronas.*`) e tratados como **alerta, não bloqueio**:
  - no máximo 4 caronas por ata;
  - soma das caronas de cada item ≤ 200% do quantitativo registrado (resposta 4 assumida "por item"). Sem itens na ata, vale o valor total;
  - ata vencida na data da carona.
  - Passar de um limite exige confirmar ("Registrar mesmo assim"). A carona fica marcada e o histórico registra quem confirmou.
  - Ata que **não permite adesão** recusa carona (é fato do documento, não interpretação de lei).
- **Histórico**: ata, itens, caronas e documentos geram linhas no histórico da licitação (quem, o quê, quando).
- **Negociação ganha** ligada a uma licitação com ata: o card sugere "Registrar carona", mas nunca cria sozinho (ADR 0005).
- **Municípios sem ata**: órgãos municipais sem licitação vencida e sem carona. A busca usa a nova consulta pública `tenders.AgencyDirectory`.

## Decisões tomadas durante a execução (entraram no ADR 0014)
- O órgão da própria licitação não pode pegar carona na ata (`OWN_AGENCY`), e o mesmo órgão aderindo de novo vira aviso.
- `Quantities` e `BrFormat` foram para `shared.domain`, porque contratos e atas usam as mesmas regras numéricas.
- O cliente HTTP do S3 é o da JDK, sem Netty nem Apache.
- V13: as linhas de carona saem em cascata com o item, para a exclusão da ata não depender da ordem da cascata.

## Etapas
1. ✅ Backend: `S3FileStorage`, configuração, bucket criado na subida, teste com SeaweedFS.
2. ✅ Backend `pricerecords`: documentos e versões (upload, download, versão nova, apagar), validação do arquivo, histórico.
3. ✅ Backend: ata, itens, caronas com limites configuráveis, oportunidades e municípios sem ata, seed.
4. ✅ Frontend: aba *Documentos* (lista, envio, versões, baixar), ata no detalhe da licitação (dados, itens, caronas), destaque de ata vigente, página *Atas* real, "Registrar carona" no funil.
5. ✅ E2E (1440 e 390), screenshots e checklist da Web Design Guidelines.
6. ✅ ADR 0014 e relatório `docs/fases/fase-3.md`.
