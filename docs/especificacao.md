# Especificação do ShemaCRM

> Texto original passado pelo cliente/produto em 26/10/2026, guardado sem alterações. As respostas às dúvidas de negócio e as decisões que mudam este texto ficam em `docs/decisoes.md` e `docs/adr/`.

1. Missão

Construa o ShemaCRM: um CRM focado em gestão de licitações públicas para a Shema Suprimentos e Serviços LTDA, empresa que vende materiais (ex.: material gráfico: banners, adesivos perfurados etc.) para órgãos públicos (prefeituras, Brigada Paraquedista, MEC/INES, Comando Militar etc.).

O sistema deve acompanhar cada licitação ganha do momento da vitória até o encerramento do contrato, controlando pedidos, saldo, documentos (principalmente a ata) e oportunidades de replicar a ata em outros municípios. O ERP comercial (propostas e contratos mais completos) fica para uma etapa futura. Hoje o escopo é só o CRM.

Idioma da interface e do domínio: português do Brasil. Código (nomes de classes, variáveis, endpoints): inglês. Mensagens de commit: português.

2. Skills: instalar primeiro, depois usar
2.1 Passo 0 — instale as skills antes de qualquer outra coisa

Nenhuma está instalada neste ambiente. Rode os comandos abaixo (Node.js 18+ necessário; funciona em PowerShell/CMD no Windows e em Linux/macOS), na raiz do projeto, para instalar em .claude/skills/ do projeto. Quando o instalador perguntar o agente, escolha Claude Code.

bash
# 1) Taste Skills (design-taste-frontend, anti-visual-genérico de IA)
npx skills add Leonxlnx/taste-skill --skill design-taste-frontend --agent claude-code

# 2) Web Design Guidelines (auditoria de UI/acessibilidade, baseada no Vercel Web Interface Guidelines)
npx skills add vercel-labs/agent-skills --skill web-design-guidelines --agent claude-code

# 3) Awesome Design (catálogo de DESIGN.md de design systems de referência)
git clone --depth 1 https://github.com/VoltAgent/awesome-design-md .claude/design-refs/awesome-design-md

# 4) Playwright CLI (CLI + skill + navegador)
npm install -g @playwright/cli@latest
npx skills add microsoft/playwright-cli --skill playwright-cli --agent claude-code
npx playwright install chromium

Depois de instalar:

Rode ls .claude/skills/ (ou dir .claude\skills no Windows) e confirme que aparecem design-taste-frontend, web-design-guidelines e playwright-cli. Confirme também que .claude/design-refs/awesome-design-md/ existe.
Reinicie a sessão do Claude Code para as skills serem carregadas.
Se algum comando falhar (nome do pacote, flag ou skill não encontrada), não pule: tente a alternativa abaixo, ou me mostre o erro.

Alternativas se falhar:

Taste Skills: npx skills add Leonxlnx/taste-skill (instala a skill do repositório inteiro; escolha design-taste-frontend).
Web Design Guidelines: npx skills add guanyang/antigravity-skills --skill web-design-guidelines --agent claude-code. Essa skill busca as regras atualizadas em https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md a cada revisão, então precisa de internet.
Playwright CLI (plugin): /plugin marketplace add microsoft/playwright-cli e depois /plugin install playwright-cli. Alternativa: playwright-cli install --skills.
Se playwright-cli não ficar no PATH, use npx @playwright/cli ... no lugar.

Registre em docs/decisoes.md o que foi instalado e como.

2.2 Como usar cada skill

Use estas skills durante o trabalho (invoque cada uma no momento certo):

Taste Skills: decisões de qualidade/estética do front-end, evitando visual genérico de template.
Web Design Guidelines: auditar a UI (acessibilidade, contraste, hierarquia, responsividade, estados vazios/erro/loading) antes de fechar cada fase.
Awesome Design: referência para o sistema visual (tokens, tipografia, espaçamento, componentes).
Playwright CLI: testes E2E e verificação visual das telas rodando de verdade (screenshots em desktop e mobile).

Se alguma dessas skills não estiver instalada, avise antes de começar e siga com a melhor alternativa equivalente, dizendo qual.

3. Referências visuais (CRM atual que a equipe usa hoje)

Descrição dos prints (use como inspiração de fluxo, melhorando a UX; não copie marca nem layout pixel a pixel):

3.1 Lista de Tarefas (tabela)

