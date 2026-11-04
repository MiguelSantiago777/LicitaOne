# Fase 10 — Plano de trabalho (kit de carona sob medida)

**Branch:** `fase-10` (a partir de `fase-9`) · **Plano de:** 04/11/2026 · **Origem:** [proposta 0001](../propostas/0001-ia-no-licitaone.md), item 4 · **Base técnica:** ADRs [0014](../adr/0014-atas-caronas-e-documentos.md) (atas e caronas) e [0018](../adr/0018-follow-up-com-ia.md)

**Status:** validado e concluído em 04/11/2026 ([relatório](fase-10.md), [ADR 0019](../adr/0019-kit-de-carona.md)).

## Decisões de 04/11/2026
1. **Kit com os três textos:** apresentação, modelo de ofício e anuência.
2. **Limite de 50% por município:** entra, configurável e sinalizado, e vale também na carona registrada.
3. **Formato:** .docx para baixar.
4. **Dados da empresa:** campos de exemplo, preenchidos no deploy.

## O que existe hoje
- A ata tem vigência, itens, quantitativo e "permite adesão". As caronas registradas são conferidas contra os limites configuráveis: 4 órgãos e 200% por item (`shema.caronas.*`, ADR 0014).
- A tela de atas lista **municípios sem ata** (prospects). O funil de negociações serve para **prospectar caronas**. A negociação aponta para o órgão candidato e para a licitação da ata.
- O alerta de ata vencendo lembra de "oferecer caronas enquanto vale", mas o texto da oferta é escrito à mão, fora do sistema.

## O que a pesquisa mostrou (04/11/2026)
O art. 86, § 2º da Lei 14.133 exige três coisas para a adesão: justificativa da vantagem, demonstração de que o preço está no mercado e **consulta e aceitação prévias do órgão gerenciador e do fornecedor**. Na prática, o processo do município que adere junta estes documentos:
- o ofício ao fornecedor pedindo a adesão;
- o ofício ao órgão gerenciador pedindo autorização;
- a autorização do gerenciador;
- o **ofício de anuência do fornecedor**;
- a pesquisa de preços, a justificativa e a declaração de orçamento.

