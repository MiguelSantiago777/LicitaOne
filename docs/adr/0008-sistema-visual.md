# 0008 — Sistema visual e componentes do front

**Status:** Aceito · 27/10/2026

## Contexto
A equipe passa o dia em tabelas e listas. A especificação pede um visual sóbrio e com personalidade (1 cor de marca, neutros e cores semânticas), números tabulares e acessibilidade AA. Pede também que o visual seja definido com as skills Taste e Awesome Design.

## Decisão
- **Estilo `achromatic-ledger`** (skill `design-taste-frontend`, modo app, seed 1428088946). O chrome não tem cor; dinheiro e contagens ficam em tinta preta; a **única cor, o azul `#1d5fbf`**, vai no dado principal de cada tela (o saldo a pedir, o período atual) e no foco. O botão principal é quase preto. Verde, âmbar e vermelho só aparecem em situações (pills claras com texto escuro do mesmo tom).
- **Rosto do produto:** fundo cinza-azulado de livro-razão (`#f2f4f7`) em vez do cinza neutro de kit, a marca de três barras decrescentes (o saldo sendo pedido) e, a partir da Fase 2, a **barra de saldo** (contratado, pedido, falta pedir) em cada contrato, como o elemento que só este produto tem.
- **Tipografia:** Geist (400/500/600/700) servida pelo próprio app (`@fontsource`, sem Google Fonts em runtime, o que respeita a CSP `font-src 'self'`). Números com `tabular-nums`; R$ sempre em pt-BR (`R$ 2.573.225,29`). Geist Mono só para IDs.
- **Componentes:** **Angular CDK** (drag-and-drop do kanban, overlay, a11y) com componentes próprios sobre tokens CSS, em vez de Angular Material. Os componentes do Material impõem o visual Material 3 (elevação, ripple, densidade) que contradiz este estilo, e sobrescrever os tokens de cada um custa mais do que escrever os poucos componentes de que o CRM precisa (botão, campo, abas, tabela, pill, menu). O CDK entrega o comportamento difícil (teclado no drag-and-drop, foco, overlays) sem visual.
- **Tema escuro** já preparado em tokens (`:root[data-theme='dark']`), ainda sem seletor na interface.
- **Shell "open plane":** barra lateral de 256px no mesmo fundo da página, item ativo em pílula cinza, sem barra superior. No celular (≤ 860px) vira uma barra de 52px com menu em tela cheia.

## Verificação
`lint.mjs --app`: 0 FAIL, com 2 avisos defensáveis (sombra no aviso flutuante e o token mono para IDs). `inspect.mjs --app` na tela de login: 0 FAIL. A auditoria Web Design Guidelines está em `docs/fases/fase-0.md`.