Colunas: checkbox, Tarefa, Status (ATRASADA em vermelho / ABERTA EM DIA em cinza), Data e hora (ordenável), Responsáveis (avatar com iniciais), Negociação (título em azul + empresa embaixo), Valor total, ícone de info.
Tarefas são nomeadas pelo órgão + valor, ex.: "Prefeitura Municipal de Magé — R$ 174.000,00", "Brigada Paraquedista — R$ 425.034,07", "MEC-INES Inst. Nac. de Educação de Surdos — R$ 425.232,00", "Comando da 1ª Região Militar — R$ 70.900,26", "Prefeitura Municipal de Cantagalo — R$ 287.201,54", "Prefeitura Municipal de Mesquita — R$ 310.000,00", "VIX Rio Soluções e Serviços — R$ 425.923,92", "Prefeitura Municipal de Conceição de Macabu — R$ 213.000,00".
Negociação associada: "LICITAÇÕES VENCIDAS E QUE FALTAM RECEBER O CONTRATO PRA ASSINAR" (valor total R$ 2.573.225,29) e "CONTRATOS ASSINADOS".

3.2 Negociações em Kanban

Alterna entre visão Kanban e lista.
Filtros: busca, "Minhas negociações", status "Em andamento"; chips de filtros ativos removíveis.
Colunas do funil, cada uma com contador e soma de valores no topo: Sem contato → Contato feito → Identificação do interesse → Apresentação → Proposta enviada → (mais à direita).
Card: etiqueta de status (Em andamento / Nova), título, empresa, indicadores (estrelas/prioridade, responsável, valor), faixa rosa quando há tarefa atrasada (órgão + data) e botão "Criar tarefa".

3.3 Pedidos do Edwin Lima (WhatsApp, áudios transcritos + textos) — esta é a especificação funcional principal:

Precisa de abas/colunas: Licitações vencidas, Contratos assinados, Contratos em andamento, Contratos encerrados + o fluxo de vendas (funil do item 3.2). As colunas devem ser simples e gerais; ao clicar em cada item abre o detalhe.
Licitações vencidas: lista de órgãos (ex.: Pref. de Magé, Pref. de Mesquita, Pref. de Nova Iguaçu). Ao clicar no órgão abre o detalhe com: UASG / Pregão Eletrônico (um campo que aceita preencher os dois), valor (ex.: R$ 300.000,00), tipo de material (ex.: MAT GRÁFICO), quantidade de itens (ex.: 5.000). Precisa de pesquisa/filtro para otimizar o trabalho.
Contratos assinados: órgão, UASG/Pregão (ex.: "UASG 123456 / Pregão Eletrônico 039/2026"), tipo de material, valor, data de assinatura (ex.: 16/05/2026). A data é muito importante para controle: se assinou há mais de um mês e ainda não houve pedido, o sistema deve sugerir um e-mail ao órgão perguntando se há previsão de pedidos de materiais, "visto que participamos do certame" (o objetivo é agilizar o pedido).
Contratos em andamento: tudo do assinado + valor já pedido, o que falta pedir (ex.: contrato R$ 300.000, pedido R$ 120.000, falta R$ 180.000,00), e itens solicitados (ex.: banner 2.000 / adesivos perfurados …). Deve haver campos livres para preencher o que foi pedido e o que falta pedir, e importação de planilha (a equipe já controla isso em planilha; importar e depois ajustar).
Contratos encerrados: tudo acima + data de encerramento (ex.: 12/12/2026) + anexar documento. É essencial registrar a ata: "a ata é dinheiro". Ela gera atestado para replicar em outras prefeituras. Mesmo com o contrato encerrado (ex.: em 2026), a ata pode continuar válida (ex.: até 2028). Se a ata vence em 2027, dá para mandar a um município para aderir e ele publicar em 2027. Precisa da aba de anexo de documentos para guardar a ata.
Cada órgão tem um detalhe com campos: nome do órgão, número/UASG, descritivo da licitação (objeto), valor, tipo de material, quantidade. Por cima disso entra o fluxo de vendas (funil).

Todos os exemplos numéricos acima (UASG 123456, Pregão 039/2026, R$ 300.000, etc.) são dados de exemplo: use-os apenas como seed de desenvolvimento, nunca como regra.

