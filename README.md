# LicitaOne

CRM de licitações públicas da Shema Suprimentos e Serviços: acompanha cada licitação ganha, da vitória ao encerramento do contrato (pedidos, saldo, atas e caronas).

- Especificação: [`docs/especificacao.md`](docs/especificacao.md)
- Respostas do cliente e pontos em aberto: [`docs/decisoes.md`](docs/decisoes.md)
- Decisões de arquitetura: [`docs/adr/`](docs/adr/README.md)
- Andamento por fase: [`docs/fases/`](docs/fases)
- Operação (produção, backup, incidentes, LGPD): [`docs/operacao/runbook.md`](docs/operacao/runbook.md), com [segurança (ASVS L1)](docs/operacao/seguranca.md), [performance](docs/operacao/performance.md) e [mensageria e DLQ](docs/operacao/mensageria.md)

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

Os E2E incluem a auditoria de acessibilidade (axe, WCAG 2.1 A/AA) em todas as telas e diálogos, em 1440 e 390 px.

O build quebra se:
- um módulo acessar a parte interna de outro (`ModularityTests`);
- a cobertura de linhas do domínio cair abaixo de 80% (JaCoCo);
- o código não estiver formatado (Spotless no backend, Prettier e ESLint no front);
- uma listagem passar a fazer uma consulta por linha (N+1, `QueryCountTests`);
- uma tela ou diálogo tiver violação de acessibilidade (axe no E2E);
- o Trivy achar vulnerabilidade crítica numa dependência ou numa imagem (CI).

## Estrutura

```
backend/    Spring Boot 4.1 (Java 21), um módulo por contexto em br.com.shema.crm.*
frontend/   Angular 22 (standalone, signals, zoneless)
e2e/        Playwright: fluxos críticos e screenshots 1440 / 390
deploy/     SeaweedFS, Prometheus e Grafana; scripts de backup/restore e de volume para EXPLAIN
docs/       especificação, decisões, ADRs e notas de cada fase
```

## Segredos
Nada de segredo no repositório: tudo vem do `.env` (ignorado pelo git) ou de variáveis de ambiente. Em produção, defina `SHEMA_JWT_PRIVATE_KEY` (sem ela, o backend gera uma chave temporária e as sessões caem a cada reinício):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048
```
