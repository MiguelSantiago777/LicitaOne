# Fase 14 — Plano de trabalho (relatórios para o balanço)

**Branch:** `fase-14` (a partir de `fase-13`) · **Plano de:** 23/11/2026 · **Origem:** pedido do usuário de 23/11/2026

**Status:** feito em 23/11/2026, aguardando validação ([relatório](fase-14.md), [ADR 0024](../adr/0024-relatorios-do-balanco.md)). Plano aprovado em 23/11/2026 ("pode commitar a 13 e seguir com a 14"). Nas dúvidas sem resposta, vale o recomendado: ano civil (janeiro a dezembro, escolhendo o ano), pedido sem cancelamento (todos contam no mês), tipo = contrato/empenho/dispensa e modalidade (sem o tipo de material) e o resumo só na aba Relatórios.

## O pedido (23/11/2026)
"Na aba de relatórios, preciso de dados gráficos pra balanço: saber quanto foi feito em cada mês, qual o melhor mês, dividir por ano etc."

O sistema ainda não tem aba de relatórios: ela é nova.

## Decisões de 23/11/2026
1. **Quatro números**, sempre separados (não se somam, porque um mesmo negócio passa por vários deles):
   - **Pedidos recebidos** (empenho/AF), pela data do pedido. É o mais perto do faturamento.
   - **Contratos assinados** (contrato, empenho e dispensa), pela data de assinatura.
   - **Licitações vencidas**, pelo valor total e pela data em que foram vencidas.
   - **Caronas**, pelo valor da adesão e pela data da adesão.
2. **Quebras:** por mês e ano, por órgão, por tipo e por município/UF.
3. **Exportar em PDF com os gráficos.**
4. **Todos os perfis veem** (Administrador, Comercial e Leitura), como o Painel.

## Escopo

### A. Aba "Relatórios" no menu
- Escolha do **ano** (padrão: o atual) e do **número** (pedidos, contratos, licitações vencidas ou caronas).
- Filtro opcional por **UF** e por **esfera** do órgão (municipal, estadual, federal).

### B. Visão do ano
- **Cartões no topo:**
  - total do ano;
  - média por mês;
  - **melhor mês** (e o pior com movimento);
  - quantidade (de pedidos, contratos…);
  - comparação com o ano anterior até o mesmo mês ("+18% sobre jan–nov de 2025").
- **Gráfico de colunas mês a mês**, com o ano anterior em cinza ao lado para comparar. O melhor mês fica destacado.
- **Acumulado do ano** (linha), com o ano anterior para comparar.
- **Tabela mês a mês** embaixo do gráfico: valor, quantidade e variação sobre o mês anterior. Ela serve também para quem usa leitor de tela.

### C. Comparação entre anos
- Total de cada ano desde o primeiro registro, em colunas, e a tabela "ano × mês" com os totais (o formato da planilha de controle).

### D. Quebras
- **Por órgão:** os 10 que mais compraram no período, em barras, com o valor e a parte do total. Os demais aparecem somados em "outros".
- **Por município/UF:** os 10 municípios que mais compraram e o total por UF.
- **Por tipo:**
  - nos pedidos e contratos, contrato × empenho × dispensa;
  - nas licitações vencidas, por modalidade (pregão, dispensa, concorrência…).

### E. PDF
- Botão **"Baixar PDF"**: o relatório do ano escolhido, com os filtros aplicados, a data de geração e os mesmos gráficos e tabelas.
- Sai no papel timbrado da Shema, como as propostas.

## Técnico
- Módulo novo **`reports`**, só de leitura. As somas saem de consultas agregadas no banco (`group by` mês, órgão, UF e tipo), sem carregar registro por registro. Não cria tabela nova.
- O módulo lê os números dos módulos donos (`tenders`, `contracts`, `pricerecords`) por uma API pública de cada um, sem acessar as tabelas dos outros diretamente.
- **Gráficos na tela:** SVG feito no próprio Angular, sem biblioteca nova. As cores são as do sistema, os gráficos funcionam no tema escuro e no celular, e cada um tem uma tabela equivalente.
- **Gráficos no PDF:** desenhados pelo OpenPDF (já usado nas propostas), com o mesmo visual. Sem dependência nova.
- Valores contam pela data do registro: um pedido de 05/03 entra em março, mesmo que tenha sido lançado depois.
- Licitação vencida conta o **valor total da licitação** (o mesmo campo da lista). Pedido, contrato e carona contam o próprio valor.

## Etapas
1. Backend: consultas agregadas, API de relatórios e testes (somas conferidas com dados conhecidos, contagem de consultas).
2. Tela: filtros, cartões, gráficos (colunas, acumulado, barras) e tabelas.
3. PDF com os gráficos.
4. E2E (1440 e 390), acessibilidade, ADR 0024 e relatório `docs/fases/fase-14.md`.

## Fora desta fase
- Metas mensais (comparar o feito com uma meta).
- Margem e lucro: o sistema não tem o custo de cada venda. O preço de custo do catálogo (Fase 13) ainda não está ligado aos pedidos.
- Quebra por responsável (não foi pedida).
- Exportar em Excel.
- Clicar no órgão para abrir as licitações dele: a lista de licitações não tem aba "todas" nem filtro de órgão no endereço (decidido na execução, 23/11).

## Dúvidas
1. **Ano civil ou outro período?** O balanço de vocês é de janeiro a dezembro? Ou querem escolher um período livre (por exemplo, jul/2025 a jun/2026)?
2. **Pedido cancelado ou devolvido:** existe na prática? Hoje o sistema não marca pedido cancelado; se acontecer, ele conta no mês dele.
3. **"Tipo":** além de contrato/empenho/dispensa e da modalidade, querem dividir pelo **tipo de material** da licitação (o campo "material" do cadastro)?
4. **Página inicial:** querem um resumo do mês (total e comparação) também no Painel, ou só na aba Relatórios?