4. Glossário de domínio (use nos nomes de telas e entidades)
Órgão: ente público comprador (prefeitura, batalhão, ministério/instituto…).
UASG: código da Unidade Administrativa de Serviços Gerais do órgão.
Pregão Eletrônico (PE): modalidade/numeração da licitação (ex.: 039/2026). Pelo menos um entre UASG e PE deve ser informado; ambos são aceitos.
Ata (de registro de preços): documento que mantém os preços registrados e tem vigência própria, independente do contrato.
Contrato: instrumento assinado, com data de assinatura, vigência e encerramento.
Adesão / replicação da ata: usar uma ata vigente para oferecer a mesma contratação a outro órgão/município. Regras legais variam; modele como regra configurável e como oportunidade comercial, sem afirmar validade jurídica.
Atestado (de capacidade técnica): comprovante de fornecimento usado para habilitação em outras licitações.
Saldo: valor do contrato − valor já pedido.
5. Funcionalidades (MVP)
5.1 Ciclo de vida da licitação (núcleo do sistema)

Estados: VENCIDA → AGUARDANDO_ASSINATURA → CONTRATO_ASSINADO → EM_ANDAMENTO → ENCERRADO.

Tela principal com abas para os quatro blocos do item 3.3 (vencidas / assinados / em andamento / encerrados), com contadores e soma de valores em cada aba.
Tabela com busca textual, filtros (órgão, município, UF, tipo de material, faixa de valor, período, responsável) e ordenação. Colunas gerais e básicas; clique abre o detalhe (drawer lateral ou página).
Detalhe com abas: Resumo, Itens e pedidos, Documentos, Histórico, Tarefas, Negociação.
Transições de estado com validação (ex.: não pode ir a "assinado" sem data de assinatura; não pode "encerrar" sem data de encerramento) e registro no histórico.
5.2 Itens, pedidos e saldo
Itens do contrato (descrição, quantidade contratada, valor unitário).
Registro de pedidos (data, itens, quantidades, valor) e cálculo automático de valor pedido e saldo a pedir.
Campos livres de observação para "o que foi pedido / o que falta pedir".
Importação de planilha (.xlsx/.csv): upload → mapeamento de colunas → pré-visualização com erros por linha → confirmação. Importação idempotente, assíncrona (job no RabbitMQ, resposta 202 com jobId), com acompanhamento de status e relatório final baixável.
5.3 Atas e documentos
Upload de documentos por licitação, com tipo (Ata, Contrato, Empenho, Atestado, Edital, Outros), versão e data.
Entidade Ata com: número, vigência até, flag "permite adesão", observações. Independente da data de encerramento do contrato.
Destaque visual em contratos encerrados com ata ainda vigente ("Ata válida até 2028 — oportunidade de replicar").
Lista dedicada "Atas vigentes / oportunidades", ordenada por vencimento, com filtro por município sem ata dessa Shema.
5.4 Alertas e follow-up automático
Regra: contrato assinado há mais de 30 dias (configurável) sem pedido → cria tarefa + rascunho de e-mail ao órgão ("Assinamos contrato com vocês, há previsão de pedidos de materiais?"). Não envie e-mail automaticamente no MVP: gere o rascunho com botão "copiar / abrir no cliente de e-mail".
Regra: ata vencendo em 90/60/30 dias (configurável).
Regra: licitação vencida há X dias sem contrato assinado → tarefa atrasada.
Jobs agendados com Spring Scheduler + ShedLock; o job apenas publica eventos no RabbitMQ (ver seção 7) e consumidores idempotentes criam tarefa e rascunho. Nunca duplicar tarefas (chave de idempotência por contrato + regra + período).
5.5 Funil de vendas (Negociações) — Kanban
Kanban com colunas configuráveis, seed: Sem contato, Contato feito, Identificação do interesse, Apresentação, Proposta enviada, Fechado ganho, Fechado perdido.
Soma de valores e contador por coluna; drag-and-drop (Angular CDK); troca Kanban/lista; filtros "Minhas negociações" e status.
Card com status, valor, responsável, prioridade, faixa de tarefa atrasada e "Criar tarefa".
Negociação pode estar vinculada a uma licitação (ex.: oportunidade de replicar ata em novo município).
5.6 Tarefas
Lista no estilo do print 3.1: tarefa, status (Atrasada / Aberta em dia / Concluída), data e hora, responsável (avatar com iniciais), negociação/licitação vinculada, valor.
Criação manual e automática (alertas), conclusão em massa, filtros e ordenação por data.
5.7 Dashboard
Totais por estado do ciclo de vida, valor total em carteira, saldo a pedir total, contratos sem pedido há > 30 dias, atas vencendo, tarefas atrasadas, top órgãos por valor.
5.8 Acesso
Login com Spring Security (OAuth2 Resource Server, JWT access + refresh; ver seção 7). Perfis: ADMIN, COMERCIAL, LEITURA. Auditoria completa (quem alterou o quê e quando).
6. Modelo de dados (ponto de partida; refine com justificativa)

