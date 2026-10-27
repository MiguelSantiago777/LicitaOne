# 0005 — Ajustes no modelo de domínio a partir das respostas do cliente

**Status:** Proposto · os pontos marcados com ⚠ esperam confirmação do cliente (ver `docs/decisoes.md`)

## Decisão
Partindo do modelo da seção 6 da especificação:

1. **Licitação 1–1 Ata, Licitação 1–N Contrato.** O contrato ganha `tipo` (`CONTRATO`, `EMPENHO`, `DISPENSA`), porque o mesmo órgão pode emitir vários empenhos sobre uma ata, às vezes sem contrato formal. ⚠ O cliente escreveu "mais de um edital".
2. **Saldo por quantidade e por valor.** Por item: `qtdContratada − Σ qtdPedida`. Por contrato: `valor − Σ valorPedido`. O saldo é calculado a partir dos pedidos e nunca digitado.
3. **Carona** (nome escolhido pelo cliente para a adesão à ata): nova entidade `Carona(ataId, orgaoId, data, percentual ou quantidades, negociacaoId?)`. Os limites ficam em configuração (`maxCaronas = 4`, `limiteTotal = 200%`), com aviso explícito de que dependem de lei e edital. ⚠ O limite vale por item ou pelo total?
4. **Funil = prospecção de caronas.** Uma negociação pode apontar para uma ata. "Fechado ganho" numa negociação com ata sugere registrar a carona, mas nunca cria a carona sozinho.
5. **Campos novos na licitação:** `sessaoEm` (data e hora da sessão), `portal` (lista editável: Compras.gov.br, BLL, Licitanet, Portal de Compras Públicas, ...) e `municipio`.
6. **Estados antes da vitória** (proposta): `EM_DISPUTA → VENCIDA | PERDIDA`, seguidos do ciclo da especificação (`VENCIDA → AGUARDANDO_ASSINATURA → CONTRATO_ASSINADO → EM_ANDAMENTO → ENCERRADO`). ⚠ Confirmar.
7. **Quadros kanban genéricos:** `Quadro(id, nome, tipo)` e `ColunaQuadro(quadroId, nome, ordem)` editáveis pelos usuários, com seed de *Funil de vendas*, *Organização mensal* e *Licitações vencidas*. O card aponta para uma negociação, uma licitação ou uma tarefa, conforme o quadro. ⚠ O que o quadro mensal organiza?

## Consequências
Nada disso está no código ainda: a Fase 0 não cria tabelas de negócio. A Fase 1 começa por este ADR, depois das confirmações.
