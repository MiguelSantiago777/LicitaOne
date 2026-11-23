# Fase 13 — Plano de trabalho (fornecedores e produtos por nicho)

**Branch:** `fase-13` (a partir de `fase-12`) · **Plano de:** 23/11/2026 · **Origem:** pedido do usuário de 23/11/2026

**Status:** plano aprovado em 23/11/2026 ("pode seguir"; nas dúvidas, vale o plano: sem importação, 90 dias e sem nichos iniciais em produção). Feito em 23/11/2026, aguardando validação ([relatório](fase-13.md), [ADR 0023](../adr/0023-fornecedores-e-produtos.md)).

## O pedido (23/11/2026)
"Preciso de uma aba de fornecedores e produtos, por nichos."

## Decisões de 23/11/2026
1. **Ligação:** o fornecedor atende um ou mais nichos; o produto fica em **um** nicho; cada produto tem um ou mais fornecedores, **cada um com o seu preço de custo**. Assim dá para ver quem vende mais barato.
2. **Nichos cadastráveis na tela:** Administrador e Vendas criam, renomeiam e arquivam nichos (ex.: limpeza, informática, material de escritório).
3. **Sem ligação com propostas por enquanto:** esta fase faz só o cadastro e a consulta. Escolher produto ao montar a proposta fica para depois.
4. **Dados extras do produto:** preço de custo e data da cotação, código CATMAT/CATSER, foto ou ficha técnica, prazo de entrega e pedido mínimo.

## Escopo

### A. Aba "Fornecedores e produtos" no menu
Três visões na mesma aba: **Nichos**, **Fornecedores** e **Produtos**. Um filtro de nicho no topo vale para as três.

### B. Nichos
- Nome (único, sem diferenciar maiúsculas) e descrição opcional.
- Mostra quantos fornecedores e produtos tem cada um.
- Arquivar em vez de excluir: um nicho arquivado some dos filtros e da escolha, mas os cadastros ligados a ele continuam.

### C. Fornecedores
- Razão social, nome fantasia, CNPJ (com conferência dos dígitos, inclusive o alfanumérico, como nas propostas), cidade/UF, site e observações.
- **Contatos** (mais de um): nome, telefone, e-mail.
- **Nichos que atende** (um ou mais).
- Lista com busca por nome ou CNPJ e filtro por nicho e UF. Linha inteira abre o fornecedor.
- No fornecedor, a lista dos produtos que ele vende, com preço e data.
- Arquivar (não excluir), pelo mesmo motivo dos nichos.

### D. Produtos
- Nome, descrição, **nicho**, unidade (un, cx, kg…), marca e código **CATMAT/CATSER** (opcional).
- **Arquivos:** foto e ficha técnica (PDF ou imagem), guardados no armazenamento S3 que o sistema já usa.
- **Fornecedores do produto**, cada linha com: preço de custo, data da cotação, prazo de entrega (dias) e pedido mínimo (quantidade).
- Lista com busca por nome, marca ou código e filtro por nicho e fornecedor. Mostra o **menor preço** e de qual fornecedor, e a data dessa cotação.
- Cotação com mais de 90 dias aparece marcada como antiga (só aviso visual).

### E. Permissões
Administrador e Vendas criam e editam; Leitura só vê. Salva sozinho a cada alteração, como nas outras telas.

## Técnico
- Módulo novo **`catalog`** no backend: tabelas `niches`, `suppliers`, `supplier_contacts`, `supplier_niches`, `products`, `product_files` e `product_offers` (produto × fornecedor com preço, data, prazo e pedido mínimo). Migração V21.
- Preço de custo com até 4 casas (`numeric(15,4)`, como o valor unitário das propostas); pedido mínimo com até 3 casas, como a quantidade.
- Uma oferta por par produto × fornecedor (atualizar o preço troca a data). Histórico de preços fica fora.
- Arquivos pelo `shared.storage` existente, com limite de tamanho e tipos aceitos (JPG, PNG, WEBP e PDF).
- Busca sem acento e sem diferenciar maiúsculas.
- Sem IA nesta fase.

## Etapas
1. Banco e backend: nichos, fornecedores, contatos, produtos, ofertas, API e testes (incluindo contagem de consultas na lista).
2. Arquivos do produto (foto e ficha técnica).
3. Tela: aba com as três visões, formulários com salvamento automático, filtros e menor preço.
4. E2E (1440 e 390), axe, ADR 0023 e relatório `docs/fases/fase-13.md`.

## Fora desta fase
- Escolher produto do cadastro ao montar a proposta de preços.
- Histórico de preços por fornecedor.
- Importar fornecedores ou produtos de planilha.
- Ligar produtos a itens de licitação, ata ou contrato.

## Dúvidas
1. **Importação:** vocês têm hoje uma planilha de fornecedores ou produtos? Se tiver, a importação pode entrar nesta fase (a partir dela) em vez de ficar para depois.
2. **Cotação antiga:** 90 dias está bom para marcar o preço como desatualizado?
3. **Nichos iniciais:** quer que eu já crie alguns nichos? Se sim, quais?
