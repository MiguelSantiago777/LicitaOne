# LicitaOne

CRM de licitações públicas da Shema Suprimentos e Serviços. Acompanha cada licitação ganha da vitória até o encerramento do contrato: assinatura, pedidos, saldo, documentos e, principalmente, a **ata de registro de preços** como oportunidade de carona em outros órgãos.

> O sistema se chamava ShemaCRM. Só o nome visível mudou: código, banco e filas continuam com `shema`/`shemacrm`.

**Situação:** o MVP está pronto (Fases 0 a 6, com todos os itens da definição de pronto). Na Fase 7, a leitura da ata com IA está pronta e aguarda a chave da API e a autorização do cliente. Veja [Andamento](#andamento).

## O que o sistema faz

**Licitações e contratos**
- **Ciclo de vida com máquina de estados:** Vencida → Aguardando assinatura → Contrato assinado → Em andamento → Encerrado. Cada transição é validada (por exemplo, não dá para assinar sem a data de assinatura) e fica no histórico.
- **Tela principal com as quatro abas** do dia a dia: vencidas, assinados, em andamento e encerrados. Cada aba tem contador e soma de valores, busca, filtros (órgão, município, UF, material, valor, período, responsável) e ordenação, tudo refletido na URL.
- **Detalhe da licitação** com as abas Resumo, Itens e pedidos, Documentos, Histórico, Tarefas e Negociação.
- **Itens, pedidos e saldo:** o saldo é calculado por quantidade de cada item e por valor. Contratos, empenhos e dispensas.
- **Importação da planilha atual** (.xlsx/.csv): mapeamento de colunas, pré-visualização com erro por linha, processamento em segundo plano e relatório baixável.

**Atas, caronas e documentos**
- **Documentos versionados** por licitação (Ata, Contrato, Empenho, Atestado, Edital, Outros). O arquivo é conferido pelo conteúdo, não só pela extensão.
- **Atas com vigência própria**, independente do contrato, e itens registrados.
- **Caronas (adesões):** registro por órgão e item, com os limites configuráveis só sinalizados. O sistema não afirma regra jurídica; a pessoa confirma.
- **Preencher com IA:** a IA lê o PDF da ata e preenche número, datas, "permite adesão" e itens, com a página de onde saiu cada valor e avisos do que não conseguiu ler. A pessoa confere e salva; o histórico registra que os dados vieram da IA. Desligado sem `ANTHROPIC_API_KEY`.
- **Destaque e lista de oportunidades:** "Ata válida até 2028, oportunidade de replicar", ordenada por vencimento, e um calendário de vencimento das atas.

**Comercial**
- **Funil de negociações em Kanban**, com arrastar e soltar, soma por coluna, troca para lista, filtros e faixa de tarefa atrasada. Uma negociação pode nascer de uma ata para oferecer carona.
- **Quadros livres** (*Organização mensal*, *Licitações vencidas* e outros que a equipe criar).
- **Tarefas** manuais e automáticas, com conclusão em massa.

**Alertas e painel**
- **Três alertas diários:**
  - contrato assinado há mais de 30 dias sem pedido;
  - ata vencendo em 90, 60 ou 30 dias;
  - licitação vencida sem contrato.
  
  Cada alerta vira tarefa com **rascunho de e-mail ao órgão** (copiar ou abrir no cliente de e-mail; nada é enviado sozinho) e aviso interno ao responsável. Nenhum alerta duplica tarefa.
- **Painel:** totais por estado, valor em carteira, saldo a pedir, contratos parados, atas vencendo, tarefas atrasadas e principais órgãos.

**Acesso e auditoria**
- **Perfis ADMIN, COMERCIAL e LEITURA.** O comercial só edita o que é dele.
- **Auditoria completa** (quem alterou o quê e quando) e controle de concorrência: duas pessoas editando o mesmo registro recebem um aviso claro.

## Tecnologia

| Camada | O que usa |
|---|---|
| Backend | Java 21 (virtual threads), Spring Boot 4.1, Spring Modulith, JPA/Hibernate, Flyway, PostgreSQL |
| Mensageria | RabbitMQ com outbox transacional, consumidores idempotentes, retry com backoff e DLQ por fila |
| IA | Claude (SDK oficial da Anthropic para Java), saída estruturada, leitura em segundo plano pela fila |
| Agendamento | Spring Scheduler + ShedLock (o job roda uma vez mesmo com várias instâncias) |
| Arquivos | Interface `FileStorage` com S3 (SeaweedFS no compose) |
| Segurança | OAuth2 Resource Server, JWT RS256 próprio, refresh em cookie HttpOnly, senhas com bcrypt, rate limit no login, checklist OWASP ASVS nível 1 |
| Frontend | Angular 22 (standalone, Signals, zoneless), cliente da API gerado do OpenAPI, locale pt-BR |
| Testes | JUnit + AssertJ (domínio sem Spring), Testcontainers (Postgres e RabbitMQ reais), Playwright com auditoria axe (WCAG 2.1 AA) em 1440 e 390 px |
| Operação | Actuator, Micrometer + Prometheus, Grafana, logs JSON com correlationId, Docker multi-stage não-root |
| CI | GitHub Actions separados para backend, frontend e E2E, com Trivy em dependências e imagens |

## Arquitetura

Um único deployable Spring Boot, dividido em **módulos por contexto de negócio** (monólito modular, [ADR 0001](docs/adr/0001-monolito-modular.md)). Cada módulo expõe só a API pública. Os módulos conversam por interface ou por eventos de domínio, e um teste quebra o build se algum módulo acessar a parte interna de outro.

| Módulo (`br.com.shema.crm.*`) | Responsabilidade |
|---|---|
| `tenders` | Órgãos, licitações e ciclo de vida |
| `contracts` | Contratos, itens, pedidos, saldo e importação de itens |
| `pricerecords` | Atas, caronas e documentos |
| `extraction` | Leitura de atas e contratos com IA (só sugere; a pessoa confere) |
| `deals` | Funil de negociações |
| `boards` | Quadros kanban livres |
| `tasks` | Tarefas manuais e de alerta |
| `imports` | Jobs de importação de planilha |
| `notifications` | Alertas, rascunhos e avisos por e-mail |
| `identity` | Usuários, perfis, login e sessão |
| `shared` | Dinheiro, datas, auditoria, outbox, erros (RFC 7807), armazenamento |

Fluxos assíncronos (importação, alertas e avisos) saem pelo outbox para o RabbitMQ e voltam por consumidores idempotentes. A topologia e o reprocessamento da DLQ estão em [`docs/operacao/mensageria.md`](docs/operacao/mensageria.md).

## Subir tudo

Requisitos: Docker com Compose v2.

```bash
cp .env.example .env      # troque as senhas
docker compose up -d --build
```

| Serviço | Endereço |
|---|---|
| Aplicação | http://localhost:14200 (login com `SHEMA_ADMIN_EMAIL` / `SHEMA_ADMIN_PASSWORD` do `.env`) |
| API e Swagger | http://localhost:18080/swagger-ui.html |
| RabbitMQ (painel) | http://localhost:15683 |
| Mailpit (e-mails do dev) | http://localhost:18025 |
| S3 (SeaweedFS) | http://localhost:18333 |
| Prometheus / Grafana | `docker compose --profile observability up -d` → http://localhost:19090 e http://localhost:13000 |

As portas ficam no `.env`. Elas evitam as padrão (5432, 8080, 4200...) para não brigar com outros projetos.

O compose de desenvolvimento sobe com **dados de exemplo** (seed) e com o Mailpit capturando os e-mails dos alertas. Para começar do zero: `docker compose down -v && docker compose up -d --build`.

## Produção

```bash
cp .env.prod.example .env.prod      # chave JWT, SMTP, senhas e endereço público
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build --wait
```

Perfil `prod`: sem dados de exemplo nem Swagger, cookie `Secure`, só o frontend publicado (atrás de um proxy com TLS) e o backend recusa subir sem a chave JWT. Instalação, backup (`deploy/backup/`), monitoramento e incidentes estão no [runbook](docs/operacao/runbook.md).

## Desenvolver

```bash
docker compose up -d postgres rabbitmq s3 mailpit   # só a infraestrutura

# backend (Java 21; lê o .env da raiz)
cd backend && ./mvnw spring-boot:run                # API em http://localhost:18080

# frontend (Node 24)
cd frontend && npm ci && npm start                  # http://localhost:4300, com proxy de /api para o backend
```

Atalhos no `Makefile`: `make infra`, `make backend`, `make frontend`, `make test`, `make e2e`, `make api`.

### Cliente da API
O front usa um cliente gerado do OpenAPI (nunca escrito à mão). Depois de mudar um endpoint, com o backend no ar:

```bash
cd frontend && npm run api:fetch && npm run api:gen
```

## Testes

```bash
cd backend && ./mvnw verify        # domínio, arquitetura (Modulith), integração com Testcontainers, JaCoCo, Spotless
cd frontend && npm test && npm run lint
cd e2e && npm ci && npx playwright install chromium && npm test   # com o sistema no ar (E2E_BASE_URL)
deploy/backup/test-restore.sh      # backup → apaga o ambiente → restaura → confere (só em dev: apaga os volumes)
```

Os testes de integração precisam do Docker no ar (Testcontainers). Os E2E incluem a auditoria de acessibilidade (axe, WCAG 2.1 A/AA) em todas as telas e diálogos, em 1440 e 390 px.

O build quebra se:
- um módulo acessar a parte interna de outro (`ModularityTests`);
- a cobertura de linhas do domínio cair abaixo de 80% (JaCoCo);
- o código não estiver formatado (Spotless no backend, Prettier e ESLint no front);
- uma listagem passar a fazer uma consulta por linha (N+1, `QueryCountTests`);
- uma tela ou diálogo tiver violação de acessibilidade (axe no E2E);
- o Trivy achar vulnerabilidade crítica numa dependência ou numa imagem (CI).

## Andamento

Uma branch por fase, cada uma a partir da anterior. O relatório de cada fase (o que ficou pronto, testes e pendências) está em [`docs/fases/`](docs/fases).

| Fase | Conteúdo | Situação |
|---|---|---|
| 0 | Fundação: compose, módulos, CI, login, observabilidade, layout | ✅ |
| 1 | Órgãos, licitações, máquina de estados, quatro abas, histórico | ✅ |
| 2 | Contratos, itens, pedidos, saldo, importação assíncrona, outbox | ✅ |
| 3 | Documentos, atas, caronas e oportunidades | ✅ |
| 4 | Funil (Kanban), tarefas e quadros livres | ✅ |
| 5 | Alertas (ShedLock → RabbitMQ), DLQ, rascunhos, painel e métricas | ✅ |
| 6 | Acessibilidade, performance, segurança (ASVS L1), backup/restore, runbook | ✅ |
| 7 | IA: "Preencher com IA" lê o PDF da ata e preenche o formulário e os itens ([ADR 0016](docs/adr/0016-leitura-com-ia.md)) | ✅ ata (falta medir com atas reais e liberar o envio) |

**Pendências:**
- respostas do cliente em [`docs/decisoes.md`](docs/decisoes.md);
- primeiro deploy (servidor, domínio com TLS, SMTP);
- antivírus nos documentos e MFA (ASVS nível 2);
- recuperação de senha por e-mail.

## Documentação

| Onde | O quê |
|---|---|
| [`docs/especificacao.md`](docs/especificacao.md) | Especificação original do cliente |
| [`docs/decisoes.md`](docs/decisoes.md) | Respostas do cliente, pontos em aberto e ferramentas instaladas |
| [`docs/adr/`](docs/adr/README.md) | Decisões de arquitetura (ADRs) |
| [`docs/fases/`](docs/fases) | Plano e relatório de cada fase |
| [`docs/propostas/`](docs/propostas) | Propostas em discussão, ainda não aprovadas |
| [`docs/operacao/runbook.md`](docs/operacao/runbook.md) | Produção, backup, incidentes e LGPD, com [segurança](docs/operacao/seguranca.md), [performance](docs/operacao/performance.md) e [mensageria e DLQ](docs/operacao/mensageria.md) |

## Estrutura

```
backend/    Spring Boot 4.1 (Java 21), um módulo por contexto em br.com.shema.crm.*
frontend/   Angular 22 (standalone, signals, zoneless)
e2e/        Playwright: fluxos críticos, acessibilidade e screenshots 1440 / 390
deploy/     SeaweedFS, Prometheus e Grafana; scripts de backup/restore e de volume para EXPLAIN
docs/       especificação, decisões, ADRs, fases, propostas e operação
.github/    workflows do CI (backend, frontend, e2e)
```

## Segredos
Nada de segredo no repositório: tudo vem do `.env` (ignorado pelo git) ou de variáveis de ambiente. Em produção, defina `SHEMA_JWT_PRIVATE_KEY` (sem ela, o backend recusa subir no perfil `prod`):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048
```