Orgao(id, nome, esfera, municipio, uf, cnpj?, contatos[]) Licitacao(id, orgaoId, uasg?, numeroPregao?, objeto, tipoMaterial, quantidadeItens, valorTotal, status, dataVitoria, responsavelId) Contrato(id, licitacaoId, dataAssinatura, vigenciaInicio, vigenciaFim, dataEncerramento?, valor) Ata(id, licitacaoId, numero, vigenciaAte, permiteAdesao, observacao) ItemContrato(id, contratoId, descricao, qtdContratada, valorUnitario) Pedido(id, contratoId, data, valor, observacao) + PedidoItem(pedidoId, itemId, quantidade) Documento(id, licitacaoId, tipo, nomeArquivo, storageKey, versao, enviadoPor, enviadoEm) Negociacao(id, titulo, licitacaoId?, orgaoId?, etapaId, valor, prioridade, status, responsavelId) / EtapaFunil(id, nome, ordem) Tarefa(id, titulo, vencimento, status, responsavelId, negociacaoId?, licitacaoId?, origem[MANUAL|ALERTA]) Historico(id, entidade, entidadeId, acao, usuarioId, em, diff) Usuario / Perfil ImportJob(id, tipo, arquivoKey, status, totalLinhas, linhasComErro, relatorioKey, criadoPor, criadoEm, concluidoEm) Outbox(id, aggregateType, aggregateId, eventType, payload, criadoEm, publicadoEm?) / MensagemProcessada(messageId, consumidor, processadaEm) Todas as entidades editáveis com version (optimistic locking), criadoEm/criadoPor/atualizadoEm/atualizadoPor.

Dinheiro sempre BigDecimal (escala 2), nunca double. Datas com LocalDate/OffsetDateTime; fuso America/Sao_Paulo.

7. Arquitetura técnica

Estilo arquitetural: monólito modular (não microsserviços)

Um único deployable Spring Boot, dividido em módulos por contexto de negócio: licitacoes, contratos (contratos, itens, pedidos), atas (atas e documentos), negociacoes (funil), tarefas, importacao, notificacoes, identidade (usuários/perfis).
Cada módulo expõe só uma API pública (pacote raiz / interface) e esconde o resto. Comunicação entre módulos por interface pública ou eventos de domínio, nunca por acesso direto a repositório de outro módulo. Os limites são verificados por teste (Spring Modulith ApplicationModules.verify() ou ArchUnit) e quebram o build se violados.
Dentro de cada módulo: Clean Architecture/DDD pragmático — pacotes domain, application, infrastructure, api. Regra de negócio (transições de estado, saldo, alertas) no domínio, testada sem Spring. Não crie abstração por reflexo: CRUD simples vai direto de service para repository, sem port/adapter só por formalidade. Justifique em docs/adr/ onde aplicou DDD completo.
Domínio com value objects (Dinheiro, Uasg, NumeroPregao, PeriodoVigencia) e máquina de estados explícita para o ciclo de vida da licitação.

Backend — stack

Java 21 (use virtual threads: spring.threads.virtual.enabled=true), Spring Boot 3.x (versão estável mais recente), Maven ou Gradle (escolha e justifique), PostgreSQL, Flyway (migrations versionadas, nunca ddl-auto=update).
Spring Web, Validation, Data JPA (Hibernate; atenção a N+1, use @EntityGraph/projeções nas listagens), Security, Scheduler. DTOs como Java records, separados das entidades; MapStruct para mapeamento.
Consultas de listagem com filtros dinâmicos via Specification/QueryDSL ou jOOQ (justifique); paginação por Pageable com limite máximo.
Concorrência: optimistic locking (@Version) nas entidades editáveis (licitação, contrato, pedido); resposta 409 tratada no front.
Auditoria: Hibernate Envers ou tabela Historico populada por eventos; sempre com usuário e timestamp. Soft delete onde houver documento jurídico.
Armazenamento de arquivos atrás de uma interface (FileStorage): disco local no MVP; S3-compatível (MinIO no docker-compose) como alvo. Metadados no banco, binário fora. Validar tipo/tamanho e antivírus (ClamAV) como evolução.
OpenAPI (springdoc) gerado; erros em RFC 7807 (ProblemDetail) com códigos de erro estáveis; versionamento da API (/api/v1); paginação, ordenação e filtros no servidor; idempotência (Idempotency-Key) nos POSTs críticos (pedidos, importação).
Cache: só se medir necessidade. Se precisar, Caffeine local antes de Redis.

