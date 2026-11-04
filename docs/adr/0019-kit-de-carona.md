# 0019 — Kit de carona sob medida (Fase 10)

**Status:** Aceito · 04/11/2026 · origem: [proposta 0001](../propostas/0001-ia-no-licitaone.md), item 4 · base: ADRs [0014](0014-atas-caronas-e-documentos.md) e [0018](0018-follow-up-com-ia.md)

## Contexto
A Shema prospecta caronas pelo funil, mas o texto da oferta ao município era escrito à mão, fora do sistema. Pela Lei 14.133 (art. 86, § 2º), a adesão exige três coisas:
- justificativa da vantagem;
- preços compatíveis com o mercado;
- **aceitação prévia do órgão gerenciador e do fornecedor**.

Na prática, o município monta o processo com o ofício ao gerenciador e o ofício de anuência do fornecedor ([plano](../fases/fase-10-plano.md), com as fontes).

## Decisões

### Três textos, e só dois com IA
- **Carta de apresentação** (Shema → prefeitura) e **justificativa** do ofício de solicitação: escritas pela IA, na hora e sem fila, como o follow-up da Fase 9.
- **Modelo de ofício de solicitação** (prefeitura → gerenciador): estrutura e base legal (art. 86, § 2º) num **modelo fixo** (`CaronaLetters`). Os campos que só o município sabe ficam entre colchetes: número do ofício, secretaria, processo, dotação e autoridade.
- **Ofício de anuência:** sempre o modelo fixo, sem IA. É o aceite da empresa, e o texto não deve variar entre municípios.
- Sem IA (chave ausente ou "Gerar sem IA"), o kit sai inteiro dos modelos fixos. A tela funciona sem a IA.

### O que a IA recebe e não pode fazer
- **Recebe:**
  - a ata (número, licitação, gerenciador, objeto, vigência e dias que faltam);
  - o município candidato;
  - o limite por município;
  - até 15 itens com a quantidade oferecida, o preço e o total;
  - a assinatura de quem está mandando.
- **Não recebe:** caronas de outros órgãos nem dados de outros clientes.
- **Regras no prompt:**
  - não inventar economia, desconto, prazo, frete ou condição;
  - não citar lei;
  - não afirmar que o município pode aderir, porque isso depende da lei local e do gerenciador.
- **Conferência no domínio:**
  - texto vazio é falha;
  - acima de 6.000 caracteres, o texto é cortado com aviso;
  - **citação de lei ou artigo com número gera aviso**, porque a base legal é a do modelo fixo.

### Limite de 50% por município
- `CaronaLimits.maxPerAgencyPercent` (`shema.caronas.max-per-agency-percent`, padrão 50) segue o § 4º do art. 86. Os 4 órgãos × 50% batem com os 200% do cliente (decisão de 04/11/2026).
- O kit mostra, por item, **quanto o município ainda pode pedir**: o menor entre o que sobra dos 50% dele e o que sobra dos 200% da ata. Isso desconta o que ele já aderiu.
- A carona registrada passa a conferir também esse limite (`AGENCY_ITEM_LIMIT` e `AGENCY_VALUE_LIMIT`). Como os outros, ele é **sinalizado e pode ser confirmado**: não é recusa.

### Recusa e aviso
- **Recusa (regra de domínio):**
  - ata vencida;
  - ata que não permite adesão;
  - o próprio gerenciador como candidato.
- **Avisos:**
  - vigência menor que 30 dias;
  - município que já aderiu;
  - limite de órgãos atingido;
  - itens sem saldo;
  - **gerenciador municipal** ("confira se a lei do município permite carona em ata de outro município"; a Lei 14.770/2023 permite, mas a norma local pode proibir).

### Módulos
- `pricerecords` ganha a API pública `CaronaOffers`, com o que oferecer, as quantidades e os avisos. `tenders.AgencyDirectory` ganha `isMunicipal`.
- O kit fica em `extraction` (`CaronaKitAssistant`), que já tinha o leitor e as métricas (`kind=CARONA`).

### Arquivo e dados da empresa
- **.docx** com o Apache POI (já no projeto, pela importação): A4, Calibri 11 e cabeçalho com a razão social, o CNPJ, o endereço e o contato. A solicitação não leva o cabeçalho, porque é do município.
- **Dados da empresa** em `shema.company.*` (`SHEMA_COMPANY_*`), com exemplos no `.env.example`. Campo em branco aparece entre colchetes no texto, e a tela avisa.
- **"Salvar nos documentos"** gera o .docx e o envia pelo envio de sempre, como documento "Outros" da licitação da ata. Só aparece para quem pode alterar a licitação.

### Nada é enviado nem gravado pelo kit
Os endpoints `GET /price-records/{id}/carona-offer`, `POST /price-records/{id}/carona-kit` e `POST /price-records/{id}/carona-kit/docx` não gravam nada. Quem tem perfil Comercial ou Administrador gera o kit. Perfil Leitura recebe 403.

## Consequências
- A carta usa só dados que já estão no sistema. O que a Shema sabe do município (porte, necessidade) entra pela revisão da pessoa, não pela IA.
- A base legal do modelo fixo precisa ser revisada pelo jurídico do cliente antes do primeiro uso real.
- Mais um tipo de chamada à Anthropic (`kind=CARONA`), coberto pela autorização de 04/11/2026.
