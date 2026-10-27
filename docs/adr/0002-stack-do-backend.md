# 0002 — Stack do backend: Spring Boot 4.1, Maven, JPA com Specifications

**Status:** Aceito · 27/10/2026

## Contexto
A especificação pede "Spring Boot 3.x (versão estável mais recente)". Em outubro de 2026, o Spring Initializr só oferece a linha 4.x (4.0.9 e 4.1.2 estáveis), e o suporte open source da linha 3.5 já terminou.

## Decisão
- **Spring Boot 4.1.2** (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3) com **Java 21** e virtual threads ligadas. Isso cumpre a intenção da especificação (estável mais recente, com suporte) e não a letra ("3.x"). ⚠ Validar com o responsável técnico.
- **Maven** com o wrapper (`mvnw`), porque é o padrão do Initializr, tem o ecossistema de plugins mais estável para os quality gates (JaCoCo, Spotless) e dispensa configuração para a equipe. Gradle não traria ganho real num único módulo.
- **Flyway** para o schema, com `ddl-auto=validate` (o Hibernate só confere o schema; nunca altera).
- **Consultas de listagem com Spring Data JPA Specifications** (Fase 1). Os filtros da tela de licitações são combinações simples de igualdade, faixa e texto, e as Specifications ficam junto das entidades que já existem. O jOOQ traria uma segunda forma de acessar o banco e geração de código a partir do schema. Ele será reavaliado se o dashboard (Fase 5) pedir agregações que o JPQL não expressa bem.
- **DTOs como records**, MapStruct para os mapeamentos não triviais. O OpenAPI marca como obrigatórios todos os campos dos records `*Response` (`OpenApiConfig`), para que o cliente gerado no front não trate tudo como opcional.
- **Optimistic locking** (`@Version`) nas entidades editáveis. Os campos de auditoria (`updatedAt`/`updatedBy`) e os contadores de login ficam fora do lock (`@OptimisticLock(excluded = true)`). Sem isso, dois logins simultâneos do mesmo usuário davam 409 (bug pego no E2E e coberto por `AuthFlowTests.concurrentLoginsOfSameUserDoNotConflict`).
- **Erros** em RFC 7807 com códigos estáveis (`ErrorCode`) e o `correlationId` da requisição.
- **Logs**: texto com `[correlationId]` no dev; JSON (ECS) nos perfis `docker` e `prod`, pelo structured logging nativo do Boot.
- **Actuator** na porta 8081, que não é publicada fora do compose: health (liveness/readiness), info, prometheus e modulith.

## Consequências
- Algumas bibliotecas citadas na especificação mudaram de linha junto com o Boot 4: springdoc 3.x, Spring Modulith 2.x, Testcontainers 2.x. Todas estão estáveis.
- Os testes de integração precisam de Docker (Testcontainers com Postgres 18 e RabbitMQ 4.3, as mesmas imagens do compose).
