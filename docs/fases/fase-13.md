# Fase 13 — Fornecedores e produtos por nicho

**Branch:** `fase-13` (a partir de `fase-12`) · **Data:** 23/11/2026 · **Plano:** [fase-13-plano.md](fase-13-plano.md) · **Decisão:** [ADR 0023](../adr/0023-fornecedores-e-produtos.md)

## O que ficou pronto

### Aba "Fornecedores e produtos" (menu Comercial)
Três abas na mesma tela: **Produtos**, **Fornecedores** e **Nichos**. O filtro de nicho vale para as três, e aba, nicho, busca, UF, arquivados e página ficam no endereço (dá para mandar o link).

- **Produtos:**
  - lista com nome, marca, CATMAT/CATSER, nicho, unidade, **menor preço** com o fornecedor e a data da cotação, e quantos fornecedores têm preço;
  - cotação com mais de 90 dias aparece marcada como antiga;
  - busca por nome, marca ou código, sem diferenciar acento;
  - "Novo produto" pede nome e nicho e abre a edição.
- **Fornecedores:**
  - lista com nome fantasia (ou razão social), CNPJ, cidade/UF, nichos e quantos produtos ele vende;
  - busca por nome, CNPJ (com ou sem pontuação) ou cidade, e filtro por UF;
  - "Novo fornecedor" pede a razão social (e, se quiser, o nicho) e abre a edição.
- **Nichos:**
  - criar, renomear (com descrição) e arquivar ou desarquivar;
  - mostra quantos fornecedores e produtos ativos tem cada um. Clicar no número abre a lista filtrada;
  - nome repetido é recusado, mesmo com outro acento ou outra caixa.
- **"Mostrar arquivados"** nas três abas. Nada é excluído.

### Edição do fornecedor
- Razão social, nome fantasia, CNPJ (dígitos conferidos), cidade, UF, site e observações.
- **Nichos que atende**, marcando caixas.
- **Contatos** (vários): nome, telefone e e-mail.
- **Produtos que vende**, com o preço dele, a data e a marca "Menor preço" quando é o mais barato.
- Arquivar: o fornecedor sai da lista e da escolha nos produtos, e o preço dele deixa de contar como o menor.

### Edição do produto
- Nome, nicho, unidade, marca, CATMAT/CATSER e descrição.
- **Preços dos fornecedores:** fornecedor, preço de custo (até 4 casas), data da cotação, prazo de entrega em dias e pedido mínimo. O menor preço fica marcado enquanto a pessoa digita.
- Mudou o preço e não mexeu na data: a cotação passa a ser de hoje.
- **Foto e ficha técnica:** envio (até 10 MB; foto em JPG, PNG ou WEBP; ficha também em PDF), miniatura da foto, baixar e apagar. Enviar arquivo não atrapalha quem está editando.

### Comum
- Salva sozinho 800 ms depois da última alteração, como nas propostas. Se outra pessoa alterou o mesmo cadastro, a tela pede para recarregar.
- Administrador e Comercial criam e alteram; Leitura só consulta.

## Técnico
- Módulo `catalog` no backend; migração `V21__catalog.sql`; dados de exemplo em `R__dev_seed_catalog.sql` (só em dev e teste).
- API em `/api/v1/catalog` (nichos, fornecedores, produtos e arquivos); cliente do front gerado de novo.
- Testes: domínio (`CatalogDomainTest`), fluxo pela API contra Postgres (`CatalogFlowTests`), contagem de consultas das listas e telas (`QueryCountTests`), E2E no desktop e no celular (`catalog.spec.ts`) e as telas novas na auditoria de acessibilidade (`a11y.spec.ts`).

## Resultado dos testes (23/11/2026)
- **Backend:** `mvn verify` com 250 testes, todos passando. Inclui os novos (`CatalogDomainTest` com 9, `CatalogFlowTests` com 4) e o `QueryCountTests` com as telas novas.
- **Frontend:** build, lint, Prettier e os 22 testes de unidade passando.
- **E2E** (`ng serve` + backend local):
  - `catalog.spec.ts` passa no desktop e no celular, inclusive repetido 2 vezes em paralelo;
  - `a11y.spec.ts`, com as 5 telas novas, passa sem violação;
  - na suíte inteira, 43 passaram. Os 13 que falharam dependem do ambiente do compose: os de IA (leitura de ata, pedido, follow-up, kit de carona e assistente) com a IA desligada no backend local, e o login por `127.0.0.1`, porque o `ng serve` só escuta em `localhost`;
  - `atas.spec.ts` estourou o tempo na suíte cheia e passou rodando sozinho.
- **Contra o compose** (`E2E_BASE_URL=http://localhost:14200`, imagens refeitas em 23/11): 46 passaram, inclusive o login por `127.0.0.1`. Os 10 que falharam são os de IA, porque o `.env` local está sem `ANTHROPIC_API_KEY` e `GEMINI_API_KEY`.
- O cliente da API foi gerado de novo. Os records de pedido do catálogo ganharam nomes próprios (`SupplierContactRequest`, `ProductOfferRequest` e `CatalogVersionRequest`) para não sobrescrever o `ContactRequest` dos contatos da licitação no OpenAPI.

## Fora desta fase
- Escolher produto do cadastro ao montar a proposta de preços.
- Histórico de preços por fornecedor.
- Importar fornecedores ou produtos de planilha.
