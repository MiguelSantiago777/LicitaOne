# 0011 — Licitações: ciclo de vida, permissões e sessão (Fase 1)

**Status:** Aceito · 27/10/2026 · detalha o [0005](0005-modelo-de-dominio.md)

## Decisões
- **Ciclo de vida** (`TenderStatus`): `EM_DISPUTA → VENCIDA | PERDIDA`, `VENCIDA → AGUARDANDO_ASSINATURA | CONTRATO_ASSINADO`, `AGUARDANDO_ASSINATURA → CONTRATO_ASSINADO`, `CONTRATO_ASSINADO → EM_ANDAMENTO | ENCERRADO`, `EM_ANDAMENTO → ENCERRADO`.
  - Cada transição que tem data exige essa data: vitória, assinatura, primeiro pedido, encerramento.
  - A data não pode estar no futuro nem vir antes da data anterior.
  - Uma violação vira HTTP 422 com um código estável em `rule`.
  - Não existe voltar de estado; uma correção, se precisar, entra como funcionalidade própria com registro no histórico.
- **Datas na própria licitação** (`signedAt`, `ordersStartedAt`, `closedAt`) até a Fase 2. Como uma licitação pode ter vários contratos ou empenhos (ADR 0005), na Fase 2 esses campos passam a representar a primeira assinatura e o primeiro pedido, e os contratos ganham entidade própria.
- **Abas da tela**: Em disputa, Vencidas (VENCIDA + AGUARDANDO_ASSINATURA), Contratos assinados, Em andamento, Encerrados, Perdidas. Os contadores seguem a busca e os filtros (`/tenders/summary`).
- **Órgão** é entidade do módulo `tenders`, porque só existe por causa das licitações. O **responsável** é uma referência por id ao módulo `identity`, lida pela API pública `UserDirectory` (busca em lote, sem N+1).
- **Permissões**: ADMIN altera tudo; COMERCIAL cadastra e altera só o que é seu (ou sem responsável); LEITURA só consulta. A resposta traz `canEdit`, para o front esconder ações que seriam recusadas.
- **Concorrência**: o cliente envia `version`, e uma versão diferente da atual dá 409 `VERSION_CONFLICT`.
- **Histórico** em `tender_events` (cadastro, alteração com diff por campo, mudança de situação com a data informada). Só recebe linhas novas, nunca altera nem apaga.
- **Value objects** `Money`, `Uasg` e `ProcessNumber` validam e normalizam a entrada ("PE 39/2026" vira "039/2026"). As colunas são de tipos simples, para consultas com LIKE e soma não passarem por conversores JPA.
- **Seed de desenvolvimento** (`db/seed/R__dev_seed.sql`, fora do perfil prod):
  - os exemplos da especificação, mais 3 responsáveis de exemplo;
  - os responsáveis não conseguem entrar até o administrador definir uma senha para eles em Usuários.
- **Sessão**:
  - reapresentar um refresh token **trocado há menos de 30 s** emite um novo sem derrubar nada. É o caso de quem recarrega a página no meio de uma renovação: o navegador descarta a resposta e manda o token anterior;
  - token encerrado por logout é só inválido;
  - fora da janela, o token trocado continua sendo tratado como roubo e derruba todas as sessões do usuário;
  - os três casos foram pegos pelo E2E.
- **Limite de login por IP** configurável (`SHEMA_LOGIN_ATTEMPTS_PER_MINUTE`, padrão 10); o E2E usa um valor alto.

## Pendências
- O OpenAPI ainda não marca campos anuláveis: os tipos gerados no front dizem `string` onde o valor pode ser `null`. Os templates tratam isso, e os avisos NG8102/NG8107 foram silenciados até corrigir no backend.
