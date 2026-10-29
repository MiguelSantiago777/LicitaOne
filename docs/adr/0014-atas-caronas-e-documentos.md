# 0014 — Atas, caronas e documentos (Fase 3)

**Status:** Aceito · 29/10/2026 · detalha o [0005](0005-modelo-de-dominio.md) e o [0007](0007-armazenamento-s3.md)

## Decisões

### Armazenamento
- **`S3FileStorage`** (AWS SDK v2, só a API S3) ao lado do `LocalFileStorage`. A escolha é feita por `shema.storage.type` (`local` por padrão; `s3` no compose, com o SeaweedFS).
  - O bucket é criado na subida, se ainda não existir.
  - O cliente HTTP é o da JDK (`url-connection-client`), sem Netty nem Apache: menos dependências.
  - A regra das chaves (`StorageKeys`) é a mesma para disco e S3.
  - Teste próprio sobe o mesmo SeaweedFS no Testcontainers. Os testes de API continuam em disco.
- A chave do arquivo vem do documento e da versão (`documents/<licitação>/<documento>/v<n>`), nunca do nome enviado.

### Documentos
- **Documento com versões.** Tipo (Ata, Contrato, Empenho, Atestado, Edital, Outros), título e data, mais uma ou mais versões. Cada versão guarda arquivo, tipo, tamanho, SHA-256, quem enviou e quando. Versão nova nunca sobrescreve a anterior.
- **Apagar é lógico** (`deleted_at`, `deleted_by`): documento jurídico não some (seção 7). O documento sai da lista e do download, mas o registro e os arquivos ficam.
- **O arquivo é conferido pelo conteúdo**, e não pelo nome nem pelo tipo que o navegador informa.
  - Aceitos, até 25 MB: PDF, PNG, JPG, Word e Excel (novos e antigos) e ODF.
  - A assinatura dos primeiros bytes tem que bater com a extensão: um `.pdf` que não é PDF é recusado (`FILE_TYPE_MISMATCH`).
  - O download sai como anexo, com `nosniff`.
  - Antivírus fica como evolução, como diz a especificação.
- **Permissão** igual à da licitação: responsável ou administrador enviam e apagam; todos consultam e baixam.

### Ata
- **Uma por licitação** (ADR 0005), registrada só depois que a licitação é vencida.
  - Campos: número, assinatura, vigência, "permite adesão", observações e o documento do arquivo da ata.
  - A **vigência vem do documento** e o sistema não calcula prazo legal (prorrogação etc.).
- **Itens da ata são da ata** (quantitativo registrado e preço unitário), não os do contrato.
  - O quantitativo registrado é a base legal da adesão. Uma licitação pode ter vários contratos ou empenhos com os mesmos itens.
  - A tela oferece "copiar do contrato" como ponto de partida. Assim `pricerecords` não depende de `contracts`.
  - Item com carona pode ser corrigido (pelo id), mas não removido.
- **Situação**: vigente, vencendo (90 dias, configurável em `shema.caronas.expiring-days`) ou vencida.
  - **Oportunidade** = vigente, permite adesão e ainda cabe outro órgão.
  - O destaque aparece no cabeçalho da licitação, nas licitações encerradas ("Ata válida até …") e na página Atas.

### Carona e limites
- **Carona** = adesão de outro órgão à ata. Guarda órgão, data, quantidade por item (o valor sai dos itens) ou só o valor (ata sem itens), a negociação de origem e observações.
  - Não é editada: carona errada é apagada e registrada de novo, e o histórico guarda as duas coisas.
- **Limites configuráveis** (`shema.caronas.max-agencies = 4`, `max-total-percent = 200`), a partir da resposta do cliente. São **sinalizados, não bloqueados** (seção 11: não inventar regra jurídica).
  - Avisos:
    - quinto órgão diferente;
    - soma das caronas de um item acima de 200% do registrado (ou do valor da ata, se ela não tem itens);
    - ata vencida na data da adesão;
    - mesmo órgão aderindo de novo.
  - `POST …/caronas/check` devolve o valor e os avisos. Registrar com aviso exige `confirmLimits = true`; sem isso, a resposta é 422 `CARONA_LIMITS`.
  - A carona confirmada fica marcada e guarda o que foi confirmado e por quem. O histórico registra.
  - **Recusas firmes**, que são fatos e não interpretação de lei: ata que não permite adesão (`ADHESION_NOT_ALLOWED`) e o próprio órgão da licitação (`OWN_AGENCY`).
  - "Por item" segue a resposta assumida em 27/10; a dúvida 4 continua aberta com o cliente.
- **Concorrência**: itens e caronas mudam o agregado com `PESSIMISTIC_FORCE_INCREMENT`. Duas caronas simultâneas não passam juntas pela checagem de limite.
- **Negociação ganha** com licitação de origem mostra "Registrar carona na ata" no card. O link abre a licitação na aba Documentos, com o diálogo já preenchido com a negociação e o órgão. A carona nunca é criada sozinha (ADR 0005).

### Municípios sem ata
- Prefeituras sem licitação vencida pela Shema e sem carona, por UF: a lista de prospecção da página Atas. "Nova negociação" abre o funil com o órgão preenchido.
- A consulta de órgãos fica no módulo dono deles, na API pública `tenders.AgencyDirectory.municipalitiesWithoutWonTender`.

### Histórico e limites entre módulos
- Ata, itens, caronas e documentos geram linhas em `price_record_events`. O detalhe da licitação junta essas linhas com as da licitação e as dos contratos numa linha do tempo só.
- `pricerecords` depende de `tenders` (licitação e órgãos), `deals` (nova API pública `DealDirectory`) e `identity`. Ninguém depende de `pricerecords`: a tela de licitações busca os destaques pela API de atas (`/price-records/highlights`).
- `Quantities` e `BrFormat` saíram de `contracts` para `shared.domain`, porque agora dois módulos usam as mesmas regras numéricas.

## Consequências
- A especificação pede "a ata é dinheiro": o contrato encerrado com ata vigente aparece em destaque e vira oportunidade no funil.
- Os dados de exemplo saíram de vez do frontend: todas as telas usam a API.
- A Fase 5 usa a vigência e o índice de `valid_until` para o alerta "ata vencendo em 90/60/30 dias".
