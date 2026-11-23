# 0024 — Relatórios para o balanço (Fase 14)

**Status:** Proposto · 23/11/2026 · origem: pedido do usuário de 23/11/2026 ([plano](../fases/fase-14-plano.md), [decisões](../decisoes.md#relatórios-para-o-balanço-23112026))

## Contexto
A Shema quer ver, para o balanço, quanto foi feito em cada mês, qual o melhor mês, a divisão por ano e as quebras por órgão, tipo e município/UF. Quer ver isso em gráficos na tela e também baixar em PDF. O sistema já guarda tudo com data e valor; faltava somar e mostrar.

## Decisões

### Quatro números separados
- **Pedidos recebidos** (data do pedido), **contratos assinados** (data da assinatura), **licitações vencidas** (data da vitória, valor total da licitação) e **caronas** (data da adesão).
- Não se somam: o mesmo negócio vira licitação vencida, depois contrato, depois pedidos. A tela mostra um por vez, em abas.
- Licitação vencida conta se tem data de vitória e não voltou para "em disputa" nem "perdida". Por isso as que já viraram contrato, estão em andamento ou foram encerradas continuam contando no mês da vitória.

### Módulo `reports`, só leitura, somado no banco
- Cada módulo dono expõe na API pública os totais já agrupados por mês, licitação/órgão e tipo:
  - `TenderFigures`: licitações vencidas, mais o órgão de cada licitação e os dados do órgão;
  - `ContractFigures`: contratos e pedidos;
  - `CaronaFigures`: caronas.
- O registro comum é `shared.reporting.MonthlyFigure`. O `reports` não lê tabela de outro módulo; o `ModularityTests` confere.
- O `reports` busca os totais de todos os anos de uma vez. São algumas centenas de linhas agrupadas, mesmo com anos de uso. Depois resolve o órgão (pedidos e contratos chegam pela licitação) e monta tudo no `ReportBuilder`, uma classe sem banco nem relógio, testada com números conhecidos.
- No total, 3 consultas por relatório (2 nas caronas), limite conferido pelo `QueryCountTests`.

### Contas
- **Média por mês:** só os meses que já passaram. No ano corrente, janeiro até o mês atual; em ano fechado, 12. Um pedido lançado com data futura entra no total, mas não na média.
- **Melhor mês:** o de maior valor. **Mais fraco:** o de menor valor entre os meses passados que tiveram movimento. Não aparece se for o mesmo que o melhor.
- **Comparação com o ano anterior:** sobre os mesmos meses (jan até o mês atual). Comparar um ano parcial com um ano inteiro distorce. Sem base (ano anterior zerado), mostra "—" em vez de um percentual infinito.
- **Variação mês a mês:** só nos meses que já passaram.
- **Quebras:** os 10 maiores órgãos e os 10 maiores municípios. Os demais viram "Outros" (em cinza, com quantos são). UF e tipo aparecem inteiros.
- **Filtro de UF e esfera:** pelo órgão. A lista de anos do seletor não muda com o filtro.

### Gráficos
- Sem biblioteca de gráficos: SVG feito em componentes Angular e, no PDF, desenhado direto com o OpenPDF (vetor). Os dois usam o mesmo arredondamento do eixo: topo em 4 passos de 1, 2, 2,5 ou 5 × 10ⁿ.
- Segue o skill de dataviz:
  - forma de **ênfase**: o ano no azul do sistema (`--brand`) e o ano anterior em cinza `#94a3b8`. O validador aprova a separação de cores, inclusive para daltonismo;
  - o cinza fica abaixo de 3:1 com o fundo e abaixo do mínimo de saturação de uma cor categórica, o que é esperado no cinza de ênfase. Por isso todo gráfico tem legenda, tooltip e tabela com os mesmos números;
  - colunas finas com a ponta arredondada e a base reta;
  - linhas de 2 px;
  - valor escrito só no melhor mês e na ponta do acumulado;
  - grade de 1 px;
  - texto sempre na cor de texto, nunca na cor da série.
- Os gráficos desenham na largura real do elemento (`ResizeObserver`), não num `viewBox` esticado, para o texto não encolher no celular. Abaixo de 30 px por mês, o eixo mostra só a inicial (J F M A…).
- As barras horizontais (órgãos, municípios, UF, tipo) são HTML: nomes longos quebram linha e o leitor de tela lê como lista.
- Ano e o anterior sem nenhum movimento: a tela mostra um aviso em vez de um gráfico com eixo inventado.

### PDF
- `GET /api/v1/reports/pdf`, com os mesmos parâmetros da tela.
- Traz cartões, colunas, tabela mês a mês, acumulado, tabela ano × mês (valores curtos para caber em retrato) e as quebras em barras.
- Sai no papel timbrado das propostas (mesma propriedade `shema.proposals.letterhead`).
- Nome do arquivo: `Relatorio-pedidos-recebidos-2026-RJ.pdf`.

### Acesso
- Todos os perfis veem, como o Painel. Não há escrita.

## Consequências
- Para metas mensais, basta um cadastro de meta por mês e número; o `ReportBuilder` já devolve o mês a mês.
- Para margem, os pedidos precisariam do preço de custo (o catálogo da Fase 13 ainda não está ligado aos pedidos).
- Pedido cancelado ou devolvido não existe no sistema. Se passar a existir, as consultas de `ContractFigures` precisam excluir.
- Na execução, o selo vermelho do menu (licitações vencidas aguardando contrato) passou de `#ef4444` para `#dc2626`. O branco sobre o vermelho antigo dava 3,8:1, abaixo do AA, e a auditoria da tela nova pegou.
