# Fase 6 — Polimento e prontidão para produção

**Branch:** `fase-6` (a partir de `fase-5`) · **Data:** 29/10/2026 · **Plano:** `docs/fases/fase-6-plano.md`

## O que ficou pronto

### Acessibilidade
- **Auditoria automática com axe** (WCAG 2.1 A e AA) no E2E, em 1440 e 390 px. Cobre:
  - as 12 telas;
  - as 6 abas do detalhe da licitação;
  - 5 diálogos (pedido, carona, nova licitação, tarefa com rascunho, negociação).
  - Qualquer violação quebra o build.
- **Corrigido:**
  - contraste do menu lateral (3,75:1 → 7:1) e do texto secundário (4,43:1 → 5,4:1);
  - botão "+" do celular sem nome acessível;
  - link "Abrir o funil" sem contraste no destaque da ata;
  - tabelas de itens da ata que rolavam de lado sem acesso pelo teclado.
- **Resultado:** zero violações.

### Performance
- **N+1 virou teste** (`QueryCountTests`):
  - uma página de 25 linhas não pode fazer mais consultas que uma de 3;
  - as telas sem paginação têm teto. Hoje: funil 7, contratos da licitação 6, atas 4, o resto 1 ou 2.
- **EXPLAIN com volume** (`deploy/perf/volume-explain.sql`): 50 mil licitações, 45 mil pedidos, 30 mil tarefas.
  - Todas as consultas quentes ficam abaixo de 110 ms.
  - Nenhum índice novo foi necessário.
  - A busca por texto é a primeira que vai pesar, e o caminho está documentado (`docs/operacao/performance.md`).

### Segurança (OWASP ASVS nível 1)
- **Checklist item a item, com evidência:** `docs/operacao/seguranca.md`.
- **Corrigido nesta fase:**
  - **senha:** de 12 a 128 caracteres; recusa senhas comuns (inclusive brasileiras), repetições, sequências e senha com o e-mail; sem regra de composição. A tela agora sugere frase-senha;
  - **produção sem chave JWT não sobe** (antes gerava uma chave temporária);
  - **cabeçalhos:** HSTS e Permissions-Policy, além dos que já existiam (CSP, X-Frame-Options, nosniff, Referrer-Policy);
  - **vulnerabilidades:** o Trivy nas imagens achou 1 CRITICAL (brotli) e HIGH (curl) no Alpine e 4 HIGH no `amqp-client` 5.30.0. Com `apk upgrade` nas duas imagens e `amqp-client` 5.37.0, ficou em **zero CRITICAL/HIGH**;
  - **CI:** o Trivy passa a escanear também as imagens, não só os arquivos de dependência.

### Backup e restore
- **Scripts em `deploy/backup/`:**
  - `backup.sh`: `pg_dump` + cópia do bucket com rclone + manifesto com SHA-256;
  - `restore.sh`: confere o manifesto, para o backend, recria o banco e devolve os arquivos.
- **Testado de ponta a ponta** (`test-restore.sh`: backup → `docker compose down -v` → subida vazia → restauração → conferência):
  - banco idêntico: 134 licitações, 125 contratos, 125 pedidos, 47 tarefas, 19 migrações;
  - os 29 arquivos de documento com o SHA-256 original.

### Produção e operação
- **`docker-compose.prod.yml`** + `.env.prod.example`:
  - perfil `prod` (sem seed, sem Swagger, cookie `Secure`, chave JWT obrigatória);
  - banco, RabbitMQ e S3 sem porta publicada;
  - SMTP de verdade no lugar do Mailpit;
  - `restart: unless-stopped`;
  - TLS no proxy da frente.