Fontes: [guia do CEFET-MG](https://www.slog.cefetmg.br/wp-content/uploads/sites/88/2025/03/Guia-4-Adesão-a-uma-ata-de-registro-de-preço.pdf) e [parecer do IFRS](https://ifrs.edu.br/wp-content/uploads/2024/09/Parecer_Referencial_05-2024-Adesao-a-Ata-de-Registro-de-Precos-pela-Lei-14.133.-2021.pdf).

Pontos que mudam o texto:
- **Ata municipal:** a Lei 14.770/2023 passou a permitir carona em ata de gerenciador municipal, desde que a ata tenha saído de licitação. Ainda assim, a norma local pode proibir; a Procuradoria de São Paulo, por exemplo, entende que a lei paulistana proíbe ([Justen](https://justen.com.br/?p=18730), [parecer PGM-SP](https://legislacao.prefeitura.sp.gov.br/leis/parecer-procuradoria-geral-do-municipio-pgm-12335-de-17-de-maio-de-2024/anexo/664e04c81411924fe954d92e/EMENTA%2012335-RBM.pdf)). A tela avisa; o texto não afirma que a carona é permitida.
- **50% por adesão:** cada órgão pode aderir a até 50% do quantitativo de cada item (§ 4º), e o total das adesões vai até o dobro (§ 5º). Os "4 municípios, 200%" do cliente batem com isso (4 × 50%). Hoje o sistema confere só o total.

Para o fornecedor, que é a posição da Shema, a carona começa quando a empresa **oferece a ata** ao município e facilita o caminho: manda a apresentação, o modelo do pedido e a anuência já pronta.

## Escopo

### A. Kit de carona para um município candidato
Fica na negociação do funil (quando ela tem órgão e licitação com ata vigente que permite adesão) e na ata, pelo botão **"Oferecer carona"** com a escolha do município. Gera três textos editáveis:
1. **Carta de apresentação** (Shema → prefeitura candidata), escrita pela IA. Cita:
   - a ata (número, órgão gerenciador, objeto, vigência e quanto tempo falta);
   - os itens com preço;
   - **quanto o município pode pedir de cada item** (50% do registrado, limitado ao que sobra dos 200%);
   - o passo a passo da adesão;
   - argumentos objetivos: preço já licitado, sem nova licitação, prazo.
2. **Modelo de ofício de solicitação de adesão** (prefeitura → órgão gerenciador), para o município adaptar. A estrutura e a base legal vêm de um **modelo fixo**, revisável pelo cliente. A IA só preenche objeto, itens, quantidades e a justificativa de vantagem, com espaços para o que só o município sabe: dotação, número do processo e secretaria.
3. **Ofício de anuência do fornecedor** (Shema → órgão aderente e gerenciador): aceita fornecer nas condições da ata. **Sem IA**, é um modelo fixo preenchido com os dados da ata e as quantidades. É documento de aceitação, e o texto não deve variar.

Para cada texto: **Copiar**, **Baixar (.docx)**, **Abrir no e-mail** (carta de apresentação) e **Salvar nos documentos da licitação**.

### B. Avisos na tela (domínio, sem IA)
- Ata vencida, sem adesão permitida ou com vigência curta (menos de 30 dias).
- Limite de órgãos atingido, ou item sem saldo de carona.
- O município já aderiu a esta ata ou já tem ata própria para o mesmo objeto.
- O gerenciador é municipal: "Confira se a lei do município permite carona em ata municipal".

### C. Ao gerar
- A negociação recebe uma nota: "Kit de carona gerado por <usuário>". Se a negociação estiver na primeira etapa, a tela **sugere** movê-la para a etapa seguinte, mas não move sozinha.

## Técnico
- **Síncrono, sem fila**, como na Fase 9: uma chamada para a carta de apresentação e outra para preencher o modelo de ofício. A anuência é montada no backend, sem modelo.
- **Contexto enviado à IA:**
  - ata (número, gerenciador, objeto, vigência, itens, preços, saldo de carona);
  - município candidato (nome, UF, porte se houver);
  - nota da negociação;
  - assinatura.
  - Não vão os dados de outras caronas nem de outros clientes.
- **Regras no prompt:**
  - não inventar economia, prazo de entrega nem condição fora da ata;
  - não afirmar que a carona é permitida pela lei local;
  - a base legal fica só no modelo fixo, nunca escrita pela IA.
- **Dados da empresa** para o cabeçalho e a anuência (razão social, CNPJ, endereço, representante e cargo) em `shema.company.*`, como hoje acontece com o nome.
- O limite de 50% por adesão entra em `CaronaLimits` (`maxPerAgencyPercent`, padrão 50). A conferência da carona registrada também passa a usá-lo: passar dele é sinalizado e pode ser confirmado, como os outros limites.
- **.docx** gerado no backend (Apache POI), com cabeçalho da empresa, data por extenso e espaço para assinatura.
- Métricas: `kind=CARONA`. E2E com o leitor de demonstração.

## Etapas
1. ✅ Limite de 50% por adesão em `CaronaLimits` e na conferência da carona; quanto cada município pode pedir por item.
2. ✅ Dados da empresa, modelo fixo do ofício de solicitação e da anuência, geração do .docx.
3. ✅ Carta de apresentação e preenchimento do ofício com IA (esquema, conferência, endpoint).
4. ✅ Tela: botão na negociação e na ata, três abas com os textos, avisos, salvar nos documentos.
5. ✅ E2E (1440 e 390), axe, ADR 0019 e relatório `docs/fases/fase-10.md`.

## Fora desta fase
- Disparar ofertas em massa para todos os prospects.
- Mandar e-mail pelo sistema.
- Gerar a pesquisa de preços ou a justificativa completa do município, que são documentos dele.

## Dúvidas
1. **O kit com três textos** (apresentação com IA, modelo de ofício com IA e anuência fixa) serve, ou vocês só querem a carta de apresentação?
2. **Limite de 50% por município:** posso incluir (configurável, sinalizado como os outros) e passar a conferir também na carona registrada?
3. **Formato:** .docx para baixar resolve, ou precisa de PDF com papel timbrado? Se tiver timbrado, mande o arquivo depois que eu uso.
4. **Dados da empresa** (CNPJ, endereço, representante e cargo): vocês me passam agora ou deixo campos de exemplo no `.env` para preencher no deploy?