Mensageria — RabbitMQ (Spring AMQP) Use RabbitMQ para o que é assíncrono por natureza, e não para CRUD síncrono:

Importação de planilha: o upload responde 202 Accepted com um jobId; o processamento roda em background por consumidor; o front acompanha o status (polling ou SSE) e baixa o relatório de erros por linha.
Alertas e follow-up: o job agendado diário só publica eventos (ContratoSemPedido30Dias, AtaVencendo, LicitacaoVencidaSemContrato); consumidores criam a tarefa e geram o rascunho de e-mail. Isso desacopla regra de alerta de efeito colateral.
Eventos de domínio entre módulos (ContratoAssinado, ContratoEncerrado, AtaCadastrada, PedidoRegistrado) para notificações e histórico, sem acoplamento direto.
Pós-processamento de documentos (varredura de antivírus, extração de texto da ata) quando entrar.

Padrões obrigatórios:

Transactional Outbox: evento só é publicado se a transação do banco commitar (tabela outbox + relay; ou Spring Modulith Event Publication Registry). Nada de publicar dentro da transação "no escuro".
Consumidores idempotentes (tabela de mensagens processadas ou chave natural), com acks manuais e prefetch configurado.
Retry com backoff exponencial + Dead Letter Queue por fila + procedimento documentado para reprocessar a DLQ.
Topologia declarada em código (exchanges topic, filas duráveis, bindings), nomes padronizados (shemacrm.<contexto>.<evento>), mensagens em JSON com envelope versionado (eventId, type, version, occurredAt, correlationId).
Em ambiente de teste, Testcontainers (RabbitMQ).
Agendamento com ShedLock (lock em PostgreSQL) para que o job rode uma vez mesmo com várias instâncias.

Segurança

Spring Security como OAuth2 Resource Server validando JWT (access curto + refresh). No MVP, emissão pelo próprio backend com chaves assimétricas (RS256/EdDSA) e kid; deixe a configuração pronta para trocar por um IdP (Keycloak) sem mexer nos módulos.
Autorização por perfil + permissão (@PreAuthorize), com teste de autorização por endpoint. Regras por recurso (ex.: COMERCIAL só edita o que é seu, LEITURA nunca escreve).
Senhas com Argon2/bcrypt, bloqueio por tentativas, rate limiting no login (Bucket4j), CORS restrito, headers de segurança, CSRF desabilitado só onde faz sentido (API stateless).
LGPD: dados pessoais mínimos (contatos de órgãos), logs sem dados sensíveis, trilha de auditoria, política de retenção documentada.
Dependências verificadas (Dependabot/Renovate + OWASP dependency-check ou Trivy no CI); nenhum segredo no repositório.

Observabilidade e operação

Spring Boot Actuator (health com liveness/readiness, métricas), Micrometer + Prometheus, OpenTelemetry para traces, logs estruturados em JSON com correlationId propagado do front → API → mensageria.
Métricas de negócio expostas (contratos sem pedido, ata a vencer, tamanho de fila, jobs de importação falhos).
Dashboards Grafana básicos no ambiente de dev (opcional no MVP, mas deixe a stack no compose em perfil observability).
Config por ambiente via application-*.yml + variáveis de ambiente (12-factor); Dockerfile multi-stage com imagem enxuta e usuário não-root; health checks no compose.

Testes (backend)

Pirâmide: muitos testes de domínio (JUnit 5 + AssertJ, sem Spring), testes de aplicação com Mockito, testes de integração com Testcontainers (PostgreSQL + RabbitMQ), testes de contrato/API (MockMvc/RestAssured) validando erros e autorização, e teste de arquitetura (ArchUnit/Modulith).
Cobertura mínima com JaCoCo (domínio ≥ 80%) como quality gate no CI; mutation testing (PIT) opcional no domínio.
Qualidade estática: Spotless/Checkstyle, SpotBugs/Error Prone, SonarQube/SonarCloud quando houver repositório remoto.

