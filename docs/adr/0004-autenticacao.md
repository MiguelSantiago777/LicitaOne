# 0004 — Autenticação: JWT próprio (RS256) e refresh token em cookie HttpOnly

**Status:** Aceito · 27/10/2026

## Decisão
- O backend é um **OAuth2 Resource Server** que valida JWT RS256. No MVP, ele mesmo emite os tokens (`AuthService`), com uma chave RSA vinda de `SHEMA_JWT_PRIVATE_KEY` (PEM PKCS#8) e o `kid` de `SHEMA_JWT_KEY_ID`. Sem chave configurada, uma chave temporária é gerada na subida (só para dev; aparece um aviso no log).
- **Access token** de 15 min, devolvido no corpo e mantido **só em memória** no front. **Refresh token** opaco de 7 dias num cookie `HttpOnly; SameSite=Strict; Path=/api/v1/auth` (`Secure` fora do dev). No banco fica só o hash SHA-256 do refresh token.
- **Rotação** a cada uso. Reapresentar um refresh token já usado revoga todas as sessões do usuário (detecção de roubo).
- **CSRF desligado.** A API é stateless e autenticada por header, e o único endpoint que usa cookie (`/auth/refresh`) está protegido pelo SameSite=Strict e pelo path restrito. Em produção, o front e a API ficam na mesma origem (o nginx repassa `/api`).
- **Senhas com bcrypt** (`DelegatingPasswordEncoder`, que permite migrar para Argon2 sem reset). Bloqueio de 15 min após 5 erros seguidos; **rate limit** de 10 tentativas de login por minuto por IP (Bucket4j em memória).
- Login com e-mail inexistente gasta o mesmo tempo que senha errada (hash fictício), para não revelar quais e-mails têm conta.
- **Perfis** `ADMIN`, `SALES` (Comercial) e `VIEWER` (Leitura) no claim `roles`; autorização com `@PreAuthorize` e teste de autorização por endpoint (`AuthFlowTests.viewerCannotListUsers`).
- O primeiro administrador é criado na subida a partir de `SHEMA_ADMIN_EMAIL` e `SHEMA_ADMIN_PASSWORD`, só quando não há nenhum usuário.

## Trocar por um IdP (Keycloak)
Basta substituir o bean `JwtDecoder` por um configurado com `issuer-uri` e mapear os papéis do IdP para o claim `roles`. Os módulos só dependem do `Jwt` validado e dos papéis, então nenhum precisa mudar. O `AuthController` deixa de ser usado.

## Limitações conhecidas
- O rate limit é por instância. Com mais de uma réplica, ele vai para o Postgres ou para o Redis (Bucket4j tem os dois backends).
- O cadastro e a gestão de usuários (convite, troca de senha) entram na Fase 1.
