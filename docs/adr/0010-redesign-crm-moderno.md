# 0010 — Redesign: visual de CRM moderno

**Status:** Aceito · 27/10/2026 · substitui o [0008](0008-sistema-visual.md)

## Contexto
O usuário reprovou o estilo da Fase 0 ("achromatic ledger": chrome sem cor, telas vazias) e pediu um visual moderno, baseado nos melhores CRMs do mercado.

## Decisão
- Padrão dos CRMs de referência (HubSpot, Pipedrive, Attio, Salesforce Lightning):
  - **barra lateral escura** (`#0f172a`) com item ativo destacado e contadores de pendências;
  - **barra superior** com busca global, notificações e botão "+ Novo";
  - canvas claro (`#f5f7fb`) e **cartões brancos com sombra suave** e raio de 12px.
- **Azul da marca `#2563eb`** nos botões principais, links, abas ativas e progresso. **Cores de status**: âmbar = aguardando contrato, azul = assinado, verde = em andamento, cinza = encerrado, vermelho = atrasado, violeta = ata/carona.
- **Inter** (variável, servida localmente) e ícones **Lucide** (ISC).
- **Avatares** com iniciais e cor estável por pessoa ou órgão.
- **KPIs** em cartões com ícone, **barras de saldo** em cada contrato e **kanban** no formato do CRM que a equipe já usa: colunas com cor da etapa, contagem, soma, prioridade em estrelas e faixa rosa de tarefa atrasada.
- **Dados de exemplo** (`frontend/src/app/features/demo/demo-data.ts`), tirados da especificação e da planilha do cliente. Um selo "Dados de exemplo" na barra superior deixa isso explícito. Eles saem quando a API da Fase 1 entrar.
- As regras de "não usar" da skill `design-taste-frontend` (sombras, ícones em quadrados coloridos, rótulos em maiúsculas) **deixam de valer** para este produto, por decisão do usuário. Continuam valendo: contraste AA, foco visível, números tabulares e moeda pt-BR.

## Consequências
- Build de produção com `inlineCritical: false`. O carregamento de CSS crítico do Angular usa um `onload` inline que a CSP do nginx bloqueia, e o CSS nunca era ativado em produção (afetava também a versão anterior).
