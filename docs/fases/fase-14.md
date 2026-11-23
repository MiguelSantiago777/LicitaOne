# Fase 14 — Relatórios para o balanço

**Branch:** `fase-14` (a partir de `fase-13`) · **Data:** 23/11/2026 · **Plano:** [fase-14-plano.md](fase-14-plano.md) · **Decisão:** [ADR 0024](../adr/0024-relatorios-do-balanco.md)

## O que ficou pronto

### Aba "Relatórios" (menu Dados)
- **Quatro números**, um por aba: pedidos recebidos, contratos assinados, licitações vencidas e caronas.
- **Filtros:** ano (do primeiro com movimento até o atual), UF e esfera do órgão. Número, ano e filtros ficam no endereço, então dá para mandar o link.
- **Cartões:**
  - total do ano e quantidade;
  - média por mês (só os meses que já passaram);
  - melhor mês com o valor, e o mais fraco;
  - comparação com o ano anterior nos mesmos meses, em verde quando sobe e em vermelho quando cai.
- **Mês a mês:** colunas do ano em azul e do ano anterior em cinza ao lado. O melhor mês leva o valor na ponta. Passar o mouse (ou tocar) mostra os dois valores e a quantidade.
- **"Ver tabela mês a mês":** valor, quantidade, ano anterior e variação sobre o mês anterior, com o total. O melhor mês fica marcado.
- **Acumulado do ano:** linha do ano e do anterior. Passar o mouse mostra o acumulado até aquele mês.
- **Comparação entre anos:** total de cada ano em barras e "Ver tabela ano × mês", no formato da planilha de controle.
- **Quebras do ano:**
  - os 10 órgãos e os 10 municípios que mais compraram, com valor e percentual; o resto soma em "Outros";
  - o total por UF;
  - nos pedidos e contratos, por tipo de contrato (contrato, empenho, dispensa); nas licitações vencidas, por modalidade.
- **Baixar PDF:** o relatório com os filtros aplicados, no papel timbrado, com os mesmos gráficos e tabelas.

### Também nesta fase
- O selo vermelho do menu ficou um pouco mais escuro (`#dc2626`), porque o branco sobre o vermelho antigo não passava no contraste mínimo.

## Técnico
- Módulo `reports` (só leitura) e APIs públicas novas: `TenderFigures`, `ContractFigures` e `CaronaFigures`, com o registro comum `shared.reporting.MonthlyFigure`. Sem migração.
- API: `GET /api/v1/reports` e `GET /api/v1/reports/pdf` (`metric`, `year`, `state`, `sphere`).
- Gráficos sem biblioteca nova: SVG na tela e OpenPDF no PDF.

## Resultado dos testes (23/11/2026)
- **Backend:** `mvn verify` com 261 testes, todos passando. Os novos são:
  - `ReportBuilderTest` (8): contas com números conhecidos, como média dos meses passados, melhor e pior mês, comparação no mesmo período, "Outros" e filtros;
  - `ReportFlowTests` (3): cada número confere com a soma feita direto no banco, nos anos atual e anterior, e também filtrado; PDF e perfil Leitura;
  - `QueryCountTests`: no máximo 3 consultas por relatório.
- **Frontend:** build, lint e os 22 testes de unidade passando.
- **E2E contra o compose** (`E2E_BASE_URL=http://localhost:14200`):
  - `reports.spec.ts` passa no desktop e no celular: cartões, gráficos, tabela que fecha com o total, filtro na URL, troca de número e nome do PDF;
  - as duas telas de relatório entraram na auditoria de acessibilidade, que passa;
  - na suíte inteira, 46 passaram. As falhas foram as 10 de IA (o `.env` local não tem chave de IA) e dois tempos estourados com a suíte cheia: a auditoria de todas as telas (que passou a ter mais telas e foi para 3 minutos) e `atas.spec.ts`. Os dois passaram rodando sozinhos.
- **Conferência visual:** o PDF gerado e as telas em 1440 e 390 px foram olhados. Na conferência foram corrigidos:
  - o eixo com valores quebrados (R$ 1,3 mi);
  - os nomes dos meses encavalados no celular;
  - o título sobreposto à tabela no PDF;
  - o arredondamento diferente entre a tela e o PDF.

## Fora desta fase
- Metas mensais.
- Margem e lucro.
- Quebra por responsável.
- Exportar em Excel.
- Clicar no órgão para abrir as licitações dele.
