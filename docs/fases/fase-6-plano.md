# Fase 6 — Plano de trabalho (polimento e prontidão para produção)

**Branch:** `fase-6` (a partir de `fase-5`) · **Início:** 29/10/2026

## Escopo (seções 10 e 12 da especificação)
- **Auditoria de design e acessibilidade**: axe (WCAG 2.1 A/AA) em todas as telas e diálogos, em 1440 e 390 px, como teste E2E que quebra o build; Web Design Guidelines nas telas que ainda não passaram por ela.
- **Performance**:
  - consultas por requisição medidas nas listagens (N+1 vira teste);
  - `EXPLAIN` das consultas quentes com volume de produção simulado;
  - índices onde faltarem.
- **Segurança**:
  - checklist OWASP ASVS nível 1, item a item, com a evidência (teste, configuração ou decisão);
  - correção do que faltar.
- **Backup e restore testados**:
  - scripts para o banco (pg_dump) e os arquivos (bucket S3);
  - um teste de ponta a ponta: backup, apagar tudo, restaurar, conferir.
- **Runbook de operação**: subir, configurar (SMTP, S3, chave JWT), monitorar, alertas, DLQ, backup, incidentes, LGPD.
- **Documentação**: README, `.env.example` de produção e o relatório final do MVP contra a definição de pronto (seção 12).
- **Subida do zero**: `docker compose down -v && docker compose up` com seed, seguido de todos os E2E.

## Etapas
1. ✅ Acessibilidade: axe no E2E, correções, checklist.
2. ✅ Performance: contador de consultas nos testes de integração, EXPLAIN com volume, índices (migração).
3. ✅ Segurança: ASVS L1, cabeçalhos, cookies, perfil `prod`, correções.
4. ✅ Backup e restore: scripts, teste e documentação.
5. ✅ Runbook, README e configuração de produção.
6. ✅ Subida do zero, E2E completo, relatório `docs/fases/fase-6.md` e checklist da definição de pronto.
