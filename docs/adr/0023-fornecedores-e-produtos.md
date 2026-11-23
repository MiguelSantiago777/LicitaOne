# 0023 — Fornecedores e produtos por nicho (Fase 13)

**Status:** Proposto · 23/11/2026 · origem: pedido do usuário de 23/11/2026 ([plano](../fases/fase-13-plano.md), [decisões](../decisoes.md#fornecedores-e-produtos-por-nicho-23112026))

## Contexto
A Shema compra de fornecedores e revende aos órgãos. O pedido é uma aba para cadastrar fornecedores e produtos, organizados por nicho (ramo), e para ver quem vende cada produto mais barato. Decidido com o usuário:
- o fornecedor atende um ou mais nichos;
- o produto fica em um nicho;
- cada produto tem um ou mais fornecedores, cada um com o seu preço de custo.

## Decisões

### Módulo próprio, sem depender de licitações nem de propostas
- Módulo `catalog`. Usa só o `shared`: armazenamento de arquivos, usuário logado e regras de CNPJ e de números.
- A ligação com as propostas (escolher o produto ao montar a proposta) ficou para depois, por decisão do usuário.

### Três agregados: nicho, fornecedor e produto
- **Nicho:** nome único sem diferenciar acento, caixa ou espaços repetidos (`name_key`). Criar "limpéza" com "Limpeza" já cadastrado dá 409. Se o outro estiver arquivado, a mensagem manda desarquivar.
- **Fornecedor:** razão social obrigatória. Nome fantasia, CNPJ (dígitos conferidos, aceita o alfanumérico), cidade, UF, site e observações são opcionais. Tem até 20 contatos (nome, telefone e e-mail; contato todo em branco é descartado). Os nichos que atende vão numa tabela de ligação (`supplier_niches`).
- **Produto:** nome e nicho obrigatórios. Unidade, marca, descrição e CATMAT/CATSER (só dígitos) são opcionais.
- **Ofertas:** o preço de custo de cada fornecedor (`product_offers`) é parte do agregado produto, no máximo um por fornecedor. Cada oferta tem preço (até 4 casas, como o valor unitário das propostas), data da cotação, prazo de entrega em dias e pedido mínimo (até 3 casas).
- Nada é excluído. Nicho, fornecedor e produto são **arquivados**: somem das listas e das escolhas, e o que estava ligado continua. Um nicho ou fornecedor arquivado que já estava ligado continua aceito ao salvar; ligar um arquivado novo é recusado (422).

### Data da cotação
- Preço novo sem data recebe a data de hoje.
- Preço alterado com a data igual à anterior também passa para hoje: a pessoa atualizou o preço e não mexeu na data.
- Preço alterado com outra data fica com a data que a pessoa escolheu (cotação recebida dias antes). Data futura é recusada.
- Cotação com mais de **90 dias** aparece como antiga. É só aviso; o preço continua valendo para o menor preço.

### Menor preço
- O menor preço é o da oferta mais barata **entre os fornecedores ativos**. No empate, vale a cotação mais recente. Fornecedor arquivado não conta.
- A lista de produtos mostra o menor preço, de quem é e a data. Na tela do fornecedor, cada produto diz se o preço dele é o menor.

### Busca e listas
- `search_text` guarda os textos de busca sem acento e em minúsculas: no fornecedor, razão social, fantasia, CNPJ e cidade; no produto, nome, marca e código. A busca é um `like` nessa coluna. O CNPJ também é achado digitado com pontuação.
- As listas são paginadas e montadas sem N+1: nichos, contatos e ofertas vêm em lote (`@BatchSize`), as contagens numa consulta agrupada e os fornecedores das ofertas num `findAllById`. O `QueryCountTests` cobre as duas listas e as telas de detalhe.

### Arquivos do produto
- Foto (JPG, PNG ou WEBP) e ficha técnica (também PDF), até 10 MB e 20 por produto. O tipo é conferido pelos primeiros bytes e precisa bater com a extensão.
- Os arquivos ficam no `FileStorage` (disco ou S3), na chave `catalog/products/{produto}/{arquivo}`. O registro (`product_files`) fica **fora do agregado** produto. Assim, enviar ou apagar um arquivo não muda a versão do produto, e quem está editando não toma conflito.
- Se a transação falhar depois de gravar o arquivo, ele é apagado do armazenamento. Ao apagar o registro, o arquivo é apagado depois do commit. Foto e ficha não têm histórico de versões, ao contrário dos documentos da licitação.

### Tela
- Uma aba "Fornecedores e produtos" no grupo Comercial, com três abas internas (Produtos, Fornecedores e Nichos). Aba, nicho, busca, UF, arquivados e página ficam na URL. O filtro de nicho vale para as três.
- "Novo produto" e "Novo fornecedor" pedem só o obrigatório, criam no servidor e abrem a edição. Ela salva sozinha 800 ms depois da última alteração, com a versão, como nas propostas.
- Permissões: Administrador e Comercial criam e alteram qualquer cadastro (não há responsável); Leitura só consulta.

## Consequências
- Para ligar produtos às propostas depois, basta a proposta guardar o `productId` no item e copiar descrição, unidade e marca. O preço de custo não vai para a proposta.
- Não há histórico de preços: atualizar o preço substitui o anterior. Se o histórico for pedido, entra uma tabela de cotações por oferta.
- A importação por planilha não foi pedida. Se vier, o `search_text` e os nomes únicos de nicho já ajudam a evitar duplicados.
