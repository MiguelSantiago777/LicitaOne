# Fase 0 — Fundação

**Branch:** `fase-0` · **Data:** 27/10/2026

## O que ficou pronto
- **Repositório** em monorepo: `backend/`, `frontend/`, `e2e/`, `deploy/`, `docs/` (com `docs/adr/`).
- **docker compose** com Postgres 18, RabbitMQ 4.3 (management), SeaweedFS (S3, ADR 0007), Mailpit, backend e frontend; perfil `observability` com Prometheus e Grafana. Health checks e volumes nomeados. Portas de host configuráveis no `.env` (as padrão estavam ocupadas por outro projeto local).
- **Backend:** Spring Boot 4.1 / Java 21 em módulos Spring Modulith com teste de arquitetura; login JWT RS256 com refresh rotativo; perfis; bloqueio por tentativas; rate limit; erros RFC 7807 com códigos estáveis; correlationId; auditoria e optimistic locking na base das entidades; Actuator (health, prometheus); logs JSON no compose; OpenAPI em `/swagger-ui.html`.
- **Frontend:** Angular 22 com login, shell (barra lateral e menu no celular), as seis áreas com título, estrutura e estados vazios honestos, sessão que sobrevive ao recarregar, renovação transparente do token e aviso de conflito (409).
- **Quality gates:** Spotless, JaCoCo (domínio ≥ 80%), Modulith verify, angular-eslint, Prettier, testes unitários, E2E e CI no GitHub Actions (backend, frontend, e2e e varredura Trivy).
- **ADRs** 0001–0009.

## Testes
| Suíte | Resultado |
|---|---|
| Backend (`./mvnw verify`): domínio, Modulith, integração com Testcontainers | 20 testes, verde, cobertura do domínio acima de 80% |
| Frontend (`npm test`) | 8 testes, verde |
| Frontend (`npm run lint`) | sem erros |
| E2E (`e2e/`, desktop 1440 e celular 390) | 10 testes, verde |

Bugs pegos pelos testes e corrigidos nesta fase:
1. Ordem dos interceptors: o refresh automático nunca disparava (ADR 0009).
2. Logins simultâneos do mesmo usuário davam 409 (ADR 0002).

## Screenshots
Gerados com `cd e2e && npm run screenshots`, em `e2e/screenshots/{desktop,phone}/` (fora do git). Telas: login, painel, licitações, atas, negociações, tarefas, importação e o menu no celular.

## Checklist Web Design Guidelines
| Item | Situação |
|---|---|
| Foco visível em todos os controles | ✅ contorno de 2px na cor da marca (`:focus-visible`) |
| Navegação por teclado | ✅ link "Pular para o conteúdo"; abas, menu e logout são botões ou links reais |
| Rótulos de formulário | ✅ `<label for>` em todos os campos; erro ligado por `aria-describedby` e `aria-invalid` |
| Erros anunciados | ✅ erro de login em `role="alert"`; avisos globais em `aria-live="polite"` |
| Ícones | ✅ decorativos (`aria-hidden`); botões só de ícone têm `aria-label` |
| Contraste AA | ✅ textos secundários `#5c6066` sobre `#f2f4f7` e branco (≥ 4.5:1) |
| Estados vazio, erro e carregando | ✅ vazio com frase e próximo passo; erro no login; botão "Entrando…" desabilitado durante o envio. Skeletons entram com os dados (Fase 1) |
| Números tabulares e moeda pt-BR | ✅ `tabular-nums`, `R$ 0,00` |
| Responsivo (390px) | ✅ menu em tela cheia, alvos de 44px, sem rolagem horizontal da página (o kanban rola dentro do próprio quadro) |
| `prefers-reduced-motion` | ✅ transições desligadas |
| Idioma e título por página | ✅ `lang="pt-BR"`; `title` por rota |
| Sem permissão | ⏳ 403 vira um `ApiError` `ACCESS_DENIED`; a tela "sem permissão" entra com as primeiras ações restritas (Fase 1) |

## Pendências e riscos
- **Spring Boot 4.1 em vez de 3.x** (ADR 0002): precisa do aval do responsável técnico.
- **Respostas do cliente a confirmar** (5 pontos em `docs/decisoes.md`): bloqueiam o modelo da Fase 1.
- **Não há repositório remoto**, então não há PR nem execução do CI no GitHub. O workflow está pronto em `.github/workflows/`, e os mesmos comandos foram rodados localmente.
- OpenTelemetry (traces) ainda não está ligado; o correlationId já é propagado do front até os logs.
- Terraform e compose de produção, backup e restore: Fase 6, como previsto.