Frontend

Angular (versão estável mais recente), standalone components, Signals, lazy routes, HttpClient + interceptors (auth, erro), reactive forms tipados.
UI: Angular Material + CDK (drag-and-drop do Kanban, tabelas, overlays) ou alternativa justificada; tema próprio via design tokens (CSS variables), sem visual padrão de template.
Estrutura por feature (licitacoes, negociacoes, tarefas, atas, dashboard, importacao, auth), com core/ e shared/.
Cliente da API gerado a partir do OpenAPI (openapi-generator ou orval), commitado ou gerado no build, nunca escrito à mão.
Estado: Signals + serviços por feature; só adote NgRx/SignalStore se o estado compartilhado justificar (registre em ADR). Dados de servidor com cache e invalidação explícitos (ex.: httpResource/TanStack Query Angular, se estável; senão serviços com shareReplay).
Tabelas grandes com paginação no servidor e virtual scroll; filtros e ordenação refletidos na URL; ChangeDetectionStrategy.OnPush por padrão; @defer para telas pesadas; budgets de bundle no angular.json.
Tratamento global de erros (interceptor + ProblemDetail), refresh de token transparente, tratamento de 409 (conflito de versão) com UX clara.
Qualidade: ESLint (angular-eslint) + Prettier, strict TypeScript, testes unitários com Vitest/Jest (ou o runner padrão da versão do Angular) e componentes testados por comportamento; E2E com Playwright.
i18n pronta (@angular/localize ou ngx-translate) com pt-BR como padrão; LOCALE_ID e pipes configurados.
Segurança do front: sem token em localStorage se puder evitar (preferir cookie HttpOnly + SameSite ou memória + refresh), sanitização padrão do Angular, CSP.

Infra de desenvolvimento e entrega

docker-compose.yml com PostgreSQL, RabbitMQ (rabbitmq:management), MinIO (+ pgAdmin opcional), perfis dev/test/observability, volumes nomeados e health checks.
Monorepo: /backend, /frontend, /docs (inclui /docs/adr), /e2e, /deploy. README com como subir tudo em um comando (docker compose up) e Makefile/scripts de atalho.
CI/CD (GitHub Actions): build + testes + cobertura + lint + análise de dependências em todo PR; imagens Docker versionadas; deploy automatizado para homologação. Pipelines separados por área (backend, frontend, e2e) com cache.
Infra como código para o ambiente alvo (Terraform ou Compose de produção), com ambientes dev, homolog e prod. Backup e restore testados do PostgreSQL e do storage de documentos.
ADRs (docs/adr/NNNN-titulo.md) para toda decisão relevante (monólito modular, outbox, auth, escolha de ORM/query, estado no front).
Seed de dados de desenvolvimento com os exemplos do item 3 (órgãos de RJ: Magé, Mesquita, Nova Iguaçu, Cantagalo, Conceição de Macabu etc.).
8. Design e UX
Usuário típico: comercial/gestor de licitações, trabalha o dia todo em tabelas e listas → densidade de informação confortável, leitura rápida, atalhos de teclado nas listas, filtros sempre visíveis e persistentes na URL.
Direção visual: sóbria, profissional e com personalidade (defina com a skill Awesome Design + Taste Skills): paleta com 1 cor de marca + neutros + semânticas (vermelho = atrasada/vencendo, âmbar = atenção, verde = ok), tipografia com hierarquia clara, números tabulares nos valores em R$.
Valores sempre formatados pt-BR (R$ 2.573.225,29), datas dd/MM/yyyy.
Estados completos em toda tela: loading (skeleton), vazio (com ação), erro (com retry), sem permissão.
Acessibilidade WCAG AA, foco visível, navegação por teclado (inclusive no Kanban), aria-* corretos. Auditar com Web Design Guidelines.
Responsivo: desktop é prioridade, mas as telas de consulta (lista/detalhe/tarefas) devem funcionar bem no celular.
Tema claro primeiro; escuro como tokens preparados.
9. Testes e verificação (Playwright CLI)

Use o Playwright CLI para:

