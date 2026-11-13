# Fase 12 — Propostas de preços

**Branch:** `fase-12` (a partir de `fase-11`) · **Data:** 13/11/2026 · **Plano:** [fase-12-plano.md](fase-12-plano.md) · **Decisão:** [ADR 0022](../adr/0022-propostas-de-precos.md)

## O que ficou pronto

### Aba "Propostas" (menu Comercial)
- **Lista:**
  - colunas número, data, cliente (com CNPJ), tipo, responsável e valor total;
  - busca por número (`2026/0001` ou `1`), cliente ou CNPJ;
  - filtro de produto ou serviço;
  - "Mostrar canceladas".
- **"Nova proposta"** já cria a proposta com o número do ano e abre a edição. O perfil Leitura vê e baixa, mas não cria nem altera.
- **Edição:**
  - **dados do cliente:** nome, CNPJ (dígitos conferidos, aceita o CNPJ alfanumérico), telefones, endereço e e-mail;
  - **produto ou serviço**, que muda o subtítulo do PDF;
  - **itens:** número (vem em sequência e pode ser trocado), descrição, unidade, marca, quantidade e valor unitário (até 4 casas). O valor total da linha e o total geral são calculados enquanto a pessoa digita;
  - **condições:** validade em dias, data da proposta, forma de pagamento e prazo de entrega;
  - **responsável:** nome, cargo e CPF. Vêm do cadastro de quem criou e podem ser trocados na proposta. "Usar como meu padrão" grava cargo e CPF no cadastro.
- **Salva sozinha** 800 ms depois da última alteração. A tela mostra "Salvando…", "Salvo" ou o erro. Se outra pessoa alterou a mesma proposta, a tela pede para recarregar.
- **Resumo** ao lado (em cima, no celular):
  - total e total por extenso;
  - o que falta para gerar o PDF;
  - aviso quando faltam dados da empresa no servidor.
- **Baixar PDF** só fica disponível com tudo preenchido e salvo. **Duplicar** cria outra proposta com número novo. **Cancelar** pede confirmação e pode ser desfeito.

### PDF
- A4, com:
  - cabeçalho da empresa;
  - "PROPOSTA DE PREÇOS Nº 2026/0001";
  - cliente;
  - tabela de itens, com unidade e marca só se algum item tiver;
  - total e total por extenso;
  - condições;
  - cidade e data por extenso;
  - **linha de assinatura em branco** com nome, cargo, CPF e razão social.
- O bloco da assinatura não se divide entre páginas, e cada página tem o número da proposta no rodapé.
- O arquivo se chama `Proposta-2026-0001.pdf` e vai para o assinador.

### Usuários
- Cargo e CPF (opcionais) na edição de usuário e no endpoint `PUT /me/profile`.

### No backend
- Módulo `proposals`:
  - agregado `Proposal` com itens;
  - `ProposalNumbers`: upsert por ano em `proposal_counters`;
  - `ProposalService` e `ProposalController` (`/api/v1/proposals`: lista, criar, salvar, duplicar, cancelar, reabrir, PDF e aviso da empresa);
  - `OpenPdfProposalDocuments` (OpenPDF 2.0.5).
- Migração `V20__proposals.sql`: tabelas de propostas, itens e contador, e cargo e CPF em `users`.
- `shared.domain`: `BrDocuments` (CPF e CNPJ, inclusive o alfanumérico) e `AmountInWords` (valor por extenso).
- `CompanyData` e `CompanyProperties` passaram de `extraction` para `shared.company`.

## Testes
- Backend: **237 testes**, todos passando (`mvnw verify`, com Spotless e JaCoCo). Os novos:
  - `AmountInWordsTest`: 27 casos, de "um centavo" a "três bilhões de reais";
  - `BrDocumentsTest`: CPF, CNPJ numérico e alfanumérico;
  - `ProposalTest`: número fixo, totais, item incompleto, cancelamento e duplicação;
  - `ProposalFlowTests`, contra Postgres real: fluxo completo com PDF, conflito de versão, permissões e **8 criações simultâneas sem número repetido**.
- E2E (1440 e 390): `proposals.spec.ts`:
  - cria;
  - preenche;
  - confere o total e o extenso;
  - roda o axe na edição;
  - baixa o PDF;
  - recarrega;
  - duplica;
  - cancela;
  - acha na lista.

  A lista entrou no `a11y.spec.ts`. Tudo passou.
- Frontend: lint, Prettier e testes de unidade passando.

## Pendente
- **Dados da empresa no servidor** (`SHEMA_COMPANY_*`: CNPJ, endereço, cidade, e-mail e telefone). Sem eles, o cabeçalho do PDF mostra os campos entre colchetes.
- **Timbrado ou logo**, se houver: hoje o cabeçalho é texto.
- Conferir com quem assina se o assinador aceita o PDF como sai (gov.br e certificado aceitam PDF comum).
