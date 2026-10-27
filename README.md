# ShemaCRM

CRM de licitações públicas da Shema Suprimentos e Serviços: acompanha cada licitação ganha, da vitória ao encerramento do contrato (pedidos, saldo, atas e caronas).

- Especificação: [`docs/especificacao.md`](docs/especificacao.md)
- Respostas do cliente e pontos em aberto: [`docs/decisoes.md`](docs/decisoes.md)
- Decisões de arquitetura: [`docs/adr/`](docs/adr/README.md)
- Andamento por fase: [`docs/fases/`](docs/fases)

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
```

O build quebra se:
- um módulo acessar a parte interna de outro (`ModularityTests`);
- a cobertura de linhas do domínio cair abaixo de 80% (JaCoCo);
- o código não estiver formatado (Spotless no backend, Prettier e ESLint no front);
- o Trivy achar vulnerabilidade crítica numa dependência (CI).

## Estrutura

```
backend/    Spring Boot 4.1 (Java 21), um módulo por contexto em br.com.shema.crm.*
frontend/   Angular 22 (standalone, signals, zoneless)
e2e/        Playwright: fluxos críticos e screenshots 1440 / 390
deploy/     configuração de SeaweedFS, Prometheus e Grafana
docs/       especificação, decisões, ADRs e notas de cada fase
```

## Segredos
Nada de segredo no repositório: tudo vem do `.env` (ignorado pelo git) ou de variáveis de ambiente. Em produção, defina `SHEMA_JWT_PRIVATE_KEY` (sem ela, o backend gera uma chave temporária e as sessões caem a cada reinício):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048
```