- **`ProdProfileTests`:** sobe o perfil `prod` de verdade e confere que não há seed, não há Swagger e o cookie é `Secure`.
- **Runbook** (`docs/operacao/runbook.md`): instalação, variáveis, atualização, monitoramento (o que merece alerta), backup, incidentes comuns, rotinas de segurança e LGPD (inventário de dados pessoais e retenção).
- **README:** produção, operação e os novos portões de qualidade.

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 134 testes, verde (ModularityTests, JaCoCo do domínio, Spotless). Novos: `QueryCountTests`, `PasswordPolicyTest`, `JwtKeyConfigTest`, `ProdProfileTests` e o teste de senha fraca em `AuthFlowTests` |
| Frontend lint, formatação, testes, build de produção | Verde |
| E2E (1440 e 390), com o compose recriado do zero (`down -v`, seed) | 42 testes, verde, incluindo os 6 de acessibilidade |
| Backup → apagar → restaurar | OK |
| Trivy (dependências e imagens, CRITICAL/HIGH) | 0 |

Observações sobre as rodadas:
- Uma rodada do backend caiu com um **crash nativo da JVM** (`EXCEPTION_ACCESS_VIOLATION`, Windows) durante `ImportFlowTests`. Foi enquanto a máquina também reconstruía as imagens e rodava o E2E. Rodada sozinha, a suíte passou inteira. Não é falha de teste, mas fica registrado.
- O teste de screenshots no celular passou dos 30 s padrão com a máquina carregada. Ele percorre muitas telas, então o limite foi para 60 s.

## Definição de pronto do MVP (seção 12)
| Item | Situação |
|---|---|
| Cadastrar uma licitação vencida e levá-la até encerrada (assinatura, pedidos, saldo) | ✅ E2E `tenders.spec` e `ContractFlowTests` |
| Quatro abas com contadores e valores corretos | ✅ E2E de busca e filtros; `TenderFlowTests` |
| Anexar a ata em contrato encerrado e ver o destaque da ata vigente | ✅ E2E `atas.spec` |
| Contratos assinados há mais de 30 dias sem pedido geram tarefa e rascunho de e-mail | ✅ E2E `alerts.spec` e `AlertFlowTests` |
| Importar a planilha atual sem retrabalho manual | ⚠ itens e pedidos de cada aba de órgão sim (Fase 2). A aba GERAL não tem UASG, pregão nem datas: as licitações são cadastradas na tela (pergunta 6 ao cliente) |
| Kanban e lista de tarefas como nos prints, com soma por coluna | ✅ E2E `deals.spec` e `boards.spec` |
| Todos os E2E passam e as telas passam na auditoria de acessibilidade | ✅ 42 E2E; axe sem violações |
| Planilha grande não trava a tela (202, segundo plano, relatório por linha) | ✅ `ImportFlowTests` e E2E de importação |
| Mensagem com falha vai para a DLQ e dá para reprocessar; nenhum alerta duplica tarefa | ✅ `AlertFlowTests` (SMTP fora → DLQ → reprocessar) e `ImportFlowTests` |
| Build quebra com violação de módulo, cobertura baixa ou vulnerabilidade crítica | ✅ local (`verify`) e nos workflows. ⚠ Os workflows do GitHub Actions não rodaram nesta sessão, porque nada foi enviado ao remoto |
| `docker compose up` sobe do zero, com seed, e o README explica o resto | ✅ feito em 29/10/2026 com `down -v` |

## Pendências
- **Perguntas ao cliente** (`docs/decisoes.md`):
  - 1, 2 e 3 (assumidas desde a Fase 1);
  - 4: o limite de 200% vale por item ou pelo total;
  - 5: quem recebe os avisos;
  - 6: colunas da aba GERAL;
  - 7: aditivos;
  - o prazo da licitação vencida sem contrato.
- **Antivírus** nos documentos (ClamAV) e **MFA**: evoluções (ASVS nível 2).
- **Recuperação de senha por e-mail:** hoje o administrador define uma senha nova.
- **Primeiro deploy:** escolher o servidor, o domínio com TLS e o SMTP, e rodar o runbook. Também ligar os workflows com o primeiro push.
