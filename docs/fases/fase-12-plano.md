# Fase 12 — Plano de trabalho (propostas de preços)

**Branch:** `fase-12` (a partir de `fase-11`) · **Plano de:** 13/11/2026 · **Origem:** pedido do usuário de 12/11/2026

**Status:** feito em 13/11/2026, aguardando validação ([relatório](fase-12.md), [ADR 0022](../adr/0022-propostas-de-precos.md)).

## O pedido (12/11/2026)
Uma aba para gerar propostas de preços. Ao entrar, o sistema já gera o número da proposta e abre:
- **dados do cliente:** nome, CNPJ, endereço, e-mail e telefones;
- **tipo:** produto ou serviço;
- **itens:** número do item, descrição, valor unitário, quantidade e valor total;
- **rodapé:** validade em dias, forma de pagamento, prazo de entrega, data, responsável, cargo, documento e assinatura.

Decidido em 12/11: fica como **aba do LicitaOne**, não como sistema à parte. Login, usuários, deploy e backup já existem.

## Decisões de 13/11/2026
1. **Número fixo:** o número é dado na criação e nunca muda, nem quando a proposta é editada. Não existe "revisão com número novo".
2. **Documento = CPF** do responsável.
3. **Sem assinatura no sistema:** o arquivo sai sem assinatura, e quem baixa assina no assinador (gov.br ou certificado).
4. **Cliente sempre digitado na hora:** não puxa órgão nem itens de licitação.

## Respostas às dúvidas (13/11/2026)
O usuário respondeu "pode ser" ao plano. Nas dúvidas sem resposta explícita, segui o recomendado:
1. Número `2026/0001`, recomeçando a cada ano.
2. Unidade e marca entram como colunas opcionais; no PDF, só aparecem se algum item tiver.
3. Sem timbrado por enquanto (cabeçalho em texto).
4. A proposta continua editável depois de baixada, mantendo o número.

## Escopo

### A. Lista de propostas (aba "Propostas" no menu)
- Colunas: número, data, cliente, CNPJ, tipo, valor total, responsável.
- Busca por número, cliente ou CNPJ e filtro por tipo e período.
- Botão **"Nova proposta"**.

### B. Nova proposta
- Ao clicar em "Nova proposta", o sistema **já cria a proposta e mostra o número** (rascunho salvo).
- **Número:** sequencial por ano, `2026/0001`, gerado no banco (sem dois iguais, mesmo com duas pessoas criando ao mesmo tempo). Não é reaproveitado: uma proposta aberta e abandonada fica na lista com o número dela, e pode ser **cancelada** (continua na lista, riscada).
- **Cliente:** nome, CNPJ (com conferência dos dígitos), endereço, e-mail e telefones (mais de um).
- **Tipo:** produto ou serviço. Muda só o título do documento ("Proposta de preços — fornecimento de produtos" ou "— prestação de serviços").
- **Itens:** tabela com número do item (preenchido em sequência, editável), descrição, valor unitário e quantidade. O **valor total** de cada linha e o **total geral** são calculados, e o total geral sai também **por extenso**.
- **Rodapé:** validade (dias), forma de pagamento, prazo de entrega e data da proposta (hoje, editável).
- **Responsável:** vem do usuário logado (nome, cargo e CPF) e pode ser trocado na proposta.
- Salva sozinho a cada alteração, como nas outras telas.

### C. Documento para assinar
- Botão **"Baixar PDF"**: cabeçalho com os dados da empresa (os mesmos `SHEMA_COMPANY_*` do kit de carona), número, cliente, tabela de itens, totais, rodapé e, no fim, a **linha de assinatura em branco** com nome, cargo e CPF do responsável.
- PDF porque é o formato que os assinadores (gov.br e certificado ICP-Brasil) aceitam.
- O nome do arquivo leva o número: `Proposta-2026-0001.pdf`.

### D. Ações na proposta
- **Duplicar:** cria uma proposta nova (número novo) com os mesmos dados, para outro cliente ou outra data.
- **Cancelar:** a proposta fica na lista, marcada como cancelada, e não pode mais ser editada.

## Técnico
- Módulo novo **`proposals`** no backend, tabela `proposals` + `proposal_items` (migração V20). Contador por ano numa tabela própria, atualizado na mesma transação da criação.
- Usuários ganham dois campos opcionais, **cargo** e **CPF**, editáveis em "Usuários" e no próprio perfil. O CPF é dado pessoal: aparece só na proposta e no cadastro, nunca em log.
- Valores em `numeric(15,2)`; quantidade com até 3 casas decimais.
- PDF gerado no backend (OpenPDF ou PDFBox, a escolher na etapa 2).
- Permissões: Administrador e Vendas criam e editam; Leitura só vê e baixa.
- Sem IA nesta fase.

## Etapas
1. Banco e backend: propostas, itens, numeração por ano, cargo e CPF no usuário, API e testes.
2. PDF da proposta.
3. Tela: lista, formulário com tabela de itens e totais, baixar PDF, duplicar e cancelar.
4. E2E (1440 e 390), axe, ADR 0022 e relatório `docs/fases/fase-12.md`.

## Fora desta fase
- Assinatura dentro do sistema.
- Ligar a proposta a uma licitação ou puxar dados de órgão.
- Mandar a proposta por e-mail pelo sistema.

## Dúvidas
1. **Formato do número:** `2026/0001`, recomeçando a cada ano, serve? Ou vocês já usam outra numeração e eu continuo a partir dela?
2. **Colunas dos itens:** além das que você pediu, entram **unidade** (un, cx, kg, mês…) e **marca**? Em proposta de licitação costumam ser exigidas.
3. **Papel timbrado:** tem logo ou timbrado da Shema para o PDF? Se tiver, mande o arquivo. Sem ele, o cabeçalho sai só com texto.
4. **Editar depois de baixar:** a proposta pode ser alterada a qualquer momento, mantendo o número? Ou depois de baixada ela trava e, para mudar, duplica?
