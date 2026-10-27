# 0009 — Arquitetura do frontend: Signals, cliente gerado, sessão em memória

**Status:** Aceito · 27/10/2026

## Decisão
- **Angular 22**, standalone, zoneless, `OnPush` em todos os componentes, rotas lazy por feature (`features/<feature>/<nome>.page.ts`), `core/` (sessão, HTTP, avisos) e `shared/` (UI).
- **Estado com Signals** e serviços por feature. NgRx/SignalStore só se aparecer estado compartilhado complexo, e com ADR próprio. Dados do servidor com cache e invalidação explícitos por serviço; `httpResource` será avaliado na Fase 1.
- **Cliente da API gerado** com **orval** (modo Angular) a partir de `frontend/openapi/openapi.json`: `npm run api:fetch` (backend no ar) e depois `npm run api:gen`. O contrato e o cliente gerado são commitados, então o build não depende do backend; o lint ignora `src/app/api/generated`.
- **Sessão:** access token só em memória (signal); refresh pelo cookie HttpOnly no `provideAppInitializer`, então recarregar a página mantém a sessão sem guardar token em storage.
- **Interceptors** nesta ordem: `correlation → error → auth`. O de erro fica **por fora** do de autenticação, para que este receba o 401 cru, renove o token uma vez (um refresh compartilhado entre as chamadas paradas) e repita a chamada. Na ordem inversa, o 401 virava `ApiError` antes e o refresh nunca disparava (bug pego no teste `auth.interceptor.spec.ts`).
- **Erros** chegam às telas como `ApiError { status, code, detail, fieldErrors, correlationId }`. As telas decidem pelo `code`. O 409 de versão vira um aviso global.
- **i18n:** `LOCALE_ID` e moeda padrão `pt-BR`/`BRL`, com os textos em português no código-fonte. A extração com `@angular/localize` entra quando houver um segundo idioma; hoje ela só adicionaria marcação sem uso.
- **Qualidade:** TypeScript strict e `strictTemplates`, angular-eslint (com as regras de acessibilidade de template), Prettier, Vitest (`ng test`), E2E com Playwright em `/e2e`.