Escrever testes E2E dos fluxos críticos:
login → criar licitação vencida → registrar assinatura → registrar pedido → ver saldo atualizado;
encerrar contrato → anexar ata → ver destaque "ata vigente";
importar planilha com linhas válidas e inválidas;
mover card no Kanban e conferir somas das colunas;
alerta de "assinado há > 30 dias sem pedido" gerando tarefa + rascunho de e-mail;
filtros e busca na lista de licitações.
Capturar screenshots de cada tela principal em 1440px e 390px e revisar visualmente antes de dar a fase como pronta.
Rodar em CI (workflow do GitHub Actions) junto com testes do backend.

Os E2E rodam contra o ambiente do docker-compose (Postgres + RabbitMQ reais), com dados de seed determinísticos. Fluxos assíncronos (importação, alertas) são verificados esperando o estado final, nunca com sleep fixo.

Cobertura mínima: domínio do backend ≥ 80% (JaCoCo como quality gate); todos os fluxos acima com E2E; teste de arquitetura sem violações.

10. Plano de entrega (trabalhe em fases e pare ao fim de cada uma para eu validar)
Fase 0 — Fundação: repositório, docker-compose (Postgres, RabbitMQ, MinIO), backend (módulos + teste de arquitetura) e frontend rodando, CI com quality gates, login (OAuth2 Resource Server), observabilidade básica (Actuator, logs JSON, correlationId), ADRs iniciais, layout base (shell, navegação, tema).
Fase 1 — Núcleo: Órgãos, Licitações com máquina de estados e as 4 abas, detalhe, histórico/auditoria, busca e filtros, optimistic locking.
Fase 2 — Contratos e pedidos: itens, pedidos, saldo, data de assinatura/encerramento, importação de planilha assíncrona (RabbitMQ + job + relatório de erros) e outbox pattern implementado.
Fase 3 — Atas e documentos: upload via FileStorage (MinIO), entidade Ata, oportunidades de replicação.
Fase 4 — Funil e tarefas: Kanban, tarefas, vínculo com licitação.
Fase 5 — Alertas e dashboard: eventos de alerta (scheduler + ShedLock → RabbitMQ → consumidores), DLQ e reprocessamento, rascunhos de e-mail, indicadores, métricas de negócio.
Fase 6 — Polimento e prontidão para produção: auditoria de design/acessibilidade, performance (N+1, índices, EXPLAIN), segurança (checklist OWASP ASVS nível 1), backup/restore testado, runbook de operação, documentação.
11. Como quero que você trabalhe
Antes de codar a Fase 0, me devolva: (a) resumo do que entendeu, (b) decisões de arquitetura e dependências escolhidas, (c) lista de dúvidas de negócio que realmente bloqueiam (máx. 8). Para o resto, assuma o padrão mais razoável e registre como ADR em docs/adr/ (e o que foi instalado das skills em docs/decisoes.md).
Commits pequenos e frequentes, uma branch por fase, PR com descrição do que mudou e como testar.
Nunca deixe segredo no repositório; use variáveis de ambiente e .env.example.
Ao fim de cada fase: testes verdes, screenshots Playwright anexados, checklist da Web Design Guidelines preenchido, e um resumo curto do que ficou pronto e do que ficou pendente.
Não invente regra jurídica de licitação: quando algo depender de lei ou edital (prazos de ata, limites de adesão), deixe configurável e sinalize para validação humana.
12. Definição de pronto do MVP
Consigo cadastrar uma licitação vencida e levá-la até encerrada, registrando assinatura, pedidos e saldo.
Vejo, numa tela só, as quatro abas com contadores e valores corretos.
Anexo a ata em um contrato encerrado e o sistema destaca a ata ainda vigente como oportunidade.
Contratos assinados há mais de 30 dias sem pedido geram tarefa e rascunho de e-mail.
Importo minha planilha atual de contratos em andamento sem retrabalho manual.
Kanban e lista de tarefas funcionam como nos prints, com soma de valores por coluna.
Todos os E2E passam e as telas passam na auditoria de acessibilidade.
A importação de planilha grande não trava a tela: responde na hora, processa em segundo plano e entrega relatório de erros por linha.
Uma mensagem com falha vai para a DLQ e dá para reprocessar; nenhum alerta gera tarefa duplicada.
O build quebra se um módulo violar os limites, se a cobertura do domínio cair abaixo do mínimo ou se houver vulnerabilidade crítica em dependência.
docker compose up sobe o sistema completo do zero, com seed, e o README explica o resto.
