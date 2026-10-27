# Fase 1 — Núcleo: órgãos e licitações

**Branch:** `fase-1` (a partir de `fase-0`) · **Data:** 27/10/2026

## O que ficou pronto (funcionando de verdade, gravando no banco)
- **Licitações**:
  - cadastro em disputa ou já vencida;
  - edição;
  - mudança de situação com data obrigatória e validação: marcar vencida ou perdida, contrato recebido, registrar assinatura, registrar primeiro pedido, encerrar;
  - histórico com quem fez o quê e o que mudou.
- **Lista**:
  - 6 abas com contadores;
  - busca por órgão, município, UASG, pregão ou material;
  - filtros por responsável, material e UF;
  - ordenação e paginação no servidor;
  - estado na URL (dá para compartilhar o link e voltar);
  - no celular vira lista de duas linhas.
- **Detalhe** com resumo, linha do tempo do ciclo, alerta de "assinado há mais de 30 dias sem pedido" e abas reservadas para as próximas fases.
- **Órgãos**: busca, cadastro e edição, e cadastro rápido de dentro do formulário de licitação.
- **Usuários** (administrador): cadastrar, mudar perfil, desativar e definir senha. Cada usuário pode trocar a própria senha.
- **Painel com números reais**:
  - carteira, vencidas sem contrato, em disputa e em andamento;
  - contratos sem pedido há mais de 30 dias;
  - próximas sessões;
  - maiores contratos.
- **Permissões**: COMERCIAL altera só o que é seu, LEITURA só consulta. Conflito de edição simultânea dá 409, com aviso.
- **Seed** com os exemplos da especificação (19 licitações, 18 órgãos, 3 responsáveis).

Negociações, Tarefas, Atas e Importação continuam com dados de exemplo, agora com um selo "Dados de exemplo · real na Fase N".

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 43 testes, verde: máquina de estados, value objects, fluxo da API com Postgres real, permissões, usuários, sessão; cobertura do domínio acima de 80%; limites dos módulos verificados |
| Frontend `npm test` / `npm run lint` | 13 testes, verde / sem erros |
| E2E (desktop 1440 e celular 390), contra o build de produção no compose | 18 testes, verde |

Fluxos E2E novos:
- cadastrar licitação vencida → registrar assinatura → ver no histórico;
- assinar sem data mostra a regra;
- busca e filtros na URL mudam os contadores.

## Bugs pegos pelos testes nesta fase
1. Resposta montada fora da transação (`LazyInitializationException`): o órgão agora vem junto no `findById`.
2. Recarregar a página no meio da renovação da sessão derrubava todas as sessões do usuário. Agora há uma janela de 30 s (ADR 0011).
3. Renovar com um token encerrado por logout também derrubava as outras sessões. Agora ele é só inválido.

## Pendências
- Respostas do cliente ainda a confirmar (`docs/decisoes.md`), com as escolhas assumidas registradas lá.
- Nulabilidade no OpenAPI (ADR 0011).
- Contatos do órgão (nome, cargo, e-mail, telefone) ficaram para a Fase 2, junto com o e-mail de follow-up.
