# Decisões e registro de ferramentas

## Skills instaladas (Passo 0) — 26/10/2026

Ambiente: Windows 11, Node.js 24.22.1, npm 11.19.1, git 2.56. Todos os comandos foram rodados na raiz do repositório (`shemacrm/`).

| Skill / recurso | Comando | Resultado | Local |
|---|---|---|---|
| Taste Skills (`design-taste-frontend`) | `npx skills add Leonxlnx/taste-skill --skill design-taste-frontend --agent claude-code -y` | OK. Avaliação: Gen *Safe*, Socket 1 alerta, Snyk *Med Risk* | `.claude/skills/design-taste-frontend` |
| Web Design Guidelines | `npx skills add vercel-labs/agent-skills --skill web-design-guidelines --agent claude-code -y` | OK. Gen *Safe*, Socket 0 alertas, Snyk *Low Risk*. Busca as regras online a cada auditoria. | `.claude/skills/web-design-guidelines` |
| Awesome Design (catálogo DESIGN.md) | `git clone --depth 1 https://github.com/VoltAgent/awesome-design-md .claude/design-refs/awesome-design-md` | OK (169 arquivos .md) | `.claude/design-refs/awesome-design-md` (fora do git, ver abaixo) |
| Playwright CLI | `npm install -g @playwright/cli@latest` | OK, `playwright-cli` no PATH | global npm |
| Skill do Playwright CLI | `npx skills add microsoft/playwright-cli --skill playwright-cli --agent claude-code -y` | OK. Gen *Safe*, Socket 1 alerta, Snyk *Med Risk* | `.claude/skills/playwright-cli` |
| Navegador | `npx playwright install chromium` | OK (Chromium headless shell) | `%LOCALAPPDATA%\ms-playwright` |

Nenhuma alternativa precisou ser usada.

### Revisão de segurança das skills
Como duas skills vieram com alerta "Med Risk", foi feita uma revisão manual antes do uso:
- `design-taste-frontend`: os scripts `.mjs` fazem `fetch` só para baixar imagens/vídeos de referência (`assets.mjs`) e usam `spawn` para abrir um Chromium via CDP para inspecionar páginas (`lib/cdp.mjs`). Não leem variáveis de ambiente nem segredos, e não enviam dados para fora.
- `playwright-cli`: só documentação (SKILL.md + `references/`).
- `web-design-guidelines`: só instruções; baixa as regras de `raw.githubusercontent.com/vercel-labs/web-interface-guidelines`.

### Versionamento
- `.claude/skills/` é commitado, para que todo mundo use a mesma versão das skills.
- `.claude/design-refs/` fica no `.gitignore` (é um clone de terceiro com `.git` próprio). Para recriar, use o comando da tabela.

## Ambiente de build
- JDK 21 (Microsoft OpenJDK 21.0.12) em `%USERPROFILE%\.jdks\ms-21.0.12.1`, **fora do PATH**. O backend usa o Maven Wrapper (`mvnw`), então não é preciso instalar Maven, só definir `JAVA_HOME`.
- Docker 29.9 disponível para o `docker compose`.
